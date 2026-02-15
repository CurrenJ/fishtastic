package grill24.fishtastic.itemeffect;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.RenderBuffersHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
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
        Fishtastic.LOGGER.debug("Checking {} effects for item: {}", effects.size(), stack.getItem());

        for (ItemEffect effect : effects) {
            if (effect.matches(stack)) {
                Fishtastic.LOGGER.info("Item {} matched effect: texture={}, priority={}",
                        stack.getItem(), effect.texture(), effect.priority());
                CACHE.put(stack, effect);
                return effect;
            }
        }
        Fishtastic.LOGGER.debug("No effects matched for item: {}", stack.getItem());
        return null;
    }

    public static boolean shouldShowEffect(ItemStack stack) {
        return getEffectForItem(stack) != null;
    }

    public static List<RenderType> getAllRenderTypes() {
        List<ItemEffect> effects = getSortedEffects();
        if (effects.isEmpty()) return Collections.emptyList();

        List<RenderType> renderTypes = new ArrayList<>();
        for (ItemEffect effect : effects) {
            renderTypes.addAll(effect.getAllRenderTypes());
        }
        return renderTypes;
    }

    public static void clearCache() {
        CACHE.clear();
        sortedEffects = null;
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

        // Dynamically register all render types from the loaded effects
        registerRenderTypes(effects);

        return sortedEffects;
    }

    private static void registerRenderTypes(List<ItemEffect> effects) {
        try {
            List<RenderType> renderTypes = getAllRenderTypes();
            if (renderTypes.isEmpty()) {
                Fishtastic.LOGGER.debug("No render types to register");
                return;
            }

            // Access RenderBuffers through Minecraft instance
            Minecraft mc = Minecraft.getInstance();
            RenderBuffers renderBuffers = mc.renderBuffers();

            if (renderBuffers instanceof RenderBuffersHelper helper) {
                helper.fishtastic$addRenderTypesToBuffer(renderTypes);
                Fishtastic.LOGGER.info("Registered {} render types for {} ItemEffects", renderTypes.size(), effects.size());
            } else {
                Fishtastic.LOGGER.error("RenderBuffers does not implement RenderBuffersHelper - mixin may not have applied");
            }
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Failed to dynamically register ItemEffect render types", e);
        }
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
