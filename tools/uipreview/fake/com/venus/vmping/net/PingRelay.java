package com.venus.vmping.net;

import com.venus.vmping.core.PingData;
import com.venus.vmping.core.PingKind;
import com.venus.vmping.core.PingStore;

import java.util.UUID;

/**
 * UI 预览器用的 {@code PingRelay} <b>替身</b>。
 *
 * <p>真实中继要开 HTTP、做认证、跑长轮询 —— 预览器里这些都换成内存状态：
 * {@link PingStore} 用的是<b>真实类</b>（它是纯逻辑，没有 MC 依赖），
 * 所以"标点怎么存、怎么过期、怎么排序"验证的是生产代码本身。
 *
 * <p>只保留界面真正用到的部分：状态、房间、维度、仓库。
 */
public final class PingRelay {

    /** 状态机（与真实类一致）。 */
    public enum State {
        OFF,
        AUTHENTICATING,
        ONLINE,
        DEGRADED
    }

    private final PingStore store = new PingStore();
    private State state = State.ONLINE;
    private String roomId = "demo-room";
    private String dimension = "minecraft:overworld";
    private String lastErrorKey = "";

    public PingStore store() {
        return store;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String roomId() {
        return roomId;
    }

    public String dimension() {
        return dimension;
    }

    public String lastErrorKey() {
        return lastErrorKey;
    }

    public String lastErrorDetail() {
        return "";
    }

    public boolean online() {
        return state == State.ONLINE;
    }

    public void reload(com.venus.vmping.config.ModConfig config) {
        // 预览器不做网络
    }

    public void start(com.venus.vmping.config.ModConfig config) {
        // 预览器不做网络
    }

    public void stop() {
        // 预览器不做网络
    }

    public void tick() {
        // 预览器不做网络
    }

    /** 发标点：预览器里直接塞进仓库（等价于"在线且成功"）。 */
    public PingData sendPing(double x, double y, double z, PingKind kind, UUID targetUuid, String targetName) {
        PingData data = PingData.local(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "You", dimension, x, y, z, kind, targetUuid, targetName,
                System.currentTimeMillis(), 20_000L);
        store.put(data);
        return data;
    }
}
