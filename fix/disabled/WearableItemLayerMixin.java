package lotr_epicfight.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;

/**
 * For LOTR's NPC armour layer (a HumanoidArmorLayer subclass), Epic Fight's automatic "bake the armour model into a
 * skinned mesh" step produces an empty mesh, so the armour becomes invisible. Use Epic Fight's own standard armour
 * meshes instead (same shapes as vanilla armour); the LOTR armour textures still come from the item as usual.
 */
@Mixin(value = WearableItemLayer.class, remap = false)
public abstract class WearableItemLayerMixin {
    @Inject(method = "getArmorModel", at = @At("HEAD"), cancellable = true)
    private void lotrEpicfight$standardMeshForLotrArmour(HumanoidArmorLayer<?, ?, ?> originalRenderer, HumanoidModel<?> originalModel, Model forgeModel,
                                                         LivingEntity entity, ArmorItem armorItem, ItemStack stack, EquipmentSlot slot,
                                                         CallbackInfoReturnable<SkinnedMesh> cir) {
        if (originalRenderer != null && originalRenderer.getClass().getName().startsWith("lotr.")) {
            SkinnedMesh mesh = switch (slot) {
                case HEAD -> Meshes.HELMET.get();
                case CHEST -> Meshes.CHESTPLATE.get();
                case LEGS -> Meshes.LEGGINS.get();
                case FEET -> Meshes.BOOTS.get();
                default -> null;
            };
            if (mesh != null) {
                cir.setReturnValue(mesh);
            }
        }
    }
}
