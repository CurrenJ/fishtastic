# Fish in World - Phase 2 handoff (bounded natural-water domains)

Session continuation checkpoint: [fish-in-world-handoff.md](fish-in-world-handoff.md).
Plan: [fish-in-world-implementation-plan.md](fish-in-world-implementation-plan.md) §Phase 2.
Phase 1 (shared visuals, dev fixture, `ambientparity` scene) is closed; see
[fish-in-world-phase1.md](fish-in-world-phase1.md).
Status: **gate closed 2026-10-13** (refreshed 28-image sets on both loaders reviewed; independent
code-review round resolved — B1 fixed and re-verified, S1/S3/S4 fixed, S2 disproven by measurement).

**No phases past 2 are in this work.** There is no fishing resolver, no networking, no density
policy, no settings and no release default: nothing in normal play starts or sees ambient fish.
Everything here is development-only, reachable from the harness through
`ClientAmbientFish.startNatural()` behind the same `DevEnvironmentCheck` gate as the Phase 1
fixture.

## What Phase 2 adds

Ambient fish now live in real, bounded, world-anchored water that is discovered around the player.

**Discovery — one water patch, not a quantized grid** (`fishsim/ambient/`, Minecraft-free):

- `WaterSource` is the cell interface discovery reads the world through: `flags` (LOADED / WATER /
  COLLISION), `fluidTop`, `collisionTop`.
- `WaterColumn` is one scanned column: ascending `[lo, hi]` water spans in world Y plus a
  `truncated` flag for "the scan hit its bound while still in water".
- `WaterDiscovery` sweeps expanding rings around the player within `radius` 24 and a band of
  `+8 / -16` cells, classifying at most `cellBudget` 512 cells and `columnBudget` 48 columns per
  tick. The 5x5 around the player is scanned first, on a rotating cursor and with at most half the
  cell budget, so water beside the player appears at once and can never starve the sweep; the
  sweep's ring cursor only advances over a cell it has dealt with, so a budget that runs out
  mid-ring costs a tick and not the cell. A scanned *wet* column is kept; a dry one is not
  remembered (the ring re-scans it as it comes round, which is how new water inside the window is
  noticed), so the cache is capped at `maxColumns` 4096, evicted farthest-first beyond
  `radius + evictSlack` 8, and a patch with no columns left is forgotten `PATCH_LIFETIME_TICKS` 600
  after its last one. `revalidateSome` refreshes the known columns on a
  rotation, but only those the player's current band actually covers: a column whose stored water
  reaches outside it is kept and counted deferred (`revalidationsDeferred`) instead of being
  re-scanned through a band that cannot see it — the band is the only view discovery gets, and a
  re-scan through it would delete live water or re-write a column truncated against an edge that is
  not a bed. `scanColumn` also probes the cell above the band's top before calling a column
  truncated, so a band top inside a lake does not paint an open surface.
- **Patch identity is adjacency, and the key is what is world-fixed.** A wet column joins an
  *adjacent* discovered wet column's patch (four neighbours), so two pools with ten dry columns
  between them are two patches, and a piece the sweep finds ahead of a gap keys separately until
  discovery connects it — then the older key wins and the younger patch's columns are re-keyed
  (`patchMerges`). `GAP_CELLS` 4 bounds the *vertical* scan instead: four consecutive dry cells
  under a column's last water cell end that column, so a cave beneath a lake stays out of the lake's
  spans. The patch has a **key, seed and origin fixed the first time its first column is found, and
  they never move** for the life of the patch (`keyInstance` starts at 1; key 0 means "no patch").
  A domain's extent is the currently discovered set of columns; only the *set* changes, never the
  frame. When eviction retires the patch object entirely it reserves the number (`keyReserved`), so
  the same water keeps its identity on re-entry: `createPatch` hands the reserved key back — with
  its original seed, origin and birth tick — when the first column rediscovered then lies in the
  same 16-block `PATCH_QUANTUM` as that seed. Re-entry through a different quantum mints a fresh
  key and a fresh frame; the harness pools are quantum-aligned because of it (see limitations).

**Domain — a bounded swim volume** (`fishsim/domain/WaterDomain`, implements `FlockDomain`):

- Built from columns: per-column water spans, bed and surface heights, a pool-wide `topMargin`,
  and six `openFaces`. It answers `contains`, `bodyFits`, `columnFits`, `bedAt`, `surfaceAt`,
  `avoidance`, `constrain`, `wallDistance`, `layerDepths`, `sizeGateRun` and the floor field
  (`FloorField.fromColumnHeights`), so the existing engine, shoal behaviour and locomotion gates
  work unchanged inside it.
- `BodyBox(half = 0.7071 * length, above, below)`: FREE_SWIM `±(0.7071L + 0.125)`, GLIDE and DRIFT
  `±(0.7071L + 0.08 / 0.05)`, BENTHIC `above = L, below = 0`. `topMargin` is the pool-wide maximum
  `above` (the 45 cm glide fish, about 0.398 blocks), so spawn head-room and stepping head-room
  agree and no resident is invalid before it moves.
- `bodyFits` **implies `contains`** at the point itself, as its first check. `contains` reserves the
  pool-wide `topMargin` while a body only needs its own `above`, so without that check a small fish
  could be spawned into the gap between the two rules, where the engine — which steps on `contains`
  — would find it outside its own water on the next rebuild and retire it as invalid.
- `OPEN_FACE_MARGIN` 0.60. A resident within it of an open face is retired: it fades out over 24
  ticks and by `(openFaceDistance / margin)^2`, whichever is further along, and the fade is one-way.
  The band is wider than the widest avoidance zone (GLIDE 0.25 + `DEFAULT_BANK_CUSHION` 0.10), so
  a fish is under 34 % of its size when it starts to disappear and never pivots on an invisible
  plane. Closed water has no band at all.

**Discovery order, and what it means for a domain.** A ring cursor is a distance, not a visited
set, so a moving player can leave a column-sized hole in the sweep's wake; it closes on the next
round. A round classifies every column of the 24-block window once — 2401 columns — and the cell
budget of 512 with 25 band-cells per column admits about 20 columns a tick, so a round takes about
120 ticks (six seconds). Until a hole closes, a scrap of water beyond it is a separate patch, and
the runtime adopts any patch of `MIN_PATCH_COLUMNS` 4 columns or more. A walk along a long river can
therefore hold two of its domains for a while, and the merge retires the absorbed piece's residents
as `REASON_DOMAIN_LOST` and refills them from the surviving patch. The water the player is actually
in never renumbers: the near pass keeps a contiguous known set around them, so the key underfoot is
stable for the whole walk (asserted over 34 blocks in `WaterDiscoveryTest`). The 25 columns of the
near square arrive within a tick or two; water further out arrives as the rings reach it, which is
why a small pond's domain is still filling for its first hundred ticks.

**Population — `common/client/ambient/AmbientFishRuntime` + `AmbientWaterSource`:**

- The fixed pool is `SPECIES` bluegill / acute iaspis / portuguese man o' war / plaice
  (`{30, 45, 40, 35}` cm fallbacks). The rendered size is the species profile's `size().mean()`,
  and a species whose `descriptor.locomotion()` disagrees with `FishVisuals.locomotionOf(anim)` is
  dropped rather than demoted.
- Per tick: discover, revalidate, adopt or retire domains (at most `MAX_DOMAINS` 4, at least
  `MIN_PATCH_COLUMNS` 4 columns, nearest first), rebuild at most one domain per
  `REBUILD_INTERVAL_TICKS` 20 with `rebuildPreserving` carry by `engineIndex`, retire at faces and
  for invalidity, fade, spawn at most `MEMBERSHIP_CHANGES_PER_TICK` 2 changes, step the engines,
  drop dead domains, record stats.
- A domain whose water went quiet and came back is *revived*, not rebuilt from scratch: `revive`
  cancels its residents' fades and keeps their positions, the key and the frame, and defers the
  first rebuild until the patch's cache has refilled to the column count the domain was last built
  from (or `REFILL_TIMEOUT_TICKS` 200 elapse). Rediscovery is budgeted, so rebuilding from the
  corner that arrived first would read residents standing in not-yet-rediscovered water as outside
  the domain and retire them. The wait is bounded, so a patch that really came back smaller is
  rebuilt anyway and the retirements that follow are honest ones.
- A rebuild whose column set the domain builder refuses — a patch scattered around dry land, whose
  bounding box is past the builder's grid cap — keeps the last known water and counts a
  `buildRejection`, retried next interval, so a refused build never retires a live population by
  itself.
- Spawning fills the nearest non-dying domain first (`TARGET_PER_DOMAIN` 8, `MAX_INDIVIDUALS` 32)
  at a position 2.5-10 blocks from the player where the whole body fits, clear of every open face
  and 0.5 blocks from its neighbours.
- `AmbientWaterSource` is the only Minecraft-aware read path. Every read is guarded by
  `hasChunkAt` — discovery must never load a chunk — and a position below the build height answers
  definitive void air (`isOutsideBuildHeight`), because the band is taller than the world is deep.
- Retirement reasons are `REASON_OPEN_FACE` (the water continues), `REASON_INVALID` (the body no
  longer fits after a rebuild) and `REASON_DOMAIN_LOST` (the patch went away). Each is counted per
  resident, so the reasons add up against the completed fades; the number of *domains* that died
  with residents in them is counted separately. A resident that is merely swimming can brush its own
  wall for a tick — the engine steps on `contains` and pushes back through the soft avoidance —
  so the body-fit rule is enforced where it is a promise: at spawn, and at each rebuild, which
  retires what no longer fits.
- Rendering is Phase 1's shared path unchanged: the same `FishVisuals`, `GroupFishPose` and
  per-frame `submitWorld`, so natural fish go through the ordinary world pipeline with no
  capture-side adapter.

## Decisions, and what was rejected
| Decision | Rejected alternative |
|---|---|
| One domain per water patch, keyed and originated at first find | Quantizing to a `VoxelDomain`-style cell grid: the local frame would move as the extent changed, so every resident would need rebasing on every rebuild |
| Rebuild carries local coordinates with no rebasing, because the origin is fixed for the patch's life | Re-anchoring the domain on the player or on the extent's centroid |
| A bottom-cut column has **no** bed (`Float.NaN`, not walkable) | Treating the band's floor as a bed: a benthic fish would stand on terrain it has never seen and be pushed into solid rock |
| A pool-wide `topMargin` for spawn and step clearance | Per-fish margins, which can spawn a fish that is already invalid |
| Closed water gets no fade band; only faces the sweep cut get one | Fading at every domain edge, which would make a pond's banks shimmer |
| The near pass gets half the cell budget, on a rotating cursor | Giving it the whole budget: dry near cells are never remembered, so it would re-scan the same 5x5 every tick and the sweep — the only thing that expands discovery — would never advance |
| A domain keeps its own origin for its whole life, even if the patch's seed was re-derived after an eviction | Rebuilding an existing domain on the patch's current seed, which would move the frame under residents that never left |
| Species dropped when the profile's locomotion disagrees with the animation's | Clamping or demoting to hover, which would ship a wrong-bodied fish |
| `AmbientWaterSource` owns the Minecraft quirks (loaded chunks, build height, a fence's shape rising into the cell above) | Teaching fishsim about block shapes and chunk loading |
| Hostile/aggressive water: none. Ambient fish are decoration-scale residents of the water they were found in | Any tracking, catching or networking (later phases own those) |

## Tests and measured budgets

| Suite | Tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| fishsim | 437 | 401 | 36 | 0 / 0 |
| common | 779 | 778 | 1 | 0 / 0 |

Counts from JUnit XML (`fishsim/build/test-results/test`, `common/build/test-results/test`).
Phase 2 added 54 fishsim tests: 23 in `WaterDiscoveryTest` (patch identity across gaps, the band's
cut columns and band-top truncation probed both ways, deferred revalidation of columns the band
cannot see, the sweep finding water outside the near square, eviction with the key reserved and the
trim tick stamped, key packing, budgets) and 28 in `WaterDomainTest` (spans and partial fluids,
body fitting — including that `bodyFits` never accepts what `contains` rejects — avoidance and the
boundary band, a bottom-cut column having no floor, a 2304-column build, the build bbox grid — a
1x8192 strip fits by exactly one cell, 1x8193 is refused — and a 4000-tick walk-out that keeps the
population off the boundary with no backstop engagement), plus 3 in `AmbientResidentTest` (the
review's S2 placement item, measured and disproven — see limitations; a shrunken domain leaving a
carried resident unhostable rather than frozen; a carried crawler keeping its finite position when
nothing is standable). The review round added the last 12 of the 54: eight in `WaterDiscoveryTest`,
one in `WaterDomainTest` and `AmbientResidentTest`'s three. No common tests were added: the
runtime's own verification is the capture below. No GameTests were added, so no GameTest task was
run.

Measured, from the test log and the capture:

- `WaterDomain.build` of 2304 columns (a 49x49 ocean): **2.08 ms** (assert < 50 ms).
- Discovery: at most 512 classified cells and 48 columns per tick by construction; the runtime
  records the peaks and reports them.
- Open-face retirements in the 4000-tick unit walk: 2, both deliberately seeded.

## Files

New, Phase 2: `fishsim/.../ambient/{WaterSource,WaterColumn,WaterDiscovery}.java`,
`fishsim/.../domain/WaterDomain.java`,
`fishsim/src/test/.../ambient/{SyntheticWater,WaterDiscoveryTest}.java`,
`fishsim/src/test/.../domain/WaterDomainTest.java`,
`fishsim/src/test/.../AmbientResidentTest.java`,
`common/.../client/ambient/{AmbientWaterSource,AmbientFishRuntime}.java`.
Additive edits to existing files: `FlockEngine` (`placeFish`, `passesGate`, `rebuildPreserving`,
`residentFits`), `FloorField` (`fromColumnHeights`), `ClientAmbientFish` (the natural mode),
`RenderSelfTest` (the `ambientwater` scene), `WaterSource` (the collision contract's wording).

## Capture

Scene `ambientwater`, opt-in by marker file, five fixtures built in the flat self-test world and
each far enough from the rest that only its own water is inside the 24-block radius: a river whose
east end runs past the radius and whose west end is a real wall behind the player, a 49x49 ocean
three deep with a sand mound and a stone spire, a shallow pond (one cell round the rim, two in the
middle) with every bank well inside, an irregular floor with a sunken corner, shelves, a dry
island, a bottom slab, a stair and a waterlogged fence post, and two pools sixteen apart with ten
dry columns between them. Each fixture restarts the runtime, is discovered and populated for
140-150 ticks, is checked against the live runtime, and is photographed. Two extra steps watch
hazards a photograph cannot show: after the ocean's close-ups, `bandwatch` settles the camera at
the water line for 40 ticks and then hovers 18 above it for 40; after the pool photographs, `trip`
parks the camera 40 blocks away for 15 ticks and then returns for 120.

Reproduce with (one client at a time; the script writes the marker into the ignored run directory
only):

```powershell
.\gradlew.bat :fabric:runClient -I docs/fish-in-world-phase2/capture.init.gradle -PambientLoader=fabric --console=plain --no-daemon '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=4'
.\gradlew.bat :neoforge:runClient -I docs/fish-in-world-phase2/capture.init.gradle -PambientLoader=neoforge --console=plain --no-daemon '-Dorg.gradle.jvmargs=-Xmx768m -XX:ActiveProcessorCount=4'
```

The marker is `ambientwater`; delete it from the run directory after a run so it cannot hijack a
later launch.

### Results (2026-10-13)

The four preserved Fabric runs before the last one (`capture-fabric-run3/4/5/6.log`) predate one or
more of the fixes above and are kept for comparison. The final runs — `capture-fabric.log` and
`capture-neoforge.log` — report **55 checks each, 0 failures**: the original 48 plus the review
round's `bandwatch` (3) and `trip` (4) checks, all counted from the live runtime rather than from
the log text.

Per fixture, Fabric / NeoForge:

| Fixture | Result |
|---|---|
| river | 1 domain, 8 residents (8 / 7 omissions), 252 columns, one patch, open face 15.268 / 14.924, minSurface 0.658 / 0.569 |
| ocean (49x49, mound, spire) | 1 domain, 8 residents (12 / 11 omissions; NeoForge has one mid-fade at the check), 2401 columns, open face 15.528 / 15.354, minSurface 0.713 / 0.782 |
| shallow pond | 1 domain, 8 residents (17 / 16 omissions), 70 columns, closed water, minSurface 0.547 / 0.547 |
| irregular floor | 1 domain, 8 residents (23 / 22 omissions), 165 columns, closed water, minSurface 0.533 / 0.704 |
| two pools | 2 domains, 12 / 14 residents across the two patches (32 / 32 omissions), 98 columns over the two domains, closed water, minSurface 0.557 / 0.526 |

Whole-run, Fabric / NeoForge:

| Check | Result |
|---|---|
| `carry` (40 ticks, 2 rebuilds) | survived 8/8 / 7/8, keyChanged 0, originMoved 0, jumped>4 0, worst 1.00 / 0.22 blocks |
| `bandwatch` (40 ticks at the water line, then 40 with the camera 18 above it) | snapshot tracked 8, 2401 columns, deferred 0; kept 2377/2401 columns with 8/8 present, nothing retiring, domainsLost/retireDomainLost/retireInvalid unchanged; deferred +599 over the hover |
| `trip` (one pool evicted, then rediscovered) | snapshot tracked 4 / 8 over 49 columns under key 35184455974914 at origin 43,-63,91; evicted patchColumns 0, domainsLost +2, retireDomainLost +12 / +16 (the other pool's residents fade too); fading all present and all retiring; revived columns 49/49, present 4/4 / 7/8, retiring 0, keyChanged 0, originMoved 0, revivals +1, outsideWater +0, invalidCover +0 / +1 |
| `containment` | outsideWater 0, bodyFitsFailures 0, nonFinite 0, bodyTouches 23 / 28, steps 1890 / 2061 |
| `budget` | peakCells 509, peakColumns 38, peakBuild 0.97 / 0.72 ms, meanBuild 0.13 ms, rebuilds 271 / 277 |
| `boundary` | minOpenFace 12.697 / 12.559 against margin 0.60, retirements 37 / 45 (openFace 0, invalid 2 / 4, domainLost 39 / 49) |
| `accounted` | spawns 77 / 85, omissions 38 / 43, retirements 37 / 45 with reasons 0+2+39 in 6 domains / 0+4+49 in 8 domains, minSurface 0.481 / 0.479, minBed 0.000, and for the last fixture columns 6386 / 6380, evicted 98 / 101, merges 3 / 2 |
| `cleanup` | armed false, domains 0, on both |

Reading the numbers:

- **Every retirement has a reason.** `retirements + retiring` never exceeds the sum of the three
  reason counters; the shortfall is fades still in flight when their domain died, which is why the
  assertion is one-sided. `domainLost` (39 residents over 6 domains on Fabric, 49 over 8 on
  NeoForge) is observed at the fixture transitions and the trip's eviction: the scene moves the
  camera to the next fixture one tick before the runtime is cleared, so a patch that leaves the
  24-block window retires its residents with `REASON_DOMAIN_LOST`, and the trip parks the camera 40
  blocks away for 15 ticks so the pool's patches die there on purpose. The other way a domain can
  be lost — a merge absorbing a piece the sweep keyed separately — does not occur in the fixtures,
  because a stationary player discovers each fixture's water contiguously; that path is covered by
  `WaterDiscoveryTest`'s walled river and two-pool cases instead. `invalid` retirements are 2 and 4
  — a rebuild finding a body that no longer fits, the trip's `invalidCover` among them on NeoForge.
- The closed-water `boundary` checks count only residents retiring for reasons other than
  `REASON_INVALID` (`leaving`): a designed INVALID retirement — a body the rebuilt water cannot
  host — can be mid-fade at a one-tick check window, while any other reason (an open face painted
  where there is none, or a dying domain) would mean the fixture's water is breaking up under its
  residents. `leaving` is 0 on all three closed fixtures on both loaders.
- The `columns`/`evicted`/`merges`/`truncated`/`unloaded` figures in `accounted` come from the
  discovery counters, which the runtime resets on `clear()`, so they describe the last fixture; the
  line says so. Everything before them is cumulative.
- **`bandwatch`** pins the hover case the review's B1 fix addresses: with the camera floating 18
  above the water, the band cannot see a single ocean column, so the sweep re-caches nothing and
  revalidation can only defer. The patch must not lose columns, residents or domains from that —
  and the deferral itself is the evidence the columns were kept rather than silently dropped
  (+599 over 40 ticks). The ocean's four corner runs (24 columns) sit beyond the eviction circle,
  so the count breathes between 2377 and 2401 as the sweep re-caches them; the check allows that
  band, since the hazard it watches for would collapse the whole patch, not nibble its horizon.
  The check first settles 40 ticks at the water line: after the close-up flight a third of the
  ocean's cache was gone (2196/2401) and, from above the water, the band can never put it back, so
  deferral starves at ~107 instead of the ~600 the settled cache produces.
- **`trip`** takes one pool out of the window and back: the camera parks 40 blocks away long
  enough for eviction to drop the water (`patchColumns` 0) and the residents to start fading, then
  returns. The same key must come back with the same frame, the water complete (49/49 columns) and
  the residents alive — which is the whole point of key reservation and of the revive-refill guard
  below. `retireDomainLost` counts both pools' residents (the other pool fades out and is not
  rediscovered in the window), and the ledger clause allows a missing tracked resident only if an
  INVALID retirement since the snapshot explains it (NeoForge's one is that); `outsideWater` must
  not move — that is the signature of the revive-refill hazard.
- The scene's close-ups wait (up to 400 ticks, polled every 20) for a live individual of each
  species: the pool is dealt round-robin, but an omitted spawn advances the rotation, so a species
  can be absent for a while in water that can host it. They still fail if a species never
  establishes itself. This is scene scheduling, not runtime behaviour. The close-ups run before
  `bandwatch` so the ocean sees the ordinary spawn rotation first — bandwatch holds the camera
  still at the water line, which is enough to shift which species is in the pool when the poll
  starts (a bandwatch-first run filled the ocean 8/8 without a plaice and the close-up timed out).
- The close-ups frame their species from 1.25 blocks in world water; they show the species, its
  size class and its pose in the fixture, not Phase 1's calibrated tank-comparison views.

### Phase 2 gate

| Plan gate | Evidence | Result |
|---|---|---|
| Stable fixed-pool fish in river, ocean, shallow pond, irregular floor and disconnected-pool fixtures | `.fish` per fixture: 8 residents in one patch each, 12 / 14 over two patches in the two pools, and all four pool species listed live at every `.start` on both loaders | PASS |
| No dry-land crossing | `.contained`: outsideWater 0 and bodyFitsFailures 0 in all ten fixture checks, and whole-run `containment` outsideWater 0 over 1890 / 2061 steps | PASS |
| No surface clipping | `.surface`: minSurfaceClearance 0.533-0.713 (Fabric) and 0.526-0.782 (NeoForge) against the 0.30 requirement | PASS |
| No recenter resets | `.carry`: ids, patch keys and domain origins unchanged across two whole rebuilds; nothing jumps (worst displacement 1.00 / 0.22 blocks in 40 ticks, jumped>4 0). `.trip` additionally takes a whole pool through eviction and revival with keyChanged 0 and originMoved 0 | PASS |
| No field-build spikes beyond budget | `.budget`: peakBuild 0.97 / 0.72 ms, mean 0.13 ms over 271-277 rebuilds; `WaterDomainTest` builds 2304 columns in 2.08 ms (< 50 ms) | PASS |
| Rebuilds preserve valid individuals and safely retire invalid ones | `.carry.survived` 8/8 and 7/8 (one legitimate departure during the window); `.accounted` counts invalid retirements (2 / 4) and accounts for every completed fade; `.trip.revived` carries residents across a genuine domain death and revival (outsideWater +0; a missing resident must be explained by an INVALID retirement, NeoForge's `invalidCover` +1) | PASS |

Beyond the plan's rows, the review round's own checks pass on both loaders: `bandwatch` (a hovering
player's blind band keeps the ocean's columns, residents and domains) and `trip` (an evicted pool
returns under its own key and frame). Between them they reproduce the revive-refill hazard whose
fix is described under limitations.

Both loaders' evidence is complete; what is not done is a **human** look at the 28 curated
screenshots, which is how the Phase 1 gate was closed. Until then this is the implementing agent's
verification.

Curated screenshots: `screenshots/selftest-<loader>-ambientwater-<shot>.png`, fourteen per loader —
`river_bank`, `river_submerged`, `ocean_floor`, `ocean_from_above`, `pond_shore`, `pond_top`,
`floor_close`, `floor_plan`, `pools_gap`, `pools_inside` and `close_<species>` for bluegill, acute
iaspis, portuguese man o' war and plaice. The pond, irregular and pool fixtures are the ones that had
no fish at all before the discovery fixes, so `pond_top`, `pools_gap` and `pools_inside` are the
screenshots that carry that change. The per-fixture `NOTE` diagnostics in `RenderSelfTest` are
development-only, exist identically on both loaders, and are what tied the discovery cursor and the
retirement units to their causes; they stay.

## Limitations and known edges

- **Revive-refill (fixed in the review round).** A revived domain used to be rebuilt the same tick
  from the part-refilled discovery cache: residents standing in water the sweep had not yet
  rediscovered read as outside the rebuilt domain — the run-wide containment check saw
  `outsideWater` grow and the rebuild retired residents `REASON_INVALID`. `revive` now defers the
  rebuild until the patch's cache holds at least as many columns as the domain was last built
  from, or `REFILL_TIMEOUT_TICKS` 200 elapse. The trip check reproduces the whole hazard: with the
  guard disabled, the proof run failed exactly as described (`trip.revived` with
  `columns=49 present=6/8`, and `containment` `outsideWater=2`).
- **Quantum-straddle key reuse.** A reserved key only comes back for a column in the same 16-block
  quantum as the patch's seed (`WaterDiscovery.createPatch`), so water rediscovered through a
  different quantum returns under a fresh key and frame: the old domain dies with its residents and
  the new key repopulates from scratch. It is sound, but it means pool geometry decides whether an
  eviction-and-return keeps its fish. The harness pools are snapped to quantum centres so the
  trip's key is the pool's own by construction; the pond straddles a boundary and would have made
  this a coin toss.
- **Revived residents can still be retired INVALID.** A revival brings back the fading residents,
  but a body the rebuilt water cannot host is retired `REASON_INVALID` — the designed answer for an
  unhostable carried body, not a freeze. The trip check allows a missing resident only when an
  INVALID retirement since the snapshot explains it (`invalidCover`, +1 on NeoForge); `outsideWater`
  staying at +0 is the part that must hold.
- **Ocean corner breathing.** The 49x49 ocean's four corner runs (24 columns) lie beyond the
  eviction circle (dx^2+dz^2 > 32^2), so the column count breathes between 2377 and 2401 while the
  sweep re-caches them. The bandwatch check allows `base - 24`; a band that cannot see the water
  deleting live columns would collapse the patch instead, which is what it fails on.
  **Superseded 2026-10-13:** the Phase 4 fix round made eviction use the same square the sweep
  discovers in, so the corner runs stay cached and the breathing stops — see
  [fish-in-world-phase4.md](fish-in-world-phase4.md) (fix item 8).
- **Bandwatch needs a settled cache.** The check only means something once the ocean's whole cache
  exists at the water line: after the close-up flight it was holed (2196/2401) and, from above the
  water, the band can never re-cache those columns, so deferral measured 107 instead of ~600. The
  check settles 40 ticks at the water line first, then climbs.
- **Revived fish pop back.** The fade is cancelled wholesale at revival (`m.fade = 1f`), so a
  resident caught mid-fade returns at full opacity rather than fading in. Accepted for the
  development scene; a production activation would want a fade-in.
- **S2 (disproven).** The review's S2 item — `placeFish` moves `pos*` without moving the binary
  model's per-fish `homeDepth`, so a placed fish could drift back to the scatter depth plane — was
  measured in the planar ambient model and disproven: ambient domains run with no swim depth plane
  and never read `homeDepth` (over 600 steps a placed fish wandered at most 0.49 blocks around its
  target while a scatter-only control stayed on its plane). No fix applied; the placement contract
  is pinned by `AmbientResidentTest.aPlacedFishKeepsTheDepthItWasPlacedAt`.
- **N5/N6/N7 (theoretical, not fixed).** Three out-of-range hazards the review flagged as not
  worth fixing: 7+ alternating runs; a teleport beyond ~46 km; 16.7M patch creations. They need
  teleport-scale travel or degenerate inputs to reach, are not reachable from ambient play, and per
  the review are documented here rather than fixed.
- The runtime has no headless unit tests of its own: its verification is the in-game capture
  above. The close-up species polling stays phase-sensitive in principle (scheduling, not runtime).

## Gate closure (2026-10-13)

An independent read-only review of the initial Phase 2 code raised one acceptance-blocking defect
(B1: revalidation re-scanned cached columns with the player's *current* vertical band, so water
outside the band was re-classified dry and deleted — flying over a pond retired its residents) and
four should-fixes (S1 frame jumps on patch-key reuse plus a one-way `dying` state; S3 in-place
STATIC demotion on rebuild; S4 NaN floor re-placement; plus the N1-N4/N8/N9 robustness nits). Each
was reproduced with a failing test first, fixed, and re-verified; designing the trip harness also
surfaced and fixed a revive-refill race (a revived domain was rebuilt same-tick from a part-refilled
cache, breaking containment). S2 was disproven by measurement and is pinned as a guard test;
N5/N6/N7 are documented as theoretical. Post-round counts: fishsim 437 / 401 passed / 36 skipped,
common 779 / 778 / 1; the capture re-ran to 55 PASS / 0 FAIL on each loader.

Visual review: the coordinator inspected the refreshed 28-image sets on both loaders — all five
fixtures and all four species close-ups, plus the pre-round set — and found fish contained and
readable with no surface clipping or stranded individuals. With the machine-checked gate table
above, the Phase 2 gate is closed. Scope: fixture domains only — arbitrary-water behaviour beyond
the discovery window, long teleports and release performance stay open for later phases.
