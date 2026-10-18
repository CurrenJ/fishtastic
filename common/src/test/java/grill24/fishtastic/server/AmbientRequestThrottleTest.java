package grill24.fishtastic.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the per-player token bucket that bounds ambient context request traffic (Fish in World,
 * Phase 4). Headless by construction: the throttle takes the tick as a parameter and has no
 * Minecraft types at all.
 */
class AmbientRequestThrottleTest {

    @Test
    void burstCapacityIsAnsweredThenRequestsAreDropped() {
        AmbientRequestThrottle throttle = new AmbientRequestThrottle();
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            assertTrue(throttle.tryAcquire(100L), "request " + i + " within the burst must be answered");
        }
        assertFalse(throttle.tryAcquire(100L), "the request past the burst must be dropped");
        assertFalse(throttle.tryAcquire(100L));
        assertEquals(AmbientRequestThrottle.BURST_CAPACITY, throttle.accepted());
        assertEquals(2, throttle.dropped());
    }

    @Test
    void refillsOneTokenPerInterval() {
        AmbientRequestThrottle throttle = new AmbientRequestThrottle();
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            throttle.tryAcquire(0L);
        }
        assertFalse(throttle.tryAcquire(AmbientRequestThrottle.REFILL_INTERVAL_TICKS - 1));

        assertTrue(throttle.tryAcquire(AmbientRequestThrottle.REFILL_INTERVAL_TICKS));
        assertFalse(throttle.tryAcquire(AmbientRequestThrottle.REFILL_INTERVAL_TICKS),
                "one interval refills exactly one token");

        // A long gap refills the whole bucket, never more.
        long farFuture = AmbientRequestThrottle.REFILL_INTERVAL_TICKS * 100L;
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            assertTrue(throttle.tryAcquire(farFuture));
        }
        assertFalse(throttle.tryAcquire(farFuture));
    }

    @Test
    void aSameTickBurstDoesNotRefill() {
        AmbientRequestThrottle throttle = new AmbientRequestThrottle();
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            throttle.tryAcquire(500L);
        }
        assertFalse(throttle.tryAcquire(500L));
        assertEquals(0, throttle.tokens());
    }

    @Test
    void aBurstAtTheNeverMarkerTickDoesNotOverflowIntoPhantomTokens() {
        AmbientRequestThrottle throttle = new AmbientRequestThrottle();
        // Long.MIN_VALUE is the "never used" marker; ticking at it must not overflow the delta
        // arithmetic (a subtraction from it would wrap back to a positive delta). The bucket just
        // behaves like a burst at whatever tick it was given.
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            assertTrue(throttle.tryAcquire(Long.MIN_VALUE));
        }
        assertFalse(throttle.tryAcquire(Long.MIN_VALUE));
        // A normal tick then re-anchors the clock and refills nothing: one request is dropped,
        // then the normal cadence resumes.
        assertFalse(throttle.tryAcquire(0L));
        assertEquals(0, throttle.tokens());
        assertTrue(throttle.tryAcquire(AmbientRequestThrottle.REFILL_INTERVAL_TICKS));
        assertFalse(throttle.tryAcquire(AmbientRequestThrottle.REFILL_INTERVAL_TICKS));
    }

    @Test
    void idleExpiryOnlyFiresAfterTheIdleWindow() {
        AmbientRequestThrottle throttle = new AmbientRequestThrottle();
        assertFalse(throttle.idleExpired(0L), "a never-used bucket holds nothing worth expiring");
        throttle.tryAcquire(100L);
        assertFalse(throttle.idleExpired(100L + AmbientRequestThrottle.IDLE_EXPIRY_TICKS));
        assertTrue(throttle.idleExpired(100L + AmbientRequestThrottle.IDLE_EXPIRY_TICKS + 1));
    }

    @Test
    void aTickGoingBackwardsCannotRefill() {
        AmbientRequestThrottle throttle = new AmbientRequestThrottle();
        for (int i = 0; i < AmbientRequestThrottle.BURST_CAPACITY; i++) {
            throttle.tryAcquire(1000L);
        }
        // A level change can reset the clock; an earlier tick must not mint tokens.
        assertFalse(throttle.tryAcquire(200L));
        assertEquals(0, throttle.tokens());
        // ...but it re-anchors the clock, so the normal cadence resumes within one interval
        // instead of stalling until the old level's time is reached again.
        assertTrue(throttle.tryAcquire(200L + AmbientRequestThrottle.REFILL_INTERVAL_TICKS));
    }
}
