package grill24.fishsim.companion;

/**
 * Everything the companion perceives about its owner on one tick (docs/fish-companions.md
 * §3.2). In game the adapter fills it from the player and camera; headless, a scripted owner does.
 *
 * @param eye         eye position
 * @param velocity    blocks per second
 * @param yawDeg      Minecraft yaw of the view
 * @param pitchDeg    Minecraft pitch of the view (positive looks down)
 * @param sprinting   the owner is sprinting
 * @param teleported  the owner moved this tick by something no fish could follow (teleport,
 *                    dimension change, respawn) — the companion fades out and back in
 * @param firstPerson the owner's camera is at their eye; otherwise it sits behind them
 * @param fovDeg      vertical field of view
 * @param inWater     water at the owner's chest: deep enough to swim, not a puddle at their feet
 * @param riding      the owner rides something (a horse, a boat, a minecart): nothing on the ground
 *                    keeps up, so a ground companion climbs on or fades onto its perch
 * @param gliding     the owner flies with elytra (the same for a ground companion)
 * @param landedFall  the height the owner fell from, blocks, on the tick they land; 0 on every
 *                    other tick. A fall, not a climb down a ladder or a creative descent: in game,
 *                    the player's fall distance as they touch down. A hard landing (3+ blocks)
 *                    knocks a ground companion off its perch (docs/fish-companions.md)
 */
public record OwnerState(Vec eye, Vec velocity, float yawDeg, float pitchDeg,
                         boolean sprinting, boolean teleported, boolean firstPerson, float fovDeg,
                         boolean inWater, boolean riding, boolean gliding, double landedFall) {

    /** An owner who isn't landing from a fall this tick. */
    public OwnerState(Vec eye, Vec velocity, float yawDeg, float pitchDeg,
                      boolean sprinting, boolean teleported, boolean firstPerson, float fovDeg,
                      boolean inWater, boolean riding, boolean gliding) {
        this(eye, velocity, yawDeg, pitchDeg, sprinting, teleported, firstPerson, fovDeg, inWater, riding, gliding, 0);
    }

    /** An owner on foot (or swimming, or in creative flight): neither riding nor gliding. */
    public OwnerState(Vec eye, Vec velocity, float yawDeg, float pitchDeg,
                      boolean sprinting, boolean teleported, boolean firstPerson, float fovDeg,
                      boolean inWater) {
        this(eye, velocity, yawDeg, pitchDeg, sprinting, teleported, firstPerson, fovDeg, inWater, false, false);
    }

    public static final double EYE_HEIGHT = 1.62;
    public static final double BODY_HEIGHT = 1.8;
    public static final double BODY_RADIUS = 0.3;
    /** Chest height below the eye: where "near the owner" is measured from. */
    public static final double CHEST_DROP = 0.5;
    /** How far behind the eye a third-person camera sits. */
    public static final double THIRD_PERSON_DISTANCE = 4.0;

    public Vec feet() { return eye.add(0, -EYE_HEIGHT, 0); }

    public Vec chest() { return eye.add(0, -CHEST_DROP, 0); }

    public Vec look() { return Vec.fromYawPitch(yawDeg, pitchDeg); }

    public Vec flatForward() { return Vec.fromYawPitch(yawDeg, 0); }

    /** The owner's right hand, horizontal. */
    public Vec flatRight() { return flatForward().cross(Vec.UP); }

    public Vec camera() { return firstPerson ? eye : eye.sub(look().scale(THIRD_PERSON_DISTANCE)); }

    public double horizontalSpeed() { return velocity.horizontalLength(); }
}
