package lotr_epicfight.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;

/**
 * LOTR's combat goal lotr.modern.world.m (priority 1, MOVE+LOOK) and Epic Fight's TargetChasingGoal (also priority 1,
 * MOVE+LOOK) compete for movement; m starts first, so Epic Fight never chases: NPCs keep LOTR's circle / retreat /
 * shield dance and only rarely get inside Epic Fight's 2-3 block attack range (NPC vs NPC fights stall).
 * When the NPC is patched by Epic Fight, holds a melee weapon, is on foot and its target is close, m steps aside so
 * Epic Fight's chase + animated attack goals drive the fight. Far away, with a bow/crossbow, or mounted, m keeps
 * control (approach, weapon switching, archery, mounted combat).
 */
@Pseudo
@Mixin(targets = "lotr.modern.world.m", remap = false)
public abstract class NpcMeleeHandoverMixin {
    private static final double HANDOVER_DISTANCE_SQR = 10.0D * 10.0D;

    @Shadow @Final private lotr.modern.world.c acA;

    private boolean lotrEpicfight$epicFightMelee() {
        lotr.modern.world.c npc = this.acA;
        if (npc == null || npc.isPassenger()) {
            return false;
        }
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive() || npc.distanceToSqr(target) > HANDOVER_DISTANCE_SQR) {
            return false;
        }
        if (npc.getMainHandItem().getItem() instanceof ProjectileWeaponItem) {
            return false;
        }
        return EpicFightCapabilities.getEntityPatch(npc, MobPatch.class) != null;
    }

    @Inject(method = "canUse()Z", at = @At("HEAD"), cancellable = true)
    private void lotrEpicfight$handoverStart(CallbackInfoReturnable<Boolean> cir) {
        if (this.lotrEpicfight$epicFightMelee()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse()Z", at = @At("HEAD"), cancellable = true)
    private void lotrEpicfight$handoverContinue(CallbackInfoReturnable<Boolean> cir) {
        if (this.lotrEpicfight$epicFightMelee()) {
            cir.setReturnValue(false);
        }
    }
}
