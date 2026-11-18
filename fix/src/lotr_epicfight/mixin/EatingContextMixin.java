package lotr_epicfight.mixin;

import lotr_epicfight.EatingState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which player is finishing an item use, for FoodHealMixin (FoodData has no owner reference). */
@Mixin(LivingEntity.class)
public abstract class EatingContextMixin {
    @Inject(method = "completeUsingItem()V", at = @At("HEAD"))
    private void lotrEpicfight$startEating(CallbackInfo ci) {
        if ((Object) this instanceof Player player && !player.level().isClientSide) {
            EatingState.set(player);
        }
    }

    @Inject(method = "completeUsingItem()V", at = @At("RETURN"))
    private void lotrEpicfight$stopEating(CallbackInfo ci) {
        EatingState.clear();
    }
}
