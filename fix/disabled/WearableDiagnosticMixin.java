package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary diagnostics inside Epic Fight's armour layer, once per LOTR entity type. */
@Mixin(value = WearableItemLayer.class, remap = false)
public abstract class WearableDiagnosticMixin {
    private static final Logger LOTR_EF_LOG2 = LogUtils.getLogger();
    private static final Set<String> LOTR_EF_SEEN2 = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<String> LOTR_EF_CURRENT = new ThreadLocal<>();

    @Inject(method = "renderLayer(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I[Lyesman/epicfight/api/utils/math/OpenMatrix4f;FFFF)V",
            at = @At("HEAD"), require = 0)
    private void lotrEpicfight$diagHead(LivingEntityPatch<?> patch, LivingEntity entity, HumanoidArmorLayer<?, ?, ?> layer, PoseStack ps, MultiBufferSource buf,
                                        int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float pt, CallbackInfo ci) {
        String type = EntityType.getKey(entity.getType()).toString();
        if (type.startsWith("lotr:") && LOTR_EF_SEEN2.add(type)) {
            LOTR_EF_CURRENT.set(type);
            LOTR_EF_LOG2.info("[lotr_epicfight diag2] {} armour layer CALLED, vanillaLayer={} firstPerson={}", type, layer == null ? "null" : layer.getClass().getName(), patch.isFirstPerson());
        } else {
            LOTR_EF_CURRENT.remove();
        }
    }

    @WrapOperation(method = "renderLayer(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I[Lyesman/epicfight/api/utils/math/OpenMatrix4f;FFFF)V",
            at = @At(value = "INVOKE", target = "Lyesman/epicfight/client/renderer/patched/layer/WearableItemLayer;getArmorModel(Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/client/model/Model;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ArmorItem;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Lyesman/epicfight/api/client/model/SkinnedMesh;"),
            require = 0)
    private SkinnedMesh lotrEpicfight$diagMesh(WearableItemLayer<?, ?, ?, ?> self, HumanoidArmorLayer<?, ?, ?> l, net.minecraft.client.model.HumanoidModel<?> m, net.minecraft.client.model.Model fm,
                                               LivingEntity e, net.minecraft.world.item.ArmorItem ai, net.minecraft.world.item.ItemStack st, EquipmentSlot slot, Operation<SkinnedMesh> original) {
        SkinnedMesh mesh = original.call(self, l, m, fm, e, ai, st, slot);
        if (LOTR_EF_CURRENT.get() != null) {
            LOTR_EF_LOG2.info("[lotr_epicfight diag2] {} slot {} -> mesh={} forgeModel={}", LOTR_EF_CURRENT.get(), slot, mesh == null ? "NULL" : mesh.getClass().getSimpleName() + " parts=" + mesh.getAllParts().size(), fm == null ? "null" : fm.getClass().getName());
        }
        return mesh;
    }

    @WrapOperation(method = "renderLayer(Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I[Lyesman/epicfight/api/utils/math/OpenMatrix4f;FFFF)V",
            at = @At(value = "INVOKE", target = "Lyesman/epicfight/client/renderer/patched/layer/WearableItemLayer;renderArmor(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILyesman/epicfight/api/client/model/SkinnedMesh;Lyesman/epicfight/api/model/Armature;FFFLnet/minecraft/resources/ResourceLocation;[Lyesman/epicfight/api/utils/math/OpenMatrix4f;)V"),
            require = 0)
    private void lotrEpicfight$diagDraw(WearableItemLayer<?, ?, ?, ?> self, PoseStack ps, MultiBufferSource buf, int light, SkinnedMesh mesh, Armature arm,
                                        float r, float g, float b, ResourceLocation tex, OpenMatrix4f[] poses, Operation<Void> original) {
        if (LOTR_EF_CURRENT.get() != null) {
            LOTR_EF_LOG2.info("[lotr_epicfight diag2] {} DRAW tex={} rgb=({},{},{}) light={}", LOTR_EF_CURRENT.get(), tex, r, g, b, light);
        }
        original.call(self, ps, buf, light, mesh, arm, r, g, b, tex, poses);
    }
}
