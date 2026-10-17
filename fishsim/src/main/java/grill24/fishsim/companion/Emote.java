package grill24.fishsim.companion;

/** One-shot visual output for the current simulation tick. Reading never consumes an event. */
public record Emote(Kind kind, Vec pos, float pitch) {
    public enum Kind { HOMECOMING, SPLASH, NOTE, SIGH, SNORE, STEAM, FROST, GLINT }
    public Emote(Kind kind, Vec pos) { this(kind, pos, 0); }
}
