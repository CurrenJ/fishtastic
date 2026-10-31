package grill24.fishtastic.network;

import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.util.Ids;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimAllEncyclopediaRewardsPacketTest {
    private static final FishEncyclopediaEntry.UnlockThresholds COMMON = EncyclopediaTiers.Tier.COMMON.thresholds; // 1/3/8/4/30
    private static final ResourceLocation COD = Ids.of("fishtastic", "cod_like");
    private static final ResourceLocation EEL = Ids.of("fishtastic", "eel_like");

    @Test
    void claimsOnlyReachedSectionsAndPaysTheirCoins() {
        PlayerQuestState state = new PlayerQuestState();
        // 4 catches reaches NAME (1), STATS (3), SPAWN_CONDITIONS (4); not TYPES (8) or LORE (30).
        int claimed = ClaimAllEncyclopediaRewardsPacket.claimFish(state, COD, COMMON, 4);
        assertEquals(3, claimed);
        assertEquals(EncyclopediaRewardSection.NAME_REVEAL.coinReward() + EncyclopediaRewardSection.STATS.coinReward()
                + EncyclopediaRewardSection.SPAWN_CONDITIONS.coinReward(), state.getTokenBalance());
    }

    @Test
    void claimingTwiceNeverPaysTwice() {
        PlayerQuestState state = new PlayerQuestState();
        ClaimAllEncyclopediaRewardsPacket.claimFish(state, COD, COMMON, 30);
        int balance = state.getTokenBalance();
        assertEquals(0, ClaimAllEncyclopediaRewardsPacket.claimFish(state, COD, COMMON, 30));
        assertEquals(balance, state.getTokenBalance());
    }

    @Test
    void matchesSingleClaimTotals() {
        PlayerQuestState all = new PlayerQuestState();
        PlayerQuestState single = new PlayerQuestState();
        int claimed = ClaimAllEncyclopediaRewardsPacket.claimAll(all, List.of(COD, EEL), id -> COMMON,
                id -> Map.of(COD, 30, EEL, 1).get(id));
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            single.claimEncyclopediaReward(COD, section);
        }
        single.claimEncyclopediaReward(EEL, EncyclopediaRewardSection.NAME_REVEAL);
        assertEquals(6, claimed);
        assertEquals(single.getTokenBalance(), all.getTokenBalance());
    }
}
