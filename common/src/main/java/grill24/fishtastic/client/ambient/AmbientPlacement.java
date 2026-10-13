package grill24.fishtastic.client.ambient;

import grill24.fishsim.core.Locomotion;

/** Floor residents start on the actual bed; swimmers use the accepted water band. */
public final class AmbientPlacement {
    private AmbientPlacement() {}

    public static float height(Locomotion locomotion, float bed, float low, float high, float fraction) {
        return locomotion == Locomotion.ANCHORED || locomotion == Locomotion.BENTHIC
                ? bed : low + fraction * (high - low);
    }
}
