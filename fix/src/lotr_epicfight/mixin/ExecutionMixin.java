package lotr_epicfight.mixin;

import java.util.List;
import lotr_epicfight.ExecutionState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.registry.entries.EpicFightSynchedAnimationVariableKeys;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.common.ComboAttacks;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategories;
import yesman.epicfight.world.capabilities.item.WeaponCategory;

/**
 * Finishers: when the player's normal attack targets a humanoid enemy at <= 20% health or stunned (long hit /
 * knocked down), the attack becomes a finisher picked at random among those fitting the held weapon.
 * - Blade Rush: Epic Fight's grappling execution (dash, grab, strike), victim plays the matching animation.
 * - Other finishers: an Epic Fight skill attack while the victim is held in a stagger; it dies at the end.
 * The player is invulnerable during the finisher (see FinisherInvulnerabilityMixin).
 */
@Mixin(value = ComboAttacks.class, remap = false)
public abstract class ExecutionMixin {
    private static final float LOW_HEALTH_RATIO = 0.2F;
    private static final double MAX_DISTANCE_SQR = 4.5D * 4.5D;    // Blade Rush dashes to the target
    private static final double MELEE_DISTANCE_SQR = 3.0D * 3.0D;  // in-place finishers

    @Inject(method = "executeOnServer", at = @At("HEAD"), cancellable = true)
    private void lotrEpicfight$execute(SkillContainer container, CompoundTag args, CallbackInfo ci) {
        ServerPlayerPatch executor = container.getServerExecutor();
        if (executor == null) return;
        Player player = executor.getOriginal();
        LivingEntity target = executor.getTarget();

        if (target == null || !target.isAlive() || target instanceof Player || target == player
                || player.distanceToSqr(target) > MAX_DISTANCE_SQR || !player.hasLineOfSight(target)) {
            return;
        }

        LivingEntityPatch<?> targetPatch = EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
        if (targetPatch == null || !(targetPatch.getArmature() instanceof HumanoidArmature)) return;

        EntityState state = targetPatch.getEntityState();
        boolean lowHealth = target.getHealth() <= target.getMaxHealth() * LOW_HEALTH_RATIO;
        boolean stunned = state.hurtLevel() > 1 || state.knockDown();
        if (!lowHealth && !stunned) return;

        WeaponCategory category = executor.getHoldingItemCapability(InteractionHand.MAIN_HAND).getWeaponCategory();
        List<AnimationAccessor<? extends StaticAnimation>> pool = lotrEpicfight$finishers(category);
        AnimationAccessor<? extends StaticAnimation> finisher = pool.get(player.getRandom().nextInt(pool.size()));

        if (finisher != Animations.BLADE_RUSH_TRY && player.distanceToSqr(target) > MELEE_DISTANCE_SQR) {
            finisher = Animations.BLADE_RUSH_TRY; // too far for an in-place finisher: dash in
        }

        ExecutionState.start(player, target, finisher);

        if (finisher == Animations.BLADE_RUSH_TRY) {
            executor.getAnimator().getVariables().put(EpicFightSynchedAnimationVariableKeys.TARGET_ENTITY.get(), Animations.BLADE_RUSH_TRY, target.getId());
        } else {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            executor.setModelYRot(yaw, true);
            target.lookAt(EntityAnchorArgument.Anchor.FEET, player.position());
            if (target instanceof Mob mob) mob.getNavigation().stop();
            targetPatch.playAnimationSynchronized(Animations.BLADE_RUSH_HIT, 0.0F);
        }

        executor.playAnimationSynchronized(finisher, 0.0F);
        ci.cancel();
    }

    private static List<AnimationAccessor<? extends StaticAnimation>> lotrEpicfight$finishers(WeaponCategory category) {
        if (category == WeaponCategories.SWORD || category == WeaponCategories.LONGSWORD
                || category == WeaponCategories.TACHI || category == WeaponCategories.UCHIGATANA) {
            return List.of(Animations.BLADE_RUSH_TRY, Animations.SWEEPING_EDGE, Animations.DANCING_EDGE, Animations.HEARTPIERCER);
        } else if (category == WeaponCategories.AXE || category == WeaponCategories.GREATSWORD) {
            return List.of(Animations.BLADE_RUSH_TRY, Animations.THE_GUILLOTINE);
        } else if (category == WeaponCategories.SPEAR || category == WeaponCategories.TRIDENT) {
            return List.of(Animations.BLADE_RUSH_TRY, Animations.HEARTPIERCER);
        } else if (category == WeaponCategories.DAGGER) {
            return List.of(Animations.BLADE_RUSH_TRY, Animations.SWEEPING_EDGE);
        }
        return List.of(Animations.BLADE_RUSH_TRY, Animations.THE_GUILLOTINE);
    }
}
