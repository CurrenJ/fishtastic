package grill24.fishtastic.client;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lightweight client-only visual toggles, stored as a plain properties file in the mod's config
 * directory. Same convention as {@link grill24.fishtastic.mcp.McpConfig}: hand-edit the file and
 * restart the game to apply changes — values are cached after first read, not polled live.
 */
public final class FishtasticClientConfig {
    private static final String FILE_NAME = "fishtastic-client.properties";
    private static final String TANK_WATER_FILL_KEY = "tankWaterFillEnabled";
    private static final String NOTIFICATION_VOLUME_KEY = "notificationVolume";
    private static final String SHAPE_GALLERY_OPEN_KEY = "shapeGalleryOpen";
    private static final int DEFAULT_NOTIFICATION_VOLUME = 100;
    private static final String DEFAULT_FILE_CONTENTS = """
            # Fishtastic client-side visual toggles.
            # Edit and restart the game to apply changes.

            # Draws an animated water fill behind fish tank glass.
            tankWaterFillEnabled=true

            # Volume (0-100) of quest/notification banner sounds. Set via
            # "/fishtastic notifications volume <0-100>" in-game, which also rewrites this file.
            notificationVolume=100

            # Whether the shape gallery beside the Fish Tank Assembly GUI starts revealed.
            # Toggled by the gallery button in that screen, which also rewrites this file.
            shapeGalleryOpen=true
            """;

    private static Boolean tankWaterFillEnabled;
    private static Integer notificationVolume;
    private static Boolean shapeGalleryOpen;

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
        return Math.clamp(getNotificationVolume(), 0, 100) / 100.0f;
    }

    /** Persists a new notification volume (0-100) to the properties file and caches it. */
    public static void setNotificationVolume(int volume) {
        notificationVolume = Math.clamp(volume, 0, 100);
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
     * Writes every setting back to the properties file. Each value is read through its own getter
     * so an untouched setting round-trips its on-disk value rather than being reset to a default.
     */
    private static void persist() {
        Path file = getConfigDirectory().resolve(FILE_NAME);
        Properties props = load();
        props.setProperty(NOTIFICATION_VOLUME_KEY, String.valueOf(getNotificationVolume()));
        props.setProperty(TANK_WATER_FILL_KEY, String.valueOf(isTankWaterFillEnabled()));
        props.setProperty(SHAPE_GALLERY_OPEN_KEY, String.valueOf(isShapeGalleryOpen()));
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
