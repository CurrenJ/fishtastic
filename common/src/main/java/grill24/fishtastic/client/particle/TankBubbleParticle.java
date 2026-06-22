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
 * Rises from a cosmetic chest decoration to the tank's glass ceiling and pops there.
 * Unlike vanilla's {@code BubbleParticle}, survival isn't tied to {@code FluidTags.WATER} —
 * the tank interior has no real water fluid blocks, only a target world Y to ascend to.
 */
public class TankBubbleParticle extends SingleQuadParticle {
    private final double popY;

    private TankBubbleParticle(ClientLevel level, double x, double y, double z, double popY, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.popY = popY;
        // Purely a visual effect — must pass through the tank's own ceiling/wall geometry on the
        // way up to the connected stack's true top, so it shouldn't collide with block shapes.
        this.hasPhysics = false;
        this.setSize(0.02F, 0.02F);
        this.quadSize *= (this.random.nextFloat() * 0.6F + 0.2F) * 0.45F;
        this.xd = (this.random.nextFloat() * 2.0F - 1.0F) * 0.01F;
        this.yd = 0.01F + this.random.nextFloat() * 0.01F;
        this.zd = (this.random.nextFloat() * 2.0F - 1.0F) * 0.01F;
        this.lifetime = 120;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0 || this.y >= this.popY) {
            this.remove();
            return;
        }
        this.yd += 0.0015;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.9F;
        this.zd *= 0.9F;
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
        public Particle createParticle(
                SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            // yAux is repurposed to carry the absolute world Y this bubble should ascend to before
            // popping (the tank's ceiling-underside) — these bubbles don't need an externally
            // supplied initial velocity the way vanilla's BubbleParticle does.
            return new TankBubbleParticle(level, x, y, z, yAux, this.sprites.get(random));
        }
    }
}
