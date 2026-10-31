package grill24.fishtastic.client.ambient;

import grill24.fishtastic.ecology.AmbientFluid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the seeded ambient roster needs to know about each loaded chunk's fluid: which of its 256
 * columns hold the ambient fluid (in the fixed order {@code z * 16 + x}) and how much usable
 * volume there is. It is read straight from the chunk, never from {@link grill24.fishsim.ambient.WaterDiscovery}:
 * discovery covers a window around one player, so two clients standing in different places would
 * disagree about a border chunk, while a chunk scan is the same everywhere the chunk is loaded.
 *
 * <p>Usable volume counts, per column, the fluid cells within {@link #DEPTH} of the column's top
 * fluid cell (the display depth: a deep ocean column counts like a pond column). One chunk is
 * scanned per tick at most, and a scan is kept for {@link #RESCAN_TICKS} so edits are noticed.
 */
public final class AmbientChunkField {

    /** Cells below a column's top fluid cell that count as usable. */
    public static final int DEPTH = 4;
    public static final int RESCAN_TICKS = 100;
    /** How long a chunk that was not loaded when its turn came waits before it is tried again. */
    public static final int UNLOADED_RETRY_TICKS = 20;

    /**
     * One chunk's scan. {@code columns} holds the column indices ({@code z * 16 + x}) with fluid,
     * ascending, and {@code topY} and {@code cells} are indexed alongside it.
     */
    public record Scan(int chunkX, int chunkZ, long scannedTick, int[] columns, int[] topY, int[] cells,
                       int usableCells) {
        public int columnCount() {
            return columns.length;
        }

        public int blockX(int i) {
            return (chunkX << 4) + (columns[i] & 15);
        }

        public int blockZ(int i) {
            return (chunkZ << 4) + (columns[i] >> 4);
        }
    }

    private final Map<Long, Scan> scans = new HashMap<>();
    /** The tick each chunk in range was last found not loaded, so it cannot win every turn and starve the rest. */
    private final Map<Long, Miss> unloadedAt = new HashMap<>();

    private record Miss(int chunkX, int chunkZ, long tick) {
    }
    private AmbientFluid fluid = AmbientFluid.WATER;
    private long scanned;

    public void reset(AmbientFluid fluid) {
        scans.clear();
        unloadedAt.clear();
        this.fluid = fluid;
    }

    public long scansDone() {
        return scanned;
    }

    public List<Scan> all() {
        return new ArrayList<>(scans.values());
    }

    public Scan get(int chunkX, int chunkZ) {
        return scans.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /**
     * Keeps the scans for the chunks within {@code radiusChunks} of a centre fresh: drops the rest,
     * and scans the most overdue chunk (a missing one first), at most one per call.
     */
    public void refresh(ClientLevel level, int centerChunkX, int centerChunkZ, int radiusChunks, long tick) {
        scans.values().removeIf(s -> Math.abs(s.chunkX() - centerChunkX) > radiusChunks + 1
                || Math.abs(s.chunkZ() - centerChunkZ) > radiusChunks + 1);
        unloadedAt.values().removeIf(m -> Math.abs(m.chunkX() - centerChunkX) > radiusChunks
                || Math.abs(m.chunkZ() - centerChunkZ) > radiusChunks);
        int bestX = 0, bestZ = 0;
        long bestAge = -1;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int cx = centerChunkX + dx, cz = centerChunkZ + dz;
                long key = ChunkPos.asLong(cx, cz);
                Scan existing = scans.get(key);
                long age;
                if (existing != null) {
                    age = tick - existing.scannedTick();
                    if (age < RESCAN_TICKS) continue;
                } else {
                    Miss missed = unloadedAt.get(key);
                    if (missed != null && tick - missed.tick() < UNLOADED_RETRY_TICKS) continue;
                    age = missed == null ? Long.MAX_VALUE : tick - missed.tick();
                }
                if (age > bestAge) {
                    bestAge = age;
                    bestX = cx;
                    bestZ = cz;
                }
            }
        }
        if (bestAge < 0) return;
        long bestKey = ChunkPos.asLong(bestX, bestZ);
        Scan scan = scan(level, bestX, bestZ, tick);
        if (scan == null) {
            scans.remove(bestKey);
            unloadedAt.put(bestKey, new Miss(bestX, bestZ, tick));
        } else {
            scans.put(bestKey, scan);
            unloadedAt.remove(bestKey);
        }
        scanned++;
    }

    private Scan scan(ClientLevel level, int cx, int cz, long tick) {
        ChunkAccess access = level.getChunk(cx, cz, ChunkStatus.FULL, false);
        if (!(access instanceof LevelChunk chunk)) return null;
        var tag = fluid == AmbientFluid.LAVA ? FluidTags.LAVA : FluidTags.WATER;
        int[] top = new int[256];
        int[] count = new int[256];
        java.util.Arrays.fill(top, Integer.MIN_VALUE);
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSection();
        for (int si = sections.length - 1; si >= 0; si--) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.getFluidState().is(tag))) continue;
            int baseY = (minSection + si) << 4;
            for (int ly = 15; ly >= 0; ly--) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        FluidState state = section.getFluidState(lx, ly, lz);
                        if (state.isEmpty() || !state.is(tag)) continue;
                        int index = lz * 16 + lx, y = baseY + ly;
                        if (top[index] == Integer.MIN_VALUE) {
                            top[index] = y;
                            count[index] = 1;
                        } else if (y > top[index] - DEPTH) {
                            count[index]++;
                        }
                    }
                }
            }
        }
        int columns = 0;
        for (int i = 0; i < 256; i++) if (top[i] != Integer.MIN_VALUE) columns++;
        int[] index = new int[columns], topY = new int[columns], cells = new int[columns];
        int n = 0, usable = 0;
        for (int i = 0; i < 256; i++) {
            if (top[i] == Integer.MIN_VALUE) continue;
            index[n] = i;
            topY[n] = top[i];
            cells[n] = count[i];
            usable += count[i];
            n++;
        }
        return new Scan(cx, cz, tick, index, topY, cells, usable);
    }
}
