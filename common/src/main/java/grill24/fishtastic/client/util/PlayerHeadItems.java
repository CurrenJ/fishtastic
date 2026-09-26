package grill24.fishtastic.client.util;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.HeadProfile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/** Shared helper for building player-head item stacks that render the player's real skin. */
public final class PlayerHeadItems {
    private PlayerHeadItems() {}

    /**
     * Builds a player-head stack for {@code uuid}/{@code name} that renders the player's actual
     * skin.
     * <p>
     * 1.20.1 has no {@code ResolvableProfile}/async profile resolver (added in 1.20.5) — a
     * player-head stack's {@code SkullOwner} just carries a name-or-uuid {@link GameProfile}
     * directly, and vanilla's own skull renderer resolves the skin from that profile via
     * {@code SkullBlockEntity}'s synchronous profile cache the same way it always has.
     * <p>
     * Builds by name rather than UUID when a name is available, matching the client resolver's
     * own name-first lookup order in newer versions — a UUID match fails whenever the UUID on
     * record doesn't match the player's current one, which happens for every Fabric dev
     * environment, since dev-auth rotates each player's UUID every session (see
     * {@code FishCatchSavedData#SINGLEPLAYER_QUEST_UUID}'s doc for the same quirk).
     */
    public static ItemStack headStack(UUID uuid, String name) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        FishtasticItemData.setHeadProfile(head, headProfile(uuid, name));
        return head;
    }

    private static HeadProfile headProfile(UUID uuid, String name) {
        return (name != null && !name.isBlank()) ? HeadProfile.ofName(name) : new HeadProfile(null, uuid, null);
    }

    /**
     * Same name-first resolution strategy as {@link #headStack}, exposed for callers that need the
     * {@link GameProfile} directly (e.g. to render a posed player model rather than an item).
     */
    public static GameProfile gameProfile(UUID uuid, String name) {
        return (name != null && !name.isBlank()) ? new GameProfile(uuid, name) : new GameProfile(uuid, "");
    }
}
