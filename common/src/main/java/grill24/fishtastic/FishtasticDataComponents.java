package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.component.RodCharmContents;
import grill24.fishtastic.component.RodHookContents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;

public class FishtasticDataComponents {
    public static Holder<DataComponentType<ItemSize>> ITEM_SIZE;
    public static Holder<DataComponentType<FishQuality>> FISH_QUALITY;
    public static Holder<DataComponentType<RodBaitContents>> ROD_BAIT_CONTENTS;
    public static Holder<DataComponentType<RodHookContents>> ROD_HOOK_CONTENTS;
    public static Holder<DataComponentType<RodCharmContents>> ROD_CHARM_CONTENTS;
    public static Holder<DataComponentType<BaitEffect>> BAIT_EFFECT;
    public static Holder<DataComponentType<HookEffect>> HOOK_EFFECT;
    public static Holder<DataComponentType<CharmEffect>> CHARM_EFFECT;
    public static Holder<DataComponentType<FishTankMaterials>> FISH_TANK_MATERIALS;
    public static Holder<DataComponentType<FishTankShape>> FISH_TANK_SHAPE;
    /** Marker-only presence component driving the fishopedia/quest_book alert-texture swap; needs {@code .persistent(...)} or the client can't hash it for container click packets. */
    public static Holder<DataComponentType<Unit>> HAS_ALERT;

    public static void registerDataComponents() {
        ITEM_SIZE = RegistrationApiSided.getInstance().registerDataComponent(
                "item_size",
                builder -> builder
                        .persistent(ItemSize.CODEC)
                        .networkSynchronized(ItemSize.STREAM_CODEC)
        );

        FISH_QUALITY = RegistrationApiSided.getInstance().registerDataComponent(
                "fish_quality",
                builder -> builder
                        .persistent(FishQuality.CODEC)
                        .networkSynchronized(FishQuality.STREAM_CODEC)
        );

        ROD_BAIT_CONTENTS = RegistrationApiSided.getInstance().registerDataComponent(
                "rod_bait_contents",
                builder -> builder
                        .persistent(RodBaitContents.CODEC)
                        .networkSynchronized(RodBaitContents.STREAM_CODEC)
        );

        ROD_HOOK_CONTENTS = RegistrationApiSided.getInstance().registerDataComponent(
                "rod_hook_contents",
                builder -> builder
                        .persistent(RodHookContents.CODEC)
                        .networkSynchronized(RodHookContents.STREAM_CODEC)
        );

        ROD_CHARM_CONTENTS = RegistrationApiSided.getInstance().registerDataComponent(
                "rod_charm_contents",
                builder -> builder
                        .persistent(RodCharmContents.CODEC)
                        .networkSynchronized(RodCharmContents.STREAM_CODEC)
        );

        BAIT_EFFECT = RegistrationApiSided.getInstance().registerDataComponent(
                "bait_effect",
                builder -> builder
                        .persistent(BaitEffect.CODEC)
                        .networkSynchronized(BaitEffect.STREAM_CODEC)
        );

        HOOK_EFFECT = RegistrationApiSided.getInstance().registerDataComponent(
                "hook_effect",
                builder -> builder
                        .persistent(HookEffect.CODEC)
                        .networkSynchronized(HookEffect.STREAM_CODEC)
        );

        CHARM_EFFECT = RegistrationApiSided.getInstance().registerDataComponent(
                "charm_effect",
                builder -> builder
                        .persistent(CharmEffect.CODEC)
                        .networkSynchronized(CharmEffect.STREAM_CODEC)
        );

        FISH_TANK_MATERIALS = RegistrationApiSided.getInstance().registerDataComponent(
                "fish_tank_materials",
                builder -> builder
                        .persistent(FishTankMaterials.CODEC)
                        .networkSynchronized(FishTankMaterials.STREAM_CODEC)
        );

        FISH_TANK_SHAPE = RegistrationApiSided.getInstance().registerDataComponent(
                "fish_tank_shape",
                builder -> builder
                        .persistent(FishTankShape.CODEC)
                        .networkSynchronized(FishTankShape.STREAM_CODEC)
        );

        HAS_ALERT = RegistrationApiSided.getInstance().registerDataComponent(
                "has_alert",
                builder -> builder
                        .persistent(Unit.CODEC)
                        .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
        );
    }
}
