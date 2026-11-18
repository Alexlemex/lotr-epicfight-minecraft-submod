package lotr_epicfight.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every item behaves as if it had the vanilla "Unbreakable" component: weapons, tools and armour
 * never lose durability and the durability bar is hidden. Must run on both sides (bar is client-side).
 */
@Mixin(ItemStack.class)
public abstract class UnbreakableItemsMixin {
    @Inject(method = "isDamageableItem()Z", at = @At("HEAD"), cancellable = true)
    private void lotrEpicfight$neverBreak(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
