package grill24.fishtastic.ecology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationRulesTest {

    @Test
    void angleUsesDirectionRatherThanVectorLengthAndRejectsZeroVectors() {
        assertEquals(0.0, ObservationRules.angleDeg(2, 0, 0, 80, 0, 0), 1e-9);
        assertEquals(90.0, ObservationRules.angleDeg(2, 0, 0, 0, 80, 0), 1e-9);
        assertEquals(180.0, ObservationRules.angleDeg(2, 0, 0, -80, 0, 0), 1e-9);
        assertEquals(180.0, ObservationRules.angleDeg(0, 0, 0, 80, 0, 0));
        assertEquals(180.0, ObservationRules.angleDeg(2, 0, 0, 0, 0, 0));
    }

    @Test
    void clientConeIncludesTheFishRadiusAtItsBoundary() {
        double distance = 80.0;
        double edge = 3.0 + Math.toDegrees(Math.asin(0.5 / distance));
        assertTrue(clientAt(edge - 1e-6, distance));
        assertFalse(clientAt(edge + 1e-6, distance));
        assertTrue(clientAt(5.0, 8.0), "the nearby fish's body overlaps the aim cone");
        assertFalse(clientAt(5.0, 64.0), "the same aim error misses a distant fish");
    }

    @Test
    void serverConeAllowsTheAuthoritativePositionSlack() {
        double distance = 80.0;
        double edge = 8.0 + Math.toDegrees(Math.asin(3.0 / distance));
        assertTrue(serverAt(edge - 1e-6, distance, 3.0));
        assertFalse(serverAt(edge + 1e-6, distance, 3.0));
        assertFalse(serverAt(15.0, distance, ObservationRules.VISIT_POSITION_SLACK));
        assertTrue(serverAt(15.0, distance, ObservationRules.AMBIENT_POSITION_SLACK),
                "ambient motion has the wider accepted uncertainty");
    }

    @Test
    void aTargetInsideItsUncertaintyRadiusIsInsideTheCone() {
        assertTrue(ObservationRules.clientCone(1, 0, 0, -0.5, 0, 0));
        assertFalse(ObservationRules.clientCone(1, 0, 0, -0.5001, 0, 0));
        assertTrue(ObservationRules.serverCone(1, 0, 0, -3.0, 0, 0, 3.0));
        assertFalse(ObservationRules.serverCone(1, 0, 0, -3.0001, 0, 0, 3.0));
        assertTrue(ObservationRules.inCone(1, 0, 0, 0, 1, 0, 90.0, 0.0),
                "a cone includes its angular edge");
    }

    @Test
    void rangeIncludesItsLimitWithTheAppropriatePositionSlack() {
        for (double slack : new double[]{0.0, ObservationRules.VISIT_POSITION_SLACK,
                ObservationRules.AMBIENT_POSITION_SLACK}) {
            double limit = 96.0 + 6.0 + slack;
            assertTrue(ObservationRules.inRange(limit, slack));
            assertFalse(ObservationRules.inRange(Math.nextUp(limit), slack));
        }
    }

    @Test
    void lifespanToleranceKeepsArrivalInclusiveAndDepartureExclusive() {
        long arrival = 1000L, departure = 1200L;
        assertFalse(ObservationRules.lifespanPresent(arrival, departure, 899L));
        assertTrue(ObservationRules.lifespanPresent(arrival, departure, 900L));
        assertTrue(ObservationRules.lifespanPresent(arrival, departure, arrival));
        assertTrue(ObservationRules.lifespanPresent(arrival, departure, 1299L));
        assertFalse(ObservationRules.lifespanPresent(arrival, departure, 1300L));
    }

    @Test
    void holdProgressReachesOneAtOneSecondAndClampsOutsideTheHold() {
        assertEquals(0f, ObservationRules.progress(-1));
        assertEquals(0f, ObservationRules.progress(0));
        assertEquals(0.5f, ObservationRules.progress(10));
        assertEquals(0.95f, ObservationRules.progress(19));
        assertEquals(1f, ObservationRules.progress(20));
        assertEquals(1f, ObservationRules.progress(21));
    }

    private static boolean clientAt(double angleDeg, double distance) {
        double angle = Math.toRadians(angleDeg);
        return ObservationRules.clientCone(1, 0, 0, distance * Math.cos(angle), distance * Math.sin(angle), 0);
    }

    private static boolean serverAt(double angleDeg, double distance, double slack) {
        double angle = Math.toRadians(angleDeg);
        return ObservationRules.serverCone(1, 0, 0, distance * Math.cos(angle), distance * Math.sin(angle), 0, slack);
    }
}
