package grill24.fishtastic.util;

import java.util.Random;

/**
 * One piece of celebration confetti — a small flat rectangle that tumbles, flutters and falls.
 *
 * <p>Same simulation shape as {@link SparkleParticle}: state advances at the fixed 20 Hz
 * {@code tick()} and the renderer interpolates between the last two states with {@code partialTick}.
 * The difference is the coordinate space: sparkles live in bar space (which is scaled and shaken
 * with the bar), whereas confetti is a screen-space effect, so positions are in units of screen
 * height measured from the screen centre — resolution independent, like {@link CatchCelebration}.
 * Positive Y is <em>down</em> here (screen convention), unlike sparkles.
 */
public class CelebrationConfetti {
    /** Gravity in screen-heights per tick². */
    private static final float GRAVITY = 0.0016f;
    /** Per-tick velocity retention — air drag, so the burst blooms then hangs before it falls. */
    private static final float DRAG = 0.965f;

    private float x, y, prevX, prevY;
    private float vx, vy;
    private float rotation, prevRotation;
    private final float rotVelocity;
    private final float flutterPhase;
    private final float flutterSpeed;
    private float age;
    private final int lifetime;

    /** Piece size in screen-heights. */
    public final float width, height;
    /** 0xRRGGBB. */
    public final int color;

    /** @param palette 0xRRGGBB colours to draw this piece's colour from (per-tier, see {@code CatchCelebration.Polish}) */
    public CelebrationConfetti(float originX, float originY, Random random, int[] palette) {
        this.x = this.prevX = originX;
        this.y = this.prevY = originY;

        // Radial burst, biased upward so it blooms over the fish before gravity takes it.
        float angle = random.nextFloat() * (float) (2 * Math.PI);
        float speed = 0.012f + random.nextFloat() * 0.030f;
        this.vx = (float) Math.cos(angle) * speed;
        this.vy = (float) Math.sin(angle) * speed - 0.010f;

        this.rotation = this.prevRotation = random.nextFloat() * 360f;
        this.rotVelocity = (random.nextBoolean() ? 1f : -1f) * (8f + random.nextFloat() * 22f);
        this.flutterPhase = random.nextFloat() * (float) (2 * Math.PI);
        this.flutterSpeed = 0.25f + random.nextFloat() * 0.35f;

        float size = 0.009f + random.nextFloat() * 0.012f;
        this.width = size;
        this.height = size * (random.nextFloat() < 0.5f ? 0.45f : 1f);
        this.color = palette[random.nextInt(palette.length)];
        this.lifetime = 45 + random.nextInt(35);
    }

    public void tick() {
        prevX = x; prevY = y; prevRotation = rotation;
        vx *= DRAG;
        vy = vy * DRAG + GRAVITY;
        x += vx;
        y += vy;
        rotation += rotVelocity;
        age++;
    }

    public boolean isAlive() { return age < lifetime; }

    /** 0 at spawn, 1 at death. */
    public float getLifetimeProgress() { return age / lifetime; }

    public float getX(float partialTick) { return prevX + (x - prevX) * partialTick; }
    public float getY(float partialTick) { return prevY + (y - prevY) * partialTick; }
    public float getRotationDegrees(float partialTick) { return prevRotation + (rotation - prevRotation) * partialTick; }

    /**
     * Horizontal scale in [0.15, 1] — a piece turning over in the air is a rectangle squeezing
     * and stretching, which is most of what makes flat confetti read as confetti.
     */
    public float getFlutter(float partialTick) {
        float raw = (float) Math.abs(Math.cos(flutterPhase + (age + partialTick) * flutterSpeed));
        return 0.15f + 0.85f * raw;
    }

    /** Opacity: full for most of its life, easing out over the last quarter. */
    public float getAlpha() {
        float p = getLifetimeProgress();
        return p < 0.75f ? 1f : Math.max(0f, (1f - p) / 0.25f);
    }
}
