package lotr_epicfight.mixin;

import lotr_epicfight.ExecutionState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.grappling.GrapplingAttackAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Ends a Blade Rush execution started by ExecutionMixin: if the victim survived the finishing strike, it dies now. */
@Mixin(value = GrapplingAttackAnimation.class, remap = false)
public abstract class GrapplingKillMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void lotrEpicfight$finishExecution(LivingEntityPatch<?> entitypatch, AssetAccessor<? extends DynamicAnimation> next, boolean isEnd, CallbackInfo ci) {
        if (entitypatch.isLogicalClient() || !(entitypatch.getOriginal() instanceof Player player)) return;
        ExecutionState.Execution e = ExecutionState.get(player);

        if (e != null && e.victim() == entitypatch.getGrapplingTarget()) {
            ExecutionState.take(player);
            ExecutionState.finish(player, e.victim());
        }
    }
}
