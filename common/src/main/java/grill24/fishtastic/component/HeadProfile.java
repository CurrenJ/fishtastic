package grill24.fishtastic.component;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The player-head profile {@link grill24.fishtastic.FishtasticItemData#setHeadProfile} writes onto
 * a stack. 1.21.1's {@code ResolvableProfile} doesn't exist before 1.20.5; this is a small
 * name/uuid/{@link GameProfile} carrier instead (docs/backport-pass2/track-b-1.20.1.md B2.1,
 * seam S1b — a matching type could replace {@code ResolvableProfile} on every branch later, but
 * isn't required now).
 */
public record HeadProfile(@Nullable String name, @Nullable UUID uuid, @Nullable GameProfile gameProfile) {
    public static HeadProfile ofName(String name) {
        return new HeadProfile(name, null, null);
    }

    public static HeadProfile of(GameProfile profile) {
        return new HeadProfile(profile.getName(), profile.getId(), profile);
    }
}
