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
        // Only does real work for NO_BAIT. Once any bait is equipped, vanilla fish are hard
        // -excluded from the pool (see FishingMinigameManager), so every candidate in
        // FishtasticFishItem.sampleRandomFish's weighted list is a mod fish getting this same
        // scalar — it cancels out of the weighted-random ratio and has zero effect on catch
        // odds. NO_BAIT is the sole case where mod fish share the pool with (unscaled) vanilla
        // fish, so it's the only preset where this value should differ from 1.0.
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

    // Baseline treasure_chance/trash_chance — also the codec's optionalFieldOf defaults below.
    // Worms and the affinity baits (Small Fish/Calm/Frenzy/Trophy/Gummy) deliberately leave
    // both at these values rather than tuning them per-bait, so tooltipLines() can omit the
    // lines entirely for those baits instead of displaying uninformative default numbers.
    public static final float DEFAULT_TREASURE_CHANCE = 0.1f;
    public static final float DEFAULT_TRASH_CHANCE = 0.05f;

    // No bait: vanilla fishing — mod fish are rare finds. This is the one and only case where
    // vanilla fish are still in the pool at all — see FishingMinigameManager's pool filter,
    // which excludes vanilla fish outright for every other BaitEffect (equality against this
    // constant is the trigger, not modFishMultiplier or any other field). Trash/treasure are
    // deliberately off the shared default — high trash (0.5) and low treasure (0.02) discourage
    // bare-hook fishing as a treasure-farming strategy.
    public static final BaitEffect NO_BAIT = new BaitEffect(
            0.0f, 0.02f, 0.5f, 0, 0.15f, 0.0f, Optional.empty(), List.of());

    // Worms: the moment a player puts on any bait at all, vanilla fish drop out of the pool
    // entirely (see FishingMinigameManager) — Worms is the cheap, guaranteed-mod-fish starter.
    // modFishMultiplier left at 1.0 (no-op — see the field doc above); treasure/trash left at
    // the defaults, same as the other worm-family baits.
    public static final BaitEffect WORMS = new BaitEffect(
            0.0f, DEFAULT_TREASURE_CHANCE, DEFAULT_TRASH_CHANCE, 1, 1.0f, 0.0f, Optional.empty(), List.of());

    // Gummy Worms: quality/size chaser — broad mild mod-fish boost, strong quality bias.
    // Its actual "generalist boost" is entirely rarityFlattening=0.85, a mild, pool-wide
    // version of the specialist baits' per-group flattening — no single tag to target here,
    // so it takes the edge off the highest base_weight fish (bluegill/neon_tetra/blazed_grub)
    // dominating the whole mod-fish pool without going as aggressive as the 0.35-0.45 used on
    // the tighter, tag-scoped specialist pools. Kept soft (0.85, not the old 0.7) specifically
    // so Gummy Worms doesn't out-flatten — and therefore out-perform — Trophy/Frenzy Bait on
    // their own rare tag-mates; see FishtasticItems.TROPHY_BAIT/FRENZY_BAIT for the other side
    // of that fix. modFishMultiplier left at 1.0 (no-op — see the field doc above); it used to
    // be set to 2.0 on the mistaken belief that a flat pool-wide multiplier could out-compete
    // the specialist baits' group affinities, which isn't possible once vanilla is out of the
    // pool — see the 2026-08-10 mod-fish-multiplier audit. Treasure/trash left at the defaults,
    // same as the other worm-family baits.
    public static final BaitEffect GUMMY_WORMS = new BaitEffect(
            0.0f, DEFAULT_TREASURE_CHANCE, DEFAULT_TRASH_CHANCE, 0, 1.0f, 2.0f, Optional.empty(), List.of(), 0.85f);

    // Blazed Grub: treasure hunter + exotic fish only pool — already a focused specialist,
    // so it shouldn't also dodge trash on top of finding treasure and exotic fish.
    public static final BaitEffect BLAZED_GRUB = new BaitEffect(
            0.0f, 0.50f, 0.0f, 0, 1.0f, 0.5f,
            Optional.of(FishtasticItemTags.EXOTIC_FISH), List.of());

    public static final Codec<BaitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("luck_bonus", 0.0f).forGetter(BaitEffect::luckBonus),
            Codec.FLOAT.optionalFieldOf("treasure_chance", DEFAULT_TREASURE_CHANCE).forGetter(BaitEffect::treasureChance),
            Codec.FLOAT.optionalFieldOf("trash_chance", DEFAULT_TRASH_CHANCE).forGetter(BaitEffect::trashChance),
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
        if (treasureChance != DEFAULT_TREASURE_CHANCE) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.treasure_chance", (int) (treasureChance * 100))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (trashChance != DEFAULT_TRASH_CHANCE) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.trash_chance", (int) (trashChance * 100))
                    .withStyle(ChatFormatting.GRAY));
        }
        // Qualitative, same reasoning as the other lines below — "+1"/"-1" is exact, but "more/
        // fewer fish per cast" is what the player actually experiences session to session.
        if (targetCountBonus != 0) {
            String key = targetCountBonus > 0
                    ? "tooltip.fishtastic.bait_effect.target_count_bonus_more"
                    : "tooltip.fishtastic.bait_effect.target_count_bonus_fewer";
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        if (modFishMultiplier != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.mod_fish_multiplier", modFishMultiplier)
                    .withStyle(ChatFormatting.GREEN));
        }
        // Qualitative for the same reason as group affinity below — the actual size/quality
        // distribution shift isn't something a player can reconstruct from a raw bias number.
        // Tiers: 0.25 (the 4 specialist baits) = low, 0.5 (Blazed Grub) = mid, 2.0 (Gummy Worms,
        // the mod's dedicated quality chaser) = high.
        if (qualityBias != 0f) {
            String key = qualityBias >= 1.0f
                    ? "tooltip.fishtastic.bait_effect.quality_bias_high"
                    : qualityBias >= 0.5f
                            ? "tooltip.fishtastic.bait_effect.quality_bias_mid"
                            : "tooltip.fishtastic.bait_effect.quality_bias_low";
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        // Qualitative — only Gummy Worms uses this (0.85; see its field doc above), so a single
        // line covers every case that currently exists.
        if (rarityFlattening != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.bait_effect.rarity_flattening")
                    .withStyle(ChatFormatting.GRAY));
        }
        exclusiveFishPool.ifPresent(tag -> lines.add(Component.translatable("tooltip.fishtastic.bait_effect.exclusive_pool",
                Utility.prettyName(tag.location().getPath())).withStyle(ChatFormatting.GOLD)));
        // Qualitative, not the raw multiplier — the actual catch-odds shift also depends on
        // nonMemberMultiplier and rarityExponent, so a precise number here would be more
        // precision than the player can actually reason about. 2.2 is Frenzy/Trophy's tier
        // (vs. 2.0 for Small Fish/Calm); see FishtasticItems' bait registrations.
        for (FishGroupAffinity affinity : fishGroupAffinities) {
            String key = affinity.multiplier() >= 2.2f
                    ? "tooltip.fishtastic.bait_effect.group_affinity_strong"
                    : "tooltip.fishtastic.bait_effect.group_affinity";
            lines.add(Component.translatable(key, Utility.prettyName(affinity.group().location().getPath()))
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }
}
