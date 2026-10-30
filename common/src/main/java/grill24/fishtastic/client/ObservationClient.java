package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.client.ambient.AmbientFishRuntime;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.ecology.ObservationRules;
import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.network.ObserveFishPacket;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Spyglass observation, client side (world fish exploration E7, E16): while the player is scoping,
 * the nearest fish (ambient or visit member) inside a generous cone around the crosshair is held;
 * a second of continuous hold on the same fish sends an {@link ObserveFishPacket}, and the server
 * decides. Species already caught or observed are skipped, so a hold only ever starts on something
 * that would teach the player a species.
 *
 * <p>Feedback is one thin bar under the crosshair that fills as the hold goes on.
 */
public final class ObservationClient {

    private ObservationClient() {
    }

    /**
     * One fish a hold could start on. {@code key} is its identity: the ambient ID, or the member UUID.
     * The packet is built only for the hold that completes.
     */
    private record Target(Object key, Identifier species, Vec3 at, Supplier<ObserveFishPacket> packet, double angle) {}

    private static Object heldKey;
    private static Identifier heldSpecies;
    private static int heldTicks;
    private static int graceTicks;
    /** Game time each species was last sent, so a refusal (the server says nothing) is not retried every second. */
    private static final Map<Identifier, Long> SENT_AT = new HashMap<>();
    /** The most obstruction checks per tick: the cone usually holds one or two fish. */
    private static final int MAX_LINE_CHECKS = 4;

    public static void reset() {
        heldKey = null;
        heldSpecies = null;
        heldTicks = 0;
        graceTicks = 0;
    }

    /** Forgets the resend cooldowns too (leaving a world, tests). */
    public static void clear() {
        reset();
        SENT_AT.clear();
    }

    /** Progress of the current hold, 0 when there is none. */
    public static float progress() {
        return heldKey == null ? 0f : ObservationRules.progress(heldTicks);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !mc.player.isScoping() || !mc.options.getCameraType().isFirstPerson()) {
            reset();
            return;
        }
        long now = mc.level.getGameTime();
        SENT_AT.values().removeIf(sent -> now - sent > ObservationRules.RESEND_COOLDOWN_TICKS || sent > now);

        Target target = pick(mc, now);
        if (target == null) {
            if (heldKey != null && ++graceTicks > ObservationRules.HOLD_GRACE_TICKS) reset();
            return;
        }
        if (target.key().equals(heldKey)) {
            heldTicks++;
            graceTicks = 0;
        } else {
            heldKey = target.key();
            heldSpecies = target.species();
            heldTicks = 1;
            graceTicks = 0;
        }
        if (heldTicks >= ObservationRules.HOLD_TICKS) {
            FishtasticTransport.sendToServer(target.packet().get());
            SENT_AT.put(target.species(), now);
            reset();
        }
    }

    /** The nearest-to-crosshair fish a hold may be on, or null. */
    private static Target pick(Minecraft mc, long now) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        List<Target> inCone = new ArrayList<>();
        for (AmbientFishRuntime.Observable fish : ClientAmbientFish.natural().observableFish()) {
            Identifier species = Ids.tryParse(fish.species());
            if (species == null || skip(species, now)) continue;
            Target target = consider(eye, look, species, new Vec3(fish.x(), fish.y(), fish.z()), fish.id(),
                    () -> ObserveFishPacket.ambient(species, fish.id(), fish.chunkX(), fish.chunkZ(), fish.slot(),
                            fish.lifespanIndex(), fish.home()));
            if (target != null) inCone.add(target);
        }
        for (FishSchoolClientRuntime.ObservableMember member : FishSchoolClientRuntime.observableMembers()) {
            if (skip(member.species(), now)) continue;
            Target target = consider(eye, look, member.species(), new Vec3(member.x(), member.y(), member.z()),
                    member.member(), () -> ObserveFishPacket.visit(member.species(), member.school(), member.member()));
            if (target != null) inCone.add(target);
        }
        inCone.sort((a, b) -> Double.compare(a.angle(), b.angle()));
        int checks = 0;
        for (Target target : inCone) {
            // Keep the fish being held while it stays in the cone, even if another is a hair closer.
            if (target.key().equals(heldKey) && visible(mc, eye, target.at())) return target;
        }
        for (Target target : inCone) {
            if (checks++ >= MAX_LINE_CHECKS) break;
            if (visible(mc, eye, target.at())) return target;
        }
        return null;
    }

    private static Target consider(Vec3 eye, Vec3 look, Identifier species, Vec3 at, Object key,
                                   Supplier<ObserveFishPacket> packet) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > ObservationRules.MAX_RANGE) return null;
        if (!ObservationRules.clientCone(look.x, look.y, look.z, dx, dy, dz)) return null;
        return new Target(key, species, at, packet, ObservationRules.angleDeg(look.x, look.y, look.z, dx, dy, dz));
    }

    /** Whether the player already knows the species, was just refused for it, or it is a secret variant. */
    private static boolean skip(Identifier species, long now) {
        if (FishEncyclopediaClientCache.isDiscovered(species)) return true;
        Long sent = SENT_AT.get(species);
        if (sent != null && now - sent < ObservationRules.RESEND_COOLDOWN_TICKS) return true;
        return BuiltInRegistries.ITEM.getOptional(species)
                .map(item -> item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH))
                .orElse(true);
    }

    /** Solid blocks between the eye and the fish hide it; water and lava do not. */
    private static boolean visible(Minecraft mc, Vec3 eye, Vec3 at) {
        HitResult hit = mc.level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(eye) + 0.25 >= at.distanceToSqr(eye);
    }

    /** The hold bar under the crosshair. Drawn only while a hold is running. */
    public static void renderHud(GuiGraphicsExtractor graphics, Minecraft mc) {
        float progress = progress();
        if (progress <= 0f || mc.options.hideGui) return;
        int width = 28, height = 3;
        int x = mc.getWindow().getGuiScaledWidth() / 2 - width / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 12;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0x90000000);
        graphics.fill(x, y, x + Math.round(width * progress), y + height, 0xFFB4FFF0);
    }

    /** Debug line for the self-test and the dev overlay. */
    public static String describe() {
        return heldKey == null ? "no hold" : heldSpecies + " " + heldTicks + "/" + ObservationRules.HOLD_TICKS;
    }
}
