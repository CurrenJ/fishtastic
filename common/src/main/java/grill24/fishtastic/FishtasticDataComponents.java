package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;

public class FishtasticDataComponents {
    public static Holder<DataComponentType<ItemSize>> ITEM_SIZE;
    public static Holder<DataComponentType<FishQuality>> FISH_QUALITY;

    public static void registerDataComponents() {
        ITEM_SIZE = RegistrationApiSided.getInstance().registerDataComponent(
                "item_size",
                builder -> builder
                        .persistent(ItemSize.CODEC)
                        .networkSynchronized(ItemSize.STREAM_CODEC)
        );

        FISH_QUALITY = RegistrationApiSided.getInstance().registerDataComponent(
                "fish_quality",
                builder -> builder
                        .persistent(FishQuality.CODEC)
                        .networkSynchronized(FishQuality.STREAM_CODEC)
        );
    }
}
