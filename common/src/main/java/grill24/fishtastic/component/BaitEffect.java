package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Optional;

public record BaitEffect(
        float luckBonus,
        float treasureChance,
        int targetCountBonus,
        float vanillaFishMultiplier,
        float modFishMultiplier,
        float qualityBias,
        Optional<TagKey<Item>> exclusiveFishPool
) {
    // No bait: vanilla fishing — mod fish are rare finds
    public static final BaitEffect NO_BAIT = new BaitEffect(
            0.0f, 0.167f, 0, 1.0f, 0.15f, 0.0f, Optional.empty());

    // Worms: slightly boosts mod fish above the bare-hook baseline
    public static final BaitEffect WORMS = new BaitEffect(
            0.5f, 0.10f, 1, 1.0f, 0.6f, 0.0f, Optional.empty());

    // Gummy Worms: mod fish unlock — pool shifted heavily toward mod fish, quality boost
    public static final BaitEffect GUMMY_WORMS = new BaitEffect(
            1.0f, 0.10f, 0, 0.2f, 4.0f, 1.5f, Optional.empty());

    // Blazed Grub: treasure hunter + exotic fish only pool
    public static final BaitEffect BLAZED_GRUB = new BaitEffect(
            2.0f, 0.50f, 0, 1.0f, 1.0f, 0.5f,
            Optional.of(TagKey.create(Registries.ITEM, Fishtastic.id("exotic_fish"))));

    public static final Codec<BaitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("luck_bonus", 0.0f).forGetter(BaitEffect::luckBonus),
            Codec.FLOAT.optionalFieldOf("treasure_chance", 0.167f).forGetter(BaitEffect::treasureChance),
            Codec.INT.optionalFieldOf("target_count_bonus", 0).forGetter(BaitEffect::targetCountBonus),
            Codec.FLOAT.optionalFieldOf("vanilla_fish_multiplier", 1.0f).forGetter(BaitEffect::vanillaFishMultiplier),
            Codec.FLOAT.optionalFieldOf("mod_fish_multiplier", 1.0f).forGetter(BaitEffect::modFishMultiplier),
            Codec.FLOAT.optionalFieldOf("quality_bias", 0.0f).forGetter(BaitEffect::qualityBias),
            TagKey.codec(Registries.ITEM).optionalFieldOf("exclusive_fish_pool").forGetter(BaitEffect::exclusiveFishPool)
    ).apply(i, BaitEffect::new));

    public static final StreamCodec<ByteBuf, BaitEffect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
