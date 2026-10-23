package grill24.fishtastic.client.selftest;

import static grill24.fishtastic.client.selftest.RenderSelfTest.armed;
import static grill24.fishtastic.client.selftest.RenderSelfTest.check;
import static grill24.fishtastic.client.selftest.RenderSelfTest.item;
import static grill24.fishtastic.client.selftest.RenderSelfTest.note;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.client.QuestLogScreen;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Quest-book UI interaction and layout checks. */
final class QuestBookScenes {
    /** Real renderer evidence for the reviewed quest-book layouts. Cache fixtures affect only this armed client. */
    static void queueQuestBookScene() {
        queue(1, mc -> mc.player.connection.sendCommand("gelatin quest_log"));
        queue(45, mc -> screenshot(mc, "questbook", "baseline"));
        queue(1, mc -> questBookPage(mc, 0, "tutorial/first_catch", true));
        queue(40, mc -> screenshot(mc, "questbook", "tutorial"));
        queue(1, QuestBookScenes::questBookInteractionChecks);
        queue(1, mc -> questBookPage(mc, 3, "collector/river_collector", false));
        queue(40, mc -> screenshot(mc, "questbook", "collection"));
        queue(1, mc -> questBookPage(mc, 5, null, false));
        queue(20, mc -> screenshot(mc, "questbook", "market"));
        queue(1, mc -> questBookPage(mc, 6, null, false));
        queue(20, mc -> screenshot(mc, "questbook", "cleanup"));
        queue(1, mc -> mc.getWindow().setWindowed(852, 480));
        queue(20, mc -> screenshot(mc, "questbook", "cleanup_426x240"));
        queue(1, mc -> mc.getWindow().setWindowed(640, 360));
        queue(20, mc -> {
            questBookResizeChecks(mc);
            screenshot(mc, "questbook", "cleanup_320x180");
        });
        queue(1, mc -> {
            mc.getWindow().setWindowed(960, 540);
            mc.setScreen(null);
        });
    }

    @SuppressWarnings("unchecked")
    private static void questBookInteractionChecks(Minecraft mc) {
        try {
            QuestLogScreen screen = (QuestLogScreen) mc.screen;
            var uiField = io.github.currenj.gelatinui.GelatinUIScreen.class.getDeclaredField("uiScreen");
            uiField.setAccessible(true);
            var ui = (io.github.currenj.gelatinui.gui.UIScreen) uiField.get(screen);
            var rowField = QuestLogScreen.class.getDeclaredField("questRowRefs");
            rowField.setAccessible(true);
            var rows = (java.util.Map<?, ?>) rowField.get(screen);
            Object refs = rows.get(Ids.of("fishtastic", "tutorial/first_catch"));
            var accessor = refs.getClass().getDeclaredMethod("row");
            accessor.setAccessible(true);
            var row = (io.github.currenj.gelatinui.gui.UIContainer<?>) accessor.invoke(refs);
            var expandedField = QuestLogScreen.class.getDeclaredField("expandedQuestByDestination");
            expandedField.setAccessible(true);
            var expanded = (java.util.Map<?, ?>) expandedField.get(screen);
            var preview = row.getChildren().stream().filter(child -> child instanceof io.github.currenj.gelatinui.gui.components.ItemButton).findFirst().orElseThrow();
            var bounds = preview.getBounds();
            ui.onMouseClick((int) bounds.getCenterX(), (int) bounds.getCenterY(), 0);
            check("questbook.rewardInspectionDoesNotToggle", expanded.containsKey(0), "expanded=" + expanded);
            var disclosure = row.getChildren().stream().filter(child -> child instanceof io.github.currenj.gelatinui.gui.components.SpriteButton button
                    && "^".equals(button.getText())).findFirst().orElseThrow();
            bounds = disclosure.getBounds();
            ui.onMouseClick((int) bounds.getCenterX(), (int) bounds.getCenterY(), 0);
            check("questbook.disclosureCollapses", !expanded.containsKey(0), "expanded=" + expanded);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Quest book interaction check failed", e);
        }
    }

    private static void questBookResizeChecks(Minecraft mc) {
        try {
            if (!(mc.screen instanceof QuestLogScreen screen)) {
                throw new IllegalStateException("Quest book not open after resize");
            }
            var uiField = io.github.currenj.gelatinui.GelatinUIScreen.class.getDeclaredField("uiScreen");
            uiField.setAccessible(true);
            var ui = (io.github.currenj.gelatinui.gui.UIScreen) uiField.get(screen);
            var root = ui.getRoot();
            var bounds = root.getBounds();
            var viewport = ui.getViewport();
            double tolerance = 0.5;
            check("questbook.smallWindowFits",
                    bounds.getMinX() >= viewport.getMinX() - tolerance
                            && bounds.getMinY() >= viewport.getMinY() - tolerance
                            && bounds.getMaxX() <= viewport.getMaxX() + tolerance
                            && bounds.getMaxY() <= viewport.getMaxY() + tolerance,
                    "bounds=" + bounds + " viewport=" + viewport);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Quest book resize check failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void questBookPage(Minecraft mc, int destination, String expanded, boolean ready) {
        try {
            if (!(mc.screen instanceof QuestLogScreen screen)) throw new IllegalStateException("Quest book not open");
            var progress = new java.util.HashMap<>(grill24.fishtastic.client.QuestClientCache.getQuestProgress());
            if (destination == 3) {
                var registry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
                var quest = registry.getOptional(Ids.of("fishtastic", expanded)).orElseThrow();
                var membersMethod = QuestLogScreen.class.getDeclaredMethod("resolveSortedTagMembers",
                        net.minecraft.tags.TagKey.class, java.util.Optional.class, net.minecraft.core.RegistryAccess.class);
                membersMethod.setAccessible(true);
                var members = (java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item>>) membersMethod.invoke(
                        null, quest.objective().targetSpeciesTag().orElseThrow(), quest.objective().excludeSpeciesTag(), mc.level.registryAccess());
                var caught = members.stream().limit(6).map(key -> key.location()).toList();
                progress.put(Ids.of("fishtastic", expanded), new grill24.fishtastic.server.PlayerQuestState.QuestProgress(
                        caught.size(), 0, false, false, caught));
                var discoveries = new java.util.HashMap<net.minecraft.resources.ResourceLocation, Integer>();
                caught.forEach(id -> discoveries.put(id, 1));
                grill24.fishtastic.client.FishEncyclopediaClientCache.update(discoveries, List.of(), List.of(), List.of());
            }
            if (ready) progress.put(Ids.of("fishtastic", expanded), new grill24.fishtastic.server.PlayerQuestState.QuestProgress(
                    1, 0, true, false, List.of()));
            var cleanup = new grill24.fishtastic.network.CleanupGoalProgress(125, 200, 0, List.of(
                    new grill24.fishtastic.network.CleanupGoalProgress.Contributor(java.util.UUID.randomUUID(), "Mira", 55),
                    new grill24.fishtastic.network.CleanupGoalProgress.Contributor(java.util.UUID.randomUUID(), "Jo", 39),
                    new grill24.fishtastic.network.CleanupGoalProgress.Contributor(java.util.UUID.randomUUID(), "Ari", 21),
                    new grill24.fishtastic.network.CleanupGoalProgress.Contributor(mc.player.getUUID(), "You", 10)));
            grill24.fishtastic.client.QuestClientCache.update(progress, 42, java.util.Map.of(), java.util.Map.of(), cleanup,
                    mc.level.getGameTime(), ItemStack.EMPTY, List.of(), 0, List.of(), List.of());
            var tab = QuestLogScreen.class.getDeclaredField("activeTabIndex");
            tab.setAccessible(true);
            tab.setInt(screen, destination);
            var field = QuestLogScreen.class.getDeclaredField("expandedQuestByDestination");
            field.setAccessible(true);
            var expandedByDestination = (java.util.Map<Integer, net.minecraft.resources.ResourceLocation>) field.get(screen);
            if (expanded != null) expandedByDestination.put(destination, Ids.of("fishtastic", expanded));
            var build = QuestLogScreen.class.getDeclaredMethod("buildUI");
            build.setAccessible(true);
            build.invoke(screen);
            if (expanded != null) {
                var focus = QuestLogScreen.class.getDeclaredMethod("pulseQuestRow", net.minecraft.resources.ResourceKey.class);
                focus.setAccessible(true);
                focus.invoke(screen, net.minecraft.resources.ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Ids.of("fishtastic", expanded)));
            }
            var viewportField = QuestLogScreen.class.getDeclaredField("questViewport");
            viewportField.setAccessible(true);
            var viewport = (io.github.currenj.gelatinui.gui.IUIElement) viewportField.get(screen);
            check("questbook.viewport." + destination, viewport.getSize().x == 316 && viewport.getSize().y > 170,
                    "size=" + viewport.getSize());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Quest book fixture failed", e);
        }
    }

}
