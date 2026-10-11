package grill24.fishtastic.client.selftest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.ItemHighlightRules;
import grill24.fishtastic.client.QuestProgressEvent;
import grill24.fishtastic.client.QuestProgressNotificationManager;
import grill24.fishtastic.client.renderer.FishtasticItemOutlineAtlas;
import grill24.fishtastic.client.renderer.FishtasticRenderPipelines;
import grill24.fishtastic.client.renderer.IrisCompat;
import grill24.fishtastic.client.renderer.TankInteriorLight;
import grill24.fishtastic.client.renderer.TankGroupFlock;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.util.ClientTankGroups;
import grill24.fishtastic.client.util.FishermanPoseDebug;
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
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.gamerules.GameRules;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Dev harness for unattended rendering checks: drives a client run that stages rendering scenes,
 * photographs them and quits, so visual checks need nobody at the keyboard.
 *
 * <p>Armed only when {@code <run dir>/fishtastic_render_selftest} exists; inert otherwise. Each
 * non-blank, non-{@code #} line of that file names a scene to run (an empty file runs them all).
 * Screenshots go to {@code <run dir>/screenshots/selftest-<loader>-<scene>-<shot>.png}; checks
 * the harness can make by itself are logged as {@code [selftest] CHECK <name>: PASS|FAIL ...}.
 * <b>The marker persists across launches and will hijack a playtest — delete it when done.</b>
 *
 * <p>Ported from the {@code port/1.21.1} branch, where it existed as a port-only harness (its
 * {@code port/1.20.1} sibling differs only in the same places this branch does: the dimensions
 * provider, {@code BundleContents}, and the pile-block item-model marker). The scenes and the
 * {@code CHECK} vocabulary are deliberately identical, so a run on either branch is comparable.
 * Where 26.1.2 has no equivalent of a 1.21.1 construct the scene says so rather than quietly
 * checking something else:
 *
 * <ul>
 *   <li>{@code fabulous} toggles {@code improvedTransparency} — 26.1.2 replaced
 *       {@code GraphicsStatus.FABULOUS} with that option, and {@link Minecraft#useShaderTransparency}
 *       now reads it off the render state.</li>
 *   <li>{@code outline} dumps the main target rather than the outline and mask atlases: 26.1.2
 *       creates those atlases without {@code USAGE_COPY_SRC} (usage 13), so they cannot be read
 *       back without changing {@link FishtasticItemOutlineAtlas}. The world shots still show every
 *       outline these atlases feed; the atlas's registration is asserted instead of its pixels.</li>
 *   <li>{@code hud} keeps its shots and drops the two checks that read {@code FishtasticHudLayers}
 *       counters. That class is a 1.21.1 construct: it exists there to draw the quest banners after
 *       vanilla's toasts, a problem 26.1.2 does not have.</li>
 *   <li>{@code prepare} sets the three world rules with commands instead of seeding them through
 *       {@link LevelSettings}, which in 26.1.2 no longer carries a {@link GameRules}.</li>
 * </ul>
 */
public final class RenderSelfTest {
    private static final String MARKER = "fishtastic_render_selftest";
    private static final String WORLD_NAME = "fishtastic_render_selftest";

    /** Scenes in the order they run. */
    private static final List<String> ALL_SCENES = List.of("tank", "shapes", "stress512", "items", "held", "outline", "fabulous",
            "guiscale", "fixes", "hud", "gizmos", "pertank", "highlight");

    /** Scenes that run only when the marker names them (they need something the default run lacks). */
    private static final List<String> OPT_IN_SCENES = List.of("matte", "matteclip", "fourwaters", "cathedral",
            "mcpsession", "cosmeticpreview", "columns", "spanpreview", "perfbench", "lighting", "bakebench", "scalebench",
            "companion", "companionparade", "companionpeek", "companionhand", "companionbond", "companionfishing",
            "companionscenes", "companionpolish", "companionpolish2", "companionpolish3");

    private static Boolean armed;
    /** Dev probe (§21): runs every client tick while non-null — tick-true sampling. */
    private static java.util.function.Consumer<Minecraft> tickProbe;
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
        // §21 probe: sampled every client tick while set (the step queue advances one step per
        // TWO ticks — step, then a tick of wait — so per-step samples can't measure per-tick
        // rates; anything needing tick-true curves goes through here).
        if (tickProbe != null) tickProbe.accept(mc);

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
        // 26.1.2's LevelSettings is (name, game type, difficulty settings, allowCommands, data
        // configuration) with no GameRules; the quiet-world rules are set by command in prepare().
        LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, new WorldOptions(1L, false, false),
                access -> access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    /**
     * Quiet world, no tutorial. Vanilla draws its toasts after every mod HUD layer, so the
     * tutorial's hint toasts would cover our overlays: stop() alone is not enough (the step is
     * recreated from the option), so the option is set to NONE too, and restored in {@link #finish}.
     */
    private static void prepare(Minecraft mc) {
        savedTutorialStep = mc.options.tutorialStep;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.getTutorial().stop();
        mc.getToastManager().clear();
        server(mc, s -> {
            run(s, "gamerule send_command_feedback false");
            // The quiet-world rules LevelSettings carried on 1.21.1, applied now that the world
            // exists — still long before any scene runs. 26.1 renamed them to snake_case; the old
            // camelCase names fail silently, which let the clock run (mcpsession builds went dark).
            run(s, "gamerule advance_time false");
            run(s, "gamerule spawn_mobs false");
            run(s, "gamerule advance_weather false");
            run(s, "time set 6000");
        });
        check("shaders.loaded", FishtasticRenderPipelines.WORLD_OUTLINE != null
                        && FishtasticRenderPipelines.TANK_WATER_FILL != null,
                "world_outline=" + FishtasticRenderPipelines.WORLD_OUTLINE
                        + " tank_water_fill=" + FishtasticRenderPipelines.TANK_WATER_FILL);
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
            case "matte" -> queueMatteScene();
            case "matteclip" -> queueMatteClipScene();
            case "fourwaters" -> queueFourWatersScene();
            case "cathedral" -> queueCathedralScene();
            case "columns" -> queueColumnsScene();
            case "spanpreview" -> queueSpanPreviewScene();
            case "perfbench" -> queuePerfBenchScene();
            case "bakebench" -> queueBakeBenchScene();
            case "scalebench" -> queueScaleBenchScene();
            case "lighting" -> queueLightingScene();
            case "mcpsession" -> queueMcpSessionScene();
            case "cosmeticpreview" -> queueCosmeticPreviewScene();
            case "companion" -> queueCompanionScene();
            case "companionparade" -> queueCompanionParadeScene();
            case "companionpeek" -> queueCompanionPeekScene();
            case "companionhand" -> queueCompanionHandScene();
            case "companionbond" -> queueCompanionBondScene();
            case "companionfishing" -> queueCompanionFishingScene();
            case "companionscenes" -> queueCompanionScenesScene();
            case "companionpolish" -> queueCompanionPolishScene();
            case "companionpolish2" -> queueCompanionPolish2Scene();
            case "companionpolish3" -> queueCompanionPolish3Scene();
            default -> throw new IllegalArgumentException(scene);
        }
    }

    /**
     * A 3-tank group with 12 fish (one legendary), a chest, a lit campfire and a lit-furnace
     * structure; a lone tank with a planted and a benthic creature; a fish pile. Photographed from
     * outside and from inside the middle tank (the tank body's back faces are culled, so the inside
     * view shows the BER alone).
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
     * Opt-in: cool-cam's alpha capture on a tank group floating in an emptied world. Needs the
     * cool-cam jar published to mavenLocal (this repo resolves it from there); it is driven through
     * its {@code /cc} commands so this class has no dependency on it. Checks, in order:
     * a single frame's alpha, that two captures of the frozen world are identical, a frozen orbit,
     * and an animated orbit that steps world ticks. Output lands in {@code <run dir>/coolcam_capture}.
     */
    private static void queueMatteScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            // Empty the flat world's ground so nothing but the tanks is left to be in frame.
            int[][] halves = {{-72, -1}, {0, 71}};
            for (int[] hx : halves) {
                for (int[] hz : halves) {
                    run(s, "fill " + (x + hx[0]) + " " + (y - 4) + " " + (z + hz[0]) + " " + (x + hx[1]) + " " + (y - 1) + " " + (z + hz[1]) + " minecraft:air");
                }
            }
            for (int dx = -1; dx <= 1; dx++) run(s, "setblock " + (x + dx) + " " + y + " " + z + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            String[] group = {"bluegill", "discus", "lionfish", "betta", "clown_loach", "giraffe_cichlid"};
            for (int i = 0; i < group.length; i++) {
                FishTankBlockEntity tank = tank(level, new BlockPos(x - 1 + i % 3, y, z));
                check("matte.addItem." + group[i], tank != null && tank.addItem(fish(group[i], 25f + 3f * i)), "");
            }
        }));
        queue(5, mc -> {
            // Nothing exists beyond the emptied 72-block radius, so keep the view inside it.
            savedRenderDistance = mc.options.renderDistance().get();
            mc.options.renderDistance().set(3);
            mc.options.hideGui = true;
            camera(mc, origin.getX() + 0.5, origin.getY() + 1.6, origin.getZ() + 1.2, 180f, 6f);
        });
        // Let the chunks mesh and the fish settle, then freeze for the single-frame checks.
        queue(100, mc -> server(mc, s -> run(s, "tick freeze")));
        queue(30, mc -> mc.player.connection.sendCommand("cc matte single_a"));
        queue(60, mc -> mc.player.connection.sendCommand("cc matte single_b"));
        queue(60, mc -> {
            File a = captureFrame("single_a", 0), b = captureFrame("single_b", 0);
            matteStats("single", a);
            check("matte.deterministic", sameImage(a, b), "two captures of the frozen world " + (sameImage(a, b) ? "match" : "differ"));
            server(mc, s -> run(s, "tick unfreeze"));
        });
        // A frozen orbit: 8 frames round the tank group.
        double cx = origin.getX() + 0.5, cy = origin.getY() + 0.5, cz = origin.getZ() - 3.5;
        queue(20, mc -> {
            mc.player.connection.sendCommand("cc capture ticks 0");
            mc.player.connection.sendCommand("cc capture fov 40");
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "cc capture orbit orbit_frozen 8 6 2.0 %.2f %.2f %.2f", cx, cy, cz));
        });
        queue(400, mc -> {
            int present = 0;
            for (int i = 0; i < 8; i++) if (captureFrame("orbit_frozen", i).isFile()) present++;
            check("matte.orbit.frames", present == 8, present + " of 8 frames written");
            File f0 = captureFrame("orbit_frozen", 0), f2 = captureFrame("orbit_frozen", 2);
            if (present == 8) {
                matteStats("orbit0", f0);
                check("matte.orbit.rotates", !sameImage(f0, f2), "frame 0 and frame 2 (90 degrees apart) differ");
            }
            check("matte.orbit.encodeNotes", new File(new File(mc.gameDirectory, "coolcam_capture/orbit_frozen"), "encode.txt").isFile(), "");
            // Frozen first, so the only ticks that pass are the ones the capture steps.
            server(mc, s -> run(s, "tick freeze"));
        });
        // An animated orbit: 20 tps at 30 fps is 2/3 of a tick per frame, so 12 frames step 7 ticks.
        queue(20, mc -> {
            animatedStartTime = mc.level.getGameTime();
            mc.player.connection.sendCommand("cc capture ticks 0.6667");
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "cc capture orbit orbit_anim 12 6 2.0 %.2f %.2f %.2f", cx, cy, cz));
        });
        queue(600, mc -> {
            int present = 0;
            for (int i = 0; i < 12; i++) if (captureFrame("orbit_anim", i).isFile()) present++;
            long advanced = mc.level.getGameTime() - animatedStartTime;
            check("matte.anim.frames", present == 12, present + " of 12 frames written");
            check("matte.anim.ticksStepped", advanced == 7, "world advanced " + advanced + " tick(s) (7 expected)");
            check("matte.anim.stayedFrozen", mc.level.tickRateManager().isFrozen(), "frozen=" + mc.level.tickRateManager().isFrozen());
            server(mc, s -> run(s, "tick unfreeze"));
            mc.player.connection.sendCommand("cc capture ticks 0");
            mc.player.connection.sendCommand("cc capture fov off");
            mc.options.renderDistance().set(savedRenderDistance);
            mc.options.hideGui = false;
        });
    }

    /**
     * Opt-in: one long animated orbit for promo test clips — a 3-tank group with 12 fish (one
     * legendary) floating in an emptied world, 150 frames at 2/3 tick per frame (30 fps real time).
     */
    private static void queueMatteClipScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            int[][] halves = {{-72, -1}, {0, 71}};
            for (int[] hx : halves) {
                for (int[] hz : halves) {
                    run(s, "fill " + (x + hx[0]) + " " + (y - 4) + " " + (z + hz[0]) + " " + (x + hx[1]) + " " + (y - 1) + " " + (z + hz[1]) + " minecraft:air");
                }
            }
            for (int dx = -1; dx <= 1; dx++) run(s, "setblock " + (x + dx) + " " + y + " " + z + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            String[] group = {"bluegill", "discus", "lionfish", "betta", "clown_loach", "giraffe_cichlid",
                    "golden_trout", "arctic_char", "greenstripe_barb", "blind_cave_tetra", "lined_seahorse", "glass_catfish"};
            for (int i = 0; i < group.length; i++) {
                FishTankBlockEntity tank = tank(level, new BlockPos(x - 1 + i % 3, y, z));
                ItemStack fish = fish(group[i], 25f + 3f * i);
                if (i == 2) FishtasticItemData.set(fish, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.LEGENDARY));
                if (tank != null) tank.addItem(fish);
            }
        }));
        queue(5, mc -> {
            savedRenderDistance = mc.options.renderDistance().get();
            mc.options.renderDistance().set(3);
            mc.options.hideGui = true;
            camera(mc, origin.getX() + 0.5, origin.getY() + 1.6, origin.getZ() + 1.2, 180f, 6f);
        });
        queue(160, mc -> {
            double cx = origin.getX() + 0.5, cy = origin.getY() + 0.5, cz = origin.getZ() - 3.5;
            mc.player.connection.sendCommand("cc capture ticks 0.6667");
            mc.player.connection.sendCommand("cc capture fov 38");
            mc.player.connection.sendCommand("cc capture size 1920 1080");
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "cc capture orbit promo_orbit 150 5.2 1.6 %.2f %.2f %.2f", cx, cy, cz));
        });
        queue(1800, mc -> {
            int present = 0;
            for (int i = 0; i < 150; i++) if (captureFrame("promo_orbit", i).isFile()) present++;
            check("matteclip.frames", present == 150, present + " of 150 frames written");
            mc.player.connection.sendCommand("cc capture ticks 0");
            mc.player.connection.sendCommand("cc capture fov off");
            mc.player.connection.sendCommand("cc capture size off");
            mc.options.renderDistance().set(savedRenderDistance);
            mc.options.hideGui = false;
        });
    }

    /**
     * Opt-in promo piece, "Four Waters": a 60 fps, 20 s alpha-matte orbit. The centre is the sea as
     * one connected three-high column, shading from sunlit quartz under a glass cupola down through
     * prismarine to a deepslate floor; around it stand three separate waters. Earth is a desert pool
     * of pupfish and killifish over red sand and bleached coral. Air is an all-glass box of glass
     * creatures hung in the air. Fire is a blackstone forge over magma for the molten idol.
     */
    private static void queueFourWatersScene() {
        final int frames = 1200;
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            clearVoid(s, x, y, z);
            for (BlockPos p : fourWatersTanks()) run(s, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;

            // Water: the sea, surface to floor, as one connected volume.
            FishTankBlockEntity floor = tank(level, new BlockPos(x, y, z));
            FishTankBlockEntity middle = tank(level, new BlockPos(x, y + 1, z));
            FishTankBlockEntity surface = tank(level, new BlockPos(x, y + 2, z));
            floor.setShape(FishTankShape.RAMPART);
            floor.setMaterials(materials(new String[]{"minecraft:deepslate_tiles", "minecraft:gravel", "fishtastic:blue_clear_stained_glass"}));
            middle.setShape(FishTankShape.MULLION);
            middle.setMaterials(materials(new String[]{"minecraft:prismarine_bricks", "minecraft:sand", "fishtastic:light_blue_clear_stained_glass"}));
            surface.setShape(FishTankShape.CUPOLA);
            surface.setMaterials(materials(new String[]{"minecraft:smooth_quartz", "minecraft:sand", "fishtastic:clear_glass"}));
            floor.setCosmetic(new CosmeticGridCell(0, 1), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3));
            floor.setCosmetic(new CosmeticGridCell(2, 1), new PlacedCosmetic(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 4)));
            floor.setCosmetic(new CosmeticGridCell(1, 0), new PlacedCosmetic(Blocks.TUBE_CORAL_FAN.defaultBlockState()));
            floor.setStructureCosmetic(new CosmeticGridCell(1, 1), new FishTankBlockEntity.PlacedStructureCosmetic(
                    structure("coral_reef_1"), Rotation.NONE), List.of(new CosmeticGridCell(1, 1)));
            stock("fourwaters.floor", floor, fish("japanese_spider_crab", 55f), fish("flapjack_octopus", 26f), fish("angler_fish", 33f),
                    fish("chaunacops", 28f), fish("royal_garden_eel", 40f));
            stock("fourwaters.middle", middle, fish("lionfish", 32f), fish("leafy_sea_dragon", 45f), fish("lined_seahorse", 16f),
                    fish("lined_seahorse", 17f), fish("yellowstripe_grunt", 24f), fish("yellowstripe_grunt", 25f), fish("yellowstripe_grunt", 26f));
            stock("fourwaters.surface", surface, fish("moorish_idol", 52f), fish("parrotfish", 48f), fish("rainfordia", 15f), fish("rainfordia", 14f),
                    fish("neon_goby", 5f), fish("neon_goby", 5.5f), fish("neon_goby", 4.5f), fish("neon_goby", 5f));

            // Earth: a desert's memory of a sea. Two dunes joined into one hill.
            FishTankBlockEntity duneA = tank(level, new BlockPos(x - 2, y, z + 1));
            FishTankBlockEntity duneB = tank(level, new BlockPos(x - 2, y, z + 2));
            for (FishTankBlockEntity dune : List.of(duneA, duneB)) {
                dune.setShape(FishTankShape.DUNE);
                dune.setMaterials(materials(new String[]{"minecraft:cut_red_sandstone", "minecraft:red_sand", "fishtastic:clear_glass"}));
            }
            duneA.setCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.DEAD_BUSH.defaultBlockState()));
            duneA.setCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState()));
            duneB.setCosmetic(new CosmeticGridCell(2, 1), new PlacedCosmetic(Blocks.DEAD_BUSH.defaultBlockState()));
            duneB.setCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState()));
            stock("fourwaters.duneA", duneA, fish("devils_hole_pupfish", 9f), fish("devils_hole_pupfish", 10f), fish("devils_hole_pupfish", 8.5f),
                    fish("orange_australe_killifish", 10f), fish("orange_australe_killifish", 11f), fish("starfish", 40f));
            stock("fourwaters.duneB", duneB, fish("devils_hole_pupfish", 9.5f), fish("devils_hole_pupfish", 9f), fish("orange_australe_killifish", 9.5f),
                    fish("sulphur_molly", 12f), fish("sulphur_molly", 11f), fish("garden_eel", 35f), fish("garden_eel", 33f));

            // Air: see-through creatures, in a see-through box, hung in see-through air.
            FishTankBlockEntity air = tank(level, new BlockPos(x - 1, y + 2, z - 2));
            air.setShape(FishTankShape.VITRINE);
            air.setMaterials(materials(new String[]{"minecraft:calcite", "minecraft:sand", "fishtastic:borderless_glass"}));
            stock("fourwaters.air", air, fish("glass_squid", 40f), fish("portuguese_man_o_war", 38f), fish("indian_glassy_fish", 13f),
                    fish("indian_glassy_fish", 14f), fish("indian_glassy_fish", 12.5f), fish("glass_catfish", 18f), fish("glass_catfish", 19f));

            // Fire: a forge where the idol was remade in magma, and a campfire that burns underwater.
            FishTankBlockEntity fire = tank(level, new BlockPos(x + 2, y, z - 1));
            fire.setShape(FishTankShape.BASTION);
            fire.setMaterials(materials(new String[]{"minecraft:gilded_blackstone", "minecraft:magma_block", "fishtastic:clear_glass"}));
            fire.setCosmetic(new CosmeticGridCell(1, 2), new PlacedCosmetic(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)));
            fire.setCosmetic(new CosmeticGridCell(1, 0), new PlacedCosmetic(Blocks.FIRE_CORAL_FAN.defaultBlockState()));
            ItemStack idol = fish("molten_moorish_idol", 58f);
            FishtasticItemData.set(idol, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.LEGENDARY));
            stock("fourwaters.fire", fire, idol, fish("fire_dartfish", 14f), fish("fire_dartfish", 13f), fish("fire_dartfish", 15f), fish("blazed_grub", 28f));
        }));
        queuePromoOrbit("four_waters", frames, new Vec3(origin.getX() + 0.5, origin.getY() + 1.4, origin.getZ() - 3.5), 9.0, 1.8, 36);
    }

    /**
     * Opt-in promo piece, "Drowned Cathedral": one 5x2x2 tank of ARCH segments, whose arches run on
     * across the seams into a two-storey arcade, a sunken nave. Moss climbs the lower storey; a kelp
     * forest rises through both, lamps hang from the vault; schools fill it like a choir, with a ruin and two lamps for altar.
     */
    private static void queueCathedralScene() {
        final int frames = 1200;
        final int length = 5, depth = 2, height = 2;
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            clearVoid(s, x, y, z);
            run(s, "fill " + x + " " + y + " " + z + " " + (x + length - 1) + " " + (y + height - 1) + " " + (z + depth - 1) + " fishtastic:fish_tank");
        }));
        queue(10, mc -> server(mc, s -> {
            ServerLevel level = s.overworld();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
            List<FishTankBlockEntity> all = new java.util.ArrayList<>();
            for (int dy = 0; dy < height; dy++) {
                for (int dx = 0; dx < length; dx++) {
                    for (int dz = 0; dz < depth; dz++) {
                        FishTankBlockEntity tank = tank(level, new BlockPos(x + dx, y + dy, z + dz));
                        tank.setShape(FishTankShape.ARCH);
                        tank.setMaterials(materials(new String[]{dy == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks",
                                "minecraft:sand", "fishtastic:clear_glass"}));
                        all.add(tank);
                    }
                }
            }

            // The altar end: a sunken ruin, lamps for candles, a boulder and a coral clump in the aisles.
            nave(level, 1, 0).setStructureCosmetic(new CosmeticGridCell(1, 1), new FishTankBlockEntity.PlacedStructureCosmetic(
                    structure("castle_ruin"), Rotation.NONE), List.of(new CosmeticGridCell(1, 1), new CosmeticGridCell(0, 0), new CosmeticGridCell(0, 1),
                    new CosmeticGridCell(1, 0), new CosmeticGridCell(2, 0), new CosmeticGridCell(2, 1), new CosmeticGridCell(2, 2)));
            placeStructure(nave(level, 0, 1), "cosmetic_lamp_prismarine_bricks", 1, 1);
            placeStructure(nave(level, 4, 0), "cosmetic_lamp_prismarine_bricks", 1, 1);
            placeStructure(nave(level, 3, 1), "mossy_boulder", 0, 1);
            placeStructure(nave(level, 2, 1), "coral_reef_2", 2, 1);

            // The kelp forest: seeded, so every render grows the same one. Strands that reach the
            // lower storey's ceiling sometimes carry on up through the upper one.
            java.util.Random rng = new java.util.Random(1517);
            for (int dx = 0; dx < length; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    FishTankBlockEntity floor = tank(level, new BlockPos(x + dx, y, z + dz));
                    for (int gx = 0; gx < 3; gx++) {
                        for (int gz = 0; gz < 3; gz++) {
                            CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
                            if (floor.getStructureAnchor(cell) != null) continue;
                            double roll = rng.nextDouble();
                            if (roll < 0.5) {
                                // One strand per cell, rooted in the floor tank and growing on up through
                                // the open storey above (up to TankColumns.maxSegments(2) = 7 segments).
                                int h = rng.nextDouble() < 0.6 ? 3 : 2;
                                if (h == 3 && rng.nextDouble() < 0.55) h += 1 + rng.nextInt(4);
                                floor.setCosmetic(cell, new PlacedCosmetic(Blocks.KELP.defaultBlockState(), h));
                            } else if (roll < 0.75) {
                                floor.setCosmetic(cell, new PlacedCosmetic(Blocks.SEAGRASS.defaultBlockState()));
                            } else if (roll < 0.8) {
                                floor.setCosmetic(cell, new PlacedCosmetic(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + rng.nextInt(3))));
                            }
                        }
                    }
                }
            }

            // The upper storey's own decoration: lamps hung from the vault on chains, roots and
            // glow berries trailing down between them. Hanging cosmetics live on the top tank's lid.
            for (int dx = 0; dx < length; dx++) {
                FishTankBlockEntity vault = tank(level, new BlockPos(x + dx, y + height - 1, z + (dx % 2)));
                if (dx % 2 == 0) {
                    vault.setCeilingCosmetic(new CosmeticGridCell(1, 1), new PlacedCosmetic(Blocks.LANTERN.defaultBlockState(), 2 + dx % 3));
                } else {
                    vault.setCeilingCosmetic(new CosmeticGridCell(1, 1), new PlacedCosmetic(Blocks.SOUL_LANTERN.defaultBlockState(), 3));
                }
                vault.setCeilingCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.HANGING_ROOTS.defaultBlockState()));
                vault.setCeilingCosmetic(new CosmeticGridCell(2, 0), new PlacedCosmetic(Blocks.CAVE_VINES.defaultBlockState(), 2 + rng.nextInt(3)));
            }
            tank(level, new BlockPos(x + 2, y + height - 1, z + 1)).setCeilingCosmetic(new CosmeticGridCell(1, 1),
                    new PlacedCosmetic(Blocks.POINTED_DRIPSTONE.defaultBlockState(), 4));
            tank(level, new BlockPos(x + 3, y + height - 1, z)).setCeilingCosmetic(new CosmeticGridCell(1, 1),
                    new PlacedCosmetic(Blocks.IRON_CHAIN.defaultBlockState(), 5));

            // The choir: schools of small fish, a few medium voices, and the quiet ones in the weeds.
            List<ItemStack> fish = new java.util.ArrayList<>();
            school(fish, rng, "neon_tetra", 24, 10f);
            school(fish, rng, "bridle_shiner", 16, 9f);
            school(fish, rng, "greenstripe_barb", 14, 12f);
            school(fish, rng, "yellowline_goby", 10, 10f);
            school(fish, rng, "yellowstripe_grunt", 16, 22f);
            school(fish, rng, "rainfordia", 10, 13f);
            school(fish, rng, "moorish_idol", 6, 38f);
            school(fish, rng, "parrotfish", 3, 40f);
            school(fish, rng, "lined_seahorse", 4, 15f);
            school(fish, rng, "leafy_sea_dragon", 2, 40f);
            school(fish, rng, "garden_eel", 5, 32f);
            school(fish, rng, "willans_chromodoris", 2, 14f);
            school(fish, rng, "trapania_scurra", 2, 12f);
            int added = 0, next = 0;
            for (ItemStack f : fish) {
                for (int tries = 0; tries < all.size(); tries++) {
                    FishTankBlockEntity tank = all.get(next++ % all.size());
                    if (tank.addItem(f)) { added++; break; }
                }
            }
            check("cathedral.stocked", added == fish.size(), added + " of " + fish.size() + " fish added");
        }));
        queuePromoOrbit("drowned_cathedral", frames, new Vec3(origin.getX() + length / 2.0, origin.getY() + 1.0, origin.getZ() - 4 + depth / 2.0), 7.6, 1.7, 36);
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
            lone.setCeilingCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.IRON_CHAIN.defaultBlockState(), 2));
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
        Object[] savedInactivity = new Object[1];
        queue(1, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            savedInactivity[0] = mc.options.inactivityFpsLimit().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            // Nobody is at the keyboard: the AFK limiter would drop to 30 fps a minute in.
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
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
            mc.options.inactivityFpsLimit().set((net.minecraft.client.InactivityFpsLimit) savedInactivity[0]);
            mc.options.hideGui = false;
            mc.levelRenderer.allChanged();
            Fishtastic.LOGGER.info("[bench] DONE");
        });
    }

    // ── bakebench / scalebench: what a cosmetic structure costs (client/perf/CosmeticCostBench) ──

    /**
     * Geometry and cold-cache bake time of every shipped structure ({@code [bakebench] STRUCT} lines),
     * with the vertex size of each chunk layer. Marker param: {@code reps=} timed bakes per structure.
     */
    private static void queueBakeBenchScene() {
        int reps = Integer.parseInt(param("reps", "21"));
        queue(40, mc -> {
            var registry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
            List<String> names = registry.keySet().stream().map(id -> id.getPath()).sorted().toList();
            grill24.fishtastic.client.perf.CosmeticCostBench.run(names, n -> registry.getOptional(structure(n)).orElse(null), reps);
            Fishtastic.LOGGER.info("[bakebench] DONE");
        });
    }

    /**
     * Steady-state frame cost of cosmetic structures as their amount grows. For each {@code structure=}
     * (repeatable) and each copy count ({@code spanCounts=} boxes for spans, laid out in a square;
     * {@code floorCounts=} tanks for floor structures), the same tanks are measured with cosmetics
     * off (NONE) and baked into the mesh (MESH), in pairs whose order alternates by round. The
     * pseudo-structure {@code empty} is the A/A control: tanks with nothing in them, where MESH and
     * NONE draw the same thing. RESULT lines carry the scene's total cosmetic quads; raw frames go
     * to {@code <run>/fishtastic_bench/scale_<loader>_<label>_<structure>_<count>_<mode>_r<N>.csv}.
     */
    private static void queueScaleBenchScene() {
        int frames = Integer.parseInt(param("frames", "3000"));
        int rounds = Integer.parseInt(param("rounds", "5"));
        String label = param("label", "default");
        int[] spanCounts = java.util.Arrays.stream(param("spanCounts", "1,4,9").split(",")).mapToInt(Integer::parseInt).toArray();
        int[] floorCounts = java.util.Arrays.stream(param("floorCounts", "9,36").split(",")).mapToInt(Integer::parseInt).toArray();
        List<String> names = params("structure").isEmpty() ? List.of("empty", "sunken_ziggurat") : params("structure");
        // ab=mesh (default): NONE against MESH. ab=scan: both halves MESH, the per-frame floor-structure
        // part scans on against skipped (CosmeticBenchmark.skipStructureScans).
        boolean scanAb = param("ab", "mesh").equals("scan");
        int x0 = origin.getX() - 9, y = origin.getY(), z0 = origin.getZ() - 4;
        var tankState = grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().defaultBlockState();
        int[] savedLimit = new int[1];
        boolean[] savedVsync = new boolean[1];
        Object[] savedInactivity = new Object[1];
        queue(1, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            savedInactivity[0] = mc.options.inactivityFpsLimit().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            mc.options.hideGui = true;
            // fullscreen=true: frame times depend on the framebuffer size, and a launch can come up windowed
            // at the 854x480 default even with fullscreen set in options. The START line records the size.
            if (param("fullscreen", "false").equals("true") && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        });
        queue(40, mc -> Fishtastic.LOGGER.info("[scale] START loader={} label={} frames={} rounds={} window={}x{}", loader, label, frames, rounds,
                mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        for (String name : names) {
            boolean[] isSpan = new boolean[1];
            int[] box = {1, 1, 1};
            queue(1, mc -> {
                var registry = mc.level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
                var st = name.equals("empty") ? null : registry.getOptional(structure(name)).orElse(null);
                isSpan[0] = st != null && st.span().isPresent();
                if (isSpan[0]) { box[0] = st.span().get().x(); box[1] = st.span().get().y(); box[2] = st.span().get().z(); }
            });
            // Whether a structure is a span is only known once the registry is up, so both count
            // lists are queued and the one that doesn't apply is skipped when the scene runs.
            // The empty control runs the floor counts.
            java.util.List<int[]> plan = new java.util.ArrayList<>();
            for (int c : spanCounts) plan.add(new int[]{1, c});
            for (int c : floorCounts) plan.add(new int[]{0, c});
            for (int[] entry : plan) {
                boolean spanCount = entry[0] == 1;
                int count = entry[1];
                List<BlockPos> tanks = new java.util.ArrayList<>();
                boolean[] skip = new boolean[1];
                int[] sceneQuads = new int[1];
                double[] cam = new double[4];
                queue(1, mc -> {
                    skip[0] = name.equals("empty") ? spanCount : spanCount != isSpan[0];
                    if (skip[0]) return;
                    server(mc, s -> {
                        run(s, "fill " + (x0 - 3) + " " + (y - 1) + " " + (z0 - 3) + " " + (x0 + 24) + " " + (y + 5) + " " + (z0 + 16) + " minecraft:air");
                        tanks.clear();
                        var level = s.overworld();
                        int k = (int) Math.ceil(Math.sqrt(count));
                        if (isSpan[0]) {
                            var registry = level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
                            for (int i = 0; i < count; i++) {
                                BlockPos min = new BlockPos(x0 + (i % k) * (box[0] + 1), y, z0 + (i / k) * (box[2] + 1));
                                for (BlockPos p : BlockPos.betweenClosed(min, min.offset(box[0] - 1, box[1] - 1, box[2] - 1))) {
                                    level.setBlockAndUpdate(p, tankState);
                                    tanks.add(p.immutable());
                                }
                                benchPlaceSpan(level, registry, min, name);
                            }
                            cam[0] = x0 + (k * (box[0] + 1) - 1) / 2.0;
                            cam[1] = k * (box[0] + 1);
                            cam[2] = k * (box[2] + 1);
                            cam[3] = box[1];
                        } else {
                            for (int i = 0; i < count; i++) {
                                BlockPos p = new BlockPos(x0 + (i % k) * 2, y, z0 + (i / k) * 2);
                                level.setBlockAndUpdate(p, tankState);
                                FishTankBlockEntity t = tank(level, p);
                                if (t != null && !name.equals("empty")) placeStructure(t, name, 1, 1);
                                tanks.add(p);
                            }
                            cam[0] = x0 + (k * 2 - 1) / 2.0;
                            cam[1] = k * 2;
                            cam[2] = k * 2;
                            cam[3] = 1;
                        }
                    });
                });
                queue(20, mc -> {
                    if (skip[0]) return;
                    double dist = Math.max(4.0, 0.62 * cam[1] + 1.5);
                    camera(mc, cam[0], y + cam[3] + 0.45 * dist, z0 - dist, 0f, 28f);
                });
                queue(30, mc -> {
                    if (skip[0]) return;
                    screenshot(mc, "scalebench", label + "_" + name + "_" + count);
                    int q = 0;
                    for (BlockPos pos : tanks) {
                        if (mc.level.getBlockEntity(pos) instanceof FishTankBlockEntity t) {
                            var snap = grill24.fishtastic.client.compositemodel.TankCosmeticMesh.compute(t);
                            var baked = grill24.fishtastic.client.compositemodel.TankCosmeticMesh.bake(snap);
                            q += baked.untinted().getAll().size() + baked.tinted().size();
                        }
                    }
                    sceneQuads[0] = q;
                });
                for (int round = 0; round < rounds; round++) {
                    for (int half = 0; half < 2; half++) {
                        boolean first = (half + round) % 2 == 0;
                        var mode = (first && !scanAb) ? grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.NONE
                                : grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.MESH;
                        boolean skipScans = scanAb && !first;
                        String arm = scanAb ? (skipScans ? "noscan" : "scan") : mode.name().toLowerCase(Locale.ROOT);
                        String tag = "scale_" + loader + "_" + label + "_" + name + "_" + count + "_" + arm + "_r" + (round + 1);
                        queue(1, mc -> {
                            if (skip[0]) return;
                            grill24.fishtastic.client.perf.CosmeticBenchmark.mode = mode;
                            grill24.fishtastic.client.perf.CosmeticBenchmark.skipStructureScans = skipScans;
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
                        waitFor(1, 600, mc -> skip[0] || mc.levelRenderer.hasRenderedAllSections(), mc -> {
                            if (skip[0]) return;
                            Fishtastic.LOGGER.info(String.format(Locale.ROOT,
                                    "[scale] BAKE %s rebuild_wait_ms=%.0f snapshots=%d snapshot_ms_total=%.2f bakes=%d bake_ms_total=%.2f quads=%d",
                                    tag, (System.nanoTime() - rebuildStart[0]) / 1e6,
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotCount.get(),
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotNanos.get() / 1e6,
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.bakeCount.get(),
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.bakeNanos.get() / 1e6,
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.bakedQuads.get()));
                        });
                        queue(40, mc -> { if (!skip[0]) grill24.fishtastic.client.perf.CosmeticBenchmark.start(frames); });
                        waitFor(1, 4000, mc -> {
                            if (skip[0]) return true;
                            grill24.fishtastic.client.perf.CosmeticBenchmark.drain();
                            return grill24.fishtastic.client.perf.CosmeticBenchmark.isDone();
                        }, mc -> {
                            if (skip[0]) return;
                            Fishtastic.LOGGER.info("[scale] RESULT {} tanks={} scene_quads={} {}", tag, tanks.size(), sceneQuads[0],
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.finish(tag));
                        });
                    }
                }
            }
        }
        queue(1, mc -> {
            grill24.fishtastic.client.perf.CosmeticBenchmark.mode = grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.MESH;
            grill24.fishtastic.client.perf.CosmeticBenchmark.skipStructureScans = false;
            mc.options.framerateLimit().set(savedLimit[0]);
            mc.options.enableVsync().set(savedVsync[0]);
            mc.options.inactivityFpsLimit().set((net.minecraft.client.InactivityFpsLimit) savedInactivity[0]);
            mc.options.hideGui = false;
            mc.levelRenderer.allChanged();
            Fishtastic.LOGGER.info("[scale] DONE");
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
        var registry = level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
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

    private static FishTankBlockEntity nave(ServerLevel level, int dx, int dz) {
        return tank(level, new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ() - 4 + dz));
    }

    private static void placeStructure(FishTankBlockEntity tank, String name, int gx, int gz) {
        CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
        tank.setStructureCosmetic(cell, new FishTankBlockEntity.PlacedStructureCosmetic(structure(name), Rotation.NONE), List.of(cell));
    }

    /** {@code count} fish of one species, sizes scattered a little round {@code sizeCm}. */
    private static void school(List<ItemStack> out, java.util.Random rng, String species, int count, float sizeCm) {
        for (int i = 0; i < count; i++) out.add(fish(species, sizeCm * (0.85f + 0.3f * rng.nextFloat())));
    }

    /** Empties the flat world's ground round the scene, so nothing but the subject is in frame. */
    private static void clearVoid(MinecraftServer s, int x, int y, int z) {
        int[][] halves = {{-72, -1}, {0, 71}};
        for (int[] hx : halves) {
            for (int[] hz : halves) {
                run(s, "fill " + (x + hx[0]) + " " + (y - 4) + " " + (z + hz[0]) + " " + (x + hx[1]) + " " + (y - 1) + " " + (z + hz[1]) + " minecraft:air");
            }
        }
        run(s, "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 6) + " " + (y + 4) + " " + (z + 3) + " minecraft:air");
    }

    /**
     * The shared tail of the promo scenes: let chunks mesh and the shoals spread, then a 60 fps
     * animated alpha orbit (a third of a tick per frame), waiting for the last frame before tidying up.
     */
    private static void queuePromoOrbit(String name, int frames, Vec3 center, double radius, double height, int fov) {
        queue(5, mc -> {
            savedRenderDistance = mc.options.renderDistance().get();
            mc.options.renderDistance().set(3);
            mc.options.hideGui = true;
            camera(mc, center.x, center.y + 1.5, center.z + 8.0, 180f, 10f);
        });
        queue(240, mc -> {
            mc.player.connection.sendCommand("cc capture ticks 0.3333333333333333");
            mc.player.connection.sendCommand("cc capture fov " + fov);
            mc.player.connection.sendCommand("cc capture size 1920 1080");
            mc.player.connection.sendCommand("cc capture warmup 3");
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "cc capture orbit %s %d %.2f %.2f %.2f %.2f %.2f",
                    name, frames, radius, height, center.x, center.y, center.z));
        });
        waitFor(20, 72000, mc -> captureFrame(name, frames - 1).isFile(), mc -> {
            int present = 0;
            for (int i = 0; i < frames; i++) if (captureFrame(name, i).isFile()) present++;
            check(name + ".frames", present == frames, present + " of " + frames + " frames written");
            mc.player.connection.sendCommand("cc capture ticks 0");
            mc.player.connection.sendCommand("cc capture fov off");
            mc.player.connection.sendCommand("cc capture size off");
            mc.options.renderDistance().set(savedRenderDistance);
            mc.options.hideGui = false;
        });
    }

    private static List<BlockPos> fourWatersTanks() {
        int x = origin.getX(), y = origin.getY(), z = origin.getZ() - 4;
        return List.of(new BlockPos(x, y, z), new BlockPos(x, y + 1, z), new BlockPos(x, y + 2, z),
                new BlockPos(x - 2, y, z + 1), new BlockPos(x - 2, y, z + 2),
                new BlockPos(x - 1, y + 2, z - 2), new BlockPos(x + 2, y, z - 1));
    }

    private static ResourceKey<grill24.fishtastic.fishtank.CosmeticStructure> structure(String name) {
        return ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, Ids.of("fishtastic", name));
    }

    /** Adds every fish to {@code tank}, checking each one fits its budget. */
    private static void stock(String label, FishTankBlockEntity tank, ItemStack... fish) {
        int added = 0;
        for (ItemStack f : fish) if (tank != null && tank.addItem(f)) added++;
        check(label + ".stocked", added == fish.length, added + " of " + fish.length + " fish added");
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
     * Opt-in: holds an empty void world open with the MCP bridge running, for an agent to build
     * cosmetic structures in and capture them. Needs {@code enabled=true} in
     * {@code config/fishtastic-mcp.properties} (the bridge's own opt-in gate, left to the agent so it
     * is never on by accident). Starts the bridge as the world owner, fences the build site with the
     * bridge region, writes the port and token to {@link #MCP_SESSION_FILE}, then waits (up to
     * {@code timeoutMinutes}, default 90) for {@link #MCP_SESSION_DONE} to appear before stopping the
     * bridge and quitting. The build site's centre floor block is logged and written to the file.
     */
    private static void queueMcpSessionScene() {
        File sessionFile = new File(Minecraft.getInstance().gameDirectory, MCP_SESSION_FILE);
        File doneFile = new File(Minecraft.getInstance().gameDirectory, MCP_SESSION_DONE);
        sessionFile.delete();
        doneFile.delete();
        queue(1, mc -> server(mc, s -> {
            BlockPos site = buildSite();
            clearVoid(s, site.getX(), site.getY(), site.getZ());
            run(s, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
                    site.getX() - BUILD_SITE_RADIUS, site.getY(), site.getZ() - BUILD_SITE_RADIUS,
                    site.getX() + BUILD_SITE_RADIUS, site.getY() + BUILD_SITE_HEIGHT, site.getZ() + BUILD_SITE_RADIUS));
            run(s, "gamemode creative @a");
        }));
        queue(10, mc -> {
            BlockPos site = buildSite();
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            server(mc, s -> run(s, String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f 180 25",
                    site.getX() + 0.5, site.getY() + 4.0, site.getZ() + 9.5)));
            // Sent as the player: the bridge only starts for the singleplayer owner.
            mc.player.connection.sendCommand("fishtastic mcp start");
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "fishtastic mcp region set %d %d %d %d %d %d",
                    site.getX() - BUILD_SITE_RADIUS, site.getY(), site.getZ() - BUILD_SITE_RADIUS,
                    site.getX() + BUILD_SITE_RADIUS, site.getY() + BUILD_SITE_HEIGHT, site.getZ() + BUILD_SITE_RADIUS));
        });
        queue(20, mc -> {
            BlockPos site = buildSite();
            boolean running = grill24.fishtastic.mcp.McpBridgeState.isRunning();
            check("mcpsession.bridgeRunning", running, running ? "port " + grill24.fishtastic.mcp.McpBridgeState.getPort()
                    : "not running - is enabled=true in config/fishtastic-mcp.properties?");
            if (!running) return;
            String text = "# Written by the RenderSelfTest mcpsession scene; copy over tools/mcp-bridge/.env.\n"
                    + "FISHTASTIC_MCP_PORT=" + grill24.fishtastic.mcp.McpBridgeState.getPort() + "\n"
                    + "FISHTASTIC_MCP_TOKEN=" + grill24.fishtastic.mcp.McpBridgeState.getToken() + "\n"
                    + "# build site centre floor block (anchor here): " + site.getX() + " " + site.getY() + " " + site.getZ() + "\n"
                    + "# region: " + (site.getX() - BUILD_SITE_RADIUS) + " " + site.getY() + " " + (site.getZ() - BUILD_SITE_RADIUS)
                    + " to " + (site.getX() + BUILD_SITE_RADIUS) + " " + (site.getY() + BUILD_SITE_HEIGHT) + " " + (site.getZ() + BUILD_SITE_RADIUS) + "\n";
            try {
                Files.writeString(sessionFile.toPath(), text);
            } catch (IOException e) {
                check("mcpsession.sessionFile", false, e.toString());
                return;
            }
            Fishtastic.LOGGER.info("[selftest] mcp session ready: site {} {} {}, session file {}, create {} to end",
                    site.getX(), site.getY(), site.getZ(), sessionFile, doneFile);
        });
        int timeoutTicks = 20 * 60 * Integer.parseInt(param("timeoutMinutes", "90"));
        waitFor(20, timeoutTicks, mc -> doneFile.isFile() || !grill24.fishtastic.mcp.McpBridgeState.isRunning(), mc -> {
            check("mcpsession.endedByAgent", doneFile.isFile(), doneFile.isFile() ? "" : "timed out or the bridge stopped");
            mc.player.connection.sendCommand("fishtastic mcp stop");
            sessionFile.delete();
            doneFile.delete();
        });
    }

    /** The {@code mcpsession} build site: its centre floor block, clear of the player's spawn. */
    private static BlockPos buildSite() {
        return new BlockPos(origin.getX(), origin.getY(), origin.getZ() - 8);
    }

    /**
     * Opt-in: for each structure named by a {@code cosmetic=<name>} marker line, places it in a lone
     * SKYLIGHT tank (glass roof, so the high orbit sees in) and takes two frozen 8-frame alpha orbits
     * with cool-cam: {@code coolcam_capture/preview_<name>_low} (just above the floor, the angle
     * players see it from) and {@code preview_<name>_high} (looking down through the roof). Checks the
     * structure loaded from the datapack, that its footprint fits the grid at all four rotations, and,
     * informationally, whether {@code cosmetic_<name>} is registered as an item yet. A spanning
     * structure is placed across a box of SKYLIGHT tanks of its span instead, and the orbits are
     * framed to the box.
     */
    private static void queueCosmeticPreviewScene() {
        List<String> names = params("cosmetic");
        check("cosmeticpreview.names", !names.isEmpty(), "marker names " + names);
        // reactionOpen=<0..1>: every reaction held that far open (docs/fish-shelters.md §12.13), orbits suffixed _open.
        float open = Float.parseFloat(param("reactionOpen", "-1"));
        String suffix = open >= 0f ? "_open" : "";
        queue(1, mc -> {
            grill24.fishtastic.client.util.ClientCosmeticReactions.forcedOpenness = open;
            savedRenderDistance = mc.options.renderDistance().get();
            mc.options.renderDistance().set(3);
            mc.options.hideGui = true;
            server(mc, s -> clearVoid(s, origin.getX(), origin.getY(), origin.getZ() - 4));
            camera(mc, origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ(), 180f, 10f);
            mc.player.connection.sendCommand("cc capture ticks 0");
            mc.player.connection.sendCommand("cc capture fov 40");
            mc.player.connection.sendCommand("cc capture size 1280 720");
        });
        Vec3 center = new Vec3(origin.getX() + 0.5, origin.getY() + 0.4, origin.getZ() - 4 + 0.5);
        for (String name : names) {
            // The orbit's centre, radius and low/high heights: a lone tank's, unless the structure spans a box.
            double[] frame = {center.x, center.y, center.z, 2.1, 0.55, 1.9};
            queue(10, mc -> server(mc, s -> {
                BlockPos pos = new BlockPos(origin.getX(), origin.getY(), origin.getZ() - 4);
                run(s, "fill " + (pos.getX() - 3) + " " + pos.getY() + " " + (pos.getZ() - 3) + " " + (pos.getX() + 3) + " "
                        + (pos.getY() + 3) + " " + (pos.getZ() + 3) + " minecraft:air");
                ServerLevel level = s.overworld();
                ResourceKey<grill24.fishtastic.fishtank.CosmeticStructure> key = structure(name);
                var found = level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY).getOptional(key);
                check("cosmeticpreview." + name + ".loaded", found.isPresent(), found.map(st -> st.parts().size() + " parts, scale " + st.scale()
                        + ", footprint " + st.footprintCells().size() + " cell(s)").orElse("not in the datapack registry"));
                boolean item = BuiltInRegistries.ITEM.containsKey(Ids.of("fishtastic", "cosmetic_" + name));
                check("cosmeticpreview." + name + ".itemRegistered", item, item ? "" : "(expected before the item is wired up)");
                if (found.isEmpty()) return;
                if (found.get().span().isPresent()) {
                    var span = found.get().span().get();
                    BlockPos min = spanPreviewMin(pos, span);
                    placeSpanBox(s, min, span);
                    var box = grill24.fishtastic.fishtank.SpanStructures.rotated(span, Rotation.NONE);
                    grill24.fishtastic.fishtank.SpanStructures.place(level, new grill24.fishtastic.fishtank.SpanStructures.Fit(min, box, null),
                            new FishTankBlockEntity.PlacedStructureCosmetic(key, Rotation.NONE), found.get());
                    frame[0] = min.getX() + span.x() / 2.0;
                    frame[1] = min.getY() + 0.25 * span.y();
                    frame[2] = min.getZ() + span.z() / 2.0;
                    frame[3] = 1.15 * Math.max(span.x(), span.z()) + 1.6;
                    frame[4] = 0.3 + 0.3 * span.y();
                    frame[5] = 1.4 * span.y() + 1.0;
                    return;
                }
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
            for (String[] orbit : new String[][]{{"low", "0.55"}, {"high", "1.9"}}) {
                String capture = "preview_" + name + "_" + orbit[0] + suffix;
                boolean low = orbit[0].equals("low");
                queue(40, mc -> mc.player.connection.sendCommand(String.format(Locale.ROOT, "cc capture orbit %s 8 %.2f %.2f %.2f %.2f %.2f",
                        capture, frame[3], low ? frame[4] : frame[5], frame[0], frame[1], frame[2])));
                waitFor(10, 1200, mc -> captureFrame(capture, 7).isFile(),
                        mc -> check("cosmeticpreview." + capture, captureFrame(capture, 7).isFile(), captureFrame(capture, 0).getParent()));
            }
            queueCosmeticItemChecks(name);
        }
        queue(10, mc -> {
            grill24.fishtastic.client.util.ClientCosmeticReactions.forcedOpenness = -1f;
            mc.player.connection.sendCommand("cc capture fov off");
            mc.player.connection.sendCommand("cc capture size off");
            mc.options.renderDistance().set(savedRenderDistance);
            mc.options.hideGui = false;
        });
    }

    /** Where a spanning structure's preview box starts: centred on {@code pos} along x and z. */
    private static BlockPos spanPreviewMin(BlockPos pos, grill24.fishtastic.fishtank.CosmeticStructure.Span span) {
        return new BlockPos(pos.getX() - span.x() / 2, pos.getY(), pos.getZ() - span.z() / 2);
    }

    /** Clears round a span-sized box at {@code min} and fills it with SKYLIGHT tanks. */
    private static void placeSpanBox(net.minecraft.server.MinecraftServer s, BlockPos min, grill24.fishtastic.fishtank.CosmeticStructure.Span span) {
        run(s, "fill " + (min.getX() - 1) + " " + min.getY() + " " + (min.getZ() - 1) + " " + (min.getX() + span.x()) + " "
                + (min.getY() + span.y()) + " " + (min.getZ() + span.z()) + " minecraft:air");
        run(s, "fill " + min.getX() + " " + min.getY() + " " + min.getZ() + " " + (min.getX() + span.x() - 1) + " "
                + (min.getY() + span.y() - 1) + " " + (min.getZ() + span.z() - 1) + " fishtastic:fish_tank");
        for (BlockPos p : BlockPos.betweenClosed(min, min.offset(span.x() - 1, span.y() - 1, span.z() - 1))) {
            FishTankBlockEntity tank = tank(s.overworld(), p);
            if (tank != null) tank.setShape(FishTankShape.SKYLIGHT);
        }
    }

    /**
     * The item side of a cosmetic, checked in game: {@code cosmetic_<name>} targets the structure, has
     * a lang name, and has a loaded shop entry that sells it; then a creative player standing on a
     * SKYLIGHT tank right-clicks the item into it through the real client interaction path, the server
     * is checked for the placed structure. Shots, looking down through the roof: {@code <name>_held}
     * (item in hand and hotbar, before the click) and {@code <name>_placed} (after). Skipped while the item isn't registered yet.
     */
    private static void queueCosmeticItemChecks(String name) {
        Identifier itemId = Ids.of("fishtastic", "cosmetic_" + name);
        // A lone tank, or for a spanning structure the top tank at the corner of a box of its span,
        // well clear of the preview box; and the tanks, for the placed check and the clean-up.
        BlockPos[] at = {new BlockPos(origin.getX() + 3, origin.getY(), origin.getZ() - 4)};
        List<BlockPos> box = new java.util.ArrayList<>();
        boolean[] registered = {false};
        queue(5, mc -> {
            registered[0] = BuiltInRegistries.ITEM.containsKey(itemId);
            if (!registered[0]) return;
            var item = BuiltInRegistries.ITEM.getValue(itemId);
            boolean targets = item instanceof grill24.fishtastic.item.FishTankStructureCosmeticItem cosmetic
                    && cosmetic.getStructureId().equals(structure(name));
            check("cosmeticpreview." + name + ".itemTargetsStructure", targets, item.getClass().getSimpleName());
            String langKey = "item.fishtastic.cosmetic_" + name;
            boolean named = net.minecraft.locale.Language.getInstance().has(langKey);
            check("cosmeticpreview." + name + ".langName", named, named ? net.minecraft.locale.Language.getInstance().getOrDefault(langKey) : "missing " + langKey);
            server(mc, s -> {
                var entry = s.registryAccess().lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY)
                        .getOptional(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, itemId));
                boolean sells = entry.isPresent() && entry.get().cost() > 0
                        && entry.get().reward().stream().anyMatch(r -> r.itemId().equals(itemId));
                check("cosmeticpreview." + name + ".shopEntry", sells, entry.map(e -> "'" + e.displayName() + "' for " + e.cost()
                        + ", daily max " + e.dailyMaxPurchases() + ", weight " + e.weight()).orElse("no shop_entry/cosmetic_" + name + ".json loaded"));
                var structure = s.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                        .getOptional(structure(name)).orElse(null);
                box.clear();
                if (structure != null && structure.span().isPresent()) {
                    var span = structure.span().get();
                    BlockPos min = new BlockPos(origin.getX() + 6, origin.getY(), origin.getZ() - 4);
                    placeSpanBox(s, min, span);
                    for (BlockPos p : BlockPos.betweenClosed(min, min.offset(span.x() - 1, span.y() - 1, span.z() - 1))) box.add(p.immutable());
                    at[0] = min.offset(0, span.y() - 1, 0);
                } else {
                    BlockPos lone = at[0];
                    run(s, "setblock " + lone.getX() + " " + lone.getY() + " " + lone.getZ() + " minecraft:air");
                    run(s, "setblock " + lone.getX() + " " + lone.getY() + " " + lone.getZ() + " fishtastic:fish_tank");
                    FishTankBlockEntity tank = tank(s.overworld(), lone);
                    if (tank != null) tank.setShape(FishTankShape.SKYLIGHT);
                    box.add(lone);
                }
                BlockPos pos = at[0];
                run(s, "gamemode creative @a");
                run(s, "item replace entity @a weapon.mainhand with " + itemId);
                run(s, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 180 90", pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
            });
        });
        queue(20, mc -> {
            if (!registered[0]) return;
            mc.options.hideGui = false;
            mc.player.getInventory().setSelectedSlot(0);
            mc.gui.getChat().clearMessages(false);
        });
        queue(10, mc -> {
            if (!registered[0]) return;
            screenshot(mc, "cosmeticpreview", name + "_held");
        });
        queue(5, mc -> {
            if (!registered[0]) return;
            BlockPos pos = at[0];
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false));
        });
        queue(20, mc -> {
            if (!registered[0]) return;
            mc.gui.getChat().clearMessages(false);
            screenshot(mc, "cosmeticpreview", name + "_placed");
            server(mc, s -> {
                boolean placed = box.stream().map(p -> tank(s.overworld(), p)).anyMatch(tank -> tank != null
                        && tank.getStructureCosmetics().values().stream().anyMatch(c -> c.structureId().equals(structure(name))));
                check("cosmeticpreview." + name + ".placesFromItem", placed, placed ? "placed by right-click" : "no structure in the tank after useItemOn");
                run(s, "item replace entity @a weapon.mainhand with minecraft:air");
                for (BlockPos p : box) run(s, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " minecraft:air");
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

    private static long animatedStartTime;

    private static File captureFrame(String name, int frame) {
        return new File(Minecraft.getInstance().gameDirectory, String.format(Locale.ROOT, "coolcam_capture/%s/frame_%04d.png", name, frame));
    }

    /** Alpha sanity for a solved frame: empty corner, a solid subject, and partial alpha where glass and water are. */
    private static void matteStats(String label, File out) {
        check("matte." + label + ".written", out.isFile(), out.toString());
        if (!out.isFile()) return;
        try (InputStream in = new FileInputStream(out); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight(), empty = 0, solid = 0, partial = 0;
            for (int py = 0; py < h; py += 2) {
                for (int px = 0; px < w; px += 2) {
                    int a = image.getPixel(px, py) >>> 24;
                    if (a <= 2) empty++; else if (a >= 253) solid++; else partial++;
                }
            }
            int corner = image.getPixel(2, 2) >>> 24;
            check("matte." + label + ".cornerEmpty", corner <= 2, "corner alpha=" + corner);
            check("matte." + label + ".hasSolid", solid > 500, "solid=" + solid);
            check("matte." + label + ".hasPartial", partial > 200, "partial=" + partial + " (glass/water/edges)");
            check("matte." + label + ".mostlyEmpty", empty > (w / 2) * (h / 2) / 4, "empty=" + empty + " of " + (w / 2) * (h / 2));
        } catch (IOException e) {
            check("matte." + label + ".read", false, e.toString());
        }
    }

    private static boolean sameImage(File a, File b) {
        if (!a.isFile() || !b.isFile()) return false;
        try (InputStream ia = new FileInputStream(a); InputStream ib = new FileInputStream(b);
             NativeImage x = NativeImage.read(ia); NativeImage y = NativeImage.read(ib)) {
            if (x.getWidth() != y.getWidth() || x.getHeight() != y.getHeight()) return false;
            return java.util.Arrays.equals(x.getPixelsABGR(), y.getPixelsABGR());
        } catch (IOException e) {
            return false;
        }
    }

    /** Frame, sand and glass for the three material sets of the shapes scene. */
    private static final String[][] MATERIAL_SETS = {
            {"minecraft:oak_planks", "minecraft:sand", "fishtastic:blue_clear_stained_glass"},
            {"minecraft:stone_bricks", "minecraft:gravel", "minecraft:glass"},
            // Waxed copper is the blockstate-redirect case (docs/fish-tank-rendering.md).
            {"minecraft:waxed_copper_block", "minecraft:red_sand", "fishtastic:pink_borderless_stained_glass"},
    };

    /**
     * Every {@link FishTankShape} in each of three material sets (two rows of ten per set), an
     * L-shaped group (diagonal corner fragments), a vertical L (edge fragments), a live material
     * change (re-mesh), and tank items with different shapes and materials in the hotbar.
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
                ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", "fish_tank")));
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
     * Stress: an 8x8x8 group (the 512-tank cap) whose tanks cycle through 64 frame/glass
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
     * The builtin/entity items (26.1's custom item model types) in the hotbar, the inventory
     * screen, item frames, on the ground and in hand; and the Shape Gallery, whose cells render
     * tank items.
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
                player.getInventory().setSelectedSlot(0);
            }
            ServerLevel level = s.overworld();
            var frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(x - 2, y, z + 1, x + 4, y + 3, z + 3));
            for (int i = 0; i < frames.size() && i < 4; i++) frames.get(i).setItem(items.get(i + 1).copy(), false);
            for (int i = 0; i < 4; i++) {
                var entity = new ItemEntity(level, x - 1 + i + 0.5, y, z + 0.5, items.get(i).copy());
                entity.setNeverPickUp();
                entity.setUnlimitedLifetime();
                entity.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(entity);
            }
        }));
        queue(20, mc -> {
            mc.options.hideGui = false;
            mc.player.getInventory().setSelectedSlot(0);
            server(mc, s -> {
                run(s, "gamemode creative @a");
                run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 1.0, (double) origin.getY(), origin.getZ() + 3.0, 0f, 25f));
            });
        });
        queue(40, mc -> screenshot(mc, "items", "world_and_hotbar"));
        queue(1, mc -> mc.setScreen(new InventoryScreen(mc.player)));
        queue(30, mc -> screenshot(mc, "items", "inventory"));
        queue(1, mc -> {
            mc.setScreen(null);
            mc.player.getInventory().setSelectedSlot(1);
        });
        queue(20, mc -> screenshot(mc, "items", "held_structure"));
        queue(1, mc -> mc.player.getInventory().setSelectedSlot(5));
        queue(20, mc -> screenshot(mc, "items", "held_pile"));
        // The Shape Gallery: an empty-hand click on a placed assembly, as in play.
        queue(1, mc -> {
            grill24.fishtastic.client.FishtasticClientConfig.setShapeGalleryOpen(true);
            mc.player.getInventory().setSelectedSlot(8);
            mc.player.getInventory().setItem(8, ItemStack.EMPTY);
            server(mc, s -> {
                for (var player : s.getPlayerList().getPlayers()) player.getInventory().setItem(8, ItemStack.EMPTY);
            });
        });
        queue(5, mc -> {
            BlockPos assembly = new BlockPos(origin.getX() + 3, origin.getY(), origin.getZ() + 4);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(assembly), Direction.UP, assembly, false));
        });
        queue(40, mc -> {
            screenshot(mc, "items", "shape_gallery");
            check("items.assemblyOpen", mc.screen != null, "screen=" + mc.screen);
        });
        queue(1, mc -> mc.setScreen(null));
    }

    /**
     * The fishing line leaves the Fishtastic rod's hand, a held fish renders at its recorded size
     * in third person, and the leaderboard's podium puppet holds its catch in the fisherman pose.
     */
    private static void queueHeldScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            server(mc, s -> {
                run(s, "gamemode survival @a");
                run(s, "fill " + (origin.getX() - 3) + " " + (origin.getY() - 1) + " " + (origin.getZ() - 12) + " "
                        + (origin.getX() + 3) + " " + (origin.getY() - 1) + " " + (origin.getZ() - 6) + " minecraft:water");
                run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 0.5, (double) origin.getY(), origin.getZ() - 3.5, 180f, 20f));
                for (var player : s.getPlayerList().getPlayers()) {
                    player.getInventory().setItem(0, item("copper_fishing_rod"));
                    player.getInventory().setItem(1, fish("giant_manta_ray", 300f));
                    player.getInventory().setItem(2, fish("bluegill", 12f));
                    player.getInventory().setSelectedSlot(0);
                }
            });
        });
        queue(20, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        });
        queue(40, mc -> screenshot(mc, "held", "rod_cast_first_person"));
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        queue(20, mc -> screenshot(mc, "held", "rod_cast_third_person"));
        queue(1, mc -> {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); // reel in
            mc.player.getInventory().setSelectedSlot(1);
        });
        queue(20, mc -> screenshot(mc, "held", "large_fish_third_person"));
        queue(1, mc -> mc.player.getInventory().setSelectedSlot(2));
        queue(20, mc -> screenshot(mc, "held", "small_fish_third_person"));
        // The fisherman hang pose: the podium puppet's code path, forced onto the player by the
        // debug toggle (the same check), holding the large fish.
        queue(1, mc -> {
            grill24.fishtastic.client.util.FishermanPoseDebug.enabledInWorld = true;
            mc.player.getInventory().setSelectedSlot(1);
        });
        queue(20, mc -> screenshot(mc, "held", "fisherman_pose"));
        queue(1, mc -> {
            grill24.fishtastic.client.util.FishermanPoseDebug.enabledInWorld = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            // The 1.21.1 harness probed gelatin-ui's PlayerAvatarRenderer$Puppet class here, because
            // its fisherman-pose matcher keyed on that class name. 26.1.2 has a real Mannequin entity,
            // gelatin-ui's PlayerAvatarRenderer draws a ClientMannequin, and FishermanPoseDebug keys
            // on EntityType.MANNEQUIN instead — so the same question ("does the pose reach the podium
            // puppet, and only things like it?") becomes an entity-type probe. The armor stand is the
            // control: a matcher widened to everything would be wrong in the other direction.
            check("held.gelatinPuppetClass",
                    FishermanPoseDebug.shouldPose(EntityType.MANNEQUIN)
                            && !FishermanPoseDebug.shouldPose(EntityType.ARMOR_STAND),
                    "(FishermanPoseDebug.shouldPose matches MANNEQUIN, not ARMOR_STAND)");
        });
    }

    private static final String[] QUALITIES = {"uncommon", "rare", "epic", "legendary"};

    private static ItemStack qualityFish(String quality) {
        ItemStack stack = fish("parrotfish", 30f);
        FishtasticItemData.set(stack, FishtasticDataComponents.FISH_QUALITY,
                new FishQuality(FishQuality.Quality.valueOf(quality.toUpperCase(Locale.ROOT))));
        return stack;
    }

    /**
     * Quality glint on a held item; static and animated (legendary) GUI outlines in the hotbar and
     * a container; world outlines on dropped items and in item frames; plus the encyclopedia's
     * never-caught silhouettes. The common-quality fish in hotbar slot 5 is the no-outline control.
     * Two shots 10 ticks apart: only the legendary slot should differ.
     *
     * <p>26.1.2 has no separately-readable atlas target to dump alongside them — the outline and
     * mask atlases are created without {@code USAGE_COPY_SRC}, so the main target is dumped instead
     * and the atlases are asserted to be registered (see the class javadoc).
     */
    private static void queueOutlineScene() {
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() + 14;
            run(s, "fill " + (x - 5) + " " + y + " " + (z - 3) + " " + (x + 5) + " " + (y + 3) + " " + (z + 4) + " minecraft:air");
            run(s, "fill " + (x - 4) + " " + y + " " + (z + 4) + " " + (x + 4) + " " + (y + 2) + " " + (z + 4) + " minecraft:stone");
            ServerLevel level = s.overworld();
            for (int i = 0; i < QUALITIES.length; i++) {
                run(s, "summon item_frame " + (x - 2 + i) + " " + (y + 1) + " " + (z + 3) + " {Facing:2b,Fixed:1b,Invulnerable:1b}");
                var entity = new ItemEntity(level, x - 1.05 + 0.7 * i, y, z + 0.9, qualityFish(QUALITIES[i]));
                entity.setNeverPickUp();
                entity.setUnlimitedLifetime();
                entity.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(entity);
            }
            for (var player : s.getPlayerList().getPlayers()) {
                for (int i = 0; i < 9; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
                for (int i = 0; i < QUALITIES.length; i++) player.getInventory().setItem(i, qualityFish(QUALITIES[i]));
                player.getInventory().setItem(4, fish("parrotfish", 30f));
                player.getInventory().setSelectedSlot(3);
            }
        }));
        queue(5, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ() + 14;
            var frames = s.overworld().getEntitiesOfClass(ItemFrame.class, new AABB(x - 3, y, z + 2, x + 3, y + 3, z + 4));
            frames.sort(Comparator.comparingDouble(net.minecraft.world.entity.Entity::getX));
            for (int i = 0; i < frames.size() && i < QUALITIES.length; i++) frames.get(i).setItem(qualityFish(QUALITIES[i]), false);
        }));
        queue(5, mc -> {
            mc.options.hideGui = false;
            mc.player.getInventory().setSelectedSlot(3);
            server(mc, s -> {
                run(s, "gamemode creative @a");
                run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 10.5, 0f, 5f));
            });
        });
        queue(60, mc -> {
            screenshot(mc, "outline", "frames_a");
            dump(mc, mc.getMainRenderTarget(), "outline", "frame_a");
            check("outline.atlasRegistered", atlasTextureRegistered(FishtasticItemOutlineAtlas.TEXTURE_ID)
                            && atlasTextureRegistered(FishtasticItemOutlineAtlas.MASK_TEXTURE_ID),
                    "outline=" + FishtasticItemOutlineAtlas.TEXTURE_ID
                            + " mask=" + FishtasticItemOutlineAtlas.MASK_TEXTURE_ID);
        });
        queue(10, mc -> {
            screenshot(mc, "outline", "frames_b");
            dump(mc, mc.getMainRenderTarget(), "outline", "frame_b");
            server(mc, s -> run(s, "execute as @a at @s run tp @s ~ ~ ~ 0 60"));
        });
        queue(20, mc -> screenshot(mc, "outline", "ground"));
        queue(1, mc -> server(mc, s -> run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                origin.getX() + 0.5, origin.getY() - 0.4, origin.getZ() + 15.4, 0f, 0f))));
        queue(20, mc -> screenshot(mc, "outline", "frames_close"));
        queue(1, mc -> mc.setScreen(new InventoryScreen(mc.player)));
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
     * Improved transparency (26.1.2's replacement for {@code GraphicsStatus.FABULOUS}): the
     * item-entity target and the translucency chain. Runs the outline and tank scenes' subjects
     * under it — world outlines, the tank's glass and water fill, and glass blocks. Expects the
     * outline and tank scenes to have run first; restores the option after.
     */
    private static void queueFabulousScene() {
        queue(1, mc -> {
            savedImprovedTransparency = mc.options.improvedTransparency().get();
            mc.options.improvedTransparency().set(true);
            // Read straight back: if the platform refuses the option (a warn-listed GPU routes the
            // toggle to a warning instead of switching the pipeline) the value is already false
            // here, which tells the check below apart from a late revert.
            improvedTransparencyAccepted = mc.options.improvedTransparency().get();
            mc.levelRenderer.allChanged();
            mc.options.hideGui = false;
            server(mc, s -> run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                    origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 12.5, 0f, 35f)));
        });
        queue(60, mc -> {
            boolean option = mc.options.improvedTransparency().get();
            boolean engaged = Minecraft.useShaderTransparency();
            if (improvedTransparencyAccepted) {
                check("fabulous.active", engaged && option,
                        "improvedTransparency=" + option + " engaged=" + engaged);
            } else {
                // The platform refused the toggle outright (this dev GPU is on the fabulous
                // warnlist), so the transparency path is never entered on this machine however the
                // option is poked. What is still worth asserting is that the two agree — the
                // pipeline must not engage a mode the options say is off.
                check("fabulous.active", !option && !engaged,
                        "improvedTransparency refused by the platform; pipeline stays regular"
                                + " (option=" + option + " engaged=" + engaged + ")");
            }
            screenshot(mc, "fabulous", "outlines");
        });
        queue(1, mc -> server(mc, s -> run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                origin.getX() + 0.5, origin.getY() - 0.4, origin.getZ() + 15.4, 0f, 0f))));
        queue(20, mc -> screenshot(mc, "fabulous", "frames_close"));
        queue(1, mc -> camera(mc, origin.getX() + 1.0, origin.getY() + 2.2, origin.getZ() - 0.6, 180f, 12f));
        queue(40, mc -> screenshot(mc, "fabulous", "tanks"));
        queue(1, mc -> server(mc, s -> run(s, "gamemode creative @a")));
        queue(1, mc -> {
            mc.options.improvedTransparency().set(savedImprovedTransparency);
            mc.levelRenderer.allChanged();
        });
        queue(20, mc -> check("fabulous.restored",
                mc.options.improvedTransparency().get() == savedImprovedTransparency, ""));
    }

    /**
     * GUI outlines at GUI scales 1, 2 and 4: the hotbar from the outline scene, which must have run
     * first. Restores the scale and the window size after.
     */
    private static void queueGuiScaleScene() {
        queue(1, mc -> {
            savedGuiScale = mc.options.guiScale().get();
            savedWindowWidth = mc.getWindow().getWidth();
            savedWindowHeight = mc.getWindow().getHeight();
            // Scale 4 needs a window at least 1280x960 (Window.calculateScale caps the scale at
            // width/320 and height/240); the dev runs open smaller ones.
            mc.getWindow().setWindowed(1920, 1080);
        });
        for (int scale : new int[]{1, 2, 4}) {
            queue(1, mc -> {
                mc.options.hideGui = false;
                mc.options.guiScale().set(scale);
                mc.resizeGui();
            });
            queue(20, mc -> {
                check("guiscale." + scale, mc.getWindow().getGuiScale() == scale, "actual=" + mc.getWindow().getGuiScale());
                screenshot(mc, "guiscale", "scale" + scale);
            });
        }
        queue(1, mc -> {
            mc.options.guiScale().set(savedGuiScale);
            mc.getWindow().setWindowed(savedWindowWidth, savedWindowHeight);
            mc.resizeGui();
        });
    }

    private static boolean savedImprovedTransparency;
    /** Whether the platform took the option at all — see {@code queueFabulousScene}. */
    private static boolean improvedTransparencyAccepted;
    private static int savedGuiScale;
    private static int savedRenderDistance;
    private static int savedWindowWidth;
    private static int savedWindowHeight;

    /** Pile of Fish (flat), a pile-block stack, structure cosmetics, the treasure chest and a tank. */
    private static List<ItemStack> itemsSceneStacks() {
        ItemStack legendary = fish("lionfish", 30f);
        FishtasticItemData.set(legendary, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.LEGENDARY));
        List<ItemStack> contents = List.of(fish("bluegill", 25f), legendary, fish("discus", 28f), fish("betta", 20f));
        ItemStack pile = new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", "pile_of_fish")));
        // 26.1.2's BundleContents holds ItemStackTemplates, not ItemStacks, and the pile-block icon
        // is selected with the ITEM_MODEL component rather than a CustomModelData marker.
        FishtasticItemData.setBundleContents(pile, new BundleContents(
                contents.stream().map(ItemStackTemplate::fromNonEmptyStack).toList()));
        ItemStack pileBlock = pile.copy();
        pileBlock.set(DataComponents.ITEM_MODEL, grill24.fishtastic.client.util.FishPileIcons.PILE_BLOCK_ITEM_MODEL);
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
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", id)));
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Ids.parse(id));
    }

    private static FishTankMaterials materials(String[] set) {
        return new FishTankMaterials(block(set[0]), block(set[1]), block(set[2]));
    }

    /**
     * A quest banner updated in place must show the new counter and the Complete! badge; and
     * opening a gelatin screen must not make JEI log "Received invalid gui properties" (grep
     * latest.log for it after the run).
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
     * A quest banner and a vanilla toast share the top-right corner, photographed with and without
     * a screen open. The 1.21.1 harness also counted HUD-layer frames here ({@code toastPassFrames}
     * / {@code hudPassCalls}) to prove the banners are drawn after the toasts. 26.1.2 has no such
     * hook — its toasts cover its HUD natively, which is why the 1.21.1 side needed one — so those
     * two checks have no subject here and only the shots remain.
     *
     * <p>The notification is enqueued on the client thread rather than through
     * {@code /fishtastic testquestnotify}: that command reaches this client-side manager from the
     * <em>server</em> thread (fine in single-player only), which races its {@code tick} and loses
     * the event often enough to make the screenshot unreliable. Same call, same event.
     */
    /**
     * Fish companions (docs/fish-companions.md §12, Phases 0 and 2): bond through the real packet and
     * check the synced attachment reached the client; the companion following in first and third
     * person; the demo minigame — shots part-way through the swim to the dock, either side of the
     * world-to-HUD handoff, docked, both passes side by side, through a camera turn and in third
     * person; a dev phantom standing in for a second player's companion; then unequip through the
     * real packet and check the exact fish came back.
     */
    private static void queueCompanionScene() {
        ItemStack bluegill = fish("bluegill", 25f);
        // The bond gate is real since phase 4: complete the species' entry first, or the bond
        // packet comes back LOCKED and every follow/dock check below has no companion to watch.
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion catchcount @p fishtastic:bluegill 200")));
        queue(1, mc -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, s -> {
                run(s, "gamemode creative @a");
                run(s, "time set 6000");
                run(s, "clear @a");
                run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 0.5, 180f, 0f));
                for (var player : s.getPlayerList().getPlayers()) {
                    grill24.fishtastic.companion.Companions.unequip(player);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(3, bluegill.copy());
                    // Keep the client's view of the inventory in step with the server's seeding
                    // (the harness does not resync it from behind custom menus — see
                    // mirrorInventoryToClient), so this scene owns its slot state no matter which
                    // scene ran before it.
                    mirrorInventoryToClient(mc, player);
                }
            });
        });
        // The real bond path: the client describes the fish, the server finds it in the inventory.
        queue(10, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new grill24.fishtastic.network.BondCompanionPacket(bluegill.copy()))));
        // "The fish left the inventory" is asserted on the SERVER, the source of truth — the client
        // view's container resync is not reliable from behind the harness's custom menus.
        final boolean[] bondTookTheFish = new boolean[1];
        queue(5, mc -> server(mc, s -> {
            for (var player : s.getPlayerList().getPlayers()) {
                bondTookTheFish[0] = player.getInventory().getItem(3).isEmpty();
            }
        }));
        queue(1, mc -> {
            ItemStack synced = grill24.fishtastic.companion.CompanionStorage.get(mc.player);
            check("companion.bondSyncedToClient", ItemStack.matches(synced, bluegill), "synced=" + synced);
            check("companion.bondTookTheFish", bondTookTheFish[0], "server-slot3-empty=" + bondTookTheFish[0]);
            check("companion.tracked", grill24.fishtastic.client.companion.ClientCompanions.count() == 1,
                    "tracked=" + grill24.fishtastic.client.companion.ClientCompanions.count());
        });
        for (int i = 0; i < 4; i++) {
            int shot = i;
            queue(15, mc -> {
                companionLog("follow " + shot);
                screenshot(mc, "companion", "fp_follow_" + shot);
            });
        }
        queue(1, mc -> mc.player.setXRot(-25f));
        queue(15, mc -> screenshot(mc, "companion", "fp_follow_lookup"));
        queue(1, mc -> {
            mc.player.setXRot(0f);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        queue(20, mc -> screenshot(mc, "companion", "tp_follow"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // Block 1: the swim to the dock and the handoff.
        queue(20, RenderSelfTest::openDemoMinigame);
        queue(8, mc -> {
            companionLog("approach");
            screenshot(mc, "companion", "dock_approach");
        });
        queue(10, mc -> screenshot(mc, "companion", "dock_before_handoff"));
        queue(4, mc -> screenshot(mc, "companion", "dock_after_handoff"));
        queue(10, mc -> {
            check("companion.docked", grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked(), "");
            screenshot(mc, "companion", "docked");
        });

        // Block 2: the same fish drawn by both passes in one frame (HUD copy shifted left).
        queue(1, mc -> {
            ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
            grill24.fishtastic.client.companion.ClientCompanions.pipCompare = true;
        });
        queue(25, RenderSelfTest::openDemoMinigame);
        for (int i = 0; i < 3; i++) {
            int shot = i;
            queue(i == 0 ? 25 : 6, mc -> screenshot(mc, "companion", "compare_" + shot));
        }
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.pipCompare = false);

        // Block 3: docked fish stays locked to the screen through a camera turn, and in third person.
        queue(1, mc -> ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation());
        queue(25, RenderSelfTest::openDemoMinigame);
        queue(25, mc -> {
            companionLog("before turn");
            screenshot(mc, "companion", "docked_before_turn");
        });
        queue(1, mc -> {
            mc.player.setYRot(mc.player.getYRot() + 40f);
            mc.player.yRotO = mc.player.getYRot();
        });
        queue(3, mc -> {
            companionLog("after turn");
            screenshot(mc, "companion", "docked_after_turn");
        });
        queue(10, mc -> {
            companionLog("after turn +10");
            screenshot(mc, "companion", "docked_after_turn_10");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(5, mc -> {
            companionLog("third person");
            screenshot(mc, "companion", "docked_third_person");
        });
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
        });
        queue(10, mc -> screenshot(mc, "companion", "undock_return"));

        // A second companion: no second player exists headlessly, so a dev phantom parked three
        // blocks ahead stands in for someone else's, drawn by the same manager pass.
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.addPhantom(
                fish("giant_manta_ray", 300f), mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3.5))));
        queue(25, mc -> {
            check("companion.secondCompanionTracked", grill24.fishtastic.client.companion.ClientCompanions.count() == 2,
                    "tracked=" + grill24.fishtastic.client.companion.ClientCompanions.count());
            screenshot(mc, "companion", "two_companions");
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms());

        // The real unequip path: the exact fish comes back and the companion is gone everywhere.
        queue(1, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                grill24.fishtastic.network.UnequipCompanionPacket.INSTANCE)));
        queue(10, mc -> {
            check("companion.unequipSyncedToClient", grill24.fishtastic.companion.CompanionStorage.get(mc.player).isEmpty(),
                    "synced=" + grill24.fishtastic.companion.CompanionStorage.get(mc.player));
            check("companion.untracked", grill24.fishtastic.client.companion.ClientCompanions.count() == 0,
                    "tracked=" + grill24.fishtastic.client.companion.ClientCompanions.count());
        });
        // The returned stack is a separate inventory sync from the attachment sync, and in the
        // harness the client's inventory view can lag a mod-screen session — so assert on the
        // SERVER's inventory, the source of truth, a few ticks later.
        final boolean[] returned = new boolean[1];
        queue(5, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                    if (ItemStack.matches(stack, bluegill)) returned[0] = true;
                }
            }
        }));
        queue(1, mc -> {
            check("companion.unequipReturnedTheFish", returned[0], "server-returned=" + returned[0]);
            screenshot(mc, "companion", "unequipped");
        });
    }

    private static void openDemoMinigame(Minecraft mc) {
        ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer)
                .fishtastic$displayItemActivation(new grill24.fishtastic.util.FishingMinigameAnimation());
    }

    private static void companionLog(String label) {
        Fishtastic.LOGGER.info("[selftest] companion {}: {}", label,
                grill24.fishtastic.client.companion.ClientCompanions.debugState());
    }

    // ── Phase 3: display modes, peeks and the companion settings ───────────────

    /** The self-test scenes' shorthand for the local player's companion. */
    private static grill24.fishtastic.client.companion.ClientCompanion companion() {
        return grill24.fishtastic.client.companion.ClientCompanions.local();
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

    /**
     * Equips a companion of the given species through the real bond path: the fish goes into the
     * inventory, the client sends the description packet, the server finds and takes it.
     *
     * <p>Seeds the species' catch count past every threshold first — the bond gate is real since
     * Phase 4, and a scene that bonds several species would otherwise come back LOCKED for every
     * species it did not seed itself (the parade found this the first time it ran after the gate
     * existed). Self-contained per bond, so a scene also works alone on a fresh world.
     */
    private static void bondFish(String species, float sizeCm) {
        ItemStack fish = fish(species, sizeCm);
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion catchcount @p fishtastic:"
                + species + " 200")));
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.unequip(player);
                player.getInventory().clearContent();
                player.getInventory().setItem(3, fish.copy());
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(8, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new grill24.fishtastic.network.BondCompanionPacket(fish.copy()))));
    }

    /** Dev diagnostics: the moving parts inside a §19 turn window, one line per sampled tick. */
    private static void turnWindowLog(Minecraft mc, String label) {
        var c = companion();
        Fishtastic.LOGGER.info("[selftest] polish.window {}: trick={} peeking={} basis={} yRot={}",
                label,
                c != null && c.devTrickActive(), c != null && c.isPeeking(),
                grill24.fishtastic.client.companion.ClientCompanions.devLocalCameraLockedFrame(),
                String.format(Locale.ROOT, "%.1f", mc.player.getYRot()));
    }

    /** Puts an item in the main hand, or clears it with {@code air}. */
    private static void holdItem(String itemId) {
        queue(1, mc -> server(mc, s -> {
            run(s, "item replace entity @a hotbar.0 with " + itemId);
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                mirrorInventoryToClient(mc, player);
            }
        }));
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

    /**
     * A minimal iteration scene for the first-person hand pass (§3.1 "hop onto your hand"): bond,
     * look down, photograph the hand copy, look back up. Kept separate from the parade so a hand-pass
     * change can be checked in under a minute, and because the hand pass draws under whatever
     * shaderpack the dev client has loaded, which is worth an isolated screen.
     */
    private static void queueCompanionHandScene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        queue(40, mc -> {
            mc.player.setXRot(80f);
            companionLog("hand scene looking down");
        });
        queue(25, mc -> {
            companionLog("hand scene settled");
            screenshot(mc, "companionhand", "looking_down");
        });
        queue(20, mc -> screenshot(mc, "companionhand", "looking_down_2"));
        queue(1, mc -> mc.player.setXRot(0f));
        queue(25, mc -> {
            companionLog("hand scene looking up");
            screenshot(mc, "companionhand", "looking_up");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionhand", "third_person"));
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, s -> {
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    grill24.fishtastic.companion.Companions.unequip(player);
                    player.getInventory().clearContent();
                }
            });
        });
        queue(10, mc -> companionLog("hand scene end"));
    }

    /**
     * Phase 3: the companion parade (docs/fish-companions.md §3, §3.1). Every display class is
     * bonded in turn and checked for its own anchor behaviour — orbiting, high arcs, the shoulder
     * spring, the perch, the poke-out-of-the-back — then the trio, the held-item reactions, the
     * look-down hand hop, and water.
     */
    private static void queueCompanionParadeScene() {        queue(1, RenderSelfTest::paradeSetup);

        // ── FREE_SWIM: orbits the shoulders. ──
        bondFish("bluegill", 25f);
        queue(45, mc -> {
            var c = companion();
            check("parade.freeSwim.class", c != null && c.displayClassName().equals("free_swim"),
                    "class=" + (c == null ? "none" : c.displayClassName()));
            double d = c == null ? -1 : c.horizontalDistanceToOwner();
            check("parade.freeSwim.orbit", d > 0.4 && d < 3.0, "horizontal=" + d);
            companionLog("free swim");
            screenshot(mc, "companionparade", "free_swim_fp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionparade", "free_swim_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // ── GLIDE: wide slow arcs overhead. ──
        bondFish("giant_manta_ray", 60f);
        queue(45, mc -> {
            var c = companion();
            check("parade.glide.class", c != null && c.displayClassName().equals("glide"),
                    "class=" + (c == null ? "none" : c.displayClassName()));
            Double h = c == null ? null : c.heightAboveOwnerEye();
            check("parade.glide.overhead", h != null && h > 0.6, "aboveEye=" + h);
            companionLog("glide");
            screenshot(mc, "companionparade", "glide_fp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> {
            screenshot(mc, "companionparade", "glide_tp");
            companionLog("glide tp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // ── DRIFT: behind one shoulder on a loose spring. ──
        bondFish("portuguese_man_o_war", 30f);
        queue(45, mc -> {
            var c = companion();
            check("parade.drift.class", c != null && c.displayClassName().equals("drift"),
                    "class=" + (c == null ? "none" : c.displayClassName()));
            double b = c == null ? -99 : c.behindness();
            check("parade.drift.behind", b > 0.0, "behind=" + b);
            companionLog("drift");
            screenshot(mc, "companionparade", "drift_fp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionparade", "drift_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // The drifter glows at night (§3): a night-time peek puts it in frame to check by eye.
        queue(1, mc -> server(mc, s -> run(s, "time set 18000")));
        queue(20, mc -> {
            companionLog("drift at night");
            grill24.fishtastic.client.companion.ClientCompanions.devStartPeek();
        });
        queue(25, mc -> {
            companionLog("drift at night, peeked");
            screenshot(mc, "companionparade", "drift_night");
        });
        queue(1, mc -> server(mc, s -> run(s, "time set 6000")));

        // ── BENTHIC: perched on the body; hops off to show itself. ──
        bondFish("starfish", 30f);
        queue(45, mc -> {
            var c = companion();
            check("parade.benthic.class", c != null && c.displayClassName().equals("benthic"),
                    "class=" + (c == null ? "none" : c.displayClassName()));
            check("parade.benthic.perched", c != null && c.isPerchedNow(), "perched=" + (c != null && c.isPerchedNow()));
            companionLog("benthic perched");
            screenshot(mc, "companionparade", "benthic_perched_fp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionparade", "benthic_perched_tp"));
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.devStartPeek();
        });
        queue(20, mc -> {
            var c = companion();
            check("parade.benthic.peekHop", c != null && c.isPeeking() && !c.isPerchedNow(),
                    "peeking=" + (c != null && c.isPeeking()) + " perched=" + (c != null && c.isPerchedNow()));
            companionLog("benthic hop");
            screenshot(mc, "companionparade", "benthic_hop_fp");
        });
        queue(120, mc -> {
            var c = companion();
            check("parade.benthic.backOnPerch", c != null && c.isPerchedNow() && !c.isPeeking(),
                    "perched=" + (c != null && c.isPerchedNow()));
        });

        // ── ANCHORED: pokes out of the back; ducks in near hostiles. ──
        bondFish("garden_eel", 30f);
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(45, mc -> {
            var c = companion();
            check("parade.anchored.class", c != null && c.displayClassName().equals("anchored"),
                    "class=" + (c == null ? "none" : c.displayClassName()));
            check("parade.anchored.perched", c != null && c.isPerchedNow(), "perched=" + (c != null && c.isPerchedNow()));
            companionLog("anchored");
            screenshot(mc, "companionparade", "anchored_perched_tp");
        });
        queue(1, mc -> server(mc, s -> {
            // The flat test world is Peaceful, which refuses to summon monsters.
            run(s, "difficulty easy");
            run(s, "summon minecraft:zombie " + (origin.getX() + 2) + " " + origin.getY() + " " + origin.getZ());
        }));
        queue(25, mc -> {
            var c = companion();
            float duck = c == null ? -1 : c.duckAmount();
            check("parade.anchored.ducks", duck > 0.5f, "duck=" + duck);
            companionLog("anchored ducking");
            screenshot(mc, "companionparade", "anchored_duck");
        });
        queue(1, mc -> server(mc, s -> {
            run(s, "kill @e[type=minecraft:zombie]");
            run(s, "difficulty peaceful");
        }));
        queue(25, mc -> {
            var c = companion();
            float duck = c == null ? -1 : c.duckAmount();
            check("parade.anchored.emerges", duck < 0.5f, "duck=" + duck);
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // ── Trio: a small free swimmer comes as three. ──
        bondFish("bluegill", 15f);
        queue(45, mc -> {
            var c = companion();
            check("parade.trio.class", c != null && c.displayClassName().equals("trio"),
                    "class=" + (c == null ? "none" : c.displayClassName()));
            check("parade.trio.count", c != null && c.fishCount() == 3, "fish=" + (c == null ? -1 : c.fishCount()));
            companionLog("trio");
            screenshot(mc, "companionparade", "trio_fp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionparade", "trio_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // ── Held-item reactions (§3.1 table). ──
        bondFish("bluegill", 25f);
        queue(40, mc -> check("parade.reaction.none", companion() != null && companion().reactionName().isEmpty(),
                "reaction=" + (companion() == null ? "none" : companion().reactionName())));
        holdItem("minecraft:fishing_rod");
        queue(30, mc -> {
            check("parade.reaction.rod", companion() != null && companion().reactionName().equals("rod"),
                    "reaction=" + (companion() == null ? "none" : companion().reactionName()));
            companionLog("rod reaction");
            screenshot(mc, "companionparade", "reaction_rod");
        });
        holdItem("fishtastic:freshwater_bait");
        queue(30, mc -> {
            check("parade.reaction.bait", companion() != null && companion().reactionName().equals("bait"),
                    "reaction=" + (companion() == null ? "none" : companion().reactionName()));
            screenshot(mc, "companionparade", "reaction_bait");
        });
        holdItem("fishtastic:bluegill");
        queue(30, mc -> check("parade.reaction.sameSpecies",
                companion() != null && companion().reactionName().equals("same_species"),
                "reaction=" + (companion() == null ? "none" : companion().reactionName())));
        holdItem("fishtastic:northern_pike");
        queue(30, mc -> {
            check("parade.reaction.predator",
                    companion() != null && companion().reactionName().equals("predator"),
                    "reaction=" + (companion() == null ? "none" : companion().reactionName()));
            screenshot(mc, "companionparade", "reaction_predator");
        });
        holdItem("minecraft:bread");
        queue(30, mc -> check("parade.reaction.food",
                companion() != null && companion().reactionName().equals("food"),
                "reaction=" + (companion() == null ? "none" : companion().reactionName())));
        holdItem("minecraft:air");
        queue(30, mc -> check("parade.reaction.clears",
                companion() != null && companion().reactionName().isEmpty(),
                "reaction=" + (companion() == null ? "none" : companion().reactionName())));

        // ── Look down: the companion hops into the first-person hand pass. ──
        int handBefore = -1;
        queue(1, mc -> mc.player.setXRot(80f));
        queue(25, mc -> {
            check("parade.handHop.active", companion() != null && companion().handHopActive(),
                    "handHop=" + (companion() != null && companion().handHopActive()));
            check("parade.handHop.drew", grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame >= 1,
                    "drawn=" + grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame);
            check("parade.handHop.visible", companion() != null && companion().visibleToOwner(),
                    "visible=" + (companion() != null && companion().visibleToOwner()));
            companionLog("looking down");
            screenshot(mc, "companionparade", "hand_hop");
        });
        queue(1, mc -> mc.player.setXRot(0f));
        queue(25, mc -> check("parade.handHop.released",
                companion() != null && !companion().handHopActive(),
                "handHop=" + (companion() != null && companion().handHopActive())));

        // ── Water: every class drops its air anchor and swims beside the player. ──
        queue(1, mc -> {
            mc.player.setXRot(0f);
            server(mc, s -> {
                int x = origin.getX(), y = origin.getY(), z = origin.getZ();
                run(s, "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 3) + " " + (y + 5) + " " + (z + 3) + " minecraft:glass");
                run(s, "fill " + (x - 2) + " " + y + " " + (z - 2) + " " + (x + 2) + " " + (y + 4) + " " + (z + 2) + " minecraft:water");
                run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                        x + 0.5, (double) y, z + 0.5, 180f, 0f));
            });
        });
        queue(20, mc -> {
            var c = companion();
            check("parade.water.swims", c != null && c.inWaterMode(),
                    "waterMode=" + (c != null && c.inWaterMode()));
            companionLog("in water");
            screenshot(mc, "companionparade", "water_swim");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> {
            companionLog("in water, third person");
            screenshot(mc, "companionparade", "water_swim_tp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        bondFish("starfish", 30f);
        queue(40, mc -> {
            var c = companion();
            check("parade.water.benthicSwims", c != null && c.inWaterMode() && !c.isPerchedNow(),
                    "waterMode=" + (c != null && c.inWaterMode()) + " perched=" + (c != null && c.isPerchedNow()));
            companionLog("benthic in water");
            screenshot(mc, "companionparade", "water_benthic");
        });

        // Cleanup: drain the pool, drop the companion, hands empty.
        queue(1, mc -> server(mc, s -> {
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            run(s, "fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x + 3) + " " + (y + 5) + " " + (z + 3) + " minecraft:air");
            run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                    x + 0.5, (double) y, z + 0.5, 180f, 0f));
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.unequip(player);
                player.getInventory().clearContent();
            }
        }));
        queue(10, mc -> companionLog("parade end"));
    }

    /**
     * Phase 3 acceptance (§3.1, R2.2): in first person the companion is visible to its owner at
     * regular intervals, and the three companion settings gate the renderer. The visibility window
     * runs with the camera looking straight ahead for 75 s and counts first-person ticks where the
     * companion was on screen.
     */
    private static void queueCompanionPeekScene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        queue(40, mc -> {
            mc.player.setXRot(0f);
            grill24.fishtastic.client.companion.ClientCompanions.visibleTicks = 0;
            grill24.fishtastic.client.companion.ClientCompanions.countedTicks = 0;
            companionLog("peek window start");
        });
        queue(1500, mc -> {
            int seen = grill24.fishtastic.client.companion.ClientCompanions.visibleTicks;
            int total = grill24.fishtastic.client.companion.ClientCompanions.countedTicks;
            float fraction = total == 0 ? 0f : seen / (float) total;
            int peeks = companion() == null ? 0 : companion().peekStarts();
            Fishtastic.LOGGER.info("[selftest] companion.visibility seen={}/{} ({}%), peeks={}",
                    seen, total, String.format(Locale.ROOT, "%.1f", fraction * 100f), peeks);
            check("peek.windowCounted", total > 1400, "counted=" + total);
            check("peek.visibleFraction", fraction >= 0.05f, "fraction=" + fraction);
            companionLog("peek window end");
            screenshot(mc, "companionpeek", "window_end");
        });

        // hideOtherCompanions: a phantom stands in for a second player's companion.
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.addPhantom(
                fish("giant_manta_ray", 300f), mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3.5))));
        queue(20, mc -> {
            int drawn = grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame;
            check("peek.bothDrawn", drawn >= 2, "drawn=" + drawn);
            screenshot(mc, "companionpeek", "two_visible");
        });
        queue(1, mc -> FishtasticClientConfig.setTestOverride(
                FishtasticClientConfig.HIDE_OTHER_COMPANIONS_KEY, true));
        queue(20, mc -> {
            int drawn = grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame;
            check("peek.othersHidden", drawn == 1, "drawn=" + drawn);
            screenshot(mc, "companionpeek", "others_hidden");
        });
        queue(1, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.HIDE_OTHER_COMPANIONS_KEY, false);
            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
        });

        // hideOwnCompanionFirstPerson: hidden in first person, drawn in third, never while docked.
        queue(1, mc -> FishtasticClientConfig.setTestOverride(
                FishtasticClientConfig.HIDE_OWN_COMPANION_FIRST_PERSON_KEY, true));
        queue(20, mc -> {
            int drawn = grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame;
            check("peek.ownHiddenFp", drawn == 0, "drawn=" + drawn);
            screenshot(mc, "companionpeek", "own_hidden_fp");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> {
            int drawn = grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame;
            check("peek.ownShownTp", drawn >= 1, "drawn=" + drawn);
            screenshot(mc, "companionpeek", "own_shown_tp");
        });
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.HIDE_OWN_COMPANION_FIRST_PERSON_KEY, false);
        });

        // companionWaitsAtBobber: on, the fish never docks; off (the default), it does.
        queue(1, mc -> FishtasticClientConfig.setTestOverride(
                FishtasticClientConfig.COMPANION_WAITS_AT_BOBBER_KEY, true));
        queue(20, RenderSelfTest::openDemoMinigame);
        queue(40, mc -> {
            check("peek.waitsNoDock", !grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked(),
                    "docked=" + grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked());
            screenshot(mc, "companionpeek", "waits_following");
            ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
        });
        queue(1, mc -> FishtasticClientConfig.setTestOverride(
                FishtasticClientConfig.COMPANION_WAITS_AT_BOBBER_KEY, false));
        queue(20, RenderSelfTest::openDemoMinigame);
        queue(40, mc -> {
            check("peek.defaultDocks", grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked(),
                    "docked=" + grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked());
            screenshot(mc, "companionpeek", "docked");
            ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
        });

        // Cleanup: never leave test overrides or a companion behind for the next scene.
        queue(1, mc -> {
            FishtasticClientConfig.clearTestOverrides();
            server(mc, s -> {
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    grill24.fishtastic.companion.Companions.unequip(player);
                    player.getInventory().clearContent();
                }
                run(s, "gamemode creative @a");
            });
        });
        queue(10, mc -> companionLog("peek scene end"));
    }

    /**
     * Phase 4 bonding UI (docs/fish-companions.md §8.2, §8.4): the Companions tab, the carried-fish
     * picker, the live bond gate, rename and unequip — driven through the real screen's own
     * handlers (the same ones its buttons call; headless input is too brittle to rely on), with the
     * status strings checked beside the screenshots so the pictures and the numbers agree.
     */
    private static void queueCompanionBondScene() {
        queue(1, RenderSelfTest::paradeSetup);

        // The self-test world is recreated for every run, so counts and stored bonds start empty —
        // no resets needed. Every player-taking command below uses @p: the argument is
        // EntityArgument.player, which refuses a multi-entity selector like @a outright.
        int lore = bluegillLoreThreshold();

        // Carried fish for the picker: two identical bluegills loose, one different size, and a
        // pile holding two more identical ones — so one row must read ×4 and one ×1.
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getInventory().clearContent();
                player.getInventory().setItem(0, fish("bluegill", 25f).copyWithCount(2));
                player.getInventory().setItem(1, fish("bluegill", 31f));
                ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
                FishtasticItemData.setBundleContents(pile, new BundleContents(List.of(
                        net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(fish("bluegill", 25f)),
                        net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(fish("bluegill", 25f)))));
                player.getInventory().setItem(2, pile);
            }
        }));

        // Bluegill parked one catch short of complete: the tab must show "Bond: <lore-1>/<lore>".
        queue(1, mc -> server(mc, s -> run(s,
                "fishtastic companion catchcount @p fishtastic:bluegill " + (lore - 1))));

        // Open the encyclopedia through the real entry point (sync + menu).
        queue(10, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new grill24.fishtastic.network.RequestFishEncyclopediaPacket(true))));
        queue(20, mc -> {
            check("bond.screenOpen", mc.screen instanceof grill24.fishtastic.client.FishEncyclopediaScreen,
                    "screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()));
            screenshot(mc, "companionbond", "fish_tab");
        });

        // --- Locked state -----------------------------------------------------
        // Since the §20 listing rule the tab only shows the equipped companion or an unlocked
        // species you are carrying — a locked species has nothing to act on and is absent, even
        // while its fish is carried (its progress lives on the Fish tab).
        queue(5, mc -> bondScreen(mc).devShowCompanions());
        queue(10, mc -> {
            check("bond.companionsView", bondScreen(mc).devOnCompanionsView(), "");
            check("bond.lockedAbsent",
                    !bondScreen(mc).devHasRow(Ids.of("fishtastic", "bluegill"))
                            && bondScreen(mc).devCompanionRowCount() == 0,
                    "rows=" + bondScreen(mc).devCompanionRowCount());
            screenshot(mc, "companionbond", "locked_absent");
        });

        // --- Unlock: raise the count, reopen, expect the FTUE + banner ---------
        queue(1, mc -> server(mc, s -> run(s,
                "fishtastic companion catchcount @p fishtastic:bluegill " + lore)));
        queue(1, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new grill24.fishtastic.network.RequestFishEncyclopediaPacket(false))));
        queue(15, mc -> {
            check("bond.ftueStarted", grill24.fishtastic.client.CompanionTutorialClientHandler.currentStep()
                            == grill24.fishtastic.tutorial.CompanionTutorialStep.INTRO,
                    "step=" + grill24.fishtastic.client.CompanionTutorialClientHandler.currentStep());
            screenshot(mc, "companionbond", "unlock_banner");
        });
        queue(10, mc -> {
            String status = bondScreen(mc).devCompanionStatus(Ids.of("fishtastic", "bluegill"));
            check("bond.unlockedStatus", "Bond available".equals(status), "status=" + status);
            screenshot(mc, "companionbond", "bond_available_with_ftue");
        });
        // The tab is already open from the locked stage, so the FTUE's advance (which fires on
        // opening the tab) needs a real close-and-reopen: disc, then tab again.
        queue(1, mc -> bondScreen(mc).devShowFish());
        queue(5, mc -> bondScreen(mc).devShowCompanions());
        queue(10, mc -> {
            check("bond.ftueCompleted", grill24.fishtastic.client.CompanionTutorialClientHandler.currentStep()
                            == grill24.fishtastic.tutorial.CompanionTutorialStep.COMPLETE,
                    "step=" + grill24.fishtastic.client.CompanionTutorialClientHandler.currentStep());
        });

        // --- Picker -----------------------------------------------------------
        queue(1, mc -> bondScreen(mc).devOpenPicker(Ids.of("fishtastic", "bluegill")));
        queue(10, mc -> {
            var groups = bondScreen(mc).devPickerGroups();
            check("bond.pickerRows", groups.size() == 2, "rows=" + groups.size());
            boolean fourAndOne = groups.stream().anyMatch(g -> g.count() == 4) && groups.stream().anyMatch(g -> g.count() == 1);
            check("bond.pickerGroups", fourAndOne,
                    "counts=" + groups.stream().map(g -> Integer.toString(g.count())).toList());
            screenshot(mc, "companionbond", "picker");
        });

        // --- Bond, rename, unequip -------------------------------------------
        queue(1, mc -> bondScreen(mc).devSelectPickerRow(0));
        queue(5, mc -> {
            screenshot(mc, "companionbond", "picker_selected");
            bondScreen(mc).devConfirmPicker();
        });
        queue(15, mc -> {
            ItemStack companion = grill24.fishtastic.companion.CompanionStorage.get(mc.player);
            check("bond.bondedThroughScreen", !companion.isEmpty(), "companion=" + companion);
            check("bond.equippedStatus", "Your companion".equals(
                            bondScreen(mc).devCompanionStatus(Ids.of("fishtastic", "bluegill"))),
                    "status=" + bondScreen(mc).devCompanionStatus(Ids.of("fishtastic", "bluegill")));
            screenshot(mc, "companionbond", "equipped");
        });
        queue(1, mc -> bondScreen(mc).devRename("Bubbles"));
        queue(15, mc -> {
            ItemStack companion = grill24.fishtastic.companion.CompanionStorage.get(mc.player);
            check("bond.renamed", companion.getHoverName().getString().equals("Bubbles"),
                    "name=" + companion.getHoverName().getString());
            screenshot(mc, "companionbond", "renamed");
        });

        // --- Unlisted species line -------------------------------------------
        // Under the §20 listing rule a locked row never shows, so the "unlocks via quest" line is
        // unreachable from this tab (the species' page can carry it later if wanted). What the tab
        // must show for a signature species is the same as any other: unlocked + carried.
        queue(1, mc -> server(mc, s -> {
            run(s, "fishtastic companion catchcount @p fishtastic:molten_moorish_idol 1");
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getInventory().add(fish("molten_moorish_idol", 40f));
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(15, mc -> {
            check("bond.unlistedLockedAbsent",
                    !bondScreen(mc).devHasRow(Ids.of("fishtastic", "molten_moorish_idol")),
                    "rows=" + bondScreen(mc).devCompanionRowCount());
            screenshot(mc, "companionbond", "unlisted_locked_absent");
        });
        queue(1, mc -> server(mc, s -> {
            run(s, "fishtastic companion unlock @p fishtastic:molten_moorish_idol");
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(15, mc -> {
            String status = bondScreen(mc).devCompanionStatus(Ids.of("fishtastic", "molten_moorish_idol"));
            check("bond.unlistedStatus", "Bond available".equals(status), "status=" + status);
            screenshot(mc, "companionbond", "unlisted_unlocked");
        });

        // --- Unequip ----------------------------------------------------------
        queue(1, mc -> bondScreen(mc).devUnequip());
        queue(15, mc -> {
            check("bond.unequipped", grill24.fishtastic.companion.CompanionStorage.get(mc.player).isEmpty(),
                    "companion=" + grill24.fishtastic.companion.CompanionStorage.get(mc.player));
            screenshot(mc, "companionbond", "unequipped");
        });
        // Close the screen: while a custom menu is open the server-side inventory changes do not
        // reach the client's view, so scenes after this one would read a stale main hand and
        // inventory (found the hard way: every later held-item reaction read the same stale fish).
        queue(1, mc -> mc.setScreen(null));

        // Cleanup: close the screen again and leave no companion or stored bonds behind.
        queue(1, mc -> mc.setScreen(null));
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.unequip(player);
                player.getInventory().clearContent();
                mirrorInventoryToClient(mc, player); // no stale carried fish for the next scene
            }
        }));
        queue(10, mc -> companionLog("bond scene end"));
    }

    /** The open encyclopedia screen, for the bond scene's dev accessors. */
    private static grill24.fishtastic.client.FishEncyclopediaScreen bondScreen(Minecraft mc) {
        return (grill24.fishtastic.client.FishEncyclopediaScreen) mc.screen;
    }

    /** Bluegill's derived lore threshold — the catch count that completes its entry (phase 4 gate). */
    private static int bluegillLoreThreshold() {
        Minecraft mc = Minecraft.getInstance();
        var key = net.minecraft.resources.ResourceKey.create(grill24.FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY,
                Ids.of("fishtastic", "bluegill"));
        return grill24.fishtastic.data.EncyclopediaTiers.thresholdsFor(mc.level.registryAccess(), key).loreCatches();
    }

    /**
     * Phase 5 (§5): the fishing loop. Casts a real bobber (its position is the at-the-line anchor
     * every client derives, and the synced bite flag is the nibble), then drives demo minigames
     * with real fish targets through docking, the bar-state reaction, a celebration fly-past ended
     * by the skip key, a catch return and an escape droop — plus the waits-at-the-bobber setting.
     *
     * <p>The demos inject the animation directly (no server session), so {@code sendMinigameResults}
     * no-ops on the -1 session id: the scene checks the client-side outcome path, and the server's
     * own half is covered by the gametests.
     */
    private static void queueCompanionFishingScene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        double[] preDock = new double[1];

        // Cast: a real hook, from a real rod, through the real use path.
        queue(30, mc -> server(mc, s -> run(s, "item replace entity @a hotbar.0 with fishtastic:copper_fishing_rod")));
        queue(5, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        final java.util.List<double[]> approach = new java.util.ArrayList<>();
        queue(30, mc -> {
            check("fish.cast", mc.player.fishing != null, "hook=" + mc.player.fishing);
            companionLog("cast");
            // §21 diag: lineWait, gap and speed per tick through the approach, so the profile in
            // force is visible over time (not just at one instant).
            startTickProbe(approach);
        });
        queue(25, mc -> {
            tickProbe = null;
            for (int i = 0; i < approach.size(); i += 4) {
                double[] r = approach.get(i);
                Fishtastic.LOGGER.info("[selftest] fish.approach t={} gap={} v={} gv={}",
                        (long) r[0], fmt3(r[9]), fmt3(Math.hypot(r[7], r[8])), fmt3(r[9] < 1e-6 ? 0 : Math.hypot(r[7], r[8]) / r[9]));
            }
            var c = companion();
            double dist = c == null ? -1 : c.lineDistance();
            preDock[0] = dist;
            if (c != null && mc.player.fishing != null) {
                Fishtastic.LOGGER.info(
                        "[selftest] fish.atBobberDiag dist={} centre={} drawn={} target={} vel={} hook={}",
                        fmt3(dist), vec3(c.devFollowCenter()), vec3(c.devFishPoint()),
                        vec3(c.devFollowTarget()), vec3(c.devFollowVelocity()),
                        vec3(mc.player.fishing.position()));
            }
            check("fish.atLine", c != null && c.lineWaitAmount() > 0.8f,
                    "line=" + (c == null ? null : c.lineWaitAmount()));
            check("fish.atBobber", dist >= 0 && dist < 1.6, "dist=" + dist);
            Fishtastic.LOGGER.info("[selftest] fish.wiggle atLine: {}",
                    c == null ? "no companion" : c.devWiggleRates());
            screenshot(mc, "companionfishing", "at_line");
        });
        // Third person, so the companion at the line is legible at all — a 25 cm fish beside a
        // bobber is a speck from the fisher's own eyes at casting distance.
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionfishing", "at_line_third_person"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // Nibble: the bite flag is synced entity data — forcing it locally is exactly what lands
        // on every client when the server's bite cycle fires.
        queue(1, mc -> {
            if (mc.player.fishing != null) {
                ((grill24.fishtastic.util.IFishingHookExtension) mc.player.fishing).fishtastic$forceBite();
            }
        });
        queue(12, mc -> {
            check("fish.nibble", companion() != null && companion().nibbleAmount() > 0.6f,
                    "nibble=" + (companion() == null ? null : companion().nibbleAmount()));
            screenshot(mc, "companionfishing", "nibble");
        });

        // Minigame 1: dock from the bobber, bar-state reaction, then a discovery celebration
        // (the bluegill is "undiscovered" and is the companion's own species, so it greets it).
        queue(1, mc -> openFishingDemoMinigame(mc, true));
        queue(30, mc -> {
            check("fish.docks", grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked(), "");
            // The rendered position: the swim to the dock is a render-time blend of the world
            // anchor, which stays on the line point so the swim has a fixed source.
            double dist = companion() == null ? -1 : companion().devFrameDistanceToHook();
            check("fish.leftBobber", preDock[0] >= 0 && dist >= 0 && dist > preDock[0] + 0.5,
                    "before=" + preDock[0] + " after=" + dist);
            screenshot(mc, "companionfishing", "docked");
        });
        queue(1, mc -> {
            // The published anchors are the docking contract (R3.1): the lane must stay clear of
            // the bar's content rect so it can never cover the bobber zone or a fish target.
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            float[] lane = anim == null ? null : anim.companionDockAnchor(w, h);
            var a = anim == null ? null : anim.companionAnchors(w, h);
            check("fish.laneClear", lane != null && a != null && lane[0] + lane[2] < a.barLeft()
                            && lane[1] - lane[2] > a.gearBottom(),
                    "lane=" + java.util.Arrays.toString(lane) + " barLeft=" + (a == null ? null : a.barLeft())
                            + " gearBottom=" + (a == null ? null : a.gearBottom()));
        });
        queue(1, mc -> {
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            var target = anim == null ? null : anim.getMinigameState().getTargets().get(0);
            for (int i = 0; i < 20 && target != null; i++) target.updateCatchProgress(1f);
        });
        queue(4, mc -> {
            check("fish.inZone", companion() != null && companion().devFishProgress() > 0.2f
                            && companion().devProgressDelta() >= -0.005f,
                    companionLogString("progress"));
            Fishtastic.LOGGER.info("[selftest] fish.wiggle docked: {}",
                    companion() == null ? "no companion" : companion().devWiggleRates());
            screenshot(mc, "companionfishing", "in_zone");
        });
        queue(1, mc -> {
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            var target = anim == null ? null : anim.getMinigameState().getTargets().get(0);
            if (target != null) {
                target.setNoCatchDrain(false); // let the drain reaction run for real
                for (int i = 0; i < 12; i++) target.updateCatchProgress(0f);
            }
        });
        queue(4, mc -> {
            check("fish.drainReaction", companion() != null && companion().devProgressDelta() < -0.02f,
                    companionLogString("drain"));
            screenshot(mc, "companionfishing", "drain");
        });
        queue(1, mc -> {
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            if (anim == null) {
                companionLog("catch: no anim");
                return;
            }
            var t = anim.getMinigameState().getTargets().get(0);
            companionLog("catch pre: state=" + t.getState() + " progress=" + t.getCatchProgress());
            anim.devCatchTarget(0);
            companionLog("catch post: caught=" + anim.getCaughtTargetIndices()
                    + " celebration=" + (anim.getCompanionCelebration() != null));
        });
        queue(8, mc -> {
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            check("fish.celebration", anim != null && anim.getCompanionCelebration() != null,
                    "anim=" + (anim != null) + " celebration=" + (anim != null && anim.getCompanionCelebration() != null));
            check("fish.greet", companion() != null && companion().flyPastAmount() > 0.15f,
                    "fly=" + (companion() == null ? null : companion().flyPastAmount()));
            screenshot(mc, "companionfishing", "celebrate_greet");
        });
        queue(35, mc -> {
            check("fish.flyPast", companion() != null && companion().flyPastAmount() > 0.5f,
                    "fly=" + (companion() == null ? null : companion().flyPastAmount()));
            screenshot(mc, "companionfishing", "celebrate_flypast");
        });
        queue(1, mc -> {
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            if (anim != null && anim.getCompanionCelebration() != null) anim.getCompanionCelebration().skipToSettle();
        });
        queue(60, mc -> {
            check("fish.skipEndsFlight", companion() != null && companion().flyPastAmount() < 0.25f,
                    "fly=" + (companion() == null ? null : companion().flyPastAmount()));
            screenshot(mc, "companionfishing", "after_skip");
        });
        queue(60, mc -> {
            check("fish.catchOutcome", companion() != null
                            && companion().devLastOutcome() == grill24.fishtastic.companion.CompanionFishingOutcome.CAUGHT,
                    "outcome=" + (companion() == null ? null : companion().devLastOutcome()));
            screenshot(mc, "companionfishing", "catch_return");
        });

        // Minigame 2: a fish that gets away — droop, sink, slow swim back (~3 s, §5).
        queue(20, mc -> openFishingDemoMinigame(mc, true));
        queue(30, mc -> {
            var anim = grill24.fishtastic.client.companion.ClientCompanions.activeMinigame();
            var target = anim == null ? null : anim.getMinigameState().getTargets().get(0);
            if (target != null) target.startFailAnimation();
            companionLog("escaping");
        });
        queue(50, mc -> {
            check("fish.escapeOutcome", companion() != null
                            && companion().devLastOutcome() == grill24.fishtastic.companion.CompanionFishingOutcome.ESCAPED,
                    "outcome=" + (companion() == null ? null : companion().devLastOutcome()));
            screenshot(mc, "companionfishing", "escape_droop");
        });
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(10, mc -> screenshot(mc, "companionfishing", "escape_third_person"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // Waits at the bobber (§11): with the setting on the companion holds the line through the
        // minigame; off, it docks again.
        queue(30, mc -> {
            grill24.fishtastic.client.FishtasticClientConfig.setTestOverride(
                    grill24.fishtastic.client.FishtasticClientConfig.COMPANION_WAITS_AT_BOBBER_KEY, true);
        });
        queue(1, mc -> openFishingDemoMinigame(mc, false));
        queue(30, mc -> {
            check("fish.waitsAtBobber", !grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked()
                            && companion() != null && companion().lineWaitAmount() > 0.5f,
                    "docked=" + grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked()
                            + " line=" + (companion() == null ? null : companion().lineWaitAmount()));
            screenshot(mc, "companionfishing", "waits_at_bobber");
        });
        queue(1, mc -> {
            grill24.fishtastic.client.FishtasticClientConfig.setTestOverride(
                    grill24.fishtastic.client.FishtasticClientConfig.COMPANION_WAITS_AT_BOBBER_KEY, false);
        });
        queue(30, mc -> {
            check("fish.docksAgain", grill24.fishtastic.client.companion.ClientCompanions.isLocalDocked(), "");
            screenshot(mc, "companionfishing", "docks_again");
        });

        // Cleanup: no animation, no overrides, no companion, no bobber.
        queue(1, mc -> {
            ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
            grill24.fishtastic.client.FishtasticClientConfig.clearTestOverrides();
        });
        queue(1, mc -> server(mc, s -> {
            run(s, "kill @e[type=minecraft:fishing_bobber]");
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.unequip(player);
                player.getInventory().clearContent();
            }
        }));
        queue(10, mc -> companionLog("fishing scene end"));
    }

    /**
     * Pet-to-pet scenes (§10, Phase 6): G1 (same species — a synchronized loop plus a bubble
     * heart) and G2 (two free swimmers — a figure eight around both owners). A headless run has
     * one real player, so the second companion is a dev phantom and the scene packet is injected
     * through the exact method the packet handler calls; the scheduler that decides scenes in
     * multiplayer is the game tests' job. After each scene the checks are that both companions
     * drop it and carry on with normal behaviour.
     */
    private static void queueCompanionScenesScene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        queue(20, mc -> {
            check("scenes.companionReady", companion() != null, "");
            check("scenes.noSceneYet", companion() != null && !companion().isInScene(), "");
        });

        final java.util.UUID[] phantom = new java.util.UUID[1];
        final java.util.List<Vec3> g1LocalOffsets = new java.util.ArrayList<>();
        final java.util.List<Vec3> g1PhantomOffsets = new java.util.ArrayList<>();
        queue(1, mc -> phantom[0] = grill24.fishtastic.client.companion.ClientCompanions.addPhantom(
                fish("bluegill", 25f), mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2.4))));
        queue(10, mc -> {
            // The same landing call the CompanionScenePacket handler makes — only the transport differs.
            grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                    grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES,
                    mc.player.getUUID(), phantom[0], mc.level.getGameTime());
            companionLog("G1 injected");
        });
        queue(14, mc -> {
            check("scenes.g1Local", companion() != null && companion().sceneType()
                    == grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES,
                    "scene=" + (companion() == null ? "none" : companion().sceneType()));
            grill24.fishtastic.client.companion.ClientCompanion partner =
                    grill24.fishtastic.client.companion.ClientCompanions.devFindAny(phantom[0]);
            check("scenes.g1Phantom", partner != null && partner.sceneType()
                    == grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES,
                    "phantom scene=" + (partner == null ? "no phantom" : partner.sceneType()));
        });
        // The bubble heart pops at HEART_TICK (3.5 s in, §20's slowed lap) — catch it while it
        // lives. Injected at T, so this lands right around T+70.
        queue(56, mc -> screenshot(mc, "companionscenes", "g1_heart"));
        // The loop swing is wide enough that first person can frame neither fish at a given
        // instant — take the geometry shots from behind the player, which holds the pair in frame.
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        // Sample both companions' offset from the scene midpoint. A fish circling the shared loop
        // sweeps a full turn in 140 ticks (§20's slowed lap); merely following would keep a
        // near-constant bearing — so the swept ANGLE (not the distance) is the discriminator, and
        // the pair must stay on opposite sides while it spins. 24 samples over 120 ticks.
        for (int i = 0; i < 24; i++) {
            queue(5, mc -> {
                grill24.fishtastic.client.companion.ClientCompanion local = companion();
                grill24.fishtastic.client.companion.ClientCompanion partner =
                        grill24.fishtastic.client.companion.ClientCompanions.devFindAny(phantom[0]);
                if (local == null || partner == null) return;
                Vec3 mid = local.devSceneMidpoint();
                if (mid == null || local.devFishPoint() == null || partner.devFishPoint() == null) return;
                g1LocalOffsets.add(local.devFishPoint().subtract(mid));
                g1PhantomOffsets.add(partner.devFishPoint().subtract(mid));
            });
        }
        queue(2, mc -> {
            double spinLocal = sweptAngle(g1LocalOffsets);
            double spinPhantom = sweptAngle(g1PhantomOffsets);
            int opposite = 0;
            for (int i = 0; i < g1LocalOffsets.size(); i++) {
                Vec3 a = g1LocalOffsets.get(i), b = g1PhantomOffsets.get(i);
                if (a.dot(b) < 0) opposite++;
            }
            Fishtastic.LOGGER.info("[selftest] scenes.g1 samples={} spinLocal={} spinPhantom={} opposite={}/{}",
                    g1LocalOffsets.size(), String.format(Locale.ROOT, "%.2f", spinLocal),
                    String.format(Locale.ROOT, "%.2f", spinPhantom), opposite, g1LocalOffsets.size());
            check("scenes.g1Looping", g1LocalOffsets.size() >= 8 && spinLocal > 4.0,
                    "samples=" + g1LocalOffsets.size() + " spin=" + String.format(Locale.ROOT, "%.2f", spinLocal));
            check("scenes.g1PhantomLooping", spinPhantom > 4.0,
                    "spin=" + String.format(Locale.ROOT, "%.2f", spinPhantom));
            check("scenes.g1Opposite", g1LocalOffsets.size() >= 8 && opposite >= g1LocalOffsets.size() * 3 / 4,
                    "opposite=" + opposite + "/" + g1LocalOffsets.size());
            screenshot(mc, "companionscenes", "g1_loop");
        });
        // G1 lasts 240 ticks (§20) and the injection happened at the scene's first queue — this
        // lands well after the scene must be over (sampling alone runs 120 ticks of it).
        queue(60, mc -> {
            check("scenes.g1Ended", companion() != null && !companion().isInScene(),
                    "scene=" + (companion() == null ? "none" : companion().sceneType()));
            grill24.fishtastic.client.companion.ClientCompanion partner =
                    grill24.fishtastic.client.companion.ClientCompanions.devFindAny(phantom[0]);
            check("scenes.g1PhantomEnded", partner != null && !partner.isInScene(), "");
            screenshot(mc, "companionscenes", "g1_after");
            companionLog("G1 end");
        });

        // G2: the same pair, a different scene — the packet is authoritative, whichever species.
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                    grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT,
                    mc.player.getUUID(), phantom[0], mc.level.getGameTime());
            companionLog("G2 injected");
        });
        queue(14, mc -> {
            check("scenes.g2Local", companion() != null && companion().sceneType()
                    == grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT,
                    "scene=" + (companion() == null ? "none" : companion().sceneType()));
            grill24.fishtastic.client.companion.ClientCompanion partner =
                    grill24.fishtastic.client.companion.ClientCompanions.devFindAny(phantom[0]);
            check("scenes.g2Phantom", partner != null && partner.sceneType()
                    == grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT, "");
        });
        queue(30, mc -> screenshot(mc, "companionscenes", "g2_eight_a"));
        queue(60, mc -> screenshot(mc, "companionscenes", "g2_eight_b"));
        // G2 lasts 320 ticks (§20) — wait it out (the two shots above land mid-scene by design).
        queue(240, mc -> {
            check("scenes.g2Ended", companion() != null && !companion().isInScene(),
                    "scene=" + (companion() == null ? "none" : companion().sceneType()));
            screenshot(mc, "companionscenes", "g2_after");
            companionLog("G2 end");
        });

        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
            server(mc, s -> {
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    grill24.fishtastic.companion.Companions.unequip(player);
                    player.getInventory().clearContent();
                }
                run(s, "gamemode creative @a");
            });
        });
        queue(10, mc -> companionLog("scenes end"));
    }

    /** Total angle swept between successive midpoint-relative points, radians (any plane — G1's
     *  ring is vertical, so the 2D horizontal cross-product would read it as a flat back-and-forth). */
    private static double sweptAngle(java.util.List<Vec3> offsets) {
        double total = 0;
        for (int i = 1; i < offsets.size(); i++) {
            Vec3 a = offsets.get(i - 1), b = offsets.get(i);
            if (a.lengthSqr() < 1e-6 || b.lengthSqr() < 1e-6) continue;
            double cos = Math.max(-1, Math.min(1, a.normalize().dot(b.normalize())));
            total += Math.acos(cos);
        }
        return total;
    }

    /** Opens a demo minigame with one bluegill fish target (optionally) in the undiscovered set. */
    private static void openFishingDemoMinigame(Minecraft mc, boolean withFish) {
        grill24.fishtastic.util.FishingMinigameAnimation animation = new grill24.fishtastic.util.FishingMinigameAnimation();
        // The bare constructor stages three example TREASURE targets (stone/diamond/gold); clear
        // them exactly as the real start-packet handler does, or the scene's target index points
        // at a placeholder rock.
        animation.getMinigameState().getTargets().clear();
        if (withFish) {
            ItemStack reward = fish("bluegill", 25f);
            animation.setUndiscoveredSpecies(java.util.Set.of(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(reward.getItem())));
            grill24.fishtastic.util.FishingTarget target = new grill24.fishtastic.util.FishingTarget(
                    java.util.List.of(reward), grill24.fishtastic.util.FishingTarget.TargetCategory.FISH,
                    new java.util.Random(7L), 0.5f, 0.2f, java.util.List.of());
            // The demo bobber rests away from the target, so the tick loop would drain (and fail)
            // it before the scene's deterministic catch; the scene re-enables drain for its
            // drain-reaction leg.
            target.setNoCatchDrain(true);
            animation.getMinigameState().addTarget(target);
        }
        ((grill24.fishtastic.util.IGameRendererExtension) mc.gameRenderer).fishtastic$displayItemActivation(() -> animation);
    }

    /** The companion's fishing reaction debug line, for scene checks. */
    private static String companionLogString(String label) {
        return label + " " + grill24.fishtastic.client.companion.ClientCompanions.debugState();
    }

    /**
     * Post-v1 polish (docs/fish-companions.md §19): the two owner-playtest motion fixes and the
     * solo-playtest dev commands.
     *
     * <ul>
     *   <li><b>Facing:</b> samples the presented-facing dot against the companion's true world
     *       velocity while a G1 scene drives its box fast — the case the owner reported ("moving in
     *       a direction they are not facing").</li>
     *   <li><b>View decoupling:</b> flicks the camera 90° while standing still and measures how far
     *       the fish travels over the next 12 ticks — the case "turning the camera relocates the
     *       fish".</li>
     *   <li><b>Commands:</b> drives every new dev trigger through the real command dispatcher.</li>
     * </ul>
     */
    private static void queueCompanionPolishScene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        queue(40, mc -> {
            check("polish.ready", companion() != null, "");
            mc.player.setXRot(0f);
            mc.player.setYRot(180f);
            mc.player.setYBodyRot(180f);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });

        // ── Command legs: every new dev trigger, through the real dispatcher ──────
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion trick")));
        queue(3, mc -> check("polish.trickCommand", companion() != null && companion().devTrickActive(),
                "active=" + (companion() != null && companion().devTrickActive())));
        queue(55, mc -> check("polish.trickEnds", companion() != null && !companion().devTrickActive(), ""));

        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion peek")));
        queue(3, mc -> check("polish.peekCommand", companion() != null && companion().isPeeking(), ""));

        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion outcome caught")));
        queue(3, mc -> check("polish.outcomeCommand",
                companion() != null && companion().fishingOutcome() == grill24.fishtastic.companion.CompanionFishingOutcome.CAUGHT,
                "outcome=" + (companion() == null ? "none" : companion().fishingOutcome())));

        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion phantom fishtastic:bluegill 30")));
        queue(3, mc -> check("polish.phantomCommand", grill24.fishtastic.client.companion.ClientCompanions.devPhantomIds().size() == 1,
                "phantoms=" + grill24.fishtastic.client.companion.ClientCompanions.devPhantomIds().size()));

        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion waitatbobber true")));
        queue(3, mc -> check("polish.settingCommand",
                grill24.fishtastic.client.FishtasticClientConfig.companionWaitsAtBobber(), ""));
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion waitatbobber false")));
        queue(3, mc -> check("polish.settingRestored",
                !grill24.fishtastic.client.FishtasticClientConfig.companionWaitsAtBobber(), ""));

        // ── View decoupling: a camera flick must not relocate the fish ────────────
        // Measured twice in one run, on one state: with the pre-polish camera-locked basis (the
        // dev seam) and with the companion's own frame — and with the peek system paused, because
        // an unseen-triggered peek dashes toward the view and would masquerade as view-following.
        final Vec3[] turnBefore = new Vec3[1];
        final float[] turnYawBefore = new float[1];
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(true);
            grill24.fishtastic.client.companion.ClientCompanions.devCameraLockedFrame(true);
        });
        queue(120, mc -> {
            // Settle: the forced peek (OUT+HOLD+BACK = 88 ticks), the trick and the outcome
            // envelope have all expired and the fish is back on its orbit. Re-assert the dev seams
            // here too: they are per-companion state, and asserting them at the sample point (not
            // only at scene start) makes the measurement self-contained if anything rebuilt since.
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(true);
            grill24.fishtastic.client.companion.ClientCompanions.devCameraLockedFrame(true);
            mc.player.setYRot(180f);
            mc.player.setYBodyRot(180f);
            check("polish.settled", companion() != null && !companion().isPeeking(),
                    "peek=" + (companion() == null ? "none" : companion().isPeeking())
                            + " trick=" + (companion() != null && companion().devTrickActive())
                            + " outcome=" + (companion() == null ? "none" : companion().fishingOutcome()));
            companionLog("camera-locked settle basis=" + grill24.fishtastic.client.companion.ClientCompanions.devLocalCameraLockedFrame());
            turnBefore[0] = companion() == null ? null : companion().devFishPoint();
            turnYawBefore[0] = companion() == null ? Float.NaN : companion().devAnchorYaw();
        });
        queue(1, mc -> mc.player.setYRot(270f)); // a 90° flick, standing still
        queue(3, mc -> turnWindowLog(mc, "before-basis k1"));
        queue(3, mc -> turnWindowLog(mc, "before-basis k2"));
        queue(6, mc -> {
            Vec3 after = companion() == null ? null : companion().devFishPoint();
            double moved = (after == null || turnBefore[0] == null) ? -1 : after.distanceTo(turnBefore[0]);
            Fishtastic.LOGGER.info("[selftest] polish.turnDisplacement (camera-locked basis) ={} blocks over 12 ticks "
                            + "after a 90-degree view flick while standing still | frameYaw {}->{} yRot {}->{} peek={} "
                            + "player=({},{},{}) before={} after={}",
                    String.format(Locale.ROOT, "%.3f", moved),
                    String.format(Locale.ROOT, "%.1f", turnYawBefore[0]),
                    companion() == null ? "none" : String.format(Locale.ROOT, "%.1f", companion().devAnchorYaw()),
                    String.format(Locale.ROOT, "%.1f", 180f),
                    String.format(Locale.ROOT, "%.1f", mc.player.getYRot()),
                    companion() != null && companion().isPeeking(),
                    String.format(Locale.ROOT, "%.2f", mc.player.getX()),
                    String.format(Locale.ROOT, "%.2f", mc.player.getY()),
                    String.format(Locale.ROOT, "%.2f", mc.player.getZ()),
                    turnBefore[0] == null ? "null" : String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", turnBefore[0].x, turnBefore[0].y, turnBefore[0].z),
                    after == null ? "null" : String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", after.x, after.y, after.z));
            check("polish.turnBeforeReproduces", moved > 0.9, "moved=" + moved);
            companionLog("camera-locked flick before/after:  basis=" + grill24.fishtastic.client.companion.ClientCompanions.devLocalCameraLockedFrame() + " " + turnBefore[0] + " -> " + after);
            screenshot(mc, "companionpolish", "after_turn_before_basis");
        });
        // Now the same flick with the companion's own frame: settle back, un-clamp the basis.
        final Vec3[] turnBefore2 = new Vec3[1];
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devCameraLockedFrame(false);
            mc.player.setYRot(180f);
            mc.player.setYBodyRot(180f);
        });
        queue(80, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(true);
            grill24.fishtastic.client.companion.ClientCompanions.devCameraLockedFrame(false);
            mc.player.setYRot(180f);
            mc.player.setYBodyRot(180f);
            companionLog("own-frame settle basis=" + grill24.fishtastic.client.companion.ClientCompanions.devLocalCameraLockedFrame());
            turnBefore2[0] = companion() == null ? null : companion().devFishPoint();
        });
        queue(1, mc -> mc.player.setYRot(270f));
        queue(3, mc -> turnWindowLog(mc, "own-frame k1"));
        queue(3, mc -> turnWindowLog(mc, "own-frame k2"));
        queue(6, mc -> {
            Vec3 after = companion() == null ? null : companion().devFishPoint();
            double moved = (after == null || turnBefore2[0] == null) ? -1 : after.distanceTo(turnBefore2[0]);
            Fishtastic.LOGGER.info("[selftest] polish.turnDisplacement (own frame) ={} blocks over 12 ticks after a "
                            + "90-degree view flick while standing still (orbit baseline is ~0.17); peek={}",
                    String.format(Locale.ROOT, "%.3f", moved),
                    companion() != null && companion().isPeeking());
            // With the pre-polish basis the same flick moves the fish well over a block; decoupled it
            // is the orbit's own drift plus a slow re-anchor, well under half a block.
            check("polish.turnKeepsFish", moved >= 0 && moved < 0.6, "moved=" + moved);
            companionLog("own-frame flick before/after:  basis=" + grill24.fishtastic.client.companion.ClientCompanions.devLocalCameraLockedFrame() + " " + turnBefore2[0] + " -> " + after);
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(false);
        });

        // ── Facing: the drawn nose must follow the world velocity ─────────────────
        final double[] dotSum = {0};
        final int[] dotCount = {0};
        final float[] dotMin = {Float.MAX_VALUE};
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion scene g1")));
        queue(4, mc -> check("polish.sceneCommand",
                companion() != null && companion().sceneType() == grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES,
                "scene=" + (companion() == null ? "none" : companion().sceneType())));
        queue(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        // Skip the blend-in: the swing from the follow orbit onto the ring is a genuine direction
        // reversal, and with the facing cap at the sim's own turn rate (docs §20) the nose lags
        // through it on purpose — exactly as a tank fish does. The check measures the steady ring,
        // where "no sampled frame faces backwards" must hold outright.
        queue(10, mc -> {});
        for (int i = 0; i < 20; i++) {
            queue(2, mc -> {
                var c = companion();
                if (c == null) return;
                float speed = c.devFaceSpeed();
                float dot = c.devFaceDot();
                if (speed > 0.02f && !Float.isNaN(dot)) {
                    dotSum[0] += dot;
                    dotCount[0]++;
                    dotMin[0] = Math.min(dotMin[0], dot);
                }
            });
        }
        queue(1, mc -> {
            double mean = dotCount[0] == 0 ? Double.NaN : dotSum[0] / dotCount[0];
            Fishtastic.LOGGER.info("[selftest] polish.faceDot mean={} min={} samples={} (scene-driven box motion; "
                            + "the pose's own organic yaw wiggle holds the mean below 1 — backwards frames are the failure)",
                    String.format(Locale.ROOT, "%.3f", mean), String.format(Locale.ROOT, "%.3f", dotMin[0]), dotCount[0]);
            // Before the fix the nose followed the box-local heading while the ring carried the
            // fish, so samples went negative; following the world velocity no sampled frame faces
            // backwards, and the mean sits near cos(wiggle) of +1.
            check("polish.facesVelocity", dotCount[0] > 6 && dotMin[0] > 0.0f,
                    "min=" + dotMin[0] + " mean=" + mean + " samples=" + dotCount[0]);
            screenshot(mc, "companionpolish", "scene_facing");
        });
        queue(1, mc -> server(mc, s -> run(s, "fishtastic companion scene stop")));
        queue(3, mc -> check("polish.sceneStop", companion() != null && !companion().isInScene(),
                "scene=" + (companion() == null ? "none" : companion().sceneType())));

        // ── Cleanup ───────────────────────────────────────────────────────────────
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
            server(mc, s -> {
                run(s, "fishtastic companion waitatobber false");
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    grill24.fishtastic.companion.Companions.unequip(player);
                    player.getInventory().clearContent();
                }
            });
        });
        queue(10, mc -> companionLog("polish scene end"));
    }

    /**
     * Playtest round 2 (docs §20): the baseline-motion metrics — position flicker and the
     * presented heading's angular error against the true world velocity — over a plain-follow
     * window, with an in-run pair for the frame-yaw interpolation (legacy snap vs interpolated);
     * then the Companions tab's carried-and-unlocked listing rule and the bond picker's viewport
     * pin under scroll. Peeks and tricks are suppressed for the motion window so only baseline
     * motion moves the fish.
     */
    private static void queueCompanionPolish2Scene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        queue(40, mc -> {
            check("polish2.ready", companion() != null, "");
            mc.player.setXRot(0f);
            mc.player.setYRot(180f);
            mc.player.setYBodyRot(180f);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(true);
        });

        // ── Jitter, legacy half: the frame yaw steps at tick rate, as before the fix ──
        queue(60, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyFrameYaw(true);
            if (companion() != null) companion().devResetFrameStats();
        });
        queue(200, mc -> {
            var c = companion();
            Fishtastic.LOGGER.info("[selftest] polish2.wiggle legacy (un-interpolated frame yaw): flicker={} step={} frames={}",
                    c == null ? "n/a" : String.format(Locale.ROOT, "%.3f", c.devFrameFlicker()),
                    c == null ? "n/a" : String.format(Locale.ROOT, "%.5f", c.devFrameStep()),
                    c == null ? 0 : c.devFrameSamples());
        });

        // ── Jitter + facing, fixed half: interpolated frame yaw, velocity heading ──
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyFrameYaw(false);
            var c = companion();
            if (c != null) {
                c.devResetFrameStats();
                c.devResetFacingStats();
            }
        });
        queue(200, mc -> {
            var c = companion();
            if (c == null) {
                check("polish2.window", false, "no companion");
                return;
            }
            float flicker = c.devFrameFlicker();
            float step = c.devFrameStep();
            int frames = c.devFrameSamples();
            float meanErr = c.devFaceErrMeanDeg();
            float maxErr = c.devFaceErrMaxDeg();
            float rateMax = c.devHeadingRateMaxDegPerSec();
            int facing = c.devFacingSamples();
            Fishtastic.LOGGER.info("[selftest] polish2.window flicker={} step={} frames={} | faceMeanDeg={} faceMaxDeg={} rateMaxDegPerSec={} facing={} dot={}",
                    String.format(Locale.ROOT, "%.3f", flicker),
                    String.format(Locale.ROOT, "%.5f", step),
                    frames,
                    String.format(Locale.ROOT, "%.1f", meanErr),
                    String.format(Locale.ROOT, "%.1f", maxErr),
                    String.format(Locale.ROOT, "%.0f", rateMax),
                    facing,
                    String.format(Locale.ROOT, "%.3f", c.devFaceDot()));
            // A sample-count guard, not a quality bar: suite runs under load shed frame samples to
            // the dt-consistency filter (99 vs ~300 solo); the flicker shape is long settled by 60.
            check("polish2.frames", frames >= 60, "frames=" + frames);
            // Fixed basis: the drawn position must travel smoothly (period-2 reverberation ≈ 1).
            check("polish2.flicker", !Float.isNaN(flicker) && flicker <= 0.2f,
                    "flicker=" + flicker + " step=" + step + " frames=" + frames);
            // The presented heading is the world-velocity heading (tank-fidelity): tight mean
            // angular error while moving, and never a snap (the rate cap bounds every step).
            check("polish2.facingMean", facing >= 60 && meanErr < 10f,
                    "mean=" + meanErr + " n=" + facing);
            check("polish2.facingMax", facing >= 60 && maxErr < 35f, "max=" + maxErr);
            check("polish2.headingRate", rateMax <= 200f, "rate=" + rateMax);
            screenshot(mc, "companionpolish2", "follow_window");
        });

        // ── The §20 listing rule: equipped OR (unlocked AND carried) ──────────────
        // Four distinct states, one species each, so every row is unambiguous: bluegill is the
        // EQUIPPED one (bondFish) and is not carried; betta is unlocked-and-carried; plaice is
        // locked-and-carried; clown_loach is unlocked-and-not-carried.
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getInventory().clearContent();
                player.getInventory().setItem(0, fish("betta", 20f));
                player.getInventory().setItem(1, fish("plaice", 20f));
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(1, mc -> server(mc, s -> {
            run(s, "fishtastic companion unlock @p fishtastic:betta");
            run(s, "fishtastic companion unlock @p fishtastic:clown_loach");
        }));
        queue(10, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new grill24.fishtastic.network.RequestFishEncyclopediaPacket(true))));
        queue(20, mc -> bondScreen(mc).devShowCompanions());
        queue(10, mc -> {
            var screen = bondScreen(mc);
            check("polish2.carriedShown", screen.devHasRow(Ids.of("fishtastic", "betta")),
                    "rows=" + screen.devCompanionRowCount());
            check("polish2.carriedLockedAbsent", !screen.devHasRow(Ids.of("fishtastic", "plaice")),
                    "rows=" + screen.devCompanionRowCount());
            check("polish2.unlockedNotCarriedAbsent", !screen.devHasRow(Ids.of("fishtastic", "clown_loach")),
                    "rows=" + screen.devCompanionRowCount());
            check("polish2.equippedShown", screen.devHasRow(Ids.of("fishtastic", "bluegill")),
                    "rows=" + screen.devCompanionRowCount());
            screenshot(mc, "companionpolish2", "tab_filter");
        });
        // Carrying nothing at all: only the equipped row remains (Unequip/rename stay reachable).
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getInventory().clearContent();
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(10, mc -> {
            var screen = bondScreen(mc);
            check("polish2.equippedOnly", screen.devHasRow(Ids.of("fishtastic", "bluegill"))
                            && screen.devCompanionRowCount() == 1,
                    "rows=" + screen.devCompanionRowCount());
            screenshot(mc, "companionpolish2", "tab_equipped_only");
        });

        // ── The §20 picker pin under scroll ───────────────────────────────────────
        // Enough rows to actually scroll: several species unlocked-and-carried, plus the equipped
        // bluegill. Without real scrollable content the pin assertion below would be vacuous.
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getInventory().setItem(0, fish("bluegill", 25f));
                player.getInventory().setItem(1, fish("betta", 20f));
                player.getInventory().setItem(2, fish("clown_loach", 20f));
                player.getInventory().setItem(3, fish("sulphur_molly", 10f));
                player.getInventory().setItem(4, fish("neon_goby", 8f));
                player.getInventory().setItem(5, fish("moorish_idol", 30f));
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(1, mc -> server(mc, s -> {
            run(s, "fishtastic companion unlock @p fishtastic:clown_loach");
            run(s, "fishtastic companion unlock @p fishtastic:sulphur_molly");
            run(s, "fishtastic companion unlock @p fishtastic:neon_goby");
            run(s, "fishtastic companion unlock @p fishtastic:moorish_idol");
        }));
        queue(5, mc -> {
            bondScreen(mc).devShowFish();
            bondScreen(mc).devShowCompanions();
        });
        queue(1, mc -> bondScreen(mc).devSetScroll(9999f)); // clamp puts us at the bottom
        queue(3, mc -> {
            check("polish2.scrolled", bondScreen(mc).devScrollY() > 0.5f,
                    "scroll=" + bondScreen(mc).devScrollY() + " rows=" + bondScreen(mc).devCompanionRowCount());
            screenshot(mc, "companionpolish2", "scrolled");
        });
        queue(1, mc -> bondScreen(mc).devOpenPicker(Ids.of("fishtastic", "bluegill")));
        queue(3, mc -> {
            var screen = bondScreen(mc);
            float[] b = screen.devPickerBounds();
            float[] dim = screen.devPickerDimBounds();
            float w = screen.devScreenWidth(), h = screen.devScreenHeight();
            Fishtastic.LOGGER.info("[selftest] polish2.picker bounds={} dim={} screen={}x{} scroll={}",
                    java.util.Arrays.toString(b), java.util.Arrays.toString(dim), w, h, screen.devScrollY());
            check("polish2.pickerOnScreen",
                    b != null && b[0] >= -1f && b[1] >= -1f && b[0] + b[2] <= w + 1f && b[1] + b[3] <= h + 1f,
                    "bounds=" + java.util.Arrays.toString(b) + " screen=" + w + "x" + h);
            check("polish2.dimCovers", dim != null && dim[1] <= 0.5f && dim[3] >= h - 1f,
                    "dim=" + java.util.Arrays.toString(dim));
            // A scroll while open (the dev path bypasses the modal swallow) must not move it: that
            // exercises the per-tick pin, not the input block.
            screen.devSetScroll(9999f);
        });
        queue(3, mc -> {
            var screen = bondScreen(mc);
            float[] b = screen.devPickerBounds();
            check("polish2.pickerPinned",
                    b != null && b[1] >= -1f && b[1] + b[3] <= screen.devScreenHeight() + 1f,
                    "bounds=" + java.util.Arrays.toString(b) + " scroll=" + screen.devScrollY());
            screenshot(mc, "companionpolish2", "picker_scrolled");
        });
        queue(1, mc -> bondScreen(mc).devSelectPickerRow(0));
        queue(3, mc -> screenshot(mc, "companionpolish2", "picker_selected_scrolled"));
        queue(1, mc -> bondScreen(mc).devCancelPicker());
        queue(3, mc -> check("polish2.pickerClosed", bondScreen(mc).devPickerBounds() == null, ""));

        // ── Cleanup ───────────────────────────────────────────────────────────────
        queue(1, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(false);
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyFrameYaw(false);
            mc.setScreen(null);
        });
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.unequip(player);
                player.getInventory().clearContent();
                mirrorInventoryToClient(mc, player); // no stale carried fish for the next scene
            }
        }));
        queue(10, mc -> companionLog("polish2 scene end"));
    }

    /**
     * Playtest round 3 (§21): the follow-acceleration ramp and the G2 figure-eight crossing.
     *
     * <p>All measurement goes through {@link #tickProbe} (every client tick): the step queue
     * advances one step per TWO ticks (step, then a tick of wait), so per-step samples cannot
     * measure per-tick rates. The ramp legs teleport the player one step per queued step (a
     * deterministic staggered walk), then stop and watch the fish come home; the crossing leg
     * drives the G2 scene twice - legacy same-sense sweep vs fixed - and samples the RAW path
     * every tick.
     */
    private static void queueCompanionPolish3Scene() {
        queue(1, RenderSelfTest::paradeSetup);
        bondFish("bluegill", 25f);
        queue(40, mc -> {
            check("ramp3.ready", companion() != null, "");
            mc.player.setXRot(0f);
            mc.player.setYRot(180f);
            mc.player.setYBodyRot(180f);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(true);
            // Pin the orbit phase: it seeds from the fish's identity hash, so an unpinned walk
            // window samples the phase at random (measured meanGap 0.23–0.57 across runs, with
            // both walk checks flipping together). 180° puts the orbit point behind the walk
            // direction, the trailing regime the round-3 bands were calibrated on.
            grill24.fishtastic.client.companion.ClientCompanions.devSetOrbitPhaseDeg(180.0);
        });
        // §21 wobble bisect params: ff=<weight> (follow feed-forward), comp=<weight> (fish-centring
        // compensation). Absent = defaults; the steady-state wobble legs report the numbers.
        String ffParam = param("ff", "");
        String compParam = param("comp", "");
        String legacyParam = param("legacy", "");
        String boxParam = param("box", "");
        String steerParam = param("steer", "");
        queue(1, mc -> {
            // steer=legacy: the moment profile without its jerk-aware lookahead — the pre-fix
            // steering, for the equal-grounds wobble A/B (docs §21).
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyMomentSteer(steerParam.equals("legacy"));
            if (!ffParam.isEmpty()) {
                grill24.fishtastic.client.companion.ClientCompanions.devFeedForward(Double.parseDouble(ffParam));
            }
            if (!compParam.isEmpty()) {
                grill24.fishtastic.client.companion.ClientCompanions.devCompensation(Double.parseDouble(compParam));
            }
            if (!legacyParam.isEmpty()) {
                grill24.fishtastic.client.companion.ClientCompanions.devLegacyFollow(!legacyParam.equals("0"));
            }
            if (!boxParam.isEmpty()) {
                grill24.fishtastic.client.companion.ClientCompanions.devFollowBoxSide(Integer.parseInt(boxParam));
            }
        });

        // -- Ramp, leg A: walk 3 blocks (0.15 per step =~ 0.075/tick), stop, watch it meander --
        // Row: {gameTime, playerX, playerZ, targetX, targetZ, centreX, centreZ, vX, vZ, gap}
        final java.util.List<double[]> walk = new java.util.ArrayList<>();
        final long[] walkStart = new long[1];
        final long[] walkStop = new long[2];
        queue(1, mc -> {
            startTickProbe(walk);
            walkStart[0] = mc.level.getGameTime();
        });
        final Vec3[] base = new Vec3[1];
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                base[0] = player.position();
                break;
            }
        }));
        for (int i = 1; i <= 20; i++) {
            final int step = i;
            queue(1, mc -> server(mc, s -> run(s, String.format(java.util.Locale.ROOT,
                    "tp @a %.4f %.4f %.4f", base[0].x + 0.15 * step, base[0].y, base[0].z))));
        }
        queue(1, mc -> walkStop[0] = mc.level.getGameTime());
        queue(100, mc -> {
            tickProbe = null; // the probe stayed on through the settle - the meander needs it
            double walkMax = 0, walkGapSum = 0;
            long walkSamples = 0;
            for (double[] r : walk) {
                if (r[0] >= walkStart[0] && r[0] <= walkStop[0]) {
                    walkMax = Math.max(walkMax, r[9]);
                    walkGapSum += r[9];
                    walkSamples++;
                }
            }
            double walkMean = walkSamples == 0 ? -1 : walkGapSum / walkSamples;
            double mMaxSpeed = 0;
            long arrive = -1;
            for (int i = 1; i < walk.size(); i++) {
                double[] r = walk.get(i);
                if (r[0] <= walkStop[0]) continue;
                double dt = r[0] - walk.get(i - 1)[0];
                if (dt < 1.0) continue; // tick-true pairs only (a duplicate row would spike the rate)
                mMaxSpeed = Math.max(mMaxSpeed,
                        Math.hypot(r[5] - walk.get(i - 1)[5], r[6] - walk.get(i - 1)[6]) / dt);
                if (arrive < 0 && r[9] < 0.15) arrive = (long) r[0];
            }
            long meanderTicks = arrive < 0 ? -1 : arrive - walkStop[0];
            Fishtastic.LOGGER.info("[selftest] ramp3.walk meanGap={} maxGap={} meanderTicks={} meanderMaxSpeed={} samples={}",
                    fmt3(walkMean), fmt3(walkMax), meanderTicks, fmt3(mMaxSpeed), walk.size());
            for (int i = 0; i < walk.size(); i += 4) {
                double[] r = walk.get(i);
                Fishtastic.LOGGER.info("[selftest] ramp3.walk.trace t={} gap={} scene={} out={} trick={} peek={}",
                        (long) r[0], fmt3(r[9]), fmt3(r[10]), fmt3(r[11]), (long) r[12], fmt3(r[13]));
            }
            // Mean, not max: the stepped walk's sawtooth makes the window peak sample-phase
            // dependent, while the mean separates the old glue (~0.21) from following (>=0.3)
            // robustly.
            check("ramp3.walkTrails", walkMean >= 0.3, "meanGap=" + fmt3(walkMean) + " maxGap=" + fmt3(walkMax));
            check("ramp3.meanders", meanderTicks >= 20 && mMaxSpeed <= 0.11,
                    "meanderTicks=" + meanderTicks + " maxSpeed=" + fmt3(mMaxSpeed));
        });
        queue(20, mc -> screenshot(mc, "companionpolish3", "meander_home"));

        // -- Ramp, leg B: sprint 8.4 blocks (0.56 per step =~ 0.28/tick), stop, watch catch-up --
        final java.util.List<double[]> sprint = new java.util.ArrayList<>();
        final long[] sprintStart = new long[1];
        final long[] sprintStop = new long[2];
        queue(1, mc -> {
            startTickProbe(sprint);
            sprintStart[0] = mc.level.getGameTime();
        });
        final Vec3[] sBase = new Vec3[1];
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                sBase[0] = player.position();
                break;
            }
        }));
        for (int i = 1; i <= 15; i++) {
            final int step = i;
            queue(1, mc -> server(mc, s -> run(s, String.format(java.util.Locale.ROOT,
                    "tp @a %.4f %.4f %.4f", sBase[0].x + 0.56 * step, sBase[0].y, sBase[0].z))));
        }
        queue(1, mc -> sprintStop[0] = mc.level.getGameTime());
        queue(80, mc -> {
            tickProbe = null;
            double sprintMax = 0, maxAccel = 0, v8 = -1, maxV = 0;
            for (int i = 0; i < sprint.size(); i++) {
                double[] r = sprint.get(i);
                if (r[0] <= sprintStop[0]) sprintMax = Math.max(sprintMax, r[9]);
                if (i < 2) continue;
                double[] q = sprint.get(i - 1);
                double[] o = sprint.get(i - 2);
                if (r[0] - q[0] < 1.0 || q[0] - o[0] < 1.0) continue;
                double v = Math.hypot(r[5] - q[5], r[6] - q[6]) / (r[0] - q[0]);
                double vPrev = Math.hypot(q[5] - o[5], q[6] - o[6]) / (q[0] - o[0]);
                maxV = Math.max(maxV, v);
                if (q[0] <= sprintStop[0]) maxAccel = Math.max(maxAccel, Math.abs(v - vPrev));
                if (v8 < 0 && r[0] >= sprintStart[0] + 8) v8 = v;
            }
            long catchTicks = -1;
            for (double[] r : sprint) {
                if (r[0] > sprintStop[0] && r[9] < 0.6) {
                    catchTicks = (long) r[0] - sprintStop[0];
                    break;
                }
            }
            Fishtastic.LOGGER.info(
                    "[selftest] ramp3.sprint maxGap={} speedAt8t={} maxTickAccel={} catchTicks={} maxSpeedSeen={} samples={}",
                    fmt3(sprintMax), fmt3(v8), fmt3(maxAccel), catchTicks, fmt3(maxV), sprint.size());
            check("ramp3.sprintTrails", sprintMax >= 1.2, "maxGap=" + fmt3(sprintMax));
            check("ramp3.accelBounded", maxAccel >= 0 && maxAccel <= 0.02, "maxTickAccel=" + fmt3(maxAccel));
            check("ramp3.rampStart", v8 >= 0 && v8 <= 0.16, "speedAt8t=" + fmt3(v8));
            check("ramp3.catchesUp", catchTicks >= 0 && catchTicks <= 150, "catchTicks=" + catchTicks);
        });
        queue(20, mc -> screenshot(mc, "companionpolish3", "catch_up"));

        // -- Crossing: the G2 eight, legacy sweep vs fixed, RAW path every tick --
        final java.util.UUID[] phantom = new java.util.UUID[1];
        queue(10, mc -> phantom[0] = grill24.fishtastic.client.companion.ClientCompanions.addPhantom(
                fish("bluegill", 25f), mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2.4))));
        queue(20, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

        final java.util.List<double[]> legacyPath = new java.util.ArrayList<>();
        queue(2, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyEight(true);
            grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                    grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT,
                    mc.player.getUUID(), phantom[0], mc.level.getGameTime());
            startPathProbe(legacyPath);
        });
        // The first G2 crossing is at elapsed 100; a step is 2 ticks, so a step ~46 lands on it.
        queue(46, mc -> screenshot(mc, "companionpolish3", "g2_legacy_crossing"));
        queue(90, mc -> {
            tickProbe = null;
            double legacyMax = maxPathTurnDeg(legacyPath);
            Fishtastic.LOGGER.info("[selftest] crossing.legacy maxTurnDeg={} samples={} top=[{}]",
                    fmt3(legacyMax), legacyPath.size(), topPathTurns(legacyPath, 4));
            check("crossing.legacyReproduces", legacyMax >= 100, "legacyMaxTurnDeg=" + fmt3(legacyMax));
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devStopScenes());

        final java.util.List<double[]> fixedPath = new java.util.ArrayList<>();
        queue(5, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyEight(false);
            grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                    grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT,
                    mc.player.getUUID(), phantom[0], mc.level.getGameTime());
            startPathProbe(fixedPath);
        });
        queue(46, mc -> screenshot(mc, "companionpolish3", "g2_fixed_crossing"));
        queue(90, mc -> {
            tickProbe = null;
            double fixedMax = maxPathTurnDeg(fixedPath);
            Fishtastic.LOGGER.info("[selftest] crossing.fixed maxTurnDeg={} samples={} top=[{}]",
                    fmt3(fixedMax), fixedPath.size(), topPathTurns(fixedPath, 4));
            check("crossing.fixedSmooth", fixedMax >= 0 && fixedMax <= 25, "fixedMaxTurnDeg=" + fmt3(fixedMax));
            check("crossing.samples", fixedPath.size() >= 120, "samples=" + fixedPath.size());
        });
        queue(20, mc -> screenshot(mc, "companionpolish3", "g2_after"));

        // Run-A reproduction (§21 wobble bisect): the exact window the original 1.24-block reading
        // came from — 186 ticks into the fixed-crossing scene, right on the heels of the legacy
        // leg, while the scene's phantom partner may still be travelling back from the whiplash.
        final java.util.List<double[]> wAfterLegacy = new java.util.ArrayList<>();
        queue(30, mc -> startDrawnProbe(wAfterLegacy));   // step 186 = run A's probe start
        queue(60, mc -> tickProbe = null);
        queue(1, mc -> {
            checkWobble("wobble.afterLegacy", wAfterLegacy);
            dumpWobbleTrace(wAfterLegacy, 2);
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devStopScenes());

        // -- Wobble regression (§21): steady-state drawn-position oscillation ------------------
        // The owner's blocker: 1+ block wobbles at >1 Hz. Each leg samples the DRAWN position per
        // tick through a steady state; the check bars the fast component of that path (a centred
        // 19-tick moving-average high-pass, WOB_HALF), i.e. exactly the wobble, not travel.
        // First leg: true idle. Stop the G2 crossing scene still playing from the leg above —
        // the round-3 regression hid behind exactly this: the "idle" probe was mid-scene.
        final java.util.List<double[]> wIdle = new java.util.ArrayList<>();
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devStopScenes());
        queue(60, mc -> startDrawnProbe(wIdle));          // scene blend decayed + swam home
        queue(60, mc -> tickProbe = null);                // ~60 ticks of idle follow
        queue(1, mc -> {
            checkWobble("wobble.idle", wIdle);
            dumpWobbleTrace(wIdle, 2);
        });

        final java.util.List<double[]> wG1 = new java.util.ArrayList<>();
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                    grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES,
                    mc.player.getUUID(), phantom[0], mc.level.getGameTime());
            startDrawnProbe(wG1);                         // from tick 0: blend-in, attack, arrival
        });
        queue(250, mc -> tickProbe = null);               // the whole scene: start, ring, end, return
        queue(1, mc -> {
            // The bar covers the scene BODY: the 19-tick high-pass reads the one-shot blend-in
            // swoop (ticks ~8-20) and the end-of-scene snap (240) as ~0.5-block residuals. Those are
            // single transitions, not the repeating wobble; the whole-scene number is logged below.
            checkWobble("wobble.g1", ticks(wG1, 30, 225));
            logWobble("wobble.g1.whole", wG1);
            dumpWobbleTrace(wG1, 4);
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devStopScenes());

        final java.util.List<double[]> wG2 = new java.util.ArrayList<>();
        queue(40, mc -> {});                              // blend decay + settle home
        queue(1, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.onScenePacket(
                    grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT,
                    mc.player.getUUID(), phantom[0], mc.level.getGameTime());
            startDrawnProbe(wG2);                         // from tick 0: blend-in, attack, arrival
        });
        queue(300, mc -> tickProbe = null);               // the whole scene incl. both crossings + end
        queue(1, mc -> {
            // Body only, as for g1 (the probe stops before G2's end at 320).
            checkWobble("wobble.g2", ticks(wG2, 30, wG2.size()));
            logWobble("wobble.g2.whole", wG2);
            dumpWobbleTrace(wG2, 4);
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devStopScenes());

        final java.util.List<double[]> wPeek = new java.util.ArrayList<>();
        queue(20, mc -> {});                              // resettle after the scene
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devStartPeek());
        queue(35, mc -> startDrawnProbe(wPeek));          // past the travel, inside HOLD(70)
        queue(45, mc -> tickProbe = null);                // ~45 ticks of held peek
        queue(1, mc -> checkWobble("wobble.peek", wPeek));

        // Water follow: a small pool at the origin, the player in it, the fish using the sim.
        final java.util.List<double[]> wWater = new java.util.ArrayList<>();
        queue(1, mc -> server(mc, s -> run(s, String.format(java.util.Locale.ROOT,
                "fill %d %d %d %d %d %d minecraft:water",
                origin.getX() - 2, origin.getY(), origin.getZ() - 2,
                origin.getX() + 2, origin.getY() + 3, origin.getZ() + 2))));
        queue(1, mc -> server(mc, s -> run(s, String.format(java.util.Locale.ROOT,
                "tp @a %.3f %.3f %.3f", origin.getX() + 0.5, (double) origin.getY() + 1, origin.getZ() + 0.5))));
        queue(90, mc -> startDrawnProbe(wWater));         // long settle: the tp home is ~11 blocks
        queue(60, mc -> tickProbe = null);                // ~120 ticks of water follow
        queue(1, mc -> checkWobble("wobble.water", wWater));
        queue(1, mc -> server(mc, s -> {
            run(s, String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
                    origin.getX() - 2, origin.getY(), origin.getZ() - 2,
                    origin.getX() + 2, origin.getY() + 3, origin.getZ() + 2));
            run(s, String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f",
                    origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 0.5));
        }));

        // -- Cleanup -------------------------------------------------------------------------
        queue(1, mc -> {
            tickProbe = null;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            grill24.fishtastic.client.companion.ClientCompanions.devStopScenes();
            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
            grill24.fishtastic.client.companion.ClientCompanions.devSuppressMotion(false);
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyEight(false);
            grill24.fishtastic.client.companion.ClientCompanions.devFeedForward(0.6);
            grill24.fishtastic.client.companion.ClientCompanions.devCompensation(0.7);
            grill24.fishtastic.client.companion.ClientCompanions.devLegacyMomentSteer(false);
        });
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.Companions.unequip(player);
                player.getInventory().clearContent();
                mirrorInventoryToClient(mc, player);
            }
        }));
        queue(10, mc -> companionLog("polish3 scene end"));
    }

    /** §21 probe: one row per client tick - steering state vs the owner and the anchor. */
    private static void startTickProbe(java.util.List<double[]> sink) {
        tickProbe = mc -> {
            if (mc.level == null || mc.player == null) return;
            grill24.fishtastic.client.companion.ClientCompanion c = companion();
            if (c == null || c.devFollowTarget() == null || c.devFollowCenter() == null) return;
            Vec3 p = mc.player.position(), t = c.devFollowTarget(), ct = c.devFollowCenter(), v = c.devFollowVelocity();
            sink.add(new double[]{mc.level.getGameTime(), p.x, p.z, t.x, t.z, ct.x, ct.z, v.x, v.z, c.devFollowGap(),
                    c.devSceneBlend(), c.devOutAmount(), c.devTrickTimer(), c.devPeekBlend()});
        };
    }

    /** §21 probe: one row per client tick - the raw scene-path point. */
    private static void startPathProbe(java.util.List<double[]> sink) {
        tickProbe = mc -> {
            if (mc.level == null) return;
            Vec3 pt = grill24.fishtastic.client.companion.ClientCompanions.devScenePathPoint();
            if (pt != null) sink.add(new double[]{mc.level.getGameTime(), pt.x, pt.y, pt.z});
        };
    }

    /** A Vec3 as "x,y,z" with 3dp, for scene diag dumps. */
    private static String vec3(Vec3 v) {
        return v == null ? "null" : "(" + fmt3(v.x) + "," + fmt3(v.y) + "," + fmt3(v.z) + ")";
    }

    /** §21 wobble bar: steady-state TRACKING error peak-to-peak, blocks (pre-fix: 1.2+±0.6 cycle). */
    private static final double WOB_MAX_P2P = 0.5;
    /**
     * Half-width (ticks) of the wobble high-pass's centred moving average. The defect's band is an
     * 18–36-tick period; the original 4 (a 9-tick window) attenuated it ~3× and PASSed a measured
     * 1.26-block wobble at 0.39. 9 (a 19-tick window) passes that band nearly whole.
     */
    private static final int WOB_HALF = 9;

    /**
     * §21 probe: one row per client tick with the full trace. Columns: 0 time, 1-3 drawn,
     * 4-6 anchor target, 7-9 box centre, 10 frame yaw, 11 owner body yaw, 12-14 sim local
     * position, 15-17 sim local velocity — everything the wobble can come from, so the offline
     * analysis can attribute it rather than guess.
     */
    private static final int TRACE_COLUMNS = 18;

    private static void startDrawnProbe(java.util.List<double[]> sink) {
        tickProbe = mc -> {
            if (mc.level == null || mc.player == null) return;
            grill24.fishtastic.client.companion.ClientCompanion c = companion();
            Vec3 pt = c == null ? null : c.devFishPoint();
            if (pt == null) return;
            Vec3 t = c.devFollowTarget(), ct = c.devFollowCenter();
            if (t == null || ct == null) return;
            float[] sim = c.devSimState();
            Vec3 eye = mc.player.getEyePosition();
            sink.add(new double[]{mc.level.getGameTime(), pt.x, pt.y, pt.z,
                    t.x, t.y, t.z, ct.x, ct.y, ct.z,
                    c.devFrameYaw(), mc.player.yBodyRot,
                    sim[0], sim[1], sim[2], sim[3], sim[4], sim[5],
                    eye.x, eye.y, eye.z,
                    c.devOrbitAngleDeg(), c.devTrickTimer(), c.devTickCount(), c.devPeekBlend(),
                    c.devSceneBlend(), c.devOutAmount(), c.devLineWait()});
        };
    }

    /**
     * §21 wobble metric: the drawn position with its slow component removed — for each axis,
     * the sample minus a centred {@link #WOB_HALF}-wide moving average. A steady path (an orbit, a scene lane,
     * a straight approach) leaves ~zero; a fast oscillation — the owner-visible wobble — is
     * exactly what remains. The bar ({@link #WOB_MAX_P2P}) sits well under the 1+ block the
     * owner reported. The plain drawn p2p and the tracking error are logged for the record:
     * the raw drawn path travels by design, and the tracking error carries the steering lag as
     * a slow rotating vector (it does not show the wobble — see the §21 bisect).
     */
    private static void checkWobble(String name, java.util.List<double[]> rows) {
        if (rows.size() < 30) {
            check(name, false, "rows=" + rows.size());
            return;
        }
        double worst = logWobble(name, rows);
        check(name, worst <= WOB_MAX_P2P, "wobbleP2p=" + fmt3(worst));
    }

    /** Probe rows {@code [from, to)}, clipped to what was sampled. */
    private static java.util.List<double[]> ticks(java.util.List<double[]> rows, int from, int to) {
        int hi = Math.min(to, rows.size());
        return rows.subList(Math.min(from, hi), hi);
    }

    /** {@link #checkWobble}'s measurement without the bar; returns the worst axis residual p2p. */
    private static double logWobble(String name, java.util.List<double[]> rows) {
        if (rows.size() < 2 * WOB_HALF + 2) return Double.NaN;
        int n = rows.size();
        double seconds = Math.max(1e-3, (rows.get(n - 1)[0] - rows.get(0)[0]) / 20.0);
        double worst = 0, zcrHz = 0;
        for (int axis = 1; axis <= 3; axis++) {
            // Only fully-windowed samples: at the edges the centred average degenerates to a
            // one-sided mean and a uniformly moving path leaves a residual in proportion to its
            // speed — measured at up to ~0.2–0.4 for a normal swim.
            int lo = WOB_HALF, hi = n - 1 - WOB_HALF;
            double[] res = new double[hi - lo + 1];
            for (int i = lo; i <= hi; i++) {
                double mean = 0;
                for (int j = i - WOB_HALF; j <= i + WOB_HALF; j++) mean += rows.get(j)[axis];
                res[i - lo] = rows.get(i)[axis] - mean / (2 * WOB_HALF + 1);
            }
            double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
            for (double v : res) {
                mn = Math.min(mn, v);
                mx = Math.max(mx, v);
            }
            worst = Math.max(worst, mx - mn);
            zcrHz = Math.max(zcrHz, zeroCrossingsHzArr(res, seconds));
        }
        double drawnP2p = Math.max(Math.max(p2p(rows, 1), p2p(rows, 2)), p2p(rows, 3));
        double trackErr = rows.get(0).length > 9
                ? Math.max(Math.max(p2pDiff(rows, 7, 4), p2pDiff(rows, 8, 5)), p2pDiff(rows, 9, 6))
                : -1;
        Fishtastic.LOGGER.info("[selftest] {} wobbleP2p={} zcrHz={} trackErr={} drawnP2p={} ticks={}",
                name, fmt3(worst), fmt3(zcrHz), fmt3(trackErr), fmt3(drawnP2p), rows.size());
        return worst;
    }

    /** Dev-only: raw wobble-probe rows, one log line each {@code step} samples. */
    private static void dumpWobbleTrace(java.util.List<double[]> rows, int step) {
        for (int i = 0; i < rows.size(); i += step) {
            double[] w = rows.get(i);
            Fishtastic.LOGGER.info("[selftest] wobble.trace t={} d=({},{},{}) tgt=({},{},{}) c=({},{},{}) "
                            + "frameYaw={} bodyYaw={} sim=({},{},{}) simv=({},{},{}) "
                            + "eye=({},{},{}) orbitDeg={} trick={} ticks={} peek={} scene={} out={} line={}",
                    (long) w[0], fmt3(w[1]), fmt3(w[2]), fmt3(w[3]), fmt3(w[4]), fmt3(w[5]), fmt3(w[6]),
                    fmt3(w[7]), fmt3(w[8]), fmt3(w[9]), fmt3(w[10]), fmt3(w[11]),
                    fmt3(w[12]), fmt3(w[13]), fmt3(w[14]), fmt3(w[15]), fmt3(w[16]), fmt3(w[17]),
                    fmt3(w[18]), fmt3(w[19]), fmt3(w[20]),
                    fmt3(w[21]), (long) w[22], (long) w[23], fmt3(w[24]),
                    fmt3(w[25]), fmt3(w[26]), fmt3(w[27]));
        }
    }

    /** Zero crossings of a raw series about its own mean, as Hz. */
    private static double zeroCrossingsHzArr(double[] vals, double seconds) {
        double mean = 0;
        for (double v : vals) mean += v;
        mean /= Math.max(1, vals.length);
        int cross = 0, sgn = 0;
        for (double v : vals) {
            int s = v - mean > 0 ? 1 : (v - mean < 0 ? -1 : 0);
            if (s != 0) {
                if (sgn != 0 && s != sgn) cross++;
                sgn = s;
            }
        }
        return cross / (2.0 * seconds);
    }

    /** Peak-to-peak of (rows[a] - rows[b]) over the sampled rows. */
    private static double p2pDiff(java.util.List<double[]> rows, int a, int b) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] r : rows) {
            double v = r[a] - r[b];
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        return hi - lo;
    }

    /** Zero crossings (Hz) of (rows[a] - rows[b]) around its own mean. */
    private static double zeroCrossingsHzDiff(java.util.List<double[]> rows, int a, int b, double seconds) {
        double mean = 0;
        for (double[] r : rows) mean += r[a] - r[b];
        mean /= rows.size();
        int crossings = 0, sign = 0;
        for (double[] r : rows) {
            double d = (r[a] - r[b]) - mean;
            int s = d > 0 ? 1 : d < 0 ? -1 : 0;
            if (s != 0 && sign != 0 && s != sign) crossings++;
            if (s != 0) sign = s;
        }
        return crossings / (2.0 * seconds);
    }

    /** Peak-to-peak of axis {@code axis} (1=x, 2=y, 3=z) over the sampled rows. */
    private static double p2p(java.util.List<double[]> rows, int axis) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] r : rows) {
            lo = Math.min(lo, r[axis]);
            hi = Math.max(hi, r[axis]);
        }
        return hi - lo;
    }

    /** Zero crossings of the axis deviation from its own mean, in Hz. */
    private static double zeroCrossingsHz(java.util.List<double[]> rows, int axis, double seconds) {
        double mean = 0;
        for (double[] r : rows) mean += r[axis];
        mean /= rows.size();
        int crossings = 0;
        int sign = 0;
        for (double[] r : rows) {
            double d = r[axis] - mean;
            int s = d > 0 ? 1 : d < 0 ? -1 : 0;
            if (s != 0 && sign != 0 && s != sign) crossings++;
            if (s != 0) sign = s;
        }
        return crossings / (2.0 * seconds);
    }

    /** {@code %.3f} for scene logs, locale-safe. */
    private static String fmt3(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /** The largest per-tick direction change (degrees) between consecutive path deltas (§21). */
    private static double maxPathTurnDeg(java.util.List<double[]> traj) {
        double max = 0;
        for (double[] turn : pathTurns(traj)) {
            max = Math.max(max, turn[1]);
        }
        return max;
    }

    /** The largest {@code n} turns as "i=idx:deg" strings, largest first. */
    private static String topPathTurns(java.util.List<double[]> traj, int n) {
        java.util.List<double[]> turns = pathTurns(traj);
        turns.sort((a, b) -> Double.compare(b[1], a[1]));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(n, turns.size()); i++) {
            if (i > 0) out.append(' ');
            out.append(String.format(java.util.Locale.ROOT, "i=%.0f:%.1f", turns.get(i)[0], turns.get(i)[1]));
        }
        return out.toString();
    }

    /** Each valid consecutive-delta turn as {sample index, angle in degrees}, per-tick rows. */
    private static java.util.List<double[]> pathTurns(java.util.List<double[]> traj) {
        java.util.List<double[]> turns = new java.util.ArrayList<>();
        for (int i = 1; i + 1 < traj.size(); i++) {
            double[] p0 = traj.get(i - 1), p1 = traj.get(i), p2 = traj.get(i + 1);
            if (p1[0] - p0[0] < 1.0 || p2[0] - p1[0] < 1.0) continue; // tick-true pairs only
            double[] d1 = {p1[1] - p0[1], p1[2] - p0[2], p1[3] - p0[3]};
            double[] d2 = {p2[1] - p1[1], p2[2] - p1[2], p2[3] - p1[3]};
            double l1 = Math.sqrt(d1[0] * d1[0] + d1[1] * d1[1] + d1[2] * d1[2]);
            double l2 = Math.sqrt(d2[0] * d2[0] + d2[1] * d2[1] + d2[2] * d2[2]);
            if (l1 < 1e-5 || l2 < 1e-5) continue;
            double cos = Math.max(-1.0, Math.min(1.0, (d1[0] * d2[0] + d1[1] * d2[1] + d1[2] * d2[2]) / (l1 * l2)));
            turns.add(new double[]{i, Math.toDegrees(Math.acos(cos))});
        }
        return turns;
    }

    private static void queueHudScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            mc.getToastManager().clear();
            server(mc, s -> run(s, "gamemode creative @a"));
            QuestProgressNotificationManager.getInstance().enqueue(new QuestProgressEvent(
                    Ids.of("fishtastic", "mastery/bluegill_novice"), 2, 3, 5, false, ItemStack.EMPTY));
            SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.literal("Vanilla toast"),
                    Component.literal("shares the banner's corner"));
        });
        // Two shots: the banner's slide-in is staggered (QuestProgressNotificationManager's
        // nextStaggerDelay), so the first can catch it part-way across.
        queue(20, mc -> screenshot(mc, "hud", "banner_and_toast"));
        queue(20, mc -> {
            check("hud.noScreen", mc.screen == null, "screen=" + mc.screen);
            check("hud.toastOnScreen",
                    mc.getToastManager().getToast(SystemToast.class, SystemToast.SystemToastId.NARRATOR_TOGGLE) != null,
                    "");
            screenshot(mc, "hud", "banner_and_toast_late");
        });
        // No fresh banner is needed for the second reading: the one enqueued above is still up (a
        // 15-tick slide-in then a 60-tick hold, and nothing here moves the manager off 1x).
        queue(1, mc -> mc.setScreen(new ChatScreen("", false)));
        queue(2, mc -> {
            check("hud.withScreen", mc.screen != null, "screen=" + mc.screen);
            screenshot(mc, "hud", "banner_with_screen_toast");
        });
        // One step, then the shot: a capture is of the frame *before* the step that asked for it ran
        // (Screenshot.grab reads the back buffer mid-frame), so clearing and shooting in the same
        // step photographed the toast twice on the first run of this half.
        queue(2, mc -> mc.getToastManager().clear());
        queue(2, mc -> screenshot(mc, "hud", "banner_with_screen"));
        queue(1, mc -> {
            mc.setScreen(null);
            mc.getToastManager().clear();
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
            mc.setScreen(new InventoryScreen(mc.player));
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
     * The cosmetic-capture wand's selection boxes: starts a session with the real command, makes
     * the three picks the wand's right-clicks would make, re-sends the session, then cancels it
     * (the clear packet must take the boxes away again).
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
                            imageA.getPixel(x0 + x, y0 + y), imageB.getPixel(x0 + x, y0 + y)) > PERTANK_PIXEL_DELTA;
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

    /** Largest per-channel difference between two ARGB pixels ({@code NativeImage.getPixel}). */
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
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", species)));
        ItemSizeHelper.setSize(stack, sizeCm);
        return stack;
    }

    /** Puts the (spectator) player's eye at an absolute position and view ({@code tp} places the feet). */
    private static void camera(Minecraft mc, double x, double eyeY, double z, float yaw, float pitch) {
        double feetY = eyeY - mc.player.getEyeHeight();
        server(mc, s -> {
            run(s, "gamemode spectator @a");
            run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f", x, feetY, z, yaw, pitch));
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

    private static boolean atlasTextureRegistered(Identifier id) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        return texture != null && texture.getTexture() != null;
    }

    /** Opaque frame of the main target (Screenshot forces alpha to 1). */
    static void screenshot(Minecraft mc, String scene, String shot) {
        Screenshot.grab(mc.gameDirectory, fileName(scene, shot), mc.getMainRenderTarget(), 1,
                msg -> Fishtastic.LOGGER.info("[selftest] {}", msg.getString()));
    }

    /** Colour attachment of {@code target} with its alpha intact, for anything alpha matters for. */
    static void dump(Minecraft mc, RenderTarget target, String scene, String shot) {
        if (target == null) {
            check("dump." + scene + "." + shot, false, "target not created");
            return;
        }
        dumpTexture(mc, target.getColorTexture(), scene, shot);
    }

    /**
     * Reads a GPU texture back into a PNG with its alpha intact — the byte-for-byte port of the
     * 1.21.1 {@code dump}, which existed because {@code Screenshot} forces alpha opaque (see
     * {@code Screenshot.takeScreenshot}, whose {@code argb | 0xFF000000} this deliberately omits).
     * Written against the same buffer/encoder calls that method uses, since 26.1.2's
     * {@code NativeImage} no longer has a {@code downloadTexture} of its own.
     *
     * <p>The texture must be created with {@code USAGE_COPY_SRC}; render targets are, the outline
     * atlases are not (see the class javadoc).
     */
    static void dumpTexture(Minecraft mc, GpuTexture source, String scene, String shot) {
        if (source == null) {
            check("dump." + scene + "." + shot, false, "texture not created");
            return;
        }
        int width = source.getWidth(0);
        int height = source.getHeight(0);
        int pixelSize = source.getFormat().pixelSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "selftest dump buffer",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) width * height * pixelSize);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(source, buffer, 0L, () -> {
            try (GpuBuffer.MappedView read = encoder.mapBuffer(buffer, true, false);
                 NativeImage image = new NativeImage(width, height, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixelABGR(x, height - y - 1, read.data().getInt((x + y * width) * pixelSize));
                    }
                }
                File out = new File(new File(mc.gameDirectory, "screenshots"), fileName(scene, shot));
                out.getParentFile().mkdirs();
                image.writeToFile(out);
                Fishtastic.LOGGER.info("[selftest] wrote {}", out);
            } catch (IOException e) {
                Fishtastic.LOGGER.error("[selftest] failed to write {}", shot, e);
            }
            buffer.close();
        }, 0);
    }

    private RenderSelfTest() {}
}
