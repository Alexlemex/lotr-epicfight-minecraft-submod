package lotr_epicfight.mixin;

import lotr_epicfight.ExecutionState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.grappling.GrapplingAttackAnimation;
import yesman.epicfight.api.animation.types.grappling.GrapplingTryAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Ends an in-place finisher (skill attack animation chosen by ExecutionMixin): the held victim dies. */
@Mixin(value = AttackAnimation.class, remap = false)
public abstract class FinisherKillMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void lotrEpicfight$finish(LivingEntityPatch<?> entitypatch, AssetAccessor<? extends DynamicAnimation> next, boolean isEnd, CallbackInfo ci) {
        // Blade Rush's dash (try) and grab (handled by GrapplingKillMixin) are not in-place finishers.
        if ((Object) this instanceof GrapplingTryAnimation || (Object) this instanceof GrapplingAttackAnimation) return;
        if (entitypatch.isLogicalClient() || !(entitypatch.getOriginal() instanceof Player player)) return;
        ExecutionState.Execution e = ExecutionState.get(player);

        if (e != null && e.animation() == ((AttackAnimation) (Object) this).getAccessor()) {
            ExecutionState.take(player);
            ExecutionState.finish(player, e.victim());
        }
    }
}
