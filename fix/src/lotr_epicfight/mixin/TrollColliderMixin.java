package lotr_epicfight.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * LOTR trolls play humanoid (illager axe) swings scaled to a ~4.5 block tall body: the weapon arc passes at the
 * troll's chest height, i.e. above a player's head, and only touches the ground 4-5 blocks away. A joint collider
 * therefore almost never meets the player ("hits the air"). For trolls the hit test of an attack phase is replaced
 * by a frontal area: while the phase is active, enemies within REACH blocks in a 150 degree cone in front of the
 * troll (or right against it, any side) are hit. Only the troll's target, players and mobs targeting the troll are
 * hit, so allied orcs standing nearby are spared. Common mixin: hits are computed on the server.
 */
@Mixin(value = AttackAnimation.Phase.class, remap = false)
public abstract class TrollColliderMixin {
    private static final double REACH = 4.2D;           // from the troll's centre to the target's edge
    private static final double MIN_COS = 0.26D;        // cos(75 deg): half angle of the frontal cone

    private static boolean lotrEf$isTroll(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getName().equals("lotr.modern.monster.Troll")) return true;
        }
        return false;
    }

    @Inject(method = "getCollidingEntities", at = @At("HEAD"), cancellable = true)
    private void lotrEpicfight$trollArea(LivingEntityPatch<?> entitypatch, AttackAnimation animation, float prevElapsedTime,
                                         float elapsedTime, float attackSpeed, CallbackInfoReturnable<List<Entity>> cir) {
        LivingEntity troll = entitypatch.getOriginal();
        if (troll == null || !lotrEf$isTroll(troll.getClass())) return;

        Entity target = troll instanceof Mob mob ? mob.getTarget() : null;
        double yaw = Math.toRadians(troll.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double close = troll.getBbWidth() * 0.5D + 1.0D;
        AABB area = troll.getBoundingBox().inflate(REACH, 1.0D, REACH);
        List<Entity> hits = new ArrayList<>();

        for (Entity e : troll.level().getEntities(troll, area)) {
            if (!(e instanceof LivingEntity living) || !living.isAlive() || e.isSpectator() || e.isPassengerOfSameVehicle(troll)) continue;
            boolean enemy = e == target || e instanceof Player || (e instanceof Mob m && m.getTarget() == troll);
            if (!enemy) continue;
            double dx = e.getX() - troll.getX(), dz = e.getZ() - troll.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist - e.getBbWidth() * 0.5D > REACH) continue;
            if (dist > close && (dx * fx + dz * fz) / dist < MIN_COS) continue;
            if (e.getBoundingBox().minY > troll.getBoundingBox().maxY || e.getBoundingBox().maxY < troll.getY() - 1.0D) continue;
            hits.add(e);
        }
        cir.setReturnValue(hits);
    }
}
