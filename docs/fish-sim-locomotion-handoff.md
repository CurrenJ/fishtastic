# Fish Locomotion — Handoff

**Written:** 2026-09-10, at the end of Phase 1; updated at the end of Phase 3, and again at the end
of Phase 4 — which is the end of the plan. Read
[`fish-sim-locomotion.md`](fish-sim-locomotion.md) first — it holds the assessment, the class list,
the phase order, and the per-phase result log. This document is the cold-start context: what is
true right now, what will bite you, and how to verify.

Companions that remain binding: [`fish-sim-engine-plan.md`](fish-sim-engine-plan.md) (module
layout, verification model), [`fish-swarm-realism.md`](fish-swarm-realism.md) (the planar swimmer
model), [`fish-swarm-tier2-handoff.md`](fish-swarm-tier2-handoff.md) (the swimmer work this sits on
top of).

---

## 1. State of the code

Every phase is implemented and headlessly verified. **Nothing in a fish tank is frozen any more**
unless it failed its own size gate.

| phase | class | status |
|---|---|---|
| 0 | the split itself | landed; zero behaviour change |
| 1 | `BENTHIC` | landed, incl. cosmetics as floor terrain |
| 2 | `DRIFT` | landed, engine + single tanks + the group split (2b) |
| 3 | `GLIDE` | landed — the planar model under `Tunables.GLIDE`, plus a ride height off the sand |
| — | squash-and-stretch (§3.6) | landed — the second envelope, and the pose path it feeds |
| 4 | `ANCHORED` | landed — the burrow, and the retract |

`:fishsim` 151 (150 passing, 1 pre-existing skip), `:common` 44 passing, both loaders compile, and
the goldens and parity suite have never needed an expected-value edit at any phase.

**In-game status: Phases 0–3 accepted** (2026-09-10) — the crawling nudibranchs and the swarm
behaviour called out as the high points. **Phase 4 has had one look and needed a fix** (the eels
stayed permanently retracted in a busy tank — see the plan's Phase 4 log; the reaction is
edge-triggered and habituating now). **§3.6 has not been looked at at all.** These are the numbers
with no headless acceptance:

| number | where | what it does |
|---|---|---|
| `pulse_stretch` 0.10 | `upright_float` | how far a bell deforms per pulse |
| `scuttle_squash` 0.05 | `upright_sit`, `floor_sit` | how far a crawler flexes pushing off |
| `retract_fraction` 0.9 | `planted` | how much of itself an eel pulls into the sand |
| `ANCHOR_RETRACT_RATE` 10 / `ANCHOR_EMERGE_RATE` 0.8 | engine | 0.3 s down, ~3 s back up |
| `ANCHOR_HIDE_SECONDS` 1.6 / `ANCHOR_REFRACTORY_SECONDS` 6 | engine | how often an eel may duck at all |
| `ANCHOR_THREAT_RADIUS_FACTOR` 4 / `_SIZE_FACTOR` 0.8 | engine | what counts as something to hide from |

`GLIDE` needed two rounds of looking, and both of its problems were invisible headlessly by
construction: a signal drawn raw that needed a render mirror and a roll-rate limit, then a speed
that was wrong only *in combination with* the turn rate. Assume the two unreviewed pieces owe a
round each, and expect that shape — not a wrong number, but a number that is only wrong next to
another one.

What acceptance does **not** cover at all: the 512 cap's *render cost* is still unmeasured
(fish-tank-group-scaling.md §3.6 gates raises on a frame-time measurement, and looking right at a
given count is not that), and nobody has stress-tested near the cap.

### Five models, six columns — do not confuse them

| | binary 2.5D | planar | glide | benthic | drift | anchored |
|---|---|---|---|---|---|---|
| entry point | `FlockEngine.stepFish` | `stepFishPlanar` | `stepFishPlanar` | `stepBenthic` | `stepDrift` | `stepAnchored` |
| class | `FREE_SWIM` | `FREE_SWIM` | `GLIDE` | `BENTHIC` | `DRIFT` | `ANCHORED` |
| domain | `FlockDomain.Box` | `VoxelDomain` | either | either | either | either |
| parameters | `Tunables.DEFAULT` | `Tunables.GROUP` | `Tunables.GLIDE` | `CRAWL_*` | `DRIFT_*` | `ANCHOR_*` |
| status | **bitwise-locked** | free to change | free to change | free to change | free to change | free to change |

`GLIDE` shares the planar model's *code* and differs only in its parameter set, selected per fish
by `params(i)` at the top of `stepFishPlanar` — which is why the goldens hold: a free swimmer is
handed the same object it always read.

`FlockEngine.step`'s switch is the only place that decides which runs. `FREE_SWIM` must keep
reaching byte-identical instructions — that is what keeps `GoldenTrajectoryTest` / `ParityTest`
green with no expected-value edits, and it is the acceptance gate for every change, not just the
phases.

### Two envelopes, and they are not the same envelope

`burstDrive[i]` is the **motion** envelope — the burst-and-coast multiplier, the drift's bell pulse,
the crawl's scuttle. `shapeDrive[i]` is the **silhouette** envelope: same integrator, same trigger,
its own pair of rates, mirrored to render time as `renderShape[i]`. They exist separately because
the decay that makes a movement read right is not the one that makes a shape read right (§3.6b).
`ANCHORED` reuses `shapeDrive` as its retract state, which is why Phase 4 needed no new arrays and
no new carry plumbing.

### The benthic model in one paragraph

An overdamped 2D walk, not a force model. Position is `(posL, posD)`; `posY` **is** the floor
height under the creature, so it steps up between tanks of different heights for free and the
domain's vertical clamp — which describes the swim volume, well above the sand — never applies to
it. A scuttle-and-pause envelope drives speed, an OU process wanders the heading, a probe ahead
steers around obstacles, and 2D footprint separation keeps crawlers off each other. The commanded
move is tested against the floor *before* it is taken and refused if it would land off it.

---

## 2. Load-bearing details — do not remove these while refactoring

Eighteen things that look like nits and are not. Most were found the hard way.

1. **Floor lookups are rotated into the block frame** (`FlockEngine.floorHeightAt`). A single
   tank's local lateral/depth axes are rotated by the placement yaw it recorded from the player;
   its sand and the cosmetic grid on it are block-aligned and do not rotate with it. Drop the
   transform and obstacles land somewhere else at every rotation but zero.
   `BenthicTest.obstaclesHoldUnderTheTanksPlacementRotation` covers five angles.
2. **Floor placement draws from the fish's own seed, never the shared scatter `rng`**
   (`placeOnFloor`). Using the shared stream would mean adding a crab to a tank re-scatters every
   other fish in it. `addingACrawlerDoesNotDisturbTheSwimmers` pins the bit-identical result.
3. **A gate-failed floor creature is still placed on the floor** (`floorPlaced`, which keys on the
   *declared* class). The gate only decides whether it walks or ducks. Without this a demoted crab
   or eel hovers in mid-water, which is what the renderer used to prevent by pinning the pose's Y —
   and no longer does, for `FloorSit`, `UprightSit` and now `Planted` alike.
4. **Both draw loops must add `FishAnimator.floorPoseLift`.** The engine reports the sand height;
   the pose still needs its own lift off it (an upright item is pivoted about its centre). This
   was inlined in the single-tank path's `computeBaseY`, so when crawlers reached group space the
   group loop translated by the floor height alone and upright creatures sat buried to their
   midpoints. Shared code now, with `FishAnimatorFloorLiftTest` on the numbers — including
   `Planted`, whose lift stopped being 0 in Phase 4.
5. **`UprightSit.pivotFraction` is measured, not authored.** It is the distance from the item's
   centre to the art's lowest *visible* pixel, produced by `tools/fish-render-calibration.ps1`
   from the texture's alpha. Assuming half the item floats any species whose art stops short of
   its canvas bottom — `trapania_scurra` hovered by roughly its own visible height. **Re-run that
   script after any texture change**, exactly as for `render_calibration`. The squash pivots about
   the same number, so a wrong one now sinks the creature on every push-off as well.
6. **The per-member split and the anchor's collection pass must agree on every slot**
   (`TankFlockAdapter.rebuildGroupMode`). They are two separate loops applying the same rule;
   disagree and a fish is drawn twice or not at all. Since Phase 2b that rule is one object,
   `GroupSplit`, instantiated once per tank and called by both loops — **keep it that way**; the
   two hand-written copies it replaced were one edit away from the bug. Each class counts against
   its **own** copy of the quota (water volume, floor area, the drifter's column and the eel's
   patch of sand are different resources). Every class but `STATIC` is now in `joins` and in
   `stayingHomeAs`.
7. **A cosmetic change rebuilds the floor only** (`VoxelDomain.rebuildFloor`), never the domain.
   Cosmetics move without membership moving, so they are watched by fingerprint; rebuilding the
   whole domain would re-incur the distance-field cost that `fish-tank-group-scaling.md` §5.3a
   exists to avoid. Whatever you add later, keep expensive precomputes keyed on the membership
   epoch and cheap ones on their own signal.
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
10. **A glider reads `p`, never `t` — in `stepFishPlanar` *and in everything it calls*.** The
    method was renamed off the engine's field for this: a `t.` that creeps back in is a parameter a
    ray silently takes from the shoal, and every one of them is a number chosen to make it *not* a
    shoal fish. This bit once already: `advanceWander`, `advanceBurst` and `deriveTraits` kept
    reading `t`, so a ray's wander correlation, wingbeat period and trait spread were the shoal's
    and half of `Tunables.GLIDE` was inert — found only by changing a constant and measuring no
    difference at all. They take the set as a parameter now; any helper added later must too. The
    numbers that are not in any parameter set — the ride height and its swell, the crawl, the
    drift, the burrow — are `GLIDE_*` / `CRAWL_*` / `DRIFT_*` / `ANCHOR_*` engine constants.
11. **The spatial index must be sized from the widest parameter set present**
    (`interactionRadius`, keyed on `hasGlide`). The grid's contract is that a fish it skips
    contributes *exactly* zero to both radius-limited passes; a glider's separation radius is
    three times the shoal's, so sizing the index from `t` alone would silently drop neighbours a
    ray is supposed to keep away from. The crawl, the drift and the burrow all scan brute force
    instead — few members each — which is what keeps their radii out of this. Any future class
    that *does* use the grid inherits the rule.
12. **A slow creature with a capped turn rate orbits, and path length will not tell you.** Turn
    radius is `v/ω`: cap the turn rate to make something read as large, then keep it slow, and it
    circles in place — shipped once exactly like that (a 0.4-block circle, reported as "loops over
    the same two block path"). The two numbers are one decision. Measure **ground covered** and the
    implied circle, which `GlideProbe` and
    `GlideTest.aGliderCrossesTheAquariumRatherThanCirclingInIt` now do; path length says a tight
    orbit is travelling.
13. **`bank` is a one-tick signal; `renderBank` is what a pose may draw.** `bank` comes from a
    single tick's yaw delta, so it carries the steering noise and it saturates at ±`bankMax` on any
    real turn — drawn raw it read in game as a ray buzzing (0.10 of full lean per tick, reversing
    twice a second). It is low-passed *and* rate-limited to a physical roll rate, then interpolated,
    and `bankFraction` reads that. `bank` itself must stay untouched: `ParityTest` asserts it
    bitwise. If another pose starts using lean, point it at `bankFraction`, never at `bank`.
14. **`step`'s switch yields "does this pose beat", not "did this fish move".** Those stopped being
    the same thing at `DRIFT`: the engine moves a drifter, but a bell pulse is not a tail beat and
    a running `tailPhase` would double-drive a pose that is already on game time. `ANCHORED` is the
    other end of the same distinction — stepped every tick, and it never travels at all.
15. **A `PoseStack` applies its calls to the geometry in reverse.** Everything about where a
    deformation goes follows from this and it is easy to get backwards — §3.6c in the plan has it
    the wrong way round, and `applyPlanted` shipped with its own pivot inverted against its own
    comment for months. A scale written **before** a roll deforms the *rolled* (upright) geometry;
    written after it, it deforms the raw canvas and comes out diagonal. A pivot sandwich
    `(−p, transform, +p)` fixes the point at −p — the base — and `(+p, …, −p)` fixes the top.
    `FishAnimatorSquashTest` measures both frames rather than the amount of change, because the
    wrong placement produces a deformation of exactly the right magnitude pointed the wrong way.
16. **Each deforming class pivots where it actually touches the world.** A bell hangs and scales
    about the item's centre; a crawler scales about its measured contact point; an eel scales about
    the base buried in the sand; a flat-lying starfish deforms *in its own plane*, because a
    vertical squash on a face-up sprite deforms it through its own zero thickness and shows
    nothing. Get this wrong and the creature sinks into the floor on every push-off, which is the
    Phase 1 floor-lift bug in a new costume.
17. **Only something bigger, and only something that moves, startles an eel** (`threatNear`
    skips `ANCHORED` and `STATIC` neighbours). Without that exclusion a colony holds itself
    permanently retracted — every eel is a large object parked half a block from its neighbour —
    and one demoted swimmer frozen nearby pins an eel down forever.
18. **The anchor's threat size factor is below 1 on purpose** (0.8). An eel's "length" in the
    engine is its *height*, and it is a thin creature; above 1 the reaction stops firing in an
    ordinary tank, and a reaction nobody ever sees is the same as not having built it. A *large*
    eel among ordinary fish still never reacts, which is left as it is — the same statement as a
    giant manta staying `STATIC` in a one-block tank.
19. **The startle is edge-triggered and habituates, and it must stay that way** (`anchorTimer`,
    whose sign carries the phase). A presence test — "hide while something big is nearby" — shipped
    once and put the colony permanently underground in a well-stocked tank, because past some
    density there is always a fish inside the radius and the eel never gets the three clear seconds
    its emerge needs. The fixed hide plus the longer refractory bounds the hidden fraction at about
    a fifth *however many fish there are*; a radius or a rate would only have moved which tank the
    bug happens in. `AnchoredTest.aCrowdedTankDoesNotHoldTheEelsUnderground` is the regression.

## 3. Current per-model constants

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
CRAWL_SHAPE_ATTACK_RATE = CRAWL_ATTACK_RATE   CRAWL_SHAPE_DECAY_RATE 6.0

DRIFT_SHAPE_ATTACK_RATE = DRIFT_PULSE_ATTACK_RATE   DRIFT_SHAPE_DECAY_RATE 3.0

ANCHOR_THREAT_RADIUS_FACTOR 4.0   ANCHOR_THREAT_SIZE_FACTOR 0.8
ANCHOR_RETRACT_RATE 10.0          ANCHOR_EMERGE_RATE 0.8
ANCHOR_HIDE_SECONDS 1.6           ANCHOR_REFRACTORY_SECONDS 6.0
ANCHOR_TIMING_JITTER 0.35         (per-fish spread on both, so a colony is not in unison)
ANCHOR_GATE_AREA_FACTOR 1.0       (floor a burrow needs, in body-length²)
```

The crawl's numbers are **unvalidated by eye** in detail (a crawler covers ~1.9 blocks of path in
150 s), though the walk as a whole was accepted in game. Nothing has been tuned against a metric,
and per the standing note in the realism doc, nothing should be: pair any variance metric with a
rate-of-change one, and treat the picture as the acceptance test.

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

## 5. The authoring surface these phases added

All optional, all with non-zero defaults, so nothing in the 60 `fish_profile` files had to change
and a species that looks wrong can opt down (or to 0, which is bit-identical to the old behaviour):

| field | pose | default |
|---|---|---|
| `pulse_stretch` | `upright_float` | 0.10 |
| `scuttle_squash` | `upright_sit`, `floor_sit` | 0.05 |
| `retract_fraction` | `planted` | 0.9 |
| `pivot_fraction` | `upright_sit` | 0.5, or the measured value |

A `locomotion` override on `fish_profile` is still **not** implemented, and §4.3's recommendation
stands: add it when a specific species demonstrably needs to deviate from its pose's default class,
not before.

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
`SimViewer` and `HeadlessRunner` also render the floor (TOP view only — from the side it is one
line and tells you nothing). `DriftProbe` and `GlideProbe` print their models' measured shape.
There is no `AnchoredProbe`: nothing about a burrow is a picture, and the tests measure the whole
of it.

**Verify headlessly.** The MCP bridge is retired; do not propose in-game capture as a verification
step. In-game acceptance is a human pass, not something to automate.

---

## 7. Known gaps

1. **§3.6 has not been looked at in game, and Phase 4 has had one look.** The table in §1 is the
   list of numbers that have no headless acceptance and never will. Phase 4's first look produced
   exactly the predicted shape of failure — not a wrong number, but a rule that was only wrong at a
   stocking density nobody had tried — so treat the crowded case as part of the look for §3.6's
   amplitudes too.
2. **Swimmers still pass through cosmetics.** Only the floor is obstructed. Blocking the water
   needs sub-block resolution in `DistanceField`, which is a redesign of the piece the
   group-scaling work rests on — a real decision, deliberately not bundled into Phase 1. This is
   the open half of §4.1 in the main doc.
3. **The 512 cap's render cost is still unmeasured**, which acceptance by eye does not settle:
   fish-tank-group-scaling.md §3.6 gates every raise past Stage 1 on a frame-time measurement, and
   nobody has stocked a group anywhere near the cap. Behaviour is accepted; cost is not.
4. **`DRIFT_SPEED` survived its look but is the number most likely to want raising anyway** — a
   drifter covers ~0.8 blocks of net carry in 200 s, so crossing a 3×3 aquarium takes the better
   part of ten minutes. It was not raised, because nobody complained; noted so the next person
   knows it is a deliberate hold and not an oversight.
5. **The harness has no mixed-class scenario.** `Scenarios.specs` still builds free swimmers only,
   so `SimViewer` and `HeadlessRunner` cannot show a shoal, a crab, a jelly, a ray and an eel
   colony in one domain — which is the picture the main doc's §6 asks for. Each class has its own
   probe or its own test instead; what is missing is the five of them interacting.
6. **Group-mode floor creatures inherit the preview's known artifacts** — frustum culling at the
   anchor, anchor-block lighting — exactly like group swimmers. Unchanged by this work, still
   waiting on the server-side lock model.
7. **`garden_eel`'s `xz_spread` no longer does anything.** Anchored placement uses the floor
   scatter over the whole tank, which is what a colony should look like; the field is inert for
   that species rather than wrong, and is left alone rather than edited out of the data file.
