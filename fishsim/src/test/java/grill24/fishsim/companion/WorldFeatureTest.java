package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldFeatureTest {
    private CompanionRun run(String scene, long seed) {
        CompanionRun run = new CompanionRun("idle_fixed",seed,CompanionTunables.defaults(),true);
        if (scene != null) run.worldScene(scene);
        run.run(1200);
        return run;
    }
    @Test void featureScenesReactAndKeepTheirGuard() {
        for (String scene : List.of("water","light","jukebox","notes","warm")) {
            double baseSeen=0, seen=0, reactions=0; long baseFades=0,fades=0;
            for(long seed=1;seed<=5;seed++) {
                CompanionRun base=run(null,seed), feature=run(scene,seed);
                baseSeen+=base.metrics().seenFraction(); seen+=feature.metrics().seenFraction();
                reactions+=feature.reactionShare(); baseFades+=base.metrics().leashEvents(); fades+=feature.metrics().leashEvents();
                assertEquals(0,feature.metrics().solidPenetrations(),scene);
                assertTrue(feature.samples.stream().allMatch(s->Double.isFinite(s.pos().x())&&Double.isFinite(s.pos().y())&&Double.isFinite(s.pos().z())),scene);
            }
            System.out.printf(java.util.Locale.ROOT,"WORLD %s seen %.4f baseline %.4f reactionShare %.4f fades %d/%d%n",scene,seen/5,baseSeen/5,reactions/5,fades,baseFades);
            assertTrue(seen >= baseSeen-0.05,scene+" seen "+seen/5+" baseline "+baseSeen/5);
            assertTrue(fades<=baseFades+3,scene);
            assertTrue(reactions>0.15,scene+" visibly reacts");
        }
    }
    @Test void pooledFeatureEnvelopeAcrossClassesAndOwnerScripts() {
        StringBuilder failures = new StringBuilder();
        for (String kind : List.of("swim","drift","percher","walker","anchored")) {
            for (String feature : List.of("water","light","jukebox","notes","warm")) {
                double baseline=0,seen=0; long baseFades=0,fades=0; int samples=0;
                for (String script : OwnerScript.NAMES) for (long seed=1;seed<=3;seed++) {
                    CompanionRun base=classRun(kind,script,seed), run=classRun(kind,script,seed);
                    if(feature.equals("water")) base.scene.addWorldWater(base.owner.feet().add(0.7,0,2.5));
                    run.worldScene(feature); base.run(1200); run.run(1200);
                    if(kind.equals("walker") && feature.equals("water") && (run.metrics().leashEvents()>base.metrics().leashEvents() || run.metrics().seenFraction()<base.metrics().seenFraction()-0.02))
                        System.out.printf("WATERCASE %s#%d seen %.3f/%.3f fades %d/%d%n",script,seed,run.metrics().seenFraction(),base.metrics().seenFraction(),run.metrics().leashEvents(),base.metrics().leashEvents());
                    baseline+=base.metrics().seenFraction(); seen+=run.metrics().seenFraction(); samples++;
                    boolean forced=Set.of("teleport","elytra","creative_flight","corridor").contains(script);
                    if(!forced) {baseFades+=base.metrics().leashEvents(); fades+=run.metrics().leashEvents();}
                    assertTrue(run.metrics().solidPenetrations()<=base.metrics().solidPenetrations(),kind+"/"+feature+"/"+script);
                }
                System.out.printf(java.util.Locale.ROOT,"ENVELOPE %s/%s seen %.4f baseline %.4f fades %d/%d%n",kind,feature,seen/samples,baseline/samples,fades,baseFades);
                if(seen<baseline-samples*0.01) failures.append(kind+"/"+feature+" seen "+seen/samples+" vs "+baseline/samples+"\n");
                if(fades>baseFades+3) failures.append(kind+"/"+feature+" fades "+fades+" vs "+baseFades+"\n");
            }
        }
        assertEquals("",failures.toString());
    }
    private CompanionRun classRun(String kind,String script,long seed) {
        if(kind.equals("percher")) return CompanionRun.benthic(script,seed,0.3f,Temperament.BASELINE,null,CompanionTunables.defaults(),true);
        if(kind.equals("walker")) return CompanionRun.benthic(script,seed,0.3f,Temperament.BASELINE,GroundGait.CRAWL,CompanionTunables.defaults(),true);
        Locomotion locomotion=switch(kind) {case "drift"->Locomotion.DRIFT;case "percher"->Locomotion.BENTHIC;case "anchored"->Locomotion.ANCHORED;default->Locomotion.FREE_SWIM;};
        return new CompanionRun(script,seed,locomotion,CompanionTunables.defaults(),true);
    }
    @Test void waterBeyondBandCannotCauseADryDip() {
        CompanionWorld world=OwnerScript.named("idle_fixed",2).nextWorld();
        var water=WorldFeature.water(1,world.owner().chest().add(0,-1,5),"ocean");
        CompanionAgent agent=new CompanionAgent(CompanionTunables.defaults(),2,0.4f,world);
        for(int tick=0;tick<300;tick++) {
            agent.step(world.withFeatures(List.of(water)).withGameTick(tick));
            assertNotEquals(PoseHint.DIP,agent.poseHint());
            assertTrue(agent.emotes().stream().noneMatch(e->e.kind()==Emote.Kind.SPLASH));
        }
    }

    @Test void swimmersAndDriftersActuallyEnterTheWaterThenSwimOut() {
        for(Locomotion motion : List.of(Locomotion.FREE_SWIM,Locomotion.DRIFT)) for(long seed=1;seed<=5;seed++) {
            CompanionRun run=new CompanionRun("idle_fixed",seed,motion,CompanionTunables.defaults(),false); run.worldScene("water");
            boolean entered=false,returned=false; int splashes=0;
            for(int tick=0;tick<1200;tick++) {
                run.tick(); Vec point=run.agent.position();
                boolean wet=run.scene.water((int)Math.floor(point.x()),(int)Math.floor(point.y()),(int)Math.floor(point.z()));
                entered|=wet;
                if(entered && !wet && point.y()>run.owner.feet().y()+0.1) returned=true;
                for(Emote emote:run.agent.emotes()) if(emote.kind()==Emote.Kind.SPLASH) {
                    splashes++; assertTrue(wet,"splash must coincide with real water contact");
                }
            }
            String at=motion+"#"+seed;
            assertTrue(entered,at+" enters real water"); assertTrue(returned,at+" swims back above the surface");
            assertTrue(splashes>0 && splashes<=3,at+" emits one sparse splash per visit: "+splashes);
            assertEquals(0,run.agent.leashEvents(),at);
        }
    }

    @Test void walkerWaterExcursionUsesCheckedArcsAndReturnsToTheBank() {
        VoxelScene scene=VoxelScene.open();
        Vec surface=new Vec(0.7,scene.feetY(),2.5); scene.addWorldWater(surface);
        Vec shore=surface.add(0,0.14,-1.2);
        GroundBody body=new GroundBody(Locomotion.BENTHIC,GroundGait.CRAWL,shore,0,0.1,0.3f,1);
        body.floorY=surface.y();
        LocalVoxels voxels=new LocalVoxels(); voxels.update(scene,shore.add(0,1,0));
        assertTrue(body.dipTo(surface.add(0,-0.2,0),voxels));
        boolean splash=false,air=false;
        for(int tick=0;tick<120;tick++) {
            body.walk(shore,0,GroundBody.Rhythm.AMBLE,Vec.ZERO,Float.NaN,voxels);
            splash|=body.waterSplash; air|=body.phase==GroundBody.Phase.AIR;
            assertTrue(voxels.sphereClear(body.pos,body.radius));
        }
        assertTrue(air,"leaves via a ballistic hop"); assertTrue(splash,"actually touches water");
        assertTrue(body.onFloor(),"returns to land");
        assertTrue(body.pos.sub(shore).horizontalLength()<0.5,"returns to the original bank: "+body.pos);
    }

    @Test void groundNotePulseHeightFollowsPitch() {
        VoxelScene scene=VoxelScene.open(); Vec start=new Vec(0.5,scene.feetY()+0.14,0.5);
        LocalVoxels blocks=new LocalVoxels(); blocks.update(scene,start.add(0,1,0));
        double[] peaks=new double[2];
        for(int pitch=0;pitch<2;pitch++) {
            GroundBody body=new GroundBody(Locomotion.BENTHIC,GroundGait.CRAWL,start,0,0.1,0.3f,1);
            body.floorY=scene.feetY(); assertTrue(body.notePulse(pitch));
            boolean impulse=false;
            for(int tick=0;tick<40;tick++) {
                body.walk(start,0,GroundBody.Rhythm.AMBLE,Vec.ZERO,Float.NaN,blocks);
                peaks[pitch]=Math.max(peaks[pitch],body.pos.y()-start.y()); impulse|=body.impulse;
            }
            assertTrue(impulse); assertTrue(body.onFloor());
        }
        assertTrue(peaks[1]>peaks[0]+0.25,"high notes make visibly higher hops");
    }

    @Test void noteBeatsAreImmediateAndConsumedOnce() {
        CompanionWorld w=OwnerScript.named("idle_fixed",1).nextWorld();
        CompanionAgent a=new CompanionAgent(CompanionTunables.defaults(),1,0.4f,w);
        WorldFeature note=WorldFeature.music(1,w.owner().chest().add(0,0,2),new WorldFeature.Music(WorldFeature.Source.NOTE_BLOCK,50,12,50,0.75f));
        a.step(w.withFeatures(List.of(note)).withGameTick(50));
        assertEquals(1,a.emotes().stream().filter(e->e.kind()==Emote.Kind.NOTE).count());
        assertEquals(0.75f,a.emotes().get(0).pitch());
        a.step(w.withFeatures(List.of(note)).withGameTick(51));
        assertTrue(a.emotes().stream().noneMatch(e->e.kind()==Emote.Kind.NOTE));
        a.step(w.withFeatures(List.of(note)).withGameTick(63));
        assertTrue(a.emotes().isEmpty());
    }
    @Test void lightsNeedDarknessAndMoonPreferenceNeedsOpenSky() {
        Mood mood=new Mood(); Notice notice=new Notice(Temperament.BASELINE,mood);
        CompanionWorld w=OwnerScript.named("idle_fixed",1).nextWorld();
        WorldFeature light=WorldFeature.light(1,w.owner().chest().add(1,0,2),14);
        Habitat lunar=new Habitat(Set.of("zone:ocean"),Map.of(),Map.of(),2,Habitat.Climate.TEMPERATE);
        for(int i=0;i<30;i++) notice.update(w.withFeatures(List.of(light)).withAmbient(MoodScenes.PLAINS.withDarkness(0)),lunar,w.owner().chest());
        assertEquals(0,notice.utility);
        for(int i=0;i<200;i++) mood.update(MoodScenes.PLAINS.withTime(Ambient.DayPart.NIGHT,0),lunar,w.owner().chest(),w.owner().chest());
        notice.update(w.withFeatures(List.of(light)).withAmbient(MoodScenes.PLAINS.withDarkness(1)),lunar,w.owner().chest());
        assertEquals(0,notice.utility,"moon gaze wins outdoors");
        for(int i=0;i<200;i++) mood.update(MoodScenes.PLAINS.withPlace(Set.of("sky:blocked"),0.8f).withTime(Ambient.DayPart.NIGHT,0),lunar,w.owner().chest(),w.owner().chest());
        notice.update(w.withFeatures(List.of(light)).withAmbient(MoodScenes.PLAINS.withDarkness(1)),lunar,w.owner().chest());
        assertTrue(notice.utility>0.5,"cave light wins when moon cannot be seen");
    }
    @Test void schoolMembersFeelOwnHomeAndWarmthAndEmitIndependently() {
        CompanionWorld w=OwnerScript.named("idle_fixed",1).nextWorld();
        CompanionGroup school=new CompanionGroup(CompanionTunables.defaults(),1,new float[]{0.2f,0.25f,0.3f},Temperament.BASELINE,w);
        school.leader().habitat(MoodScenes.OCEAN); school.follower(0).habitat(MoodScenes.OCEAN);
        school.follower(1).habitat(new Habitat(Set.of("zone:nether"),Map.of(),Map.of(),1,Habitat.Climate.HOT));
        w=w.withAmbient(MoodScenes.PLAINS.withPlace(Set.of("zone:ocean"),0.8f)); school.step(w);
        assertTrue(school.leader().emotes().stream().anyMatch(e->e.kind()==Emote.Kind.HOMECOMING));
        assertTrue(school.follower(0).emotes().stream().anyMatch(e->e.kind()==Emote.Kind.HOMECOMING));
        assertTrue(school.follower(1).emotes().stream().noneMatch(e->e.kind()==Emote.Kind.HOMECOMING));
        w=w.withAmbient(MoodScenes.PLAINS.withPlace(Set.of("snow"),0)).withFeatures(List.of(WorldFeature.warmth(1,w.owner().chest())));
        for(int i=0;i<200;i++) school.step(w);
        assertTrue(school.leader().mood().warmed()); assertTrue(school.follower(0).mood().warmed());
    }
    @Test void mixedGroundFollowersDoNotInheritTheirLeadersGlumLook() {
        OwnerScript script=OwnerScript.named("idle_fixed",1); CompanionWorld w=script.nextWorld();
        BenthicGroup group=new BenthicGroup(CompanionTunables.defaults(),1,0.3f,Temperament.BASELINE,GroundGait.CRAWL,3,BenthicGroup.Formation.LINE,w);
        group.member(0).habitat(new Habitat(Set.of("zone:nether"),Map.of(),Map.of(),1,Habitat.Climate.HOT));
        group.member(1).habitat(MoodScenes.OCEAN);
        group.member(2).habitat(new Habitat(Set.of("minecraft:snowy_plains"),Map.of(),Map.of(),1,Habitat.Climate.COLD));
        Ambient snow=MoodScenes.PLAINS.withPlace(Set.of("minecraft:snowy_plains"),0.2f).withWeather(Ambient.Weather.RAIN);
        int ownShiver=0,contentDroop=0;
        for(int i=0;i<300;i++) {
            // Dry temperate air glums the HOT leader; snow cold-weights need an explicit snowy ambient next.
            w=script.nextWorld().withAmbient(snow); group.step(w);
            if(group.member(2).poseHint()==PoseHint.DROOP) contentDroop++;
        }
        assertTrue(group.member(0).mood().glum()>0.4f);
        assertEquals(0,contentDroop,"content cold-home follower never inherits the HOT leader's droop");
        snow=snow.withWeather(Ambient.Weather.SNOW).withPlace(Set.of("minecraft:snowy_plains"),0);
        for(int i=0;i<300;i++) { group.step(script.nextWorld().withAmbient(snow)); if(group.member(1).poseHint()==PoseHint.SHIVER) ownShiver++; }
        assertTrue(ownShiver>100,"temperate follower shows its own shiver");
    }

    @Test void indexedWarmthRetainsTheBaskTrajectoryAndHold() {
        for (Locomotion motion : List.of(Locomotion.FREE_SWIM,Locomotion.DRIFT,Locomotion.BENTHIC)) {
            for (float length : new float[]{0.2f,0.6f}) {
                OwnerScript script=OwnerScript.named("idle_fixed",2);
                CompanionWorld world=script.nextWorld();
                CompanionAgent legacy=new CompanionAgent(CompanionTunables.defaults(),2,motion,length,world);
                CompanionAgent indexed=new CompanionAgent(CompanionTunables.defaults(),2,motion,length,world);
                legacy.habitat(MoodScenes.OCEAN); indexed.habitat(MoodScenes.OCEAN);
                Vec fire=world.owner().feet().add(0.5,0.5,3.5);
                Ambient cold=MoodScenes.PLAINS.withPlace(Set.of("snow"),0);
                for(int tick=0;tick<1000;tick++) {
                    world=script.nextWorld();
                    legacy.step(world.withAmbient(cold.withWarmth(List.of(fire))));
                    indexed.step(world.withAmbient(cold).withFeatures(List.of(WorldFeature.warmth(1,fire))));
                    assertEquals(legacy.position(),indexed.position(),motion+" length "+length+" tick "+tick);
                    assertEquals(legacy.mood(),indexed.mood());
                    assertEquals(legacy.basking(),indexed.basking());
                }
            }
        }
    }

    @Test void newlyHeardMusicDancesDuringTravelWithoutStealingEscort() {
        CompanionWorld w=OwnerScript.named("idle_fixed",1).nextWorld(); OwnerState o=w.owner();
        OwnerState moving=new OwnerState(o.eye(),new Vec(0,0,4),o.yawDeg(),o.pitchDeg(),false,false,true,70,false);
        w=new CompanionWorld(moving,w.blocks(),List.of(),CompanionWorld.Held.NONE,false);
        for(double sourceDistance : new double[]{3,12}) {
            CompanionAgent fish=new CompanionAgent(CompanionTunables.defaults(),1,0.4f,w);
            var music=WorldFeature.music(1,o.chest().add(0,0,sourceDistance),new WorldFeature.Music(WorldFeature.Source.JUKEBOX,0,2400,0,0));
            for(int i=0;i<30;i++) fish.step(w.withFeatures(List.of(music)).withGameTick(i));
            assertEquals(PoseHint.DANCE,fish.poseHint());
            assertNotEquals("NOTICE",fish.goal());
        }
    }

    @Test void leavingTheFeatureCannotHoldTheFishBehind() {
        OwnerScript script=OwnerScript.named("idle_fixed",1); CompanionWorld w=script.nextWorld();
        CompanionAgent a=new CompanionAgent(CompanionTunables.defaults(),1,0.4f,w);
        var music=WorldFeature.music(1,w.owner().chest().add(0,0,2),new WorldFeature.Music(WorldFeature.Source.JUKEBOX,0,2400,0,0));
        for(int tick=0;tick<150;tick++) a.step(w.withFeatures(List.of(music)).withGameTick(tick));
        OwnerState o=w.owner(); OwnerState moving=new OwnerState(o.eye(),new Vec(0,0,4),o.yawDeg(),o.pitchDeg(),false,false,true,70,false);
        a.step(new CompanionWorld(moving,w.blocks(),List.of(),CompanionWorld.Held.NONE,false,w.ambient()).withFeatures(List.of(music)).withGameTick(151));
        assertNotEquals("NOTICE",a.goal());
    }
}
