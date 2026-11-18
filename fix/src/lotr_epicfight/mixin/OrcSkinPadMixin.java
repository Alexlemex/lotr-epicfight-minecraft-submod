package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * LOTR orc / uruk-hai / black uruk skins are old 64x32 sheets, but every LOTR NPC is rendered with Epic Fight's
 * BIPED_OLD_TEX mesh, whose UVs expect the classic layout on a 64x64 sheet (v in 0..0.5). Instead of shipping padded
 * copies of LOTR's textures, pad them at load time: same pixels on top, transparent bottom half.
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.SimpleTexture$TextureImage")
public abstract class OrcSkinPadMixin {
    // legacy faction orcs (npc/orc/...) and the generic orc_npc / uruk_npc skins (faction_npc/<orc faction>_N.png)
    private static final String[] PREFIXES = {"textures/entity/npc/orc/", "textures/entity/faction_npc/"};

    private static boolean lotrEpicfight$matches(String path) {
        for (String prefix : PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @WrapOperation(method = "load", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private static NativeImage lotrEpicfight$padOrcSkin(InputStream stream, Operation<NativeImage> original,
                                                        @Local(argsOnly = true) ResourceLocation location) {
        NativeImage image = original.call(stream);
        if (!"lotr".equals(location.getNamespace()) || !lotrEpicfight$matches(location.getPath())
                || image.getWidth() != 2 * image.getHeight()) {
            return image;
        }
        int w = image.getWidth(), h = image.getHeight();
        NativeImage padded = new NativeImage(image.format(), w, h * 2, true); // calloc -> transparent
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                padded.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
            }
        }
        image.close();
        return padded;
    }
}
