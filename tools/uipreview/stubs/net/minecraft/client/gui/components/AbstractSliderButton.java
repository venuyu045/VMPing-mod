package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * UI 预览器用的 {@code AbstractSliderButton} 替身。
 *
 * <p>滑条是设置界面的核心控件（音量 / 图标最远距离），这里要复现两件事：
 * <ol>
 *   <li><b>语义</b>：{@code value ∈ [0,1]}、{@code updateMessage()} / {@code applyValue()}
 *       的调用时机与游戏一致（拖动时实时调 {@code applyValue}，数值变化后调 {@code updateMessage}）；</li>
 *   <li><b>外观</b>：原版滑条是"深色底 + 白色手柄"，文字居中压在滑条上。</li>
 * </ol>
 *
 * <p>注意构造顺序与游戏内一致：<b>父类构造函数里会调一次 {@link #updateMessage()}</b>，
 * 那时子类字段还没赋值 —— 生产代码必须容忍这一点（{@code SettingsScreen.ValueSlider}
 * 里就有对应的 null 判断）。
 */
public abstract class AbstractSliderButton implements Renderable, GuiEventListener,
        NarratableEntry, AlphaAware {

    /** 滑条底槽颜色（原版是贴图，这里用近似色）。 */
    private static final int TRACK_COLOR = 0xFF000000;
    private static final int TRACK_INNER = 0xFF505050;
    private static final int HANDLE_COLOR = 0xFFC0C0C0;
    private static final int HANDLE_BORDER = 0xFF000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** 鼠标左键编号（SDL3 1-based）。 */
    private static final int LEFT_BUTTON = 1;

    private int x;
    private int y;
    private int width;
    private int height;
    /** 当前值，范围 [0,1]。 */
    protected double value;
    /** 是否被拖拽中。 */
    protected boolean canChangeValue;
    private Component message;
    private boolean focused;
    private float alpha = 1.0f;

    protected AbstractSliderButton(int x, int y, int width, int height, Component message, double value) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message == null ? Component.empty() : message;
        this.value = Math.max(0d, Math.min(1d, value));
        updateMessage();
    }

    /** 数值变化时刷新文字（子类实现）。 */
    protected abstract void updateMessage();

    /** 数值被用户改动时回调（子类实现）。 */
    protected abstract void applyValue();

    public Component getMessage() {
        return message;
    }

    public void setMessage(Component message) {
        this.message = message == null ? Component.empty() : message;
    }

    /** 直接设置值（不触发 {@link #applyValue()}，与游戏内一致）。 */
    protected void setValue(double value) {
        this.value = Math.max(0d, Math.min(1d, value));
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int argb = alphaColor(TRACK_COLOR, alpha);
        context.fill(x, y, x + width, y + height, argb);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, alphaColor(TRACK_INNER, alpha));

        int handleX = x + (int) Math.round((width - 8) * value);
        context.fill(handleX, y, handleX + 8, y + height, alphaColor(HANDLE_COLOR, alpha));
        context.outline(handleX, y, 8, height, alphaColor(HANDLE_BORDER, alpha));

        String text = message.getString();
        context.centeredText(net.minecraft.client.Minecraft.ensureInstance().font, text,
                x + width / 2, y + (height - 8) / 2, alphaColor(TEXT_COLOR, alpha));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != LEFT_BUTTON || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        this.canChangeValue = true;
        dragTo(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (canChangeValue) {
            canChangeValue = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (canChangeValue) {
            dragTo(event.x());
            return true;
        }
        return false;
    }

    private void dragTo(double mouseX) {
        double ratio = (mouseX - (x + 4)) / (double) Math.max(1, width - 8);
        setValue(ratio);
        updateMessage();
        applyValue();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        // 预览器不朗读
    }

    @Override
    public NarrationPriority narrationPriority() {
        return focused ? NarrationPriority.FOCUSED
                : (canChangeValue ? NarrationPriority.HOVERED : NarrationPriority.NONE);
    }

    private static int alphaColor(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
