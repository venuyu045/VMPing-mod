package com.venus.vmping.config;

/**
 * UI 预览器用的 {@code ModConfig} <b>替身</b>。
 *
 * <p>真实类会读写 {@code config/vmping/config.json}，并依赖 Fabric 的配置目录。
 * 预览器不关心落盘，只关心<b>字段默认值</b> —— 因为界面的初始状态完全由这些默认值决定，
 * 差一个字段，预览图上的开关就会和游戏里不一样（这种 bug 最难发现，因为"看起来能跑"）。
 *
 * <p>所以这里的每个字段都与真实 {@code ModConfig} <b>逐条对齐</b>，
 * 改真实类的时候记得同步这里。{@link UI} 常量同理（轮盘半径、边缘箭头边距都在里面）。
 */
public final class ModConfig {

    public static final String DEFAULT_API_BASE_URL = "https://www.venusyu045.cn/api/ping";
    public static final int CURRENT_CONFIG_VERSION = 1;

    public int configVersion = CURRENT_CONFIG_VERSION;
    public String apiBaseUrl = DEFAULT_API_BASE_URL;
    public boolean enabled = true;
    public boolean showOwnPings = true;
    public boolean showOthersPings = true;
    public boolean showDistance = true;
    public boolean showPlayerNames = false;
    public boolean playSound = true;
    public float soundVolume = 0.7f;
    public int maxIconDistance = 96;
    public int pingTtlSeconds = 20;
    public int pollTimeoutSeconds = 20;
    public boolean showEdgeIndicator = true;
    public boolean showStatus = true;
    public String authServer = "";
    public boolean debugLogging = false;
    public boolean degradeToLocal = true;

    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }

    public static ModConfig load() {
        instance = new ModConfig();
        return instance;
    }

    public void save() {
        // 预览器不落盘
    }

    public void sanitize() {
        soundVolume = Math.max(0f, Math.min(1f, soundVolume));
        maxIconDistance = Math.max(8, Math.min(512, maxIconDistance));
    }

    public boolean relayEnabled() {
        return enabled && apiBaseUrl != null && !apiBaseUrl.isBlank();
    }

    public boolean secureBackend() {
        return true;
    }

    /** 界面常量（必须与真实类一致）。 */
    public static final class UI {
        public static final int WHEEL_RADIUS = 78;
        public static final int WHEEL_INNER_RADIUS = 22;
        public static final float SEGMENT_GAP_DEGREES = 3f;
        public static final int WHEEL_BG = 0xB4000000;
        public static final int WHEEL_BORDER = 0x66FFFFFF;
        public static final int WHEEL_HIGHLIGHT = 0x40FFFFFF;
        public static final int WHEEL_TEXT = 0xFFFFFFFF;
        public static final int WHEEL_CENTER_RADIUS = 16;
        public static final double MARKER_SIZE = 0.9d;
        public static final double MARKER_LIFT = 0.05d;
        public static final double BEAM_HEIGHT = 24d;
        public static final int EDGE_INDICATOR_MARGIN = 24;
        public static final int EDGE_INDICATOR_SIZE = 10;
    }
}
