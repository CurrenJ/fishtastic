package grill24.fishtastic.component;

import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip, defaults, normalization and {@code HAS_ALERT} for {@link ComponentKey} — the 1.20.1
 * NBT stand-in for 1.21.1 data components (docs/backport-pass2/track-b-1.20.1.md B2.1).
 */
class ComponentKeyTest {
    /** {@code ItemStack}'s static init reads {@code BuiltInRegistries.ITEM}; see FishSphereContainerTest's note. */
    @BeforeAll
    static void bootstrapRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final ComponentKey<ItemSize> ITEM_SIZE = ComponentKey.of("test_item_size", ItemSize.CODEC);
    private static final ComponentKey<Unit> HAS_ALERT = ComponentKey.of("test_has_alert", com.mojang.serialization.Codec.unit(Unit.INSTANCE));

    @Test
    void absentKeyReturnsNullWithNoRegisteredDefault() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertNull(ITEM_SIZE.get(stack));
        assertFalse(ITEM_SIZE.has(stack));
    }

    @Test
    void setThenGetRoundTrips() {
        ItemStack stack = new ItemStack(Items.STICK);
        ITEM_SIZE.set(stack, new ItemSize(2.5f));
        assertEquals(new ItemSize(2.5f), ITEM_SIZE.get(stack));
        assertTrue(ITEM_SIZE.has(stack));
    }

    @Test
    void fallsBackToRegisteredItemDefault() {
        ItemComponentDefaults.register(Items.STICK, ITEM_SIZE, new ItemSize(1.0f));
        try {
            ItemStack stack = new ItemStack(Items.STICK);
            assertEquals(new ItemSize(1.0f), ITEM_SIZE.get(stack));
            assertTrue(ITEM_SIZE.has(stack));
        } finally {
            ItemComponentDefaults.register(Items.STICK, ITEM_SIZE, null);
        }
    }

    @Test
    void settingValueEqualToDefaultDropsTheKeyInsteadOfStoringIt() {
        ItemComponentDefaults.register(Items.STICK, ITEM_SIZE, new ItemSize(1.0f));
        try {
            ItemStack stack = new ItemStack(Items.STICK);
            ITEM_SIZE.set(stack, new ItemSize(1.0f));
            assertNull(stack.getTag(), "normalization should drop the key, leaving no tag at all");
            assertEquals(new ItemSize(1.0f), ITEM_SIZE.get(stack), "still reads back via the default");
        } finally {
            ItemComponentDefaults.register(Items.STICK, ITEM_SIZE, null);
        }
    }

    @Test
    void removeDeletesAnExplicitlySetValue() {
        ItemStack stack = new ItemStack(Items.STICK);
        ITEM_SIZE.set(stack, new ItemSize(3.0f));
        ITEM_SIZE.remove(stack);
        assertNull(ITEM_SIZE.get(stack));
        assertFalse(ITEM_SIZE.has(stack));
    }

    @Test
    void hasAlertRoundTripsAsAnEmptyCompoundUnitMarker() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertFalse(HAS_ALERT.has(stack));
        HAS_ALERT.set(stack, Unit.INSTANCE);
        assertTrue(HAS_ALERT.has(stack));
        assertEquals(Unit.INSTANCE, HAS_ALERT.get(stack));
    }

    @Test
    void byIdFindsARegisteredKey() {
        assertSame(ITEM_SIZE, ComponentKey.byId(ITEM_SIZE.id));
    }

    @Test
    void byIdReturnsNullForAnUnknownId() {
        assertNull(ComponentKey.byId(grill24.fishtastic.util.Ids.of("fishtastic", "definitely_not_a_component")));
    }
}
