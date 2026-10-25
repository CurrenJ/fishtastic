package grill24.fishtastic.client.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.env.DevEnvironmentCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** Activates and renders the natural, authoritative ambient population around the player. */
public final class ClientAmbientFish {
    private ClientAmbientFish() {}
    private static final AmbientFishRuntime RUNTIME = new AmbientFishRuntime();
    private static DevelopmentView developmentView;
    private static boolean developmentMode, productionTest, harnessActive;
    private static ClientLevel lastNaturalLevel;
    private static Vec3 lastPlayerPosition;
    public static int worldSubmitsThisFrame;

    /** The dev harness may inject a reference scene without shipping its implementation. */
    public interface DevelopmentView {
        /** False when the reference's level/water no longer exists. */
        boolean tick(Minecraft mc);
        int submitWorld(PoseStack poses, SubmitNodeCollector nodes, float partialTick);
    }

    public static boolean useDevelopmentView(DevelopmentView view) {
        if (!DevEnvironmentCheck.isDevelopmentEnvironment() || view == null) return false;
        clear();
        developmentView = view;
        return true;
    }

    public static boolean hasDevelopmentView() { return developmentView != null; }

    /** Harness activation is supplied by the harness, never discovered by production code. */
    public static void harnessActive(boolean active) {
        if (DevEnvironmentCheck.isDevelopmentEnvironment()) harnessActive = active;
    }

    public static boolean startNatural() {
        Minecraft mc = Minecraft.getInstance();
        if (!DevEnvironmentCheck.isDevelopmentEnvironment() || mc.level == null) return false;
        developmentView = null;
        developmentMode = true;
        return RUNTIME.start(mc.level);
    }

    /** Exercise automatic activation with config overrides in the development harness. */
    public static void productionTest(boolean enabled) {
        clear();
        productionTest = enabled;
    }

    public static void settingsChanged() { clear(); }
    public static boolean naturalArmed() { return RUNTIME.armed(); }
    public static AmbientFishRuntime natural() { return RUNTIME; }
    public static long naturalSteps() { return RUNTIME.steps(); }

    public static void clear() {
        developmentView = null;
        developmentMode = false;
        lastNaturalLevel = null;
        lastPlayerPosition = null;
        RUNTIME.clear();
        worldSubmitsThisFrame = 0;
        AmbientContextClient.resetSession();
    }

    public static void invalidate() { clear(); }

    /** Called even with no world, so disconnects release all state. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        grill24.fishtastic.client.FishSchoolClientRuntime.tick(mc);
        if (developmentView != null) {
            if (!developmentView.tick(mc)) clear();
            return;
        }
        // The harness opts in through a development-only setter; normal play reads settings.
        boolean automatic = productionTest || !harnessActive;
        if (!developmentMode && automatic) {
            int density = FishtasticClientConfig.getAmbientFishDensity();
            boolean enabled = FishtasticClientConfig.isAmbientFishEnabled() && density > 0;
            if (!enabled || mc.level == null || mc.player == null) {
                if (RUNTIME.armed()) clear();
                return;
            }
            AmbientBudget budget = AmbientBudget.forDensity(density);
            if (!RUNTIME.armed()) RUNTIME.start(mc.level, budget);
            else RUNTIME.configure(budget);
        }
        if (RUNTIME.armed() && mc.player != null && mc.level != null) {
            Vec3 position = mc.player.position();
            if (lastNaturalLevel != mc.level || (lastPlayerPosition != null
                    && lastPlayerPosition.distanceToSqr(position) > 64 * 64)) {
                RUNTIME.start(mc.level, RUNTIME.budget());
                AmbientContextClient.resetSession();
            }
            lastNaturalLevel = mc.level;
            lastPlayerPosition = position;
        }
        RUNTIME.tick(mc);
        AmbientContextClient.tick(mc, RUNTIME);
    }

    public static void submitWorld(PoseStack poses, SubmitNodeCollector nodes, float partialTick) {
        if (developmentView != null) {
            worldSubmitsThisFrame = developmentView.submitWorld(poses, nodes, partialTick);
        } else {
            RUNTIME.submitWorld(poses, nodes, partialTick);
            worldSubmitsThisFrame = RUNTIME.submitsThisFrame();
        }
        grill24.fishtastic.client.FishSchoolClientRuntime.submitWorld(poses, nodes, partialTick);
    }
}
