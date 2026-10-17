package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.Vec;
import grill24.fishsim.companion.WorldFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Heard events only: entering a chunk never discovers a song already in progress. */
public final class CompanionMusic {
    private CompanionMusic() {}
    private static ClientLevel currentLevel;
    private static final Map<Long, WorldFeature> EVENTS = new HashMap<>();

    public static void clear() { EVENTS.clear(); currentLevel = null; }

    private static void level(ClientLevel level) {
        if (currentLevel != level) { EVENTS.clear(); currentLevel = level; }
    }

    public static void jukebox(ClientLevel level, BlockPos pos, long lengthTicks) {
        level(level);
        long tick = level.getGameTime();
        EVENTS.put(pos.asLong(), WorldFeature.music(pos.asLong(), center(pos),
                new WorldFeature.Music(WorldFeature.Source.JUKEBOX, tick, Math.max(1, lengthTicks), tick, 0f)));
    }

    public static void stop(ClientLevel level, BlockPos pos) {
        level(level);
        EVENTS.remove(pos.asLong());
    }

    public static void note(ClientLevel level, BlockPos pos, float pitch) {
        level(level);
        long tick = level.getGameTime();
        EVENTS.put(pos.asLong(), WorldFeature.music(pos.asLong(), center(pos),
                new WorldFeature.Music(WorldFeature.Source.NOTE_BLOCK, tick, 13, tick, pitch)));
    }

    public static List<WorldFeature> features(ClientLevel level, long tick) {
        level(level);
        EVENTS.values().removeIf(f -> tick >= f.music().startTick() + f.music().lengthTicks());
        return List.copyOf(EVENTS.values());
    }

    private static Vec center(BlockPos pos) { return new Vec(pos.getX() + .5, pos.getY() + 1.0, pos.getZ() + .5); }
}
