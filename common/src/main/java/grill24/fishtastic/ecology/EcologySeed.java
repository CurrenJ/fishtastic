package grill24.fishtastic.ecology;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The per-dimension seed that every ecology hash starts from. The server derives it from the world
 * seed and the dimension and sends only the result to clients, so clients can reproduce the ecology
 * without ever learning the world seed.
 *
 * <p>The derivation is SHA-256 truncated to 64 bits rather than a bijective mixer: a bijection of a
 * 64-bit world seed would be invertible by anyone holding this class, a truncated cryptographic
 * hash is not. Pure and stable; runs only on dimension setup, never per tick.
 */
public final class EcologySeed {

    public static final String DOMAIN = "fishtastic:ecology";

    private EcologySeed() {
    }

    /** {@code hash(worldSeed, "fishtastic:ecology", dimension)}; {@code dimension} is e.g. {@code minecraft:overworld}. */
    public static long derive(long worldSeed, String dimension) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(worldSeed).array());
            digest.update(DOMAIN.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(dimension.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /** A short stable label for logs and debug output that does not print the seed itself. */
    public static String fingerprint(long seed) {
        return String.format("%08x", (int) (EcologyHash.mix(seed ^ 0x46494E47L) >>> 32));
    }
}
