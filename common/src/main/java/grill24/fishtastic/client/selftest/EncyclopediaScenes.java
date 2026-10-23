package grill24.fishtastic.client.selftest;

import static grill24.fishtastic.client.selftest.RenderSelfTest.check;
import static grill24.fishtastic.client.selftest.RenderSelfTest.fileName;
import static grill24.fishtastic.client.selftest.RenderSelfTest.fish;
import static grill24.fishtastic.client.selftest.RenderSelfTest.item;
import static grill24.fishtastic.client.selftest.QuestBookScenes.key;
import static grill24.fishtastic.client.selftest.RenderSelfTest.note;
import static grill24.fishtastic.client.selftest.RenderSelfTest.origin;
import static grill24.fishtastic.client.selftest.RenderSelfTest.pressHelpButton;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.savedGuiScale;
import static grill24.fishtastic.client.selftest.RenderSelfTest.savedWindowHeight;
import static grill24.fishtastic.client.selftest.RenderSelfTest.savedWindowWidth;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;
import static grill24.fishtastic.client.selftest.QuestBookScenes.spokenByGameNarrator;
import static grill24.fishtastic.client.selftest.QuestBookScenes.tabThrough;
import static grill24.fishtastic.client.selftest.RenderSelfTest.testingHelpMenus;
import static grill24.fishtastic.client.selftest.QuestBookScenes.vanillaNarration;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.FishEncyclopediaScreen;
import grill24.fishtastic.client.QuestLogScreen;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.io.File;
import java.io.IOException;
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

    /**
     * Encyclopedia info pages across progress states and GUI scales, for the page redesign
     * (docs/encyclopedia-redesign/README.md). Notes each page's overflow and content scale.
     */
    static void queueEncyclopediaScene() {
        // species, catches (-1 = lore threshold), claim every unlocked reward
        record Fixture(String species, int catches, boolean claimAll) {}
        List<Fixture> fixtures = List.of(
                new Fixture("betta", -1, true),               // complete, 5 conditions, 2 zones
                new Fixture("glass_squid", -1, false),        // complete, every reward unclaimed
                new Fixture("bluegill", 3, false),            // mid-progress common fish
                new Fixture("arctic_char", 1, true),          // first catch only
                new Fixture("portuguese_man_o_war", 0, false) // never caught
        );
        queue(1, mc -> {
            savedGuiScale = mc.options.guiScale().get();
            savedWindowWidth = mc.getWindow().getWidth();
            savedWindowHeight = mc.getWindow().getHeight();
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.hideGui = false;
            server(mc, server -> {
                run(server, "gamemode survival @a");
                var data = FishCatchSavedData.getOrCreate(server);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    data.setTutorialStep(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                    grill24.fishtastic.network.TutorialSyncPacket.sendToPlayer(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                    java.util.UUID key = data.resolvePlayerKey(player);
                    String name = player.getName().getString();
                    for (Fixture f : fixtures) {
                        Identifier id = Ids.of("fishtastic", f.species());
                        var fishKey = net.minecraft.resources.ResourceKey.create(grill24.FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, id);
                        var thresholds = grill24.fishtastic.data.EncyclopediaTiers.thresholdsFor(server.registryAccess(), fishKey);
                        note("encyclopedia.thresholds " + f.species() + " " + thresholds);
                        int catches = f.catches() < 0 ? thresholds.loreCatches() : f.catches();
                        if (catches > 0) {
                            ItemStack best = fish(f.species(), 31f);
                            FishtasticItemData.set(best, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.RARE));
                            data.recordCatch(key, name, best);
                            data.recordCatch(java.util.UUID.nameUUIDFromBytes(new byte[]{1}), "Rival", fish(f.species(), 44f));
                        }
                        data.setCatchCount(key, name, id, catches);
                        if (f.claimAll()) {
                            var state = data.getOrCreateQuestState(player);
                            for (var section : grill24.fishtastic.data.EncyclopediaRewardSection.values()) {
                                if (catches >= section.threshold(thresholds)) state.claimEncyclopediaReward(id, section);
                            }
                        }
                    }
                    data.setDirty();
                    grill24.fishtastic.network.FishEncyclopediaSyncPacket.sendToPlayer(player, data);
                }
            });
        });
        // 1080p Auto (4), 720p Auto (3), the common manual scales 2 and 1, and the compact card.
        record Config(int windowW, int windowH, int guiScale) {}
        List<Config> configs = List.of(
                new Config(1920, 1080, 4), new Config(1280, 720, 3), new Config(1920, 1080, 2),
                new Config(1920, 1080, 1), new Config(320, 240, 1));
        for (Config cfg : configs) {
            String tag = cfg.windowW() + "x" + cfg.windowH() + "_s" + cfg.guiScale();
            queue(5, mc -> {
                if (mc.player != null) mc.player.closeContainer();
                mc.getWindow().setWindowed(cfg.windowW(), cfg.windowH());
                mc.options.guiScale().set(cfg.guiScale());
                mc.resizeGui();
            });
            queue(5, mc -> server(mc, server -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    grill24.fishtastic.compat.GelatinOpenMenuCompat.openFishEncyclopediaMenu(player);
                }
            }));
            // The disc, with the book-wide Claim all button, then a real click-equivalent selection.
            queue(60, mc -> {
                check("encyclopedia.disc.open." + tag, mc.screen instanceof FishEncyclopediaScreen, "screen=" + mc.screen);
                if (mc.screen instanceof FishEncyclopediaScreen s) {
                    var nav = s.keyboardNav();
                    var spoken = tabThrough(s, nav, 4);
                    note("a11y.encyclopedia.disc.stops=" + nav.stopCount() + " spoken=" + spoken);
                    String narrated = vanillaNarration(s);
                    note("a11y.encyclopedia.vanillaNarration=" + narrated);
                    check("a11y.encyclopedia.vanillaNarration." + tag, narrated.contains(nav.focusedDescription()),
                            "narration=" + narrated);
                    String audible = spokenByGameNarrator(mc, s);
                    note("a11y.encyclopedia.gameNarratorSpoke=" + audible);
                    check("a11y.encyclopedia.gameNarratorSpeaks." + tag, audible.contains(nav.focusedDescription()),
                            "spoken=" + audible);
                    check("a11y.encyclopedia.disc.stops." + tag, nav.stopCount() >= 5, "stops=" + nav.stopCount());
                    check("a11y.encyclopedia.disc.labelled." + tag,
                            spoken.stream().noneMatch(x -> x == null || x.isBlank() || x.contains("Unlabeled")),
                            "spoken=" + spoken);
                    nav.mouseUsed();
                }
                screenshot(mc, "encyclopedia", tag + "_disc");
            });
            // Enter on a focused disc fish opens its page, like a click; then put the disc back.
            queue(1, mc -> {
                if (mc.screen instanceof FishEncyclopediaScreen s) {
                    s.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0));
                    s.keyPressed(key(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0));
                }
            });
            queue(60, mc -> {
                var s = mc.screen instanceof FishEncyclopediaScreen e ? e : null;
                check("a11y.encyclopedia.enterOpensPage." + tag, s != null && s.isInfoPageOpen(), "page open after Enter");
                if (s != null) s.closeInfoPage();
            });
            queue(60, mc -> {
                var s = mc.screen instanceof FishEncyclopediaScreen e ? e : null;
                check("a11y.encyclopedia.enterRoundTrip." + tag, s != null && !s.isInfoPageOpen() && !s.isClosingInfoPage(),
                        "disc restored");
            });
            queue(1, mc -> {
                if (mc.screen instanceof FishEncyclopediaScreen screen) screen.selectFish(Ids.of("fishtastic", "betta"));
            });
            queue(2, mc -> screenshot(mc, "encyclopedia", tag + "_flight"));
            queue(40, mc -> {
                if (mc.screen instanceof FishEncyclopediaScreen s) {
                    var nav = s.keyboardNav();
                    var spoken = tabThrough(s, nav, 6);
                    note("a11y.encyclopedia.page.stops=" + nav.stopCount() + " spoken=" + spoken);
                    check("a11y.encyclopedia.page.stops." + tag, nav.stopCount() >= 2, "stops=" + nav.stopCount());
                    check("a11y.encyclopedia.page.labelled." + tag,
                            spoken.stream().noneMatch(x -> x == null || x.isBlank() || x.contains("Unlabeled")),
                            "spoken=" + spoken);
                    // A tooltip hotspot must read what is printed on the page it covers, not only its hover text.
                    var everyStop = tabThrough(s, nav, nav.stopCount());
                    String tooltipStop = everyStop.stream().filter(x -> x != null && x.contains("Based on where and when")).findFirst().orElse(null);
                    note("a11y.encyclopedia.page.tooltipStop=" + tooltipStop);
                    // The compact card draws its verdict as plain text with no tooltip hotspot.
                    check("a11y.encyclopedia.tooltipReadsVisibleText." + tag,
                            s.isCompactPage() ? tooltipStop == null : tooltipStop != null && tooltipStop.length() > "Based on where and when you're fishing".length() + 4
                                    && !tooltipStop.startsWith("Based on where and when"),
                            "stop=" + tooltipStop);
                    nav.mouseUsed();
                }
                var screen = mc.screen instanceof FishEncyclopediaScreen s ? s : null;
                check("encyclopedia.flight.landed." + tag, screen != null && screen.isInfoPageOpen()
                        && Ids.of("fishtastic", "betta").equals(screen.shownFishId()), "page open after flight");
                if (cfg.windowW() < grill24.fishtastic.client.encyclopedia.page.EncyclopediaPageLayout.W
                        || cfg.windowH() < grill24.fishtastic.client.encyclopedia.page.EncyclopediaPageLayout.H) {
                    check("encyclopedia.compact." + tag, screen != null && screen.isCompactPage(),
                            "compact fallback active");
                }
                screenshot(mc, "encyclopedia", tag + "_betta_flown");
            });
            // Browse to the next species: slide, then the page shows the neighbour.
            queue(1, mc -> {
                if (mc.screen instanceof FishEncyclopediaScreen screen) screen.browseFish(1);
            });
            queue(3, mc -> screenshot(mc, "encyclopedia", tag + "_browse_mid"));
            queue(30, mc -> screenshot(mc, "encyclopedia", tag + "_browsed"));
            // Close after browsing: the shown fish flies to its own slot, the disc is whole again.
            queue(1, mc -> {
                if (mc.screen instanceof FishEncyclopediaScreen screen) screen.closeInfoPage();
            });
            queue(4, mc -> screenshot(mc, "encyclopedia", tag + "_closing"));
            queue(40, mc -> {
                var screen = mc.screen instanceof FishEncyclopediaScreen s ? s : null;
                check("encyclopedia.closed." + tag, screen != null && !screen.isInfoPageOpen() && !screen.isClosingInfoPage(),
                        "disc restored after close");
                screenshot(mc, "encyclopedia", tag + "_disc_after");
            });
            // Every fixture straight onto its page (the Quest Log deep link).
            for (Fixture f : fixtures) {
                queue(1, mc -> {
                    if (mc.player != null) mc.player.closeContainer();
                    FishEncyclopediaScreen.selectOnNextOpen(Ids.of("fishtastic", f.species()));
                });
                queue(5, mc -> server(mc, server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        grill24.fishtastic.compat.GelatinOpenMenuCompat.openFishEncyclopediaMenu(player);
                    }
                }));
                queue(60, mc -> {
                    check("encyclopedia.open." + tag + "." + f.species(),
                            mc.screen instanceof FishEncyclopediaScreen s && s.isInfoPageOpen()
                                    && Ids.of("fishtastic", f.species()).equals(s.shownFishId()), "screen=" + mc.screen);
                    screenshot(mc, "encyclopedia", tag + "_" + f.species());
                });
                if (f.species().equals("betta")) {
                    queue(1, mc -> {
                        if (mc.screen instanceof FishEncyclopediaScreen screen) screen.showNotesTab();
                    });
                    queue(5, mc -> screenshot(mc, "encyclopedia", tag + "_betta_notes"));
                }
            }
            queue(1, mc -> { if (mc.player != null) mc.player.closeContainer(); });
        }
        queue(5, mc -> {
            mc.options.guiScale().set(savedGuiScale);
            mc.getWindow().setWindowed(savedWindowWidth, savedWindowHeight);
            mc.resizeGui();
        });
    }

    /** A fixed 416x232 integer-scaled canvas of Fishopedia portraits, for the opt-in {@code portrait} scene. */
    private static final class PortraitCanvas {
        record Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme theme, boolean uncaught, String fish, int x, int y) {}

        final io.github.currenj.gelatinui.gui.UIScreen ui;
        final int k, originX, originY;
        final List<Spec> specs;
        final List<grill24.fishtastic.client.encyclopedia.portrait.Portrait> portraits = new java.util.ArrayList<>();

        PortraitCanvas(io.github.currenj.gelatinui.gui.UIScreen ui, int screenW, int screenH, List<Spec> specs) {
            this.ui = ui;
            this.specs = specs;
            var root = new grill24.fishtastic.client.FreeformContainer();
            root.setSize(screenW, screenH);
            // Opaque full-screen backdrop: nothing outside the plates may change between captures.
            var backdrop = new io.github.currenj.gelatinui.gui.components.Rectangle(screenW, screenH, 0xFF18161A);
            root.addChild(backdrop);
            k = Math.max(1, Math.min(screenW / 416, screenH / 232));
            originX = (screenW - 416 * k) / 2;
            originY = (screenH - 232 * k) / 2;
            var canvas = new grill24.fishtastic.client.FreeformContainer();
            canvas.setSize(416, 232);
            canvas.setTargetScale(k, false);
            canvas.setPosition(new org.joml.Vector2f(originX, originY));
            root.addChild(canvas);
            var iconLayer = new grill24.fishtastic.client.FreeformContainer();
            iconLayer.setSize(416, 232);
            var frontLayer = new grill24.fishtastic.client.FreeformContainer();
            frontLayer.setSize(416, 232);
            var toggleLayer = new grill24.fishtastic.client.FreeformContainer();
            toggleLayer.setSize(416, 232);
            for (Spec s : specs) {
                var p = new grill24.fishtastic.client.encyclopedia.portrait.Portrait(ui, s.theme(),
                        ("fishtastic:" + s.fish()).hashCode(), s.uncaught(), k);
                p.placeAt(s.x(), s.y());
                portraits.add(p);
                canvas.addChild(p.backLayer());
                var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Fishtastic.id(s.fish())));
                var icon = io.github.currenj.gelatinui.gui.UI.itemRenderer(stack);
                icon.showCount(false);
                icon.setTargetScale(4f, false);
                icon.setPosition(new org.joml.Vector2f(s.x() + grill24.fishtastic.client.encyclopedia.portrait.Portrait.FISH_SLOT_X,
                        s.y() + grill24.fishtastic.client.encyclopedia.portrait.Portrait.FISH_SLOT_Y));
                iconLayer.addChild(icon);
                frontLayer.addChild(p.frontLayer());
                toggleLayer.addChild(p.toggle());
            }
            canvas.addChild(iconLayer);
            canvas.addChild(frontLayer);
            canvas.addChild(toggleLayer);
            ui.setRoot(root);
            ui.setScrollEnabled(false);
            ui.setScrollY(0f);
        }

        static PortraitCanvas install(net.minecraft.client.gui.screens.Screen screen, List<Spec> specs) {
            try {
                var field = io.github.currenj.gelatinui.GelatinUIScreen.class.getDeclaredField("uiScreen");
                field.setAccessible(true);
                var ui = (io.github.currenj.gelatinui.gui.UIScreen) field.get(screen);
                return new PortraitCanvas(ui, screen.width, screen.height, specs);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        int sx(float canvasX) { return Math.round(originX + canvasX * k); }
        int sy(float canvasY) { return Math.round(originY + canvasY * k); }

        /** Screen point of a plate-local point of plate {@code i}. */
        int px(int i, float plateX) { return sx(specs.get(i).x() + plateX); }
        int py(int i, float plateY) { return sy(specs.get(i).y() + plateY); }

        /** Cheap hash of every live particle's drawn position: equal iff the frame is identical. */
        static int fingerprint(grill24.fishtastic.client.encyclopedia.portrait.PortraitBubbles m) {
            int h = m.alive();
            for (var b : m.bubbles()) h = h * 31 + (b.drawX() * 131 + b.drawY()) + b.kind.ordinal() + (b.popping ? 7 * b.popFrame() : 0);
            return h;
        }

        void advanceAll(float seconds) {
            for (var p : portraits) p.model().advance(seconds);
        }

        /** Counts differing pixels between two screenshots, split into inside/outside every plate. */
        int[] diff(java.io.File a, java.io.File b, double guiScale) throws java.io.IOException {
            var ia = javax.imageio.ImageIO.read(a);
            var ib = javax.imageio.ImageIO.read(b);
            if (ia == null || ib == null || ia.getWidth() != ib.getWidth() || ia.getHeight() != ib.getHeight()) return null;
            int inside = 0, outside = 0;
            for (int y = 0; y < ia.getHeight(); y++) {
                for (int x = 0; x < ia.getWidth(); x++) {
                    if ((ia.getRGB(x, y) & 0xFFFFFF) == (ib.getRGB(x, y) & 0xFFFFFF)) continue;
                    boolean in = false;
                    for (Spec s : specs) {
                        double x0 = (originX + s.x() * k) * guiScale, y0 = (originY + s.y() * k) * guiScale;
                        double w = 156.0 * k * guiScale, h = 66.0 * k * guiScale;
                        if (x >= x0 && x < x0 + w && y >= y0 && y < y0 + h) { in = true; break; }
                    }
                    if (in) inside++; else outside++;
                }
            }
            return new int[]{inside, outside};
        }
    }

    /**
     * Fishopedia portrait (bubbles, plates, front layer, toggle) inside the live encyclopedia screen at
     * 1080p with GUI scale 4 (k=1) and 2 (k=2). Canvas A shows the six themes; canvas B shows a muted
     * (uncaught) and a caught ocean portrait and runs the interaction checks.
     */
    static void queuePortraitScene() {
        record Config(int windowW, int windowH, int guiScale) {}
        List<Config> configs = List.of(new Config(1920, 1080, 4), new Config(1920, 1080, 2));
        List<PortraitCanvas.Spec> layoutA = List.of(
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.OCEAN, false, "moorish_idol", 40, 12),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.RIVER, false, "bluegill", 220, 12),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.DEEP_OCEAN, false, "glass_squid", 40, 84),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.CAVE, false, "blind_cavefish", 220, 84),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.HIGH_ALTITUDE, false, "golden_trout", 40, 156),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.NETHER, false, "molten_moorish_idol", 220, 156));
        List<PortraitCanvas.Spec> layoutB = List.of(
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.OCEAN, true, "moorish_idol", 40, 12),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.OCEAN, false, "moorish_idol", 220, 12),
                new PortraitCanvas.Spec(grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme.NETHER, false, "molten_moorish_idol", 40, 84));
        boolean[] origPref = {FishtasticClientConfig.isEncyclopediaPortraitAnimations()};
        PortraitCanvas[] cv = {null};
        int[] fp = {0, 0};
        String playText = Component.translatable("screen.fishtastic.encyclopedia.portrait.play").getString();
        String pauseText = Component.translatable("screen.fishtastic.encyclopedia.portrait.pause").getString();
        queue(1, mc -> {
            savedGuiScale = mc.options.guiScale().get();
            savedWindowWidth = mc.getWindow().getWidth();
            savedWindowHeight = mc.getWindow().getHeight();
            mc.options.hideGui = false;
            origPref[0] = FishtasticClientConfig.isEncyclopediaPortraitAnimations();
            FishtasticClientConfig.setEncyclopediaPortraitAnimations(true);
            grill24.fishtastic.client.encyclopedia.portrait.PortraitBackLayer.setManualStepping(true);
            note("portrait.pref original=" + origPref[0]);
            server(mc, server -> {
                run(server, "gamemode survival @a");
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    FishCatchSavedData.getOrCreate(server).setTutorialStep(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                    grill24.fishtastic.network.TutorialSyncPacket.sendToPlayer(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                }
            });
        });
        for (Config cfg : configs) {
            String tag = "s" + cfg.guiScale();
            for (int layout = 0; layout < 2; layout++) {
                boolean isA = layout == 0;
                String name = (isA ? "A_" : "B_") + tag;
                List<PortraitCanvas.Spec> specs = isA ? layoutA : layoutB;
                queue(5, mc -> {
                    if (mc.player != null) mc.player.closeContainer();
                    mc.getWindow().setWindowed(cfg.windowW(), cfg.windowH());
                    mc.options.guiScale().set(cfg.guiScale());
                    mc.resizeGui();
                });
                queue(10, mc -> server(mc, server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        grill24.fishtastic.compat.GelatinOpenMenuCompat.openFishEncyclopediaMenu(player);
                    }
                }));
                queue(40, mc -> {
                    check("portrait.open." + name, mc.screen instanceof FishEncyclopediaScreen, "screen=" + mc.screen);
                    grill24.fishtastic.client.encyclopedia.portrait.PortraitBackLayer.setManualStepping(true);
                    FishtasticClientConfig.setEncyclopediaPortraitAnimations(true);
                    cv[0] = PortraitCanvas.install(mc.screen, specs);
                    note("portrait.layout " + name + " screen=" + mc.screen.width + "x" + mc.screen.height
                            + " gui=" + (int) mc.getWindow().getGuiScale() + " fb=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                            + " k=" + cv[0].k + " origin=" + cv[0].originX + "," + cv[0].originY);
                    injectCursor(mc, 0.5, 0.5);
                });
                // three deterministic moments: t0 (after open), +1 s, +2 s
                queue(5, mc -> screenshot(mc, "portrait", name + "_t0"));
                queue(3, mc -> cv[0].advanceAll(1.0f));
                queue(5, mc -> screenshot(mc, "portrait", name + "_t1"));
                queue(3, mc -> cv[0].advanceAll(1.0f));
                queue(5, mc -> screenshot(mc, "portrait", name + "_t2"));
                queue(40, mc -> {
                    try {
                        File dir = new File(mc.gameDirectory, "screenshots");
                        double g = mc.getWindow().getGuiScale();
                        int[] d01 = cv[0].diff(new File(dir, fileName("portrait", name + "_t0")), new File(dir, fileName("portrait", name + "_t1")), g);
                        int[] d12 = cv[0].diff(new File(dir, fileName("portrait", name + "_t1")), new File(dir, fileName("portrait", name + "_t2")), g);
                        check("portrait.clip." + name, d01 != null && d12 != null && d01[1] == 0 && d12[1] == 0,
                                "t0->t1 inside=" + (d01 == null ? -1 : d01[0]) + " outside=" + (d01 == null ? -1 : d01[1])
                                        + " t1->t2 inside=" + (d12 == null ? -1 : d12[0]) + " outside=" + (d12 == null ? -1 : d12[1]));
                        check("portrait.animates." + name, d01 != null && d01[0] > 0 && d12 != null && d12[0] > 0,
                                "pixels inside plates changed between captures");
                    } catch (java.io.IOException | RuntimeException e) {
                        check("portrait.clip." + name, false, "analysis failed: " + e);
                    }
                    StringBuilder peaks = new StringBuilder();
                    boolean ok = true;
                    for (int i = 0; i < cv[0].portraits.size(); i++) {
                        var m = cv[0].portraits.get(i).model();
                        peaks.append(m.theme()).append('=').append(m.peak()).append(' ');
                        ok &= m.peak() <= (isA ? 32 : 56) && m.alive() <= 56;
                    }
                    check("portrait.peak." + name, ok, peaks.toString().trim() + " (cap " + (isA ? 32 : 56) + ")");
                });
                if (isA) continue;

                // ---- canvas B: interactions (portrait 0 = uncaught ocean, 1 = caught ocean, 2 = nether)
                queue(3, mc -> {
                    var c = cv[0];
                    var m0 = c.portraits.get(0).model();
                    int before = m0.alive();
                    c.ui.onMouseClick(c.px(0, 5), c.py(0, 50), 0);
                    int after = m0.alive();
                    check("portrait.click_plume." + tag, after > before, "alive " + before + " -> " + after + " (empty plate corner)");
                    c.ui.onMouseClick(c.px(0, 5), c.py(0, 50), 0);
                    check("portrait.click_cooldown." + tag, m0.alive() == after, "second click in the same instant adds nothing");
                    var m1 = c.portraits.get(1).model();
                    long frontBefore = m1.bubbles().stream().filter(b -> b.front).count();
                    int aliveBefore = m1.alive();
                    c.ui.onMouseClick(c.px(1, 46 + 32), c.py(1, 33), 0);
                    long frontAfter = m1.bubbles().stream().filter(b -> b.front).count();
                    check("portrait.click_fish_slot_front." + tag, m1.alive() > aliveBefore && frontAfter - frontBefore >= m1.alive() - aliveBefore,
                            "alive " + aliveBefore + " -> " + m1.alive() + " front " + frontBefore + " -> " + frontAfter);
                    var m2 = c.portraits.get(2).model();
                    c.ui.onMouseClick(c.px(2, 100), c.py(2, 40), 0);
                    check("portrait.click_nether_embers." + tag, m2.bubbles().stream().filter(b -> b.kind == grill24.fishtastic.client.encyclopedia.portrait.PortraitBubbles.Kind.EMBER && b.size == 2).count() >= 10,
                            "hot sparks alive=" + m2.alive());
                    c.advanceAll(0.4f);
                    int a2 = m0.alive();
                    c.ui.onMouseClick(c.px(0, 5), c.py(0, 50), 0);
                    check("portrait.click_after_cooldown." + tag, m0.alive() > a2, "alive " + a2 + " -> " + m0.alive());
                    c.advanceAll(0.5f);
                });
                queue(5, mc -> screenshot(mc, "portrait", "B_" + tag + "_clicks"));
                queue(3, mc -> {
                    var c = cv[0];
                    c.ui.onMouseClick(c.px(1, 5), c.py(1, 50), 0);   // keep a small plume alive in the toggle shots
                    injectCursor(mc, c.px(1, 156 - 11 + 4.5f), c.py(1, 2 + 4.5f));
                });
                queue(10, mc -> {
                    var c = cv[0];
                    var tip = c.portraits.get(1).toggle();
                    check("portrait.tooltip." + tag, c.ui.getTooltip() != null, "tooltip=" + c.ui.getTooltip());
                    check("portrait.tooltip_scale." + tag, Math.abs(tip.tooltipContent().getGlobalScale() - c.k) < 0.01f,
                            "content global scale=" + tip.tooltipContent().getGlobalScale() + " k=" + c.k);
                    check("portrait.tooltip_text_pause." + tag, pauseText.equals(tip.tooltipLine1()), "line1=" + tip.tooltipLine1());
                    screenshot(mc, "portrait", "B_" + tag + "_toggle_hover");
                });
                // toggle: flips the pref, freezes the model, restores; run with real stepping
                queue(5, mc -> {
                    grill24.fishtastic.client.encyclopedia.portrait.PortraitBackLayer.setManualStepping(false);
                    var c = cv[0];
                    c.ui.onMouseClick(c.px(1, 156 - 11 + 4.5f), c.py(1, 2 + 4.5f), 0);
                    check("portrait.toggle_flips_pref." + tag, !FishtasticClientConfig.isEncyclopediaPortraitAnimations(),
                            "pref=" + FishtasticClientConfig.isEncyclopediaPortraitAnimations());
                    check("portrait.tooltip_text_play." + tag, playText.equals(c.portraits.get(1).toggle().tooltipLine1()),
                            "line1=" + c.portraits.get(1).toggle().tooltipLine1());
                });
                queue(5, mc -> fp[0] = PortraitCanvas.fingerprint(cv[0].portraits.get(1).model()));
                queue(15, mc -> {
                    var c = cv[0];
                    fp[1] = PortraitCanvas.fingerprint(c.portraits.get(1).model());
                    check("portrait.paused_freezes." + tag, fp[0] == fp[1] && c.portraits.get(1).model().paused() && c.portraits.get(0).model().paused(),
                            "fingerprint " + fp[0] + " vs " + fp[1] + " (both portraits paused)");
                    int before = c.portraits.get(0).model().alive();
                    c.ui.onMouseClick(c.px(0, 5), c.py(0, 50), 0);
                    check("portrait.paused_click_noop." + tag, c.portraits.get(0).model().alive() == before, "alive " + before + " -> " + c.portraits.get(0).model().alive());
                    screenshot(mc, "portrait", "B_" + tag + "_paused");
                });
                queue(5, mc -> {
                    var c = cv[0];
                    c.ui.onMouseClick(c.px(1, 156 - 11 + 4.5f), c.py(1, 2 + 4.5f), 0);
                    check("portrait.toggle_restores_pref." + tag, FishtasticClientConfig.isEncyclopediaPortraitAnimations(),
                            "pref=" + FishtasticClientConfig.isEncyclopediaPortraitAnimations());
                    fp[0] = PortraitCanvas.fingerprint(c.portraits.get(1).model());
                });
                queue(20, mc -> {
                    var c = cv[0];
                    int now = PortraitCanvas.fingerprint(c.portraits.get(1).model());
                    check("portrait.resumes." + tag, now != fp[0] && !c.portraits.get(1).model().paused(), "fingerprint " + fp[0] + " -> " + now);
                    check("portrait.tooltip_text_back_to_pause." + tag, pauseText.equals(c.portraits.get(1).toggle().tooltipLine1()),
                            "line1=" + c.portraits.get(1).toggle().tooltipLine1());
                    injectCursor(mc, 0.5, 0.5);
                });
            }
        }
        queue(5, mc -> {
            FishtasticClientConfig.setEncyclopediaPortraitAnimations(origPref[0]);
            grill24.fishtastic.client.encyclopedia.portrait.PortraitBackLayer.setManualStepping(false);
            check("portrait.pref_restored", FishtasticClientConfig.isEncyclopediaPortraitAnimations() == origPref[0], "pref=" + origPref[0]);
            if (mc.player != null) mc.player.closeContainer();
            mc.options.guiScale().set(savedGuiScale);
            mc.getWindow().setWindowed(savedWindowWidth, savedWindowHeight);
            mc.resizeGui();
        });
    }

    /**
     * Puts the cursor at a GUI-coordinate point by writing MouseHandler's position directly.
     * glfwSetCursorPos only works while the window has input focus (it fails silently otherwise),
     * which made the hover check depend on whatever the person at the machine was doing.
     */
    private static void injectCursor(Minecraft mc, double guiX, double guiY) {
        try {
            double scale = mc.getWindow().getGuiScale();
            var x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, guiX * scale);
            y.setDouble(mc.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void queueTutorialScene() {
        int[] session = {-1};
        queue(1, mc -> {
            testingHelpMenus = true;
            mc.options.hideGui = false;
            FishtasticKeyBinds.openQuestLog.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            FishtasticKeyBinds.openFishEncyclopedia.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            server(mc, server -> {
                run(server, "gamemode survival @a");
                run(server, "clear @a");
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    var data = FishCatchSavedData.getOrCreate(server);
                    data.setTutorialStep(player, grill24.fishtastic.tutorial.TutorialStep.NOT_STARTED);
                    data.getOrCreateQuestState(player).resetProgress(grill24.fishtastic.tutorial.TutorialManager.TUTORIAL_QUEST_KEY);
                    player.getInventory().setItem(0, item("copper_fishing_rod"));
                    grill24.fishtastic.tutorial.TutorialManager.onItemCrafted(player);
                }
            });
        });
        queue(20, mc -> {
            check("tutorial.offer", mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "First offer");
            screenshot(mc, "tutorial", "offer");
            pressHelpButton(mc, "tutorial.fishtastic.fishing_offer.later");
        });
        queue(10, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        });
        queue(15, mc -> {
            check("tutorial.dismiss_persists", mc.screen == null
                    && grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.DEFERRED, "Casting must not reopen the offer");
            mc.setScreen(new PauseScreen(true));
        });
        queue(5, mc -> {
            screenshot(mc, "tutorial", "pause_help");
            pressHelpButton(mc, "tutorial.fishtastic.help.title");
        });
        queue(5, mc -> {
            check("tutorial.help_accessible", mc.screen instanceof grill24.fishtastic.client.TutorialHelpScreen, "No custom keys required");
            screenshot(mc, "tutorial", "help");
            pressHelpButton(mc, "tutorial.fishtastic.help.fishing");
        });
        queue(15, mc -> {
            check("tutorial.replay", grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.BAIT_LOAD, "Explicit practice");
            mc.setScreen(new InventoryScreen(mc.player));
        });
        queue(10, mc -> screenshot(mc, "tutorial", "bait"));
        queue(1, mc -> {
            mc.screen.onClose();
            server(mc, server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                grill24.fishtastic.item.FishtasticFishingRodItem.setBait(player.getInventory().getItem(0), item("worms"));
                grill24.fishtastic.tutorial.TutorialManager.onBaitLoaded(player);
                grill24.fishtastic.tutorial.TutorialManager.onHookCast(player, player.getInventory().getItem(0));
                session[0] = grill24.fishtastic.server.FishingMinigameManager.get(player.level()).startTutorialSession(player);
            });
        });
        queue(20, mc -> {
            var animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
            check("tutorial.immediate_practice", grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.MINIGAME_CATCH
                    && animation instanceof grill24.fishtastic.util.FishingMinigameAnimation fishing
                    && !fishing.getMinigameState().isPaused(), "No acknowledgement or three-second gate");
            screenshot(mc, "tutorial", "practice");
            server(mc, server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                grill24.fishtastic.server.FishingMinigameManager.get(player.level())
                        .handleMinigameComplete(player, session[0], List.of(0));
            });
        });
        queue(15, mc -> {
            check("tutorial.ends_on_catch", grill24.fishtastic.client.TutorialClientHandler.getCurrentStep()
                    == grill24.fishtastic.tutorial.TutorialStep.COMPLETE, "No quest/shop/encyclopedia chain");
            check("tutorial.keeps_animation", ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation()
                    instanceof grill24.fishtastic.util.FishingMinigameAnimation, "Completion must not cancel the catch animation");
            ((IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
            grill24.fishtastic.client.FishingMinigameClientHandler.cancel();
            useTutorialBook(mc, FishtasticItems.QUEST_BOOK.value());
        });
        queue(20, mc -> {
            check("tutorial.rewards_without_lesson", mc.screen instanceof QuestLogScreen
                    && grill24.fishtastic.client.QuestClientCache.getProgress(
                    grill24.fishtastic.tutorial.TutorialManager.TUTORIAL_QUEST_KEY.identifier()).completed(),
                    "First Catch reward is ready after the lesson ends");
            try {
                var field = QuestLogScreen.class.getDeclaredField("questRowRefs");
                field.setAccessible(true);
                check("tutorial.reward_row_visible", ((java.util.Map<?, ?>) field.get(mc.screen)).containsKey(
                        grill24.fishtastic.tutorial.TutorialManager.TUTORIAL_QUEST_KEY.identifier()), "Reward row remains visible");
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            screenshot(mc, "tutorial", "reward");
            mc.screen.onClose();
            mc.setScreen(new PauseScreen(true));
        });
        queue(5, mc -> pressHelpButton(mc, "tutorial.fishtastic.help.title"));
        queue(5, mc -> pressHelpButton(mc, "tutorial.fishtastic.help.encyclopedia"));
        queue(20, mc -> {
            check("tutorial.encyclopedia_hint", mc.screen instanceof FishEncyclopediaScreen
                    && grill24.fishtastic.client.EncyclopediaTutorialClientHandler.hasActiveLesson(), "One optional hint");
            screenshot(mc, "tutorial", "encyclopedia_hint");
            pressHelpButton(mc, "tutorial.fishtastic.dismiss");
        });
        queue(10, mc -> {
            check("tutorial.hint_dismissed", !grill24.fishtastic.client.EncyclopediaTutorialClientHandler.hasActiveLesson(), "Explicit dismissal");
            mc.screen.onClose();
            testingHelpMenus = false;
        });
    }

    private static void useTutorialBook(Minecraft mc, net.minecraft.world.item.Item book) {
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getItem(i).is(book)) continue;
            mc.player.getInventory().setSelectedSlot(i);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
        check("tutorial.has_book", false, "missing " + book);
    }

}
