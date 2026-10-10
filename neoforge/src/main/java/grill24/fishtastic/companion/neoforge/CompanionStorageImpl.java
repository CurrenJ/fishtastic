package grill24.fishtastic.companion.neoforge;

import grill24.fishtastic.Fishtastic;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge backing for {@link grill24.fishtastic.companion.CompanionStorage}. */
public final class CompanionStorageImpl {
    private CompanionStorageImpl() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Fishtastic.MOD_ID);

    /**
     * Empty by default; saved only when set; kept on death; synced to the owner and every player
     * tracking them ({@code setData} syncs on its own — see {@code AttachmentHolder#setData}).
     */
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> COMPANION =
            ATTACHMENT_TYPES.register("companion", () -> AttachmentType.builder(() -> ItemStack.EMPTY)
                    .serialize(ItemStack.OPTIONAL_CODEC.fieldOf("stack"), stack -> !stack.isEmpty())
                    .copyOnDeath()
                    .sync(ItemStack.OPTIONAL_STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static ItemStack get(Player player) {
        return player.getData(COMPANION);
    }

    public static void set(Player player, ItemStack stack) {
        player.setData(COMPANION, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }
}
