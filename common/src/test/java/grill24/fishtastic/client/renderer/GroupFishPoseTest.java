package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.data.FishAnimationConfig;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Freeze the pre-extraction tank transform sequence, including normal matrices and local frame. */
class GroupFishPoseTest {
    @Test void swimming() { compare(FishAnimationConfig.HorizontalSwim.DEFAULT, Locomotion.FREE_SWIM); }
    @Test void gliding() { compare(FishAnimationConfig.BellyDown.DEFAULT, Locomotion.GLIDE); }
    @Test void drifting() { compare(FishAnimationConfig.UprightFloat.DEFAULT, Locomotion.DRIFT); }
    @Test void benthic() { compare(FishAnimationConfig.FloorSit.DEFAULT, Locomotion.BENTHIC); }
    @Test void anchored() { compare(FishAnimationConfig.Planted.DEFAULT, Locomotion.ANCHORED); }
    @Test void demoted() { compare(FishAnimationConfig.UprightFloat.DEFAULT, Locomotion.STATIC); }

    private void compare(FishAnimationConfig anim, Locomotion mode) {
        FlockEngine e = new FlockEngine(Tunables.GROUP);
        boolean[][][] cells = new boolean[4][2][3];
        for (boolean[][] plane : cells) for (boolean[] row : plane) java.util.Arrays.fill(row,true);
        e.rebuildPreserving(new FishSpec[]{new FishSpec(0.3f,mode,false,1)}, new int[]{-1},42,0f,20f,new VoxelDomain(cells));
        for(int n=0;n<60;n++) e.step();
        e.interpolate(0.4f);
        float[] before = e.renderPhase.clone();
        PoseStack actual = new PoseStack(), expected = new PoseStack();
        GroupFishPose.apply(actual,e,anim,0,60.4f,2.3f,-0.4f,1.7f,new Random());
        // Established tank sequence retained independently as an extraction regression fixture.
        float scale=e.lengths[0], t=60.4f;
        expected.translate(2.3f+e.renderX[0],-0.4f+e.renderY[0]+FishAnimator.floorPoseLift(anim,scale),1.7f+e.renderZ[0]);
        Random random=new Random(e.seeds[0]);
        if(e.swimmers[0]) FishAnimator.applySwimming(expected,(FishAnimationConfig.HorizontalSwim)anim,random,
                e.renderPhase[0],e.renderYaw[0]+180f,false,e.speedFactor(0),e.bank[0],e.renderBobRoom[0],scale);
        else switch(e.locomotion[0]) {
            case BENTHIC -> FishAnimator.applyBenthic(expected,anim,random,t,e.renderYaw[0]+180f,e.baseRotations[0],scale,false,e.renderShape[0]);
            case GLIDE -> FishAnimator.applyGliding(expected,anim,random,t,e.renderYaw[0]+180f,e.bankFraction(0),e.baseRotations[0],scale,false);
            case DRIFT -> FishAnimator.applyDrifting(expected,anim,random,t,e.baseRotations[0],scale,e.hoverMirrored[0],e.renderShape[0]);
            case ANCHORED -> FishAnimator.applyAnchored(expected,anim,random,t,e.baseRotations[0],scale,e.hoverMirrored[0],e.renderShape[0]);
            default -> FishAnimator.apply(expected,anim,random,t,e.baseRotations[0],scale,e.hoverMirrored[0]);
        }
        expected.scale(scale,scale,scale);
        assertArrayEquals(expected.last().pose().get(new float[16]),actual.last().pose().get(new float[16]),0f);
        assertArrayEquals(expected.last().normal().get(new float[9]),actual.last().normal().get(new float[9]),0f);
        assertArrayEquals(before,e.renderPhase,0f,"posing must not advance simulation");
    }
}
