package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

/**
 * A data component that stores an item's size and provides tooltip information.
 */
public record ItemSize(float size) implements TooltipProvider {

    public static final Codec<ItemSize> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("size").forGetter(ItemSize::size)
            ).apply(instance, ItemSize::new)
    );

    public static final StreamCodec<ByteBuf, ItemSize> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            ItemSize::size,
            ItemSize::new
    );

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag, DataComponentGetter components) {
        String sizeText = String.format("%.1f", size);

        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.item_size", sizeText)
                .withStyle(ChatFormatting.GRAY));
    }
}
