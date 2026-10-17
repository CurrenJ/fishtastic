package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FishVisualsTest {
    @Test void swimming() { assertEquals(Locomotion.FREE_SWIM, FishVisuals.locomotionOf(FishAnimationConfig.HorizontalSwim.DEFAULT)); }
    @Test void gliding() { assertEquals(Locomotion.GLIDE, FishVisuals.locomotionOf(FishAnimationConfig.BellyDown.DEFAULT)); }
    @Test void drifting() { assertEquals(Locomotion.DRIFT, FishVisuals.locomotionOf(FishAnimationConfig.UprightFloat.DEFAULT)); }
    @Test void floorSitting() { assertEquals(Locomotion.BENTHIC, FishVisuals.locomotionOf(FishAnimationConfig.FloorSit.DEFAULT)); }
    @Test void uprightSitting() { assertEquals(Locomotion.BENTHIC, FishVisuals.locomotionOf(FishAnimationConfig.UprightSit.DEFAULT)); }
    @Test void planted() { assertEquals(Locomotion.ANCHORED, FishVisuals.locomotionOf(FishAnimationConfig.Planted.DEFAULT)); }
}
