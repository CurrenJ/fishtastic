package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Temperament;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.companion.CompanionFishingOutcome;
import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every companion fish the client can see (docs/fish-companions-sim-plan.md §3.5). Each tick it
 * reads the synced companion of every player in the client level ({@link CompanionStorage}) and
 * keeps one {@link ClientCompanion} per non-empty one — created when a player with a companion
 * comes into view, rebuilt when their companion changes, dropped when they leave or unequip — then
 * steps each one's sim from the world the {@link CompanionWorldAdapter} builds.
 *
 * <p>The client settings are respected at the submit sites: other players' companions can be hidden
 * entirely, and your own can be hidden in first person.
 *
 * <p>Fishing reactions (the dock on the minigame HUD) and pet-to-pet scenes are deferred to later
 * phases of the sim rewrite (plan §5): their packets still arrive here and are ignored for now, and
 * the dock's picture-in-picture renderer stays registered but parked.
 */
public final class ClientCompanions {
    private ClientCompanions() {}

    private static final Map<UUID, ClientCompanion> COMPANIONS = new HashMap<>();
    /** Dev-only companions with no owner, round an imaginary still owner — a stand-in for another player's. */
    private static final Map<UUID, ClientCompanion> PHANTOMS = new HashMap<>();
    @Nullable private static ClientLevel trackedLevel;
    @Nullable private static CompanionWorldAdapter.LevelBlocks blocks;
    /** Another companion counts as a point of interest within this range of an owner, blocks. */
    private static final double COMPANION_RANGE = 16.0;

    /** Dev: the temperament forced on the local companion, or null for its species' own. */
    @Nullable private static Temperament temperamentOverride;
    /** Dev: draw the debug overlay. */
    private static boolean overlay;

    /** Dev diagnostics: companion draws submitted by the most recent world pass (one frame's worth). */
    public static int worldSubmitsThisFrame;
    /** Dev diagnostics: perch-layer calls and the draws it made, cumulative. */
    public static int perchLayerCalls, perchSubmits;
    /** Dev diagnostics: the parked docked-fish renderer's run count (always 0 until fishing returns). */
    public static int pipRenders;

    // Cost of the sim, for the overlay (plan §7 risk 3): block samples and time per tick, averaged
    // over the last second, and the worst tick.
    private static long lastSamples, lastSampleNanos;
    private static final int COST_WINDOW = 20;
    private static final long[] costSamples = new long[COST_WINDOW], costSampleNanos = new long[COST_WINDOW],
            costStepNanos = new long[COST_WINDOW];
    private static int costIndex;
    /** Peaks since the last {@link #devResetCost}: block samples and whole-step time in one tick. */
    private static long peakSamples, peakStepNanos;

    // ---------------------------------------------------------------- tracking

    /** Client tick (20 Hz). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            COMPANIONS.clear();
            PHANTOMS.clear();
            CompanionDevPoints.clear();
            trackedLevel = level;
            blocks = level == null ? null : new CompanionWorldAdapter.LevelBlocks(level);
            lastSamples = lastSampleNanos = 0;
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
                boolean local = player == mc.player;
                COMPANIONS.put(id, new ClientCompanion(player, null, 0f, stack, level,
                        local ? temperamentOverride : null));
            }
        }
        COMPANIONS.keySet().retainAll(seen);

        CompanionDevPoints.tick(level);
        List<PointOfInterest> devPoints = CompanionDevPoints.points();
        long step = 0;
        for (ClientCompanion companion : all()) {
            companion.tick(level, blocks, othersNear(companion), devPoints);
            step += companion.stepNanos;
        }
        recordCost(step);
    }

    /** Every companion this client runs, phantoms included. */
    private static List<ClientCompanion> all() {
        List<ClientCompanion> all = new ArrayList<>(COMPANIONS.size() + PHANTOMS.size());
        all.addAll(COMPANIONS.values());
        all.addAll(PHANTOMS.values());
        return all;
    }

    /** The other companions near {@code self}'s fish, as its points of interest (from this client's own sims). */
    private static List<PointOfInterest> othersNear(ClientCompanion self) {
        if (self.agent() == null || count() < 2) return List.of();
        List<PointOfInterest> out = new ArrayList<>();
        int i = 0;
        for (ClientCompanion other : all()) {
            i++;
            if (other == self || other.agent() == null) continue;
            if (other.agent().position().distanceTo(self.agent().position()) > COMPANION_RANGE) continue;
            // A stable id per companion that can't collide with an entity's: its owner's entity id,
            // negated (phantoms have no owner; they take an index below every real id).
            int id = other.owner() != null ? -1 - other.owner().getId() : Integer.MIN_VALUE + i;
            PointOfInterest p = other.asPoint(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static void recordCost(long stepNanos) {
        if (blocks == null) return;
        costSamples[costIndex] = blocks.samples - lastSamples;
        costSampleNanos[costIndex] = blocks.nanos - lastSampleNanos;
        costStepNanos[costIndex] = stepNanos;
        peakSamples = Math.max(peakSamples, costSamples[costIndex]);
        peakStepNanos = Math.max(peakStepNanos, stepNanos);
        costIndex = (costIndex + 1) % COST_WINDOW;
        lastSamples = blocks.samples;
        lastSampleNanos = blocks.nanos;
    }

    /** Dev: restarts the cost peaks (the self-test's walk leg). */
    public static void devResetCost() {
        peakSamples = 0;
        peakStepNanos = 0;
    }

    /** A block changed on the client: every companion forgets the blocks it sampled (cheap; resampled lazily). */
    public static void onBlockChanged(BlockPos pos) {
        if (COMPANIONS.isEmpty() && PHANTOMS.isEmpty()) return;
        for (ClientCompanion companion : all()) companion.invalidateBlocks();
    }

    /** A chunk arrived: cells sampled while it was missing read as air, so forget them. */
    public static void onChunkLoaded(ChunkPos pos) {
        onBlockChanged(BlockPos.ZERO);
    }

    /** The fishing-event packet's landing point. Fishing reactions are deferred (plan §5); ignored for now. */
    public static void onFishingEvent(UUID owner, CompanionFishingOutcome outcome) {
    }

    /** The scene packet's landing point. Pet-to-pet scenes return in Phase 4 (plan §5); ignored for now. */
    public static void onScenePacket(CompanionSceneType scene, UUID first, UUID second, long startTime) {
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

    // ---------------------------------------------------------------- world pass

    /** Called from the platform's level submit hook; the stack is camera-relative. */
    public static void submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        worldSubmitsThisFrame = 0;
        if (mc.level == null || count() == 0) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (ClientCompanion companion : COMPANIONS.values()) {
            if (!shouldDraw(companion, firstPerson)) continue;
            if (companion.submitWorld(poseStack, nodes, cam, partialTick)) worldSubmitsThisFrame++;
        }
        if (FishtasticClientConfig.hideOtherCompanions()) return;
        for (ClientCompanion phantom : PHANTOMS.values()) {
            if (phantom.submitWorld(poseStack, nodes, cam, partialTick)) worldSubmitsThisFrame++;
        }
    }

    /**
     * Whether this companion should be drawn at all — false for another player's when companions
     * are hidden or that player is invisible, and for your own in first person when the setting
     * hides it.
     */
    private static boolean shouldDraw(ClientCompanion companion, boolean firstPerson) {
        Player owner = companion.owner();
        if (owner == null) return true;
        boolean local = owner == Minecraft.getInstance().player;
        if (!local) return !FishtasticClientConfig.hideOtherCompanions() && !owner.isInvisible();
        return !(firstPerson && FishtasticClientConfig.hideOwnCompanionFirstPerson());
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

    /** Called every HUD frame after the minigame draws. Draws the dev debug overlay when it is on. */
    public static void renderHud(GuiGraphicsExtractor graphics, @Nullable FishingMinigameAnimation minigame, float partialTick) {
        if (!overlay) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<String> lines = new ArrayList<>();
        ClientCompanion local = local();
        if (local == null) lines.add("no local companion");
        else lines.addAll(local.debugLines());
        lines.add(costLine());
        int y = 4;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        graphics.fill(2, 2, 8 + width, 6 + lines.size() * 10, 0xA0000000);
        for (String line : lines) {
            graphics.text(font, line, 5, y, 0xFFE0E0E0);
            y += 10;
        }
    }

    /** The sim's cost over the last second: block samples, their time, and the whole step (plan §7 risk 3). */
    static String costLine() {
        long samples = 0, sampleNanos = 0, stepNanos = 0, worstStep = 0;
        for (int i = 0; i < COST_WINDOW; i++) {
            samples += costSamples[i];
            sampleNanos += costSampleNanos[i];
            stepNanos += costStepNanos[i];
            worstStep = Math.max(worstStep, costStepNanos[i]);
        }
        return String.format(Locale.ROOT, "%d companions  blocks %.0f/tick %.0f us/tick  step %.0f us/tick (worst %d us)"
                        + "  peak blocks %d/tick, step %d us",
                count(), samples / (double) COST_WINDOW, sampleNanos / 1000.0 / COST_WINDOW,
                stepNanos / 1000.0 / COST_WINDOW, worstStep / 1000, peakSamples, peakStepNanos / 1000);
    }

    /** Called from {@link CompanionPipRenderer}: the docked fish is parked with the fishing reactions. */
    static void submitDockedFish(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTime) {
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
     * Dev-only: a companion with no owner, swimming round an imaginary owner standing still at
     * {@code eye} — a stand-in for another player's companion.
     */
    public static UUID addPhantom(ItemStack fish, Vec3 eye, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        UUID id = UUID.randomUUID();
        if (mc.level != null) PHANTOMS.put(id, new ClientCompanion(null, eye, yaw, fish, mc.level, null));
        return id;
    }

    public static void clearPhantoms() {
        PHANTOMS.clear();
    }

    /** Dev-only: the ids of every dev phantom currently parked. */
    public static Set<UUID> devPhantomIds() {
        return new HashSet<>(PHANTOMS.keySet());
    }

    /** Dev-only (`/fishtastic companion phantom <species> [size]`): another companion, 4 blocks ahead. */
    public static String devAddPhantom(ItemStack fish) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 ahead = mc.player.getEyePosition().add(mc.player.getForward().multiply(1, 0, 1).normalize().scale(4));
        addPhantom(fish, ahead, mc.player.getYRot() + 180f);
        return "Phantom " + fish.getHoverName().getString() + " added 4 blocks ahead (an imaginary owner it swims round)";
    }

    /**
     * Dev-only (`/fishtastic companion temperament <id>|species`): rebuilds the local companion with
     * a temperament of your choosing, or its species' own.
     */
    public static String devTemperament(@Nullable String id) {
        if (id == null) {
            temperamentOverride = null;
        } else {
            Temperament t = Temperament.named(id);
            if (!t.name().equals(id.toLowerCase(Locale.ROOT)) && !id.equalsIgnoreCase("baseline")) {
                return "Unknown temperament " + id;
            }
            temperamentOverride = t;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) COMPANIONS.remove(player.getUUID()); // rebuilt next tick
        return "Companion temperament: " + (id == null ? "its species' own" : temperamentOverride.name());
    }

    /** Dev-only: every temperament id the command accepts. */
    public static List<String> devTemperamentIds() {
        List<String> ids = new ArrayList<>();
        ids.add("baseline");
        for (Temperament t : Temperament.ALL) ids.add(t.name());
        return ids;
    }

    /** Dev-only (`/fishtastic companion fake hostile|cat|item|companion|clear`). */
    public static String devFake(String kind) {
        if (kind.equals("clear")) {
            CompanionDevPoints.clear();
            return "Fake points cleared";
        }
        ClientCompanion local = local();
        Player player = Minecraft.getInstance().player;
        if (local == null || player == null) return "No local companion — bond one first";
        CompanionDevPoints.Kind k = CompanionDevPoints.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        return CompanionDevPoints.add(k, new CompanionWorldAdapter.OwnerTracker().owner(player, true));
    }

    /** Dev-only (`/fishtastic companion hurt`): the local companion sees its owner hurt next tick. */
    public static String devHurt() {
        ClientCompanion local = local();
        if (local == null) return "No local companion";
        local.devHurt();
        return "Owner hurt (for the companion only)";
    }

    /** Dev-only (`/fishtastic companion drowsy`): as if you'd stood still long enough for it to settle. */
    public static String devDrowsy() {
        ClientCompanion local = local();
        if (local == null) return "No local companion";
        local.devDrowsy();
        return "Companion drowsy — stand still and it settles";
    }

    /** Dev-only (`/fishtastic companion debug on|off`): the debug overlay. */
    public static String devOverlay(boolean on) {
        overlay = on;
        return "Companion debug overlay " + (on ? "on" : "off");
    }

    /**
     * Dev-only (`/fishtastic companion catchdemo`): opens a demo bar whose one fish target is a
     * member of the local companion's own species, flagged undiscovered. Lives here rather than in
     * the command because the command class must never use {@code Minecraft.player} — see the class
     * note on {@code CompanionCommand}.
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

    /** Dev diagnostics for the local player's companion. */
    public static String debugState() {
        ClientCompanion local = local();
        return "tracked=" + count() + " perchLayer=" + perchLayerCalls + "/" + perchSubmits + " | " + (local == null ? "no local companion" : String.join(" | ", local.debugLines()))
                + " | " + costLine();
    }
}
