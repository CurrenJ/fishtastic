package grill24.fishtastic.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * PORT-ONLY seam between the shared gametest harness and each loader's mock-player factory
 * (A6.1, docs/backport-pass2/track-a-1.21.1.md).
 *
 * <p>About a fifth of the suite needs a joined player, and the two loaders cannot produce one
 * the same way. Vanilla's {@link GameTestHelper#makeMockServerPlayerInLevel()} is enough on
 * Fabric, but on NeoForge its mock connection never goes through the configuration-phase
 * handshake that registers NeoForge's modded payload channels, so {@code
 * NetworkRegistry.checkPacket} rejects any custom packet sent to it - which breaks every test
 * whose join sends one. NeoForge therefore installs its own factory
 * ({@code NeoForgeTestPlayers}).
 *
 * <p>Because this class lives in {@code common/src/testmod}, which each platform compiles onto
 * its own classpath, it may name neither {@code makeMockServerPlayerInLevel} nor
 * {@code NeoForgeTestPlayers}. Each platform installs its factory from its own entrypoint
 * before any test runs; {@link #playerSupplier} is what the generated harness passes to the
 * bodies that take a supplier.
 */
public final class FishtasticTestSupport {

    private static Function<GameTestHelper, ServerPlayer> playerFactory;

    private FishtasticTestSupport() {}

    /** Called once per platform, from that platform's gametest entrypoint. */
    public static void installPlayerFactory(Function<GameTestHelper, ServerPlayer> factory) {
        playerFactory = factory;
    }

    /**
     * A supplier of a fresh joined player in this test's level. The bodies call {@code get()}
     * once per player they need, so this stays lazy exactly as the per-platform call sites were.
     */
    public static Supplier<ServerPlayer> playerSupplier(GameTestHelper helper) {
        Function<GameTestHelper, ServerPlayer> factory = playerFactory;
        if (factory == null) {
            throw new IllegalStateException(
                    "No gametest player factory installed: the platform's gametest entrypoint must call "
                            + "FishtasticTestSupport.installPlayerFactory before the tests run");
        }
        return () -> factory.apply(helper);
    }
}
