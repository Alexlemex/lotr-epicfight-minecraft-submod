package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lotr_epicfight.olog.OlogAssets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.api.asset.JsonAssetLoader;

/**
 * Serves the Olog-hai armature/meshes (lotr_epicfight:animmodels/entity/olog_hai*.json) generated at runtime by
 * OlogAssets instead of shipping files derived from Epic Fight's / LOTR's assets.
 * Our jar has no such file, so Epic Fight's JsonAssetLoader falls back (client and server alike) to reading
 * "/assets/<ns>/<path>" through the mod's classes: that read is answered here with the generated JSON.
 */
@Mixin(value = JsonAssetLoader.class, remap = false)
public abstract class OlogAssetMixin {
    @WrapOperation(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private InputStream lotrEpicfight$generatedOlog(Class<?> modClass, String path, Operation<InputStream> original) {
        InputStream stream = original.call(modClass, path);
        if (stream == null && path != null && path.startsWith(OlogAssets.PREFIX)) {
            String json = OlogAssets.forPath(path, OlogAssets.isClient());
            if (json != null) {
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        return stream;
    }
}
