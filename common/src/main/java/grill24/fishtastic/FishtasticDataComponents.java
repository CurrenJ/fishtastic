package grill24.fishtastic;

import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.ComponentKey;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.component.RodCharmContents;
import grill24.fishtastic.component.RodHookContents;
import net.minecraft.util.Unit;

import com.mojang.serialization.Codec;

/**
 * Same class and field names as on 1.21.1, but every field is a {@link ComponentKey} (an NBT-key
 * identifier, docs/backport-pass2/track-b-1.20.1.md B2.1) instead of a
 * {@code Holder<DataComponentType<T>>}. Call sites are unchanged — they never name
 * {@code DataComponentType} themselves, only {@link FishtasticItemData}'s facade methods.
 */
public class FishtasticDataComponents {
    public static final ComponentKey<ItemSize> ITEM_SIZE = ComponentKey.of("item_size", ItemSize.CODEC);
    public static final ComponentKey<FishQuality> FISH_QUALITY = ComponentKey.of("fish_quality", FishQuality.CODEC);
    public static final ComponentKey<RodBaitContents> ROD_BAIT_CONTENTS = ComponentKey.of("rod_bait_contents", RodBaitContents.CODEC);
    public static final ComponentKey<RodHookContents> ROD_HOOK_CONTENTS = ComponentKey.of("rod_hook_contents", RodHookContents.CODEC);
    public static final ComponentKey<RodCharmContents> ROD_CHARM_CONTENTS = ComponentKey.of("rod_charm_contents", RodCharmContents.CODEC);
    public static final ComponentKey<BaitEffect> BAIT_EFFECT = ComponentKey.of("bait_effect", BaitEffect.CODEC);
    public static final ComponentKey<HookEffect> HOOK_EFFECT = ComponentKey.of("hook_effect", HookEffect.CODEC);
    public static final ComponentKey<CharmEffect> CHARM_EFFECT = ComponentKey.of("charm_effect", CharmEffect.CODEC);
    public static final ComponentKey<FishTankMaterials> FISH_TANK_MATERIALS = ComponentKey.of("fish_tank_materials", FishTankMaterials.CODEC);
    public static final ComponentKey<FishTankShape> FISH_TANK_SHAPE = ComponentKey.of("fish_tank_shape", FishTankShape.CODEC);
    /** Marker-only presence component driving the fishopedia/quest_book alert-texture swap. */
    public static final ComponentKey<Unit> HAS_ALERT = ComponentKey.of("has_alert", Codec.unit(Unit.INSTANCE));

    /** No-op on 1.20.1: components are NBT keys, nothing to register. Kept so {@code Fishtastic#init} is identical on every branch. */
    public static void registerDataComponents() {}
}
