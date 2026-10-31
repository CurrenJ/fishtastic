package grill24.fishtastic.client.encyclopedia;

import grill24.fishtastic.client.encyclopedia.EncyclopediaWords.Effect;
import grill24.fishtastic.client.encyclopedia.EncyclopediaWords.Spot;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishProfile.ConditionTier;
import grill24.fishtastic.data.FishProfile.Zone;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static grill24.fishtastic.data.EncyclopediaRewardSection.*;
import static org.junit.jupiter.api.Assertions.*;

class EncyclopediaWordsTest {
    private static final ConditionTier P = ConditionTier.PRIMARY;
    private static final ConditionTier S = ConditionTier.SECONDARY;

    @Test
    void effectBandEdges() {
        assertEquals(Effect.GREAT, EncyclopediaWords.effect(1.75f, P));
        assertEquals(Effect.GOOD, EncyclopediaWords.effect(1.7499f, P));
        assertEquals(Effect.GOOD, EncyclopediaWords.effect(1.45f, P));
        assertEquals(Effect.SLIGHT, EncyclopediaWords.effect(1.4499f, P));
        assertEquals(Effect.SLIGHT, EncyclopediaWords.effect(1.0001f, P));
        assertEquals(Effect.POOR, EncyclopediaWords.effect(1.0f, P));
        assertEquals(Effect.POOR, EncyclopediaWords.effect(0.45f, P));
        assertEquals(Effect.BAD, EncyclopediaWords.effect(0.4499f, P));
    }

    @Test
    void effectShippedValues() {
        assertEquals(Effect.GREAT, EncyclopediaWords.effect(2.0f, P));
        assertEquals(Effect.GREAT, EncyclopediaWords.effect(1.8f, P));
        assertEquals(Effect.GOOD, EncyclopediaWords.effect(1.5f, P));
        assertEquals(Effect.SLIGHT, EncyclopediaWords.effect(1.3f, P));
        assertEquals(Effect.SLIGHT, EncyclopediaWords.effect(1.25f, S));
        assertEquals(Effect.POOR, EncyclopediaWords.effect(0.5f, P));
        assertEquals(Effect.BAD, EncyclopediaWords.effect(0.25f, P));
        assertEquals(Effect.BAD, EncyclopediaWords.effect(0.1f, P));
    }

    @Test
    void secondaryBoostIsAlwaysSlight() {
        assertEquals(Effect.SLIGHT, EncyclopediaWords.effect(2.0f, S));
        assertEquals(Effect.POOR, EncyclopediaWords.effect(0.5f, S));
    }

    @Test
    void helpsAndLangKeys() {
        assertTrue(Effect.GREAT.helps());
        assertTrue(Effect.GOOD.helps());
        assertTrue(Effect.SLIGHT.helps());
        assertFalse(Effect.POOR.helps());
        assertFalse(Effect.BAD.helps());
        assertEquals("screen.fishtastic.encyclopedia.effect.slight", Effect.SLIGHT.langKey());
        assertEquals("screen.fishtastic.encyclopedia.verdict.fair", Spot.FAIR.langKey());
    }

    @Test
    void spotBandEdges() {
        assertEquals(Spot.GREAT, EncyclopediaWords.spot(1.75f));
        assertEquals(Spot.GOOD, EncyclopediaWords.spot(1.7499f));
        assertEquals(Spot.GOOD, EncyclopediaWords.spot(1.2f));
        assertEquals(Spot.FAIR, EncyclopediaWords.spot(1.1999f));
        assertEquals(Spot.FAIR, EncyclopediaWords.spot(1.0f));
        assertEquals(Spot.FAIR, EncyclopediaWords.spot(0.8001f));
        assertEquals(Spot.POOR, EncyclopediaWords.spot(0.8f));
        assertEquals(Spot.POOR, EncyclopediaWords.spot(0.0f));
    }

    @Test
    void unlockOrderForEveryTierPreset() {
        // Tier thresholds are (name, stats, types, spawn, lore).
        assertEquals(List.of(NAME_REVEAL, STATS, TYPES, SPAWN_CONDITIONS, LORE),
                FishPageModel.unlockOrder(EncyclopediaTiers.Tier.VERY_RARE.thresholds)); // 1/1/1/1/3
        assertEquals(List.of(NAME_REVEAL, STATS, TYPES, SPAWN_CONDITIONS, LORE),
                FishPageModel.unlockOrder(EncyclopediaTiers.Tier.RARE.thresholds)); // 1/1/2/2/6
        assertEquals(List.of(NAME_REVEAL, STATS, SPAWN_CONDITIONS, TYPES, LORE),
                FishPageModel.unlockOrder(EncyclopediaTiers.Tier.UNCOMMON.thresholds)); // 1/2/4/3/15
        assertEquals(List.of(NAME_REVEAL, STATS, SPAWN_CONDITIONS, TYPES, LORE),
                FishPageModel.unlockOrder(EncyclopediaTiers.Tier.COMMON.thresholds)); // 1/3/8/4/30
        assertEquals(List.of(NAME_REVEAL, STATS, SPAWN_CONDITIONS, TYPES, LORE),
                FishPageModel.unlockOrder(EncyclopediaTiers.Tier.ABUNDANT.thresholds)); // 1/4/12/6/50
    }

    @Test
    void unlockOrderUsesEnumOrderForTies() {
        for (EncyclopediaTiers.Tier tier : EncyclopediaTiers.Tier.values()) {
            List<EncyclopediaRewardSection> order = FishPageModel.unlockOrder(tier.thresholds);
            assertEquals(5, order.size());
            for (int i = 1; i < order.size(); i++) {
                int prev = order.get(i - 1).threshold(tier.thresholds);
                int next = order.get(i).threshold(tier.thresholds);
                assertTrue(prev <= next, tier + " sorted by threshold");
                if (prev == next) assertTrue(order.get(i - 1).ordinal() < order.get(i).ordinal(), tier + " tie in enum order");
            }
        }
    }

    @Test
    void poolZoneNamesMostSpecificZone() {
        assertEquals(Zone.DEEP_OCEAN, FishPageModel.poolZone(EnumSet.of(Zone.OCEAN, Zone.DEEP_OCEAN)));
        assertEquals(Zone.CAVE, FishPageModel.poolZone(EnumSet.of(Zone.RIVER, Zone.CAVE)));
        assertEquals(Zone.HIGH_ALTITUDE, FishPageModel.poolZone(EnumSet.of(Zone.RIVER, Zone.HIGH_ALTITUDE)));
        assertEquals(Zone.NETHER, FishPageModel.poolZone(EnumSet.of(Zone.NETHER)));
        assertEquals(Zone.OCEAN, FishPageModel.poolZone(EnumSet.of(Zone.OCEAN)));
        assertEquals(Zone.RIVER, FishPageModel.poolZone(EnumSet.of(Zone.RIVER)));
        assertNull(FishPageModel.poolZone(EnumSet.noneOf(Zone.class)));
        for (var pool : EncyclopediaTiers.LOCATION_POOLS) assertNotNull(FishPageModel.poolZone(pool));
    }
}
