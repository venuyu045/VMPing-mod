package net.minecraft.world.phys;

/**
 * UI 预览器用的 {@code HitResult} 替身。
 *
 * <p>标点轮盘松开时会做一次射线拾取：预览器里永远返回 {@link Type#MISS}，
 * 于是轮盘走"标向视线前方"的分支 —— 这正是我们要在预览里看到的默认行为。
 */
public class HitResult {

    /** 命中类型（与游戏内一致）。 */
    public enum Type {
        MISS,
        BLOCK,
        ENTITY
    }

    private final Type type;
    private final Vec3 location;

    public HitResult(Type type, Vec3 location) {
        this.type = type;
        this.location = location;
    }

    public Type getType() {
        return type;
    }

    public Vec3 getLocation() {
        return location;
    }
}
