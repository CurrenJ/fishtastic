package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import grill24.fishtastic.itemeffect.condition.AndCondition;
import grill24.fishtastic.itemeffect.condition.ComponentCondition;
import grill24.fishtastic.itemeffect.condition.ComponentValueCondition;
import grill24.fishtastic.itemeffect.condition.ItemTagCondition;
import grill24.fishtastic.util.Utility;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Game tests for the ItemEffect condition rule engine — AndCondition, ComponentCondition,
 * ComponentValueCondition, ItemTagCondition, and ItemEffect.matches() itself. All pure
 * ItemStack + BuiltInRegistries.DATA_COMPONENT_TYPE logic, no datapack/render dependency.
 *
 * ItemEffectManager is intentionally out of scope here — it calls Minecraft.getInstance()
 * directly and is unreachable from a server-side GameTest.
 */
public final class ItemEffectConditionGameTests {

    private ItemEffectConditionGameTests() {}

    /**
     * ItemTagCondition.matches reflects real tag membership: cod is in the vanilla
     * fishes tag, diamond is not.
     */
    public static void itemTagConditionMatchesRealTag(GameTestHelper helper) {
        ItemTagCondition condition = new ItemTagCondition(ItemTags.FISHES.location());
        helper.assertTrue(condition.matches(new ItemStack(Items.COD)),
            "Cod must match the vanilla fishes tag");
        helper.assertTrue(!condition.matches(new ItemStack(Items.DIAMOND)),
            "Diamond must not match the vanilla fishes tag");
        helper.succeed();
    }

    /**
     * ComponentCondition.matches is true iff the stack carries the named component,
     * regardless of its value.
     */
    public static void componentConditionMatchesPresenceOnly(GameTestHelper helper) {
        Identifier itemSizeId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(FishtasticDataComponents.ITEM_SIZE.value());
        ComponentCondition condition = new ComponentCondition(itemSizeId);

        ItemStack withoutComponent = new ItemStack(FishtasticItems.BLUEGILL.value());
        helper.assertTrue(!condition.matches(withoutComponent),
            "Stack without the component must not match");

        ItemStack withComponent = new ItemStack(FishtasticItems.BLUEGILL.value());
        withComponent.set(FishtasticDataComponents.ITEM_SIZE.value(), new ItemSize(50f));
        helper.assertTrue(condition.matches(withComponent),
            "Stack with the component present must match, regardless of its value");
        helper.succeed();
    }

    /**
     * ComponentValueCondition.matches checks a specific JSON field of the component's
     * encoded value — FishQuality's "quality" field is a good real-world fixture.
     */
    public static void componentValueConditionMatchesFieldValue(GameTestHelper helper) {
        Identifier qualityId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(FishtasticDataComponents.FISH_QUALITY.value());
        ComponentValueCondition matchingCondition = new ComponentValueCondition(qualityId, "quality", "rare");
        ComponentValueCondition nonMatchingCondition = new ComponentValueCondition(qualityId, "quality", "legendary");

        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        stack.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(FishQuality.Quality.RARE));

        helper.assertTrue(matchingCondition.matches(stack),
            "Condition targeting the stack's actual quality value must match");
        helper.assertTrue(!nonMatchingCondition.matches(stack),
            "Condition targeting a different quality value must not match");

        ItemStack unset = new ItemStack(FishtasticItems.BLUEGILL.value());
        helper.assertTrue(!matchingCondition.matches(unset),
            "Stack without the component at all must not match");
        helper.succeed();
    }

    /**
     * AndCondition follows allMatch semantics: vacuously true on an empty list, true when
     * every sub-condition matches, false if any one sub-condition fails.
     */
    public static void andConditionSemantics(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        ItemEffectCondition alwaysTrue = new ItemTagCondition(ItemTags.FISHES.location());
        ItemEffectCondition alwaysFalse = new ItemTagCondition(Utility.ft("nonexistent_tag"));

        helper.assertTrue(new AndCondition(List.of()).matches(cod),
            "Empty condition list must be vacuously true");
        helper.assertTrue(new AndCondition(List.of(alwaysTrue, alwaysTrue)).matches(cod),
            "All-true condition list must match");
        helper.assertTrue(!new AndCondition(List.of(alwaysTrue, alwaysFalse)).matches(cod),
            "One failing condition among many must make the AND fail");
        helper.succeed();
    }

    /**
     * ItemEffect.matches(): enabled=false always returns false regardless of conditions;
     * enabled=true requires every condition to match (same allMatch semantics as AndCondition).
     */
    public static void itemEffectMatchesRespectsEnabledAndConditions(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        ItemTagCondition trueCondition = new ItemTagCondition(ItemTags.FISHES.location());
        ItemTagCondition falseCondition = new ItemTagCondition(Utility.ft("nonexistent_tag"));

        ItemEffect disabled = newDummyEffect(List.of(trueCondition), false);
        helper.assertTrue(!disabled.matches(cod),
            "enabled=false must always return false regardless of conditions");

        ItemEffect allTrue = newDummyEffect(List.of(trueCondition), true);
        helper.assertTrue(allTrue.matches(cod),
            "enabled=true with all-true conditions must match");

        ItemEffect oneFalse = newDummyEffect(List.of(trueCondition, falseCondition), true);
        helper.assertTrue(!oneFalse.matches(cod),
            "enabled=true with one failing condition must not match");
        helper.succeed();
    }

    /** outline/render fields are irrelevant to matches() — pass harmless defaults. */
    private static ItemEffect newDummyEffect(List<ItemEffectCondition> conditions, boolean enabled) {
        return new ItemEffect(Utility.ft("test_texture"), conditions, 0, enabled,
            0, 0f, 1f, 1f, false, false, 150f, 5, 0.65f);
    }
}
