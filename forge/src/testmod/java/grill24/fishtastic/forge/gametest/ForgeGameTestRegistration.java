package grill24.fishtastic.forge.gametest;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.gametest.FishtasticGameTests;
import grill24.fishtastic.gametest.FishtasticTestSupport;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the shared gametest harness with Forge (B6.1, docs/backport-pass2/track-b-1.20.1.md).
 * The tests themselves are the platform-agnostic {@link FishtasticGameTests}.
 *
 * <p>Unlike NeoForge 21.1 (A6.1), Forge 47 ships its own real {@code @GameTestHolder}/
 * {@code @PrefixGameTestTemplate} annotations and its own {@code RegisterGameTestsEvent} — no
 * compile-only stub is needed for this module (see {@link FishtasticGameTests}'s "PORT-ONLY"
 * annotation for why Fabric still needs one). {@code RegisterGameTestsEvent} is a mod-bus event
 * ({@code IModBusEvent}), so the bus is named explicitly — Forge's default {@code
 * @Mod.EventBusSubscriber} bus is {@code FORGE}, not {@code MOD}.
 */
@Mod.EventBusSubscriber(modid = Fishtastic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeGameTestRegistration {

    private ForgeGameTestRegistration() {}

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        FishtasticTestSupport.installPlayerFactory(ForgeTestPlayers::create);
        event.register(FishtasticGameTests.class);
    }
}
