package grill24.fishtastic.client.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Development audio fixtures use the same heard-event store as vanilla callbacks. */
public final class CompanionFeatureDev {
    private CompanionFeatureDev() {}
    public static String music(String kind) {
        var mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null || mc.player == null) return;
            BlockPos pos = mc.player.blockPosition().relative(mc.player.getDirection(), 2);
            if (kind.equals("jukebox")) CompanionMusic.jukebox(mc.level, pos, 20 * 30);
            else CompanionMusic.note(mc.level, pos, .75f);
        });
        return "Companion feature queued: " + kind;
    }
}
