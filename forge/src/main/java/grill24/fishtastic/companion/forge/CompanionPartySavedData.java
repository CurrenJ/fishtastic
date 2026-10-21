package grill24.fishtastic.companion.forge;

import com.mojang.serialization.Codec;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.companion.CompanionParty;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned companion parties, saved in the overworld's data folder and keyed by player UUID.
 * The party survives death/player replacement because it never lives on the player entity.
 * Deliberately not part of {@code FishCatchSavedData}: a backup restore rewinds that independently
 * of inventories, which would let bond -> backup -> unequip -> restore duplicate the fish.
 */
public final class CompanionPartySavedData extends SavedData {
    public static final String FILE_ID = "fishtastic_companions";

    private static final Codec<Map<UUID, CompanionParty>> CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompanionParty.CODEC).fieldOf("parties").codec();

    public static final SavedData.Factory<CompanionPartySavedData> FACTORY =
            new SavedData.Factory<>(CompanionPartySavedData::new, CompanionPartySavedData::load, null);

    private final Map<UUID, CompanionParty> parties = new HashMap<>();

    public static CompanionPartySavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    private static CompanionPartySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionPartySavedData data = new CompanionPartySavedData();
        CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .resultOrPartial(error -> Fishtastic.LOGGER.error("Failed to load companion parties: {}", error))
                .ifPresent(data.parties::putAll);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), parties)
                .resultOrPartial(error -> Fishtastic.LOGGER.error("Failed to save companion parties: {}", error))
                .ifPresent(encoded -> tag.merge((CompoundTag) encoded));
        return tag;
    }

    public CompanionParty get(UUID player) {
        return parties.getOrDefault(player, CompanionParty.EMPTY);
    }

    /** An empty party removes the entry, mirroring the attachment loaders' "not attached". */
    public void set(UUID player, CompanionParty party) {
        if (party.isEmpty()) {
            if (parties.remove(player) != null) setDirty();
        } else {
            parties.put(player, party);
            setDirty();
        }
    }
}
