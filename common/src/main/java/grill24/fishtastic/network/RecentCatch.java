package grill24.fishtastic.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One individual fish a player pulled out of the water, kept as a short rolling history per player
 * (see {@code FishCatchSavedData}) on top of the aggregate per-species totals. Aggregates can't
 * answer "what did they catch lately", which is what the leaderboard podium's Fish Pile shows.
 */
public record RecentCatch(Identifier fishType, float size, FishQuality.Quality quality) {

    public static final Codec<RecentCatch> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("fish").forGetter(RecentCatch::fishType),
                    Codec.FLOAT.fieldOf("size").forGetter(RecentCatch::size),
                    FishQuality.Quality.CODEC.fieldOf("quality").forGetter(RecentCatch::quality)
            ).apply(instance, RecentCatch::new));

    private static final StreamCodec<ByteBuf, FishQuality.Quality> QUALITY_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> FishQuality.Quality.values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, RecentCatch> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            RecentCatch::fishType,
            ByteBufCodecs.FLOAT,
            RecentCatch::size,
            QUALITY_STREAM_CODEC,
            RecentCatch::quality,
            RecentCatch::new
    );

    /** Rebuilds the caught fish as a real item stack, sized and graded as it was when landed. */
    public ItemStack toStack() {
        Item item = BuiltInRegistries.ITEM.getOptional(fishType).orElse(null);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        ItemSizeHelper.setSize(stack, size);
        FishQualityHelper.setQuality(stack, quality);
        return stack;
    }
}
