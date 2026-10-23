package grill24.fishtastic.client.selftest;

import static grill24.fishtastic.client.selftest.RenderSelfTest.check;
import static grill24.fishtastic.client.selftest.RenderSelfTest.fileName;
import static grill24.fishtastic.client.selftest.RenderSelfTest.fish;
import static grill24.fishtastic.client.selftest.RenderSelfTest.item;
import static grill24.fishtastic.client.selftest.RenderSelfTest.note;
import static grill24.fishtastic.client.selftest.RenderSelfTest.origin;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.savedGuiScale;
import static grill24.fishtastic.client.selftest.RenderSelfTest.savedWindowHeight;
import static grill24.fishtastic.client.selftest.RenderSelfTest.savedWindowWidth;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;
import static grill24.fishtastic.client.selftest.RenderSelfTest.testingHelpMenus;

import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.client.QuestLogScreen;
import grill24.fishtastic.client.FishEncyclopediaScreen;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.network.FishtasticTransport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Encyclopedia, portrait and tutorial UI checks. */
final class EncyclopediaScenes {
    /** Stable three-player podium fixture for inspecting foot contact across renderer versions. */
    static void queueLeaderboardScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            server(mc, server -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.getInventory().setItem(8, new ItemStack(FishtasticItems.LEADERBOARDS_BOOK.value()));
                    FishCatchSavedData.getOrCreate(server).setTutorialStep(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                    grill24.fishtastic.network.TutorialSyncPacket.sendToPlayer(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                }
            });
        });
        queue(15, mc -> useTutorialBook(mc, FishtasticItems.LEADERBOARDS_BOOK.value()));
        queue(20, mc -> {
            check("leaderboard.open", mc.screen instanceof grill24.fishtastic.client.LeaderboardScreen, "screen=" + mc.screen);
            try {
                var screen = (grill24.fishtastic.client.LeaderboardScreen) mc.screen;
                var response = screen.getClass().getDeclaredMethod("onLeaderboardResponse", grill24.fishtastic.network.LeaderboardResponsePacket.class);
                response.setAccessible(true);
                var entries = new java.util.ArrayList<grill24.fishtastic.network.LeaderboardEntry>();
                for (int rank = 0; rank < 3; rank++) {
                    entries.add(grill24.fishtastic.network.LeaderboardEntry.globalBestSize(
                            BuiltInRegistries.ITEM.getKey(FishtasticItems.BLUEGILL.value()), mc.player.getUUID(),
                            mc.player.getName().getString(), 100f - rank * 20f, FishQuality.Quality.COMMON));
                }
                response.invoke(screen, new grill24.fishtastic.network.LeaderboardResponsePacket(
                        grill24.fishtastic.network.LeaderboardType.GLOBAL_BEST_SIZE, false, entries));
                var tabs = screen.getClass().getDeclaredField("tabs");
                tabs.setAccessible(true);
                ((io.github.currenj.gelatinui.gui.components.ItemTabs) tabs.get(screen)).select(3);
                grill24.fishtastic.network.LeaderboardResponsePacket.registerClientHandler(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        queue(25, mc -> screenshot(mc, "leaderboard", "podium"));
        queue(1, mc -> { if (mc.screen != null) mc.screen.onClose(); });
    }

    static void queueTutorialScene() {
        int[] session = {-1};
        queue(1, mc -> {
            mc.options.hideGui = false;
            FishtasticKeyBinds.openQuestLog.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            FishtasticKeyBinds.openFishEncyclopedia.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            FishtasticKeyBinds.openLeaderboards.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            server(mc, s -> {
                run(s, "gamemode survival @a");
                run(s, "clear @a");
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    var data = FishCatchSavedData.getOrCreate(s);
                    data.setTutorialStep(player, grill24.fishtastic.tutorial.TutorialStep.NOT_STARTED);
                    data.getOrCreateQuestState(player).resetProgress(grill24.fishtastic.tutorial.TutorialManager.TUTORIAL_QUEST_KEY);
                    player.getInventory().setItem(0, item("copper_fishing_rod"));
                    grill24.fishtastic.tutorial.TutorialManager.onItemCrafted(player);
                }
            });
        });
        queue(20, mc -> {
            check("tutorial.fishing_offer", mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen,
                    "A new rod must offer Play tutorial / Skip for later");
            screenshot(mc, "tutorial", "fishing_offer");
            mc.screen.children().stream()
                    .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                    .map(child -> (net.minecraft.client.gui.components.Button) child)
                    .filter(button -> button.getMessage().getString().equals(Component.translatable("tutorial.fishtastic.fishing_offer.later").getString()))
                    .findFirst().orElseThrow().onPress();
        });
        queue(10, mc -> {
            check("tutorial.fishing_later", grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.DEFERRED, "Skipping must leave ordinary fishing available");
            mc.player.getInventory().selected = 0;
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        queue(15, mc -> {
            check("tutorial.fishing_retry_on_cast", mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                    && grill24.fishtastic.client.TutorialClientHandler.getCurrentStep() == grill24.fishtastic.tutorial.TutorialStep.FISHING_OFFER,
                    "A copper cast after Skip for later must offer the lesson again");
            mc.screen.children().stream()
                    .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                    .map(child -> (net.minecraft.client.gui.components.Button) child)
                    .filter(button -> button.getMessage().getString().equals(Component.translatable("tutorial.fishtastic.fishing_offer.show").getString()))
                    .findFirst().orElseThrow().onPress();
        });
        queue(15, mc -> {
            check("tutorial.fishing_play", grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.BAIT_LOAD, "Play must begin the bait lesson");
            mc.setScreen(new InventoryScreen(mc.player));
        });
        queue(10, mc -> screenshot(mc, "tutorial", "bait"));
        queue(1, mc -> {
            mc.screen.onClose();
            server(mc, s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                grill24.fishtastic.item.FishtasticFishingRodItem.setBait(player.getInventory().getItem(0), item("worms"));
                grill24.fishtastic.tutorial.TutorialManager.onBaitLoaded(player);
                grill24.fishtastic.tutorial.TutorialManager.onHookCast(player, player.getInventory().getItem(0));
                session[0] = grill24.fishtastic.server.FishingMinigameManager.get(player.serverLevel()).startTutorialSession(player);
            });
        });
        queue(25, mc -> {
            screenshot(mc, "tutorial", "minigame_intro");
            check("tutorial.intro_waits", grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.MINIGAME_INTRO, "no automatic advance");
        });
        queue(65, mc -> {
            screenshot(mc, "tutorial", "minigame_controls");
        });
        queue(10, mc -> {
            ((IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
            grill24.fishtastic.client.FishingMinigameClientHandler.cancel();
            server(mc, s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                grill24.fishtastic.server.FishingMinigameManager.get(player.serverLevel())
                        .handleMinigameComplete(player, session[0], List.of(0));
            });
        });
        queue(25, mc -> {
            screenshot(mc, "tutorial", "quest_book_hint");
            useTutorialBook(mc, FishtasticItems.QUEST_BOOK.value());
        });
        queue(25, mc -> {
            check("tutorial.book_without_keys", mc.screen instanceof QuestLogScreen
                    && grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.QUEST_CLAIM, "screen=" + mc.screen);
            screenshot(mc, "tutorial", "claim");
            if (mc.screen != null) mc.screen.onClose();
        });
        queue(15, mc -> {
            screenshot(mc, "tutorial", "reopen_hint");
            useTutorialBook(mc, FishtasticItems.QUEST_BOOK.value());
        });
        queue(15, mc -> FishtasticTransport.sendToServer(
                new grill24.fishtastic.network.CompleteQuestPacket(grill24.fishtastic.tutorial.TutorialManager.TUTORIAL_QUEST_KEY.location())));
        queue(25, mc -> {
            screenshot(mc, "tutorial", "shop_hint");
            try {
                var field = QuestLogScreen.class.getDeclaredField("questTabs");
                field.setAccessible(true);
                ((io.github.currenj.gelatinui.gui.components.ItemTabs) field.get(mc.screen)).select(5);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        queue(15, mc -> {
            screenshot(mc, "tutorial", "shop_visited");
            if (mc.screen != null) mc.screen.onClose();
        });
        queue(15, mc -> {
            check("tutorial.offer", mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "screen=" + mc.screen);
            screenshot(mc, "tutorial", "offer");
            if (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
                mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                        .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow().onPress();
            }
        });
        queue(15, mc -> {
            screenshot(mc, "tutorial", "fishopedia_hint");
            useTutorialBook(mc, FishtasticItems.FISHOPEDIA.value());
        });
        queue(30, mc -> {
            check("tutorial.handoff", mc.screen instanceof FishEncyclopediaScreen
                    && grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.COMPLETE, "screen=" + mc.screen);
            screenshot(mc, "tutorial", "encyclopedia");
        });
        queue(1, mc -> { if (mc.screen != null) mc.screen.onClose(); });
    }

    static void useTutorialBook(Minecraft mc, net.minecraft.world.item.Item book) {
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getItem(i).is(book)) continue;
            mc.player.getInventory().selected = i;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
        check("tutorial.has_book", false, "missing " + book);
    }

}
