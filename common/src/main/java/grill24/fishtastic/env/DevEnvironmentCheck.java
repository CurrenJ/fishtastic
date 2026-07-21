package grill24.fishtastic.env;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Loader-specific "is this a dev environment" check. Deliberately lives outside {@code grill24.fishtastic.mcp}
 * (unlike the rest of that package) because production builds exclude {@code mcp/**} from the shipped jar
 * entirely - every call site that reaches into the mcp package guards itself with this check first, so this
 * class (and its {@code false}-in-production answer) has to still be present when mcp isn't.
 */
public class DevEnvironmentCheck {
    @ExpectPlatform
    public static boolean isDevelopmentEnvironment() {
        throw new AssertionError();
    }
}
