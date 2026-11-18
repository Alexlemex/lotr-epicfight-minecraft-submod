package lotr_epicfight.mixin;

import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns off LOTR's race model for the player (lotr.modern.client.CharacterClient event handlers),
 * so the player is drawn by the vanilla PlayerRenderer and Epic Fight animates it normally.
 * The race choice itself (gameplay, stats, LOTR data) is untouched — only its rendering is skipped.
 */
@Pseudo
@Mixin(targets = "lotr.modern.client.CharacterClient", remap = false)
public abstract class CharacterClientMixin {
    @Inject(method = "render(Lnet/neoforged/neoforge/client/event/RenderPlayerEvent$Pre;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lotrEpicfight$vanillaPlayerModel(RenderPlayerEvent.Pre event, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "arm(Lnet/neoforged/neoforge/client/event/RenderArmEvent;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lotrEpicfight$vanillaPlayerArm(RenderArmEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
