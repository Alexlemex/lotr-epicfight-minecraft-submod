package lotr_epicfight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.patched.layer.RenderOriginalModelLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * LOTR's WizardHatLayer (Gandalf's / Saruman's hat) is an unknown layer for Epic Fight, so it was drawn by the
 * static "RenderOriginalModelLayer" fallback at the model root: frozen, not following the head.
 * Draw it at the Epic Fight "Head" joint instead. The layer itself applies head.translateAndRotate(), so the
 * vanilla head part is zeroed during the call (Epic Fight never runs setupAnim, its pose would be stale).
 */
@Mixin(value = RenderOriginalModelLayer.class, remap = false)
public abstract class WizardHatMixin {
    @Inject(method = "renderLayer(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/layers/RenderLayer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I[Lyesman/epicfight/api/utils/math/OpenMatrix4f;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void lotrEpicfight$hatOnHead(LivingEntityPatch<?> patch, LivingEntity entity, RenderLayer layer, PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks, CallbackInfo ci) {
        if (layer == null || !layer.getClass().getName().equals("lotr.modern.client.WizardHatLayer")
                || !(layer.getParentModel() instanceof HumanoidModel<?> model)) return;
        Armature armature = patch.getArmature();
        var head = armature.searchJointByName("Head");
        if (head == null) return;
        ci.cancel();

        OpenMatrix4f headMatrix = new OpenMatrix4f().scale(new Vec3f(-1.0F, -1.0F, 1.0F)).mulFront(poses[head.getId()]).translate(0, 0.02F, 0);
        ModelPart h = model.head;
        float x = h.x, y = h.y, z = h.z, xr = h.xRot, yr = h.yRot, zr = h.zRot, sx = h.xScale, sy = h.yScale, sz = h.zScale;
        h.x = 0; h.y = 0; h.z = 0; h.xRot = 0; h.yRot = 0; h.zRot = 0; h.xScale = 1; h.yScale = 1; h.zScale = 1;
        poseStack.pushPose();
        MathUtils.mulStack(poseStack, headMatrix);
        try {
            layer.render(poseStack, buffer, packedLight, entity, entity.walkAnimation.position(), entity.walkAnimation.speed(), partialTicks, bob, yRot, xRot);
        } finally {
            poseStack.popPose();
            h.x = x; h.y = y; h.z = z; h.xRot = xr; h.yRot = yr; h.zRot = zr; h.xScale = sx; h.yScale = sy; h.zScale = sz;
        }
    }
}
