package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;

/**
 * One locomotion class's way of moving: the body's limits and the mind's sense of where such a
 * creature rides (docs/fish-companions-sim-plan.md §3.4, §9). Built fresh from
 * {@link CompanionTunables} every tick, so the viewer's sliders apply live; FREE_SWIM's gait is the
 * tunables' own swim fields, untouched.
 *
 * @param rideHeight   preferred height above the owner's chest, blocks (lowered under a ceiling)
 * @param heightSpread scale on the ±0.6 block height variation of loiter spots
 * @param bandScale    scale on the comfort band's radii
 * @param hopMin       shortest move between loiter spots, blocks
 * @param hopMax       longest move between loiter spots, blocks
 * @param lateralScale scale on the escort's riding distance beside the owner
 * @param alongOffset  added to the escort's riding position along the owner's travel (negative rides behind)
 */
record Gait(float cruiseSpeed, float catchUpSpeed, float maxSpeed, float steeringGain, float maxForce,
            float maxLateralAccel, float maxJerk, float turnRateDegPerTick, float pitchMaxDeg,
            float pitchRateDegPerTick, float bankMaxDeg,
            float wanderSigma, float wanderTheta, float wanderAuthority, float wanderVertical,
            float burstPeriodSeconds, float burstDuty, float burstThrust, float burstCoast,
            double rideHeight, double heightSpread, double bandScale, double hopMin, double hopMax,
            double lateralScale, double alongOffset) {

    static Gait of(Locomotion c, CompanionTunables t) {
        if (c == Locomotion.GLIDE) {
            return new Gait(t.glideCruiseSpeed, t.catchUpSpeed, t.maxSpeed, t.steeringGain, t.maxForce,
                    t.glideLateralAccel, t.maxJerk, t.glideTurnRateDegPerTick, t.glidePitchMaxDeg,
                    t.glidePitchRateDegPerTick, t.glideBankMaxDeg,
                    t.glideWanderSigma, t.glideWanderTheta, t.wanderAuthority, t.wanderVertical,
                    t.glideBurstPeriodSeconds, t.glideBurstDuty, t.glideBurstThrust, t.glideBurstCoast,
                    t.glideRideHeight, 0.5, t.glideBandScale, 2.0, 5.0, 1.4, 0);
        }
        if (c == Locomotion.DRIFT) {
            // DriftBody reads its own drift* tunables; these matter to the mind: its pace, and that
            // it rides behind one shoulder on a loose spring (spec §3), close and a little high.
            return new Gait(t.driftCruiseSpeed, t.driftTopSpeed, t.maxSpeed, t.steeringGain, t.maxForce,
                    t.maxLateralAccel, t.maxJerk, t.turnRateDegPerTick, t.pitchMaxDeg,
                    t.pitchRateDegPerTick, t.bankMaxDeg,
                    t.wanderSigma, t.wanderTheta, t.wanderAuthority, t.wanderVertical,
                    t.burstPeriodSeconds, t.burstDuty, t.burstThrust, t.burstCoast,
                    t.driftRideHeight, 0.6, 0.9, 0.5, 2.0, 0.55, -0.7);
        }
        return new Gait(t.cruiseSpeed, t.catchUpSpeed, t.maxSpeed, t.steeringGain, t.maxForce,
                t.maxLateralAccel, t.maxJerk, t.turnRateDegPerTick, t.pitchMaxDeg,
                t.pitchRateDegPerTick, t.bankMaxDeg,
                t.wanderSigma, t.wanderTheta, t.wanderAuthority, t.wanderVertical,
                t.burstPeriodSeconds, t.burstDuty, t.burstThrust, t.burstCoast,
                0, 1, 1, 0.7, 3.2, 1, 0);
    }

    /**
     * This gait's turning limits moved {@code k} of the way toward {@code agile}'s — a glider tucking
     * in where there's no room for its wide arcs. Speeds, rhythm and where it rides are unchanged.
     */
    Gait tucked(Gait agile, double k) {
        if (k <= 0) return this;
        return new Gait(cruiseSpeed, catchUpSpeed, maxSpeed, steeringGain, maxForce,
                lerp(maxLateralAccel, agile.maxLateralAccel, k), maxJerk,
                lerp(turnRateDegPerTick, agile.turnRateDegPerTick, k), lerp(pitchMaxDeg, agile.pitchMaxDeg, k),
                lerp(pitchRateDegPerTick, agile.pitchRateDegPerTick, k), bankMaxDeg,
                wanderSigma, wanderTheta, wanderAuthority, wanderVertical,
                burstPeriodSeconds, burstDuty, burstThrust, burstCoast,
                rideHeight, heightSpread, bandScale, hopMin, hopMax, lateralScale, alongOffset);
    }

    private static float lerp(float a, float b, double k) {
        return (float) (a + (b - a) * Math.min(1, k));
    }
}
