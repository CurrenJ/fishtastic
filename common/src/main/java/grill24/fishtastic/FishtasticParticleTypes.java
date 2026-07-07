package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;

public class FishtasticParticleTypes {
    public static Holder<SimpleParticleType> TANK_BUBBLE;
    public static Holder<SimpleParticleType> MINI_SMOKE;
    public static Holder<SimpleParticleType> MINI_FLAME;

    public static void registerParticleTypes() {
        TANK_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("tank_bubble");
        MINI_SMOKE = RegistrationApiSided.getInstance().registerParticleType("mini_smoke");
        MINI_FLAME = RegistrationApiSided.getInstance().registerParticleType("mini_flame");
    }
}
