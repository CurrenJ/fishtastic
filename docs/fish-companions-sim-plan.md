# Fish Companions — Simulation Rewrite Plan

**Written:** 2026-10-11 · **Status: Phase S spike built and passed the owner's gate 2026-10-11 (§8.5); Phase 1 next.**

**Decision (owner, 2026-10-11):** rebuild companion motion the way the tank was rebuilt
([fish-sim-engine-plan.md](fish-sim-engine-plan.md)): a Minecraft-free simulation in `:fishsim`,
verified by headless tests and video, with a thin adapter as the only code that knows Minecraft.
The fish has to read as a creature deciding where to go, not as a staged scene.

**Scope change (owner, 2026-10-11):** all fishing reactions ([fish-companions.md](fish-companions.md)
§5, including the minigame dock, the celebration fly-past and the bobber visits) are **deferred**. We
come back to them only if the new architecture lands and the owner is happy with it. Nothing in this
plan designs for them.

[fish-companions.md](fish-companions.md) stays the behaviour spec for everything else (§1–4, §6–10).
This plan replaces its motion approach (§1 "reuse the fish sim", §11 "Motion", §19–22) and its
phase table (§12).

---

## 1. Why the current motion reads as staged

`ClientCompanion` (2,489 lines) decides **where the fish should be** and then makes the fish chase
that point:

- **Where to be comes from formulas:** `orbitAngle` advancing at a fixed rate, `eightWander()`,
  `peekPoint()`, `perchPoint()`, `handHopPoint()`, `dockWorldPoint()`.
- **Behaviours are cross-faded** by blend weights: `peekBlend`, `sceneBlend`, `dock`, `handHop`,
  `outAmount`.
- **The fish is a tank sim dragged along behind the result.** `CompanionFlock` runs the tank
  engine in an invisible box whose centre is moved to the blended point every tick.

The fish never makes a decision, so its timing is regular, its paths repeat their shape and it never
changes its mind. The moving box also creates two motion sources that don't know about each other.
That accounts for most of rounds 2–4: the wobble limit cycle, the broken face-your-velocity rule,
the figure-eight crossing. The fish also has **no block awareness**: it swims through walls,
ceilings and doors.

## 2. Goals and non-goals

**Goals**
1. The fish acts on its own wants: it picks goals, commits to them for a while and drops them when
   something more pressing comes up. Temperament changes the weights, not the code paths.
2. It moves in world space with the same body physics as the tank fish: turn-rate cap, burst and
   glide, banking, speed-coupled tail. There is no follow box and no frame to drag.
3. It never enters solid blocks and finds its way through doors and corridors after the owner.
4. The owner sees it regularly in first person because it **wants to be seen**, not because a
   peek is on a timer.
5. Almost all iteration happens headless: tests in seconds, video, and a viewer the owner can walk
   around in without launching Minecraft.

**Non-goals (this iteration)**
- Fishing reactions, docking and celebrations (deferred, see above).
- Tank interactions E3/E4 (already planned as later improvements).
- New rendering. `FishAnimator` and the per-class pose dispatch are kept as they are.

---

## 3. Architecture

### 3.1 Where it lives

A new package, `grill24.fishsim.companion`, inside `:fishsim`. It ships in the mod jar alongside
`core`/`domain`, under the same rules: no Minecraft imports (the compile classpath enforces it) and
Java 17 `--release`, so the 1.21.1 and 1.20.1 backports can share it. Harness and viewer additions
go in `harness`/`viewer` and never ship.

```
fishsim/src/main/java/grill24/fishsim/companion/
  CompanionWorld      the input interface: what the fish can perceive (§3.2)
  CompanionAgent      one fish: Body + Mind, fixed-dt step, interpolate(alpha)
  Body                movement physics per locomotion class (§3.4)
  Mind                drives -> goal selection with commitment (§3.3)
  Goal                one current intention (loiter, swim-to, inspect, perch, hide, catch-up)
  Steering            goal -> desired velocity, plus obstacle avoidance from LocalVoxels
  LocalVoxels         a rolling block window around the owner: solid/water bits + distance field
  CompanionGroup      a trio: leader agent and two followers using local boid rules
  Temperament         drive weights and timing per temperament
```

### 3.2 The input interface: `CompanionWorld`

A snapshot built once per tick, at 20 Hz. Everything the fish knows comes through it:

| Input | Contents |
|---|---|
| Owner | eye position, velocity, look yaw/pitch, flags (sneaking, sprinting, on ground, in water, flying, gliding), `teleported` flag |
| Camera | perspective (first or third person), FOV. Used for "am I being seen" and "am I blocking the view" |
| Blocks | `LocalVoxels`: a ~24³ window centred on the owner, with solid and water bits. Re-centred only when the owner crosses a chunk-ish threshold, plus dirty updates for block changes |
| Points of interest | typed list: held item (with its reaction category), hostile mobs, cats, dropped items, other companions (position + velocity + species/class) |
| Environment | time of day, rain, a comfort scalar for the biome (the adapter derives it from zones, biome weights and SNOW, §6 of the spec) |

In game, the adapter fills this from Minecraft. In headless runs, a scripted owner and a voxel
scene fill it. The agent can't tell the difference.

### 3.3 The mind: drives, goals, commitment

Each drive is a scalar urgency that rises and falls with its own time constants and inputs. Every
tick the mind scores its candidate goals as urgency × temperament weight. It keeps the current goal
unless a challenger beats it by a margin (hysteresis), and every goal has a minimum commitment
time unless it's an emergency. That is the core change: what the fish does next depends on its state
and what it perceives, not on a clock.

| Drive | Rises when | Goals it proposes |
|---|---|---|
| **Proximity** | the owner is beyond the fish's comfort band. The band is a radius range, not an orbit, and it widens while the owner stands still | catch-up (fast, direct), drift back (lazy) |
| **Wander** | nothing else is pressing. Satisfied by moving | loiter at a nearby open spot inside the band, chosen by sampling open voxels |
| **Attention** | the fish hasn't been in the owner's view for a while. Reset by being seen | swim through the owner's view: pick a spot in the view cone and approach it. Pass through, don't park |
| **Curiosity** | a point of interest appears (held item, dropped item, another companion), then habituates | inspect: approach, hold at a species-sized distance, lose interest |
| **Wariness** | hostiles or cats nearby (dwell → habituation → cooldown, spec R7.2) | hide: go to the owner's far side or onto the perch |
| **Rest** | time awake, species' off-hours, the owner idling a long time | settle: slow loiter or perch for perchers |

Temperament (spec §4) maps directly onto this table. Skittish raises wariness gain. Docile narrows
the comfort band and lengthens rests. Sprinter gets a "race ahead" goal under proximity while the
owner sprints. Ambusher holds still, then darts at curiosity targets. Ghost gets long rests.
Erratic, trembler and metronome change commitment timing. Held-item reactions (spec §3.1) become
curiosity targets with a pose hint, not offset points.

**The leash is a backstop, not a behaviour.** If the owner outruns any swim (elytra, minecarts,
teleport), the fish scale-animates out and back in near the owner, the one placement that isn't a
swim (spec §1 motion rule). The same tests that guard containment in the tank make sure this never
fires in normal walking or sprinting.

### 3.4 The body: movement physics per locomotion class

The body takes a desired velocity and applies the class's limits:

- **FREE_SWIM:** the tank's planar model carried into 3D air. Continuous yaw with the 7°/tick cap
  (`PLANAR_TURN_RATE`), a pitch limit for climbs, burst and glide cycle, OU wander noise on the
  committed heading, bank from turn rate. Outputs heading, pitch, bank, `speedFactor`, tail phase
  and shape: exactly what the animator already consumes.
- **GLIDE:** the same model under `Tunables.GLIDE`: slow turns, wide arcs, high ride height.
- **DRIFT:** pulse and sink with a weak drive toward its goal.
- **BENTHIC / ANCHORED:** "perch" is a real attachment to the owner, because a crab riding a
  shoulder is the parrot pattern, not a fake. Leaving and returning are a body-paced hop with a
  ballistic arc and landing settle. In water, both switch to the free-swim body (spec E1).

**Revised by the spike (§8.2): the body is heading-first, not a copy of the tank's velocity
steering.** The paragraph below was the plan; the spike found the tank's rules let a companion swim
backwards, so only the tank's constants and envelopes were carried over.

**Reuse decision: copy first, extract later.** The tank's planar integrator lives inside the
4,800-line `FlockEngine`, written as parallel arrays and locked bitwise by `GoldenTrajectoryTest`
and `ParityTest`. Extracting it now would put the tank at risk to speed up the companion. The spike
ports the kinematic rules into `companion/Body`, reading the same `Tunables` constants. Once both
are stable, a later step can extract one shared body class, with the tank goldens as the gate.

### 3.5 The adapter (the only Minecraft-aware code)

In `common/.../client/companion/`:

- `CompanionWorldAdapter` builds the `CompanionWorld` snapshot each client tick. It samples
  collision shapes into `LocalVoxels` (a non-empty collision shape counts as solid) and gathers
  points of interest from the existing entity queries.
- `ClientCompanions` keeps the registry, eviction and render-hook role it has today, but ticks
  `CompanionAgent`s instead of `ClientCompanion`.
- Rendering: the world-render hook, `CompanionPerchLayer` for perched poses, one
  `ItemStackRenderState` per fish, and the pose dispatch/`FishAnimator` coupling from
  `CompanionFlock`. The sim drives them with world-space pose values; nothing is box-relative.
- Every feature ships with an in-game debug trigger (owner's solo-playtest rule): force a drive,
  show the current goal and drive bars in a debug overlay, spawn a phantom companion.

### 3.6 Multiplayer

Each client simulates every visible companion from that owner's synced pose, as it does today. The
seed is shared, so remote companions behave in character, but they don't have to match frame for
frame across clients. That's fine for a cosmetic. Pet-to-pet scenes (§5, phase 4) keep the
server's "a meeting happened" event so both clients play one at the same time. The motion of the
scene comes from the social drive, not a scripted figure-eight.

---

## 4. Verification

### 4.1 Scripted owners and scenes (the headless test matrix)

**Owner scripts:** standing idle, walking straight, walking with turns, sprint and stop, 180° turns,
camera sweeps while standing, looking down, jumping, creative flight, elytra (fast enough to
trigger the leash), teleport, wading into water.

**Scenes:** open flat ground, a 1×2 corridor, a doorway, low ceiling, dense forest (column
obstacles), a stairwell, an underwater pond.

### 4.2 Invariants (tests, seconds to run)

| # | Invariant | Guards against |
|---|---|---|
| 1 | The body never enters a solid voxel, in any scene × owner × seed | swimming through walls |
| 2 | Acceleration, jerk and turn rate stay under the class caps | snapping, the wobble class of bug |
| 3 | Drawn heading follows world velocity while moving (with hysteresis) | the facing bug |
| 4 | Never inside the camera near-clip zone; never sitting on the screen centre for more than N ticks | fish in your face |
| 5 | First-person visibility: seen at least X% of a 10-minute walk, and the longest gap without a sighting stays under Y s | the invisible companion (spec R2.2) |
| 6 | Leash never fires in walking/sprinting scripts; it fires in elytra/teleport and recovers with the scale animation | needless pops |
| 7 | Reaches the owner through corridor and doorway scenes within a time bound | getting stuck behind walls |
| 8 | Same seed and inputs give bitwise-identical trajectories | non-determinism |
| 9 | Step count is independent of frame rate; `interpolate` is the only per-frame call | frame-rate dependence |
| 10 | Goal switches respect minimum commitment, except for flagged emergencies | dithering between goals |

**Diagnostics, never objectives** (per the metrics-as-proxies lesson): position periodicity relative
to the owner (orbits show up as a spectral peak), how long goals last, speed variety paired with
rate of change. These help explain what we see. Tuning toward them is how the "hop" shipped last
time.

### 4.3 Seeing it without Minecraft

- **`runCompanionHeadless`** exports a top view, a side view and a **first-person camera view**
  (what the player would see, using the scripted owner's camera) as GIFs, plus trajectory plots and
  a metrics CSV. The tank harness has no first-person view, so this is new and most of the value.
- **`runCompanionViewer`** is a playground: you drive the owner with WASD and the mouse, flip between
  first person, third person and top view, see drive bars and the current goal as an overlay, and
  adjust tunables with sliders. The owner can play with the companion's personality in seconds,
  without launching Minecraft.
- **The owner's eye is the gate.** Tests guard the floor, and the viewer and video decide the feel.

---

## 5. Build order

### Phase S — Spike (the go/no-go)

Scope: **FREE_SWIM only**, solo (no trio). Drives: proximity, wander, attention. Scenes: open flat,
corridor. Owner scripts: idle, walk, walk+turn, sprint/stop, camera sweep. Invariants 1–3, 5, 8, 9.
Headless exporter with the first-person view; the viewer with WASD control.

**Gate:** the owner drives the fish around in the viewer and watches the first-person GIFs. Go only
if it clearly reads as a creature and not a staged scene. No-go costs a few days, and the old code
is untouched until then.

### Phase 1 — Bodies and perception

GLIDE, DRIFT, and the BENTHIC/ANCHORED perch plus hop bodies; trios (`CompanionGroup`); the rest of
the scenes and owner scripts; the full invariant table.

### Phase 2 — The full mind

Curiosity, wariness and rest; points of interest (held items, hostiles, cats, dropped items);
temperament weights for all temperaments; held-item reactions as curiosity targets.

### Phase 3 — Into the game

`CompanionWorldAdapter` + `LocalVoxels` sampling; switch `ClientCompanions` to agents; **delete the
old motion core** (`ClientCompanion` motion and behaviour, `CompanionFlock`'s box frame); debug
overlay and triggers; both loaders. Gate: owner solo playtest.

### Phase 4 — Pet-to-pet

A social drive: greeting on approach, synchronized swim for same species (G1), and play around both
owners for two free swimmers (G2), all as goals the minds pursue. The server event provides the
timing and one-scene-per-companion rule (spec §10). The scripted figure-eight goes.

### Phase 5 — Ambient world (spec §6)

Biome comfort, rain, time and moon, as modifiers on drives and body (sleepy = slower and longer rests,
shivering = a body noise term).

### Deferred

Fishing reactions (spec §5) in full; tank interactions (E3, E4). If fishing comes back, a bobber is
just another point of interest and the dock is a presentation hand-off. The design should make
that easy without building it now.

---

## 6. What happens to the existing code

| Keep | Rewrite on the sim | Delete (phase 3) |
|---|---|---|
| Player attachment, bond/equip/unequip, sync, debug command | Display modes (§3) → drives + bodies | Orbit/figure-eight/peek/perch path formulas |
| Companions tab, picker, banners, encyclopedia thresholds | Personality (§4) → drive weights | Blend weights (`peekBlend`, `sceneBlend`, …) |
| World-render hook, `CompanionPerchLayer`, per-fish render states | Held-item reactions → curiosity targets | `CompanionFlock`'s follow-box frame |
| `FishAnimator` + per-class pose dispatch | Pet-to-pet scenes → social drive | Dock/PiP motion (deferred with fishing; renderer files stay parked) |
| Name, pet, feed, emote packets | — | Dev-only wobble and frame-stat probes tied to the old controller |

---

## 7. Risks and open questions

1. **3D body vs the tank's planar model.** Tank fish turn in a plane and adjust height separately.
   An air companion needs real climbs. The spike settles how much pitch looks right before
   it reads as a bird.
2. **Visibility vs annoyance.** The attention drive could make the fish pushy. Invariant 4 and
   habituation on the attention drive are the guards. Its strength is an owner-tuned slider.
3. **`LocalVoxels` sampling cost.** A 24³ window is about 14k cells, re-sampled only when the owner
   moves far or blocks change. Measure it in phase 3; rebuild incrementally if needed.
4. **Fast travel.** Elytra and minecarts outpace any fish. The leash's scale-out and scale-in is the
   accepted answer. Tune where it fires, not whether.
5. **Body extraction later.** Two copies of the kinematics can drift. Accept it until both are
   stable, then extract with the tank goldens as the gate.

---

## 8. Phase S spike — as built (2026-10-11)

Uncommitted. Nothing in `common/` changed; the old companion code still runs in game untouched.

### 8.1 What exists

| Where | What |
|---|---|
| `fishsim/.../companion/` (ships in the jar, unused by the mod yet) | `CompanionAgent` (step/interpolate, fade + hidden-while-outrun), `Mind` (drives proximity/wander/attention; goals LOITER, ESCORT, CATCH_UP, SHOW_OFF; commitment + hysteresis), `Body` (heading-first FREE_SWIM), `LocalVoxels`, `Breadcrumbs` (owner trail for routing round walls), `Visibility`, `Capsule` (owner body), `OwnerState`, `BlockQuery`, `Vec`, `CompanionTunables` |
| `fishsim/.../harness/companion/` (dev only) | `VoxelScene` (open, corridor, doorway, forest), `OwnerScript` + `Pilot` (11 scripted owners), `CompanionRun` (record + metrics), `CompanionHeadless` |
| `fishsim/.../render/CompanionRenderer` | software first-person view + top-down map, shared by export and viewer |
| `fishsim/.../viewer/CompanionViewer` | the playground |
| `fishsim/src/test/.../companion/` | `CompanionInvariantTest` (10 tests, 11 scripts × 3 seeds × 2 min); opt-in probes `SeedSweepProbe` (multi-seed A/B), `WobbleProbe`, `TrailSnapshotProbe`, `RenderTimingProbe` (`-PsimStdout`) |

```
./gradlew :fishsim:runCompanionViewer      # WASD, Shift, drag/arrows, F, T, Space, N, R; owner dropdown plays any script live
./gradlew :fishsim:runCompanionHeadless    # build/companion-export: <script>.gif, <script>-sheet.png, metrics.csv
./gradlew :fishsim:test --tests 'grill24.fishsim.companion.*'
```

### 8.2 What the spike changed in the plan

- **Heading-first body.** The tank's model steers a velocity and caps only the turning part of the
  acceleration; braking is uncapped, so velocity passes through zero and comes out reversed. The
  first run measured 100–170° between facing and travel: a fish swimming backwards. The body now
  has a heading and a forward speed. Turn rate and turn-rate change are capped and tighten with
  speed (`maxLateralAccel`, `maxJerk`), and speed is cut back when the wanted direction is off to
  the side. Facing error is 0 by construction. Kept from the tank: the 7°/tick rate, OU wander,
  burst-and-coast, and bank from turn rate.
- **A heading-first fish can't sidestep, so it has to see ahead.** Three things keep it off
  blocks:
  - braking to stopping distance, probed along the heading, the velocity and the current turn arc;
  - whisker steering: the smallest yaw/pitch swing that's clear, or failing that the roomiest one,
    which is what works in a 1-wide tunnel; the side is sticky so it doesn't dither;
  - routes that keep a margin.

  Multi-seed A/B against "whiskers off": doorway 180 → 31 contacts, forest 281 → 44 (8 seeds ×
  2 min each). The hard per-axis resolve remains, and penetrations are 0 everywhere.
- **The owner is a moving obstacle.** Braking for the owner uses relative velocity. Braking along
  the fish's own heading stopped trailing fish from ever overtaking: walk went from 4% to 29%
  catch-up. Owner avoidance grows with fish speed and adds the owner's closing speed. Routes that
  would cut through the owner get a waypoint round their side.
- **Show-off swims relative to the owner** (feed-forward of their velocity), or it can't reach the
  front of someone walking. It succeeds only once the owner has actually had a look (15 ticks in
  view), and if they missed it the pass is swum again from the other side.
- **Leash: hidden while outrun.** The fish fades out, then stays hidden while the owner is faster
  than any swim, and comes back once they slow. Elytra went from 37 pops to 9 fades in 2 min. The
  owner approved fade out/in on 2026-10-11.
- **Tunnels: lead ahead when trailing means squeezing past.** In tight spaces the fish falls in
  behind on the owner's trail, but if it's already in front it leads the way.

### 8.3 Results (seed 1, 2 min per script; full table from `runCompanionHeadless`)

| Script | Contacts | Seen | Longest unseen | Goals |
|---|---|---|---|---|
| idle_fixed | 0 | 32% | 18.5 s | loiter 96%, show-off 4% |
| idle_looking | 0 | 35% | 21.0 s | loiter 86%, show-off 14% |
| walk | 0 | 23% | 10.2 s | escort 92%, catch-up 4% |
| walk_turns | 0 | 17% | 10.8 s | escort 73%, catch-up 13% |
| doorway | 3 (≤1.1 b/s) | 14% | 14.1 s | |
| forest | 6 (≤0.9 b/s) | 13% | 21.7 s | catch-up 36% |
| corridor | 9 (≤1.1 b/s) | 11% | 18.3 s | |

Facing error 0°, turn rate ≤ 7°/tick everywhere. There's no limit cycle: 0–10 fast turn reversals
per minute, where a wobble would show 40–80. Sim step ~7 µs; viewer frame 6–8 ms.

### 8.4 Known gaps (for the owner's look, not fixed)

- **Jerk floor ~120 b/s³** (invariant bound 160): from combined lateral/forward/slip changes. Not
  visibly a snap in the trails, but it's the number to watch if motion reads twitchy.
- **Forest and other constant walking: catch-up 30–40%, seen 6–13%.** The owner turns 90° at lanes
  and trunks cut the fish off. This may read fine (a pet trailing through trees) or as laggy.
- **1-wide corridor:** an owner turning round walks into the fish (no room to pass), and the fish
  can't get in front to be seen. The invariants exempt `corridor` for overlap and visibility.
- **Escort reads as a lane** beside the owner between decisions (7–16 s). The ride position drifts,
  but if it looks pinned, raise the drift or shorten the decision interval in the viewer.
- Not yet built (spike scope): trios, other locomotion classes, curiosity/wariness/rest,
  temperaments, `CompanionWorld` as one snapshot interface (the agent takes `OwnerState` +
  `BlockQuery` directly).

### 8.5 Gate

The owner drives the fish in `runCompanionViewer` (manual and scripted owners) and watches the
first-person GIFs. **Go** = it reads as a creature deciding things, not a staged scene → Phase 1.

**Result (owner, 2026-10-11): go.** "This looks a lot better. I really like this."

---

## 9. Phase 1 — order, gates, and as built

| Step | Builds | Gate |
|---|---|---|
| **1a** Harness + perception | water bits (`BlockQuery.water`, `LocalVoxels`), `OwnerState.inWater` (water at the chest), terrain-following pilot (opt-in per script); scenes `stairwell`, `low_ceiling`, `pond`; scripts `stairwell`, `low_ceiling`, `pond_dive`, `wading`, `about_face`, `jumping`, `creative_flight`; water in the renderer | FREE_SWIM fingerprints of the 11 spike scripts bitwise identical (`FingerprintProbe`); FREE_SWIM invariants on the new scripts, or the gap named |
| **1b** Body seam + GLIDE (**parked**, §9.2) | per-class gait, GLIDE from `Tunables.GLIDE` with a high ride height; viewer class picker | FREE_SWIM fingerprints unchanged; GLIDE invariants × all scripts × 3 seeds; 8-seed sweep |
| **1c** DRIFT (**first cut**, §9.3) | bell-axis body: thrust only along the axis, pulse and coast, passive sink; effort raises pulse rate and strength | thrust never off-axis, tilt rate under cap, invariants, no leash on foot |
| **1d** Perch + hop | BENTHIC/ANCHORED attachment (parrot pattern), ballistic hop with crouch, bounded air control and settle; show-off = a hop across the face; in water, hop off into the free-swim body and back on after | hop accel bounded, arcs clear of blocks, no leash on foot, water switch in `pond_dive`/`wading` |
| **1e** Trios | `CompanionGroup`: leader agent + two boid followers on the heading-first body | per-member invariants, followers never overlap or straggle |
| **1f** Full table | invariant test over class × script × seed; per-class export | **owner gate**: viewer + per-class GIFs |

Defaults chosen for the owner's gate: a drifter keeps up by pulsing harder, never by a tether; perchers
in air hop only between body slots and the hand (no ground walking — that needs a crawl body).

### 9.1 Step 1a — as built

- Every spike trajectory is bitwise unchanged (33 fingerprints, 11 scripts × 3 seeds).
- FREE_SWIM on the new scripts (8 seeds × 2 min): 0 penetrations, 0 facing error, contacts only
  in the cellar (5, ≤ 0.97 b/s).
- **Trailing a constantly moving owner** (named in `CompanionInvariantTest`). Owner, 2026-10-11:
  "it's okay for the fish to trail behind on constant movement. That's kind of what you'd
  expect" — to be confirmed in person; until then these stay named exceptions, not tuning targets:
  - `about_face` (reversing every 2–3 s): the fish starts every leg behind the owner, spends ~2 s
    catching up, then rides at 65–80° off centre, just outside the ~49° horizontal half-FOV; legs
    end before the attention drive (7–20 s) fires. Longest unseen 38.6 s across 8 seeds, catch-up 51%.
  - `stairwell`: the same trailing on a climb (34.1 s unseen, catch-up 36%), and the tight turns
    put it near the camera 0.7% of ticks on average, 1.1% at worst (bound 1%, relaxed to 2% there).
- Correction to §8.2: re-measured at b3b1b0b6, "whiskers off" gives doorway 102 / forest 108
  contacts (8 seeds × 2 min), not 180 / 281; the whiskers-on numbers (31 / 44) match.

### 9.2 Step 1b — the body seam, a contact deadlock, and GLIDE (parked)

**The seam.** `Gait` holds a class's body limits and riding geometry, rebuilt from
`CompanionTunables` every tick (sliders stay live). `Body` and `Mind` read it; FREE_SWIM's gait is
the existing fields, so the seam itself left all 33 fingerprints bitwise identical.

**A contact deadlock in the spike body (fixed).** FREE_SWIM in the cellar, seed 4, sat motionless
against a pillar for 27 s while its owner walked round. The hard resolve stops a body exactly one
radius from a block; the braking probes sweep radius + 0.02, so they overlap the block from the
start. At rest the velocity is pure vertical slip, the velocity probe slides up the face, reads 0
free, the speed target is 0, and the body never leaves rest. Fix: a body at rest (forward speed
< 0.1 b/s) brakes on its nose and turn arc only. Equal grounds (FREE_SWIM, 8 seeds × 2 min, off →
on): cellar farthest 26.8 → 2.0 s, corridor 14.9 → 12.6 s, contacts corridor 90 → 78, doorway
31 → 47, forest 44 → 54, max impact unchanged (≤ 2.68 b/s). Two broader variants (relax every
probe; relax the velocity probe) raised contacts further and were dropped. Only obstacle-scene
fingerprints moved (7 of 33); open ground is bitwise unchanged.

**GLIDE — parked by the owner (2026-10-11):** "glide in both the companion and the fish tank sim
need a major refactor in the future. They do not work as I'd like at the moment." What exists is a
first cut, left in place but not tuned further:

- The tank's flat 2.2°/tick cap could not follow an owner: walk catch-up 50%, stairwell farthest
  23.7 s, cellar 32.8 s (8 seeds). Raising the pitch cap (stairs climb at 45°) and matching the
  swimmer's size were both tested and refuted.
- As parked: turning capped by sideways acceleration (2.5 b/s², pivot ≤ 4.5°/tick), and a "tuck"
  toward the swimmer's limits as blocks close in. In the open it passes every invariant.
- In corridor, doorway, forest and stairwell it doesn't (named `GLIDE_TIGHT_SPACE_GAPS`): 12–20
  contacts/min, impacts to 3.3 b/s, farthest 13–18 s, fades in the 1-wide tunnel. The tuck
  helped 7 of 9 scripts and regressed the stairwell (13.2 → 18.0 s).
- The refactor should be designed for the tank and the companion together.

### 9.3 Step 1c — DRIFT, a first cut with open problems

**The body.** `Motor` is now the base every body shares (the mind and agent talk to it); the
extraction left all FREE_SWIM fingerprints bitwise identical. `DriftBody` is a pulsed controller:
it computes the acceleration it needs (drag feed-forward, sink compensation, a correction toward
the desired velocity), tilts its bell toward that at a capped rate (never past 85°), and thrusts
only along the bell, in `sin²` strokes whose strength is fixed at each stroke's start so the
cycle's mean matches the need. Idle: slow strokes (2.8 s), mostly upright against the sink. It
rides behind one shoulder (`Gait.alongOffset` −0.7). The renderer draws a bell with trailing
tentacles; `CompanionRun.Metrics.tailFirstFraction` counts ticks travelling > 100° from the body's
front (for a drifter only ticks with real thrust count — sinking between strokes is just sinking).

**Owner decision (2026-10-11):** of trailing far, being towed in the owner's wake, or "stronger
pulses", the owner chose **stronger pulses** — it keeps swimming itself, pulsing faster and harder
when behind. Top speed 5.5 → 7 b/s, thrust 32 b/s², fastest cycle 0.8 s.

**What the traces show.** On a straight walk it keeps pace (within a block, alongside). The lag
comes from stops and turns: each stroke adds ~3 b/s so it overshoots the owner (8.4 b/s against a
7 b/s ask); a jelly can't brake, only stop pushing; and reversing means swinging the bell ~160°
through upright, 1.5–2.7 s of nearly useless thrust. Stronger pulses alone barely moved it (walk
mean 4.51 → 4.64 b/s, farthest still ~10 s).

**Open (not adopted — each measured mixed or worse on 8 seeds):**
- braking by room (cap the asked-for speed toward a block to what drag can stop): open-ground
  contacts 0 → 40, a leash on `walk`;
- never pulsing into its own wake (stop thrust when the bell faces against travel): worse on the
  same scripts;
- tilt 3 → 6°/tick: farthest better on 7 of 9 scripts (about_face 43.7 → 21.3 s) but ground
  contacts 0 → 41 on `sprint_stop` and doorway impacts to 8.1 b/s.

**Where it stands (8 seeds × 2 min, tilt 3°/tick):** 0 penetrations; farthest 10–44 s on walking
scripts; impacts to 6–7 b/s in the doorway and cellar; tail-first 1–15%. DRIFT stays out of the
invariant table until stroke sizing (arrive at the asked speed instead of overshooting), altitude
hold near the ground, and reorientation have a design.
