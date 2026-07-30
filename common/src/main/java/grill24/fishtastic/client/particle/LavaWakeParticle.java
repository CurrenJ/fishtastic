package grill24.fishtastic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Lava-toned replica of vanilla {@code WakeParticle} — the continuous ripple effect that plays
 * while a fish is circling the bobber. Same motion code; only the sprite differs (see the
 * {@code fishtastic:lava_wake} particle definition).
 */
public class LavaWakeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    private LavaWakeParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.sprites = sprites;
        this.xd *= 0.3F;
        this.yd = this.random.nextFloat() * 0.2F + 0.1F;
        this.zd *= 0.3F;
        this.setSize(0.01F, 0.01F);
        this.lifetime = (int) (8.0 / (this.random.nextFloat() * 0.8 + 0.2));
        this.setSpriteFromAge(sprites);
        this.gravity = 0.0F;
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        int life = 60 - this.lifetime;
        if (this.lifetime-- <= 0) {
            this.remove();
        } else {
            this.yd = this.yd - this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.98F;
            this.yd *= 0.98F;
            this.zd *= 0.98F;
            float size = life * 0.001F;
            this.setSize(size, size);
            this.setSprite(this.sprites.get(life % 4, 4));
        }
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
            return new LavaWakeParticle(level, x, y, z, xAux, yAux, zAux, this.sprites);
        }
    }
}
