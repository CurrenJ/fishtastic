package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.MoodPresets;
import grill24.fishsim.companion.PartyLayer;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The dev half of {@link ClientCompanions} (audit §5.3): phantoms, the debug overlay and its slot,
 * the dev triggers behind {@code /fishtastic companion ...}, forced moods and temperaments, the
 * catch demo, the live party switches, and the self-test's diagnostics. Nothing here runs in normal
 * play unless a dev command or the self-test calls it; the party state it acts on stays in
 * {@link ClientCompanions}.
 */
public final class ClientCompanionsDev {
    private ClientCompanionsDev() {}

    /** Dev: the party slot (0 = lead) the overlay and the dev triggers act on. */
    private static int devSlot = CompanionParty.LEAD;
    /** Dev: draw the debug overlay. */
    private static boolean overlay;
    /** Dev: restarts the cost peaks (the self-test's walk leg). */
    public static void devResetCost() {
        ClientCompanions.peakSamples = 0;
        ClientCompanions.peakStepNanos = 0;
    }
    /**
     * Dev-only (`/fishtastic companion predatorscene`): the predator scene (D21, G4) now, skipping its
     * wait and the standing-still rule: the party's predator laps you and its prey hides.
     */
    public static String devPredatorScene() {
        PartyLayer layer = ClientCompanions.localLayer();
        return layer == null ? "No companion party" : layer.devPredatorScene();
    }
    /** The local companion the dev triggers and the overlay act on: the group holding {@link #devSlot}. */
    @Nullable
    private static ClientCompanion selected() {
        return ClientCompanions.inSlot(ClientCompanions.localCompanions(), devSlot, false);
    }
    /** Called every HUD frame after the minigame draws. Draws the dev debug overlay when it is on. */
    public static void renderHud(GuiGraphics graphics, float partialTick) {
        if (!overlay) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<String> lines = new ArrayList<>(partyLines());
        String switches = switchesLine();
        if (switches != null) lines.add(switches);
        ClientCompanion selected = selected();
        if (selected == null) lines.add("slot " + (devSlot + 1) + ": empty");
        else lines.addAll(selected.debugLines());
        lines.add(costLine());
        int y = 4;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        graphics.fill(2, 2, 8 + width, 6 + lines.size() * 10, 0xA0000000);
        for (String line : lines) {
            graphics.drawString(font, line, 5, y, 0xFFE0E0E0);
            y += 10;
        }
    }
    /**
     * The overlay's party header (party spec Phase 3b): one line per unit, the selected one marked,
     * with each one's home perch, where it is and the hand turn; then the party's hand clock and
     * its lease refusals.
     */
    private static List<String> partyLines() {
        List<ClientCompanion> companions = ClientCompanions.localCompanions();
        if (companions.isEmpty()) return List.of("party: empty");
        List<String> lines = new ArrayList<>();
        lines.add("party (debug slot " + (devSlot + 1) + ")");
        for (ClientCompanion c : companions) lines.add((c.holdsSlot(devSlot) ? "> " : "  ") + c.partyStateLine());
        lines.add(partyClockLine(companions));
        PartyLayer layer = ClientCompanions.localLayer();
        String scene = layer == null ? null : layer.sceneLine();
        if (scene != null) lines.add("  " + scene);
        String cross = layer == null ? null : layer.crossSceneLine();
        if (cross != null) lines.add("  " + cross);
        return lines;
    }
    /** The party's hand clock (shared, or each percher's own) and its lease refusals, with their rate. */
    private static String partyClockLine(List<ClientCompanion> companions) {
        int refusals = 0, perchers = 0, sharedWait = -1;
        long ageTicks = 0;
        String turn = null;
        for (ClientCompanion c : companions) {
            ageTicks = Math.max(ageTicks, c.ageTicks);
            for (int i = 0; i < c.memberCount(); i++) {
                var m = c.agent() == null ? null : c.member(i);
                if (m == null || m.perchHome() == null) continue;
                perchers++;
                refusals += m.leaseRefusals();
                if (m.handClockShared()) {
                    sharedWait = m.handClockWait();
                    if (m.handClockReady()) turn = c.partyLine().substring(0, c.partyLine().indexOf(':'));
                }
            }
        }
        if (perchers == 0) return "  hand clock: none (no perchers)";
        String clock = sharedWait < 0 ? "each percher's own"
                : sharedWait > 0 ? "shared, next call in " + (sharedWait + 19) / 20 + " s"
                : "shared, ready" + (turn == null ? " (nobody's turn: someone is on or going to a hand)" : ", turn: " + turn);
        double minutes = Math.max(ageTicks, 1) / (20.0 * 60.0);
        return String.format(Locale.ROOT, "  hand clock: %s  lease refusals %d (%.1f/min)", clock, refusals,
                refusals / minutes);
    }
    /** The sim's cost over the last second: block samples, their time, and the whole step (plan §7 risk 3). */
    static String costLine() {
        long samples = 0, sampleNanos = 0, stepNanos = 0, worstStep = 0;
        for (int i = 0; i < ClientCompanions.COST_WINDOW; i++) {
            samples += ClientCompanions.costSamples[i];
            sampleNanos += ClientCompanions.costSampleNanos[i];
            stepNanos += ClientCompanions.costStepNanos[i];
            worstStep = Math.max(worstStep, ClientCompanions.costStepNanos[i]);
        }
        return String.format(Locale.ROOT, "%d companions (%d fish)  blocks %.0f/tick %.0f us/tick  step %.0f us/tick (worst %d us)"
                        + "  peak blocks %d/tick, step %d us",
                ClientCompanions.count(), ClientCompanions.bodyCount(), samples / (double) ClientCompanions.COST_WINDOW, sampleNanos / 1000.0 / ClientCompanions.COST_WINDOW,
                stepNanos / 1000.0 / ClientCompanions.COST_WINDOW, worstStep / 1000, ClientCompanions.peakSamples, ClientCompanions.peakStepNanos / 1000);
    }
    /** The local player's running minigame, if any. */
    @Nullable
    public static FishingMinigameAnimation activeMinigame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return null;
        return ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation() instanceof FishingMinigameAnimation m
                && m.isActive() ? m : null;
    }
    /**
     * Dev-only: a companion with no owner, swimming round an imaginary owner standing still at
     * {@code eye} — a stand-in for another player's companion.
     */
    public static UUID addPhantom(ItemStack fish, Vec3 eye, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        UUID id = UUID.randomUUID();
        if (mc.level != null) {
            ClientCompanions.PHANTOMS.put(id, new ClientCompanions.OwnerParty(null, null, List.of(ClientCompanion.phantom(eye, yaw, fish, mc.level))));
        }
        return id;
    }
    public static void clearPhantoms() {
        ClientCompanions.PHANTOMS.clear();
    }
    /** Dev-only: the ids of every dev phantom currently parked. */
    public static Set<UUID> devPhantomIds() {
        return new HashSet<>(ClientCompanions.PHANTOMS.keySet());
    }
    /** Dev-only (`/fishtastic companion phantom <species> [size]`): another companion, 4 blocks ahead. */
    public static String devAddPhantom(ItemStack fish) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 ahead = mc.player.getEyePosition().add(mc.player.getForward().multiply(1, 0, 1).normalize().scale(4));
        addPhantom(fish, ahead, mc.player.getYRot() + 180f);
        return "Phantom " + fish.getHoverName().getString() + " added 4 blocks ahead (an imaginary owner it swims round)";
    }
    /**
     * Dev-only (`/fishtastic companion debug slot <1-3>`): which party slot the overlay and the dev
     * triggers act on — the companion (solo or group) holding that slot.
     */
    public static String devSlot(int slot) {
        devSlot = slot;
        ClientCompanion c = selected();
        return "Companion debug slot " + (slot + 1) + (c == null ? " (empty)" : ": " + c.partyLine());
    }
    /** Dev-only: the party slot the dev triggers act on, 0-based. */
    public static int devSlot() {
        return devSlot;
    }
    /**
     * Dev-only (`/fishtastic companion temperament <id>|species`): rebuilds the selected companion
     * (the debug slot's) with a temperament of your choosing, or its species' own.
     */
    public static String devTemperament(@Nullable String id) {
        Temperament chosen = null;
        if (id != null) {
            Temperament t = Temperament.named(id);
            if (!t.name().equals(id.toLowerCase(Locale.ROOT)) && !id.equalsIgnoreCase("baseline")) {
                return "Unknown temperament " + id;
            }
            chosen = t;
        }
        // A group shares one mind: every slot it holds gets the override, so it survives regrouping.
        ClientCompanion c = selected();
        if (c != null) {
            for (int s : c.group().slots()) ClientCompanions.temperamentOverrides[s] = chosen;
        } else {
            ClientCompanions.temperamentOverrides[devSlot] = chosen;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) ClientCompanions.PARTIES.remove(player.getUUID()); // rebuilt next tick
        return "Companion temperament (slot " + (devSlot + 1) + "): " + (id == null ? "its species' own" : chosen.name());
    }
    /** Dev-only: every temperament id the command accepts. */
    public static List<String> devTemperamentIds() {
        List<String> ids = new ArrayList<>();
        ids.add("baseline");
        for (Temperament t : Temperament.ALL) ids.add(t.name());
        return ids;
    }
    /** Dev-only (`/fishtastic companion fake hostile|cat|item|companion|clear`): seen by the whole local party. */
    public static String devFake(String kind) {
        if (kind.equals("clear")) {
            CompanionDevPoints.clear();
            return "Fake points cleared";
        }
        ClientCompanion local = selected();
        Player player = Minecraft.getInstance().player;
        if (local == null || player == null) return "No companion in debug slot " + (devSlot + 1) + " — bond one first";
        CompanionDevPoints.Kind k = CompanionDevPoints.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        return CompanionDevPoints.add(k, new CompanionWorldAdapter.OwnerTracker().owner(player, true));
    }
    /** Dev-only (`/fishtastic companion hurt`): the selected companion sees its owner hurt next tick. */
    public static String devHurt() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        local.devHurt();
        return "Owner hurt (for the slot " + (devSlot + 1) + " companion only)";
    }
    /** A forced mood: a preset's ambient and habitat, and the campfire "warm" placed (or null). */
    public record DevMood(MoodPresets.Preset preset, @Nullable Vec warmAt) {}
    @Nullable private static DevMood devMood;
    /** The forced mood for your own companions, or null for the real world's. */
    @Nullable
    public static DevMood devMood() { return devMood; }
    /** The mood presets the dev trigger takes, and "off". */
    public static List<String> devMoodNames() {
        List<String> names = new ArrayList<>();
        for (var p : MoodPresets.ALL) names.add(p.name());
        names.add("off");
        return names;
    }
    /**
     * Dev-only (`/fishtastic companion mood <name>`): your companions feel a preset mood (cold,
     * warm, glum, glee, sleepy, moon) as if the world and their species were the preset's; "off"
     * goes back to the real world. "warm" puts a virtual campfire 3.5 blocks in front of you.
     */
    public static String devMood(String name) {
        if (name.equals("off")) {
            devMood = null;
            return "Companion moods back to the real world";
        }
        MoodPresets.Preset preset;
        try {
            preset = MoodPresets.named(name);
        } catch (IllegalArgumentException e) {
            return "No mood " + name + " (" + String.join(", ", devMoodNames()) + ")";
        }
        Vec warm = null;
        var player = Minecraft.getInstance().player;
        if (preset.warmAt() != null && player != null) {
            Vec3 fwd = Vec3.directionFromRotation(0, player.getYRot()).scale(preset.warmAt().z());
            Vec3 at = player.position().add(fwd).add(0, preset.warmAt().y(), 0);
            warm = new Vec(at.x, at.y, at.z);
        }
        devMood = new DevMood(preset, warm);
        return "Companions feel: " + name + (warm != null ? " (a campfire 3.5 blocks ahead, stand away from it)" : "")
                + " — see the overlay's mood line";
    }
    /** Dev-only (`/fishtastic companion drowsy`): as if you'd stood still long enough for it to settle. */
    public static String devDrowsy() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        local.devDrowsy();
        return "Companion drowsy — stand still and it settles";
    }
    /** Dev-only (`/fishtastic companion ground`): a ground benthic (and its group) back down off the perch. */
    public static String devGround() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        return local.devGround() ? "Companion back down to the ground" : "That companion doesn't walk on the ground";
    }
    /** Dev-only (`/fishtastic companion climb`): a ground benthic (and its group, in turn) climbs onto the perch. */
    public static String devClimb() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        return local.devClimb() ? "Companion climbing on — stand still" : "That companion doesn't walk on the ground";
    }
    /** Dev-only (`/fishtastic companion tumble`): a perched ground benthic is knocked off (waits until attached). */
    public static String devTumble() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        return local.devTumble() ? "Companion tumbles off once it's perched and out of cooldown"
                : "That companion doesn't walk on the ground";
    }
    private static String noSelected() {
        return "No companion in debug slot " + (devSlot + 1);
    }
    /** Dev-only (`/fishtastic companion debug on|off`): the debug overlay. */
    public static String devOverlay(boolean on) {
        overlay = on;
        return "Companion debug overlay " + (on ? "on" : "off");
    }
    /**
     * Dev-only (`/fishtastic companion catchdemo`): opens a demo bar whose one fish target is a
     * member of the local lead companion's own species, flagged undiscovered. Lives here rather than
     * in the command because the command class must never use {@code Minecraft.player} — see the
     * class note on {@code CompanionCommand}.
     */
    public static String devOpenCatchDemo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "No client player";
        ItemStack companion = CompanionStorage.lead(mc.player);
        if (companion.isEmpty()) return "No lead companion — bond one first (or /fishtastic companion on)";
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
    private static final CompanionTunables DEFAULTS = CompanionTunables.defaults();
    /** Dev-only (`/fishtastic companion switch`): every party switch, those changed from the default marked. */
    public static String devSwitchList() {
        StringBuilder out = new StringBuilder("Companion switches:");
        for (String name : CompanionTunables.DEV_SWITCHES) {
            Object value = switchValue(ClientCompanion.TUNABLES, name);
            boolean changed = !value.equals(switchValue(DEFAULTS, name));
            out.append("\n  ").append(name).append(" ").append(show(value))
                    .append(changed ? "  (changed; default " + show(switchValue(DEFAULTS, name)) + ")" : "");
        }
        return out.toString();
    }
    /**
     * Dev-only (`/fishtastic companion switch <name> <value>`): sets one switch for every party this
     * client runs, live.
     *
     * @param value {@code on}/{@code off} (or true/false), or seconds for anchoredPredatorDuckSeconds
     */
    public static String devSwitch(String name, String value) {
        if (!CompanionTunables.DEV_SWITCHES.contains(name)) return "Unknown switch " + name + " — /fishtastic companion switch lists them";
        java.lang.reflect.Field f = switchField(name);
        try {
            if (f.getType() == boolean.class) {
                Boolean on = switch (value.toLowerCase(Locale.ROOT)) {
                    case "on", "true" -> Boolean.TRUE;
                    case "off", "false" -> Boolean.FALSE;
                    default -> null;
                };
                if (on == null) return name + " takes on or off";
                f.setBoolean(ClientCompanion.TUNABLES, on);
            } else {
                float seconds = Float.parseFloat(value);
                if (seconds < 0) return name + " takes seconds, 0 or more";
                f.setFloat(ClientCompanion.TUNABLES, seconds);
            }
        } catch (NumberFormatException e) {
            return name + " takes a number of seconds";
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return name + " " + show(switchValue(ClientCompanion.TUNABLES, name)) + " (live)";
    }
    /** Dev-only (`/fishtastic companion switch reset`): every party switch back to its default. */
    public static String devSwitchReset() {
        for (String name : CompanionTunables.DEV_SWITCHES) {
            Object def = switchValue(DEFAULTS, name);
            if (def.equals(switchValue(ClientCompanion.TUNABLES, name))) continue;
            try {
                switchField(name).set(ClientCompanion.TUNABLES, def);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return "Companion switches back to their defaults";
    }
    /**
     * Self-test only: the hand clock's gap after a hand landing, so a scene sees turns go round in
     * seconds rather than minutes. Read at each landing, so it acts from the next one. Not a switch:
     * {@link #devSwitchReset} leaves it alone, and the caller puts the default back.
     */
    public static void devHandGap(float minSeconds, float maxSeconds) {
        ClientCompanion.TUNABLES.handGapMinSeconds = minSeconds;
        ClientCompanion.TUNABLES.handGapMaxSeconds = maxSeconds;
    }
    /** Self-test only: {@link #devHandGap} back to the defaults. */
    public static void devHandGapReset() {
        devHandGap(DEFAULTS.handGapMinSeconds, DEFAULTS.handGapMaxSeconds);
    }
    /** The overlay's line naming every switch changed from its default, or null when none is. */
    @Nullable
    private static String switchesLine() {
        List<String> changed = new ArrayList<>();
        for (String name : CompanionTunables.DEV_SWITCHES) {
            Object value = switchValue(ClientCompanion.TUNABLES, name);
            if (!value.equals(switchValue(DEFAULTS, name))) changed.add(name + " " + show(value).toUpperCase(Locale.ROOT));
        }
        return changed.isEmpty() ? null : "switches: " + String.join(", ", changed);
    }
    private static java.lang.reflect.Field switchField(String name) {
        try {
            return CompanionTunables.class.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("DEV_SWITCHES names a missing field: " + name, e);
        }
    }
    private static Object switchValue(CompanionTunables t, String name) {
        try {
            return switchField(name).get(t);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
    private static String show(Object value) {
        return value instanceof Boolean b ? (b ? "on" : "off") : value + " s";
    }
    /** Dev diagnostics for the local player's party: every group, then the selected one in detail. */
    public static String debugState() {
        ClientCompanion local = selected();
        String switches = switchesLine();
        return "tracked=" + ClientCompanions.count() + " bodies=" + ClientCompanions.bodyCount() + " perchLayer=" + ClientCompanions.perchLayerCalls + "/" + ClientCompanions.perchSubmits
                + " | " + String.join(" | ", partyLines())
                + (switches == null ? "" : " | " + switches)
                + " | " + (local == null ? "no companion in debug slot " + (devSlot + 1) : String.join(" | ", local.debugLines()))
                + " | " + costLine();
    }
}
