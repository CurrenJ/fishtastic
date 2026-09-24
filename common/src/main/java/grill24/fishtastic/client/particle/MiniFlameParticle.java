package grill24.fishtastic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.RisingParticle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A scaled-down replica of vanilla {@code FlameParticle} for miniature fish tank furnace cosmetics.
 * Vanilla's own smaller variant ({@code ParticleTypes.SMALL_FLAME}, scaled 0.5x) still reads oversized
 * next to a furnace rendered at cosmetic scale (often well under 0.2 blocks), and {@code FlameParticle}
 * itself can't be reused directly since its constructor is private to its own package.
 */
public class MiniFlameParticle extends RisingParticle {
    private MiniFlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, xd, yd, zd);
        this.setSprite(sprite);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void move(double xa, double ya, double za) {
        this.setBoundingBox(this.getBoundingBox().move(xa, ya, za));
        this.setLocationFromBoundingbox();
    }

    @Override
    public float getQuadSize(float a) {
        float s = (this.age + a) / this.lifetime;
        return this.quadSize * (1.0F - s * s * 0.5F);
    }

    // Vanilla FlameParticle's block-light ramp (26.1's LightCoordsUtil.addSmoothBlockEmission).
    @Override
    public int getLightColor(float a) {
        float s = Mth.clamp((this.age + a) / this.lifetime, 0.0F, 1.0F);
        int light = super.getLightColor(a);
        int block = Math.min(240, (light & 0xFF) + (int) (s * 15.0F * 16.0F));
        return block | (light >> 16 & 0xFF) << 16;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private static final float SCALE = 0.18F;

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux) {
            Particle particle = new MiniFlameParticle(level, x, y, z, xAux, yAux, zAux, this.sprites.get(level.getRandom()));
            particle.scale(SCALE);
            return particle;
        }
    }
}
