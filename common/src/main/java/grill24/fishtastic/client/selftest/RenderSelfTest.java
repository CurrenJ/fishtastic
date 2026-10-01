package grill24.fishtastic.client.selftest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticHudLayers;
import grill24.fishtastic.client.ItemHighlightRules;
import grill24.fishtastic.client.QuestProgressEvent;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.client.renderer.FishtasticItemOutlineAtlas;
import grill24.fishtastic.client.renderer.FishtasticShaders;
import grill24.fishtastic.client.renderer.IrisCompat;
import grill24.fishtastic.client.renderer.TankInteriorLight;
import grill24.fishtastic.command.CosmeticCaptureSession;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.LightLayer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.fishtank.TankColumns;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.network.CosmeticCaptureSyncPacket;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.util.ItemSizeHelper;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.renderer.TankGroupFlock;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.util.ClientTankGroups;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import java.io.FileInputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * PORT-ONLY dev harness for the 1.21.1 rendering port (A5 and its gate G1,
 * docs/backport-pass2/track-a5-rendering-1.21.1.md): drives an unattended client run that stages
 * rendering scenes, photographs them and quits, so visual checks need nobody at the keyboard.
 * The pattern is the rendering spike's {@code OutlineSpikeSelfTest}.
 *
 * <p>Armed only when {@code <run dir>/fishtastic_render_selftest} exists; inert otherwise. Each
 * non-blank, non-{@code #} line of that file names a scene to run (an empty file runs them all).
 * Screenshots go to {@code <run dir>/screenshots/selftest-<loader>-<scene>-<shot>.png}; checks
 * the harness can make by itself are logged as {@code [selftest] CHECK <name>: PASS|FAIL ...}.
 */
public final class RenderSelfTest {
    private static final String MARKER = "fishtastic_render_selftest";
    private static final String WORLD_NAME = "fishtastic_render_selftest";

    /** Scenes in the order they run. */
    private static final List<String> ALL_SCENES = List.of("tank", "shapes", "stress512", "items", "held", "outline", "fabulous",
            "guiscale", "fixes", "hud", "gizmos", "pertank", "highlight");

    /** Scenes that run only when the marker names them (they need something the default run lacks). */
    private static final List<String> OPT_IN_SCENES = List.of("cosmeticpreview", "columns", "spanpreview", "perfbench", "lighting");

    private static Boolean armed;
    private static Set<String> scenes;
    /** {@code key=value} marker lines, for scenes that take arguments; a key may repeat. */
    private static final java.util.Map<String, List<String>> PARAMS = new java.util.HashMap<>();
    private static String loader;
    private static boolean worldRequested;
    private static int titleTicks;
    private static boolean started;
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static int wait;
    private static BlockPos origin;
    private static TutorialSteps savedTutorialStep;
    private static boolean savedPauseOnLostFocus;

    private record Step(int delayTicks, Consumer<Minecraft> action) {}

    /** Called once per client tick by each loader's client entrypoint. */
    public static void tick(Minecraft mc, String loaderName) {
        if (armed == null) {
            File marker = new File(mc.gameDirectory, MARKER);
            armed = marker.exists();
            if (!armed) return;
            loader = loaderName;
            scenes = readScenes(marker);
            // Unattended: a focus change must not pause the game (the integrated server would stop
            // running the scene commands). Restored in finish().
            savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            Fishtastic.LOGGER.info("[selftest] armed on {}: scenes {}", loader, scenes);
        }
        if (!armed) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);

        if (!worldRequested) {
            if (mc.screen instanceof TitleScreen && ++titleTicks > 40) {
                worldRequested = true;
                createWorld(mc);
            }
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;

        if (!started) {
            started = true;
            origin = mc.player.blockPosition();
            queue(60, RenderSelfTest::prepare);
            for (String scene : scenes) queueScene(scene);
            queue(20, RenderSelfTest::finish);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) return;
        step.action().accept(mc);
        Step next = STEPS.peek();
        if (next != null) wait = next.delayTicks();
    }

    private static Set<String> readScenes(File marker) {
        Set<String> requested = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int eq = s.indexOf('=');
                if (eq > 0) PARAMS.computeIfAbsent(s.substring(0, eq).trim(), k -> new java.util.ArrayList<>()).add(s.substring(eq + 1).trim());
                else requested.add(s);
            }
        } catch (IOException e) {
            Fishtastic.LOGGER.warn("[selftest] could not read marker file", e);
        }
        if (requested.isEmpty()) return new LinkedHashSet<>(ALL_SCENES);
        for (String s : requested) {
            if (!ALL_SCENES.contains(s) && !OPT_IN_SCENES.contains(s)) Fishtastic.LOGGER.warn("[selftest] unknown scene '{}'", s);
        }
        requested.removeIf(s -> !ALL_SCENES.contains(s) && !OPT_IN_SCENES.contains(s));
        return requested;
    }

    private static List<String> params(String key) {
        return PARAMS.getOrDefault(key, List.of());
    }

    private static String param(String key, String fallback) {
        List<String> values = params(key);
        return values.isEmpty() ? fallback : values.get(values.size() - 1);
    }

    private static void queue(int delayTicks, Consumer<Minecraft> action) {
        if (STEPS.isEmpty() && wait == 0) wait = delayTicks;
        STEPS.add(new Step(delayTicks, action));
    }

    // ── Setup and teardown ───────────────────────────────────────────────────

    private static void createWorld(Minecraft mc) {
        Path save = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD_NAME);
        if (Files.exists(save)) {
            try (Stream<Path> walk = Files.walk(save)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                Fishtastic.LOGGER.warn("[selftest] could not delete the old self-test world", e);
            }
        }
        Fishtastic.LOGGER.info("[selftest] creating flat world");
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, new WorldOptions(1L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    /**
     * Quiet world, no tutorial. Vanilla draws its toasts after every mod HUD layer on 1.21.1, so the
     * tutorial's hint toasts would cover our overlays: stop() alone is not enough (the step is
     * recreated from the option), so the option is set to NONE too, and restored in {@link #finish}.
     */
    private static void prepare(Minecraft mc) {
        savedTutorialStep = mc.options.tutorialStep;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.getTutorial().stop();
        mc.getToasts().clear();
        server(mc, s -> {
            run(s, "gamerule sendCommandFeedback false");
            run(s, "time set 6000");
        });
        check("shaders.loaded", FishtasticShaders.outlineBake != null && FishtasticShaders.outlineBakeLegendary != null,
                "outline_bake=" + FishtasticShaders.outlineBake + " legendary=" + FishtasticShaders.outlineBakeLegendary);
        Fishtastic.LOGGER.info("[selftest] origin {} (gui scale {}, window {}x{})", origin,
                mc.getWindow().getGuiScale(), mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    private static void finish(Minecraft mc) {
        mc.options.tutorialStep = savedTutorialStep;
        mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        mc.options.hideGui = false;
        mc.options.save();
        Fishtastic.LOGGER.info("[selftest] complete, stopping");
        mc.stop();
    }

    // ── Scenes ───────────────────────────────────────────────────────────────

    private static void queueScene(String scene) {
        switch (scene) {
            case "tank" -> queueTankScene();
            case "shapes" -> queueShapesScene();
            case "stress512" -> queueStressScene();
            case "items" -> queueItemsScene();
            case "held" -> queueHeldScene();
            case "outline" -> queueOutlineScene();
            case "fabulous" -> queueFabulousScene();
            case "guiscale" -> queueGuiScaleScene();
            case "fixes" -> queueFixesScene();
            case "hud" -> queueHudScene();
            case "gizmos" -> queueGizmosScene();
            case "pertank" -> queuePerTankScene();
            case "highlight" -> queueHighlightScene();
            case "columns" -> queueColumnsScene();
            case "spanpreview" -> queueSpanPreviewScene();
            case "perfbench" -> queuePerfBenchScene();
            case "lighting" -> queueLightingScene();
            case "cosmeticpreview" -> queueCosmeticPreviewScene();
            default -> throw new IllegalArgumentException(scene);
        }
    }

    /**
     * A5.1: a 3-tank group with 12 fish (one legendary), a chest, a lit campfire and a lit-furnace
     * structure; a lone tank with a planted and a benthic creature; a fish pile. Photographed from
     * outside and from inside the middle tank (the tank body is the missing model until A5.2, and
     * its back faces are culled, so the inside view shows the BER alone).
     */
    private static void queueTankScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            run(s, "fill " + (x - 3) + " " + y + " " + (z - 2) + " " + (x + 5) + " " + (y + 3) + " " + (z + 2) + " minecraft:air");
            for (int dx = -1; dx <= 1; dx++) run(s, "setblock " + (x + dx) + " " + y + " " + z + " fishtastic:fish_tank");
            run(s, "setblock " + (x + 3) + " " + y + " " + z + " fishtastic:fish_tank");
            run(s, "setblock " + (x + 3) + " " + y + " " + (z + 2) + " fishtastic:fish_pile");
            // Standalone see-through blocks, for their chunk layers (FishtasticBlockRenderLayers).
            run(s, "setblock " + (x + 3) + " " + (y + 1) + " " + z + " fishtastic:blue_clear_stained_glass");
            run(s, "setblock " + (x - 1) + " " + (y + 1) + " " + z + " fishtastic:clear_glass");
        }));
        queue(5, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            String[] group = {"bluegill", "discus", "lionfish", "betta", "clown_loach", "giraffe_cichlid",
                    "golden_trout", "arctic_char", "greenstripe_barb", "blind_cave_tetra", "lined_seahorse", "glass_catfish"};
            for (int i = 0; i < group.length; i++) {
                FishTankBlockEntity tank = tank(level, new BlockPos(x - 1 + i % 3, y, z));
                ItemStack fish = fish(group[i], 25f + 3f * i);
                if (i == 2) FishtasticItemData.set(fish, FishtasticDataComponents.FISH_QUALITY,
                        new FishQuality(FishQuality.Quality.LEGENDARY));
                check("tank.addItem." + group[i], tank != null && tank.addItem(fish), "");
            }
            FishTankBlockEntity left = tank(level, new BlockPos(x - 1, y, z));
            FishTankBlockEntity middle = tank(level, new BlockPos(x, y, z));
            FishTankBlockEntity right = tank(level, new BlockPos(x + 1, y, z));
            left.setCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH)));
            middle.setCosmetic(new CosmeticGridCell(1, 2), new PlacedCosmetic(
                    Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)));
            right.setStructureCosmetic(new CosmeticGridCell(2, 2), new FishTankBlockEntity.PlacedStructureCosmetic(
                    ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, Ids.of("fishtastic", "dynamic_duo")),
                    Rotation.NONE), List.of(new CosmeticGridCell(2, 2)));

            FishTankBlockEntity lone = tank(level, new BlockPos(x + 3, y, z));
            lone.addItem(fish("starfish", 20f));
            lone.addItem(fish("garden_eel", 40f));
            lone.addItem(fish("plaice", 35f));

            if (level.getBlockEntity(new BlockPos(x + 3, y, z + 2)) instanceof FishPileBlockEntity pile) {
                for (String species : List.of("bluegill", "discus", "lionfish", "betta")) pile.insertSingle(fish(species, 30f));
            }
            check("tank.group.openFaces", middle.getOpenFaces().contains(Direction.EAST)
                    && middle.getOpenFaces().contains(Direction.WEST), "middle=" + middle.getOpenFaces());
        }));
        queue(5, mc -> {
            mc.options.hideGui = true;
            camera(mc, origin.getX() + 1.0, origin.getY() + 2.2, origin.getZ() - 0.6, 180f, 12f);
        });
        queue(60, mc -> screenshot(mc, "tank", "outside"));
        queue(1, mc -> camera(mc, origin.getX() + 0.5, origin.getY() + 0.55, origin.getZ() - 3.08, 180f, 8f));
        queue(40, mc -> screenshot(mc, "tank", "inside_a"));
        queue(10, mc -> screenshot(mc, "tank", "inside_b"));
        queue(1, mc -> camera(mc, origin.getX() + 3.5, origin.getY() + 0.55, origin.getZ() - 3.08, 180f, 8f));
        queue(30, mc -> screenshot(mc, "tank", "lone_inside"));
        queue(1, mc -> camera(mc, origin.getX() + 5.2, origin.getY() + 1.8, origin.getZ() - 0.5, 150f, 45f));
        queue(30, mc -> screenshot(mc, "tank", "lone_and_pile"));
        queue(1, mc -> mc.options.hideGui = false);
    }


    // ── Lighting experiment ──────────────────────────────────────────────────

    /**
     * Opt-in: A/B shots of what lights a tank's interior, for comparing shaders on and off (run it
     * once per Iris setting). Two copies of the same 3-tank group: one outdoors at noon, one sealed
     * in a stone room. The world is frozen once the fish are stocked, so the only thing that differs
     * between two shots of a station is the variable under test: the tank's own light level (0/4/8/15,
     * patched on its block states), the {@link TankInteriorLight} level, the water fill, a
     * sea-lantern "hood" above each tank, and a glowstone-lit vs pitch-dark room. Each shot logs the
     * block light measured in front of the group so the patch is proven to have taken.
     */
    private static void queueLightingScene() {
        final int[] stations = LIGHTING_STATIONS;
        queue(1, mc -> server(mc, s -> {
            int y = origin.getY(), z = origin.getZ() - 8;
            for (int sx : stations) {
                int x = origin.getX() + sx;
                run(s, "fill " + (x - 7) + " " + (y - 1) + " " + (z - 6) + " " + (x + 7) + " " + (y + 7) + " " + (z + 8) + " minecraft:air");
                run(s, "fill " + (x - 7) + " " + (y - 1) + " " + (z - 6) + " " + (x + 7) + " " + (y - 1) + " " + (z + 8) + " minecraft:smooth_stone");
            }
            int rx = origin.getX() + stations[1];
            run(s, "fill " + (rx - 5) + " " + (y - 1) + " " + (z - 3) + " " + (rx + 5) + " " + (y + 5) + " " + (z + 6) + " minecraft:stone_bricks hollow");
            for (int sx : stations) {
                for (int dx = -1; dx <= 1; dx++) run(s, "setblock " + (origin.getX() + sx + dx) + " " + y + " " + z + " fishtastic:fish_tank");
            }
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int y = origin.getY(), z = origin.getZ() - 8;
            for (int sx : stations) {
                int x = origin.getX() + sx;
                FishTankBlockEntity left = tank(level, new BlockPos(x - 1, y, z));
                FishTankBlockEntity middle = tank(level, new BlockPos(x, y, z));
                FishTankBlockEntity right = tank(level, new BlockPos(x + 1, y, z));
                left.setCosmetic(new CosmeticGridCell(0, 1), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3));
                left.setCosmetic(new CosmeticGridCell(2, 0), new PlacedCosmetic(Blocks.TUBE_CORAL_FAN.defaultBlockState()));
                middle.setStructureCosmetic(new CosmeticGridCell(1, 1), new FishTankBlockEntity.PlacedStructureCosmetic(
                        structure("coral_reef_1"), Rotation.NONE), List.of(new CosmeticGridCell(1, 1)));
                right.setCosmetic(new CosmeticGridCell(1, 0), new PlacedCosmetic(Blocks.BRAIN_CORAL.defaultBlockState()));
                stock("lighting.left." + sx, left, fish("moorish_idol", 40f), fish("bluegill", 25f), fish("discus", 22f));
                stock("lighting.middle." + sx, middle, fish("lionfish", 32f), fish("clown_loach", 20f), fish("betta", 12f));
                stock("lighting.right." + sx, right, fish("parrotfish", 38f), fish("neon_goby", 5f), fish("neon_goby", 5.5f), fish("starfish", 20f));
            }
        }));
        queue(5, mc -> check("lighting.iris", true, "shaderPackInUse=" + IrisCompat.isShaderPackInUse()));
        // Let the flocks spread out, then freeze them in place for the rest of the scene.
        queue(160, mc -> server(mc, s -> run(s, "tick freeze")));
        queue(5, mc -> mc.options.hideGui = true);

        // Every shot sets the interior light explicitly: the setting defaults on, and the "before"
        // shots need it off. The player's own value is restored at the end.
        // Outdoors at noon: the sun already lights the interior, so the interior light should not matter.
        lightingShot("out", stations[0], "baseline", 8, mc -> interior(mc, 0));
        lightingShot("out", stations[0], "fill_off", 8, mc -> setFill(false));
        lightingShot("out", stations[0], "i13", 8, mc -> {
            setFill(true);
            interior(mc, 13);
        });

        // Sealed room, no other light: only the tank lights it.
        lightingShot("room", stations[1], "dark_l8", 8, mc -> interior(mc, 0));
        lightingShot("room", stations[1], "dark_l0", 0, mc -> {});
        lightingShot("room", stations[1], "dark_l15", 15, mc -> {});
        // Interior light: the inside is drawn brighter, the room gets only the block's own light.
        lightingShot("room", stations[1], "dark_l8_i13", 8, mc -> interior(mc, 13));
        lightingShot("room", stations[1], "dark_l4_i13", 4, mc -> {});
        lightingShot("room", stations[1], "dark_l0_i13", 0, mc -> {});
        lightingShot("room", stations[1], "dark_l8_i10", 8, mc -> interior(mc, 10));
        lightingShot("room", stations[1], "dark_l8_i15", 8, mc -> interior(mc, 15));
        lightingShot("room", stations[1], "dark_l8_fill_off", 8, mc -> {
            interior(mc, 0);
            setFill(false);
        });
        lightingShot("room", stations[1], "dark_l8_hood", 8, mc -> {
            setFill(true);
            hood(mc, stations[1], "minecraft:sea_lantern");
        });
        lightingShot("room", stations[1], "dark_l0_hood", 0, mc -> {});
        // A lived-in room: glowstone in the ceiling, the case players actually have.
        lightingShot("room", stations[1], "lit_l8", 8, mc -> {
            hood(mc, stations[1], "minecraft:air");
            server(mc, s -> {
                int x = origin.getX() + stations[1], y = origin.getY(), z = origin.getZ() - 8;
                run(s, "setblock " + (x - 2) + " " + (y + 5) + " " + (z + 3) + " minecraft:glowstone");
                run(s, "setblock " + (x + 2) + " " + (y + 5) + " " + (z + 3) + " minecraft:glowstone");
            });
        });
        lightingShot("room", stations[1], "lit_l8_hood", 8, mc -> hood(mc, stations[1], "minecraft:sea_lantern"));
        lightingShot("room", stations[1], "lit_l8_i13", 8, mc -> {
            hood(mc, stations[1], "minecraft:air");
            interior(mc, 13);
        });

        // The real control path: command -> sync packet -> client handler -> config.
        queue(1, mc -> {
            if (savedInterior == null) savedInterior = FishtasticClientConfig.getTankInteriorLight();
            server(mc, s -> run(s, "execute as @p run fishtastic tank interiorlight 7"));
        });
        queue(10, mc -> check("lighting.command", FishtasticClientConfig.getTankInteriorLight() == 7,
                "tankInteriorLight=" + FishtasticClientConfig.getTankInteriorLight()));
        queue(1, mc -> {
            setTankEmission(8);
            if (savedInterior != null) FishtasticClientConfig.setTankInteriorLight(savedInterior);
            remeshTanks(mc);
            if (savedFill != null) FishtasticClientConfig.setTankWaterFillEnabled(savedFill);
            mc.options.hideGui = false;
            server(mc, s -> run(s, "tick unfreeze"));
        });
    }

    private static final int[] LIGHTING_STATIONS = {0, 24};

    /** Applies {@code setup} and the tank light level, waits for relighting, then shoots front and close-up. */
    private static void lightingShot(String where, int stationX, String shot, int emission, Consumer<Minecraft> setup) {
        queue(1, mc -> {
            setup.accept(mc);
            if (setTankEmission(emission)) relightTanks(mc);
        });
        queue(1, mc -> camera(mc, origin.getX() + stationX + 0.5, origin.getY() + 1.4, origin.getZ() - 8 + 4.5, 180f, 8f));
        queue(60, mc -> {
            BlockPos front = new BlockPos(origin.getX() + stationX, origin.getY(), origin.getZ() - 8 + 1);
            check("lighting." + where + "." + shot + ".light", true, "emission=" + emission
                    + " stateEmission=" + grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().defaultBlockState().getLightEmission()
                    + " blockLightInFront=" + mc.level.getBrightness(LightLayer.BLOCK, front)
                    + " skyLightInFront=" + mc.level.getBrightness(LightLayer.SKY, front)
                    + " blockLightAtTank=" + mc.level.getBrightness(LightLayer.BLOCK, front.north()));
            screenshot(mc, "lighting", where + "-" + shot + "-front");
        });
        queue(1, mc -> camera(mc, origin.getX() + stationX + 0.5, origin.getY() + 0.7, origin.getZ() - 8 + 1.9, 180f, 4f));
        queue(30, mc -> screenshot(mc, "lighting", where + "-" + shot + "-close"));
    }

    private static int tankEmission = 8;

    /** Rewrites the cached light emission on every fish tank block state; true when it changed. */
    private static boolean setTankEmission(int emission) {
        if (emission == tankEmission) return false;
        try {
            java.lang.reflect.Field field = BlockBehaviour.BlockStateBase.class.getDeclaredField("lightEmission");
            field.setAccessible(true);
            for (var state : grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().getStateDefinition().getPossibleStates()) {
                field.setInt(state, emission);
            }
            tankEmission = emission;
            return true;
        } catch (ReflectiveOperationException e) {
            check("lighting.emission", false, e.toString());
            return false;
        }
    }

    /**
     * Re-runs block light at every tank on both sides: the server's engine (for the record) and the
     * client's own, which is what chunk meshes and block entities actually sample. The client's
     * sections are then marked dirty so the chunk mesh is rebuilt with the new light.
     */
    private static void relightTanks(Minecraft mc) {
        List<BlockPos> tanks = new java.util.ArrayList<>();
        for (int sx : LIGHTING_STATIONS) {
            for (int dx = -1; dx <= 1; dx++) tanks.add(new BlockPos(origin.getX() + sx + dx, origin.getY(), origin.getZ() - 8));
        }
        server(mc, s -> tanks.forEach(p -> s.overworld().getChunkSource().getLightEngine().checkBlock(p)));
        var clientLight = mc.level.getChunkSource().getLightEngine();
        tanks.forEach(clientLight::checkBlock);
        clientLight.runLightUpdates();
        for (BlockPos p : tanks) mc.levelRenderer.setBlocksDirty(p.getX() - 16, p.getY() - 16, p.getZ() - 16, p.getX() + 16, p.getY() + 16, p.getZ() + 16);
        remeshTanks(mc);
    }

    private static Integer savedInterior;

    /**
     * Sets the {@link TankInteriorLight} level and re-meshes just the scene's tanks (cheaper and
     * quicker to settle than {@link TankInteriorLight#set}'s full re-mesh).
     */
    private static void interior(Minecraft mc, int level) {
        if (savedInterior == null) savedInterior = FishtasticClientConfig.getTankInteriorLight();
        FishtasticClientConfig.setTankInteriorLight(level);
        remeshTanks(mc);
    }

    private static void remeshTanks(Minecraft mc) {
        for (int sx : LIGHTING_STATIONS) {
            int x = origin.getX() + sx, y = origin.getY(), z = origin.getZ() - 8;
            mc.levelRenderer.setBlocksDirty(x - 2, y - 1, z - 1, x + 2, y + 1, z + 1);
        }
    }

    private static void hood(Minecraft mc, int stationX, String block) {
        server(mc, s -> {
            for (int dx = -1; dx <= 1; dx++) {
                run(s, "setblock " + (origin.getX() + stationX + dx) + " " + (origin.getY() + 1) + " " + (origin.getZ() - 8) + " " + block);
            }
        });
    }

    private static Boolean savedFill;

    private static void setFill(boolean enabled) {
        if (savedFill == null) savedFill = FishtasticClientConfig.isTankWaterFillEnabled();
        FishtasticClientConfig.setTankWaterFillEnabled(enabled);
    }

    /**
     * Column cosmetics (docs/fish-tanks.md §6): a 2-wide, 3-storey stack with kelp grown from the
     * bottom sand up through the open floors, and chains, lanterns, dripstone and vines hung from the
     * top lids down through them; beside it a lone tank holding the single-tank maxima (3 kelp, a
     * lantern on 2 chains). Every segment is drawn by the tank it starts in, so the strands should
     * read as continuous across both seams.
     */
    private static void queueColumnsScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            clearVoid(s, x, y, z);
            run(s, "fill " + x + " " + y + " " + z + " " + (x + 1) + " " + (y + 2) + " " + z + " fishtastic:fish_tank");
            run(s, "setblock " + (x + 3) + " " + y + " " + z + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            BlockPos bottom = new BlockPos(x, y, z), top = new BlockPos(x, y + 2, z);
            check("columns.storeys", TankColumns.storeys(level, bottom) == 3, "storeys=" + TankColumns.storeys(level, bottom));
            check("columns.ends", TankColumns.ceilingOf(level, bottom).equals(top) && TankColumns.floorOf(level, top).equals(bottom),
                    "ceilingOf=" + TankColumns.ceilingOf(level, bottom) + " floorOf=" + TankColumns.floorOf(level, top));
            check("columns.room", TankColumns.maxSegments(1) == 3 && TankColumns.maxSegments(3) == 11, "");

            FishTankBlockEntity left = tank(level, bottom), right = tank(level, bottom.east());
            left.setCosmetic(new CosmeticGridCell(0, 1), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 11));
            left.setCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 6));
            right.setCosmetic(new CosmeticGridCell(1, 1), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 9));
            right.setCosmetic(new CosmeticGridCell(2, 0), new PlacedCosmetic(Blocks.SEAGRASS.defaultBlockState()));

            FishTankBlockEntity leftLid = tank(level, top), rightLid = tank(level, top.east());
            leftLid.setCeilingCosmetic(new CosmeticGridCell(2, 0), new PlacedCosmetic(Blocks.LANTERN.defaultBlockState(), 6));
            leftLid.setCeilingCosmetic(new CosmeticGridCell(1, 2), new PlacedCosmetic(Blocks.POINTED_DRIPSTONE.defaultBlockState(), 5));
            rightLid.setCeilingCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.CAVE_VINES.defaultBlockState(), 7));
            rightLid.setCeilingCosmetic(new CosmeticGridCell(2, 1), new PlacedCosmetic(Blocks.SOUL_LANTERN.defaultBlockState(), 2));
            rightLid.setCeilingCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.HANGING_ROOTS.defaultBlockState()));
            rightLid.setCeilingCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.SPORE_BLOSSOM.defaultBlockState()));

            FishTankBlockEntity lone = tank(level, new BlockPos(x + 3, y, z));
            lone.setCosmetic(new CosmeticGridCell(0, 1), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3));
            lone.setCeilingCosmetic(new CosmeticGridCell(2, 1), new PlacedCosmetic(Blocks.LANTERN.defaultBlockState(), 3));
            lone.setCeilingCosmetic(new CosmeticGridCell(1, 2), new PlacedCosmetic(Blocks.WEEPING_VINES.defaultBlockState(), 2));
            lone.setCeilingCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.CHAIN.defaultBlockState(), 2));
        }));
        queue(5, mc -> camera(mc, origin.getX() + 2.0, origin.getY() + 1.5, origin.getZ() - 4 + 5.5, 180f, 0f));
        queue(80, mc -> screenshot(mc, "columns", "front"));
        queue(1, mc -> camera(mc, origin.getX() + 1.0, origin.getY() + 2.6, origin.getZ() - 4 + 2.6, 180f, 10f));
        queue(40, mc -> screenshot(mc, "columns", "upper"));
        queue(1, mc -> camera(mc, origin.getX() + 1.0, origin.getY() + 0.9, origin.getZ() - 4 + 2.6, 180f, -10f));
        queue(40, mc -> screenshot(mc, "columns", "lower"));
        queue(1, mc -> camera(mc, origin.getX() + 3.5, origin.getY() + 0.6, origin.getZ() - 4 + 1.8, 180f, 0f));
        queue(40, mc -> screenshot(mc, "columns", "lone"));

        // Placement preview (CosmeticPlacementPreview): a creative player holding a cosmetic, aimed
        // at a cell. Green = a click would place there, red = refused.
        int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
        // Sea pickle at the lone tank's empty centre cell: green on the sand.
        queue(1, mc -> { mc.options.hideGui = true; aimHolding(mc, x + 3.5, y + 1.9, z + 1.6, x + 3.5, y + 0.125, z + 0.5, "minecraft:sea_pickle"); });
        queue(30, mc -> screenshot(mc, "columns", "preview_floor"));
        // Kelp at the left column's 11-segment strand, already at the 3-storey maximum: red.
        queue(1, mc -> aimHolding(mc, x + 0.2, y + 1.5, z + 1.8, x + 0.21, y + 1.4, z + 0.5, "minecraft:kelp"));
        queue(30, mc -> screenshot(mc, "columns", "preview_kelp_full"));
        // Lantern aimed up at the right column's empty centre lid cell: green under the top lid.
        queue(1, mc -> aimHolding(mc, x + 1.5, y + 1.6, z + 1.6, x + 1.5, y + 2.9375, z + 0.5, "minecraft:lantern"));
        queue(30, mc -> screenshot(mc, "columns", "preview_lid"));
        // Pass-through: from beside the right column, looking west through its glass and down
        // onto the left column's bottom sand. The highlight belongs to the left tank.
        queue(1, mc -> aimHolding(mc, x + 2.6, y + 1.2, z + 0.21, x + 0.5, y + 0.125, z + 0.21, "minecraft:sea_pickle"));
        queue(30, mc -> screenshot(mc, "columns", "preview_passthrough"));
        queue(1, mc -> mc.options.hideGui = false);
    }

    /**
     * Spanning structures (SpanStructures): a 4x2 tank, 2 storeys tall, for the Whale Fall and a
     * 2x2 tank, 3 storeys tall, for the Drowned Pagoda. Each is placed by a creative player
     * right-clicking its item at the bottom sand through the upper storey's glass — the real
     * client interaction path — after a shot of the placement preview (box outline + footprint).
     * Checks that every tank in each box resolves to the span, then photographs both from several
     * angles, and logs FPS on the same view before and after placing, as a rough cost reading.
     */
    private static void queueSpanPreviewScene() {
        int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
        BlockPos whaleMin = new BlockPos(x - 4, y, z), pagodaMin = new BlockPos(x + 3, y, z);
        int[] fps = new int[4];
        queue(1, mc -> server(mc, s -> {
            run(s, "fill " + (x - 8) + " " + (y - 1) + " " + (z - 4) + " " + (x + 8) + " " + (y + 5) + " " + (z + 6) + " minecraft:air");
            run(s, "fill " + whaleMin.getX() + " " + y + " " + z + " " + (whaleMin.getX() + 3) + " " + (y + 1) + " " + (z + 1) + " fishtastic:fish_tank");
            run(s, "fill " + pagodaMin.getX() + " " + y + " " + z + " " + (pagodaMin.getX() + 1) + " " + (y + 2) + " " + (z + 1) + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(whaleMin, whaleMin.offset(3, 1, 1))) {
                FishTankBlockEntity tank = tank(level, pos);
                if (tank != null) tank.setMaterials(materials(new String[]{"minecraft:polished_deepslate", "minecraft:sand", "fishtastic:clear_glass"}));
            }
            for (BlockPos pos : BlockPos.betweenClosed(pagodaMin, pagodaMin.offset(1, 2, 1))) {
                FishTankBlockEntity tank = tank(level, pos);
                if (tank != null) tank.setMaterials(materials(new String[]{"minecraft:dark_oak_planks", "minecraft:sand", "fishtastic:clear_glass"}));
            }
        }));
        // FPS on the final framing, empty tanks — uncapped, so the reading means something.
        int[] savedLimit = new int[1];
        boolean[] savedVsync = new boolean[1];
        queue(20, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            mc.options.hideGui = true;
            camera(mc, x + 0.5, y + 1.6, z + 7.5, 180f, 8f);
        });
        queue(100, mc -> fps[0] = mc.getFps());
        queue(40, mc -> fps[1] = mc.getFps());

        // Whale Fall: aim through the upper storey at the bottom sand of the second tank along.
        queue(1, mc -> aimHolding(mc, whaleMin.getX() + 1.5, y + 2.7, z + 3.2, whaleMin.getX() + 1.5, y + 0.125, z + 0.9, "fishtastic:cosmetic_whale_fall"));
        queue(30, mc -> screenshot(mc, "spanpreview", "whale_preview"));
        queue(1, mc -> {
            if (mc.hitResult instanceof BlockHitResult hit) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            else check("spanpreview.whale.aimed", false, "no block under the crosshair");
        });
        // Drowned Pagoda: aim through the upper storeys at the bottom sand.
        queue(20, mc -> aimHolding(mc, pagodaMin.getX() + 1.0, y + 3.7, z + 3.4, pagodaMin.getX() + 0.8, y + 0.125, z + 0.8, "fishtastic:cosmetic_drowned_pagoda"));
        queue(30, mc -> screenshot(mc, "spanpreview", "pagoda_preview"));
        queue(1, mc -> {
            if (mc.hitResult instanceof BlockHitResult hit) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            else check("spanpreview.pagoda.aimed", false, "no block under the crosshair");
        });
        queue(20, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            checkSpanBox(level, "whale", whaleMin, 4, 2, 2, "whale_fall");
            checkSpanBox(level, "pagoda", pagodaMin, 2, 3, 2, "drowned_pagoda");
            run(s, "item replace entity @a weapon.mainhand with minecraft:air");
        }));

        // Same framing as the empty reading, now with both structures.
        queue(1, mc -> camera(mc, x + 0.5, y + 1.6, z + 7.5, 180f, 8f));
        queue(100, mc -> fps[2] = mc.getFps());
        queue(40, mc -> {
            fps[3] = mc.getFps();
            check("spanpreview.fps", true, "uncapped: empty " + fps[0] + "/" + fps[1] + " fps, with both structures " + fps[2] + "/" + fps[3] + " fps");
            mc.options.framerateLimit().set(savedLimit[0]);
            mc.options.enableVsync().set(savedVsync[0]);
            screenshot(mc, "spanpreview", "both");
        });
        double wx = whaleMin.getX() + 2.0, pzc = pagodaMin.getX() + 1.0;
        Object[][] shots = {
                {"whale_front", wx, y + 1.0, z + 5.2, 180f, 4f},
                {"whale_quarter", wx + 3.6, y + 1.9, z + 4.6, 140f, 18f},
                {"whale_head", whaleMin.getX() - 2.6, y + 1.2, z + 1.0, -90f, 10f},
                {"whale_high", wx - 1.0, y + 3.6, z + 4.4, 200f, 38f},
                {"whale_inside", wx - 0.4, y + 0.55, z + 1.0, -90f, 0f},
                {"pagoda_front", pzc, y + 1.5, z + 5.8, 180f, 2f},
                {"pagoda_quarter", pzc + 3.2, y + 2.4, z + 4.4, 145f, 14f},
                {"pagoda_up", pzc, y + 0.4, z + 3.0, 180f, -38f},
                {"pagoda_high", pzc - 2.4, y + 4.6, z + 4.2, 210f, 32f},
        };
        for (Object[] shot : shots) {
            queue(1, mc -> camera(mc, (double) shot[1], (double) shot[2], (double) shot[3], (float) shot[4], (float) shot[5]));
            queue(40, mc -> screenshot(mc, "spanpreview", (String) shot[0]));
        }

        // Placement facing: a structure's front must turn toward the player who placed it. A skull
        // candle placed from the south, photographed from where that player stood.
        BlockPos lone = new BlockPos(x - 6, y, z + 3);
        queue(1, mc -> server(mc, s -> run(s, "setblock " + lone.getX() + " " + y + " " + lone.getZ() + " fishtastic:fish_tank")));
        queue(10, mc -> aimHolding(mc, lone.getX() + 0.5, y + 1.7, lone.getZ() + 2.2, lone.getX() + 0.5, y + 0.125, lone.getZ() + 0.5, "fishtastic:cosmetic_wax_skull_candle"));
        queue(20, mc -> {
            if (mc.hitResult instanceof BlockHitResult hit) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        });
        queue(20, mc -> server(mc, s -> {
            FishTankBlockEntity tank = tank(s.overworld(), lone);
            var placed = tank == null ? null : tank.getStructureCosmetics().values().stream().findFirst().orElse(null);
            check("spanpreview.facesPlayer", placed != null && placed.rotation() == Rotation.NONE,
                    placed == null ? "skull candle not placed" : "placed facing north (from the south) got rotation " + placed.rotation());
        }));
        queue(1, mc -> camera(mc, lone.getX() + 0.5, y + 0.6, lone.getZ() + 1.6, 180f, 5f));
        queue(30, mc -> screenshot(mc, "spanpreview", "skull_facing"));

        // Tinted cosmetics in the chunk mesh must take their own tint (leaves green, not grey):
        // an oak and a birch tree, plus seagrass, coral, pickles and a chest (still per-frame).
        BlockPos grove = lone.east(2);
        queue(1, mc -> server(mc, s -> run(s, "fill " + grove.getX() + " " + y + " " + grove.getZ() + " " + (grove.getX() + 1) + " " + y + " " + grove.getZ() + " fishtastic:fish_tank")));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            FishTankBlockEntity a = tank(level, grove), b = tank(level, grove.east());
            if (a == null || b == null) { check("spanpreview.grove", false, "tanks missing"); return; }
            placeStructure(a, "oak_tree", 1, 1);
            placeStructure(b, "birch_tree", 1, 1);
            a.setCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.SEAGRASS.defaultBlockState()));
            a.setCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3)));
            b.setCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.TUBE_CORAL_FAN.defaultBlockState()));
            b.setCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.CHEST.defaultBlockState()));
        }));
        queue(1, mc -> camera(mc, grove.getX() + 1.0, y + 0.8, grove.getZ() + 2.4, 180f, 8f));
        queue(40, mc -> screenshot(mc, "spanpreview", "grove"));
        queue(1, mc -> mc.options.hideGui = false);
    }

    // ── perfbench: cosmetic rendering, chunk mesh vs per-frame ──────────────────────────────

    /** One benchmark workload: builds its tanks and cosmetics, returns the tanks it made; and where to look from. */
    private record BenchScene(String name, java.util.function.Function<ServerLevel, List<BlockPos>> build,
                              double camX, double camY, double camZ, float yaw, float pitch) {}

    /**
     * Measures cosmetic rendering cost (see {@link grill24.fishtastic.client.perf.CosmeticBenchmark}).
     * For each workload, under each mode — NONE (no cosmetics: the baseline), PER_FRAME (the previous
     * path: every cosmetic block submitted every frame) and MESH (baked into the chunk mesh) — in a
     * rotating order over {@code rounds=} rounds: switch mode, re-mesh every section and wait until
     * all are built, warm up, then record {@code frames=} frames uncapped with vsync off. Logs one
     * {@code [bench] RESULT} line per measurement and one {@code [bench] BAKE} line per mesh build;
     * raw per-frame samples go to {@code <run>/fishtastic_bench/}. Marker params: {@code frames=},
     * {@code rounds=}, {@code label=} (e.g. the shader setting, for the file names).
     */
    private static void queuePerfBenchScene() {
        int frames = Integer.parseInt(param("frames", "3000"));
        int rounds = Integer.parseInt(param("rounds", "2"));
        String label = param("label", "default");
        int x0 = origin.getX() - 9, y = origin.getY(), z0 = origin.getZ() - 4;
        String[] structures = {"castle_ruin", "coral_reef_1", "oak_tree", "birch_tree", "spruce_gazebo", "mossy_boulder"};

        List<BenchScene> scenes = List.of(
                new BenchScene("typical", level -> {
                    List<BlockPos> tanks = new java.util.ArrayList<>();
                    for (int i = 0; i < 24; i++) {
                        BlockPos pos = new BlockPos(x0 + (i % 6) * 2, y, z0 + (i / 6) * 2);
                        level.setBlockAndUpdate(pos, grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().defaultBlockState());
                        FishTankBlockEntity t = tank(level, pos);
                        if (t == null) continue;
                        t.setCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3));
                        t.setCosmetic(new CosmeticGridCell(2, 0), new PlacedCosmetic(Blocks.SEAGRASS.defaultBlockState()));
                        t.setCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3)));
                        t.setCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.TUBE_CORAL_FAN.defaultBlockState()));
                        t.setCosmetic(new CosmeticGridCell(1, 0), new PlacedCosmetic(Blocks.BRAIN_CORAL_FAN.defaultBlockState()));
                        t.setCeilingCosmetic(new CosmeticGridCell(2, 1), new PlacedCosmetic(Blocks.LANTERN.defaultBlockState(), 2));
                        placeStructure(t, structures[i % structures.length], 1, 1);
                        tanks.add(pos);
                    }
                    return tanks;
                }, x0 + 5.5, y + 5.0, z0 - 4.5, 0f, 35f),
                new BenchScene("setpieces", level -> benchSpans(level, x0, y, z0, 1),
                        x0 + 4.0, y + 2.6, z0 - 6.0, 0f, 12f),
                new BenchScene("stress", level -> benchSpans(level, x0, y, z0, 4),
                        x0 + 9.5, y + 3.2, z0 - 9.0, 0f, 12f));

        grill24.fishtastic.client.perf.CosmeticBenchmark.Mode[] modes = grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.values();
        int[] savedLimit = new int[1];
        boolean[] savedVsync = new boolean[1];
        queue(1, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            // (26.1.2 also lifts the AFK frame limiter here; 1.21.1 has none.)
            mc.options.hideGui = true;
            Fishtastic.LOGGER.info("[bench] START loader={} label={} frames={} rounds={} window={}x{}", loader, label, frames, rounds,
                    mc.getWindow().getWidth(), mc.getWindow().getHeight());
        });

        for (BenchScene scene : scenes) {
            List<BlockPos> tanks = new java.util.ArrayList<>();
            queue(1, mc -> server(mc, s -> run(s, "fill " + (x0 - 2) + " " + (y - 1) + " " + (z0 - 2) + " " + (x0 + 22) + " " + (y + 4) + " " + (z0 + 12) + " minecraft:air")));
            queue(5, mc -> server(mc, s -> { tanks.clear(); tanks.addAll(scene.build().apply(s.overworld())); }));
            queue(20, mc -> camera(mc, scene.camX(), scene.camY(), scene.camZ(), scene.yaw(), scene.pitch()));
            queue(20, mc -> screenshot(mc, "perfbench", scene.name()));
            for (int round = 0; round < rounds; round++) {
                for (int k = 0; k < modes.length; k++) {
                    var mode = modes[(k + round) % modes.length];   // rotate the order each round
                    String tag = loader + "_" + label + "_" + scene.name() + "_" + mode.name().toLowerCase(Locale.ROOT) + "_r" + (round + 1);
                    queue(1, mc -> {
                        grill24.fishtastic.client.perf.CosmeticBenchmark.mode = mode;
                        grill24.fishtastic.client.compositemodel.TankCosmeticMesh.clearCache();
                        grill24.fishtastic.client.perf.CosmeticBenchmark.resetBakeStats();
                        for (BlockPos pos : tanks) {
                            if (mc.level.getBlockEntity(pos) instanceof FishTankBlockEntity t) {
                                grill24.fishtastic.architectury.RegistrationApiSided.getInstance().requestModelDataUpdate(t);
                            }
                        }
                        mc.levelRenderer.allChanged();
                    });
                    long[] rebuildStart = new long[1];
                    queue(1, mc -> rebuildStart[0] = System.nanoTime());
                    waitFor(1, 600, mc -> mc.levelRenderer.hasRenderedAllSections(), mc -> {
                        var b = grill24.fishtastic.client.perf.CosmeticBenchmark.class;
                        Fishtastic.LOGGER.info(String.format(Locale.ROOT,
                                "[bench] BAKE %s rebuild_wait_ms=%.0f snapshots=%d snapshot_ms_total=%.2f bakes=%d bake_ms_total=%.2f pieces=%d quads=%d",
                                tag, (System.nanoTime() - rebuildStart[0]) / 1e6,
                                grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotCount.get(),
                                grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotNanos.get() / 1e6,
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakeCount.get(),
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakeNanos.get() / 1e6,
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakedPieces.get(),
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakedQuads.get()));
                    });
                    queue(40, mc -> grill24.fishtastic.client.perf.CosmeticBenchmark.start(frames));
                    waitFor(1, 4000, mc -> {
                        grill24.fishtastic.client.perf.CosmeticBenchmark.drain();
                        return grill24.fishtastic.client.perf.CosmeticBenchmark.isDone();
                    }, mc -> Fishtastic.LOGGER.info("[bench] RESULT {} {}", tag, grill24.fishtastic.client.perf.CosmeticBenchmark.finish(tag)));
                }
            }
        }
        queue(1, mc -> {
            grill24.fishtastic.client.perf.CosmeticBenchmark.mode = grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.MESH;
            mc.options.framerateLimit().set(savedLimit[0]);
            mc.options.enableVsync().set(savedVsync[0]);
            mc.options.hideGui = false;
            mc.levelRenderer.allChanged();
            Fishtastic.LOGGER.info("[bench] DONE");
        });
    }

    /** {@code count} Whale Falls in a row and {@code count} Drowned Pagodas in a row behind them, placed directly. */
    private static List<BlockPos> benchSpans(ServerLevel level, int x0, int y, int z0, int count) {
        List<BlockPos> tanks = new java.util.ArrayList<>();
        var tankState = grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().defaultBlockState();
        for (int i = 0; i < count; i++) {
            BlockPos whale = new BlockPos(x0 + i * 5, y, z0);
            BlockPos pagoda = new BlockPos(x0 + i * 3, y, z0 + 4);
            for (BlockPos pos : BlockPos.betweenClosed(whale, whale.offset(3, 1, 1))) { level.setBlockAndUpdate(pos, tankState); tanks.add(pos.immutable()); }
            for (BlockPos pos : BlockPos.betweenClosed(pagoda, pagoda.offset(1, 2, 1))) { level.setBlockAndUpdate(pos, tankState); tanks.add(pos.immutable()); }
        }
        var registry = level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
        for (int i = 0; i < count; i++) {
            benchPlaceSpan(level, registry, new BlockPos(x0 + i * 5, y, z0), "whale_fall");
            benchPlaceSpan(level, registry, new BlockPos(x0 + i * 3, y, z0 + 4), "drowned_pagoda");
        }
        return tanks;
    }

    private static void benchPlaceSpan(ServerLevel level, net.minecraft.core.Registry<grill24.fishtastic.fishtank.CosmeticStructure> registry,
                                       BlockPos min, String name) {
        var structure = registry.getOptional(structure(name)).orElse(null);
        if (structure == null || structure.span().isEmpty()) { check("perfbench.structure." + name, false, "not loaded"); return; }
        var box = grill24.fishtastic.fishtank.SpanStructures.rotated(structure.span().get(), Rotation.NONE);
        grill24.fishtastic.fishtank.SpanStructures.place(level, new grill24.fishtastic.fishtank.SpanStructures.Fit(min, box, null),
                new FishTankBlockEntity.PlacedStructureCosmetic(structure(name), Rotation.NONE), structure);
    }

    /** Every tank in the box at {@code min} must resolve to the span anchored there, holding {@code name}. */
    private static void checkSpanBox(ServerLevel level, String label, BlockPos min, int sx, int sy, int sz, String name) {
        int ok = 0, total = sx * sy * sz;
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(sx - 1, sy - 1, sz - 1))) {
            FishTankBlockEntity tank = tank(level, pos);
            var ref = tank == null ? null : grill24.fishtastic.fishtank.SpanStructures.resolve(level, tank);
            if (ref != null && ref.anchor().getBlockPos().equals(min) && ref.placed().structureId().equals(structure(name))) ok++;
        }
        check("spanpreview." + label + ".placed", ok == total, ok + " of " + total + " tanks resolve to " + name + " anchored at " + min.toShortString());
    }

    /**
     * Stands a creative player (on a barrier, so it doesn't fall) with its eye at the given point,
     * looking at the target point, holding {@code itemId} — for shots of the placement preview,
     * which a spectator never gets because spectators don't target blocks.
     */
    private static void aimHolding(Minecraft mc, double eyeX, double eyeY, double eyeZ,
                                   double targetX, double targetY, double targetZ, String itemId) {
        // Feet snap to a whole block (standing on the barrier), so aim from where the eye really ends up.
        double feetY = Math.floor(eyeY - mc.player.getEyeHeight());
        double realEyeY = feetY + mc.player.getEyeHeight();
        double dx = targetX - eyeX, dy = targetY - realEyeY, dz = targetZ - eyeZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        server(mc, s -> {
            run(s, "gamemode creative @a");
            run(s, String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier",
                    (int) Math.floor(eyeX), (int) feetY - 1, (int) Math.floor(eyeZ)));
            run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", eyeX, feetY, eyeZ, yaw, pitch));
            run(s, "item replace entity @a weapon.mainhand with " + itemId);
        });
    }

    private static void placeStructure(FishTankBlockEntity tank, String name, int gx, int gz) {
        CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
        tank.setStructureCosmetic(cell, new FishTankBlockEntity.PlacedStructureCosmetic(structure(name), Rotation.NONE), List.of(cell));
    }

    /** Empties the flat world's ground round the scene, so nothing but the subject is in frame. */
    private static void stock(String label, FishTankBlockEntity tank, ItemStack... fish) {
        int added = 0;
        for (ItemStack f : fish) if (tank != null && tank.addItem(f)) added++;
        check(label + ".stocked", added == fish.length, added + " of " + fish.length + " fish added");
    }

    private static void clearVoid(MinecraftServer s, int x, int y, int z) {
        int[][] halves = {{-72, -1}, {0, 71}};
        for (int[] hx : halves) {
            for (int[] hz : halves) {
                run(s, "fill " + (x + hx[0]) + " " + (y - 4) + " " + (z + hz[0]) + " " + (x + hx[1]) + " " + (y - 1) + " " + (z + hz[1]) + " minecraft:air");
            }
        }
        run(s, "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 6) + " " + (y + 4) + " " + (z + 3) + " minecraft:air");
    }

    private static ResourceKey<grill24.fishtastic.fishtank.CosmeticStructure> structure(String name) {
        return ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, Ids.of("fishtastic", name));
    }

    /** Polls {@code done} every {@code everyTicks} until it holds or {@code timeoutTicks} pass, then runs {@code then}. */
    private static void waitFor(int everyTicks, int timeoutTicks, java.util.function.Predicate<Minecraft> done, Consumer<Minecraft> then) {
        int[] waited = {0};
        Consumer<Minecraft>[] poll = new Consumer[1];
        poll[0] = mc -> {
            waited[0] += everyTicks;
            if (done.test(mc) || waited[0] >= timeoutTicks) {
                then.accept(mc);
            } else {
                STEPS.addFirst(new Step(everyTicks, poll[0]));
            }
        };
        queue(everyTicks, poll[0]);
    }

    // ── Cosmetic authoring (the cosmetic-from-description skill) ───────────────

    /** Where {@code mcpsession} writes the bridge's port and token, in the node bridge's .env format. */
    private static final String MCP_SESSION_FILE = "fishtastic_mcp_session.env";
    /** Created by the driving agent to end an {@code mcpsession}. */
    private static final String MCP_SESSION_DONE = "fishtastic_mcp_session_done";
    /** Half-width of the cleared, region-fenced build site, and its height. */
    private static final int BUILD_SITE_RADIUS = 10, BUILD_SITE_HEIGHT = 14;

    /**
     * Opt-in: for each structure named by a {@code cosmetic=<name>} marker line, places it in a lone
     * SKYLIGHT tank (glass roof, so the high orbit sees in) and, on 26.1.2, takes two frozen 8-frame
     * alpha orbits with cool-cam (not on this branch: one plain shot instead). Checks the
     * structure loaded from the datapack, that its footprint fits the grid at all four rotations, and,
     * informationally, whether {@code cosmetic_<name>} is registered as an item yet.
     */
    private static void queueCosmeticPreviewScene() {
        List<String> names = params("cosmetic");
        check("cosmeticpreview.names", !names.isEmpty(), "marker names " + names);
        queue(1, mc -> {
            savedRenderDistance = mc.options.renderDistance().get();
            mc.options.renderDistance().set(3);
            mc.options.hideGui = true;
            server(mc, s -> clearVoid(s, origin.getX(), origin.getY(), origin.getZ() - 4));
            camera(mc, origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ(), 180f, 10f);
        });
        for (String name : names) {
            queue(10, mc -> server(mc, s -> {
                BlockPos pos = new BlockPos(origin.getX(), origin.getY(), origin.getZ() - 4);
                run(s, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:air");
                ServerLevel level = s.overworld();
                ResourceKey<grill24.fishtastic.fishtank.CosmeticStructure> key = structure(name);
                var found = level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY).getOptional(key);
                check("cosmeticpreview." + name + ".loaded", found.isPresent(), found.map(st -> st.parts().size() + " parts, scale " + st.scale()
                        + ", footprint " + st.footprintCells().size() + " cell(s)").orElse("not in the datapack registry"));
                boolean item = BuiltInRegistries.ITEM.containsKey(Ids.of("fishtastic", "cosmetic_" + name));
                check("cosmeticpreview." + name + ".itemRegistered", item, item ? "" : "(expected before the item is wired up)");
                if (found.isEmpty()) return;
                for (Rotation rotation : Rotation.values()) {
                    check("cosmeticpreview." + name + ".fits." + rotation.name().toLowerCase(Locale.ROOT), footprintAt(found.get(), rotation) != null, "");
                }
                run(s, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " fishtastic:fish_tank");
                FishTankBlockEntity tank = tank(level, pos);
                List<CosmeticGridCell> cells = footprintAt(found.get(), Rotation.NONE);
                if (tank != null && cells != null) {
                    tank.setShape(FishTankShape.SKYLIGHT);
                    tank.setStructureCosmetic(cells.get(0), new FishTankBlockEntity.PlacedStructureCosmetic(key, Rotation.NONE), cells);
                }
            }));
            // PORT-ONLY: 26.1.2 takes two cool-cam alpha orbits here; cool-cam isn't ported (owner
            // decision D4), so this branch keeps only the checks and the item shots.
            queue(40, mc -> screenshot(mc, "cosmeticpreview", name + "_tank"));
            queueCosmeticItemChecks(name);
        }
        queue(10, mc -> {
            mc.options.renderDistance().set(savedRenderDistance);
            mc.options.hideGui = false;
        });
    }

    /**
     * The item side of a cosmetic, checked in game: {@code cosmetic_<name>} targets the structure, has
     * a lang name, and has a loaded shop entry that sells it; then a creative player standing on a
     * SKYLIGHT tank right-clicks the item into it through the real client interaction path, the server
     * is checked for the placed structure. Shots, looking down through the roof: {@code <name>_held}
     * (item in hand and hotbar, before the click) and {@code <name>_placed} (after). Skipped while the item isn't registered yet.
     */
    private static void queueCosmeticItemChecks(String name) {
        ResourceLocation itemId = Ids.of("fishtastic", "cosmetic_" + name);
        BlockPos pos = new BlockPos(origin.getX() + 3, origin.getY(), origin.getZ() - 4);
        boolean[] registered = {false};
        queue(5, mc -> {
            registered[0] = BuiltInRegistries.ITEM.containsKey(itemId);
            if (!registered[0]) return;
            var item = BuiltInRegistries.ITEM.get(itemId);
            boolean targets = item instanceof grill24.fishtastic.item.FishTankStructureCosmeticItem cosmetic
                    && cosmetic.getStructureId().equals(structure(name));
            check("cosmeticpreview." + name + ".itemTargetsStructure", targets, item.getClass().getSimpleName());
            String langKey = "item.fishtastic.cosmetic_" + name;
            boolean named = net.minecraft.locale.Language.getInstance().has(langKey);
            check("cosmeticpreview." + name + ".langName", named, named ? net.minecraft.locale.Language.getInstance().getOrDefault(langKey) : "missing " + langKey);
            server(mc, s -> {
                var entry = s.registryAccess().registryOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY)
                        .getOptional(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, itemId));
                boolean sells = entry.isPresent() && entry.get().cost() > 0
                        && entry.get().reward().stream().anyMatch(r -> r.itemId().equals(itemId));
                check("cosmeticpreview." + name + ".shopEntry", sells, entry.map(e -> "'" + e.displayName() + "' for " + e.cost()
                        + ", daily max " + e.dailyMaxPurchases() + ", weight " + e.weight()).orElse("no shop_entry/cosmetic_" + name + ".json loaded"));
                run(s, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:air");
                run(s, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " fishtastic:fish_tank");
                FishTankBlockEntity tank = tank(s.overworld(), pos);
                if (tank != null) tank.setShape(FishTankShape.SKYLIGHT);
                run(s, "gamemode creative @a");
                run(s, "item replace entity @a weapon.mainhand with " + itemId);
                run(s, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 180 90", pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
            });
        });
        queue(20, mc -> {
            if (!registered[0]) return;
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 0;
            mc.gui.getChat().clearMessages(false);
        });
        queue(10, mc -> {
            if (!registered[0]) return;
            screenshot(mc, "cosmeticpreview", name + "_held");
        });
        queue(5, mc -> {
            if (!registered[0]) return;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false));
        });
        queue(20, mc -> {
            if (!registered[0]) return;
            mc.gui.getChat().clearMessages(false);
            screenshot(mc, "cosmeticpreview", name + "_placed");
            server(mc, s -> {
                FishTankBlockEntity tank = tank(s.overworld(), pos);
                boolean placed = tank != null && tank.getStructureCosmetics().values().stream()
                        .anyMatch(c -> c.structureId().equals(structure(name)));
                check("cosmeticpreview." + name + ".placesFromItem", placed, placed ? "placed by right-click" : "no structure in the tank after useItemOn");
                run(s, "item replace entity @a weapon.mainhand with minecraft:air");
                run(s, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:air");
            });
        });
        queue(5, mc -> mc.options.hideGui = true);
    }

    /**
     * The anchor (first) and the other cells {@code structure} occupies at {@code rotation}, for the
     * first anchor where every rotated cell lands on the grid — the centre is tried first — or null.
     */
    private static List<CosmeticGridCell> footprintAt(grill24.fishtastic.fishtank.CosmeticStructure structure, Rotation rotation) {
        int[][] anchors = {{1, 1}, {0, 0}, {1, 0}, {0, 1}, {2, 2}, {2, 1}, {1, 2}, {2, 0}, {0, 2}};
        for (int[] a : anchors) {
            List<CosmeticGridCell> cells = new java.util.ArrayList<>();
            cells.add(new CosmeticGridCell(a[0], a[1]));
            boolean fits = true;
            for (var offset : structure.footprintCells()) {
                var r = grill24.fishtastic.fishtank.CosmeticStructures.rotateFootprintCell(rotation, offset);
                int gx = a[0] + r.dx(), gz = a[1] + r.dz();
                if (!CosmeticGridCell.isValid(gx, gz)) { fits = false; break; }
                CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
                if (!cells.contains(cell)) cells.add(cell);
            }
            if (fits) return cells;
        }
        return null;
    }


    /** Frame, sand and glass for the three material sets of the shapes scene. */
    private static final String[][] MATERIAL_SETS = {
            {"minecraft:oak_planks", "minecraft:sand", "fishtastic:blue_clear_stained_glass"},
            {"minecraft:stone_bricks", "minecraft:gravel", "minecraft:glass"},
            // Waxed copper is the blockstate-redirect case (docs/fish-tank-rendering.md).
            {"minecraft:waxed_copper_block", "minecraft:red_sand", "fishtastic:pink_borderless_stained_glass"},
    };

    /**
     * A5.2: every {@link FishTankShape} in each of three material sets (two rows of ten per set),
     * an L-shaped group (diagonal corner fragments), a vertical L (edge fragments), a live
     * material change (re-mesh), and tank items with different shapes and materials in the hotbar.
     */
    private static void queueShapesScene() {
        FishTankShape[] shapes = FishTankShape.values();
        queue(1, mc -> server(mc, s -> {
            int x0 = origin.getX() - 10, y = origin.getY(), z0 = origin.getZ() - 20;
            run(s, "fill " + (x0 - 2) + " " + y + " " + (z0 - 16) + " " + (x0 + 22) + " " + (y + 3) + " " + (z0 + 8) + " minecraft:air");
            for (int set = 0; set < MATERIAL_SETS.length; set++) {
                for (int i = 0; i < shapes.length; i++) {
                    run(s, "setblock " + (x0 + 2 * (i % 10)) + " " + y + " " + (z0 - 6 * set - 2 * (i / 10)) + " fishtastic:fish_tank");
                }
            }
            // L-group (corner fragments) and vertical L (edge fragments), default materials.
            int lx = x0 + 2, lz = z0 + 5;
            run(s, "setblock " + lx + " " + y + " " + lz + " fishtastic:fish_tank");
            run(s, "setblock " + (lx + 1) + " " + y + " " + lz + " fishtastic:fish_tank");
            run(s, "setblock " + lx + " " + y + " " + (lz - 1) + " fishtastic:fish_tank");
            int vx = x0 + 8;
            run(s, "setblock " + vx + " " + y + " " + lz + " fishtastic:fish_tank");
            run(s, "setblock " + (vx + 1) + " " + y + " " + lz + " fishtastic:fish_tank");
            run(s, "setblock " + vx + " " + (y + 1) + " " + lz + " fishtastic:fish_tank");
        }));
        queue(5, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x0 = origin.getX() - 10, y = origin.getY(), z0 = origin.getZ() - 20;
            for (int set = 0; set < MATERIAL_SETS.length; set++) {
                FishTankMaterials materials = materials(MATERIAL_SETS[set]);
                for (int i = 0; i < shapes.length; i++) {
                    FishTankBlockEntity tank = tank(level, new BlockPos(x0 + 2 * (i % 10), y, z0 - 6 * set - 2 * (i / 10)));
                    if (tank == null) continue;
                    tank.setShape(shapes[i]);
                    tank.setMaterials(materials);
                }
            }
            FishTankBlockEntity corner = tank(level, new BlockPos(x0 + 2, y, z0 + 5));
            check("shapes.lGroup.openFaces", corner != null && corner.getOpenFaces().contains(Direction.EAST)
                    && corner.getOpenFaces().contains(Direction.NORTH), "corner=" + (corner == null ? null : corner.getOpenFaces()));
            FishTankBlockEntity base = tank(level, new BlockPos(x0 + 8, y, z0 + 5));
            check("shapes.verticalL.openFaces", base != null && base.getOpenFaces().contains(Direction.EAST)
                    && base.getOpenFaces().contains(Direction.UP), "base=" + (base == null ? null : base.getOpenFaces()));
            // Tank items: different shapes and materials, for the item model (and later the gallery).
            for (int i = 0; i < 9; i++) {
                ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", "fish_tank")));
                FishtasticItemData.set(item, FishtasticDataComponents.FISH_TANK_SHAPE, shapes[(i * 2) % shapes.length]);
                FishtasticItemData.set(item, FishtasticDataComponents.FISH_TANK_MATERIALS, materials(MATERIAL_SETS[i % MATERIAL_SETS.length]));
                for (var player : s.getPlayerList().getPlayers()) player.getInventory().setItem(i, item.copy());
            }
        }));
        for (int set = 0; set < MATERIAL_SETS.length; set++) {
            int row = set;
            queue(5, mc -> {
                mc.options.hideGui = true;
                camera(mc, origin.getX() - 1.0, origin.getY() + 4.0, origin.getZ() - 20 - 6 * row + 7.5, 180f, 25f);
            });
            queue(60, mc -> screenshot(mc, "shapes", "set" + row));
        }
        queue(1, mc -> camera(mc, origin.getX() - 3.0, origin.getY() + 3.0, origin.getZ() - 11.0, 180f, 30f));
        queue(60, mc -> screenshot(mc, "shapes", "groups"));
        queue(1, mc -> server(mc, s -> {
            FishTankBlockEntity corner = tank(s.overworld(), new BlockPos(origin.getX() - 8, origin.getY(), origin.getZ() - 15));
            if (corner != null) corner.setMaterials(materials(new String[]{"minecraft:diamond_block", "minecraft:red_sand", "minecraft:glass"}));
        }));
        queue(40, mc -> screenshot(mc, "shapes", "groups_rematerialed"));
        queue(1, mc -> {
            mc.options.hideGui = false;
            server(mc, s -> run(s, "gamemode creative @a"));
        });
        queue(40, mc -> screenshot(mc, "shapes", "hotbar"));
    }

    /**
     * A5.2 stress: an 8x8x8 group (the 512-tank cap) whose tanks cycle through 64 frame/glass
     * combinations the model has never baked, so the chunk-meshing threads bake them all at once
     * and concurrently. Watch the log for exceptions and missing-texture quads.
     */
    private static void queueStressScene() {
        String[] frames = {"stone_bricks", "oak_planks", "spruce_planks", "birch_planks", "dark_oak_planks", "bricks",
                "deepslate_tiles", "polished_andesite", "quartz_block", "mud_bricks", "cherry_planks", "bamboo_planks",
                "crimson_planks", "warped_planks", "prismarine_bricks", "waxed_copper_block"};
        String[] glasses = {"fishtastic:blue_clear_stained_glass", "minecraft:glass", "fishtastic:clear_glass",
                "minecraft:red_stained_glass"};
        queue(1, mc -> server(mc, s -> {
            int x0 = origin.getX() + 12, y = origin.getY(), z0 = origin.getZ() - 12;
            run(s, "fill " + x0 + " " + y + " " + z0 + " " + (x0 + 7) + " " + (y + 7) + " " + (z0 + 7) + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x0 = origin.getX() + 12, y = origin.getY(), z0 = origin.getZ() - 12, placed = 0;
            for (int i = 0; i < 512; i++) {
                FishTankBlockEntity tank = tank(level, new BlockPos(x0 + i % 8, y + (i / 8) % 8, z0 + i / 64));
                if (tank == null) continue;
                placed++;
                tank.setMaterials(new FishTankMaterials(block("minecraft:" + frames[i % 16]), block("minecraft:sand"),
                        block(glasses[(i / 16) % 4])));
            }
            FishTankBlockEntity centre = tank(level, new BlockPos(x0 + 3, y + 3, z0 + 3));
            check("stress512.placed", placed == 512, "placed=" + placed);
            check("stress512.centreOpenFaces", centre != null && centre.getOpenFaces().size() == 6,
                    "centre=" + (centre == null ? null : centre.getOpenFaces()));
        }));
        queue(5, mc -> {
            mc.options.hideGui = true;
            // Minecraft yaw: 0 faces +Z, 90 faces -X, 180 faces -Z; -138 looks north-east at the group.
            camera(mc, origin.getX() + 8.0, origin.getY() + 11.0, origin.getZ() + 1.0, -138f, 35f);
        });
        queue(100, mc -> screenshot(mc, "stress512", "group"));
        queue(1, mc -> mc.options.hideGui = false);
    }

    /**
     * A5.3: the builtin/entity items (26.1's custom item model types) in the hotbar, the inventory
     * screen, item frames, on the ground and in hand; and the Shape Gallery, whose cells render
     * tank items (A4 saw them blank).
     */
    private static void queueItemsScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() + 6;
            run(s, "fill " + (x - 4) + " " + y + " " + (z - 1) + " " + (x + 4) + " " + (y + 3) + " " + (z + 4) + " minecraft:air");
            run(s, "fill " + (x - 4) + " " + y + " " + (z + 3) + " " + (x + 4) + " " + (y + 2) + " " + (z + 3) + " minecraft:stone");
            run(s, "setblock " + (x + 3) + " " + y + " " + (z - 2) + " fishtastic:fish_tank_assembly");
            List<ItemStack> items = itemsSceneStacks();
            for (int i = 0; i < 4; i++) {
                run(s, "summon item_frame " + (x - 1 + i) + " " + (y + 1) + " " + (z + 2) + " {Facing:2b,Fixed:1b,Invulnerable:1b}");
            }
            for (var player : s.getPlayerList().getPlayers()) {
                for (int i = 0; i < 9; i++) player.getInventory().setItem(i, items.get(i).copy());
                player.getInventory().selected = 0;
            }
            ServerLevel level = s.overworld();
            var frames = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                    new net.minecraft.world.phys.AABB(x - 2, y, z + 1, x + 4, y + 3, z + 3));
            for (int i = 0; i < frames.size() && i < 4; i++) frames.get(i).setItem(items.get(i + 1).copy(), false);
            for (int i = 0; i < 4; i++) {
                var entity = new net.minecraft.world.entity.item.ItemEntity(level, x - 1 + i + 0.5, y, z + 0.5, items.get(i).copy());
                entity.setNeverPickUp();
                entity.setUnlimitedLifetime();
                entity.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(entity);
            }
        }));
        queue(20, mc -> {
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 0;
            server(mc, s -> {
                run(s, "gamemode creative @a");
                run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 1.0, (double) origin.getY(), origin.getZ() + 3.0, 0f, 25f));
            });
        });
        queue(40, mc -> screenshot(mc, "items", "world_and_hotbar"));
        queue(1, mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        queue(30, mc -> screenshot(mc, "items", "inventory"));
        queue(1, mc -> {
            mc.setScreen(null);
            mc.player.getInventory().selected = 1;
        });
        queue(20, mc -> screenshot(mc, "items", "held_structure"));
        queue(1, mc -> mc.player.getInventory().selected = 5);
        queue(20, mc -> screenshot(mc, "items", "held_pile"));
        // The Shape Gallery: an empty-hand click on a placed assembly, as in play.
        queue(1, mc -> {
            grill24.fishtastic.client.FishtasticClientConfig.setShapeGalleryOpen(true);
            mc.player.getInventory().selected = 8;
            mc.player.getInventory().setItem(8, ItemStack.EMPTY);
            server(mc, s -> {
                for (var player : s.getPlayerList().getPlayers()) player.getInventory().setItem(8, ItemStack.EMPTY);
            });
        });
        queue(5, mc -> {
            BlockPos assembly = new BlockPos(origin.getX() + 3, origin.getY(), origin.getZ() + 4);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(assembly), Direction.UP, assembly, false));
        });
        queue(40, mc -> {
            screenshot(mc, "items", "shape_gallery");
            check("items.assemblyOpen", mc.screen != null, "screen=" + mc.screen);
        });
        queue(1, mc -> mc.setScreen(null));
    }

    /**
     * A5.6: the fishing line leaves the Fishtastic rod's hand (FishingHookRendererMixin), a held
     * fish renders at its recorded size in third person (ItemInHandLayerMixin), and the
     * leaderboard's podium puppet holds its catch in the fisherman pose (HumanoidModelMixin).
     */
    private static void queueHeldScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            server(mc, s -> {
                run(s, "gamemode survival @a");
                run(s, "fill " + (origin.getX() - 3) + " " + (origin.getY() - 1) + " " + (origin.getZ() - 12) + " "
                        + (origin.getX() + 3) + " " + (origin.getY() - 1) + " " + (origin.getZ() - 6) + " minecraft:water");
                run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 0.5, (double) origin.getY(), origin.getZ() - 3.5, 180f, 20f));
                for (var player : s.getPlayerList().getPlayers()) {
                    player.getInventory().setItem(0, item("copper_fishing_rod"));
                    player.getInventory().setItem(1, fish("giant_manta_ray", 300f));
                    player.getInventory().setItem(2, fish("bluegill", 12f));
                    player.getInventory().selected = 0;
                }
            });
        });
        queue(20, mc -> {
            mc.player.getInventory().selected = 0;
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        queue(40, mc -> screenshot(mc, "held", "rod_cast_first_person"));
        queue(1, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        queue(20, mc -> screenshot(mc, "held", "rod_cast_third_person"));
        queue(1, mc -> {
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND); // reel in
            mc.player.getInventory().selected = 1;
        });
        queue(20, mc -> screenshot(mc, "held", "large_fish_third_person"));
        queue(1, mc -> mc.player.getInventory().selected = 2);
        queue(20, mc -> screenshot(mc, "held", "small_fish_third_person"));
        // The fisherman hang pose: the podium puppet's code path, forced onto the player by the
        // debug toggle (the same check), holding the large fish.
        queue(1, mc -> {
            grill24.fishtastic.client.util.FishermanPoseDebug.enabledInWorld = true;
            mc.player.getInventory().selected = 1;
        });
        queue(20, mc -> screenshot(mc, "held", "fisherman_pose"));
        queue(1, mc -> {
            grill24.fishtastic.client.util.FishermanPoseDebug.enabledInWorld = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            boolean puppetClassResolves;
            try {
                Class.forName("io.github.currenj.gelatinui.gui.components.PlayerAvatarRenderer$Puppet");
                puppetClassResolves = true;
            } catch (ClassNotFoundException e) {
                puppetClassResolves = false;
            }
            check("held.gelatinPuppetClass", puppetClassResolves, "(FishermanPoseDebug matches the podium puppet by this name)");
        });
    }

    private static final String[] QUALITIES = {"uncommon", "rare", "epic", "legendary"};

    private static ItemStack qualityFish(String quality) {
        ItemStack stack = fish("parrotfish", 30f);
        FishtasticItemData.set(stack, FishtasticDataComponents.FISH_QUALITY,
                new FishQuality(FishQuality.Quality.valueOf(quality.toUpperCase(java.util.Locale.ROOT))));
        return stack;
    }

    /**
     * A5.4 = the rendering spike's criteria on production code: quality glint on a held item;
     * static and animated (legendary) GUI outlines in the hotbar and a container; world outlines
     * on dropped items and in item frames; plus the encyclopedia's never-caught silhouettes. The
     * common-quality fish in hotbar slot 5 is the no-outline control. Atlases are dumped 10 ticks
     * apart (only the legendary slot should differ).
     */
    private static void queueOutlineScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() + 14;
            run(s, "fill " + (x - 5) + " " + y + " " + (z - 3) + " " + (x + 5) + " " + (y + 3) + " " + (z + 4) + " minecraft:air");
            run(s, "fill " + (x - 4) + " " + y + " " + (z + 4) + " " + (x + 4) + " " + (y + 2) + " " + (z + 4) + " minecraft:stone");
            ServerLevel level = s.overworld();
            for (int i = 0; i < QUALITIES.length; i++) {
                run(s, "summon item_frame " + (x - 2 + i) + " " + (y + 1) + " " + (z + 3) + " {Facing:2b,Fixed:1b,Invulnerable:1b}");
                var entity = new net.minecraft.world.entity.item.ItemEntity(level, x - 1.05 + 0.7 * i, y, z + 0.9, qualityFish(QUALITIES[i]));
                entity.setNeverPickUp();
                entity.setUnlimitedLifetime();
                entity.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(entity);
            }
            for (var player : s.getPlayerList().getPlayers()) {
                for (int i = 0; i < 9; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
                for (int i = 0; i < QUALITIES.length; i++) player.getInventory().setItem(i, qualityFish(QUALITIES[i]));
                player.getInventory().setItem(4, fish("parrotfish", 30f));
                player.getInventory().selected = 3;
            }
        }));
        queue(5, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() + 14;
            var frames = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                    new net.minecraft.world.phys.AABB(x - 3, y, z + 2, x + 3, y + 3, z + 4));
            frames.sort(java.util.Comparator.comparingDouble(net.minecraft.world.entity.Entity::getX));
            for (int i = 0; i < frames.size() && i < QUALITIES.length; i++) frames.get(i).setItem(qualityFish(QUALITIES[i]), false);
        }));
        queue(5, mc -> {
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 3;
            server(mc, s -> {
                run(s, "gamemode creative @a");
                run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 10.5, 0f, 5f));
            });
        });
        queue(60, mc -> {
            screenshot(mc, "outline", "frames_a");
            dump(mc, FishtasticItemOutlineAtlas.getInstance().outlineTarget(), "outline", "atlas_outline_a");
            dump(mc, FishtasticItemOutlineAtlas.getInstance().maskTarget(), "outline", "atlas_mask_a");
        });
        queue(10, mc -> {
            screenshot(mc, "outline", "frames_b");
            dump(mc, FishtasticItemOutlineAtlas.getInstance().outlineTarget(), "outline", "atlas_outline_b");
            server(mc, s -> run(s, "execute as @a at @s run tp @s ~ ~ ~ 0 60"));
        });
        queue(20, mc -> screenshot(mc, "outline", "ground"));
        queue(1, mc -> server(mc, s -> run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                origin.getX() + 0.5, origin.getY() - 0.4, origin.getZ() + 15.4, 0f, 0f))));
        queue(20, mc -> screenshot(mc, "outline", "frames_close"));
        queue(1, mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        queue(30, mc -> screenshot(mc, "outline", "inventory"));
        queue(1, mc -> {
            mc.setScreen(null);
            mc.player.connection.sendCommand("gelatin fish_encyclopedia");
        });
        queue(40, mc -> {
            screenshot(mc, "outline", "encyclopedia");
            check("outline.encyclopediaOpen", mc.screen != null, "screen=" + mc.screen);
        });
        queue(1, mc -> mc.setScreen(null));
    }

    /**
     * G1: Fabulous graphics (the item-entity target and the translucency chain). Runs the outline
     * and tank scenes' subjects under Fabulous: world outlines (ITEM_OUTLINE draws into the
     * item-entity target, spike finding F4), the tank's glass and water fill, and glass blocks.
     * Expects the outline and tank scenes to have run first; restores the graphics mode after.
     */
    private static void queueFabulousScene() {
        queue(1, mc -> {
            savedGraphics = mc.options.graphicsMode().get();
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FABULOUS);
            mc.levelRenderer.allChanged();
            mc.options.hideGui = false;
            server(mc, s -> run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                    origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 12.5, 0f, 35f)));
        });
        queue(60, mc -> {
            check("fabulous.active", net.minecraft.client.Minecraft.useShaderTransparency(), "graphics=" + mc.options.graphicsMode().get());
            screenshot(mc, "fabulous", "outlines");
        });
        queue(1, mc -> server(mc, s -> run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                origin.getX() + 0.5, origin.getY() - 0.4, origin.getZ() + 15.4, 0f, 0f))));
        queue(20, mc -> screenshot(mc, "fabulous", "frames_close"));
        queue(1, mc -> camera(mc, origin.getX() + 1.0, origin.getY() + 2.2, origin.getZ() - 0.6, 180f, 12f));
        queue(40, mc -> screenshot(mc, "fabulous", "tanks"));
        queue(1, mc -> server(mc, s -> run(s, "gamemode creative @a")));
        queue(1, mc -> {
            mc.options.graphicsMode().set(savedGraphics);
            mc.levelRenderer.allChanged();
        });
        queue(20, mc -> check("fabulous.restored", mc.options.graphicsMode().get() == savedGraphics, ""));
    }

    /**
     * G1: GUI outlines at GUI scales 1, 2 and 4 (the spike only ran scale 3): the hotbar from the
     * outline scene, which must have run first. Restores the scale after.
     */
    private static void queueGuiScaleScene() {
        queue(1, mc -> {
            savedGuiScale = mc.options.guiScale().get();
            savedWindowWidth = mc.getWindow().getWidth();
            savedWindowHeight = mc.getWindow().getHeight();
            // Scale 4 needs a window at least 1280x960 (Window.calculateScale caps the scale at
            // width/320 and height/240); the dev runs open smaller ones.
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
        });
        for (int scale : new int[]{1, 2, 4}) {
            queue(1, mc -> {
                mc.options.hideGui = false;
                mc.options.guiScale().set(scale);
                mc.resizeDisplay();
            });
            queue(20, mc -> {
                check("guiscale." + scale, mc.getWindow().getGuiScale() == scale, "actual=" + mc.getWindow().getGuiScale());
                screenshot(mc, "guiscale", "scale" + scale);
            });
        }
        queue(1, mc -> {
            mc.options.guiScale().set(savedGuiScale);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), savedWindowWidth, savedWindowHeight);
            mc.resizeDisplay();
        });
    }

    private static net.minecraft.client.GraphicsStatus savedGraphics;
    private static int savedGuiScale;
    private static int savedRenderDistance;
    private static int savedWindowWidth;
    private static int savedWindowHeight;

    /** Pile of Fish (flat), a pile-block stack, structure cosmetics, the treasure chest and a tank. */
    private static List<ItemStack> itemsSceneStacks() {
        ItemStack legendary = fish("lionfish", 30f);
        FishtasticItemData.set(legendary, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.LEGENDARY));
        List<ItemStack> contents = List.of(fish("bluegill", 25f), legendary, fish("discus", 28f), fish("betta", 20f));
        ItemStack pile = new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", "pile_of_fish")));
        FishtasticItemData.setBundleContents(pile, new net.minecraft.world.item.component.BundleContents(contents));
        ItemStack pileBlock = pile.copy();
        pileBlock.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, grill24.fishtastic.client.util.FishPileIcons.PILE_BLOCK_MARKER);
        return List.of(
                pile,
                item("cosmetic_castle_ruin"),
                item("cosmetic_fence_arch_oak"),
                item("cosmetic_treasure_chest"),
                item("cosmetic_coral_reef_1"),
                pileBlock,
                item("cosmetic_dynamic_duo"),
                item("fish_tank"),
                item("cosmetic_birch_tree"));
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", id)));
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(Ids.parse(id));
    }

    private static FishTankMaterials materials(String[] set) {
        return new FishTankMaterials(block(set[0]), block(set[1]), block(set[2]));
    }

    /**
     * The two 26.1.2 fixes cherry-picked in e33f5792: a quest banner updated in place must show the
     * new counter and the Complete! badge; and opening a gelatin screen must not make JEI log
     * "Received invalid gui properties" (grep latest.log for it after the run).
     */
    private static void queueFixesScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            server(mc, s -> run(s, "gamemode creative @a"));
            mc.player.connection.sendCommand("fishtastic testquestnotify");
        });
        queue(30, mc -> screenshot(mc, "fixes", "notify_first"));
        queue(1, mc -> mc.player.connection.sendCommand("fishtastic testquestnotify complete"));
        queue(30, mc -> screenshot(mc, "fixes", "notify_updated"));
        queue(200, mc -> mc.player.connection.sendCommand("gelatin quest_log"));
        queue(40, mc -> {
            screenshot(mc, "fixes", "quest_log");
            check("fixes.questLogOpen", mc.screen != null, "screen=" + mc.screen);
        });
        queue(1, mc -> mc.setScreen(null));
    }

    /**
     * A5.7 (owner decision 2026-09-24): with no screen open the HUD layers are drawn after vanilla's
     * toasts, so a toast can never cover a quest notification — a vanilla toast and the quest
     * banners share the top-right corner. Shows both at once (the banner must be the visible one),
     * then the same pair with a screen open, where the layers go back to the HUD pass: under the
     * screen, and still under the toasts, which vanilla draws after the screen.
     *
     * <p>The notification is enqueued on the client thread rather than through
     * {@code /fishtastic testquestnotify}: that command reaches this client-side manager from the
     * <em>server</em> thread (fine in single-player only), which races its {@code tick} and loses
     * the event often enough to make the screenshot unreliable. Same call, same event.
     */
    private static void queueHudScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            mc.getToasts().clear();
            FishtasticHudLayers.toastPassFrames = 0;
            server(mc, s -> run(s, "gamemode creative @a"));
            QuestProgressNotificationManager.getInstance().enqueue(new QuestProgressEvent(
                    Ids.of("fishtastic", "mastery/bluegill_novice"), 2, 3, 5, false, ItemStack.EMPTY));
            SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.literal("Vanilla toast"),
                    Component.literal("shares the banner's corner"));
        });
        // Two shots: the banner's slide-in is staggered (QuestProgressNotificationManager's
        // nextStaggerDelay), so the first can catch it part-way across.
        queue(20, mc -> screenshot(mc, "hud", "banner_and_toast"));
        queue(20, mc -> {
            check("hud.noScreen", mc.screen == null, "screen=" + mc.screen);
            check("hud.toastOnScreen",
                    mc.getToasts().getToast(SystemToast.class, SystemToast.SystemToastId.NARRATOR_TOGGLE) != null,
                    "");
            check("hud.drawnAfterToasts", FishtasticHudLayers.toastPassFrames > 0,
                    "toastPassFrames=" + FishtasticHudLayers.toastPassFrames);
            screenshot(mc, "hud", "banner_and_toast_late");
        });
        // With a screen open the mixin stays out of it -- its guard is the negation of
        // drawnInHudPass -- and the loaders' HUD hooks draw the layers instead. No fresh banner is
        // needed: the one enqueued above is still up (a 15-tick slide-in then a 60-tick hold, and
        // nothing here moves the manager off 1x), so the same banner is in the second reading too.
        //
        // That banner is in the toast's corner, which makes the two shots one experiment. Vanilla
        // draws the HUD at GameRenderer line 1081, the screen at 1097, the toasts at 1140: with a
        // screen open our layers are drawn by the HUD hook and the toast lands on top of them, so
        // `banner_with_screen_toast` shows the toast and no banner -- the banner is *behind* it,
        // exactly the case the toast-pass hook exists to avoid, and the look 26.1.2 has all the
        // time (its toasts cover its HUD too, screen or no screen). Clearing the toasts and
        // shooting again puts the banner back in view under a transparent ChatScreen: drawn, at
        // its margin, just outranked. The toast-pass counter is the path that must stay flat here;
        // hudPassCalls is the loaders' hooks asking and being told to draw.
        queue(1, mc -> {
            FishtasticHudLayers.toastPassFrames = 0;
            FishtasticHudLayers.hudPassCalls = 0;
            mc.setScreen(new ChatScreen(""));
        });
        queue(2, mc -> {
            check("hud.withScreen", mc.screen != null, "screen=" + mc.screen);
            check("hud.hudPassWithScreen", FishtasticHudLayers.toastPassFrames == 0,
                    "toastPassFrames=" + FishtasticHudLayers.toastPassFrames);
            check("hud.hudPassRan", FishtasticHudLayers.hudPassCalls > 0,
                    "hudPassCalls=" + FishtasticHudLayers.hudPassCalls);
            screenshot(mc, "hud", "banner_with_screen_toast");
        });
        // One step, then the shot: a capture is of the frame *before* the step that asked for it ran
        // (Screenshot.grab reads the back buffer mid-frame), so clearing and shooting in the same
        // step photographed the toast twice on the first run of this half.
        queue(2, mc -> mc.getToasts().clear());
        queue(2, mc -> screenshot(mc, "hud", "banner_with_screen"));
        queue(1, mc -> {
            mc.setScreen(null);
            mc.getToasts().clear();
        });
    }

    /**
     * The gold "look here" outline on inventory slots: a hotbar-adjacent row holding both rods, worms, a
     * hook and a stick (the control, which must never glow), shot with bait, a rod and nothing on the
     * cursor. Also checks the stateless rules.
     */
    private static void queueHighlightScene() {
        queue(1, mc -> server(mc, s -> {
            run(s, "gamemode survival @a");
            run(s, "clear @a");
            run(s, "give @a fishtastic:copper_fishing_rod");
            run(s, "give @a fishtastic:obsidian_fishing_rod");
            run(s, "give @a fishtastic:worms 16");
            run(s, "give @a fishtastic:hook");
            run(s, "give @a minecraft:stick");
        }));
        queue(10, mc -> {
            mc.options.hideGui = false;
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        });
        ItemStack worms = new ItemStack(FishtasticItems.WORMS.value());
        ItemStack rod = new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
        ItemStack stick = new ItemStack(net.minecraft.world.item.Items.STICK);
        queue(1, mc -> {
            check("highlight.bait_to_rod", ItemHighlightRules.matches(worms, rod), "");
            check("highlight.rod_to_bait", ItemHighlightRules.matches(rod, worms), "");
            check("highlight.bait_to_stick", !ItemHighlightRules.matches(worms, stick), "");
            check("highlight.empty_cursor", !ItemHighlightRules.matches(ItemStack.EMPTY, rod), "");
            mc.player.containerMenu.setCarried(worms.copy());
        });
        queue(10, mc -> screenshot(mc, "highlight", "bait_on_cursor"));
        queue(1, mc -> mc.player.containerMenu.setCarried(rod.copy()));
        queue(10, mc -> screenshot(mc, "highlight", "rod_on_cursor"));
        queue(1, mc -> mc.player.containerMenu.setCarried(ItemStack.EMPTY));
        queue(10, mc -> screenshot(mc, "highlight", "empty_cursor"));
        queue(1, mc -> {
            mc.setScreen(null);
            server(mc, s -> run(s, "clear @a"));
        });
    }

    /**
     * A5.7: the cosmetic-capture wand's selection boxes. 26.1 collects them into per-tick gizmos;
     * on 1.21.1 they're drawn from the loaders' world-render hooks ({@code CosmeticCaptureClientState}),
     * which nothing in game reaches without a wand session — so this starts one with the real
     * command, makes the three picks the wand's right-clicks would make, and re-sends the session,
     * then cancels it (the clear packet must take the boxes away again).
     */
    private static void queueGizmosScene() {
        queue(1, mc -> {
            mc.options.hideGui = true;
            mc.player.connection.sendCommand("fishtastic cosmetic capture start");
        });
        queue(10, mc -> server(mc, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            CosmeticCaptureSession session = CosmeticCaptureSession.get(player.getUUID());
            if (session == null) {
                check("gizmos.session", false, "no session after /fishtastic cosmetic capture start");
                return;
            }
            session.recordClick(origin.offset(-2, 1, -2));
            session.recordClick(origin.offset(1, 3, 2));
            session.recordClick(origin.offset(4, 1, 0));
            CosmeticCaptureSyncPacket.sendToPlayer(player, session);
            check("gizmos.session", true, "corner1=" + session.corner1() + " corner2=" + session.corner2()
                    + " anchor=" + session.anchor());
        }));
        queue(1, mc -> camera(mc, origin.getX() + 0.5, origin.getY() + 8.0, origin.getZ() + 14.5, 180f, 20f));
        queue(40, mc -> screenshot(mc, "gizmos", "selection"));
        queue(1, mc -> mc.player.connection.sendCommand("fishtastic cosmetic capture cancel"));
        queue(30, mc -> screenshot(mc, "gizmos", "cancelled"));
        queue(1, mc -> mc.options.hideGui = false);
    }

    // ── Per-tank group rendering ─────────────────────────────────────────────

    /**
     * How far west of the camera the group's anchor stands, in blocks.
     *
     * <p>This is the number that makes the scene reproduce the defect, and it is much larger than
     * "out of view" would suggest. Vanilla culls terrain against a frustum pushed <em>back</em> along
     * the view vector until it fully contains the 8-block cube around the camera
     * ({@code LevelRenderer.offsetFrustum} → {@code Frustum.offsetToFullyIncludeCameraCube(8)}), which
     * reaches roughly a dozen blocks behind the camera. A section merely a few blocks behind the
     * camera is therefore still dispatched, and a scene staged that close measures nothing: it was
     * tried, and {@code pertank.anchorNotRendered} is the check that caught it.
     */
    private static final int PERTANK_ANCHOR_BEHIND = 41;
    /**
     * Stocked tanks either side of the camera: a long run east of it, where the anchor is far
     * behind, and a shorter one just west, so the control view — the same spot facing the anchor —
     * has fish to show too. Both views have to be able to see fish, or one of the two measurements
     * below would compare two frames that were never going to differ.
     */
    private static final int PERTANK_EAST_FISH_TANKS = 11;
    private static final int PERTANK_WEST_FISH_TANKS = 4;
    /**
     * Fish per stocked tank. Two 85 cm fish is what a tank's size-based capacity budget accepts
     * ({@code TankCapacity}); asking for four made {@code pertank.stocked} fail, which is what that
     * check is for. The group scatters its fish over the whole row on rebuild, so how many end up
     * in front of either camera is a draw from that spread — this density over fifteen tanks keeps
     * the expected count clear of the floor {@link #checkFishAhead} enforces.
     */
    private static final int PERTANK_FISH_PER_TANK = 2;
    /**
     * Several species, so the shoal cannot school into one clump somewhere off screen. The check
     * below counts fish <em>pixels</em>, and the fish are free to swim the whole row.
     */
    private static final String[] PERTANK_SPECIES = {"discus", "lionfish", "clown_loach", "greenstripe_barb"};
    /** Eye height above the row, and how far north of it the two cameras stand. */
    private static final double PERTANK_EYE_ABOVE = 1.6;
    private static final double PERTANK_EYE_OFFSET_Z = 1.0;
    /** Fish size in cm. Big enough that a human looking at the shot can see them at a glance, and
     *  comfortably inside the group's size gate whatever it is at the row's length. */
    private static final float PERTANK_FISH_SIZE_CM = 85f;
    /** Where the camera stands, in blocks east of the section boundary the row is laid out from. */
    private static final double PERTANK_CAMERA_ALONG = 2.5;
    /** Pixels that must change before fish count as drawn, and how the camera looks along the row. */
    private static final int PERTANK_MIN_SIGNAL = 400;
    private static final float PERTANK_PITCH = 18f;
    /**
     * The frame rectangle the fish measurement covers, as fractions of the frame — the sky is
     * cropped out of it, because the clouds move.
     */
    private static final double PERTANK_CROP_X0 = 0.08, PERTANK_CROP_X1 = 0.92;
    private static final double PERTANK_CROP_Y0 = 0.30, PERTANK_CROP_Y1 = 1.0;
    /**
     * Per-channel difference a pixel must exceed to count as changed. PNG is lossless, so this is
     * only here to absorb a renderer's own frame-to-frame noise.
     */
    private static final int PERTANK_PIXEL_DELTA = 8;

    /**
     * The multi-tank culling gate (docs/fish-tank-group-scaling.md §9.8).
     *
     * <p>Group fish used to be drawn by the group's anchor alone, and a block entity renderer only
     * runs while its own chunk section is on screen — so every swimming fish in a connected build
     * vanished the moment the anchor's section left the frustum. This scene builds exactly that
     * configuration and measures it.
     *
     * <p>The row runs from the anchor, {@value #PERTANK_ANCHOR_BEHIND} blocks west of the camera,
     * east through the boundary to the stocked tanks in front of it — a long connector run of empty
     * tanks in between, because the distance is what the defect needs and not an accident of layout
     * (see {@link #PERTANK_ANCHOR_BEHIND} for why a few blocks will not do). Facing east along the
     * row puts the anchor's section far behind the camera, where vanilla's section cull cannot
     * reach; facing west from the same spot brings it into view. Fish must be drawn in both, and the
     * anchor's own extract tick says whether each view is what it claims: the scene fails if the
     * anchor was rendered while the camera faced away from it.
     *
     * <p>Each view is shot twice, stocked and emptied, and the two frames differenced. The emptied
     * scene is shot twice more so the measurement carries its own noise floor — this machine's
     * animation and shader noise, measured, rather than a guessed constant. The water fill is
     * switched off for the scene, so an animated surface over every wall stays out of the signal.
     *
     * <p>The difference is measured in solid runs of changed pixels rather than a flat count, so the
     * fish are what clears the floor and the bubble wakes they trail are not — see
     * {@link #shotDifference}. Reverting the fix was measured against this scene by hand: it takes
     * the fish out of the away view and leaves nothing but wakes, which now fails by a factor of
     * several rather than passing on them.
     */
    private static void queuePerTankScene() {
        boolean waterFillWasOn = FishtasticClientConfig.isTankWaterFillEnabled();
        ChatVisiblity chatWas = Minecraft.getInstance().options.chatVisibility().get();

        // Stand at the row before building it: /setblock cannot touch an unloaded chunk, and the
        // client only receives the tanks' contents once it is near them.
        queue(1, mc -> cameraFacingRow(mc, false));
        queue(20, mc -> {
            FishtasticClientConfig.setTankWaterFillEnabled(false);
            // Staging is dozens of server commands and their feedback lands in the chat overlay,
            // which sits inside the measurement crop. Hiding it removes both a barrier over the
            // fish and the one thing in the frame that could still be changing between two shots.
            mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        });
        queue(1, mc -> server(mc, RenderSelfTest::stagePerTankRow));
        queue(20, mc -> server(mc, s -> check("pertank.stocked", stockPerTankRow(s.overworld()), "")));
        queue(20, RenderSelfTest::checkPerTankGroup);

        // ── Facing away from the anchor: the configuration that used to lose the shoal ──
        queue(60, mc -> screenshot(mc, "pertank", "away_fish"));
        queue(1, mc -> {
            checkAnchorRendered(mc, "pertank.anchorNotRendered", false);
            // Enough to explain the pixels, not a proxy for them: the pixel check below carries the
            // verdict, and this only has to distinguish "the shoal drifted behind the camera" from
            // "nothing was drawn".
            checkFishAhead(mc, "pertank.fishAheadAway", false, 2);
            checkOwnership(mc, "pertank.ownershipAway");
        });
        queue(1, mc -> server(mc, s -> emptyPerTankRow(s.overworld())));
        queue(20, mc -> screenshot(mc, "pertank", "away_empty"));
        queue(20, mc -> screenshot(mc, "pertank", "away_empty2"));

        // ── Facing the anchor: the same fish, drawn in the same place, with the anchor in view ──
        queue(1, mc -> server(mc, s -> check("pertank.restocked", stockPerTankRow(s.overworld()), "")));
        queue(1, mc -> cameraFacingRow(mc, true));
        queue(60, mc -> screenshot(mc, "pertank", "toward_fish"));
        queue(1, mc -> {
            checkAnchorRendered(mc, "pertank.anchorRendered", true);
            // Fewer fish are stocked west of the camera than east, and a couple may have drifted the
            // other way by now, so this asks for less than the away view does.
            checkFishAhead(mc, "pertank.fishAheadToward", true, 2);
        });
        queue(1, mc -> server(mc, s -> emptyPerTankRow(s.overworld())));
        queue(20, mc -> screenshot(mc, "pertank", "toward_empty"));

        queue(1, mc -> {
            FishtasticClientConfig.setTankWaterFillEnabled(waterFillWasOn);
            mc.options.chatVisibility().set(chatWas);
        });
        queue(40, RenderSelfTest::checkPerTankDiffs);
    }

    /**
     * The section boundary the row is laid out from. The row runs west from here to the anchor and
     * east through the camera to the stocked tanks, so the anchor's section is the far side of a
     * boundary the camera never crosses — a group whose tanks all shared one section could not
     * reproduce the defect at all, because vanilla dispatches block entities a section at a time.
     */
    private static int pertankBoundary() {
        return Math.floorDiv(origin.getX(), 16) * 16;
    }

    /** The group's anchor: the row's west end, {@value #PERTANK_ANCHOR_BEHIND} blocks behind the camera. */
    private static BlockPos pertankAnchorPos() {
        return new BlockPos(pertankBoundary() - PERTANK_ANCHOR_BEHIND, origin.getY(), origin.getZ() + 40);
    }

    /** Where the camera stands along the row: over a connector tank, just east of the boundary. */
    private static double pertankCameraX() {
        return pertankBoundary() + PERTANK_CAMERA_ALONG;
    }

    /** First of the stocked tanks east of the camera, and first of the shorter run west of it. */
    private static int pertankFirstEastFishTankX() {
        return (int) Math.ceil(pertankCameraX()) + 1;
    }

    private static int pertankFirstWestFishTankX() {
        return (int) Math.floor(pertankCameraX()) - PERTANK_WEST_FISH_TANKS;
    }

    private static int pertankLastTankX() {
        return pertankFirstEastFishTankX() + PERTANK_EAST_FISH_TANKS - 1;
    }

    private static int pertankStockedFish() {
        return (PERTANK_EAST_FISH_TANKS + PERTANK_WEST_FISH_TANKS) * PERTANK_FISH_PER_TANK;
    }

    private static int pertankTankCount() {
        return pertankLastTankX() - pertankAnchorPos().getX() + 1;
    }

    /**
     * Stands the (spectator) camera beside the row, looking along it. {@code towardAnchor} faces
     * west, which brings the anchor's section into the frustum; the other way faces east, away from
     * it, with the whole run of connector tanks between.
     */
    private static void cameraFacingRow(Minecraft mc, boolean towardAnchor) {
        BlockPos anchor = pertankAnchorPos();
        camera(mc,
                pertankCameraX(),
                anchor.getY() + PERTANK_EYE_ABOVE,
                anchor.getZ() - PERTANK_EYE_OFFSET_Z,
                towardAnchor ? 90f : -90f,
                PERTANK_PITCH);
    }

    private static void stagePerTankRow(MinecraftServer server) {
        BlockPos anchor = pertankAnchorPos();
        int x = anchor.getX(), y = anchor.getY(), z = anchor.getZ();
        int last = pertankLastTankX();
        run(server, "fill " + (x - 3) + " " + (y - 2) + " " + (z - 2) + " " + (last + 3) + " "
                + (y + 3) + " " + (z + 2) + " minecraft:air");
        for (int tankX = x; tankX <= last; tankX++) {
            run(server, "setblock " + tankX + " " + y + " " + z + " fishtastic:fish_tank");
        }
        run(server, "time set 6000");
    }

    /**
     * Stocks the tanks either side of the camera, leaving the long connector run to the anchor
     * empty on purpose. The fish are free swimmers in the whole group, so what matters is that they
     * start in front of the camera in both views, and {@link #checkFishAhead} measures whether they
     * are still there when the shot is taken.
     */
    private static boolean stockPerTankRow(ServerLevel level) {
        BlockPos anchor = pertankAnchorPos();
        int y = anchor.getY(), z = anchor.getZ();
        boolean stocked = true;
        for (int i = 0; i < PERTANK_WEST_FISH_TANKS + PERTANK_EAST_FISH_TANKS; i++) {
            int tankX = i < PERTANK_WEST_FISH_TANKS
                    ? pertankFirstWestFishTankX() + i
                    : pertankFirstEastFishTankX() + i - PERTANK_WEST_FISH_TANKS;
            FishTankBlockEntity tank = tank(level, new BlockPos(tankX, y, z));
            if (tank == null) {
                stocked = false;
                continue;
            }
            for (int f = 0; f < PERTANK_FISH_PER_TANK; f++) {
                stocked &= tank.addItem(fish(PERTANK_SPECIES[(i + f) % PERTANK_SPECIES.length], PERTANK_FISH_SIZE_CM));
            }
        }
        return stocked;
    }

    /**
     * Empties every slot of every tank in the row. Deliberately {@code setItem} rather than
     * {@code clearContent}: the latter never sends a block update, so the client would go on
     * rendering the shoal and the "emptied" frame would be identical to the stocked one — the check
     * would then fail with no hint why.
     */
    private static void emptyPerTankRow(ServerLevel level) {
        BlockPos anchor = pertankAnchorPos();
        int y = anchor.getY(), z = anchor.getZ();
        for (int tankX = anchor.getX(); tankX <= pertankLastTankX(); tankX++) {
            if (!(level.getBlockEntity(new BlockPos(tankX, y, z)) instanceof FishTankBlockEntity tank)) continue;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                tank.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Whether the row really is one group anchored at its west end, with every fish given to the
     * group engine rather than left hovering in its own tank. Without this the scene could be
     * measuring the single-tank path and calling it a pass.
     */
    private static void checkPerTankGroup(Minecraft mc) {
        BlockPos anchor = pertankAnchorPos();
        if (!(mc.level.getBlockEntity(anchor) instanceof FishTankBlockEntity be)) {
            check("pertank.oneGroup", false, "no client tank at " + anchor);
            return;
        }
        ClientTankGroups.Entry entry = ClientTankGroups.get(be, mc.level);
        TankGroups.Group group = entry.group();
        check("pertank.oneGroup", group.isMultiTank() && group.members().size() == pertankTankCount(),
                "members=" + group.members().size() + " expected=" + pertankTankCount());
        check("pertank.anchorAtWestEnd", group.anchor().equals(anchor),
                "anchor=" + group.anchor() + " expected=" + anchor);
        TankGroupFlock flock = entry.flock();
        int stocked = pertankStockedFish();
        int groupFish = flock == null ? -1 : flock.count();
        check("pertank.joinedTheGroup", groupFish == stocked,
                "groupFish=" + groupFish + " stocked=" + stocked);
    }

    /**
     * Whether the anchor's block entity is being dispatched at all — the scene's proof that it is
     * reproducing the configuration it claims rather than assuming it from camera geometry. A
     * section is only handed to its renderers while it survives vanilla's frustum and occlusion
     * tests, and facing away from the anchor exists precisely to keep it out of them.
     */
    private static void checkAnchorRendered(Minecraft mc, String name, boolean expected) {
        long last = ClientTankFlocks.lastExtractTickOf(pertankAnchorPos());
        long since = last == Long.MIN_VALUE ? Long.MAX_VALUE : ClientTankFlocks.tickCounter() - last;
        boolean rendered = since <= 2;
        check(name, rendered == expected,
                "anchor " + (rendered ? "rendered" : "not rendered") + " (last extract "
                        + (since == Long.MAX_VALUE ? "never" : since + " ticks ago") + "), expected "
                        + (expected ? "rendered" : "out of view"));
    }

    /**
     * How many of the group's fish are on the side of the camera the view looks along. The shoal is
     * free to swim the whole row, and a view whose fish have all drifted behind the camera would
     * read as "nothing was drawn" — this is what tells those two apart in the log.
     */
    private static void checkFishAhead(Minecraft mc, String name, boolean expectingWest, int minimum) {
        double cameraX = pertankCameraX();
        int ahead = 0;
        int total = ClientTankFlocks.fishCount(mc.level, pertankAnchorPos());
        for (int index = 0; index < total; index++) {
            Vec3 pos = ClientTankFlocks.worldPositionOf(mc.level, pertankAnchorPos(), index, mc.level.getGameTime());
            if (pos == null) continue;
            if (expectingWest ? pos.x < cameraX : pos.x > cameraX) ahead++;
        }
        check(name, ahead >= minimum,
                "fishAhead=" + ahead + " of " + total + " (camera x=" + cameraX + ")");
    }

    /**
     * The group's own account of how its fish are spread across its tanks. The two ways this can go
     * wrong are invisible in a frame — a fish owned by nobody is never drawn, one owned by two tanks
     * is drawn twice with the second overwriting the first's shared render state — so it is asserted
     * here, against real geometry and real fish positions rather than a unit fixture.
     */
    private static void checkOwnership(Minecraft mc, String name) {
        BlockPos anchor = pertankAnchorPos();
        if (!(mc.level.getBlockEntity(anchor) instanceof FishTankBlockEntity be)) {
            check(name, false, "no client tank at " + anchor);
            return;
        }
        TankGroupFlock flock = ClientTankGroups.get(be, mc.level).flock();
        if (flock == null) {
            check(name, false, "no group runtime (nothing on screen?)");
            return;
        }
        TankGroupFlock.OwnershipAudit audit = flock.auditOwnership();
        check(name, audit.isPartition(), audit.fish() + " fish, " + audit.drawn() + " drawn once, "
                + audit.claimedTwice() + " twice, " + audit.claimedNever() + " never, "
                + audit.membersDrawing() + "/" + audit.members() + " tanks drawing");
        check(name + ".multiTank", audit.membersDrawing() >= 2, audit.membersDrawing() + " tanks drawing");
    }

    /**
     * Differentials the stocked and emptied frames of both views, against their own noise floor.
     *
     * <p>Each view is asked only to clear that floor, not to match the other: the shoal scatters
     * over the whole row, so how much of it is in front of either camera is a draw, and the away
     * view is legitimately the emptier of the two. An earlier version required the away measurement
     * to be at least a third of the control's and failed a good build on a run where the scatter
     * had left most of the shoal behind the other camera.
     */
    private static void checkPerTankDiffs(Minecraft mc) {
        int noise = shotDifference(mc, "away_empty", "away_empty2");
        int away = shotDifference(mc, "away_fish", "away_empty");
        int toward = shotDifference(mc, "toward_fish", "toward_empty");
        int floor = Math.max(PERTANK_MIN_SIGNAL, 4 * Math.max(noise, 0));
        check("pertank.harnessSeesFish", toward > floor,
                "toward=" + toward + " (anchor in view) noise=" + noise + " floor=" + floor);
        check("pertank.fishWithAnchorOutOfView", away > floor,
                "away=" + away + " (anchor's section behind the camera) noise=" + noise + " floor=" + floor);
    }

    /**
     * Pixels that changed between two self-test shots over {@link #PERTANK_CROP_X0 the measurement
     * crop}, counting only those that changed together with their four neighbours, or {@code -1} if
     * either shot could not be read.
     *
     * <p>The shots are the PNGs the harness itself wrote, read back rather than kept in memory, so
     * the evidence for a PASS is a file anyone can open and look at. The neighbour test is what
     * separates the fish from the bubble wakes they trail: both vanish when the tanks are emptied,
     * but a fish sprite is a solid blob tens of pixels across and a bubble is a handful, which
     * erosion keeps and drops respectively. Without it, a build in which the fish were not being
     * drawn at all could still clear the floor on their wakes alone.
     */
    private static int shotDifference(Minecraft mc, String shotA, String shotB) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File fileA = new File(dir, fileName("pertank", shotA));
        File fileB = new File(dir, fileName("pertank", shotB));
        if (!fileA.isFile() || !fileB.isFile()) {
            Fishtastic.LOGGER.error("[selftest] nothing to compare: {} / {}", fileA, fileB);
            return -1;
        }
        try (InputStream streamA = new FileInputStream(fileA);
             InputStream streamB = new FileInputStream(fileB);
             NativeImage imageA = NativeImage.read(streamA);
             NativeImage imageB = NativeImage.read(streamB)) {
            int width = Math.min(imageA.getWidth(), imageB.getWidth());
            int height = Math.min(imageA.getHeight(), imageB.getHeight());
            int x0 = (int) (width * PERTANK_CROP_X0), x1 = (int) (width * PERTANK_CROP_X1);
            int y0 = (int) (height * PERTANK_CROP_Y0), y1 = (int) (height * PERTANK_CROP_Y1);
            int cropWidth = x1 - x0;
            int cropHeight = y1 - y0;

            boolean[] changed = new boolean[cropWidth * cropHeight];
            for (int y = 0; y < cropHeight; y++) {
                for (int x = 0; x < cropWidth; x++) {
                    changed[y * cropWidth + x] = channelDelta(
                            imageA.getPixelRGBA(x0 + x, y0 + y), imageB.getPixelRGBA(x0 + x, y0 + y)) > PERTANK_PIXEL_DELTA;
                }
            }

            int solid = 0;
            for (int y = 1; y < cropHeight - 1; y++) {
                for (int x = 1; x < cropWidth - 1; x++) {
                    int at = y * cropWidth + x;
                    if (changed[at] && changed[at - 1] && changed[at + 1]
                            && changed[at - cropWidth] && changed[at + cropWidth]) {
                        solid++;
                    }
                }
            }
            return solid;
        } catch (IOException e) {
            Fishtastic.LOGGER.error("[selftest] could not read {}", shotA, e);
            return -1;
        }
    }

    /** Largest per-channel difference between two ARGB pixels ({@code NativeImage.getPixelRGBA}). */
    private static int channelDelta(int pixelA, int pixelB) {
        int red = Math.abs(((pixelA >> 16) & 0xFF) - ((pixelB >> 16) & 0xFF));
        int green = Math.abs(((pixelA >> 8) & 0xFF) - ((pixelB >> 8) & 0xFF));
        int blue = Math.abs((pixelA & 0xFF) - (pixelB & 0xFF));
        return Math.max(red, Math.max(green, blue));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static FishTankBlockEntity tank(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FishTankBlockEntity tank ? tank : null;
    }

    private static ItemStack fish(String species, float sizeCm) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", species)));
        ItemSizeHelper.setSize(stack, sizeCm);
        return stack;
    }

    /** Puts the (spectator) player's eye at an absolute position and view ({@code tp} places the feet). */
    private static void camera(Minecraft mc, double x, double eyeY, double z, float yaw, float pitch) {
        double feetY = eyeY - mc.player.getEyeHeight();
        server(mc, s -> {
            run(s, "gamemode spectator @a");
            run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f", x, feetY, z, yaw, pitch));
        });
    }

    private static void server(Minecraft mc, Consumer<MinecraftServer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server));
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    static void check(String name, boolean pass, String detail) {
        Fishtastic.LOGGER.info("[selftest] CHECK {}: {} {}", name, pass ? "PASS" : "FAIL", detail);
    }

    private static String fileName(String scene, String shot) {
        return "selftest-" + loader + "-" + scene + "-" + shot + ".png";
    }

    /** Opaque frame of the main target (Screenshot forces alpha to 1). */
    static void screenshot(Minecraft mc, String scene, String shot) {
        Screenshot.grab(mc.gameDirectory, fileName(scene, shot), mc.getMainRenderTarget(),
                msg -> Fishtastic.LOGGER.info("[selftest] {}", msg.getString()));
    }

    /** Colour attachment of {@code target} with its alpha intact, for anything alpha matters for. */
    static void dump(Minecraft mc, RenderTarget target, String scene, String shot) {
        if (target == null) {
            check("dump." + scene + "." + shot, false, "target not created");
            return;
        }
        try (NativeImage image = new NativeImage(target.width, target.height, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            File out = new File(new File(mc.gameDirectory, "screenshots"), fileName(scene, shot));
            out.getParentFile().mkdirs();
            image.writeToFile(out);
            Fishtastic.LOGGER.info("[selftest] wrote {}", out);
        } catch (IOException e) {
            Fishtastic.LOGGER.error("[selftest] failed to write {}", shot, e);
        }
    }

    private RenderSelfTest() {}
}
