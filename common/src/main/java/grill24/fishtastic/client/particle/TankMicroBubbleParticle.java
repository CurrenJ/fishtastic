package grill24.fishtastic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A bubble shed by a simulated fish (docs/fish-tank-bubbles.md). Three sizes share this class: the
 * 1px {@code tiny_bubble}, the 2px {@code small_bubble}, and {@code medium_bubble} — our own
 * slightly smaller take on vanilla's bubble sprite, at up to half the size {@link TankBubbleParticle} draws its. All are short-lived and normally dissolve in the water column rather than
 * reaching the glass, the way a real micro-bubble is absorbed; the {@code popY} ceiling exists so
 * one that would otherwise escape a shallow tank pops at the glass instead.
 *
 * <p>Like {@link TankBubbleParticle}, no physics: the tank interior has no fluid and the bubble
 * must pass through the tank's own geometry on the way up a stack.
 *
 * <p>Aux convention: {@code xAux}/{@code zAux} carry an initial horizontal drift (a fraction of
 * the emitting fish's velocity, so a trail streams off a moving fish), {@code yAux} the absolute
 * world Y to pop at.
 */
public class TankMicroBubbleParticle extends SingleQuadParticle {
    /**
     * Per-size tuning; {@code sizeMin..sizeMax} is the random multiplier on {@code quadSize}, and
     * {@code shrinkTicks} how many final ticks the bubble spends scaling down to nothing (0 = pop
     * at full size, like vanilla's).
     */
    private record Profile(float quadSize, float sizeMin, float sizeMax, int minLife, int lifeRange,
            float minRise, float riseRange, int shrinkTicks) {}

    private static final Profile TINY = new Profile(0.0055F, 0.75F, 1.25F, 15, 20, 0.004F, 0.006F, 8);
    private static final Profile SMALL = new Profile(0.01125F, 0.75F, 1.25F, 40, 40, 0.010F, 0.010F, 12);
    /**
     * The mod's own {@code medium_bubble} sprite (a slightly smaller take on vanilla's bubble) at up
     * to half the size {@link TankBubbleParticle} draws the full one — the step below full. Wide size spread on purpose: a run of vanilla-style bubbles should look like a
     * loose range of sizes, not one stamp repeated.
     */
    private static final Profile MEDIUM = new Profile(0.04F, 0.35F, 1.15F, 60, 50, 0.012F, 0.012F, 0);

    private final double popY;
    private final int shrinkTicks;

    private TankMicroBubbleParticle(ClientLevel level, double x, double y, double z,
            double driftX, double popY, double driftZ, TextureAtlasSprite sprite, Profile profile) {
        super(level, x, y, z, sprite);
        this.popY = popY;
        this.shrinkTicks = profile.shrinkTicks();
        this.hasPhysics = false;
        this.setSize(0.01F, 0.01F);
        this.quadSize = profile.quadSize() * (profile.sizeMin() + this.random.nextFloat() * (profile.sizeMax() - profile.sizeMin()));
        this.xd = driftX;
        this.yd = profile.minRise() + this.random.nextFloat() * profile.riseRange();
        this.zd = driftZ;
        this.lifetime = profile.minLife() + this.random.nextInt(profile.lifeRange());
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0 || this.y >= this.popY) {
            // A dissolving micro-bubble has already shrunk to nothing; a vanilla-style one bursts.
            if (shrinkTicks <= 0) {
                TankBubblePopParticle.spawn(this.level, this.x, this.y, this.z, this.quadSize);
            }
            this.remove();
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        // Inherited drift bleeds off quickly — a bubble leaves the fish's slipstream within a few
        // ticks and then just rises.
        this.xd *= 0.85F;
        this.zd *= 0.85F;
        // A faint wobble so a column of bubbles doesn't read as a straight dotted line.
        this.xd += (this.random.nextFloat() - 0.5F) * 0.0015F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.0015F;
    }

    /**
     * The micro-bubbles dissolve rather than pop: over the last {@code shrinkTicks} of life the
     * quad scales linearly to zero, interpolated per frame so the fade is smooth at any frame rate.
     */
    @Override
    public float getQuadSize(float partialTick) {
        if (shrinkTicks <= 0) return this.quadSize;
        float remaining = this.lifetime - partialTick;
        if (remaining >= shrinkTicks) return this.quadSize;
        return this.quadSize * Math.max(0f, remaining / shrinkTicks);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    private static Particle create(SpriteSet sprites, Profile profile, ClientLevel level,
            double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random) {
        return new TankMicroBubbleParticle(level, x, y, z, xAux, yAux, zAux, sprites.get(random), profile);
    }

    public static class TinyProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public TinyProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return create(sprites, TINY, level, x, y, z, xAux, yAux, zAux, random);
        }
    }

    public static class MediumProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public MediumProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return create(sprites, MEDIUM, level, x, y, z, xAux, yAux, zAux, random);
        }
    }

    public static class SmallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SmallProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return create(sprites, SMALL, level, x, y, z, xAux, yAux, zAux, random);
        }
    }
}
