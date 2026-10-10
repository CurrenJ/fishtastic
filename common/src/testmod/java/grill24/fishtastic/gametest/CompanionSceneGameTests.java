package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.network.CompanionScenePacket;
import grill24.fishtastic.server.CompanionSceneManager;
import grill24.fishtastic.server.FishingMinigameManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The pet-to-pet scene scheduler (docs/fish-companions.md §10): the server-side rules — who plays
 * which scene, who is told, and everything that stops a scene from starting. The client-side
 * playback is the self-test scene's job; this file is only about the decisions.
 *
 * <p>Every test runs the real scheduler ({@code CompanionSceneManager.tickLevel}) against mock
 * players with real companions, with an explicit clock, and reads the packets through the
 * manager's test sink instead of the players' connections.
 */
public final class CompanionSceneGameTests {
    private CompanionSceneGameTests() {}

    private static final Item BLUEGILL = FishtasticItems.BLUEGILL.value();
    private static final Item BETTA = FishtasticItems.BETTA.value();
    private static final Item STARFISH = FishtasticItems.STARFISH.value();
    private static final Item GARDEN_EEL = FishtasticItems.GARDEN_EEL.value();

    /** One scene packet as a recipient saw it. */
    private record Sent(ServerPlayer recipient, CompanionScenePacket packet) {}

    /**
     * Fresh state: no busied companion, no sink, and no companion anywhere (mock players from
     * earlier tests stay in the player list for the JVM's lifetime, so every test clears them all
     * rather than assuming it is alone in the level).
     */
    private static void setUp(GameTestHelper helper) {
        CompanionSceneManager.resetForTest();
        for (ServerPlayer player : helper.getLevel().players()) {
            CompanionStorage.set(player, ItemStack.EMPTY);
            player.fishing = null;
        }
    }

    /**
     * Offsets a mock player from wherever {@code placeNewPlayer} put them — spawn, in
     * already-loaded chunks. Only relative distance matters to the scheduler, so any loaded patch
     * works, and a test must not drag the chunk loader across the map to park players.
     */
    private static ServerPlayer place(ServerPlayer player, double dx, double dz) {
        Vec3 base = player.position();
        player.setPos(base.x + dx, base.y, base.z + dz);
        return player;
    }

    private static void equip(ServerPlayer player, Item fish) {
        CompanionStorage.set(player, new ItemStack(fish));
    }

    /** One scheduler tick at {@code now}, collecting everything sent. */
    private static List<Sent> tick(GameTestHelper helper, long now) {
        List<Sent> sent = new ArrayList<>();
        CompanionSceneManager.packetSinkForTest = (recipient, packet) -> sent.add(new Sent(recipient, packet));
        CompanionSceneManager.tickLevel(helper.getLevel(), now);
        return sent;
    }

    private static Set<UUID> recipientsOf(List<Sent> sent) {
        return sent.stream().map(s -> s.recipient().getUUID()).collect(Collectors.toSet());
    }

    public static void sameSpeciesPairPlaysG1AndTellsBothOwners(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);

        List<Sent> sent = tick(helper, 1000);

        Set<UUID> recipients = recipientsOf(sent);
        helper.assertTrue(recipients.contains(a.getUUID()) && recipients.contains(b.getUUID()),
                "both owners should be told, got " + recipients);
        CompanionScenePacket packet = sent.get(0).packet();
        helper.assertTrue(packet.scene() == CompanionSceneType.G1_SAME_SPECIES, "scene was " + packet.scene());
        helper.assertTrue(packet.startTime() == 1000, "start time was " + packet.startTime());
        Set<UUID> participants = Set.of(packet.first(), packet.second());
        helper.assertTrue(participants.equals(Set.of(a.getUUID(), b.getUUID())),
                "participants were " + participants);
        helper.assertTrue(CompanionSceneManager.isBusy(a.getUUID(), 1000)
                && CompanionSceneManager.isBusy(b.getUUID(), 1000), "both participants must be busy");
        helper.succeed();
    }

    public static void cooldownGatesRepeats(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);

        helper.assertTrue(tick(helper, 1000).size() >= 2, "the first approach must play a scene");
        helper.assertTrue(tick(helper, 1005).isEmpty(), "no repeat while either companion is on cooldown");

        long afterCooldown = 1000 + CompanionSceneType.G1_SAME_SPECIES.durationTicks()
                + CompanionSceneManager.COOLDOWN_TICKS + 1;
        helper.assertTrue(!tick(helper, afterCooldown).isEmpty(),
                "a companion still together after the cooldown greets again");
        helper.succeed();
    }

    public static void noSceneWhileOwnerInMinigame(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);

        FishingMinigameManager.get(helper.getLevel()).seedSessionForTest(a, List.of(List.of(new ItemStack(BLUEGILL))));
        helper.assertTrue(tick(helper, 1000).isEmpty(), "no scene starts while an owner is mid-minigame");
        helper.succeed();
    }

    public static void noSceneWhileOwnerHasALineOut(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);

        a.fishing = new FishingHook(a, helper.getLevel(), 0, 1);
        helper.assertTrue(tick(helper, 1000).isEmpty(),
                "no scene while an owner's line is out - the companion belongs at the bobber (§5)");
        a.fishing = null;
        helper.assertTrue(!tick(helper, 1001).isEmpty(), "the scene plays once the line is gone");
        helper.succeed();
    }

    public static void differentFreeSwimmersPlayG2(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, BLUEGILL);
        equip(b, BETTA);

        List<Sent> sent = tick(helper, 1000);
        helper.assertTrue(!sent.isEmpty(), "two free swimmers should play");
        helper.assertTrue(sent.get(0).packet().scene() == CompanionSceneType.G2_FREE_SWIM_EIGHT,
                "scene was " + sent.get(0).packet().scene());
        helper.succeed();
    }

    public static void percherPairPlaysNothing(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, STARFISH);
        equip(b, GARDEN_EEL); // different species, and neither swims - nothing to choreograph yet

        helper.assertTrue(tick(helper, 1000).isEmpty(), "a non-swimming, mixed pair plays nothing in v1");
        helper.succeed();
    }

    public static void outOfRangePairPlaysNothing(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 0, 12);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);

        helper.assertTrue(tick(helper, 1000).isEmpty(), "12 blocks apart is outside the greet range");
        helper.succeed();
    }

    public static void oneScenePerCompanion(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        ServerPlayer c = place(mockPlayer.get(), 4, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);
        equip(c, BLUEGILL);

        helper.assertTrue(tick(helper, 1000).size() >= 2, "the first pair plays");
        helper.assertTrue(tick(helper, 1001).isEmpty(),
                "nobody is free while the first scene (and its cooldown) runs - one scene per companion");
        helper.succeed();
    }

    public static void sameSpeciesPairBeatsFreeSwimmers(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        ServerPlayer c = place(mockPlayer.get(), 4, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);
        equip(c, BETTA);

        List<Sent> sent = tick(helper, 1000);
        helper.assertTrue(!sent.isEmpty(), "a scene should play among the three");
        CompanionScenePacket packet = sent.get(0).packet();
        helper.assertTrue(packet.scene() == CompanionSceneType.G1_SAME_SPECIES,
                "same species (G1) beats free swimmers (G2), got " + packet.scene());
        helper.assertTrue(Set.of(packet.first(), packet.second()).equals(Set.of(a.getUUID(), b.getUUID())),
                "the same-species pair should be the bluegills");
        helper.succeed();
    }

    public static void nearbyObserversAreToldAndFarOnesAreNot(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        setUp(helper);
        ServerPlayer a = place(mockPlayer.get(), 0, 0);
        ServerPlayer b = place(mockPlayer.get(), 2, 0);
        equip(a, BLUEGILL);
        equip(b, BLUEGILL);
        ServerPlayer near = place(mockPlayer.get(), 20, 0);  // a companion-less bystander, in sight
        ServerPlayer far = place(mockPlayer.get(), 200, 0);  // too far to see anything

        List<Sent> sent = tick(helper, 1000);
        Set<UUID> recipients = recipientsOf(sent);
        helper.assertTrue(recipients.contains(a.getUUID()) && recipients.contains(b.getUUID()),
                "the participants are always told");
        helper.assertTrue(recipients.contains(near.getUUID()), "a nearby bystander sees the scene");
        helper.assertTrue(!recipients.contains(far.getUUID()), "a far bystander is not told");
        // Exactly the players within the packet's reach of either participant — stray mock players
        // from earlier tests included, so the assertion stays exact without moving anyone.
        double reachSqr = 128.0 * 128.0;
        Set<UUID> expected = helper.getLevel().players().stream()
                .filter(p -> p.distanceToSqr(a) <= reachSqr || p.distanceToSqr(b) <= reachSqr)
                .map(ServerPlayer::getUUID)
                .collect(Collectors.toSet());
        helper.assertTrue(recipients.equals(expected), "recipients " + recipients + " != expected " + expected);
        helper.succeed();
    }
}
