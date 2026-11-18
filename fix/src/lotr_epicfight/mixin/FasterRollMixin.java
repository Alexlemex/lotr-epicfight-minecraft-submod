package lotr_epicfight.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.StaticAnimationProperty;
import yesman.epicfight.api.animation.types.DodgeAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;

/**
 * Plays Epic Fight's roll dodge faster. A dodge cannot be re-cast until its animation ends, so the
 * animation length is the real "cooldown" of the roll (~0.75 s by default -> ~0.45 s here).
 */
@Mixin(value = DodgeAnimation.class, remap = false)
public abstract class FasterRollMixin {
    private static final float ROLL_SPEED = 1.6F;

    @Inject(method = "<init>(FFLyesman/epicfight/api/animation/AnimationManager$AnimationAccessor;FFLyesman/epicfight/api/asset/AssetAccessor;)V", at = @At("TAIL"))
    private void lotrEpicfight$fasterRoll(float transition, float delay, AnimationAccessor<? extends DodgeAnimation> accessor,
                                          float width, float height, AssetAccessor<? extends Armature> armature, CallbackInfo ci) {
        ResourceLocation id = accessor.registryName();
        if ("epicfight".equals(id.getNamespace()) && id.getPath().startsWith("biped/skill/roll_")) {
            ((DodgeAnimation) (Object) this).addProperty(StaticAnimationProperty.PLAY_SPEED_MODIFIER,
                    (self, patch, speed, prev, elapsed) -> speed * ROLL_SPEED);
        }
    }
}
