package net.minecraft.world.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * UI 预览器用的 {@code Entity} 替身。
 *
 * <p>标点轮盘点到实体时会读位置、包围盒高度、UUID 和名字（用于"标在某只怪身上"）。
 * 预览器里没有真实世界，所以这里给一份固定的假数据，保证这段代码能跑通。
 */
public class Entity {

    private final UUID uuid = UUID.randomUUID();
    private final Vec3 position;
    private final String name;

    public Entity(Vec3 position, String name) {
        this.position = position;
        this.name = name;
    }

    public Vec3 position() {
        return position;
    }

    /** 包围盒高度（玩家 1.8）。 */
    public double getBbHeight() {
        return 1.8d;
    }

    public UUID getUUID() {
        return uuid;
    }

    public Component getName() {
        return Component.literal(name);
    }
}
