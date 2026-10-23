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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Quest-book UI interaction and layout checks. */
final class QuestBookScenes {
    /** Real renderer evidence for the reviewed quest-book layouts. Cache fixtures affect only this armed client. */
    static void queueQuestBookScene() {
        queue(1, mc -> mc.player.connection.sendCommand("gelatin quest_log"));
        queue(45, mc -> screenshot(mc, "questbook", "baseline"));
        queue(1, QuestBookScenes::questBookKeyboardChecks);
        // Enter on the second rail stop ("Goals") must switch destination exactly like a click.
        queue(1, mc -> {
            var screen = (QuestLogScreen) mc.screen;
            screen.keyboardNav().mouseUsed();
            screen.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0));
            screen.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0));
            note("a11y.questbook.enterOn=" + screen.keyboardNav().focusedDescription());
            screen.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0));
        });
        queue(10, mc -> {
            try {
                var tab = QuestLogScreen.class.getDeclaredField("activeTabIndex");
                tab.setAccessible(true);
                check("a11y.questbook.enterActivates", tab.getInt(mc.screen) == 1, "activeTabIndex=" + tab.getInt(mc.screen));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            ((QuestLogScreen) mc.screen).keyboardNav().mouseUsed();
        });
        queue(1, mc -> questBookPage(mc, 0, "tutorial/first_catch", true));
        queue(1, mc -> questBookPage(mc, 0, "tutorial/first_catch", true));
        queue(40, mc -> screenshot(mc, "questbook", "tutorial"));
        queue(1, QuestBookScenes::questBookInteractionChecks);
        // Clicking a reward preview opens that species in the encyclopedia; come back to the log.
        queue(1, mc -> {
            if (!(mc.screen instanceof QuestLogScreen)) mc.player.connection.sendCommand("gelatin quest_log");
        });
        queue(40, mc -> check("questbook.reopened", mc.screen instanceof QuestLogScreen, "screen=" + mc.screen));
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

    static net.minecraft.client.input.KeyEvent key(int code, int mods) {
        return new net.minecraft.client.input.KeyEvent(code, 0, mods);
    }

    /** Tabs through a screen, returning what the narrator would say at each stop. */
    static java.util.List<String> tabThrough(net.minecraft.client.gui.screens.Screen screen,
            grill24.fishtastic.client.UiKeyboardNav nav, int presses) {
        java.util.List<String> spoken = new java.util.ArrayList<>();
        for (int i = 0; i < presses; i++) {
            screen.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0));
            spoken.add(nav.focusedDescription());
        }
        return spoken;
    }

    /** Runs vanilla's own narration collector over a screen, as the narrator would. */
    static String vanillaNarration(net.minecraft.client.gui.screens.Screen screen) {
        try {
            var stateField = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("narrationState");
            stateField.setAccessible(true);
            var state = (net.minecraft.client.gui.narration.ScreenNarrationCollector) stateField.get(screen);
            var update = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod("updateNarrationState",
                    net.minecraft.client.gui.narration.NarrationElementOutput.class);
            update.setAccessible(true);
            state.update(out -> {
                try {
                    update.invoke(screen, out);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            return state.collectNarrationText(true);
        } catch (ReflectiveOperationException e) {
            return "ERROR " + e;
        }
    }

    /**
     * Drives the game's real narration path (narrator option on, screen asks to narrate) with a
     * recording stand-in for the OS speech library, and returns what would have been spoken.
     */
    static String spokenByGameNarrator(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        try {
            var gameNarrator = mc.getNarrator();
            var field = net.minecraft.client.GameNarrator.class.getDeclaredField("narrator");
            field.setAccessible(true);
            Object original = field.get(gameNarrator);
            StringBuilder spoken = new StringBuilder();
            Object recorder = java.lang.reflect.Proxy.newProxyInstance(
                    com.mojang.text2speech.Narrator.class.getClassLoader(),
                    new Class<?>[] {com.mojang.text2speech.Narrator.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "say" -> {
                                spoken.append(args[0]);
                                return null;
                            }
                            case "active" -> {
                                return true;
                            }
                            default -> {
                                return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                            }
                        }
                    });
            var previousStatus = mc.options.narrator().get();
            field.set(gameNarrator, recorder);
            try {
                mc.options.narrator().set(net.minecraft.client.NarratorStatus.ALL);
                screen.triggerImmediateNarration(false);
            } finally {
                mc.options.narrator().set(previousStatus);
                field.set(gameNarrator, original);
            }
            return spoken.toString();
        } catch (ReflectiveOperationException e) {
            return "ERROR " + e;
        }
    }

    private static void questBookKeyboardChecks(Minecraft mc) {
        QuestLogScreen screen = (QuestLogScreen) mc.screen;
        var nav = screen.keyboardNav();
        var spoken = tabThrough(screen, nav, 16);
        note("a11y.questbook.stops=" + nav.stopCount() + " spoken=" + spoken);
        check("a11y.questbook.stops", nav.stopCount() >= 8, "stops=" + nav.stopCount());
        check("a11y.questbook.labelled", spoken.stream().noneMatch(s -> s == null || s.isBlank()),
                "unlabelled stop in " + spoken);
        check("a11y.questbook.selectedAnnounced", spoken.stream().anyMatch(s -> s != null && s.contains("selected")),
                "no 'selected' in " + spoken);
        // Shift+Tab walks backward; Enter on a destination switches the page like a click would.
        screen.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT));
        String back = nav.focusedDescription();
        check("a11y.questbook.shiftTab", back != null && !back.equals(spoken.get(spoken.size() - 1)), "back=" + back);
        screen.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0));
        String narrated = vanillaNarration(screen);
        note("a11y.questbook.vanillaNarration=" + narrated);
        check("a11y.questbook.vanillaNarration", narrated.contains(nav.focusedDescription()) && narrated.contains("Captain"),
                "narration=" + narrated);
        String audible = spokenByGameNarrator(mc, screen);
        note("a11y.questbook.gameNarratorSpoke=" + audible);
        check("a11y.questbook.gameNarratorSpeaks", audible.contains(nav.focusedDescription()),
                "spoken=" + audible);
        nav.mouseUsed();
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
                var registry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
                var quest = registry.getOptional(Ids.of("fishtastic", expanded)).orElseThrow();
                var membersMethod = QuestLogScreen.class.getDeclaredMethod("resolveSortedTagMembers",
                        net.minecraft.tags.TagKey.class, java.util.Optional.class, net.minecraft.core.RegistryAccess.class);
                membersMethod.setAccessible(true);
                var members = (java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item>>) membersMethod.invoke(
                        null, quest.objective().targetSpeciesTag().orElseThrow(), quest.objective().excludeSpeciesTag(), mc.level.registryAccess());
                var caught = members.stream().limit(6).map(key -> key.identifier()).toList();
                progress.put(Ids.of("fishtastic", expanded), new grill24.fishtastic.server.PlayerQuestState.QuestProgress(
                        caught.size(), 0, false, false, caught));
                var discoveries = new java.util.HashMap<net.minecraft.resources.Identifier, Integer>();
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
                    mc.level.getGameTime(), ItemStack.EMPTY, List.of(), 0, 0, true, List.of(), List.of());
            var tab = QuestLogScreen.class.getDeclaredField("activeTabIndex");
            tab.setAccessible(true);
            tab.setInt(screen, destination);
            var field = QuestLogScreen.class.getDeclaredField("expandedQuestByDestination");
            field.setAccessible(true);
            var expandedByDestination = (java.util.Map<Integer, net.minecraft.resources.Identifier>) field.get(screen);
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
