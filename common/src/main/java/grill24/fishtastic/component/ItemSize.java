package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A data component that stores an item's size and provides tooltip information.
 * <p>
 * {@code TooltipProvider} doesn't exist before 1.20.5, so on 1.20.1 {@code addToTooltip} is called
 * directly by {@code mixin/ItemStackMixin} rather than through that interface (B2.3).
 */
public record ItemSize(float size) {

    public static final Codec<ItemSize> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("size").forGetter(ItemSize::size)
            ).apply(instance, ItemSize::new)
    );

    public static final BufCodec<ItemSize> STREAM_CODEC = BufCodec.composite(
            BufCodecs.FLOAT,
            ItemSize::size,
            ItemSize::new
    );

    public void addToTooltip(@Nullable Level level, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag) {
        String sizeText = String.format("%.1f", size);

        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.item_size", sizeText)
                .withStyle(ChatFormatting.GRAY));
    }
}
