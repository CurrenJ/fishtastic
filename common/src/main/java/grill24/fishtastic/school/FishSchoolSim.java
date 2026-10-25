package grill24.fishtastic.school;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.WaterDomain;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One interactive school's motion: a {@link FlockEngine} in a {@link WaterDomain} built from the
 * visit's water — the same engine the ambient population and the tanks run, and nothing else.
 *
 * <p>The server runs one of these per school and every watching client runs a copy. Both are
 * built from the same {@link Setup} and fed the same {@link Input}s at the same step counts, and
 * the engine is a pure function of those (fishsim's {@code SchoolLockstepTest}), so the copies
 * agree bit for bit without positions being streamed. Everything here is therefore deterministic
 * by construction: inputs carry floats (never doubles a side might round differently), in the
 * school's own frame (blocks from {@link Setup#origin}, so precision does not fall off far from
 * the world origin), and anything an input decides — which way a fish comes at a bobber — is
 * decided from engine state both sides share.
 *
 * <p>No watcher is ever declared: each client has a different player nearby, and a startle on one
 * copy would be a divergence (decided in docs/interactive-fish-schools-design-spec.md).
 */
public final class FishSchoolSim {

    /** The ambient population's per-fish rotation jitter, degrees. */
    public static final float ROTATION_JITTER = 20f;

    /** A member's starting point, in the school's frame. */
    public record Placement(UUID member, float l, float y, float d) {}

    /**
     * Everything a copy is built from.
     *
     * @param origin  the school's frame: positions and anchors are blocks from this block's corner
     * @param columns the visit's water, in world coordinates
     * @param spec    the species' engine descriptor, as the server resolved it — shipped rather than
     *                re-derived, so a client never builds the school from different numbers
     * @param seed    the engine's base seed
     * @param members every member present at step 0, in order
     */
    public record Setup(BlockPos origin, List<WaterDomain.Column> columns, FishSpec spec, long seed,
                        List<Placement> members) {
        public Setup {
            columns = List.copyOf(columns);
            members = List.copyOf(members);
        }
    }

    /** A change the server makes to the school, applied on every copy at the same step count. */
    public sealed interface Input permits Leave, Rejoin, Approach, Withdraw, Water {}

    /** The member leaves the water: hooked into a fight, caught, or gone with its visit. */
    public record Leave(UUID member) implements Input {}

    /** The member is back in the water at {@code (l, y, d)}, as after a fight that got away. */
    public record Rejoin(UUID member, float l, float y, float d) implements Input {}

    /**
     * A bobber at {@code (l, y, d)} — the point under it a fish can nose, chosen by
     * {@link #anchorPoint} — and the member sent to it, approaching {@code speedScale} times as
     * fast as a visit and holding there {@code holdSeconds}. Whether it could go is the engine's
     * answer, the same on every copy; {@link #apply} returns it.
     */
    public record Approach(UUID anchor, UUID member, float l, float y, float d, float speedScale,
                           float holdSeconds) implements Input {}

    /** The bobber is gone, or the server gave up on it: its fish, if still sent, is called off. */
    public record Withdraw(UUID anchor) implements Input {}

    /** The visit's water changed shape (a block placed or broken in it). */
    public record Water(List<WaterDomain.Column> columns) implements Input {
        public Water {
            columns = List.copyOf(columns);
        }
    }

    /** A sent fish touched its bobber ({@code touch}), or let go of it. */
    public record Event(UUID anchor, boolean touch) {}

    /** An input and the step it is applied before (when {@link #steps()} equals {@code step}). */
    public record Stamped(long step, Input input) {}

    private final BlockPos origin;
    private final FishSpec spec;
    private final long seed;
    private final WaterDomain.BodyBox body;
    private final FlockEngine engine = new FlockEngine(Tunables.GROUP);
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Shelter> anchors = new LinkedHashMap<>();
    private WaterDomain domain;
    private long steps;

    private FishSchoolSim(BlockPos origin, FishSpec spec, long seed, WaterDomain domain) {
        this.origin = origin;
        this.spec = spec;
        this.seed = seed;
        this.body = WaterDomain.BodyBox.of(spec.locomotion(), spec.length());
        this.domain = domain;
    }

    /** A copy at step 0, or null when the water cannot be built into a domain at all. */
    public static @Nullable FishSchoolSim create(Setup setup) {
        WaterDomain.BodyBox body = WaterDomain.BodyBox.of(setup.spec().locomotion(), setup.spec().length());
        WaterDomain domain = buildDomain(setup.origin(), setup.columns(), body);
        if (domain == null) return null;
        FishSchoolSim sim = new FishSchoolSim(setup.origin(), setup.spec(), setup.seed(), domain);
        for (Placement p : setup.members()) sim.order.add(p.member());
        sim.engine.rebuild(sim.specs(), setup.seed(), 0f, ROTATION_JITTER, domain);
        for (int i = 0; i < setup.members().size(); i++) {
            Placement p = setup.members().get(i);
            sim.engine.placeFish(i, p.l(), p.y(), p.d());
        }
        return sim;
    }

    private static @Nullable WaterDomain buildDomain(BlockPos origin, List<WaterDomain.Column> columns,
                                                     WaterDomain.BodyBox body) {
        // Every face closed: a school stays inside its own visit's water (design spec §5.4).
        return WaterDomain.build(columns, origin.getX(), origin.getY(), origin.getZ(), body.above(), null);
    }

    /** The species descriptor a school of {@code length}-block, {@code locomotion} fish runs with. */
    public static FishSpec spec(float length, Locomotion locomotion, ShelterUse shelterUse) {
        // One species per school: the id only has to agree with itself.
        return new FishSpec(length, locomotion, false, 0, shelterUse);
    }

    private FishSpec[] specs() {
        FishSpec[] specs = new FishSpec[order.size()];
        for (int i = 0; i < specs.length; i++) {
            // Alternate the starting heading, as a tank's per-slot mirror flag does.
            specs[i] = new FishSpec(spec.length(), spec.locomotion(), (i & 1) == 0, spec.species(), spec.shelterUse());
        }
        return specs;
    }

    public BlockPos origin() { return origin; }
    public FishSpec spec() { return spec; }
    public WaterDomain.BodyBox body() { return body; }
    public FlockEngine engine() { return engine; }
    public WaterDomain domain() { return domain; }
    /** Steps taken since step 0. */
    public long steps() { return steps; }
    /** The members in the water, in engine order. */
    public List<UUID> members() { return List.copyOf(order); }
    public int indexOf(UUID member) { return order.indexOf(member); }

    /** One engine step; each touch and let-go since the last is handed to {@code events}. */
    public void step(@Nullable Consumer<Event> events) {
        engine.step();
        steps++;
        engine.drainTriggerEvents(e -> {
            if (events != null && e.key() instanceof UUID anchor) events.accept(new Event(anchor, e.start()));
        });
    }

    /** Applies {@code input} before the next step. False when it changed nothing. */
    public boolean apply(Input input) {
        return switch (input) {
            case Leave leave -> leave(leave.member());
            case Rejoin rejoin -> rejoin(rejoin);
            case Approach approach -> approach(approach);
            case Withdraw withdraw -> withdraw(withdraw.anchor());
            case Water water -> water(water.columns());
        };
    }

    private boolean leave(UUID member) {
        int gone = order.indexOf(member);
        if (gone < 0) return false;
        int[] carry = new int[order.size() - 1];
        for (int i = 0, j = 0; i < order.size(); i++) {
            if (i != gone) carry[j++] = i;
        }
        order.remove(gone);
        engine.rebuildPreserving(specs(), carry, seed, 0f, ROTATION_JITTER, domain);
        return true;
    }

    private boolean rejoin(Rejoin rejoin) {
        if (order.contains(rejoin.member())) return false;
        int[] carry = new int[order.size() + 1];
        for (int i = 0; i < order.size(); i++) carry[i] = i;
        carry[order.size()] = -1;
        order.add(rejoin.member());
        engine.rebuildPreserving(specs(), carry, seed, 0f, ROTATION_JITTER, domain);
        engine.placeFish(order.size() - 1, rejoin.l(), rejoin.y(), rejoin.d());
        return true;
    }

    private boolean approach(Approach approach) {
        int fish = order.indexOf(approach.member());
        if (fish < 0 || anchors.containsKey(approach.anchor())) return false;
        // Nose first along the line it comes from: the fish's own side of the bobber.
        float dl = approach.l() - engine.posL()[fish], dd = approach.d() - engine.posD()[fish];
        anchors.put(approach.anchor(), Shelter.noseAnchor(approach.l(), approach.y(), approach.d(), dl, dd,
                approach.holdSeconds(), approach.anchor()));
        domain.rebuildShelters(List.copyOf(anchors.values()));
        if (engine.sendToTrigger(anchorIndex(approach.anchor()), fish, approach.speedScale())) return true;
        anchors.remove(approach.anchor());
        domain.rebuildShelters(List.copyOf(anchors.values()));
        return false;
    }

    private boolean withdraw(UUID anchor) {
        if (anchors.remove(anchor) == null) return false;
        domain.rebuildShelters(List.copyOf(anchors.values()));
        return true;
    }

    private boolean water(List<WaterDomain.Column> columns) {
        WaterDomain next = buildDomain(origin, columns, body);
        if (next == null) return false;
        next.rebuildShelters(List.copyOf(anchors.values()));
        domain = next;
        int[] carry = new int[order.size()];
        for (int i = 0; i < carry.length; i++) carry[i] = i;
        engine.rebuildPreserving(specs(), carry, seed, 0f, ROTATION_JITTER, domain);
        return true;
    }

    private int anchorIndex(UUID anchor) {
        int i = 0;
        for (UUID key : anchors.keySet()) {
            if (key.equals(anchor)) return i;
            i++;
        }
        return -1;
    }

    /** Whether the fish sent to {@code anchor} is still on its way there or holding at it. */
    public boolean errandActive(UUID anchor) {
        int s = anchorIndex(anchor);
        return s >= 0 && engine.triggerFish(s) >= 0;
    }

    public boolean hasAnchor(UUID anchor) {
        return anchors.containsKey(anchor);
    }

    /** Where {@code anchor}'s fish puts its nose, in the school's frame, or null if there is no such anchor. */
    public float @Nullable [] anchorAt(UUID anchor) {
        Shelter shelter = anchors.get(anchor);
        if (shelter == null) return null;
        Shelter.Mouth face = shelter.mouths().get(0);
        return new float[]{face.centerL(), face.centerY(), face.centerD()};
    }

    /**
     * Where under a bobber at {@code (l, y, d)} a fish can put its nose: the first point at or
     * below it, down to {@link #ANCHOR_SEARCH_DEPTH}, where the whole anchor (its box reaches
     * {@link Shelter#NOSE_ANCHOR_HALF} above and below) is in the water by the engine's own rule,
     * which is what the engine checks before it will send a fish there. A floating bobber sits
     * above the head-room a fish keeps under the surface, so this is usually a little below it.
     * Null when there is no such point. Not part of the lockstep: the server calls it, and ships
     * the answer inside its {@link Approach}.
     */
    public float @Nullable [] anchorPoint(float l, float y, float d) {
        float h = Shelter.NOSE_ANCHOR_HALF;
        for (float drop = 0f; drop <= ANCHOR_SEARCH_DEPTH; drop += ANCHOR_SEARCH_STEP) {
            float at = y - drop;
            if (domain.contains(l, at + h, d) && domain.contains(l, at, d) && domain.contains(l, at - h, d)) {
                return new float[]{l, at, d};
            }
        }
        return null;
    }

    /** How far below a bobber its anchor may be looked for, blocks: the hook hangs under it. */
    static final float ANCHOR_SEARCH_DEPTH = 1.5f;
    static final float ANCHOR_SEARCH_STEP = 0.1f;

    /** Fish #{@code i}'s heading for display, degrees, from its velocity ({@code atan2(l, d)}). */
    public float yawDeg(int i) {
        return (float) Math.toDegrees(StrictMath.atan2(engine.velL()[i], engine.velD()[i]));
    }

    /**
     * A fingerprint of where every fish is right now: two copies at the same step agree on it
     * exactly when they agree on every position bit. Order-sensitive, as the engine is.
     */
    public int checksum() {
        int h = order.size();
        for (int i = 0; i < order.size(); i++) {
            h = 31 * h + order.get(i).hashCode();
            h = 31 * h + Float.floatToIntBits(engine.posL()[i]);
            h = 31 * h + Float.floatToIntBits(engine.posY()[i]);
            h = 31 * h + Float.floatToIntBits(engine.posD()[i]);
        }
        return h;
    }

    /** Whether a member placed at {@code (l, y, d)} would have room for its whole body. */
    public boolean bodyFits(float l, float y, float d) {
        return domain.bodyFits(l, y, d, body);
    }
}
