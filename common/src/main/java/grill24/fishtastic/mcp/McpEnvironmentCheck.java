package grill24.fishtastic.mcp;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Loader-specific "is this a dev environment" check, gating the MCP bridge from ever activating in a
 * distributed build even if {@code /fishtastic mcp start} is somehow invoked. Mirrors
 * {@link grill24.fishtastic.architectury.RegistrationApiSided}'s {@code @ExpectPlatform} shape.
 */
public class McpEnvironmentCheck {
    @ExpectPlatform
    public static boolean isDevelopmentEnvironment() {
        throw new AssertionError();
    }
}
