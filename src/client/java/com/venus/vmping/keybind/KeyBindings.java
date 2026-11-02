package com.venus.vmping.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.venus.vmping.VMPingClient;
import com.venus.vmping.gui.PingWheelScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 快捷键注册：**鼠标中键**（默认）按住打开标点轮盘，松开标点。
 *
 * <p>26.3 的 API 与旧版本不同（用官方 jar 的 {@code javap} 核对过）：
 * <ul>
 *   <li>{@code KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)}；</li>
 *   <li>分类从枚举变成 {@code KeyMapping.Category}（record，按 {@link Identifier} 注册）；</li>
 *   <li>Fabric 侧注册入口是
 *       {@code net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper#registerKeyMapping}；
 *       不经过它注册的按键不会出现在「控制 → 按键绑定」里。</li>
 * </ul>
 *
 * <p><b>中键冲突</b>：原版中键是"选取方块"（{@code keyPickItem}）。两个键位同时绑定中键时，
 * 原版会先消费点击 —— 所以轮盘打开期间用 {@code mixin/MinecraftPickBlockMixin} 抑制
 * {@code Minecraft#pickBlockOrEntity}，避免"标点顺便把方块复制到手上"。
 */
public final class KeyBindings {

    /** 按键分类路径；语言文件 key 是 {@code key.category.vmping.main}。 */
    private static final String CATEGORY_PATH = "main";
    /** 按键名称（翻译 key），语言文件 {@code key.vmping.ping}。 */
    private static final String PING_KEY_NAME = "key.vmping.ping";
    /** 默认键：鼠标中键。 */
    private static final int DEFAULT_BUTTON = InputConstants.MOUSE_BUTTON_MIDDLE;

    private static KeyMapping pingMapping;
    private static boolean registered;

    private KeyBindings() {
    }

    /** 注册按键（只在客户端初始化时调用一次）。 */
    public static void register() {
        if (registered) {
            // 热重载 / 开发环境重复初始化时不能重复注册：同一个 Category 注册两次会抛异常
            return;
        }
        registered = true;
        try {
            KeyMapping.Category category = resolveCategory();
            pingMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    PING_KEY_NAME, InputConstants.Type.MOUSE, DEFAULT_BUTTON, category));
        } catch (Throwable t) {
            // 注册失败只影响快捷键，绝不能让客户端起不来（命令 / 界面仍然可用）
            VMPingClient.LOGGER.warn("注册标点快捷键失败: {}", t.toString());
        }
    }

    /**
     * 拿到（或创建）本模组的按键分类。
     *
     * <p>{@code KeyMapping.Category.register} 对同一 ID 重复调用会抛
     * {@link IllegalArgumentException}，所以这里降级成直接构造 —— 分类只是排序用的。
     */
    private static KeyMapping.Category resolveCategory() {
        Identifier id = Identifier.fromNamespaceAndPath(VMPingClient.MOD_ID, CATEGORY_PATH);
        try {
            return KeyMapping.Category.register(id);
        } catch (IllegalArgumentException alreadyRegistered) {
            return new KeyMapping.Category(id);
        }
    }

    /** 每客户端 tick 调用：有按下就打开轮盘。 */
    public static void tick() {
        KeyMapping mapping = pingMapping;
        if (mapping == null) {
            return;
        }
        // 一次性消费掉积压的点击（按住中键不应该排队打开 N 次）
        boolean clicked = false;
        while (mapping.consumeClick()) {
            clicked = true;
        }
        if (!clicked) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        // 已经有界面开着（聊天 / 其它模组界面）时不抢占焦点
        if (minecraft.gui.screen() != null) {
            return;
        }
        PingWheelScreen.open();
    }

    /** 标点键（渲染/命中判断用；未注册时返回 {@code null}）。 */
    public static KeyMapping pingKey() {
        return pingMapping;
    }

    /** 当前绑定键的可读名称（命令提示 / 文档用）。 */
    public static String keyName() {
        KeyMapping mapping = pingMapping;
        if (mapping == null) {
            return "Mouse Middle";
        }
        try {
            Component name = mapping.getTranslatedKeyMessage();
            String text = name == null ? "" : name.getString();
            return text.isBlank() ? "Mouse Middle" : text;
        } catch (Throwable t) {
            return "Mouse Middle";
        }
    }
}
