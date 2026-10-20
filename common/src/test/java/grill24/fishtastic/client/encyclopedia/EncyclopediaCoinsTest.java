package grill24.fishtastic.client.encyclopedia;

import grill24.fishtastic.client.encyclopedia.EncyclopediaCoins.Rung;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncyclopediaCoinsTest {
    @Test
    void ladderEdges() {
        assertEquals(Rung.ONE_COIN, EncyclopediaCoins.forAmount(1));
        assertEquals(Rung.TWO_COINS, EncyclopediaCoins.forAmount(2));
        assertEquals(Rung.SMALL_PILE, EncyclopediaCoins.forAmount(3));
        assertEquals(Rung.SMALL_PILE, EncyclopediaCoins.forAmount(7));
        assertEquals(Rung.MEDIUM_PILE, EncyclopediaCoins.forAmount(8));
        assertEquals(Rung.MEDIUM_PILE, EncyclopediaCoins.forAmount(19));
        assertEquals(Rung.LARGE_PILE, EncyclopediaCoins.forAmount(20));
        assertEquals(Rung.LARGE_PILE, EncyclopediaCoins.forAmount(544));
    }
}
