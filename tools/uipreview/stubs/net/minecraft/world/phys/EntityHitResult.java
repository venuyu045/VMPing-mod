package net.minecraft.world.phys;

import net.minecraft.world.entity.Entity;

/** UI 预览器用的 {@code EntityHitResult} 替身（点到实体时的命中结果）。 */
public class EntityHitResult extends HitResult {

    private final Entity entity;

    public EntityHitResult(Entity entity) {
        super(Type.ENTITY, entity == null ? Vec3.ZERO : entity.position());
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
