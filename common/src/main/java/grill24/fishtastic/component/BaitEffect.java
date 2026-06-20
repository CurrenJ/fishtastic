package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.util.Utility;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record BaitEffect(
        float luckBonus,
        float treasureChance,
        float trashChance,
        int targetCountBonus,
        float vanillaFishMultiplier,
        float modFishMultiplier,
        float qualityBias,
        Optional<TagKey<Item>> exclusiveFishPool,
        List<FishGroupAffinity> fishGroupAffinities
) {
    public record FishGroupAffinity(TagKey<Item> group, float multiplier) {
        public static final Codec<FishGroupAffinity> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.ITEM).fieldOf("group").forGetter(FishGroupAffinity::group),
                Codec.FLOAT.fieldOf("multiplier").forGetter(FishGroupAffinity::multiplier)
        ).apply(i, FishGroupAffinity::new));

        public static final StreamCodec<ByteBuf, FishGroupAffinity> STREAM_CODEC =
                ByteBufCodecs.fromCodec(CODEC);
    }

    // No bait: vanilla fishing — mod fish are rare finds
    public static final BaitEffect NO_BAIT = new BaitEffect(
            0.0f, 0.167f, 0.12f, 0, 1.0f, 0.15f, 0.0f, Optional.empty(), List.of());

    // Worms: slightly boosts mod fish above the bare-hook baseline
    public static final BaitEffect WORMS = new BaitEffect(
            0.5f, 0.10f, 0.05f, 1, 1.0f, 1.25f, 0.0f, Optional.empty(), List.of());

    // Gummy Worms: quality/size chaser — broad mild mod-fish boost, strong quality bias.
    // Deliberately weaker on raw mod-fish weight than the exclusive-pool specialist baits
    // (Freshwater/Ocean/Predator/Deep Sea Bait), so it stays the generalist "biggest catch"
    // tool rather than also being the best way to farm any one species.
    public static final BaitEffect GUMMY_WORMS = new BaitEffect(
            1.0f, 0.10f, 0.05f, 0, 0.3f, 2.0f, 2.0f, Optional.empty(), List.of());

    // Blazed Grub: treasure hunter + exotic fish only pool — already a focused specialist,
    // so it shouldn't also dodge trash on top of finding treasure and exotic fish.
    public static final BaitEffect BLAZED_GRUB = new BaitEffect(
            2.0f, 0.50f, 0.0f, 0, 1.0f, 1.0f, 0.5f,
            Optional.of(FishtasticItemTags.EXOTIC_FISH), List.of());

    public static final Codec<BaitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("luck_bonus", 0.0f).forGetter(BaitEffect::luckBonus),
            Codec.FLOAT.optionalFieldOf("treasure_chance", 0.167f).forGetter(BaitEffect::treasureChance),
            Codec.FLOAT.optionalFieldOf("trash_chance", 0.12f).forGetter(BaitEffect::trashChance),
            Codec.INT.optionalFieldOf("target_count_bonus", 0).forGetter(BaitEffect::targetCountBonus),
            Codec.FLOAT.optionalFieldOf("vanilla_fish_multiplier", 1.0f).forGetter(BaitEffect::vanillaFishMultiplier),
            Codec.FLOAT.optionalFieldOf("mod_fish_multiplier", 1.0f).forGetter(BaitEffect::modFishMultiplier),
            Codec.FLOAT.optionalFieldOf("quality_bias", 0.0f).forGetter(BaitEffect::qualityBias),
            TagKey.codec(Registries.ITEM).optionalFieldOf("exclusive_fish_pool").forGetter(BaitEffect::exclusiveFishPool),
            FishGroupAffinity.CODEC.listOf().optionalFieldOf("fish_group_affinities", List.of()).forGetter(BaitEffect::fishGroupAffinities)
    ).apply(i, BaitEffect::new));

    public static final StreamCodec<ByteBuf, BaitEffect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** Player-facing description of this bait's effects, for use in item/rod tooltips. */
    public List<Component> tooltipLines() {
        List<Component> lines = new ArrayList<>();
        if (luckBonus != 0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.luck", luckBonus).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.fishtastic.bait_effect.treasure_chance", (int) (treasureChance * 100))
                .withStyle(ChatFormatting.GRAY));
        if (trashChance != 0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.trash_chance", (int) (trashChance * 100))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (targetCountBonus != 0) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.target_count_bonus", targetCountBonus)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (vanillaFishMultiplier != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.vanilla_fish_multiplier", vanillaFishMultiplier)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (modFishMultiplier != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.mod_fish_multiplier", modFishMultiplier)
                    .withStyle(ChatFormatting.GREEN));
        }
        if (qualityBias != 0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.quality_bias", qualityBias)
                    .withStyle(ChatFormatting.GRAY));
        }
        exclusiveFishPool.ifPresent(tag -> lines.add(Component.translatable("tooltip.fishtastic.bait_effect.exclusive_pool",
                Utility.prettyName(tag.location().getPath())).withStyle(ChatFormatting.GOLD)));
        for (FishGroupAffinity affinity : fishGroupAffinities) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.group_affinity",
                            Utility.prettyName(affinity.group().location().getPath()), affinity.multiplier())
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }
}
