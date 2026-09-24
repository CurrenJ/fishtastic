package grill24.fishtastic.itemeffect;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.FishtasticItemOutlineAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

public class ItemEffectManager {
    private static final WeakHashMap<ItemStack, ItemEffect> CACHE = new WeakHashMap<>();
    private static List<ItemEffect> sortedEffects = null;

    public static ItemEffect getEffectForItem(ItemStack stack) {
        if (stack.isEmpty()) return null;

        ItemEffect cached = CACHE.get(stack);
        if (cached != null) return cached;

        List<ItemEffect> effects = getSortedEffects();

        for (ItemEffect effect : effects) {
            if (effect.matches(stack)) {
                CACHE.put(stack, effect);
                return effect;
            }
        }
        return null;
    }

    public static boolean shouldShowEffect(ItemStack stack) {
        return getEffectForItem(stack) != null;
    }

    public static void clearCache() {
        CACHE.clear();
        sortedEffects = null;
        // Effect definitions (and possibly item models) changed — baked outline-atlas
        // slots are stale. GPU-side clearing is deferred to the render thread.
        FishtasticItemOutlineAtlas.getInstance().invalidate();
    }

    private static List<ItemEffect> getSortedEffects() {
        if (sortedEffects != null) return sortedEffects;

        Registry<ItemEffect> registry = getRegistry();
        if (registry == null) {
            Fishtastic.LOGGER.warn("ItemEffect registry is null - no effects will be loaded");
            return Collections.emptyList();
        }

        List<ItemEffect> effects = new ArrayList<>();
        int totalCount = 0;
        int enabledCount = 0;
        for (ItemEffect effect : registry) {
            totalCount++;
            if (effect.enabled()) {
                effects.add(effect);
                enabledCount++;
                Fishtastic.LOGGER.info("Loaded enabled ItemEffect: texture={}, priority={}, conditions={}",
                        effect.texture(), effect.priority(), effect.conditions().size());
            } else {
                Fishtastic.LOGGER.info("Skipped disabled ItemEffect: texture={}", effect.texture());
            }
        }
        Fishtastic.LOGGER.info("ItemEffect registry loaded: {} total effects, {} enabled", totalCount, enabledCount);

        effects.sort(Comparator.comparingInt(ItemEffect::priority).reversed());
        sortedEffects = effects;

        // PORT A5.4: 26.1.2 registers each effect's glint render types with RenderBuffers here
        // (RenderBuffersHelper). That moves to the client-side ItemEffectRenderData.

        return sortedEffects;
    }

    private static Registry<ItemEffect> getRegistry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            Fishtastic.LOGGER.debug("Cannot get ItemEffect registry - level is null");
            return null;
        }

        try {
            Registry<ItemEffect> registry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.ITEM_EFFECT_REGISTRY_KEY);
            Fishtastic.LOGGER.debug("Successfully accessed ItemEffect registry");
            return registry;
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to access ItemEffect registry", e);
            return null;
        }
    }
}
