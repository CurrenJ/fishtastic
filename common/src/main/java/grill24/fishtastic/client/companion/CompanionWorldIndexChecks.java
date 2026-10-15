package grill24.fishtastic.client.companion;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import grill24.fishsim.companion.WorldFeature;
import java.util.*;

/** Real client-section regression checks, invoked by companionworldcost on both loaders. */
public final class CompanionWorldIndexChecks {
    private CompanionWorldIndexChecks() {}
    public static Map<String, String> run(ClientLevel level, BlockPos near) {
        Map<String,String> results = new LinkedHashMap<>();
        var profiles = level.registryAccess().lookupOrThrow(grill24.FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        profiles.keySet().stream().sorted(Comparator.comparing(Object::toString)).forEach(id -> {
            var profile = profiles.getValue(id);
            var h = CompanionAmbient.habitatOf(Optional.of(profile));
            grill24.fishtastic.Fishtastic.LOGGER.info("[selftest] companionhabitat species={} climate={} homes={} hours={} nightLights={}",
                    id,h.climate(),new TreeSet<>(h.homes()),h.hours(),h.nightActive());
        });
        for (String species : List.of("bluegill", "betta", "lionfish", "arctic_char", "discus")) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(grill24.fishtastic.Fishtastic.id(species));
            var profile = grill24.fishtastic.companion.CompanionGroups.profileOf(level.registryAccess(),new net.minecraft.world.item.ItemStack(item));
            var habitat = CompanionAmbient.habitatOf(profile);
            var expected = species.equals("arctic_char") ? grill24.fishsim.companion.Habitat.Climate.COLD
                    : species.equals("discus") ? grill24.fishsim.companion.Habitat.Climate.HOT
                    : grill24.fishsim.companion.Habitat.Climate.TEMPERATE;
            results.put("habitat_"+species,profile.isPresent() && habitat.climate()==expected ? "" : "climate="+habitat.climate()+" expected="+expected);
        }
        // An otherwise empty high section in an already loaded chunk; never load another chunk.
        BlockPos p = new BlockPos(near.getX(), 159, near.getZ());
        Map<BlockPos,BlockState> saved = new LinkedHashMap<>();
        for (BlockPos b : List.of(p,p.above(),p.offset(1,0,0),p.offset(1,1,0))) saved.put(b,level.getBlockState(b));
        BlockPos warmOwner = new BlockPos((near.getX()>>4)*16+8,159,(near.getZ()>>4)*16+8);
        List<BlockPos> warmFixtures = new ArrayList<>();
        for (BlockPos offset : List.of(new BlockPos(1,0,0),new BlockPos(0,0,1),new BlockPos(-1,0,0),new BlockPos(0,0,-1),new BlockPos(-4,0,-4),new BlockPos(-4,0,4))) warmFixtures.add(warmOwner.offset(offset));
        for(int x=2;x<=5;x++) for(int z=2;z<=5;z++) warmFixtures.add(warmOwner.offset(x,0,z));
        for(BlockPos b : warmFixtures) saved.putIfAbsent(b,level.getBlockState(b));
        WorldFeatureIndex index = new WorldFeatureIndex(level);
        try {
            for (BlockPos b : saved.keySet()) level.setBlock(b,Blocks.AIR.defaultBlockState(),3);
            settle(index,p,results);
            index.tick(List.of(p));
            results.put("empty_palette_zero",index.cost().reads()==0 ? "" : index.cost().toString());
            level.setBlock(p,Blocks.TORCH.defaultBlockState(),3); index.changed(p); index.tick(List.of(p));
            results.put("delta_light_add",has(index,p,WorldFeature.Kind.LIGHT) ? "" : "light absent");
            var cached = index.features(p,8); index.tick(List.of(p));
            results.put("query_cache_standing",cached==index.features(p,8) ? "" : "standing query was rebuilt");
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3); index.changed(p); index.tick(List.of(p));
            results.put("delta_light_remove",!has(index,p,WorldFeature.Kind.LIGHT) ? "" : "stale light");
            level.setBlock(p,Blocks.WATER.defaultBlockState(),3); index.changed(p); index.tick(List.of(p));
            results.put("water_boundary_surface",has(index,p,WorldFeature.Kind.WATER_SURFACE) ? "" : "surface absent");
            var kneeEye = new net.minecraft.world.phys.Vec3(p.getX()+.5,p.getY()+1.62,p.getZ()+.5);
            var chestEye = new net.minecraft.world.phys.Vec3(p.getX()+.5,p.getY()+1.4,p.getZ()+.5);
            var kneeAmbient = CompanionAmbient.at(level,kneeEye,List.of());
            var chestAmbient = CompanionAmbient.at(level,chestEye,List.of());
            results.put("water_home_knee_dry",!waterHome(kneeAmbient) ? "" : "knee-deep wrongly at water home "+kneeAmbient.place());
            results.put("water_home_chest_wet",waterHome(chestAmbient) ? "" : "chest-deep missing water home "+chestAmbient.place());
            level.setBlock(p.above(),Blocks.STONE.defaultBlockState(),3); index.changed(p.above()); index.tick(List.of(p));
            results.put("water_boundary_cover",!has(index,p,WorldFeature.Kind.WATER_SURFACE) ? "" : "covered surface remains");
            level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3); index.changed(p.above()); index.tick(List.of(p));
            level.setBlock(p.offset(1,0,0),Blocks.WATER.defaultBlockState(),3); index.changed(p.offset(1,0,0)); index.tick(List.of(p));
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3); index.changed(p); index.tick(List.of(p));
            results.put("water_representative_fallback",has(index,p,WorldFeature.Kind.WATER_SURFACE) ? "" : "alternate surface lost");
            level.setBlock(p,Blocks.CAMPFIRE.defaultBlockState(),3); index.changed(p); index.tick(List.of(p));
            var expected = CompanionAmbient.scanWarmth(level,p);
            results.put("warmth_parity",expected.equals(index.warmth(p)) ? "" : "old="+expected+" index="+index.warmth(p));
            index.chunkChanged(new ChunkPos(p.getX()>>4,p.getZ()>>4));
            results.put("unload_drop",index.features(p,8).isEmpty() ? "" : "stale unloaded features");
            settle(index,p,results);
            results.put("reload_discover",has(index,p,WorldFeature.Kind.WARMTH) ? "" : "warmth absent on reload");
            // More unique deltas than fit one tick must remain bounded and eventually settle.
            for (int x=0;x<16;x++) for (int y=144;y<160;y++) for (int z=0;z<16;z++) index.changed(new BlockPos((p.getX()>>4)*16+x,y,(p.getZ()>>4)*16+z));
            for (int t=0;t<40;t++) { index.tick(List.of(p)); if(index.cost().reads()>WorldFeatureIndex.READ_BUDGET) results.put("delta_budget","over budget "+index.cost()); }
            results.putIfAbsent("delta_budget","");
            index.tick(List.of(p));
            results.put("standing_zero",index.cost().reads()==0 ? "" : index.cost().toString());
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3); index.changed(p);
            settle(index,warmOwner,results);
            for(int i=0;i<warmFixtures.size();i++) {
                BlockPos b = warmFixtures.get(i);
                level.setBlock(b,i<6 ? Blocks.CAMPFIRE.defaultBlockState() : Blocks.LAVA.defaultBlockState(),3);
                index.changed(b);
            }
            settle(index,warmOwner,results);
            var oldMulti = CompanionAmbient.scanWarmth(level,warmOwner);
            var newMulti = index.warmth(warmOwner);
            results.put("warmth_lava_cells_exact",oldMulti.equals(newMulti) ? "" : "old="+oldMulti+" new="+newMulti);
            BlockPos firstTie = warmOwner.offset(0,0,1), secondTie = warmOwner.offset(1,0,0);
            var firstVec = new grill24.fishsim.companion.Vec(firstTie.getX()+.5,firstTie.getY()+.5,firstTie.getZ()+.5);
            var secondVec = new grill24.fishsim.companion.Vec(secondTie.getX()+.5,secondTie.getY()+.5,secondTie.getZ()+.5);
            results.put("warmth_xyz_tie",newMulti.contains(firstVec) && !newMulti.contains(secondVec) ? "" : "points="+newMulti);
            index.features(warmOwner,10); // populate query caches before changing their representative
            level.setBlock(firstTie,Blocks.AIR.defaultBlockState(),3); index.changed(firstTie); index.tick(List.of(warmOwner));
            var afterRemoval = CompanionAmbient.scanWarmth(level,warmOwner);
            results.put("warmth_cached_delta_fallback",afterRemoval.equals(index.warmth(warmOwner)) && index.warmth(warmOwner).contains(secondVec) ? "" : "old="+afterRemoval+" new="+index.warmth(warmOwner));
        } finally {
            saved.forEach((b,state)->level.setBlock(b,state,3));
            saved.keySet().forEach(ClientCompanions::onBlockChanged);
        }
        return results;
    }
    private static boolean waterHome(grill24.fishsim.companion.Ambient ambient) {
        return ambient.place().stream().anyMatch(Set.of("zone:river","zone:ocean","zone:deep_ocean")::contains);
    }
    private static boolean has(WorldFeatureIndex i,BlockPos p,WorldFeature.Kind kind) { return i.features(p,8).stream().anyMatch(f->f.kind()==kind); }
    private static void settle(WorldFeatureIndex i,BlockPos p,Map<String,String> results) {
        for(int t=0;t<200;t++) { i.tick(List.of(p)); if(i.cost().reads()>WorldFeatureIndex.READ_BUDGET) results.put("scan_budget","over budget "+i.cost()); if(i.cost().pendingSections()==0) break; }
        results.putIfAbsent("scan_budget","");
    }
}
