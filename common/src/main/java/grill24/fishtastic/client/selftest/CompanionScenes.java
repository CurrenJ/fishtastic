package grill24.fishtastic.client.selftest;

import grill24.fishtastic.client.selftest.RenderSelfTest.Step;

import static grill24.fishtastic.client.selftest.RenderSelfTest.STEPS;
import static grill24.fishtastic.client.selftest.RenderSelfTest.block;
import static grill24.fishtastic.client.selftest.RenderSelfTest.check;
import static grill24.fishtastic.client.selftest.AmbientScenes.fill;
import static grill24.fishtastic.client.selftest.RenderSelfTest.fish;
import static grill24.fishtastic.client.selftest.RenderSelfTest.item;
import static grill24.fishtastic.client.selftest.QuestBookScenes.key;
import static grill24.fishtastic.client.selftest.RenderSelfTest.note;
import static grill24.fishtastic.client.selftest.RenderSelfTest.origin;
import static grill24.fishtastic.client.selftest.RenderSelfTest.param;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.scenes;
import static grill24.fishtastic.client.selftest.RenderSelfTest.school;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;
import static grill24.fishtastic.client.selftest.RenderSelfTest.started;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tick;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tickProbe;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Party, movement, mood and multiplayer scene fixtures. */
final class CompanionScenes {
    static void queuePartyLoginRegression(String scene) {
        if (scene.equals("partyseed")) {
            queue(1, mc -> mc.player.connection.sendCommand("fishtastic companion give @s fishtastic:bluegill 20 1"));
            queueUntil("partyseed.stored", 200, mc -> !grill24.fishtastic.companion.CompanionStorage.party(mc.player).isEmpty(),
                    () -> "party=" + grill24.fishtastic.companion.CompanionStorage.party(Minecraft.getInstance().player));
        } else if (scene.equals("partyreturn")) {
            queueUntil("partyreturn.persisted", 200, mc -> !grill24.fishtastic.companion.CompanionStorage.party(mc.player).isEmpty(),
                    () -> "party=" + grill24.fishtastic.companion.CompanionStorage.party(Minecraft.getInstance().player));
            queue(100, mc -> note("partyreturn owner stayed online for observer"));
        } else {
            queue(1, mc -> check("partyobserver.owner_initially_offline",
                    mc.level.players().stream().noneMatch(p -> p.getName().getString().equals("FishB")), "waiting for FishB"));
            queueUntil("partyobserver.returning_party", 3600, mc -> mc.level.players().stream()
                            .anyMatch(p -> p.getName().getString().equals("FishB")
                                    && !grill24.fishtastic.companion.CompanionStorage.party(p).isEmpty()),
                    () -> "online=" + Minecraft.getInstance().level.players().stream().map(p -> p.getName().getString()).toList());
        }
    }

    // ── Fish companions ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    /**
     * Two-client spike (party spec §28.5): two clients join one dedicated server
     * ({@code server=host:port}, {@code role=a|b}; both opped by the init script). Each waits for the
     * other, stands 3 blocks from it face to face, takes one free swimmer, and checks that it sees
     * the other player's party built and simulated, and that the server's scene packet arrives.
     * Then it lingers so the peer's shots include it.
     */
    static void queueTwoClientScene() {
        final boolean a = param("role", "a").equals("a");
        final int[] waited = {0};
        final Consumer<Minecraft>[] peerPoll = new Consumer[1];
        peerPoll[0] = mc -> {
            if (mc.level.players().size() >= 2) {
                note("twoclient peer joined after " + waited[0] + " ticks");
                return;
            }
            if ((waited[0] += 20) > 20 * 180) {
                check("twoclient.peer_joined", false, "alone after 180 s");
                return;
            }
            STEPS.addFirst(new Step(20, peerPoll[0]));
        };
        queue(1, peerPoll[0]);
        queue(1, mc -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true);
            var c = mc.player.connection;
            c.sendCommand("gamerule advance_time false");
            c.sendCommand("gamerule spawn_mobs false");
            c.sendCommand("time set 6000");
            c.sendCommand(a ? "tp @s 0.5 -60 0.5 -90 0" : "tp @s 3.5 -60 0.5 90 0");
            c.sendCommand("fishtastic companion give @s " + (a ? "fishtastic:bluegill 20 1" : "fishtastic:discus 20 1"));
        });
        queue(100, mc -> {
            Player peer = mc.level.players().stream().filter(p -> p != mc.player).findFirst().orElse(null);
            check("twoclient.peer_visible", peer != null,
                    mc.level.players().stream().map(p -> p.getName().getString()).toList().toString());
            var mine = grill24.fishtastic.client.companion.ClientCompanions.localCompanions();
            check("twoclient.local_party", !mine.isEmpty(), mine.stream()
                    .map(grill24.fishtastic.client.companion.ClientCompanion::partyLine).toList().toString());
            var theirs = peer == null ? List.<grill24.fishtastic.client.companion.ClientCompanion>of()
                    : grill24.fishtastic.client.companion.ClientCompanions.companionsOf(peer);
            check("twoclient.remote_party", !theirs.isEmpty(), theirs.stream()
                    .map(grill24.fishtastic.client.companion.ClientCompanion::partyLine).toList().toString());
            screenshot(mc, "twoclient", (a ? "a" : "b") + "_facing");
        });
        final int[] sceneWait = {0};
        final Consumer<Minecraft>[] scenePoll = new Consumer[1];
        scenePoll[0] = mc -> {
            int n = grill24.fishtastic.client.companion.ClientCompanions.devScenePackets;
            if (n > 0 || (sceneWait[0] += 20) > 20 * 40) {
                check("twoclient.scene_packet", n > 0, n + " packets, last "
                        + grill24.fishtastic.client.companion.ClientCompanions.devLastScene);
                return;
            }
            STEPS.addFirst(new Step(20, scenePoll[0]));
        };
        queue(1, scenePoll[0]);
        // The scene plays on this client (party spec §28): its own fish on the path after the
        // greeting, the other player's fish in the scene too (this client simulates both), and
        // the eight running to its end.
        queueUntil("twoclient.local_on_path", 400, mc -> {
            String sc = grill24.fishtastic.client.companion.ClientCompanions.devLocalScene();
            if (sc == null || !sc.startsWith("EIGHT") || !sc.contains("leader SCENE")) return false;
            return Integer.parseInt(sc.replaceAll(".* t(\\d+) .*", "$1")) > grill24.fishsim.companion.CrossScene.GREET_TICKS + 10;
        }, CompanionScenes::localScene);
        queue(1, mc -> {
            Player peer = mc.level.players().stream().filter(pl -> pl != mc.player).findFirst().orElse(null);
            var theirs = peer == null ? List.<grill24.fishtastic.client.companion.ClientCompanion>of()
                    : grill24.fishtastic.client.companion.ClientCompanions.companionsOf(peer);
            String goal = theirs.isEmpty() || theirs.get(0).agent() == null ? "none" : theirs.get(0).agent().goal();
            check("twoclient.remote_in_scene", "SCENE".equals(goal), "peer's fish: " + goal);
            mc.gui.getChat().clearMessages(false);
            screenshot(mc, "twoclient", (a ? "a" : "b") + "_scene");
        });
        queueUntil("twoclient.scene_done", 500, mc -> "EIGHT done".equals(
                grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd), CompanionScenes::localScene);
        queue(20, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(30, mc -> screenshot(mc, "twoclient", (a ? "a" : "b") + "_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // Linger so the peer's shots and checks still find this player.
        queue(400, mc -> note("twoclient done, " + mc.level.players().size() + " players"));
    }

    @SuppressWarnings("unchecked")
    /**
     * Polls {@code cond} every 5 ticks for up to {@code maxTicks}, then records {@code name} as a
     * check with {@code detail} (and how long it took).
     */
    static void queueUntil(String name, int maxTicks, java.util.function.Predicate<Minecraft> cond,
                                   java.util.function.Supplier<String> detail) {
        final int[] waited = {0};
        final Consumer<Minecraft>[] poll = new Consumer[1];
        poll[0] = mc -> {
            boolean ok = cond.test(mc);
            if (ok || (waited[0] += 5) > maxTicks) {
                check(name, ok, detail.get() + " (" + waited[0] + " ticks)");
                return;
            }
            STEPS.addFirst(new Step(5, poll[0]));
        };
        queue(1, poll[0]);
    }

    /** A command prefix that runs as this client's own player, by name (never @p: the ghost can be nearer). */
    private static String asMe(Minecraft mc) {
        String n = mc.player.getName().getString();
        return "execute as " + n + " at " + n + " run ";
    }

    /** The ghost owner as this client sees it, or null. */
    private static Player ghostPlayer(Minecraft mc) {
        if (mc.level == null) return null;
        for (Player p : mc.level.players()) {
            if (p.getName().getString().equals(grill24.fishtastic.server.CompanionGhost.NAME)) return p;
        }
        return null;
    }

    private static String localScene() {
        String s = grill24.fishtastic.client.companion.ClientCompanions.devLocalScene();
        return s == null ? "none (last ended: " + grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd + ")" : s;
    }

    private static void shoot(Minecraft mc, String shot) {
        mc.gui.getChat().clearMessages(false);
        screenshot(mc, "companionscenes", shot);
    }

    /**
     * Cross-player scenes alone (party spec §28.3, D31): the ghost owner walks up with a discus
     * while the local player has a bluegill, the real scheduler starts G2, and the bluegill greets
     * and swims the eight; then a bluegill ghost and a forced G1 (with the bubble heart), a packet
     * 120 ticks late joining mid-path, and the ghost walking off ending a scene.
     */
    static void queueCompanionScenesScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true));
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "fishtastic companion give @p fishtastic:bluegill 20 1");
            run(s, asMe(mc) + "fishtastic companion ghost add fishtastic:discus");
        }));
        queueUntil("companionscenes.ghost_party", 300, mc -> {
            Player g = ghostPlayer(mc);
            return g != null && !grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).isEmpty();
        }, () -> "ghost " + (ghostPlayer(Minecraft.getInstance()) != null));
        final int[] before = new int[2];
        queue(1, mc -> {
            before[0] = grill24.fishtastic.client.companion.ClientCompanions.scenesStarted;
            before[1] = grill24.fishtastic.client.companion.ClientCompanions.heartsSpawned;
        });
        // The real scheduler: the ghost walks into range and G2 starts by itself.
        queueUntil("companionscenes.g2_scheduled", 400,
                mc -> localScene().startsWith("EIGHT"), CompanionScenes::localScene);
        queue(CrossSceneTicks.GREET + 30, mc -> {
            String sc = localScene();
            check("companionscenes.g2_on_path", sc.contains("leader SCENE") && !sc.contains("left:"), sc);
            shoot(mc, "g2_fp");
        });
        queue(20, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(20, mc -> shoot(mc, "g2_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        queueUntil("companionscenes.g2_done", 600, mc -> "EIGHT done".equals(
                grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd), CompanionScenes::localScene);
        // G1: a bluegill ghost, the scene forced (the pair is on cooldown now).
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost add fishtastic:bluegill")));
        queue(80, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost scene g1")));
        queueUntil("companionscenes.g1_started", 100, mc -> localScene().startsWith("RING"), CompanionScenes::localScene);
        // Face the ghost, a little up, so the ring and the heart between the two players are in shot.
        queue(1, mc -> {
            Player g = ghostPlayer(mc);
            if (g == null) return;
            double dx = g.getX() - mc.player.getX(), dz = g.getZ() - mc.player.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            mc.player.setYRot(yaw);
            mc.player.setYHeadRot(yaw);
            mc.player.setXRot(-15f);
            note(String.format(Locale.ROOT, "ghost at %.1f %.1f %.1f, me at %.1f %.1f %.1f, %.1f blocks", g.getX(), g.getY(),
                    g.getZ(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), Math.sqrt(dx * dx + dz * dz)));
        });
        queueUntil("companionscenes.g1_heart", 200,
                mc -> grill24.fishtastic.client.companion.ClientCompanions.heartsSpawned > before[1],
                () -> "hearts " + grill24.fishtastic.client.companion.ClientCompanions.heartsSpawned);
        queue(2, mc -> {
            // The heart's bubbles are in open air here (vanilla's would be gone after one tick).
            var bubbles = grill24.fishtastic.client.companion.ClientCompanions.devHeartBubbles;
            long inAir = bubbles.stream().filter(b -> {
                net.minecraft.world.phys.AABB box = b.getBoundingBox();
                return mc.level.getFluidState(BlockPos.containing(box.getCenter())).isEmpty();
            }).count();
            check("companionscenes.heart_bubbles_in_air", bubbles.size() == 32 && inAir == bubbles.size(),
                    bubbles.size() + " bubbles, " + inAir + " in air");
            shoot(mc, "g1_heart");
        });
        // Vanilla's bubble removes itself on its first tick out of water; these must outlive that.
        // Later they pop as they reach their height (0.5 up, at a random rise), so a few have gone by 1 s.
        queue(1, mc -> {
            var bubbles = grill24.fishtastic.client.companion.ClientCompanions.devHeartBubbles;
            long alive = bubbles.stream().filter(net.minecraft.client.particle.Particle::isAlive).count();
            check("companionscenes.heart_bubbles_live_in_air", alive == bubbles.size() && !bubbles.isEmpty(),
                    alive + " of " + bubbles.size() + " alive 3 ticks after the heart");
        });
        queue(17, mc -> {
            var bubbles = grill24.fishtastic.client.companion.ClientCompanions.devHeartBubbles;
            long alive = bubbles.stream().filter(net.minecraft.client.particle.Particle::isAlive).count();
            note("heart bubbles alive after 1 s: " + alive + " of " + bubbles.size());
            shoot(mc, "g1_heart_later");
        });
        queue(20, mc -> {
            String sc = localScene();
            check("companionscenes.g1_on_path", sc.contains("leader SCENE") && !sc.contains("left:"), sc);
            shoot(mc, "g1_ring");
        });
        queueUntil("companionscenes.g1_done", 300, mc -> "RING done".equals(
                grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd), CompanionScenes::localScene);
        // A packet 120 ticks late joins mid-path, past the greeting.
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost scene g2 120")));
        queueUntil("companionscenes.late_joins_mid_path", 60, mc -> {
            String sc = grill24.fishtastic.client.companion.ClientCompanions.devLocalScene();
            if (sc == null || !sc.startsWith("EIGHT")) return false;
            int t = Integer.parseInt(sc.replaceAll(".* t(\\d+) .*", "$1"));
            return t >= 120;
        }, CompanionScenes::localScene);
        // The ghost walks off: the scene ends early.
        queue(10, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost walk leave")));
        queueUntil("companionscenes.walk_off_ends_it", 200, mc -> {
            String end = grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd;
            return grill24.fishtastic.client.companion.ClientCompanions.devLocalScene() == null && end != null
                    && end.startsWith("EIGHT") && !end.endsWith("done");
        }, () -> "last ended: " + grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd);
        queue(1, mc -> {
            check("companionscenes.both_started_here", grill24.fishtastic.client.companion.ClientCompanions.scenesStarted
                    >= before[0] + 3, "scenes started " + grill24.fishtastic.client.companion.ClientCompanions.scenesStarted);
            server(mc, s -> run(s, "fishtastic companion ghost remove"));
        });
        queue(20, mc -> server(mc, CompanionScenes::clearParties));
    }

    /**
     * Remote-party level of detail (audit §6.2): the ghost owner, still a tracked player, has its
     * party dropped once it stands past the drop range, kept between the two ranges, and rebuilt
     * when it comes back.
     */
    static void queueCompanionLodScene() {
        String ghost = grill24.fishtastic.server.CompanionGhost.NAME;
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, asMe(mc) + "fishtastic companion ghost add fishtastic:discus");
        }));
        queueUntil("companionlod.ghost_party", 300, mc -> {
            Player g = ghostPlayer(mc);
            return g != null && !grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).isEmpty();
        }, () -> "ghost " + (ghostPlayer(Minecraft.getInstance()) != null));
        java.util.function.Supplier<String> where = () -> {
            Minecraft mc = Minecraft.getInstance();
            Player g = ghostPlayer(mc);
            return g == null ? "ghost not tracked" : String.format(Locale.ROOT, "ghost %.1f blocks off, %d units",
                    g.distanceTo(mc.player), grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).size());
        };
        // 76 blocks: past the drop range, inside the 6-chunk view the ghost stays tracked in.
        queue(1, mc -> server(mc, s -> {
            run(s, asMe(mc) + "fishtastic companion ghost walk stand");
            run(s, asMe(mc) + "tp " + ghost + " ~76 ~ ~");
        }));
        queueUntil("companionlod.far_party_dropped", 100, mc -> {
            Player g = ghostPlayer(mc);
            return g != null && g.distanceTo(mc.player) > 72
                    && grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).isEmpty();
        }, where);
        // Back to 68 blocks, between the ranges: a dropped party is not rebuilt there...
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "tp " + ghost + " ~68 ~ ~")));
        queue(40, mc -> {
            Player g = ghostPlayer(mc);
            check("companionlod.between_stays_dropped", g != null && g.distanceTo(mc.player) > 64
                    && grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).isEmpty(), where.get());
        });
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "tp " + ghost + " ~4 ~ ~")));
        queueUntil("companionlod.near_party_rebuilt", 100, mc -> {
            Player g = ghostPlayer(mc);
            return g != null && !grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).isEmpty();
        }, where);
        // ...and a running one is kept there.
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "tp " + ghost + " ~68 ~ ~")));
        queue(40, mc -> {
            Player g = ghostPlayer(mc);
            check("companionlod.between_stays_running", g != null && g.distanceTo(mc.player) > 64
                    && !grill24.fishtastic.client.companion.ClientCompanions.companionsOf(g).isEmpty(), where.get());
            server(mc, s -> run(s, "fishtastic companion ghost remove"));
        });
        queue(20, mc -> server(mc, CompanionScenes::clearParties));
    }

    /** The scene greeting's length, ticks (CrossScene.GREET_TICKS). */
    private static final class CrossSceneTicks {
        static final int GREET = grill24.fishsim.companion.CrossScene.GREET_TICKS;
    }

    private static void paradeSetup(Minecraft mc) {
        mc.options.hideGui = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        server(mc, s -> {
            run(s, "gamemode creative @a");
            run(s, "time set 6000");
            run(s, "kill @e[type=!player]");
            run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                    origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 0.5, 180f, 0f));
        });
    }


    /** The inventory-based companion menu, exercised through real container clicks. */
    static void queueCompanionBondScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> server(mc, s -> {
            run(s, "fishtastic companion unlock @p fishtastic:bluegill");
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.CompanionStorage.clear(player);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, fish("bluegill", 25f).copyWithCount(2));
                player.getInventory().setItem(1, fish("bluegill", 31f));
                ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
                FishtasticItemData.setBundleContents(pile, new BundleContents(List.of(
                        net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(fish("bluegill", 40f)),
                        net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(fish("bluegill", 45f)))));
                player.getInventory().setItem(2, pile);
                // Never unlocked here: the refusal flash (1d) for a catch-gated and a quest-only species.
                player.getInventory().setItem(4, fish("oscar", 30f));
                player.getInventory().setItem(5, fish("molten_moorish_idol", 30f));
            }
        }));
        queue(15, mc -> grill24.fishtastic.client.CompanionScreen.open(fish("bluegill", 25f)));
        queue(20, mc -> {
            check("bond.inventoryScreen", mc.screen instanceof grill24.fishtastic.client.CompanionScreen, "screen=" + mc.screen);
            check("bond.slotCount", mc.player.containerMenu.slots.size() == 39, "slots=" + mc.player.containerMenu.slots.size());
            screenshot(mc, "companionbond", "inventory_picker");
        });
        // Menu slots 0-2 are the party; the hotbar starts at 30 (party spec §5.3).
        queue(1, mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 30, 0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player));
        queue(15, mc -> {
            check("bond.equipped", !grill24.fishtastic.companion.CompanionStorage.get(mc.player, 0).isEmpty(), "");
            check("bond.oneTaken", mc.player.getInventory().getItem(0).getCount() == 1, "");
            screenshot(mc, "companionbond", "equipped");
        });
        queue(1, mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 31, 0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player));
        queue(15, mc -> {
            check("bond.second", grill24.fishtastic.util.ItemSizeHelper.getSize(grill24.fishtastic.companion.CompanionStorage.get(mc.player, 1)) == 31f, "");
            screenshot(mc, "companionbond", "pair");
        });
        queue(1, mc -> FishtasticTransport.sendToServer(
                new grill24.fishtastic.network.RenameCompanionPacket(0, "Bubbles")));
        queue(15, mc -> {
            check("bond.renamed", grill24.fishtastic.companion.CompanionStorage.get(mc.player, 0).getHoverName().getString().equals("Bubbles"), "");
            screenshot(mc, "companionbond", "renamed");
        });
        // Party 1d (§5.5): the pencil under slot 2 opens its name row; moving that fish closes it unsaved.
        queue(1, mc -> {
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) screen.openEditor(1);
        });
        queue(5, mc -> {
            check("bond.editorOpens", mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen && screen.editingSlot() == 1,
                    "screen=" + mc.screen);
            screenshot(mc, "companionbond", "pencil_open");
        });
        queue(1, mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 1, 0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player));
        queue(15, mc -> check("bond.editorClosesOnMove", mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen
                && screen.editingSlot() == -1 && grill24.fishtastic.companion.CompanionStorage.get(mc.player, 1).isEmpty(), ""));
        // The pile strip puts the chosen fish on the cursor; the pile keeps its other fish.
        queue(1, mc -> {
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) screen.showPiles(true);
        });
        queue(5, mc -> {
            screenshot(mc, "companionbond", "pile_strip");
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) screen.clickPile(0);
        });
        queue(15, mc -> {
            ItemStack carried = mc.player.containerMenu.getCarried();
            check("bond.pileToCursor", carried.is(FishtasticItems.BLUEGILL.value()) && carried.getCount() == 1, "carried=" + carried);
            check("bond.pileKeepsRest", mc.player.getInventory().getItem(2).is(FishtasticItems.BLUEGILL.value()),
                    "slot2=" + mc.player.getInventory().getItem(2));
            screenshot(mc, "companionbond", "pile_cursor");
        });
        // Put it down in an empty inventory slot (menu slot 3 is inventory row 1, column 1).
        queue(1, mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 3, 0,
                net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player));
        // A fish that isn't bonded: shift-click flashes its own slot and the party slot it was headed for.
        queue(10, mc -> {
            check("bond.lockedPrecondition", !grill24.fishtastic.client.QuestClientCache.isBondUnlocked(Ids.of("fishtastic", "oscar"))
                    && !grill24.fishtastic.client.QuestClientCache.isBondUnlocked(Ids.of("fishtastic", "molten_moorish_idol")), "");
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) {
                screen.showPiles(false);
                screen.clickSlot(34, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
            }
        });
        queue(3, mc -> {
            boolean flashing = mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen
                    && screen.isFlashing(1) && screen.isFlashing(34);
            check("bond.lockedFlashes", flashing, "screen=" + mc.screen);
            screenshot(mc, "companionbond", "locked_flash");
        });
        queue(15, mc -> {
            check("bond.lockedRefused", grill24.fishtastic.companion.CompanionStorage.get(mc.player, 1).isEmpty()
                    && mc.player.getInventory().getItem(4).getCount() == 1, "");
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) {
                screen.clickSlot(35, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
            }
        });
        queue(3, mc -> screenshot(mc, "companionbond", "locked_flash_quest"));
        queue(1, mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player));
        queue(1, mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 1, 0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player));
        queue(15, mc -> {
            check("bond.returned", grill24.fishtastic.companion.CompanionStorage.party(mc.player).isEmpty(), "");
            screenshot(mc, "companionbond", "returned");
            mc.player.closeContainer();
        });
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) player.getInventory().clearContent();
        }));
    }

    static void queueCompanionSimScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true));
        // One fish is one body now (party spec §3): a small bluegill is a small solo, not a trio,
        // and every fish is drawn at its true size (D11).
        String[][] fish = {{"bluegill", "9", "small_solo"}, {"bluegill", "25", "solo"}, {"starfish", "20", "benthic"},
                {"garden_eel", "40", "anchored"}, {"glass_squid", "20", "drift"}, {"giant_manta_ray", "150", "glide"}};
        queue(1, mc -> server(mc, CompanionScenes::clearParties));
        for (String[] f : fish) {
            // Slot 1 (the lead) each time, so each fish replaces the last: one companion on screen.
            queue(1, mc -> server(mc, s -> run(s, "fishtastic companion give @p fishtastic:" + f[0] + " " + f[1] + " 1")));
            queue(60, mc -> {
                var local = grill24.fishtastic.client.companion.ClientCompanions.local();
                check("companionsim." + f[2] + ".tracked", local != null && local.agent() != null,
                        "local=" + local);
                check("companionsim." + f[2] + ".oneBody",
                        grill24.fishtastic.client.companion.ClientCompanions.localCompanions().size() == 1
                                && local != null && local.bodyCount() == 1, "local=" + (local == null ? null : local.partyLine()));
                companionLog(f[2] + " first person");
                screenshot(mc, "companionsim", f[2] + "_fp");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            queue(30, mc -> {
                companionLog(f[2] + " third person");
                screenshot(mc, "companionsim", f[2] + "_tp");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
        }
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion give @p fishtastic:bluegill 25 1")));
        queue(60, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devFake("hostile"));
        queue(120, mc -> {
            companionLog("fake hostile close");
            screenshot(mc, "companionsim", "hostile_fp");
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devFake("clear"));
        // The sampling cost while walking (plan §7 risk 3): 4 blocks/s for 10 s across fresh
        // ground, so the block window re-centres several times.
        queue(40, mc -> {
            grill24.fishtastic.client.companion.ClientCompanionsDev.devResetCost();
            companionLog("walk start");
        });
        for (int i = 0; i < 200; i++) {
            queue(1, mc -> mc.player.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() - 0.2));
        }
        queue(1, mc -> {
            companionLog("walk end");
            screenshot(mc, "companionsim", "walk_fp");
        });
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false);
            server(mc, CompanionScenes::clearParties);
        });
    }

    @org.jetbrains.annotations.Nullable
    /** Where the local member of {@code species} is: its unit and index, or null. */
    private static Object[] memberOf(String species) {
        for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
            for (int i = 0; i < c.fishCount(); i++) if (c.memberSpecies(i).equals(species)) return new Object[]{c, i};
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    /** {@code species}' own mood (a mixed shoal's members each have one), or null when it isn't stepping yet. */
    private static grill24.fishsim.companion.CompanionAgent.MoodLevels moodOf(String species) {
        Object[] m = memberOf(species);
        return m == null ? null : ((grill24.fishtastic.client.companion.ClientCompanion) m[0]).memberMood((Integer) m[1]);
    }

    @org.jetbrains.annotations.Nullable
    private static grill24.fishsim.companion.Habitat.Climate climateOf(String species) {
        Object[] m = memberOf(species);
        return m == null ? null : ((grill24.fishtastic.client.companion.ClientCompanion) m[0]).memberHabitat((Integer) m[1]).climate();
    }

    @org.jetbrains.annotations.Nullable
    private static grill24.fishsim.companion.PoseHint poseOf(String species) {
        Object[] m = memberOf(species);
        return m == null ? null : ((grill24.fishtastic.client.companion.ClientCompanion) m[0]).memberPose((Integer) m[1]);
    }

    private static String moods() {
        StringBuilder b = new StringBuilder();
        for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
            for (int i = 0; i < c.fishCount(); i++) {
                var m = c.memberMood(i);
                if (m == null) continue;
                b.append(c.memberSpecies(i)).append(' ').append(c.memberHabitat(i).climate()).append(String.format(Locale.ROOT,
                        ": cold %.2f glum %.2f glee %.2f sleepy %.2f moon %.2f%s pose %s; ", m.cold(), m.glum(), m.glee(),
                        m.sleepy(), m.moon(), m.warmed() ? " warmed" : "", c.memberPose(i)));
            }
        }
        var lead = grill24.fishtastic.client.companion.ClientCompanions.local();
        if (lead != null) b.append(lead.ambient().weather()).append(' ').append(lead.ambient().temperature())
                .append(' ').append(lead.ambient().dayPart()).append(" moon ").append(lead.ambient().moonPhase())
                .append(" warm ").append(lead.ambient().warmth().size());
        return b.toString();
    }

    private static boolean anyMood(grill24.fishsim.companion.CompanionAgent.MoodLevels m) {
        return m.cold() > 0 || m.glum() > 0 || m.glee() > 0 || m.sleepy() > 0 || m.moon() > 0;
    }

    /**
     * Phase 5 moods (docs/fish-companions.md, spec §6) from the real world: a mixed
     * party (temperate bluegill, cold-water arctic char, Nether discus) in plains, then snow
     * (/fillbiome), a campfire, rain, a full-moon midnight with a parrotfish, and the dev trigger.
     */
    /**
     * Phase 5 moods (docs/fish-companions.md, spec §6) from the real world: a mixed
     * party (temperate bluegill, cold-water arctic char, Nether discus) in plains, then snow
     * (/fillbiome), a campfire, rain, a full-moon midnight with a parrotfish, and the dev trigger.
     */
    static void queueCompanionParticlesScene() {
        queue(1, CompanionScenes::paradeSetup);
        var particles = new grill24.fishtastic.client.companion.CompanionParticles();
        var saved = new net.minecraft.server.level.ParticleStatus[1];
        long[] decreasedAfter = new long[1];
        var glintBlock = new BlockPos[1];
        var glintOriginal = new net.minecraft.world.level.block.state.BlockState[1];
        queue(1, mc -> {
            saved[0] = mc.options.particles().get();
            mc.options.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);
            var p = mc.player.getEyePosition().add(2, 0, 0);
            particles.emit(mc.level, java.util.List.of(new grill24.fishsim.companion.Emote(
                    grill24.fishsim.companion.Emote.Kind.NOTE,
                    new grill24.fishsim.companion.Vec(p.x, p.y, p.z), .5f)));
            check("companionparticles.minimal", particles.spawned() == 0, "spawned=" + particles.spawned());
            mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
            var c = mc.gameRenderer.getMainCamera().position();
            particles.emit(mc.level, java.util.List.of(new grill24.fishsim.companion.Emote(
                    grill24.fishsim.companion.Emote.Kind.NOTE,
                    new grill24.fishsim.companion.Vec(c.x, c.y, c.z), .5f)));
            check("companionparticles.near_field", particles.spawned() == 0, "spawned=" + particles.spawned());
        });
        for (var kind : grill24.fishsim.companion.Emote.Kind.values()) queue(24, mc -> {
            var p = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2));
            if (kind == grill24.fishsim.companion.Emote.Kind.GLINT) {
                glintBlock[0] = BlockPos.containing(p);
                glintOriginal[0] = mc.level.getBlockState(glintBlock[0]);
                mc.level.setBlockAndUpdate(glintBlock[0], Blocks.GLOWSTONE.defaultBlockState());
                p = Vec3.atCenterOf(glintBlock[0]);
            }
            long before = particles.spawned();
            var beat = java.util.List.of(new grill24.fishsim.companion.Emote(kind,
                    new grill24.fishsim.companion.Vec(p.x, p.y, p.z), .75f));
            particles.emit(mc.level, beat);
            long after = particles.spawned();
            particles.emit(mc.level, beat);
            check("companionparticles." + kind, after > before && particles.spawned() == after, "spawned=" + after);
            if (kind == grill24.fishsim.companion.Emote.Kind.GLINT)
                check("companionparticles.glint_outside_opaque_light", particles.lastSpawnPosition() != null
                        && particles.lastSpawnPosition().y() > glintBlock[0].getY() + 1,
                        "actual spawn=" + particles.lastSpawnPosition());
            screenshot(mc, "companionparticles", kind.name().toLowerCase(Locale.ROOT));
        });
        queue(24, mc -> {
            if (glintBlock[0] != null) mc.level.setBlockAndUpdate(glintBlock[0], glintOriginal[0]);
            mc.options.particles().set(net.minecraft.server.level.ParticleStatus.DECREASED);
            var p = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2));
            var beat = java.util.List.of(new grill24.fishsim.companion.Emote(
                    grill24.fishsim.companion.Emote.Kind.NOTE,
                    new grill24.fishsim.companion.Vec(p.x, p.y, p.z), .5f));
            particles.emit(mc.level, beat);
            decreasedAfter[0] = particles.spawned();
        });
        queue(10, mc -> {
            var p = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2));
            particles.emit(mc.level, java.util.List.of(new grill24.fishsim.companion.Emote(
                    grill24.fishsim.companion.Emote.Kind.NOTE,
                    new grill24.fishsim.companion.Vec(p.x, p.y, p.z), .5f)));
            check("companionparticles.decreased_rate", particles.spawned() == decreasedAfter[0], "spawned=" + particles.spawned());
            mc.options.particles().set(saved[0]);
        });
    }

    static void queueCompanionWorldScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> {
            var identity = new org.joml.Matrix4f();
            var left = grill24.fishtastic.client.renderer.CompanionFeatureLean.toward(identity, new grill24.fishsim.companion.Vec(-1, 0, 0));
            var right = grill24.fishtastic.client.renderer.CompanionFeatureLean.toward(identity, new grill24.fishsim.companion.Vec(1, 0, 0));
            check("companionworld.percher.opposite_lean", left.roll() > 0 && right.roll() < 0, "left=" + left + " right=" + right);
            for (int frame = 0; frame < 4; frame++) {
                var matrix = new org.joml.Matrix4f().rotateY(frame * .8f).rotateX(frame * .2f);
                if (frame % 2 == 1) matrix.scale(-1, -1, 1);
                for (int side : new int[]{-1, 1}) {
                    var target = new grill24.fishsim.companion.Vec(side, 0, .3);
                    var lean = grill24.fishtastic.client.renderer.CompanionFeatureLean.toward(matrix, target);
                    var up = matrix.transformDirection(new org.joml.Vector3f(0, 1, 0));
                    var tip = new org.joml.Matrix4f(matrix).rotateX((float) Math.toRadians(lean.pitch()))
                            .rotateZ((float) Math.toRadians(lean.roll())).transformDirection(new org.joml.Vector3f(0, 1, 0));
                    check("companionworld.percher.frame" + frame + ".side" + side,
                            tip.sub(up).dot((float) target.x(), (float) target.y(), (float) target.z()) > 0,
                            "source-directed lean=" + lean);
                }
            }
        });
        queue(1, mc -> {
            mc.options.hideGui = true;
            server(mc, s -> run(s, asMe(mc) + "tp @s ~ ~ ~ 180 22"));
        });
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            // Equip through the shared API rather than the dev-only command so the same scene runs
            // against a production install (the command tree excludes /fishtastic companion there).
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.equip(player, 0, fish("bluegill", 25f));
            }
            run(s, "weather clear 99999");
        }));
        queue(100, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false));
        queue(1, mc -> {
            BlockPos pos = mc.player.blockPosition().offset(3, 0, 0);
            var songs = mc.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG);
            var song = songs.listElements().findFirst().orElseThrow();
            mc.level.levelEvent(1010, pos, songs.getId(song.value()));
            check("companionworld.jukebox_event_start",
                    grill24.fishtastic.client.companion.CompanionMusic.features(mc.level, mc.level.getGameTime()).stream()
                            .anyMatch(f -> f.id() == pos.asLong()), "vanilla event 1010");
            mc.level.levelEvent(1011, pos, 0);
            check("companionworld.jukebox_event_stop",
                    grill24.fishtastic.client.companion.CompanionMusic.features(mc.level, mc.level.getGameTime()).stream()
                            .noneMatch(f -> f.id() == pos.asLong()), "vanilla event 1011");
            mc.level.setBlockAndUpdate(pos, Blocks.NOTE_BLOCK.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.NoteBlock.NOTE, 18));
            mc.level.blockEvent(pos, Blocks.NOTE_BLOCK, 0, 0);
            check("companionworld.note_event",
                    grill24.fishtastic.client.companion.CompanionMusic.features(mc.level, mc.level.getGameTime()).stream()
                            .anyMatch(f -> f.id() == pos.asLong() && f.music().pitch() == .75f), "vanilla block event");
            mc.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            check("companionworld.note_expiry",
                    grill24.fishtastic.client.companion.CompanionMusic.features(mc.level, mc.level.getGameTime() + 13).stream()
                            .noneMatch(f -> f.id() == pos.asLong()), "note expires after pulse");
            grill24.fishtastic.client.companion.CompanionMusic.jukebox(mc.level, pos, 2);
            check("companionworld.song_expiry",
                    grill24.fishtastic.client.companion.CompanionMusic.features(mc.level, mc.level.getGameTime() + 2).isEmpty(), "song length honored");
            grill24.fishtastic.client.companion.CompanionMusic.note(mc.level, pos, .5f);
            grill24.fishtastic.client.companion.CompanionMusic.clear();
            check("companionworld.music_clear",
                    grill24.fishtastic.client.companion.CompanionMusic.features(mc.level, mc.level.getGameTime()).isEmpty(), "disconnect reset hook clears events");
        });
        for (String feature : java.util.List.of("jukebox", "notes", "water", "light", "warm")) {
            if (feature.equals("warm")) {
                queue(1, mc -> server(mc, s -> {
                    run(s, asMe(mc) + "fill ~-6 ~ ~-6 ~6 ~5 ~6 minecraft:air");
                    run(s, asMe(mc) + "fillbiome ~-12 ~-3 ~-12 ~12 ~8 ~12 minecraft:snowy_plains");
                    run(s, "time set 6000");
                }));
                queue(220, mc -> {
                    var mood = moodOf("bluegill");
                    check("companionworld.warm.starts_cold", mood != null && mood.cold() > .5f, moods());
                    screenshot(mc, "companionworld", "warm-before-fire");
                });
            }
            queue(1, mc -> grill24.fishtastic.client.companion.CompanionMusic.clear());
            queue(1, mc -> server(mc, s -> {
                run(s, "time set " + (feature.equals("light") ? "18000" : "6000"));
                run(s, asMe(mc) + "fill ~-6 ~ ~-6 ~6 ~5 ~6 minecraft:air");
                if (feature.equals("jukebox")) run(s, asMe(mc) + "setblock ~ ~ ~-2 minecraft:jukebox");
                if (feature.equals("notes")) run(s, asMe(mc) + "setblock ~ ~ ~-2 minecraft:note_block[note=18]");
                // Same placement the dev-only /fishtastic companion feature command performs, so the
                // scene also runs against a production install.
                if (!feature.equals("jukebox") && !feature.equals("notes")) {
                    ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                    BlockPos at = player.blockPosition().relative(player.getDirection(),
                            feature.equals("light") ? 5 : feature.equals("warm") ? 4 : 2);
                    var state = switch (feature) {
                        case "water" -> Blocks.WATER.defaultBlockState();
                        case "light" -> Blocks.TORCH.defaultBlockState();
                        default -> Blocks.CAMPFIRE.defaultBlockState();
                    };
                    player.level().setBlockAndUpdate(at, state);
                }
                if (feature.equals("water")) {
                    run(s, asMe(mc) + "setblock ~1 ~ ~-2 minecraft:stone");
                    run(s, asMe(mc) + "setblock ~-1 ~ ~-2 minecraft:stone");
                    run(s, asMe(mc) + "setblock ~ ~ ~-1 minecraft:stone");
                    run(s, asMe(mc) + "setblock ~ ~ ~-3 minecraft:stone");
                }
            }));
            queue(1, mc -> {
                if (feature.equals("jukebox") || feature.equals("notes")) {
                    grill24.fishtastic.client.companion.CompanionFeatureDev.music(feature);
                }
            });
            queue(40, mc -> {
                if (feature.equals("light")) {
                    var c = grill24.fishtastic.client.companion.ClientCompanions.local();
                    check("companionworld.light.settled_darkness", c != null && c.ambient().darkness() > .45f,
                            c == null ? "no companion" : "darkness=" + c.ambient().darkness());
                }
            });
            boolean[] reacted = {false};
            boolean[] waterContact = {false};
            String[] splashPosition = {"no source-specific SPLASH observed"};
            queue(1, mc -> {
                int[] clock = {0};
                tickProbe = client -> {
                    int tick = clock[0]++;
                    if (tick >= 180) { tickProbe = null; return; }
                    if (feature.equals("notes") && tick % 10 == 0)
                        grill24.fishtastic.client.companion.CompanionFeatureDev.music("notes");
                    for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
                        if (c.agent() == null || !"NOTICE".equals(c.agent().goal())) continue;
                        var noticed = c.agent().noticedFeature();
                        int distance = feature.equals("light") ? 5 : feature.equals("warm") ? 4 : 2;
                        BlockPos source = client.player.blockPosition().relative(client.player.getDirection(), distance);
                        var expectedKind = feature.equals("jukebox") || feature.equals("notes") ? grill24.fishsim.companion.WorldFeature.Kind.MUSIC
                                : feature.equals("water") ? grill24.fishsim.companion.WorldFeature.Kind.WATER_SURFACE
                                : feature.equals("light") ? grill24.fishsim.companion.WorldFeature.Kind.LIGHT
                                : grill24.fishsim.companion.WorldFeature.Kind.WARMTH;
                        if (noticed == null || noticed.id() != source.asLong() || noticed.kind() != expectedKind) continue;
                        if (feature.equals("water")) for (var emote : c.agent().emotes()) {
                            if (emote.kind() != grill24.fishsim.companion.Emote.Kind.SPLASH) continue;
                            var pos = emote.pos();
                            boolean wet = client.level.getFluidState(BlockPos.containing(pos.x(), pos.y(), pos.z()))
                                    .is(net.minecraft.tags.FluidTags.WATER);
                            splashPosition[0] = "splash=" + pos + " real_water=" + wet;
                            if (wet) waterContact[0] = true;
                        }
                        if (feature.equals("notes") && noticed.music().source() != grill24.fishsim.companion.WorldFeature.Source.NOTE_BLOCK) continue;
                        if (feature.equals("jukebox") && noticed.music().source() != grill24.fishsim.companion.WorldFeature.Source.JUKEBOX) continue;
                        var pose = c.agent().poseHint();
                        boolean expected = feature.equals("jukebox") || feature.equals("notes")
                                ? pose == grill24.fishsim.companion.PoseHint.DANCE
                                : pose == grill24.fishsim.companion.PoseHint.NOSE
                                    || feature.equals("water") && pose == grill24.fishsim.companion.PoseHint.DIP;
                        if (expected) reacted[0] = true;
                    }
                    if (tick < 80 && tick % 4 == 0)
                        screenshot(client, "companionworld", feature + "-frame" + String.format(Locale.ROOT, "%02d", tick / 4));
                };
            });
            queue(182, mc -> {
                tickProbe = null;
                check("companionworld." + feature + ".reaction", reacted[0],
                        grill24.fishtastic.client.companion.ClientCompanionsDev.debugState());
                if (feature.equals("water"))
                    check("companionworld.water.physical_contact", waterContact[0], splashPosition[0]);
                if (feature.equals("warm")) {
                    var mood = moodOf("bluegill");
                    check("companionworld.warm.basked", mood != null && mood.warmed(), moods());
                }
                screenshot(mc, "companionworld", feature);
                grill24.fishtastic.client.companion.CompanionMusic.stop(mc.level, mc.player.blockPosition().relative(mc.player.getDirection(), 2));
                grill24.fishtastic.client.companion.ClientCompanionsDev.devMood("off");
            });
            queue(100, mc -> {});
        }
        queue(1, mc -> server(mc, s -> {
            run(s, asMe(mc) + "fill ~-6 ~ ~-6 ~6 ~5 ~6 minecraft:air");
            run(s, asMe(mc) + "fillbiome ~-12 ~-3 ~-12 ~12 ~8 ~12 minecraft:plains");
        }));
        queue(220, mc -> {});
        for (String mood : java.util.List.of("sleepy", "glum")) {
            boolean[] visiblePose = {false};
            queue(1, mc -> {
                grill24.fishtastic.client.companion.ClientCompanionsDev.devMood(mood);
                int[] clock = {0};
                tickProbe = client -> {
                    int tick = clock[0]++;
                    if (tick >= 140) { tickProbe = null; return; }
                    if (poseOf("bluegill") == (mood.equals("sleepy") ? grill24.fishsim.companion.PoseHint.SLEEPY
                            : grill24.fishsim.companion.PoseHint.DROOP)) visiblePose[0] = true;
                    if (tick >= 60 && tick < 140 && tick % 4 == 0)
                        screenshot(client, "companionworld", mood + "-frame" + String.format(Locale.ROOT, "%02d", (tick - 60) / 4));
                };
            });
            queue(142, mc -> {
                tickProbe = null;
                check("companionworld." + mood + ".pose", visiblePose[0], moods());
                grill24.fishtastic.client.companion.ClientCompanionsDev.devMood("off");
            });
        }
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server(mc, s -> {
                clearParties(s);
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    grill24.fishtastic.companion.Companions.equip(player, 0, fish("garden_eel", 40f));
                }
                run(s, "time set 18000");
            });
        });
        queue(140, mc -> {});
        for (int side : new int[]{-1, 1}) {
            queue(1, mc -> server(mc, s -> {
                run(s, asMe(mc) + "fill ~-6 ~ ~-6 ~6 ~5 ~6 minecraft:air");
                run(s, asMe(mc) + "setblock ~" + (side * 4) + " ~ ~-3 minecraft:sea_lantern");
            }));
            boolean[] watched = {false};
            for (int frame = 0; frame < 8; frame++) {
                int number = frame;
                queue(10, mc -> {
                    var c = grill24.fishtastic.client.companion.ClientCompanions.local();
                    if (c != null && c.agent() != null && c.agent().poseHint() == grill24.fishsim.companion.PoseHint.WATCH
                            && c.agent().noticedFeature() != null
                            && c.agent().noticedFeature().kind() == grill24.fishsim.companion.WorldFeature.Kind.LIGHT
                            && c.agent().noticedFeature().id() == mc.player.blockPosition().offset(side * 4, 0, -3).asLong()) watched[0] = true;
                    screenshot(mc, "companionworld", "percher-" + (side < 0 ? "left" : "right") + "-frame" + number);
                });
            }
            queue(1, mc -> check("companionworld.percher." + (side < 0 ? "left" : "right") + ".watch", watched[0],
                    grill24.fishtastic.client.companion.ClientCompanionsDev.debugState()));
        }
        queue(1, mc -> server(mc, s -> {
            run(s, asMe(mc) + "fill ~-6 ~ ~-6 ~6 ~5 ~6 minecraft:air");
            run(s, "time set 6000");
            run(s, asMe(mc) + "fillbiome ~-12 ~-3 ~-12 ~12 ~8 ~12 minecraft:plains");
            clearParties(s);
        }));
        queue(1, mc -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); });
    }

    /** Equal-world A/B: each old sampler and the new shared index see identical owner paths. */
    static void queueCompanionWorldCostScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.CompanionWorldIndexChecks.run(mc.level, mc.player.blockPosition())
                .forEach((name, detail) -> check("companionworldcost." + name, detail.isEmpty(), detail)));
        for (String terrain : java.util.List.of("superflat", "village_fixture", "lush_cave_fixture", "ocean_shore_fixture", "nether_fixture")) {
        queue(1, mc -> server(mc, s -> {
            run(s, asMe(mc) + "fill ~-10 ~-1 ~-10 ~10 ~4 ~10 minecraft:air");
            run(s, asMe(mc) + "fill ~-10 ~-1 ~-10 ~10 ~-1 ~10 minecraft:stone");
            String biome = switch (terrain) {
                case "lush_cave_fixture" -> "lush_caves";
                case "ocean_shore_fixture" -> "ocean";
                case "nether_fixture" -> "nether_wastes";
                default -> "plains";
            };
            run(s, asMe(mc) + "fillbiome ~-12 ~-3 ~-12 ~12 ~8 ~12 minecraft:" + biome);
            String block = switch (terrain) {
                case "village_fixture" -> "minecraft:oak_planks";
                case "lush_cave_fixture" -> "minecraft:moss_block";
                case "ocean_shore_fixture" -> "minecraft:water";
                case "nether_fixture" -> "minecraft:netherrack";
                default -> "minecraft:stone";
            };
            run(s, asMe(mc) + "fill ~2 ~ ~-8 ~8 ~ ~8 " + block);
            if (terrain.equals("village_fixture")) {
                run(s, asMe(mc) + "setblock ~2 ~1 ~ minecraft:campfire");
                run(s, asMe(mc) + "setblock ~4 ~1 ~ minecraft:lantern");
            } else if (terrain.equals("lush_cave_fixture")) {
                run(s, asMe(mc) + "fill ~2 ~3 ~-8 ~8 ~3 ~8 minecraft:glow_lichen[down=true]");
                run(s, asMe(mc) + "fill ~2 ~ ~2 ~4 ~ ~4 minecraft:water");
            } else if (terrain.equals("nether_fixture")) {
                run(s, asMe(mc) + "fill ~2 ~ ~2 ~4 ~ ~4 minecraft:lava");
                run(s, asMe(mc) + "setblock ~6 ~1 ~ minecraft:shroomlight");
            }
        }));
        queue(100, mc -> {});
        for (String scenario : java.util.List.of("standing", "walking", "sprinting", "party3", "secondowner")) {
            var legacy = new grill24.fishtastic.client.companion.CompanionAmbient.Sampler[scenario.equals("party3") ? 3 : scenario.equals("secondowner") ? 2 : 1];
            for (int i = 0; i < legacy.length; i++) legacy[i] = new grill24.fishtastic.client.companion.CompanionAmbient.Sampler();
            var index = new grill24.fishtastic.client.companion.WorldFeatureIndex[1];
            long[] totals = new long[8]; // steady old/new reads, old/new peak, old/new worst ns; all-tick index peak/worst ns
            queue(1, mc -> {
                index[0] = new grill24.fishtastic.client.companion.WorldFeatureIndex(mc.level);
                int[] clock = {0};
                tickProbe = client -> {
                    final int tick = clock[0]++;
                    if (tick >= 400) { tickProbe = null; return; }
                    double speed = scenario.equals("walking") ? .215 : scenario.equals("sprinting") ? .28 : 0;
                    double path = tick * speed % 32;
                    var eye = mc.player.getEyePosition().add(path <= 16 ? path - 8 : 24 - path, 0, 0);
                    var owners = new java.util.ArrayList<BlockPos>();
                    long reads = 0, nanos = 0;
                    for (int i = 0; i < legacy.length; i++) {
                        var p = scenario.equals("secondowner") ? eye.add(i * 4, 0, 0) : eye;
                        long r = legacy[i].reads, n = legacy[i].nanos;
                        legacy[i].sampleForCost(mc.level, p);
                        reads += legacy[i].reads - r; nanos += legacy[i].nanos - n;
                        owners.add(BlockPos.containing(p));
                    }
                    index[0].tick(owners);
                    for (BlockPos owner : new java.util.LinkedHashSet<>(owners)) index[0].features(owner, 10);
                    var cost = index[0].cost();
                    totals[6] = Math.max(totals[6], cost.reads());
                    totals[7] = Math.max(totals[7], cost.nanos());
                    if (tick >= 200) {
                        totals[0] += reads; totals[1] += cost.reads();
                        totals[2] = Math.max(totals[2], reads); totals[3] = Math.max(totals[3], cost.reads());
                        totals[4] = Math.max(totals[4], nanos); totals[5] = Math.max(totals[5], cost.nanos());
                    }
                };
            });
            queue(402, mc -> {
                tickProbe = null;
                Fishtastic.LOGGER.info("[selftest] worldcost terrain={} scenario={} old steady_mean={} steady_peak={} steady_worst_us={} index steady_mean={} steady_peak={} steady_worst_us={} discovery_peak={} discovery_worst_us={}",
                        terrain, scenario, totals[0] / 200.0, totals[2], totals[4] / 1000.0, totals[1] / 200.0,
                        totals[3], totals[5] / 1000.0, totals[6], totals[7] / 1000.0);
                check("companionworldcost." + terrain + "." + scenario + ".budget",
                        totals[6] <= grill24.fishtastic.client.companion.WorldFeatureIndex.READ_BUDGET, "peak=" + totals[6]);
                if (scenario.equals("standing") || scenario.equals("party3") || scenario.equals("secondowner"))
                    check("companionworldcost." + scenario + ".zero_standing", totals[1] == 0, "reads=" + totals[1]);
            });
        }
        }
    }

    static void queueCompanionMoodsScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true));
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "weather clear 99999");
            run(s, "fishtastic companion give @p fishtastic:bluegill 25 1");
            run(s, "fishtastic companion give @p fishtastic:arctic_char 30 2");
            run(s, "fishtastic companion give @p fishtastic:discus 20 3");
        }));
        queueUntil("companionmoods.party", 200, mc -> moodOf("bluegill") != null && moodOf("arctic_char") != null
                && moodOf("discus") != null, CompanionScenes::moods);
        queue(1, mc -> check("companionmoods.climates",
                climateOf("bluegill") == grill24.fishsim.companion.Habitat.Climate.TEMPERATE
                        && climateOf("arctic_char") == grill24.fishsim.companion.Habitat.Climate.COLD
                        && climateOf("discus") == grill24.fishsim.companion.Habitat.Climate.HOT, moods()));
        // Plains: the temperate and cold fish content, the Nether fish glum out of the heat.
        queue(200, mc -> {
            var b = moodOf("bluegill");
            var a = moodOf("arctic_char");
            var d = moodOf("discus");
            check("companionmoods.plains", b != null && !anyMood(b) && a != null && !anyMood(a)
                    && d != null && d.glum() > 0.4f, moods());
            companionLog("plains");
            screenshot(mc, "companionmoods", "plains");
        });
        // Snow: the bluegill and the discus shiver; the arctic char doesn't.
        // The flat world's floor is at y -60: keep the box above the build limit (-64).
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "fillbiome ~-12 ~-3 ~-12 ~12 ~8 ~12 minecraft:snowy_plains")));
        // Pose samples per member: the bluegill and discus shiver, the arctic char never does.
        final int[] shivers = {0, 0};
        for (int i = 0; i < 40; i++) {
            queue(5, mc -> {
                if (poseOf("bluegill") == grill24.fishsim.companion.PoseHint.SHIVER) shivers[0]++;
                if (poseOf("arctic_char") == grill24.fishsim.companion.PoseHint.SHIVER) shivers[1]++;
            });
        }
        queue(1, mc -> {
            var b = moodOf("bluegill");
            var a = moodOf("arctic_char");
            var d = moodOf("discus");
            check("companionmoods.snow", b != null && b.cold() > 0.5f && a != null && a.cold() == 0f
                    && d != null && d.cold() > 0.5f, moods());
            check("companionmoods.shiver_pose", shivers[0] > 10 && shivers[1] == 0,
                    "shiver samples bluegill " + shivers[0] + "/40, arctic char " + shivers[1] + "/40 " + moods());
            companionLog("snow");
            screenshot(mc, "companionmoods", "snow");
        });
        // A campfire beside the owner: warm, the shiver goes.
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "setblock ~2 ~ ~ minecraft:campfire")));
        queue(200, mc -> {
            var b = moodOf("bluegill");
            var d = moodOf("discus");
            // In snow the Nether fish was cold, not glum: by the fire it's warm, not glad.
            check("companionmoods.campfire", b != null && b.warmed() && b.cold() < 0.05f && d != null && d.warmed()
                    && d.cold() < 0.05f, moods());
            companionLog("campfire");
            screenshot(mc, "companionmoods", "campfire");
        });
        queue(1, mc -> server(mc, s -> {
            run(s, asMe(mc) + "setblock ~2 ~ ~ minecraft:air");
            run(s, asMe(mc) + "fillbiome ~-12 ~-3 ~-12 ~12 ~8 ~12 minecraft:plains");
            run(s, "weather rain 99999");
        }));
        // Rain in plains: the bluegill glad, the discus glum.
        queue(240, mc -> {
            var b = moodOf("bluegill");
            var d = moodOf("discus");
            check("companionmoods.rain", b != null && b.glee() > 0.5f && b.cold() == 0f && d != null && d.glum() > 0.4f, moods());
            companionLog("rain");
            screenshot(mc, "companionmoods", "rain");
        });
        // A full-moon midnight with a parrotfish (a full-moon species) in slot 3.
        queue(1, mc -> server(mc, s -> {
            run(s, "weather clear 99999");
            run(s, "time set 18000");
            run(s, "fishtastic companion give @p fishtastic:parrotfish 30 3");
        }));
        queue(240, mc -> {
            var p = moodOf("parrotfish");
            var b = moodOf("bluegill");
            var lead = grill24.fishtastic.client.companion.ClientCompanions.local();
            boolean fullMoonNight = lead != null && lead.ambient().fullMoon()
                    && lead.ambient().dayPart() == grill24.fishsim.companion.Ambient.DayPart.NIGHT;
            check("companionmoods.moon", fullMoonNight && p != null && p.moon() > 0.5f && b != null && b.moon() == 0f, moods());
            companionLog("moon");
            screenshot(mc, "companionmoods", "moon");
        });
        // The dev trigger forces a mood on every unit, and "off" gives the world back.
        queue(1, mc -> {
            server(mc, s -> run(s, "time set 6000"));
            grill24.fishtastic.client.companion.ClientCompanionsDev.devMood("sleepy");
        });
        queue(200, mc -> {
            var b = moodOf("bluegill");
            var p = moodOf("parrotfish");
            check("companionmoods.dev_sleepy", b != null && b.sleepy() > 0.5f && p != null && p.sleepy() > 0.5f, moods());
            grill24.fishtastic.client.companion.ClientCompanionsDev.devMood("cold");
        });
        queue(200, mc -> {
            var b = moodOf("bluegill");
            check("companionmoods.dev_cold", b != null && b.cold() > 0.5f, moods());
            screenshot(mc, "companionmoods", "dev_cold");
            grill24.fishtastic.client.companion.ClientCompanionsDev.devMood("off");
        });
        queue(160, mc -> {
            var b = moodOf("bluegill");
            check("companionmoods.dev_off", b != null && !anyMood(b), moods());
        });
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false);
            server(mc, s -> {
                run(s, "weather clear");
                clearParties(s);
            });
        });
    }

    /**
     * The three-slot party (docs/fish-companions.md, D6, D11): a school of three
     * mixed-size bluegill from three slots, then a mixed party of three species (one solo each),
     * in first and third person, with and without the owner sneaking (each fish's name above it).
     */
    /**
     * The three-slot party (docs/fish-companions.md, D6, D11): a school of three
     * mixed-size bluegill from three slots, then a mixed party of three species (one solo each),
     * in first and third person, with and without the owner sneaking (each fish's name above it).
     */
    static void queueCompanionPartyScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true));
        String[][] parties = {
                {"school", "bluegill 9", "bluegill 16", "bluegill 24"},
                {"mixed", "bluegill 12", "starfish 40", "garden_eel 40"},
                // Three ground walkers of different species: leases, the shared hand clock and the
                // ground push all at work, for the party overlay (Phase 3b).
                {"ground", "japanese_spider_crab 30", "common_octopus 40", "willans_chromodoris 6"}};
        // groups and bodies each party makes
        String[][] expect = {{"1", "3"}, {"3", "3"}, {"3", "3"}};
        for (int p = 0; p < parties.length; p++) {
            String[] party = parties[p];
            String[] want = expect[p];
            String name = party[0];
            queue(1, mc -> server(mc, s -> {
                clearParties(s);
                for (int slot = 1; slot <= 3; slot++) {
                    String[] f = party[slot].split(" ");
                    run(s, "fishtastic companion give @p fishtastic:" + f[0] + " " + f[1] + " " + slot);
                }
            }));
            queue(80, mc -> {
                var companions = grill24.fishtastic.client.companion.ClientCompanions.localCompanions();
                int bodies = 0;
                for (var c : companions) bodies += c.bodyCount();
                String lines = String.join("; ", companions.stream()
                        .map(grill24.fishtastic.client.companion.ClientCompanion::partyLine).toList());
                check("companionparty." + name + ".groups", companions.size() == Integer.parseInt(want[0]), lines);
                check("companionparty." + name + ".bodies", bodies == Integer.parseInt(want[1]), lines);
                boolean kinds = name.equals("school")
                        ? companions.size() == 1 && companions.get(0).displayClassName().equals("school of 3")
                        : companions.stream().allMatch(c -> c.bodyCount() == 1);
                check("companionparty." + name + ".kinds", kinds, lines);
                if (name.equals("ground")) {
                    String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
                    check("companionparty.ground.overlay_clock", state.contains("hand clock: shared"), state);
                    check("companionparty.ground.overlay_homes", state.split(" home ", -1).length > 3, state);
                }
                if (name.equals("mixed")) {
                    // The party layer (spec §6.1, Phase 3): the eel's home is the head (D5), and
                    // no two riders share a perch.
                    java.util.Set<grill24.fishsim.companion.PerchBody.Slot> taken = new java.util.HashSet<>();
                    boolean shared = false;
                    String eelHome = "none";
                    StringBuilder perches = new StringBuilder();
                    for (var c : companions) {
                        var a = c.agent();
                        if (a == null) continue;
                        if (c.locomotion() == grill24.fishsim.core.Locomotion.ANCHORED) eelHome = String.valueOf(a.perchHome());
                        perches.append(c.partyLine()).append(" home ").append(a.perchHome())
                                .append(a.riding() ? " on " + a.perchSlot() : " off").append("; ");
                        if (a.riding() && !a.perchSlot().hand() && !taken.add(a.perchSlot())) shared = true;
                    }
                    check("companionparty.mixed.eel_home_head", eelHome.equals("HEAD"), perches.toString());
                    check("companionparty.mixed.no_shared_perch", !shared, perches.toString());
                }
                companionLog(name + " first person");
                screenshot(mc, "companionparty", name + "_fp");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            queue(40, mc -> {
                companionLog(name + " third person");
                screenshot(mc, "companionparty", name + "_tp");
                mc.options.keyShift.setDown(true);
            });
            queue(20, mc -> {
                check("companionparty." + name + ".sneaking", mc.player.isCrouching(), "");
                screenshot(mc, "companionparty", name + "_tp_names");
                mc.options.keyShift.setDown(false);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
        }
        queueSharedClockWatch();
        queuePredatorScene();
        queue(20, mc -> {
            grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false);
            server(mc, CompanionScenes::clearParties);
        });
    }

    /**
     * The shared hand clock live (party spec §19, Phase 3d), on the ground party still equipped: the
     * owner stands still, looking level with an empty hand, so only the clock calls fish to the
     * hand. With the gap cut to 4-6 s for the test, over 60 s the turns must go round all three
     * units, and no two may be on a hand at once.
     */
    private static void queueSharedClockWatch() {
        java.util.Map<String, Integer> before = new java.util.HashMap<>();
        java.util.Map<String, Integer> landed = new java.util.TreeMap<>();
        int[] maxOnHand = new int[1];
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanionsDev.devHandGap(4f, 6f);
            server(mc, s -> run(s, "item replace entity @p weapon.mainhand with minecraft:air"));
            for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
                if (c.agent() != null) before.put(c.partyLine(), handVisitTotal(c.agent()));
            }
        });
        for (int k = 0; k < 1200; k++) {
            int tick = k;
            queue(1, mc -> {
                int onHand = 0;
                for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
                    var a = c.agent();
                    if (a != null && a.riding() && a.perchSlot().hand()) onHand++;
                }
                maxOnHand[0] = Math.max(maxOnHand[0], onHand);
                if (tick % 200 == 0) companionLog("clock t=" + tick / 20);
                if (tick == 600) screenshot(mc, "companionparty", "clock_30s");
            });
        }
        queue(1, mc -> {
            for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
                if (c.agent() != null) landed.put(c.partyLine(),
                        handVisitTotal(c.agent()) - before.getOrDefault(c.partyLine(), 0));
            }
            long visitors = landed.values().stream().filter(n -> n > 0).count();
            String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
            check("companionparty.clock.shared", state.contains("hand clock: shared"), state);
            check("companionparty.clock.turns_go_round", visitors == 3, "hand landings in 60 s " + landed);
            check("companionparty.clock.one_at_a_time", maxOnHand[0] <= 1, "most on a hand at once " + maxOnHand[0]);
            grill24.fishtastic.client.companion.ClientCompanionsDev.devHandGapReset();
        });
    }

    /**
     * The predator scene (party spec D21, §27): a pike with two prey swims apart from their shoal;
     * the dev command starts the scene at once, and within 8 s the pike laps the owner (goal LAP)
     * while the shoal's leader hides (HIDE), and the overlay names it.
     */
    private static void queuePredatorScene() {
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "fishtastic companion give @p fishtastic:northern_pike 50 1");
            run(s, "fishtastic companion give @p fishtastic:bluegill 15 2");
            run(s, "fishtastic companion give @p fishtastic:discus 15 3");
        }));
        boolean[] lapped = new boolean[1], hid = new boolean[1];
        String[] overlay = new String[1];
        queue(100, mc -> {
            var companions = grill24.fishtastic.client.companion.ClientCompanions.localCompanions();
            String lines = String.join("; ", companions.stream()
                    .map(grill24.fishtastic.client.companion.ClientCompanion::partyLine).toList());
            check("companionparty.predator.apart", companions.size() == 2
                    && companions.stream().anyMatch(c -> c.bodyCount() == 2), lines);
            server(mc, s -> run(s, "fishtastic companion predatorscene"));
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        for (int k = 0; k < 160; k++) {
            int tick = k;
            queue(1, mc -> {
                for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
                    if (c.agent() == null) continue;
                    if ("LAP".equals(c.agent().goal())) lapped[0] = true;
                    if (c.bodyCount() == 2 && "HIDE".equals(c.agent().goal())) hid[0] = true;
                }
                String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
                if (state.contains("predator scene: on")) overlay[0] = state;
                if (tick == 40) {
                    companionLog("predator scene 2s");
                    screenshot(mc, "companionparty", "predator_scene_tp");
                }
            });
        }
        queue(1, mc -> {
            String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
            check("companionparty.predator.lap", lapped[0], state);
            check("companionparty.predator.prey_hid", hid[0], state);
            check("companionparty.predator.overlay", overlay[0] != null, state);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    private static int handVisitTotal(grill24.fishsim.companion.CompanionAgent a) {
        int total = 0;
        for (int n : a.handVisits()) total += n;
        return total;
    }

    /**
     * Garden eels on their owner (D5, party spec §6.1): three of them settle on the head and both
     * shoulders, each with its art's base on the perch (Planted.baseX), photographed from the front
     * and behind; then a lone eel while its owner holds a predator fish, which ducks only briefly
     * (CompanionTunables.anchoredPredatorDuckSeconds) and comes back up to watch it.
     */
    static void queueCompanionEelScene() {
        queue(1, CompanionScenes::paradeSetup);
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "item replace entity @p weapon.mainhand with minecraft:air");
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.equip(player, 0, fish("garden_eel", 40f));
            }
            run(s, "fishtastic companion give @p fishtastic:garden_eel 35 2");
            run(s, "fishtastic companion give @p fishtastic:garden_eel 45 3");
        }));
        queue(160, mc -> {
            var companions = grill24.fishtastic.client.companion.ClientCompanions.localCompanions();
            java.util.Set<String> homes = new java.util.TreeSet<>();
            boolean allRiding = true;
            StringBuilder state = new StringBuilder();
            for (var c : companions) {
                var a = c.agent();
                if (a == null) { allRiding = false; continue; }
                homes.add(String.valueOf(a.perchHome()));
                allRiding &= a.riding() && a.perchSlot() == a.perchHome();
                state.append(c.partyLine()).append(" home ").append(a.perchHome())
                        .append(a.riding() ? " on " + a.perchSlot() : " off").append("; ");
            }
            check("companioneel.three.homes", homes.equals(new java.util.TreeSet<>(
                    java.util.List.of("HEAD", "LEFT_SHOULDER", "RIGHT_SHOULDER"))), state.toString());
            check("companioneel.three.riding_home", allRiding, state.toString());
            // Eels never visit a hand, so three of them share no hand clock (Phase 3d).
            String overlay = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
            check("companioneel.three.no_shared_clock", overlay.contains("hand clock: each percher's own"), overlay);
            // Which side of the player the sim holds each shoulder eel on, by Minecraft's own
            // right vector (facing +z, the right hand is -x): the perch layer must draw it there.
            net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
            double rx = -look.z, rz = look.x;
            for (var c : companions) {
                var a = c.agent();
                if (a == null || !a.riding() || a.perchSlot() == grill24.fishsim.companion.PerchBody.Slot.HEAD) continue;
                double side = (a.position().x() - mc.player.getX()) * rx + (a.position().z() - mc.player.getZ()) * rz;
                Fishtastic.LOGGER.info("[selftest] eel side {} sim offset to the player's right {}", a.perchSlot(),
                        String.format(Locale.ROOT, "%.2f", side));
            }
            // Zoomed in, against a dark wall behind the player (it faces -z): pale eels on sky
            // are unreadable at the default field of view.
            savedFov = mc.options.fov().get();
            mc.options.fov().set(30);
            server(mc, s -> run(s, eelWall(3, "black_concrete")));
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        queue(20, mc -> screenshot(mc, "companioneel", "three_front"));
        queue(1, mc -> {
            server(mc, s -> {
                run(s, eelWall(3, "air"));
                run(s, eelWall(-3, "black_concrete"));
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        queue(20, mc -> screenshot(mc, "companioneel", "three_back"));
        queue(1, mc -> {
            server(mc, s -> run(s, eelWall(-3, "air")));
            // Shoulder bases sit on the arm's centre, which differs for slim and wide arms.
            Fishtastic.LOGGER.info("[selftest] eel skin model {}", mc.player.getSkin().model());
            mc.options.fov().set(savedFov);
        });
        // The party switches (Phase 3c), through the real command: one turned off shows on the
        // overlay, and reset clears it with the eels' homes untouched.
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion switch otherPlayerClearance off")));
        queue(120, mc -> {
            String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
            check("companioneel.switch.overlay", state.contains("switches: otherPlayerClearance OFF"), state);
            server(mc, s -> run(s, "fishtastic companion switch reset"));
        });
        queue(160, mc -> {
            String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
            check("companioneel.switch.reset", eelHomes().equals(EEL_SPLIT) && !state.contains("switches:"),
                    eelHomes() + " | " + state);
        });
        // Two eels: the second takes RIGHT_SHOULDER alone, so the shot shows which arm the perch
        // layer draws it on (the sim holds it on the player's right).
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "fishtastic companion give @p fishtastic:garden_eel 40 1");
            run(s, "fishtastic companion give @p fishtastic:garden_eel 35 2");
            run(s, eelWall(3, "black_concrete"));
        }));
        queue(160, mc -> {
            savedFov = mc.options.fov().get();
            mc.options.fov().set(30);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        queue(20, mc -> screenshot(mc, "companioneel", "two_front"));
        queue(1, mc -> {
            mc.options.fov().set(savedFov);
            server(mc, s -> run(s, eelWall(3, "air")));
        });
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "fishtastic companion give @p fishtastic:garden_eel 40 1");
        }));
        queue(120, mc -> {
            grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            eelLog("calm");
            server(mc, s -> run(s, "item replace entity @p weapon.mainhand with fishtastic:lionfish"));
        });
        int[] ducked = new int[1];
        for (int k = 0; k < 100; k++) {
            int tick = k;
            queue(1, mc -> {
                var lead = grill24.fishtastic.client.companion.ClientCompanions.local();
                if (lead != null && lead.agent() != null && lead.agent().poseHint() == grill24.fishsim.companion.PoseHint.DUCK) ducked[0]++;
                if (tick == 20) screenshot(mc, "companioneel", "predator_1s_front");
                if (tick == 80) screenshot(mc, "companioneel", "predator_4s_front");
                if (tick % 20 == 0) eelLog("predator t=" + tick / 20);
            });
        }
        queue(1, mc -> {
            var lead = grill24.fishtastic.client.companion.ClientCompanions.local();
            String pose = lead == null || lead.agent() == null ? "none" : lead.agent().poseHint().name();
            check("companioneel.predator.brief_duck", ducked[0] > 10 && ducked[0] <= 50,
                    "ducked " + ducked[0] + " of 100 ticks");
            check("companioneel.predator.back_up", !pose.equals("DUCK"), "pose " + pose + " after 5 s");
            server(mc, s -> run(s, "item replace entity @p weapon.mainhand with minecraft:air"));
        });
        queue(20, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false);
            server(mc, CompanionScenes::clearParties);
        });
    }

    private static int savedFov = 70;

    /** Three eels' homes with party homes on (D5): head, then both shoulders. */
    private static final java.util.Set<String> EEL_SPLIT = new java.util.TreeSet<>(
            java.util.List.of("HEAD", "LEFT_SHOULDER", "RIGHT_SHOULDER"));

    /** The local party's home perches, as a sorted set of names. */
    private static java.util.Set<String> eelHomes() {
        java.util.Set<String> homes = new java.util.TreeSet<>();
        for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
            if (c.agent() != null) homes.add(String.valueOf(c.agent().perchHome()));
        }
        return homes;
    }

    /** A 7×5 wall {@code dz} blocks from the scene origin, for a backdrop. */
    private static String eelWall(int dz, String block) {
        return String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:%s", origin.getX() - 3, origin.getY(),
                origin.getZ() + dz, origin.getX() + 3, origin.getY() + 4, origin.getZ() + dz, block);
    }

    private static void eelLog(String label) {
        String state = grill24.fishtastic.client.companion.ClientCompanionsDev.debugState();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("goal \\S+|pose \\S+|threat [^|]*?retract [0-9.]+").matcher(state);
        StringBuilder out = new StringBuilder();
        while (m.find()) out.append(m.group()).append("  ");
        Fishtastic.LOGGER.info("[selftest] eel {}: {}", label, out);
    }

    /**
     * Empties every player's companion party. {@code /fishtastic companion off} needs a player
     * source, and {@link #run} runs commands as the server, so it would silently do nothing.
     */
    private static void clearParties(MinecraftServer s) {
        for (ServerPlayer player : s.getPlayerList().getPlayers()) grill24.fishtastic.companion.CompanionStorage.clear(player);
    }

    private static void companionLog(String label) {
        Fishtastic.LOGGER.info("[selftest] companion {}: {}", label,
                grill24.fishtastic.client.companion.ClientCompanionsDev.debugState());
    }

    /**
     * Copies the server player's inventory onto the client's own view. In the headless harness the
     * client's inventory does not resync while a mod screen with a menu is (or has been) open — see
     * the bond scene's close — so the held-item reaction checks would otherwise read a stale main
     * hand (found twice: every reaction leg read the same stale fish, then the "no reaction" leg).
     */
    private static void mirrorInventoryToClient(Minecraft mc, ServerPlayer player) {
        var client = mc.player.getInventory();
        for (int slot = 0; slot < client.getContainerSize(); slot++) {
            client.setItem(slot, player.getInventory().getItem(slot).copy());
        }
    }

    /** Bluegill's derived lore threshold — the catch count that completes its entry (phase 4 gate). */
    private static int bluegillLoreThreshold() {
        Minecraft mc = Minecraft.getInstance();
        var key = net.minecraft.resources.ResourceKey.create(grill24.FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY,
                Ids.of("fishtastic", "bluegill"));
        return grill24.fishtastic.data.EncyclopediaTiers.thresholdsFor(mc.level.registryAccess(), key).loreCatches();
    }


}
