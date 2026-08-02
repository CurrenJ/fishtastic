package grill24.fishtastic.data;

import net.minecraft.resources.Identifier;

/** The five catch-gated encyclopedia sections that also pay a one-time quest-token reward. Zones and Records have none. */
public enum EncyclopediaRewardSection {
    NAME_REVEAL(1) {
        @Override
        public int threshold(FishEncyclopediaEntry.UnlockThresholds thresholds) {
            return thresholds.nameRevealCatches();
        }
    },
    STATS(1) {
        @Override
        public int threshold(FishEncyclopediaEntry.UnlockThresholds thresholds) {
            return thresholds.statsCatches();
        }
    },
    TYPES(1) {
        @Override
        public int threshold(FishEncyclopediaEntry.UnlockThresholds thresholds) {
            return thresholds.typesCatches();
        }
    },
    SPAWN_CONDITIONS(2) {
        @Override
        public int threshold(FishEncyclopediaEntry.UnlockThresholds thresholds) {
            return thresholds.spawnConditionsCatches();
        }
    },
    LORE(3) {
        @Override
        public int threshold(FishEncyclopediaEntry.UnlockThresholds thresholds) {
            return thresholds.loreCatches();
        }
    };

    private final int coinReward;

    EncyclopediaRewardSection(int coinReward) {
        this.coinReward = coinReward;
    }

    /** Catch count at which this section (and its reward) unlocks, for a given fish's thresholds. */
    public abstract int threshold(FishEncyclopediaEntry.UnlockThresholds thresholds);

    /** Quest tokens granted the one time this section's reward is claimed. */
    public int coinReward() {
        return coinReward;
    }

    /** Composite key for one fish's reward slot, shared by server persistence, the wire format, and the client cache. */
    public String key(Identifier fishId) {
        return fishId + "#" + name();
    }
}
