package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;

/**
 * Hands school packets to the client runtime by name, so this shared package never links a client
 * class (a dedicated server must be able to load every packet).
 */
final class FishSchoolClientBridge {
    private FishSchoolClientBridge() {}

    private static final String RUNTIME = "grill24.fishtastic.client.FishSchoolClientRuntime";

    static <T> void invoke(String method, Class<T> type, T packet) {
        try {
            Class.forName(RUNTIME).getMethod(method, type).invoke(null, packet);
        } catch (ReflectiveOperationException e) {
            Fishtastic.LOGGER.error("Failed to apply fish school packet {}", type.getSimpleName(), e);
        }
    }
}
