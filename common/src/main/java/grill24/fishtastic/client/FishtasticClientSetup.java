package grill24.fishtastic.client;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.PileOfFishItemModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.lang.reflect.Field;

public final class FishtasticClientSetup {
    @SuppressWarnings("unchecked")
    public static void registerItemModelTypes() {
        // ItemModels.ID_MAPPER is private — access widener works on Fabric but not
        // NeoForge, so we fall back to reflection when direct access fails.
        try {
            Field field = ItemModels.class.getDeclaredField("ID_MAPPER");
            field.setAccessible(true);
            ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> idMapper =
                    (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>>) field.get(null);
            idMapper.put(
                    Fishtastic.id("pile_of_fish_layers"),
                    PileOfFishItemModel.Unbaked.MAP_CODEC
            );
            Fishtastic.LOGGER.info("Registered pile_of_fish_layers item model type.");
        } catch (ReflectiveOperationException e) {
            Fishtastic.LOGGER.error("Failed to register pile_of_fish_layers item model type!", e);
        }
    }
}


