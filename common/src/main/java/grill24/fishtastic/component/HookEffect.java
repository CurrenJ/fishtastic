package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public record HookEffect(
        float qualityBias,
        float trashChanceDelta,
        float treasureChanceDelta
) {
    public static final HookEffect HOOK = new HookEffect(0.5f, -0.04f, 0.0f);
    // Old, rusted hook: reliably drags up trash and never treasure — trash delta tuned to
    // land at 50% trash chance against the no-bait default (0.12), treasure delta large enough
    // to zero out treasure chance under any bait's base value (clamped at 0 in FishingMinigameManager).
    public static final HookEffect OLD_COPPER_HOOK = new HookEffect(-0.5f, 0.38f, -1.0f);
    // Golden hook: pure treasure finder, no quality or trash change. The flat +0.10 is added
    // before Four-Leaf's x1.5 multiplier (see FishingMinigameManager#generateTargetsForOrigin),
    // so the charm amplifies the hook's bonus rather than stacking beside it: the no-bait base
    // of 1/6 goes 0.167 -> 0.267 on its own, and 0.40 with the charm.
    public static final HookEffect GOLDEN_HOOK = new HookEffect(0.0f, 0.0f, 0.10f);

    public static final Codec<HookEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("quality_bias", 0.0f).forGetter(HookEffect::qualityBias),
            Codec.FLOAT.optionalFieldOf("trash_chance_delta", 0.0f).forGetter(HookEffect::trashChanceDelta),
            Codec.FLOAT.optionalFieldOf("treasure_chance_delta", 0.0f).forGetter(HookEffect::treasureChanceDelta)
    ).apply(i, HookEffect::new));

    public static final BufCodec<HookEffect> STREAM_CODEC = BufCodecs.fromCodec(CODEC);

    // Qualitative, same reasoning as BaitEffect's tooltip lines — sign is what the player
    // actually experiences, the exact magnitude isn't something they can reason about in play.
    // Downsides are red so a hook that's a net loss (the rusted hook) can't be mistaken for an
    // upgrade — it drops out of the treasure loot table, where players expect to find upgrades.
    public List<Component> tooltipLines() {
        List<Component> lines = new ArrayList<>();
        if (qualityBias != 0f) {
            String key = qualityBias > 0
                    ? "tooltip.fishtastic.hook_effect.quality_bias_positive"
                    : "tooltip.fishtastic.hook_effect.quality_bias_negative";
            lines.add(Component.translatable(key).withStyle(style(qualityBias > 0)));
        }
        if (trashChanceDelta != 0f) {
            String key = trashChanceDelta > 0
                    ? "tooltip.fishtastic.hook_effect.trash_chance_delta_more"
                    : "tooltip.fishtastic.hook_effect.trash_chance_delta_less";
            // More trash is the bad direction here, so the sign test is inverted.
            lines.add(Component.translatable(key).withStyle(style(trashChanceDelta < 0)));
        }
        if (treasureChanceDelta != 0f) {
            String key;
            if (treasureChanceDelta <= -1.0f) {
                // Delta this large zeroes treasure chance regardless of bait's base value.
                key = "tooltip.fishtastic.hook_effect.treasure_chance_delta_none";
            } else {
                key = treasureChanceDelta > 0
                        ? "tooltip.fishtastic.hook_effect.treasure_chance_delta_more"
                        : "tooltip.fishtastic.hook_effect.treasure_chance_delta_less";
            }
            lines.add(Component.translatable(key).withStyle(style(treasureChanceDelta > 0)));
        }
        return lines;
    }

    // Green for benefits, red for drawbacks — same valence convention BaitEffect already uses.
    private static ChatFormatting style(boolean beneficial) {
        return beneficial ? ChatFormatting.GREEN : ChatFormatting.RED;
    }
}
