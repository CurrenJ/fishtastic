# Solid Cosmetics — Handoff

**Written:** 2026-10-02, at the end of the shelters work (accepted in game through `7d0a75f5`).

> **Status (2026-10-02, later):** built, uncommitted, awaiting the owner's in-game look. What was
> built, the measurements, and the three places it departs from the decisions below (no box cap,
> pockets filled, no wedge guards) are in [`fish-shelters.md`](fish-shelters.md) §12.3.1.

Before that status note, nothing in this document was built. Read [`fish-shelters.md`](fish-shelters.md) §12.3 first. It
holds the design ("Obstacles: swimmers stop passing through cosmetics"), which is step 2 of §12.7.
Also read §12.8 and the parts it builds on (§4 hull avoidance, §7 lone-tank promotion). This
document is the cold-start context: what's decided, what will bite you, and how to verify.

Companions that remain binding: [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md) (module
layout, verification model), [`fish-tank-group-scaling.md`](fish-tank-group-scaling.md) (why
`DistanceField` is off limits), [`fish-sim-locomotion.md`](fish-sim-locomotion.md) §4.1 (the
deferred sub-block field this replaces).

## Goal

Free swimmers avoid the solid parts of every placed cosmetic structure, the way they already avoid
a shelter's hull. Today only shelter hulls are solid. Fish swim straight through the whale's ribs,
castle walls, arches and lanterns. This also unlocks the next step: gates on the fence arches,
Torii Gate and Castle Ruin, which is meaningless while pillars aren't solid.

## Decisions already made (don't relitigate)

- **Don't do it through `DistanceField`.** It's shared per group and keyed by the membership epoch,
  and its build cost is the group-scaling work's main constraint. Cosmetics change far more often.
- **Generalise the shelter hull instead.** The engine already softly avoids oriented boxes
  (`FlockEngine`: `AvoidHull`, `refreshAvoidHulls`, `HULL_MARGIN = 0.10`, `insideAnyHull`,
  `hullTopInTheWay`). Obstacles are more of the same.
- **Derive at load, per structure.** Rasterise each part's block shape at ¼ build cell, so a fence
  post is a post, not a cube. Greedy-merge into at most ~12 boxes, and cache. Store them unrotated;
  they turn with the structure at placement, like shelters.
- **Solid vs soft by block tag** (`fishtastic:soft_cosmetic`). The first pass of soft blocks:
  leaves, kelp, seagrass, coral fans, vines, petals. Fish may brush through soft parts.
- **Rebuild on the cosmetic fingerprint**, in the same pass as the floor and shelters
  (`TankFloors.fingerprint` / `groupFingerprint`; `TankFlockAdapter` and `TankGroupFlock`, where
  they call `rebuildFloor` and `rebuildShelters`). Never on the membership epoch.
- **Bin the boxes per block cell** of the domain, so a fish queries only its own cell and its
  neighbours. Cost per fish must stay flat in a 512-tank group.
- **A shelter's hull becomes one of its structure's obstacle boxes.** The corridor exception stays:
  a fish using a shelter ignores that structure's boxes.
- **No pathfinding.** There are two guards against wedging in concave pockets. Resample a wander
  target that lies inside an obstacle box or is blocked by one, and retarget a fish whose progress
  stalls for about 2 s.
- **The size gate keeps ignoring obstacles at first.**
- **Lone tanks: promote** (decided 2026-10-02, §12.8 Q6). A lone tank holding a solid cosmetic and
  a swimmer runs the planar engine, as §7 already does for shelters. Today promotion is
  `TankFlockAdapter.wantsShelters(...)` → `rebuildPromoted(...)`, and the renderer's promoted path
  is in `FishTankBlockEntityRenderer` (search "promoted to the planar model"). Promotion ships only
  after an in-game look at a crowded, heavily decorated 1×1×1 tank, which already mills as a torus.

## Hard constraints

- **The goldens stay bitwise.** The binary single-tank model (`Tunables.DEFAULT`, `stepFish`) is
  locked by `GoldenTrajectoryTest` and `ParityTest`, and `VoxelGoldenTrajectoryTest` locks
  shelter-less voxel domains. Gate all new code so a domain with no obstacles runs exactly today's
  instructions. That is how shelters stayed golden-safe (`shelterSim = planar &&
  !shelters.isEmpty()`). Obstacles need their own gate, since most decorated domains have no shelter.
- **A new invariant:** a swimmer not using a shelter is never inside a solid obstacle box, and
  `backstopEngagements() == 0` still holds. Measure it over a headless matrix that places **every
  shipped cosmetic** (`common/src/main/resources/data/fishtastic/fishtastic/cosmetic_structure/*.json`),
  not synthetic boxes. Thin posts, overhangs and spans live in the shipped set. Speed, acceleration
  and jerk bounds are as in `ShelterVisitTest` / `ShelterStartleTest` (jerk ≤ 12).
- **Prove root causes with a probe before fixing.** Every bug in the shelter work was found by
  measuring (jerk traces, distance histograms, per-fish displacement), not by reading code. A
  throwaway JUnit probe in `fishsim/src/test` that prints, run with `-i` and grepped, was the
  workhorse. Delete it before committing.

## Where things are

- **Engine:** `fishsim/.../core/FlockEngine.java`. Domain: `fishsim/.../domain/VoxelDomain.java`
  (`rebuildShelters`, `shelters()`, `rebuildFloor`), `Shelter.OrientedBox` (`signedDistance`,
  `contains`, `toBox`), `FlockDomain`.
- **Minecraft side:**
  - Structures: `CosmeticStructure`. `partCells()` uses the horizontal ratio `scale / CELL_WIDTH`;
    `offsetY` is in plain block units. Unit tests can't load `CosmeticStructure` without Minecraft's
    bootstrap (its codecs touch `BlockState`), so keep geometry testable without it, as
    `ShelterGeometry` and `SpanStructures.buildOrigin(Span, scale, Rotation)` do.
  - Placement: `TankShelters` (`inBlockFrame`, `toEngine`, `single`, `group`) is the frame mapping
    obstacles should reuse. Spans use `SpanStructures.layout` / `buildOrigin`; both use
    `CosmeticStructures.rotateOffset`.
  - Block shapes need care: the IRLights work found a collision-shape fallback faking a full cube.
- **Harness:** `Scenarios` (domains such as `3x1x1+log`, `+pipe`, `+logpair`, `4x2x2+skull`; casts
  `loaches|visitors|skittish|lurker`), `HeadlessRunner`
  (`:fishsim:runHeadless -PsimArgs="--domain … --cast … --fish … --watcher pace|hover --film-view top|side"`,
  which writes `*-visit.png` / `*-startle.png` contact sheets), and `Metrics`.
- **Patterns to copy:** `ShelterHullTest` (the "never inside a hull it isn't using" invariant),
  `ShelterVisitTest` (the matrix and its `Watch`), `TankSheltersTest` (frame mapping checked
  against independent routes, never by restating the formula).

## Suggested order

1. **Baseline.** Read §12.3, then measure today's clipping: a headless matrix of every shipped
   cosmetic in a group domain, counting fish-ticks inside solid parts. That's the baseline and the
   probe.
2. **Geometry, Minecraft side.** Parts → shape rasterisation → merged boxes, unit tested on the
   shipped files (box count, coverage, soft parts excluded).
3. **Engine.** An obstacle list on the domain, binning, soft avoidance reusing the hull ramp, the
   corridor exception, the two wedge guards, and the gate that keeps the goldens bitwise.
4. **Wiring.** Fingerprint rebuild for groups and lone tanks, then promotion of decorated lone
   tanks.
5. **Acceptance.** The invariant matrix green, contact sheets of fish skirting the whale's ribs and
   an arch's pillars, then the owner's in-game look before promotion ships.

## Working notes

- **Commits take minutes.** The pre-commit hook runs the Fabric and NeoForge game tests; run
  commits in the background. 1Password signs them, so the owner approves each one.
- **"Game tests failed" may not be a test failure.** Read the hook's log. A running dev client
  locks the common jar ("used by another process"), and afterwards the Gradle daemon can throw
  `ClosedFileSystemException` on `:common:jar` until `./gradlew --stop`. Ask the owner to close
  the game; never kill it.
- **Commit only when asked**, and split commits when the owner asks. Verify each split commit's
  state alone with `git stash push --keep-index`.
