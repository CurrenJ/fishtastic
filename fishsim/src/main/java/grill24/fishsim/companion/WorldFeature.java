package grill24.fishsim.companion;

/** A stable, typed local-world stimulus. Coordinates are block centres/surface points. */
public record WorldFeature(Kind kind, long id, Vec pos, int emission, String zone, Music music) {
    public enum Kind { WARMTH, WATER_SURFACE, LIGHT, MUSIC }
    public enum Source { JUKEBOX, NOTE_BLOCK }
    /** Times use CompanionWorld.gameTick, never wall clock time. Pitch is normalized 0..1. */
    public record Music(Source source, long startTick, long lengthTicks, long lastBeatTick, float pitch) {
        public Music { pitch = Math.max(0, Math.min(1, pitch)); }
        public boolean active(long tick) {
            return source == Source.JUKEBOX ? tick >= startTick && tick - startTick < lengthTicks
                    : tick >= lastBeatTick && tick - lastBeatTick <= 12;
        }
    }
    public WorldFeature {
        java.util.Objects.requireNonNull(kind);
        java.util.Objects.requireNonNull(pos);
        emission = Math.max(0, Math.min(15, emission));
        zone = zone == null ? "" : zone;
        if (kind == Kind.MUSIC) java.util.Objects.requireNonNull(music);
    }
    public static WorldFeature warmth(long id, Vec pos) { return new WorldFeature(Kind.WARMTH,id,pos,0,"",null); }
    public static WorldFeature water(long id, Vec pos, String zone) { return new WorldFeature(Kind.WATER_SURFACE,id,pos,0,zone,null); }
    public static WorldFeature light(long id, Vec pos, int emission) { return new WorldFeature(Kind.LIGHT,id,pos,emission,"",null); }
    public static WorldFeature music(long id, Vec pos, Music music) { return new WorldFeature(Kind.MUSIC,id,pos,0,"",music); }
}
