package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * LOTR bug fix (crash on world join when it rains): lotr.modern.client.sound.Ambience.replace swaps the vanilla rain/
 * thunder sound and reads getVolume()/getPitch() on the original SoundInstance, but NeoForge fires PlaySoundEvent
 * before the instance is resolved, so its Sound is still null -> NullPointerException. Resolve it first.
 */
@Pseudo
@Mixin(targets = "lotr.modern.client.sound.Ambience", remap = false)
public abstract class AmbienceRainFixMixin {
    private static void lotrEpicfight$resolve(SoundInstance sound) {
        if (sound.getSound() == null) {
            sound.resolve(Minecraft.getInstance().getSoundManager());
        }
    }

    @WrapOperation(method = "replace", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
    private static float lotrEpicfight$safeVolume(SoundInstance sound, Operation<Float> original) {
        lotrEpicfight$resolve(sound);
        return sound.getSound() == null ? 1.0F : original.call(sound);
    }

    @WrapOperation(method = "replace", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getPitch()F"))
    private static float lotrEpicfight$safePitch(SoundInstance sound, Operation<Float> original) {
        lotrEpicfight$resolve(sound);
        return sound.getSound() == null ? 1.0F : original.call(sound);
    }
}
