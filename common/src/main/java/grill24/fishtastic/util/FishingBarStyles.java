package grill24.fishtastic.util;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.util.FishingMinigameAnimation.FishingBarLayout;
import grill24.fishtastic.util.FishingMinigameAnimation.GuiTextureItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of selectable bar + bobber looks for the fishing minigame. Each style bundles the
 * normal layout with its small-bobber variant (Small Fish Bait), so any texture size can be
 * dropped in by declaring a new style here — sizing is expressed in texture pixels and the
 * renderer scales every style to the same on-screen height.
 *
 * <p>To try a different look, use the client command {@code /fishingbar <style>}. To pick per game context later, make
 * {@link #select} consult that context instead of returning {@link #active()}.
 */
public final class FishingBarStyles {
    /** A named bar/bobber look. {@code small} is used when the equipped bait shrinks the bobber. */
    public record Style(String id, FishingBarLayout normal, FishingBarLayout small) {
        public FishingBarLayout layout(boolean smallBobber) { return smallBobber ? small : normal; }
    }

    private static final Map<String, Style> STYLES = new LinkedHashMap<>();

    /** The original 32px art (textures/item). */
    public static final Style CLASSIC = register(new Style("classic",
            FishingMinigameAnimation.LAYOUT, FishingMinigameAnimation.LAYOUT_SMALL));

    /**
     * 64px art (textures/gui): 16px-wide bar spanning the full height, interior rows ~4-59, and an
     * 18px-tall bobber. No dedicated small-bobber sprite yet, so the small variant reuses the normal
     * sprite with a proportionally shorter catch window (14px, mirroring classic's 9 -> 7).
     */
    public static final Style TALL = register(tall("tall", "fishing_bar_2.png", "fishing_bobber_2.png"));

    /** Same geometry as {@link #TALL} with the lava bar art (fishing_bar_3). */
    public static final Style LAVA = register(tall("lava", "fishing_bar_3.png", "fishing_bobber_2.png"));

    private static volatile Style active = CLASSIC;

    private FishingBarStyles() {}

    /** Style for a new minigame session — the hook for future per-context selection. */
    public static Style select() {
        return active;
    }

    public static Style active() {
        return active;
    }

    /** Switches the style used by new sessions; false if {@code id} names no registered style. */
    public static boolean setActive(String id) {
        Style style = STYLES.get(id);
        if (style == null) return false;
        active = style;
        return true;
    }

    public static java.util.Collection<String> ids() {
        return STYLES.keySet();
    }

    public static Style byId(String id) {
        return STYLES.getOrDefault(id, CLASSIC);
    }

    private static Style register(Style style) {
        STYLES.put(style.id(), style);
        return style;
    }

    private static Style tall(String id, String barTexture, String bobberTexture) {
        GuiTextureItem bar = new GuiTextureItem(0, 0, 16, 64, 64, 64, Fishtastic.id("textures/gui/" + barTexture));
        GuiTextureItem bobber = new GuiTextureItem(0, 0, 16, 64, 64, 64, Fishtastic.id("textures/gui/" + bobberTexture));
        return new Style(id,
                new FishingBarLayout(bar, bobber, 56, 18, 52, 1, 3),
                new FishingBarLayout(bar, bobber, 56, 14, 52, 1, 3));
    }
}
