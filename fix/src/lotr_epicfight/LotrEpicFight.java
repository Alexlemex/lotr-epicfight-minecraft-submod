package lotr_epicfight;

import net.neoforged.fml.common.Mod;

/**
 * Empty mod entry point. Its only purpose is to make this jar a javafml mod (FMLModContainer): on a dedicated
 * server Epic Fight has no client resource manager and reads armature JSONs (assets/lotr_epicfight/animmodels/...)
 * through FMLModContainer.modClasses -> Class.getResourceAsStream. A lowcodefml jar has no mod class, so custom
 * armatures (Olog-hai...) could not be loaded server-side.
 */
@Mod("lotr_epicfight")
public final class LotrEpicFight {
    public LotrEpicFight() {
    }
}
