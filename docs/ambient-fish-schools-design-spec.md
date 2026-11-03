# Ambient fish schools and speed variety: design spec

**Status:** Design accepted 2026-11-02; owner decisions D1–D7 in §10. Steps 0–2b accepted in the owner playtest 2026-11-02 and committed (a4321590); step 3 and the step 4 group leash accepted in game 2026-11-03 and committed; measurements in §11.
The step table in §9 tracks progress.

**Related:** [ambient fish](fish-in-world.md),
[world fish exploration](world-fish-exploration-design-spec.md),
[in-world fish encounters](in-world-fish-encounters-design-spec.md) (the authored `world_encounter.shape`),
[swarm realism](fish-swarm-realism.md), [locomotion classes](fish-sim-locomotion.md).

## 1. Problem

The open water looks staged. Two separate problems cause this.

1. **No groups.** Fish are spread evenly, one at a time. In a tank they have had time to gather
   into schools, and that is what makes a tank look alive.
2. **Uniform, slow speed.** Every free swimmer moves at about the same slow pace.

### 1.1 Why ambient fish never group (verified in code)

- **Each fish is placed independently.** A roster slot is one fish
  (`AmbientRoster`). Its home column, height and species are separate hashes of its own ID, so
  fish of the same species land in unrelated places across the chunk.
- **The flock cannot see across that gap.** Ambient domains run `Tunables.GROUP`, which was tuned
  for tanks. Its `neighborRange` is **0.9 blocks**, so alignment and cohesion only act on fish
  that are already within a block of each other. Same-species fish placed at random columns
  several blocks apart never sense each other, however long they live. This is the main
  cause; a short lifespan is secondary. (The lifespan is 10 to 20 minutes, but the player
  travels, so the fish in view are almost always recent spawns.)
- **The leash holds them apart.** A fish that strays more than `LEASH_MARGIN` (8) blocks past its
  home chunk fades out. Even if a group formed, it could not travel as one.
- **Species mixing.** The species draw (75% raw pool, 25% square-root flattened) is made per
  fish. A patch of water therefore holds a mixed sample of the pool, not a few species in numbers.

### 1.2 Why speed is uniform (verified in code)

- `FishSpec` carries length, locomotion, mirror, species and shelter use. **It has no speed.**
- Every FREE_SWIM fish uses `GROUP.patrolSpeed` 0.10 b/s (cruise ceiling `maxSpeed` 0.20),
  scaled by a ±12% per-fish trait and the 4-second burst swell. A 20 cm minnow and a 1 m
  pike cruise at the same block speed.
- 0.10 b/s suits a tank about one block wide. In open water a fish takes 10 seconds to cross a
  block, which reads as drifting rather than swimming.

So both problems come from the ambient sim reusing tank tuning in open water. Speed is partly
the "fish tank sim change" the owner suspected, but it can be fixed **without touching tanks**:
give ambient water its own tunable set and give the engine a per-fish speed input.

## 2. Goals and non-goals

**Goals**

- G1. Open water shows recognisable groups: schools of schooling species, pairs, loose groups,
  and lone fish, matching each species' authored `world_encounter.shape`.
- G2. A group looks **already formed** the first time it is seen. It does not assemble in front
  of the player.
- G3. Speed varies visibly between species and sizes, and occasional fast movers appear (owner
  preference: varied speed reads as alive, but every move must stay smooth).
- G4. No change to tank motion, tank goldens or interactive schools **by default**. Tanks get the
  cruise tier only as an opt-in trial (§5.4); it becomes the default only if the owner approves it in game.
- G5. Determinism is unchanged: two clients derive the same groups, species and members
  (E12: each client still moves its own copy).
- G6. Resource use is measured at every step (tick time, allocation, render submit) with the
  `ambientswim` scene, against the 2026-11-02 baseline: about 0.9 ms a tick and 0.6 MB a tick
  at density 50.

**Non-goals**

- No catchability. An ambient school is decoration. It must not borrow the interactive cues
  (quality trail tint, sprint bubbles), and it never makes a species catchable (exploration
  spec §4, §7).
- No school-specific steering code. Group motion goes through `FlockEngine` inputs and tunables
  only. This is the same rule the interactive schools follow.
- No change to which species are eligible where. That stays `NaturalPool`.

## 3. Design overview

A roster slot becomes a **group slot**. Each lifespan of the slot spawns a group, which holds
one species, one home cell and N members. N comes from the species' authored shape. The group
is placed already formed (clustered, with shared headings) and is leashed as a unit. Open water
runs its own tunable set, and each fish's speed is scaled by species and size.

```
lifespan(seed, chunk, slot, k) ──► groupId
   groupId ─► species draw (once)         ─► shape from world_encounter (solitary/pair/loose/school)
           ─► member count N in shape range
           ─► home cell, height
           ─► member i: fishId = mix(groupId, i), offset in formation, own traits
```

## 4. Grouping

### 4.1 Group slots

- `AmbientRoster.Lifespan` is unchanged. Its `fishId` becomes the **group ID**, and member IDs
  are `mix(groupId, i)` for `i < N`. Each member keeps its own mirror flag, density rank and
  traits, all hashed from its member ID.
- The species is drawn **once per group** at the group's home cell, as it is drawn per fish today.
- The shape comes from `FishProfile.visitShape()`: the authored `world_encounter.shape`, or the
  `base_weight` fallback. Of 66 profiles, 53 author a shape: 8 school, 14 loose group, 9 pair,
  22 solitary. 13 use the fallback.
- **Ambient member counts** are separate from interactive visit counts, because decoration can
  afford more fish than a catch event. Proposed defaults:

  | Shape | Interactive visit | Ambient group (proposed) |
  |---|---|---|
  | Solitary | 1 | 1 |
  | Pair | 2 | 2 |
  | Loose group | 2–4 | 3–5 |
  | School | 3–6 | 6–12 |

  A per-species `world_encounter.ambient_members` override is allowed but not required. **D1
  (decided):** ambient schools go up to 12.
- Only FREE_SWIM species may form a school, the same constraint as visits. Benthic and drifter
  species can be pairs or loose groups, which works through placement alone.

### 4.2 Keeping the species mix honest

If a slot spawns six neon tetras where it used to spawn one fish, schoolers flood the water.
Two options:

- **(a) Fish-weighted (recommended).** Divide each species' sampler weight by its expected group
  size, so the expected **share of individual fish** per species stays what it is today. The
  water keeps today's mix, but the fish of a species arrive together. The pool code is
  unchanged; this is one reweighting step in `VisualSampler.build`.
- **(b) Group-weighted.** Leave the weights as they are, so each group is drawn as one fish is
  today. Schoolers then outnumber everything else by about their group size.

**D2 (decided): (a).** The species mix by individual fish stays what it is today.

### 4.3 Slot count

A chunk's slot count today is capped by volume at 16 fish. With groups, the **member** total
must still respect the volume budget. Proposal: `slotCount` counts expected members. A chunk
earns slots until the sum of expected group sizes reaches its fish allowance. Water that can
hold 16 fish might then hold two schools of 6 and a few loners, rather than 16 scattered fish.
`AmbientBudget.individuals` remains the hard cap on simulated fish.

### 4.4 Formed on arrival

Convergence takes too long to rely on (G2), so the group is placed in formation:

- **Cluster placement.** Members are placed by rejection sampling around the home cell, at a
  spacing set by the shape: about 1.5 body lengths for a school, 3 to 4 for a loose group,
  1 to 2 for a pair. Each position goes through the existing `findPlacement` fit checks. A
  member that cannot fit is dropped from the group; the group is not skipped.
- **Shared initial heading.** Members of a school start with one heading plus a small hashed
  spread. Loose groups get independent headings.
- **Atomic spawn.** All members spawn, defer and fade in on the same tick. A half-spawned school
  would look like the pop-in that fill-ahead removed. The near-in-view deferral applies to the
  whole group.
- **Atomic displacement.** Cap displacement (`REASON_DISPLACED`) ranks and evicts whole groups.

### 4.5 Staying together

Flocking acts on same-species fish (`FishSpec.species`), so a placed school already flocks with
itself. To hold it together in open water:

- The open-water tunable set (§5.1) widens `neighborRange` from 0.9 to about 2.5 to 3 blocks,
  so members that drift apart can still find each other.
- **Per-shape cohesion.** Add a per-fish cohesion and alignment scale (a `FishSpec` field, so
  the engine never learns about shapes). A school member gets strong alignment and cohesion, a
  loose-group member medium, a solitary fish zero. A solitary fish then ignores its own species
  instead of clumping with the next lone fish of that species.
- **Group leash.** The leash is measured from the **group's centroid** to the home chunk
  footprint, with a margin of about 16 instead of 8. When the centroid strays, the whole group
  fades and re-enters at home. A single member that falls too far behind its group (more than
  about 6 blocks) fades alone and is not re-spawned until the next lifespan.
- **D3 (decided):** a group stays leashed to its home chunk with the looser centroid margin
  above. Roaming routes need cross-chunk ownership and are out of scope.

## 5. Speed variety

### 5.1 An open-water tunable set

Add `Tunables.OPEN_WATER`, derived from `GROUP`, and use it only in `AmbientFishRuntime` (in place
of `new FlockEngine(Tunables.GROUP)`). Tanks keep `GROUP` and `SINGLE`, so their goldens and
parity are untouched (G4). Starting points, all to be measured:

| Tunable | GROUP | OPEN_WATER (start) | Why |
|---|---|---|---|
| `patrolSpeed` | 0.10 | ~0.30 | Cross a block in about 3 s, not 10 |
| `maxSpeed` | 0.20 | ~0.70 | Headroom for bursts and fast species |
| `neighborRange` | 0.9 | ~2.5 | Schools sense members across open water |
| `wanderTurnTheta` | 0.9 | lower | Longer, straighter runs (no glass to turn at) |
| `separationRadiusOther` | 0.60 | ~1.0 | Strangers give each other room in open water |

Any tunable that bounds collision lookahead or wall avoidance has to be rechecked at the
higher speeds. The worst-case closest-approach test that capped `separationLookahead` should be
rerun on this set.

### 5.2 Per-fish speed scale

Add `FishSpec.speedScale`. It multiplies the fish's patrol speed and speed ceiling, and folds
into the existing trait multiplier (`patrolScale[i]`, `speedScale[i]`). It is the only new input
to the engine. The adapter computes it as:

```
speedScale = sizeTerm(length) × speciesTerm × individualTerm
```

- **sizeTerm.** Real fish cruise at roughly a constant number of body lengths per second, which
  would make a 1 m fish five times faster than a 20 cm one. That is too much spread for a game,
  so a damped curve is used: `(length / refLength)^0.5`, clamped to about 0.6 to 1.8.
- **speciesTerm.** A `world_encounter.cruise` field: `slow` (0.6), `normal` (1.0) or
  `fast` (1.5). **D4 (decided): every species authors its tier.** All 66 profiles get one in
  step 2, and the add-fish-species skill checklist gains it. A missing tier reads as `normal`
  so a third-party datapack profile still loads, but a test fails on any shipped profile
  without one. The tier scales speed within the species' own locomotion set (GLIDE and drifters
  keep their sets). Examples: trout-like fish `fast`, sunfish and coelacanth `slow`.
- **individualTerm.** The existing ±12% trait jitter, kept.
- **School members share one group speed scale** and keep only their individual jitter.
  `speedMatchWeight` is weak by design, so a school that disagreed on cruise would shear apart.

### 5.3 Occasional fast movers

The owner likes occasional fast movers, but a bigger variance metric alone shipped a visible
"hop" once. So:

- A small hashed fraction of FREE_SWIM groups (proposed 10%) are **transiting**. They get a
  speed scale of about 1.6 and a low wander turn, so they cross the area on a long, straight
  run. Lone fast fish and fast schools passing through give the water a sense of movement.
- A rare **dart** (proposed: a mean of one every 40 to 90 s per fish, hashed) is a single burst
  cycle with a higher thrust scale. It reuses the burst machinery, so it adds no new steering.
- Both are judged by **peak rate of speed change**, not just speed spread: the
  `withinCv`/`maxRate` pair from swarm realism. A change that raises spread but also raises
  `maxRate` beyond today's value fails.

### 5.4 Tank trial (owner request 2026-11-02)

The owner wanted to see whether the cruise tier also looks good in tanks. It was built as a trial
behind a toggle, accepted in game 2026-11-02, and then adopted as a full feature with the toggle
removed (D7). The bullets below record the trial design; the toggle ones are historical.

- **What applies in tanks:** only `speciesTerm` (the cruise tier) and the existing individual
  jitter. The size term is left out. Tanks render fish to scale inside a fixed volume, so a size
  term would make large fish both bigger and faster in the same small space, and they would hit
  the glass more often.
- **Damped spread.** A tank is about 0.7 blocks wide per block, so the open-water spread of
  0.6–1.5 is probably too wide. The trial starts at a tank spread of 0.8 / 1.0 / 1.25, set by a
  separate tunable so it can be widened without affecting open water.
- **Where it plugs in.** The tank adapters (`TankFlockAdapter` for single tanks,
  `TankGroupFlock` for tank groups) fill `FishSpec.speedScale` from the profile's tier when the
  toggle is on, and 1.0 when it is off. With the toggle off, every tank golden and the
  single-tank parity suite must stay bit-identical. That is the gate for adding the field at all.
- **Interactive schools stay at 1.0.** `FishSchoolSim` is a lockstep replica, and
  `FishSchoolWire` serialises `FishSpec`, so a new field there needs a wire change and a
  deterministic value on both sides. It is out of scope for the trial.
- **Toggle.** A client config `tanks.cruiseTiers` (default off) plus a dev command to flip it
  live, so the owner can A/B the same tank in a solo playtest. The `:fishsim` viewer gets the
  same switch, so a headless side-by-side GIF can come first.
- **Checks before the owner look:** in the tank harness, with the toggle on, measure glass
  contacts and closest approaches against the toggle off, plus the `withinCv`/`maxRate` pair.
  Fast fish must not pile into walls, and slow fish must not stall in place.
- If the owner likes it, the default flips and the tank goldens are regenerated in a separate
  commit, so the motion change can be reviewed on its own.

## 6. Determinism, budget and cost

- Group ID, species, shape, N, member IDs, offsets, initial headings, speed scales and transiting
  flags are all hashes of `(seed, chunk, slot, lifespan, i)`. Nothing new is sent over the network.
- Density draw. **D5 (decided):** density draws whole groups by group ID rank, so a lower
  setting shows fewer groups, each at full size.
- Cost. Wider `neighborRange` grows the neighbor-grid cells. Faster fish leave the discovered
  water sooner and use more of the fill-ahead lead. Both must be measured in `ambientswim` at
  densities 50 and 100, stationary and at 5.6 b/s. The budget to beat: tick 0.9 ms (1.0 at
  density 100), about 0.6 MB a tick, submit 0.18 ms a frame, and spawns seen appearing no worse
  than 6 to 14.

## 7. Verification

**Headless (`:fishsim` and unit tests)**

- `AmbientRoster` / group tests: group sizes inside the shape range; member IDs are stable;
  density subsets are stable (raising density only adds groups); the fish-weighted mix (D2a)
  matches today's per-fish species shares within binomial tolerance (extend `VisualSamplerTest`).
- An engine test that `speedScale = 1` and the `GROUP`/`SINGLE` sets leave every golden
  bit-identical (G4).
- New `ambientswim` metrics, logged per run:
  - **same-species nearest-neighbor share**: the fraction of fish whose nearest neighbor is the
    same species. This is the direct "clumped" measure. It is low today.
  - **school polarization** per group at spawn and 60 s later (formed on arrival, and stays formed).
  - **group cohesion**: the member spread around the centroid over time; schools must not split.
  - **speed distribution** across species and the `maxRate` guard.
- Metrics are proxies. The owner's look decides; the numbers only rule out regressions.

**In game:** a dev-only `/fishtastic ambient` debug that outlines groups and prints shape, N,
speed scale and transiting flag for the group under the crosshair, so the owner can judge in a
solo playtest. An `ambient regroup` command respawns nearby groups for quick A/B comparison.

## 8. Risks

- **Crowding.** Schools of 12 in a 3-wide river would jam. Placement drops members that do not
  fit, but narrow water may need a cap on N from the domain's longest run.
- **Shared look with interactive schools.** An ambient school must look different from a
  catchable one. Interactive visits keep the trail tint and bubbles, so this should hold. It
  needs an explicit check in the owner's look.
- **Speed exposes pop-in.** Faster fish cross the 32-block window sooner, so more spawn and leave
  per minute. Measure it, and consider ranking groups ahead of the player first.
- **Ports.** `FishSpec` and `Tunables` changes touch `:fishsim`, which all branches share. Keep the
  new fields additive with neutral defaults so tank parity holds on every branch.

## 9. Proposed steps

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline: add the §7 metrics to `ambientswim`, record today's numbers | Numbers logged |
| 1 | Built 2026-11-02, uncommitted. `Tunables.OPEN_WATER`; the ambient engines run it (the A/B commands used for the look are removed). Accepted in game 2026-11-02 |
| 2 | `FishSpec.speedScale` + size/species/individual terms; author `cruise` on all 66 profiles + skill checklist | Speed spread up, `maxRate` not up; cost logged; every profile has a tier |
| 2b | Built 2026-11-02, uncommitted. Tank adapters apply `CruiseTier.tank()` (0.8/1.0/1.25); `HeadlessRunner --cruise-tiers` for headless comparison. Accepted in game 2026-11-02 (single tanks and groups); adopted as a full feature, no switch |
| 3 | Group slots: species once per group, members, fish-weighted mix, atomic spawn, displacement and fade | Mix test; nearest-neighbor share up; no pop regression |
| 4 | Per-shape cohesion + group leash | Groups hold 60 s; cost logged |
| 5 | Transiting groups + darts | `maxRate` guard; owner look |
| 6 | Debug overlay + regroup command | Owner solo playtest |

Order (D6, decided): speed first (steps 1–2), then grouping (steps 3–4).

| Step | Status |
|---|---|
| 0 | Built 2026-11-02, uncommitted. `ambientswim` logs a `MOTION` line per settle; baseline in §11 |
| 1 | Built 2026-11-02, uncommitted. `Tunables.OPEN_WATER`; the ambient engines run it (the A/B commands used for the look are removed). Accepted in game 2026-11-02 |
| 2 | Built 2026-11-02, uncommitted. `FishSpec.speedScale` (engine field `cruise[]`, x1 bit-identical); tier × size term in `AmbientMotion`; `cruise` on all 66 profiles (11 fast, 27 normal, 28 slow); skill checklist + `WorldEncounterTest`. Accepted in game 2026-11-02: reads as swimming, no hops or twitches |
| 2b | Built 2026-11-02, uncommitted. Tank adapters apply `CruiseTier.tank()` (0.8/1.0/1.25); `HeadlessRunner --cruise-tiers` for headless comparison. Accepted in game 2026-11-02 (single tanks and groups); adopted as a full feature, no switch |
| 3 | Built 2026-11-02/03, committed. `AmbientGroups` (sizes, member IDs, spacing); roster slots are groups (`SLOT_DIVISOR` 2 after §11.4, at least two slots for small water, size capped by the chunk allowance); species once per group, fish-weighted sampler (D2); density draws groups (D5); whole-group spawn, deferral, retry and displacement; members cluster round an anchor (or a living groupmate well inside the leash), schools and pairs share a heading; observations report the group ID. Owner look 2026-11-03: groups look natural; water felt thin, retuned in §11.4 (density cap 224→288, groups fill to the cap). Re-look passed 2026-11-03 |
| 4 | Partly built with step 3: the group leash (centroid, `GROUP_LEASH_MARGIN` 16; a member fades alone only past that box plus `LEASH_MARGIN`). Needed because groups travelling together tripped the 8-block per-fish leash 267 times; a 6-block distance-to-centroid straggler rule was tried and dropped (it faded loose-group members, which spread without the stronger cohesion). Per-shape cohesion not started |
| 5 | Not started |
| 6 | Not started |

## 10. Owner decisions (2026-11-02)

- **D1** Ambient schools go up to 12 members.
- **D2** The species mix by individual fish stays the same (fish-weighted sampler).
- **D3** Groups stay leashed to their home chunk, with a looser centroid margin.
- **D4** Every species authors a `slow`/`normal`/`fast` cruise tier.
- **D5** Lower density shows fewer groups, not smaller ones.
- **D6** Speed first (steps 1–2), then grouping (steps 3–4).
- **D7** Try the cruise tier in tanks as an opt-in trial (step 2b); the default changes only after an in-game look.
  Outcome 2026-11-02: accepted in game and adopted as a full feature. The tier always applies in tanks,
  and the `tankCruiseTiers` setting, the `tankcruise` command and the ambient `motion` and `speed` A/B
  commands are removed (an old `tankCruiseTiers` line is dropped from the config file). The tank
  goldens did not need regenerating: they build specs without the adapter, so they stay at x1.

## 11. Measurements

### 11.1 Ambient, `ambientswim` (density 50, Fabric, 2026-11-02)

Two stationary settles of 600 ticks and two 120-block legs at 5.6 b/s per run. Logs:
`build/ambientschools-step0.log` (baseline) and `build/ambientschools-step2-full50.log` (`motion=full`).
Speeds in b/s; rate is the tick-to-tick change in speed, in b/s².

| Metric | Baseline (leg 0 / 1) | Steps 1+2 (leg 0 / 1) |
|---|---|---|
| Mean speed | 0.100 / 0.103 | 0.302 / 0.304 |
| Per-fish speed p10–p90 | 0.091–0.111 | 0.20–0.47 |
| Spread between fish (`acrossCv`) | 0.075 / 0.086 | 0.317 / 0.309 |
| Variation within a fish (`withinCv`) | 0.251 / 0.252 | 0.253 / 0.262 |
| Rate p95 (relative to mean speed) | 0.073 (0.73×) | 0.235 / 0.240 (0.78×) |
| Rate p99 (relative to mean speed) | 0.084 (0.84×) | 0.345 / 0.342 (1.13×) |
| `nnSame` vs random `nnChance` | 0.053 vs 0.100; 0.071 vs 0.061 | 0.073 vs 0.102; 0.038 vs 0.061 |
| Swim/stationary fish in view | 0.89 / 1.06 | 0.84 / 1.10 |
| Pops seen while swimming | 0 / 0 | 0 / 0 |
| Tick ms | 0.93 / 0.81 | 0.89 / 0.80 |
| Allocation KB/tick | 593 / 597 | 582 / 584 |
| Render submit ms/frame | 0.18 / 0.18 | 0.17 / 0.18 |

Reading:
- Speed tripled and the spread between fish quadrupled, and the cost did not move.
- At p95, speed changes scale with the speed itself (0.73× vs 0.78× of mean speed).
- The p99 tail is about 35% sharper relative to speed. Watch for a "hop" in the owner's look.
- One rate sample of 12.7 in leg 1 is a single re-placement under the 4 b/s jump filter, not a swim.
- Clumping is unchanged: it stays at about the random-mix level, as expected before grouping (step 3).

### 11.2 Tank trial, `HeadlessRunner` (12 fish, seed 42, 6000 ticks)

GIFs: `fishsim/build/tank-cruise-trial/` (`<domain>-n12-s42.gif` off, `<domain>-cruise-n12-s42.gif` on).

| Domain | Wall penetrations / hard clamps | Min pairwise distance | Speed CV | Max accel | Max jerk |
|---|---|---|---|---|---|
| box (single tank) off → on | 0 / 0 → 0 / 0 | 0.088 → 0.077 | 0.548 → 0.572 | 0.442 → 0.441 | 3.12 → 3.10 |
| L group off → on | 0 / 0 → 0 / 0 | 0.106 → 0.111 | 0.253 → 0.265 | 0.500 → 0.491 | 8.30 → 8.23 |
| 3x1x3 group off → on | 0 / 0 → 0 / 0 | 0.101 → 0.126 | 0.204 → 0.217 | 0.463 → 0.500 | 4.53 → 5.69 |

With the trial off, `:fishsim` goldens and parity pass bit-identically (579 tests, 0 failures).
`SpeedScaleTest` pins x1 bit-identity and a proportional speed change.

### 11.3 Grouping, step 3 (density 50, same fixture and seed as §11.1)

Log: `build/ambientschools-step3c.log`.

| Metric | Steps 1+2 (leg 0 / 1) | Step 3 (leg 0 / 1) |
|---|---|---|
| `nnSame` vs random `nnChance` | 0.073 vs 0.102; 0.038 vs 0.061 | **0.639 vs 0.146; 0.740 vs 0.085** |
| Same-species fish within 3 blocks | 0.06 / 0.02 | 3.93 / 3.33 |
| Mean fish alive while stationary | 95.6 / 108.4 | 77.2 / 104.1 |
| Stationary fish in view | 17.28 / 14.71 | 6.02 / 12.86 |
| Fish in view while swimming | 14.53 / 16.15 | 17.52 / 13.74 |
| Pops seen while swimming | 0 / 0 | 0 / 0 |
| Tick ms | 0.89 / 0.80 | 0.73 / 0.75 |
| Allocation KB/tick | 582 / 584 | 529 / 536 |
| Render submit ms/frame | 0.17 / 0.18 | 0.11 / 0.09 |
| Rate p99 relative to mean speed | 1.14 / 1.13 | 0.93 / 1.03 |

Reading:
- Fish now sit with their own species about 6–9× more often than a random mix would give.
- Cost went down slightly, because there are fewer slots and fewer separate placements.
- The leg-0 stationary view is the expected price of clumping. The fish in view arrive as a few
  groups, so a view can hold a school or nothing.
- Leg 0 also holds fewer fish (77 vs 96). A group only spawns whole, so near the cap a 12-fish
  school waits for room. Check this in the owner's look.

Regression scenes (`ambientwater`, `ambientisland`, `ambientfloor`, `ambientwalk`): 67/67 pass
(`build/ambientschools-step3d.log`); leash retirements are 10, against 1 before grouping.
- `ambientwater.carry.anchored` failed on the committed build too (a4321590, 5.56 blocks). The cause
  was the check, not a teleport: a fish that faded after the snapshot re-entered under the same ID
  elsewhere. The check now skips any fish whose birth tick changed.

### 11.4 Thin-water retune (owner look 2026-11-03)

The owner found the water thinner: a stationary view showed about 6 fish where it had shown 17, and
asked to split the difference. Two causes were measured (`groupDropped` and `heldAtCap` on the
`MOTION` line):
- A whole group spawned only if it fit under the cap exactly, so the population sat about 15%
  under it. Groups were held at the cap 98 times in leg 0 and 953 times in leg 1.
- Leg 0 had too few groups nearby: 106 of 144 alive with nothing held at the cap.

Members failing to place was not a cause (0–9 dropped).

Changes:
- A group may spawn while the population is under the cap, overshooting by at most one group;
  the hard ceiling still holds.
- `AmbientBudget.MAX_INDIVIDUALS` 224 → 288 (density 50: 112 → 144).
- `AmbientGroups.SLOT_DIVISOR` 3 → 2.

| Metric (leg 0 / 1) | Before grouping (§11.1) | Step 3 (§11.3) | Retuned |
|---|---|---|---|
| Stationary fish in view | 17.28 / 14.71 | 6.02 / 12.86 | 8.22–8.59 / 11.99–18.47 |
| Fish in view while swimming | 14.53 / 16.15 | 17.52 / 13.74 | 24.8–25.8 / 20.0–21.7 |
| Mean fish alive while stationary | 95.6 / 108.4 | 77.2 / 104.1 | 96–107 / 136–139 |
| `nnSame` | 0.073 / 0.038 | 0.639 / 0.740 | 0.62–0.64 / 0.67–0.73 |
| Tick ms | 0.89 / 0.80 | 0.73 / 0.75 | 0.78–0.91 / 0.81–0.83 |
| Allocation KB/tick | 582 / 584 | 529 / 536 | 615–621 / 631–644 |
| Render submit ms/frame | 0.17 / 0.18 | 0.11 / 0.09 | 0.22–0.26 / 0.20–0.23 |

Ranges span two runs (`build/ambientschools-step3-div2.log`, `build/ambientschools-step3f.log`).
- The stationary view averages about 10–13 across the two legs, about the midpoint asked for.
- Tick time is back at the pre-grouping level.
- Render submit is up about 0.05 ms a frame for the extra fish.

Regression scenes 67/67 (`build/ambientschools-step3g.log`). Two fixture expectations changed with
the design:
- `irregular` now expects 2 fish, not 3: its small water holds one or two groups.
- `bandwatch.kept` counts an open-face exit as turnover. Faster fish reach the horizon inside the
  40-tick window; the column count still guards the hazard it watches for.
