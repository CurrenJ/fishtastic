package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.EcologyClient;
import grill24.fishtastic.ecology.EcologySeed;
import grill24.fishtastic.ecology.NaturalPool;
import grill24.fishtastic.network.EcologySeedSyncPacket;
import grill24.fishtastic.server.EcologySeedSync;
import grill24.fishtastic.server.FishingPoolResolver;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/**
 * World fish ecology core (docs/world-fish-exploration-implementation-plan.md, Step 1) against a
 * real level: the extracted natural pool equals the pre-refactor resolve path with real registries
 * and a real biome, and the ecology seed is derived (not the world seed) and survives the packet
 * codec. All methods are pure GameTestHelper consumers.
 */
public final class EcologyGameTests {

    private EcologyGameTests() {}

    public static void naturalPoolMatchesTheServerResolvePath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Registry<Item> items = level.registryAccess().registryOrThrow(Registries.ITEM);
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        FishingPoolResolver.SpeciesContext expected = FishingPoolResolver.resolve(items, profiles,
                FishingPoolResolver.resolveEnvironment(level, pos, null),
                FishingPoolResolver.Inputs.of(BaitEffect.NO_BAIT, null, FishingPoolResolver.PersonalEquipment.NEUTRAL));
        FishingPoolResolver.SpeciesContext actual = NaturalPool.resolveAt(level, pos);

        helper.assertTrue(!actual.candidates().isEmpty(), "The natural pool must not be empty in a real level");
        helper.assertTrue(expected.equals(actual), "NaturalPool must equal the server resolve path");
        helper.assertTrue(expected.fingerprint() == actual.fingerprint(), "Fingerprints must match");
        helper.succeed();
    }

    public static void ecologySeedIsDerivedAndSurvivesThePacketCodec(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        long seed = EcologySeedSync.seedFor(level);

        helper.assertTrue(seed == EcologySeed.derive(level.getSeed(), dimension), "Seed must be the documented derivation");
        helper.assertTrue(seed != level.getSeed(), "The world seed must never be what clients receive");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        EcologySeedSyncPacket.STREAM_CODEC.encode(buf, new EcologySeedSyncPacket(dimension, seed));
        EcologySeedSyncPacket decoded = EcologySeedSyncPacket.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.seed() == seed && decoded.dimension().equals(dimension), "Packet must round-trip");

        EcologyClient.clear();
        EcologyClient.set(decoded.dimension(), decoded.seed());
        helper.assertTrue(Long.valueOf(seed).equals(EcologyClient.seedFor(dimension)), "Client holder must return the seed");
        helper.assertTrue(EcologyClient.seedFor("minecraft:the_nether") == null, "A seed for another dimension must not leak across");
        EcologyClient.clear();
        helper.succeed();
    }
}
