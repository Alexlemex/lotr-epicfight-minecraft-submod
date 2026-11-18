package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.client.online.EpicSkins;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

/**
 * Epic Fight bug: when its cosmetics web server is unreachable (DNS failure, offline...), the response callback
 * gets response == null and throws a NullPointerException on response.statusCode() before creating the cape's
 * cloth simulation. Result: no player cape at all in Epic Fight battle mode.
 * Fix: on a failed connection, fall back to Epic Fight's default cape (EpicSkins.initDefaultCape).
 */
@Mixin(value = EpicSkins.class, remap = false)
public abstract class CapeFallbackMixin {
    @WrapOperation(method = "initEpicSkins", at = @At(value = "INVOKE",
            target = "Lyesman/epicfight/client/online/EpicFightServerConnectionHelper;getPlayerSkinInfo(Ljava/lang/String;Ljava/lang/String;Ljava/util/function/BiConsumer;)V"))
    private static void lotrEpicfight$capeFallback(String domain, String uuid, BiConsumer<HttpResponse<String>, Exception> callback,
                                                   Operation<Void> original, AbstractClientPlayerPatch<?> playerpatch) {
        original.call(domain, uuid, (BiConsumer<HttpResponse<String>, Exception>) (response, exception) -> {
            if (exception != null || response == null) {
                EpicSkins.initDefaultCape(playerpatch);
                return;
            }
            callback.accept(response, exception);
        });
    }
}
