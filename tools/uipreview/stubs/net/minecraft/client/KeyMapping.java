package net.minecraft.client;

/**
 * UI 预览器用的 {@code KeyMapping} 替身。
 *
 * <p>真实实现要接 SDL3 输入、注册到 {@code Options}、处理冲突；预览器只需要
 * 一个能回答"这个键现在按着吗"的对象 —— 标点轮盘靠它判断"按键松开了没有"。
 */
public class KeyMapping {

    private final String name;
    private boolean down;

    public KeyMapping(String name, boolean down) {
        this.name = name;
        this.down = down;
    }

    /** 翻译 key（语言文件里的按键名）。 */
    public String getName() {
        return name;
    }

    public boolean isDown() {
        return down;
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    /** 这次鼠标点击是不是绑定在这个键上（轮盘靠它判断"松开中键"）。 */
    public boolean matchesMouse(net.minecraft.client.input.MouseButtonEvent event) {
        return true;
    }
}
