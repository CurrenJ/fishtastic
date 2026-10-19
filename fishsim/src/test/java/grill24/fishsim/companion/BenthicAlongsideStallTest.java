package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenthicAlongsideStallTest {
    private static OwnerState owner(float yaw, Vec velocity) {
        return new OwnerState(new Vec(.5,1.62,.5),velocity,yaw,0,false,false,true,70,false);
    }
    private static final BlockQuery PILLAR = (x,y,z) -> y<0 || (x==-1&&z==0&&y<3);
    private static final Vec REAR = new Vec(-.25,0,-1);
    private record Fixture(GroundBody body, GroundMind mind, LocalVoxels voxels, Drives drives) {
        void tick(OwnerState owner) { mind.wantClose=true; mind.step(owner,voxels,drives,.3f); }
    }
    private static Fixture fixture(long seed, BlockQuery blocks) {
        var body=new GroundBody(Locomotion.BENTHIC,GroundGait.CRAWL,new Vec(.5,.15,-1.5),0,.15,.3f,seed);
        var voxels=new LocalVoxels();voxels.update(blocks,owner(0,Vec.ZERO).feet());
        return new Fixture(body,new GroundMind(new Breadcrumbs(),body,seed),voxels,new Drives(Temperament.BASELINE));
    }

    @Test void stalledPillarApproachActuallyReachesFreeRearStationForEverySeed() {
        boolean saved=GroundMind.alongsideRecoveryEnabled;
        try {
            for(boolean enabled:new boolean[]{false,true}) {
                GroundMind.alongsideRecoveryEnabled=enabled;
                for(int seed=1;seed<=20;seed++) {
                    var f=fixture(seed,PILLAR);int firstArrival=-1; boolean fadeBeforeArrival=false;
                    for(int tick=0;tick<300;tick++) {
                        f.tick(owner(0,Vec.ZERO));
                        assertTrue(f.voxels.sphereClear(f.body.pos,f.body.radius),"seed="+seed+" tick="+tick);
                        if(f.body.pos.sub(REAR).horizontalLength()<.4&&tick>40&&firstArrival<0) firstArrival=tick;
                        if(firstArrival<0) fadeBeforeArrival|=f.mind.wantsFade;
                    }
                    if(enabled) {
                        assertTrue(firstArrival>=0&&firstArrival<150,"seed="+seed+" first arrival="+firstArrival);

                        assertFalse(fadeBeforeArrival,"seed="+seed);

                    } else assertTrue(f.body.pos.sub(REAR).horizontalLength()>.5,"baseline fixture stays at pillar");
                }
            }
        } finally {GroundMind.alongsideRecoveryEnabled=saved;}
    }

    @Test void ownerMovementOrTurnClearsRecoveryStation() {
        for(boolean turn:new boolean[]{false,true}) {
            var f=fixture(2,PILLAR);
            for(int tick=0;tick<250;tick++) f.tick(owner(0,Vec.ZERO));

            OwnerState changed=owner(turn?90:0,turn?Vec.ZERO:new Vec(0,0,1));
            f.tick(changed);
            Vec preferred=changed.feet().add(changed.flatRight().scale(.75)).add(changed.flatForward().scale(.3))
                    .add(changed.velocity().horizontal().scale(.3));
            assertEquals(preferred,f.mind.target);
        }
    }

    @Test void ordinaryOneBlockStepDoesNotSelectRearStation() {
        BlockQuery step=(x,y,z)->y<0||(x==-1&&z==0&&y==0);
        var f=fixture(2,step);
        for(int tick=0;tick<200;tick++) f.tick(owner(0,Vec.ZERO));
        assertNotEquals(REAR,f.mind.target);
    }

    @Test void companionCanPerchOrResumeFollowingAfterPillarRecovery() throws Exception {
        var field=CompanionAgent.class.getDeclaredField("perch");field.setAccessible(true);
        for(int seed=1;seed<=20;seed++) {
            var world=new CompanionWorld(owner(0,Vec.ZERO),PILLAR,java.util.List.of(),CompanionWorld.Held.NONE,false);
            var agent=new CompanionAgent(CompanionTunables.defaults(),seed,Locomotion.BENTHIC,.3f,Temperament.BASELINE,GroundGait.CRAWL,world);
            var perch=(PerchBody)field.get(agent);
            perch.pos=perch.ground.pos=new Vec(.5,perch.ground.radius+LocalVoxels.GROUND_LIFT,-1.5);
            perch.ground.floorY=0;perch.home=PerchBody.Slot.RIGHT_SHOULDER;
            agent.devPerch();boolean perched=false, recovery=false, released=false;
            for(int tick=0;tick<600;tick++) {agent.step(world);perched|=agent.riding(); recovery|=agent.target()!=null&&agent.target().sub(REAR).horizontalLength()<.1; released|=recovery&&!agent.comingClose()&&agent.onGround();}
            assertTrue(perched||released,"must perch or return to ordinary ground behaviour after escape, seed="+seed+" pos="+agent.position());
            agent.devGround();
            for(int tick=0;tick<300;tick++) {
                var moving=new OwnerState(new Vec(.5,1.62,.5+tick*.1),new Vec(0,0,2),0,0,false,false,true,70,false);
                agent.step(new CompanionWorld(moving,PILLAR,java.util.List.of(),CompanionWorld.Held.NONE,false));
            }
            assertTrue(agent.position().z()>20,"must follow owner after escape, seed="+seed);
        }
    }
    @Test void ordinaryApproachesKeepTheirEqualSeedTrajectories() {
        boolean saved=GroundMind.alongsideRecoveryEnabled;
        try {
            for(String script:java.util.List.of("low_ceiling","jumping","walk_flat","walk_meander","stairs_up_down"))
                for(float length:new float[]{.3f,.6f}) for(int seed=1;seed<=12;seed++) {
                    GroundMind.alongsideRecoveryEnabled=false;
                    var before=grill24.fishsim.harness.companion.CompanionRun.benthic(script,seed,length,Temperament.BASELINE,GroundGait.CRAWL,CompanionTunables.defaults(),true);
                    before.run(2400);
                    GroundMind.alongsideRecoveryEnabled=true;
                    var after=grill24.fishsim.harness.companion.CompanionRun.benthic(script,seed,length,Temperament.BASELINE,GroundGait.CRAWL,CompanionTunables.defaults(),true);
                    after.run(2400);
                    String label=script+" length="+length+" seed="+seed;
                    assertEquals(before.metrics(),after.metrics(),label);
                    assertEquals(before.groundMetrics(),after.groundMetrics(),label);
                    for(int tick=0;tick<2400;tick++) assertEquals(before.samples.get(tick).pos(),after.samples.get(tick).pos(),label+" tick="+tick);
                }
        } finally {GroundMind.alongsideRecoveryEnabled=saved;}
    }
    @Test void blockedRearStationKeepsStrandedFadeFallback() {
        BlockQuery walls=(x,y,z)->y<0||(x==-1&&(z==0||z==-1)&&y<3);
        var f=fixture(1,walls);
        for(int tick=0;tick<1000;tick++) f.tick(owner(0,Vec.ZERO));
        assertTrue(f.mind.wantsFade);
    }
}
