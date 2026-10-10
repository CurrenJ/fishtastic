package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.IGuiGraphicsExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every companion fish the client can see (docs/fish-companions.md §11). Each tick it reads the
 * synced companion of every player in the client level ({@link CompanionStorage}) and keeps one
 * {@link ClientCompanion} per non-empty one — created when a player with a companion comes into
 * view, rebuilt when their companion changes, dropped when they leave or unequip. Only the local
 * player's companion docks on the minigame HUD.
 */
public final class ClientCompanions {
    private ClientCompanions() {}

    private static final Map<UUID, ClientCompanion> COMPANIONS = new HashMap<>();
    /** Dev-only companions with no owner, parked at a point — the self-test's stand-in for another player. */
    private static final Map<UUID, ClientCompanion> PHANTOMS = new HashMap<>();
    @Nullable private static ClientLevel trackedLevel;

    /** The minigame's dock anchor from the latest HUD frame, GUI px {x, y, halfSize}; null when there is none. */
    @Nullable private static float[] anchor;
    private static int screenW = 1, screenH = 1;

    /**
     * Dev hook: keep drawing the docked fish in the world as well, and shift the HUD copy one
     * lane-width left, so the two passes can be compared side by side in one frame.
     */
    public static boolean pipCompare;
    /** Dev diagnostics: docked draws queued into the GUI, and how many the renderer actually ran. */
    public static int pipSubmits, pipRenders;
    /** Dev hook: packed light the docked fish uses, or -1 for the world's light. */
    public static int pipLightOverride = -1;

    // ---------------------------------------------------------------- tracking

    /** Client tick (20 Hz). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            COMPANIONS.clear();
            PHANTOMS.clear();
            trackedLevel = level;
        }
        if (level == null) return;

        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            ItemStack stack = CompanionStorage.get(player);
            if (stack.isEmpty()) continue;
            UUID id = player.getUUID();
            seen.add(id);
            ClientCompanion existing = COMPANIONS.get(id);
            if (existing == null || existing.owner() != player || !ItemStack.matches(existing.stack(), stack)) {
                COMPANIONS.put(id, new ClientCompanion(player, null, stack, level));
            }
        }
        COMPANIONS.keySet().retainAll(seen);

        for (ClientCompanion companion : COMPANIONS.values()) {
            companion.tick(companion.owner() == mc.player ? anchor : null);
        }
        for (ClientCompanion phantom : PHANTOMS.values()) phantom.tick(null);
    }

    @Nullable
    public static ClientCompanion local() {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : COMPANIONS.get(player.getUUID());
    }

    /** How many companions the client is currently tracking, phantoms included. */
    public static int count() {
        return COMPANIONS.size() + PHANTOMS.size();
    }

    public static boolean isLocalDocked() {
        ClientCompanion local = local();
        return local != null && local.isDocked();
    }

    // ---------------------------------------------------------------- world pass

    /** Called from the platform's level submit hook; the stack is camera-relative. */
    public static void submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || count() == 0) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        for (ClientCompanion companion : COMPANIONS.values()) {
            float[] dockAnchor = companion.owner() == mc.player ? anchor : null;
            companion.submitWorld(poseStack, nodes, camera, partialTick, dockAnchor, screenW, screenH, pipCompare);
        }
        for (ClientCompanion phantom : PHANTOMS.values()) {
            phantom.submitWorld(poseStack, nodes, camera, partialTick, null, screenW, screenH, false);
        }
    }

    // ---------------------------------------------------------------- HUD pass

    /** Called every HUD frame after the minigame draws, with the live minigame (or null). */
    public static void renderHud(GuiGraphicsExtractor graphics, @Nullable FishingMinigameAnimation minigame, float partialTick) {
        ClientCompanion local = local();
        screenW = graphics.guiWidth();
        screenH = graphics.guiHeight();
        anchor = local != null && minigame != null ? minigame.companionDockAnchor(screenW, screenH) : null;
        if (local == null || !local.isDocked() || anchor == null) return;

        Minecraft mc = Minecraft.getInstance();
        CameraRenderState cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        float pad = anchor[2] * 1.5f;
        int x0 = Mth.floor(anchor[0] - pad), x1 = Mth.ceil(anchor[0] + pad);
        int y0 = Mth.floor(anchor[1] - pad), y1 = Mth.ceil(anchor[1] + pad);

        // The camera's perspective cropped to [x0,x1]×[y0,y1] — an off-axis frustum, so the fish
        // lands in the texture exactly where the full-screen projection would put it.
        float nx0 = 2f * x0 / screenW - 1f, nx1 = 2f * x1 / screenW - 1f;
        float ny0 = 1f - 2f * y1 / screenH, ny1 = 1f - 2f * y0 / screenH;
        float cx = (nx0 + nx1) / 2f, hx = (nx1 - nx0) / 2f;
        float cy = (ny0 + ny1) / 2f, hy = (ny1 - ny0) / 2f;
        Matrix4f crop = new Matrix4f().scale(1f / hx, 1f / hy, 1f).translate(-cx, -cy, 0f);
        Matrix4f projection = crop.mul(cs.projectionMatrix).mul(cs.viewRotationMatrix);

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        pipSubmits++;
        ((IGuiGraphicsExtension) graphics).fishtastic$addPictureInPicture(new CompanionPipRenderState(
                projection, local.boxPose(cam),
                pipLightOverride >= 0 ? pipLightOverride : local.lightAt(local.fishOffset()),
                ClientCompanion.gameTime(partialTick), x0, y0, x1, y1,
                pipCompare ? new org.joml.Matrix3x2f().translation(-2f * pad, 0f) : new org.joml.Matrix3x2f()));
    }

    /** Called from {@link CompanionPipRenderer} inside the docked fish's view-space pose. */
    static void submitDockedFish(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTime) {
        ClientCompanion local = local();
        if (local != null) local.submitDockedFish(poseStack, nodes, light, gameTime);
    }

    /** The local player's running minigame, if any. */
    @Nullable
    public static FishingMinigameAnimation activeMinigame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return null;
        return ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation() instanceof FishingMinigameAnimation m
                && m.isActive() ? m : null;
    }

    // ---------------------------------------------------------------- dev tooling

    /**
     * Dev-only: a companion with no owner, parked at {@code eye}. Headless self-tests have no
     * second player, so this is how they check that the manager draws more than one companion.
     */
    public static UUID addPhantom(ItemStack fish, Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        UUID id = UUID.randomUUID();
        if (mc.level != null) PHANTOMS.put(id, new ClientCompanion(null, eye, fish, mc.level));
        return id;
    }

    public static void clearPhantoms() {
        PHANTOMS.clear();
    }

    /** Dev diagnostics for the local player's companion. */
    public static String debugState() {
        ClientCompanion local = local();
        String head = String.format(java.util.Locale.ROOT, "tracked=%d pipSubmits=%d pipRenders=%d | ",
                count(), pipSubmits, pipRenders);
        return head + (local == null ? "no local companion" : local.debugState(anchor, screenW, screenH));
    }
}
