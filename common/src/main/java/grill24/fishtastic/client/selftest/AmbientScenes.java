package grill24.fishtastic.client.selftest;

import grill24.fishtastic.client.selftest.RenderSelfTest.Step;

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

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.client.renderer.TankGroupFlock;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
            server(mc, server -> run(server, "tick freeze"));
        });
        queue(10, mc -> frozenSteps[0] = fixture[0].steps());
        queue(20, mc -> {
            check("ambientparity.fixture.freeze", frozenSteps[0]==fixture[0].steps(),
                    "steps="+frozenSteps[0]);
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
        queue(140, mc -> ambientWaterFixture("pond", 1, 1, 1, false));
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

    static void queueAmbientIslandScene() {
        // The deep ocean is built above the superflat terrain, so its foundation must not fall.
        queue(1, mc -> worldCommand(mc, "fill 0 39 0 40 39 40 minecraft:sandstone"));
        queue(2, mc -> worldCommand(mc, "fill 0 40 0 40 51 40 minecraft:water"));
        queue(2, mc -> worldCommand(mc, "fill 0 52 0 40 63 40 minecraft:water"));
        queue(2, mc -> worldCommand(mc, "fill 17 40 19 20 64 22 minecraft:stone"));
        queue(2, mc -> worldCommand(mc, "fillbiome 0 40 0 40 55 40 minecraft:ocean"));
        queue(2, mc -> worldCommand(mc, "fillbiome 0 56 0 40 72 40 minecraft:ocean"));
        queue(2, mc -> {
            FishtasticClientConfig.setTestOverride(FishtasticClientConfig.AMBIENT_FISH_ENABLED_KEY, true);
            FishtasticClientConfig.setAmbientDensityTestOverride(50);
            ClientAmbientFish.productionTest(true);
            camera(mc, 19.5, 66.6, 21.5, -90, 25);
        });
        waitFor(10, 900, mc -> ClientAmbientFish.natural().stats().active >= 20, mc -> {
            check("ambientisland.population", ClientAmbientFish.natural().stats().active >= 20,
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
            check("ambientisland.sustained", ClientAmbientFish.natural().stats().active >= 20,
                    ClientAmbientFish.natural().diagnostics().debugState());
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
        });
    }

    static void queueAmbientFloorScene() {
        // Scenes share one client session; count expiries from this scene's own start rather than
        // inheriting the long-run population scene's retirement total.
        final long[] expiredBasis = {0L};
        final java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> initialResidents =
                new java.util.LinkedHashMap<>();
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
            expiredBasis[0] = ClientAmbientFish.natural().stats().retireExpired;
            camera(mc, 20.5, 63, 20.5, 90, 5);
        });
        waitFor(10, 900, mc -> ClientAmbientFish.natural().stats().active >= 8
                && ClientAmbientFish.natural().diagnostics().individuals().stream()
                .anyMatch(p -> p.species().endsWith("garden_eel") && !p.retiring()), mc -> {
            var runtime = ClientAmbientFish.natural();
            var stats = runtime.stats();
            check("ambientfloor.population", stats.active >= 8 && stats.individuals <= 64 && stats.rosterDrawn > 0,
                    "active=" + stats.active + "/" + stats.individuals + " drawn=" + stats.rosterDrawn
                            + " pending=" + stats.rosterPending + " noDomain=" + stats.rosterNoDomain
                            + " " + runtime.diagnostics().debugState());
            initialResidents.putAll(liveResidents(runtime, mc.level.getGameTime()));
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
            var continuity = lifespanContinuity(initialResidents, individualMap(runtime), mc.level.getGameTime());
            long expiredSinceStart = runtime.stats().retireExpired - expiredBasis[0];
            check("ambientfloor.continuity", continuity.isComplete(expiredSinceStart),
                    "scheduledAlive=" + continuity.expectedAlive() + " preserved=" + continuity.preserved()
                            + " completedExpiries=" + continuity.completedExpiries() + " observedExpiries="
                            + expiredSinceStart + " " + runtime.diagnostics().debugState());
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
        });
    }

    static void queueAmbientWalkScene() {
        var initialResidents = new java.util.LinkedHashMap<Long,
                grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual>();
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
        waitFor(10, 900, mc -> ClientAmbientFish.natural().stats().active >= 8, mc -> {
            var runtime = ClientAmbientFish.natural();
            var stats = runtime.stats();
            check("ambientwalk.populated", stats.active >= 8 && stats.individuals <= 32 && stats.rosterDrawn > 0,
                    "active=" + stats.active + "/" + stats.individuals + " drawn=" + stats.rosterDrawn
                            + " pending=" + stats.rosterPending + " noDomain=" + stats.rosterNoDomain
                            + " " + runtime.diagnostics().debugState());
            initial[0] = runtime.stats().domainsLost;
            initial[1] = runtime.stats().retireExpired;
            initialResidents.putAll(liveResidents(runtime, mc.level.getGameTime()));
        });
        for (int step = 1; step <= 8; step++) {
            final double x = 20.5 + (step <= 4 ? step : 8 - step);
            queue(40, mc -> camera(mc, x, 65, 20.5, 90, 20));
        }
        queue(80, mc -> {
            var runtime = ClientAmbientFish.natural();
            var continuity = lifespanContinuity(initialResidents, individualMap(runtime), mc.level.getGameTime());
            long expiredSinceStart = runtime.stats().retireExpired - initial[1];
            check("ambientwalk.noPatchRetirement", runtime.stats().domainsLost == initial[0], runtime.diagnostics().debugState());
            check("ambientwalk.continuity", continuity.isComplete(expiredSinceStart),
                    "scheduledAlive=" + continuity.expectedAlive() + " preserved=" + continuity.preserved()
                            + " completedExpiries=" + continuity.completedExpiries() + " observedExpiries="
                            + expiredSinceStart);
            ClientAmbientFish.productionTest(false);
            FishtasticClientConfig.clearTestOverrides();
        });
    }

    private record LifespanContinuity(int expectedAlive, int preserved, int completedExpiries) {
        boolean isComplete(long observedExpiries) {
            return expectedAlive > 0 && preserved == expectedAlive && observedExpiries >= completedExpiries;
        }
    }

    private static java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual>
    liveResidents(grill24.fishtastic.client.ambient.AmbientFishRuntime runtime, long now) {
        var residents = new java.util.LinkedHashMap<Long,
                grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual>();
        for (var fish : runtime.diagnostics().individuals()) {
            if (!fish.retiring() && fish.bornTick() + fish.lifetimeTicks() > now) residents.put(fish.id(), fish);
        }
        return residents;
    }

    private static java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual>
    individualMap(grill24.fishtastic.client.ambient.AmbientFishRuntime runtime) {
        var individuals = new java.util.LinkedHashMap<Long,
                grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual>();
        for (var fish : runtime.diagnostics().individuals()) individuals.put(fish.id(), fish);
        return individuals;
    }

    private static LifespanContinuity lifespanContinuity(
            java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> initial,
            java.util.Map<Long, grill24.fishtastic.client.ambient.AmbientDiagnostics.Individual> current,
            long now) {
        int expectedAlive = 0, preserved = 0, completedExpiries = 0;
        for (var fish : initial.values()) {
            long departure = fish.bornTick() + fish.lifetimeTicks();
            if (departure > now) {
                expectedAlive++;
                var resident = current.get(fish.id());
                if (resident != null && !resident.retiring() && resident.patchKey() == fish.patchKey()
                        && resident.species().equals(fish.species())) preserved++;
            } else if (departure + grill24.fishtastic.client.ambient.AmbientTuning.POPULATION_FADE_TICKS <= now) {
                completedExpiries++;
            }
        }
        return new LifespanContinuity(expectedAlive, preserved, completedExpiries);
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
