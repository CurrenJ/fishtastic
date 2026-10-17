package grill24.fishtastic.companion;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where 1.20.1 keeps every player's {@link CompanionParty}: its own overworld save file, keyed by
 * player UUID. Neither Fabric API 0.92 nor Forge 47 has a player attachment API, so the versions
 * that do (1.21.1, 26.1.2) use a platform attachment; this one uses a single common implementation,
 * and the party survives death and respawn because it never lives on the player entity.
 *
 * <p>Deliberately not in {@code FishCatchSavedData}: a backup restore rewinds that independently of
 * inventories, which would let a bond → backup → unequip → restore duplicate the fish.
 */
public final class CompanionPartySavedData extends SavedData {
    /** Flattened like {@code FishCatchSavedData.FILE_ID}: 1.20.1 storage only takes flat names. */
    public static final String FILE_ID = "fishtastic_companions";

    private static final Codec<Map<UUID, CompanionParty>> CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompanionParty.CODEC).fieldOf("parties").codec();

    private final Map<UUID, CompanionParty> parties = new HashMap<>();

    public static CompanionPartySavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CompanionPartySavedData::load, CompanionPartySavedData::new, FILE_ID);
    }

    private static CompanionPartySavedData load(CompoundTag tag) {
        CompanionPartySavedData data = new CompanionPartySavedData();
        CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> grill24.fishtastic.Fishtastic.LOGGER.error("Failed to load companion parties: {}", error))
                .ifPresent(data.parties::putAll);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CODEC.encodeStart(NbtOps.INSTANCE, parties)
                .resultOrPartial(error -> grill24.fishtastic.Fishtastic.LOGGER.error("Failed to save companion parties: {}", error))
                .ifPresent(encoded -> tag.merge((CompoundTag) encoded));
        return tag;
    }

    public CompanionParty get(UUID player) {
        return parties.getOrDefault(player, CompanionParty.EMPTY);
    }

    /** An empty party removes the entry, like the other versions' "not attached". */
    public void set(UUID player, CompanionParty party) {
        if (party.isEmpty()) {
            if (parties.remove(player) != null) setDirty();
        } else {
            parties.put(player, party);
            setDirty();
        }
    }
}
