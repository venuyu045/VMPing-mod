package com.venus.vmping.net;

import com.google.gson.JsonObject;
import com.venus.vmping.VMPingClient;
import com.venus.vmping.config.ModConfig;
import com.venus.vmping.core.ClientContext;
import com.venus.vmping.core.PingClock;
import com.venus.vmping.core.PingData;
import com.venus.vmping.core.PingKind;
import com.venus.vmping.core.PingStore;
import com.venus.vmping.core.RoomKey;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 标点中继：认证 → 长轮询 → 发送，一套完整的客户端状态机。
 *
 * <p>线程模型（重要）：
 * <ul>
 *   <li>{@link #start}/{@link #stop}/{@link #sendPing} 都在**客户端主线程**调用；</li>
 *   <li>HTTP 回调在 JDK 的公共线程池里跑，只写 {@link PingStore}（并发安全）与 volatile 状态；</li>
 *   <li>渲染线程只读 {@link #store()} 的快照与 volatile 状态，不会被网络阻塞。</li>
 * </ul>
 *
 * <p>状态机：
 * <pre>
 *   OFF ──进服──► AUTHENTICATING ──成功──► ONLINE ──连续失败──► DEGRADED
 *    ▲                 │                     │                    │
 *    └──退服───────────┴─────401────────────┴──────恢复────────────┘
 * </pre>
 * {@code DEGRADED} 时标点仍然本地可见（{@link PingStore} 里 {@code local=true}），
 * 只是别人看不见；界面状态栏会说明原因，不弹窗、不刷屏。
 */
public final class PingRelay {

    /** 状态机状态。 */
    public enum State {
        /** 未连接（未进服 / 配置关闭）。 */
        OFF,
        /** 正在认证。 */
        AUTHENTICATING,
        /** 在线：能收能发。 */
        ONLINE,
        /** 降级：后端不可用，只做本地标点。 */
        DEGRADED
    }

    /** 连续失败后重试的退避序列（毫秒），封顶 30s。 */
    private static final long[] BACKOFF_MS = {1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L};

    private final PingStore store = new PingStore();
    private final PingClock clock = store.clock();

    private volatile ModConfig config = ModConfig.get();
    private volatile PingApi api;
    private volatile PingAuth auth;

    private volatile State state = State.OFF;
    private volatile String roomId = "";
    private volatile String dimension = "";
    private volatile String lastErrorKey = "";
    private volatile String lastErrorDetail = "";

    /** 是否还有一个长轮询在飞（同一时刻只允许一个）。 */
    private final AtomicBoolean polling = new AtomicBoolean(false);
    /** 会话代号：每次 start/stop 自增，异步回调发现代号变了就丢弃结果（防止退服后旧响应写进新房间）。 */
    private final AtomicLong generation = new AtomicLong(0L);
    /** 连续失败次数（决定退避）。 */
    private final AtomicLong failures = new AtomicLong(0L);
    /** 下次允许重试的时间戳。 */
    private volatile long nextRetryAtMs = 0L;
    /** 是否已经启动过（防止重复 start）。 */
    private final AtomicBoolean started = new AtomicBoolean(false);

    public PingStore store() {
        return store;
    }

    public State state() {
        return state;
    }

    public String roomId() {
        return roomId;
    }

    public String dimension() {
        return dimension;
    }

    /** 最近一次错误的语言 key（空串 = 没有错误）。 */
    public String lastErrorKey() {
        return lastErrorKey;
    }

    /** 最近一次错误的原始信息（诊断用，只进日志/调试命令）。 */
    public String lastErrorDetail() {
        return lastErrorDetail;
    }

    public boolean online() {
        return state == State.ONLINE;
    }

    /** 是否已经为当前世界启动过（tick 里做"进服事件没触发"的兜底判断用）。 */
    public boolean isStarted() {
        return started.get();
    }

    /** 配置热更新：重建 HTTP 客户端并重启中继。 */
    public synchronized void reload(ModConfig newConfig) {
        this.config = newConfig;
        boolean wasActive = started.get();
        if (wasActive) {
            stop();
            start(newConfig);
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 进服：计算房间、恢复/发起认证、启动长轮询。
     *
     * <p>由 {@code VMPingClient} 在 {@code ClientPlayConnectionEvents.JOIN} 里调用。
     */
    public synchronized void start(ModConfig activeConfig) {
        this.config = activeConfig;
        if (!activeConfig.enabled) {
            state = State.OFF;
            return;
        }
        String newRoomId = ClientContext.roomId();
        String newDimension = ClientContext.dimensionId();
        if (newRoomId.isBlank()) {
            // 单人世界/尚未加载完：不启用中继（本地标点仍然可用）
            state = State.OFF;
            roomId = "";
            return;
        }
        long gen = generation.incrementAndGet();
        started.set(true);
        roomId = newRoomId;
        dimension = newDimension;
        store.clear();
        clock.reset();
        failures.set(0L);
        nextRetryAtMs = 0L;
        lastErrorKey = "";
        lastErrorDetail = "";

        // 单机 / 局域网不同步（协议 v1.1 §3）：所有单机玩家共用一个房间会造成隐私问题，
        // 而且"同服玩家"的语义本身就模糊。只做本地标点（tick 仍然要跑，负责过期清理）。
        String normalized = RoomKey.normalize(ClientContext.rawServerAddress());
        if (RoomKey.SINGLEPLAYER.equals(normalized) || RoomKey.LAN.equals(normalized)) {
            roomId = "";
            state = State.DEGRADED;
            lastErrorKey = "vmping.status.local_only";
            VMPingClient.LOGGER.debug("单机/局域网世界不同步标点（本地模式）");
            return;
        }

        if (!activeConfig.relayEnabled()) {
            // 纯本地模式：不认证、不轮询
            state = State.DEGRADED;
            lastErrorKey = "vmping.status.local_only";
            return;
        }
        if (!activeConfig.secureBackend()) {
            state = State.DEGRADED;
            lastErrorKey = "vmping.error.insecure_backend";
            return;
        }

        // 重建 HTTP 客户端（后端地址可能变了）
        closeClients();
        PingApi newApi = new PingApi(activeConfig.apiBaseUrl);
        PingAuth newAuth = new PingAuth(newApi);
        this.api = newApi;
        this.auth = newAuth;

        state = State.AUTHENTICATING;
        VMPingClient.LOGGER.info("标点中继启动：room={} dimension={} backend={}",
                roomId, dimension, activeConfig.apiBaseUrl);

        // 先看磁盘里有没有还没过期的 token，有就直接用（进服瞬间不用等一轮认证）
        PingAuth.Session saved = newAuth.loadSavedSession();
        if (saved != null) {
            VMPingClient.LOGGER.info("复用已保存的标点会话（{}）", saved.mcName());
            state = State.ONLINE;
            pollLoop(gen);
            return;
        }
        newAuth.authenticate(activeConfig.authServer)
                .whenComplete((session, throwable) -> {
                    if (generation.get() != gen) {
                        return;
                    }
                    if (throwable != null) {
                        PingApi.ApiException apiError = PingApi.unwrap(throwable);
                        onFailure(gen, apiError, true);
                        return;
                    }
                    state = State.ONLINE;
                    failures.set(0L);
                    VMPingClient.LOGGER.info("标点中继已上线：{}", session.mcName());
                    pollLoop(gen);
                });
    }

    /** 退服：停轮询、清标点、关客户端。 */
    public synchronized void stop() {
        generation.incrementAndGet();
        started.set(false);
        polling.set(false);
        state = State.OFF;
        roomId = "";
        dimension = "";
        store.clear();
        clock.reset();
        closeClients();
    }

    /** 切维度：更新当前维度并清掉其它维度的旧标点（保留自己的短时标点）。 */
    public void onDimensionChanged(String newDimension) {
        if (newDimension == null || newDimension.isBlank() || newDimension.equals(dimension)) {
            return;
        }
        dimension = newDimension;
        store.clearOthers(ClientContext.selfUuid());
    }

    private void closeClients() {
        PingApi currentApi = api;
        PingAuth currentAuth = auth;
        api = null;
        auth = null;
        if (currentApi != null) {
            currentApi.close();
        }
        if (currentAuth != null) {
            currentAuth.close();
        }
    }

    // ==================== 发送 ====================

    /**
     * 发一个标点。
     *
     * <p>先本地插入（立即渲染，0 延迟），再异步 POST；POST 成功用服务端返回的真实 id/seq
     * 替换本地占位；失败则把标点降级为"仅本地"（{@code local=true}）并记录错误 key。
     *
     * @return 本地占位标点（调用方可用于播放音效/动画）
     */
    public PingData sendPing(double x, double y, double z, PingKind kind,
                             UUID targetUuid, String targetName) {
        UUID self = ClientContext.selfUuid();
        String selfName = ClientContext.selfName();
        String currentDimension = dimension.isBlank() ? ClientContext.dimensionId() : dimension;
        long ttl = Math.max(1_000L, config.pingTtlSeconds * 1000L);

        PingData localPing = PingData.local(self, selfName, currentDimension, x, y, z, kind,
                targetUuid, targetName, clock.now(), ttl);

        PingApi currentApi = api;
        PingAuth currentAuth = auth;
        boolean canSend = state == State.ONLINE && currentApi != null && currentApi.hasToken()
                && roomId != null && !roomId.isBlank();

        if (!canSend) {
            // 降级：只本地可见
            store.put(localPing);
            if (config.degradeToLocal) {
                lastErrorKey = state == State.OFF ? "vmping.status.disconnected" : "vmping.status.local_only";
            }
            return localPing;
        }

        // 本地先显示（用 local=true 的占位，POST 回来再替换）
        store.put(localPing);

        JsonObject body = PingCodec.encodeSend(currentDimension, x, y, z, kind, targetUuid, targetName,
                System.currentTimeMillis());
        long gen = generation.get();
        currentApi.sendPing(roomId, body).whenComplete((response, throwable) -> {
            if (generation.get() != gen) {
                return;
            }
            if (throwable != null) {
                PingApi.ApiException apiError = PingApi.unwrap(throwable);
                VMPingClient.LOGGER.debug("标点发送失败: {} {}", apiError.code(), apiError.getMessage());
                store.put(localPing); // 保留本地占位（local=true）
                if (apiError.needsReauth() && currentAuth != null) {
                    // token 失效：清掉本地会话，下一次 tick 会重新认证
                    currentAuth.clearSession();
                    state = State.AUTHENTICATING;
                    authenticateAndPoll(gen);
                } else {
                    lastErrorKey = errorKey(apiError);
                    lastErrorDetail = apiError.code() + ": " + apiError.getMessage();
                }
                return;
            }
            // 成功：用服务端返回的 id/seq/serverTime/expiresAt 合并出"权威标点"替换本地占位。
            // 注意 POST 响应只有 id/seq/时间（没有坐标/玩家），所以不能直接 decodePing 整条，
            // 必须拿本地那条的数据 + 服务端分配的元数据拼一条。
            String serverId = PingCodec.optString(response, "id", "");
            if (serverId.isBlank()) {
                serverId = localPing.id();
            }
            long seq = PingCodec.optLong(response, "seq", localPing.seq());
            long serverTime = PingCodec.optLong(response, "serverTime", localPing.serverTime());
            long expiresAt = PingCodec.optLong(response, "expiresAt", localPing.expiresAt());
            PingData confirmed = new PingData(
                    serverId, seq, localPing.playerUuid(), localPing.playerName(),
                    localPing.dimension(), localPing.x(), localPing.y(), localPing.z(),
                    localPing.kind(), localPing.targetUuid(), localPing.targetName(),
                    serverTime, expiresAt, false);
            store.remove(localPing.id());
            store.put(confirmed);
            lastErrorKey = "";
            failures.set(0L);
        });
        return localPing;
    }

    // ==================== 长轮询 ====================

    private void pollLoop(long gen) {
        if (generation.get() != gen || !started.get()) {
            return;
        }
        PingApi currentApi = api;
        if (currentApi == null || !currentApi.hasToken()) {
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return; // 已有请求在飞
        }
        long since = store.lastSeq();
        int timeout = config.pollTimeoutSeconds;
        String filterDimension = ""; // 不按维度过滤：同服不同维度的标点也收，渲染时再筛
        currentApi.pollPings(roomId, since, timeout, filterDimension)
                .whenComplete((response, throwable) -> {
                    polling.set(false);
                    if (generation.get() != gen || !started.get()) {
                        return;
                    }
                    if (throwable != null) {
                        PingApi.ApiException apiError = PingApi.unwrap(throwable);
                        onFailure(gen, apiError, false);
                        return;
                    }
                    PingCodec.PollResult result = PingCodec.decodePollResponse(response);
                    if (result.serverTime() > 0L) {
                        store.syncClock(result.serverTime());
                    }
                    // 房间被回收后重建：seq 归零。如果还拿着旧 since，会永远收不到新标点。
                    if (result.seq() > 0L && result.seq() < store.lastSeq()) {
                        VMPingClient.LOGGER.debug("检测到房间序号回退（{} < {}），重置游标",
                                result.seq(), store.lastSeq());
                        store.clearOthers(ClientContext.selfUuid());
                    }
                    List<PingData> received = result.pings();
                    if (!received.isEmpty()) {
                        store.putAll(received);
                        // 别人刚标的点：给个提示音（由 UI 层消费）
                        VMPingClient.getInstance().onRemotePings(received);
                    }
                    failures.set(0L);
                    nextRetryAtMs = 0L;
                    lastErrorKey = "";
                    // 立刻发下一次长轮询（长轮询本身会阻塞到有数据或超时，不会忙等）
                    pollLoop(gen);
                });
    }

    /** 认证（重新）成功后继续轮询。 */
    private void authenticateAndPoll(long gen) {
        PingAuth currentAuth = auth;
        if (currentAuth == null) {
            return;
        }
        currentAuth.authenticate(config.authServer).whenComplete((session, throwable) -> {
            if (generation.get() != gen) {
                return;
            }
            if (throwable != null) {
                onFailure(gen, PingApi.unwrap(throwable), true);
                return;
            }
            state = State.ONLINE;
            failures.set(0L);
            nextRetryAtMs = 0L;
            pollLoop(gen);
        });
    }

    /** 失败处理：记录错误、退避、必要时重新认证。 */
    private void onFailure(long gen, PingApi.ApiException error, boolean fromAuth) {
        long count = failures.incrementAndGet();
        lastErrorKey = errorKey(error);
        lastErrorDetail = error.code() + ": " + error.getMessage();
        VMPingClient.LOGGER.debug("标点中继失败（第 {} 次，auth={}）: {} {}", count, fromAuth,
                error.code(), error.getMessage());

        if (error.needsReauth()) {
            PingAuth currentAuth = auth;
            if (currentAuth != null) {
                currentAuth.clearSession();
            }
            state = State.AUTHENTICATING;
            scheduleRetry(gen, count, () -> authenticateAndPoll(gen));
            return;
        }
        state = State.DEGRADED;
        if (error.backendUnavailable()) {
            scheduleRetry(gen, count, () -> pollLoop(gen));
        }
    }

    private void scheduleRetry(long gen, long failuresSoFar, Runnable action) {
        long delay = BACKOFF_MS[(int) Math.min(BACKOFF_MS.length - 1, Math.max(0, failuresSoFar - 1))];
        nextRetryAtMs = System.currentTimeMillis() + delay;
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation.get() != gen || !started.get()) {
                return;
            }
            action.run();
        });
    }

    // ==================== 每 tick 维护 ====================

    /**
     * 每客户端 tick 调用（20 次/秒）：
     * <ul>
     *   <li>清理过期标点；</li>
     *   <li>检测维度变化；</li>
     *   <li>如果处于降级/认证中且退避时间到了，补一次重试（兜底：delayedExecutor 万一被吞）。</li>
     * </ul>
     */
    public void tick() {
        if (!started.get()) {
            return;
        }
        String currentDimension = ClientContext.dimensionId();
        if (!currentDimension.isBlank() && !currentDimension.equals(dimension)) {
            onDimensionChanged(currentDimension);
        }
        store.expire(clock.now());

        if (state != State.ONLINE && nextRetryAtMs > 0L && System.currentTimeMillis() >= nextRetryAtMs) {
            long gen = generation.get();
            nextRetryAtMs = 0L;
            if (state == State.AUTHENTICATING) {
                authenticateAndPoll(gen);
            } else if (state == State.DEGRADED && config.relayEnabled()) {
                pollLoop(gen);
            }
        }
    }

    // ==================== 错误文案 ====================

    /**
     * 协议错误码 → 语言文件 key。
     *
     * <p>约定：这里只返回 key，真正的文案在 {@code assets/vmping/lang/*.json}；
     * 新增错误码时必须同步补语言文件，否则玩家会看到 key 本身。
     */
    private static String errorKey(PingApi.ApiException error) {
        String code = error == null ? "" : error.code();
        return switch (code) {
            case "no_session", "no_profile" -> "vmping.error.no_session";
            case "no_token", "unauthorized", "bad_token" -> "vmping.error.no_token";
            case "insecure_backend" -> "vmping.error.insecure_backend";
            case "rate_limited" -> "vmping.error.rate_limited";
            case "join_failed" -> "vmping.error.join_failed";
            case "circuit_open", "network_error" -> "vmping.status.local_only";
            default -> "vmping.error.unknown";
        };
    }
}
