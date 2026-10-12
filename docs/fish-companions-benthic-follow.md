# Fish companions: benthics on the ground (design spec)

Status: Phases 1–2 verified, large-crab terrain gap repaired (§13); Phase 3 group foundation built (§15). Owner authorized proceeding through Phases 3–5 on 2026-10-12 with visual approval pending. Full implementation is not complete. Builds on the companion sim (docs/fish-companions-sim-plan.md, Phases 1–3).
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

## 11. Phase 2 — original handoff (superseded by §12)

Paused at the coordinator's request (usage). Nothing committed or staged. Baseline for this phase is 95a320fd.

**Fingerprint baseline:** recorded before any Phase 2 edit with `FingerprintProbe -PsimStdout -PfpAll=1`, saved to
`%TEMP%/fp_base_p2.txt` (516 lines: 33 FP + 69 each of FREE_SWIM, GLIDE, BENTHIC, ANCHORED, DRIFT, PERCHER, TRIO).
If %TEMP% has been cleaned, re-record it from a 95a320fd checkout (git stash or a worktree), not from this tree.

**Done (edited, NOT yet compiled):**
- `OwnerState`: new last component `double landedFall` (the height of the fall the owner lands from this tick, else 0). The
  11- and 9-arg constructors delegate with 0, so `common/` callers are unchanged. Phase 5's adapter fills it from the player's fall distance.
- `OwnerScript`: the Pilot tracks the fall (`airborne`/`fallTop`, gravity falls in terrain mode only, so ladders, flight and
  swimming never count) and reports `landed`; a scripted hurt (`Pilot.hurt()`/`takeHurt()`, `hurtOnce()` segment) that
  `nextWorld` passes through as `CompanionWorld.hurt` (scripts without one still use `CompanionWorld.of`, so they're unchanged).
  The new `GROUND_NAMES` are mine_in_place, pace_chest (both start with a 45 s stand so it climbs on first),
  long_idle_then_walk, hurt_while_perched and fall_landing. fall_landing runs on the `cliff` scene: the owner starts on top at
  z=8.5 and walks 5 blocks off the edge at z=6. That is under the 6-block dismount threshold, so it is still perched when the
  owner lands the 5-block fall.
- `GroundBody.placeTumbling(p, v, yaw)`: falls keeping its facing (no flip, no turn to the fall).
- `GroundMind`: `daze` (ticks it stands still after a tumble landing, counted only while grounded) and `closeCalm` (an
  AMBLE approach at 0.8× cruise for the climb, instead of a scurry).

**Not started — the PerchBody / CompanionAgent core (the plan, worked out against the code):**
1. `PerchBody` fields: `climbing`, `dismounting`, `perchedTicks` (ticks home on the perch: reset on a climb landing, on
   `reperch`, and on the riding switch in `wantedSlot`), a travel ring of owner feet (80 ticks), `sinceSprint`,
   `sinceMounted`, `tumbleCooldown` (200), `tumbleTicks`/`tumbling`, counters `climbs`/`dismounts`/`tumbles`, and `devClimb`/`devTumble`.
   Gate ALL of it on `ground != null` so the PERCHER (no-gait) path stays bitwise identical.
2. CLIMB, in `groundStep`'s `ready` block: `climbWant = (drives.resting || devClimb) && owner horizontal speed < 0.3 && not
   riding or gliding`. Priority: cover > hand (look-down, held item, unseen) > climb to `home`. If the slot is in range and
   the arc is clear, set `climbing = true` and call `beginGroundHop(home)`. Otherwise `gmind.wantClose` with `closeCalm = true`.
   On landing in `fly()`, if `climbing`, set `homeGround = false`, `climbs++` and `perchedTicks = 0`. Clear `climbing` in
   `abortGroundHop`, `enterGroundFalling` and `startSwim`.
3. DISMOUNT, in `wantedSlot` after the riding check: if `ground != null && !homeGround && perchedTicks >= 200 &&` net feet
   travel over the 80-tick ring is at least 6 `&& sinceSprint >= 20 && sinceMounted >= 80 && !inWater`, then set
   `homeGround = true` and `dismounting = true`; the existing `hopDown` path takes it from there. Give `groundSpot` a
   behind-and-to-the-side-first option order for a dismount. Count `dismounts` in `enterGroundFalling` when `dismounting`.
4. TUMBLE, at the top of `step()`: `ground != null && attached() && tumbleCooldown == 0 && !riding/gliding &&
   (drives.sinceHurt == 0 || o.landedFall() >= 3 || devTumble)`.
   - Call `attach(o)` first. The velocity is the owner's horizontal velocity, plus 2 b/s outward (the slot's side; the head
     and back fall backward), plus 2 b/s up.
   - Measure the impulse against the owner's velocity with y zeroed: at a fall landing the owner's y velocity is about
     −18, which would blow the 16 b/s impulse bound.
   - Then `ground.placeTumbling`, `gmind.daze = 10`, `groundHopCooldown = 40`, `homeGround = true`, `tumbles++`, and enter
     GROUND.
   - `onOwner()` stays true for the first 3 tumble ticks.
   - The cooldown stops a hostile's once-a-second hits from bouncing it back off its cover shoulder each time.
5. `CompanionAgent`: `climbs()`, `dismounts()`, `tumbles()`, `tumbling()`, and `landMode()` (TUMBLE / CLIMB / DISMOUNT /
   PERCHED / SWIM / HOP / a ground land mode), plus `devPerch()` and `devTumble()`. `devGround` already exists.
6. Harness:
   - Add `tumbling` and `land` to `CompanionRun.Sample` (`CompanionRun` is the only place it's constructed).
   - Exempt tumble-air ticks from the tail-first and facing checks in `metrics()`: it falls sideways while facing ahead,
     by design.
   - Add climbs, dismounts, tumbles and the first-climb tick to `GroundMetrics`.
7. Tests, in `BenthicGroundInvariantTest`:
   - mine_in_place and pace_chest: at most 1 climb and 1 dismount.
   - long_idle_then_walk: a climb, then a dismount after the walk starts.
   - hurt_while_perched and fall_landing: a tumble.
   - Tumble acceleration within the hop bound.
   - Add a spider config on stepped ground once item 8 is fixed.
8. Spider-crab carry-over: not yet traced. Start with `BenthicGroundProbe -PsweepLength=0.6 -PsweepSeeds=8` on
   stairs_up_down and step_gaps, then `-Ptrace=stairs_up_down -PtracePos=<fading seed>`. Suspects, untested: `pace` caps the
   size at 0.8; the standoff and crouch time scale with size; on 1-deep treads the 0.6 footprint spans two steps.
9. Viewer keys (tumble/hurt, climb), a land-mode overlay line, and the §11 as-built write-up.

**Compiles / tests right now:** unknown, and probably not. Nothing has been compiled since these edits; `OwnerState` and
`OwnerScript` changed signatures, and nothing that reads the new fields exists yet. First step next time: `./gradlew :fishsim:compileJava`.

**Traced findings so far:** none (no runs made this phase beyond the baseline).

## 12. Phase 2 as built (2026-10-12, uncommitted)

**Scope.** Solo perch transitions in `fishsim`; the game adapter remains on its existing no-gait
percher path. Formations, ground desires and game integration remain Phases 3–5. The owner viewer
gate is still open; no visual approval is inferred from the headless checks.

### 12.1 Behavior

- **CLIMB:** sustained rest with the owner stationary calls the companion onto its seeded home
  slot. Cover and hand attention retain priority. It approaches calmly when out of range, crouches,
  then uses the checked ballistic arc. A successful landing ends its ground visit and starts the
  ten-second perch dwell clock; aborted hops do not count as climbs.
- **DISMOUNT:** after at least ten seconds perched, net horizontal owner travel of six blocks over
  the last eighty ticks calls it down. Sprinting suppresses it until twenty ticks after the sprint;
  riding/gliding suppress it until eighty ticks after returning to foot travel. The landing choices
  prefer behind and beside the owner. Mining and pacing between nearby chests leave it perched.
- **TUMBLE:** owner hurt or a landed fall of at least three blocks knocks an attached companion
  outward at 2 blocks/s and upward at 2 blocks/s, carrying the owner's horizontal velocity. It
  keeps its facing in flight, lands with the ground body's squash and pauses ten grounded ticks
  before resuming. The first three ticks count as leaving the owner. A 200-tick cooldown protects
  a regained perch from repeated hits; riding, gliding and water suppress land tumbles.
- **Inputs and diagnostics:** `OwnerState.landedFall` defaults to zero for existing callers. The
  terrain pilot measures gravity falls and supports scripted hurt. The harness records tumble
  flight and land mode, and reports climbs, dismounts, tumbles and first climb attempt tick.
  Tumble flight is exempt from heading-first checks; collision, acceleration and impulse checks
  still apply. `CompanionAgent` exposes the same counters and `devPerch` / `devTumble` triggers.
- **Viewer:** P requests a climb, B requests a tumble, K hurts the owner and G returns to ground.
  The overlay shows land mode and transition counters. Use `benthic_park` with manual driving,
  or the new `mine_in_place`, `pace_chest`, `long_idle_then_walk`, `hurt_while_perched` and
  `fall_landing` scripts. A tumble request waits until attached and out of cooldown.

### 12.2 Verification

- `:fishsim:test` and both platform builds passed together: **329 tests, 312 passed, 17 skipped,
  zero failures**. Measurement probes are skipped in this normal suite. Existing compiler
  deprecation/unchecked warnings remain.
- The ground invariant matrix now includes the five transition scripts. Explicit checks require
  one climb and no dismount on mining/chest pacing, a climb and dismount on the long idle/walk,
  and tumbles on hurt and hard landing. A separate check covers short-fall rejection, rider
  protection, repeated hits during cooldown and a new tumble after cooldown expires.
- Regenerated the missing baseline from detached commit `95a320fd` in an isolated checkout.
  `FingerprintProbe -PsimStdout -PfpAll=1`: **all 447 protected fingerprints identical** (FREE_SWIM,
  GLIDE, DRIFT, ANCHORED, PERCHER and TRIO); BENTHIC changed in 30 of 69 runs as expected.
  Baseline and current logs are `build/fp-base-p2.txt` and `build/p2-targeted.txt`.
- Final validation log: `build/p2-validation.txt`. `git diff --check` passes.

### 12.3 Remaining gate and terrain gap (historical; terrain repair in §13)

Run `./gradlew :fishsim:runCompanionViewer` for the owner's look at idle climbing, walking
dismounts and the tumble/landing. Do this before advancing to formations or game integration.

The Phase 1 spider-crab terrain gap remains open. The eight-seed 0.6-block crawl diagnostic
(`build/p2-spider.txt`) reproduces fades in 6/8 half-stair runs and 4/8 terrace runs. With automatic
dismounts, those runs can return to ground and fade again: up to three fades on stairs, four on
terraces; longest measured stall 6.6 seconds and worst terrace impact 8.87 blocks/s. Large crabs
remain asserted on level ground only. A trace shows short stair bounds repeatedly shedding
horizontal speed; trying wider bounds across adjacent treads did not reliably eliminate stalls
or impacts, so that experiment was removed. Next work should trace the stalled routing and
landing states as well as stair speed, rather than merely increasing gait speed. This gap still
needs resolution before Phase 5; the 3.5-second stuck and no-fade terrain guarantees are not
claimed for the large crab.

## 13. Terrain continuation (2026-10-12, uncommitted)

Continued the Phase 2 handoff without crossing its owner viewer gate. Formations, ground desires
and game integration remain Phases 3–5.

### 13.1 Repairs

- **Corner approaches:** ascending step hops now use the same highest-footprint support rule as
  walking. The old requirement for support under every footprint column rejected valid diagonal
  landings at stair corners; the crab stopped there while routing still reported a reachable trail,
  until lag forced a fade. The ballistic clearance check still validates the complete arc.
- **Hop heading:** a terrain hop waits until its heading is within 20 degrees of the chosen route.
  Previously a crab still turning after a hand-hop landing could launch toward a terrace's side,
  fall off and become stranded below the tall section. It brakes and turns before committing now.
- **Edge touchdowns:** flight uses a separate support query, independent of the three-block limit
  on deliberate walking drops. A deep neighbouring footprint column no longer masks the surface
  supporting the body. Surfaces above the previous underside do not count as touchdowns.
- **Perch hops down:** a descending hop hands control to the ground motor when it crosses actual
  support, including a higher tread before its chosen landing. Previously it hit that tread as an
  air collision (the reproduced 8.87 blocks/s impact), then fell from the collision state.
- **Regression coverage:** spider-crab runs now cover stairs and terraces as well as level walking,
  meandering and sprinting, with eight seeds. They use the existing collision, floor, motion, stuck,
  fade and visibility bounds without new exceptions. Traces print their target and continue from
  the requested start tick through the end of the run, to expose later-cycle stalls.

### 13.2 Measurements and remaining work

The eight-seed, two-minute 0.6-block crawler diagnostic (`build/benthic-spider-fix.txt`) now has
zero fades in all eight stair runs and all eight terrace runs, zero block penetrations and zero
floor-error ticks. Worst stuck time is 0.6 seconds on stairs and 2.2 seconds on terraces; worst
contact is 2.15 blocks/s on stairs and 0.44 on terraces. The named stair/terrace gap from §12.3 is
resolved for this matrix. The general greedy-routing limitation remains, including slab-path
fallbacks; this does not claim universal terrain pathfinding.

Final validation (`build/benthic-validation.txt`): `:fishsim:test :fabric:build :neoforge:build`
passed together, 329 tests with 312 passed, 17 diagnostic probes skipped and zero failures.
The expanded ground matrix runs 121 two-minute simulations (the spider configuration now uses
eight seeds). `FingerprintProbe -PsimStdout -PfpAll=1` (`build/benthic-fingerprints.txt`) matches
all 447 protected fingerprints against `build/fp-base-p2.txt`: FREE_SWIM, GLIDE, DRIFT, ANCHORED,
PERCHER and TRIO are bitwise unchanged. `git diff --check` passes.

The owner gate is still open. Run `./gradlew :fishsim:runCompanionViewer`, choose BENTHIC spider
crab in `benthic_park`, and review stairs/terraces plus P (climb), walking (dismount) and B/K
(tumble/hurt). The owner should approve the movement before advancing to Phase 3 formations.

## 14. Current-game perch rendering feedback (2026-10-12)

The owner's game playtest found an upside-down crab embedded in its shoulder, a vertical starfish
embedded in its shoulder, and head riders that did not follow head pitch. These are renderer bugs
in the existing game percher, independent of the ground simulation's pending integration.

The perch layer now converts the player's down-positive model frame to the animator's up-positive
frame, preserves floor-sitters' flat pose, and lifts benthic centres by `FishAnimator.floorPoseLift`
(including each upright species' measured foot pivot). Shoulder riders attach to the posed torso;
head riders attach to the actual head model part before the crown offset, inheriting head pitch,
yaw and pose changes. The anchored back pose retains its existing transform. Visual confirmation
in game is still required.

Automatic dismounts are still simulation-only: at least ten seconds perched, then six blocks of
net walking travel within four seconds. Standing still encourages climbing after sustained rest,
not dismounting. The current game constructors still select the no-gait percher; ground commands
and game adaptation remain Phase 5. This feedback does not close the terrain viewer gate.

## 15. Group foundation continuation (2026-10-12, uncommitted)

**Owner authorization:** "Proceed through Phases 3–5; keep visual approval pending." Continue the
full benthic-follow implementation; do not stop at the earlier viewer gate or infer visual approval.

### 15.1 Implemented

- `FishProfile.companionGroup`: optional `companion_group` codec with count 1–3 and `line`/`crowd`.
  Starfish declares three in a line; trapania_scurra and willans_chromodoris declare crowds of three.
  Other profiles are unchanged. Codec round-trip and rejection tests cover both formations, zero,
  negative and excessive counts, and unknown formation names.
- `BenthicGroup` supplies one full leader with shared mind/drives and follower motors. The follower
  agent references the leader's mind but never updates/thinks/resets it, nor independently updates
  shared drives. Each has its own ground/perch motor, phase offset, checked hops, fade fallback and
  interpolation. Water stations follow the swimming leader instead of aiming at the floor.
- Lines steer at the predecessor's newest foot trail point and regulate spacing with speed. An
  earlier implementation aimed at a short station behind it; arrival braking overshot that point
  and made followers circle back, accumulating lag. Sharp travel turns prepare follower headings
  together. Crowds use separation/cohesion without alignment. Initial followers spawn at distinct
  floor positions, with clearance checked before placement.
- Multi-member groups allocate left shoulder, right shoulder, head leader-first and suppress
  shoulder wandering. Solos retain their seeded slot. Settling climbs wait for the predecessor's
  successful landing, with an additional stagger clock; viewer P requests the same sequence.
- Formation routing avoids walking through the owner's feet and anticipates their path, giving
  small crawlers extra room to escape. This is currently formation-only. Ordinary solo routing
  remains the verified Phase 2 path. Urgent scurry skips the idle rhythm's speed-request delay,
  retaining the motor's acceleration, jerk and turn caps.
- Viewer class choices `BENTHIC starfish line` and `BENTHIC slug crowd` draw all members. G/P/B
  operate on the whole group. `BenthicGroupTest` runs 24 two-minute group simulations across flat
  walking, meanders, stairs and idle-then-walk; checks terrain clearance, floor contact, foot overlap
  ≤0.5% and no fades on flat walking. Separate tests cover unique slots, staggered successful climbs
  and count rejection. Diagnostics with `-PsimStdout` print overlaps and high-lag routing states.

### 15.2 Still required for the full goal

Validation of this retained foundation: `:fishsim:test :common:test :fabric:build :neoforge:build`
passed together (`build/benthic-phase3-validation.txt`): 332 simulation tests, 315 passed and 17
diagnostic probes skipped; 760 common tests with zero failures. The group matrix and group codec
tests pass. `build/benthic-phase3-fingerprints.txt` matches all 447 protected fingerprints against
`build/fp-base-p2.txt`. `git diff --check` passes. These checks do not cover the unfinished items below.

This is a foundation, not a completed Phase 3–5 claim. Continue with:

1. Line-order and inter-member overlap invariants; measured line wave/crowd spread. Pace-dependent
   leader meander is not enabled: the first attempt exposed another flat-walk follower fade and
   was removed. Per-member phase offsets exist, but animator breath/gait plumbing remains Phase 5.
2. Ground INSPECT/HIDE/GUARD/NUZZLE and in-view overtake/roadside/turn reveal beats. Current sharp
   turn preparation handles heading delay but is not the full turn-reveal beat or a seen guarantee.
3. Any-player scatter (`PLAYER` points), passer-by and backward-walk scripts, with solo as well as
   group coverage; preserve the currently passing solo and protected non-benthic paths.
4. Game adapter: gait selection, group field consumption, collision tops/partial floor sampling,
   riding/gliding/fall events, sample-budget measurement. Game constructors still select the
   existing no-gait percher and do not consume `companion_group` yet.
5. Render all group members on ground/water/perches, including phase-offset breath and plaice
   ripple. Commands/overlay must expose ground/perch/tumble, count/formation, lag and trail state.
6. Broad invariant/fingerprint verification, seen measurements against the percher baseline,
   in-game render/playtest verification, and the owner's pending visual review. Do not mark the
   full goal complete while any of these remains unverified.

## 16. Player scatter and adapter continuation (2026-10-12, uncommitted)

The owner's authorization in §15 remains in force; visual approval is still pending.

- `PLAYER` points carry nearby bystanders' feet and velocity. The game adapter gathers alive
  players within four blocks, excluding the owner, and discards teleport velocities. Drives ignore
  these points: passing players do not create curiosity or fear.
- Both solo ground motors and formation members anticipate a bystander's crossing up to 1.2 seconds
  ahead, choose a lateral escape on the side they already face, and use urgent scurry. Players on
  another floor are ignored. When several paths threaten a member, it reacts to the soonest crossing.
  The existing owner avoidance and the verified formation owner-scatter path are retained.
- `BenthicPlayerScatterTest` verifies the solo escape decision, absence of curiosity/fear, rejection
  of another-floor players, and 18 ten-second passer simulations (three sizes, two directions,
  three seeds), with at most one underfoot tick per run (0.5%). Full backward-owner and repeated
  passer group scripts remain required; this does not close all of §15.2 item 3.
- `LevelBlocks.top` supplies the collision shape's maximum height within its cell. The usual
  `solid` then `top` pair shares a collision query, preserving one counted sample per queried cell.
  This is the sim's existing column approximation, not exact stair or multi-box shape geometry.
  Sampling-budget measurement in game remains pending.
- The owner adapter now passes riding and elytra flags, and retains descending fall distance across
  vanilla's landing reset. Teleports, water, ladders, mounts, elytra and creative flight discard
  pending falls. Tests cover the final descent, a single landing event and protected movement.
- Viewer terrain changes invalidate all benthic members; scripted doors wait until every member
  clears the doorway. This fixes stale follower collision data and doors closing through followers.

Game gait/group constructors, ground desires and in-view beats, rendering/commands, broad group
invariants and visual verification remain unfinished as listed in §15.2. The game still uses the
no-gait percher until constructor and rendering integration is completed.

Validation: `:fishsim:test :common:test :fabric:build :neoforge:build` passed together
(`build/benthic-scatter-final-validation.txt`): 335 simulation tests, 318 passed, 17 diagnostic
probes skipped, zero failures; 762 common tests with zero failures. Both loader builds passed.
`FingerprintProbe -PsimStdout -PfpAll=1` (`build/benthic-scatter-fingerprints.txt`) matches all
447 protected fingerprints against `build/fp-base-p2.txt`. `git diff --check` passes. Changes are
uncommitted. Resume with the unfinished ground desires and visibility beats in §15.2, plus the
remaining formation invariants and game integration; keep the full goal active.

## 17. Ground desires continuation (2026-10-12, uncommitted)

- Ground leaders now use the existing `Mind` goal scoring, commitment and cooldowns, with
  perception taken from their actual ground position. Ground proximity uses the horizontal
  four-to-sixteen-block lag range: the swimmer's comfort band incorrectly made normal floor
  idle spots so urgent that inspection and nuzzling could never win.
- INSPECT supplies a species/temperament-sized floor standoff for items and companions. Only
  an actual close approach engages and habituates it; ground fish no longer satisfy curiosity
  simply by watching from anywhere. Held items retain their existing hand-hop presentation.
- HIDE huddles on the owner's far side from a hostile; GUARD stands between the owner and that
  hostile, looking toward it and puffing or biting. Hostiles no longer send ground fish onto
  a shoulder. Cats retain their head-climb response. NUZZLE brushes along one ankle and past
  it, then uses the Mind's existing cooldown; it is available to docile temperaments.
- Ground destinations are projected onto footprint support and checked as walkable. Terrain
  recovery, catching up, tumble daze and foot avoidance retain priority. Followers do not think
  independently: they follow their leader's detour and share its pose/drives.
- `goal()` and `goalTicks()` now report the ground Mind's desire and its clock. `landMode()`
  separately reports idle/follow/catch-up and transitions. Ground metrics use the latter for
  travel/stuck/catch-up measurements. Mixing desire names with the old travel-mode clock had
  produced false commitment failures. The game overlay includes land mode, lag, trail size and
  reachability when a ground trail exists.
- Four end-to-end behavior tests check actual item approach and release, opposite ground
  cover/guard sides, ankle approach and nuzzle cooldown, and both formations approaching their
  leader's inspection. `BenthicVisibilityTest` is a normal regression test comparing three seeds
  of two-minute walks to the no-gait percher, counting only ticks with owner speed above 1 b/s.
  Straight walking measures 17.47% seen against 15.56%; meandering measures 20.17% against 16.69%
  (`build/benthic-seen-baseline.txt`). The required straight-walking comparison passes.

Validation: `:fishsim:test` (`build/benthic-desires-clock-validation.txt`) passes 340 tests:
323 passed, 17 diagnostic probes skipped, zero failures. `FingerprintProbe -PsimStdout -PfpAll=1`
and both platform builds passed together (`build/benthic-desires-fingerprints-builds.txt`);
all 447 protected fingerprints match `build/fp-base-p2.txt`. `git diff --check` passes.

**Still unfinished:** ground overtake, roadside and turn-reveal beats. A direct moving destination
ahead of the owner caused terrain contacts and follower fades and was removed, along with its
early attention override. The retained visibility measurements include existing hand hops; they
do not prove the requested ground beats. The failed experiment is recorded in
`build/benthic-ground-beats-validation.txt` for diagnosis. Use stable overtaking stations and
heading preparation, and account for the ground catch-up hysteresis interrupting the detour,
before retrying. Keep the full terrain/group contracts; do not relax them to keep this experiment.
Also continue the remaining Phase 3 measurements/invariants, repeated climb-cycle and cat-cover
slot checks, passer/backward group scripts, and Phase 5 game integration and visual verification
from §15.2. The game still constructs the no-gait percher; visual approval remains pending.

## 18. In-view beats continuation (2026-10-12, uncommitted)

The owner's authorization in §15 remains in force; visual approval is still pending.

### 18.1 Behavior

- **One beat state in `GroundMind`**, for a solo or a group leader in GROUND_FOLLOW (never a follower,
  which follows its leader's trail through the beat). A beat targets a **fixed world spot**,
  chosen once: `ahead` blocks along the owner's travel, past where they will be when it gets
  there, off to the side it is already on. It is planned only if reachable in ≤ 5 s at 85% of its
  top scurry, and only where (a) the owner's own way ahead is level and walkable for the whole beat,
  (b) its route to the spot is level (no step at all: a beat is a dash, not a climb), and (c) the
  dash, any lead and the wait stay outside the reach of `ownerPush` against the owner walking on
  straight. Otherwise no beat. This is the §17 retry advice: no moving destination.
- **OVERTAKE** (3.3 ahead, 1.5–2.3 aside): it scurries past, then **leads**, keeping 3.3 ahead at
  the owner's pace along the pre-checked line to a fixed end point for 1.5–3 s (×1.5 for a
  racer), then stops and waits facing on: the drop back. **ROADSIDE** (4.8 ahead, 1.6–2.4 aside):
  it dashes there and waits facing the path. Either ends once the owner passes it by 0.6, or on
  a turn of more than 40°, an intent, a hand hop, a scatter, catch-up, a fade or a timeout. If they
  stop with it out ahead, it stays there in view, and that becomes its first idle spot.
- **Trigger:** a straight walk (travel heading within 10° over 12 ticks), looking where they go,
  not sprinting, unseen for ½ of the ground attention time (¼ for the sprinter's `raceAhead`),
  checked every 10 ticks. The sprinter picks overtake 75% of the time, others 30%. Cooldown 5 s
  for a racer, 12 s for others. During a beat the catch-up switch waits until 1.5 blocks later.
- **The turn reveal is the walk itself.** Within 8 blocks with a walkable line, a solo already
  walks straight at its owner and cuts the corner. A separate TURN dash was built and measured:
  no seen gain (baseline crab meander 21.95% off vs 21.89% on, 20 turn beats), and in the pillared
  cellar and on stairs it added contacts. It was removed.
- **Beats don't starve the hand ride.** A glimpse during a beat, or in the 60 ticks after, does
  not reset the attention clock. When they did, the beats (due sooner) displaced the hand ride,
  which is ~98% of walking seen, and the sprinter crab lost walking seen in 9 of 12 seeds.
- Beats draw from their own random stream, so a run in which no beat starts is bitwise the run
  without them. `GroundMind.beatsEnabled` (diagnostics) switches them off.
  `CompanionAgent.beat()/overtakes()/roadsides()` and the viewer overlay report them.

### 18.2 Measurements (equal seeds, beats off vs on)

- **Walking seen, per seed (12 seeds):** sprinter crab walk_flat up in 12/12 (e.g. 295→361,
  270→538), sprinter crab meander up 11/12, baseline spider crab (0.6) walk_flat up 12/12,
  baseline crab meander up 9/12. Baseline crab walk_flat: 3 beats in 12 seeds ("others rarely";
  a 0.3 crab barely out-walks a 4.3 b/s owner), 9 runs identical, 3 down (−3, −2 and −69 ticks:
  a one-beat reshuffle).
- **Terrain tails (20 seeds; low_ceiling, jumping, stairs 0.3/0.6, meander):** fades, leash runs,
  inside-owner runs and unseen > 30 s identical or within one tick-level difference. No beat
  starts in the cellar. Six-seed seen table: no beat ever adds a fade (`build/beat-probe-final.txt`).
- **Pre-existing, not beats (unchanged with beats off):** slug (0.12) fades 36–39 in 6 seeds of
  sprint_away (it can't keep pace with a sprint); spider crab stairs fades 4 in 20 seeds (seeds
  1–8, which the invariant runs, are clean).

### 18.3 Named gap found on the way (not fixed)

**Idle come-alongside walks under the owner's feet.** When a ground crab comes up for a hand
hop from the far side of the hand, its straight walk crosses their feet. Once the hand is in
range with a clear arc, it crouches there, inside their body for ~1 s (traced: low_ceiling
seed 1, ticks 1238–1260). This is the cellar's inside-owner tail; beats only resample it. Two
fixes were tried and **reverted, unvalidated**: a no-hop-from-underfoot guard (doubled the
cellar's worst inside share, 0.009 → 0.020), and a round-the-feet detour (cost the jumping script
17 s of unseen, 27.5 → 44.3 s). Formations already route round the ankles; a solo needs a
detour that doesn't strand the hop.

### 18.4 Validation

`:fishsim:test` (`build/beats-validation-3.txt`): 347 tests, 325 passed, 22 diagnostic probes
skipped, zero failures. Normal-suite `BenthicBeatTest` covers the racer's beats, seen against
beats off, no beat-caused fades, and the bitwise no-beat run. `BenthicBeatProbe` (simStdout) has
`beats` (seen table), `perSeed`, `tails` and `cases`/`inside` traces. `FingerprintProbe
-PsimStdout -PfpAll=1` (`build/beats-fingerprints.txt`): all 447 protected fingerprints match
`build/fp-base-p2.txt`; the 69 BENTHIC changed as meant. `:fabric:build :neoforge:build` and
`:common:test` pass. `git diff --check` passes.

**Still unfinished (from §15.2/§17):** the §18.3 come-alongside gap; Phase 3 line-order and
inter-member overlap invariants, measured line wave and crowd spread; repeated climb cycles and
cat-cover slot checks; passer and backward-owner group scripts; and all of Phase 5 game
integration (gait/group constructors, rendering every member, commands, the floor-sampling
budget in game). The game still constructs the no-gait percher. Owner viewer look for the
beats: `./gradlew :fishsim:runCompanionViewer`, BENTHIC (sprinter for frequent overtakes),
`benthic_park` or walk_flat, watching the overlay's beat field.
