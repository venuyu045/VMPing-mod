package com.venus.vmping.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.venus.vmping.VMPingClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 模组配置（{@code config/vmping/config.json}）。
 *
 * <p>设计原则：
 * <ul>
 *   <li><b>字段缺失 = 用默认值</b>：老版本配置升级后不会因为少字段变成 0/false；</li>
 *   <li><b>写盘用临时文件 + 原子替换</b>：写一半崩溃不会把配置写坏；</li>
 *   <li><b>解析失败回退默认并备份坏文件</b>：玩家手改坏 JSON 时游戏照常能进。</li>
 * </ul>
 *
 * <p>UI 常量集中在 {@link UI}：轮盘半径、扇区颜色等都在这里，界面和渲染共用同一份。
 */
public final class ModConfig {

    /** 官方中继后端（与 VMFashion 共用同一台服务器，不同路由）。 */
    public static final String DEFAULT_API_BASE_URL = "https://www.venusyu045.cn/api/ping";
    /** 配置文件版本：将来改结构时用来做迁移。 */
    public static final int CURRENT_CONFIG_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("vmping").resolve("config.json");

    // ==================== 配置字段（公开，Gson 直接读写） ====================

    /** 配置结构版本。 */
    public int configVersion = CURRENT_CONFIG_VERSION;

    /** 中继后端地址；留空 = 纯本地模式（标点只自己可见）。 */
    public String apiBaseUrl = DEFAULT_API_BASE_URL;

    /** 总开关：关掉后不显示轮盘、不发标点。 */
    public boolean enabled = true;

    /** 是否显示自己的标点。 */
    public boolean showOwnPings = true;

    /** 是否显示别人的标点。 */
    public boolean showOthersPings = true;

    /** 是否显示距离数字。 */
    public boolean showDistance = true;

    /** 是否显示标点者名字（按住 Tab 玩家列表时强制显示）。 */
    public boolean showPlayerNames = false;

    /** 是否播放提示音。 */
    public boolean playSound = true;

    /** 提示音音量（0~1）。 */
    public float soundVolume = 0.7f;

    /** 超过这个距离（格）不再画图标，只画屏幕边缘箭头。 */
    public int maxIconDistance = 96;

    /** 标点本地显示时长（秒）；服务端 TTL 是 20s，客户端不会显示得更久。 */
    public int pingTtlSeconds = 20;

    /** 长轮询等待时长（秒），服务端上限 25。 */
    public int pollTimeoutSeconds = 20;

    /** 是否显示屏幕边缘方向箭头。 */
    public boolean showEdgeIndicator = true;

    /** 是否在 HUD 上显示连接状态（认证失败/降级时给个提示）。 */
    public boolean showStatus = true;

    /** 皮肤站地址覆盖（authlib-injector 探测失败时手填；正版留空）。 */
    public String authServer = "";

    /** 调试日志：把每次请求/响应摘要打进日志。 */
    public boolean debugLogging = false;

    /** 中继不可用时是否自动降级为纯本地标点。 */
    public boolean degradeToLocal = true;

    // ==================== 读取 / 保存 ====================

    /** 当前生效的配置（内存单例）。 */
    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** 从磁盘读配置；文件不存在/损坏时返回默认配置（损坏文件会被改名备份）。 */
    public static ModConfig load() {
        ModConfig config = new ModConfig();
        try {
            if (!Files.exists(CONFIG_PATH)) {
                config.save();
                return config;
            }
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null) {
                throw new IOException("配置内容为空");
            }
            ModConfig parsed = GSON.fromJson(object, ModConfig.class);
            if (parsed == null) {
                throw new IOException("配置解析结果为空");
            }
            parsed.sanitize();
            instance = parsed;
            return parsed;
        } catch (Exception e) {
            VMPingClient.LOGGER.warn("读取配置失败，使用默认配置: {}", e.toString());
            backupBroken();
            config.sanitize();
            config.save();
            instance = config;
            return config;
        }
    }

    /** 保存到磁盘（原子替换）。 */
    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Path temp = CONFIG_PATH.resolveSibling("config.json.tmp");
            Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(temp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VMPingClient.LOGGER.warn("保存配置失败: {}", e.toString());
        }
    }

    /** 配置文件路径（诊断命令用）。 */
    public static Path configPath() {
        return CONFIG_PATH;
    }

    /** 把越界值拉回合法区间（手改配置写错了也不至于让界面炸掉）。 */
    public void sanitize() {
        if (apiBaseUrl == null) {
            apiBaseUrl = "";
        }
        apiBaseUrl = apiBaseUrl.trim();
        while (apiBaseUrl.endsWith("/")) {
            apiBaseUrl = apiBaseUrl.substring(0, apiBaseUrl.length() - 1);
        }
        soundVolume = Math.max(0f, Math.min(1f, soundVolume));
        maxIconDistance = Math.max(8, Math.min(512, maxIconDistance));
        pingTtlSeconds = Math.max(3, Math.min(20, pingTtlSeconds));
        pollTimeoutSeconds = Math.max(5, Math.min(25, pollTimeoutSeconds));
        if (authServer == null) {
            authServer = "";
        }
        if (configVersion <= 0) {
            configVersion = CURRENT_CONFIG_VERSION;
        }
    }

    /** 是否启用中继（总开关 + 配了后端地址）。 */
    public boolean relayEnabled() {
        return enabled && apiBaseUrl != null && !apiBaseUrl.isBlank();
    }

    /** 后端地址是否是 https（或本机回环 http）—— 凭据只发给这种地址。 */
    public boolean secureBackend() {
        return isSecureUrl(apiBaseUrl);
    }

    /**
     * URL 安全判定：https，或本机回环的 http。
     *
     * <p>为什么要单独判定：认证请求里带的是 Minecraft accessToken，明文 http 等于把账号交给中间人。
     */
    public static boolean isSecureUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            String host = uri.getHost();
            if (host == null || host.isBlank() || uri.getUserInfo() != null) {
                return false;
            }
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "::1".equals(host) || "[::1]".equals(host);
            return "https".equals(scheme) || ("http".equals(scheme) && loopback);
        } catch (Exception e) {
            return false;
        }
    }

    private static void backupBroken() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Path backup = CONFIG_PATH.resolveSibling("config.broken.json");
                Files.move(CONFIG_PATH, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // 备份失败不影响主流程
        }
    }

    /** 界面常量 —— 轮盘与 HUD 共用，改一处即可。 */
    public static final class UI {
        /** 轮盘外半径（逻辑像素）。 */
        public static final int WHEEL_RADIUS = 78;
        /** 轮盘内半径（中心死区，鼠标在里面算"未选中"）。 */
        public static final int WHEEL_INNER_RADIUS = 22;
        /** 扇区之间的间隙（角度，度）。 */
        public static final float SEGMENT_GAP_DEGREES = 3f;
        /** 轮盘背景色（半透明黑）。 */
        public static final int WHEEL_BG = 0xB4000000;
        /** 轮盘边框色。 */
        public static final int WHEEL_BORDER = 0x66FFFFFF;
        /** 选中扇区的高亮色（白色半透明）。 */
        public static final int WHEEL_HIGHLIGHT = 0x40FFFFFF;
        /** 中心提示文字色。 */
        public static final int WHEEL_TEXT = 0xFFFFFFFF;
        /** 轮盘中心圆半径。 */
        public static final int WHEEL_CENTER_RADIUS = 16;
        /** 世界标记的图标边长（格）。 */
        public static final double MARKER_SIZE = 0.9d;
        /** 标记从地面抬起的高度（格）。 */
        public static final double MARKER_LIFT = 0.05d;
        /** 信标光柱高度（格）。 */
        public static final double BEAM_HEIGHT = 24d;
        /** 屏幕边缘指示箭头的边距（像素）。 */
        public static final int EDGE_INDICATOR_MARGIN = 24;
        /** 边缘箭头的尺寸（像素）。 */
        public static final int EDGE_INDICATOR_SIZE = 10;
    }
}
