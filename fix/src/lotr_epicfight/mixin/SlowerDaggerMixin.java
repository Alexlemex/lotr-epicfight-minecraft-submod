package lotr_epicfight.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Player dagger attacks (biped/combat/dagger_*: combos, dual, dash, air slash) play 10% slower.
 * Common mixin: client and server must agree on the play speed (hit timing).
 */
@Mixin(value = AttackAnimation.class, remap = false)
public abstract class SlowerDaggerMixin {
    private static final float DAGGER_SPEED = 0.9F;

    @Inject(method = "getPlaySpeed", at = @At("RETURN"), cancellable = true)
    private void lotrEpicfight$slowerDagger(LivingEntityPatch<?> entitypatch, DynamicAnimation animation, CallbackInfoReturnable<Float> cir) {
        ResourceLocation id = ((AttackAnimation) (Object) this).getAccessor().registryName();
        if ("epicfight".equals(id.getNamespace()) && id.getPath().startsWith("biped/combat/dagger_")) {
            cir.setReturnValue(cir.getReturnValueF() * DAGGER_SPEED);
        }
    }
}
