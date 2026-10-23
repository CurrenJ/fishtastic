package grill24.fishtastic.forge.gametest;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.gametest.GameTestSelection;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.gametest.ForgeGameTestHooks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registers the shared 377-test suite into Forge's {@code minecraft:test_instance} registry.
 *
 * <p>Invoked reflectively from {@code RegistryLoadTaskMixin} while that registry is still writable.
 * Forge's own {@code @GameTest} discovery ({@link ForgeGameTestHooks#gatherTests}) is used for the
 * annotations; this class only wraps its results in {@link GameTestInstance}s because Forge ships no
 * hook that does it.
 */
public final class ForgeGameTestRegistration {
    private ForgeGameTestRegistration() {}

    @SuppressWarnings("unused") // invoked reflectively
    public static void registerIntoRegistry(WritableRegistry<GameTestInstance> registry) {
        Map<Identifier, ForgeGameTestHooks.TestReference> gathered =
                ForgeGameTestHooks.gatherTests(ForgeFishtasticGameTests.class, null);
        if (GameTestSelection.hasFilter()) {
            gathered.entrySet().removeIf(entry -> !GameTestSelection.matches(entry.getKey().getPath()));
        }

        Map<ResourceKey<GameTestInstance>, GameTestInstance> instances = new LinkedHashMap<>();
        Holder<TestEnvironmentDefinition<?>> environment =
                Holder.direct(new TestEnvironmentDefinition.AllOf(List.of()));
        for (var entry : gathered.entrySet()) {
            TestData<Identifier> raw = entry.getValue().data();
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                    environment, raw.structure(), raw.maxTicks(), raw.setupTicks(), raw.required(),
                    raw.rotation(), raw.manualOnly(), raw.maxAttempts(), raw.requiredSuccesses(),
                    raw.skyAccess(), raw.padding());
            instances.put(ResourceKey.create(Registries.TEST_INSTANCE, entry.getKey()),
                    new ConsumerTestInstance(entry.getValue().consumer(), data));
        }

        instances.forEach((key, instance) -> Registry.register(registry, key, instance));
    }

    /** Minimal {@link GameTestInstance} wrapping a test function; mirrors NeoForge's equivalent. */
    private static final class ConsumerTestInstance extends GameTestInstance {
        private final Consumer<GameTestHelper> test;

        ConsumerTestInstance(Consumer<GameTestHelper> test,
                             TestData<Holder<TestEnvironmentDefinition<?>>> data) {
            super(data);
            this.test = test;
        }

        @Override
        public void run(GameTestHelper helper) {
            this.test.accept(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            throw new UnsupportedOperationException("ConsumerTestInstance is not serializable");
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("fishtastic");
        }
    }
}
