package grill24.fishtastic.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

/**
 * Lava-toned replica of vanilla {@code BubbleParticle}. Vanilla's version hardcodes a
 * {@code FluidTags.WATER} survival check and would otherwise despawn the instant it's spawned
 * in lava — this swaps that check to {@code FluidTags.LAVA} so it behaves correctly there.
 */
public class LavaBubbleParticle extends SingleQuadParticle {
    public LavaBubbleParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.setSize(0.02F, 0.02F);
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.2F);
        this.xd = xa * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
        this.yd = ya * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
        this.zd = za * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
        this.lifetime = (int) (8.0 / (this.random.nextFloat() * 0.8 + 0.2));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0) {
            this.remove();
        } else {
            this.yd += 0.002;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.85F;
            this.yd *= 0.85F;
            this.zd *= 0.85F;
            if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.LAVA)) {
                this.remove();
            }
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
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
            return new LavaBubbleParticle(level, x, y, z, xAux, yAux, zAux, this.sprite.get(random));
        }
    }
}
