package lotr_epicfight.mixin;

import lotr_epicfight.EatingState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eating heals a fixed amount instantly: 1 HP (half a heart) per food point the item gives
 * (bread 5 = 2.5 hearts, cooked beef 8 = 4 hearts). Hooked on FoodData.add, which every food goes
 * through (vanilla Player.eat AND LOTR foods/drinks that call FoodData.eat directly).
 * Only while a player is finishing an item use, so saturation from other sources does not heal.
 */
@Mixin(FoodData.class)
public abstract class FoodHealMixin {
    private static final float HEAL_PER_FOOD_POINT = 1.0F;

    @Inject(method = "add(IF)V", at = @At("HEAD"))
    private void lotrEpicfight$healOnEat(int foodLevel, float saturation, CallbackInfo ci) {
        Player player = EatingState.get();
        if (player != null && player.getFoodData() == (Object) this && foodLevel > 0) {
            player.heal(foodLevel * HEAL_PER_FOOD_POINT);
        }
    }
}
