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
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.client.ambient.AmbientContextClient;
import grill24.fishtastic.network.AmbientContextResponsePacket;
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
            "companionbond", "companionsim", "companionparty", "ambientparity", "ambientwater", "ambientcontext", "ambientpopulation", "ambientlifecycle", "ambientisland", "ambientfloor", "ambientwalk");

    private static Boolean armed;
    /** Keep ordinary automatic features from interfering with explicitly armed fixture scenes. */
    public static boolean isArmed() {
        return armed == null ? new File(Minecraft.getInstance().gameDirectory, MARKER).exists()
                : armed;
    }
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

    private static void createWorld(Minecraft mc) {
        Path runRoot = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        Path save = runRoot.resolve("saves").resolve(WORLD_NAME).normalize();
        if (!save.startsWith(runRoot) || !save.getParent().equals(runRoot.resolve("saves"))
                || !save.getFileName().toString().equals(WORLD_NAME)) {
            throw new IllegalStateException("Unsafe self-test world path: " + save);
        }
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
     * Opt-in multiplayer: {@code server=host:port} in the marker joins that server from the title
     * screen instead of creating the flat world. Used by the Phase 4 dedicated-server capture; a
     * joined client has no integrated server, so the scenes that reach for one must not be named.
     */
    private static void connectToServer(Minecraft mc, String hostPort) {
        net.minecraft.client.multiplayer.resolver.ServerAddress address =
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(hostPort);
        // parseString returns its private INVALID sentinel (host "server.invalid") on bad input.
        if (address == null || address.getHost().isEmpty() || address.getHost().equals("server.invalid")) {
            Fishtastic.LOGGER.error("[selftest] cannot parse server address '{}'", hostPort);
            return;
        }
        Fishtastic.LOGGER.info("[selftest] joining {}", hostPort);
        // ServerData must not be null: startConnecting dereferences its ip/name for the fail screen.
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc, address,
                new net.minecraft.client.multiplayer.ServerData("selftest", hostPort,
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER),
                false, null);
    }

    /** Quiet world, no tutorial. Vanilla draws its toasts after every mod HUD layer, so the
     * tutorial's hint toasts would cover our overlays: stop() alone is not enough (the step is
     * recreated from the option), so the option is set to NONE too, and restored in {@link #finish}.
     */
    private static void prepare(Minecraft mc) {
        savedTutorialStep = mc.options.tutorialStep;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.getTutorial().stop();
        mc.getToastManager().clear();
        if (remoteServer == null) {
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
        } else {
            // A joined client holds no command permission here, and the dedicated capture wants the
            // server's own clock and weather anyway (the authoritative side is what is under test).
            note("joined " + remoteServer + ": quiet-world rules left to the server");
        }
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
            case "companionbond" -> queueCompanionBondScene();
            case "companionsim" -> queueCompanionSimScene();
            case "companionparty" -> queueCompanionPartyScene();
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
    private static void queueAmbientParityScene() {
        BlockPos[] water = new BlockPos[1], tanks = new BlockPos[1];
        long[] frozenSteps = new long[1], beforeAway = new long[1];
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
                            grill24.fishtastic.client.ambient.ClientAmbientFish.SPECIES[i],
                            grill24.fishtastic.client.ambient.ClientAmbientFish.SIZE_CM[i])), "");
                }
            });
        });
        queue(40, mc -> {
            mc.options.hideGui = true;
            check("ambientparity.fixture.started", grill24.fishtastic.client.ambient.ClientAmbientFish.startFixture(water[0]),
                    "origin="+water[0]);
            camera(mc, origin.getX(), origin.getY()+3.4, origin.getZ()+1.5, 180f, 16f);
        });
        queue(100, mc -> {
            check("ambientparity.fixture.count", grill24.fishtastic.client.ambient.ClientAmbientFish.count()==4, "four fixed individuals");
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
            beforeAway[0] = grill24.fishtastic.client.ambient.ClientAmbientFish.steps();
        });
        queue(40, mc -> {
            check("ambientparity.fixture.offcamera", grill24.fishtastic.client.ambient.ClientAmbientFish.steps()>beforeAway[0]
                            && water[0].equals(grill24.fishtastic.client.ambient.ClientAmbientFish.origin())
                            && grill24.fishtastic.client.ambient.ClientAmbientFish.identity(0)==1L,
                    "off-camera simulation retains fixed origin and identity");
            server(mc, server -> run(server, "tick freeze"));
        });
        queue(10, mc -> frozenSteps[0] = grill24.fishtastic.client.ambient.ClientAmbientFish.steps());
        queue(20, mc -> {
            check("ambientparity.fixture.freeze", frozenSteps[0]==grill24.fishtastic.client.ambient.ClientAmbientFish.steps(),
                    "steps="+frozenSteps[0]);
        });
        // World time and both simulations are frozen: photograph every species closely enough
        // to assess its sprite/scale/pose, without requiring lucky positions in a wide camera.
        for (int index = 0; index < 4; index++) {
            final int i = index;
            queue(1, mc -> {
                var pose = grill24.fishtastic.client.ambient.ClientAmbientFish.fixturePose(i, 0f);
                if (pose != null) parityCloseCamera(mc, pose.position(), pose.yaw(), water[0]);
            });
            queue(20, mc -> screenshot(mc, "ambientparity", "world_" + grill24.fishtastic.client.ambient.ClientAmbientFish.SPECIES[i]));
            queue(1, mc -> {
                if (!(mc.level.getBlockEntity(tanks[0]) instanceof FishTankBlockEntity tank)) return;
                TankGroupFlock group = ClientTankFlocks.getOrCreate(tank, tanks[0].hashCode()).groupFlock();
                if (group == null) return;
                ItemStack fish = fish(grill24.fishtastic.client.ambient.ClientAmbientFish.SPECIES[i],
                        grill24.fishtastic.client.ambient.ClientAmbientFish.SIZE_CM[i]);
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
            queue(20, mc -> screenshot(mc, "ambientparity", "tank_" + grill24.fishtastic.client.ambient.ClientAmbientFish.SPECIES[i]));
        }
        queue(1, mc -> {
            server(mc, server -> run(server, "tick unfreeze"));
            grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
            check("ambientparity.fixture.cleanup", grill24.fishtastic.client.ambient.ClientAmbientFish.count()==0, "");
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
        queue(110, mc -> ambientWaterFixture("river", 1, 4, 1, true));
        queue(1, mc -> camera(mc, riverX - 8.5, gy + 1.65, riverZ + 0.5, -90f, 14f));
        queue(3, mc -> screenshot(mc, "ambientwater", "river_bank"));
        queue(1, mc -> camera(mc, riverX + 8.5, gy - 0.2, riverZ + 0.5, -90f, 2f));
        queue(3, mc -> screenshot(mc, "ambientwater", "river_submerged"));

        // ── ocean: 49x49, three deep, open on every side and under the surface, with a spire
        queue(2, mc -> camera(mc, oceanX + 0.5, gy - 0.2, oceanZ + 0.5, -90f, 6f));
        queue(4, mc -> ambientWaterStart("ocean"));
        queue(4, mc -> server(mc, s -> {
            fill(s, oceanX - 24, gy - 3, oceanZ - 24, oceanX + 24, gy - 1, oceanZ + 24, "minecraft:air");
            fill(s, oceanX - 24, gy - 4, oceanZ - 24, oceanX + 24, gy - 4, oceanZ + 24, "minecraft:sand");
            fill(s, oceanX - 7, gy - 3, oceanZ - 7, oceanX - 5, gy - 3, oceanZ - 5, "minecraft:sand");
            fill(s, oceanX + 6, gy - 3, oceanZ + 6, oceanX + 6, gy - 2, oceanZ + 6, "minecraft:stone");
            fill(s, oceanX - 24, gy - 3, oceanZ - 24, oceanX + 24, gy - 1, oceanZ + 24, "minecraft:water keep");
        }));
        queue(150, mc -> ambientWaterFixture("ocean", 1, 6, 1, true));
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
            for (var p : runtime.individuals()) {
                double d = Math.hypot(p.x() - (player.getX() + 0.5), p.z() - (player.getZ() + 0.5));
                if (d < best) {
                    best = d;
                    tripKey[0] = p.patchKey();
                }
            }
            int fading = 0;
            for (var p : runtime.individuals()) {
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
            for (var r : runtime.domainReports()) {
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
            tripColumns[0] = runtime.patchColumns(tripKey[0]);
            check("ambientwater.trip.snapshot", tripIds.size() >= 2 && runtime.patchColumns(tripKey[0]) >= 4,
                    "tracked=" + tripIds.size() + " fadingAtSnapshot=" + fading + " key=" + tripKey[0]
                            + " columns=" + runtime.patchColumns(tripKey[0]) + " origin="
                            + tripOrigin[0] + "," + tripOrigin[1] + "," + tripOrigin[2]);
        });
        queue(1, mc -> camera(mc, poolAX - 40.5, gy + 0.7, poolZ + 0.5, -90f, 2f));
        queue(15, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            int present = 0, retiring = 0;
            for (var p : runtime.individuals()) {
                if (!tripIds.contains(p.id())) continue;
                present++;
                if (p.retiring()) retiring++;
            }
            var s = runtime.stats();
            int columns = runtime.patchColumns(tripKey[0]);
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
            for (var p : runtime.individuals()) {
                if (!tripIds.contains(p.id())) continue;
                present++;
                if (p.retiring()) retiring++;
                if (p.patchKey() != tripKey[0]) keyChanged++;
            }
            int originMoved = 0;
            for (var r : runtime.domainReports()) {
                if (r.patchKey() == tripKey[0] && (r.originX() != tripOrigin[0]
                        || r.originY() != tripOrigin[1] || r.originZ() != tripOrigin[2])) originMoved++;
            }
            var s = runtime.stats();
            int columns = runtime.patchColumns(tripKey[0]);
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
            // must never happen is a resident outside the water; the per-fixture checks also pin the
            // body fit and finiteness at their own snapshots.
            check("ambientwater.containment", stats.outsideWater == 0,
                    "outsideWater=" + stats.outsideWater + " bodyTouches=" + stats.bodyFitsFailures
                            + " steps=" + stats.steps);
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
            check("ambientwater.cleanup", !runtime.armed() && runtime.domainCount() == 0 && runtime.individuals().isEmpty()
                            && grill24.fishtastic.client.ambient.ClientAmbientFish.count() == 0,
                    "armed=" + runtime.armed() + " domains=" + runtime.domainCount());
        });
    }

    /** Restarts the natural population where the player is standing; the camera step ran before. */
    private static void ambientWaterStart(String fixture) {
        grill24.fishtastic.client.ambient.ClientAmbientFish.clear();
        boolean started = grill24.fishtastic.client.ambient.ClientAmbientFish.startNatural();
        check("ambientwater." + fixture + ".start", started,
                grill24.fishtastic.client.ambient.ClientAmbientFish.natural().pool().toString());
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
            ItemStack rod = new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", "copper_fishing_rod")));
            int selected = mc.player.getInventory().getSelectedSlot();
            // The creative GUI's own sequence (CreativeModeInventoryScreen.handleHotbarLoadOrSave):
            // write the local inventory, send the creative-slot packet, resync the local menu.
            // handleCreativeModeItemAdd alone is send-only, and the server's handler suppresses the
            // echo (setRemoteSlot before broadcastChanges), so without the local write the server's
            // copy would move while the client's own hand — and with it the equipment signature the
            // refresh policy watches — stayed empty.
            mc.player.getInventory().setItem(selected, rod);
            mc.gameMode.handleCreativeModeItemAdd(rod, 36 + selected);
            mc.player.inventoryMenu.broadcastChanges();
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

    /** How often the long run samples, and how many samples it takes: 120 x 20 = two minutes. */
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
        queue(1, mc -> worldCommand(mc, "fill 0 39 0 40 39 40 minecraft:sand"));
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
                    ClientAmbientFish.natural().debugState());
            boolean nearSurface = ClientAmbientFish.natural().individuals().stream().filter(p -> !p.retiring())
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
            note("ambientisland " + ClientAmbientFish.natural().debugState());
        });
        queue(800, mc -> {
            check("ambientisland.sustained", ClientAmbientFish.natural().stats().active >= 26,
                    ClientAmbientFish.natural().debugState());
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
                && ClientAmbientFish.natural().individuals().stream()
                .anyMatch(p -> p.species().endsWith("garden_eel") && !p.retiring()), mc -> {
            var runtime = ClientAmbientFish.natural();
            check("ambientfloor.population", runtime.stats().active >= 60 && runtime.stats().individuals <= 64,
                    runtime.debugState());
            var eels = runtime.individuals().stream()
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
                    "expiry retirements=" + runtime.stats().retireExpired + " " + runtime.debugState());
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
            check("ambientwalk.populated", runtime.stats().active >= 28, runtime.debugState());
            initial[0] = runtime.stats().domainsLost;
            initial[1] = runtime.stats().retireExpired;
            for (var fish : runtime.individuals()) if (!fish.retiring()) initialIds.add(fish.id());
        });
        for (int step = 1; step <= 8; step++) {
            final double x = 20.5 + (step <= 4 ? step : 8 - step);
            queue(40, mc -> camera(mc, x, 65, 20.5, 90, 20));
        }
        queue(80, mc -> {
            var runtime = ClientAmbientFish.natural();
            long surviving = runtime.individuals().stream()
                    .filter(p -> !p.retiring() && initialIds.contains(p.id())).count();
            check("ambientwalk.noPatchRetirement", runtime.stats().domainsLost == initial[0], runtime.debugState());
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
                    ClientAmbientFish.natural().debugState());
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
            grill24.fishtastic.client.companion.ClientCompanions.devAddPhantom(fish("bluegill", 30f));
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
                    runtime.debugState());
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
            worldCommand(mc, "fill -8 " + (gy - 4) + " -8 55 " + (gy - 4) + " 55 minecraft:sand");
        });
        queue(2, mc -> worldCommand(mc, "fill -8 " + (gy - 3) + " -8 55 " + (gy - 1) + " 55 minecraft:water"));
        queue(400, mc -> {
            var runtime = ClientAmbientFish.natural();
            var stats = runtime.stats();
            check("ambientlifecycle.openwater.bounded", runtime.discovery().columnCount() <= 4096
                    && stats.individuals <= 32 && runtime.domainCount() <= 4
                    && stats.peakDiscoveryCells <= 512 && stats.peakDiscoveryColumns <= 48,
                    runtime.debugState());
            note("ambientlifecycle.openwater perf " + runtime.debugState());
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
        queue(60, mc -> {
            tickProbe = null;
            check("ambientlifecycle.empty.retired", ClientAmbientFish.natural().stats().individuals == 0,
                    "authoritative OK/empty retires residents and spawns none");
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
                    && ClientAmbientFish.natural().budget().density() == 50, ClientAmbientFish.natural().debugState());
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
            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
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
        final java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientFishRuntime.Individual> tracked =
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
                    && grill24.fishtastic.client.ambient.ClientAmbientFish.natural().appliedFingerprint(sample.patchKey())
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
            var representable = runtime.representableSpecies(key[0]);
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
                    && runtime.appliedFingerprint(key[0]) == ctx.fingerprint();
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
                    && runtime.appliedFingerprint(key[0]) == ctx.fingerprint();
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
                            && runtime.appliedFingerprint(key[0]) == laneFp[0]
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
            if (ctx == null || runtime.appliedFingerprint(key[0]) != ctx.fingerprint()) return false;
            var representable = new java.util.HashSet<>(runtime.representableSpecies(key[0]));
            if (stage[0] == 0) {
                if (populationStats()[0] - statBase[0] < 1) return false;
                // The first tick with exclusions on the books: every resident now outside the pool
                // must already be fading, and with the eligibility reason — not frozen, not
                // mass-retired by some other path.
                int outside = 0, wrongReason = 0;
                for (var p : runtime.individuals()) {
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
            for (var p : runtime.individuals()) {
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
            var representable = runtime.representableSpecies(key[0]);
            return !representable.isEmpty() && censusTotal(census) >= 3
                    && census.keySet().stream().allMatch(new java.util.HashSet<>(representable)::contains);
        }, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var census = patchCensus(key[0]);
            var representable = new java.util.LinkedHashSet<>(runtime.representableSpecies(key[0]));
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
            System.arraycopy(populationStats(), 0, statBase, 0, statBase.length);
            note("ambientpopulation.return before census=" + censusText(patchCensus(key[0]))
                    + " tracked=" + tracked.size());
            handRod(mc, null, null, null);
        });
        waitFor(5, 600, mc -> {
            var ctx = grill24.fishtastic.client.ambient.AmbientContextClient.store().freshContext(key[0]);
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            return ctx != null && !ctx.heldRod() && ctx.fingerprint() != laneFp[0]
                    && runtime.appliedFingerprint(key[0]) == ctx.fingerprint();
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
        queue(2, mc -> {
            System.arraycopy(populationStats(), 0, runBase, 0, runBase.length);
            idsAtRunStart.clear();
            for (var p : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().individuals()) {
                if (p.patchKey() == key[0] && !p.retiring()) idsAtRunStart.add(p.id());
            }
            note("ambientpopulation.longrun start census=" + censusText(patchCensus(key[0]))
                    + " ids=" + idsAtRunStart.size());
        });
        Consumer<Minecraft>[] longPoll = new Consumer[1];
        longPoll[0] = mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            long time = mc.level.getGameTime();
            var representable = runtime.representableSpecies(key[0]);
            if (representable.isEmpty()) {
                violations[2]++;
            } else if (polls[0] > 0) { // the first poll settles right after the return lane's checks
                var set = new java.util.HashSet<>(representable);
                for (var p : runtime.individuals()) {
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
            for (var p : runtime.individuals()) {
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
            var sampler = runtime.samplerFor(key[0]);
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
        queue(LONG_RUN_EVERY, longPoll[0]);

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
            return runtime.patchColumns(key[0]) == 0
                    && runtime.stats().retainedStores > retentionBase[0]
                    && runtime.stats().domainsLost > retentionBase[3];
        }, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var s = runtime.stats();
            check("ambientpopulation.retention.dropped",
                    runtime.patchColumns(key[0]) == 0 && s.domainsLost > retentionBase[3]
                            && s.retainedStores > retentionBase[0]
                            && runtime.retainedStateCount() <= grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES,
                    "columns=" + runtime.patchColumns(key[0]) + " domainsLost=+" + (s.domainsLost - retentionBase[3])
                            + " retainedStores=+" + (s.retainedStores - retentionBase[0])
                            + " retained=" + runtime.retainedStateCount() + "/"
                            + grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES);
        });
        queue(1, mc -> worldCommand(mc, "tp @a " + standX + " " + gy + " " + standZ));
        waitFor(10, 900, mc -> {
            var sample = nearestShownSample(target);
            var store = grill24.fishtastic.client.ambient.AmbientContextClient.store();
            return sample != null && store.stateOf(sample.patchKey()) == grill24.fishtastic.client.ambient.AmbientContextStore.State.OK
                    && grill24.fishtastic.client.ambient.ClientAmbientFish.natural().patchColumns(sample.patchKey()) >= 4
                    && censusTotal(patchCensus(sample.patchKey())) >= 1;
        }, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            var s = runtime.stats();
            check("ambientpopulation.retention.resumed",
                    s.retainedRestores > retentionBase[1]
                            && runtime.retainedStateCount() <= grill24.fishtastic.client.ambient.AmbientTuning.MAX_RETAINED_PATCHES,
                    "retainedRestores=+" + (s.retainedRestores - retentionBase[1])
                            + " retainedExpiries=+" + (s.retainedExpiries - retentionBase[2])
                            + " retained=" + runtime.retainedStateCount() + "/"
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
                    !runtime.armed() && runtime.domainCount() == 0 && runtime.retainedStateCount() == 0,
                    "armed=" + runtime.armed() + " domains=" + runtime.domainCount()
                            + " retained=" + runtime.retainedStateCount());
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
        for (var p : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().individuals()) {
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
        for (var entry : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().samplerFor(patchKey)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(entry.species().replace("fishtastic:", ""))
                    .append(" w=").append(entry.weight())
                    .append(String.format(java.util.Locale.ROOT, " vis=%.3f raw=%.3f", entry.share(), entry.rawShare()));
        }
        return sb.length() == 0 ? "<no sampler>" : sb.toString();
    }

    /** Non-retiring residents of one patch by id — immutable snapshots, safe to hold across ticks. */
    private static java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientFishRuntime.Individual> snapshotResidents(long patchKey) {
        java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientFishRuntime.Individual> out = new java.util.LinkedHashMap<>();
        for (var p : grill24.fishtastic.client.ambient.ClientAmbientFish.natural().individuals()) {
            if (p.patchKey() == patchKey && !p.retiring()) out.put(p.id(), p);
        }
        return out;
    }

    /** How many members of a patch — fading ones included — hold a species outside its sampler. */
    private static int countOutOfPool(grill24.fishtastic.client.ambient.AmbientFishRuntime runtime, long patchKey) {
        var representable = new java.util.HashSet<>(runtime.representableSpecies(patchKey));
        int out = 0;
        for (var p : runtime.individuals()) {
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
    private static String carryReport(java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientFishRuntime.Individual> before,
                                      long patchKey, double maxMove, long[] statBase, long[] out) {
        var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
        int present = 0, speciesChanged = 0, patchChanged = 0, jumped = 0, retiringNow = 0;
        double worst = 0;
        for (var p : runtime.individuals()) {
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
                : new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", rodItem)));
        if (!rod.isEmpty()) {
            if (baitItem != null) {
                grill24.fishtastic.item.FishtasticFishingRodItem.setBait(rod,
                        new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", baitItem))));
            }
            if (charmItem != null) {
                grill24.fishtastic.item.FishtasticFishingRodItem.setCharm(rod,
                        new ItemStack(BuiltInRegistries.ITEM.getValue(Ids.of("fishtastic", charmItem))));
            }
        }
        int selected = mc.player.getInventory().getSelectedSlot();
        // The creative GUI's own sequence: write the local inventory, send the creative-slot
        // packet, resync the local menu (handleCreativeModeItemAdd alone is send-only, and the
        // server's handler suppresses the echo).
        mc.player.getInventory().setItem(selected, rod);
        mc.gameMode.handleCreativeModeItemAdd(rod, 36 + selected);
        mc.player.inventoryMenu.broadcastChanges();
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
                            player.level().dimension().identifier(), patchKey, geometryGeneration, contextGeneration, pos)));
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
     * Reads the live population within twelve blocks of the player — one fixture's own fish — and
     * asserts the Phase 2 gate conditions: real water, bodies that fit it, head-room under the
     * surface, a boundary policy that matches the water (a fade band only where the water really
     * continues), and discovery that stays inside its budget.
     */
    private static void ambientWaterFixture(String fixture, int minDomains, int minFish, int minPatches, boolean expectOpenFace) {
        var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
        var stats = runtime.stats();
        BlockPos player = Minecraft.getInstance().player.blockPosition();
        List<grill24.fishtastic.client.ambient.AmbientFishRuntime.Individual> near = new java.util.ArrayList<>();
        for (var p : runtime.individuals()) {
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
            if (!p.bodyFits()) unfit++;
            minSurface = Math.min(minSurface, p.surfaceClearance());
            minBed = Math.min(minBed, p.bedClearance());
            minOpen = Math.min(minOpen, p.openFaceDistance());
            if (p.openFaceDistance() <= grill24.fishsim.domain.WaterDomain.OPEN_FACE_MARGIN) inFadeBand++;
        }
        boolean openFaceNear = false;
        int columns = 0;
        for (var r : runtime.domainReports()) {
            if (!patches.contains(r.patchKey()) || r.columns() <= 0) continue;
            columns += r.columns();
            if (r.openMinL() || r.openMaxL() || r.openMinY() || r.openMaxY() || r.openMinD() || r.openMaxD()) openFaceNear = true;
        }
        check("ambientwater." + fixture + ".domains", runtime.domainCount() >= minDomains,
                "domains=" + runtime.domainCount() + " expected>=" + minDomains);
        check("ambientwater." + fixture + ".fish", near.size() >= minFish && patches.size() >= minPatches,
                "near=" + near.size() + " expected>=" + minFish + " patches=" + patches + " expected>=" + minPatches
                        + " active=" + stats.active + " retiring=" + stats.retiring + " omissions=" + stats.omissions);
        check("ambientwater." + fixture + ".contained", near.size() > 0 && outsideWater == 0 && unfit == 0 && nonFinite == 0,
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
            for (var p : runtime.individuals()) {
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
                        + runtime.pool().size() + " representable)");
                return;
            }
            final String shot = chosen.substring(chosen.indexOf(':') + 1);
            done.add(chosen);
            var found = runtime.individuals().stream()
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
            for (var p : runtime.individuals()) before.put(p.id(), new double[]{p.x(), p.y(), p.z(), p.patchKey()});
            for (var r : runtime.domainReports()) {
                origins.put(r.patchKey(), new int[]{r.originX(), r.originY(), r.originZ()});
            }
            check("ambientwater.carry.snapshot", before.size() >= 4, "tracked=" + before.size()
                    + " rebuilds=" + runtime.stats().rebuilds);
        });
        queue(40, mc -> {
            var runtime = grill24.fishtastic.client.ambient.ClientAmbientFish.natural();
            int survived = 0, keyChanged = 0, jumped = 0, originMoved = 0;
            double worst = 0;
            for (var p : runtime.individuals()) {
                double[] was = before.get(p.id());
                if (was == null) continue;
                survived++;
                if ((long) was[3] != p.patchKey()) keyChanged++;
                double d = Math.sqrt(Math.pow(p.x() - was[0], 2) + Math.pow(p.y() - was[1], 2) + Math.pow(p.z() - was[2], 2));
                worst = Math.max(worst, d);
                if (d > 4.0) jumped++;
            }
            for (var r : runtime.domainReports()) {
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
            for (var p : runtime.individuals()) {
                if (base[0] == 0L) base[0] = p.patchKey();
                // Only residents that are actually swimming: Phase 5 turnover retires individuals
                // continuously (EXCLUDED/EXPIRED), and one already mid-fade at the snapshot can be
                // gone before the window closes without any retirement starting inside it. Stable
                // ones can only leave through a retirement this window will count.
                if (p.retiring()) continue;
                tracked.add(p.id());
            }
            base[1] = runtime.patchColumns(base[0]);
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
            for (var p : runtime.individuals()) {
                if (!tracked.contains(p.id())) continue;
                present++;
                if (p.retiring()) retiring++;
            }
            var s = runtime.stats();
            int columns = runtime.patchColumns(base[0]);
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
    // ── Fish companions ─────────────────────────────────────────────────────────

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
    private static void queueCompanionBondScene() {
        queue(1, RenderSelfTest::paradeSetup);
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
        queue(1, mc -> mc.player.connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new grill24.fishtastic.network.RenameCompanionPacket(0, "Bubbles"))));
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

    private static void queueCompanionSimScene() {
        queue(1, RenderSelfTest::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devOverlay(true));
        // One fish is one body now (party spec §3): a small bluegill is a small solo, not a trio,
        // and every fish is drawn at its true size (D11).
        String[][] fish = {{"bluegill", "9", "small_solo"}, {"bluegill", "25", "solo"}, {"starfish", "20", "benthic"},
                {"garden_eel", "40", "anchored"}, {"glass_squid", "20", "drift"}, {"giant_manta_ray", "150", "glide"}};
        queue(1, mc -> server(mc, RenderSelfTest::clearParties));
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
        queue(60, mc -> grill24.fishtastic.client.companion.ClientCompanions.devFake("hostile"));
        queue(120, mc -> {
            companionLog("fake hostile close");
            screenshot(mc, "companionsim", "hostile_fp");
        });
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devFake("clear"));
        // The sampling cost while walking (plan §7 risk 3): 4 blocks/s for 10 s across fresh
        // ground, so the block window re-centres several times.
        queue(40, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devResetCost();
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
            grill24.fishtastic.client.companion.ClientCompanions.devOverlay(false);
            server(mc, RenderSelfTest::clearParties);
        });
    }

    /**
     * The three-slot party (docs/fish-companions-party.md §3, D6, D11): a school of three
     * mixed-size bluegill from three slots, then a mixed party of three species (one solo each),
     * in first and third person, with and without the owner sneaking (each fish's name above it).
     */
    private static void queueCompanionPartyScene() {
        queue(1, RenderSelfTest::paradeSetup);
        queue(1, mc -> grill24.fishtastic.client.companion.ClientCompanions.devOverlay(true));
        String[][] parties = {
                {"school", "bluegill 9", "bluegill 16", "bluegill 24"},
                {"mixed", "bluegill 12", "starfish 40", "garden_eel 40"}};
        // groups and bodies each party makes
        String[][] expect = {{"1", "3"}, {"3", "3"}};
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
        queue(20, mc -> {
            grill24.fishtastic.client.companion.ClientCompanions.devOverlay(false);
            server(mc, RenderSelfTest::clearParties);
        });
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
                grill24.fishtastic.client.companion.ClientCompanions.debugState());
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

    /**
     * One world command on either side: the integrated server's thread locally, or the connection
     * when the marker joined a dedicated server (where it is asynchronous and needs the client to be
     * op'd — the ambientcontext scene sends none, so this exists for scenes that really need one).
     */
    private static void worldCommand(Minecraft mc, String command) {
        if (remoteServer == null) {
            server(mc, s -> run(s, command));
        } else if (mc.player != null) {
            mc.player.connection.sendCommand(command);
        }
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    static void check(String name, boolean pass, String detail) {
        Fishtastic.LOGGER.info("[selftest] CHECK {}: {} {}", name, pass ? "PASS" : "FAIL", detail);
    }

    /** A diagnostic line: evidence for the log that passes no judgement. */
    static void note(String message) {
        Fishtastic.LOGGER.info("[selftest] NOTE {}", message);
    }

    /** A diagnostic line plus where the player is, whose block Y sets every column's scan band. */
    private static void noteState(String label) {
        BlockPos p = Minecraft.getInstance().player.blockPosition();
        note(label + " at " + p.getX() + "," + p.getY() + "," + p.getZ() + " "
                + grill24.fishtastic.client.ambient.ClientAmbientFish.natural().debugState());
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
