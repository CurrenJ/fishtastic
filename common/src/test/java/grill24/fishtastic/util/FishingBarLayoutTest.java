package grill24.fishtastic.util;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.util.FishingMinigameAnimation.FishingBarLayout;
import grill24.fishtastic.util.FishingMinigameAnimation.GuiTextureItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bobber travel and target art for the selectable fishing bar styles ({@code FishingBarStyles}).
 *
 * <p>{@code bobberMaxYOffset} is the fraction of bar texture height the bobber sprite travels per unit
 * of bobber position. Position tops out at {@code 1 - bobberSize()}, so the travel span must be
 * divided by that or the extra drop/rise pixels the tall styles use leave the sprite short of the
 * bar's ceiling — a gap that only shows as "the bobber stops before the art line" while playing, on
 * one style, in one direction. The classic styles have no drop/rise, where the divisor is
 * algebraically an identity (28/32 either way), so this pins that too: the fix must not move the
 * original bar.
 *
 * <p>The tall/lava styles' own fish icon is a GUI sprite here rather than 26.1.2's item-model swap
 * (see {@link FishingBarLayout#fishTargetSprite()}), so the last check is that each style's sprite
 * texture is actually checked in: a missing one renders as the purple/black missing texture in the
 * minigame, which nothing else in the build would catch.
 */
class FishingBarLayoutTest {

    /** Mirrors {@code FishingBarStyles.tall}'s 64px art. */
    private static GuiTextureItem tex64() {
        return new GuiTextureItem(0, 0, 16, 64, 64, 64, Fishtastic.id("textures/gui/fishing_bar_2.png"));
    }

    /** Mirrors {@code FishingMinigameAnimation.LAYOUT}'s 32px art. */
    private static GuiTextureItem tex32() {
        return new GuiTextureItem(0, 0, 8, 32, 32, 32, Fishtastic.id("textures/item/fishing_bar.png"));
    }

    @Test
    void classicTravelIsUnchangedByTheDivisor() {
        // Classic: no drop/rise, so travel = travelZonePx - bobberHeightPx, and 1 - h/t is (t - h)/t,
        // making the divisor cancel — the old formula ((travel + drop + rise) / texHeight) is preserved.
        assertEquals(0.875f, new FishingBarLayout(tex32(), tex32(), 28, 9, 26).bobberMaxYOffset(), 1e-5f,
                "classic 28px travel, 9px bobber = 28/32");
        assertEquals(0.875f, new FishingBarLayout(tex32(), tex32(), 28, 7, 26).bobberMaxYOffset(), 1e-5f,
                "small-bobber classic: 21/0.75 = 28, so also 28/32");
        assertEquals(0.875f, FishingMinigameAnimation.LAYOUT.bobberMaxYOffset(), 1e-5f, "the shipped classic layout");
        assertEquals(0.875f, FishingMinigameAnimation.LAYOUT_SMALL.bobberMaxYOffset(), 1e-5f, "the shipped small layout");
    }

    @Test
    void tallTravelReachesTheBarCeiling() {
        // Tall: 56px travel, 18px bobber (14px small), 1px drop, 3px rise over a 64px texture.
        FishingBarLayout normal = new FishingBarLayout(tex64(), tex64(), 56, 18, 52, 1, 3);
        FishingBarLayout small = new FishingBarLayout(tex64(), tex64(), 56, 14, 52, 1, 3);

        assertCeilingReached(normal, 56, 18, 1, 3);
        assertCeilingReached(small, 56, 14, 1, 3);

        // The sprite travels further than it used to ((56 + 1 + 3) / 64 = 0.9375), which is the fix.
        assertTrue(normal.bobberMaxYOffset() > 0.9375f, "tall normal travel must now overshoot the old 0.9375");
        assertEquals(0.9671053f, normal.bobberMaxYOffset(), 1e-5f, "42 * 56 / 38 / 64");
        assertEquals(0.9583333f, small.bobberMaxYOffset(), 1e-5f, "46 * 56 / 42 / 64");
    }

    /** At the top of its travel ({@code 1 - bobberSize}) the sprite covers travel - height + drop + rise px. */
    private static void assertCeilingReached(FishingBarLayout layout, int travel, int height, int drop, int rise) {
        float atCeiling = (1f - layout.bobberSize()) * layout.bobberMaxYOffset() * layout.bar().texHeight();
        assertEquals(travel - height + drop + rise, atCeiling, 1e-3f,
                "bobber sprite must land on the bar ceiling at maximum position");
    }

    @Test
    void onlyTheTallAndLavaStylesSwapTheTargetArt() {
        assertNull(FishingBarStyles.CLASSIC.normal().fishTargetSprite(), "classic keeps the item icon");
        assertNull(FishingBarStyles.CLASSIC.small().fishTargetSprite(), "classic keeps the item icon");
        assertEquals(1f, FishingBarStyles.CLASSIC.normal().targetScale(), 1e-6f, "classic draws at 1x");

        for (FishingBarStyles.Style style : new FishingBarStyles.Style[] { FishingBarStyles.TALL, FishingBarStyles.LAVA }) {
            for (FishingBarLayout layout : new FishingBarLayout[] { style.normal(), style.small() }) {
                GuiTextureItem sprite = layout.fishTargetSprite();
                assertNotNull(sprite, style.id() + " draws its own fish target art");
                assertEquals(1.25f, layout.targetScale(), 1e-6f, style.id() + " draws targets 1.25x (2x art density)");
                assertSpriteCheckedIn(sprite);
            }
        }
    }

    /** The sprite's texture must exist in {@code common/src/main/resources} (test cwd is {@code common/}). */
    private static void assertSpriteCheckedIn(GuiTextureItem sprite) {
        // GuiTextureItem textures are full resource paths, extension included ("textures/gui/…png").
        Path file = Path.of("src/main/resources/assets/" + sprite.texture().getNamespace() + "/" + sprite.texture().getPath());
        assertTrue(Files.isRegularFile(file), "missing sprite texture: " + file);
        // A GUI sprite is blitted 1:1 from the texture, so its declared size must match the image's.
        assertEquals(32, sprite.texWidth(), "declared texture width");
        assertEquals(32, sprite.texHeight(), "declared texture height");
    }
}
