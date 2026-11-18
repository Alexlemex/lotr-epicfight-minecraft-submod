package lotr_epicfight.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Eating takes half the time (1.6 s -> 0.8 s), for every food including LOTR ones. Drinks are unchanged. */
@Mixin(ItemStack.class)
public abstract class FasterEatingMixin {
    private static final float EAT_TIME_FACTOR = 0.5F;

    @Inject(method = "getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"), cancellable = true)
    private void lotrEpicfight$fasterEating(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int duration = cir.getReturnValueI();
        if (duration > 0 && ((ItemStack) (Object) this).getUseAnimation() == UseAnim.EAT) {
            cir.setReturnValue(Math.max(1, Math.round(duration * EAT_TIME_FACTOR)));
        }
    }
}
