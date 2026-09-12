package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;

public class FishtasticParticleTypes {
    public static Holder<SimpleParticleType> TANK_BUBBLE;
    /** Fish-emitted micro-bubbles (docs/fish-tank-bubbles.md): 1px, 2px and half-size-vanilla, rolled by weight. */
    public static Holder<SimpleParticleType> TINY_BUBBLE;
    public static Holder<SimpleParticleType> SMALL_BUBBLE;
    public static Holder<SimpleParticleType> MEDIUM_BUBBLE;
    /** The five-frame pop a vanilla-style tank bubble plays when it dies, sized to the bubble. */
    public static Holder<SimpleParticleType> TANK_BUBBLE_POP;
    public static Holder<SimpleParticleType> MINI_SMOKE;
    public static Holder<SimpleParticleType> MINI_FLAME;
    public static Holder<SimpleParticleType> MINI_CAMPFIRE_SMOKE;
    public static Holder<SimpleParticleType> LAVA_WAKE;
    public static Holder<SimpleParticleType> LAVA_BUBBLE;
    public static Holder<SimpleParticleType> LAVA_SPLASH;

    public static void registerParticleTypes() {
        TANK_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("tank_bubble");
        TINY_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("tiny_bubble");
        SMALL_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("small_bubble");
        MEDIUM_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("medium_bubble");
        TANK_BUBBLE_POP = RegistrationApiSided.getInstance().registerParticleType("tank_bubble_pop");
        MINI_SMOKE = RegistrationApiSided.getInstance().registerParticleType("mini_smoke");
        MINI_FLAME = RegistrationApiSided.getInstance().registerParticleType("mini_flame");
        MINI_CAMPFIRE_SMOKE = RegistrationApiSided.getInstance().registerParticleType("mini_campfire_smoke");
        LAVA_WAKE = RegistrationApiSided.getInstance().registerParticleType("lava_wake");
        LAVA_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("lava_bubble");
        LAVA_SPLASH = RegistrationApiSided.getInstance().registerParticleType("lava_splash");
    }
}
