package net.minecraft.world.phys;

/** UI 预览器用的 {@code Vec3} 替身（只保留坐标与最常用的运算）。 */
public class Vec3 {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double distanceTo(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
