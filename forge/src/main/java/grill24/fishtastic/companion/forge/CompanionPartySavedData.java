package grill24.fishtastic.companion.forge;

import com.mojang.serialization.Codec;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CompanionPartySavedData extends SavedData {
    private static final Codec<Map<UUID, CompanionParty>> PARTY_MAP_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompanionParty.CODEC)
                    .fieldOf("parties").codec();
    private static final Codec<CompanionPartySavedData> CODEC =
            PARTY_MAP_CODEC.xmap(CompanionPartySavedData::new, data -> data.parties);

    public static final SavedDataType<CompanionPartySavedData> TYPE = new SavedDataType<>(
            Ids.of("fishtastic", "companion_parties"),
            CompanionPartySavedData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, CompanionParty> parties = new HashMap<>();

    private CompanionPartySavedData() {}

    private CompanionPartySavedData(Map<UUID, CompanionParty> parties) {
        this.parties.putAll(parties);
    }

    public static CompanionPartySavedData getOrCreate(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public CompanionParty get(UUID player) {
        return parties.getOrDefault(player, CompanionParty.EMPTY);
    }

    public void set(UUID player, CompanionParty party) {
        if (party.isEmpty()) {
            if (parties.remove(player) != null) setDirty();
        } else {
            parties.put(player, party);
            setDirty();
        }
    }
}
