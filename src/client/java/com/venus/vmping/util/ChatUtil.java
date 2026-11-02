package com.venus.vmping.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 聊天栏 / 动作栏提示工具。
 *
 * <p>约定（与 VMFashion 保持一致）：玩家可见文案一律用 {@link Component#translatable} 传进来，
 * 语言文件里写译文；Java 里不要拼中文，日志除外（日志是开发者看的）。
 */
public final class ChatUtil {

    /** 聊天栏前缀。 */
    public static final String PREFIX = "[VMPing] ";

    private ChatUtil() {
    }

    /** 给文案加统一前缀。 */
    public static Component prefixed(Component message) {
        Component body = message == null ? Component.empty() : message;
        return Component.literal(PREFIX).append(body);
    }

    /** 显示一条仅客户端可见的聊天栏消息（可翻译文案）。 */
    public static void showMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        Component line = prefixed(message);
        client.execute(() -> client.player.sendSystemMessage(line));
    }

    /** 显示一条动作栏消息（可翻译文案）。 */
    public static void showActionBar(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        Component line = message == null ? Component.empty() : message;
        client.execute(() -> client.player.sendOverlayMessage(line));
    }

    /** 调试日志用：把玩家可见 key 之外的原始异常文本打到聊天栏（仅 /vmping debug 时调用）。 */
    public static void showRaw(String text) {
        showMessage(Component.literal(text == null ? "" : text));
    }
}
