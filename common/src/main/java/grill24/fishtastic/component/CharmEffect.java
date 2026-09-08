package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.util.Utility;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public record CharmEffect(
        float inputForceMultiplier,
        boolean showRarityOutline,
        float trashChanceDelta,
        float treasureChanceMultiplier,
        boolean forceNightFishing,
        float nightMultiplierBonus,
        List<BaitEffect.FishGroupAffinity> fishGroupAffinities,
        boolean showTopWeightedFish,
        boolean autoPileFish,
        float baitSaveChance
) {

    public static final CharmEffect AMETHYST_CHARM = new CharmEffect(
            1.2f, false, 0.0f, 1.0f, false, 1.0f, List.of(), false, false, 0.0f);

    public static final CharmEffect CRYSTAL_BALL_CHARM = new CharmEffect(
            1.0f, true, 0.0f, 1.0f, false, 1.0f, List.of(), false, false, 0.0f);

    // Four Leaf Charm: pure treasure hunter, no trash-dodging (that's Amethyst/hook territory).
    // x1.5 is a proportional boost, not a flat delta, so it scales with the bait: default 0.10
    // treasure chance -> 0.15, and Blazed Grub's already-high 0.50 base -> 0.75 treasure chance
    // with 0 trash. That's high, but it's Blazed Grub's own base rate doing most of the work -
    // Four Leaf just amplifies whatever treasure rate is already in play instead of stacking an
    // independent flat bonus on top.
    public static final CharmEffect FOUR_LEAF_CHARM = new CharmEffect(
            1.0f, false, 0.0f, 1.5f, false, 1.0f, List.of(), false, false, 0.0f);

    public static final CharmEffect LUNA_CHARM = new CharmEffect(
            1.0f, false, 0.0f, 1.0f, true, 1.25f, List.of(), false, false, 0.0f);

    public static final CharmEffect BANANA_CHARM = new CharmEffect(
            1.0f, false, 0.0f, 1.0f, false, 1.0f,
            List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.COLOR_YELLOW, 1.5f, 1.0f)), false, false, 0.0f);

    public static final CharmEffect ANGLERS_ALMANAC = new CharmEffect(
            1.0f, false, 0.0f, 1.0f, false, 1.0f, List.of(), true, false, 0.0f);

    public static final CharmEffect LITTLE_FISH_BOX = new CharmEffect(
            1.0f, false, 0.0f, 1.0f, false, 1.0f, List.of(), false, true, 0.0f);

    public static final CharmEffect BAIT_BUDDY_CHARM = new CharmEffect(
            1.0f, false, 0.0f, 1.0f, false, 1.0f, List.of(), false, false, 0.5f);

    public static final Codec<CharmEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("input_force_multiplier", 1.0f).forGetter(CharmEffect::inputForceMultiplier),
            Codec.BOOL.optionalFieldOf("show_rarity_outline", false).forGetter(CharmEffect::showRarityOutline),
            Codec.FLOAT.optionalFieldOf("trash_chance_delta", 0.0f).forGetter(CharmEffect::trashChanceDelta),
            Codec.FLOAT.optionalFieldOf("treasure_chance_multiplier", 1.0f).forGetter(CharmEffect::treasureChanceMultiplier),
            Codec.BOOL.optionalFieldOf("force_night_fishing", false).forGetter(CharmEffect::forceNightFishing),
            Codec.FLOAT.optionalFieldOf("night_multiplier_bonus", 1.0f).forGetter(CharmEffect::nightMultiplierBonus),
            BaitEffect.FishGroupAffinity.CODEC.listOf().optionalFieldOf("fish_group_affinities", List.of()).forGetter(CharmEffect::fishGroupAffinities),
            Codec.BOOL.optionalFieldOf("show_top_weighted_fish", false).forGetter(CharmEffect::showTopWeightedFish),
            Codec.BOOL.optionalFieldOf("auto_pile_fish", false).forGetter(CharmEffect::autoPileFish),
            Codec.FLOAT.optionalFieldOf("bait_save_chance", 0.0f).forGetter(CharmEffect::baitSaveChance)
    ).apply(i, CharmEffect::new));

    public static final StreamCodec<ByteBuf, CharmEffect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public List<Component> tooltipLines() {
        List<Component> lines = new ArrayList<>();
        if (inputForceMultiplier != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.input_force_multiplier", inputForceMultiplier)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (showRarityOutline) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.show_rarity_outline")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (trashChanceDelta != 0.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.trash_chance_delta", (int) (trashChanceDelta * 100))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (treasureChanceMultiplier != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.treasure_chance_multiplier", treasureChanceMultiplier)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (forceNightFishing) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.force_night_fishing")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (nightMultiplierBonus != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.night_multiplier_bonus", nightMultiplierBonus)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        for (BaitEffect.FishGroupAffinity affinity : fishGroupAffinities) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.group_affinity",
                            Utility.prettyName(affinity.group().location().getPath()), affinity.multiplier())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (showTopWeightedFish) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.show_top_weighted_fish")
                    .withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.passive_inventory")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (autoPileFish) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.auto_pile_fish")
                    .withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.passive_inventory")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (baitSaveChance != 0.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.bait_save_chance", (int) (baitSaveChance * 100))
                    .withStyle(ChatFormatting.BLUE));
        }
        return lines;
    }
}
