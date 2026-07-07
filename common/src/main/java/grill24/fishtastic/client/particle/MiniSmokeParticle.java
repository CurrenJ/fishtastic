package grill24.fishtastic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A scaled-down replica of vanilla {@code SmokeParticle} for miniature fish tank furnace cosmetics.
 * Vanilla's own {@code SmokeParticle.Provider} hardcodes full (1.0) scale with no smaller built-in
 * variant, unlike {@code ParticleTypes.SMALL_FLAME} for flame.
 */
public class MiniSmokeParticle extends BaseAshSmokeParticle {
    private static final float SCALE = 0.18F;

    private MiniSmokeParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, 0.1F, 0.1F, 0.1F, xa, ya, za, SCALE, sprites, 0.3F, 8, -0.1F, true);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return new MiniSmokeParticle(level, x, y, z, xAux, yAux, zAux, this.sprites);
        }
    }
}
