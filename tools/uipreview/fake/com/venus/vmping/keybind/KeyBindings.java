package com.venus.vmping.keybind;

import net.minecraft.client.KeyMapping;

/**
 * UI 预览器用的 {@code KeyBindings} <b>替身</b>。
 *
 * <p>真实类要注册 {@code KeyMapping}（Fabric 的 keymapping API），预览器里没有输入系统，
 * 所以这里返回一个"一直按着"的假按键 —— 轮盘靠它判断"按键松开就取消"，
 * 一直按着才能把轮盘画出来（否则第一帧就被当成松开了）。
 */
public final class KeyBindings {

    /** 假的标点键：始终处于按下状态。 */
    private static final KeyMapping PING_KEY = new KeyMapping("key.vmping.ping", true);

    private KeyBindings() {
    }

    public static void register() {
        // 预览器不注册按键
    }

    public static void tick() {
        // 预览器不消费点击
    }

    public static KeyMapping pingKey() {
        return PING_KEY;
    }

    public static String keyName() {
        return "Mouse Middle";
    }
}
