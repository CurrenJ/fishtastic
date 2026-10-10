package grill24.fishtastic.companion.fabric;

import grill24.fishtastic.Fishtastic;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Fabric backing for {@link grill24.fishtastic.companion.CompanionStorage}. */
public final class CompanionStorageImpl {
    private CompanionStorageImpl() {}

    /**
     * Absent when there is no companion (an empty stack is stored as "not attached"), persistent,
     * kept on death, and synced to the owner and every player tracking them.
     */
    private static final AttachmentType<ItemStack> COMPANION = AttachmentRegistry.create(
            Fishtastic.id("companion"),
            builder -> builder
                    .persistent(ItemStack.CODEC)
                    .copyOnDeath()
                    .syncWith(ItemStack.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Forces the static registration above during mod init. */
    public static void register() {}

    public static ItemStack get(Player player) {
        return player.getAttachedOrElse(COMPANION, ItemStack.EMPTY);
    }

    public static void set(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            player.removeAttached(COMPANION);
        } else {
            player.setAttached(COMPANION, stack.copy());
        }
    }
}
