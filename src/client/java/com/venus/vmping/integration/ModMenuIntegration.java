package com.venus.vmping.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.venus.vmping.gui.SettingsScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mod Menu 集成（可选依赖）。
 *
 * <p>Mod Menu 是 {@code compileOnly} 依赖：玩家不装 Mod Menu 也能正常玩，
 * 只有装了才会按 {@code fabric.mod.json} 的 {@code modmenu} entrypoint 加载本类。
 * 所以这个类<b>只能</b>引用 Mod Menu 的 API 和 Minecraft 的类，不能反向被主入口静态依赖，
 * 否则没装 Mod Menu 的玩家会 {@code NoClassDefFoundError}。
 *
 * <p>点击模组列表里的「配置」按钮时，Mod Menu 会把当前的模组列表界面当作 parent 传进来，
 * 玩家在设置界面按 ESC / 点「取消」会回到那里，而不是直接回游戏。
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // SettingsScreen.createScreen(parent) —— 显式转换是因为 ConfigScreenFactory 是泛型接口
        return (ConfigScreenFactory<Screen>) SettingsScreen::createScreen;
    }
}
