package grill24.fishtastic.neoforge.gametest;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.gametest.FishtasticGameTests;
import grill24.fishtastic.gametest.FishtasticTestSupport;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * Registers the shared gametest harness with NeoForge (A6.1,
 * docs/backport-pass2/track-a-1.21.1.md). The tests themselves are the platform-agnostic
 * {@link FishtasticGameTests}.
 *
 * <p>This replaces a 26.1-era registration that built {@code TestData}/{@code GameTestInstance}/
 * {@code TestEnvironmentDefinition} objects by hand: none of those types exist in NeoForge
 * 21.1.209, whose {@link RegisterGameTestsEvent} exposes only {@code register(Class)} and
 * {@code register(Method)} and drives everything from the annotations. That also removes the
 * hand-written per-test list that had drifted from the Fabric side.
 *
 * <p>{@code RegisterGameTestsEvent} is a mod-bus event, so the bus is named explicitly - FML 4
 * needs {@code bus = MOD}. This class is only discovered because the NeoForge build puts the
 * {@code testmod} source set in the mod's {@code main} Loom mod group.
 */
@EventBusSubscriber(modid = Fishtastic.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeGameTestRegistration {

    private NeoForgeGameTestRegistration() {}

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        FishtasticTestSupport.installPlayerFactory(NeoForgeTestPlayers::makeMockServerPlayerInLevel);
        event.register(FishtasticGameTests.class);
    }
}
