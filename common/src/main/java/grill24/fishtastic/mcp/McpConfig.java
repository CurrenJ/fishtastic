package grill24.fishtastic.mcp;

import dev.architectury.injectables.annotations.ExpectPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Off-by-default gate for the MCP bridge, layered on top of {@link grill24.fishtastic.env.DevEnvironmentCheck}'s
 * dev-environment check: even in a dev environment, {@code /fishtastic mcp start} stays disabled until the mod
 * author explicitly opts in via a hand-edited config file. See ideas.txt - "MCP causes lag buildup and eventual
 * softlock/crash" is exactly the failure mode this exists to keep from happening by accident.
 */
public final class McpConfig {
    private static final String FILE_NAME = "fishtastic-mcp.properties";
    private static final String ENABLED_KEY = "enabled";
    private static final String DEFAULT_FILE_CONTENTS = """
            # Fishtastic MCP bridge - disabled by default (dev-only tooling; see McpBridgeServer's docs).
            # Set to true to allow `/fishtastic mcp start` to run, then restart the game.
            enabled=false
            """;

    private McpConfig() {}

    @ExpectPlatform
    public static Path getConfigDirectory() {
        throw new AssertionError();
    }

    public static boolean isEnabled() {
        Path file = getConfigDirectory().resolve(FILE_NAME);
        if (!Files.exists(file)) {
            writeDefault(file);
            return false;
        }

        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            return false;
        }
        return Boolean.parseBoolean(props.getProperty(ENABLED_KEY, "false"));
    }

    private static void writeDefault(Path file) {
        try {
            Files.writeString(file, DEFAULT_FILE_CONTENTS);
        } catch (IOException ignored) {
            // Best-effort - if the config directory isn't writable the bridge just stays disabled.
        }
    }
}
