package lotr_epicfight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/** Doubles the player's base Epic Fight stamina (15 -> 30). Regeneration scales with it (it is % of max per tick). */
@Mixin(value = PlayerPatch.class, remap = false)
public abstract class StaminaBonusMixin {
    private static final float STAMINA_BONUS = 15.0F;

    @Inject(method = "getMaxStamina", at = @At("RETURN"), cancellable = true)
    private void lotrEpicfight$moreStamina(CallbackInfoReturnable<Float> cir) {
        float max = cir.getReturnValueF();
        if (max > 0.0F) cir.setReturnValue(max + STAMINA_BONUS);
    }
}
