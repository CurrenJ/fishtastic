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
 * A scaled-down replica of vanilla {@code CampfireSmokeParticle} — the soft, slow-rising puff that
 * reads as "campfire smoke" (vanilla's {@code CampfireBlockEntity} never spawns the ordinary ash
 * {@code SMOKE} particle from the fire itself; the cosy/signal puff is the whole visual). Vanilla's
 * own {@code CosyProvider} hardcodes a 3.0 scale sized for a full block; this shrinks it down for the
 * miniature fish tank cosmetic, the same way {@link MiniSmokeParticle} shrinks ordinary ash smoke for
 * furnace-family cosmetics.
 */
public class MiniCampfireSmokeParticle extends SingleQuadParticle {
    private static final float SCALE = 0.6F;

    private MiniCampfireSmokeParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.scale(SCALE);
        this.setSize(0.05F, 0.05F);
        this.lifetime = this.random.nextInt(30) + 40;
        this.gravity = 3.0E-6F;
        this.xd = xa;
        this.yd = ya + this.random.nextFloat() / 500.0F;
        this.zd = za;
        this.setAlpha(0.9F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime && this.alpha > 0.0F) {
            this.xd += this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
            this.zd += this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
            this.yd -= this.gravity;
            this.move(this.xd, this.yd, this.zd);
            if (this.age >= this.lifetime - 20 && this.alpha > 0.01F) {
                this.alpha -= 0.03F;
            }
        } else {
            this.remove();
        }
    }

    // OPAQUE, not vanilla CampfireSmokeParticle's TRANSLUCENT: every particle in this mod that has to
    // render inside the tank's own translucent glass uses OPAQUE (see TankBubbleParticle, MiniSmokeParticle)
    // — translucent particles and translucent block geometry aren't reliably cross-sorted against each
    // other, so the glass can draw over the particle regardless of true depth.
    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
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
            return new MiniCampfireSmokeParticle(level, x, y, z, xAux, yAux, zAux, this.sprites.get(random));
        }
    }
}
