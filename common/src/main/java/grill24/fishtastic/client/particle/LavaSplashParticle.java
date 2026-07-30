package grill24.fishtastic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Lava-toned replica of vanilla {@code SplashParticle} (the "tease" burst spawned while a fish
 * is being lured in). Same motion code as vanilla, just a different sprite.
 */
public class LavaSplashParticle extends WaterDropParticle {
    public LavaSplashParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.gravity = 0.04F;
        if (ya == 0.0 && (xa != 0.0 || za != 0.0)) {
            this.xd = xa;
            this.yd = 0.1;
            this.zd = za;
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return new LavaSplashParticle(level, x, y, z, xAux, yAux, zAux, this.sprite.get(random));
        }
    }
}
