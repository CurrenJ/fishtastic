package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticDataComponents;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code rarityFlattening} (defaults to 1.0, i.e. no effect) is the generalist analog of
 * {@link FishGroupAffinity#rarityExponent()} — where that field flattens a fish's weight only
 * when it belongs to the bait's targeted group, this applies {@code weight^rarityFlattening}
 * (anchored the same way, against {@code FishProfile.DEFAULT_BASE_WEIGHT}) to every mod fish
 * that belongs to at least one of {@code FishtasticItemTags.FISH_GROUPS}. Meant for baits with
 * no {@link FishGroupAffinity} of their own — a generalist bait like Gummy Worms has no single
 * tag to scope flattening to, so it flattens across the whole curated taxonomy instead. Fish
 * outside every group tag (not yet categorized) are left untouched by this term — see
 * {@code FishtasticFishItem.getFishingLootWeight}.
 */
public record BaitEffect(
        float luckBonus,
        float treasureChance,
        float trashChance,
        int targetCountBonus,
        float modFishMultiplier,
        float qualityBias,
        Optional<TagKey<Item>> exclusiveFishPool,
        List<FishGroupAffinity> fishGroupAffinities,
        float rarityFlattening
) {
    /**
     * Backward-compatible constructor for baits with no pool-wide rarity flattening (the
     * common case — only generalist baits with no {@link FishGroupAffinity} of their own, like
     * Gummy Worms, have a reason to use it; see {@code rarityFlattening}).
     */
    public BaitEffect(float luckBonus, float treasureChance, float trashChance, int targetCountBonus,
                       float modFishMultiplier, float qualityBias, Optional<TagKey<Item>> exclusiveFishPool,
                       List<FishGroupAffinity> fishGroupAffinities) {
        this(luckBonus, treasureChance, trashChance, targetCountBonus, modFishMultiplier, qualityBias,
                exclusiveFishPool, fishGroupAffinities, 1.0f);
    }
    /**
     * {@code multiplier} applies to fish carrying {@code group}; {@code nonMemberMultiplier}
     * (defaults to 1.0, i.e. no effect) applies to everything else — lets a bait boost a
     * targeted subset and suppress the rest without hard-excluding it from the pool.
     * <p>
     * {@code rarityExponent} (defaults to 1.0, i.e. no effect) is applied to a member fish's raw
     * loot weight as {@code weight^rarityExponent} before {@code multiplier}, only for fish in
     * {@code group} — values below 1.0 compress the spread between common and rare members of
     * that group (temperature-style flattening), so {@code multiplier} keeps controlling the
     * group's overall share of the pool while {@code rarityExponent} controls how evenly that
     * share is spread across the group's members.
     */
    public record FishGroupAffinity(TagKey<Item> group, float multiplier, float nonMemberMultiplier, float rarityExponent) {
        public FishGroupAffinity(TagKey<Item> group, float multiplier, float nonMemberMultiplier) {
            this(group, multiplier, nonMemberMultiplier, 1.0f);
        }

        public static final Codec<FishGroupAffinity> CODEC = RecordCodecBuilder.create(i -> i.group(
                TagKey.codec(Registries.ITEM).fieldOf("group").forGetter(FishGroupAffinity::group),
                Codec.FLOAT.fieldOf("multiplier").forGetter(FishGroupAffinity::multiplier),
                Codec.FLOAT.optionalFieldOf("non_member_multiplier", 1.0f).forGetter(FishGroupAffinity::nonMemberMultiplier),
                Codec.FLOAT.optionalFieldOf("rarity_exponent", 1.0f).forGetter(FishGroupAffinity::rarityExponent)
        ).apply(i, FishGroupAffinity::new));

        public static final StreamCodec<ByteBuf, FishGroupAffinity> STREAM_CODEC =
                ByteBufCodecs.fromCodec(CODEC);
    }

    // No bait: vanilla fishing — mod fish are rare finds. This is the one and only case where
    // vanilla fish are still in the pool at all — see FishingMinigameManager's pool filter,
    // which excludes vanilla fish outright for every other BaitEffect (equality against this
    // constant is the trigger, not modFishMultiplier or any other field).
    public static final BaitEffect NO_BAIT = new BaitEffect(
            0.0f, 0.167f, 0.12f, 0, 0.15f, 0.0f, Optional.empty(), List.of());

    // Worms: the moment a player puts on any bait at all, vanilla fish drop out of the pool
    // entirely (see FishingMinigameManager) — Worms is the cheap, guaranteed-mod-fish starter.
    public static final BaitEffect WORMS = new BaitEffect(
            0.0f, 0.10f, 0.05f, 1, 1.25f, 0.0f, Optional.empty(), List.of());

    // Gummy Worms: quality/size chaser — broad mild mod-fish boost, strong quality bias.
    // Deliberately weaker on raw mod-fish weight than the specialist baits (Small Fish/Calm/
    // Frenzy/Trophy Bait), so it stays the generalist "biggest catch" tool rather than also
    // being the best way to farm any one subset. rarityFlattening=0.85 is a mild, pool-wide
    // version of the specialist baits' per-group flattening — no single tag to target here,
    // so it takes the edge off the highest base_weight fish (bluegill/neon_tetra/blazed_grub)
    // dominating the whole mod-fish pool without going as aggressive as the 0.35-0.45 used on
    // the tighter, tag-scoped specialist pools. Kept soft (0.85, not the old 0.7) specifically
    // so Gummy Worms doesn't out-flatten — and therefore out-perform — Trophy/Frenzy Bait on
    // their own rare tag-mates; see FishtasticItems.TROPHY_BAIT/FRENZY_BAIT for the other side
    // of that fix.
    public static final BaitEffect GUMMY_WORMS = new BaitEffect(
            0.0f, 0.10f, 0.05f, 0, 2.0f, 2.0f, Optional.empty(), List.of(), 0.85f);

    // Blazed Grub: treasure hunter + exotic fish only pool — already a focused specialist,
    // so it shouldn't also dodge trash on top of finding treasure and exotic fish.
    public static final BaitEffect BLAZED_GRUB = new BaitEffect(
            0.0f, 0.50f, 0.0f, 0, 1.0f, 0.5f,
            Optional.of(FishtasticItemTags.EXOTIC_FISH), List.of());

    public static final Codec<BaitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("luck_bonus", 0.0f).forGetter(BaitEffect::luckBonus),
            Codec.FLOAT.optionalFieldOf("treasure_chance", 0.167f).forGetter(BaitEffect::treasureChance),
            Codec.FLOAT.optionalFieldOf("trash_chance", 0.12f).forGetter(BaitEffect::trashChance),
            Codec.INT.optionalFieldOf("target_count_bonus", 0).forGetter(BaitEffect::targetCountBonus),
            Codec.FLOAT.optionalFieldOf("mod_fish_multiplier", 1.0f).forGetter(BaitEffect::modFishMultiplier),
            Codec.FLOAT.optionalFieldOf("quality_bias", 0.0f).forGetter(BaitEffect::qualityBias),
            TagKey.codec(Registries.ITEM).optionalFieldOf("exclusive_fish_pool").forGetter(BaitEffect::exclusiveFishPool),
            FishGroupAffinity.CODEC.listOf().optionalFieldOf("fish_group_affinities", List.of()).forGetter(BaitEffect::fishGroupAffinities),
            Codec.FLOAT.optionalFieldOf("rarity_flattening", 1.0f).forGetter(BaitEffect::rarityFlattening)
    ).apply(i, BaitEffect::new));

    public static final StreamCodec<ByteBuf, BaitEffect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /**
     * Reads the effective {@link BaitEffect} off a bait item stack — the item's default
     * {@code BAIT_EFFECT} component, scaled up if the stack also carries a {@link FishQuality}
     * (as caught Blazed Grubs do). Returns {@code null} if the stack has no bait effect at all.
     */
    @Nullable
    public static BaitEffect fromStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        BaitEffect base = stack.get(FishtasticDataComponents.BAIT_EFFECT.value());
        if (base == null) return null;
        FishQuality fishQuality = stack.get(FishtasticDataComponents.FISH_QUALITY.value());
        return fishQuality != null ? base.scaledByQuality(fishQuality.quality()) : base;
    }

    /**
     * Scales this effect up for a higher-quality catch of the bait itself (currently only
     * reachable by Blazed Grub, the only item both catchable as a fish and usable as bait).
     * +10% per quality tier above Common, capping at +40% for Legendary; Legendary catches
     * also grant one extra target as a bonus reward.
     */
    public BaitEffect scaledByQuality(FishQuality.Quality quality) {
        float scale = 1.0f + quality.ordinal() * 0.1f;
        int bonusTargets = quality == FishQuality.Quality.LEGENDARY ? 1 : 0;
        if (scale == 1.0f && bonusTargets == 0) return this;
        return new BaitEffect(
                luckBonus * scale,
                Math.min(1.0f, treasureChance * scale),
                trashChance,
                targetCountBonus + bonusTargets,
                modFishMultiplier * scale,
                qualityBias * scale,
                exclusiveFishPool,
                fishGroupAffinities,
                rarityFlattening
        );
    }

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
