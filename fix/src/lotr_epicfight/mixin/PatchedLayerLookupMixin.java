package lotr_epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

import java.util.Map;

/**
 * Epic Fight looks up its animated replacement of a vanilla render layer by the layer's exact class.
 * LOTR uses subclasses (e.g. lotr.modern.client.y extends HumanoidArmorLayer for NPC armour). Resolve the key to the
 * nearest superclass that Epic Fight knows, for both the containsKey check and the get.
 *  - initLayerLast: no static "RenderOriginalModelLayer" fallback is registered for such subclasses.
 *  - renderLayer:   the subclass is drawn by the superclass' animated layer (WearableItemLayer for armour).
 */
@Mixin(value = PatchedLivingEntityRenderer.class, remap = false)
public abstract class PatchedLayerLookupMixin {
    private static Object lotrEpicfight$resolve(Map<?, ?> map, Object key) {
        if (key instanceof Class<?> c && !map.containsKey(c)) {
            Class<?> s = c.getSuperclass();
            while (s != null && RenderLayer.class.isAssignableFrom(s)) {
                if (map.containsKey(s)) return s;
                s = s.getSuperclass();
            }
        }
        return key;
    }

    @WrapOperation(method = {"initLayerLast", "renderLayer"}, at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z"))
    private boolean lotrEpicfight$containsViaSuper(Map<Object, Object> map, Object key, Operation<Boolean> original) {
        return original.call(map, lotrEpicfight$resolve(map, key));
    }

    @WrapOperation(method = "renderLayer", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object lotrEpicfight$getViaSuper(Map<Object, Object> map, Object key, Operation<Object> original) {
        return original.call(map, lotrEpicfight$resolve(map, key));
    }
}
