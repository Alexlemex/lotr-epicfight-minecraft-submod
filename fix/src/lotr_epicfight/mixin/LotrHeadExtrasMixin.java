package lotr_epicfight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * LOTR NPC models add extra 3D pieces to the head (beards, hobbit hair, ears, noses, teeth, horns, mohawks…).
 * Epic Fight's humanoid mesh only has the standard head, so those pieces disappeared. Draw them, attached to the
 * Epic Fight "Head" joint (same transform Epic Fight uses for worn skulls), with the NPC's own skin texture.
 * The standard 8x8x8 head cube is skipped since Epic Fight already draws it.
 */
@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public abstract class LotrHeadExtrasMixin {
    private static final Map<ModelPart, ModelPart> LOTR_EF_HEAD_EXTRAS = new WeakHashMap<>();
    private static Field lotrEf$cubesField, lotrEf$childrenField;

    @SuppressWarnings("unchecked")
    private static ModelPart lotrEf$extras(ModelPart head) {
        return LOTR_EF_HEAD_EXTRAS.computeIfAbsent(head, h -> {
            try {
                if (lotrEf$cubesField == null) {
                    for (Field f : ModelPart.class.getDeclaredFields()) {
                        if (List.class.isAssignableFrom(f.getType())) { f.setAccessible(true); lotrEf$cubesField = f; }
                        if (Map.class.isAssignableFrom(f.getType())) { f.setAccessible(true); lotrEf$childrenField = f; }
                    }
                }
                List<ModelPart.Cube> cubes = new ArrayList<>();
                for (ModelPart.Cube c : (List<ModelPart.Cube>) lotrEf$cubesField.get(h)) {
                    boolean standardHead = Math.abs(c.minX + 4) < 0.01F && Math.abs(c.minY + 8) < 0.01F && Math.abs(c.minZ + 4) < 0.01F
                        && Math.abs(c.maxX - 4) < 0.01F && Math.abs(c.maxY) < 0.01F && Math.abs(c.maxZ - 4) < 0.01F;
                    if (!standardHead) cubes.add(c);
                }
                Map<String, ModelPart> children = new HashMap<>((Map<String, ModelPart>) lotrEf$childrenField.get(h));
                children.remove("hat");
                if (cubes.isEmpty() && children.isEmpty()) return null;
                return new ModelPart(cubes, children);
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V",
            at = @At(value = "INVOKE", target = "Lyesman/epicfight/client/renderer/patched/entity/PatchedLivingEntityRenderer;renderLayer(Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/world/entity/LivingEntity;[Lyesman/epicfight/api/utils/math/OpenMatrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void lotrEpicfight$drawHeadExtras(LivingEntity entity, LivingEntityPatch<?> patch, LivingEntityRenderer renderer, MultiBufferSource buffer,
                                              PoseStack poseStack, int packedLight, float partialTicks, CallbackInfo ci) {
        if (!renderer.getClass().getName().startsWith("lotr.") || !(renderer.getModel() instanceof HumanoidModel<?> model) || entity.isInvisible()) return;
        ModelPart extras = lotrEf$extras(model.head);
        // LOTR's "hat" part is its hair/beard hood (texOffs 0,32, taller than a head): draw it too, at its offset from the head.
        ModelPart hat = model.hat;
        boolean drawHat = hat != null && hat.visible && !hat.skipDraw;
        if (extras == null && !drawHat) return;

        Armature armature = patch.getArmature();
        OpenMatrix4f[] poses = armature.getPoseMatrices();
        OpenMatrix4f headMatrix = new OpenMatrix4f().scale(new Vec3f(-1.0F, -1.0F, 1.0F)).mulFront(poses[armature.searchJointByName("Head").getId()]).translate(0, 0.02F, 0);

        ResourceLocation texture = renderer.getTextureLocation(entity);
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        var vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        poseStack.pushPose();
        MathUtils.mulStack(poseStack, headMatrix);
        if (extras != null) {
            extras.x = 0; extras.y = 0; extras.z = 0; extras.xRot = 0; extras.yRot = 0; extras.zRot = 0;
            extras.render(poseStack, vc, packedLight, overlay);
        }
        if (drawHat) {
            float x = hat.x, y = hat.y, z = hat.z, xr = hat.xRot, yr = hat.yRot, zr = hat.zRot;
            // Vanilla HumanoidModel.setupAnim does hat.copyFrom(head), so in LOTR's own rendering the hood sits exactly
            // on the head pivot; its declared PartPose offset (0, 2, 0) is never visible. Use the head pivot.
            hat.x = 0; hat.y = 0; hat.z = 0;
            hat.xRot = 0; hat.yRot = 0; hat.zRot = 0;
            hat.render(poseStack, vc, packedLight, overlay);
            hat.x = x; hat.y = y; hat.z = z; hat.xRot = xr; hat.yRot = yr; hat.zRot = zr;
        }
        poseStack.popPose();
    }
}
