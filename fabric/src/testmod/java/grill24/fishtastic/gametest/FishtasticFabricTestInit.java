package grill24.fishtastic.gametest;

/**
 * PORT-ONLY Fabric side of the shared gametest harness (A6.1). Listed as a second
 * {@code fabric-gametest} entrypoint purely so Fabric instantiates it, which installs this
 * loader's mock-player factory into {@link FishtasticTestSupport} before any test runs.
 *
 * <p>It declares no test methods of its own; the tests live in {@code FishtasticGameTests},
 * which is the other entrypoint. Vanilla's
 * {@link net.minecraft.gametest.framework.GameTestHelper#makeMockServerPlayerInLevel()} is
 * sufficient here - see {@link FishtasticTestSupport} for why NeoForge cannot use it.
 */
public class FishtasticFabricTestInit {

    public FishtasticFabricTestInit() {
        FishtasticTestSupport.installPlayerFactory(helper -> helper.makeMockServerPlayerInLevel());
    }
}
