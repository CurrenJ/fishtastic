package grill24.fishtastic.client.util;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishsim.core.FlockEngine;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticReaction;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The client half of a structure's reactions (docs/fish-shelters.md §12.13): turns the engine's
 * trigger events into the moment itself. A reaction opens when its event starts, holds, and
 * closes; this keeps each one's timeline, plays its quiet sound cues and bubble bursts where the
 * structure stands, and answers the renderer's one question — how open is it now.
 *
 * <p>Nothing here is stored or synced. Every event comes from a client-side engine, keyed by
 * {@link CosmeticReaction.Key}, so the structure is looked up again by its tank and anchor cell.
 */
public final class ClientCosmeticReactions {

    private ClientCosmeticReactions() {}

    /** A gate's door whose fish never came through is closed anyway after this long, ticks. */
    private static final long GATE_MAX_OPEN_TICKS = 600;
    /** A lit glow candle's flame flickers up this often, ticks. */
    private static final int FLAME_INTERVAL_TICKS = 4;

    private static final class Active {
        final CosmeticReaction reaction;
        final CosmeticStructure structure;
        final Rotation rotation;
        final long start;
        long end = -1;
        boolean closeCued;

        Active(CosmeticReaction reaction, CosmeticStructure structure, Rotation rotation, long start) {
            this.reaction = reaction;
            this.structure = structure;
            this.rotation = rotation;
            this.start = start;
        }

        /** When it starts closing: a fixed hold after opening, or for a gate, a hold after its fish is through. */
        long closeStart() {
            long opened = start + reaction.openTicks();
            if (reaction.gate().isEmpty()) return opened + reaction.holdTicks();
            if (end < 0) return Math.max(opened, start + GATE_MAX_OPEN_TICKS);
            return Math.max(opened, end) + reaction.holdTicks();
        }

        long done() {
            return closeStart() + reaction.closeTicks();
        }

        /** 0 at rest to 1 open, eased so a lid or a door starts and stops softly. */
        float openness(float time) {
            float rise = Mth.clamp((time - start) / reaction.openTicks(), 0f, 1f);
            float fall = Mth.clamp((time - closeStart()) / reaction.closeTicks(), 0f, 1f);
            float u = Math.min(rise, 1f - fall);
            return u * u * (3f - 2f * u);
        }
    }

    private static final Map<CosmeticReaction.Key, Active> ACTIVE = new HashMap<>();

    /**
     * A dev override: every reaction held at this openness, 0 to 1, whatever the fish do; below 0,
     * off. The headless preview sets it to look at a structure open.
     */
    public static float forcedOpenness = -1f;
    private static ClientLevel activeLevel;

    /** Takes one engine event. Called right after the engine that raised it steps. */
    public static void accept(FlockEngine.TriggerEvent event) {
        if (!(event.key() instanceof CosmeticReaction.Key key)) return;
        if (!(Minecraft.getInstance().level instanceof ClientLevel level)) return;
        sameLevel(level);
        long now = level.getGameTime();
        if (!event.start()) {
            Active active = ACTIVE.get(key);
            if (active != null && active.end < 0) active.end = now;
            return;
        }
        if (!(level.getBlockEntity(key.tank()) instanceof FishTankBlockEntity tank)) return;
        FishTankBlockEntity.PlacedStructureCosmetic placed = tank.getStructureCosmetics().get(key.anchor());
        if (placed == null) return;
        CosmeticStructure structure = level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                .getOptional(placed.structureId()).orElse(null);
        if (structure == null || key.reaction() >= structure.reactions().size()) return;
        CosmeticReaction reaction = structure.reactions().get(key.reaction());
        Active active = new Active(reaction, structure, placed.rotation(), now);
        ACTIVE.put(key, active);

        reaction.openSound().ifPresent(sound -> play(level, key, active, sound));
        for (CosmeticReaction.Burst burst : reaction.bursts()) {
            double[] at = world(key, active, burst.at().get(0), burst.at().get(1), burst.at().get(2));
            BlockPos top = FishTankBlockEntityRenderer.topOfConnectedTankStack(level, key.tank());
            double pop = top.getY() + FishTankBlockEntityRenderer.TANK_CEILING_Y;
            double spread = structure.scale() * 0.6;
            for (int n = 0; n < burst.count(); n++) {
                level.addParticle(FishtasticParticleTypes.TANK_BUBBLE.value(),
                        at[0] + (level.getRandom().nextDouble() - 0.5) * spread, at[1] + level.getRandom().nextDouble() * spread,
                        at[2] + (level.getRandom().nextDouble() - 0.5) * spread, 0.0, pop, 0.0);
            }
        }
    }

    /** Once per client tick: closing cues, the flicker of lit candles, and forgetting finished moments. */
    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        if (!(Minecraft.getInstance().level instanceof ClientLevel level)) {
            ACTIVE.clear();
            return;
        }
        if (!sameLevel(level)) return;
        long now = level.getGameTime();
        for (Iterator<Map.Entry<CosmeticReaction.Key, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<CosmeticReaction.Key, Active> entry = it.next();
            Active active = entry.getValue();
            if (now >= active.done()) {
                it.remove();
                continue;
            }
            if (!active.closeCued && now >= active.closeStart()) {
                active.closeCued = true;
                active.reaction.closeSound().ifPresent(sound -> play(level, entry.getKey(), active, sound));
            }
            if (!active.reaction.glows().isEmpty() && (now - active.start) % FLAME_INTERVAL_TICKS == 0
                    && active.openness(now) > 0.5f) {
                flames(level, entry.getKey(), active);
            }
        }
    }

    /**
     * How open each of a placed structure's reactions is at {@code time} (game ticks plus the
     * partial tick), in reaction order, or null when none is under way — the common case.
     */
    public static float[] openness(BlockPos tank, CosmeticGridCell anchor, CosmeticStructure structure, float time) {
        if (structure.reactions().isEmpty()) return null;
        if (forcedOpenness >= 0f) {
            float[] all = new float[structure.reactions().size()];
            java.util.Arrays.fill(all, Math.min(1f, forcedOpenness));
            return all;
        }
        if (ACTIVE.isEmpty()) return null;
        float[] out = null;
        for (int r = 0; r < structure.reactions().size(); r++) {
            Active active = ACTIVE.get(new CosmeticReaction.Key(tank, anchor, r));
            if (active == null || active.structure != structure) continue;
            if (out == null) out = new float[structure.reactions().size()];
            out[r] = active.openness(time);
        }
        return out;
    }

    /** Forgets everything when the world changes under us; true when it hasn't. */
    private static boolean sameLevel(ClientLevel level) {
        if (activeLevel == level) return true;
        activeLevel = level;
        ACTIVE.clear();
        return false;
    }

    private static void play(ClientLevel level, CosmeticReaction.Key key, Active active, CosmeticReaction.Sound sound) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(sound.id())
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(sound.id()));
        double[] at = world(key, active, 0f, 1f, 0f);
        level.playLocalSound(at[0], at[1], at[2], event, SoundSource.AMBIENT, sound.volume(), sound.pitch(), false);
    }

    /** A small flame over every lit candle in a glowing group, scaled to the structure. */
    private static void flames(ClientLevel level, CosmeticReaction.Key key, Active active) {
        List<CosmeticStructure.StructurePart> parts = active.structure.parts();
        float xz = active.structure.span().isPresent() ? 1f : active.structure.scale() / (float) CosmeticGridCell.CELL_WIDTH;
        for (CosmeticStructure.StructurePart part : parts) {
            if (part.group().isEmpty() || !active.reaction.glows().contains(part.group().get())) continue;
            BlockState state = part.state();
            if (!(state.getBlock() instanceof CandleBlock)) continue;
            double[] at = world(key, active, part.offsetX() / xz, part.offsetY() + 0.5f, part.offsetZ() / xz);
            level.addParticle(FishtasticParticleTypes.MINI_FLAME.value(), at[0], at[1], at[2], 0.0, 0.0, 0.0);
        }
    }

    /** A point in the structure's build units, turned with it, in world space. */
    static double[] world(CosmeticReaction.Key key, Active active, float bx, float by, float bz) {
        float scale = active.structure.scale();
        float[] r = CosmeticStructures.rotateOffset(active.rotation, bx, bz);
        return new double[]{
                key.tank().getX() + key.anchor().localX() + r[0] * scale,
                key.tank().getY() + CosmeticGridCell.FLOOR_Y + by * scale,
                key.tank().getZ() + key.anchor().localZ() + r[1] * scale};
    }
}
