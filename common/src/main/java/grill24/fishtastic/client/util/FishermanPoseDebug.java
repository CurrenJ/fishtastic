package grill24.fishtastic.client.util;

import net.minecraft.world.entity.EntityType;

/**
 * Live-tunable values for the leaderboard/mannequin "gripping the fish by the tail" hang pose
 * (see {@code HumanoidModelMixin} for the arm joint, {@code FishTankBlockEntityRenderer
 * #getHeldItemHangingRollDegrees} for the item roll fallback). Read fresh every render frame, so
 * changing a field here takes effect immediately — the intended way to change them is
 * {@code /fishtastic pose <field> <value>} (dev-only), not editing this class directly; once a
 * good set of values is found in-game, bake them back into the mixin/helper as the new defaults
 * and this class can revert to those defaults.
 */
public final class FishermanPoseDebug {
    private FishermanPoseDebug() {}

    /** Holding arm's joint rotation, radians — see HumanoidModelMixin. */
    public static float armXRot = -1.4f;
    public static float armYRot = 0.0f;
    public static float armZRot = 0.0f;

    /** Item roll fallback (no head_uv/tail_uv authored), degrees — see FishTankBlockEntityRenderer. */
    public static float rollDiagonalDegrees = 225f;
    public static float rollStraightDegrees = 0f;

    /**
     * Item position offset, applied inside the same push/scale/rotate bracket as the roll (see
     * ItemInHandLayerMixin) — so it's expressed in the sprite's own pre-scale local units and
     * automatically grows/shrinks with the fish's recorded size and rotates with the roll, rather
     * than needing separate tuning per size. Intent: shift the sprite so the grip point (tail)
     * sits at the hand's origin instead of the sprite's centre. No head_uv/tail_uv-derived exact
     * offset yet (no species has authored points) — this is the same species-agnostic fallback
     * approach as the roll fallback, tuned by eye.
     */
    public static float offsetX = 0.2f;
    public static float offsetY = 0f;
    public static float offsetZ = -0.06f;

    /**
     * Debug-only toggle (see {@code /fishtastic pose world <true|false>}) that forces this pose
     * onto every real {@code EntityType.PLAYER} holding a sized fish, not just decorative
     * Mannequins — a preview for a possible future player emote, not itself networked or
     * persisted. While on, the holding arm goes rigid: this pose overrides vanilla's arm
     * animation unconditionally (see HumanoidModelMixin), so it will visibly fight normal
     * walk/attack/swim animation exactly like it would for a real emote system — expected, not a
     * bug, but not something to leave on for actual play.
     */
    public static boolean enabledInWorld = false;

    /** Shared by HumanoidModelMixin and ItemInHandLayerMixin — kept here (a plain class) rather
      * than in either mixin, since calling one mixin's members from another isn't reliable (mixin
      * classes get merged into their targets and aren't dependably invokable as themselves). */
    public static boolean shouldPose(EntityType<?> entityType) {
        return entityType == EntityType.MANNEQUIN || (enabledInWorld && entityType == EntityType.PLAYER);
    }

    public static String describe() {
        return String.format(
                "armXRot=%.3f armYRot=%.3f armZRot=%.3f | rollDiagonal=%.1f rollStraight=%.1f | "
                        + "offsetX=%.3f offsetY=%.3f offsetZ=%.3f | enabledInWorld=%b",
                armXRot, armYRot, armZRot, rollDiagonalDegrees, rollStraightDegrees, offsetX, offsetY, offsetZ,
                enabledInWorld);
    }
}
