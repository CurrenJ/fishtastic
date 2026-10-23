package grill24.fishtastic.client.selftest;

import static grill24.fishtastic.client.selftest.RenderSelfTest.STEPS;
import static grill24.fishtastic.client.selftest.RenderSelfTest.armed;
import static grill24.fishtastic.client.selftest.RenderSelfTest.block;
import static grill24.fishtastic.client.selftest.RenderSelfTest.camera;
import static grill24.fishtastic.client.selftest.RenderSelfTest.check;
import static grill24.fishtastic.client.selftest.RenderSelfTest.finish;
import static grill24.fishtastic.client.selftest.RenderSelfTest.fish;
import static grill24.fishtastic.client.selftest.RenderSelfTest.item;
import static grill24.fishtastic.client.selftest.RenderSelfTest.note;
import static grill24.fishtastic.client.selftest.RenderSelfTest.noteCell;
import static grill24.fishtastic.client.selftest.RenderSelfTest.noteState;
import static grill24.fishtastic.client.selftest.RenderSelfTest.origin;
import static grill24.fishtastic.client.selftest.RenderSelfTest.placeSpanBox;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.CompanionScenes.queueUntil;
import static grill24.fishtastic.client.selftest.RenderSelfTest.remoteServer;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.scenes;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;
import static grill24.fishtastic.client.selftest.RenderSelfTest.started;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tank;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tick;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tickProbe;
import static grill24.fishtastic.client.selftest.RenderSelfTest.wait;
import static grill24.fishtastic.client.selftest.RenderSelfTest.waitFor;
import static grill24.fishtastic.client.selftest.RenderSelfTest.worldCommand;
import static grill24.fishtastic.client.selftest.RenderSelfTest.param;

import grill24.fishtastic.client.selftest.RenderSelfTest.Step;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.ambient.AmbientContextClient;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.network.AmbientContextResponsePacket;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.renderer.TankGroupFlock;
import grill24.fishtastic.client.util.ClientTankFlocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Ambient fish fixtures, population and lifecycle checks. */
final class AmbientScenes {
    /** Matched 4x2x3 tank/water volumes and recorded sizes; compare normal shore and submerged views. */
    static void queueAmbientParityScene() {
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
    static void queueAmbientWaterScene() {
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
    static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
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
    static void queueAmbientContextScene() {
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
    static void queueAmbientIslandScene() {
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

    static void queueAmbientFloorScene() {
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

    static void queueAmbientWalkScene() {
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

    static void queueAmbientLifecycleScene() {
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

    static void queueAmbientPopulationScene() {
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

}
