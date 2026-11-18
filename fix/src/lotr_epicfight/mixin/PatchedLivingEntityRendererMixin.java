package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.client.renderer.patched.entity.PPlayerRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * LOTR replaces the player's renderer with its own race renderer (lotr.modern.client.z, a LivingEntityRenderer
 * with a HumanoidModel). Epic Fight's PPlayerRenderer.prepareModel casts the renderer to PlayerRenderer and crashes.
 * When the renderer is not a PlayerRenderer, only do the generic mesh reset and skip the player-only part
 * (skin layer visibility), so Epic Fight keeps animating the player with LOTR's skin.
 */
@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public abstract class PatchedLivingEntityRendererMixin {
    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V",
        at = @At(value = "INVOKE", target = "Lyesman/epicfight/client/renderer/patched/entity/PatchedLivingEntityRenderer;prepareModel(Lyesman/epicfight/api/client/model/SkinnedMesh;Lnet/minecraft/world/entity/LivingEntity;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;)V")
    )
    private void lotrEpicfight$guardPlayerRenderer(PatchedLivingEntityRenderer<?, ?, ?, ?, ?> self, SkinnedMesh mesh, LivingEntity entity,
                                                   LivingEntityPatch<?> patch, LivingEntityRenderer<?, ?> renderer, Operation<Void> original) {
        if (self instanceof PPlayerRenderer && !(renderer instanceof PlayerRenderer)) {
            mesh.initialize();
            return;
        }
        original.call(self, mesh, entity, patch, renderer);
    }
}
