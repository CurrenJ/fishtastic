package grill24.fishtastic.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryBreachPresentationTest {
    @Test
    void apexCueRequiresCrossingTheSourceThreshold() {
        assertTrue(LegendaryBreachPresentation.crossedSourceThreshold(18.9, 19.1, 19.0));
        assertFalse(LegendaryBreachPresentation.crossedSourceThreshold(19.0, 19.1, 19.0),
                "an event starting at the threshold has already crossed it");
        assertFalse(LegendaryBreachPresentation.crossedSourceThreshold(19.0, 19.0, 19.0));
        assertFalse(LegendaryBreachPresentation.crossedSourceThreshold(19.1, 19.2, 19.0),
                "a tracker created after the apex must not replay the apex cue");
        assertFalse(LegendaryBreachPresentation.crossedSourceThreshold(19.1, 18.9, 19.0),
                "source time must not run backward");
    }

    @Test
    void sparklesPopInFlareAndShrinkAway() {
        assertEquals(0f, LegendaryBreachPresentation.twinkle(0f));
        assertEquals(0f, LegendaryBreachPresentation.twinkle(1f));
        float peak = 0f, peakAt = 0f;
        for (int i = 1; i < 100; i++) {
            float value = LegendaryBreachPresentation.twinkle(i / 100f);
            if (value > peak) { peak = value; peakAt = i / 100f; }
        }
        assertTrue(LegendaryBreachPresentation.twinkle(0.15f) > 0.5f, "it pops in fast");
        assertTrue(peak > 1.2f && peakAt > 0.3f && peakAt < 0.5f, "it flares before mid-life: " + peak + " at " + peakAt);
        assertTrue(LegendaryBreachPresentation.twinkle(0.97f) < 0.15f, "it shrinks away at the end");
    }

    @Test
    void drawnAgesAreLerpedThroughTheLastTickLikePositions() {
        assertEquals(4.78f, LegendaryBreachPresentation.renderAge(5f, 0.22f, 0f), 1e-6f,
                "at the start of a frame's tick the age is last tick's");
        assertEquals(4.89f, LegendaryBreachPresentation.renderAge(5f, 0.22f, 0.5f), 1e-6f);
        assertEquals(5f, LegendaryBreachPresentation.renderAge(5f, 0.22f, 1f), 1e-6f);
        assertEquals(0f, LegendaryBreachPresentation.renderAge(0f, 1f, 0.25f),
                "a mote born this tick is never drawn at a negative age");
    }
}
