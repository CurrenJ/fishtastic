package grill24.fishtastic.client.companion;

/** Retains a fall across vanilla's reset of fallDistance on the landing tick. */
final class OwnerFallTracker {
    private boolean known, grounded, previouslyEligible;
    private double lastY, distance;

    double update(double y, boolean onGround, double vanillaFallDistance, boolean eligible, boolean teleported) {
        double landed = 0;
        if (known && eligible && previouslyEligible && !teleported) {
            if (!grounded) {
                distance += Math.max(0, lastY - y);
                if (onGround) landed = Math.max(distance, vanillaFallDistance);
            }
            if (!onGround) distance = Math.max(distance, vanillaFallDistance);
        }
        if (onGround || !eligible || teleported) distance = 0;
        known = true;
        grounded = onGround;
        previouslyEligible = eligible && !teleported;
        lastY = y;
        return landed;
    }
}
