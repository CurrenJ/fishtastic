package grill24.fishtastic.server;

import com.mojang.serialization.JavaOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Companion bond persistence (docs/fish-companions.md, R6.4): unlocks are stored so a bond
 * never re-locks, they survive a save/load round trip (and therefore backup restores), and an old
 * save written before the field existed still loads. The banner queue is deliberately <em>not</em>
 * persisted.
 */
class PlayerQuestStateCompanionBondsTest {

    private static ResourceLocation id(String path) {
        return grill24.fishtastic.util.Ids.of("fishtastic", path);
    }

    private static PlayerQuestState roundTrip(PlayerQuestState state) {
        return PlayerQuestState.CODEC
                .encodeStart(JavaOps.INSTANCE, state)
                .flatMap(encoded -> PlayerQuestState.CODEC.parse(JavaOps.INSTANCE, encoded))
                .getOrThrow(msg -> new IllegalStateException(msg));
    }

    @Test
    void unlocksSurviveARoundTrip() {
        PlayerQuestState state = new PlayerQuestState();
        assertTrue(state.unlockCompanionBond(id("bluegill")));
        assertTrue(state.unlockCompanionBond(id("oscar")));

        PlayerQuestState loaded = roundTrip(state);

        assertTrue(loaded.isCompanionBondUnlocked(id("bluegill")));
        assertTrue(loaded.isCompanionBondUnlocked(id("oscar")));
        assertFalse(loaded.isCompanionBondUnlocked(id("pike")));
        assertEquals(state.getUnlockedCompanionSnapshot(), loaded.getUnlockedCompanionSnapshot());
    }

    @Test
    void unlockIsIdempotent() {
        PlayerQuestState state = new PlayerQuestState();
        assertTrue(state.unlockCompanionBond(id("bluegill")), "first unlock is the transition");
        assertFalse(state.unlockCompanionBond(id("bluegill")), "a repeat call must not report a transition");
    }

    @Test
    void pendingBannersDrainOnceAndAreNotPersisted() {
        PlayerQuestState state = new PlayerQuestState();
        state.unlockCompanionBond(id("bluegill"));

        assertTrue(state.hasPendingCompanionBanners());
        assertEquals(List.of(id("bluegill")), state.drainPendingCompanionBanners());
        assertFalse(state.hasPendingCompanionBanners(), "draining must clear the queue");

        // A reload is the same state minus the live queue: the unlock stays, the banner doesn't.
        PlayerQuestState loaded = roundTrip(state);
        assertTrue(loaded.isCompanionBondUnlocked(id("bluegill")));
        assertFalse(loaded.hasPendingCompanionBanners(), "banners must never survive a save");
    }

    @Test
    void oldSavesWithoutTheFieldLoadEmpty() {
        // Exactly the shape an old save has: the required fields present, the new field absent.
        PlayerQuestState state = PlayerQuestState.CODEC
                .parse(JavaOps.INSTANCE, java.util.Map.of("progress", java.util.Map.of()))
                .getOrThrow(msg -> new IllegalStateException(msg));
        assertEquals(0, state.getUnlockedCompanionSnapshot().size(), "absent field must mean no unlocks");
    }

    @Test
    void clearCompanionBondsWipesEverything() {
        PlayerQuestState state = new PlayerQuestState();
        state.unlockCompanionBond(id("bluegill"));
        state.clearCompanionBonds();
        assertFalse(state.isCompanionBondUnlocked(id("bluegill")));
        assertFalse(state.hasPendingCompanionBanners());
    }
}
