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
    private static final String DEFAULT_FILE_CONTENTS = """
            # Fishtastic client-side visual toggles.
            # Edit and restart the game to apply changes.

            # Draws an animated water fill behind fish tank glass.
            tankWaterFillEnabled=true
            """;

    private static Boolean tankWaterFillEnabled;

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
