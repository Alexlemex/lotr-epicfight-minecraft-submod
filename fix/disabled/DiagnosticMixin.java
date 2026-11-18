package lotr_epicfight.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary diagnostics: logs once per LOTR entity type what Epic Fight does with its render layers and armour. */
@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public abstract class DiagnosticMixin {
    private static final Logger LOTR_EF_LOG = LogUtils.getLogger();
    private static final Set<String> LOTR_EF_SEEN = ConcurrentHashMap.newKeySet();

    @Shadow @Final protected Map<Class<?>, ?> patchedLayers;

    @Inject(method = "renderLayer", at = @At("HEAD"), require = 0)
    private void lotrEpicfight$diagnose(LivingEntityRenderer<?, ?> renderer, yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch, LivingEntity entity,
                                        yesman.epicfight.api.utils.math.OpenMatrix4f[] poses, net.minecraft.client.renderer.MultiBufferSource buffer,
                                        com.mojang.blaze3d.vertex.PoseStack poseStack, int light, float partialTicks, CallbackInfo ci) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (!type.startsWith("lotr:") || !LOTR_EF_SEEN.add(type)) return;
        StringBuilder sb = new StringBuilder("[lotr_epicfight diag] ").append(type)
            .append(" renderer=").append(renderer.getClass().getName())
            .append(" patchedRenderer=").append(this.getClass().getName()).append('\n');
        java.util.List<?> layerList;
        try { java.lang.reflect.Field f = LivingEntityRenderer.class.getDeclaredField("layers"); f.setAccessible(true); layerList = (java.util.List<?>) f.get(renderer); }
        catch (Exception e) { layerList = java.util.List.of(); sb.append("   (cannot read layers: ").append(e).append(")\n"); }
        for (Object layer : layerList) {
            Class<?> c = layer.getClass();
            Object direct = this.patchedLayers.get(c);
            Class<?> s = c;
            Object viaSuper = null;
            while (s != null && viaSuper == null) { viaSuper = this.patchedLayers.get(s); s = s.getSuperclass(); }
            sb.append("   layer ").append(c.getName()).append(" (super ").append(c.getSuperclass().getName()).append(") -> direct=")
              .append(direct == null ? "none" : direct.getClass().getSimpleName())
              .append(" viaSuper=").append(viaSuper == null ? "none" : viaSuper.getClass().getSimpleName()).append('\n');
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack st = entity.getItemBySlot(slot);
            if (!st.isEmpty()) {
                sb.append("   slot ").append(slot).append(" = ").append(BuiltInRegistries.ITEM.getKey(st.getItem()))
                  .append(" class=").append(st.getItem().getClass().getName())
                  .append(" isArmorItem=").append(st.getItem() instanceof ArmorItem)
                  .append(st.getItem() instanceof ArmorItem ai ? " armorSlot=" + ai.getEquipmentSlot() : "").append('\n');
            }
        }
        LOTR_EF_LOG.info(sb.toString());
    }
}
