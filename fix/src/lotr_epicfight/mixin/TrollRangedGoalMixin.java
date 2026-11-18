package lotr_epicfight.mixin;

import java.util.Set;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.world.capabilities.entitypatch.MobPatch;

/**
 * Epic Fight removes every RangedAttackGoal from patched mobs. LOTR trolls' rock throw (lotr.modern.monster.Troll$2,
 * a RangedAttackGoal subclass used against far targets) would be lost, so keep it: Epic Fight's combat behaviours
 * handle melee range, the LOTR goal still throws rocks at range. Common mixin (AI runs on the server).
 */
@Mixin(value = MobPatch.class, remap = false)
public abstract class TrollRangedGoalMixin {
    @Inject(method = "selectGoalToRemove", at = @At("TAIL"))
    private void lotrEpicfight$keepTrollRockThrow(Set<Goal> toRemove, CallbackInfo ci) {
        toRemove.removeIf(goal -> goal instanceof RangedAttackGoal && goal.getClass().getName().startsWith("lotr.modern.monster.Troll$"));
    }
}
