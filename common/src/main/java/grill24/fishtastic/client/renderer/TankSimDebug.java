package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.util.ClientTankGroups;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes, in words, what the client-side simulation is actually doing with one tank's fish —
 * the same split the renderer draws from, reported as text. Written for the class of report that
 * cannot be diagnosed from a screenshot: "fish in this tank just bob in place". A fish that never
 * translates is a fish the engine classified {@code Locomotion.STATIC}, and there are three ways
 * that happens — a lone tank's size gate, a group's size gate (a cramped group), or the fish
 * budget's per-tank quota — which look identical on screen but are told apart by the numbers here:
 * the group's member count and run, the quota, and which of this tank's fish joined the group's
 * engine versus stayed home.
 *
 * <p>Client-only, read-only, and dev-only by use: it reaches {@link ClientTankGroups} and
 * {@link ClientTankFlocks}, which exist on the client (in single-player, the same JVM the command
 * runs in). Runs on whichever thread the command runs on; the state it reads is stable between
 * ticks, so the numbers are a consistent snapshot even without a task hop.
 */
public final class TankSimDebug {

    private TankSimDebug() {}

    /** One line per fact, ready to paste. */
    public static List<String> describe(FishTankBlockEntity be, Level level) {
        List<String> out = new ArrayList<>();
        String at = "(" + be.getBlockPos().toShortString() + ")";

        // The tank's real contents.
        List<ItemStack> fish = new ArrayList<>();
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
            ItemStack stack = be.getItem(slot);
            if (!stack.isEmpty()) fish.add(stack);
        }
        out.add("Tank " + at + ": " + fish.size() + " fish in " + FishTankBlockEntity.CONTAINER_SIZE + " slots");

        ClientTankGroups.Entry entry = ClientTankGroups.get(be, level);
        TankGroups.Group group = entry.group();
        int members = group.members().size();
        out.add("Render group: " + members + " member tank(s)"
                + (entry.domain() != null ? ", straight-run gate " + String.format(java.util.Locale.ROOT, "%.2f", entry.domain().sizeGateRun()) + " blocks" : ", standalone (legacy single-tank model, gate 0.70 blocks)"));

        TankFlockAdapter flock = ClientTankFlocks.getOrCreate(be, be.getBlockPos().hashCode());
        out.add("This tank: " + flock.count() + " fish simulated locally, "
                + (flock.groupFlock() != null ? flock.groupFlock().count() + " in the shared group engine" : "no shared group engine"));

        // The same split both passes apply — the group's own stored budget when there is one, so
        // this describes the verdict the renderer is actually drawing from.
        float gateRun = entry.domain() != null ? entry.domain().sizeGateRun() : 0.7f;
        TankFishBudget budget = flock.groupFlock() != null ? flock.groupFlock().budget()
                : members > 1 ? TankFishBudget.forGroup(group, level) : TankFishBudget.unlimited();
        TankGroupFlock.GroupSplit split = new TankGroupFlock.GroupSplit(
                gateRun, grill24.fishsim.core.Tunables.DEFAULT.gateFactor(), budget);
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
            ItemStack stack = be.getItem(slot);
            if (stack.isEmpty()) continue;
            FishTankBlockEntityRenderer.ResolvedFishRender render =
                    FishTankBlockEntityRenderer.resolveFishRender(stack, level);
            float length = TankFlockAdapter.renderedLength(stack, render.renderCalibration());
            Locomotion locomotion = TankFlockAdapter.locomotionOf(render.animation());
            boolean joins = split.joins(locomotion, length);
            Locomotion drawn = joins ? locomotion : TankGroupFlock.GroupSplit.stayingHomeAs(locomotion);
            out.add("  slot " + slot + ": " + stack.getHoverName().getString()
                    + String.format(java.util.Locale.ROOT, " %.2f blocks ", length)
                    + locomotion + " -> " + (joins ? "swims in the group" : "stays home as " + drawn
                    + " (budget cap " + budget.perTankCap(locomotion) + " for this class)"));
        }
        return out;
    }
}
