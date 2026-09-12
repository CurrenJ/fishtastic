# Fish Tank Group Size — Scaling Analysis

**Written:** 2026-09-10. **Question:** how far can `TankGroups.RENDER_MAX_GROUP_SIZE` (currently 64)
safely be raised, and what has to change first?

> **Status: Stages 0-2 shipped, cap now 512; accepted in game 2026-09-10.** See
> [Section 9](#9-what-shipped) for what landed, what the numbers came out at, and the two items
> deliberately left open. The analysis below is preserved as written; Section 9 is the only part
> added after implementation.

Companion to [`fish-swarm-realism.md`](fish-swarm-realism.md) (the swarm model itself). The bug that
prompted this — fish confined to a corner blob in large structures — is diagnosed in §1; this
document is about the cost structure that the cap exists to contain.

---

## 0. Summary

**The cap is not protecting what it looks like it protects.** It bounds *tanks*, but every cost that
matters scales with *fish*, and a tank holds up to 27 of them. A fully-stocked 64-tank group is
1728 fish, which costs **33 ms per tick** — already 66% of a 20 Hz tick budget, at today's cap.

Three costs were measured. In order of severity:

| # | Cost | Scaling | Today at the cap | Verdict |
|---|---|---|---|---|
| 1 | `FlockEngine.step()` | **O(n²)** in fish | 33 ms/tick @ 1728 fish | The wall |
| 2 | `TankGroups.of()` per block entity per frame | **O(G²)** per frame | 4.0 ms/frame @ 64 tanks | Already over budget |
| 3 | `VoxelDomain` construction | superlinear in bounding-box volume, **rebuilt on every content change** | 1.2 ms @ 64 tanks; 109 ms @ 1728 | Latent |

Negligible: scatter placement (1.5 ms at 1728 fish) and `interpolate()` (0.017 ms/frame at 1728).

**Not measured, and probably the real ceiling: GPU cost.** Each fish is one `ItemStackRenderState`
submitted individually. No headless test can tell us what 2000 item models per frame costs. This
must be measured in game before any cap is raised.

The recommended path is three staged fixes (§5) that convert #1 from O(n²) to O(n) and #2 from
O(G²) to O(G), after which the defensible limit becomes a **fish budget** rather than a tank count.

---

## 1. What the cap does today

A 9×9×9 structure is 729 tanks; the flood fill stops at 64.

Three behaviours compound. `getOpenFaces()` returns an `EnumSet<Direction>`, iterating in ordinal
order `DOWN, UP, NORTH(−Z), SOUTH(+Z), WEST(−X), EAST(+X)`, so when the cap truncates the BFS the
surviving cells skew down/north/west. `anchor = members.getFirst()` after a sort, and
`Vec3i.compareTo` orders Y → Z → X, so the anchor is the minimum corner. And `TankFlockAdapter:280`
gives non-anchor members `groupEngine = null`, so they render only their own *non-swimming* fish.

Replicating the flood fill over solid cubes, **exactly one** tank in the whole structure elects
itself anchor — always `(0,0,0)`:

| structure | tanks | simulated | swimmable volume |
|---|---|---|---|
| 3×3×3 | 27 | 27 | 100% |
| 4×4×4 | 64 | 64 | 100% |
| 5×5×5 | 125 | 64 | **51.2%** |
| 7×7×7 | 343 | 67 | 19.5% |
| 9×9×9 | 729 | 68 | 9.3% |

In a 9-cube the union of simulatable cells has centroid (1.17, 1.63, 1.23) against a true centre of
4.0 — 60 cells on the low X side against 1 on the high side, likewise Z, and 54 against 4 on Y.
Swim-capable fish in the other 665 tanks render nowhere at all.

Two incidental defects found on the way: the group **overshoots its own cap** (64 → 68, because
`visited.size() < cap` is tested before a node is polled, and that node still expands all six
faces), and `VoxelDomain.avoidance` ignores its `marginVertical` parameter, using `margin` on all
three axes where `FlockDomain.Box` honours it.

---

## 2. Method

All numbers are single-threaded wall-clock on the development machine, via throwaway JUnit probes
run under `-PsimStdout` (the workflow in the Tier 2 handoff's §5). Probes were deleted afterward;
§7 says how to rebuild them.

**Absolute milliseconds are machine-specific; the scaling exponents and ratios are not.** Treat the
former as order-of-magnitude and the latter as the finding.

Budgets used throughout: a client tick is 50 ms at 20 Hz and a frame is 16.6 ms at 60 fps, but the
swarm is one feature among many. The working budget below is **≤2 ms/tick** for simulation and
**≤1 ms/frame** for anything per-frame.

---

## 3. Measured costs

### 3.1 `FlockEngine.step()` — O(n²) in fish count

Measured in a 6×6×6 domain:

| fish | ms/tick | µs/fish | % of a 50 ms tick |
|---|---|---|---|
| 50 | 0.020 | 0.41 | 0.0 |
| 100 | 0.100 | 1.00 | 0.2 |
| 200 | 0.444 | 2.22 | 0.9 |
| 400 | 1.842 | 4.61 | 3.7 |
| 800 | 7.484 | 9.36 | 15.0 |
| 1200 | 16.986 | 14.16 | 34.0 |
| 1728 | 34.873 | 20.18 | 69.7 |
| 3000 | 100.871 | 33.6 | 201.7 |

Doubling the fish quadruples the cost (400 → 800 is 1.84 → 7.48 ms) and µs/fish doubles. Textbook
O(n²), from the two brute-force passes in `stepFishPlanar`: the separation loop over all `count`
fish (now carrying the anticipatory extension too) and `findNearestSwimmers`.

**At a 2 ms budget, today's engine supports about 450 fish** — under a fifth of what the current
64-tank cap permits.

### 3.2 How much of that O(n²) is useful work

Both hot loops are radius-limited: separation uses 0.24 within a species and 0.60 across, and the
neighbour search uses `neighborRange` 0.9. Counting, over settled sims, the fraction of ordered
pairs actually inside those radii:

| config | fish | fish/block | frac <0.60 | frac <0.90 | pairs/fish <0.90 | speed-up ceiling |
|---|---|---|---|---|---|---|
| cube4 n=256 | 256 | 4.0 | 0.0298 | 0.0806 | 20.5 | 12.4× |
| cube6 n=216 | 216 | 1.0 | 0.0128 | 0.0254 | 5.5 | 39.3× |
| cube6 n=864 | 864 | 4.0 | 0.0089 | 0.0242 | 20.9 | 41.3× |
| cube8 n=512 | 512 | 1.0 | 0.0056 | 0.0116 | 5.9 | 86.1× |
| cube8 n=2048 | 2048 | 4.0 | 0.0038 | 0.0098 | 20.0 | 102.2× |
| cube10 n=1000 | 1000 | 1.0 | 0.0030 | 0.0061 | 6.1 | 163.4× |

**This is the key result.** `pairs/fish` is essentially constant — ~20 at 4 fish/block, ~6 at 1
fish/block — *regardless of n or domain size*. The number of interactions a fish actually has is set
by local density, not by how many fish exist. The O(n²) is therefore almost entirely wasted work,
and a spatial index makes the step **O(n) with a density-dependent constant**.

### 3.3 `TankGroups.of()` — O(G²) per frame

`ClientTankFlocks.getOrCreate` runs in `extract`, i.e. **once per visible tank per frame**, and
calls `TankGroups.of` — an allocating BFS (`HashSet`, `ArrayDeque`, `ArrayList`, plus a sort) over
up to the cap. So the per-frame cost is O(G) per tank × G tanks.

| cap | visited | µs/call | ms/frame with that many tanks in view |
|---|---|---|---|
| 64 | 68 | 59.3 | **4.0** |
| 256 | 256 | 60.9 | 15.6 |
| 1024 | 1024 | 272.3 | 278.9 |
| 4096 | 4096 | 592.2 | 2425.5 |

**At today's cap this already costs 4 ms/frame** — a quarter of a 60 fps budget spent rediscovering
a group that has not changed. It is a hard blocker on any raise: at 256 it exceeds a whole frame's
budget, and at 1024 it is unusable.

### 3.4 `VoxelDomain` / `DistanceField` construction

Rebuilt from scratch whenever `rebuildGroupMode` runs. Samples per axis are `blocks × 4 + 1`.

| cube | tanks | samples | build ms | field MB |
|---|---|---|---|---|
| 4 | 64 | 4 913 | 1.2 | 0.07 |
| 6 | 216 | 15 625 | 4.6 | 0.24 |
| 8 | 512 | 35 937 | 14.6 | 0.55 |
| 9 | 729 | 50 653 | 26.0 | 0.77 |
| 10 | 1000 | 68 921 | 43.7 | 1.05 |
| 12 | 1728 | 117 649 | 108.9 | 1.80 |

Cost per sample rises with size (0.00025 → 0.00093 ms), because `nearestFaceDistance` searches
outward in cube shells until it can prove no nearer face exists — for a sample deep inside a solid
cube that is O(s³) cells visited, giving roughly O(s⁶) overall. Shape matters as much as tank count:
64 tanks as a line or slab build in 0.6–0.7 ms, while a hollow 10-shell (488 tanks, same bounding
box as a 1000-tank cube) takes 31.5 ms.

**The important part is not the absolute number but when it is paid.** `sync()` calls
`rebuildGroupMode` on *content* change as well as membership change, and `rebuildGroupMode`
unconditionally does `new VoxelDomain(group.occupancy())`. So **adding one fish to one tank rebuilds
the entire distance field** — 26 ms in a 9-cube, 109 ms in a 12-cube. The occupancy grid has not
changed; only the inventory has.

### 3.5 Costs that turned out not to matter

**Scatter placement**, despite an O(n²) `isFarEnough`: 0.03 ms at 100 fish, 1.5 ms at 1728. The
40-attempt cap bounds it.

**`interpolate()`**, per frame: 0.0015 ms at 200 fish, 0.017 ms at 1728, 0.036 ms at 3000 — linear,
and the insertion sort's near-sorted assumption holds up.

> A first version of this measurement reported `interpolate` at 11 ms/frame at 1728 fish and looked
> like a third wall. That benchmark stepped the sim inside the timed region, so it was measuring
> `step` and attributing it to `interpolate`. The corrected figure is 640× smaller. Worth recording
> because the bad number would have driven real work into the wrong place.

### 3.6 What could not be measured here

Every fish gets its own `ItemStackRenderState` and its own `submit`. `fishsim` has no Minecraft on
its classpath by construction, so the GPU and render-thread cost of N fish models is invisible to
this analysis. Given that vanilla item models run to hundreds of quads each, **this is the most
likely true ceiling**, and it is the one number that would change the recommendation most. It has to
be measured in game (§6).

---

## 4. Reframing: the cap bounds the wrong quantity

`RENDER_MAX_GROUP_SIZE` limits tanks. Every measured cost scales with fish. `CONTAINER_SIZE` is 27,
and — unlike the single-tank path, which honours `swarm.count()` — **the group path applies no
per-fish limit at all**: `rebuildGroupMode` walks every member × every slot and adds every
swim-capable stack.

So the guarantee the cap actually provides is "at most 64 × 27 = 1728 fish", i.e. 33 ms/tick, i.e.
already ~17× over a 2 ms budget. The current value is safe in practice only because nobody fills
1728 slots.

Combined cost by structure size, at a plausible 4 fish per tank:

| cube | tanks | fish | build ms (one-off) | step ms/tick | % of tick |
|---|---|---|---|---|---|
| 4 | 64 | 256 | 1.9 | 1.08 | 2.2 |
| 5 | 125 | 500 | 3.5 | 2.73 | 5.5 |
| 6 | 216 | 864 | 4.4 | 8.05 | 16.1 |
| 7 | 343 | 1372 | 8.0 | 21.79 | 43.6 |
| 8 | 512 | 2048 | 16.2 | 48.46 | 96.9 |
| 9 | 729 | 2916 | 27.1 | 97.43 | 194.9 |

**Any raised cap must be paired with a fish budget**, or the tank number is not a bound on anything.

---

## 5. Solutions

### 5.1 Cache group discovery — prerequisite, no cap change needed

Fixes §3.3. Group membership changes only when a tank is placed, broken, or its open faces change —
never per frame. Compute each structure's group once, key it by a membership epoch, and share one
result among all members instead of having every block entity rediscover it.

- **Removes** 4 ms/frame at today's cap, and the O(G²) term entirely.
- **Also fixes the anchor-dependence** noted in `GAMEPLAY_MAX_GROUP_SIZE`'s comment: one shared
  group per structure cannot disagree with itself about who is in it.
- **Effort:** low. **Risk:** low — invalidation is the only subtlety, and `openFaces` already
  changes through a known path.

This is worth doing on its own merits even if the cap never moves.

### 5.2 Spatial index in `FlockEngine` — the main event

Fixes §3.1. Replace both brute-force passes with a uniform grid (cell list): a counting sort of fish
into cells of `max(separationRadiusOther, neighborRange) = 0.9` blocks, then scan the 3×3×3
neighbourhood.

A prototype measured against brute force on settled positions:

| config | fish | brute ms | grid ms | wall-clock | candidates/fish | candidate reduction |
|---|---|---|---|---|---|---|
| cube4 n=256 | 256 | 0.475 | 0.206 | 2.3× | 79.9 | 3.2× |
| cube6 n=864 | 864 | 1.636 | 0.461 | 3.6× | 69.0 | 12.5× |
| cube8 n=2048 | 2048 | 3.378 | 1.248 | 2.7× | 77.3 | **26.5×** |
| cube10 n=4000 | 4000 | 11.652 | 2.750 | 4.2× | 78.9 | 50.7× |
| cube12 n=8000 | 8000 | 44.978 | 6.323 | 7.1× | 92.6 | **86.4×** |

**Read the candidate column, not the wall-clock column.** The prototype's per-pair work is three
subtractions and a compare; `stepFishPlanar`'s is a square root, several divisions, the anticipatory
time-to-closest-approach block and an insertion sort. The grid's build and indirection overhead
therefore dominates the prototype and masks the win, while in the engine the per-candidate cost is
roughly an order of magnitude higher and the realized speed-up should track candidate reduction far
more closely.

Candidates/fish sits at ~70–93 against a theoretical ~20 useful (§3.2), because a 27-cell
neighbourhood at cell = radius covers ~6.5× the volume of the query sphere. **Cell size is an open
tuning question** — a finer cell with a wider scan trades over-fetch against cell-visit overhead, and
the two loops want different radii (0.24/0.60 vs 0.9), so separate indices or a two-level scan may
beat one shared grid. This was not settled; it needs its own sweep against the real engine, not a
prototype.

- **Expected:** step becomes O(n). At a 2 ms budget, somewhere in the low thousands of fish rather
  than ~450 — but the honest form of that claim is "≈26× fewer candidate evaluations at 2000 fish",
  with the constant to be measured once implemented.
- **Effort:** moderate. **Risk:** moderate — this is bitwise-visible. Neighbour *selection* must stay
  identical (`findNearestSwimmers` keeps the k nearest, and ties must break the same way) or the
  planar golden fixture changes and the behaviour drifts. Build it against
  `VoxelGoldenTrajectoryTest` and require the fixture **not** to need regenerating: unlike a
  deliberate model change, an index is supposed to be a pure optimisation. That test is the
  specification.
- The binary single-tank model must keep its existing brute-force path for parity regardless.

### 5.3 Cache the domain, and build the field faster

Two separate fixes for §3.4.

**(a) Do not rebuild the distance field on content change.** The field depends only on occupancy.
Cache the `VoxelDomain` alongside the shared group from §5.1 and rebuild only when membership
changes. This alone removes a 26–109 ms hitch from the common case (a player adding a fish), and it
is cheap and low-risk.

**(b) Replace the shell search with a real distance transform.** `nearestFaceDistance` is
O(cells-in-radius) per sample; a multi-pass chamfer or exact Felzenszwalb-style separable EDT is
O(samples) total, turning the ~O(s⁶) build into ~O(s³). At cube 12 that is 109 ms → single-digit ms.
Higher effort, and it needs care to preserve exact face-distance semantics at concave seams (the
thing the current code gets right and an AABB-union approach gets wrong), so it should be gated on a
test asserting the new field matches the old one within tolerance across the domain matrix.

**(c) `SAMPLES_PER_BLOCK` is a large, cheap lever if needed.** It is 4; dropping to 2 cuts samples
8×, memory 8×, and build time by more than that. It would coarsen wall avoidance, so it trades
against containment — the invariant suite would tell us immediately. Worth keeping in reserve rather
than spending first.

### 5.4 A fish budget, and LOD

Given §4 and the unmeasured render cost, the cap that actually protects the frame must be on fish.
Options, roughly in order of preference:

1. **Per-group fish budget** with deterministic selection (e.g. by member sort order then slot) so
   the same fish are chosen every frame and the choice does not depend on the viewing tank.
2. **Distance/frustum LOD** — freeze or coarsen the simulation for groups that are far away or off
   screen. The sim already runs per group in `tickAll()` independent of visibility, so a far-away
   64-tank aquarium currently costs full price.
3. **Tick decimation** for large groups — step at 10 Hz and interpolate over two ticks. Cheap, but
   it interacts with the animation clock and the burst cycle, so it needs its own verification.

---

## 6. Recommended staging

Each stage is independently shippable and independently verifiable.

| Stage | Work | Cap after | Gating evidence |
|---|---|---|---|
| 0 | §5.1 shared group cache + §5.3(a) domain cache | **64** (unchanged) | 4 ms/frame → ~0; no rebuild hitch on content change |
| 1 | §5.4(1) per-group fish budget | 64, now *actually* bounded | step ≤2 ms/tick at the budget |
| 2 | §5.2 spatial index | **512** | golden fixture unchanged; step ≤2 ms at the new budget |
| 3 | §5.3(b) distance transform | **4096** | build ≤10 ms at cube 16; field matches old within tolerance |

Stage 0 is worth doing regardless of whether the cap ever moves — it fixes a cost being paid today.

Stage 1 before Stage 2 deliberately: it makes the existing cap honest, and it is the change that
makes any later raise safe by construction rather than by hoping tanks stay half-empty.

**Every raise past Stage 1 must be gated on an in-game render measurement (§3.6), not on these
headless numbers.** If item-model rendering turns out to cost ~1 ms per 100 fish, the render budget
sets the cap and Stages 2–3 buy nothing visible.

---

## 7. Reproducing this

The probes were throwaway, per the Tier 2 handoff's §5 workflow, and are deleted. To rebuild them,
each is a JUnit class in `fishsim/src/test/java/grill24/fishsim/` run with:

```bash
./gradlew :fishsim:test --tests '*YourProbe*' -PsimStdout --rerun-tasks
```

The measurements were:

- **step vs n** — `new VoxelDomain(cube(6))`, rebuild with n specs, 200 warm-up steps, then time 300
  steps. Time `step()` and `interpolate()` in *separate* accumulators (see §3.5).
- **pair density** — settle 600 ticks, then every 40 ticks count ordered pairs under 0.60 and 0.90.
- **flood fill** — replicate `TankGroups.of` with plain records: BFS in `Direction` ordinal order
  `{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}`, sort by Y→Z→X.
- **field build** — time `new VoxelDomain(g)` for solid cubes 2–12 and for line/slab/hollow shapes.
- **grid prototype** — counting-sort cell list at cell 0.9, 3×3×3 scan, compared against the brute
  force on identical settled positions; assert both find the same pair count.

If any of this is redone often, promote it to a `harness/` class with a Gradle task alongside
`runHeadless` / `runWallTurnAnalysis` rather than re-deriving it.

**A caution.** A cell-size sweep over `{0.9, 0.6, 0.45, 0.3}` × five configs up to 8000 fish was
started and abandoned: at cell 0.3 the scan is 7³ = 343 cells per fish, and the run did not finish in
minutes. Bound the parameter space before launching sweeps at these sizes.

---

## 8. Open questions

1. **What does rendering N fish actually cost?** The single most important unknown (§3.6).
2. **Optimal grid cell size**, and whether the separation loop (0.24/0.60) and the neighbour search
   (0.9) should share one index (§5.2).
3. **Does the spatial index preserve neighbour selection exactly?** If ties break differently the
   planar golden fixture moves, and an optimisation that changes behaviour is not an optimisation.
4. **What is a realistic maximum fish count** in a large build — is 4/tank right, or do players fill
   all 27 slots? This sets the budget in Stage 1.
5. **Should a very large structure be one shoal or several?** A single 4096-tank group means one
   cohesive school across the whole build; partitioning would be cheaper but caps shoal size.
6. **Server-side cost** is out of scope here — the swarm is client-only — but group discovery is
   shared with gameplay queries at `GAMEPLAY_MAX_GROUP_SIZE = 8192`, which §5.1's cache would also
   benefit.

---

## 9. What shipped

Implemented 2026-09-10, against the staging table in §6. Stages 0, 1 and 2 landed together;
**`RENDER_MAX_GROUP_SIZE` is now 512**, paired with a fish budget as §4 requires.

### 9.1 Stage 0 — the two caches (§5.1, §5.3a)

`ClientTankGroups` holds one discovered group per structure, keyed on a global membership epoch
(`TankGroups.membershipEpoch()`), and registers the result under *every* member so one flood fill
serves the whole structure instead of one per block entity per frame. The group's `VoxelDomain` is
cached in the same entry, so a content change no longer rebuilds the distance field — the
26–109 ms hitch on "player adds a fish" is gone.

The epoch is bumped only where adjacency really changes: `setFaceOpen`, `setOpenFaces`,
`updateConnections`, `setRemoved`, and `loadAdditional` *when the open-face bits actually differ*.
That last guard is the load-bearing one — every content change reaches the client through
`loadAdditional`, so bumping unconditionally there would have quietly undone §5.3(a).

Over-cap structures partition rather than overlap: a walk treats already-claimed positions as
absent, so two anchors can never simulate the same fish. Which partition you get depends on which
member renders first, and then holds for the epoch.

The §1 cap-overshoot defect is fixed in the same walk — the cap is tested before each *admission*
rather than before each poll, so a capped walk lands on exactly `maxGroupSize` instead of
overshooting by up to five.

### 9.2 Stage 1 — the fish budget (§5.4(1))

`RENDER_MAX_GROUP_FISH = 1024`, enforced as a **per-tank quota** (`RENDER_MAX_GROUP_FISH /
memberCount`, floored at 1) rather than as a running total.

The quota form is not a detail. §5.4(1) suggested "deterministic selection by member sort order
then slot", but a running total has to be evaluated identically by two passes that run in
different block entities with no shared state — the anchor decides who swims, each member decides
who hovers. A pure function of the member count cannot disagree with itself; a running total
depends on where in the walk a tank sat, and any mismatch renders a fish twice or not at all. A
quota also spreads the shoal across the structure instead of filling the first tanks in sort order
and starving the far corner, which is the §1 artifact in a new costume.

Answering §8.4 as posed: this binds only past ~38 tanks (`1024 / CONTAINER_SIZE`). Every smaller
build behaves exactly as it did before the budget existed.

### 9.3 Stage 2 — the spatial index (§5.2)

`NeighborGrid`: counting-sort cell list at cell = the interaction radius, scanned over the cell
range covering `position ± reach` (2–3 cells per axis, ~15.6 cells rather than a fixed 27).

Two things §5.2 did not anticipate:

- **The cell size is not `neighborRange`.** The Tier 2 anticipatory separation term is not
  distance-guarded — it fires on the predicted offset at closest approach — so it reaches
  `separationRadiusOther + 2·maxSpeed·(1+traitJitter)·separationLookahead` ≈ 1.05, above the 0.9
  neighbour range. The radius is derived from the tunables rather than tuned.
- **Candidates are emitted in ascending fish index, via a bitset.** §5.2 called for the golden
  fixture not to move, and that requires bit-exact float summation order, not merely the same
  neighbour set. Sorting per query would cost more than the pruning saves; walking a bitset
  restores global ascending order in O(candidates + n/64).

The index also indexes *start-of-step* positions while the loop reads live ones (fish before `i`
have already integrated), so every query reaches one tick of travel further than the radius.

**Answering §8.3: yes, exactly.** Both golden fixtures are unchanged, and
`SpatialIndexEquivalenceTest` compares raw float bits against the brute-force path over 600 ticks
across six domain shapes up to 864 fish. Verified to have teeth: shrinking the radius to 0.75×
`neighborRange` fails every case on tick 1.

§8.2 is answered only in part — one shared index serves both loops, sized by the larger radius. A
finer cell with a wider scan was not swept; §7's caution about bounding the parameter space first
still stands.

### 9.4 Measured result

`StepScalingProbe` (`./gradlew :fishsim:test --tests '*StepScalingProbe*' -PsimStdout`), same
machine as §3.1 — brute force reproduces this document's numbers (35.2 ms at 1728 vs 34.9), so the
two columns are comparable:

| domain | fish | brute ms | indexed ms | speed-up | µs/fish |
|---|---|---|---|---|---|
| cube6 | 864 | 8.77 | 2.01 | 4.4× | 2.33 |
| cube8 | 1728 | 35.00 | 4.18 | 8.4× | 2.42 |
| cube8 | 2048 | 50.08 | 5.66 | 8.8× | 2.76 |
| cube10 | 3000 | 109.23 | 7.03 | 15.5× | 2.34 |
| cube12 | 4000 | 192.59 | 8.47 | **22.7×** | 2.12 |

µs/fish is now roughly flat instead of doubling with every doubling of `n` — the step is O(n), as
§3.2 predicted it could be. At the 1024 budget that is **1.7–2.4 ms/tick**, i.e. the §2 working
budget, against ~21 ms for the same fish count before.

**One case where the index does not help, recorded rather than designed around.** Per-fish cost is
set by density, and the index cannot discard neighbours that genuinely are neighbours:

| domain | fish | fish/block | brute ms | indexed ms | speed-up | µs/fish |
|---|---|---|---|---|---|---|
| cube3 | 729 | 27 | 7.16 | 5.77 | 1.2× | 7.92 |
| cube4 | 1024 | 16 | 13.26 | 6.54 | 2.0× | 6.39 |

A small build with all 27 slots of every tank filled costs ~6.5 ms/tick at the budget, ~3× over.
That is buildable, just extreme. The honest fixes are a density-aware budget or the LOD in
§5.4(2); neither was attempted here.

### 9.5 Deliberately not done

**§5.3(b), the distance transform, and the Stage 3 cap of 4096.** Its only payoff is a cap raise
that §6 gates on an in-game render measurement, and §5.3(a) already removed the hitch players
actually feel. Left for whoever takes §8.1.

**The `marginVertical` defect from §1 — attempted, reverted, and this is the interesting one.**
`VoxelDomain.avoidance` ignoring its `marginVertical` parameter is not the one-line fix it looks
like. Scaling the ramp by the gradient's verticality (a vertical face keeps `margin`, a floor or
ceiling gets `marginVertical`, corners blend continuously) is the right shape and does work — but
at 0.05 the vertical zone is far too tight for GROUP's speeds, which are nearly double the
single-tank set's. **Measured: the hard backstop engaged 6 times in the L domain**, which the
invariant suite treats as a containment failure rather than a tuning nit.

The root cause is that `Tunables.GROUP` simply inherited `DEFAULT.wallMarginVertical()` and has
never had a value of its own, *because nothing has ever read it*. So the fix is a tuning
workstream — give GROUP its own value, sweep it against the invariant matrix, and deliberately
regenerate the voxel golden fixture, since it visibly changes how close fish swim to floors and
ceilings. Bundling that with Stage 2 would also have destroyed Stage 2's correctness gate, which
is precisely "the fixture must **not** move". The reasoning is repeated at the call site in
`VoxelDomain.avoidance` so the next reader does not rediscover it the hard way.

### 9.6 In-game acceptance, and what is still unmeasured

**Accepted in game 2026-09-10.** Reported on a 100+ tank build — comfortably above the old cap of
64, so the raise itself is exercised: no noticeable lag, and both hitches the caches target are
visibly gone (adding a connected tank, which invalidates the membership epoch and re-walks; adding
a fish to a tank, which no longer rebuilds the distance field).

That is the acceptance pass, and it is worth being precise about what it does and does not settle.
It confirms the change is not a regression at a size that used to be impossible. It does **not**
answer §3.6 or §8.1: nobody has measured what rendering N fish costs, "no noticeable lag" is an
impression rather than a number, and a 100-tank build sits far below both the 512 tank cap and the
1024 fish budget — so neither bound was actually pressed.

§3.6 and §8.1 are therefore still open: every number in this section is simulation cost. The raise
to 512 is safe with respect to *simulation* because the fish budget bounds it; if item-model
rendering turns out to be the real ceiling, the budget — not the tank cap — is the knob to turn
down, and it is now a single constant.

The cheap way to close this out, if it ever matters: build past 38 tanks with the tanks fully
stocked, so the quota actually binds and the fish budget is saturated, and watch a frame-time graph
rather than watching for a stutter.

### 9.7 Where the code is

| Concern | File |
|---|---|
| Caps, fish budget, quota, epoch, flood fill | `common/.../fishtank/TankGroups.java` |
| Shared group + domain cache | `common/.../client/util/ClientTankGroups.java` |
| Quota applied to both passes | `common/.../client/renderer/TankFlockAdapter.java` |
| Epoch bumps, `isFaceOpen` | `common/.../blockentity/FishTankBlockEntity.java` |
| Spatial index | `fishsim/.../core/NeighborGrid.java` |
| Radius derivation, index wiring | `fishsim/.../core/FlockEngine.java` |
| Bit-exactness gate | `fishsim/src/test/.../SpatialIndexEquivalenceTest.java` |
| Budget arithmetic | `common/src/test/.../TankGroupsBudgetTest.java` |
| Measurement probe (skipped without `-PsimStdout`) | `fishsim/src/test/.../StepScalingProbe.java` |
