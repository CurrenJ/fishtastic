package grill24.fishtastic.client.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

/** Shared helper for building player-head item stacks that render the player's real skin. */
public final class PlayerHeadItems {
    private PlayerHeadItems() {}

    /**
     * Builds a player-head stack for {@code uuid}/{@code name} that renders the player's actual
     * skin.
     * <p>
     * {@link ResolvableProfile#createResolved} takes the given {@code GameProfile} as-is with no
     * further lookup, so a profile built from just a UUID/name (no "textures" property) renders
     * the default Steve/Alex skin. {@link ResolvableProfile#createUnresolved} instead marks it as
     * needing resolution, which the client's item renderer resolves asynchronously via the
     * profile resolver — the same way vanilla player-head items with only a name/id resolve their
     * texture.
     * <p>
     * Resolves by name rather than UUID when a name is available: the client's resolver
     * ({@code LocalPlayerResolver}) checks the tab list first, by name or by UUID, before falling
     * back to a real profile-service lookup. A UUID match against the tab list fails whenever the
     * UUID on record doesn't match the player's current one — which happens for every Fabric dev
     * environment, since dev-auth rotates each player's UUID every session (see
     * {@code FishCatchSavedData#SINGLEPLAYER_QUEST_UUID}'s doc for the same quirk) — where a name
     * match still finds them. This carries the same username-squatting caveat vanilla player heads
     * already have when built from just a name (Mojang can reassign a changed username), which is
     * an acceptable trade for actually resolving the common case.
     */
    public static ItemStack headStack(UUID uuid, String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        ResolvableProfile profile = (name != null && !name.isBlank())
                ? ResolvableProfile.createUnresolved(name)
                : ResolvableProfile.createUnresolved(uuid);
        head.set(DataComponents.PROFILE, profile);
        return head;
    }
}
