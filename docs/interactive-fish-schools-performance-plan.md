# Interactive fish schools: performance plan

Status: **0, A0, A, C and E done** (2026-10-25, uncommitted, all A/B'd on the `fishschoolperf` scene); B, D and F
dropped by the owner; the 60 s safety rescan is kept (see Decisions). No decisions are open. Keep the step table and the
findings current as work lands: what was measured, what changed, what was decided.

Scope: the interactive-school path end to end. On the server that is `FishSchoolManager` and the
`FishSchoolSim` / `FlockEngine` it steps. On the client it is `FishSchoolReplica` (the lockstep
copy) and `FishSchoolClientRuntime` (draw, trail and bite fountain). Behaviour is defined in
[the design spec](interactive-fish-schools-design-spec.md); motion and lockstep are in
[the motion plan](interactive-fish-schools-motion-plan.md).

Findings 1-7 below are the audit's estimates from reading the code; [Step 0 results](#step-0-results)
replaces them with numbers. No fix is called a win without an A/B against those numbers.

## Steps

| Step | What | Status |
|---|---|---|
| 0 | Perf self-test: server school tick time, late-join catch-up time, per-frame mote submit time | Done 2026-10-25: `fishschoolperf` scene, baseline below |
| A0 | Unchanged rescans adopted by identity, so `tickVisitsAndMembers` stops comparing ~4000 cells per school every tick (new finding 0) | Done 2026-10-25: server tick 2.9 ms → 0.94 ms (A/B below) |
| A | Water region computed once, then updated only on block changes inside it (replaces the 1 s rescan) | Done 2026-10-25: server tick 0.94 → 0.085 ms idle, max 23 → 0.37 ms |
| B | Spawn scheduling checks the region schedule before scanning for water | Not done: measured ~8 us/tick mean (0.25 ms once a second per player, at most 2 players). **Dropped** (owner, 2026-10-25) |
| C | Hook-check path search bounded at the accepted length | Done 2026-10-25: wall-phase hooks 160 us / 9.1 ms → 8.6 us / 0.59 ms |
| D | Schools with no player in tracking range pause engine steps and region work | Not done: after A only the engine step is left, 50-75 us/tick for six schools; risks pausing GameTest schools. **Dropped** (owner, 2026-10-25) |
| E | Replay work capped: time budget per tick; new epochs while nobody is watching | Done 2026-10-25: late-join tick 52-61 ms → 0.69 ms |
| F | Client motes: one submit per sprite per school, ring buffer, distance cull | Not done: measured 40-55 us/frame. **Dropped** (owner, 2026-10-25) |

All server school work is now ~0.08-0.13 ms per tick at six schools (it was 2.9 ms), and the
late-join hitch is gone. What is left is listed under [Results after A, C, E](#results-after-a-c-e).

## Findings

Roughly by expected impact. Finding 0 was found by step 0; 1-7 are the audit's.

### 0. Region comparison every tick (server, measured)

`tickVisitsAndMembers` compared the cached region with `runtime.water` every tick
(`cells.equals(runtime.water)`). `runtime.water` kept the set from the epoch's first scan, while
each 1 s rescan builds a new, equal `HashSet`, so the identity shortcut in `AbstractSet.equals`
never fired after the first rescan: ~4000 `BlockPos` lookups per school per tick, about 2.2 ms of
the 2.9 ms server tick at six schools. Fixed in A0 by adopting an unchanged rescan's set.

### 1. Constant water rescan (server)

`FishSchoolManager.tickVisitsAndMembers` calls `currentWaterRegion` for every school whose anchor
chunk is loaded, every tick. `RegionCache` expires after `WATER_REVALIDATE_TICKS` (20), so each
school re-runs `discoverConnectedWater` once a second. That happens whether or not any player is
near.

Per cell visited:
- `validWaterCell` on each of 6 neighbours: `hasChunkAt`, a fluid-state lookup, a block-state
  lookup and the collision shape.
- When the species is set: `level.getBiome`, then `FishProfile.Zone.resolve` (about 6 biome tag
  checks plus an `EnumSet` allocation), then `FishtasticFishItem.isZoneEligible` (a profile
  registry lookup).

Since 2026-10-25 a region can hold 400 columns and up to `MAX_WATER_CELLS` = 4400 cells; it used
to be 256 cells (see the motion plan, "Bobbers beside a fish got no bite"). That makes one rescan
up to ~17x heavier than before. At the per-dimension maximum of 6 schools the estimate is
~150k block lookups and ~26k biome/zone checks a second. Nearly all of them return what the
previous scan returned.

When the result differs from `runtime.water`:
- The whole new column list goes out as a `FishSchoolSim.Water` input (`broadcastInput`) to
  every tracking player.
- The server engine and every client copy rebuild their domain (`FishSchoolSim.water` →
  `buildDomain` + `rebuildPreserving`).
- The input is appended to `runtime.inputs` for good. Every later setup packet resends it, and
  every late joiner replays it.

Likely churn sources, all unverified: surface ice forming and melting in cold biomes (an iced
cell stops being source water); edge cells in chunks loading and unloading (`hasChunkAt` false
drops them); player edits.

### 2. Spawn scheduling scans before checking it is due (server)

`scheduleNearbyRegionAttempts` runs every 20 ticks for up to 2 players. It first calls
`nearestWater(player, 12, 5, 2)`: up to 25 x 25 x 8 = 5000 `validWaterCell` calls. Only then
does it look up the region's schedule, which allows one attempt per
`fishSchoolAttemptIntervalTicks` (default 24000, one in-game day).

That is about 10k wasted block lookups a second whenever anyone is online, with or without
schools. `findRegion` is also a linear stream over up to `MAX_RETAINED_REGIONS` (4096) entries
each time.

### 3. Late-join and replay hitches (client, render thread)

`FishSchoolReplica.tick` replays `REPLAY_STEPS_PER_TICK` = 4000 engine steps per client tick
until the copy catches up. A client entering tracking range replays from the epoch's step 0,
and the server's setup packet carries every input so far (`List.copyOf(runtime.inputs)`).

- A default visit lives 12000 ticks, so a late joiner can replay up to 12000 steps: three ticks
  at 4000 steps each.
- A late or misordered input, or the first fingerprint mismatch, sets `replayNeeded`. `restart`
  then replays again from step 0.
- A new epoch only starts once `runtime.inputs` passes `EPOCH_INPUT_LIMIT`.

The cost of one `FlockEngine.step` with 2-8 fish is unknown. So 4000 steps on the render thread
could be negligible or a visible hitch: step 0 measures it.

### 4. Unbounded path search on hook checks (server)

`reserveNearbyHooks` runs every 5 ticks for up to 8 players (`MAX_HOOK_CHECKS_PER_TICK`). For each
player with a line out over school water, `hookChoice` runs `findPath` once for each AVAILABLE
member within `interestRadius`.

`findPath` is a breadth-first search over the school's region and allocates a `HashMap` each
time. The result is rejected when the path is longer than `radius + 3`, but the search does not
stop at that depth. An unreachable bobber therefore explores the whole region: now up to 4400
cells, for each member, every 5 ticks. This only costs anything while someone is fishing near a
school.

### 5. Schools simulate with nobody near (server)

`advanceSims` steps the engine every tick, and finding 1 rescans the region every second, for
every school whose anchor chunk is loaded. That includes schools no player is within
`trackingRange` of: spawn-chunk schools, or ones near a different player than the one who left.
Nobody can see these schools or get a bite from them.

### 6. Mote draw and bookkeeping (client)

- `submitTrail` makes one `submitCustomGeometry` per mote. The cap is up to `MAX_TRAIL_MOTES`
  (256) per school, and one bite fountain adds ~130.
- `tickTrail` removes motes with `ArrayList.remove(i)`. Both cap loops use `remove(0)`. Each call
  shifts the list, so the work is quadratic in the worst case (small at 256).
- Motes have no distance or frustum culling. Fish are only drawn within tracking range (48), but
  their motes are submitted regardless.

### 7. Minor

`activeSchools`, `activeMembers` and `globalActiveSchools` are full stream passes, run each spawn
attempt. `findRegion` is a linear scan (finding 2). These are cheap; tidy them only while
already in the code.

## Proposals

### Step 0: measure

A perf self-test scene, using the existing benchmark and perfbench infrastructure in
`client/selftest`. It records:

- **Server:** wall time of `FishSchoolManager.tick` split by phase (`advanceSims`, region work,
  `reserveNearbyHooks`, `syncNearbyPlayers`). Run with 6 schools in large, deep water (the
  `fishschoolreach` pool, scaled up) and one player fishing.
- **Client:** time for a late join to catch up into a 10-minute-old school (12000 steps), and the
  cost of one `FlockEngine.step` at 2, 4 and 8 fish.
- **Client:** per-frame time in `submitWorld` with full trails plus one bite fountain.
- **Churn:** how many `Water` inputs a school gets over 10 minutes beside a freezing lake and
  beside a chunk edge.

Record the baseline numbers here before any fix.

### A. Region computed once, then change-driven

- Compute the region when the school forms (already done in `tryCreateSchool`) and when an
  epoch starts.
- Cache each cell's zone-eligibility result with the region. Biome and height do not change
  during a visit.
- Hook block changes: a mixin on a central block-change path (to find: `ServerLevel` block
  update / chunk `setBlockState`; must exist on every loader). It tests the changed position
  against each active school's region bounding box, at most 6 AABB tests per change. On a hit,
  mark the school dirty.
- Debounce: rescan a dirty school about 20 ticks after its last change, so a burst of edits
  costs one rescan. The rescan re-checks water only and reuses the cached zone results.
- Send a `Water` input only when the cell set actually changed. This is already true; with the
  rescan gone it also becomes rare.
- Chunk loads inside a region's box can mark it dirty too, so edge cells excluded while
  unloaded rejoin.
- Decided 2026-10-25 (owner): keep a slow safety rescan, every 60 s, spread over 200 ticks by school.

### B. Spawn scheduling checks the schedule first

**Dropped** (owner, 2026-10-25): the measured gain is microseconds per tick (see Steps). Kept for the record.

Compute the region coordinates from the player's own position, and check that region's schedule
before calling `nearestWater`. Water found in a neighbouring region would be scheduled under its
own region next time, so nothing changes behaviourally. Optionally index regions in a map keyed
by (x, z) instead of the linear `findRegion`.

### C. Bounded path search

Stop `findPath` at depth `radius + 3`, the length `hookChoice` accepts anyway, and/or cache
(member cell, hook cell) results for a few seconds. This keeps the bobber-eligibility behaviour
identical; the `fishschoolreach` scene verifies it.

### D. Pause schools nobody is near

**Dropped** (owner, 2026-10-25): the measured gain is microseconds per tick (see Steps). Kept for the record.

When no player is within `trackingRange` of a school's anchor, skip its engine step and region
work. The visit's lifetime still counts down in game time. Saved positions stay valid, and
clients only ever hold a school while in range.

To check:
- Is `activeUntil` / departure handled correctly while paused?
- Does resuming need a new epoch, or does the next step simply continue?

### E. Capped replay work

- **Time budget:** replace the fixed `REPLAY_STEPS_PER_TICK` with a time budget (for example
  2 ms per tick), keeping a step-count ceiling.
- **Fresh epochs:** start a new epoch, a setup built from current positions as `runtime(...)`
  already does at `EPOCH_INPUT_LIMIT`, once an epoch is old (steps or inputs). Only do it while
  `runtime.trackers` is empty, so no watcher sees the copy rebuild. Late joiners then replay a
  short history instead of a whole visit.
- Decide both with the step 0 numbers. If a step is a few microseconds, only the restart-on-
  mismatch case may matter.

### F. Client motes

**Dropped** (owner, 2026-10-25): the measured gain is microseconds per tick (see Steps). Kept for the record.

- One `submitCustomGeometry` per (school, sprite) with all its motes in one buffer callback,
  instead of one per mote.
- Ring buffer or swap-remove instead of shifting the `ArrayList`.
- Skip motes beyond a distance from the camera; the cue's purpose is near-field.
- Decide with the step 0 per-frame numbers.

## Step 0 results

Scene `fishschoolperf` (Fabric 26.1.2 dev client, integrated server, 2026-10-25): six bluegill
schools (3-4 fish each) in six 29x29x11 pools, all within tracking range of a creative player
hovering between pools (0,0) and (0,-34). Phases of 600 ticks unless noted, counters reset
before each. Server numbers are per overworld tick: `FishSchoolPerf.skipLevel` leaves out
dimensions with no players and no schools, which diluted the first run's numbers 3x.
Logs: `build/selftest-logs/fishschoolperf-fabric-{baseline,identity}.log`.

**Server, before → after A0** (mean / max per tick):

| Phase | Tick | `visits` | `region` (inside visits) | `hooks` | `advance` | `schedule` | `Water` inputs |
|---|---|---|---|---|---|---|---|
| idle | 2.94 / 15.3 ms → 0.94 / 23.2 ms | 2.87 → 0.87 ms | 0.65 → 0.74 ms | 2 us | 57 us | 8 us / 0.28 ms | 0 |
| fishing | 2.82 / 15.8 → 0.96 / 24.2 ms | 2.73 → 0.86 ms | 0.66 → 0.72 ms | 7-9 us / 2.9 ms | 75 us | 8 us | 0 |
| wall | 2.78 / 16.2 → 0.94 / 18.1 ms | 2.70 → 0.80 ms | 0.67 → 0.68 ms | 14-65 us / 0.9-2.1 ms | 57 us | 8 us | 0 |
| freeze (1200 ticks) | 2.91 / 23.7 → 0.92 / 26.6 ms | 2.85 → 0.86 ms | 0.71 → 0.72 ms | 1 us | 50 us | 7 us | 36 → 26 |

- **Region rescans** (finding 1): 0.3 scans/tick (six schools, once a second each), ~4000 cells
  and ~4000 zone checks per scan, ~2.4 ms per scan. The 15-26 ms max ticks are all six schools
  rescanning on the same tick: they were created together, so their caches expire together.
  Single scans peaked at 3-10 ms.
- **Churn** (finding 1): a frozen-river pool at random tick speed 30 sent a `Water` input on 26-36
  of its 60 rescans in 1200 ticks (300-390 surface cells iced). Each is a domain rebuild on the
  server and every client, and is kept in the epoch's input history for good. Chunk-edge churn
  was not measured.
- **Hook paths** (finding 4): with the bobber just over the wall from a fish, 119 of 120 hook
  checks searched and refused ("no water path"); up to 1883 cells per search, 655 cells/tick,
  65 us/tick mean. Cheap; the 2.9 ms max was one tick.
- **Schedule** (finding 2): the player hovers 9 blocks over water, so `nearestWater` finds none and
  scans all 5000 cells every 20 ticks: ~0.25 ms each time, 8 us/tick mean.
- `advance` (six engines stepping): 50-75 us/tick.

**Client** (render thread):
- `submitWorld`: 37-55 us/frame mean, 0.26 ms max; 70-83 mote submits/frame; max 118 motes on a
  school during a bite fountain. Finding 6 is real but not worth a step (F).
- `tick` (six copies stepping, trails): 73-105 us mean, 0.6 ms max.
- **Late join**: `forgetTrackingForTest` made the server resend all six setups at step ~2063.
  The six copies replayed 12,800 steps in one client tick: **59-61 ms**, a dropped-frame hitch.
- **Replay bench** (one copy, 400 columns, 12000 steps, second round): 3.8 us/step at 2 fish,
  4.1 at 4, 5.2-7.7 at 8. One `REPLAY_STEPS_PER_TICK` (4000) slice is 15-33 ms per school. A
  late join into six full-length visits would be ~3 ticks of ~100 ms each.
- Frame counts fell after the first phase (3590 frames idle, then 1150-1310 fishing, 900 wall,
  1800 in the 1200-tick freeze) while school submit time stayed flat. Not school code: most
  likely the OS/driver throttling a game window with no input (the owner sees fullscreen fps drop
  after a while without input, recovering on mouse movement). Frame counts from unattended
  scenes are therefore not comparable across phases; judge client cost by per-frame and per-tick
  times, as above.

**Lockstep:** 0 mismatches and 0 replays on every copy before and after the late join, all runs.

## Results after A, C, E

Same scene, same machine (Fabric; logs `build/selftest-logs/fishschoolperf-fabric-{stepA,stepCE}.log`):

| Phase | Server tick mean / max: baseline → A0 → A → A+C+E | Notes |
|---|---|---|
| idle | 2.94 / 15.3 → 0.94 / 23.2 → 0.085 / 0.37 → 0.082 / 0.31 ms | 0 rescans; `advance` (58 us) is now most of it |
| fishing | 2.82 / 15.8 → 0.96 / 24.2 → 0.14 / 6.7 → 0.13 / 6.8 ms | the 6.8 ms max is one school's 60 s safety rescan |
| wall | 2.78 / 16.2 → 0.94 / 18.1 → 0.23 / 9.3 → 0.09 / 0.65 ms | C: largest search 3240 → 831 cells |
| freeze | 2.91 / 23.7 → 0.92 / 26.6 → 0.09 / 3.1 → 0.10 / 2.7 ms | `Water` inputs 26-36 → 9-12 (debounced); icing still reaches the copies |

- **A** (`ServerLevelSchoolsMixin` + `RegionCache`): a school's region is rescanned only when a
  block in its search box (plus one) changed, 20 ticks after the last change or 100 after the
  first if changes never stop; every 100 ticks while the last scan met an unloaded chunk; and a
  60 s safety rescan, spread over 200 ticks by school. Habitat results are cached per cell, so
  rescans make no biome/zone checks (`zoneChecks` 1189/tick → 0).
- **C** (`withinPath`): breadth-first to depth `floor(radius + 3)` and no further; the same
  acceptance rule as before, with no path list built.
- **E**: one 2 ms replay budget per client tick shared by all copies (each still takes at least
  32 steps; caught-up copies ignore it), plus a fresh epoch for a school no client holds once its
  epoch is 1200 steps old and it has no live anchor. The scene's late join now lands on a fresh
  epoch (copies at step 57, 396 steps replayed in all, max client tick 0.69 ms). The budget path
  is covered by `FishSchoolReplicaTest.aSpentReplayBudgetSlowsTheCatchUpButKeepsItExact`.
- Lockstep: 0 mismatches and 0 replays on every copy, every run. Fabric GameTests 389/389 (5
  school tests). The scene passes 7/7 on Fabric, NeoForge and Forge (the mixin applies on all
  three, and the freeze phase's `Water` inputs prove the hook fires).

Left, by size: the 60 s safety rescan (one ~2-7 ms tick per school per minute); `advance` at
~10 us per school per tick; the schedule scan (finding 2). None is a visible problem.

## Decisions

Made:
- 2026-10-25: **B, D and F dropped** (owner). Measured gains were microseconds per tick; D also
  risked pausing schools in GameTests.
- 2026-10-25: **60 s safety rescan kept** (owner): one rescan per school per 60 s, spread over
  200 ticks by school (`REGION_SAFETY_RESCAN_TICKS`, `REGION_SAFETY_SPREAD_TICKS`). It covers
  changes that bypass `setBlock`, at one 2-7 ms tick per school per minute.
- Frame rate fell in the later phases (~120 → 30-40 fps): closed 2026-10-25 as idle-input
  throttling by the OS/driver (owner's observation), not school code.

## Handoff (2026-10-25, after A, C, E)

State: all uncommitted. `:common:compileJava` clean; school unit tests 12/12; Fabric GameTests
389/389; `fishschoolperf` 7/7 on Fabric, NeoForge and Forge.

Files touched for this plan:
- `server/FishSchoolPerf.java` (new): timers and counters; `skipLevel` leaves out empty dimensions.
- `server/FishSchoolManager.java`: per-phase timing in `tick` (`reserveNearbyHooks` moved from the
  end of `tickVisitsAndMembers` into `tick`, still last); A0 identity adoption; A (`RegionCache`,
  `WaterScan`, `scanConnectedWater` with zone cache and unloaded-chunk flag, `onBlockChanged`,
  self-registration on the level); C (`withinPath` replaces `findPath`); E (`UNWATCHED_EPOCH_STEPS`
  renewal in `advanceSims`); `forgetTrackingForTest` seam.
- `util/IServerLevelSchools.java` + `mixin/ServerLevelSchoolsMixin.java` (new), registered in
  `fishtastic.mixins.json` and Forge's `fishtastic-common-forge.mixins.json`.
- `school/FishSchoolReplica.java`: deadline-aware `tick`, `MIN_REPLAY_STEPS_PER_TICK`, `setup()`.
- `client/FishSchoolClientRuntime.java`: `Perf`, `replayBenchForSelfTest`, `REPLAY_BUDGET_NANOS`.
- `client/selftest/FishSchoolScenes.java` + `RenderSelfTest.java`: scene `fishschoolperf`.
- `common/src/test/.../FishSchoolReplicaTest.java`: budget test.

Run the scene: write `fishschoolperf` to the loader's game-dir marker (`fabric/run`, `neoforge/run`,
Forge's `forge/build/f02-run/client`), run `:<loader>:runClient` (about 4 minutes), **remove or
restore the marker**, grep the log for `fishschoolperf`. Forge's game dir already holds a
`stress512` marker from 2026-10-19; it was left in place (backed up and restored around the run).

Not yet done: the 1.21.1 / 1.20.1 ports (the mixin target must be re-checked per version), and the
owner's look at the open decisions above.

## Log

- 2026-10-25: owner dropped B, D and F and kept the 60 s safety rescan. Remaining: commit, then the ports.

- 2026-10-25: A, C and E done and A/B'd (table in "Results after A, C, E"); B, D, F recommended
  against on the numbers. Verified on Fabric, NeoForge and Forge; Fabric GameTests 389/389.

- 2026-10-25: step 0 done: `fishschoolperf` scene written and run on Fabric (baseline above).
  Found and fixed finding 0 (A0): server tick 2.9 ms → 0.94 ms at six schools, A/B'd on the same
  scene. F measured not worth doing; E justified by a 60 ms late-join tick.

- 2026-10-25: step 0 counters added (see Handoff); no numbers yet.

- 2026-10-25: audit written; no code changed.
