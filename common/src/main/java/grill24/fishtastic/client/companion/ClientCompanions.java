package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.companion.CompanionFishingOutcome;
import grill24.fishtastic.companion.CompanionSceneType;
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
 *
 * <p>Phase 3 respects the three client settings here and at the submit sites: other players'
 * companions can be hidden entirely, your own can be hidden in first person (but never while it is
 * docked — a docked companion is part of the minigame HUD), and docking itself can be skipped with
 * "waits at the bobber" (which until the fishing-reactions phase simply leaves the fish following).
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
    /** Dev diagnostics: companion draws submitted through the world pass, cumulative. */
    public static int worldSubmits;
    /** Dev diagnostics: draws submitted by the most recent world pass (one frame's worth). */
    public static int worldSubmitsThisFrame;
    /**
     * Dev diagnostics for the §3.1 visibility acceptance: first-person ticks counted, and how many
     * of them the local player's companion was on screen. The self-test samples the deltas.
     */
    public static int visibleTicks, countedTicks;

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

        // A finished minigame resolves the local companion's outcome (§5): the client knows which
        // targets it landed, and whether any was a fish. The animation is remembered until its
        // hide is seen (or it vanishes entirely — the renderer drops it a second after hiding
        // starts), and the outcome fires exactly once. Other players' companions get this from
        // the server's fishing-event packet instead (their bar never existed here).
        FishingMinigameAnimation minigame = activeMinigame();
        if (minigame != null && minigame != lastMinigame) {
            lastMinigame = minigame;
            outcomeResolved = false;
        }
        if (lastMinigame != null && !outcomeResolved
                && (lastMinigame.isHidingNow() || minigame == null)) {
            outcomeResolved = true;
            ClientCompanion local = local();
            if (local != null) {
                CompanionFishingOutcome outcome = CompanionFishingOutcome.of(
                        lastMinigame.hasFishTarget(), lastMinigame.caughtFish());
                if (outcome != CompanionFishingOutcome.NONE) local.setFishingOutcome(outcome);
            }
            lastMinigame = minigame;
        }

        // The acceptance counter: only first person counts, because third person sees your own
        // player model (and the perch layer) the same way another player would.
        ClientCompanion local = local();
        if (local != null && mc.options.getCameraType().isFirstPerson()) {
            countedTicks++;
            if (local.visibleToOwner()) visibleTicks++;
        }
    }

    /** The minigame whose session-end outcome has not been consumed yet, if one is in flight. */
    @Nullable
    private static FishingMinigameAnimation lastMinigame;
    /** One outcome per session: the hide animation lasts a second, so this fires exactly once. */
    private static boolean outcomeResolved;

    /**
     * The fishing-event packet's landing point: another player's session just resolved, so their
     * companion plays the catch wiggle or escape droop where it waits — at their bobber (§5, §11).
     */
    public static void onFishingEvent(UUID owner, CompanionFishingOutcome outcome) {
        ClientCompanion companion = COMPANIONS.get(owner);
        if (companion != null) companion.setFishingOutcome(outcome);
    }

    /**
     * The scene packet's landing point (§10): the server has picked the scene, its two
     * participants and its start time; hand it to whichever of their companions this client
     * tracks. A participant this client can't see simply doesn't play it — nothing to do.
     */
    public static void onScenePacket(CompanionSceneType scene, UUID first, UUID second, long startTime) {
        ClientCompanion firstCompanion = findAny(first);
        if (firstCompanion != null) firstCompanion.startScene(scene, startTime, second, false);
        ClientCompanion secondCompanion = findAny(second);
        if (secondCompanion != null) secondCompanion.startScene(scene, startTime, first, true);
    }

    /** The tracked companion for any owner id — a real player's, or a dev phantom's. */
    @Nullable
    static ClientCompanion findAny(UUID owner) {
        ClientCompanion tracked = COMPANIONS.get(owner);
        return tracked != null ? tracked : PHANTOMS.get(owner);
    }

    /** Dev-only: {@link #findAny} for the self-test scenes, which hold only the phantom's id. */
    @Nullable
    public static ClientCompanion devFindAny(UUID owner) {
        return findAny(owner);
    }

    /** Where a scene partner's body (or a dev phantom's park) is — the scene path math's other end. */
    @Nullable
    static Vec3 scenePartnerPoint(UUID partner) {
        ClientCompanion companion = findAny(partner);
        return companion == null ? null : companion.sceneHolderPoint();
    }

    @Nullable
    public static ClientCompanion local() {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : COMPANIONS.get(player.getUUID());
    }

    /** The tracked companion of any player — the perch layer's lookup by rendered entity id. */
    @Nullable
    public static ClientCompanion get(Player player) {
        return COMPANIONS.get(player.getUUID());
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
        worldSubmitsThisFrame = 0;
        if (mc.level == null || count() == 0) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (ClientCompanion companion : COMPANIONS.values()) {
            if (!shouldDraw(companion, firstPerson) || !shouldDrawWorldPass(companion)) continue;
            boolean local = companion.owner() == mc.player;
            if (companion.submitWorld(poseStack, nodes, camera, partialTick,
                    local ? anchor : null, screenW, screenH, pipCompare)) {
                worldSubmits++;
                worldSubmitsThisFrame++;
            }
        }
        for (ClientCompanion phantom : PHANTOMS.values()) {
            if (FishtasticClientConfig.hideOtherCompanions()) continue;
            if (phantom.submitWorld(poseStack, nodes, camera, partialTick, null, screenW, screenH, false)) {
                worldSubmits++;
                worldSubmitsThisFrame++;
            }
        }
    }

    /**
     * Whether the world pass should draw this companion at all — false for another player's when
     * companions are hidden or that player is invisible, and for your own in first person when the
     * setting hides it (never while docked: the docked fish is part of the minigame HUD and is
     * drawn by the GUI pass).
     */
    private static boolean shouldDraw(ClientCompanion companion, boolean firstPerson) {
        Player owner = companion.owner();
        if (owner == null) return true; // dev phantom
        boolean local = owner == Minecraft.getInstance().player;
        if (!local) return !FishtasticClientConfig.hideOtherCompanions() && !owner.isInvisible();
        return !(firstPerson && !companion.isDocked() && FishtasticClientConfig.hideOwnCompanionFirstPerson());
    }

    /**
     * The world pass skips a percher that is on the perch — the player render layer draws it.
     * Everything else, including the look-down hand hop, is the world pass's to draw.
     */
    private static boolean shouldDrawWorldPass(ClientCompanion companion) {
        return !companion.drawsOnPerch();
    }

    /** True when the player layer should draw this companion perched on its owner's body. */
    public static boolean shouldDrawOnPerch(ClientCompanion companion) {
        Player owner = companion.owner();
        if (owner == null) return false;
        boolean local = owner == Minecraft.getInstance().player;
        if (!local && FishtasticClientConfig.hideOtherCompanions()) return false;
        if (owner.isInvisible()) return false;
        return companion.drawsOnPerch();
    }

    // ---------------------------------------------------------------- HUD pass

    /** Called every HUD frame after the minigame draws, with the live minigame (or null). */
    public static void renderHud(GuiGraphicsExtractor graphics, @Nullable FishingMinigameAnimation minigame, float partialTick) {
        ClientCompanion local = local();
        screenW = graphics.guiWidth();
        screenH = graphics.guiHeight();
        // "Companion waits at the bobber" (§11, §5): the companion keeps its place at the owner's
        // bobber in the world instead of docking — see ClientCompanion#updateFishing.
        boolean dock = minigame != null && !FishtasticClientConfig.companionWaitsAtBobber();
        anchor = local != null && dock ? minigame.companionDockAnchor(screenW, screenH) : null;
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
                local.dockedTime(partialTick), x0, y0, x1, y1,
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

    /** Dev-only: makes the local companion start a peek right now, for the self-test scenes. */
    public static void devStartPeek() {
        ClientCompanion local = local();
        if (local != null) local.devStartPeek();
    }

    /** Dev-only: makes the local companion start its class trick now (the `trick` dev command). */
    public static void devStartTrick() {
        ClientCompanion local = local();
        if (local != null) local.devStartTrick();
    }

    /**
     * Dev-only (`/fishtastic companion catchdemo`): opens a demo bar whose one fish target is a
     * member of the local companion's own species, flagged undiscovered — landing it is a full
     * tiered celebration and the same-species greet fires. No drain, so it waits for
     * {@link #devForceCatch()}. Lives here rather than in the command because the command class
     * must never use {@code Minecraft.player} — see the class note on
     * {@code CompanionCommand}; the client-only classes it touches are loadable only here.
     */
    public static String devOpenCatchDemo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "No client player";
        ItemStack companion = CompanionStorage.get(mc.player);
        if (companion.isEmpty()) return "No companion equipped — bond one first (or /fishtastic companion on)";
        ItemStack reward = companion.copyWithCount(1);
        FishingMinigameAnimation animation = new FishingMinigameAnimation();
        // The bare constructor stages three example TREASURE targets; clear them first, exactly as
        // the start-packet handler and the self-test scenes do.
        animation.getMinigameState().getTargets().clear();
        grill24.fishtastic.util.FishingTarget target = new grill24.fishtastic.util.FishingTarget(
                java.util.List.of(reward), grill24.fishtastic.util.FishingTarget.TargetCategory.FISH,
                new java.util.Random(7L), 0.5f, 0.2f, java.util.List.of());
        target.setNoCatchDrain(true);
        animation.getMinigameState().addTarget(target);
        animation.setUndiscoveredSpecies(java.util.Set.of(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(reward.getItem())));
        ((IGameRendererExtension) mc.gameRenderer).fishtastic$displayItemActivation(() -> animation);
        return "Demo catch open — /fishtastic companion catch to land it";
    }

    /** Dev-only (`/fishtastic companion catch`): lands the first active target through the real catch path. */
    public static String devForceCatch() {
        FishingMinigameAnimation animation = activeMinigame();
        if (animation == null) return "No minigame open — /fishtastic companion catchdemo first";
        var targets = animation.getMinigameState().getTargets();
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getState() == grill24.fishtastic.util.FishingTarget.TargetState.ACTIVE) {
                animation.devCatchTarget(i);
                return "Caught target " + i;
            }
        }
        return "No active target to catch";
    }

    /** Dev-only: plays a catch/escape reaction on the local companion now (the `outcome` command). */
    public static void devSetOutcome(CompanionFishingOutcome kind) {
        ClientCompanion local = local();
        if (local != null) local.setFishingOutcome(kind);
    }

    /** Dev-only (`/fishtastic companion scene stop`): ends every running scene immediately. */
    public static void devStopScenes() {
        for (ClientCompanion companion : COMPANIONS.values()) companion.endScene();
        for (ClientCompanion phantom : PHANTOMS.values()) phantom.endScene();
    }

    /**
     * Dev-only (`/fishtastic companion scene g1|g2`): plays a pet-to-pet scene with a dev phantom
     * as the second companion — a solo game has only one real player, so the phantom stands in and
     * the scene packet lands through the same method the network handler calls. Lives here, not in
     * the command, for the link-time class-resolution reason in {@link #devOpenCatchDemo()}.
     */
    public static String devStartScene(CompanionSceneType type) {
        Minecraft mc = Minecraft.getInstance();
        ClientCompanion local = local();
        if (local == null) return "No local companion — bond one first";
        java.util.UUID partner = devPhantomIds().stream().findFirst().orElseGet(() -> addPhantom(
                local.devStack(), mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2.4))));
        onScenePacket(type, mc.player.getUUID(), partner, mc.level.getGameTime());
        return type.name() + " scene playing (phantom partner) — /fishtastic companion scene stop to end it";
    }

    /** Dev-only (`/fishtastic companion phantom <species> [size]`): parks a second, ownerless fish. */
    public static String devAddPhantom(ItemStack fish) {
        Minecraft mc = Minecraft.getInstance();
        addPhantom(fish, mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2.4)));
        return "Phantom " + fish.getHoverName().getString() + " parked (a second companion to watch)";
    }

    /** Dev-only (§19 scene): pause peeks and tricks on the local companion for a clean measurement. */
    public static void devSuppressMotion(boolean suppressed) {
        ClientCompanion local = local();
        if (local != null) local.devSuppressMotion(suppressed);
    }

    /** Dev-only (§19 scene): restore the pre-polish camera-locked basis on the local companion. */
    public static void devCameraLockedFrame(boolean cameraLocked) {
        ClientCompanion local = local();
        if (local != null) local.devCameraLockedFrame(cameraLocked);
    }

    /** Dev-only: whether the local companion currently runs the forced camera-locked basis. */
    public static boolean devLocalCameraLockedFrame() {
        ClientCompanion local = local();
        return local != null && local.devIsCameraLockedFrame();
    }

    /** Dev-only: the ids of every dev phantom currently parked, for the dev commands and scenes. */
    public static Set<UUID> devPhantomIds() {
        return new HashSet<>(PHANTOMS.keySet());
    }

    /** Dev diagnostics for the local player's companion. */
    public static String debugState() {
        ClientCompanion local = local();
        String head = String.format(java.util.Locale.ROOT, "tracked=%d pipSubmits=%d pipRenders=%d world=%d vis=%d/%d | ",
                count(), pipSubmits, pipRenders, worldSubmits, visibleTicks, countedTicks);
        return head + (local == null ? "no local companion" : local.debugState(anchor, screenW, screenH));
    }
}
