package grill24.fishtastic.client.particle;

import grill24.fishtastic.FishtasticParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The pop a vanilla-style tank bubble plays when it dies. Vanilla's own {@code BubbleParticle}
 * never pops — its {@code bubble_pop} is a separate five-frame particle spawned by other systems
 * at a fixed ~0.1–0.2 quad size — so this is that animation, sized to the bubble that just burst
 * ({@code xAux} carries the dying bubble's quad size). Sits still and plays out in 4 ticks, no
 * physics, like everything else inside the glass.
 */
public class TankBubblePopParticle extends SingleQuadParticle {
    /** Pop ring drawn a little wider than the bubble it replaces, as vanilla's reads. */
    private static final float POP_SCALE = 1.4F;
    private static final int POP_LIFETIME = 4;

    private final SpriteSet sprites;

    private TankBubblePopParticle(ClientLevel level, double x, double y, double z, float bubbleQuadSize, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.hasPhysics = false;
        this.lifetime = POP_LIFETIME;
        this.quadSize = bubbleQuadSize * POP_SCALE;
        this.setSpriteFromAge(sprites);
    }

    /** Spawns the pop for a bubble dying at ({@code x}, {@code y}, {@code z}) with the given quad size. */
    public static void spawn(ClientLevel level, double x, double y, double z, float bubbleQuadSize) {
        level.addParticle(FishtasticParticleTypes.TANK_BUBBLE_POP.value(), x, y, z, bubbleQuadSize, 0.0, 0.0);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return new TankBubblePopParticle(level, x, y, z, (float) xAux, this.sprites);
        }
    }
}
