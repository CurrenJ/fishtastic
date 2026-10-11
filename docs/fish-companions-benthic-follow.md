# Fish companions: benthics on the ground (design spec)

Status: spec settled 2026-10-11; Phase 1 built 2026-10-12 (§10), awaiting the owner's viewer look. Builds on the companion sim (docs/fish-companions-sim-plan.md, Phases 1–3).
Owner calls are marked **(owner)**; §9 records the answers to the open questions (all settled 2026-10-11).

## 1. The idea

Today a BENTHIC companion on land is always perched (plan §9.4); its only ground contact is the
hop to the hand. This changes the default: **on land a benthic lives on the ground**, follows its
owner on foot with the same quality of decisions as a free swimmer, and climbs onto the owner only
when the owner has been idle a long time. Walking-pace travel brings it back down.

Benthic species (from `floor_sit` / `upright_sit` profiles):

| Species | Mean size | Temperament | Group (proposed) |
|---|---|---|---|
| starfish | 40 cm | docile | line of 3 |
| trapania_scurra | 12 cm | docile | crowd of 3 |
| willans_chromodoris | 14.5 cm | docile | crowd of 3 |
| common_octopus | 45 cm | wily | solo |
| japanese_spider_crab | 60 cm | wily | solo |
| plaice | 35 cm | wily | solo, flat ripple-glide gait (§4) |

## 2. Groups **(owner: per-species data field)**

`fish_profile` gains an optional `companion_group`:

```json
"companion_group": { "count": 3, "formation": "line" }   // line | crowd
```

- Absent → today's rule (FREE_SWIM under 20 cm → trio, otherwise solo). For BENTHIC the field
  replaces the size rule.
- `count` ≤ 3 for BENTHIC, because there are three perch slots (left shoulder, right shoulder, head).
  The codec rejects more.
- One leader (a full `CompanionAgent` with the mind) plus followers, as for trios
  (`CompanionGroup`). Followers walk the same ground body; they don't get minds of their own.
- Perch slots are given out leader first. A solo benthic keeps its seeded slot, the head included,
  whatever its size: a 60 cm spider crab on the head is wanted **(owner)**.

## 3. Land modes

```
            owner idle, rest settled                 walking travel past threshold
 GROUND_IDLE ───────────────────────► CLIMB ─► PERCHED ─────────────────────────► DISMOUNT
     ▲  │ owner sets off                                 jolt (hurt, hard landing) ─► TUMBLE
     │  ▼                                                                               │
 GROUND_FOLLOW ◄────────────────────────────────────────────────────────────────────────┘
     │ lag past catch-up distance
     ▼
 CATCH_UP (scurry bursts) ──► past fade distance: fade out, fade in on PERCHED
```

Water (chest wet → SWIM) and the hand show-off stay as built and can interrupt any land mode.

- **GROUND_IDLE.** Owner still or pottering. The group mills around the owner's feet, within
  about 1–3 blocks, and inspects things (§5). It never parks under the owner's feet.
- **GROUND_FOLLOW.** Owner travelling. The formation follows the trail (§4).
- **CLIMB.** Rest settled (the existing rest drive crossing 0.5 with the owner still, about 30 s+
  of idle) → each member hops to its slot with the existing ballistic hop. They go in turn, not all
  on one tick.
- **PERCHED.** As built: the parrot attachment, shoulder swaps (not while settled), cover on the
  head from cats.
- **DISMOUNT.** A deliberate hop down, behind and to the side of the owner. Triggered by walking
  travel past the threshold (§6).
- **TUMBLE.** Only on a jolt: the owner is hurt, or lands a fall of 3+ blocks. A physical fall
  off the shoulder, then a landing squash and a yaw turn to face its new heading; no flip-over **(owner)**.
  **(owner: hop down; tumble only on jolts)**
- **CATCH_UP.** See §6.

## 4. Formations and the ground body

**Ground body (new `GroundBody`, a `Motor`).** It is heading-first like `Body`: tail-first motion
stays at 0, and it turns under a yaw cap. On top of that:
- A 2D walk on a floor height sampled from `LocalVoxels` (the column's top collision face).
  Feet are on the floor except during hops.
- A stop-and-turn dwell cycle and burst-and-coast speed, from the tank BENTHIC walk constants
  (`Tunables`). Speed variety reads as alive; keep it smooth.
- Steps: 0.5-block rises are walked up (slabs, stairs); 1-block rises are a small hop (the existing
  hop with its block-clearance check); drops of 3 blocks or less are walked off, with a landing squash.
- Gait speed caps by size, so small slugs don't skate. The gap they open is the lag that §6
  handles.
- **Plaice gait (owner).** A flatfish doesn't walk: the plaice glides flat on the floor, with a
  body-length ripple running nose to tail that drives it forward (`shapeDrive` as the ripple phase,
  faster ripple = faster glide). Same body, its own gait profile; no stop-and-turn crouch.

**Foot trail.** A second `Breadcrumbs` at foot height, projected to the floor. A member walks to the
newest crumb it can reach, which is "the way you went", as for swimmers.

**Line (starfish).** The leader follows the owner's foot trail. Each follower follows the trail of the
member ahead at about 1.2 body lengths. **Waviness is not imposed.** It comes from the leader's
meander (its wander noise, curiosity detours) travelling down the line. "Sometimes wavy" = the
leader's meander amplitude rises when the owner walks slowly and calmly, and falls when they walk briskly.

**Crowd (slugs).** Floor boids with separation, plus cohesion to a centre that trails the owner by
about 1.5–2.5 blocks. **No alignment term**, so it reads as a crowd, not a school. Members spread out
when stopped and string out when catching up.

**Squash and stretch ("breathe").** `FishAnimator.applyBenthic` → `applyFloorSit` already takes
a `shapeDrive`. In game it gets the gait phase (contract on push-off, stretch on glide) instead
of the tank's burst drive. Amplitude grows with speed; there's a slow breath when idle. Perched
riders keep the idle breath. Each member's phase is offset, so a line ripples instead of pulsing in
unison.

## 5. Desires on the ground (on par with FREE_SWIM)

These use the existing `Mind` and `Drives`; the goals are expressed through the ground body:

| Goal | On the ground |
|---|---|
| INSPECT | Detour to dropped items and other companions; the line's leader peels off and the rest follow it, then they hurry back into place |
| HIDE | Hostile → huddle on the owner's far side at their feet; cat → climb to the head (as built) |
| GUARD (aggressive) | Square up between the owner and the hostile, on the ground |
| NUZZLE (docile) | Brush past the owner's ankles while idle |
| SHOW_OFF | Hand hop as built (needs the owner looking down) |
| SETTLE | Slower milling and longer dwells at the feet, then CLIMB |

**New input: stepped on.** When **any player** walks into the formation (the owner walking
backward or turning, or a passer-by) **(owner)**, members within about 0.4 blocks of that player's feet
scatter (crowd) or the line splits around the feet and re-forms. The owner's feet come from
`OwnerState`; other players need a new input in `CompanionWorld`, nearby players' foot position
and velocity (a new `PointOfInterest` kind, `PLAYER`, within about 4 blocks; the adapter fills it from
the client's player list).

**In-view beats** (the owner chose to keep a loose "seen" guarantee):
- **Overtake.** Now and then (the sprinter temperament often, others rarely) the leader scurries
  past the owner on the ground to the edge of the view, then drops back. The line follows.
- **Roadside stop.** A curiosity detour that ends beside the path ahead, so the owner walks past
  it.
- **Turn reveal.** On a sharp owner turn the formation cuts the corner, not the trail, so it
  sweeps through the edge of the view.

Target: "seen" while walking a straight line in first person should be at least the perched
baseline (measured, not guessed: OwnerSpaceProbe on the walking scripts).

## 6. Travel thresholds and catch-up **(owner: lag, then catch up)**

**Dismount (PERCHED → ground).** Net horizontal distance of 6 blocks in a 4 s window, at walking
pace (not sprinting, not riding, not gliding), and at least 10 s since the climb. Mining, building,
pacing at a chest and turning on the spot never trigger it. This is net distance, not speed.

**Lag and catch-up (on the ground).**
- Lag beyond about 4 blocks of trail → CATCH_UP: scurry bursts at up to 1.6× gait speed, visible
  and lively (speed variety), still heading-first.
- Lag beyond about 16 blocks of trail, or no reachable crumb for 3 s (ladder, gap, closed door) →
  fade out where it is and **fade in on the perch**. A fade in on the ground behind a fast-moving
  owner would just lag again, so it would fade over and over.
- After a perch fade-in, the normal dismount threshold brings it back down once the owner walks.

**Riding and elytra exception (owner).** On an animal or vehicle, or gliding, the lag window is
gone almost at once, so there is no chase. Members within hop range climb on as the owner gets on;
the rest fade straight onto the perch. They stay on until the owner is off the mount or landed, and
then the normal dismount threshold applies. `OwnerState` needs `riding` and `gliding` flags (adapter:
`isPassenger()`, `isFallFlying()`).

## 7. Verification

Headless first, the same loop as Phases 1–2.

- **New scripts:** walk_flat, walk_meander, stairs_up_down, slab_path, step_gaps (1-block rises),
  parkour_gap, ladder, closed_door, mine_in_place, pace_chest, walk_backward_into_line,
  sprint_away, ride_off, elytra_glide, passerby_walks_through, long_idle_then_walk,
  hurt_while_perched, fall_landing, plaice_walk_flat.
- **Invariants:**
  - feet on the floor ±ε outside hops
  - tail-first = 0
  - no block penetration
  - line order kept (no member passes the one ahead, except a deliberate overtake by the leader)
  - foot-overlap ticks ≤ 0.5%
  - no getting on and off: at most 1 climb/dismount pair per mine_in_place / pace_chest run
  - stuck (no progress, not idle) ≤ 3 s
  - fade count per minute bounded (none in walk_flat/stairs)
  - hop accel/jerk bounds as built
- **Measured, not bound:** seen % on the walking scripts against the perched baseline; line wave
  amplitude; crowd spread.
- **Fingerprints:** FREE_SWIM, GLIDE, DRIFT and trio fingerprints stay bitwise unchanged.
  BENTHIC changes on purpose. ANCHORED stays unchanged.
- **Owner gate:** drive it in `runCompanionViewer` with a terrain scene (needs floor rendering and
  a ground-level owner) before it goes into the game. Then the solo playtest, with debug triggers:
  `/fishtastic companion benthic ground|perch|tumble`, a forced group count/formation, and the
  overlay showing mode, lag and trail state.

## 8. Phases

1. **Ground body + foot trail, solo.** GROUND_IDLE / GROUND_FOLLOW / CATCH_UP / fade to perch;
   the riding/elytra exception; terrain scripts; viewer terrain. Plaice ripple-glide gait. Gate:
   crab, octopus and plaice following in the viewer.
2. **Perch transitions.** CLIMB on settle, DISMOUNT threshold, TUMBLE. Gate: mine_in_place shows
   no getting on and off; owner look at tumble.
3. **Formations.** `companion_group` field + codec, line and crowd, perch slot allocation, phase-offset
   breathe. Gate: owner look at line wave and crowd in the viewer.
4. **Desires and in-view beats.** Ground INSPECT/HIDE/GUARD/NUZZLE, stepped-on scatter (any
   player: `PLAYER` points of interest),
   overtake / roadside / turn reveal. Gate: seen % against baseline + owner look.
5. **In game.** Adapter (floor sampling cost against the 25 samples/tick budget, ×3 members), animator
   gait → shapeDrive, perch layer for 3 riders, debug commands. Gate: solo playtest.

## 9. Owner answers (2026-10-11)

- **Q1. Riding and elytra exception:** yes, climb on or fade onto the perch at once (§6).
- **Q2. Plaice:** a flat ripple-glide on the ground (§4).
- **Q3. Large solos on the head:** fine as is: "just right".
- **Q4. Stepped-on scatter:** for any player, not just the owner (§5).
- **Q5. Tumble art:** a landing squash and a turn is enough; no flip-over (§3).


## 10. Phase 1 as built (2026-10-12, uncommitted)

**Scope.** Spec §8 step 1: one benthic on the ground — the ground body, the foot trail, the land
modes with catch-up and the fade onto the perch, the riding and gliding exception, the plaice's
ripple-glide, terrain scripts and scenes, invariants, and a viewer the owner can drive. Perch
transitions (CLIMB on settle, DISMOUNT, TUMBLE) are Phase 2; formations Phase 3; desires and the
stepped-on scatter Phase 4; the game Phase 5.

### 10.1 What's built

All in `fishsim` (`grill24.fishsim.companion`); `common/` untouched.

- **`GroundBody`** (a `Motor`): heading-first like `Body` — yaw turns at ≤ 6.5°/tick with a turn-
  accel cap and a 9 b/s² sideways cap, forward speed under accel 9 / decel 11 b/s² and jerk 90 b/s³,
  slowing to turn when the way it wants is off to the side. It never moves where it isn't facing.
  - **Floor.** Its height is the highest surface under its footprint (the ≤ 4 columns under the
    square of its radius), from `BlockQuery.top` (new: a slab 0.5, a carpet 1/16; default a full
    block, so every existing scene and the game adapter are unchanged). A footprint column that's
    a wall or too deep a drop refuses the move before it's made.
  - **Steps.** Rises up to 0.13 (a carpet) are walked, up to 1 block hopped, more is a wall.
    Risers are planned for: on the move (speed > 0.8) it bounds up a step without stopping when an
    arc from where it would leave is clear and lands with room to run on; otherwise it brakes to a
    standoff (0.12 + 0.2 × rise) short of the riser, faces it, crouches (4 ticks × size pace; the
    plaice 2) and hops. Landed too close for an arc (after a fall, say), a **climb hop** goes
    straight up and then over under ≤ 10 b/s² of air control; it's planned by simulating the same
    controller it flies with.
  - **Edges.** Drops up to 3 blocks are walked off and fallen, but only toward somewhere lower
    (the walk target's floor is ≥ 0.25 below): wandering along a raised walk it keeps to it. It
    won't walk off an edge whose fall lands within reach of its owner's feet, and brakes for it.
  - **Flight** (hops and falls) integrates exactly under gravity (midpoint velocity), so it flies
    the arc that was checked clear — the plain Euler step ran ~0.14 blocks low by a riser and clipped
    edges it had been checked to clear (traced on the terraces).
  - **Rhythm**, from the tank crawl: AMBLE (idle scuttle-and-pause, 3.5 s × 40%), TROT (follow:
    0.7–1.15 × cruise every 0.9 s), SCURRY (catch-up: 1.1–1.6 × cruise every 0.7 s). Cruise is
    3.6 b/s × √(length / 0.3) × the temperament's pace; a slow temperament still scurries at full
    pace. `shape` is the crawl's push-off envelope (the walk's breath, for Phase 5's animator);
    `squash` the crouch and landing.
  - **`GroundGait.RIPPLE`** (the plaice): 0.85 × the cruise, a smoother and slower rhythm (idle
    6 s × 30%, trot 0.85–1.05 every 1.6 s), a 2-tick flick instead of a crouch, and a ripple whose
    frequency follows its speed in `tailPhase`.
- **`GroundMind`**: the land modes **GROUND_IDLE / GROUND_FOLLOW / CATCH_UP**.
  - **Foot trail.** A second `Breadcrumbs` of the owner's feet projected onto the floor — only
    where they set foot (not in the air over a gap, which made the lag jump 12 blocks mid-jump).
    It walks straight to its owner when it can (within 8 blocks), otherwise to the newest crumb it
    can walk to; the walk check samples every 0.1 blocks (coarser steps passed over pillar corners
    the body can't).
  - **Lag** is the walk along that way (judged only with its feet down: mid-fall its floor is the
    one it left). Catch-up past 4 blocks (aiming well past its follow spot, so arrival braking
    doesn't hold it at the owner's pace), back below 2.5 after at least 20 ticks.
  - **Fade onto the perch** (spec §6) past 16 blocks of lag, or after 3 s **stranded**: no crumb
    it can walk to, or standing on the newest one with the owner further on. Stranded at a gap,
    it first tries a **gap hop** to a crumb across within hop range.
  - **Idle** pottering alternates spots at the edge of the owner's view (2.9–3.9 blocks out,
    6–24° off centre — the floor only shows in a level 70° view beyond ~2.7 blocks) with spots
    beside or behind them, within a hop of their level; dwells 1.5–5 s (twice that resting),
    then settles facing them, roughly.
  - **Out from underfoot**: pushed away from where the owner's feet are and will be (0.35 s and
    0.8 s ahead), sideways off their line when they walk at it.
- **`PerchBody`** gains modes **GROUND** and **GROUND_CROUCH**; a benthic with a gait lives on the
  ground (`homeGround`). From the ground it hops up to the hand to show itself (unseen for 60% of
  the usual attention time — down there it's out of sight more), to cover (as built: far shoulder,
  head from a cat), and onto its home slot when the owner rides or glides. A hop up starts only
  from where an arc is clear (not from behind the owner, through them); out of range it first
  comes up alongside on that side. Beside a moving owner it **leaps on the run** (no stop) once
  facing the slot, and only while the owner's ground ahead is level. Off the hand (or out of cover)
  it hops down to a spot beside the owner chosen at launch. In water it hops in or just swims, and
  comes out onto the ground.
- **Riding and gliding** (owner Q1): `OwnerState` gains `riding` and `gliding` (the old
  constructor defaults both false). It climbs onto its home slot if it can launch within 1 s,
  otherwise fades onto the perch.
- **The fade** for a benthic always reappears on the perch at once (never hidden). **Phase 1
  limit:** there it stays — CLIMB/DISMOUNT are Phase 2 — so after any fade (a teleport, a ladder, a
  ride) the run continues perched. `G` in the viewer (`CompanionAgent.devGround`) sends it back
  down by hand, the Phase 2 dismount's stand-in.
- **The game's path is unchanged.** `CompanionAgent`'s existing constructors (which `common/`
  uses) keep the Phase 3 percher; the ground needs the new `GroundGait` constructor, which the
  harness and viewer use. `FingerprintProbe -PfpAll` hashes that path as "PERCHER": bitwise
  identical to the Phase 3 BENTHIC on all 69 runs.
- **Harness.** Scenes `half_stairs`, `slab_path` (with a carpet strip), `terraces`, `trench`,
  `cliff`, `door_wall` (a door that shuts once nothing is in the doorway), `benthic_park` (all of
  them side by side, to drive round). `OwnerScript.GROUND_NAMES`: walk_flat, walk_meander,
  stairs_up_down, slab_path, step_gaps, parkour_gap, ladder, closed_door, sprint_away, ride_off,
  elytra_glide, plaice_walk_flat — kept apart from `NAMES`, so the swimmers' table and fingerprints
  don't run them. The pilot reads slab heights (`top`); the `elytra` script now reports gliding
  (zero-time wrappers, no timeline shift). `CompanionRun.groundMetrics()` (floor error, stuck,
  fades, impulses, lag, catch-up share); `Sample` gains impulse, ground, lag, door state.
- **Viewer.** Classes BENTHIC (crab), BENTHIC plaice, BENTHIC slug (0.12), BENTHIC spider crab
  (0.6); the terrain scenes and ground scripts in the dropdowns; keys J jump, M mount, E glide,
  G back to the ground. Partial blocks drawn at their height, the owner drawn in third person (F),
  the foot trail on the map, and the ground phase, lag and fades on the overlay.
- **Probes.** `BenthicGroundProbe` (-PsweepClass=CRAWL|RIPPLE, -PsweepLength, -PsweepSeeds,
  -Psweep=all, -PsweepTemperament; -Ptrace=script with -PtracePos=seed, -PsweepTicks=from or −1
  for acceleration/jerk spikes only). `FingerprintProbe` now covers DRIFT and the PERCHER path.

### 10.2 Invariants and results

`BenthicGroundInvariantTest` (75 runs × 2 min: the crab on all 12 ground scripts, the plaice on 6,
a slug-sized crawler on 4, a spider-crab-sized one on 3, × 3 seeds), all passing: never inside a
block; feet on the floor (±0.01) whenever not in the air — 0 ticks off; contacts ≤ 8/min and
≤ 4 b/s; turn ≤ 7°/tick, accel within the hop bound with launches, touchdowns and carpet steps
counted apart as impulses (≤ 16 b/s; measured ≤ ~14, the running leap to a hand); jerk ≤ 800;
tail-first ≤ 0.5%, facing error < 1°; stuck (far, not moving) ≤ 3.5 s; no fade where it can follow
(walks, stairs, terraces, sprint for the crab and spider sizes); must fade at the ladder; on a mount
or elytra never left walking more than 40 ticks; underfoot and near-clip ≤ 1%; seen ≥ 5% with no
unseen stretch over 30 s; goals committed ≥ 20 ticks.

`CompanionInvariantTest` (the swimmers' table, which runs BENTHIC on its 23 scripts × 3 seeds and
10 temperaments against the baseline's 40-seed envelope) passes with the benthic on the ground — no
named gaps added there. The rest of `:fishsim:test` (tank goldens included) passes; `build -x test`
passes for common, Fabric and NeoForge.

**Fingerprints** (`FingerprintProbe -PfpAll`, 447 + 69 lines): FREE_SWIM (spike and table),
GLIDE, DRIFT, ANCHORED and TRIO bitwise unchanged; BENTHIC changed on all 69, as meant; the
no-gait PERCHER path equal to the old BENTHIC on all 69.

**The crab over 8 seeds** (ground scripts): no fade on walk_flat, walk_meander, stairs_up_down,
step_gaps, sprint_away, parkour_gap (it crosses the trench by gap hop), plaice_walk_flat; slab_path 1
in 8, closed_door 0 in 8 on the final build (2 in 8 earlier in development: shut on the far side), ladder every run (stranded at the foot, as meant),
ride_off / elytra_glide every run (climbed on or faded, plus the scripts' teleport home). Mean lag
following 2.5–4.9 blocks (walk_flat 3.3, max 6.4); catch-up 11% of ground time on a meander, ~55%
on terrain and a sprint. Cost about 3.5 µs a tick headless.

### 10.3 Named gaps

- **Spider-crab size (0.6) on stepped ground.** Half stairs fade in 6 of 8 seeds, terraces in 4,
  with one contact up to 8.9 b/s: a big body bounding up 1-deep treads falls behind past 16 blocks.
  The test asserts it on the level only. Worth fixing before Phase 5 (Japanese spider crab).
- **Slug size (0.12) on terraces:** one late riser contact up to 4.5 b/s in 1 of 8; bound 5 there.
- **Routing is greedy** (straight lines and the owner's crumbs, no pathfinding): wherever the
  way back needs a detour it fades after 3 s (the spec's fallback) — slab_path 1 in 8.
- **Phase 1 stays perched after any fade** (§10.1); the viewer's `G` is the stand-in.
- **The full-block `stairwell`** in the swimmers' table (a tread a block high, not Minecraft stairs —
  `stairs_up_down` covers those) falls behind and faded on 1 seed in 8 during development; it
  passes on the table's 3 seeds.

### 10.4 For the owner's viewer look (`./gradlew :fishsim:runCompanionViewer`)

Pick **BENTHIC** (or plaice / slug / spider crab), scene **benthic_park**, owner **manual**:

1. The follow on a walk: about 3 blocks behind, trotting with a surge, then bursts of scurry to
   catch up. Lively enough, too busy, or should it keep closer?
2. Half stairs and terraces: it bounds up steps on the run and stops to crouch only when it must.
   Does a crab hopping every half-step read right (on real stairs it will)?
3. Idle at your feet: spots ahead in view alternate with spots beside or behind you.
4. Every so often (unseen ~4–12 s) it comes up alongside and leaps onto your hand, sits a couple
   of seconds and hops back down. Too often now that it's on the ground?
5. The plaice's glide versus the crab's scuttle.
6. Walk backwards into it (S): it steps out of your way rather than being walked over.
7. Mount (M) or glide (E): it climbs on, or fades onto its perch, within a second. Then G to send
   it back down (Phase 2 will do that itself).
8. The trench (J to jump it at a run): it hops across after you.
