package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.client.encyclopedia.portrait.PortraitBubbles.Bubble;
import grill24.fishtastic.client.encyclopedia.portrait.PortraitBubbles.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortraitBubblesTest {

    private static String fingerprint(PortraitBubbles m) {
        StringBuilder sb = new StringBuilder();
        for (Bubble b : m.bubbles()) {
            sb.append(b.kind).append(':').append(b.drawX()).append(',').append(b.drawY()).append(',').append(b.front).append(';');
        }
        return sb.toString();
    }

    @Test
    void deterministicForSeed() {
        for (PortraitTheme theme : PortraitTheme.values()) {
            PortraitBubbles a = new PortraitBubbles(theme, 1234L);
            PortraitBubbles b = new PortraitBubbles(theme, 1234L);
            a.warmStart(5);
            b.warmStart(5);
            a.onOpen();
            b.onOpen();
            a.advance(3);
            b.advance(3);
            assertEquals(fingerprint(a), fingerprint(b), theme.name());
            assertEquals(a.alive(), b.alive());
        }
        PortraitBubbles c = new PortraitBubbles(PortraitTheme.OCEAN, 99L);
        PortraitBubbles d = new PortraitBubbles(PortraitTheme.OCEAN, 100L);
        c.warmStart(5);
        d.warmStart(5);
        assertNotEquals(fingerprint(c), fingerprint(d));
    }

    @Test
    void ambientNeverExceedsCap() {
        for (PortraitTheme theme : PortraitTheme.values()) {
            PortraitBubbles m = new PortraitBubbles(theme, 7L);
            for (int i = 0; i < 20 * 120; i++) {
                m.step(0.05f);
                assertTrue(m.alive() <= PortraitBubbles.MAX_BUBBLES, theme + " alive=" + m.alive());
            }
            assertTrue(m.peak() <= PortraitBubbles.MAX_BUBBLES);
            assertTrue(m.peak() > 3, theme + " should be visibly alive, peak=" + m.peak());
        }
    }

    @Test
    void playerExtrasNeverExceedExtrasCap() {
        for (PortraitTheme theme : PortraitTheme.values()) {
            PortraitBubbles m = new PortraitBubbles(theme, 3L);
            m.warmStart(5);
            for (int i = 0; i < 20 * 30; i++) {
                if (i % 7 == 0) m.click(60, 40);
                if (i % 40 == 0) m.claimBurst();
                m.step(0.05f);
                assertTrue(m.alive() <= PortraitBubbles.MAX_WITH_EXTRAS, theme + " alive=" + m.alive());
            }
            assertTrue(m.peak() <= PortraitBubbles.MAX_WITH_EXTRAS);
        }
    }

    @Test
    void clickCooldownAndPaused() {
        PortraitBubbles m = new PortraitBubbles(PortraitTheme.OCEAN, 1L);
        m.warmStart(5);
        assertTrue(m.click(20, 30));
        assertFalse(m.click(20, 30), "second click inside the cooldown");
        m.step(0.1f);
        m.step(0.1f);
        assertFalse(m.click(20, 30), "0.2 s < 0.35 s");
        m.step(0.1f);
        m.step(0.1f);
        assertTrue(m.click(20, 30), "0.4 s later");

        m.step(0.1f);
        m.step(0.1f);
        m.step(0.1f);
        m.step(0.1f);
        m.setPaused(true);
        int before = m.alive();
        assertFalse(m.click(20, 30), "paused");
        assertEquals(before, m.alive());
    }

    @Test
    void clickOnFishPutsPlumeInFront() {
        PortraitBubbles m = new PortraitBubbles(PortraitTheme.OCEAN, 1L);
        m.click(70, 40);
        List<Bubble> clicked = new ArrayList<>(m.bubbles());
        assertFalse(clicked.isEmpty());
        assertTrue(clicked.stream().allMatch(b -> b.front));

        PortraitBubbles n = new PortraitBubbles(PortraitTheme.OCEAN, 1L);
        n.click(10, 40);
        assertTrue(n.bubbles().stream().noneMatch(b -> b.front));
    }

    @Test
    void claimBurstSkippedWhenPausedOrReduced() {
        PortraitBubbles paused = new PortraitBubbles(PortraitTheme.OCEAN, 5L);
        paused.setPaused(true);
        paused.claimBurst();
        assertEquals(0, paused.alive());

        PortraitBubbles reduced = new PortraitBubbles(PortraitTheme.OCEAN, 5L);
        reduced.setReducedEffects(true);
        reduced.claimBurst();
        assertEquals(0, reduced.alive());

        PortraitBubbles normal = new PortraitBubbles(PortraitTheme.OCEAN, 5L);
        normal.claimBurst();
        assertEquals(30, normal.alive(), "16 small/medium + 14 micro");

        PortraitBubbles lava = new PortraitBubbles(PortraitTheme.NETHER, 5L);
        lava.claimBurst();
        assertEquals(28, lava.alive());
    }

    @Test
    void dtIsClampedAndPausedStepDoesNothing() {
        PortraitBubbles a = new PortraitBubbles(PortraitTheme.OCEAN, 11L);
        PortraitBubbles b = new PortraitBubbles(PortraitTheme.OCEAN, 11L);
        a.step(5.0f);
        b.step(0.1f);
        assertEquals(0.1f, a.time(), 1e-6f);
        assertEquals(fingerprint(b), fingerprint(a));

        PortraitBubbles p = new PortraitBubbles(PortraitTheme.OCEAN, 11L);
        p.warmStart(5);
        p.setPaused(true);
        String frozen = fingerprint(p);
        for (int i = 0; i < 100; i++) p.step(0.05f);
        assertEquals(frozen, fingerprint(p));
        p.setPaused(false);
        p.step(0.1f);
        assertNotEquals(frozen, fingerprint(p));
    }

    @Test
    void welcomePlumeWithinWindowAfterOpen() {
        for (long seed = 0; seed < 40; seed++) {
            PortraitBubbles m = new PortraitBubbles(PortraitTheme.OCEAN, seed);
            m.warmStart(5);
            m.onOpen();
            float t = m.plumeTimer();
            assertTrue(t >= 0.8f && t <= 1.6f, "timer " + t);
            // Step until just before and just after the timer; the next timer is the 5-8 s cadence.
            m.advance(t - 0.1f);
            assertTrue(m.plumeTimer() > 0f, "not yet fired");
            m.advance(0.25f);
            assertTrue(m.plumeTimer() >= 4.5f && m.plumeTimer() <= 8.0f, "fired, next in " + m.plumeTimer());
        }
    }

    @Test
    void netherEmitsEmbersNotBubbles() {
        PortraitBubbles m = new PortraitBubbles(PortraitTheme.NETHER, 2L);
        boolean sawEmber = false;
        for (int i = 0; i < 20 * 20; i++) {
            m.step(0.05f);
            for (Bubble b : m.bubbles()) {
                assertTrue(b.kind == Kind.EMBER || b.kind == Kind.BLOB, "unexpected " + b.kind);
                sawEmber |= b.kind == Kind.EMBER;
            }
        }
        assertTrue(sawEmber);
        m.click(70, 40);
        assertTrue(m.bubbles().stream().allMatch(b -> b.kind == Kind.EMBER || b.kind == Kind.BLOB));
        assertTrue(m.bubbles().stream().anyMatch(b -> b.kind == Kind.EMBER && b.size == 2 && b.front));
    }

    @Test
    void deepOceanDropsMarineSnow() {
        PortraitBubbles m = new PortraitBubbles(PortraitTheme.DEEP_OCEAN, 4L);
        boolean snow = false;
        for (int i = 0; i < 20 * 20 && !snow; i++) {
            m.step(0.05f);
            snow = m.bubbles().stream().anyMatch(b -> b.kind == Kind.SNOW && b.vy > 0);
        }
        assertTrue(snow);
    }

    @Test
    void mediumBubblesPopAtSurface() {
        PortraitBubbles m = new PortraitBubbles(PortraitTheme.OCEAN, 8L);
        boolean popped = false;
        for (int i = 0; i < 20 * 40 && !popped; i++) {
            m.step(0.05f);
            popped = m.bubbles().stream().anyMatch(b -> b.popping && b.kind == Kind.MEDIUM);
        }
        assertTrue(popped);
    }

    @Test
    void zoneMapping() {
        for (var zone : grill24.fishtastic.data.FishProfile.Zone.values()) {
            assertEquals(zone.name(), PortraitTheme.forZone(zone).name());
        }
        assertEquals("fishtastic:textures/gui/encyclopedia/plate_deep_ocean.png",
                PortraitTheme.DEEP_OCEAN.plateTexture.toString());
    }
}
