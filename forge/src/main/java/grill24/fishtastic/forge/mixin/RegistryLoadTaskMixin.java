package grill24.fishtastic.forge.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Makes {@code Registries.TEST_INSTANCE} actually receive this mod's game tests on Forge 26.1.2.
 *
 * <p>Forge 64 ships {@code @net.minecraftforge.gametest.GameTest} and
 * {@link ForgeGameTestHooks#gatherTests}, but nothing in the Forge jars ever calls the gatherer, and
 * {@code Registries.TEST_FUNCTION} is frozen during bootstrap, so mods cannot supply functions
 * either. NeoForge solves this with a source patch to {@code RegistryDataLoader#load}; Forge has no
 * equivalent hook. The constructor of {@code RegistryLoadTask} runs before the registry's datapack
 * entries are loaded and before it is frozen, which is the last writable moment. This mirrors the
 * approach already used by the sibling Potions Plus mod on the same Forge build.
 *
 * <p>The registration class lives in the {@code testmod} source set, which {@code main} cannot
 * reference at compile time; the reflective call is a silent no-op in production, where those
 * classes are absent and {@link ForgeGameTestHooks#isGametestServer()} is false anyway.
 */
@Mixin(RegistryLoadTask.class)
public abstract class RegistryLoadTaskMixin<T> {

    @Shadow
    @Final
    protected RegistryDataLoader.RegistryData<T> data;

    @Shadow
    @Final
    private WritableRegistry<T> registry;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void fishtastic$registerForgeGameTests(
            RegistryDataLoader.RegistryData<T> data,
            Lifecycle lifecycle,
            Map<ResourceKey<?>, Exception> loadingErrors,
            CallbackInfo ci) {
        if (!ForgeGameTestHooks.isGametestServer()) {
            return;
        }
        if (!this.data.key().equals(Registries.TEST_INSTANCE)) {
            return;
        }

        try {
            Class<?> registration =
                    Class.forName("grill24.fishtastic.forge.gametest.ForgeGameTestRegistration");
            Method registerIntoRegistry = registration.getMethod("registerIntoRegistry", WritableRegistry.class);
            registerIntoRegistry.invoke(null, this.registry);
        } catch (ClassNotFoundException e) {
            // Testmod classes are absent from the production jar.
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register Forge game tests", e);
        }
    }
}
