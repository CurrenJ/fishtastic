# Fish Companions — Simulation Rewrite Plan

**Written:** 2026-10-11 · **Status: Phases 1 and 2 passed the owner's gate 2026-10-11 (§9.6, §10.6); Phase 3 (into the game) built, awaiting the owner's solo playtest (§11.7). GLIDE and DRIFT parked. Phase 4 done 2026-10-15; Phase 5 decisions recorded (§5), not started.**

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

**Done 2026-10-15** (docs/fish-companions-party.md §26–28): the in-party predator/prey split and
scene (G4 inside a party); G1's ring and G2's eight across players as the `SCENE` goal, timed by
the shared clock from the two owners (D29), with the greeting as each scene's opening (D27);
free swimmers only (D25); swimmers keep out of other players' bodies. Validated headless
(`CrossSceneTest`, `CrossSceneProbe`, the two-view divergence probe) and in game on both loaders
(`companionscenes` with the dev ghost owner, and a dedicated-server two-client self-test). Cross-
player G3/G4 and class pairings stay for later.

### Phase 5 — Ambient world (spec §6)

Biome comfort, rain, time and moon, as modifiers on drives and body (sleepy = slower and longer rests,
shivering = a body noise term).

**Owner decisions (2026-10-15), not started:**
- Scope is as above: biome comfort, rain, sleepy off-hours, and the full-moon idle. The mob
  reactions in spec §6 (cats, axolotls, dolphins) stay deferred.
- Each fish has its own mood, from its own species' home. A mixed party can be split, with one
  fish shivering and its party-mates content.
- A shivering fish reacts to warm things nearby: campfires, fire and lava.

Steps (agreed 2026-10-15): 5a, the ambient input on `CompanionWorld` (a neutral snapshot keeps
the fingerprints bitwise); 5b, comfort, rain and warmth; 5c, sleepy off-hours and the full-moon idle;
5d, the game adapter, a debug trigger, the overlay, and a self-test on both loaders. Mood strength:
start subtle and capped (agreed), so seen % and fades don't drop; tuned in the viewer after 5b.

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
| **1d** Perch + hop (**built**, §9.4) | BENTHIC/ANCHORED attachment (parrot pattern), ballistic hop with crouch, bounded air control and settle; show-off = a hop across the face; in water, hop off into the free-swim body and back on after | hop accel bounded, arcs clear of blocks, no leash on foot, water switch in `pond_dive`/`wading` |
| **1e** Trios (**built**, §9.5) | `CompanionGroup`: leader agent + two boid followers on the heading-first body | per-member invariants, followers never overlap or straggle |
| **1f** Full table (**built**, §9.6) | invariant test over class × script × seed; per-class export | **owner gate**: viewer + per-class GIFs |

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

### 9.4 Step 1d — perchers (BENTHIC, ANCHORED)

**As built.** `PerchBody` (a `Motor`) holds a free-swim `Body` the `Mind` steers in water.
- **Perched** is a real attachment, the parrot pattern: right shoulder, left shoulder or head by
  seed (the back for ANCHORED). It moves with the owner, teleports included, so a riding percher
  never needs the fade.
- **Hops** are ballistic (gravity 22 b/s²): a size-paced crouch, a launch aimed at where the target
  slot will be at landing, bounded air control (10 b/s²), and a 4-tick settle. An arc is launched
  only if it's clear of blocks and, once off the owner's body, never passes back through it or
  within 0.1 of the camera lens past its radius; failing that the hop is higher and slower, or
  waits. It also waits until the owner's height and velocity have held steady for 0.5 s.
- **Show-off = a hop onto the hand** on its own side, in the view's frame, low and to the side,
  where a held item sits in first person. Also when the owner looks down (pitch > 50° for 0.75 s).
  It sits 2–4 s, then hops home; now and then it changes shoulders. ANCHORED never hops in air.
- **Water** (spec E1): chest in water for 10 ticks → it hops off into the water and swims under
  the mind; dry for 20 ticks, steady, and within 3.5 blocks → it hops back on. A bump mid-hop
  hands it to the swim body, with a 1 s wait before remounting.
- Invariants count a percher on its owner (riding, or the first/last 0.12 s of a hop) as part of
  their silhouette, as with vanilla parrots: no block, owner, near-clip or acceleration checks
  there. Its own bounds: accel ≤ gravity + air control + 2; jerk ≤ 800 (hop/swim hand-offs —
  hitting water — measured to ~670).

**Results (8 seeds × 2 min).** BENTHIC: 0 penetrations; no leash on foot; longest unseen ~20 s
almost everywhere, seen 16–30%; inside-owner and near-clip ≤ 0.5% of ticks. It even passes
`about_face`, a FREE_SWIM gap. ANCHORED: never seen in first person in air (spec: "pokes out of
the back", "planted never leaves its perch"); in water it swims like the rest.

**Bugs found and fixed along the way (each confirmed by a trace, A/B'd on 8 seeds):**
- Hand slots were always on the right: a left-shoulder crab's hop crossed the owner's face, and
  under the cellar ceiling it could never hop. Hands are now per side.
- Owner clearance on an arc was a fixed window (samples 30–70%), which on a 0.6-block hop is still
  touching the shoulder: every low arc failed. Now topological (leave the body, don't re-enter).
- Visibility was judged on last tick's position against this tick's camera; a crab on a walking
  owner's hand read as out of view (walk: seen 2% → 11%). **The spike's `Mind.perceive` judges a
  swimmer the same way** — left as is (it would change approved FREE_SWIM behaviour); for Phase 2.
- Hops met stair steps: the owner stepped a block in 1/8 s mid-hop. The steadiness gate took
  stairwell contacts 39 → 20 (height) → 3 (velocity too).

**Tried and reverted (mixed or worse on 8 seeds):** extra block margin for hops while the owner
moves (unseen time doubled); retargeting a hop whose target swung into a wall (stairwell contacts
39 → 83); water-entry spots beside/behind the owner (pond contacts 0 → 8).

**Named gaps.** Corridor and stairwell contacts (up to 5.6–7.4 b/s, rare); `pond_dive` inside-owner
up to 1.2% of ticks on one seed (the free-swim body under a diving owner; bound 1.5% there).

**For the owner's look.** ANCHORED is invisible in first person by spec — is that wanted? A hop
back from the water can pass right in front of the camera (legal, but may read as in-your-face).
Hops are a crouch-launch-settle; the animator's pose for them is Phase 3.

### 9.5 Step 1e — trios

**As built.** `CompanionGroup.isTrio(locomotion, sizeCm)` is spec §2's rule (FREE_SWIM and under
20 cm). The leader is a full `CompanionAgent`; the two followers swim the same heading-first
`Body`, so they turn under the same caps, stop for walls the same way and can't swim backwards.
Each follower keeps a loose station behind and to one side of the leader in the leader's frame
(2.2 and 1.8 body lengths, breathing with a slow drift), separation from the other two that grows
with its own speed, and the owner's soft push. With a wall between it and the leader it follows
the leader's own trail. Well off station (> 2 blocks) it may sprint to 1.4× the top speed: small
schooling fish dash to rejoin. Fades travel together; a follower more than 14 blocks off fades and
rejoins by itself. `TrioInvariantTest` checks the followers; the viewer and exporter have TRIO.

**Measured (8 seeds × 2 min).** Followers: 0 penetrations, never tail-first, turn ≤ 7°/tick,
jerk ≤ 130. Bodies passing through each other, measured on the drawn bodies (capsules ±0.4 × length
long, 0.12 × length wide): ≤ 1.3% of ticks on open scripts, 3% in the 1-wide tunnel. (Measured as
spheres of 0.3 × length it reads up to 6.8%, but the trace showed those were fish flank to flank at
matching velocity, not through each other.) No follower fades on any open script.

**Decisions, each A/B'd on 8 seeds.**
- The "lost" line at 8 blocks made followers pop out and back routinely (6–7 per 8 runs on open
  walks, 30–35 in doorway/forest): a follower facing away when the leader set off after a
  sprinting owner fell 8 behind before it could turn. At 14, with the sprint, it swims back
  instead: open-walk fades 0, doorway 30 → 0, forest 35 → 4; the cost is longer trailing in
  obstacle scenes (doorway straggle 3.9 → 9.1 s, stairwell 7.2 → 11.6 s).
- Braking for the nearest school-mate (instead of the owner) changed overlap by under half a point
  — reverted.

**Named gaps.** Followers bump trunks or stairwell steps at up to ~4 b/s while sprinting to rejoin
(bound 5 there, 3 elsewhere); the 1-wide tunnel is exempt from overlap.

### 9.6 Phase 1 — where it stands, and the owner's gate

| Class | State | In the invariant table |
|---|---|---|
| FREE_SWIM | spike body, plus the resting-contact fix (§9.2) | yes |
| Trio | built (§9.5) | yes (`TrioInvariantTest`) |
| BENTHIC, ANCHORED | built (§9.4) | yes |
| GLIDE | **parked** for a tank + companion refactor (§9.2) | yes, with tight-space gaps |
| DRIFT | **parked** (owner, 2026-10-11): first cut with open problems (§9.3) | no |

Every FREE_SWIM spike trajectory on open ground is still bitwise identical; only obstacle scenes
moved, from the contact fix. The new scenes and scripts are in `VoxelScene`/`OwnerScript`; the
viewer picks class, scene and owner (manual or any script).

**For the owner to look at** (`runCompanionViewer`, or the GIFs from
`runCompanionHeadless -PsimArgs="--class TRIO|BENTHIC|ANCHORED|DRIFT"`):
1. Trailing a constantly moving owner (FREE_SWIM `about_face`, `stairwell`): is it fine, as the
   owner thought?
2. Trios: does the school read as three fish travelling together, or as one fish with two stuck on?
3. Crabs: perch, the hop onto the hand, the water switch. A hop back from the water can pass right
   in front of the camera.
4. ANCHORED never shows in first person (by spec). Wanted?
5. DRIFT: stronger pulses as chosen, but it still lags after stops and turns (§9.3) — decide
   whether to design it further now or park it with GLIDE.

**Owner's answers (2026-10-11):** 1 — "Trailing looks fine." 2 — "Trio definitely reads."
3 — "Crab looks good!" 4 — ANCHORED invisible in first person: yes, as specced. 5 — open: the
owner asked which species drift. Only three are `upright_float` (so DRIFT): glass squid (jet
propulsion — the pulse body fits, as a strong, quick-turning jetter), flapjack octopus (pulses its
bell — fits), Portuguese man o' war (no propulsion of its own; sail-carried — the pulse body is
the wrong model for it). Owner: "Let's leave drifters for now" — **DRIFT is parked** as the
first cut in §9.3; when it returns, the squid and octopus likely want a jetter (quick to
reorient, strokes sized to land at the asked speed) and the man o' war a passive floater. The
`VISIBILITY_GAPS` for trailing (§9.1) are now accepted behaviour,
not gaps.

**Found for Phase 2:** the spike's `Mind.perceive` judges "seen" on last tick's position against
this tick's camera (§9.4); fixing it changes FREE_SWIM's attention timing, so it waits for the
attention work.

---

## 10. Phase 2 — order, gates, and as built

| Step | Builds | Gate |
|---|---|---|
| **2a** World snapshot (**built**) | `CompanionWorld` (owner, blocks, points of interest, held item, owner hurt) — one per tick, the only thing agents and trios step from | all FREE_SWIM fingerprints bitwise identical, and the new all-class baseline (`-PfpAll`: 4 classes and the trio × 18 scripts × 3 seeds) too |
| **2b** Seen timing (**measured, not adopted**) | the harness judges "seen" itself; the mind's one-tick-stale judgement A/B'd against a fresh one | 8-seed A/B over every script; adopt only a clear win |
| **2c** Points of interest in the harness (**built**) | `ScriptedPoints` tracks (mobs walking up, sitters, passers, companions alongside, item drops, a held-item schedule, hurt events); scripts `held_items`, `hostile_approach`, `cat_lounging`, `item_drops`, `companion_passes`; drawn in first person and the top view | old fingerprints unchanged; new scripts in the invariant tables |
| **2d** Curiosity → INSPECT (**built**) | `Drives` (curiosity with per-thing memory: novelty, habituation, cooldown); held items as curiosity targets with a pose hint; perchers hop to the hand for a held item, only look at things further off | invariants on every class including the new scripts; notice time, inspect time, second-look habituation |
| **2e** Wariness → HIDE (**built**) | hostiles (at once), cats (after a dwell), predator fish in the hand; dwell → habituation → cooldown (R7.2). Swimmers hide on the owner's far side, BENTHIC hops to the far slot or the head (cats), ANCHORED ducks in | never toward the threat; reaches cover in bounded time; habituates to a lounging cat |
| **2f** Rest → SETTLE (**built**) | rest builds while the owner idles; slower, closer, longer dwells; perchers stop swapping shoulders | rest rises on long idles; any movement or new thing wakes it within a bound |
| **2g** Temperaments (**built**) | all ten, as weights and timing on the above (spec §4) | invariants × temperament; per-temperament GIFs; **owner gate** |

### 10.1 Steps 2a–2c — as built

- **2a.** `CompanionWorld` + `PointOfInterest` (kinds HOSTILE, CAT, DROPPED_ITEM, COMPANION; a
  stable id so the mind can tell a cat that stayed from a new one) and `CompanionWorld.Held` (the
  spec §3.1 reaction categories; the adapter maps stacks to them). `heldPoint()` is where the held
  item draws the eye: the rod's tip out in front, otherwise the main hand low right in the view.
  All 303 fingerprints (33 spike + 4 classes × 54 + trio × 54) are bitwise unchanged.
- **2b — the seen timing (plan §9.4's open item).** First a metric fix: the recorded `seen` was
  the mind's own (stale) judgement, so the metric moved with whatever the mind did. The harness now
  judges it itself, from the post-step position against the same tick's camera. Same tree, old vs
  new metric (FREE_SWIM, 8 seeds): seen walk 19 → 23%, walk_turns 20 → 22%, others ±1; longest
  unseen unchanged except `about_face` 38.6 → 28.1 s (the old metric overstated that gap).
  Trajectories are untouched.
  Then the behaviour A/B on equal grounds (new metric both sides, 8 seeds × all 18 scripts, mind
  stale vs fresh): **mixed, not adopted.** Fresh was better on stairwell longest unseen
  34.0 → 23.2 s, forest inside-owner 102 → 63, near-clip ticks overall 558 → 492; worse on walk
  longest unseen 15.3 → 20.3 s, walk_turns inside-owner 22 → 52, doorway contacts 47 → 71,
  teleport longest unseen 18 → 22 s; seen % and show-offs/min within a point everywhere. No
  consistent direction — a one-tick shift in when attention resets, diverging chaotically. The
  swimmer keeps the stale judgement; the percher keeps its post-step one (there it mattered: a
  crab riding a walking owner's hand, §9.4).
- **2c.** Fish ignore everything so far, so the five new scripts measure the Phase 1 mind on new
  owner motions (FREE_SWIM seed 1: 0 contacts, seen 34–50%, longest unseen 16–29 s). They exposed
  one Phase 1 gap: with the owner standing still, a loiter spot 2–2.5 blocks straight ahead makes
  the fish slow through screen centre for 2.1–2.3 s (bound 2 s; `pickSpot` only keeps the central
  12° clear within 1.2 blocks). Named `CENTRE_DWELL_GAPS` (bound 50 ticks) until step 2f reworks
  the idle swim.

### 10.2 Step 2d — curiosity → INSPECT, as built

**The drive.** `Drives` is updated by every agent every tick, whatever its body is doing. Things it
can be curious about: the held item (spec §3.1 categories; a predator fish is fear, not interest),
dropped items and other companions within 10 blocks of the owner. Each is remembered by id:
- **noticed** over 0.5 s — a creature takes a beat before it reacts;
- **novelty** bait 1.0, rod and own species 0.9, other companions 0.8, name tag and dropped
  items 0.7, food 0.62;
- **habituation** only while the fish is actually close (`engage`): a few seconds of looking
  (rod and bait 6 s, food 2.5 s, others 4 s) and it's had enough; unattended things dull over 40 s;
- **cooldown** 30 s once satisfied; each later visit is 0.6× as interesting, until it has been
  gone a minute and is forgotten;
- **kind fatigue** (spec R7.2's item streams): each finished inspection of a dropped item or a
  companion tires the fish of that kind by 0.25 (max 0.8), wearing off over 90 s;
- world things count only within 4–5 blocks of the owner, so an inspection never fights the
  proximity drive.

**The goal.** `INSPECT` (swimmers, and perchers swimming in water): utility = curiosity; ends when
the thing is gone or no longer the most interesting. It swims to a stand-off spot — for a held item
in the view's own frame beside and beyond the hand, never deeper than keeps it 14° off centre (a
0.7-block glider's first version slid to screen centre); for a rod, by its tip; for a world thing,
a species-sized distance off on the fish's side, a little above — then holds it loosely and noses at
the thing at a creep. It carries the thing's velocity (a held item moves with a walking owner).
`PoseHint` tells the animator what the pose should say: EXCITED (rod), BEG (bait), NOSE (own
species, a dropped item), TILT (name tag), MILD (food), WATCH.

**Perchers.** A held item with curiosity ≥ 0.45 draws a BENTHIC percher to the hand (its own
side's hand — the Phase 1 rule that keeps a left-shoulder crab from crossing the face) and keeps it
there while interest lasts; only time on the hand habituates it. Anything else gets a look:
pose WATCH plus `lookTarget()` for the animator, and watching habituates. ANCHORED only watches.

**Measured** (`CuriosityProbe`, 8 seeds, `held_items`):

| Held | FREE_SWIM notice / engaged / seen while engaged | BENTHIC notice / on the hand |
|---|---|---|
| rod | 0.3 s / 4.9 s / 82% | 0.8 s / 3.0 s |
| bait | 0.6 s / 5.0 s / 92% | 0.6 s / 3.3 s |
| food | 0.4 s / 1.8 s / 69% | 1.0 s / 0.8 s |
| own species | 0.3 s / 3.4 s / 69% | 0.8 s / 2.3 s |
| name tag | 0.4 s / 2.9 s / 72% | 0.9 s / 1.6 s |
| predator fish | never | never |
| rod, second time | 0.5 s / 2.7 s / 70% | 0.9 s / 1.2 s |

Item stream (`item_drops`, seven items): 3.4 inspections per 2 min (7.0 before kind fatigue);
passing companions 4.0. Hostiles and cats draw no curiosity. All 303 earlier fingerprints are
bitwise unchanged (with nothing to be curious about, the drive is silent); invariants pass on every
class and script.

**Tried and reverted.** Keeping loiter spots out of the central 12° out to 2.5 blocks (the
centre-dwell invariant's own zone, against the spike's 1.2): over 8 seeds the longest centre dwell
fell on nine scripts (elytra 63 → 14, hostile_approach 55 → 13, idle_fixed 50 → 30 ticks) but forest
contacts rose 54 → 65 and cellar inside-owner 131 → 146, failing two invariants on the test seeds —
mixed, reverted. The 8-seed sweep also shows the centre-dwell gap is wider than §10.1 said: 40–63
ticks on nine scripts, Phase 1's included. `CENTRE_DWELL_GAPS` names the three scripts where the test
seeds catch it; step 2f's idle swim is where it gets fixed.

**For the owner's look.** A left-shoulder crab watches a held item from the left hand, not the
right one holding it. Is that fine, or should it cross over (a longer hop round the face)?

### 10.3 Step 2e — wariness → HIDE, as built

**The drive** (in `Drives`, beside curiosity). Sources: hostiles within 8 blocks of the owner
(strongest inside 3), cats within 5 (strongest inside 1.5), a predator fish in the hand. Each is
remembered by id, following spec R7.2:
- **dwell** before it alarms: a hostile 4 ticks (a reaction time), a predator fish 4, a cat 2 s;
- **habituation** while it stays: a cat over 15 s, a held predator over 20 s, a hostile over 40 s
  but only while it keeps more than 4 blocks off (one closing in never wears off); wariness falls
  to 30% at full habituation;
- **cooldown**: once used to a cat or a held predator, a minute before that one can alarm it
  again, and then it starts half used to it.

**The goal.** `HIDE`: utility 1.2 × wariness, above showing off and curiosity (curiosity is muted
while anything alarms it); over 0.6 it's an emergency and cuts commitment, like a catch-up. Cover is
on the owner's far side from the threat — behind the head from a cat or a held predator fish (spec
§3.1 "hides behind your head", §6), behind the body from a hostile — reached in a quick swim (up to
0.7 × catch-up speed, routed round the owner) and held loosely, nose toward the danger at a creep.
Pose HIDE.

**Perchers.** At wariness ≥ 0.4 a BENTHIC percher hops to cover and stays: onto the head from a cat
("flee onto your head"), otherwise to the shoulder on the far side; pose HIDE with the threat as
its look target. An ANCHORED fish ducks in (pose DUCK) when a hostile is within 3 blocks or it is
otherwise wary — it never leaves the back.

**Measured** (`WarinessProbe`, 8 seeds; wariness means are per 5 s across the script):

| | FREE_SWIM | BENTHIC |
|---|---|---|
| hostile_approach: reaction / far side while alarmed | 0.20 s / 91% | 0 s / 97% |
| cat_lounging: reaction / far side | 0.06 s / 90% | 0 s / on the head |
| held predator: reaction / far side | 0 s / 96% | 0 s / 97% |

The lounging cat shows the R7.2 shape: wariness 0.75 after the dwell, 0.55, 0.26, gone by 25 s,
and the cooldown keeps it gone. A zombie at arm's reach keeps it hidden throughout (0.96); a
skeleton standing 7 blocks off barely registers (0.05–0.09).
After 1.5 s in cover the swimmer is nearer the threat than its owner on 1.9% of alarmed ticks — all
of them in one moment (traced, 4 seeds): the owner walks off with a zombie trailing them and the
fish, in cover beyond it, has to pass it to keep up (never closer than 1.45 blocks).

All 303 earlier fingerprints bitwise unchanged; invariants pass on every class and script.

**Named gap.** `GLIDE_COVER_GAPS`: when a hostile walks round the owner the cover spot flips sides,
and a glider caught close to the owner pivots on the spot at its slow pivot rate for ~2 s, 0.45
blocks from the eye (near-clip 1.04% of ticks on one seed, bound 1.5% there). A swimmer turns
through it. GLIDE stays parked.

### 10.4 Step 2f — rest → SETTLE, as built (and a trio fix it exposed)

**The drive** (in `Drives`). Rest starts building once the owner has been still for 10 s, reaching
1 over a further 45 s (× the temperament's weight). It drains in 1.5 s once they move, in 1 s while
curiosity or wariness is stirred, and at once if the owner is hurt. Settled = rest crossed 0.5 with
the owner still, until it falls below 0.3 or they move. Off-hours (Phase 5) will add to the build
rate; for now there is no input for it.

**The goal.** `SETTLE` replaces LOITER as the idle base goal while settled: the same drift between
spots, at 0.6× the pace, with lingers 3× as long (4.5–15 s) and half the mill. Its spots alternate
strictly between the edge of the owner's view (15–28° off centre, 1.6–2.4 blocks out, a little
below the line of sight — a dozing fish to be seen) and close beside or behind them; none are in
the central 12° within 2.5 blocks. If the owner turns the view to put the spot in the middle, the
fish drifts aside after a second, and crossing the middle it never dawdles or lingers. Pose
SLEEPY while lingering. Show-offs aren't damped: attention stays the guarantee of being seen (a
first cut damped them and the fish went 30+ s unseen behind its owner). Perchers stop changing
shoulders while settled (pose SLEEPY) but still hop to the hand when unseen a while.

**Measured** (`RestProbe`, `SeedSweepProbe` with a rest-off temperament on the same tree, 8 seeds):
- FREE_SWIM over a 10-minute idle: first settles 32 s in, settled 95% of the time, 0.29 vs
  0.58 b/s loitering, seen 49%. It wakes on the tick its owner sets off.
- Rest off → on: idle_fixed seen 35 → 44%, longest centre dwell 50 → 32 ticks, show-offs
  1.13 → 0.69 /min (it's seen at the edge of view instead); idle_looking seen 32 → 40%, longest
  unseen 21.3 → 27.2 s; camera_sweep centre dwell 30 → 11; companion_passes 43 → 13. Cost:
  item_drops inside-owner 9 → 20 ticks (8 runs). Walking and obstacle scripts unchanged.
- Fingerprints: of the 33 spike runs only idle_fixed, idle_looking and camera_sweep changed (as
  meant); the same three for GLIDE and BENTHIC; ANCHORED none.

**Exposed: trio followers stuck behind a still owner.** With the leader idling on the far side,
a follower pressed against its owner's back sat there for 4–9 s: its station pull pointed through
the owner and the body braked for them (traced: follower 0.4 blocks from the owner's axis, its
line to the leader passing 0.08–0.28 from it). Followers now take the leader's round-the-owner
waypoint (`Mind.aroundOwner`, made static and shared; the leader is bitwise unchanged) when within
0.7 blocks of the owner's body in open water (clear 2.5 blocks every way at its height).
Five variants were A/B'd on 8 seeds × 23 scripts. Every one fixed the open-ground straggle
(idle_fixed and camera_sweep 3.8 → 0 s, hostile_approach 9.4 → 3.0, cat_lounging 5.5 → 2.5,
item_drops 4.8 → 1.5, companion_passes 6.0 → 2.3). Without the open-water condition, cramped
scenes paid for it (corridor contacts 107 → 161, waypoints beside the owner in walls). With it, the
obstacle-scene differences left are the size of the chaos floor, measured by A/B'ing two
near-identical variants (forest contacts 221 vs 195, cellar max impact 1.45 vs 4.66 b/s): forest
straggle 7.2 → 9.9 s appears identically with the open-water reach at 1.5 or 2.5 blocks — routing
that can't happen inside the forest's 2-wide lanes — so it is one early divergence, not a cost.
Adopted. All trio fingerprints change.

**Named gap.** `GLIDE_COVER_GAPS` gains `cat_lounging` (cover behind the head; the same glider pivot).

### 10.5 Step 2g — temperaments, as built

**The table** (`Temperament`; spec §4: temperament changes *how*, never *which*). `BASELINE` is the
Phase 1 behaviour, every weight 1. The ten from the data pack are departures from it:

| Temperament | Weights and timing | Its own move |
|---|---|---|
| docile | band 0.8, rest 1.5, dwell 1.3, wariness 0.8, a little stillness | **NUZZLE**: after 5 s of the owner standing still, over to the side of their head for 1.5–2.5 s; cooldown 25–45 s, doubling with each nuzzle in the same still spell (R7.2) |
| skittish | wariness 2.0, curiosity 0.7, keeps 1.6× further from things it inspects | **peeks out** of cover sideways for a second every 2–4 s |
| aggressive | curiosity 1.1 | **GUARD**: against a hostile it takes station between the owner and it, puffed up (PUFF), instead of hiding; **bites** the air (BITE, a short lunge when guarding) each time the owner is hurt. A percher squares up on the near shoulder; an eel leans out instead of ducking |
| sprinter | pace 1.2, rest 0.6 | **races ahead** (2.6 blocks) while the owner sprints, excited |
| ambusher | curiosity 1.3, dwell 1.5, stillness 0.6 | **stalks** dropped items: creeps to twice the distance, holds still 1–2 s, then darts in |
| wily | curiosity 1.2 | its pet-to-pet role is Phase 4 |
| ghost | rest 2.0, dwell 2.0, decisions 1.3, pace 0.8, stillness 0.85 | long, still hovers |
| erratic | decisions 0.5, dwell 0.6, commitment 0.7, jitter 1.5 | jittery timing |
| trembler | jitter 1.25 | TREMBLE pose at rest (the animator's shiver) |
| metronome | regular 1.0, jitter 0.8 | every interval the middle of its range |

Perchers take the weights that apply to them (wariness, curiosity, rest, how often they change
shoulders) and express the moves as slots and poses. The animator gets everything as `PoseHint`s:
EXCITED, BEG, NOSE, TILT, MILD, WATCH, HIDE, DUCK, SLEEPY, PUFF, BITE, PEEK, NUZZLE, TREMBLE.

**Measured** (`TemperamentFeatureProbe`, FREE_SWIM, 8 seeds, baseline → temperament): docile 0 →
4.6 nuzzles per 10-minute idle (4% of the time); sprinter rides > 1 block ahead on 7 → 25% of
sprinting ticks; ambusher stalks/holds 14% of `item_drops`, top inspect speed 1.89 → 2.61 b/s (the
dart); aggressive guards 33% of `hostile_approach`, 99% of it close in front of the owner; skittish
peeks 3.7%; ghost idles at 0.40 → 0.19 b/s and is settled 71 → 78%; trembler shivers 23% of an idle.

**How the table judges a temperament.** A temperament changes every trajectory, so its three test
seeds land on fresh samples of the baseline's own tails. `TemperamentSweepProbe` measured how often
each breaks each bound against the baseline on the same seeds: over 40 seeds × the cramped scripts
the *baseline* breaks the cellar's inside-owner bound in 1 run in 11, so fixed per-run bounds over
ten temperaments are a lottery. So the temperament rows (FREE_SWIM and BENTHIC × 23 scripts × 3
seeds) keep every hard invariant unchanged — never in a block, body limits, never tail-first,
facing, commitment — and hold each statistical bound to the looser of the original and 1.5× the
baseline's worst run of 40 seeds, computed by the test. Real temperament effects it found, fixed:
- **ghost and ambusher parked in the owner's face** (longest centre dwell 201 and 132 ticks against
  the baseline's 63): a still fish lingering at a centred spot. Fixed for everyone (below).
- **a jittery heading in a tunnel** (skittish 14 contacts/min in the corridor; trembler and erratic
  +20–35% contacts overall): a jittery temperament now steadies itself between walls.
- **an acceleration-cap breach** (erratic catch-up, aggressive's cover dart): the body capped yaw
  and pitch separately, so a fast turn while climbing overran `maxLateralAccel` by up to 2%. The
  cap now holds for the combined turn at speed. A/B (8 seeds × 23 scripts): baseline identical
  everywhere (it never engaged); erratic only pond_dive and wading change (max accel 16.8 → 15.5).
  GLIDE is left out (parked; its low cap would engage constantly). HIDE's dart is also capped at
  0.7 × catch-up speed all told.

**The loiter centre gap, fixed** (§10.1–10.2's `CENTRE_DWELL_GAPS`, now empty). Every loiter keeps
the middle of the view clear the way SETTLE does: it never lingers there, and if its spot or the
fish ends up there (the owner turned to it) it is off elsewhere after a second. The earlier attempt
(keeping spots out to 2.5 blocks) was mixed; this one leaves spot choice alone. A/B (FREE_SWIM, 8
seeds × 23 scripts): worst centre dwell 63 → 37 ticks (elytra 63 → 14, held_items 60 → 30, pond_dive
46 → 18, teleport 36 → 11); forest contacts +9% and cellar inside-owner +5% (inside the chaos floor
of ±13% measured with near-identical variants); teleport, wading, creative_flight and item_drops
better. Still temperaments also keep spots out of the centre to 2.5 blocks.

**Named.** `FOREST_IMPACT_GAPS`: a swimmer catching up through trunks meets one at up to ~4 b/s —
3 of 40 seeds on the Phase 1 code (max 4.15), 4 of 40 now; bound 4.5 there. `PERCHER_TIGHT_SPACE_GAPS`
gains `doorway` (a shoulder-to-shoulder hop as the owner walks through meets the frame, 5.3 b/s on
docile). `DOCILE_TIGHT_GAPS`: riding 20% tighter, a docile fish is caught a little more often by an
owner jumping up through it (+7% inside-owner ticks over 40 seeds; bound 1.5% on `jumping`).

**Fingerprints.** Of the 33 spike runs, step 2f changed the three long-idle scripts (rest) and 2g
six more (the loiter centre rule: corridor ×2, elytra, forest, sprint_stop, teleport); the rest are
bitwise as at 8783e094. GLIDE moves only through the shared mind's loiter rule; its body is untouched.

**Viewer.** A temperament picker, and in manual mode keys to make things happen: H cycles the held
item, Z a zombie walks up, C a cat strolls over, I drops an item ahead, O another companion swims
past, K the owner gets hurt, X clears them. The overlay shows curiosity, wariness and rest bars and
the pose hint.

### 10.6 For the owner's gate

`./gradlew :fishsim:runCompanionViewer` (pick a temperament; H/Z/C/I/O/K in manual mode), and the
GIFs from `runCompanionHeadless -PsimArgs="--temperament <name> --script <script>"` (built:
`build/companion-export`).
1. Held items: the swimmer comes over and noses at each (rod tip, bait, its own species), a second
   rod gets a shorter look, a predator fish sends it behind your head. Do these read as reactions?
2. A left-shoulder crab watches a held item from the *left* hand (the Phase 1 rule keeps it from
   crossing your face). Fine, or should it cross to the item's hand?
3. Hiding: behind you from a zombie in front, in front (low in view) from one behind; onto your
   head for a crab when a cat comes. Right amount, right speed?
4. Rest: after ~30 s of standing still the fish settles and dozes at the edge of your view. Too
   soon, too late?
5. Temperaments: the aggressive fish bites on every hit (a zombie at arm's reach hits once a
   second) — too much? The sprinter overshoots ahead after a sprint-and-stop — lively or annoying?
   Docile nuzzles about every 2 minutes of standing still. Which read as the species, which don't?

**Owner's answers (2026-10-11): Phase 2 passed the gate.** 1 — "the held item reactions look
really good and def read." 2 — a left-shoulder crab watching from its own hand: fine as is.
3 — settling after ~30 s idle: yes. 4 — temperaments (aggressive bites, sprinter overshoot,
docile nuzzle rate): "already nicely balanced on all these fronts." Phase 3 (into the game) is next.

---

## 11. Phase 3 — into the game, as built (2026-10-11)

| Step | Builds | Gate |
|---|---|---|
| **3a** Adapter | `CompanionWorldAdapter`: blocks (non-empty collision shape = solid; water from the fluid state), owner state, points of interest, held item, hurt | 378 fingerprints (`-PfpAll`) bitwise identical to HEAD (equal grounds: a HEAD worktree and this tree, same probe) |
| **3b** Switch and delete | `ClientCompanion` is now the sim's `CompanionAgent`/`CompanionGroup` plus the bonded stack and drawing; `CompanionFlock` deleted | both loaders build; the self-test's `companionsim` scene passes on both |
| **3c** Rendering | `CompanionRender`: world pose → `FishAnimator`; `CompanionPerchLayer` for riding perchers | shots on both loaders |
| **3d** Debug | overlay and `/fishtastic companion` triggers | every trigger works solo |
| **3e** Cost | sampling cost measured in game | **owner's solo playtest** |

### 11.1 The adapter

- **Blocks.** One `LevelBlocks` per client level, shared by every companion. It has to be one object:
  `LocalVoxels` forgets everything it sampled whenever the source object changes. Block changes
  (`LevelRenderer.blockChanged`) and chunk arrivals (`ClientLevel.onChunkLoaded`) make every
  companion forget its window; cells are resampled lazily.
- **Owner.** Eye, and velocity from the tick-to-tick eye move. A move of more than 8 blocks in a tick
  is a teleport (velocity zeroed); a dimension change or respawn rebuilds the companion anyway.
  View yaw/pitch, sprinting, water at the chest. The camera (first person or not) and FOV are only
  known for the local player; anyone else's companion assumes first person at 70°.
- **Hurt (decided).** Vanilla broadcasts the hurt animation to every client, so each client reads
  `hurtTime` rising for any owner. No packet of our own; remote companions react too.
- **Points of interest.** Within 12 blocks (8 up and down) of the owner: cats and ocelots, `Enemy`
  mobs, item entities (stable entity ids). **Other companions (decided):** built from the agents
  this client already runs, within 16 blocks, id = −1 − owner entity id.
- **Held.** The old `reactions` order: rod (vanilla or `#fishing_rods`), `#fishing_bait`, own
  species, `#predator_fish`, name tag, anything with a food component.
- **Seed.** From the owner's UUID and the bonded fish's item id and size, so it is the same on
  every client (the old `stack.hashCode()` was an identity hash).
- **Temperament.** The fish profile's `temperament` key → `Temperament.named(path)`.
- **Trio.** `CompanionGroup.isTrio` (FREE_SWIM under 20 cm).
- **Length.** The sim and the drawing both use the true-to-scale length with a 0.25-block floor
  (the old follow floor). The perch layer uses the same floor, so a fish is the same size on a
  shoulder as when it hops off.
- **DRIFT (owner's call, 2026-10-11):** the current first-cut `DriftBody` runs in game as it is.

### 11.2 Rendering

- **World pass** (`CompanionRender`): translate to the interpolated position, turn to the heading
  (`YP(−yaw)`), pitch the nose (`XP(pitch)`; a drifter's pitch is its bell axis, upright −90, so it
  tilts by pitch + 90). Then the class's own `FishAnimator` entry point with the sprite's nose
  forward: swimmers through `applySwimming` on the tail clock (the sim's wrapped tail phase
  unwrapped per frame into ticks, × the old 0.55 swim clock), with bank and speed factor; gliders
  `applyGliding` with bank/28°; drifters `applyDrifting` with the pulse as the bell; benthic
  `applyBenthic` with the crouch squash; anchored `applyAnchored` with the duck. Scale = length ×
  appear (the fade). Trio followers are drawn the same way from their own poses.
- **Perch layer.** A percher riding a shoulder, the head or the back is drawn on the player model
  (slot from the sim). Flat poses (starfish) are stood up on the shoulders *and* now on the head,
  lifted by half their length — the sim can choose the head, and lying flat there it was a sliver.
- **Hand.** A percher riding the hand is the world pass's. For its owner in first person it is
  attached to the view at frame rate (`PerchBody.slotPoint` at the frame's camera, the parrot
  pattern), so a mouse flick doesn't leave it a tick behind.
- **Pose hints mapped** (where the old companion had an equivalent): DUCK → the anchored retract
  envelope; TILT (name tag) → a 25° head roll; BEG → nose up 15°; MILD → nose up 5°. Each is eased
  per tick and drawn the same in the world pass and the perch layer.
- **Not mapped (owner to decide how they look):** EXCITED, NOSE, WATCH, HIDE, SLEEPY, PUFF, BITE,
  PEEK, NUZZLE, TREMBLE. The *moves* behind them all happen (the sim swims to the item, hides,
  nuzzles, lunges); only the pose colouring is missing. The overlay shows the live hint.

### 11.3 What went

`ClientCompanion`'s motion and behaviour (orbit, figure-eight, peeks, tricks, perch paths, the
follow steering, the frame yaw, blend weights, docking, at-the-line, scenes): about 2,000 lines.
`CompanionFlock` (the follow box): deleted. Every old-controller dev hook (trick, peek, outcome,
scene, legacy/bisect switches), and the self-test scenes built on them (`companion`, `parade`,
`peek`, `hand`, `fishing`, `scenes`, `polish` 1–3). Kept: bond/equip/sync, the packets (the
fishing-event and scene handlers are no-ops until fishing and Phase 4 return), the Companions tab
and its `companionbond` scene, the encyclopedia, `CompanionFishPose`, and the parked dock/PiP
renderer (registered, draws nothing).

### 11.4 Debug triggers (solo playtest)

```
/fishtastic companion debug true|false            overlay: species, class, temperament, goal, perch state,
                                                  pose hint, fade, drive bars, held, points, sim cost
/fishtastic companion temperament <id>|species    force a temperament (rebuilds the companion)
/fishtastic companion fake hostile|cat|item|companion|clear
                                                  scripted things laid out from where you look:
                                                  a zombie walks up to arm's reach (25 s), a cat strolls
                                                  in from the right and sits (40 s), an item 2.5 blocks
                                                  ahead (20 s), a companion swims across in front;
                                                  each marked with particles
/fishtastic companion hurt                        it sees you hurt (aggressive bites)
/fishtastic companion drowsy                      as if you'd idled long enough to settle
/fishtastic companion phantom <species> [size]    another companion round an imaginary owner 4 blocks ahead
```

Real mobs, cats, dropped items and held items all work too (`/summon zombie`, Q to drop).

### 11.5 Measured (plan §7 risk 3)

Self-test `companionsim` (NeoForge, superflat; Fabric the same within noise):
- Standing: 0 block samples per tick once the window is warm; the whole sim step is 20–90 µs per
  tick, worst 430 µs (a trio's first tick).
- Walking at 4 b/s for 10 s (the window re-centres every 6 blocks): 3 samples per tick on average
  (~1 µs), **peak 25 samples in one tick**, worst step 126 µs.
- Sampling is not worth optimising; the lazy window does its job. Dense terrain is unmeasured but
  costs the same per cell.

### 11.6 Known gaps and things to look at

- **A hop starts from the model's shoulder, not the sim's slot.** The perch layer places a rider on
  the player model (body yaw); the sim's slot follows the view yaw, so at the push-off the fish can
  jump a few centimetres when head and body point different ways.
- **The garden eel on the back** is the old planted pose: a sliver poking out of the upper back.
- **The Fabric dev run has Iris with Complementary on**, so Fabric shots are under shaders; NeoForge
  has none. The perch layer draws under both.
- **Phantoms** have an imaginary owner who never moves; a percher phantom sits on the imaginary
  shoulder in mid-air.
- **Two real clients** remain untested (as before).

### 11.7 For the owner's gate

Solo playtest, both loaders: `/fishtastic companion give @p fishtastic:<species> [size]`, then
`/fishtastic companion debug true`.
1. Does it read as the same creature as in the viewer: a trio (bluegill 12), a solo swimmer
   (bluegill 25), a starfish riding and hopping, the eel, a drifter, a ray (GLIDE parked)?
2. Held items, a real or fake zombie and cat, dropped items, standing still for ~30 s.
3. The unmapped pose hints (§11.2): which need a look of their own, and what?
4. Anything that pops: hop starts (§11.6), fades, teleports, entering water.
