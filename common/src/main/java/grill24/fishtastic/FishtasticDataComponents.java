package grill24.fishtastic;

import com.mojang.serialization.Codec;
import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

public class FishtasticDataComponents {
    public static Holder<DataComponentType<Float>> ITEM_SIZE;

    public static void registerDataComponents() {
        ITEM_SIZE = RegistrationApiSided.getInstance().registerDataComponent(
                "item_size",
                builder -> builder
                        .persistent(Codec.FLOAT)
                        .networkSynchronized(ByteBufCodecs.FLOAT)
        );
    }
}
