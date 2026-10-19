package grill24.fishtastic.companion.neoforge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.companion.CompanionParty;
import net.minecraft.world.entity.player.Player;
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
     * Empty by default; saved only when non-empty; kept on death; synced to the owner and every
     * player tracking them ({@code setData} syncs on its own — see {@code AttachmentHolder#setData}).
     * The id predates the party: its codec reads the old {@code {stack: ...}} attachment into the
     * lead slot.
     */
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<CompanionParty>> COMPANION =
            ATTACHMENT_TYPES.register("companion", () -> AttachmentType.builder(() -> CompanionParty.EMPTY)
                    .serialize(CompanionParty.MAP_CODEC, party -> !party.isEmpty())
                    .copyOnDeath()
                    .sync(CompanionParty.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static CompanionParty party(Player player) {
        return player.getData(COMPANION);
    }

    public static void setParty(Player player, CompanionParty party) {
        player.setData(COMPANION, party);
    }
}
