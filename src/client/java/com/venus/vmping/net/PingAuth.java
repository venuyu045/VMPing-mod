package com.venus.vmping.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.venus.vmping.VMPingClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 中继后端的身份认证（与 VMFashion 的时装认证同一套 serverId 挑战流程）。
 *
 * <p>为什么要认证：中继是**公网服务**，如果谁都能往房间里写标点，任何人都能冒充别人
 * 喊"这里有钻石"。认证证明"这个 UUID 现在确实登录着这个账号"，服务端据此签发 token，
 * 之后所有标点都带 token 里的身份，客户端伪造不了 {@code playerName}。
 *
 * <p>三步流程（协议 v1 §2）：
 * <ol>
 *   <li>{@code POST /auth/challenge} 拿一次性 {@code serverId}（TTL 120s、绑定来源 IP）；</li>
 *   <li>用当前 MC 会话向**会话服务器** join（正版 = Mojang，皮肤站 = 皮肤站 Yggdrasil）；</li>
 *   <li>{@code POST /auth/verify}，后端反查 {@code hasJoined} 并比对 UUID，一致才签发 token。</li>
 * </ol>
 *
 * <p>皮肤站地址探测顺序：JVM 参数里的 authlib-injector → 配置项。正版玩家两者都没有，留空即可。
 *
 * <p>token 落盘在 {@code config/vmping/session.json}（权限 600），过期前 60s 视为过期并重新认证。
 */
public final class PingAuth {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(15);
    private static final String MOJANG_SESSION_SERVER = "https://sessionserver.mojang.com";

    private static final Path SESSION_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("vmping").resolve("session.json");

    private final PingApi api;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Session session;
    private volatile CompletableFuture<Session> inFlight;

    public PingAuth(PingApi api) {
        this.api = api;
    }

    /** 一次成功的认证结果。 */
    public record Session(String token, String mcUuid, String mcName, String authType,
                          long expiresAt, String apiBaseUrl) {
        /** 是否即将过期（提前 60s 视为过期，避免请求发到一半失效）。 */
        public boolean expired() {
            return System.currentTimeMillis() > expiresAt - 60_000L;
        }
    }

    public PingApi api() {
        return api;
    }

    public Session session() {
        return session;
    }

    /** 释放本类自己的 {@link HttpClient}（换后端地址时调用）。 */
    public void close() {
        try {
            http.shutdownNow();
        } catch (Throwable t) {
            VMPingClient.LOGGER.debug("关闭标点认证 HttpClient 失败（忽略）: {}", t.toString());
        }
    }

    // ==================== 认证主流程 ====================

    /**
     * 完成一次认证；并发调用复用同一个 future（进服瞬间可能有多个触发点）。
     *
     * @param authServerOverride 配置里手填的皮肤站地址，空则自动探测
     */
    public CompletableFuture<Session> authenticate(String authServerOverride) {
        CompletableFuture<Session> existing = inFlight;
        if (existing != null && !existing.isDone()) {
            return existing;
        }
        CompletableFuture<Session> future = doAuthenticate(authServerOverride);
        inFlight = future;
        return future.whenComplete((ignored, throwable) -> inFlight = null);
    }

    private CompletableFuture<Session> doAuthenticate(String authServerOverride) {
        // 明文 http 后端：连认证都不要做 —— /auth/verify 的响应体里就是 token
        String apiBase = api == null ? "" : api.baseUrl();
        if (!apiBase.isBlank() && !com.venus.vmping.config.ModConfig.isSecureUrl(apiBase)) {
            return CompletableFuture.failedFuture(new PingApi.ApiException(400, "insecure_backend",
                    "标点后端地址不是 https，出于安全已停止发送登录凭据"));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return CompletableFuture.failedFuture(
                    new PingApi.ApiException(-1, "no_session", "Minecraft 客户端尚未初始化"));
        }
        User user = minecraft.getUser();
        if (user == null) {
            return CompletableFuture.failedFuture(
                    new PingApi.ApiException(-1, "no_session", "尚未登录 Minecraft 账号"));
        }
        UUID uuid = user.getProfileId();
        String name = user.getName();
        String accessToken = user.getAccessToken();
        String authServer = detectAuthServer(authServerOverride);

        if (uuid == null || name == null || name.isBlank()) {
            return CompletableFuture.failedFuture(
                    new PingApi.ApiException(-1, "no_profile", "无法读取当前角色信息"));
        }
        if (accessToken == null || accessToken.isBlank() || accessToken.length() < 8) {
            return CompletableFuture.failedFuture(new PingApi.ApiException(-1, "no_token",
                    "当前启动器没有提供有效的登录令牌（离线登录无法同步标点）"));
        }

        VMPingClient.LOGGER.info("开始标点后端认证：{} ({}) authServer={}", name, uuid,
                authServer.isBlank() ? "mojang" : authServer);

        return api.challenge(uuid.toString(), name, authServer)
                .thenCompose(challenge -> {
                    String serverId = PingCodec.optString(challenge, "serverId", "");
                    if (serverId.isBlank()) {
                        throw new CompletionException(new PingApi.ApiException(-1, "bad_challenge",
                                "后端没有返回 serverId"));
                    }
                    String authType = PingCodec.optString(challenge, "authType", "mojang");
                    return joinSession(authServer, accessToken, uuid, serverId)
                            .thenCompose(ignored -> api.verify(uuid.toString(), name, authServer, serverId))
                            .thenApply(verify -> buildSession(verify, uuid.toString(), name, authType));
                });
    }

    private Session buildSession(JsonObject verify, String uuid, String name, String fallbackAuthType) {
        String token = PingCodec.optString(verify, "token", "");
        if (token.isBlank()) {
            throw new CompletionException(new PingApi.ApiException(-1, "bad_verify", "后端没有返回 token"));
        }
        String authType = PingCodec.optString(verify, "authType", fallbackAuthType);
        long expiresInSeconds = PingCodec.optLong(verify, "expiresIn", 86_400L);
        if (expiresInSeconds <= 0) {
            expiresInSeconds = 86_400L;
        }
        long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;
        // 服务端也可能直接给绝对时间戳（毫秒），两种都兼容
        long absolute = PingCodec.optLong(verify, "expiresAt", 0L);
        if (absolute > 1_000_000_000_000L) {
            expiresAt = absolute;
        }

        Session result = new Session(token, uuid, name, authType, expiresAt, api.baseUrl());
        api.setToken(token);
        this.session = result;
        saveSession(result);
        VMPingClient.LOGGER.info("标点后端认证成功：{}（{}），token 有效期至 {}", name, authType, expiresAt);
        return result;
    }

    /**
     * 向会话服务器 join，证明"这个 UUID 现在确实登录着"。
     *
     * <p>正版走 Mojang，皮肤站走 {@code {base}/sessionserver/session/minecraft/join}。
     */
    private CompletableFuture<Void> joinSession(String authServer, String accessToken, UUID uuid, String serverId) {
        if (authServer != null && !authServer.isBlank() && validateAuthServer(authServer).isBlank()) {
            return CompletableFuture.failedFuture(new PingApi.ApiException(-1, "bad_session_url",
                    "皮肤站地址必须是 https（本机回环除外）且不含用户信息"));
        }
        String url = authServer == null || authServer.isBlank()
                ? MOJANG_SESSION_SERVER + "/session/minecraft/join"
                : stripTrailingSlash(authServer) + "/sessionserver/session/minecraft/join";

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("selectedProfile", uuid.toString().replace("-", ""));
        body.addProperty("serverId", serverId);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(JOIN_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "VMPing/" + VMPingClient.version())
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                    new PingApi.ApiException(-1, "bad_session_url", "会话服务器地址无效: " + url));
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> {
                    int status = response.statusCode();
                    if (status / 100 == 2) {
                        return;
                    }
                    String message = "会话服务器拒绝了 join（HTTP " + status + "）";
                    if (status == 403) {
                        message = "会话服务器拒绝：登录令牌可能已过期，请重新登录游戏";
                    }
                    throw new CompletionException(new PingApi.ApiException(status, "join_failed", message));
                });
    }

    // ==================== 皮肤站地址探测 ====================

    /**
     * 探测当前客户端使用的 Yggdrasil 皮肤站地址。
     *
     * <p>authlib-injector 以 {@code -javaagent:...authlib-injector.jar=<URL>} 的形式注入，
     * 从中可以直接读出皮肤站 base；没有就是正版。
     *
     * <p>优先相信启动参数：这是启动器实际在用的皮肤站，恶意 config 不能覆盖它去骗取 accessToken。
     */
    public static String detectAuthServer(String override) {
        String fromAgent = detectAuthServerFromJavaagent();
        if (!fromAgent.isBlank()) {
            return fromAgent;
        }
        if (override != null && !override.isBlank()) {
            String validated = validateAuthServer(override);
            if (validated.isBlank()) {
                VMPingClient.LOGGER.warn("配置的皮肤站地址非法（必须是 https 或本机回环 http）：{}", override);
            }
            return validated;
        }
        return "";
    }

    private static String detectAuthServerFromJavaagent() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                if (arg == null || !arg.startsWith("-javaagent:") || !arg.contains("authlib-injector")) {
                    continue;
                }
                int eq = arg.indexOf('=');
                if (eq > 0 && eq + 1 < arg.length()) {
                    String value = arg.substring(eq + 1).trim();
                    String validated = validateAuthServer(value);
                    if (!validated.isBlank()) {
                        return validated;
                    }
                }
            }
        } catch (Throwable t) {
            VMPingClient.LOGGER.debug("探测 authlib-injector 参数失败: {}", t.toString());
        }
        return "";
    }

    /**
     * 校验皮肤站 base：只允许 https（本机回环允许 http），拒绝 userinfo/query/fragment。
     * 非法返回空串。皮肤站地址会接收 Minecraft accessToken，不能把凭据发给任意 URL。
     */
    static String validateAuthServer(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            String host = uri.getHost();
            if (host == null || host.isBlank() || uri.getUserInfo() != null) {
                return "";
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                return "";
            }
            boolean loopback = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
            if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
                return "";
            }
            return stripTrailingSlash(value);
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    // ==================== 会话持久化 ====================

    /** 从磁盘加载上次的 token（后端地址变了就不复用）。 */
    public Session loadSavedSession() {
        try {
            if (!Files.exists(SESSION_FILE)) {
                return null;
            }
            String json = Files.readString(SESSION_FILE, StandardCharsets.UTF_8);
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null) {
                return null;
            }
            String token = PingCodec.optString(object, "token", "");
            String mcUuid = PingCodec.optString(object, "mcUuid", "");
            String mcName = PingCodec.optString(object, "mcName", "");
            String authType = PingCodec.optString(object, "authType", "mojang");
            long expiresAt = PingCodec.optLong(object, "expiresAt", 0L);
            String apiBaseUrl = PingCodec.optString(object, "apiBaseUrl", "");
            if (token.isBlank() || mcUuid.isBlank() || expiresAt <= 0L) {
                return null;
            }
            if (!apiBaseUrl.equalsIgnoreCase(api.baseUrl())) {
                // 换后端了：旧 token 对新后端无效，直接丢弃
                return null;
            }
            Session restored = new Session(token, mcUuid, mcName, authType, expiresAt, apiBaseUrl);
            if (restored.expired()) {
                return null;
            }
            this.session = restored;
            api.setToken(token);
            return restored;
        } catch (Exception e) {
            VMPingClient.LOGGER.debug("读取会话文件失败（忽略）: {}", e.toString());
            return null;
        }
    }

    /** 保存会话（权限 600：token 是凭据，不能让同机其它用户读）。 */
    private void saveSession(Session value) {
        JsonObject object = new JsonObject();
        object.addProperty("token", value.token());
        object.addProperty("mcUuid", value.mcUuid());
        object.addProperty("mcName", value.mcName());
        object.addProperty("authType", value.authType());
        object.addProperty("expiresAt", value.expiresAt());
        object.addProperty("apiBaseUrl", value.apiBaseUrl());
        try {
            Files.createDirectories(SESSION_FILE.getParent());
            Path temp = SESSION_FILE.resolveSibling("session.json.tmp");
            Files.writeString(temp, GSON.toJson(object), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(temp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (Exception ignored) {
                // Windows / 非 POSIX 文件系统没有权限位，忽略
            }
            try {
                Files.move(temp, SESSION_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, SESSION_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VMPingClient.LOGGER.warn("保存会话失败: {}", e.toString());
        }
    }

    /** 清空本地会话（退出登录 / token 失效时）。 */
    public void clearSession() {
        this.session = null;
        api.setToken(null);
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException e) {
            VMPingClient.LOGGER.debug("删除会话文件失败（忽略）: {}", e.toString());
        }
    }
}
