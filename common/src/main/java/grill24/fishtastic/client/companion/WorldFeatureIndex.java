package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.Vec;
import grill24.fishsim.companion.WorldFeature;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** One client-level index. Discovery and deltas share a strict block-read budget. */
public final class WorldFeatureIndex {
    public static final int READ_BUDGET = 512;
    private final ClientLevel level;
    private final Map<BlockPos, Section> sections = new HashMap<>();
    private final Set<BlockPos> deltas = new LinkedHashSet<>();
    private List<BlockPos> owners = List.of();
    private record Query(BlockPos owner, double radius) {}
    private final Map<Query,List<WorldFeature>> featureCache = new HashMap<>();
    private final Map<BlockPos,List<Vec>> warmthCache = new HashMap<>();
    private long totalReads, totalNanos;
    private int lastReads;
    private long lastNanos;
    private static final class Section {
        final BlockPos origin;
        final LevelChunkSection source;
        final Map<Long, WorldFeature> warmth = new HashMap<>(), lights = new HashMap<>();
        final Map<Long, WorldFeature> water = new HashMap<>();
        final BitSet surfaces = new BitSet(4096);
        int cursor;
        Section(BlockPos origin, LevelChunkSection source) { this.origin = origin; this.source = source; }
    }
    public record Cost(int reads, long nanos, long totalReads, long totalNanos, int pendingSections) {}
    public WorldFeatureIndex(ClientLevel level) { this.level = level; }
    public Cost cost() { return new Cost(lastReads,lastNanos,totalReads,totalNanos,(int)sections.values().stream().filter(s -> s.cursor < 4096).count()); }
    private static boolean candidate(BlockState state) {
        return CompanionAmbient.warm(state) || state.getLightEmission() >= 10 || state.getFluidState().is(FluidTags.WATER);
    }
    public void tick(List<BlockPos> owners) {
        long start = System.nanoTime(); lastReads = 0; this.owners = List.copyOf(owners);
        // Drop unloaded/out-of-range sections; never request a chunk load.
        if (sections.entrySet().removeIf(e -> !near(e.getKey()) || chunk(e.getKey()) == null)) { featureCache.clear(); warmthCache.clear(); }
        for (BlockPos owner : owners) {
            for (int x = (owner.getX()-10)>>4; x <= (owner.getX()+10)>>4; x++)
                for (int z = (owner.getZ()-10)>>4; z <= (owner.getZ()+10)>>4; z++)
                    for (int y = (owner.getY()-10)>>4; y <= (owner.getY()+10)>>4; y++) {
                        BlockPos origin = new BlockPos(x<<4,y<<4,z<<4);
                        LevelChunk c = chunk(origin);
                        if (c == null || origin.getY() < level.getMinY() || origin.getY() >= level.getMaxY()) continue;
                        LevelChunkSection source = c.getSection(c.getSectionIndex(origin.getY()));
                        Section old = sections.get(origin);
                        if (old != null && old.source == source) continue;
                        Section s = new Section(origin,source);
                        if (source.hasOnlyAir() || !source.maybeHas(WorldFeatureIndex::candidate)) s.cursor = 4096;
                        sections.put(origin,s);
                        featureCache.clear(); warmthCache.clear();
                    }
        }
        Iterator<BlockPos> changes = deltas.iterator();
        while (changes.hasNext() && lastReads <= READ_BUDGET-4) {
            BlockPos p = changes.next(); changes.remove();
            update(p); update(p.below());
        }
        List<Section> dirty = sections.values().stream().filter(s -> s.cursor < 4096)
                .sorted(Comparator.comparingDouble(s -> distance(s.origin))).toList();
        for (Section s : dirty) {
            while (s.cursor < 4096 && lastReads <= READ_BUDGET-2) {
                int i = s.cursor++;
                classify(s,s.origin.offset(i&15,(i>>8)&15,(i>>4)&15));
            }
            if (lastReads > READ_BUDGET-2) break;
        }
        if (lastReads>0) { featureCache.clear(); warmthCache.clear(); }
        else {
            warmthCache.keySet().retainAll(owners);
            featureCache.keySet().removeIf(q -> !owners.contains(q.owner()));
        }
        lastNanos = System.nanoTime()-start; totalNanos += lastNanos; totalReads += lastReads;
    }
    private LevelChunk chunk(BlockPos p) { return level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false); }
    private double distance(BlockPos p) { return owners.stream().mapToDouble(o -> o.distSqr(p.offset(8,8,8))).min().orElse(Double.MAX_VALUE); }
    private boolean near(BlockPos p) {
        return owners.stream().anyMatch(o -> Math.abs((o.getX()>>4)-(p.getX()>>4))<=1 && Math.abs((o.getY()>>4)-(p.getY()>>4))<=1 && Math.abs((o.getZ()>>4)-(p.getZ()>>4))<=1);
    }
    public void changed(BlockPos p) {
        BlockPos a = new BlockPos((p.getX()>>4)<<4,(p.getY()>>4)<<4,(p.getZ()>>4)<<4);
        BlockPos below = p.below();
        BlockPos b = new BlockPos((below.getX()>>4)<<4,(below.getY()>>4)<<4,(below.getZ()>>4)<<4);
        if (sections.containsKey(a) || sections.containsKey(b)) deltas.add(p.immutable());
    }
    public void chunkChanged(ChunkPos p) {
        sections.keySet().removeIf(k -> (k.getX()>>4)==p.x() && (k.getZ()>>4)==p.z());
        featureCache.clear(); warmthCache.clear();
    }
    private void update(BlockPos p) {
        Section s = sections.get(new BlockPos((p.getX()>>4)<<4,(p.getY()>>4)<<4,(p.getZ()>>4)<<4));
        if (s != null && chunk(p) != null) classify(s,p);
    }
    private void classify(Section s, BlockPos p) {
        lastReads++;
        BlockState state = level.getBlockState(p);
        long id = p.asLong(); Vec pos = new Vec(p.getX()+.5,p.getY()+.5,p.getZ()+.5);
        s.warmth.remove(id); s.lights.remove(id);
        if (CompanionAmbient.warm(state)) s.warmth.put(id,WorldFeature.warmth(id,pos));
        if (state.getLightEmission() >= 10) s.lights.put(id,WorldFeature.light(id,pos,state.getLightEmission()));
        int local = (p.getX()&15) | ((p.getZ()&15)<<4) | ((p.getY()&15)<<8);
        boolean surface = false;
        if (state.getFluidState().is(FluidTags.WATER)) {
            lastReads++;
            surface = level.getBlockState(p.above()).isAir();
        }
        boolean wasSurface = s.surfaces.get(local);
        s.surfaces.set(local,surface);
        if (wasSurface == surface) return;
        // A compact bitset retains alternatives for deltas without listing ocean cells as features.
        long cell = BlockPos.asLong(p.getX()>>2,p.getY()>>4,p.getZ()>>2);
        s.water.remove(cell);
        for (int y=15;y>=0;y--) {
            boolean found = false;
            for (int z=(p.getZ()&12);z<(p.getZ()&12)+4 && !found;z++)
                for (int x=(p.getX()&12);x<(p.getX()&12)+4;x++) {
                    if (!s.surfaces.get(x | (z<<4) | (y<<8))) continue;
                    BlockPos w = s.origin.offset(x,y,z);
                    Set<FishProfile.Zone> zones = FishProfile.Zone.resolve(level.getBiome(w),w.getY(),level.getSeaLevel());
                    String zone = zones.contains(FishProfile.Zone.DEEP_OCEAN) ? "zone:deep_ocean"
                            : zones.contains(FishProfile.Zone.OCEAN) ? "zone:ocean" : "zone:river";
                    s.water.put(cell,WorldFeature.water(w.asLong(),new Vec(w.getX()+.5,w.getY()+1,w.getZ()+.5),zone));
                    found = true; break;
                }
            if (found) break;
        }
    }
    public List<WorldFeature> features(BlockPos owner, double radius) {
        Query key = new Query(owner.immutable(),radius);
        List<WorldFeature> cached = featureCache.get(key);
        if (cached!=null) return cached;
        long start = System.nanoTime();
        List<WorldFeature> out = new ArrayList<>();
        for (Section s : sections.values()) for (Map<Long,WorldFeature> map : List.of(s.lights,s.water))
            for (WorldFeature f : map.values()) if (f.pos().distanceTo(new Vec(owner.getX()+.5,owner.getY()+.5,owner.getZ()+.5))<=radius) out.add(f);
        for (Vec p : warmth(owner)) { BlockPos b = BlockPos.containing(p.x(),p.y(),p.z()); out.add(WorldFeature.warmth(b.asLong(),p)); }
        out.sort(Comparator.comparingDouble((WorldFeature f) -> f.pos().distanceTo(new Vec(owner.getX()+.5,owner.getY()+.5,owner.getZ()+.5))).thenComparingLong(WorldFeature::id));
        List<WorldFeature> result = List.copyOf(out); featureCache.put(key,result);
        long elapsed = System.nanoTime()-start; lastNanos += elapsed; totalNanos += elapsed;
        return result;
    }
    /** Exact legacy warmth query: same cuboid, 4-block cells and four nearest representatives. */
    public List<Vec> warmth(BlockPos owner) {
        List<Vec> cached = warmthCache.get(owner);
        if (cached!=null) return cached;
        // Insert in the legacy X, Y, Z scan order: colliding cell keys iterate in insertion order, which breaks distance ties.
        List<WorldFeature> candidates = new ArrayList<>();
        for (Section s : sections.values()) for (WorldFeature f : s.warmth.values()) {
            BlockPos p = BlockPos.of(f.id());
            if (Math.abs(p.getX()-owner.getX())<=6 && Math.abs(p.getY()-owner.getY())<=3 && Math.abs(p.getZ()-owner.getZ())<=6) candidates.add(f);
        }
        candidates.sort(Comparator.comparingInt((WorldFeature f)->BlockPos.getX(f.id())).thenComparingInt(f->BlockPos.getY(f.id())).thenComparingInt(f->BlockPos.getZ(f.id())));
        Map<Long,WorldFeature> cells = new HashMap<>();
        for (WorldFeature f : candidates) {
            BlockPos p = BlockPos.of(f.id());
            long cell = BlockPos.asLong(p.getX()>>2,p.getY()>>2,p.getZ()>>2);
            WorldFeature old = cells.get(cell);
            double distance = p.distSqr(owner);
            BlockPos oldPos = old == null ? null : BlockPos.of(old.id());
            boolean earlier = oldPos != null && (p.getX()<oldPos.getX() || p.getX()==oldPos.getX() && (p.getY()<oldPos.getY() || p.getY()==oldPos.getY() && p.getZ()<oldPos.getZ()));
            if (old == null || distance<oldPos.distSqr(owner) || distance==oldPos.distSqr(owner) && earlier) cells.put(cell,f);
        }
        List<Vec> result = cells.values().stream().sorted(Comparator.comparingDouble((WorldFeature f)->BlockPos.of(f.id()).distSqr(owner))).limit(4).map(WorldFeature::pos).toList();
        warmthCache.put(owner.immutable(),result);
        return result;
    }
}
