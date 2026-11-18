package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;

/**
 * LOTR faction NPCs (lotr.modern.world.c) fight with their own goal lotr.modern.world.m, which is not a
 * MeleeAttackGoal, so Epic Fight does not remove it: it lands an instant vanilla hit every 12-40 ticks on top
 * of Epic Fight's animated attacks (double damage, no telegraph). When the NPC is patched by Epic Fight, that
 * vanilla melee hit is dropped; movement, shield and bow logic of the goal are kept.
 */
@Pseudo
@Mixin(targets = "lotr.modern.world.m", remap = false)
public abstract class NpcCombatGoalMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Llotr/modern/world/c;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean lotrEpicfight$noVanillaMeleeHit(lotr.modern.world.c npc, Entity target, Operation<Boolean> original) {
        if (EpicFightCapabilities.getEntityPatch(npc, MobPatch.class) != null) {
            return false;
        }
        return original.call(npc, target);
    }
}
