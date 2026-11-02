package com.venus.vmping;

import com.venus.vmping.config.ModConfig;
import com.venus.vmping.core.PingData;
import com.venus.vmping.net.PingRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * UI 预览器用的 {@code VMPingClient} <b>替身</b>。
 *
 * <p>真实入口类 {@code implements ClientModInitializer}，会牵进整条 Fabric 启动链
 * （注册命令、快捷键、HUD、网络事件），在预览器里根本跑不起来。
 * 这里只保留界面会用到的 getter，行为与真实类一致（单例、同一个 relay 实例）。
 */
public final class VMPingClient {

    public static final String MOD_ID = "vmping";
    public static final String MOD_NAME = "Venus Mc Ping";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final VMPingClient INSTANCE = new VMPingClient();

    private final PingRelay relay = new PingRelay();
    private ModConfig config = ModConfig.get();

    public static VMPingClient getInstance() {
        return INSTANCE;
    }

    public static PingRelay relay() {
        return INSTANCE.relay;
    }

    public static String version() {
        return "0.1.0";
    }

    public ModConfig config() {
        return config;
    }

    public void applyConfig(ModConfig newConfig) {
        this.config = newConfig;
    }

    public void onLocalPing(PingData ping) {
        // 预览器不播音效
    }

    public void onRemotePings(List<PingData> received) {
        // 预览器不播音效
    }
}
