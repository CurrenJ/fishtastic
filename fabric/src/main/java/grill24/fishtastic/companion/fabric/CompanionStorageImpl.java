package grill24.fishtastic.companion.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.companion.CompanionParty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/** Fabric backing for {@link grill24.fishtastic.companion.CompanionStorage}. */
public final class CompanionStorageImpl {
    private CompanionStorageImpl() {}

    /**
     * Absent when the party is empty (stored as "not attached"), persistent, kept on death, and
     * synced to the owner and every player tracking them. The id predates the party, but the
     * codec now accepts only the three-slot form; the retired single-stack form is not migrated.
     */
    private static final AttachmentType<CompanionParty> COMPANION = AttachmentRegistry.create(
            Fishtastic.id("companion"),
            builder -> builder
                    .persistent(CompanionParty.CODEC)
                    .copyOnDeath()
                    .syncWith(CompanionParty.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Forces the static registration above during mod init. */
    public static void register() {}

    public static CompanionParty party(Player player) {
        return player.getAttachedOrElse(COMPANION, CompanionParty.EMPTY);
    }

    public static void setParty(Player player, CompanionParty party) {
        if (party.isEmpty()) {
            player.removeAttached(COMPANION);
        } else {
            player.setAttached(COMPANION, party);
        }
    }
}
