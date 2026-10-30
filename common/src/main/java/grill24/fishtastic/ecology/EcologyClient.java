package grill24.fishtastic.ecology;

import org.jetbrains.annotations.Nullable;

/**
 * Client-side holder for the ecology seed the server sent for the current dimension. Plain static
 * state (written on the client thread by {@link grill24.fishtastic.network.EcologySeedSyncPacket},
 * read by client ecology code); no Minecraft client classes, so common code may reference it freely.
 */
public final class EcologyClient {

    private record Held(String dimension, long seed) {
    }

    private static volatile Held held;

    private EcologyClient() {
    }

    public static void set(String dimension, long seed) {
        held = new Held(dimension, seed);
    }

    public static void clear() {
        held = null;
    }

    public static boolean hasSeed() {
        return held != null;
    }

    /** The seed for {@code dimension}, or null when none has arrived for it (or it is a stale other dimension). */
    public static @Nullable Long seedFor(String dimension) {
        Held current = held;
        return current != null && current.dimension().equals(dimension) ? current.seed() : null;
    }

    /** The dimension the held seed belongs to, or null. */
    public static @Nullable String dimension() {
        Held current = held;
        return current == null ? null : current.dimension();
    }
}
