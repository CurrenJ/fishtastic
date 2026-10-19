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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.client.QuestLogScreen;
import grill24.fishtastic.client.FishEncyclopediaScreen;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.ambient.AmbientContextClient;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.network.AmbientContextResponsePacket;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.world.phys.AABB;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.io.FileInputStream;
import java.io.InputStream;
import net.minecraft.client.CameraType;
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
    // PORT-ONLY: 26.1.2's cool-cam and MCP-bridge scenes (matte, matteclip, fourwaters, cathedral,
    // mcpsession, cosmeticpreview) are not ported - neither mod side exists on this branch (D4).
    private static final List<String> OPT_IN_SCENES = List.of("columns", "spanpreview", "perfbench", "lighting", "bakebench", "scalebench", "leaderboard", "tutorial", "companiontutorial", "companionbond", "companionsim", "companionparty", "companioneel", "twoclient", "companionscenes", "companionlod", "companionmoods", "companionworld", "companionparticles", "companionworldcost", "ambientparity", "ambientwater", "ambientcontext", "ambientpopulation", "ambientlifecycle", "ambientisland", "ambientfloor", "ambientwalk", "celebrationoverlap");

    private static Boolean armed;
    /** Dev probe (§21): runs every client tick while non-null — tick-true sampling. */
    private static java.util.function.Consumer<Minecraft> tickProbe;
    private static Set<String> scenes;
    /** {@code key=value} marker lines, for scenes that take arguments; a key may repeat. */
    private static final java.util.Map<String, List<String>> PARAMS = new java.util.HashMap<>();
    private static String loader;
    private static boolean worldRequested;
    /** {@code server=host:port} marker param: join that dedicated server instead of creating the flat world. */
    private static String remoteServer;
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
            grill24.fishtastic.client.ambient.AmbientFishRuntime.setInvariantStats(true);
            grill24.fishtastic.client.ambient.ClientAmbientFish.harnessActive(true);
        }
        if (!armed) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        // §21 probe: sampled every client tick while set (the step queue advances one step per
        // TWO ticks, so per-step samples can't measure per-tick rates).
        if (tickProbe != null) tickProbe.accept(mc);

        if (!worldRequested) {
            if (mc.screen instanceof TitleScreen && ++titleTicks > 40) {
                worldRequested = true;
                remoteServer = param("server", null);
                if (remoteServer == null) createWorld(mc); else connectToServer(mc, remoteServer);
            }
            return;
        }
        if (mc.level == null || mc.player == null) return;
        // A joined client has no integrated server; its scenes must not reach for one (server()).
        if (remoteServer == null && mc.getSingleplayerServer() == null) return;

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

    /** Opt-in multiplayer: {@code server=host:port} in the marker joins that server from the title screen. */
    private static void connectToServer(Minecraft mc, String hostPort) {
        net.minecraft.client.multiplayer.resolver.ServerAddress address =
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(hostPort);
        if (address == null || address.getHost().isEmpty() || address.getHost().equals("server.invalid")) {
            Fishtastic.LOGGER.error("[selftest] cannot parse server address '{}'", hostPort);
            return;
        }
        Fishtastic.LOGGER.info("[selftest] joining {}", hostPort);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc, address,
                new net.minecraft.client.multiplayer.ServerData("selftest", hostPort, false),
                false);
    }

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
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
        if (remoteServer == null) {
            server(mc, s -> {
                run(s, "gamerule sendCommandFeedback false");
                run(s, "time set 6000");
            });
        } else {
            // A joined client holds no integrated server; the dedicated server owns the clock and weather.
            note("joined " + remoteServer + ": quiet-world rules left to the server");
        }
        check("shaders.loaded", FishtasticShaders.outlineBake != null && FishtasticShaders.outlineBakeLegendary != null,
                "outline_bake=" + FishtasticShaders.outlineBake + " legendary=" + FishtasticShaders.outlineBakeLegendary);
        Fishtastic.LOGGER.info("[selftest] origin {} (gui scale {}, window {}x{})", origin,
                mc.getWindow().getGuiScale(), mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    private static void finish(Minecraft mc) {
        grill24.fishtastic.client.ambient.ClientAmbientFish.harnessActive(false);
        grill24.fishtastic.client.ambient.AmbientFishRuntime.setInvariantStats(false);
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
            case "celebrationoverlap" -> queueCelebrationOverlapScene();
            case "gizmos" -> queueGizmosScene();
            case "pertank" -> queuePerTankScene();
            case "highlight" -> queueHighlightScene();
            case "leaderboard" -> queueLeaderboardScene();
            case "tutorial" -> queueTutorialScene();
            case "companiontutorial" -> queueCompanionTutorialScene();
            case "columns" -> queueColumnsScene();
            case "spanpreview" -> queueSpanPreviewScene();
            case "perfbench" -> queuePerfBenchScene();
            case "bakebench" -> queueBakeBenchScene();
            case "scalebench" -> queueScaleBenchScene();
            case "lighting" -> queueLightingScene();
            case "companionbond" -> queueCompanionBondScene();
            case "companionsim" -> queueCompanionSimScene();
            case "companionparty" -> queueCompanionPartyScene();
            case "companioneel" -> queueCompanionEelScene();
            case "twoclient" -> queueTwoClientScene();
            case "companionscenes" -> queueCompanionScenesScene();
            case "companionlod" -> queueCompanionLodScene();
            case "companionmoods" -> queueCompanionMoodsScene();
            case "companionworld" -> queueCompanionWorldScene();
            case "companionparticles" -> queueCompanionParticlesScene();
            case "companionworldcost" -> queueCompanionWorldCostScene();
            case "ambientparity" -> queueAmbientParityScene();
            case "ambientwater" -> queueAmbientWaterScene();
            case "ambientcontext" -> queueAmbientContextScene();
            case "ambientpopulation" -> queueAmbientPopulationScene();
            case "ambientlifecycle" -> queueAmbientLifecycleScene();
            case "ambientisland" -> queueAmbientIslandScene();
            case "ambientfloor" -> queueAmbientFloorScene();
            case "ambientwalk" -> queueAmbientWalkScene();
            default -> throw new IllegalArgumentException(scene);
        }
    }

    /** Matched 4x2x3 tank/water volumes and recorded sizes; compare normal shore and submerged views. */
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

    private static void queueAmbientParityScene() {
        AmbientParityFixture[] fixture = new AmbientParityFixture[1];
        BlockPos[] water = new BlockPos[1], tanks = new BlockPos[1];
        long[] beforeAway = new long[1];
        queue(1, mc -> {
            tanks[0] = origin.offset(-5, 0, -7);
            water[0] = origin.offset(1, 0, -7);
            server(mc, server -> {
                BlockPos min = tanks[0];
                run(server, "fill " + (min.getX()-1) + " " + min.getY() + " " + (min.getZ()-1) + " "
                        + (water[0].getX()+4) + " " + (min.getY()+4) + " " + (min.getZ()+4) + " minecraft:air");
                placeSpanBox(server, min, new grill24.fishtastic.fishtank.CosmeticStructure.Span(4,2,3));
                BlockPos w = water[0];
                run(server, "fill " + (w.getX()-1) + " " + (w.getY()-1) + " " + (w.getZ()-1) + " "
                        + (w.getX()+4) + " " + (w.getY()+1) + " " + (w.getZ()+3) + " minecraft:glass");
                run(server, "fill " + w.getX() + " " + (w.getY()-1) + " " + w.getZ() + " "
                        + (w.getX()+3) + " " + (w.getY()-1) + " " + (w.getZ()+2) + " minecraft:sand");
                run(server, "fill " + w.getX() + " " + w.getY() + " " + w.getZ() + " "
                        + (w.getX()+3) + " " + (w.getY()+1) + " " + (w.getZ()+2) + " minecraft:water");
                for (int i=0;i<4;i++) {
                    FishTankBlockEntity tank = tank(server.overworld(), min.offset(i,0,0));
                    check("ambientparity.tank.add."+i, tank != null && tank.addItem(fish(
                            AmbientParityFixture.SPECIES[i],
                            AmbientParityFixture.SIZE_CM[i])), "");
                }
            });
        });
        queue(40, mc -> {
            mc.options.hideGui = true;
            fixture[0] = AmbientParityFixture.create(water[0]);
            check("ambientparity.fixture.started", ClientAmbientFish.useDevelopmentView(fixture[0]),
                    "origin="+water[0]);
            camera(mc, origin.getX(), origin.getY()+3.4, origin.getZ()+1.5, 180f, 16f);
        });
        queue(100, mc -> {
            check("ambientparity.fixture.count", fixture[0].count()==4, "four fixed individuals");
            check("ambientparity.fixture.submits", grill24.fishtastic.client.ambient.ClientAmbientFish.worldSubmitsThisFrame==4,
                    "submits="+grill24.fishtastic.client.ambient.ClientAmbientFish.worldSubmitsThisFrame);
            screenshot(mc, "ambientparity", "shore_pair");
        });
        queue(1, mc -> camera(mc, water[0].getX()+2, water[0].getY()+0.9, water[0].getZ()+2.65, 180f, 5f));
        queue(40, mc -> screenshot(mc, "ambientparity", "world_submerged"));
        queue(1, mc -> camera(mc, tanks[0].getX()+2, tanks[0].getY()+0.9, tanks[0].getZ()+2.65, 180f, 5f));
        queue(40, mc -> screenshot(mc, "ambientparity", "tank_inside"));
        queue(1, mc -> {
            camera(mc, water[0].getX()+2, water[0].getY()+0.9, water[0].getZ()+2.65, 0f, 0f);
            beforeAway[0] = fixture[0].steps();
        });
        queue(40, mc -> {
            check("ambientparity.fixture.offcamera", fixture[0].steps()>beforeAway[0]
                            && water[0].equals(fixture[0].origin())
                            && fixture[0].identity(0)==1L,
                    "off-camera simulation retains fixed origin and identity");
            // [1.20.1] no /tick freeze (1.20.3+): the fixture keeps stepping, so there is no
            // freeze check on this target; the close-ups below photograph live poses.
            note("ambientparity.fixture.freeze skipped: no /tick freeze on 1.20.1");
        });
        // World time and both simulations are frozen: photograph every species closely enough
        // to assess its sprite/scale/pose, without requiring lucky positions in a wide camera.
        for (int index = 0; index < 4; index++) {
            final int i = index;
            queue(1, mc -> {
                var pose = fixture[0].fixturePose(i, 0f);
                if (pose != null) parityCloseCamera(mc, pose.position(), pose.yaw(), water[0]);
            });
            queue(20, mc -> screenshot(mc, "ambientparity", "world_" + AmbientParityFixture.SPECIES[i]));
            queue(1, mc -> {
                if (!(mc.level.getBlockEntity(tanks[0]) instanceof FishTankBlockEntity tank)) return;
                TankGroupFlock group = ClientTankFlocks.getOrCreate(tank, tanks[0].hashCode()).groupFlock();
                if (group == null) return;
                ItemStack fish = fish(AmbientParityFixture.SPECIES[i],
                        AmbientParityFixture.SIZE_CM[i]);
                int species = BuiltInRegistries.ITEM.getId(fish.getItem());
                for (int j = 0; j < group.count(); j++) {
                    if (group.fishSpecies(j) != species) continue;
                    var anim = grill24.fishtastic.client.renderer.FishVisuals.resolve(fish, mc.level).animation();
                    var pose = group.fishPose(j, mc.level.getGameTime());
                    parityCloseCamera(mc, pose.position(), pose.yaw(), tanks[0]);
                    check("ambientparity.tank.motion." + i,
                            pose.locomotion() == grill24.fishtastic.client.renderer.FishVisuals.locomotionOf(anim),
                            "mode=" + pose.locomotion() + " length=" + pose.length());
                    return;
                }
                check("ambientparity.tank.motion." + i, false, "species missing from group engine");
            });
            queue(20, mc -> screenshot(mc, "ambientparity", "tank_" + AmbientParityFixture.SPECIES[i]));
        }
        queue(1, mc -> {
            server(mc, server -> run(server, "tick unfreeze"));
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            check("ambientparity.fixture.cleanup", !ClientAmbientFish.hasDevelopmentView(), "");
        });
    }

    private static void parityCloseCamera(Minecraft mc, Vec3 target, float yaw, BlockPos min) {
        double radians = Math.toRadians(yaw), nx = Math.sin(radians), nz = Math.cos(radians);
        double plus = parityCameraRoom(target, nx, nz, min), minus = parityCameraRoom(target, -nx, -nz, min);
        if (minus > plus) { nx = -nx; nz = -nz; plus = minus; }
        double distance = Math.min(1.2, Math.max(0.3, plus - 0.05));
        double x = Math.max(min.getX()+0.25, Math.min(min.getX()+3.75, target.x + nx * distance));
        double z = Math.max(min.getZ()+0.25, Math.min(min.getZ()+2.75, target.z + nz * distance));
        double eyeY = Math.min(min.getY()+1.75, target.y+0.65);
        distance = Math.hypot(x-target.x, z-target.z);
        float cameraYaw = (float)Math.toDegrees(Math.atan2(x-target.x, target.z-z));
        float pitch = (float)Math.toDegrees(Math.atan2(eyeY-target.y, distance));
        camera(mc, x, eyeY, z, cameraYaw, pitch);
    }

    /** Pick the inward broadside view so corner posts cannot obscure the specimen. */
    private static double parityCameraRoom(Vec3 target, double nx, double nz, BlockPos min) {
        double x = Math.abs(nx) < 1e-5 ? Double.POSITIVE_INFINITY
                : ((nx > 0 ? min.getX()+3.75 : min.getX()+0.25)-target.x)/nx;
        double z = Math.abs(nz) < 1e-5 ? Double.POSITIVE_INFINITY
                : ((nz > 0 ? min.getZ()+2.75 : min.getZ()+0.25)-target.z)/nz;
        return Math.min(x,z);
    }

    // ── Phase 2: natural water fixtures ──────────────────────────────────────

    /**
     * Phase 2: five natural-water fixtures, each built where the player is standing and each far
     * enough from the rest that only its own water is ever inside the twenty-four block discovery
     * radius. A river whose far end runs past the radius, an open ocean basin with a mound, a
     * spire and a shelf, a shallow pond whose banks are all well inside, an irregular floor of
     * shelves, a bottom slab, a stair, a waterlogged fence post and a dry island, and two pools
     * separated by more than the gap that would merge them.
     *
     * <p>Each fixture is built <em>after</em> the player is in it — the fill command needs its
     * chunks — then left to discover and populate before the checks read the live runtime, and
     * photographed. Each one restarts the runtime so its columns cannot crowd the next fixture's
     * discovery budget; continuity within a fixture is still exercised, because the runtime rebuilds
     * every twenty ticks and the carry check reads positions across two rebuilds.
     *
     * <p>Every shot is an ordinary world screenshot: natural fish go through the same world
     * pipelines as the tank population, with no capture-side adapter.
     */
    private static void queueAmbientWaterScene() {
        final int gy = origin.getY();
        final int riverX = origin.getX() - 6, riverZ = origin.getZ() + 60;
        final int oceanX = origin.getX() + 80, oceanZ = origin.getZ();
        final int pondX = origin.getX(), pondZ = origin.getZ() - 60;
        final int irrX = origin.getX() + 80, irrZ = origin.getZ() - 60;
        // poolAX/poolZ are snapped to the centre of a 16-block patch quantum so both 7x7 pools sit
        // wholly inside one quantum. The fixture origin moves with the generated world, and a pool
        // that straddles a quantum boundary seeds its patch from one quantum's column while the
        // trip's return pose reads another's, so the returning water mints a fresh key and the
        // reserved one never revives — measured on two runs before this was pinned.
        final int poolAX = Math.floorDiv(origin.getX() + 44, 16) * 16 + 8;
        final int poolBX = poolAX + 16, poolZ = Math.floorDiv(origin.getZ() + 90, 16) * 16 + 8;

        // ── river: two deep, sixty-one long; west end behind the player, east end past the radius
        queue(2, mc -> camera(mc, riverX + 3.5, gy - 0.2, riverZ + 0.5, -90f, 2f));
        queue(4, mc -> ambientWaterStart("river"));
        queue(4, mc -> server(mc, s -> {
            fill(s, riverX, gy - 2, riverZ - 4, riverX + 60, gy - 1, riverZ + 4, "minecraft:air");
            fill(s, riverX, gy - 2, riverZ - 4, riverX + 60, gy - 1, riverZ + 4, "minecraft:water keep");
        }));
        queue(1, mc -> noteState("river t1"));
        queue(2, mc -> noteState("river t3"));
        queue(7, mc -> noteState("river t10"));
        queue(30, mc -> noteState("river t40"));
        waitFor(5, 900, mc -> ambientWaterReady(4, true),
                mc -> ambientWaterFixture("river", 1, 4, 1, true));
        queue(1, mc -> camera(mc, riverX - 8.5, gy + 1.65, riverZ + 0.5, -90f, 14f));
        queue(3, mc -> screenshot(mc, "ambientwater", "river_bank"));
        queue(1, mc -> camera(mc, riverX + 8.5, gy - 0.2, riverZ + 0.5, -90f, 2f));
        queue(3, mc -> screenshot(mc, "ambientwater", "river_submerged"));

        // ── ocean: 49x49, three deep, open on every side and under the surface, with a spire
        queue(2, mc -> camera(mc, oceanX + 0.5, gy - 0.2, oceanZ + 0.5, -90f, 6f));
        queue(4, mc -> ambientWaterStart("ocean"));
        queue(4, mc -> server(mc, s -> {
            fill(s, oceanX - 24, gy - 3, oceanZ - 24, oceanX + 24, gy - 1, oceanZ + 24, "minecraft:air");
            // This is the world's lowest layer; a gravity-affected sand floor falls into the void.
            fill(s, oceanX - 24, gy - 4, oceanZ - 24, oceanX + 24, gy - 4, oceanZ + 24, "minecraft:sandstone");
            fill(s, oceanX - 7, gy - 3, oceanZ - 7, oceanX - 5, gy - 3, oceanZ - 5, "minecraft:sand");
            fill(s, oceanX + 6, gy - 3, oceanZ + 6, oceanX + 6, gy - 2, oceanZ + 6, "minecraft:stone");
            fill(s, oceanX - 24, gy - 3, oceanZ - 24, oceanX + 24, gy - 1, oceanZ + 24, "minecraft:water keep");
        }));
        waitFor(5, 900, mc -> ambientWaterReady(6, true),
                mc -> ambientWaterFixture("ocean", 1, 6, 1, true));
        queueAmbientCarryCheck();
        // The closeups run first — their polls jump the queue — so the ocean sees the same spawn
        // rotation the scene had before the band watch existed; the watch only needs the ocean's
        // water and its key, which the closeups do not touch.
        queue(1, mc -> queueSpeciesCloseup(0, 0, new java.util.LinkedHashSet<>()));
        queueAmbientBandWatchCheck(oceanX, gy, oceanZ);
        queue(1, mc -> camera(mc, oceanX + 0.5, gy + 8.0, oceanZ + 0.5, -90f, 55f));
        queue(3, mc -> screenshot(mc, "ambientwater", "ocean_from_above"));
        queue(1, mc -> camera(mc, oceanX + 11.5, gy - 2.6, oceanZ + 11.5, 135f, 6f));
        queue(3, mc -> screenshot(mc, "ambientwater", "ocean_floor"));

        // ── pond: one to three deep, banks four blocks out, every face closed
        queue(2, mc -> camera(mc, pondX - 6.5, gy + 0.7, pondZ + 0.5, -90f, 12f));
        queue(4, mc -> ambientWaterStart("pond"));
        queue(4, mc -> server(mc, s -> {
            fill(s, pondX - 4, gy - 2, pondZ - 4, pondX + 4, gy - 1, pondZ + 4, "minecraft:air");
            fill(s, pondX - 2, gy - 3, pondZ - 2, pondX + 2, gy - 3, pondZ + 2, "minecraft:air");
            fill(s, pondX - 4, gy - 2, pondZ - 4, pondX + 4, gy - 2, pondZ + 4, "minecraft:sand");
            fill(s, pondX - 2, gy - 2, pondZ - 2, pondX + 2, gy - 2, pondZ + 2, "minecraft:air");
            fill(s, pondX - 2, gy - 3, pondZ - 2, pondX + 2, gy - 3, pondZ + 2, "minecraft:sand");
            fill(s, pondX - 4, gy - 2, pondZ - 4, pondX + 4, gy - 1, pondZ + 4, "minecraft:water keep");
        }));
        queue(1, mc -> {
            noteCell("pond rim", pondX + 3, gy - 1, pondZ);
            noteCell("pond middle top", pondX, gy - 1, pondZ);
            noteCell("pond middle low", pondX, gy - 2, pondZ);
            noteState("pond t1");
        });
        queue(9, mc -> noteState("pond t10"));
        queue(140, mc -> ambientWaterFixture("pond", 1, 2, 1, false));
        queue(1, mc -> camera(mc, pondX - 8.5, gy + 1.65, pondZ + 0.5, -90f, 20f));
        queue(3, mc -> screenshot(mc, "ambientwater", "pond_shore"));
        queue(1, mc -> camera(mc, pondX + 0.5, gy + 3.2, pondZ + 0.5, -90f, 62f));
        queue(3, mc -> screenshot(mc, "ambientwater", "pond_top"));

        // ── irregular floor: sunken corner, shelves, dry island, slab, stair, waterlogged fence
        queue(2, mc -> camera(mc, irrX + 0.5, gy - 0.2, irrZ + 0.5, -90f, 4f));
        queue(4, mc -> ambientWaterStart("irregular"));
        queue(4, mc -> server(mc, s -> {
            fill(s, irrX - 6, gy - 3, irrZ - 6, irrX + 6, gy - 1, irrZ + 6, "minecraft:air");
            fill(s, irrX - 6, gy - 3, irrZ - 6, irrX + 6, gy - 3, irrZ + 6, "minecraft:sand");
            fill(s, irrX - 6, gy - 3, irrZ - 6, irrX - 3, gy - 3, irrZ - 3, "minecraft:air");
            fill(s, irrX + 2, gy - 2, irrZ - 2, irrX + 5, gy - 2, irrZ + 1, "minecraft:sand");
            fill(s, irrX + 4, gy - 2, irrZ + 3, irrX + 5, gy - 1, irrZ + 4, "minecraft:sand");
            fill(s, irrX - 4, gy - 3, irrZ + 4, irrX - 4, gy - 2, irrZ + 4, "minecraft:stone");
            run(s, "setblock " + (irrX - 2) + " " + (gy - 3) + " " + (irrZ + 2) + " minecraft:oak_slab[type=bottom,waterlogged=true]");
            run(s, "setblock " + (irrX - 1) + " " + (gy - 3) + " " + (irrZ + 2) + " minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]");
            run(s, "setblock " + irrX + " " + (gy - 3) + " " + (irrZ + 2) + " minecraft:oak_fence[waterlogged=true]");
            fill(s, irrX - 6, gy - 3, irrZ - 6, irrX + 6, gy - 1, irrZ + 6, "minecraft:water keep");
        }));
        queue(150, mc -> ambientWaterFixture("irregular", 1, 3, 1, false));
        queue(1, mc -> camera(mc, irrX + 0.5, gy + 4.5, irrZ + 0.5, -90f, 55f));
        queue(3, mc -> screenshot(mc, "ambientwater", "floor_plan"));
        queue(1, mc -> camera(mc, irrX + 1.5, gy - 0.5, irrZ - 4.5, 0f, 8f));
        queue(3, mc -> screenshot(mc, "ambientwater", "floor_close"));

        // ── disconnected pools: sixteen apart, ten columns of dry gap between them
        queue(2, mc -> camera(mc, poolAX + 8.5, gy + 0.7, poolZ + 0.5, -90f, 10f));
        queue(4, mc -> ambientWaterStart("pools"));
        queue(4, mc -> server(mc, s -> {
            for (int poolX : new int[]{poolAX, poolBX}) {
                fill(s, poolX - 3, gy - 3, poolZ - 3, poolX + 3, gy - 1, poolZ + 3, "minecraft:air");
                fill(s, poolX - 3, gy - 3, poolZ - 3, poolX + 3, gy - 3, poolZ + 3, "minecraft:sand");
                fill(s, poolX - 3, gy - 3, poolZ - 3, poolX + 3, gy - 1, poolZ + 3, "minecraft:water keep");
            }
        }));
        queue(1, mc -> {
            noteCell("poolA top", poolAX, gy - 1, poolZ);
            noteCell("poolA low", poolAX, gy - 2, poolZ);
            noteState("pools t1");
        });
        queue(9, mc -> noteState("pools t10"));
        queue(140, mc -> ambientWaterFixture("pools", 2, 4, 2, false));
        queue(1, mc -> camera(mc, poolAX + 8.5, gy + 4.0, poolZ + 0.5, -90f, 50f));
        queue(3, mc -> screenshot(mc, "ambientwater", "pools_gap"));
        queue(1, mc -> camera(mc, poolAX + 0.5, gy - 0.2, poolZ + 0.5, -90f, 2f));
        queue(3, mc -> screenshot(mc, "ambientwater", "pools_inside"));

        // ── the window leaves one pool and comes back: eviction drops its water, its residents
        //    fade, and then the same key, the same frame and every one of them return. The pool is
        //    the fixture to do this on because its columns all sit in one patch quantum (see the
        //    poolZ note above), so the returning key is the pool's own key by construction, not by
        //    luck of where the sweep re-entered; the pond straddles a quantum boundary and would
        //    make this a coin toss.
        final java.util.Set<Long> tripIds = new java.util.LinkedHashSet<>();
        final long[] tripKey = {0L};
        final int[] tripColumns = new int[1];
        final int[] tripOrigin = new int[3];
        final long[] tripLost = new long[6]; // domainsLost, retireDomainLost, outsideWater, retireInvalid, retireExcluded, retireExpired
        final long[] tripRevivals = {0L};
        queue(1, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            tripIds.clear();
            tripKey[0] = 0L;
            double best = Double.MAX_VALUE;
            BlockPos player = Minecraft.getInstance().player.blockPosition();
            for (var p : runtime.diagnostics().individuals()) {
                double d = Math.hypot(p.x() - (player.getX() + 0.5), p.z() - (player.getZ() + 0.5));
                if (d < best) {
                    best = d;
                    tripKey[0] = p.patchKey();
                }
            }
            int fading = 0;
            for (var p : runtime.diagnostics().individuals()) {
                if (p.patchKey() != tripKey[0]) continue;
                // Only residents that are actually swimming: one already mid-fade — an INVALID
                // retirement from the fixture's own rebuilds — can be gone before the window's
                // revival would reach it, and saving it is not what this trip is about.
                if (p.retiring()) {
                    fading++;
                    continue;
                }
                tripIds.add(p.id());
            }
            for (var r : runtime.diagnostics().domainReports()) {
                if (r.patchKey() != tripKey[0]) continue;
                tripOrigin[0] = r.originX();
                tripOrigin[1] = r.originY();
                tripOrigin[2] = r.originZ();
            }
            var s = runtime.stats();
            tripLost[0] = s.domainsLost;
            tripLost[1] = s.retireDomainLost;
            tripLost[2] = s.outsideWater;
            tripLost[3] = s.retireInvalid;
            tripLost[4] = s.retireExcluded;
            tripLost[5] = s.retireExpired;
            tripRevivals[0] = s.revivals;
            tripColumns[0] = runtime.diagnostics().patchColumns(tripKey[0]);
            check("ambientwater.trip.snapshot", tripIds.size() >= 2 && runtime.diagnostics().patchColumns(tripKey[0]) >= 4,
                    "tracked=" + tripIds.size() + " fadingAtSnapshot=" + fading + " key=" + tripKey[0]
                            + " columns=" + runtime.diagnostics().patchColumns(tripKey[0]) + " origin="
                            + tripOrigin[0] + "," + tripOrigin[1] + "," + tripOrigin[2]);
        });
        queue(1, mc -> camera(mc, poolAX - 40.5, gy + 0.7, poolZ + 0.5, -90f, 2f));
        queue(15, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            int present = 0, retiring = 0;
            for (var p : runtime.diagnostics().individuals()) {
                if (!tripIds.contains(p.id())) continue;
                present++;
                if (p.retiring()) retiring++;
            }
            var s = runtime.stats();
            int columns = runtime.diagnostics().patchColumns(tripKey[0]);
            check("ambientwater.trip.evicted", columns == 0 && s.domainsLost > tripLost[0]
                            && s.retireDomainLost - tripLost[1] >= tripIds.size(),
                    "patchColumns=" + columns + " domainsLost=+" + (s.domainsLost - tripLost[0])
                            + " retireDomainLost=+" + (s.retireDomainLost - tripLost[1]) + "/" + tripIds.size());
            check("ambientwater.trip.fading", present == tripIds.size() && retiring == present,
                    "present=" + present + "/" + tripIds.size() + " retiring=" + retiring);
        });
        queue(1, mc -> camera(mc, poolAX + 0.5, gy - 0.2, poolZ + 0.5, -90f, 2f));
        // A long window: the pool's outer ring is rediscovered by the sweep, whose cursor can be
        // anywhere in its 24-ring walk when the camera returns, so the last columns take up to
        // ~90 ticks to come back. By the end of it the water must be all there — a revived domain
        // that could only ever rebuild from a corner of it would have shed residents along the way.
        queue(120, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            int present = 0, retiring = 0, keyChanged = 0;
            for (var p : runtime.diagnostics().individuals()) {
                if (!tripIds.contains(p.id())) continue;
                present++;
                if (p.retiring()) retiring++;
                if (p.patchKey() != tripKey[0]) keyChanged++;
            }
            int originMoved = 0;
            for (var r : runtime.diagnostics().domainReports()) {
                if (r.patchKey() == tripKey[0] && (r.originX() != tripOrigin[0]
                        || r.originY() != tripOrigin[1] || r.originZ() != tripOrigin[2])) originMoved++;
            }
            var s = runtime.stats();
            int columns = runtime.diagnostics().patchColumns(tripKey[0]);
            // The water must be wholly back, under the pool's own key and frame, with the residents
            // alive. Ledger clauses pin what "alive" means against the bug this reproduces: nobody
            // may read as outside the rebuilt domain (the hazard's outsideWater signature), and a
            // resident that is missing must be explained by a retirement since the snapshot — the
            // designed answers to a body the rebuilt water cannot host (INVALID), to a species that
            // left the authoritative pool (EXCLUDED), or to a lifetime running out (EXPIRED) — not
            // by an unaccounted disappearance.
            long outside = s.outsideWater - tripLost[2];
            long invalid = s.retireInvalid - tripLost[3];
            long excluded = s.retireExcluded - tripLost[4];
            long expired = s.retireExpired - tripLost[5];
            check("ambientwater.trip.revived",
                    columns >= tripColumns[0] && retiring == 0 && keyChanged == 0
                            && originMoved == 0 && s.revivals > tripRevivals[0]
                            && outside == 0 && present + invalid + excluded + expired >= tripIds.size(),
                    "columns=" + columns + "/" + tripColumns[0] + " present=" + present + "/" + tripIds.size()
                            + " retiring=" + retiring + " keyChanged=" + keyChanged
                            + " originMoved=" + originMoved + " revivals=+" + (s.revivals - tripRevivals[0])
                            + " outsideWater=+" + outside + " coverage=+" + (invalid + excluded + expired));
        });

        // ── whole-run totals, then hand the runtime back
        queue(1, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var stats = runtime.stats();
            // A resident that is swimming may legitimately brush its own wall for a tick — the engine
            // steps on `contains` and pushes back through the soft avoidance, while `bodyFits` is the
            // spawn and rebuild predicate, enforced in `findSpawn` and reported as `invalid` below. What
            // must never happen is an active resident outside the water. Whole-body spawn fit is
            // checked after placement; per-fixture snapshots check centre containment and finiteness.
            check("ambientwater.containment", stats.outsideWater == 0,
                    "outsideWater=" + stats.outsideWater + " bodyTouches=" + stats.bodyFitsFailures
                            + " rebuildInvalidatedCenters=" + stats.rebuildInvalidatedCenters
                            + " steps=" + stats.steps);
            check("ambientwater.spawn_body_fit", stats.spawnFitChecks > 0 && stats.spawnFitFailures == 0,
                    "post-placement checks=" + stats.spawnFitChecks + " failures=" + stats.spawnFitFailures);
            check("ambientwater.budget", stats.peakDiscoveryCells <= 512 && stats.peakDiscoveryColumns <= 48
                            && stats.peakBuildMillis < 25.0,
                    String.format("peakCells=%d peakColumns=%d peakBuild=%.2fms meanBuild=%.2fms rebuilds=%d domainBuilds=%d",
                            stats.peakDiscoveryCells, stats.peakDiscoveryColumns, stats.peakBuildMillis,
                            stats.meanBuildMillis(), stats.rebuilds, stats.domainBuilds));
            check("ambientwater.boundary",
                    stats.minOpenFaceDistance > grill24.fishsim.domain.WaterDomain.OPEN_FACE_MARGIN - 0.15f
                            || stats.minOpenFaceDistance == Double.MAX_VALUE,
                    String.format("minOpenFace=%.3f margin=%.2f retirements=%d (openFace=%d invalid=%d domainLost=%d)",
                            stats.minOpenFaceDistance, grill24.fishsim.domain.WaterDomain.OPEN_FACE_MARGIN,
                            stats.retirements, stats.retireOpenFace, stats.retireInvalid, stats.retireDomainLost));
            // Every retirement that has finished, plus the ones still fading, was started with a
            // reason — so the reasons must cover them. (A retirement that never finishes, because its
            // domain was dropped under it, is the other direction and only shows as a shortfall.)
            check("ambientwater.accounted",
                    stats.retirements + stats.retiring <= stats.retireOpenFace + stats.retireInvalid
                            + stats.retireDomainLost + stats.retireExcluded + stats.retireExpired,
                    String.format("spawns=%d omissions=%d retirements=%d+fading%d (openFace=%d invalid=%d domainLost=%d"
                                    + " excluded=%d expired=%d in %d domains)"
                                    + " minSurface=%.3f minBed=%.3f reads=%d columns=%d evicted=%d merges=%d truncated=%d unloaded=%d (last fixture)",
                            stats.spawns, stats.omissions, stats.retirements, stats.retiring,
                            stats.retireOpenFace, stats.retireInvalid, stats.retireDomainLost,
                            stats.retireExcluded, stats.retireExpired, stats.domainsLost,
                            stats.minSurfaceClearance, stats.minBedClearance, stats.worldReads,
                            stats.discoveryColumns, stats.columnsEvicted, stats.patchMerges,
                            stats.truncatedColumns, stats.unloadedScans));
        });
        queue(1, mc -> {
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            check("ambientwater.cleanup", !runtime.armed() && runtime.domainCount() == 0 && runtime.diagnostics().individuals().isEmpty()
                            && !ClientAmbientFish.hasDevelopmentView(),
                    "armed=" + runtime.armed() + " domains=" + runtime.domainCount());
        });
    }

    /** Restarts the natural population where the player is standing; the camera step ran before. */
    private static void ambientWaterStart(String fixture) {
        grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
        boolean started = grill24.fishtastic.client.ambient.ClientAmbientFish.startNatural();
        check("ambientwater." + fixture + ".start", started,
                grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().pool().toString());
    }

    /**
     * One fill command; {@code block} may carry a mode, {@code "minecraft:water keep"} replacing only
     * air and leaving anything already placed in the box alone.
     */
    private static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        run(server, "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block);
    }

    // ── Phase 4: authoritative context snapshots ─────────────────────────────

    /**
     * Opt-in: the context pipeline end to end. A 7x7 pond is built (singleplayer; a marker with
     * {@code server=host:port} joins a dedicated server whose flat world is all water instead), the
     * ambient runtime is armed where the player stands, and then the store is watched through four
     * phases:
     *
     * <ol>
     *   <li>the pond's patch must reach {@code OK} at a settled sample column, and the client's
     *       fingerprint and candidate weights must equal the integrated server's own resolve of the
     *       same origin (replayed in-process);</li>
     *   <li>a rod handed over the creative-slot packet must switch the answer to the held-rod lane
     *       with a different fingerprint, again equal to the server's resolve with the rod;</li>
     *   <li>a real terrain change to the patch's own sample column must be noticed within a few
     *       ticks — the patch re-asks at a bumped geometry generation and returns to OK against the
     *       new water, rather than serving the old sample cell until the ~10 s periodic;</li>
     *   <li>a response to the pre-switch inputs — carrying a request generation newer than anything
     *       the pipeline sent, but the context generation the rod already moved past — must be
     *       discarded, and the fresh rod answer must survive it;</li>
     *   <li>the run's dispatch count must stay inside the one-per-20-ticks bound with at most
     *       {@code MAX_ORIGINS} origins each.</li>
     * </ol>
     *
     * <p>On a joined server the in-process replay is skipped: there the server's own INFO log of the
     * response it sent is the authoritative side, and the two logs are compared outside the game.
     */
    private static void queueAmbientContextScene() {
        final boolean remote = remoteServer != null;
        final int gy = origin.getY();
        // The pond centre is pinned at an absolute (24, 24) — deliberately NOT derived from the
        // spawn, which the flat preset randomises over a few blocks. What the scene needs is that
        // the whole pond sits inside one patch quantum, PATCH_QUANTUM = 16 blocks: a patch that
        // straddles a boundary can re-key mid-scene, because a pocket born in the neighbouring
        // quantum seeds its own patch and a merge keeps the numerically smallest key — and keys
        // pack quanta as a signed long, so a negative-quantum key beats every positive one. Lived
        // case, on an earlier fixture whose top water layer sat one above the flat grass and
        // spilled as a skirt: a spawn put the pond centre at 8 (mod 16), the skirt crossed the
        // x = 0 boundary, a swept-dead spill cell born west of it minted its own patch in quantum
        // x = -1, and the merge silently moved the pond's key from 1 to -17592186044409 mid-run —
        // every pinned-key waiter then watched a dead patch (redux of
        // WaterDiscoveryTest.aMergeAcrossTheQuantumBoundaryKeepsTheSmallestKeySoAKeyCanChange).
        // The pond here is dug flush instead — water at gy-2..gy-1, its top layer at the grass
        // surface — so there is no skirt to cross a boundary at all, and 24 (mod 16) puts the
        // pond edges (21..27) inside quantum (1, 1) with 5 blocks of margin to the west/south.
        // The player is teleported to a fixed (8, 16) so the pond is inside the discovery window
        // whatever the spawn.
        final int px = 24;
        final int pz = 24;
        final int standX = 8;
        final int standZ = 16;
        // A joined world is water everywhere, so there the target is simply where the player landed.
        final BlockPos target = remote ? origin : new BlockPos(px, gy, pz);

        final long[] phaseAKey = {0L};
        final long[] phaseAFp = {0L};
        final long[] phaseBFp = {0L};
        final int[] phaseACtx = {0};
        final List<grill24.fishtastic.network.AmbientContextResponsePacket.Candidate> phaseACandidates =
                new java.util.ArrayList<>();
        final long[] ticksAtStart = {0L};
        final long[] dispatchesAtStart = {0L};
        final long[] originsAtStart = {0L};

        queue(1, mc -> {
            ticksAtStart[0] = mc.level.getGameTime();
            dispatchesAtStart[0] = grill24.fishtastic.client.ambient.AmbientContextClient.dispatches();
            originsAtStart[0] = grill24.fishtastic.client.ambient.AmbientContextClient.originsSent();
            note("ambientcontext start " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
        });
        if (!remote) {
            // Pin the viewpoint before anything is measured: the runtime arms ~8 ticks after this,
            // so discovery starts around the teleported player, not the random spawn.
            queue(2, mc -> worldCommand(mc, "tp @a " + standX + " " + gy + " " + standZ));
            // Dug flush into the ground, not heaped above it: the pit is two deep (gy-2..gy-1),
            // floored with sand at gy-3, and the water's top layer sits at the grass surface
            // (gy-1) — walled in by the surrounding ground on every side, so it cannot spill.
            queue(2, mc -> worldCommand(mc, "fill " + (px - 3) + " " + (gy - 2) + " " + (pz - 3) + " "
                    + (px + 3) + " " + (gy - 1) + " " + (pz + 3) + " minecraft:air"));
            queue(2, mc -> worldCommand(mc, "fill " + (px - 3) + " " + (gy - 3) + " " + (pz - 3) + " "
                    + (px + 3) + " " + (gy - 3) + " " + (pz + 3) + " minecraft:sand"));
            queue(2, mc -> worldCommand(mc, "fill " + (px - 3) + " " + (gy - 2) + " " + (pz - 3) + " "
                    + (px + 3) + " " + (gy - 1) + " " + (pz + 3) + " minecraft:water keep"));
        }
        queue(2, mc -> {
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            boolean armed = grill24.fishtastic.client.ambient.ClientAmbientFish.startNatural();
            check("ambientcontext.runtime", armed, "armed=" + armed + " target " + target.toShortString());
        });

        // Phase A: the pond's patch answered, at a sample column that has not moved for two polls
        // and was answered after it settled. The runtime's sample is nearest-to-seed and can move
        // while a patch is still being discovered; the replay below asks about the cell the applied
        // answer was actually computed at, so the column must have settled first.
        final int[] stablePolls = {0};
        final long[] lastSamplePos = {Long.MIN_VALUE};
        final long[] responsesAtLastMove = {-1L};
        waitFor(10, 800, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            if (sample == null
                    || store.stateOf(sample.patchKey()) != grill24.fishtastic.client.ambient.AmbientContextStore.State.OK) {
                stablePolls[0] = 0;
                return false;
            }
            if (sample.pos().asLong() != lastSamplePos[0]) {
                lastSamplePos[0] = sample.pos().asLong();
                stablePolls[0] = 0;
                responsesAtLastMove[0] = store.responsesIn();
                return false;
            }
            stablePolls[0]++;
            return stablePolls[0] >= 2 && store.responsesIn() > responsesAtLastMove[0];
        }, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            var ctx = sample == null ? null : store.freshContext(sample.patchKey());
            if (ctx == null || sample == null) {
                check("ambientcontext.received", false,
                        "no OK context for a settled patch; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                return;
            }
            phaseAKey[0] = sample.patchKey();
            phaseAFp[0] = ctx.fingerprint();
            phaseACtx[0] = store.contextGeneration();
            phaseACandidates.clear();
            phaseACandidates.addAll(ctx.candidates());
            check("ambientcontext.received", !ctx.candidates().isEmpty(),
                    "key=" + sample.patchKey() + " fp=" + Long.toHexString(ctx.fingerprint())
                            + " n=" + ctx.candidates().size() + " ctxGen=" + phaseACtx[0]
                            + " at " + sample.pos().toShortString());
            check("ambientcontext.lane.worms", !ctx.sessionSourced() && !ctx.heldRod() && !ctx.truncated(),
                    "session=" + ctx.sessionSourced() + " rod=" + ctx.heldRod() + " truncated=" + ctx.truncated());
            if (!remote) {
                replayAgainstIntegratedServer(mc, "a", sample.pos(), phaseAKey[0],
                        store.geometryGenerationOf(sample.patchKey()), phaseACtx[0], phaseAFp[0], false,
                        phaseACandidates, store.session(), 100000);
            }
        });

        // Phase B: the rod, handed through the same creative-slot packet the creative inventory
        // uses — the one equipment change a joined client can make without op. The server sees it
        // in the player's own inventory, so the fresh answer must be the held-rod lane.
        queue(2, mc -> {
            ItemStack rod = new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", "copper_fishing_rod")));
            int selected = mc.player.getInventory().selected;
            // The creative GUI's own sequence (CreativeModeInventoryScreen.handleHotbarLoadOrSave):
            // write the local inventory, send the creative-slot packet, resync the local menu.
            // handleCreativeModeItemAdd alone is send-only, and the server's handler suppresses the
            // echo (setRemoteSlot before broadcastChanges), so without the local write the server's
            // copy would move while the client's own hand — and with it the equipment signature the
            // refresh policy watches — stayed empty.
            mc.player.getInventory().setItem(selected, rod);
            mc.gameMode.handleCreativeModeItemAdd(rod, 36 + selected);
            mc.player.inventoryMenu.broadcastChanges();
            server(mc, s -> s.getPlayerList().getPlayers().get(0).getInventory().setItem(selected, rod.copy()));
            note("ambientcontext handing rod to menu slot " + (36 + selected));
        });
        final int[] phaseBStablePolls = {0};
        final long[] phaseBLastPos = {Long.MIN_VALUE};
        waitFor(10, 800, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            var ctx = sample == null ? null : store.freshContext(sample.patchKey());
            if (ctx == null || !ctx.heldRod() || ctx.sessionSourced()
                    || ctx.fingerprint() == phaseAFp[0] || store.contextGeneration() <= phaseACtx[0]) {
                phaseBStablePolls[0] = 0;
                return false;
            }
            // The post-bump answer must also be at a settled column, for the same reason as phase A.
            if (sample.pos().asLong() != phaseBLastPos[0]) {
                phaseBLastPos[0] = sample.pos().asLong();
                phaseBStablePolls[0] = 0;
                return false;
            }
            phaseBStablePolls[0]++;
            return phaseBStablePolls[0] >= 2;
        }, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            var ctx = sample == null ? null : store.freshContext(sample.patchKey());
            if (ctx == null || sample == null) {
                check("ambientcontext.switch.lane", false,
                        "no rod-lane context after the switch; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                return;
            }
            phaseBFp[0] = ctx.fingerprint();
            check("ambientcontext.switch.lane", ctx.heldRod() && !ctx.sessionSourced() && ctx.fingerprint() != phaseAFp[0],
                    "fp=" + Long.toHexString(ctx.fingerprint()) + " (was " + Long.toHexString(phaseAFp[0])
                            + ") rod=" + ctx.heldRod() + " ctxGen=" + store.contextGeneration() + " wasCtx="
                            + phaseACtx[0] + " ctxDrops=" + store.droppedStaleContext());
            if (!remote) {
                replayAgainstIntegratedServer(mc, "b", sample.pos(), sample.patchKey(),
                        store.geometryGenerationOf(sample.patchKey()), store.contextGeneration(), phaseBFp[0], true,
                        ctx.candidates(), store.session(), 100100);
            }
        });

        // Phase C: a real terrain change to the shown patch's own sample column must be noticed
        // and re-asked at once — the patch has to re-ask at a bumped geometry generation and come
        // back OK against the new water, instead of serving the old sample cell until the ~10 s
        // periodic. The edit replaces the top water cell of the column the settled answer was
        // computed at with stone; the water below it stays, so the column still answers — it is
        // the sample cell that moved. Stone and not air on purpose: the edited cell sits in the
        // pond's top layer surrounded by source water, and neighbours refill a removed cell within
        // a few ticks (measured on the earlier spilling fixture: setblock air at a water cell came
        // back level-7) — only an unrefillable edit moves the geometry for good. Discovery notices
        // the edit only through its own revalidation
        // rotation (the sweep skips columns that are already known), so this waiter measures the
        // whole chain: rotation → per-tick signature compare → bump → re-ask → answer.
        //
        // The check is two-stage so its evidence separates the two clocks involved: stage 1 waits
        // for the runtime's own patch signature to move (re-reading a known column is discovery's
        // budgeted rotation, seconds at most — Phase 2 machinery, not Phase 4). Stage 2 then gives
        // the store at most one min-dispatch-interval plus a packet's flight to bump and apply a
        // fresh answer. Before the per-tick compare the bump and re-ask only happened at a
        // dispatch, so a move between periodics (up to 200 ticks) could go unnoticed — the RED
        // run showed geomGen frozen and dispatches=+0 across the whole window. The applied and
        // dispatch counters are snapshotted at the EDIT, not at the signature move: the bump's own
        // dispatch can land on the very tick the waiter first sees the move, and a snapshot taken
        // then would already count it, so "dispatches since the move" could never come true even
        // on correct code. An apply after the edit cannot be a pre-edit answer misread either way:
        // the store only applies a strictly newer request generation. Skipped on a joined server,
        // where no world edit is possible.
        if (remote) {
            note("ambientcontext.geometry.reask skipped (remote: no world edit on a joined server)");
        } else {
            final long[] keyC = {0L};
            final int[] geomBefore = {0};
            final long[] sigBefore = {0L};
            final long[] dispatchesBeforeC = {0L};
            final long[] appliedAtEdit = {0L};
            final long[] dispatchesAtEdit = {0L};
            final BlockPos[] samplePosC = {null};
            queue(2, mc -> {
                var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
                var sample = nearestShownSample(target);
                if (sample == null
                        || store.stateOf(sample.patchKey()) != grill24.fishtastic.client.ambient.AmbientContextStore.State.OK) {
                    check("ambientcontext.geometry.reask", false,
                            "no settled OK patch to disturb; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                    keyC[0] = -1L;
                    return;
                }
                keyC[0] = sample.patchKey();
                geomBefore[0] = store.geometryGenerationOf(sample.patchKey());
                sigBefore[0] = sample.geometrySignature();
                dispatchesBeforeC[0] = grill24.fishtastic.client.ambient.AmbientContextClient.dispatches();
                samplePosC[0] = sample.pos();
                note("ambientcontext.geometry editing top water cell " + samplePosC[0].toShortString()
                        + " of key=" + keyC[0] + " (geomGen=" + geomBefore[0]
                        + ", sig=" + Long.toHexString(sigBefore[0]) + ")");
                noteCell("ambientcontext.geometry before", samplePosC[0].getX(), samplePosC[0].getY(),
                        samplePosC[0].getZ());
            });
            queue(2, mc -> {
                if (keyC[0] == -1L) return;
                appliedAtEdit[0] = grill24.fishtastic.client.ambient.AmbientContextClient.store().entriesApplied();
                dispatchesAtEdit[0] = grill24.fishtastic.client.ambient.AmbientContextClient.dispatches();
                worldCommand(mc, "setblock " + samplePosC[0].getX() + " " + samplePosC[0].getY() + " "
                        + samplePosC[0].getZ() + " minecraft:stone");
            });
            // One waiter, two stages, so the evidence separates the two clocks involved. Stage 1
            // waits for the runtime to re-read the edited column — discovery's own budgeted
            // rotation over the patch's cached columns (Phase 2 machinery; its latency is why
            // this window is not tick-counted). The moment that lands, stage 2 starts: the
            // manager's per-tick compare must bump the generation and the re-ask's answer must
            // apply within one min-dispatch interval (20 t) plus a packet's flight — far inside
            // the 200-tick periodic, so only the per-tick geometry trigger can meet it. Both the
            // applied and dispatch counters are measured against their values at the edit, so a
            // bump-and-dispatch landing in the same tick as the move is still honoured; an apply
            // can only be an answer the pipeline requested after the edit (strictly newer request
            // generation). The 800-tick backstop covers stage 1 even in the slowest rotation; the
            // verdict detail says which stage ran out.
            final int[] stage = {0};        // 0 = waiting for the signature move; 1 = waiting for bump + re-ask
            final int[] stage2Ticks = {0};
            waitFor(1, 800, mc -> {
                if (keyC[0] == -1L) return true;
                var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
                if (stage[0] == 0) {
                    var sample = sampleOfKey(keyC[0]);
                    if (sample != null && sample.geometrySignature() != sigBefore[0]) {
                        stage[0] = 1;
                        noteCell("ambientcontext.geometry after", samplePosC[0].getX(), samplePosC[0].getY(),
                                samplePosC[0].getZ());
                    }
                    return false; // either still waiting for the move, or starting stage 2 next tick
                }
                stage2Ticks[0]++;
                var ctx = store.freshContext(keyC[0]);
                boolean pass = store.geometryGenerationOf(keyC[0]) > geomBefore[0] && ctx != null && ctx.heldRod()
                        && store.entriesApplied() > appliedAtEdit[0]
                        && grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() > dispatchesAtEdit[0];
                return pass || stage2Ticks[0] > 30;
            }, mc -> {
                if (keyC[0] == -1L) return;
                var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
                var ctx = store.freshContext(keyC[0]);
                var sample = sampleOfKey(keyC[0]);
                if (stage[0] == 0) {
                    var nearest = nearestShownSample(target);
                    check("ambientcontext.geometry.reask", false,
                            "discovery never re-read the edited column (sig=" + Long.toHexString(sigBefore[0])
                                    + " unchanged, shown=" + (sample != null)
                                    + (sample == null && nearest != null
                                            ? " (nearest shown now key=" + nearest.patchKey() + ")" : "")
                                    + "); "
                                    + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                    return;
                }
                check("ambientcontext.geometry.reask",
                        store.geometryGenerationOf(keyC[0]) > geomBefore[0] && ctx != null && ctx.heldRod()
                                && !ctx.sessionSourced() && store.entriesApplied() > appliedAtEdit[0]
                                && grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() > dispatchesAtEdit[0]
                                && sample != null,
                        "geomGen=" + geomBefore[0] + "->" + store.geometryGenerationOf(keyC[0])
                                + " state=" + store.stateOf(keyC[0])
                                + " fp=" + (ctx == null ? "none" : Long.toHexString(ctx.fingerprint()))
                                + " rod=" + (ctx != null && ctx.heldRod())
                                + " applied=+" + (store.entriesApplied() - appliedAtEdit[0]) + " since edit"
                                + " stage2=" + stage2Ticks[0] + "t"
                                + " sameKey=" + (sample != null)
                                + " dispatches=+" + (grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() - dispatchesBeforeC[0])
                                + "; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
            });
        }

        // Phase D: the stale-response rule, end to end. The injected answer is the phase-A value
        // (pre-rod) under the pre-rod context generation, but with a request generation newer than
        // anything the pipeline sent — so only the generation check can reject it, and the fresh
        // rod answer must survive untouched.
        queue(5, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            var fresh = sample == null ? null : store.freshContext(sample.patchKey());
            if (sample == null || fresh == null) {
                check("ambientcontext.stale.dropped", false,
                        "no fresh context to protect; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                return;
            }
            long dropsBefore = store.droppedStaleContext();
            var staleEntry = new grill24.fishtastic.network.AmbientContextResponsePacket.Entry(
                    sample.patchKey(), store.geometryGenerationOf(sample.patchKey()), phaseACtx[0],
                    grill24.fishtastic.network.AmbientContextResponsePacket.Status.OK,
                    false, false, false, phaseAFp[0], phaseACandidates);
            grill24.fishtastic.client.ambient.AmbientContextClient.injectForTest(
                    new grill24.fishtastic.network.AmbientContextResponsePacket(store.session(),
                            store.requestGeneration() + 100, List.of(staleEntry)));
            var after = store.freshContext(sample.patchKey());
            check("ambientcontext.stale.dropped",
                    store.droppedStaleContext() == dropsBefore + 1 && after != null
                            && after.fingerprint() == phaseBFp[0] && after.heldRod(),
                    "ctxDrops=+" + (store.droppedStaleContext() - dropsBefore)
                            + " ctxGen=" + store.contextGeneration() + " entryCtx=" + phaseACtx[0]
                            + " surviving fp=" + (after == null ? "none" : Long.toHexString(after.fingerprint()))
                            + " want " + Long.toHexString(phaseBFp[0]) + "; "
                            + grill24.fishtastic.client.ambient.AmbientContextClient.lastResponseSummary());
        });

        // Phase E: the traffic bound for the whole scene, and the server's own work counters.
        queue(1, mc -> {
            long elapsed = mc.level.getGameTime() - ticksAtStart[0];
            long sent = grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() - dispatchesAtStart[0];
            long origins = grill24.fishtastic.client.ambient.AmbientContextClient.originsSent() - originsAtStart[0];
            long bound = elapsed / grill24.fishtastic.client.ambient.AmbientContextClient.MIN_DISPATCH_INTERVAL_TICKS + 3;
            check("ambientcontext.traffic",
                    sent <= bound && origins <= sent * grill24.fishtastic.network.AmbientContextRequestPacket.MAX_ORIGINS,
                    "dispatches=" + sent + " origins=" + origins + " responses="
                            + grill24.fishtastic.client.ambient.AmbientContextClient.responsePackets()
                            + " in " + elapsed + " ticks (bound " + bound + ")");
            note("ambientcontext end " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
            if (!remote) {
                server(mc, s -> note("ambientcontext server counters responsesSent="
                        + grill24.fishtastic.server.AmbientContextServer.responsesSent()
                        + " originsResolved=" + grill24.fishtastic.server.AmbientContextServer.originsResolved()
                        + " originsRejected=" + grill24.fishtastic.server.AmbientContextServer.originsRejected()));
            }
        });
        queue(2, mc -> grill24.fishtastic.client.ambient.ClientAmbientFish.clear());
    }

    /** The shown patch whose sample is nearest {@code target}, or null when nothing is shown. */
    private static grill24.fishtastic.client.ambient.AmbientFishRuntime.PatchSample nearestShownSample(BlockPos target) {
        grill24.fishtastic.client.ambient.AmbientFishRuntime.PatchSample best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var sample : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().patchSamples()) {
            double dx = sample.pos().getX() - target.getX();
            double dz = sample.pos().getZ() - target.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = sample;
            }
        }
        return best;
    }

    /** The live shown sample for one patch key, re-read from the runtime each call; null when not shown. */
    private static grill24.fishtastic.client.ambient.AmbientFishRuntime.PatchSample sampleOfKey(long patchKey) {
        for (var sample : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().patchSamples()) {
            if (sample.patchKey() == patchKey) return sample;
        }
        return null;
    }

    // ── Phase 5: weighted population and equipment transitions ───────────────

    /** How often the long run samples, and how many samples it takes: 600 x 20 = ten minutes. */
    private static final int LONG_RUN_EVERY = 20, LONG_RUN_POLLS = 600;

    /**
     * Opt-in: the Phase 5 population end to end. A 7x7 pond (the context scene's absolute fixture —
     * one patch quantum, no spill) is dug flush, the runtime armed at a fixed standing spot, and
     * three scripted equipment switches plus a return drive the population through every transition
     * class:
     *
     * <ol>
     *   <li><b>no rod</b> — the standard-worms baseline: the patch must reach OK, the runtime must
     *       build its sampler and fill the pond from the representable set;</li>
     *   <li><b>rod + Gummy Worms</b> — weight-only: the candidate species are identical, the weights
     *       (and so the fingerprint) move. No resident may be excluded and every tracked one must
     *       keep its own identity — abundance shifts by turnover, never by clearing the pond;</li>
     *   <li><b>+ Amethyst Charm</b> — a fingerprint change with identical candidates AND identical
     *       weights (the charm's night bonus is 1.0 and it has no affinities): the sampler rebuilds
     *       and the population still must not move;</li>
     *   <li><b>Amethyst → Crystal Ball Charm</b> — quality-only: the two charms differ only in
     *       fields the resolver excludes from its fingerprint (input force, rarity outline), so the
     *       server answers with the SAME fingerprint while the client's equipment hash still moves.
     *       A dispatch must happen and apply; the sampler must NOT rebuild and nobody may move;</li>
     *   <li><b>Blazed Grub</b> — hard eligibility: an exotic-only exclusive pool. Residents whose
     *       species left the pool must retire through the eligibility fade (measured against the
     *       ~2 s spec) and after the settle no resident may hold a species the pool does not name;</li>
     *   <li><b>back to no rod</b> — the reverse hard change: the exotic residents common to both
     *       pools must be retained; the pond resumes the baseline pool;</li>
     *   <li><b>a ten-minute idle</b> — turnover under bounded, species-independent lifetimes, with
     *       the observed occupancy tracking the sampler's 75/25 visual mixture (the raw catch share
     *       is logged beside it, since the whole point of the flattened quarter is that they
     *       differ);</li>
     *   <li><b>walk out and back</b> — the patch's random state must be retained across the absence
     *       (bounded by count and by age) and resumed, not re-seeded on return.</li>
     * </ol>
     *
     * <p>The player stays in CREATIVE and moves by {@code tp}: the equipment handoffs go through the
     * creative-slot packet, which the server drops outside creative, so this scene must never use
     * {@link #camera} (which sets spectator).
     */
    private static void queueAmbientIslandScene() {
        // The deep ocean is built above the superflat terrain, so its foundation must not fall.
        queue(1, mc -> worldCommand(mc, "fill 0 39 0 40 39 40 minecraft:sandstone"));
        queue(2, mc -> worldCommand(mc, "fill 0 40 0 40 51 40 minecraft:water"));
        queue(2, mc -> worldCommand(mc, "fill 0 52 0 40 63 40 minecraft:water"));
        queue(2, mc -> worldCommand(mc, "fill 17 40 19 20 64 22 minecraft:stone"));
        queue(2, mc -> worldCommand(mc, "fillbiome 0 40 0 40 72 40 minecraft:ocean"));
        queue(2, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(50);
            ClientAmbientFish.productionTest(true);
            camera(mc, 19.5, 66.6, 21.5, -90, 25);
        });
        waitFor(10, 900, mc -> ClientAmbientFish.natural().stats().active >= 28, mc -> {
            check("ambientisland.population", ClientAmbientFish.natural().stats().active >= 28,
                    ClientAmbientFish.natural().diagnostics().debugState());
            boolean nearSurface = ClientAmbientFish.natural().diagnostics().individuals().stream().filter(p -> !p.retiring())
                    .allMatch(p -> p.surfaceClearance() <= 4.01 && p.surfaceClearance() >= 0);
            check("ambientisland.depth", nearSurface, "active fish stay within the four-block display band");
            screenshot(mc, "ambientisland", "shore");
            camera(mc, 25, 62.5, 22, 90, 0);
        });
        queue(80, mc -> {
            check("ambientisland.submitted", ClientAmbientFish.worldSubmitsThisFrame > 0,
                    "world submissions=" + ClientAmbientFish.worldSubmitsThisFrame);
            check("ambientisland.budget", ClientAmbientFish.natural().stats().individuals <= 32,
                    "50% global cap stays thirty-two");
            screenshot(mc, "ambientisland", "underwater");
            note("ambientisland " + ClientAmbientFish.natural().diagnostics().debugState());
        });
        queue(800, mc -> {
            check("ambientisland.sustained", ClientAmbientFish.natural().stats().active >= 26,
                    ClientAmbientFish.natural().diagnostics().debugState());
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
        });
    }

    private static void queueAmbientFloorScene() {
        // Replace the old deep-ocean fixture too: disconnected lower spans must not leave the
        // shallow bed marked unknown merely because the lower water hits the scan boundary.
        queue(1, mc -> worldCommand(mc, "fill 0 40 0 40 50 40 minecraft:sandstone"));
        queue(2, mc -> worldCommand(mc, "fill 0 51 0 40 60 40 minecraft:sandstone"));
        queue(2, mc -> worldCommand(mc, "fill 0 61 0 40 61 40 minecraft:sand"));
        queue(2, mc -> worldCommand(mc, "fill 0 62 0 40 63 40 minecraft:water"));
        queue(2, mc -> worldCommand(mc, "fill 0 64 0 40 70 40 minecraft:air"));
        queue(2, mc -> worldCommand(mc, "fillbiome 0 61 0 40 72 40 minecraft:ocean"));
        queue(2, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(100);
            ClientAmbientFish.productionTest(true);
            camera(mc, 20.5, 63, 20.5, 90, 5);
        });
        waitFor(10, 900, mc -> ClientAmbientFish.natural().stats().active >= 60
                && ClientAmbientFish.natural().diagnostics().individuals().stream()
                .anyMatch(p -> p.species().endsWith("garden_eel") && !p.retiring()), mc -> {
            var runtime = ClientAmbientFish.natural();
            check("ambientfloor.population", runtime.stats().active >= 60 && runtime.stats().individuals <= 64,
                    runtime.diagnostics().debugState());
            var eels = runtime.diagnostics().individuals().stream()
                    .filter(p -> p.species().endsWith("garden_eel") && !p.retiring()).toList();
            check("ambientfloor.eelBed", !eels.isEmpty()
                    && eels.stream().allMatch(p -> Math.abs(p.bedClearance()) < 0.001), "eels=" + eels);
            if (!eels.isEmpty()) {
                var eel = eels.get(0);
                camera(mc, eel.x() + 1.4, eel.y() + 0.4, eel.z(), 90, 5);
            }
        });
        queue(20, mc -> screenshot(mc, "ambientfloor", "eel_bed"));
        queue(800, mc -> {
            var runtime = ClientAmbientFish.natural();
            check("ambientfloor.continuity", runtime.stats().retireExpired == 0,
                    "expiry retirements=" + runtime.stats().retireExpired + " " + runtime.diagnostics().debugState());
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
        });
    }

    private static void queueAmbientWalkScene() {
        var initialIds = new java.util.HashSet<Long>();
        long[] initial = new long[2];
        queue(1, mc -> worldCommand(mc, "fill 0 61 0 40 61 40 minecraft:sandstone"));
        queue(2, mc -> worldCommand(mc, "fill 0 62 0 40 63 40 minecraft:water"));
        queue(2, mc -> worldCommand(mc, "fill 0 64 0 40 70 40 minecraft:air"));
        queue(2, mc -> worldCommand(mc, "fillbiome 0 61 0 40 72 40 minecraft:ocean"));
        queue(2, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(50);
            ClientAmbientFish.productionTest(true);
            camera(mc, 20.5, 65, 20.5, 90, 20);
        });
        waitFor(10, 900, mc -> ClientAmbientFish.natural().stats().active >= 28, mc -> {
            var runtime = ClientAmbientFish.natural();
            check("ambientwalk.populated", runtime.stats().active >= 28, runtime.diagnostics().debugState());
            initial[0] = runtime.stats().domainsLost;
            initial[1] = runtime.stats().retireExpired;
            for (var fish : runtime.diagnostics().individuals()) if (!fish.retiring()) initialIds.add(fish.id());
        });
        for (int step = 1; step <= 8; step++) {
            final double x = 20.5 + (step <= 4 ? step : 8 - step);
            queue(40, mc -> camera(mc, x, 65, 20.5, 90, 20));
        }
        queue(80, mc -> {
            var runtime = ClientAmbientFish.natural();
            long surviving = runtime.diagnostics().individuals().stream()
                    .filter(p -> !p.retiring() && initialIds.contains(p.id())).count();
            check("ambientwalk.noPatchRetirement", runtime.stats().domainsLost == initial[0], runtime.diagnostics().debugState());
            check("ambientwalk.continuity", !initialIds.isEmpty() && surviving >= initialIds.size() / 2
                    && runtime.stats().retireExpired == initial[1], "surviving=" + surviving + "/" + initialIds.size());
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
        });
    }

    private static void queueAmbientLifecycleScene() {
        if (remoteServer != null) return;
        int gy = origin.getY();
        BlockPos target = new BlockPos(24, gy, 24);
        long[] before = new long[5];
        double[] times = new double[4];
        AmbientContextResponsePacket[] old = new AmbientContextResponsePacket[1];
        queue(1, mc -> worldCommand(mc, "tp @a 18 " + gy + " 20"));
        queue(2, mc -> worldCommand(mc, "fill 21 " + (gy - 3) + " 21 27 " + (gy - 3) + " 27 minecraft:sand"));
        queue(2, mc -> worldCommand(mc, "fill 21 " + (gy - 2) + " 21 27 " + (gy - 1) + " 27 minecraft:water"));
        queue(2, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(100);
            ClientAmbientFish.productionTest(true);
        });
        waitFor(10, 800, mc -> ClientAmbientFish.natural().stats().active >= 5, mc -> {
            check("ambientlifecycle.automatic", ClientAmbientFish.natural().stats().active >= 5,
                    ClientAmbientFish.natural().diagnostics().debugState());
            var sample = nearestShownSample(target);
            var store = AmbientContextClient.store();
            var ctx = sample == null ? null : store.freshContext(sample.patchKey());
            if (ctx != null) old[0] = new AmbientContextResponsePacket(store.session(), store.requestGeneration() + 1,
                    List.of(new AmbientContextResponsePacket.Entry(sample.patchKey(), store.geometryGenerationOf(sample.patchKey()),
                            store.contextGeneration(), AmbientContextResponsePacket.Status.OK, false, false, false,
                            ctx.fingerprint(), ctx.candidates())));
            // Coexisting render/simulation consumers on the same client.
            server(mc, s -> {
                BlockPos min = new BlockPos(16, gy, 25);
                placeSpanBox(s, min, new grill24.fishtastic.fishtank.CosmeticStructure.Span(2, 2, 2));
                FishTankBlockEntity tank = tank(s.overworld(), min);
                if (tank != null) for (int i = 0; i < 4; i++) tank.addItem(fish("bluegill", 30f));
                run(s, "summon minecraft:salmon 24 " + (gy - 1.5) + " 24");
            });
            grill24.fishtastic.client.companion.ClientCompanionsDev.devAddPhantom(fish("bluegill", 30f));
            var stats = ClientAmbientFish.natural().stats();
            before[0] = stats.ticks;
            before[1] = stats.renderFrames;
            times[0] = stats.totalTickMillis;
            times[1] = stats.totalSubmitMillis;
            worldCommand(mc, "tp @a 17.5 " + gy + " 24.5 -90 30");
        });
        queue(600, mc -> {
            var runtime = ClientAmbientFish.natural();
            var stats = runtime.stats();
            double tickMean = (stats.totalTickMillis - times[0]) / Math.max(1, stats.ticks - before[0]);
            double submitMean = (stats.totalSubmitMillis - times[1]) / Math.max(1, stats.renderFrames - before[1]);
            check("ambientlifecycle.budgets", stats.individuals <= runtime.budget().individuals()
                            && runtime.domainCount() <= runtime.budget().domains()
                            && stats.peakDiscoveryCells <= 512 && stats.peakDiscoveryColumns <= 48,
                    runtime.diagnostics().debugState());
            note(String.format(Locale.ROOT, "ambientlifecycle.perf ticks=%d frames=%d meanTickMs=%.3f peakTickMs=%.3f "
                            + "meanSubmitMs=%.3f peakSubmitMs=%.3f meanBuildMs=%.3f peakBuildMs=%.3f (CPU submission only)",
                    stats.ticks - before[0], stats.renderFrames - before[1], tickMean, stats.peakTickMillis,
                    submitMean, stats.peakSubmitMillis, stats.meanBuildMillis(), stats.peakBuildMillis));
            note("ambientlifecycle traffic " + AmbientContextClient.debugState());
            screenshot(mc, "ambientlifecycle", "coexistence");
            camera(mc, 24, gy - 0.8, 26.6, 180, 0);
        });
        queue(5, mc -> {
            screenshot(mc, "ambientlifecycle", "underwater");
            worldCommand(mc, "tp @a 18 " + gy + " 20");
            // The superflat surface is four blocks above the world minimum. Sand here replaces
            // bedrock with an unsupported falling block, so the supposed fixed floor disappears.
            worldCommand(mc, "fill -8 " + (gy - 4) + " -8 55 " + (gy - 4) + " 55 minecraft:stone");
        });
        queue(2, mc -> worldCommand(mc, "fill -8 " + (gy - 3) + " -8 55 " + (gy - 1) + " 55 minecraft:water"));
        // Measure open water only once discovery and cached bed revalidation have both settled.
        double[] openTimes = new double[2];
        long[] openTicks = new long[2];
        long[] openBuilds = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
        waitFor(10, 1200, mc -> {
            var runtime = ClientAmbientFish.natural();
            long now = mc.level.getGameTime();
            // The old pond had a shallower bed. Finding every column is not enough: budgeted
            // revalidation must also replace its cached readings before this is settled water.
            long staleBeds = runtime.discovery().columns().stream()
                    .filter(column -> Math.abs(column.water().bedY() - (gy - 3)) > 0.01f).count();
            if (runtime.discovery().columnCount() == 2401 && openBuilds[3] != staleBeds) {
                note("ambientlifecycle.openwater.cache staleBeds=" + staleBeds + " changedColumns="
                        + runtime.discovery().counters().columnsChanged);
            }
            openBuilds[3] = staleBeds;
            if (runtime.discovery().columnCount() != 2401 || runtime.domainCount() != 1
                    || staleBeds != 0
                    || openBuilds[0] != runtime.stats().domainBuilds) {
                openBuilds[0] = runtime.stats().domainBuilds;
                openBuilds[1] = now;
                return false;
            }
            return now - openBuilds[1] >= 40;
        }, mc -> {
            check("ambientlifecycle.openwater.settled", ClientAmbientFish.natural().discovery().columnCount() == 2401
                            && openBuilds[3] == 0
                            && mc.level.getGameTime() - openBuilds[1] >= 40,
                    "full water window, staleBeds=" + openBuilds[3] + ", no domain rebuild for forty ticks");
            openBuilds[2] = ClientAmbientFish.natural().stats().domainBuilds;
            openTicks[0] = ClientAmbientFish.natural().stats().ticks;
            openTimes[0] = ClientAmbientFish.natural().stats().totalTickMillis;
            openTicks[1] = AmbientContextClient.ticks();
            openTimes[1] = AmbientContextClient.totalTickMillis();
        });
        queue(200, mc -> {
            var runtime = ClientAmbientFish.natural();
            var stats = runtime.stats();
            check("ambientlifecycle.openwater.no_timed_rebuilds", stats.domainBuilds == openBuilds[2],
                    "domain builds=" + openBuilds[2] + "->" + stats.domainBuilds + " over "
                            + (stats.ticks - openTicks[0]) + " settled ticks");
            // The budget, not a figure: the density-100 cap has moved with tuning (32 when this was written).
            check("ambientlifecycle.openwater.bounded", runtime.discovery().columnCount() <= 4096
                    && stats.individuals <= runtime.budget().individuals() && runtime.domainCount() <= runtime.budget().domains()
                    && stats.peakDiscoveryCells <= 512 && stats.peakDiscoveryColumns <= 48,
                    runtime.diagnostics().debugState());
            note(String.format(Locale.ROOT, "ambientlifecycle.openwater window ticks=%d known=%d indivs=%d"
                            + " runtimeMeanTickMs=%.4f contextMeanTickMs=%.4f",
                    stats.ticks - openTicks[0], runtime.discovery().columnCount(), stats.individuals,
                    (stats.totalTickMillis - openTimes[0]) / Math.max(1, stats.ticks - openTicks[0]),
                    (AmbientContextClient.totalTickMillis() - openTimes[1]) / Math.max(1, AmbientContextClient.ticks() - openTicks[1])));
            note("ambientlifecycle.openwater perf " + runtime.diagnostics().debugState());
            // A current authoritative empty pool is different from a pending/rejected answer.
            // Keep the synthetic answer current while observing its full retirement fade.
            tickProbe = client -> {
                var sample = nearestShownSample(target);
                var store = AmbientContextClient.store();
                if (sample != null) AmbientContextClient.injectForTest(new AmbientContextResponsePacket(store.session(),
                        1_000_000 + (int) client.level.getGameTime(),
                        List.of(new AmbientContextResponsePacket.Entry(sample.patchKey(), store.geometryGenerationOf(sample.patchKey()),
                                store.contextGeneration(), AmbientContextResponsePacket.Status.OK, false, false, false,
                                123L, List.of()))));
            };
        });
        // Retirements finish a few per tick after the fade, so a full density-100 population takes
        // longer than one fade to go; wait for it rather than for a fixed 60 ticks.
        queueUntil("ambientlifecycle.empty.retired", 300, mc -> ClientAmbientFish.natural().stats().individuals == 0,
                () -> "authoritative OK/empty retires residents and spawns none: "
                        + ClientAmbientFish.natural().stats().individuals + " left");
        queue(1, mc -> {
            tickProbe = null;
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, false);
        });
        queue(2, mc -> {
            check("ambientlifecycle.disable.cleared", !ClientAmbientFish.naturalArmed()
                    && ClientAmbientFish.natural().domainCount() == 0
                    && ClientAmbientFish.natural().discovery().columnCount() == 0
                    && AmbientContextClient.store().states().isEmpty(), "all state released");
            before[2] = AmbientContextClient.dispatches();
            before[3] = ClientAmbientFish.naturalSteps();
            before[4] = ClientAmbientFish.natural().waterSource().flagReads();
        });
        queue(80, mc -> {
            check("ambientlifecycle.disable.idle", before[2] == AmbientContextClient.dispatches()
                            && before[3] == ClientAmbientFish.naturalSteps()
                            && before[4] == ClientAmbientFish.natural().waterSource().flagReads()
                            && ClientAmbientFish.worldSubmitsThisFrame == 0, "no requests, reads, simulation or submissions");
            if (old[0] != null) {
                long dropped = AmbientContextClient.store().droppedOldSession();
                AmbientContextClient.injectForTest(old[0]);
                check("ambientlifecycle.disable.late", AmbientContextClient.store().droppedOldSession() == dropped + 1,
                        "pre-disable response discarded");
            }
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(50);
        });
        waitFor(10, 600, mc -> ClientAmbientFish.natural().stats().active >= 3, mc -> {
            check("ambientlifecycle.reenable", ClientAmbientFish.natural().stats().active >= 3
                    && ClientAmbientFish.natural().budget().density() == 50, ClientAmbientFish.natural().diagnostics().debugState());
            ClientAmbientFish.invalidate();
            check("ambientlifecycle.reload.cleared", !ClientAmbientFish.naturalArmed()
                    && AmbientContextClient.store().states().isEmpty(), "same resource/tag reload hook used by both loaders");
        });
        waitFor(10, 600, mc -> ClientAmbientFish.natural().stats().active >= 3, mc -> {
            check("ambientlifecycle.reload.rearmed", ClientAmbientFish.natural().stats().active >= 3, "automatic restart");
            before[0] = AmbientContextClient.store().contextGeneration();
            worldCommand(mc, "weather rain");
        });
        waitFor(2, 100, mc -> AmbientContextClient.store().contextGeneration() != before[0], mc -> {
            check("ambientlifecycle.weather", AmbientContextClient.store().contextGeneration() != before[0],
                    "observable weather change promptly invalidates the pool");
            before[0] = AmbientContextClient.store().session();
            worldCommand(mc, "execute in minecraft:the_nether run tp @a 0 100 0");
        });
        waitFor(5, 200, mc -> mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER), mc -> {
            check("ambientlifecycle.dimension", mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER)
                    && before[0] != AmbientContextClient.store().session()
                    && ClientAmbientFish.natural().stats().individuals == 0, "old world population/session released");
            worldCommand(mc, "execute in minecraft:overworld run tp @a 18 " + gy + " 20");
        });
        waitFor(10, 800, mc -> mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && ClientAmbientFish.natural().stats().active >= 3, mc -> {
            check("ambientlifecycle.dimension.return", mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    && ClientAmbientFish.natural().stats().active >= 3, "new world automatically populated");
            before[0] = AmbientContextClient.store().session();
            worldCommand(mc, "tp @a 160 " + gy + " 160");
        });
        queue(10, mc -> {
            check("ambientlifecycle.teleport", before[0] != AmbientContextClient.store().session()
                    && ClientAmbientFish.natural().stats().individuals == 0, "old population and context session invalidated");
            FishtasticClientConfig.setAmbientDensityTestOverride(0);
        });
        queue(5, mc -> {
            check("ambientlifecycle.zero", !ClientAmbientFish.naturalArmed(), "zero density stops runtime");
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
            grill24.fishtastic.client.companion.ClientCompanionsDev.clearPhantoms();
        });
    }

    private static void queueAmbientPopulationScene() {
        if (remoteServer != null) {
            note("ambientpopulation skipped (needs the local pond fixture; a joined client cannot edit it)");
            return;
        }
        final int gy = origin.getY();
        // The pond sits at the context scene's proven absolute spot (inside one patch quantum); the
        // stand is different on purpose — spawns land within SPAWN_RADIUS (10 blocks) of the PLAYER,
        // and the context scene's (8, 16) is ~17 blocks out, close enough to discover the water but
        // far enough that nothing could ever spawn in it. From (18, 20) the whole near half of the
        // pond is inside the spawn disc and the player is still safely outside the pond (nearest
        // water cell 3.2 blocks away, past SPAWN_NEAREST).
        final int px = 24, pz = 24, standX = 18, standZ = 20;
        final BlockPos target = new BlockPos(px, gy, pz);

        final long[] key = {0L};
        final long[] laneFp = {0L};       // the fingerprint in force when the current lane settled
        final java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> tracked =
                new java.util.LinkedHashMap<>();
        final long[] statBase = new long[8];
        final java.util.Set<String> candidatesBefore = new java.util.LinkedHashSet<>();
        final java.util.Map<String, Integer> weightsBefore = new java.util.LinkedHashMap<>();
        final long[] dispatchBase = {0L};
        final long[] appliedBase = {0L};

        queue(1, mc -> note("ambientpopulation start " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState()));
        queue(2, mc -> worldCommand(mc, "tp @a " + standX + " " + gy + " " + standZ));
        queue(2, mc -> worldCommand(mc, "fill " + (px - 3) + " " + (gy - 2) + " " + (pz - 3) + " "
                + (px + 3) + " " + (gy - 1) + " " + (pz + 3) + " minecraft:air"));
        queue(2, mc -> worldCommand(mc, "fill " + (px - 3) + " " + (gy - 3) + " " + (pz - 3) + " "
                + (px + 3) + " " + (gy - 3) + " " + (pz + 3) + " minecraft:sand"));
        queue(2, mc -> worldCommand(mc, "fill " + (px - 3) + " " + (gy - 2) + " " + (pz - 3) + " "
                + (px + 3) + " " + (gy - 1) + " " + (pz + 3) + " minecraft:water keep"));
        queue(2, mc -> {
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            boolean armed = grill24.fishtastic.client.ambient.ClientAmbientFish.startNatural();
            check("ambientpopulation.runtime", armed, "target " + target.toShortString());
        });

        // ── baseline: no rod, the standard-worms pool, the pond populated from it
        waitFor(10, 800, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            if (sample == null || store.stateOf(sample.patchKey()) != grill24.fishtastic.client.ambient.AmbientContextStore.State.OK) {
                return false;
            }
            var ctx = store.freshContext(sample.patchKey());
            return ctx != null
                    && grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().appliedFingerprint(sample.patchKey())
                            == ctx.fingerprint()
                    && censusTotal(patchCensus(sample.patchKey())) >= 5;
        }, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var sample = nearestShownSample(target);
            var ctx = sample == null ? null : store.freshContext(sample.patchKey());
            if (ctx == null || sample == null) {
                check("ambientpopulation.baseline", false,
                        "no applied context for the pond; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                return;
            }
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            key[0] = sample.patchKey();
            laneFp[0] = ctx.fingerprint();
            var census = patchCensus(key[0]);
            var representable = runtime.diagnostics().representableSpecies(key[0]);
            boolean legal = census.keySet().stream().allMatch(representable::contains);
            check("ambientpopulation.baseline",
                    censusTotal(census) >= 5 && !ctx.heldRod() && !ctx.sessionSourced() && !representable.isEmpty() && legal,
                    "key=" + key[0] + " fp=" + Long.toHexString(laneFp[0]) + " census=" + censusText(census)
                            + " representable=" + representable.size() + "/" + ctx.candidates().size() + " legal=" + legal);
            note("ambientpopulation.baseline sampler " + samplerText(key[0]));
        });
        queue(1, mc -> worldCommand(mc, "tp @a 17.5 " + gy + " 24.5 -90 30"));
        queue(3, mc -> screenshot(mc, "ambientpopulation", "baseline"));
        queue(1, mc -> worldCommand(mc, "tp @a " + standX + " " + gy + " " + standZ));

        // ── lane W: rod + Gummy Worms — weight-only. Same species, different weights (rarity
        //    flattening 0.85), so the fingerprint moves and the sampler rebuilds; nobody is excluded
        //    and every tracked resident keeps its identity, patch and position.
        queue(2, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.weights.applied", false, "no OK context before the bait switch");
                return;
            }
            tracked.clear();
            tracked.putAll(snapshotResidents(key[0]));
            candidatesBefore.clear();
            candidatesBefore.addAll(candidateIds(ctx));
            weightsBefore.clear();
            weightsBefore.putAll(candidateWeights(ctx));
            System.arraycopy(populationStats(), 0, statBase, 0, statBase.length);
            note("ambientpopulation.weights before census=" + censusText(patchCensus(key[0]))
                    + " candidates=" + candidatesBefore.size() + " tracked=" + tracked.size());
            handRod(mc, "copper_fishing_rod", "gummy_worms", null);
        });
        waitFor(5, 600, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            return ctx != null && ctx.heldRod() && !ctx.sessionSourced() && ctx.fingerprint() != laneFp[0]
                    && runtime.diagnostics().appliedFingerprint(key[0]) == ctx.fingerprint();
        }, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var ctx = store.freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.weights.applied", false,
                        "no OK context after the bait switch; " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
                return;
            }
            boolean membershipSame = candidateIds(ctx).equals(candidatesBefore);
            boolean weightsMoved = !candidateWeights(ctx).equals(weightsBefore);
            check("ambientpopulation.weights.applied",
                    ctx.heldRod() && ctx.fingerprint() != laneFp[0] && membershipSame && weightsMoved,
                    "fp=" + Long.toHexString(laneFp[0]) + "->" + Long.toHexString(ctx.fingerprint())
                            + " candidates=" + ctx.candidates().size() + " membershipSame=" + membershipSame
                            + " weightsMoved=" + weightsMoved);
            if (membershipSame) laneFp[0] = ctx.fingerprint();
        });
        queue(20, mc -> {
            long[] now = populationStats();
            long[] carry = new long[7];
            String detail = carryReport(tracked, key[0], 4.0, statBase, carry);
            check("ambientpopulation.weights.carry",
                    carry[5] == 0 && carry[2] == 0 && carry[3] == 0 && carry[4] == 0,
                    "tracked=" + tracked.size() + " " + detail);
            check("ambientpopulation.weights.nonexcluded", now[0] - statBase[0] == 0,
                    "retireExcluded=+" + (now[0] - statBase[0]) + " samplerBuilds=+" + (now[5] - statBase[5])
                            + " (a weight-only change must not exclude anyone)");
            note("ambientpopulation.weights after census=" + censusText(patchCensus(key[0])));
        });

        // ── lane C: + Amethyst Charm — a fingerprint change with the candidates AND weights
        //    untouched (night bonus 1.0, no affinities). The rebuild must be as harmless as the
        //    weight-only move.
        queue(2, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.charm.applied", false, "no OK context before the charm switch");
                return;
            }
            tracked.clear();
            tracked.putAll(snapshotResidents(key[0]));
            candidatesBefore.clear();
            candidatesBefore.addAll(candidateIds(ctx));
            weightsBefore.clear();
            weightsBefore.putAll(candidateWeights(ctx));
            System.arraycopy(populationStats(), 0, statBase, 0, statBase.length);
            note("ambientpopulation.charm before census=" + censusText(patchCensus(key[0])));
            handRod(mc, "copper_fishing_rod", "gummy_worms", "amethyst_charm");
        });
        waitFor(5, 600, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            return ctx != null && ctx.fingerprint() != laneFp[0]
                    && runtime.diagnostics().appliedFingerprint(key[0]) == ctx.fingerprint();
        }, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.charm.applied", false, "no OK context after the charm switch");
                return;
            }
            boolean membershipSame = candidateIds(ctx).equals(candidatesBefore);
            boolean weightsSame = candidateWeights(ctx).equals(weightsBefore);
            check("ambientpopulation.charm.applied",
                    ctx.heldRod() && ctx.fingerprint() != laneFp[0] && membershipSame && weightsSame,
                    "fp=" + Long.toHexString(laneFp[0]) + "->" + Long.toHexString(ctx.fingerprint())
                            + " membershipSame=" + membershipSame + " weightsSame=" + weightsSame);
            if (membershipSame) laneFp[0] = ctx.fingerprint();
        });
        queue(20, mc -> {
            long[] now = populationStats();
            long[] carry = new long[7];
            String detail = carryReport(tracked, key[0], 4.0, statBase, carry);
            check("ambientpopulation.charm.carry",
                    carry[5] == 0 && carry[2] == 0 && carry[3] == 0 && carry[4] == 0,
                    "tracked=" + tracked.size() + " " + detail);
            check("ambientpopulation.charm.nonexcluded", now[0] - statBase[0] == 0,
                    "retireExcluded=+" + (now[0] - statBase[0]) + " samplerBuilds=+" + (now[5] - statBase[5]));
            note("ambientpopulation.charm after census=" + censusText(patchCensus(key[0])));
        });

        // ── lane Q: Amethyst → Crystal Ball — quality-only. The charms differ in input force and
        //    rarity outline, fields the resolver deliberately excludes; the client equipment hash
        //    still moves, so a dispatch must happen and apply, under the SAME fingerprint — and the
        //    runtime must not rebuild the sampler or touch a single individual.
        queue(2, mc -> {
            tracked.clear();
            tracked.putAll(snapshotResidents(key[0]));
            System.arraycopy(populationStats(), 0, statBase, 0, statBase.length);
            dispatchBase[0] = grill24.fishtastic.client.ambient.AmbientContextClient.dispatches();
            appliedBase[0] = grill24.fishtastic.client.ambient.AmbientContextClient.store().entriesApplied();
            note("ambientpopulation.quality before census=" + censusText(patchCensus(key[0]))
                    + " (amethyst -> crystal ball: same species inputs, different items)");
            handRod(mc, "copper_fishing_rod", "gummy_worms", "crystal_ball_charm");
        });
        waitFor(5, 400, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var ctx = store.freshContext(key[0]);
            return ctx != null && ctx.fingerprint() == laneFp[0]
                    && store.entriesApplied() > appliedBase[0]
                    && grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() > dispatchBase[0];
        }, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var ctx = store.freshContext(key[0]);
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            check("ambientpopulation.quality.applied",
                    ctx != null && ctx.fingerprint() == laneFp[0] && ctx.heldRod()
                            && runtime.diagnostics().appliedFingerprint(key[0]) == laneFp[0]
                            && store.entriesApplied() > appliedBase[0]
                            && grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() > dispatchBase[0],
                    "fp=" + (ctx == null ? "none" : Long.toHexString(ctx.fingerprint())) + " (unchanged "
                            + Long.toHexString(laneFp[0]) + ") dispatches=+"
                            + (grill24.fishtastic.client.ambient.AmbientContextClient.dispatches() - dispatchBase[0])
                            + " applied=+" + (store.entriesApplied() - appliedBase[0]));
        });
        queue(20, mc -> {
            long[] now = populationStats();
            long[] carry = new long[7];
            String detail = carryReport(tracked, key[0], 4.0, statBase, carry);
            check("ambientpopulation.quality.untouched",
                    carry[5] == 0 && carry[2] == 0 && carry[3] == 0 && carry[4] == 0
                            && now[5] - statBase[5] == 0 && now[0] - statBase[0] == 0,
                    "samplerBuilds=+" + (now[5] - statBase[5]) + " retireExcluded=+" + (now[0] - statBase[0])
                            + " tracked=" + tracked.size() + " " + detail);
        });

        // ── lane E: Blazed Grub — hard eligibility (the exotic-only exclusive pool). The residents
        //    whose species left the pool must fade out (REASON_EXCLUDED) inside the eligibility
        //    bound; the pond then refills from the new pool alone, so no resident may hold a species
        //    the new pool does not name. A second snapshot taken here carries into the return leg.
        final boolean[] reasonOk = {false};
        final String[] reasonDetail = {""};
        final long[] excludeTick = {0L};
        final int[] stage = {0};
        queue(2, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.excluded.retired", false, "no OK context before the eligibility switch");
                return;
            }
            tracked.clear();
            tracked.putAll(snapshotResidents(key[0]));
            System.arraycopy(populationStats(), 0, statBase, 0, statBase.length);
            note("ambientpopulation.excluded before census=" + censusText(patchCensus(key[0])));
            handRod(mc, "copper_fishing_rod", "blazed_grub", "crystal_ball_charm");
        });
        waitFor(1, 900, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var ctx = store.freshContext(key[0]);
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            if (ctx == null || runtime.diagnostics().appliedFingerprint(key[0]) != ctx.fingerprint()) return false;
            var representable = new java.util.HashSet<>(runtime.diagnostics().representableSpecies(key[0]));
            if (stage[0] == 0) {
                if (populationStats()[0] - statBase[0] < 1) return false;
                // The first tick with exclusions on the books: every resident now outside the pool
                // must already be fading, and with the eligibility reason — not frozen, not
                // mass-retired by some other path.
                int outside = 0, wrongReason = 0;
                for (var p : runtime.diagnostics().individuals()) {
                    if (p.patchKey() != key[0] || representable.contains(p.species())) continue;
                    outside++;
                    if (!p.retiring() || (p.retireReason() != grill24.fishtastic.client.ambient.AmbientFishRuntime.REASON_EXCLUDED
                            && p.retireReason() != grill24.fishtastic.client.ambient.AmbientFishRuntime.REASON_EXPIRED)) {
                        wrongReason++;
                    }
                }
                reasonOk[0] = outside > 0 && wrongReason == 0;
                reasonDetail[0] = "outsidePool=" + outside + " wrongReason=" + wrongReason;
                excludeTick[0] = mc.level.getGameTime();
                stage[0] = 1;
                return false;
            }
            // Settled the tick the last out-of-pool resident is gone (fading counts as gone here:
            // this leg measures the fade's own duration, so the refill wait belongs to its own pass).
            // With nothing representable the same predicate drains every member, by construction.
            for (var p : runtime.diagnostics().individuals()) {
                if (p.patchKey() == key[0] && !representable.contains(p.species())) return false;
            }
            return true;
        }, mc -> {
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            long[] now = populationStats();
            long fadeTicks = mc.level.getGameTime() - excludeTick[0];
            check("ambientpopulation.excluded.reasons",
                    reasonOk[0] && now[0] - statBase[0] >= 1,
                    "retireExcluded=+" + (now[0] - statBase[0]) + " " + reasonDetail[0]
                            + " (every resident outside the new pool was already fading when the first exclusion landed)");
            check("ambientpopulation.excluded.bound",
                    stage[0] == 1 && fadeTicks >= grill24.fishtastic.client.ambient.AmbientTuning.POPULATION_FADE_TICKS - 4
                            && fadeTicks <= grill24.fishtastic.client.ambient.AmbientTuning.POPULATION_FADE_TICKS + 6,
                    "apply-to-last-gone=" + fadeTicks + "t (spec ~2 s = "
                            + grill24.fishtastic.client.ambient.AmbientTuning.POPULATION_FADE_TICKS + "t, allowed "
                            + (grill24.fishtastic.client.ambient.AmbientTuning.POPULATION_FADE_TICKS - 4) + ".."
                            + (grill24.fishtastic.client.ambient.AmbientTuning.POPULATION_FADE_TICKS + 6)
                            + "t; the lower bound is what proves the fade ran rather than an instant clear)");
            note("ambientpopulation.excluded after census=" + censusText(patchCensus(key[0])));
        });
        // The grub pool now refills the pond on its own: within a bounded wait the census must be
        // non-empty, entirely inside the new representable set, and the tracking snapshot for the
        // return leg is taken from exactly that state.
        waitFor(5, 900, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var census = patchCensus(key[0]);
            var representable = runtime.diagnostics().representableSpecies(key[0]);
            return !representable.isEmpty() && censusTotal(census) >= 3
                    && census.keySet().stream().allMatch(new java.util.HashSet<>(representable)::contains);
        }, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var census = patchCensus(key[0]);
            var representable = new java.util.LinkedHashSet<>(runtime.diagnostics().representableSpecies(key[0]));
            boolean legal = census.keySet().stream().allMatch(representable::contains);
            check("ambientpopulation.grublane.legal",
                    legal && censusTotal(census) >= 3 && countOutOfPool(runtime, key[0]) == 0,
                    "census=" + censusText(census) + " representable=" + representable
                            + " outsidePoolNow=" + countOutOfPool(runtime, key[0]));
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx != null) laneFp[0] = ctx.fingerprint();
            tracked.clear();
            tracked.putAll(snapshotResidents(key[0]));
            note("ambientpopulation.grublane sampler " + samplerText(key[0]));
        });
        queue(1, mc -> worldCommand(mc, "tp @a 17.5 " + gy + " 24.5 -90 30"));
        queue(3, mc -> screenshot(mc, "ambientpopulation", "grublane"));
        queue(1, mc -> worldCommand(mc, "tp @a " + standX + " " + gy + " " + standZ));

        // ── lane R: back to no rod — the reverse hard change. The exotic residents common to both
        //    pools keep their identity; nobody is excluded, and the baseline pool names every one
        //    of them.
        queue(2, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.return.applied", false, "no OK context before removing the rod");
                return;
            }
            candidatesBefore.clear();
            candidatesBefore.addAll(candidateIds(ctx));
            // The grublane photograph moves the owner after its earlier census. Take the carry
            // snapshot and retirement baseline together, immediately before the equipment change:
            // a resident retired during that camera trip must not become an unexplained loss here.
            tracked.clear();
            tracked.putAll(snapshotResidents(key[0]));
            System.arraycopy(populationStats(), 0, statBase, 0, statBase.length);
            note("ambientpopulation.return before census=" + censusText(patchCensus(key[0]))
                    + " tracked=" + tracked.size());
            handRod(mc, null, null, null);
        });
        waitFor(5, 600, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            return ctx != null && !ctx.heldRod() && ctx.fingerprint() != laneFp[0]
                    && runtime.diagnostics().appliedFingerprint(key[0]) == ctx.fingerprint();
        }, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            if (ctx == null) {
                check("ambientpopulation.return.applied", false, "no OK context after removing the rod");
                return;
            }
            boolean superset = candidateIds(ctx).containsAll(candidatesBefore);
            check("ambientpopulation.return.applied",
                    !ctx.heldRod() && !ctx.sessionSourced() && ctx.fingerprint() != laneFp[0] && superset,
                    "fp=" + Long.toHexString(laneFp[0]) + "->" + Long.toHexString(ctx.fingerprint())
                            + " candidates=" + ctx.candidates().size() + " grubSubsetOfBaseline=" + superset);
            laneFp[0] = ctx.fingerprint();
        });
        queue(20, mc -> {
            long[] now = populationStats();
            long[] carry = new long[7];
            String detail = carryReport(tracked, key[0], 4.0, statBase, carry);
            check("ambientpopulation.return.carry",
                    carry[0] >= 1 && carry[5] == 0 && carry[2] == 0 && carry[3] == 0 && carry[4] == 0,
                    "tracked=" + tracked.size() + " " + detail);
            check("ambientpopulation.return.nonexcluded", now[0] - statBase[0] == 0,
                    "retireExcluded=+" + (now[0] - statBase[0]));
            note("ambientpopulation.return after census=" + censusText(patchCensus(key[0])));
        });

        // ── lane F: the long idle. Turnover under bounded, species-independent lifetimes; every
        //    live species inside the representable set at every sample; the observed occupancy
        //    against the sampler's own mixture, with the raw catch share beside it.
        final java.util.Map<String, Integer> occupancy = new java.util.LinkedHashMap<>();
        final int[] polls = {0};
        final int[] violations = new int[3]; // [illegalSpecies, ageOverLifetime, noRepresentableSet]
        final java.util.Set<Long> idsAtRunStart = new java.util.LinkedHashSet<>();
        final long[] runBase = new long[8];
        // The soak is 12000 ticks (about ten minutes of wall clock, 70% of the whole ambient pass), so it
        // runs only when the marker says `soak=on`; every other lane is unaffected.
        final boolean soak = "on".equals(param("soak", "off"));
        if (soak) queue(2, mc -> {
            System.arraycopy(populationStats(), 0, runBase, 0, runBase.length);
            idsAtRunStart.clear();
            for (var p : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().individuals()) {
                if (p.patchKey() == key[0] && !p.retiring()) idsAtRunStart.add(p.id());
            }
            note("ambientpopulation.longrun start census=" + censusText(patchCensus(key[0]))
                    + " ids=" + idsAtRunStart.size());
        });
        Consumer<Minecraft>[] longPoll = new Consumer[1];
        longPoll[0] = mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            long time = mc.level.getGameTime();
            var representable = runtime.diagnostics().representableSpecies(key[0]);
            if (representable.isEmpty()) {
                violations[2]++;
            } else if (polls[0] > 0) { // the first poll settles right after the return lane's checks
                var set = new java.util.HashSet<>(representable);
                for (var p : runtime.diagnostics().individuals()) {
                    if (p.patchKey() != key[0]) continue;
                    if (!set.contains(p.species())) violations[0]++;
                    if (!p.retiring() && time - p.bornTick() > p.lifetimeTicks() + 2) violations[1]++;
                }
            }
            for (var e : patchCensus(key[0]).entrySet()) occupancy.merge(e.getKey(), e.getValue(), Integer::sum);
            polls[0]++;
            if (polls[0] < LONG_RUN_POLLS) {
                STEPS.addFirst(new Step(LONG_RUN_EVERY, longPoll[0]));
                return;
            }
            long[] now = populationStats();
            long births = now[6] - runBase[6];
            long expired = now[1] - runBase[1];
            int survivors = 0;
            for (var p : runtime.diagnostics().individuals()) {
                if (p.patchKey() == key[0] && !p.retiring() && idsAtRunStart.contains(p.id())) survivors++;
            }
            check("ambientpopulation.longrun.turnover",
                    births >= 12 && expired >= 8 && survivors <= Math.max(1, idsAtRunStart.size() / 2)
                            && now[0] == runBase[0],
                    "spawns=+" + births + " retireExpired=+" + expired + " startIdsSurviving=" + survivors
                            + "/" + idsAtRunStart.size() + " retireExcluded=+" + (now[0] - runBase[0])
                            + " over " + (polls[0] * LONG_RUN_EVERY) + " ticks");
            check("ambientpopulation.longrun.lifetimes",
                    violations[1] == 0 && violations[0] == 0 && violations[2] == 0,
                    "agePastLifetime=" + violations[1] + " illegalSpecies=" + violations[0]
                            + " noPoolPolls=" + violations[2] + " over " + polls[0] + " polls (age checked per poll)");
            var sampler = runtime.diagnostics().samplerFor(key[0]);
            double total = 0;
            for (int value : occupancy.values()) total += value;
            String top = null, obsTop = null;
            double topShare = 0, obsTopShare = 0;
            boolean covered = true;
            for (var entry : sampler) {
                if (entry.share() > topShare) {
                    topShare = entry.share();
                    top = entry.species();
                }
                if (entry.share() >= 0.13 && !occupancy.containsKey(entry.species())) covered = false;
            }
            for (var entry : occupancy.entrySet()) {
                double share = entry.getValue() / Math.max(1, total);
                if (share > obsTopShare) {
                    obsTopShare = share;
                    obsTop = entry.getKey();
                }
            }
            check("ambientpopulation.longrun.occupancy",
                    top != null && top.equals(obsTop) && obsTopShare >= 0.12 && obsTopShare <= 0.60
                            && covered && total >= 300,
                    String.format(java.util.Locale.ROOT,
                            "observed top %s %.3f vs mixture top %s %.3f; covered(>=0.13)=%s slotPolls=%.0f",
                            obsTop, obsTopShare, top, topShare, covered, total));
            StringBuilder table = new StringBuilder();
            for (var entry : sampler) {
                table.append(String.format(java.util.Locale.ROOT, " %s obs=%.3f vis=%.3f raw=%.3f;",
                        entry.species().replace("fishtastic:", ""),
                        occupancy.getOrDefault(entry.species(), 0) / Math.max(1, total),
                        entry.share(), entry.rawShare()));
            }
            note("ambientpopulation.longrun observed-vs-mixture" + table);
        };
        if (soak) queue(LONG_RUN_EVERY, longPoll[0]);

        // ── lane D: walk out and back — the patch's random state must be retained (bounded by
        //    count and by age) and the return must resume the stream rather than re-seed it.
        final long[] retentionBase = new long[4]; // retainedStores, retainedRestores, retainedExpiries, domainsLost
        queue(2, mc -> {
            var s = grill24.fishtastic.client.ambient.ClientAmbientFish.natural().stats();
            retentionBase[0] = s.retainedStores;
            retentionBase[1] = s.retainedRestores;
            retentionBase[2] = s.retainedExpiries;
            retentionBase[3] = s.domainsLost;
            note("ambientpopulation.retention leaving census=" + censusText(patchCensus(key[0])));
        });
        queue(1, mc -> worldCommand(mc, "tp @a " + (px + 40) + " " + gy + " " + (pz + 20)));
        // Waited, not fixed-tick: the domain drop (fade + budgeted removals) and the column
        // eviction are both budgeted work, so "away long enough" is a predicate, not a guess.
        waitFor(10, 300, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            return runtime.diagnostics().patchColumns(key[0]) == 0
                    && runtime.stats().retainedStores > retentionBase[0]
                    && runtime.stats().domainsLost > retentionBase[3];
        }, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var s = runtime.stats();
            check("ambientpopulation.retention.dropped",
                    runtime.diagnostics().patchColumns(key[0]) == 0 && s.domainsLost > retentionBase[3]
                            && s.retainedStores > retentionBase[0]
                            && runtime.diagnostics().retainedStateCount() <= grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES,
                    "columns=" + runtime.diagnostics().patchColumns(key[0]) + " domainsLost=+" + (s.domainsLost - retentionBase[3])
                            + " retainedStores=+" + (s.retainedStores - retentionBase[0])
                            + " retained=" + runtime.diagnostics().retainedStateCount() + "/"
                            + grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES);
        });
        queue(1, mc -> worldCommand(mc, "tp @a " + standX + " " + gy + " " + standZ));
        waitFor(10, 900, mc -> {
            var sample = nearestShownSample(target);
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            return sample != null && store.stateOf(sample.patchKey()) == grill24.fishtastic.client.ambient.AmbientContextStore.State.OK
                    && grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().patchColumns(sample.patchKey()) >= 4
                    && censusTotal(patchCensus(sample.patchKey())) >= 1;
        }, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var s = runtime.stats();
            check("ambientpopulation.retention.resumed",
                    s.retainedRestores > retentionBase[1]
                            && runtime.diagnostics().retainedStateCount() <= grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES,
                    "retainedRestores=+" + (s.retainedRestores - retentionBase[1])
                            + " retainedExpiries=+" + (s.retainedExpiries - retentionBase[2])
                            + " retained=" + runtime.diagnostics().retainedStateCount() + "/"
                            + grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES
                            + " (the pond resumed its retained stream, not a fresh seed)");
            note("ambientpopulation.retention back census=" + censusText(patchCensus(key[0])));
        });

        queue(1, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var s = runtime.stats();
            // Every removal, finished or fading, was started with a reason: the reasons must cover
            // them (same ledger closure as the ambientwater scene).
            check("ambientpopulation.accounted",
                    s.retirements + s.retiring <= s.retireOpenFace + s.retireInvalid
                            + s.retireDomainLost + s.retireExcluded + s.retireExpired,
                    String.format(java.util.Locale.ROOT,
                            "spawns=%d retirements=%d+fading%d (openFace=%d invalid=%d domainLost=%d excluded=%d expired=%d)"
                                    + " samplerBuilds=%d omissions=%d exclusions=%d contextGaps=%d"
                                    + " retained stores=%d restores=%d expiries=%d",
                            s.spawns, s.retirements, s.retiring, s.retireOpenFace, s.retireInvalid,
                            s.retireDomainLost, s.retireExcluded, s.retireExpired, s.samplerBuilds,
                            s.unrepresentableOmissions, s.exclusions, s.contextGaps,
                            s.retainedStores, s.retainedRestores, s.retainedExpiries));
            note("ambientpopulation end " + grill24.fishtastic.client.ambient.AmbientContextClient.debugState());
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            check("ambientpopulation.cleanup",
                    !runtime.armed() && runtime.domainCount() == 0 && runtime.diagnostics().retainedStateCount() == 0,
                    "armed=" + runtime.armed() + " domains=" + runtime.domainCount()
                            + " retained=" + runtime.diagnostics().retainedStateCount());
        });
    }

    /** The Phase 5 counters, in one array: reasons 0-4, samplerBuilds 5, spawns 6, retirements 7. */
    private static long[] populationStats() {
        var s = grill24.fishtastic.client.ambient.ClientAmbientFish.natural().stats();
        return new long[]{s.retireExcluded, s.retireExpired, s.retireInvalid, s.retireOpenFace,
                s.retireDomainLost, s.samplerBuilds, s.spawns, s.retirements};
    }

    /** Live non-retiring residents of one patch by full species id, sorted. The scene's census. */
    private static java.util.TreeMap<String, Integer> patchCensus(long patchKey) {
        java.util.TreeMap<String, Integer> out = new java.util.TreeMap<>();
        for (var p : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().individuals()) {
            if (p.patchKey() != patchKey || p.retiring()) continue;
            out.merge(p.species(), 1, Integer::sum);
        }
        return out;
    }

    private static int censusTotal(java.util.Map<String, Integer> census) {
        int total = 0;
        for (int value : census.values()) total += value;
        return total;
    }

    /** {@code neon_tetra=3 blind_cave_tetra=1} for the log. */
    private static String censusText(java.util.Map<String, Integer> census) {
        StringBuilder sb = new StringBuilder();
        for (var entry : census.entrySet()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(entry.getKey().replace("fishtastic:", "")).append('=').append(entry.getValue());
        }
        return sb.length() == 0 ? "<empty>" : sb.toString();
    }

    /** One patch's sampler as a log table: {@code species w= weight vis= share raw= raw share}. */
    private static String samplerText(long patchKey) {
        StringBuilder sb = new StringBuilder();
        for (var entry : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().samplerFor(patchKey)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(entry.species().replace("fishtastic:", ""))
                    .append(" w=").append(entry.weight())
                    .append(String.format(java.util.Locale.ROOT, " vis=%.3f raw=%.3f", entry.share(), entry.rawShare()));
        }
        return sb.length() == 0 ? "<no sampler>" : sb.toString();
    }

    /** Non-retiring residents of one patch by id — immutable snapshots, safe to hold across ticks. */
    private static java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> snapshotResidents(long patchKey) {
        java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> out = new java.util.LinkedHashMap<>();
        for (var p : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().individuals()) {
            if (p.patchKey() == patchKey && !p.retiring()) out.put(p.id(), p);
        }
        return out;
    }

    /** How many members of a patch — fading ones included — hold a species outside its sampler. */
    private static int countOutOfPool(grill24.fishtastic.client.ambient.AmbientFishRuntime runtime, long patchKey) {
        var representable = new java.util.HashSet<>(runtime.diagnostics().representableSpecies(patchKey));
        int out = 0;
        for (var p : runtime.diagnostics().individuals()) {
            if (p.patchKey() == patchKey && !representable.contains(p.species())) out++;
        }
        return out;
    }

    /** The full species ids of a context's candidates, in order. */
    private static java.util.Set<String> candidateIds(grill24.fishtastic.client.ambient.AmbientContextStore.Context ctx) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (var candidate : ctx.candidates()) out.add(candidate.species().toString());
        return out;
    }

    private static java.util.Map<String, Integer> candidateWeights(grill24.fishtastic.client.ambient.AmbientContextStore.Context ctx) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (var candidate : ctx.candidates()) out.put(candidate.species().toString(), candidate.weight());
        return out;
    }

    /**
     * Identity carry for a snapshot of residents: how many are still present, with the same species
     * and patch, and roughly where they were. {@code out} receives
     * {@code [present, missing, speciesChanged, patchChanged, jumped, unaccounted, retiringNow]},
     * where {@code unaccounted} is the number of missing ids no retirement reason since
     * {@code statBase} can explain — the one figure that must be zero for a "nothing was disturbed"
     * claim. Returns the one-line detail for the log.
     */
    private static String carryReport(java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> before,
                                      long patchKey, double maxMove, long[] statBase, long[] out) {
        var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
        int present = 0, speciesChanged = 0, patchChanged = 0, jumped = 0, retiringNow = 0;
        double worst = 0;
        for (var p : runtime.diagnostics().individuals()) {
            var was = before.get(p.id());
            if (was == null) continue;
            present++;
            if (p.retiring()) retiringNow++;
            if (p.patchKey() != patchKey) patchChanged++;
            if (!p.species().equals(was.species())) speciesChanged++;
            double d = Math.sqrt(Math.pow(p.x() - was.x(), 2) + Math.pow(p.y() - was.y(), 2) + Math.pow(p.z() - was.z(), 2));
            worst = Math.max(worst, d);
            if (d > maxMove) jumped++;
        }
        int missing = before.size() - present;
        long[] now = populationStats();
        long reasons = 0;
        for (int i = 0; i <= 4; i++) reasons += now[i] - statBase[i];
        long unaccounted = Math.max(0, missing - reasons);
        out[0] = present;
        out[1] = missing;
        out[2] = speciesChanged;
        out[3] = patchChanged;
        out[4] = jumped;
        out[5] = unaccounted;
        out[6] = retiringNow;
        return String.format(java.util.Locale.ROOT,
                "present=%d/%d missing=%d unaccounted=%d speciesChanged=%d patchChanged=%d jumps>%.1f=%d worst=%.2f retiringNow=%d",
                present, before.size(), missing, unaccounted, speciesChanged, patchChanged, maxMove, jumped, worst, retiringNow);
    }

    /**
     * Hands the player a fishtastic rod through the creative-slot packet — the creative GUI's own
     * sequence, as in {@link #queueAmbientContextScene} phase B — with the named bait and charm
     * fitted ({@code null} leaves that slot empty, a null rod empties the hand for the no-rod lane).
     * The server drops the packet outside creative, so scenes that call this must move by
     * {@code tp}, never {@link #camera}.
     */
    private static void handRod(Minecraft mc, String rodItem, String baitItem, String charmItem) {
        ItemStack rod = rodItem == null ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", rodItem)));
        if (!rod.isEmpty()) {
            if (baitItem != null) {
                grill24.fishtastic.item.FishtasticFishingRodItem.setBait(rod,
                        new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", baitItem))));
            }
            if (charmItem != null) {
                grill24.fishtastic.item.FishtasticFishingRodItem.setCharm(rod,
                        new ItemStack(BuiltInRegistries.ITEM.get(Ids.of("fishtastic", charmItem))));
            }
        }
        int selected = mc.player.getInventory().selected;
        // The creative GUI's own sequence: write the local inventory, send the creative-slot
        // packet, resync the local menu (handleCreativeModeItemAdd alone is send-only, and the
        // server's handler suppresses the echo).
        mc.player.getInventory().setItem(selected, rod);
        mc.gameMode.handleCreativeModeItemAdd(rod, 36 + selected);
        mc.player.inventoryMenu.broadcastChanges();
        // Singleplayer only: also write the server's copy directly. After the ambientwater scene the
        // creative-slot packet above is not reflected in the server's main hand (cause not found; the
        // standalone scene and the GameTests prove the server side), which silently kept the worms lane.
        if (remoteServer == null) server(mc, s -> s.getPlayerList().getPlayers().get(0).getInventory().setItem(selected, rod.copy()));
        note("ambientpopulation handing " + (rod.isEmpty() ? "<emptied hand>"
                : rodItem + (baitItem == null ? "" : "+" + baitItem) + (charmItem == null ? "" : "+" + charmItem))
                + " to menu slot " + (36 + selected));
    }

    /**
     * Replays one origin against the integrated server's own resolve and checks the client's answer
     * against it in-process — the singleplayer half of "clients agree with authoritative species
     * contexts" (on a joined server the server's own response log is the authoritative side). The
     * replay goes through {@code buildResponse} with the client's real tokens, and reports the
     * server's resolution counters around it as evidence the work happened there.
     */
    private static void replayAgainstIntegratedServer(Minecraft mc, String label, BlockPos pos, long patchKey,
                                                      int geometryGeneration, int contextGeneration, long expectedFp,
                                                      boolean expectRod,
                                                      List<grill24.fishtastic.network.AmbientContextResponsePacket.Candidate> expectedCandidates,
                                                      long session, int requestGeneration) {
        server(mc, s -> {
            long resolvedBefore = grill24.fishtastic.server.AmbientContextServer.originsResolved();
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var request = new grill24.fishtastic.network.AmbientContextRequestPacket(session, requestGeneration,
                    List.of(new grill24.fishtastic.network.AmbientContextRequestPacket.Origin(
                            player.level().dimension().location(), patchKey, geometryGeneration, contextGeneration, pos)));
            var response = grill24.fishtastic.server.AmbientContextServer.buildResponse(player, request,
                    player.level().getGameTime());
            if (response == null || response.entries().size() != 1) {
                check("ambientcontext.replay." + label, false,
                        response == null ? "throttled (a supported no-answer)" : "entries=" + response.entries().size());
                return;
            }
            var entry = response.entries().get(0);
            boolean fpOk = entry.fingerprint() == expectedFp;
            boolean laneOk = entry.heldRod() == expectRod && !entry.sessionSourced();
            boolean candidatesOk = entry.candidates().equals(expectedCandidates);
            check("ambientcontext.replay." + label, fpOk && laneOk && candidatesOk
                            && entry.status() == grill24.fishtastic.network.AmbientContextResponsePacket.Status.OK,
                    "server fp=" + Long.toHexString(entry.fingerprint()) + " client fp=" + Long.toHexString(expectedFp)
                            + " rod=" + entry.heldRod() + "(want " + expectRod + ") session=" + entry.sessionSourced()
                            + " n=" + entry.candidates().size() + " candidatesEqual=" + candidatesOk
                            + " resolved=+" + (grill24.fishtastic.server.AmbientContextServer.originsResolved() - resolvedBefore));
        });
    }

    /**
     * Bounded readiness for a water fixture whose discovery and population arrive incrementally.
     */
    private static boolean ambientWaterReady(int minFish, boolean expectOpenFace) {
        var runtime = ClientAmbientFish.natural();
        BlockPos player = Minecraft.getInstance().player.blockPosition();
        var patches = new java.util.HashSet<Long>();
        int near = 0;
        for (var fish : runtime.diagnostics().individuals()) {
            if (fish.retiring() || Math.hypot(fish.x() - (player.getX() + 0.5),
                    fish.z() - (player.getZ() + 0.5)) > 12) continue;
            near++;
            patches.add(fish.patchKey());
        }
        if (near < minFish) return false;
        if (!expectOpenFace) return true;
        // A discovery sweep shares its budget with near scans and revalidation. Wait for it to
        // reach the expected horizon instead of asserting a boundary before it has been scanned.
        return runtime.diagnostics().domainReports().stream().anyMatch(r -> patches.contains(r.patchKey())
                && (r.openMinL() || r.openMaxL() || r.openMinY() || r.openMaxY() || r.openMinD() || r.openMaxD()));
    }

    /** Reads actual fixture containment, surface clearance, population and boundary state. */
    private static void ambientWaterFixture(String fixture, int minDomains, int minFish, int minPatches, boolean expectOpenFace) {
        var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
        var stats = runtime.stats();
        BlockPos player = Minecraft.getInstance().player.blockPosition();
        List<grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> near = new java.util.ArrayList<>();
        for (var p : runtime.diagnostics().individuals()) {
            if (Math.hypot(p.x() - (player.getX() + 0.5), p.z() - (player.getZ() + 0.5)) <= 12.0) near.add(p);
        }
        java.util.Set<Long> patches = new java.util.LinkedHashSet<>();
        int outsideWater = 0, unfit = 0, nonFinite = 0, inFadeBand = 0, retiring = 0, leaving = 0;
        double minSurface = Double.MAX_VALUE, minOpen = Double.MAX_VALUE, minBed = Double.MAX_VALUE;
        for (var p : near) {
            patches.add(p.patchKey());
            if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z())) nonFinite++;
            // A fish leaving through an open face is on its way out, so containment is asserted on
            // the residents; the run-wide counters behind it include the whole population.
            if (p.retiring()) {
                retiring++;
                // An INVALID retirement is the designed answer to a resident whose body the rebuilt
                // water cannot host, and its fade is transient — it is not this fixture's water
                // failing. Neither is a Phase 5 retirement: an individual whose species left the
                // pool (EXCLUDED) or whose own lifetime ran out (EXPIRED) fades wherever it stands,
                // in closed water as much as open. Only the boundary and domain-loss reasons are
                // the water's own doing, so only those count as "leaving".
                if (p.retireReason() != grill24.fishtastic.client.ambient.AmbientFishRuntime.REASON_INVALID
                        && p.retireReason() != grill24.fishtastic.client.ambient.AmbientFishRuntime.REASON_EXCLUDED
                        && p.retireReason() != grill24.fishtastic.client.ambient.AmbientFishRuntime.REASON_EXPIRED) {
                    leaving++;
                }
                continue;
            }
            if (!p.inWater()) outsideWater++;
            if (!p.bodyFits()) {
                unfit++;
                note("ambientwater." + fixture + ".unfit " + p);
            }
            minSurface = Math.min(minSurface, p.surfaceClearance());
            minBed = Math.min(minBed, p.bedClearance());
            minOpen = Math.min(minOpen, p.openFaceDistance());
            if (p.openFaceDistance() <= grill24.fishsim.domain.WaterDomain.OPEN_FACE_MARGIN) inFadeBand++;
        }
        boolean openFaceNear = false;
        int columns = 0;
        for (var r : runtime.diagnostics().domainReports()) {
            if (!patches.contains(r.patchKey()) || r.columns() <= 0) continue;
            columns += r.columns();
            if (r.openMinL() || r.openMaxL() || r.openMinY() || r.openMaxY() || r.openMinD() || r.openMaxD()) openFaceNear = true;
        }
        check("ambientwater." + fixture + ".domains", runtime.domainCount() >= minDomains,
                "domains=" + runtime.domainCount() + " expected>=" + minDomains);
        check("ambientwater." + fixture + ".fish", near.size() >= minFish && patches.size() >= minPatches,
                "near=" + near.size() + " expected>=" + minFish + " patches=" + patches + " expected>=" + minPatches
                        + " active=" + stats.active + " retiring=" + stats.retiring + " omissions=" + stats.omissions);
        // Phase 2's accepted contract allows a body to brush a bank under soft avoidance. Whole-
        // body fit is promised at spawn (checked separately after placement) and at rebuild; the
        // active engine's per-step invariant is centre containment, including surface head-room.
        check("ambientwater." + fixture + ".contained", near.size() > 0 && outsideWater == 0 && nonFinite == 0,
                "outsideWater=" + outsideWater + " bodyFitsFailures=" + unfit + " nonFinite=" + nonFinite
                        + " retiring=" + retiring);
        check("ambientwater." + fixture + ".surface", near.size() > 0 && minSurface > 0.30,
                String.format("minSurfaceClearance=%.3f (residents only, 0.30 required)", minSurface));
        check("ambientwater." + fixture + ".boundary",
                near.size() > 0 && (expectOpenFace ? openFaceNear : minOpen > 1e30 && leaving == 0),
                !expectOpenFace ? String.format("closed water: minOpenFace=%.1f retiring=%d leaving=%d minBedClearance=%.3f",
                        minOpen, retiring, leaving, minBed)
                        : String.format("open face in range: insideFadeBandNow=%d minOpenFace=%.3f", inFadeBand, minOpen));
        check("ambientwater." + fixture + ".water", columns > 0 && near.size() > 0,
                "columns=" + columns + " insideFadeBandNow=" + inFadeBand);
    }

    /** How many species close-ups the water scene shoots; fewer if fewer species are present. */
    private static final int CLOSEUP_SHOTS = 4;

    /**
     * Frames, checks and shoots up to {@link #CLOSEUP_SHOTS} close-ups, choosing at shot time the
     * most abundant species that has not been shot yet. Phase 5 makes the pool the server's answer
     * rather than a fixed list, so the shot no longer names a species up front: picking by live
     * census keeps the appearance evidence on whatever is really in the water, and each shot is
     * still an assertion that the chosen species has a live individual to photograph. Polled every
     * 20 ticks for up to 400 — an individual can be mid-fade or waiting on the spawn budget — and
     * the chain is strictly serial so each species is shot from the camera aimed at it; its steps
     * jump the queue, because the whole scene is queued up front and a step appended normally would
     * run after every later fixture had been built and the population had gone.
     */
    private static void queueSpeciesCloseup(int shotIndex, int attempt, java.util.Set<String> done) {
        if (shotIndex >= CLOSEUP_SHOTS) return;
        STEPS.addFirst(new Step(20, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            java.util.Map<String, Integer> census = new java.util.LinkedHashMap<>();
            for (var p : runtime.diagnostics().individuals()) {
                if (p.retiring() || done.contains(p.species())) continue;
                census.merge(p.species(), 1, Integer::sum);
            }
            String sp = null;
            int most = 0;
            for (var entry : census.entrySet()) {
                if (entry.getValue() > most) {
                    most = entry.getValue();
                    sp = entry.getKey();
                }
            }
            final String chosen = sp;
            if (chosen == null) {
                if (attempt < 20) {
                    queueSpeciesCloseup(shotIndex, attempt + 1, done);
                    return;
                }
                // Nothing left to photograph: the population is what it is. The fixture checks above
                // already asserted it is non-empty and hostable, so a short close-up set is not a
                // failure — it is the pool's honest size this run.
                note("ambientwater.close.done after " + shotIndex + " shots; no unshot species left ("
                        + runtime.diagnostics().pool().size() + " representable)");
                return;
            }
            final String shot = chosen.substring(chosen.indexOf(':') + 1);
            done.add(chosen);
            var found = runtime.diagnostics().individuals().stream()
                    .filter(p -> p.species().equals(chosen) && !p.retiring()).findFirst().orElse(null);
            if (found == null) {
                queueSpeciesCloseup(shotIndex, attempt + 1, done);
                return;
            }
            camera(mc, found.x(), found.y() + 0.32, found.z() + 1.25, 180f, 4f);
            check("ambientwater.close." + shot, true, String.format(
                    "id=%d patch=%d length=%.2f yaw=%.0f at %.2f/%.2f/%.2f", found.id(), found.patchKey(),
                    found.length(), found.yaw(), found.x(), found.y(), found.z()));
            STEPS.addFirst(new Step(3, mc2 -> {
                screenshot(mc2, "ambientwater", "close_" + shot);
                queueSpeciesCloseup(shotIndex + 1, 0, done);
            }));
        }));
    }

    /**
     * Identity carry: over forty ticks — two whole rebuilds — every tracked individual keeps its id
     * and its patch key, its domain keeps its origin, and nothing jumps. A recentre that reset the
     * population, or a rebuild that rebased the local frame, would show up here as moved origins or
     * teleported fish.
     */
    private static void queueAmbientCarryCheck() {
        final java.util.Map<Long, double[]> before = new java.util.LinkedHashMap<>();
        final java.util.Map<Long, int[]> origins = new java.util.LinkedHashMap<>();
        queue(1, mc -> {
            before.clear();
            origins.clear();
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            for (var p : runtime.diagnostics().individuals()) before.put(p.id(), new double[]{p.x(), p.y(), p.z(), p.patchKey()});
            for (var r : runtime.diagnostics().domainReports()) {
                origins.put(r.patchKey(), new int[]{r.originX(), r.originY(), r.originZ()});
            }
            check("ambientwater.carry.snapshot", before.size() >= 4, "tracked=" + before.size()
                    + " rebuilds=" + runtime.stats().rebuilds);
        });
        queue(40, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            int survived = 0, keyChanged = 0, jumped = 0, originMoved = 0;
            double worst = 0;
            for (var p : runtime.diagnostics().individuals()) {
                double[] was = before.get(p.id());
                if (was == null) continue;
                survived++;
                if ((long) was[3] != p.patchKey()) keyChanged++;
                double d = Math.sqrt(Math.pow(p.x() - was[0], 2) + Math.pow(p.y() - was[1], 2) + Math.pow(p.z() - was[2], 2));
                worst = Math.max(worst, d);
                if (d > 4.0) jumped++;
            }
            for (var r : runtime.diagnostics().domainReports()) {
                int[] was = origins.get(r.patchKey());
                if (was != null && (was[0] != r.originX() || was[1] != r.originY() || was[2] != r.originZ())) originMoved++;
            }
            check("ambientwater.carry.survived", survived >= 4, "survived=" + survived + "/" + before.size()
                    + " rebuilds=" + runtime.stats().rebuilds);
            check("ambientwater.carry.anchored", keyChanged == 0 && originMoved == 0 && jumped == 0,
                    String.format("keyChanged=%d originMoved=%d jumped>4=%d worst=%.2f blocks in 40 ticks",
                            keyChanged, originMoved, jumped, worst));
        });
    }

    /**
     * The scan band leaves the water: the camera rises until the band starts above the ocean's own
     * surface, so every stored column reaches below it and revalidation through this band cannot
     * see a drop of the water. Forty ticks of that must defer — the review's version read "no
     * water" up here and deleted live columns with the patch built on them — so the columns, the
     * key and every resident have to be exactly as they were while the deferred count climbs.
     *
     * <p>It opens with two seconds at the water line: whatever the previous camera moves left the
     * cache in, the sweep re-scans it back down there, and the count then means something. From the
     * hover it never could — the band is above the water, so an uncached column reads dry and is
     * never re-cached, the sweep's budget drains on the same holes every lap, and the deferral this
     * watches for starves below its threshold (measured: 107 defers, not 600).
     */
    private static void queueAmbientBandWatchCheck(int x, int y, int z) {
        final java.util.Set<Long> tracked = new java.util.LinkedHashSet<>();
        final long[] base = new long[8]; // key, columns, deferred, domainsLost, retireDomainLost, retireInvalid, retireExcluded, retireExpired
        queue(1, mc -> camera(mc, x + 0.5, y - 0.2, z + 0.5, -90f, 2f));
        queue(40, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            tracked.clear();
            base[0] = 0L;
            for (var p : runtime.diagnostics().individuals()) {
                if (base[0] == 0L) base[0] = p.patchKey();
                // Only residents that are actually swimming: Phase 5 turnover retires individuals
                // continuously (EXCLUDED/EXPIRED), and one already mid-fade at the snapshot can be
                // gone before the window closes without any retirement starting inside it. Stable
                // ones can only leave through a retirement this window will count.
                if (p.retiring()) continue;
                tracked.add(p.id());
            }
            base[1] = runtime.diagnostics().patchColumns(base[0]);
            var s = runtime.stats();
            base[2] = s.revalidationsDeferred;
            base[3] = s.domainsLost;
            base[4] = s.retireDomainLost;
            base[5] = s.retireInvalid;
            base[6] = s.retireExcluded;
            base[7] = s.retireExpired;
            check("ambientwater.bandwatch.snapshot", tracked.size() >= 4 && base[1] >= 4,
                    "tracked=" + tracked.size() + " key=" + base[0] + " columns=" + base[1]
                            + " deferred=" + base[2]);
        });
        queue(1, mc -> camera(mc, x + 0.5, y + 18.0, z + 0.5, -90f, 60f));
        queue(40, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            int present = 0, retiring = 0;
            for (var p : runtime.diagnostics().individuals()) {
                if (!tracked.contains(p.id())) continue;
                present++;
                if (p.retiring()) retiring++;
            }
            var s = runtime.stats();
            int columns = runtime.diagnostics().patchColumns(base[0]);
            long benign = (s.retireExcluded - base[6]) + (s.retireExpired - base[7]);
            // The ocean's corners lie beyond the eviction radius (dx^2+dz^2 > 32^2), so eviction
            // sheds those 24 columns whenever the sweep has just re-cached them and the count
            // breathes between 2377 and 2401. Allow that band — the hazard this watches for is a
            // band that cannot see the water deleting live columns, which collapses the patch,
            // not the eviction of its own horizon.
            //
            // A tracked resident may leave through Phase 5 turnover, which is benign and counted:
            // a retirement beginning inside the window still shows the member fading for its 40
            // ticks, but this covers both the fading and the gone. A retirement that began before
            // the snapshot cannot have been tracked (only non-retiring residents are).
            check("ambientwater.bandwatch.kept",
                    columns >= base[1] - 24 && columns >= 4 && present + benign >= tracked.size()
                            && s.domainsLost == base[3] && s.retireDomainLost == base[4]
                            && s.retireInvalid == base[5],
                    "columns=" + columns + "/" + base[1] + " present=" + present + "/" + tracked.size()
                            + " retiring=" + retiring + " benign=+" + benign
                            + " domainsLost=+" + (s.domainsLost - base[3])
                            + " retireDomainLost=+" + (s.retireDomainLost - base[4])
                            + " retireInvalid=+" + (s.retireInvalid - base[5]));
            check("ambientwater.bandwatch.deferred", s.revalidationsDeferred - base[2] >= 200,
                    "deferred=+" + (s.revalidationsDeferred - base[2])
                            + " over 40 ticks with the band above the water");
        });
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
     *
     * <p>[1.20.1] there is no {@code /tick freeze} (1.20.3+), so the flocks keep swimming between
     * shots; nothing the checks read depends on it.
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
            // (26.1.2 also lifts the AFK frame limiter here; 1.20.1 has none.)
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
                    waitFor(1, 600, mc -> mc.levelRenderer.hasRenderedAllChunks(), mc -> {
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

    // ── bakebench / scalebench: what a cosmetic structure costs (client/perf/CosmeticCostBench) ──

    /**
     * Geometry and cold-cache bake time of every shipped structure ({@code [bakebench] STRUCT} lines),
     * with the vertex size of each chunk layer. Marker param: {@code reps=} timed bakes per structure.
     */
    private static void queueBakeBenchScene() {
        int reps = Integer.parseInt(param("reps", "21"));
        queue(40, mc -> {
            var registry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
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
        queue(1, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
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
                var registry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
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
                            var registry = level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
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
                            q += baked.untinted().size() + baked.tinted().size();
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
                        waitFor(1, 600, mc -> skip[0] || mc.levelRenderer.hasRenderedAllChunks(), mc -> {
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
        FishtasticItemData.setBundleContents(pile, new grill24.fishtastic.component.BundleContents(contents));
        ItemStack pileBlock = pile.copy();
        pileBlock.getOrCreateTag().putInt("CustomModelData", grill24.fishtastic.client.util.FishPileIcons.PILE_BLOCK_MARKER);
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
    /** Visual regression: discovery rays behind several notification banners. */
    private static void queueCelebrationOverlapScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            mc.getToasts().clear();
            mc.setScreen(null);

            server(mc, s -> run(s, "gamemode creative @a"));
        });
        queue(20, mc -> {

            mc.setScreen(null);
            var preview = grill24.fishtastic.util.FishingMinigameAnimation.createCelebrationPreview(
                    grill24.fishtastic.util.CatchCelebration.Tier.DISCOVERY,
                    new ItemStack(FishtasticItems.BLUEGILL.value()));
            ((IGameRendererExtension) mc.gameRenderer).fishtastic$displayItemActivation(preview);
            for (String quest : new String[]{"bluegill_novice", "bluegill_adept", "bluegill_master"}) {
                QuestProgressNotificationManager.getInstance().enqueue(new QuestProgressEvent(
                        Ids.of("fishtastic", "mastery/" + quest), 2, 3, 5, false, ItemStack.EMPTY));
            }
        });
        queue(25, mc -> screenshot(mc, "celebrationoverlap", "rays_early"));
        queue(15, mc -> screenshot(mc, "celebrationoverlap", "rays_late"));
        queue(1, mc -> ((IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation());
        queue(1, mc -> screenshot(mc, "celebrationoverlap", "without_rays"));
    }

    private static void queueHudScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            mc.getToasts().clear();
            FishtasticHudLayers.toastPassFrames = 0;
            server(mc, s -> run(s, "gamemode creative @a"));
            QuestProgressNotificationManager.getInstance().enqueue(new QuestProgressEvent(
                    Ids.of("fishtastic", "mastery/bluegill_novice"), 2, 3, 5, false, ItemStack.EMPTY));
            SystemToast.add(mc.getToasts(), SystemToast.SystemToastIds.NARRATOR_TOGGLE,
                    Component.literal("Vanilla toast"),
                    Component.literal("shares the banner's corner"));
        });
        // Two shots: the banner's slide-in is staggered (QuestProgressNotificationManager's
        // nextStaggerDelay), so the first can catch it part-way across.
        queue(20, mc -> screenshot(mc, "hud", "banner_and_toast"));
        queue(20, mc -> {
            check("hud.noScreen", mc.screen == null, "screen=" + mc.screen);
            check("hud.toastOnScreen",
                    mc.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.NARRATOR_TOGGLE) != null,
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

    /** The actual companion menu and packet path, with optional custom shortcuts unbound. */
    private static void queueCompanionTutorialScene() {
        queue(1, mc -> {
            mc.options.hideGui = false;
            FishtasticKeyBinds.bondCompanion.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            FishtasticKeyBinds.openFishEncyclopedia.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            server(mc, s -> {
                run(s, "gamemode survival @a");
                run(s, "clear @a");
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    var data = FishCatchSavedData.getOrCreate(s);
                    data.setTutorialStep(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                    grill24.fishtastic.network.TutorialSyncPacket.sendToPlayer(player, grill24.fishtastic.tutorial.TutorialStep.COMPLETE);
                    data.setEncyclopediaTutorialStep(player, grill24.fishtastic.tutorial.EncyclopediaTutorialStep.COMPLETE);
                    grill24.fishtastic.network.EncyclopediaTutorialSyncPacket.sendToPlayer(player, grill24.fishtastic.tutorial.EncyclopediaTutorialStep.COMPLETE);
                    grill24.fishtastic.companion.CompanionStorage.clear(player);
                    data.setCompanionTutorialStep(player, grill24.fishtastic.tutorial.CompanionTutorialStep.NOT_STARTED);
                    data.getOrCreateQuestState(player).unlockCompanionBond(Ids.of("fishtastic", "bluegill"));
                    grill24.fishtastic.tutorial.CompanionTutorialManager.onBondUnlocked(player);
                    player.getInventory().setItem(1, fish("bluegill", 25));
                    ItemStack pile = item("pile_of_fish");
                    FishtasticItemData.setBundleContents(pile, new grill24.fishtastic.component.BundleContents(List.of(
                            fish("bluegill", 20),
                            fish("bluegill", 30))));
                    player.getInventory().setItem(2, pile);
                    grill24.fishtastic.network.QuestSyncPacket.sendToPlayer(player, data);
                }
            });
        });
        queue(25, mc -> {
            mc.getToasts().clear();
            screenshot(mc, "companiontutorial", "book_hint");
            useTutorialBook(mc, FishtasticItems.FISHOPEDIA.value());
        });
        queue(30, mc -> {
            screenshot(mc, "companiontutorial", "encyclopedia");
            check("companiontutorial.main_has_no_entry_button",
                    mc.screen instanceof grill24.fishtastic.client.FishEncyclopediaScreen encyclopedia
                            && !encyclopedia.hasCompanionEntryButton(), "main encyclopedia");
            mc.setScreen(null);
            grill24.fishtastic.client.FishEncyclopediaScreen.selectOnNextOpen(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(FishtasticItems.BLUEGILL.value()));
        });
        queue(15, mc -> useTutorialBook(mc, FishtasticItems.FISHOPEDIA.value()));
        queue(35, mc -> {
            screenshot(mc, "companiontutorial", "species_entry");
            check("companiontutorial.species_has_choose_fish",
                    mc.screen instanceof grill24.fishtastic.client.FishEncyclopediaScreen encyclopedia
                            && encyclopedia.hasCompanionEntryButton(), "unlocked species entry");
            grill24.fishtastic.client.CompanionScreen.open(new ItemStack(FishtasticItems.BLUEGILL.value()));
        });
        queue(20, mc -> {
            checkCompanionLesson("equip", grill24.fishtastic.tutorial.CompanionTutorialStep.EQUIP);
            screenshot(mc, "companiontutorial", "equip");
            ((grill24.fishtastic.client.CompanionScreen) mc.screen).clickSlot(
                    grill24.fishtastic.menu.CompanionMenu.INVENTORY_END - 8, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
        });
        queue(15, mc -> {
            checkCompanionLesson("return", grill24.fishtastic.tutorial.CompanionTutorialStep.RETURN);
            screenshot(mc, "companiontutorial", "return");
            ((grill24.fishtastic.client.CompanionScreen) mc.screen).clickSlot(0, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        });
        queue(8, mc -> {
            checkCompanionLesson("plain_pickup_returns", grill24.fishtastic.tutorial.CompanionTutorialStep.REEQUIP);
            ((grill24.fishtastic.client.CompanionScreen) mc.screen).clickSlot(
                    grill24.fishtastic.menu.CompanionMenu.INVENTORY_END - 8, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        });
        queue(15, mc -> {
            checkCompanionLesson("reequip", grill24.fishtastic.tutorial.CompanionTutorialStep.REEQUIP);
            screenshot(mc, "companiontutorial", "reequip");
            ((grill24.fishtastic.client.CompanionScreen) mc.screen).clickSlot(
                    grill24.fishtastic.menu.CompanionMenu.INVENTORY_END - 8, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
        });
        queue(15, mc -> {
            checkCompanionLesson("lead", grill24.fishtastic.tutorial.CompanionTutorialStep.LEAD);
            screenshot(mc, "companiontutorial", "lead");
            pressCompanionButton(mc, "tutorial.fishtastic.companion.next");
        });
        queue(15, mc -> {
            checkCompanionLesson("rename", grill24.fishtastic.tutorial.CompanionTutorialStep.RENAME);
            ((grill24.fishtastic.client.CompanionScreen) mc.screen).openEditor(0);
        });
        queue(10, mc -> {
            screenshot(mc, "companiontutorial", "rename");
            mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.EditBox)
                    .map(child -> (net.minecraft.client.gui.components.EditBox) child).findFirst().orElseThrow().setValue("Bubbles");
            pressCompanionButton(mc, ""); // The save button's check mark has no translation key.
        });
        queue(15, mc -> {
            checkCompanionLesson("groups", grill24.fishtastic.tutorial.CompanionTutorialStep.GROUPS);
            screenshot(mc, "companiontutorial", "groups");
            pressCompanionButton(mc, "tutorial.fishtastic.companion.next");
        });
        queue(15, mc -> {
            checkCompanionLesson("piles", grill24.fishtastic.tutorial.CompanionTutorialStep.PILES);
            pressCompanionButton(mc, "screen.fishtastic.companion.piles");
        });
        queue(10, mc -> {
            screenshot(mc, "companiontutorial", "piles");
            pressCompanionButton(mc, "tutorial.fishtastic.companion.finish");
        });
        queue(15, mc -> {
            checkCompanionLesson("complete", grill24.fishtastic.tutorial.CompanionTutorialStep.COMPLETE);
            screenshot(mc, "companiontutorial", "complete");
            if (mc.screen != null) mc.screen.onClose();
        });
    }

    private static void checkCompanionLesson(String name, grill24.fishtastic.tutorial.CompanionTutorialStep expected) {
        var actual = grill24.fishtastic.client.CompanionTutorialClientHandler.currentStep();
        check("companiontutorial." + name, actual == expected, "expected=" + expected + " actual=" + actual);
    }

    private static void pressCompanionButton(Minecraft mc, String translation) {
        String label = translation.isEmpty() ? "✓" : Component.translatable(translation).getString();
        var button = mc.screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                .map(child -> (net.minecraft.client.gui.components.Button) child)
                .filter(child -> child.visible && child.active && child.getMessage().getString().equals(label)).findFirst();
        check("companiontutorial.button", button.isPresent(), "label=" + label);
        button.ifPresent(child -> child.onPress());
    }

    /** Books-first tutorial at a small window size with all custom menu shortcuts unbound. */
    /** Stable three-player podium fixture for inspecting foot contact across renderer versions. */
    private static void queueLeaderboardScene() {
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

    private static void queueTutorialScene() {
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
            grill24.fishtastic.client.TutorialClientHandler.onMinigameImpulse();
        });
        queue(65, mc -> {
            screenshot(mc, "tutorial", "minigame_controls");
            grill24.fishtastic.client.TutorialClientHandler.onMinigameImpulse();
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
        queue(15, mc -> grill24.fishtastic.network.NetworkApiSided.sendToServer(
                new grill24.fishtastic.network.CompleteQuestPacket(grill24.fishtastic.tutorial.TutorialManager.TUTORIAL_QUEST_KEY.location()),
                grill24.fishtastic.network.CompleteQuestPacket.STREAM_CODEC));
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

    private static void useTutorialBook(Minecraft mc, net.minecraft.world.item.Item book) {
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getItem(i).is(book)) continue;
            mc.player.getInventory().selected = i;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return;
        }
        check("tutorial.has_book", false, "missing " + book);
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

    // ── Companion bond (stage 3 client verification) ─────────────────────────

    private static void paradeSetup(Minecraft mc) {
        mc.options.hideGui = false;
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        server(mc, s -> {
            run(s, "gamemode creative @a");
            run(s, "time set 6000");
            run(s, "kill @e[type=!player]");
            run(s, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f",
                    origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 0.5, 180f, 0f));
        });
    }

    /**
     * The party slot as the integrated server stores it. 1.20.1 keeps the party in server save data,
     * so a client-side read is empty until party sync (F09). {@code slot -1}: first non-empty slot.
     */
    private static ItemStack partyServer(Minecraft mc, int slot) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        if (slot >= 0) return grill24.fishtastic.companion.CompanionStorage.get(player, slot);
        for (int i = 0; i < 3; i++) {
            ItemStack stack = grill24.fishtastic.companion.CompanionStorage.get(player, i);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void containerClick(Minecraft mc, int slot, net.minecraft.world.inventory.ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, type, mc.player);
    }

    /** Reads a private static field (test-only peek at CompanionBondInput's tracked screen). */
    private static Object peekStatic(Class<?> type, String field) {
        try {
            var f = type.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            return "ERR " + e;
        }
    }

    /**
     * Stage-3 client verification (F08): the bond-unlock banner and quest-sync round trip, the
     * hold-to-bond mixin (render-head tick + tooltip hint), the feedback packet handler, the
     * companion screen driven by real container clicks and packets, and the encyclopedia bond UI.
     */
    private static void queueCompanionSimScene() {
        queue(1, RenderSelfTest::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true));
        // One fish is one body now (party spec §3): a small bluegill is a small solo, not a trio,
        // and every fish is drawn at its true size (D11).
        int[] perchBefore = {0};
        String[][] fish = {{"bluegill", "9", "small_solo"}, {"bluegill", "25", "solo"}, {"starfish", "20", "benthic"},
                {"garden_eel", "40", "anchored"}, {"glass_squid", "20", "drift"}, {"giant_manta_ray", "150", "glide"}};
        queue(1, mc -> server(mc, RenderSelfTest::clearParties));
        for (String[] f : fish) {
            // Slot 1 (the lead) each time, so each fish replaces the last: one companion on screen.
            queue(1, mc -> perchBefore[0] = grill24.fishtastic.client.companion.ClientCompanions.perchSubmits);
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
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            });
            queue(30, mc -> {
                // PORT-ONLY: the world pass or the perch layer actually submitted this companion's art (a percher is only
                // drawn in third person, never in your own first-person view).
                check("companionsim." + f[2] + ".drawn",
                        grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame > 0
                                || grill24.fishtastic.client.companion.ClientCompanions.perchSubmits > perchBefore[0],
                        "world=" + grill24.fishtastic.client.companion.ClientCompanions.worldSubmitsThisFrame
                                + " perch=" + grill24.fishtastic.client.companion.ClientCompanions.perchSubmits);
                companionLog(f[2] + " third person");
                screenshot(mc, "companionsim", f[2] + "_tp");
                // PORT-ONLY: clean (overlay-free) shots from behind and in front, so the fish itself is judged.
                grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false);
            });
            queue(3, mc -> screenshot(mc, "companionsim", f[2] + "_clean_back"));
            queue(1, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
            queue(10, mc -> {
                screenshot(mc, "companionsim", f[2] + "_clean_front");
                grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
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
            server(mc, RenderSelfTest::clearParties);
        });
    }



    /** Where the local member of {@code species} is: its unit and index, or null. */
    @org.jetbrains.annotations.Nullable
    private static Object[] memberOf(String species) {
        for (var c : grill24.fishtastic.client.companion.ClientCompanions.localCompanions()) {
            for (int i = 0; i < c.fishCount(); i++) if (c.memberSpecies(i).equals(species)) return new Object[]{c, i};
        }
        return null;
    }

    /** {@code species}' own mood (a mixed shoal's members each have one), or null when it isn't stepping yet. */
    @org.jetbrains.annotations.Nullable
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
     * Two-client spike (party spec §28.5): two clients join one dedicated server
     * ({@code server=host:port}, {@code role=a|b}; both opped by the init script). Each waits for the
     * other, stands 3 blocks from it face to face, takes one free swimmer, and checks that it sees
     * the other player's party built and simulated, and that the server's scene packet arrives.
     * Then it lingers so the peer's shots include it.
     */
    @SuppressWarnings("unchecked")
    private static void queueTwoClientScene() {
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
        }, RenderSelfTest::localScene);
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
                grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd), RenderSelfTest::localScene);
        queue(20, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(30, mc -> screenshot(mc, "twoclient", (a ? "a" : "b") + "_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // Linger so the peer's shots and checks still find this player.
        queue(400, mc -> note("twoclient done, " + mc.level.players().size() + " players"));
    }

    /**
     * Polls {@code cond} every 5 ticks for up to {@code maxTicks}, then records {@code name} as a
     * check with {@code detail} (and how long it took).
     */
    @SuppressWarnings("unchecked")
    private static void queueUntil(String name, int maxTicks, java.util.function.Predicate<Minecraft> cond,
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
    private static void queueCompanionScenesScene() {
        queue(1, RenderSelfTest::paradeSetup);
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
                mc -> localScene().startsWith("EIGHT"), RenderSelfTest::localScene);
        queue(CrossSceneTicks.GREET + 30, mc -> {
            String sc = localScene();
            check("companionscenes.g2_on_path", sc.contains("leader SCENE") && !sc.contains("left:"), sc);
            shoot(mc, "g2_fp");
        });
        queue(20, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        queue(20, mc -> shoot(mc, "g2_tp"));
        queue(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        queueUntil("companionscenes.g2_done", 600, mc -> "EIGHT done".equals(
                grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd), RenderSelfTest::localScene);
        // G1: a bluegill ghost, the scene forced (the pair is on cooldown now).
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost add fishtastic:bluegill")));
        queue(80, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost scene g1")));
        queueUntil("companionscenes.g1_started", 100, mc -> localScene().startsWith("RING"), RenderSelfTest::localScene);
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
                grill24.fishtastic.client.companion.ClientCompanions.devLastSceneEnd), RenderSelfTest::localScene);
        // A packet 120 ticks late joins mid-path, past the greeting.
        queue(1, mc -> server(mc, s -> run(s, asMe(mc) + "fishtastic companion ghost scene g2 120")));
        queueUntil("companionscenes.late_joins_mid_path", 60, mc -> {
            String sc = grill24.fishtastic.client.companion.ClientCompanions.devLocalScene();
            if (sc == null || !sc.startsWith("EIGHT")) return false;
            int t = Integer.parseInt(sc.replaceAll(".* t(\\d+) .*", "$1"));
            return t >= 120;
        }, RenderSelfTest::localScene);
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
        queue(20, mc -> server(mc, RenderSelfTest::clearParties));
    }

    /**
     * Remote-party level of detail (audit §6.2): the ghost owner, still a tracked player, has its
     * party dropped once it stands past the drop range, kept between the two ranges, and rebuilt
     * when it comes back.
     */
    private static void queueCompanionLodScene() {
        String ghost = grill24.fishtastic.server.CompanionGhost.NAME;
        queue(1, RenderSelfTest::paradeSetup);
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
        queue(20, mc -> server(mc, RenderSelfTest::clearParties));
    }

    /** The scene greeting's length, ticks (CrossScene.GREET_TICKS). */
    private static final class CrossSceneTicks {
        static final int GREET = grill24.fishsim.companion.CrossScene.GREET_TICKS;
    }




    /**
     * Phase 5 moods (docs/fish-companions-sim-plan.md §5, spec §6) from the real world: a mixed
     * party (temperate bluegill, cold-water arctic char, Nether discus) in plains, then snow
     * (/fillbiome), a campfire, rain, a full-moon midnight with a parrotfish, and the dev trigger.
     */
    private static void queueCompanionParticlesScene() {
        queue(1, RenderSelfTest::paradeSetup);
        var particles = new grill24.fishtastic.client.companion.CompanionParticles();
        var saved = new net.minecraft.client.ParticleStatus[1];
        long[] decreasedAfter = new long[1];
        var glintBlock = new BlockPos[1];
        var glintOriginal = new net.minecraft.world.level.block.state.BlockState[1];
        queue(1, mc -> {
            saved[0] = mc.options.particles().get();
            mc.options.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
            var p = mc.player.getEyePosition().add(2, 0, 0);
            particles.emit(mc.level, java.util.List.of(new grill24.fishsim.companion.Emote(
                    grill24.fishsim.companion.Emote.Kind.NOTE,
                    new grill24.fishsim.companion.Vec(p.x, p.y, p.z), .5f)));
            check("companionparticles.minimal", particles.spawned() == 0, "spawned=" + particles.spawned());
            mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
            var c = mc.gameRenderer.getMainCamera().getPosition();
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
            mc.options.particles().set(net.minecraft.client.ParticleStatus.DECREASED);
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

    private static void queueCompanionWorldScene() {
        queue(1, RenderSelfTest::paradeSetup);
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
            run(s, "fishtastic companion give @p fishtastic:bluegill 25 1");
            run(s, "weather clear 99999");
        }));
        queue(100, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(false));
        queue(1, mc -> {
            BlockPos pos = mc.player.blockPosition().offset(3, 0, 0);
            // 1.20.1: event 1010's data is the record item's raw id (no JukeboxSong registry).
            net.minecraft.world.item.Item record = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(i -> i instanceof net.minecraft.world.item.RecordItem).findFirst().orElseThrow();
            mc.level.levelEvent(1010, pos, net.minecraft.world.item.Item.getId(record));
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
                run(s, asMe(mc) + "fishtastic companion feature " + feature);
                if (feature.equals("water")) {
                    run(s, asMe(mc) + "setblock ~1 ~ ~-2 minecraft:stone");
                    run(s, asMe(mc) + "setblock ~-1 ~ ~-2 minecraft:stone");
                    run(s, asMe(mc) + "setblock ~ ~ ~-1 minecraft:stone");
                    run(s, asMe(mc) + "setblock ~ ~ ~-3 minecraft:stone");
                }
            }));
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
                run(s, "fishtastic companion give @p fishtastic:garden_eel 40 1");
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
    private static void queueCompanionWorldCostScene() {
        queue(1, RenderSelfTest::paradeSetup);
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

    private static void queueCompanionMoodsScene() {
        queue(1, RenderSelfTest::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanionsDev.devOverlay(true));
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "weather clear 99999");
            run(s, "fishtastic companion give @p fishtastic:bluegill 25 1");
            run(s, "fishtastic companion give @p fishtastic:arctic_char 30 2");
            run(s, "fishtastic companion give @p fishtastic:discus 20 3");
        }));
        queueUntil("companionmoods.party", 200, mc -> moodOf("bluegill") != null && moodOf("arctic_char") != null
                && moodOf("discus") != null, RenderSelfTest::moods);
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
     * The three-slot party (docs/fish-companions-party.md §3, D6, D11): a school of three
     * mixed-size bluegill from three slots, then a mixed party of three species (one solo each),
     * in first and third person, with and without the owner sneaking (each fish's name above it).
     */
    private static void queueCompanionPartyScene() {
        queue(1, RenderSelfTest::paradeSetup);
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
            server(mc, RenderSelfTest::clearParties);
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
    private static void queueCompanionEelScene() {
        queue(1, RenderSelfTest::paradeSetup);
        queue(1, mc -> server(mc, s -> {
            clearParties(s);
            run(s, "item replace entity @p weapon.mainhand with minecraft:air");
            run(s, "fishtastic companion give @p fishtastic:garden_eel 40 1");
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
            Fishtastic.LOGGER.info("[selftest] eel skin model {}", mc.player.getModelName());
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
            server(mc, RenderSelfTest::clearParties);
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

    private static void queueCompanionBondScene() {
        queue(1, RenderSelfTest::paradeSetup);
        queue(1, mc -> server(mc, s -> {
            run(s, "fishtastic companion resetbonds @p");
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                grill24.fishtastic.companion.CompanionStorage.clear(player);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, fish("bluegill", 25f).copyWithCount(2));
                player.getInventory().setItem(1, fish("bluegill", 31f));
                ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
                FishtasticItemData.setBundleContents(pile, new grill24.fishtastic.component.BundleContents(
                        List.of(fish("bluegill", 40f), fish("bluegill", 45f))));
                player.getInventory().setItem(2, pile);
                // Never unlocked here: the refusal flash for a catch-gated and a quest-only species.
                player.getInventory().setItem(4, fish("oscar", 30f));
                player.getInventory().setItem(5, fish("molten_moorish_idol", 30f));
            }
        }));
        // Feedback packet round trip: a locked fish is refused by the server, the client handler records it.
        queue(10, mc -> {
            grill24.fishtastic.client.CompanionClientState.reset();
            check("bond.lockedPrecondition", !grill24.fishtastic.client.QuestClientCache.isBondUnlocked(Ids.of("fishtastic", "oscar"))
                    && !grill24.fishtastic.client.QuestClientCache.isBondUnlocked(Ids.of("fishtastic", "molten_moorish_idol")), "");
            grill24.fishtastic.network.NetworkApiSided.sendToServer(
                    new grill24.fishtastic.network.BondCompanionPacket(fish("oscar", 30f)),
                    grill24.fishtastic.network.BondCompanionPacket.STREAM_CODEC);
        });
        queue(15, mc -> {
            var outcome = grill24.fishtastic.client.CompanionClientState.consumeRecentFeedback();
            check("bond.feedbackHandler", outcome == grill24.fishtastic.network.CompanionFeedbackPacket.Outcome.LOCKED, "outcome=" + outcome);
            check("bond.lockedNotBonded", partyServer(mc, -1).isEmpty(), "");
        });
        // Unlock sync: server -> QuestSyncPacket -> QuestClientCache -> the bond-unlock banner.
        queue(1, mc -> {
            mc.getToasts().clear();
            server(mc, s -> run(s, "fishtastic companion unlock @p fishtastic:bluegill"));
        });
        queue(25, mc -> {
            check("bond.unlockSynced", grill24.fishtastic.client.QuestClientCache.isBondUnlocked(Ids.of("fishtastic", "bluegill")), "");
            screenshot(mc, "companionbond", "unlock_banner");
        });
        queue(25, mc -> screenshot(mc, "companionbond", "unlock_banner_late"));
        // Hold-to-bond mixin: render head ticks CompanionBondInput; the tooltip return appends the hint.
        // Survival only: in creative, InventoryScreen redirects to the creative screen, which the hook skips.
        queue(60, mc -> server(mc, s -> run(s, "gamemode survival @a")));
        queue(10, mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        queue(15, mc -> {
            check("bond.survivalInventoryScreen", mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen,
                    "screen=" + mc.screen);
            Object tracked = peekStatic(grill24.fishtastic.client.CompanionBondInput.class, "screen");
            check("bond.mixinRenderHead", tracked == mc.screen && tracked != null, "tracked=" + tracked);
            try {
                var m = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class
                        .getDeclaredMethod("getTooltipFromContainerItem", ItemStack.class);
                m.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Component> lines = (List<Component>) m.invoke(mc.screen, fish("bluegill", 25f));
                @SuppressWarnings("unchecked")
                List<Component> oscar = (List<Component>) m.invoke(mc.screen, fish("oscar", 25f));
                check("bond.mixinTooltipHint", lines.size() == oscar.size() + 1,
                        "unlocked=" + lines.stream().map(Component::getString).toList()
                                + " locked=" + oscar.stream().map(Component::getString).toList());
            } catch (ReflectiveOperationException e) {
                check("bond.mixinTooltipHint", false, "reflection " + e);
            }
            screenshot(mc, "companionbond", "inventory");
            mc.setScreen(null);
            server(mc, srv -> run(srv, "gamemode creative @a"));
        });
        // The companion screen via the real open-menu packet.
        queue(5, mc -> grill24.fishtastic.client.CompanionScreen.open(fish("bluegill", 25f)));
        queue(20, mc -> {
            check("bond.inventoryScreen", mc.screen instanceof grill24.fishtastic.client.CompanionScreen, "screen=" + mc.screen);
            check("bond.slotCount", mc.player.containerMenu.slots.size() == 39, "slots=" + mc.player.containerMenu.slots.size());
            screenshot(mc, "companionbond", "inventory_picker");
        });
        // Menu slots 0-2 are the party; the hotbar starts at 30.
        queue(1, mc -> containerClick(mc, 30, net.minecraft.world.inventory.ClickType.QUICK_MOVE));
        queue(15, mc -> {
            check("bond.equipped", !partyServer(mc, 0).isEmpty(), "");
            check("bond.oneTaken", mc.player.getInventory().getItem(0).getCount() == 1, "");
            screenshot(mc, "companionbond", "equipped");
        });
        queue(1, mc -> containerClick(mc, 31, net.minecraft.world.inventory.ClickType.QUICK_MOVE));
        queue(15, mc -> {
            check("bond.second", ItemSizeHelper.getSize(partyServer(mc, 1)) == 31f, "");
            screenshot(mc, "companionbond", "pair");
        });
        queue(1, mc -> grill24.fishtastic.network.NetworkApiSided.sendToServer(
                new grill24.fishtastic.network.RenameCompanionPacket(0, "Bubbles"),
                grill24.fishtastic.network.RenameCompanionPacket.STREAM_CODEC));
        queue(15, mc -> {
            check("bond.renamed", partyServer(mc, 0).getHoverName().getString().equals("Bubbles"), "");
            screenshot(mc, "companionbond", "renamed");
        });
        queue(1, mc -> {
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) screen.openEditor(1);
        });
        queue(5, mc -> {
            check("bond.editorOpens", mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen && screen.editingSlot() == 1,
                    "screen=" + mc.screen);
            screenshot(mc, "companionbond", "pencil_open");
        });
        queue(1, mc -> containerClick(mc, 1, net.minecraft.world.inventory.ClickType.QUICK_MOVE));
        queue(15, mc -> check("bond.editorClosesOnMove", mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen
                && screen.editingSlot() == -1 && partyServer(mc, 1).isEmpty(), ""));
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
        queue(1, mc -> containerClick(mc, 3, net.minecraft.world.inventory.ClickType.PICKUP));
        // A fish that isn't bonded: shift-click flashes its own slot and the party slot it was headed for.
        queue(10, mc -> {
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) {
                screen.showPiles(false);
                screen.clickSlot(34, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
            }
        });
        queue(3, mc -> {
            boolean flashing = mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen
                    && screen.isFlashing(1) && screen.isFlashing(34);
            check("bond.lockedFlashes", flashing, "screen=" + mc.screen);
            screenshot(mc, "companionbond", "locked_flash");
        });
        queue(15, mc -> {
            check("bond.lockedRefused", partyServer(mc, 1).isEmpty()
                    && mc.player.getInventory().getItem(4).getCount() == 1, "");
            if (mc.screen instanceof grill24.fishtastic.client.CompanionScreen screen) {
                screen.clickSlot(35, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
            }
        });
        queue(3, mc -> screenshot(mc, "companionbond", "locked_flash_quest"));
        queue(1, mc -> containerClick(mc, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE));
        queue(1, mc -> containerClick(mc, 1, net.minecraft.world.inventory.ClickType.QUICK_MOVE));
        queue(15, mc -> {
            check("bond.returned", partyServer(mc, -1).isEmpty(), "");
            screenshot(mc, "companionbond", "returned");
            mc.player.closeContainer();
        });
        // Encyclopedia bond UI: hearts on bond-ready icons and the Companions section.
        queue(5, mc -> server(mc, s -> run(s, "fishtastic companion catchcount @p fishtastic:bluegill 20")));
        queue(10, mc -> mc.player.connection.sendCommand("gelatin fish_encyclopedia"));
        queue(50, mc -> {
            check("bond.encyclopediaOpen", mc.screen != null && mc.screen.getClass().getSimpleName().contains("Encyclopedia"), "screen=" + mc.screen);
            screenshot(mc, "companionbond", "encyclopedia");
        });
        // Select bluegill as a click would, to reach the info page's Companions section.
        queue(1, mc -> {
            try {
                var key = ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, Ids.of("fishtastic", "bluegill"));
                var icons = mc.screen.getClass().getDeclaredField("iconRefs");
                icons.setAccessible(true);
                Object icon = ((java.util.Map<?, ?>) icons.get(mc.screen)).get(key);
                var select = mc.screen.getClass().getDeclaredMethod("onFishSelected",
                        io.github.currenj.gelatinui.gui.IUIElement.class, ResourceKey.class);
                select.setAccessible(true);
                select.invoke(mc.screen, icon, key);
                check("bond.encyclopediaSelect", icon != null, "icon=" + icon);
            } catch (ReflectiveOperationException e) {
                check("bond.encyclopediaSelect", false, "reflection " + e);
            }
        });
        queue(90, mc -> screenshot(mc, "companionbond", "encyclopedia_info"));
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            queue(1, mc -> {
                for (int k = 0; k < 20; k++) mc.screen.mouseScrolled(800, 450, -4);
            });
            queue(30, mc -> screenshot(mc, "companionbond", "encyclopedia_scroll" + n));
        }
        queue(1, mc -> mc.setScreen(null));
        queue(1, mc -> server(mc, s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                player.getInventory().clearContent();
                grill24.fishtastic.companion.CompanionStorage.clear(player);
            }
            run(s, "fishtastic companion resetbonds @p");
        }));
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

    private static void clearParties(MinecraftServer s) {
        for (ServerPlayer player : s.getPlayerList().getPlayers()) grill24.fishtastic.companion.CompanionStorage.clear(player);
    }

    private static void companionLog(String label) {
        Fishtastic.LOGGER.info("[selftest] companion {}: {}", label,
                grill24.fishtastic.client.companion.ClientCompanionsDev.debugState());
    }

    private static void server(Minecraft mc, Consumer<MinecraftServer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server));
    }

    private static void worldCommand(Minecraft mc, String command) {
        if (remoteServer == null) {
            server(mc, s -> run(s, command));
        } else if (mc.player != null) {
            mc.player.connection.sendCommand(command);
        }
    }

    /** A diagnostic line plus where the player is, whose block Y sets every column's scan band. */
    private static void noteState(String label) {
        BlockPos p = Minecraft.getInstance().player.blockPosition();
        note(label + " at " + p.getX() + "," + p.getY() + "," + p.getZ() + " "
                + grill24.fishtastic.client.ambient.ClientAmbientFish.natural().diagnostics().debugState());
    }

    /** What the client level really holds at one cell: the ground truth discovery reads. */
    private static void noteCell(String label, int x, int y, int z) {
        var level = Minecraft.getInstance().level;
        BlockPos pos = new BlockPos(x, y, z);
        note(label + " cell " + x + "," + y + "," + z
                + " loaded=" + level.hasChunkAt(pos)
                + " fluid=" + level.getFluidState(pos)
                + " block=" + level.getBlockState(pos));
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** A diagnostic line: evidence for the log that passes no judgement. */
    static void note(String message) {
        Fishtastic.LOGGER.info("[selftest] NOTE {}", message);
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
