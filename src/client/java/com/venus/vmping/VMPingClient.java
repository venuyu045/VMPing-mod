package com.venus.vmping;

import com.venus.vmping.command.PingCommand;
import com.venus.vmping.config.ModConfig;
import com.venus.vmping.core.ClientContext;
import com.venus.vmping.core.PingData;
import com.venus.vmping.hud.StatusOverlay;
import com.venus.vmping.keybind.KeyBindings;
import com.venus.vmping.net.PingRelay;
import com.venus.vmping.render.CameraCapture;
import com.venus.vmping.render.PingRenderer;
import com.venus.vmping.util.SoundPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Venus Mc Ping —— 客户端模组入口。
 *
 * <p>启动顺序：配置 → 快捷键 / 命令 → 事件注册（进服启动中继、退服停止、每 tick 驱动）。
 *
 * <p>纯客户端模组（{@code environment: "client"}）：不注册任何服务端 entrypoint，
 * 也不向服务器发自定义载荷 —— 标点全部走 HTTP 中继，所以**服务器装不装 mod 都能用**。
 */
public class VMPingClient implements ClientModInitializer {

    public static final String MOD_ID = "vmping";
    public static final String MOD_NAME = "Venus Mc Ping";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static VMPingClient instance;
    private static String cachedVersion = "0.0.0";

    private volatile ModConfig config;
    private final PingRelay relay = new PingRelay();

    public static VMPingClient getInstance() {
        return instance;
    }

    /** 全局中继（渲染/命令/界面都从这里取状态）。 */
    public static PingRelay relay() {
        return instance == null ? new PingRelay() : instance.relay;
    }

    /** 模组版本（HTTP User-Agent / 诊断用），来自 fabric.mod.json 的版本字段。 */
    public static String version() {
        return cachedVersion;
    }

    public ModConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        cachedVersion = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
        LOGGER.info("[{}] 正在初始化（v{}）...", MOD_NAME, cachedVersion);

        // 配置（缺失字段自动用默认值，兼容旧 config.json）
        config = ModConfig.load();

        // 快捷键（默认鼠标中键）+ 客户端命令
        KeyBindings.register();
        PingCommand.register();

        // 进服：启动中继；退服：停止并清空
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> relay.start(config));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            relay.stop();
            // 退服时丢掉上一局的相机矩阵与状态角标，避免下次进服画出"幽灵标记"
            CameraCapture.invalidate();
            StatusOverlay.reset();
        });

        // 每 tick：快捷键 → 打开轮盘；中继 → 清理过期标点 / 重试
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyBindings.tick();
            // 兜底：JOIN 事件在极少数环境（回放 / 某些代理）不会触发，这里在"已经进世界
            // 但中继还没启动"时补一次。start() 自己会做单机/局域网判断，不会误发请求。
            if (!relay.isStarted() && config.enabled && client.player != null
                    && !ClientContext.roomId().isBlank()) {
                relay.start(config);
            }
            relay.tick();
        });

        // HUD：世界标点（屏幕空间投影）+ 连接状态角标。
        // 26.3 没有 WorldRenderEvents，但 fabric-rendering-v1 提供了 HudElementRegistry，
        // 注册到原版 HUD 的最后（画在最上层），每帧由 Gui 统一调用。
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "pings"),
                (graphics, deltaTracker) -> {
                    PingRenderer.render(graphics, deltaTracker);
                    StatusOverlay.render(graphics);
                });

        // 退出游戏：保存配置
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (config != null) {
                config.save();
            }
        });

        LOGGER.info("[{}] 初始化完成：后端={}，快捷键={}", MOD_NAME,
                config.apiBaseUrl.isBlank() ? "本地模式" : config.apiBaseUrl, KeyBindings.keyName());
    }

    /** 配置热更新（{@code /vmping config reload}）。 */
    public void applyConfig(ModConfig newConfig) {
        this.config = newConfig;
        relay.reload(newConfig);
        LOGGER.info("配置已重载：后端={}，开关={}", newConfig.apiBaseUrl, newConfig.enabled);
    }

    /** 自己发了一个标点（本地立即渲染之后的回调）：播音效 + 可选动作栏提示。 */
    public void onLocalPing(PingData ping) {
        SoundPlayer.playOwnPing(ping == null ? null : ping.kind());
    }

    /** 收到别人新标点（长轮询回调，网络线程）：播音效（音效播放内部会切回主线程）。 */
    public void onRemotePings(List<PingData> received) {
        if (received == null || received.isEmpty()) {
            return;
        }
        if (!config.showOthersPings) {
            return;
        }
        // 只对"最新的那一条"响一声，一次收到 10 条不响 10 次
        PingData latest = received.get(received.size() - 1);
        SoundPlayer.playRemotePing(latest.kind());
    }
}
