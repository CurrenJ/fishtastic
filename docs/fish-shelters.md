# Shelters — Fish That Use the Cosmetics

**Written:** 2026-10-02 · **Status:** draft spec, nothing built · **Idea:** #22 in
[`cosmetic-ideas.md`](cosmetic-ideas.md) ("hiding caves and holes").

**Scope:** `:fishsim` engine + `TankFlockAdapter` / `TankGroupFlock` + `CosmeticStructure`
data + the cosmetic capture command. Companion to
[`fish-sim-engine-plan.md`](fish-sim-engine-plan.md), [`fish-swarm-realism.md`](fish-swarm-realism.md)
and [`fish-sim-locomotion.md`](fish-sim-locomotion.md). Their invariants are binding here. In
particular the binary single-tank model (`Tunables.DEFAULT`, `FlockEngine.stepFish`) is
bitwise-locked by `GoldenTrajectoryTest` / `ParityTest`, and **nothing here may perturb it**.

---

## 1. What we want to see

A clown loach noses along the sand, turns into the mouth of the hollow log and is gone. A few
seconds later it backs out. A player walks up to the glass and the two discus bolt into the clay
pipe, then ease back out once the player stops moving. A bichir lives in the rock cave with only its
head showing at the entrance, and slides out now and then to cruise the tank.

Fish going into and out of things is the best moment a tank can give on video, and it is the first
time a cosmetic *does* something. It also gives players a reason to arrange tanks deliberately: a
den only works if its mouth faces open water, and only the fish small enough to fit will use it.

### 1.1 Non-goals

- **General water obstruction.** Swimmers still pass through ordinary cosmetics after this work.
  Only shelters become solid to swimmers (§4). Blocking the water for every cosmetic needs a
  sub-block `DistanceField`, which is a separate decision ([`fish-sim-locomotion.md`](fish-sim-locomotion.md) §4.1).
- **Pathfinding.** Fish reach a shelter by steering, not by planning. A shelter they can't reach
  is one they give up on (§5.4).
- **Anything server-side or authoritative.** The simulation stays client-only and cosmetic. Two
  players may see different fish in the log.
- **Maintenance mechanics.** Shelters are never a need, a stat or a happiness meter. A fish without
  a shelter behaves exactly as it does today.
- **New rendering paths.** Hidden fish are hidden by the depth buffer, because the shelter's blocks
  are opaque and already drawn. Nothing fades or culls.

---

## 2. Where things stand

| Fact | Consequence for this work |
|---|---|
| Lone tanks run the **binary** 2.5D model (`TankFlockAdapter`, `Tunables.DEFAULT`); only multi-tank groups run the **planar** model (`TankGroupFlock`, `Tunables.GROUP`, continuous yaw). | The binary model can't learn anything new. A lone tank needs a way onto the planar model before its fish can use a shelter (§7). |
| Cosmetics obstruct the **floor** (`FloorField`, 3×3 cells per block, `TankFloors`); swimmers ignore them. | A hollow log is currently a ghost to swimmers. Shelters need a solid hull (§4), or a fish swimming through the log's wall ruins the fish swimming through its mouth. |
| Cosmetic changes are watched by fingerprint and rebuild only the floor (`VoxelDomain.rebuildFloor`). | Shelters ride the same fingerprint and rebuild in the same pass. No new invalidation path. |
| The engine has a **watcher**: `FlockEngine.setWatcher`, fed the local player's eye position each tick, with edge-triggered, positive-evidence arming (`anchorArmed`). | The startle-to-shelter trigger (§5.3) reuses it rather than inventing a second one. |
| `ANCHORED` eels already "hide". | Their three lessons apply directly: react to an event rather than a state, bound the hidden fraction independently of stocking, and treat "no signal" as "unknown" rather than "absent". |
| Rendered fish lengths run from **0.08** (neon goby) through **0.19** (neon tetra), **0.26** (clown loach) and **0.36** (ornate bichir) to **0.55** (electric eel) blocks. | The new Hollow Log's hollow is 0.18 × 0.18 blocks and 0.45 long. Shelter size gates are real numbers that decide which fish can use which shelter (§5.2), not a formality. |

---

## 3. Data: what a shelter is

### 3.1 On the structure

`CosmeticStructure` gains one optional field. Structures without it are unchanged.

```json
"shelter": {
  "interior": [ { "x": 1, "y": 1, "z": 1 }, ... ],
  "capacity": 2
}
```

- **`interior`**: the build-grid cells (the same integer grid the parts' offsets come from, before
  `scale`) that make up the hollow. These are the cells a fish may occupy while inside.
- **`capacity`**: how many fish may be inside at once. Optional. It defaults to the number of
  interior cells ÷ 4, with a minimum of 1.

Everything else is **derived at load**, so there's nothing for an author to get wrong:

- **Mouths** are the interior cells' faces that open onto a non-part cell from which the outside of
  the structure's bounding box can be reached by flood fill. A hollow with no mouth fails
  validation ("shelter interior is sealed"). A face that opens into a pocket that doesn't reach the
  outside is not a mouth.
- **The hull** is the bounding box of the parts adjacent to the interior: the walls the fish must
  not pass through. It's a box, not the exact shape. §4.1 explains why that is enough.
- Each mouth carries a **centre**, an **inward normal** and a **half-size** (its opening in
  blocks after `scale`).

Rotation follows the footprint: the codec stores the unrotated shelter, and `Rotation` is applied
at placement exactly as `footprintAt` does today.

**Span structures** (`whale_fall`, `drowned_pagoda`) carry the same field in their box's build
grid. `tools/span-structure-gen/gen.py` emits it. The whale fall's rib cage is the obvious first
spanning shelter.

### 3.2 Authoring: markers in the capture

Hand-listing interior cells is error-prone, so the capture command learns a marker. During an MCP
build, `minecraft:structure_void` placed in the hollow marks interior cells. `/fishtastic cosmetic
capture` strips them from `parts`, writes them as `shelter.interior`, and runs the mouth
derivation immediately, so a sealed hollow is a capture error rather than a datapack load error.
The `fishtastic-mcp-builder` and `cosmetic-from-description` skills get a short section on it.

`structure_void` is chosen because it is invisible and is never a real cosmetic part.

### 3.3 On the species

One new optional field on the per-species swarm config (`SwarmConfig`, already the home for
per-species simulation tuning, so no new codec):

```json
"swarm": { "shelter": "visitor" }
```

| Value | Behaviour | Starting cast |
|---|---|---|
| *(absent)* | Never uses shelters. The default, so **no existing profile changes**. | everything else |
| `visitor` | Occasional unhurried visits: in, linger, out. | clown loach, neon goby, yellowline goby, rainfordia, blind cave tetra |
| `skittish` | Visits occasionally, and bolts for cover when the watcher approaches (§5.3). | neon tetra, fire dartfish |
| `lurker` | Claims one shelter as home and rests in its mouth facing out; makes short sorties (§5.5). | ornate bichir, ophisternon candidum, black ghost knifefish |

**Shipped cast (Phase 5).** This differs from the first pass in four places. The discus is out: it is a tall disc, so the 0.4 height ratio in the mouth gate (§5.2) is wrong for it and it would clip through a 0.18 mouth. The lizardfish is out: at a mean of 0.47 blocks it fails the Hollow Log's mouth gate, and it's a sand-percher, not a cave fish. The glass catfish is out: it's a mid-water schooler, and ducking into a log works against how it looks. The blind cave tetra moved to `visitor`, because a blind fish shouldn't bolt from a watcher it can't see. Rainfordia (a reef goby that keeps to the rockwork) and the fire dartfish (which dives into its burrow when startled) were added. Every opted-in species uses the `horizontal_swim` pose, since shelters only take `FREE_SWIM` fish.

**Considered and rejected: deriving this from `temperament`.** Temperament has suggestive names
(`skittish`, `ambusher`, `ghost`), but it tunes the fishing minigame. Coupling the two would mean a
minigame difficulty change silently re-tunes tank behaviour. The mapping is also wrong in places:
the clown loach, the most famous cave-hider in the hobby, is `erratic`. It is an explicit per-species
authoring decision, and roughly a dozen species need it.

---

## 4. The hull: shelters are solid

### 4.1 Avoidance

`FlockDomain` gains `shelters()`, which returns the domain's shelter records in the engine's local
frame. Each swimmer adds a soft repulsion from every shelter hull within margin. This is the same
proximity ramp `Box.axisAvoidance` uses, computed against an oriented box, and it is summed into the
existing avoidance vector.

**Except the fish's own corridor.** A fish in the `ENTER`, `INSIDE` or `EXIT` state (§5.1)
ignores the hull of the shelter it is using, and instead is confined to that shelter's mouth
corridor and interior. Every other fish still sees the hull.

A box is enough because a shelter is, almost by definition, a compact enclosed thing. If a future
shelter is a long arch where a box is visibly wrong, the record can grow to a short list of boxes.
That is an extension, not a redesign.

### 4.2 The backstop

The domain's hard `constrain()` does **not** learn about hulls. Soft containment must stay soft:
`backstopEngagements() == 0` remains an invariant. A new invariant takes the hull's place: *a
swimmer not using a shelter is never inside its hull*, measured over the test matrix. The soft term
is tuned until that holds, exactly as wall avoidance was.

### 4.3 Things placed on top of fish

A shelter can appear around a fish: the player right-clicks a log onto a spot a fish is crossing.
That fish is **adopted**: it's put in `INSIDE` for that shelter and leaves by the nearest mouth. It
never gets an ejection impulse or a teleport. Capacity is exceeded briefly in this case and nothing
else enters until it's back under. A shelter removed with fish inside drops them straight to
`ROAMING` where they are, which is open water now that the hull is gone.

---

## 5. The behaviour

### 5.1 One state machine, layered on the swimmer

```
ROAMING ──(trigger, §5.3)──▶ APPROACH ──(at mouth, aligned)──▶ ENTER ──(inside)──▶ INSIDE
   ▲                            │                                                  │
   │                       (timeout §5.4)                                     (dwell over)
   └──────── cooldown ◀──────── │ ◀──────────────────── EXIT ◀────────────────────┘
```

| State | Steering | Notes |
|---|---|---|
| `ROAMING` | today's planar flocking, unchanged | everything below is skipped while `ROAMING` |
| `APPROACH` | desired velocity toward a **staging point** one body length outside the chosen mouth, along its normal; flocking weights fade toward zero | ordinary wall and hull avoidance still apply |
| `ENTER` | along the mouth's inward normal at a reduced speed; the turn-rate cap still applies | starts only once the fish is at the staging point and its heading is within ~30° of the normal |
| `INSIDE` | slow OU drift inside the interior box, speed at about 20% of patrol; separation applies between occupants | the fish is drawn where it is; the shelter's blocks hide it |
| `EXIT` | out along the mouth's outward normal to the staging point, then flocking fades back in | |

Per-fish state: the state, the shelter index, the mouth index and one timer. It's carried across
`rebuildPreserving` like `anchorTimer` (extend `RebuildCarryTest`). If a rebuild loses the shelter
(it was removed, or the index no longer matches), the fish goes to `ROAMING`, as in §4.3.

### 5.2 Who fits where

A fish may choose a shelter only if both of these hold:

- **The mouth admits its height:** `0.4 × length ≤ 2 × mouthHalfSize`. Fish sprites are roughly
  0.4 times as tall as they are long, and the item is thin, so height is what has to fit.
- **The interior holds its length:** `length ≤ 1.1 × interiorLongestRun`. This doesn't apply to
  lurkers, which need only `0.6 × length`: their head stays in the mouth.

So the Hollow Log (0.18 × 0.18 mouth, 0.45 long) takes a tetra, a loach or a goby, and only just
takes a knifefish. A bigger log takes the bichir. This is deliberately visible to players: shelter
size is a design choice they make, and the shop's descriptions can say "for small fish".

### 5.3 Triggers: events, not states

Three ways into `APPROACH`, each written as a change rather than a condition. That lesson cost
three rounds on the garden eels:

1. **Spontaneous visit** (`visitor`, `skittish`). A per-fish Poisson clock at a low mean rate
   (start with one visit every ~40 s, jittered ±35% off the seed like every other per-fish
   constant), which fires only while `ROAMING` and outside the cooldown.
2. **Startle** (`skittish` only). The watcher *arriving* within 3 blocks of the fish, using the
   eels' exact arming rule: armed only by positive evidence that the watcher left (present and
   beyond 1.5× the radius), and starting unarmed. A startled fish uses `APPROACH` with burst speed
   and a short timeout. If no shelter fits or has room, it does nothing new: the startle is a
   reason to hide, not a new scatter behaviour.
3. **Home** (`lurker` only). See §5.5.

The chosen shelter is the **nearest one that fits and has room**, measured to its staging point.
The choice is made once on entering `APPROACH` and never re-scored, because a fish that keeps
changing its mind reads as indecisive, not alive.

### 5.4 Bounds that don't depend on stocking

- **Dwell:** `INSIDE` lasts 3–8 s for a visit and 5–10 s after a startle. A startled fish leaves
  only once the watcher has stopped *approaching* (it is no closer than a second ago) and the dwell
  minimum has passed. A player standing still is furniture, as with the eels.
- **Cooldown:** after `EXIT`, at least 20 s of `ROAMING`, so a fish is always seen out in the open
  for a good while between visits.
- **Hidden budget:** at most `max(1, ⌊0.25 × swimmers⌋)` fish in a domain may be in `ENTER` or
  `INSIDE` at once, counting lurkers resting in a mouth as visible. A trigger that would exceed it
  simply doesn't fire. This is the tank-wide guarantee that a well-stocked tank never empties into
  its caves, whatever the stocking.
- **Approach timeout:** `APPROACH` gives up after 6 s (2 s for a startle) and goes to cooldown.
  This is what makes "no pathfinding" safe. A fish behind the L-bend of a group, a mouth facing the
  glass, or a mouth blocked by another cosmetic all end the same way: a short purposeful swim
  toward the shelter, then back to normal. A mouth whose staging point is outside the domain or
  inside another hull is disabled at domain build, so the common blocked case never starts.

### 5.5 Lurkers

A lurker **claims** the nearest shelter that fits at rebuild, one lurker per shelter. A claimed
shelter's capacity drops by one for everyone else. A lurker with nothing to claim behaves as a
`visitor`.

Its home loop is `ENTER` to a **mouth pose**, and the pose is the whole effect: body inside, head at
the mouth plane, facing out along the normal and holding position with a slow sway. Every 30–60 s
it makes a sortie (`EXIT`, about 10 s of `ROAMING`, then back to `APPROACH` its own shelter with no
cooldown). The mouth pose counts as visible for the hidden budget, because it is the most visible
thing in the tank.

### 5.6 Edge-on fish

Entering a mouth that faces the camera means swimming into the screen, which shows the sprite
edge-on for a moment. The planar model already allows this whenever a fish swims along the depth
axis, and the extruded item mesh keeps it a dark sliver rather than nothing. The planar model has no
broadside invariant to break. Watch for it in the in-game look. If it reads badly, the fix is to
prefer mouths whose normal is closer to lateral when a shelter has more than one.

### 5.7 The bob in a hollow

Found in the first in-game look: a loach in the Hollow Log clipped through its floor and roof. The
renderer draws a vertical bob on top of the engine's position. It's ±0.125 blocks by default,
against a bore 0.18 tall, and the engine never sees it. Two fixes:

- **The engine confines the sprite, not just its centre.** The interior confinement's vertical
  ramp is inset by the sprite's half-height (`0.4 × length ÷ 2`, the mouth gate's ratio). The inset
  moves where the ramp starts but not its slope or its peak: squeezing the ramp broke the jerk
  bound for a startled fish.
- **The renderer caps the bob at the room left.** `FlockEngine.renderBobRoom` is the headroom
  above and below the sprite in the hollow, opening up at 0.5 blocks per block once the fish is
  more than half a body length outside. It's a function of position only, never of visit state,
  so it has no jumps. `FishAnimator` caps the bob's amplitude at it, after taking off the share
  the bob's tilt adds at the nose. Fish swimming close past a log lose some of their bob too,
  which is right: they'd clip its outside the same way.

`ShelterBobRoomTest` holds both: no sprite past the bore at the top or bottom of its capped bob, and the
room changing no faster than the bob itself moves while a fish enters.

---

---

## 6. Benthic dens (later)

The octopus is the most iconic den animal there is, and it is `BENTHIC`. A floor-level shelter
(interior cells on the bottom layer) can be walked into: the crawl tests moves against the floor
field, so the shelter's interior floor cells become walkable floor, and the hull's other floor cells
stay blocked. It's the same state machine, with `stepBenthic` instead of planar steering. This is
deliberately a later phase: it touches the floor field's blocked-cell logic, which Phase 1 of the
locomotion work made load-bearing.

---

## 7. Lone tanks

The single-tank binary model is bitwise-locked, so it can't learn shelters. Most players keep
plenty of single tanks, and a log that only works in a group would feel broken. The options:

| Option | Cost |
|---|---|
| **A. Groups only.** A shelter in a lone tank is decorative; its fish ignore it. | Nothing to build. Players will read it as a bug. |
| **B. Promote.** A lone tank that contains a shelter *and* a fish with a `shelter` behaviour runs the planar engine over a 1×1×1 `VoxelDomain` with `Tunables.GROUP`, which is exactly what a group of one would be. | Small: the adapter already builds both. The golden lock is untouched, since every lone tank without that combination takes the identical code. The fish in a promoted tank switch from the binary model's look to the planar one's. The crowded 1×1×1 planar case mills as a torus ([`fish-swarm-realism.md`](fish-swarm-realism.md) §4.3), and that has already passed an in-game look. |

**Decided (2026-10-02): B.** It is the only option where the feature works where players put it,
and its risk is contained to tanks that opted in by stocking both halves.

---

## 8. Cosmetics to ship with it

The behaviour is worth only as much as the shelters on offer. Each is built through
`cosmetic-from-description` with interior markers (§3.2):

| Cosmetic | Mouth | Fits | Notes |
|---|---|---|---|
| **Hollow Log** (shipped 2026-10-02) | one end, plus a closed back | small fish | retrofit: hand-add `shelter.interior` for its 2×2×4 hollow |
| **Clay Pipe** | both ends | small fish | the classic aquarium hide, and the one fish swim *through* |
| **Rock Cave** | one wide arch | medium fish | sized for the bichir; the default lurker home |
| **Moray Rock** | two small holes | lurkers | stacked stone with holes at different heights |
| **Coconut Half** | one notch | tiny fish | a one-cell accent for gobies |
| **Whale Fall** (span) | between the ribs | many | the first spanning shelter; `gen.py` emits the interior |

Shop descriptions should name who fits ("a hide for small, shy fish").

---

## 9. Build order

Each phase ships on its own. Unconfigured species behave exactly as before at every step.

| Phase | Content | Acceptance |
|---|---|---|
| **0** | `Shelter` record in `:fishsim` domain; `FlockDomain.shelters()` (empty for `Box`); `CosmeticStructure.shelter` codec, mouth derivation and validation; capture markers; `TankShelters` adapter (block frame → engine frame, as `TankFloors` does); rebuild on the existing cosmetic fingerprint; `FrameRenderer` / `SimViewer` draw hulls and mouths. **No behaviour change.** | all goldens and parity green with **no expected-value edits**; mouth-derivation unit tests (sealed hollow, pocket that isn't a mouth, two mouths, all four rotations) |
| **1** | Hull avoidance (§4) and the lone-tank promotion (§7). Swimmers stop passing through shelters. | hull invariant holds over the matrix; zero backstop engagements; voxel golden regenerated once, deliberately |
| **2** | The state machine with `visitor` (§5.1–5.4): staging, enter, dwell, exit, capacity, hidden budget, timeout, adoption and removal. | invariants in §10; a `Scenarios` entry (a 3×1×1 group, one log, eight loaches) exported to GIF and inspected |
| **3** | `skittish` startle on the watcher. | tests for one-reaction-per-approach, a watcher who stays, and a dropped watcher signal, mirroring `AnchoredTest`'s three transients |
| **4** | `lurker` claim and mouth pose. | claim is stable across rebuilds; mouth pose holds position and heading |
| **5** | Content (§8) and the species opt-ins (§3.3). | `cosmeticpreview` passes for each new shelter; in-game look |
| **6** | Benthic dens (§6). | a crawler only enters via a mouth cell |

---

## 10. Verification

Headless first, as with every engine change since the extraction. The GIF is the acceptance test,
and the numbers explain it.

**Invariants** (in the style of `InvariantTest`, over seeds × the domain matrix × with and
without shelters):

- A swimmer not in `ENTER`/`INSIDE`/`EXIT` for a shelter is never inside its hull.
- A swimmer in `INSIDE` is always within the interior box, and it entered through a mouth: its
  trajectory crossed the mouth plane inside the mouth's extent. (`aFishOnlyEntersThroughTheMouth`.)
- Occupancy never exceeds capacity except under adoption (§4.3).
- The hidden count never exceeds the hidden budget, **at every stocking level tested from 2 to 64
  fish**. This is the eels' crowded-tank lesson stated as a test.
- Every fish that enters `APPROACH` leaves it within its timeout.
- `backstopEngagements() == 0`, and the existing accel/jerk/flip-storm bounds still hold.
- State, shelter, mouth and timer carry across `rebuildPreserving`.

**Diagnostics, not targets:** fraction of time hidden per species, visits per minute, seconds from
startle to cover. The standing warnings apply: pooled speed CV shipped a hop and `nnFront` never
moved. A number moving the right way is not the tank looking right.

**In-game look**, once, at the end of Phase 4: the loach in the log, the tetras bolting when you
walk up, the bichir in its cave, and the edge-on entry (§5.6).

---

## 11. Open questions

1. ~~**Lone tanks**~~ **Decided: option B** (§7). Progress is tracked in the shared
   implementation plan doc, not here.
2. **The starting cast** in §3.3 is a first pass from the hobby's reputation for each fish. It's an
   authoring call, not an engine one.
3. **Should a startled fish that finds no room do something visible anyway**, such as a short dart
   away from the watcher? The spec says no, keeping the startle strictly a reason to hide. A
   general startle is Tier 3 in [`fish-swarm-realism.md`](fish-swarm-realism.md) and deserves its own
   design.
4. **Day/night.** The black ghost knifefish hides by day and roams at night. The engine has no
   light signal, and adding one is out of scope here. Worth noting for after.
5. **Quests.** A `tank_snapshot` objective such as "a fish living in a den" would reward building
   one. It's out of scope, and it must stay a snapshot of tank state, never a need.
