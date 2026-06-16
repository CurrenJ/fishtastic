package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Game tests for ItemSize, FishQuality, BaitEffect, and RodBaitContents data components.
 * All logic is pure in-memory — no blocks or world state needed.
 */
public final class ItemComponentGameTests {

    private ItemComponentGameTests() {}

    // -------------------------------------------------------------------------
    // ItemSize
    // -------------------------------------------------------------------------

    /**
     * ItemSizeHelper.setSize stores the value; getSize retrieves it; hasSize returns true.
     */
    public static void itemSizeSetAndGet(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        helper.assertTrue(!ItemSizeHelper.hasSize(stack), "Fresh stack must not have size component");

        ItemSizeHelper.setSize(stack, 75.5f);
        helper.assertTrue(ItemSizeHelper.hasSize(stack), "Stack must have size after setSize");
        helper.assertTrue(ItemSizeHelper.getSize(stack) == 75.5f,
            "getSize must return 75.5, got " + ItemSizeHelper.getSize(stack));
        helper.succeed();
    }

    /**
     * removeSize clears the component; getSize returns 0 afterwards.
     */
    public static void itemSizeRemove(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        ItemSizeHelper.setSize(stack, 50f);
        ItemSizeHelper.removeSize(stack);

        helper.assertTrue(!ItemSizeHelper.hasSize(stack), "Size must be absent after remove");
        helper.assertTrue(ItemSizeHelper.getSize(stack) == 0f,
            "getSize must return 0 after remove, got " + ItemSizeHelper.getSize(stack));
        helper.succeed();
    }

    /**
     * withSize returns a copy with the new size, leaving the original unchanged.
     */
    public static void itemSizeWithSizeCopies(GameTestHelper helper) {
        ItemStack original = new ItemStack(FishtasticItems.BLUEGILL.value());
        ItemSizeHelper.setSize(original, 40f);

        ItemStack copy = ItemSizeHelper.withSize(original, 80f);
        helper.assertTrue(ItemSizeHelper.getSize(original) == 40f,
            "Original must retain size 40, got " + ItemSizeHelper.getSize(original));
        helper.assertTrue(ItemSizeHelper.getSize(copy) == 80f,
            "Copy must have size 80, got " + ItemSizeHelper.getSize(copy));
        helper.succeed();
    }

    /**
     * setSize on empty ItemStack is a no-op — no component set.
     */
    public static void itemSizeIgnoresEmptyStack(GameTestHelper helper) {
        ItemStack empty = ItemStack.EMPTY;
        ItemSizeHelper.setSize(empty, 50f); // must not throw
        helper.assertTrue(!ItemSizeHelper.hasSize(empty), "Empty stack must not acquire size component");
        helper.succeed();
    }

    /**
     * The ItemSize component is directly accessible via the DataComponentType key.
     */
    public static void itemSizeComponentKey(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        stack.set(FishtasticDataComponents.ITEM_SIZE.value(), new ItemSize(123.4f));
        ItemSize component = stack.get(FishtasticDataComponents.ITEM_SIZE.value());
        helper.assertTrue(component != null, "Component must be present after set");
        helper.assertTrue(component.size() == 123.4f, "Component value must be 123.4, got " + component.size());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // FishQuality
    // -------------------------------------------------------------------------

    /**
     * FishQualityHelper.setQuality stores; getQuality retrieves all five tiers.
     */
    public static void fishQualityAllTiers(GameTestHelper helper) {
        for (FishQuality.Quality tier : FishQuality.Quality.values()) {
            ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
            FishQualityHelper.setQuality(stack, tier);
            FishQuality.Quality read = FishQualityHelper.getQuality(stack);
            helper.assertTrue(read == tier,
                "Expected quality " + tier + ", got " + read);
        }
        helper.succeed();
    }

    /**
     * shouldRenderEffect: false for COMMON, true for UNCOMMON and above.
     */
    public static void fishQualityShouldRenderEffect(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());

        FishQualityHelper.setQuality(stack, FishQuality.Quality.COMMON);
        helper.assertTrue(!FishQualityHelper.shouldRenderEffect(stack),
            "COMMON must not render effect");

        for (FishQuality.Quality tier : new FishQuality.Quality[]{
            FishQuality.Quality.UNCOMMON, FishQuality.Quality.RARE,
            FishQuality.Quality.EPIC, FishQuality.Quality.LEGENDARY}) {
            FishQualityHelper.setQuality(stack, tier);
            helper.assertTrue(FishQualityHelper.shouldRenderEffect(stack),
                tier + " must render effect");
        }
        helper.succeed();
    }

    /**
     * getEffectIntensity returns the documented values for each quality tier.
     */
    public static void fishQualityEffectIntensity(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());

        FishQualityHelper.setQuality(stack, FishQuality.Quality.COMMON);
        helper.assertTrue(new FishQuality(FishQuality.Quality.COMMON).getEffectIntensity() == 0.0f,
            "COMMON intensity must be 0.0");

        helper.assertTrue(new FishQuality(FishQuality.Quality.UNCOMMON).getEffectIntensity() == 0.3f,
            "UNCOMMON intensity must be 0.3");
        helper.assertTrue(new FishQuality(FishQuality.Quality.RARE).getEffectIntensity() == 0.3f,
            "RARE intensity must be 0.3");
        helper.assertTrue(new FishQuality(FishQuality.Quality.EPIC).getEffectIntensity() == 0.6f,
            "EPIC intensity must be 0.6");
        helper.assertTrue(new FishQuality(FishQuality.Quality.LEGENDARY).getEffectIntensity() == 1.0f,
            "LEGENDARY intensity must be 1.0");
        helper.succeed();
    }

    /**
     * getQuality returns null for a stack with no quality component.
     */
    public static void fishQualityNullWhenAbsent(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        helper.assertTrue(FishQualityHelper.getQuality(stack) == null,
            "getQuality must return null for unset stack");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // BaitEffect presets
    // -------------------------------------------------------------------------

    /**
     * WORMS preset has the documented field values.
     */
    public static void baitEffectWormsPreset(GameTestHelper helper) {
        BaitEffect worms = BaitEffect.WORMS;
        helper.assertTrue(worms.luckBonus() == 0.5f,        "WORMS luckBonus must be 0.5");
        helper.assertTrue(worms.treasureChance() == 0.10f,  "WORMS treasureChance must be 0.10");
        helper.assertTrue(worms.targetCountBonus() == 1,    "WORMS targetCountBonus must be 1");
        helper.assertTrue(worms.vanillaFishMultiplier() == 1.0f, "WORMS vanillaFishMultiplier must be 1.0");
        helper.assertTrue(worms.modFishMultiplier() == 0.6f, "WORMS modFishMultiplier must be 0.6");
        helper.assertTrue(worms.qualityBias() == 0.0f,      "WORMS qualityBias must be 0.0");
        helper.assertTrue(worms.exclusiveFishPool().isEmpty(), "WORMS must have no exclusive pool");
        helper.succeed();
    }

    /**
     * BLAZED_GRUB preset declares an exclusive fish pool (exotic_fish tag).
     */
    public static void baitEffectBlazedGrubExclusivePool(GameTestHelper helper) {
        BaitEffect grub = BaitEffect.BLAZED_GRUB;
        helper.assertTrue(grub.exclusiveFishPool().isPresent(),
            "BLAZED_GRUB must have an exclusive fish pool");
        Optional<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> pool = grub.exclusiveFishPool();
        helper.assertTrue(
            pool.get().location().getPath().equals("exotic_fish"),
            "BLAZED_GRUB exclusive pool must be 'exotic_fish', got " + pool.get().location().getPath()
        );
        helper.succeed();
    }

    /**
     * BaitEffect stored as a data component on an ItemStack round-trips via get().
     */
    public static void baitEffectComponentRoundTrip(GameTestHelper helper) {
        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        rod.set(FishtasticDataComponents.BAIT_EFFECT.value(), BaitEffect.GUMMY_WORMS);

        BaitEffect read = rod.get(FishtasticDataComponents.BAIT_EFFECT.value());
        helper.assertTrue(read != null, "BaitEffect component must be present after set");
        helper.assertTrue(read.luckBonus() == BaitEffect.GUMMY_WORMS.luckBonus(),
            "luckBonus must survive component round-trip");
        helper.assertTrue(read.modFishMultiplier() == BaitEffect.GUMMY_WORMS.modFishMultiplier(),
            "modFishMultiplier must survive component round-trip");
        helper.succeed();
    }

    /**
     * NO_BAIT preset has the expected default values.
     */
    public static void baitEffectNoBaitDefaults(GameTestHelper helper) {
        BaitEffect none = BaitEffect.NO_BAIT;
        helper.assertTrue(none.luckBonus() == 0.0f,         "NO_BAIT luckBonus must be 0.0");
        helper.assertTrue(none.modFishMultiplier() == 0.15f, "NO_BAIT modFishMultiplier must be 0.15");
        helper.assertTrue(none.fishGroupAffinities().isEmpty(), "NO_BAIT must have no group affinities");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // RodBaitContents
    // -------------------------------------------------------------------------

    /**
     * RodBaitContents.EMPTY wraps an empty ItemStack, so isEmpty() is true.
     */
    public static void rodBaitContentsEmptyIsEmpty(GameTestHelper helper) {
        helper.assertTrue(RodBaitContents.EMPTY.isEmpty(), "EMPTY must report isEmpty() true");
        helper.assertTrue(RodBaitContents.EMPTY.stack().isEmpty(), "EMPTY must wrap an empty ItemStack");
        helper.succeed();
    }

    /**
     * Wrapping a non-empty ItemStack makes isEmpty() false.
     */
    public static void rodBaitContentsNonEmptyStackIsNotEmpty(GameTestHelper helper) {
        ItemStack worms = new ItemStack(FishtasticItems.WORMS.value());
        RodBaitContents contents = new RodBaitContents(worms);
        helper.assertTrue(!contents.isEmpty(), "Wrapping a non-empty stack must make isEmpty() false");
        helper.succeed();
    }

    /**
     * copyStack() returns an equal-but-distinct ItemStack — mutating the copy must not
     * affect the original stack held by the RodBaitContents.
     */
    public static void rodBaitContentsCopyStackIsDistinct(GameTestHelper helper) {
        ItemStack worms = new ItemStack(FishtasticItems.WORMS.value(), 5);
        RodBaitContents contents = new RodBaitContents(worms);

        ItemStack copy = contents.copyStack();
        helper.assertTrue(copy != worms, "copyStack() must not return the same instance");
        helper.assertTrue(copy.getItem() == worms.getItem() && copy.getCount() == worms.getCount(),
            "copyStack() must be equal in item and count to the original");

        copy.setCount(1);
        helper.assertTrue(worms.getCount() == 5,
            "Mutating the copy must not affect the original stack, original count was " + worms.getCount());
        helper.succeed();
    }
}
