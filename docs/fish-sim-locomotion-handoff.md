# Fish Locomotion — Handoff

**Written:** 2026-09-10, at the end of Phase 1; updated at the end of Phase 2b. Read
[`fish-sim-locomotion.md`](fish-sim-locomotion.md) first — it holds the assessment, the class list,
the phase order, and the per-phase result log. This document is the cold-start context for picking
up Phase 3: what is true right now, what will bite you, and how to verify.

Companions that remain binding: [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md) (module
layout, verification model), [`fish-swarm-realism.md`](fish-swarm-realism.md) (the planar swimmer
model), [`fish-swarm-tier2-handoff.md`](fish-swarm-tier2-handoff.md) (the swimmer work this sits on
top of).

---

## 1. State of the code

Phases 0, 1 and 2 (including 2b, the group split) are implemented and headlessly verified.
Phases 3–4 are untouched.

| phase | class | status |
|---|---|---|
| 0 | the split itself | landed; zero behaviour change |
| 1 | `BENTHIC` | landed, incl. cosmetics as floor terrain |
| 2 | `DRIFT` | landed, engine + single tanks + the group split (2b) |
| 3 | `GLIDE` | not started — 3 ray species still frozen |
| 4 | `ANCHORED` | not started — 2 garden eels still frozen |

`:fishsim` 131 passing (1 pre-existing skip), `:common` 36 passing, both loaders compile.

**In-game status.** Crawlers have had a *partial* look: the user confirmed `willans_chromodoris`
sits correctly after the group-path floor-lift fix, and reported `trapania_scurra` floating, which
was a pre-existing pivot bug now fixed by measurement. Nobody has yet watched a crawler walk for a
while, or watched one in a real multi-tank group. **A full in-game acceptance pass is still owed —
for this, for Tier 2, and for the 512-fish cap, all three of which have landed headlessly only.**
Doing all three in one session is the sensible move.

### Four models now — do not confuse them

| | binary 2.5D | planar | benthic | drift |
|---|---|---|---|---|
| entry point | `FlockEngine.stepFish` | `stepFishPlanar` | `stepBenthic` | `stepDrift` |
| class | `FREE_SWIM` | `FREE_SWIM` | `BENTHIC` | `DRIFT` |
| domain | `FlockDomain.Box` | `VoxelDomain` | either | either |
| parameters | `Tunables.DEFAULT` | `Tunables.GROUP` | engine constants (`CRAWL_*`) | engine constants (`DRIFT_*`) |
| status | **bitwise-locked** | free to change | free to change | free to change |

`FlockEngine.step`'s switch is the only place that decides which runs. `FREE_SWIM` must keep
reaching byte-identical instructions — that is what keeps `GoldenTrajectoryTest` / `ParityTest`
green with no expected-value edits, and it is the acceptance gate for every phase, not just
Phase 0.

### The benthic model in one paragraph

An overdamped 2D walk, not a force model. Position is `(posL, posD)`; `posY` **is** the floor
height under the creature, so it steps up between tanks of different heights for free and the
domain's vertical clamp — which describes the swim volume, well above the sand — never applies to
it. A scuttle-and-pause envelope drives speed, an OU process wanders the heading, a probe ahead
steers around obstacles, and 2D footprint separation keeps crawlers off each other. The commanded
move is tested against the floor *before* it is taken and refused if it would land off it.

---

## 2. Load-bearing details — do not remove these while refactoring

Seven things that look like nits and are not. Most were found the hard way.

1. **Floor lookups are rotated into the block frame** (`FlockEngine.floorHeightAt`). A single
   tank's local lateral/depth axes are rotated by the placement yaw it recorded from the player;
   its sand and the cosmetic grid on it are block-aligned and do not rotate with it. Drop the
   transform and obstacles land somewhere else at every rotation but zero.
   `BenthicTest.obstaclesHoldUnderTheTanksPlacementRotation` covers five angles.
2. **Benthic placement draws from the fish's own seed, never the shared scatter `rng`**
   (`placeOnFloor`). Using the shared stream would mean adding a crab to a tank re-scatters every
   other fish in it. `addingACrawlerDoesNotDisturbTheSwimmers` pins the bit-identical result.
3. **A gate-failed crawler is still placed on the floor.** Placement keys on the *declared* class,
   the gate only decides whether it walks. Without this a demoted crab hovers in mid-water, which
   is what the renderer used to prevent by pinning the pose's Y — and no longer does.
4. **Both draw loops must add `FishAnimator.floorPoseLift`.** The engine reports the sand height;
   the pose still needs its own lift off it (an upright item is pivoted about its centre). This
   was inlined in the single-tank path's `computeBaseY`, so when crawlers reached group space the
   group loop translated by the floor height alone and upright creatures sat buried to their
   midpoints. Shared code now, with `FishAnimatorFloorLiftTest` on the numbers.
5. **`UprightSit.pivotFraction` is measured, not authored.** It is the distance from the item's
   centre to the art's lowest *visible* pixel, produced by `tools/fish-render-calibration.ps1`
   from the texture's alpha. Assuming half the item floats any species whose art stops short of
   its canvas bottom — `trapania_scurra` hovered by roughly its own visible height. **Re-run that
   script after any texture change**, exactly as for `render_calibration`.
6. **The per-member split and the anchor's collection pass must agree on every slot**
   (`TankFlockAdapter.rebuildGroupMode`). They are two separate loops applying the same rule;
   disagree and a fish is drawn twice or not at all. Since Phase 2b that rule is one object,
   `GroupSplit`, instantiated once per tank and called by both loops — **keep it that way**; the
   two hand-written copies it replaced were one edit away from the bug. Each class counts against
   its **own** copy of the quota (water volume, floor area, and the drifter's column are different
   resources). Adding a class in Phase 3 or 4 is one line in `GroupSplit.joins`, and — if it can
   move under its own steam in a lone tank — one in `stayingHomeAs`.
7. **A cosmetic change rebuilds the floor only** (`VoxelDomain.rebuildFloor`), never the domain.
   Cosmetics move without membership moving, so they are watched by fingerprint; rebuilding the
   whole domain would re-incur the distance-field cost that `fish-tank-group-scaling.md` §5.3a
   exists to avoid. Whatever you add later, keep expensive precomputes keyed on the membership
   epoch and cheap ones on their own signal.

---

8. **A drifter's wall margins are its own, not `Tunables`'** (`DRIFT_WALL_MARGIN`,
   `DRIFT_WALL_MARGIN_VERTICAL`). Point them at `t.wallMargin()` and the avoidance term goes
   active across the whole width of a one-block-deep tank, where it out-accelerates the drift
   four to one and the jellyfish reads as swimming. `DriftTest.aDrifterNeverSwims` catches it,
   but only because it measures *sustained* displacement — a peak bound would be a test of the
   wall margin wearing a drift test's name.
9. **The pulse and the sink are balanced against each other by construction, not by tuning.**
   `DRIFT_SINK_SPEED` is the duty-cycle mean of `DRIFT_PULSE_SPEED`; the leftover bias is absorbed
   by the avoidance term, which is why the model survives a domain of a different height.
   `DriftTest.theVerticalCycleStaysCentred` is the guard, and it samples the whole run rather than
   the endpoint — a jellyfish that pins to the lid for a minute and comes back down would pass an
   endpoint check.
10. **`step`'s switch yields "does this pose beat", not "did this fish move".** Those stopped being
    the same thing at `DRIFT`: the engine moves a drifter, but a bell pulse is not a tail beat and
    a running `tailPhase` would double-drive a pose that is already on game time.

## 3. Current benthic constants

In `FlockEngine`, not `Tunables` — deliberately, following `PLANAR_TURN_RATE` and the burst
envelope rates. They are internal to one motion model, and keeping them out of `Tunables` means
`DEFAULT`/`GROUP` are untouched and the parity lock cannot be reached from them at all. They
graduate to `Tunables` the day `SimViewer` needs sliders for them (which is cheap — see the Tier 2
handoff §1).

```
CRAWL_SPEED 0.035          CRAWL_TURN_RATE 4.5        CRAWL_DWELL_SECONDS 6.0
CRAWL_DUTY 0.35            CRAWL_ATTACK_RATE 2.5      CRAWL_STOP_RATE 2.0
CRAWL_WANDER_SIGMA 0.25    CRAWL_WANDER_THETA 1.2
CRAWL_PROBE 0.14           CRAWL_PROBE_SPREAD 60      CRAWL_EDGE_MARGIN 0.02
CRAWL_FOOTPRINT 0.6        CRAWL_SEPARATION_SPEED 0.04
CRAWL_GATE_AREA_FACTOR 4   (walkable floor needed, in body-length²)
```

These are **unvalidated by eye**. A crawler covers ~1.9 blocks of path in 150 s at these values.
Nothing has been tuned against a metric, and per the standing note in the realism doc, nothing
should be: pair any variance metric with a rate-of-change one, and treat the picture as the
acceptance test.

## 4. Measured per-species data

`pivot_fraction`, written into `fish_profile/*.json` by the calibration script:

| species | pivot | note |
|---|---|---|
| `trapania_scurra` | 0.19 | art fills half its canvas; was the visible bug |
| `japanese_spider_crab` | 0.34 | |
| `common_octopus` | 0.41 | also carries a hand-tuned `floor_offset: 0.12` — worth re-checking in game now the pivot is right |
| `willans_chromodoris` | 0.41 | confirmed good in game |

Unmeasured species default to 0.5, i.e. the pre-measurement behaviour, so adding the field moved
nothing else.

---

## 5. Where the seams are for Phases 3–4

Everything the remaining classes need already exists; none of them needs new domain machinery the
way `BENTHIC` needed the floor.

* **`DRIFT` (Phase 2) — done, including the group split (2b).** The `upright_float` pose's
  `spin_rate` and bob stayed where they are: pose, not motion. One lesson from 2b worth carrying
  into Phase 3: the **group draw loop** in `FishTankBlockEntityRenderer.submitGroupSwimmers` had a
  bare `(HorizontalSwim) anim` cast on its else branch, which was correct only while the group
  held swimmers and crawlers. It now branches on `swimmers[]` first. Admitting `GLIDE` (a
  `belly_down`) to the group would have hit the same cast — check that branch before you widen
  `GroupSplit`.
* **`GLIDE` (Phase 3, rays) — next.** Mostly a parameter set over `stepFishPlanar`, so the work is
  choosing where those parameters live — this is the case that probably *does* want `Tunables`
  entries, since it is the same model with different numbers. Also switch `belly_down`'s
  `bank_amplitude` from an open-loop sine to the engine's `bank[i]`, the way `applySwimming`
  already does for swimmers.
* **`ANCHORED` (Phase 4, garden eels).** Cheapest of the three. Fixed footprint from a 2D floor
  scatter (reuse `placeOnFloor`), plus a retract/emerge float driven by the neighbour query the
  engine already performs. Note `Planted`'s Y is **still pinned by the renderer**
  (`computeBaseY`), unlike the crawler poses — that pinning is what Phase 4 replaces, and
  `floorPoseLift` deliberately returns 0 for it today so the two cannot double-count.

Each phase is expected to move that class's row out of `LocomotionTest`'s "never moves" assertions
and into its own invariant set, the way `BenthicTest` replaced `BENTHIC`'s. That test failing is
the signal that the phase is working, not that it broke something.

---

## 6. How to verify

```bash
./gradlew :fishsim:test :common:test          # must stay green, with no golden/parity edits
./gradlew :neoforge:compileJava :fabric:compileJava
```

The picture, which is the real acceptance test for anything behavioural:

```bash
# top-down view of the floor walk, with each crawler's path drawn over the terrain
java -cp "fishsim/build/classes/java/main;fishsim/build/classes/java/test" \
     grill24.fishsim.BenthicProbe out.png 3000
```

Brown cells are obstructed floor, dark cells walkable sand; crawler trails should hug the
boundaries without crossing them, and take whatever corridor is left between obstacles.
`SimViewer` and `HeadlessRunner` also render the floor now (TOP view only — from the side it is
one line and tells you nothing).

**Verify headlessly.** The MCP bridge is retired; do not propose in-game capture as a verification
step. In-game acceptance is a human pass, not something to automate.

---

## 7. Known gaps

1. **Swimmers still pass through cosmetics.** Only the floor is obstructed. Blocking the water
   needs sub-block resolution in `DistanceField`, which is a redesign of the piece the
   group-scaling work rests on — a real decision, deliberately not bundled into Phase 1. This is
   the open half of §4.1 in the main doc.
2. **No in-game acceptance** for the benthic walk, the drift, Tier 2, or the 512 cap. See §1.
   The drift constants have the same status as the crawl's: measured with `DriftProbe`, never
   looked at. The one most likely to want raising after a look is `DRIFT_SPEED` — a drifter
   covers ~0.8 blocks of net carry in 200 s, so crossing a 3×3 aquarium would take the better part
   of ten minutes.
3. **The benthic constants have never been looked at by eye.** §3.
4. **`lined_seahorse`'s `render_calibration` moved 1.1 → 1.07** when the calibration script was
   re-run. That is pre-existing drift from a texture change, not a locomotion change; it is in the
   working tree and should be either kept deliberately or split out.
5. **Group-mode crawlers inherit the preview's known artifacts** — frustum culling at the anchor,
   anchor-block lighting — exactly like group swimmers. Unchanged by this work, still waiting on
   the server-side lock model.
