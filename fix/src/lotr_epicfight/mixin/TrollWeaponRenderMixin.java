package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PCustomEntityRenderer;

/**
 * LOTR trolls draw their weapon (club / warhammer / battleaxe) with a separate texture (troll/weapons.png).
 * Epic Fight's custom renderer draws a single mesh with the entity texture, so the weapon is a second mesh
 * (lotr_epicfight:entity/<troll>_weapon, rigid on Hand_R) drawn right after the body with the weapon texture.
 * Their armour/outfit layer (TrollRenderer$a) is likewise a third mesh (<troll>_armor, inflated shirt + trousers).
 */
@Mixin(value = PCustomEntityRenderer.class, remap = false)
public abstract class TrollWeaponRenderMixin {
    private static final ResourceLocation WEAPON_TEX = ResourceLocation.fromNamespaceAndPath("lotr", "textures/entity/troll/weapons.png");
    private static final ResourceLocation STONE_TEX = ResourceLocation.fromNamespaceAndPath("lotr", "textures/entity/troll/stone.png");
    private static final Map<EntityType<?>, AssetAccessor<SkinnedMesh>> WEAPONS = new HashMap<>();
    private static final Map<EntityType<?>, AssetAccessor<SkinnedMesh>> ARMORS = new HashMap<>();
    private static Method lotrEf$isStone;
    private static Method lotrEf$outfit;

    private static AssetAccessor<SkinnedMesh> lotrEf$weapon(EntityType<?> type) {
        return lotrEf$extraMesh(WEAPONS, type, "_weapon");
    }

    private static AssetAccessor<SkinnedMesh> lotrEf$extraMesh(Map<EntityType<?>, AssetAccessor<SkinnedMesh>> cache, EntityType<?> type, String suffix) {
        return cache.computeIfAbsent(type, t -> {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            if (!"lotr".equals(key.getNamespace())) return null;
            try {
                AssetAccessor<SkinnedMesh> mesh = Meshes.getOrCreate(ResourceLocation.fromNamespaceAndPath("lotr_epicfight", "entity/" + key.getPath() + suffix),
                        loader -> loader.loadSkinnedMesh(SkinnedMesh::new));
                mesh.get();
                return mesh;
            } catch (Exception e) {
                return null;  // no such mesh for this entity
            }
        });
    }

    private static boolean lotrEf$isLotr(LivingEntity entity) {
        return "lotr".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    /** Same choice as LOTR TrollRenderer.c(Troll): olog-hai armour by entity id parity, mirk troll armour, else outfit_N. */
    private static ResourceLocation lotrEf$armorTexture(LivingEntity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        String path;
        if (id.equals("olog_hai")) {
            path = "textures/entity/troll/olog_hai_armor/" + (entity.getId() & 1) + ".png";
        } else if (id.equals("mirk_troll")) {
            path = "textures/entity/troll/mirk_troll_armor/0.png";
        } else {
            int outfit = 0;
            try {
                if (lotrEf$outfit == null) lotrEf$outfit = entity.getClass().getMethod("outfit");
                outfit = (Integer) lotrEf$outfit.invoke(entity);
            } catch (Exception e) {
            }
            path = "textures/entity/troll/outfit_" + outfit + ".png";
        }
        return ResourceLocation.fromNamespaceAndPath("lotr", path);
    }

    private static boolean lotrEf$stone(LivingEntity entity) {
        try {
            if (lotrEf$isStone == null) lotrEf$isStone = entity.getClass().getMethod("isStone");
            return (Boolean) lotrEf$isStone.invoke(entity);
        } catch (Exception e) {
            return false;
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lnet/minecraft/client/renderer/entity/EntityRenderer;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V",
            at = @At(value = "INVOKE", target = "Lyesman/epicfight/api/client/model/SkinnedMesh;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;IFFFFILyesman/epicfight/api/model/Armature;[Lyesman/epicfight/api/utils/math/OpenMatrix4f;)V"))
    private void lotrEpicfight$drawTrollWeapon(SkinnedMesh mesh, PoseStack poseStack, MultiBufferSource buffer, RenderType renderType, int light,
                                              float r, float g, float b, float a, int overlay, Armature armature, OpenMatrix4f[] poses,
                                              Operation<Void> original, LivingEntity entity) {
        // Epic Fight bug: when the entity glows (e.g. Epic Fight's lock-on/target highlight) PCustomEntityRenderer swaps the
        // body render type for RenderType.outline(tex) only, so the body is drawn into the outline buffer and nowhere
        // else (invisible body). Vanilla draws the normal type through the OutlineBufferSource, which adds the outline.
        RenderType bodyType = renderType;
        if (lotrEf$isLotr(entity) && Minecraft.getInstance().shouldEntityAppearGlowing(entity)) {
            bodyType = RenderType.entityCutoutNoCull(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).getTextureLocation(entity));
        }
        original.call(mesh, poseStack, buffer, bodyType, light, r, g, b, a, overlay, armature, poses);
        if (entity.isInvisible()) return;
        boolean stone = lotrEf$stone(entity);
        AssetAccessor<SkinnedMesh> armor = lotrEf$extraMesh(ARMORS, entity.getType(), "_armor");
        if (armor != null) {
            RenderType armorType = RenderType.entityCutoutNoCull(stone ? STONE_TEX : lotrEf$armorTexture(entity));
            armor.get().draw(poseStack, buffer, armorType, light, r, g, b, a, overlay, armature, poses);
        }
        AssetAccessor<SkinnedMesh> weapon = lotrEf$weapon(entity.getType());
        if (weapon != null) {
            RenderType weaponType = RenderType.entityCutoutNoCull(stone ? STONE_TEX : WEAPON_TEX);
            weapon.get().draw(poseStack, buffer, weaponType, light, r, g, b, a, overlay, armature, poses);
        }
    }
}
