package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.BlockQuery;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.OwnerState;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Vec;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The only Minecraft-aware part of the companion sim (docs/fish-companions-sim-plan.md §3.5):
 * builds the {@link CompanionWorld} snapshot the agents step from, once per client tick.
 *
 * <ul>
 *   <li><b>Blocks</b> — {@link LevelBlocks}: a cell is solid when its block has a non-empty
 *       collision shape, wet when it holds water. One instance per level, because the agents'
 *       {@code LocalVoxels} forget everything they sampled whenever the source object changes.</li>
 *   <li><b>Owner</b> — {@link OwnerTracker}: eye, velocity from the tick-to-tick move, view,
 *       sprinting, the camera (only known for the local player; anyone else is taken to be in first
 *       person at the default FOV), water at the chest, and the teleport and hurt edges.</li>
 *   <li><b>Points of interest</b> — hostiles, cats and ocelots, dropped items, plus other players'
 *       companions handed in by {@link ClientCompanions} from the agents it already runs.</li>
 *   <li><b>Held item</b> — the owner's main hand mapped onto the spec §3.1 reaction categories,
 *       per companion (its own species is a category of its own).</li>
 * </ul>
 */
public final class CompanionWorldAdapter {
    private CompanionWorldAdapter() {}

    /** Things are gathered this far round the owner (the drives look 4–10 blocks out). */
    private static final double POINT_RANGE = 12.0, POINT_HEIGHT = 8.0;
    /** A move this long in one tick is a teleport, not a walk (elytra is ~3 blocks a tick). */
    private static final double TELEPORT_BLOCKS = 8.0;
    /** The FOV assumed for other players, whose settings this client can't see. */
    private static final float DEFAULT_FOV = 70f;

    /**
     * The level's blocks as the sim asks about them. Counts the cells it is actually asked to
     * sample, and the time that takes, for the debug overlay (plan §7 risk 3).
     */
    public static final class LevelBlocks implements BlockQuery {
        private final ClientLevel level;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int shapeX, shapeY, shapeZ;
        private float shapeTop;
        private boolean shapeKnown;
        long samples;
        long nanos;

        LevelBlocks(ClientLevel level) {
            this.level = level;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            long start = System.nanoTime();
            cursor.set(x, y, z);
            var shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            boolean solid = !shape.isEmpty();
            shapeX = x;
            shapeY = y;
            shapeZ = z;
            shapeTop = solid ? (float) Math.min(1, shape.max(net.minecraft.core.Direction.Axis.Y)) : 0;
            shapeKnown = true;
            nanos += System.nanoTime() - start;
            samples++;
            return solid;
        }

        @Override
        public float top(int x, int y, int z) {
            // LocalVoxels asks solid then top for each cell. Reuse that collision query.
            if (!shapeKnown || x != shapeX || y != shapeY || z != shapeZ) solid(x, y, z);
            return shapeTop;
        }

        @Override
        public boolean water(int x, int y, int z) {
            cursor.set(x, y, z);
            return level.getFluidState(cursor).is(FluidTags.WATER);
        }

        public ClientLevel level() { return level; }
    }

    /** Per-owner memory across ticks: last eye position (velocity, teleports) and the hurt clock. */
    public static final class OwnerTracker {
        private Vec3 lastEye;
        private int lastHurtTime;
        private final OwnerFallTracker fall = new OwnerFallTracker();

        /** This tick's owner state. Call once per tick, before {@link #hurt}. */
        public OwnerState owner(Player player, boolean local) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 eye = player.getEyePosition();
            Vec3 delta = lastEye == null ? Vec3.ZERO : eye.subtract(lastEye);
            boolean teleported = delta.length() > TELEPORT_BLOCKS;
            if (teleported) delta = Vec3.ZERO;
            lastEye = eye;
            boolean firstPerson = !local || mc.options.getCameraType().isFirstPerson();
            float fov = local ? mc.options.fov().get().floatValue() : DEFAULT_FOV;
            Vec3 chest = eye.subtract(0, OwnerState.CHEST_DROP, 0);
            boolean inWater = player.level().getFluidState(BlockPos.containing(chest)).is(FluidTags.WATER);
            boolean riding = player.isPassenger(), gliding = player.isFallFlying();
            double landedFall = fall.update(player.getY(), player.onGround(), player.fallDistance,
                    !inWater && !player.isInWater() && !riding && !gliding
                            && !player.getAbilities().flying && !player.onClimbable(), teleported);
            return new OwnerState(vec(eye), vec(delta.scale(20)), player.getYRot(), player.getXRot(),
                    player.isSprinting(), teleported, firstPerson, fov, inWater, riding, gliding, landedFall);
        }

        /**
         * Whether the owner was hurt since last tick. Vanilla broadcasts the hurt animation to every
         * client, so this works for anyone's companion without a packet of our own.
         */
        public boolean hurt(Player player) {
            boolean hurt = player.hurtTime > lastHurtTime;
            lastHurtTime = player.hurtTime;
            return hurt;
        }
    }

    /** What the owner's main hand means to a companion of {@code species} (spec §3.1; first match wins). */
    public static CompanionWorld.Held held(ItemStack hand, ItemStack species) {
        if (hand.isEmpty()) return CompanionWorld.Held.NONE;
        if (hand.is(Items.FISHING_ROD) || hand.is(FishtasticItemTags.FISHING_RODS)) return CompanionWorld.Held.ROD;
        if (hand.is(FishtasticItemTags.FISHING_BAIT)) return CompanionWorld.Held.BAIT;
        if (hand.is(species.getItem())) return CompanionWorld.Held.SAME_SPECIES;
        if (hand.is(FishtasticItemTags.PREDATOR_FISH)) return CompanionWorld.Held.PREDATOR;
        if (hand.is(Items.NAME_TAG)) return CompanionWorld.Held.NAME_TAG;
        if (FishtasticItemData.isFood(hand)) return CompanionWorld.Held.FOOD;
        return CompanionWorld.Held.NONE;
    }

    /**
     * The hostiles, cats and dropped items round {@code owner}, then {@code companions} (other
     * players' companions, already built by the caller).
     */
    public static List<PointOfInterest> points(ClientLevel level, Player owner, List<PointOfInterest> companions) {
        return ownerPoints(level, owner).with(companions);
    }

    /**
     * Everything {@link #points} finds round one owner — the entities and the players near them —
     * which depends on the owner alone, so a party's units share one query (audit §6.2).
     */
    public record OwnerPoints(List<PointOfInterest> entities, List<PointOfInterest> players) {

        /** One unit's points: the owner's entities, then {@code companions}, then the nearby players. */
        public List<PointOfInterest> with(List<PointOfInterest> companions) {
            List<PointOfInterest> points = new ArrayList<>(entities.size() + companions.size() + players.size());
            points.addAll(entities);
            points.addAll(companions);
            points.addAll(players);
            return points;
        }
    }

    public static OwnerPoints ownerPoints(ClientLevel level, Player owner) {
        List<PointOfInterest> entities = new ArrayList<>();
        AABB box = AABB.ofSize(owner.getEyePosition(), POINT_RANGE * 2, POINT_HEIGHT * 2, POINT_RANGE * 2);
        for (Entity e : level.getEntities(owner, box, e -> e.isAlive() && !(e instanceof Player))) {
            PointOfInterest.Kind kind;
            if (e instanceof Cat || e instanceof Ocelot) kind = PointOfInterest.Kind.CAT;
            else if (e instanceof Enemy) kind = PointOfInterest.Kind.HOSTILE;
            else if (e instanceof ItemEntity) kind = PointOfInterest.Kind.DROPPED_ITEM;
            else continue;
            entities.add(point(kind, e.getId(), e));
        }
        List<PointOfInterest> players = new ArrayList<>();
        for (Player player : level.players()) {
            if (player == owner || !player.isAlive() || player.distanceToSqr(owner) > 16) continue;
            PointOfInterest body = point(PointOfInterest.Kind.PLAYER, player.getId(), player);
            players.add(new PointOfInterest(body.kind(), body.id(),
                    new Vec(player.getX(), player.getY(), player.getZ()), body.vel(), body.radius()));
        }
        return new OwnerPoints(List.copyOf(entities), List.copyOf(players));
    }

    /** An entity as a point of interest: its centre, its tick-to-tick velocity and half its width. */
    static PointOfInterest point(PointOfInterest.Kind kind, int id, Entity e) {
        double half = e.getBbHeight() / 2;
        Vec pos = new Vec(e.getX(), e.getY() + half, e.getZ());
        Vec vel = new Vec(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo).scale(20);
        if (vel.length() > 20 * TELEPORT_BLOCKS) vel = Vec.ZERO;
        return new PointOfInterest(kind, id, pos, vel, e.getBbWidth() / 2);
    }

    static Vec vec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    static Vec3 vec3(Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
