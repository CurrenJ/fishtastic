package grill24.fishtastic.client.renderer;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Zone icon art shared by the fishing minigame's HUD zone stack ({@code FishingMinigameAnimation})
 * and the fish encyclopedia's zone condition rows ({@code FishEncyclopediaScreen}).
 *
 * <p>Explicit rather than derived from {@link FishProfile.Zone#getSerializedName()} so a zone
 * without art is skipped instead of drawing a missing-texture placeholder.
 */
public final class ZoneIconTextures {
    private ZoneIconTextures() {}

    /** Native pixel size of every zone icon texture (square). */
    public static final int TEXTURE_PX = 32;

    private static final Map<FishProfile.Zone, Identifier> TEXTURES = new EnumMap<>(FishProfile.Zone.class);
    static {
        TEXTURES.put(FishProfile.Zone.OCEAN, texture("ocean"));
        TEXTURES.put(FishProfile.Zone.DEEP_OCEAN, texture("deep_ocean"));
        TEXTURES.put(FishProfile.Zone.RIVER, texture("river"));
        TEXTURES.put(FishProfile.Zone.NETHER, texture("nether"));
        TEXTURES.put(FishProfile.Zone.CAVE, texture("cave"));
        TEXTURES.put(FishProfile.Zone.HIGH_ALTITUDE, texture("high_altitude"));
    }

    private static Identifier texture(String name) {
        return Fishtastic.id("textures/item/zone/" + name + ".png");
    }

    @Nullable
    public static Identifier get(FishProfile.Zone zone) {
        return TEXTURES.get(zone);
    }
}
