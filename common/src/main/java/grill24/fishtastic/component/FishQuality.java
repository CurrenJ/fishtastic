package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A data component that stores a fish's quality tier and provides tooltip information.
 * Quality affects visual effects and indicates rarity/value.
 */
public record FishQuality(Quality quality) implements TooltipProvider {

    public enum Quality implements StringRepresentable {
        COMMON("common", ChatFormatting.GRAY, "Common"),
        UNCOMMON("uncommon", ChatFormatting.GREEN, "Uncommon"),
        RARE("rare", ChatFormatting.BLUE, "Rare"),
        EPIC("epic", ChatFormatting.LIGHT_PURPLE, "Epic"),
        LEGENDARY("legendary", ChatFormatting.GOLD, "Legendary");

        private final String name;
        private final ChatFormatting color;
        private final String displayName;

        Quality(String name, ChatFormatting color, String displayName) {
            this.name = name;
            this.color = color;
            this.displayName = displayName;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }

        public ChatFormatting getColor() {
            return color;
        }

        /** Untranslated fallback name; prefer {@link #getTranslatableName()} for player-facing text. */
        public String getDisplayName() {
            return displayName;
        }

        public Component getTranslatableName() {
            return Component.translatable("fishtastic.quality." + name);
        }

        public static final Codec<Quality> CODEC = StringRepresentable.fromEnum(Quality::values);
    }

    public static final Codec<FishQuality> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Quality.CODEC.fieldOf("quality").forGetter(FishQuality::quality)
            ).apply(instance, FishQuality::new)
    );

    public static final StreamCodec<ByteBuf, FishQuality> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Quality.CODEC),
            FishQuality::quality,
            FishQuality::new
    );

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag, DataComponentGetter components) {
        // Add the quality information to the tooltip with appropriate color
        tooltipAdder.accept(Component.translatable("tooltip.fishtastic.fish_quality", quality.getTranslatableName())
                .withStyle(quality.getColor()));
    }

    /**
     * Check if this quality should render with a visual effect
     */
    public boolean shouldRenderEffect() {
        return quality.ordinal() >= Quality.UNCOMMON.ordinal();
    }

    /**
     * Get the effect intensity (0.0 to 1.0) for rendering effects
     */
    public float getEffectIntensity() {
        return switch (quality) {
            case COMMON -> 0.0f;
            case UNCOMMON, RARE -> 0.3f;
            case EPIC -> 0.6f;
            case LEGENDARY -> 1.0f;
        };
    }
}
