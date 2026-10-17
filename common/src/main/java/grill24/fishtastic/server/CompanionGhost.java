package grill24.fishtastic.server;

import com.mojang.authlib.GameProfile;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * A dev-only second player for testing cross-player scenes alone (party spec §28.3, D31): a real
 * {@link ServerPlayer} on a dead connection, added to the player list, so the owner's client sees
 * a genuine remote player, builds its party from the synced storage and runs it as it would any
 * other player's; and the real scheduler ({@link CompanionSceneManager}) pairs the two and sends
 * the real scene packet. The server moves it by script: it walks up to {@link #STAND_OFF} blocks
 * from the owner and stands facing them, or walks off and leaves.
 *
 * <p>One ghost per server. It joins and leaves like a player (chat line, tab list), and its player
 * file is written to the world on removal, as the game tests' mock players' are.
 */
public final class CompanionGhost {
    private CompanionGhost() {}

    /** The ghost's fixed name and offline-style id, so a second {@code add} replaces it. */
    public static final String NAME = "CompanionGhost";
    public static final UUID ID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    /** Where it appears (blocks ahead of the owner), where it stops, and how far it walks off. */
    public static final double APPEAR = 12.0, STAND_OFF = 3.0, LEAVE = 16.0;
    /** A walking player's speed, blocks per tick. */
    private static final double WALK = 4.317 / 20.0;

    public enum Mode { APPROACH, STAND, LEAVE }

    @Nullable private static ServerPlayer ghost;
    @Nullable private static UUID owner;
    private static Mode mode = Mode.STAND;
    private static double standOff = STAND_OFF;

    @Nullable
    public static ServerPlayer ghost() {
        return ghost != null && !ghost.isRemoved() ? ghost : null;
    }

    /**
     * Adds the ghost {@link #APPEAR} blocks ahead of {@code owner} with {@code fish} as its party
     * (one per slot), facing them, and sets it walking up. Replaces any ghost there was.
     */
    public static ServerPlayer add(ServerPlayer owner, List<ItemStack> fish) {
        remove(owner.level().getServer());
        ServerLevel level = owner.serverLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(ID, NAME), false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isCreative() {
                return true;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        GhostConnection.configure(connection);
        Vec3 ahead = Vec3.directionFromRotation(0, owner.getYRot()).scale(APPEAR);
        Vec3 at = owner.position().add(ahead.x, 0, ahead.z);
        player.moveTo(at.x, owner.getY(), at.z, faceYaw(at, owner.position()), 0);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.moveTo(at.x, owner.getY(), at.z, faceYaw(at, owner.position()), 0);
        CompanionStorage.clear(player);
        for (int i = 0; i < fish.size() && i < 3; i++) Companions.equip(player, i, fish.get(i));
        ghost = player;
        CompanionGhost.owner = owner.getUUID();
        mode = Mode.APPROACH;
        standOff = STAND_OFF;
        Fishtastic.LOGGER.info("[ghost] added beside {} with {} fish", owner.getName().getString(), fish.size());
        return player;
    }

    /** Sets what it does: walk up and stand ({@code standOff} blocks off), stand, or walk off and leave. */
    public static void walk(Mode m, double standOffBlocks) {
        mode = m;
        standOff = standOffBlocks;
    }

    public static void remove(MinecraftServer server) {
        ServerPlayer g = ghost;
        ghost = null;
        owner = null;
        if (g == null || g.isRemoved()) return;
        server.getPlayerList().remove(g);
        Fishtastic.LOGGER.info("[ghost] removed");
    }

    /** One server tick: walk the script toward (or away from) its owner. */
    public static void tick(MinecraftServer server) {
        ServerPlayer g = ghost();
        if (g == null) return;
        ServerPlayer o = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (o == null || o.level() != g.level()) {
            remove(server);
            return;
        }
        Vec3 me = g.position(), them = o.position();
        Vec3 flat = new Vec3(me.x - them.x, 0, me.z - them.z);
        double d = flat.length();
        Vec3 away = d < 1e-6 ? new Vec3(1, 0, 0) : flat.scale(1 / d);
        double step = switch (mode) {
            case APPROACH -> d > standOff ? -Math.min(WALK, d - standOff) : 0;
            case LEAVE -> WALK;
            case STAND -> 0;
        };
        Vec3 next = new Vec3(me.x + away.x * step, them.y, me.z + away.z * step);
        float yaw = mode == Mode.LEAVE ? faceYaw(me, me.add(away)) : faceYaw(next, them);
        g.moveTo(next.x, next.y, next.z, yaw, 0);
        g.setYHeadRot(yaw);
        g.setYBodyRot(yaw);
        if (mode == Mode.APPROACH && d <= standOff + 1e-3) mode = Mode.STAND;
        if (mode == Mode.LEAVE && d >= LEAVE) remove(server);
    }

    /** The yaw that looks from {@code from} toward {@code to}. */
    private static float faceYaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    public static String describe() {
        ServerPlayer g = ghost();
        if (g == null) return "No ghost";
        return "Ghost " + mode.name().toLowerCase() + " at " + String.format("%.1f, %.1f, %.1f", g.getX(), g.getY(), g.getZ())
                + ", party " + CompanionStorage.party(g);
    }
}
