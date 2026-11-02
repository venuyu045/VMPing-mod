package com.venus.vmping.gui;

import com.venus.vmping.VMPingClient;
import com.venus.vmping.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 设置界面 —— 全部用<b>原版控件</b>（{@code Button} / {@code AbstractSliderButton} / {@code EditBox}），
 * 不自己造轮子。
 *
 * <p>为什么用原版控件而不是自绘：原版控件自带键盘导航、叙述（无障碍）、
 * 鼠标拖拽与音效，自绘一套既费事又容易被玩家吐槽"手感不对"；
 * 我们的界面只有 8 个开关 + 2 个滑条 + 1 个输入框，原版控件完全够用。
 *
 * <p>交互约定：
 * <ul>
 *   <li><b>完成</b>：把控件上的值写回 {@link ModConfig} → 存盘 → 调
 *       {@code VMPingClient#applyConfig} <b>热生效</b>（不用重进游戏/重启客户端）；</li>
 *   <li><b>取消</b>：丢弃本次改动（控件只改本界面内的副本，不动 {@code ModConfig} 单例）；</li>
 *   <li><b>恢复默认</b>：把控件重置成默认值（仍需点"完成"才生效）。</li>
 * </ul>
 *
 * <p>布局是<b>流式</b>的：先按内容算出面板高度，再垂直居中；
 * 窗口大小 / GUI 缩放变化时 {@link #init()} 会被重新调用，控件按字段里的当前值重建，
 * 所以"改了一半再拖窗口"不会丢改动。
 */
public final class SettingsScreen extends Screen {

    /** 面板最大宽度（逻辑像素）。 */
    private static final int PANEL_MAX_WIDTH = 340;
    /** 面板内边距。 */
    private static final int PADDING = 10;
    /** 控件行高。 */
    private static final int ROW_HEIGHT = 20;
    /** 行间距。 */
    private static final int ROW_GAP = 4;
    /** 分区之间的额外间距。 */
    private static final int SECTION_GAP = 10;
    /** 面板标题区高度。 */
    private static final int TITLE_HEIGHT = 18;
    /** URL 行额外留给分区标题的高度。 */
    private static final int LABEL_HEIGHT = 11;

    /** 遮罩色（半透明黑，压暗后面的世界/主菜单）。 */
    private static final int OVERLAY = 0x99000000;
    /** 面板底色。 */
    private static final int PANEL_BG = 0xF0101010;
    /** 面板边框色。 */
    private static final int PANEL_BORDER = 0xFF3C3C3C;
    /** 分区标题颜色。 */
    private static final int SECTION_COLOR = 0xFF9AD1FF;
    /** 普通文字颜色。 */
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    /** 返回用（ESC / 完成 / 取消都回到这里；{@code null} = 回游戏）。 */
    private final Screen parent;

    // ==================== 布局度量（init 时按窗口高度自适应选一组） ====================
    /** 控件行高。 */
    private int rowHeight = ROW_HEIGHT;
    /** 行间距。 */
    private int rowGap = ROW_GAP;
    /** 分区间距。 */
    private int sectionGap = SECTION_GAP;
    /** 分区标题占用的高度。 */
    private int labelHeight = LABEL_HEIGHT;

    // ==================== 面板几何（init 时算好，渲染直接用） ====================
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int toggleTop;
    private int slidersTop;
    private int urlTop;

    // ==================== 控件 ====================
    private EditBox apiUrlBox;
    private ValueSlider volumeSlider;
    private ValueSlider distanceSlider;

    // ==================== 待保存的值（控件改这里，点"完成"才写回配置） ====================
    private boolean enabled;
    private boolean showOwnPings;
    private boolean showOthersPings;
    private boolean showDistance;
    private boolean showPlayerNames;
    private boolean playSound;
    private boolean showEdgeIndicator;
    private boolean showStatus;
    /** 音量百分比 0..100。 */
    private int volumePercent;
    /** 图标最远距离（格）。 */
    private int maxIconDistance;
    /** 后端地址（输入框原文）。 */
    private String apiUrl = "";

    /**
     * @param parent 父界面（Mod Menu 传模组列表；{@code null} 表示直接回游戏）
     */
    public SettingsScreen(Screen parent) {
        super(Component.translatable("vmping.settings.title"));
        this.parent = parent;
        loadFromConfig(ModConfig.get());
    }

    /** Mod Menu 的工厂方法（{@code ConfigScreenFactory} 需要一个 {@code Screen}）。 */
    public static Screen createScreen(Screen parent) {
        return new SettingsScreen(parent);
    }

    /** 从配置读一遍待保存值。 */
    private void loadFromConfig(ModConfig config) {
        this.enabled = config.enabled;
        this.showOwnPings = config.showOwnPings;
        this.showOthersPings = config.showOthersPings;
        this.showDistance = config.showDistance;
        this.showPlayerNames = config.showPlayerNames;
        this.playSound = config.playSound;
        this.showEdgeIndicator = config.showEdgeIndicator;
        this.showStatus = config.showStatus;
        this.volumePercent = Math.round(config.soundVolume * 100f);
        this.maxIconDistance = config.maxIconDistance;
        this.apiUrl = config.apiBaseUrl == null ? "" : config.apiBaseUrl;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        // 窗口太矮（GUI 缩放很大 / 小窗口）时自动压缩行高与间距，保证整个面板放得下
        int[] metrics = pickMetrics(this.height - 12);
        this.rowHeight = metrics[0];
        this.rowGap = metrics[1];
        this.sectionGap = metrics[2];
        this.labelHeight = metrics[3];

        int columnGap = 6;
        int panelW = Math.min(PANEL_MAX_WIDTH, Math.max(220, this.width - 24));
        int contentWidth = panelW - PADDING * 2;
        int columnWidth = (contentWidth - columnGap) / 2;

        // 先算总高度：4 行开关 + 2 个滑条 + URL（含分区标题）+ 底部按钮
        int toggleRows = 4;
        int togglesHeight = toggleRows * rowHeight + (toggleRows - 1) * rowGap;
        int slidersHeight = 2 * rowHeight + rowGap;
        int urlHeight = labelHeight + rowHeight;
        int contentHeight = labelHeight + togglesHeight
                + sectionGap + labelHeight + slidersHeight
                + sectionGap + urlHeight
                + sectionGap + rowHeight;
        int panelH = TITLE_HEIGHT + PADDING * 2 + contentHeight;

        this.panelWidth = panelW;
        this.panelHeight = panelH;
        this.panelX = (this.width - panelW) / 2;
        this.panelY = Math.max(6, (this.height - panelH) / 2);

        int contentLeft = panelX + PADDING;
        int cursorY = panelY + TITLE_HEIGHT + PADDING + labelHeight;

        // ---------- 开关（2 列 × 4 行） ----------
        this.toggleTop = cursorY;
        addToggle(contentLeft, cursorY, columnWidth, "vmping.settings.enabled",
                this.enabled, value -> this.enabled = value);
        addToggle(contentLeft + columnWidth + columnGap, cursorY, columnWidth, "vmping.settings.show_own",
                this.showOwnPings, value -> this.showOwnPings = value);
        cursorY += rowHeight + rowGap;

        addToggle(contentLeft, cursorY, columnWidth, "vmping.settings.show_others",
                this.showOthersPings, value -> this.showOthersPings = value);
        addToggle(contentLeft + columnWidth + columnGap, cursorY, columnWidth, "vmping.settings.show_distance",
                this.showDistance, value -> this.showDistance = value);
        cursorY += rowHeight + rowGap;

        addToggle(contentLeft, cursorY, columnWidth, "vmping.settings.show_names",
                this.showPlayerNames, value -> this.showPlayerNames = value);
        addToggle(contentLeft + columnWidth + columnGap, cursorY, columnWidth, "vmping.settings.play_sound",
                this.playSound, value -> this.playSound = value);
        cursorY += rowHeight + rowGap;

        addToggle(contentLeft, cursorY, columnWidth, "vmping.settings.show_edge",
                this.showEdgeIndicator, value -> this.showEdgeIndicator = value);
        addToggle(contentLeft + columnWidth + columnGap, cursorY, columnWidth, "vmping.settings.show_status",
                this.showStatus, value -> this.showStatus = value);
        cursorY += rowHeight + sectionGap + labelHeight;

        // ---------- 滑条 ----------
        this.slidersTop = cursorY;
        this.volumeSlider = new ValueSlider(contentLeft, cursorY, contentWidth, rowHeight,
                "vmping.settings.volume", 0, 100, this.volumePercent, "%",
                value -> this.volumePercent = value);
        this.addRenderableWidget(this.volumeSlider);
        cursorY += rowHeight + rowGap;

        this.distanceSlider = new ValueSlider(contentLeft, cursorY, contentWidth, rowHeight,
                "vmping.settings.max_distance", 8, 256, this.maxIconDistance, "vmping.hud.distance.unit",
                value -> this.maxIconDistance = value);
        this.addRenderableWidget(this.distanceSlider);
        cursorY += rowHeight + sectionGap + labelHeight;

        // ---------- 后端地址 ----------
        this.urlTop = cursorY;
        this.apiUrlBox = new EditBox(this.font, contentLeft, cursorY, contentWidth, rowHeight,
                Component.translatable("vmping.settings.api_url"));
        this.apiUrlBox.setMaxLength(200);
        this.apiUrlBox.setValue(this.apiUrl);
        this.apiUrlBox.setHint(Component.translatable("vmping.settings.api_url.hint"));
        // 输入过程中就同步到字段，窗口 resize 重建控件时不丢内容
        this.apiUrlBox.setResponder(value -> this.apiUrl = value == null ? "" : value);
        this.addRenderableWidget(this.apiUrlBox);
        cursorY += urlHeight + sectionGap;

        // ---------- 底部按钮 ----------
        int buttonGap = 6;
        int buttonWidth = (contentWidth - buttonGap * 2) / 3;
        Button done = Button.builder(Component.translatable("vmping.settings.done"),
                        button -> saveAndClose())
                .bounds(contentLeft, cursorY, buttonWidth, rowHeight)
                .build();
        Button cancel = Button.builder(Component.translatable("vmping.settings.cancel"),
                        button -> onClose())
                .bounds(contentLeft + buttonWidth + buttonGap, cursorY, buttonWidth, rowHeight)
                .build();
        Button defaults = Button.builder(Component.translatable("vmping.settings.defaults"),
                        button -> resetToDefaults())
                .bounds(contentLeft + (buttonWidth + buttonGap) * 2, cursorY, buttonWidth, rowHeight)
                .build();
        this.addRenderableWidget(done);
        this.addRenderableWidget(cancel);
        this.addRenderableWidget(defaults);
    }

    /**
     * 建一个"开关"按钮：文字形如「显示自己的标点：开」。
     *
     * <p>为什么不用 {@code CycleButton}：它的视觉是「< 值 >」的翻页样式，
     * 对布尔开关来说"开/关"两个词直接显示更好读，玩家一眼能看出当前状态。
     */
    private void addToggle(int x, int y, int width, String labelKey, boolean initial,
                           Consumer<Boolean> setter) {
        boolean[] state = {initial};
        Button[] holder = new Button[1];
        holder[0] = Button.builder(toggleLabel(labelKey, state[0]), button -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            holder[0].setMessage(toggleLabel(labelKey, state[0]));
        }).bounds(x, y, width, ROW_HEIGHT).build();
        this.addRenderableWidget(holder[0]);
    }

    /** 「标签：开/关」。 */
    private static Component toggleLabel(String labelKey, boolean on) {
        return Component.translatable("vmping.settings.toggle",
                Component.translatable(labelKey),
                Component.translatable(on ? "vmping.settings.on" : "vmping.settings.off"));
    }

    /**
     * 选一组能在给定高度里放下的布局度量。
     *
     * <p>候选从"最宽松"到"最紧凑"排列，取第一个放得下的；都放不下就用最紧凑的
     * （此时面板会略微超出窗口，但控件仍然可见、可点 —— 总比把"完成"按钮挤出屏幕好）。
     *
     * @return {@code {行高, 行间距, 分区间距, 分区标题高度}}
     */
    private static int[] pickMetrics(int availableHeight) {
        int[][] candidates = {
                {20, 4, 10, 11},
                {18, 3, 8, 10},
                {16, 2, 6, 9},
                {14, 2, 4, 9},
        };
        for (int[] candidate : candidates) {
            if (layoutHeight(candidate[0], candidate[1], candidate[2], candidate[3]) <= availableHeight) {
                return candidate;
            }
        }
        return candidates[candidates.length - 1];
    }

    /** 按给定度量算整个面板的高度（与 {@link #init()} 里的算法必须一致）。 */
    private static int layoutHeight(int rowHeight, int rowGap, int sectionGap, int labelHeight) {
        int togglesHeight = 4 * rowHeight + 3 * rowGap;
        int slidersHeight = 2 * rowHeight + rowGap;
        int urlHeight = labelHeight + rowHeight;
        int contentHeight = labelHeight + togglesHeight
                + sectionGap + labelHeight + slidersHeight
                + sectionGap + urlHeight
                + sectionGap + rowHeight;
        return TITLE_HEIGHT + PADDING * 2 + contentHeight;
    }

    /** 恢复默认值（只改本界面字段与控件，点"完成"才写回配置）。 */
    private void resetToDefaults() {
        ModConfig defaults = new ModConfig();
        this.enabled = defaults.enabled;
        this.showOwnPings = defaults.showOwnPings;
        this.showOthersPings = defaults.showOthersPings;
        this.showDistance = defaults.showDistance;
        this.showPlayerNames = defaults.showPlayerNames;
        this.playSound = defaults.playSound;
        this.showEdgeIndicator = defaults.showEdgeIndicator;
        this.showStatus = defaults.showStatus;
        this.volumePercent = Math.round(defaults.soundVolume * 100f);
        this.maxIconDistance = defaults.maxIconDistance;
        this.apiUrl = defaults.apiBaseUrl;
        // 重建控件：init() 会按上面这些字段重新生成（滑条位置、开关文字、输入框内容）
        this.rebuildWidgets();
    }

    /** 写回配置 + 存盘 + 热生效 + 关闭。 */
    private void saveAndClose() {
        ModConfig config = ModConfig.get();
        config.enabled = this.enabled;
        config.showOwnPings = this.showOwnPings;
        config.showOthersPings = this.showOthersPings;
        config.showDistance = this.showDistance;
        config.showPlayerNames = this.showPlayerNames;
        config.playSound = this.playSound;
        config.showEdgeIndicator = this.showEdgeIndicator;
        config.showStatus = this.showStatus;
        config.soundVolume = this.volumePercent / 100f;
        config.maxIconDistance = this.maxIconDistance;
        config.apiBaseUrl = this.apiUrl == null ? "" : this.apiUrl.trim();
        config.sanitize();
        config.save();

        VMPingClient client = VMPingClient.getInstance();
        if (client != null) {
            // 热生效：重建 HTTP 客户端 + 重启中继（不需要重启游戏）
            client.applyConfig(config);
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ==================== 渲染 ====================

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        // 1) 压暗背景（不自己调 extractBackground，避免"blur 两次"崩溃）
        context.fill(0, 0, this.width, this.height, OVERLAY);
        // 2) 面板
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG);
        context.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
        // 3) 标题
        context.centeredText(this.font, this.title, this.width / 2,
                panelY + (TITLE_HEIGHT - this.font.lineHeight) / 2 + 1, TEXT_COLOR);

        // 4) 分区小标题（画在控件组的正上方）
        int contentLeft = panelX + PADDING;
        context.text(this.font, Component.translatable("vmping.settings.section.general"),
                contentLeft, toggleTop - labelHeight, SECTION_COLOR, false);
        context.text(this.font, Component.translatable("vmping.settings.section.display"),
                contentLeft, slidersTop - labelHeight, SECTION_COLOR, false);
        context.text(this.font, Component.translatable("vmping.settings.api_url"),
                contentLeft, urlTop - labelHeight, SECTION_COLOR, false);

        // 5) 控件（原版统一绘制，必须在自定义底板之后）
        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    /**
     * 整数滑条（音量 / 图标最远距离共用）。
     *
     * <p>为什么不用原版 {@code OptionInstance} 滑条：那套要接 {@code Options} 与
     * {@code OptionInstance.CaptionBasedToString}，为了两个滑条引入一整条配置抽象不值得。
     */
    private static final class ValueSlider extends AbstractSliderButton {

        private final String labelKey;
        private final int min;
        private final int max;
        /** 数值后缀：{@code "%"} 或一个翻译 key（如距离单位）。 */
        private final String suffix;
        /** 数值变化回调（把值同步回界面字段，resize 重建时不丢）。 */
        private final IntConsumer onChange;

        ValueSlider(int x, int y, int width, int height, String labelKey,
                    int min, int max, int initial, String suffix, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(),
                    (initial - min) / (double) Math.max(1, max - min));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            this.onChange = onChange;
            updateMessage();
        }

        /** 当前整数值。 */
        int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            // 注意：父类构造函数里会调一次 updateMessage，那时子类字段还没赋值
            if (labelKey == null) {
                setMessage(Component.empty());
                return;
            }
            Component suffixComponent = suffix == null || suffix.isEmpty()
                    ? Component.empty()
                    : (suffix.startsWith("vmping.")
                    ? Component.translatable(suffix)
                    : Component.literal(suffix));
            setMessage(Component.translatable("vmping.settings.slider",
                    Component.translatable(labelKey),
                    Component.literal(String.valueOf(current())).append(suffixComponent)));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (onChange != null) {
                onChange.accept(current());
            }
        }
    }
}
