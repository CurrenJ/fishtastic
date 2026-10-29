package grill24.fishtastic.client.renderer;

/** Render-thread context used by the 1.21.1 item-property override for wet/dry fish art. */
public final class FishRenderMedium {
    public enum Forced { AUTO, WET, DRY }
    private static boolean wet;
    private static Forced forced = Forced.AUTO;

    public static boolean wet() {
        return switch (forced) {
            case WET -> true;
            case DRY -> false;
            case AUTO -> wet;
        };
    }

    /** Sets the renderer context and returns its previous value; restore it in a finally block. */
    public static boolean set(boolean value) {
        boolean previous = wet;
        wet = value;
        return previous;
    }

    public static Forced forced() { return forced; }
    public static void setForced(Forced value) { forced = value; }
    private FishRenderMedium() {}
}
