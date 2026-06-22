package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;

public class FishtasticParticleTypes {
    public static Holder<SimpleParticleType> TANK_BUBBLE;

    public static void registerParticleTypes() {
        TANK_BUBBLE = RegistrationApiSided.getInstance().registerParticleType("tank_bubble");
    }
}
