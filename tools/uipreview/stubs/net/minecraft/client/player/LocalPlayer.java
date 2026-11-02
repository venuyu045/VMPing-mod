package net.minecraft.client.player;

import net.minecraft.network.chat.Component;

/**
 * 本地玩家 —— UI 预览器里是一个"有坐标、有血量"的占位对象。
 *
 * <p>界面和功能类会读 {@code mc.player.getY()}（判断高度）、{@code getHealth()}（血量监控）、
 * {@code sendOverlayMessage(...)}（给玩家提示）。预览器不模拟真实世界，但要让这些调用
 * <b>能跑通不崩</b>，并且能通过 {@link #setY(double)} / {@link #setHealth(float)} 制造
 * 特定状态（比如"血量很低"来验证逃生界面）。
 */
public class LocalPlayer {

    private double x;
    private double y = 64.0;
    private double z;
    private float health = 20.0f;
    /** 最近一条 overlay 提示（预览器可读，用来断言"点了之后有没有给玩家提示"）。 */
    private String lastOverlayMessage;

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public void sendOverlayMessage(Component message) {
        this.lastOverlayMessage = message == null ? null : message.getString();
    }

    public String lastOverlayMessage() {
        return lastOverlayMessage;
    }

    /** 射线拾取：预览器里永远打空（走"标向视线前方"的分支）。 */
    public net.minecraft.world.phys.HitResult pick(double maxDistance, float partialTicks, boolean hitFluids) {
        return new net.minecraft.world.phys.HitResult(net.minecraft.world.phys.HitResult.Type.MISS,
                new net.minecraft.world.phys.Vec3(x, y + 1.62d, z));
    }

    /** 眼睛位置（站立时比脚高 1.62 格）。 */
    public net.minecraft.world.phys.Vec3 getEyePosition() {
        return new net.minecraft.world.phys.Vec3(x, y + 1.62d, z);
    }

    /** 视线方向：预览器固定朝 +z 看。 */
    public net.minecraft.world.phys.Vec3 getViewVector(float partialTicks) {
        return new net.minecraft.world.phys.Vec3(0d, 0d, 1d);
    }
}
