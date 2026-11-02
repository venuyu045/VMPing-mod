package com.venus.vmping.uipreview;

import com.venus.vmping.VMPingClient;
import com.venus.vmping.config.ModConfig;
import com.venus.vmping.gui.PingWheelScreen;
import com.venus.vmping.gui.SettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import uipreview.UiPreviewAdapter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VMPing 的 UI 预览适配器 —— 预览器内核与本模组之间唯一的接缝。
 *
 * <p>它告诉内核三件事：<b>语言文件在哪</b>（{@link #modId()}）、
 * <b>要预览哪个界面</b>（{@link #createScreen(String)}）、
 * <b>有哪些场景</b>（{@link #scenarios()}）。
 * 界面本身的布局与绘制全部走生产代码（{@link PingWheelScreen} / {@link SettingsScreen}），
 * 预览器不参与任何布局计算 —— 所以预览图上量到的间距就是游戏里会出现的间距。
 *
 * <p>跑法：
 * <pre>
 *   bash tools/uipreview/run.sh --all --report
 * </pre>
 */
public final class VmpingAdapter implements UiPreviewAdapter {

    @Override
    public String modId() {
        return "vmping";
    }

    @Override
    public String displayName() {
        return "Venus Mc Ping";
    }

    @Override
    public Screen createScreen() {
        return new PingWheelScreen();
    }

    @Override
    public Screen createScreen(String scenario) {
        if (scenario != null && scenario.startsWith("settings")) {
            // parent = null：设置界面点"取消"直接回游戏（预览器里没有父界面）
            return new SettingsScreen(null);
        }
        return new PingWheelScreen();
    }

    @Override
    public void installFixtures(String scenario, Path workDir) {
        // 每个场景都从"默认配置 + 空仓库"开始，避免上一个场景的状态串到下一个
        ModConfig config = ModConfig.get();
        ModConfig defaults = new ModConfig();
        config.enabled = defaults.enabled;
        config.showOwnPings = defaults.showOwnPings;
        config.showOthersPings = defaults.showOthersPings;
        config.showDistance = defaults.showDistance;
        config.showPlayerNames = defaults.showPlayerNames;
        config.playSound = defaults.playSound;
        config.showEdgeIndicator = defaults.showEdgeIndicator;
        config.showStatus = defaults.showStatus;
        config.soundVolume = defaults.soundVolume;
        config.maxIconDistance = defaults.maxIconDistance;
        config.apiBaseUrl = defaults.apiBaseUrl;
        VMPingClient.relay().store().clear();
    }

    @Override
    public Map<String, Scenario> scenarios() {
        Map<String, Scenario> scenarios = new LinkedHashMap<>();
        // 轮盘：鼠标停在"危险"扇区（扇区 1，角度 108°）
        scenarios.put("default", Scenario.of("default", "轮盘（选中·危险）", screen -> { },
                (screen, width, height) -> wheelPoint(width, height, 108d, 50)));
        // 轮盘：鼠标在中心死区（不选中任何类型）
        scenarios.put("wheel_center", Scenario.of("wheel_center", "轮盘（未选中）", screen -> { },
                (screen, width, height) -> new int[]{width / 2, height / 2}));
        // 轮盘：选中"求助"（扇区 4，角度 324°）
        scenarios.put("wheel_help", Scenario.of("wheel_help", "轮盘（选中·求助）", screen -> { },
                (screen, width, height) -> wheelPoint(width, height, 324d, 52)));
        // 设置界面：默认状态
        scenarios.put("settings", Scenario.of("settings", "设置（默认）", screen -> { }));
        // 设置界面：鼠标悬停在开关上
        scenarios.put("settings_hover", Scenario.of("settings_hover", "设置（悬停）", screen -> { },
                (screen, width, height) -> new int[]{width / 2 - 80, height / 2 - 40}));
        return scenarios;
    }

    @Override
    public Map<String, Object> describeState(Screen screen) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("screen", screen == null ? "-" : screen.getClass().getSimpleName());
        state.put("enabled", ModConfig.get().enabled);
        state.put("wheelSelected", screen instanceof PingWheelScreen wheel ? wheel.selectedSector() : -1);
        return state;
    }

    /**
     * 轮盘上某个角度、某个半径处的屏幕坐标。
     *
     * <p>轮盘的角度约定是"0° = 正上、顺时针"，与数学上的 atan2 不同，所以这里要换算：
     * {@code dx = sin(θ)·r}、{@code dy = -cos(θ)·r}（屏幕 y 向下）。
     */
    private static int[] wheelPoint(int width, int height, double degrees, double radius) {
        double radians = Math.toRadians(degrees);
        int x = (int) Math.round(width / 2.0 + Math.sin(radians) * radius);
        int y = (int) Math.round(height / 2.0 - Math.cos(radians) * radius);
        return new int[]{x, y};
    }
}
