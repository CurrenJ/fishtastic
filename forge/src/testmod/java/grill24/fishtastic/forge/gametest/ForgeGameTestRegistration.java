package grill24.fishtastic.forge.gametest;

import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.gametest.FishtasticGameTests;
import grill24.fishtastic.gametest.FishtasticTestSupport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Forge 52 GameTest registration for the shared harness.
 *
 * <p>{@link FishtasticGameTests} carries vanilla {@link GameTest} annotations plus NeoForge's
 * holder annotations (compiled against testmod-only stubs). Forge cannot read the NeoForge holder,
 * and adding Forge's own holder to the shared class would put a Forge-only annotation type in
 * front of the Fabric and NeoForge annotation parsers. Instead this entrypoint generates Forge
 * {@link TestFunction}s reflectively from the shared methods, so the class stays loader-neutral
 * and the wrapper set is not duplicated.
 *
 * <p>Every generated function uses batch {@code fishtastic} and the explicit template
 * {@code fishtastic:fishtastic_empty}, so Forge's prefix/namespace rewriting never applies and
 * the structure id matches what NeoForge and Fabric resolve. The batch name is also what
 * {@code forge.enabledGameTestNamespaces} filters on.
 *
 * <p>Like the other loaders' entrypoints this installs the joined-player factory and the
 * death-respawn hand-off before any test runs.
 */
public final class ForgeGameTestRegistration {
    private static final String BATCH = "fishtastic";
    private static final String EMPTY_STRUCTURE = "fishtastic:fishtastic_empty";

    private ForgeGameTestRegistration() {}

    /** Called reflectively from {@code FishtasticForge} only in development and only when present. */
    public static void install() {
        FishtasticTestSupport.installPlayerFactory(GameTestHelper::makeMockServerPlayerInLevel);
        // Forge keeps companion parties in world SavedData keyed by player UUID, so a real
        // respawn keeps the same UUID and needs no player-data copy. The harness's synthetic
        // "after" player has a different UUID, so mirror a real respawn by moving the party.
        FishtasticTestSupport.installRespawnHandoff((before, after) ->
                CompanionStorage.setParty(
                        after, new CompanionParty(CompanionStorage.party(before).slots())));
    }

    /** The generator class is registered by the main Forge module, which owns the event type. */
    public static Class<?> generatorClass() {
        return ForgeTestGenerator.class;
    }

    /** Registered via {@code event.register(Class)}; Forge expands it into individual functions. */
    public static final class ForgeTestGenerator {
        private ForgeTestGenerator() {}

        @GameTestGenerator
        public static Collection<TestFunction> generate() {
            List<TestFunction> tests = new ArrayList<>();
            for (Method method : FishtasticGameTests.class.getDeclaredMethods()) {
                GameTest meta = method.getAnnotation(GameTest.class);
                if (meta == null
                        || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != GameTestHelper.class) {
                    continue;
                }
                String name = BATCH + "." + method.getName().toLowerCase(Locale.ROOT);
                tests.add(new TestFunction(
                        BATCH,
                        name,
                        EMPTY_STRUCTURE,
                        Rotation.values()[meta.rotationSteps()],
                        meta.timeoutTicks(),
                        meta.setupTicks(),
                        meta.required(),
                        meta.manualOnly(),
                        meta.attempts(),
                        meta.requiredSuccesses(),
                        meta.skyAccess(),
                        helper -> invoke(method, helper)));
            }
            return tests;
        }

        private static void invoke(Method method, GameTestHelper helper) {
            try {
                Object instance = method.getDeclaringClass().getDeclaredConstructor().newInstance();
                method.invoke(instance, helper);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new RuntimeException(cause);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke GameTest " + method.getName(), e);
            }
        }
    }
}
