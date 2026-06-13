package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.component.RodBaitContents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;

public class FishtasticDataComponents {
    public static Holder<DataComponentType<ItemSize>> ITEM_SIZE;
    public static Holder<DataComponentType<FishQuality>> FISH_QUALITY;
    public static Holder<DataComponentType<RodBaitContents>> ROD_BAIT_CONTENTS;
    public static Holder<DataComponentType<BaitEffect>> BAIT_EFFECT;

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

        ROD_BAIT_CONTENTS = RegistrationApiSided.getInstance().registerDataComponent(
                "rod_bait_contents",
                builder -> builder
                        .persistent(RodBaitContents.CODEC)
                        .networkSynchronized(RodBaitContents.STREAM_CODEC)
        );

        BAIT_EFFECT = RegistrationApiSided.getInstance().registerDataComponent(
                "bait_effect",
                builder -> builder
                        .persistent(BaitEffect.CODEC)
                        .networkSynchronized(BaitEffect.STREAM_CODEC)
        );
    }
}
