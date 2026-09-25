package grill24.fishtastic.network;

import net.minecraft.resources.ResourceLocation;

/**
 * A 1.20.1 stand-in for {@code net.minecraft.network.protocol.common.custom.CustomPacketPayload}
 * (docs/backport-pass2/track-b-1.20.1.md, B3.1): 1.20.1 has no packet-payload registry, so packets
 * are dispatched by the platform registrars (B3.2) using this id instead. The 23 payload records
 * keep their {@code TYPE} field and {@code type()} override under the same names.
 */
public interface FishtasticPayload {
    PayloadType<?> type();

    record PayloadType<T extends FishtasticPayload>(ResourceLocation id) {
    }
}
