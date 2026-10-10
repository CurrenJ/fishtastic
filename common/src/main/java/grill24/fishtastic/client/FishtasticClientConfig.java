package grill24.fishtastic.client;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lightweight client-only visual toggles, stored as a plain properties file in the mod's config
 * directory. Same convention as {@link grill24.fishtastic.mcp.McpConfig}: hand-edit the file and
 * restart the game to apply changes — values are cached after first read, not polled live. Settings
 * with a setter here can also be changed in-game (via command or GUI), which updates the cache and
 * rewrites the file so the change applies immediately and survives a restart.
 */
public final class FishtasticClientConfig {
    private static final String FILE_NAME = "fishtastic-client.properties";
    private static final String TANK_WATER_FILL_KEY = "tankWaterFillEnabled";
    private static final String TANK_INTERIOR_LIGHT_KEY = "tankInteriorLight";
    private static final int DEFAULT_TANK_INTERIOR_LIGHT = 13;
    private static final String NOTIFICATION_VOLUME_KEY = "notificationVolume";
    private static final String SHAPE_GALLERY_OPEN_KEY = "shapeGalleryOpen";
    private static final String REDUCED_CELEBRATION_EFFECTS_KEY = "reducedCelebrationEffects";
    /** Don't draw other players' fish companions (docs/fish-companions.md §11 settings). */
    public static final String HIDE_OTHER_COMPANIONS_KEY = "hideOtherCompanions";
    /** Don't draw your own companion in first person; ignored while it's docked on the minigame. */
    public static final String HIDE_OWN_COMPANION_FIRST_PERSON_KEY = "hideOwnCompanionFirstPerson";
    /**
     * Skip docking during the minigame so the companion stays in the world. Default false (it
     * docks). The full "waits at the bobber" behaviour is Phase 5; for now this only skips the
     * dock, and the companion keeps following.
     */
    public static final String COMPANION_WAITS_AT_BOBBER_KEY = "companionWaitsAtBobber";
    private static final int DEFAULT_NOTIFICATION_VOLUME = 50;
    private static final String DEFAULT_FILE_CONTENTS = """
            # Fishtastic client-side visual toggles.
            # Edit and restart the game to apply changes.

            # Draws an animated water fill behind fish tank glass. Toggled via
            # "/fishtastic tank waterfill <true|false>" in-game, which also rewrites this file.
            tankWaterFillEnabled=true

            # Light level (0-15) fish tank interiors (fish, water, sand, decorations, glass) are drawn
            # with, so a tank in a dark room stays bright without lighting the room around it.
            # 0 turns it off and leaves tanks lit by the world alone. Set via
            # "/fishtastic tank interiorlight <0-15>" in-game, which also rewrites this file.
            tankInteriorLight=13

            # Volume (0-100) of quest/notification banner sounds. Set via
            # "/fishtastic notifications volume <0-100>" in-game, which also rewrites this file.
            notificationVolume=50

            # Whether the shape gallery beside the Fish Tank Assembly GUI starts revealed.
            # Toggled by the gallery button in that screen, which also rewrites this file.
            shapeGalleryOpen=true

            # Accessibility: tones down the catch celebrations (legendary + first-discovery). Removes the
            # screen flash, shake, dimming, confetti, sparkles, spinning rays and hopping text; the
            # silhouette reveal, a still glow and the name banner remain. Set via
            # "/fishtastic reducedeffects <true|false>" in-game, which also rewrites this file.
            reducedCelebrationEffects=false

            # Fish companions (docs/fish-companions.md §11). All three default to off.

            # Don't draw other players' fish companions. Your own is unaffected.
            hideOtherCompanions=false

            # Don't draw your own fish companion in first person. Ignored while it is docked on the
            # fishing minigame (the docked fish is part of the minigame HUD).
            hideOwnCompanionFirstPerson=false

            # Skip docking during the fishing minigame so the companion stays out in the world.
            # Default false, i.e. it docks. (The "waits at the bobber" behaviour arrives with the
            # fishing-reactions phase; until then it simply keeps following.)
            companionWaitsAtBobber=false
            """;

    private static Boolean tankWaterFillEnabled;
    private static Integer tankInteriorLight;
    private static Integer notificationVolume;
    private static Boolean shapeGalleryOpen;
    private static Boolean reducedCelebrationEffects;
    private static Boolean hideOtherCompanions;
    private static Boolean hideOwnCompanionFirstPerson;
    private static Boolean companionWaitsAtBobber;

    /**
     * Dev-only (self-tests): values that override the on-disk settings without touching the file.
     * The self-test scenes flip companion settings to prove they gate the renderer; going through
     * the real setters would rewrite the owner's config file (dropping its comments), so a test run
     * must never do it. Cleared by {@link #clearTestOverrides()} at the end of a scene.
     */
    private static final java.util.Map<String, Boolean> TEST_OVERRIDES = new java.util.HashMap<>();

    public static void setTestOverride(String key, boolean value) {
        TEST_OVERRIDES.put(key, value);
    }

    public static void clearTestOverrides() {
        TEST_OVERRIDES.clear();
    }

    private FishtasticClientConfig() {}

    @ExpectPlatform
    public static Path getConfigDirectory() {
        throw new AssertionError();
    }

    public static boolean isTankWaterFillEnabled() {
        if (tankWaterFillEnabled == null) {
            tankWaterFillEnabled = Boolean.parseBoolean(load().getProperty(TANK_WATER_FILL_KEY, "true"));
        }
        return tankWaterFillEnabled;
    }

    /**
     * Persists the tank water fill toggle to the properties file and caches it. The renderer reads
     * {@link #isTankWaterFillEnabled()} every frame, so the change is visible immediately.
     */
    public static void setTankWaterFillEnabled(boolean enabled) {
        tankWaterFillEnabled = enabled;
        persist();
    }

    /** Minimum light level (0-15) tank interiors are drawn with; 0 is off. */
    public static int getTankInteriorLight() {
        if (tankInteriorLight == null) {
            try {
                tankInteriorLight = Mth.clamp(Integer.parseInt(load().getProperty(
                        TANK_INTERIOR_LIGHT_KEY, String.valueOf(DEFAULT_TANK_INTERIOR_LIGHT))), 0, 15);
            } catch (NumberFormatException e) {
                tankInteriorLight = DEFAULT_TANK_INTERIOR_LIGHT;
            }
        }
        return tankInteriorLight;
    }

    /**
     * Persists a new tank interior light level (0-15) and caches it. Tank chunk meshes bake this
     * value in, so callers go through {@link grill24.fishtastic.client.renderer.TankInteriorLight#set}
     * to have them rebuilt.
     */
    public static void setTankInteriorLight(int level) {
        tankInteriorLight = Mth.clamp(level, 0, 15);
        persist();
    }

    /** Notification banner sound volume, as a 0-100 int. */
    public static int getNotificationVolume() {
        if (notificationVolume == null) {
            try {
                notificationVolume = Integer.parseInt(load().getProperty(
                        NOTIFICATION_VOLUME_KEY, String.valueOf(DEFAULT_NOTIFICATION_VOLUME)));
            } catch (NumberFormatException e) {
                notificationVolume = DEFAULT_NOTIFICATION_VOLUME;
            }
        }
        return notificationVolume;
    }

    /** Notification banner sound volume, as a 0.0-1.0 float suitable for sound playback. */
    public static float getNotificationVolumeFraction() {
        return Mth.clamp(getNotificationVolume(), 0, 100) / 100.0f;
    }

    /** Persists a new notification volume (0-100) to the properties file and caches it. */
    public static void setNotificationVolume(int volume) {
        notificationVolume = Mth.clamp(volume, 0, 100);
        persist();
    }

    /**
     * Whether the shape gallery beside the Fish Tank Assembly GUI is revealed. Defaults to true so
     * the gallery - and with it the whole tank shape catalog - is discoverable on first open.
     */
    public static boolean isShapeGalleryOpen() {
        if (shapeGalleryOpen == null) {
            shapeGalleryOpen = Boolean.parseBoolean(load().getProperty(SHAPE_GALLERY_OPEN_KEY, "true"));
        }
        return shapeGalleryOpen;
    }

    /** Persists the shape gallery's revealed state to the properties file and caches it. */
    public static void setShapeGalleryOpen(boolean open) {
        shapeGalleryOpen = open;
        persist();
    }

    /**
     * Whether catch celebrations should play their reduced (accessibility) form. Read once when a
     * celebration starts, so flipping it mid-celebration doesn't change one already in flight.
     */
    public static boolean isReducedCelebrationEffects() {
        if (reducedCelebrationEffects == null) {
            reducedCelebrationEffects = Boolean.parseBoolean(load().getProperty(REDUCED_CELEBRATION_EFFECTS_KEY, "false"));
        }
        return reducedCelebrationEffects;
    }

    /** Persists the reduced-effects setting to the properties file and caches it. */
    public static void setReducedCelebrationEffects(boolean reduced) {
        reducedCelebrationEffects = reduced;
        persist();
    }

    /** Whether other players' fish companions are hidden. */
    public static boolean hideOtherCompanions() {
        Boolean override = TEST_OVERRIDES.get(HIDE_OTHER_COMPANIONS_KEY);
        if (override != null) return override;
        if (hideOtherCompanions == null) {
            hideOtherCompanions = Boolean.parseBoolean(load().getProperty(HIDE_OTHER_COMPANIONS_KEY, "false"));
        }
        return hideOtherCompanions;
    }

    /**
     * Whether your own companion is hidden in first person. Always read together with the dock
     * state — a docked companion is part of the minigame HUD and stays visible regardless.
     */
    public static boolean hideOwnCompanionFirstPerson() {
        Boolean override = TEST_OVERRIDES.get(HIDE_OWN_COMPANION_FIRST_PERSON_KEY);
        if (override != null) return override;
        if (hideOwnCompanionFirstPerson == null) {
            hideOwnCompanionFirstPerson = Boolean.parseBoolean(load().getProperty(HIDE_OWN_COMPANION_FIRST_PERSON_KEY, "false"));
        }
        return hideOwnCompanionFirstPerson;
    }

    /** Whether the companion skips docking during the minigame (Phase 5 will make it wait at the bobber). */
    public static boolean companionWaitsAtBobber() {
        Boolean override = TEST_OVERRIDES.get(COMPANION_WAITS_AT_BOBBER_KEY);
        if (override != null) return override;
        if (companionWaitsAtBobber == null) {
            companionWaitsAtBobber = Boolean.parseBoolean(load().getProperty(COMPANION_WAITS_AT_BOBBER_KEY, "false"));
        }
        return companionWaitsAtBobber;
    }

    /** Persists the hide-other-companions toggle (also reachable via the dev `companion` command). */
    public static void setHideOtherCompanions(boolean hide) {
        hideOtherCompanions = hide;
        persist();
    }

    /** Persists the hide-own-companion-in-first-person toggle (never applied while docked). */
    public static void setHideOwnCompanionFirstPerson(boolean hide) {
        hideOwnCompanionFirstPerson = hide;
        persist();
    }

    /** Persists the waits-at-the-bobber toggle. */
    public static void setCompanionWaitsAtBobber(boolean wait) {
        companionWaitsAtBobber = wait;
        persist();
    }

    /**
     * Writes every setting back to the properties file. Each value is read through its own getter
     * so an untouched setting round-trips its on-disk value rather than being reset to a default.
     */
    private static void persist() {
        Path file = getConfigDirectory().resolve(FILE_NAME);
        Properties props = load();
        props.setProperty(NOTIFICATION_VOLUME_KEY, String.valueOf(getNotificationVolume()));
        props.setProperty(TANK_WATER_FILL_KEY, String.valueOf(isTankWaterFillEnabled()));
        props.setProperty(TANK_INTERIOR_LIGHT_KEY, String.valueOf(getTankInteriorLight()));
        props.setProperty(SHAPE_GALLERY_OPEN_KEY, String.valueOf(isShapeGalleryOpen()));
        props.setProperty(REDUCED_CELEBRATION_EFFECTS_KEY, String.valueOf(isReducedCelebrationEffects()));
        props.setProperty(HIDE_OTHER_COMPANIONS_KEY, String.valueOf(hideOtherCompanions()));
        props.setProperty(HIDE_OWN_COMPANION_FIRST_PERSON_KEY, String.valueOf(hideOwnCompanionFirstPerson()));
        props.setProperty(COMPANION_WAITS_AT_BOBBER_KEY, String.valueOf(companionWaitsAtBobber()));
        try (var out = Files.newOutputStream(file)) {
            props.store(out, "Fishtastic client-side visual toggles.");
        } catch (IOException ignored) {
            // Best-effort - if the config file can't be written, the in-memory value still applies
            // for the rest of this session.
        }
    }

    private static Properties load() {
        Path file = getConfigDirectory().resolve(FILE_NAME);
        if (!Files.exists(file)) {
            writeDefault(file);
        }

        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            // Best-effort - if the config file can't be read, defaults apply.
        }
        return props;
    }

    private static void writeDefault(Path file) {
        try {
            Files.writeString(file, DEFAULT_FILE_CONTENTS);
        } catch (IOException ignored) {
            // Best-effort - if the config directory isn't writable, defaults apply.
        }
    }
}
