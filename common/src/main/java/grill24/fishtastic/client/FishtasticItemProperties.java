package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * PORT-ONLY (1.21.1): the item-model predicates the generated item models test in their
 * {@code overrides}. 26.1 selects these variants with data-driven item models instead, so this
 * class has no 26.1 counterpart.
 *
 * <p>{@code ItemProperties.register} has a different signature per loader (vanilla's private
 * {@code ClampedItemPropertyFunction} overload, widened by Fabric API, vs NeoForge's public
 * {@code ItemPropertyFunction} patch), so each loader passes its own {@link Registrar}.
 */
public final class FishtasticItemProperties {
    @FunctionalInterface
    public interface Registrar {
        void register(Item item, ResourceLocation id, ClampedItemPropertyFunction function);
    }

    public static void register(Registrar registrar) {
        // Vanilla registers minecraft:cast only for Items.FISHING_ROD.
        ResourceLocation cast = Ids.withDefaultNamespace("cast");
        registrar.register(FishtasticItems.COPPER_FISHING_ROD.value(), cast, FishtasticItemProperties::cast);
        registrar.register(FishtasticItems.OBSIDIAN_FISHING_ROD.value(), cast, FishtasticItemProperties::cast);

        ResourceLocation hasAlert = Ids.of(Fishtastic.MOD_ID, "has_alert");
        ClampedItemPropertyFunction alert = (stack, level, entity, seed) ->
                FishtasticItemData.has(stack, FishtasticDataComponents.HAS_ALERT) ? 1.0F : 0.0F;
        registrar.register(FishtasticItems.FISHOPEDIA.value(), hasAlert, alert);
        registrar.register(FishtasticItems.QUEST_BOOK.value(), hasAlert, alert);
    }

    /** Vanilla's {@code minecraft:cast} function for {@code Items.FISHING_ROD} (1.21.1 {@code ItemProperties}). */
    private static float cast(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (entity == null) {
            return 0.0F;
        }
        boolean mainHand = entity.getMainHandItem() == stack;
        boolean offHand = entity.getOffhandItem() == stack;
        if (entity.getMainHandItem().getItem() instanceof FishingRodItem) {
            offHand = false;
        }
        return (mainHand || offHand) && entity instanceof Player player && player.fishing != null ? 1.0F : 0.0F;
    }

    private FishtasticItemProperties() {}
}
