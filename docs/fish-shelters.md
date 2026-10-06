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

- **General water obstruction.** Swimmers still pass through ordinary cosmetics after this work (§12.3 proposes changing that).
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
  outside is not a mouth. The flood never goes below the build grid's floor (y = 0): a structure
  stands on the sand, so a pocket open only through gaps in its floor, like the Whale Fall's
  snout, is still a pocket.
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
| `visitor` | Occasional unhurried visits: in, linger, out. | clown loach, neon goby, yellowline goby, rainfordia, blind cave tetra, bridle shiner |
| `skittish` | Visits occasionally, and bolts for cover when the watcher approaches (§5.3). | neon tetra, fire dartfish |
| `lurker` | Claims one shelter as home and rests in its mouth facing out; makes short sorties (§5.5). | ornate bichir, ophisternon candidum, black ghost knifefish |

**Shipped cast (Phase 5).** This differs from the first pass in four places. The discus is out: it is a tall disc, so the 0.4 height ratio in the mouth gate (§5.2) is wrong for it and it would clip through a 0.18 mouth. The lizardfish is out: at a mean of 0.47 blocks it fails the Hollow Log's mouth gate, and it's a sand-percher, not a cave fish. The glass catfish is out: it's a mid-water schooler, and ducking into a log works against how it looks. The blind cave tetra moved to `visitor`, because a blind fish shouldn't bolt from a watcher it can't see. Rainfordia (a reef goby that keeps to the rockwork) and the fire dartfish (which dives into its burrow when startled) were added. Every opted-in species uses the `horizontal_swim` pose, since shelters only take `FREE_SWIM` fish. The bridle shiner joined on 2026-10-02 for the Whale Fall's skull: at 0.14 blocks on average (0.23 at three sigma) it clears the eye sockets' 0.31 limit, and the log's too.

**Big fish joined (2026-10-03, §12.9).** With dens and lairs sized for them, the ambush hunters
became lurkers: northern pike, longnose gar, electric eel, angler fish and the lizardfish, whose
first exclusion was the Hollow Log's mouth (the Basalt Grotto and the Capsized Galleon take it
now). The oscar and the parrotfish became visitors. The discus stays out, for the reason above.

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
   (one visit every ~60 s, jittered ±35% off the seed like every other per-fish constant), which
   fires only while `ROAMING` and outside the cooldown. It raises an urge that lasts 20 s and
   becomes an approach once a fitting shelter's staging point is within **1 block**. The visitor
   swims there at 2× patrol (0.14 blocks/s), planning its arrival like the dash.

   *Revised 2026-10-02, after the owner rarely saw visits.* The reach was 4 s of patrol, about
   0.28 blocks, with a 40 s clock. Measured in a 5x2x1 with one pipe, a roaming fish was in reach
   2% of the time, and 11 loaches visited 0.7 times a minute between them. Now 5.4, with 1.2 of
   the 11 hidden on average. The clock went to 60 s because nearly every urge now finds a
   shelter, and at 40 the hidden budget would be full most of the time.
2. **Startle** (`skittish` only). The watcher *lunging*: within 3 blocks of the fish and at least
   0.5 blocks closer to it than a second ago. Afterwards the fish can't be startled again for 15 s.
   A startled fish **dashes** for any fitting shelter whose staging point is within 1.5 blocks
   (§5.3.1). One that gets no place **flinches** instead (§5.3.2).

   All of a tick's startles are resolved together, before anyone steps, and places in cover go
   **nearest first**: every fish that dashes is at least as near its cover as every startled fish
   that doesn't. The first version went in index order, so a fish across the tank could take the
   place of one at the mouth.

   *Revised 2026-10-02, after the in-game look* ("I can only very rarely get the neon tetras to
   dart into the spot"). The first version used the eels' arming rule (armed only by seeing the
   watcher beyond 4.5 blocks) and a reach of 2 s at 1.8× patrol, about 0.25 blocks. Measured in
   the owner's 5×2×1 with two logs: a watcher who stays by the tank, stepping up to the glass and
   back, never re-armed anything, and of the startles that did fire, only 0.5–10% had cover in
   reach, since in a two-storey tank most fish are a block or more from a floor log. A watcher
   standing still, or already there when the tank loads, is still not moving, so it still startles
   nobody.
3. **Home** (`lurker` only). See §5.5.

#### 5.3.1 The dash

A startled fish is the one fast mover in the tank. It dashes at 0.40 blocks/s (twice the ordinary
speed cap, scaled by its own patrol jitter) with its own speed cap while dashing, and its tail
beats faster to match. It plans its arrival, slowing at 0.3 blocks/s² so it reaches the staging
point at its entry speed (1.8× patrol) rather than at full dash. A block-scale dive peaks at about
0.30, because it has to start slowing before it reaches full speed. The approach climbs over a hull in its way in
proportion to the height still to climb. All-or-nothing climbing chattered at ±maxForce at dash
speed.

In shelter domains, the whole tick's change of velocity is held to maxForce, not just the
steering's. Vertical damping and the speed cap act after the force clamp. On a dash they added
up to 0.65 against 0.5, and a dash ending would halve the cap in one tick. Held, the end of a dash
is a slow-down over a second or so. Shelter-less domains run none of this, so the goldens hold.

Measured in the 5×2×1 with two logs, 11 fish and a watcher stepping up to the glass and back
for ten minutes: every dash reached cover (47 of 47, 50 of 50 over two seeds). Of the startles
that didn't dash, 101 of 113 found the hidden budget (2 of 11) spent. The budget is now the limit,
not reach.

Two smoothness fixes came with it. The climb over a hull in the way fades in and out over a
quarter second, remembering the height to clear. Switched on and off, it wiped out a dash's
0.3 blocks/s descent in one tick when the line to the staging point began grazing the hull's
corner, and gave it back when the fish cleared the far edge (jerk 14.3 against 12). And for 2 s
after a dash or flinch ends, a fish over its ordinary speed cap is only kept from speeding up,
while steering slows it. Clamping it straight down measured 12.9.

#### 5.3.2 The flinch

*Added 2026-10-02 at the owner's request; this closes open question 3.* A skittish fish that is
lunged at but gets no place in cover (the hidden budget is spent, or no shelter it fits has room)
darts away from the watcher and settles. Without it, a lunge at a shoal of eleven sent two into
the log and left nine acting as if nothing had happened.

- **Direction:** horizontal, straight away from the watcher, turned by up to ±35° per fish so the
  shoal fans out rather than moving as one. If a hull lies in the next 0.5 blocks, the direction
  turns further, in steps of 30°, up to a quarter turn off straight away. With nothing clear, the fish
  doesn't flinch. At flinch speed the hull's soft avoidance, tuned for cruising, let a fish dart
  straight into the log.
- **Shape:** 1.6 s, up to 0.35 blocks/s (under the dash's 0.40), reaching full strength in the
  first 10% and fading over the last half. Height stays the shoal's, and walls still apply.
- **While flinching**, a fish's visit clock and cooldown wait. It has its own speed cap, and the
  settle above brings it back down.

**A frightened fish beats a casual one to the door** *(2026-10-02, the owner's choice)*. With
visits common (§5.3 trigger 1), the hidden budget was so often full that dashes in the 5x2x1
with two logs fell from 40-58 to 10-23 in ten minutes. Now a startled fish that finds no room may
take the place of a visitor still swimming to a shelter, and that visitor turns away, carrying
on as it was going. Only a visitor farther from its staging point than the startled fish is from
cover: one already at the door keeps its place. Fish entering or inside, and a lurker on its way
home, are never bumped, so the budget is never exceeded. Dashes recovered to 23-32. The rest of
the shortfall is fish already inside, which is the budget working.

`ShelterStartleTest` holds it: every fish lunged at either hides or flinches, at least 75% end
the flinch farther from the watcher (one beside a log's mouth may have to dart along the glass),
and five seconds on none is flinching or faster than a cruiser.

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
- **Approach timeout:** `APPROACH` gives up after the time to swim its 1-block reach at its
  approach speed plus 4 s (about 11 s; at least 6) for a visit, 12 s for a lurker going home, and
  6 s for a startle's dash of up to 1.5 blocks. Then it goes to cooldown.
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
| **Clay Pipe** (shipped 2026-10-02) | both ends | small fish | the log's body in terracotta, open at both ends with a whole roof; fish swim *through* it (§12.4) |
| **Rock Cave** | one wide arch | medium fish | sized for the bichir; the default lurker home |
| **Moray Rock** | two small holes | lurkers | stacked stone with holes at different heights |
| **Coconut Half** | one notch | tiny fish | a one-cell accent for gobies |
| **Fence arches** (gates, 2026-10-02) | front and back of the opening | fish up to ~0.73 blocks | `"kind": "gate"`, the column between the posts up to the beam, lantern included (§12.4.1) |
| **Torii Gate** (gate, 2026-10-02) | front and back | any fish | rebuilt taller at scale 0.075 (posts 6 cells apart under an 11-wide kasagi, 9.5 cells high) with a 5×5-cell opening; the old 0.09 build's 0.18-tall opening was narrower than the wall margin above the sand, so `closeGaps` sealed it for every fish. Its path stones became moss (soft) and the one in the gateway went |
| **Spruce Gazebo** (open, 2026-10-02) | all four sides, under the roof | fish up to ~0.32 blocks | `"kind": "open"`: fish visit the floor under the roof on show. Shrunk 0.15 → 0.13: at 0.15 a lone tank left 0.21 beside it and 0.20 above the roof, both closed as gaps, so the floor under the roof was the only water a fish could hold — the old stuck spot |
| **Whale Fall** (span; skull shipped 2026-10-02) | the two eye sockets | fish up to ~0.31 blocks | the first spanning shelter. `gen.py` emits the skull's widest part (cells 5..7 x 1..2 x 4..10, capacity 3); the eyes face each other, so fish swim in one and out the other. Only that part, because the engine confines a fish to the interior's bounding box and the whole hollow narrows toward the snout. Between the ribs is still to come |

Shop descriptions should name who fits ("a hide for small, shy fish").

The second wave (2026-10-03), sized for medium and large fish, is in §12.9.

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
3. ~~**Should a startled fish that finds no room do something visible anyway?**~~ **Decided
   (2026-10-02): yes, it flinches** (§5.3.2). A general startle, for every species and not only
   skittish ones near a shelter, is still Tier 3 in [`fish-swarm-realism.md`](fish-swarm-realism.md).
4. **Day/night.** The black ghost knifefish hides by day and roams at night. The engine has no
   light signal, and adding one is out of scope here. Worth noting for after.
5. **Quests.** A `tank_snapshot` objective such as "a fish living in a den" would reward building
   one. It's out of scope, and it must stay a snapshot of tank state, never a need.

---

## 12. Beyond hiding: affordances

**Written:** 2026-10-02, after the Hollow Log passed its in-game look. Draft; nothing here is built.

The visit state machine (§5.1) is more general than shelters. Its real shape is *go to a spot on a
structure, do something there, leave*. Hiding is one verb. This section adds more verbs on the same
base, sets how often each happens, and makes the obstacle change that gates can't work without
(§12.3).

### 12.1 One base, several verbs

| Verb | What the fish does | Example cosmetics | New plumbing |
|---|---|---|---|
| **Hide** (shipped) | Enters a hollow, lingers, leaves by the mouth it came in by | Hollow Log, Rock Cave, Coconut Half | none |
| **Pass through** | Enters one mouth, leaves by another | Clay Pipe, Whale Fall ribs | small: `EXIT` picks a mouth other than `chosenMouth(i)` |
| **Gate** | Swims through an opening without stopping | the fence arches, Torii Gate, Castle Ruin windows | none beyond pass-through: a gate is a pass-through shelter with no dwell |
| **Den** | A benthic crawler walks in and stays | floor-level caves for the octopus | §6: the floor field's blocked cells |
| **Trigger** | Noses an anchor point, and the structure reacts | Giant Clam, a chest, a bubbler, a lantern | new: engine events and moving parts (§12.5) |
| **Perch / graze** | Rests on top of a structure, or picks at its surface | Mossy Boulder, coral reefs | small: a staging point on a surface, no interior |

**A gate is a pass-through shelter, not a new type.** Its opening is marked with interior cells
exactly as a hollow is (§3.2), and the derivation already finds a mouth on each side. What makes it
a gate is a dwell of zero and no length gate, and both follow from the hollow's shape: a run
shorter than the fish is a gate to that fish. So no new field is needed, and the data surface stays
at the one optional field §3.1 promised.

### 12.2 Rarity scales with spectacle

Some interactions are wallpaper and some are moments. A moment that happens every ten seconds stops
being one. So every verb has a **rarity tier**, and the more striking the interaction, the rarer
it is:

| Tier | Verbs | Clock | Feel |
|---|---|---|---|
| **Common** | hide, pass through, gate | per fish, Poisson, mean ~40 s (§5.3 as shipped) | the tank is always doing something small |
| **Uncommon** | perch, graze, den | per fish, mean ~2 min | noticed if you watch for a minute |
| **Rare** | trigger (clam, chest, bubbler) | **per structure**, mean ~4 min, plus a tank-wide budget | a moment you'd call someone over for |

Two rules carry over from §5.4 and get stricter as the tier goes up:

- **Rare clocks run per structure, not per fish.** A per-fish clock makes a ten-fish tank ten times
  as eventful, which is exactly the stocking dependence §5.4 exists to prevent. A clam opens about
  once every four minutes whether two fish or twenty could do it. When its clock fires, the
  structure picks the nearest eligible fish. If none is eligible, the clock re-arms and nothing
  happens.
- **One rare moment at a time per domain.** Two clams opening together reads as a timer, not as
  life. A domain-wide lock is held from `APPROACH` until the reaction ends, then a short refractory
  period (~30 s) follows before any rare clock may fire again.

The tier is a property of the verb by default. A structure can override it (`"rarity": "rare"`)
for a common verb that its look makes special, e.g. a gate through a lighthouse's lit doorway.

The rates are first numbers for the in-game look, not derived ones. As the swarm work learned
([`fish-swarm-realism.md`](fish-swarm-realism.md), "Lesson for the rest of this roadmap"), a
metric is diagnostic, not an objective. The acceptance is by eye: does a rare moment *feel* rare
after five minutes in front of the tank?

### 12.3 Obstacles: swimmers stop passing through cosmetics

*Cold-start context for building this: [`fish-shelters-obstacles-handoff.md`](fish-shelters-obstacles-handoff.md).*

This is a prerequisite, not an extra. Today, swimmers pass through every cosmetic except shelter
hulls (§1.1). For hiding, that was tolerable: the log is solid and the rest is background. For
gates it's fatal: a fish swimming through an arch looks no different from one swimming through its
pillar unless the pillar is solid. And the more a tank is built up to *invite* interaction, the
more clipping it shows.

**Don't do this through `DistanceField`.** Giving the distance field sub-block resolution is the
redesign that [`fish-sim-locomotion.md`](fish-sim-locomotion.md) §4.1 deferred. The field is
shared per group and keyed by the membership epoch, and its build cost is the group-scaling work's
main constraint ([`fish-tank-group-scaling.md`](fish-tank-group-scaling.md) §3.4). Cosmetics
change far more often than membership.

**Instead, generalise the shelter hull.** The engine already avoids oriented boxes softly (§4.1),
and that passed its look. Obstacles become more of the same:

- **Derivation, at load.** Each structure's parts are rasterised from their block shapes at ¼ of a
  build cell, so a fence post is a post, not a cube. The result is greedy-merged into at most ~12
  boxes. It's deterministic and cached per structure. Like the shelter, the stored boxes are
  unrotated and turn with the structure at placement.
- **Solid and soft.** Foliage, kelp, seagrass and coral fans are **soft**: fish may brush through
  them, as real fish do through plants, and it looks right. Stone, wood, metal and glass are
  **solid**. The class comes from a block tag (`fishtastic:soft_cosmetic`), so authors don't set it
  per part.
- **Rebuilt on the cosmetic fingerprint**, in the same pass as `rebuildFloor` and the shelters
  (§2), never on the membership epoch. The distance field is untouched.
- **Binned.** Boxes are listed per block cell of the domain. A fish queries only its own cell and
  its neighbours, so the cost per fish stays flat however decorated a 512-tank group gets.
- **A shelter's hull becomes one of its structure's obstacle boxes.** The corridor exception (§4.1)
  still applies: a fish using a shelter ignores that structure's boxes and is confined to the
  corridor instead.

**No pathfinding, still.** Soft avoidance by steering has one known failure: a fish can wedge
itself in a concave pocket, like behind a tree trunk or in a ruin's corner. Two cheap guards:

1. A wander target inside an obstacle box, or with a box squarely across the straight line to it,
   is resampled.
2. A fish whose progress toward its target stalls for ~2 s gets a new target, as an `APPROACH`
   timeout does today (§5.4).

**The size gate keeps ignoring obstacles at first.** The domain's run lengths still read the
water's bounds alone. A tank so full of rock that a fish can't turn is a decorating choice. Revisit
if the look says otherwise.

**Invariant, as with hulls (§4.2):** *a swimmer not using a shelter is never inside a solid
obstacle box*, with `backstopEngagements() == 0` unchanged. Measure it over a headless matrix that
places **every shipped cosmetic**, not just synthetic boxes, because the shipped set is where the
thin posts, overhangs and spans are.

**Lone tanks are the real decision here** (open question 6). The binary model is bitwise-locked and
can't learn obstacles. §7 promotes a lone tank to the planar engine only when it holds a shelter
and a fish that uses one. Obstacles would promote nearly every decorated lone tank.

#### 12.3.1 Built (2026-10-02), and where it departs from the above

Uncommitted, awaiting the owner's look. Measured baseline before it, over every shipped cosmetic
(12 visitors, 2 seeds × 6000 ticks, lone tank and the middle of a 3×1×1): **5.4%** of swimming
fish-ticks inside a solid part, worst the Drowned Pagoda at 28% and the Castle Ruin in a lone tank
at 23%. After: **0** inside a derived box, and inside the exact block shapes too.

- **Derivation** (`ObstacleGeometry`, pure; `CosmeticObstacles`, the Minecraft half). Outline
  shapes at ¼ build unit, the `fishtastic:soft_cosmetic` tag (leaves, corals and coral fans, kelp,
  seagrass, vines, petals, leaf litter, moss carpet, roots, short grass, fern, sea pickle,
  mushrooms), greedy-merged, cached per structure, cleared on a tag reload.
  - **No ~12-box cap.** Exact merges run 1–31 boxes for most structures, but 152 for the Whale
    Fall and 320 for the Drowned Pagoda. Getting those to 12 means coarsening, and coarsening
    fills openings: the arch, the doorway, the gap a pass-through swims. Binning already makes the
    count free per fish, so the merge stays exact and uncapped.
  - **Positions are not rounded.** `partCells()` rounds, and the Dynamic Duo's blocks stand half a
    cell off the grid. Rounded, its boxes sat half a block from what is drawn, and fish swam
    through it.
  - **Pockets no fish can reach are filled** (`fillPockets`). Sealed cavities exist in the shipped
    set (the Amethyst Geode's is 4.6% of its free water, plus the Giant Clam and the reef), and
    the scatter could drop a fish into one for good. A reef nook reachable only through a
    one-block opening held one fish for an entire run. Water not within 0.06 blocks of somewhere
    a fish's centre fits, connected to the outside, is filled. Openings fish use are wider.
  - A shelter's hull is carved out, and the shelter carries it as before. Every other part of the
    structure is solid to every fish, its users included.
- **Engine.** `FlockDomain.obstacles()`, `VoxelDomain.rebuildObstacles`, rebuilt on the cosmetic
  fingerprint. Boxes are binned per block, and `ObstacleBinningEquivalenceTest` holds the bins to
  bit-for-bit identity with no binning. The hull ramp and `closeGaps` are reused. All of it is
  gated on a non-empty obstacle list, so every golden is untouched.
  - **Shelter accessibility.** A mouth whose corridor (mouth to staging point) crosses an obstacle
    is not offered for entry or for passing through. A cosmetic dropped across a doorway closes
    that doorway rather than trapping fish in it (`ObstacleCorridorTest`).
    `ObstacleShelterAccessTest` holds every shipped shelter to the same entries per approach,
    mouths and pass-throughs with its parts solid as without. Measured: pipe 203/273 both ways;
    log 119/265 → 126/266; whale 27/157 → 18/116, the same rate per attempt, with fewer attempts
    because its solid bulk keeps fish further from the skull.
  - **`closeGaps` probes a box's sides in the water.** A box whose centre is above the waterline
    (a fence arch's top beam) had every side probe "out", grew to fill the tank front to back, and
    pinned a fish against the glass (76 backstop engagements). Hulls, centred in the water, probe
    where they always did.
  - **The approach climb stays hulls only.** Climbing obstacles too sent fish for the top of a
    sprig on a log's back, against the lid (jerk 14.4). Flinches do check obstacles.
  - **The whole-tick force clamp now runs with obstacles as well as shelters.** A fish turned hard
    by the pagoda's eaves at top speed measured an acceleration of 0.56 against 0.53.
  - **No wedge guards.** Guard 1 has nothing to act on: the planar model has no wander target, only
    heading noise. Guard 2 (retarget a fish stalled ~2 s) was built and measured, then removed. It
    did not free the Spruce Gazebo's doorway fish (stalls 1632 → 1620 of 3600 windows), because
    that doorway's only exit lies inside the glass's 0.20 wall margin in a lone tank, and walls must
    win. It added two jerk violations (13.1, 14.4). The one fish truly stuck, the reef nook, was a
    pocket, which the fill fixed.
- **Lone tanks are promoted** (`TankFlockAdapter.wantsObstacles`) when they hold a solid part and a
    swimmer. Per decision 6 this ships only after the in-game look at a crowded, decorated lone
    tank, and the measurements say what to look for. A large structure in a lone tank leaves a
    ring or a room of water, a lot of it inside the wall margin, so:
  - 12 fish there is over-stocked. The Spruce Gazebo measured jerk 13.8 on 1 seed in 10 (separation
    chattering in the crowd under its roof; 8.2–11.5 on the rest, 6.0–9.3 without obstacles).
    The Wax Skull Candle measured a 3-tick backstop. The invariant matrix holds lone tanks at 6
    fish, where everything passes.
  - Fish linger in doorways and under roofs: 2-second windows with under 0.1 blocks of progress
    rise from 1.7% to 14–45% in a lone tank (the gazebo 45%), and far less in groups. It may read
    as hiding under an overhang, or as stuck. That is the look's call.
  - Pre-existing, not obstacles: the Hollow Log and Clay Pipe in a lone tank at 12 visitors already
    take a rare backstop with no obstacles at all.
- **Verification.** `ObstacleInvariantTest` covers every shipped cosmetic: 248 runs over 3×1×1 at 6
  and 12 fish, lone tanks at 6, spans in their own box, alternating rotations. Each run holds the
  bounds: never inside, no backstop, no wall penetration, speed/accel/jerk ≤ 12, no fish stuck.
  The rest: `CosmeticObstaclesTest` (every drawn solid shape covered, soft structures empty, no
  pocket left, shelter hollows and the arch opening open), `TankObstaclesTest` (placement against
  the renderer at every rotation, the lone-tank frame round trip), `ObstacleGeometryTest`, and the
  two fishsim tests above. Harness: `--obstacles FILE`, written by `ObstacleExport`
  (`-PexportObstacles=DIR`).

### 12.4 Pass-through and gates

- **Mouth choice on exit** *(built 2026-10-02)*. When a visit ends, `EXIT` takes the usable mouth
  that the fish fits and whose normal is most opposed to the entry mouth's, if one is within ~60°
  of opposite (`FlockEngine.throughMouth`). So a fish goes *through* the pipe, or across the whale's
  skull from eye to eye, instead of turning round and backing out. A one-mouth shelter is
  unchanged, and a lurker at home always leaves by its own mouth. `ShelterPassThroughTest`: every
  pipe visit leaves by the far end, and the log is still backed out of. The pipe joins
  `ShelterVisitTest`'s matrix. A span's shelter is placed from its anchor tank
  (`SpanStructures.buildOrigin`, `TankShelters`).
- **Gates are taken in the direction of travel.** A roaming fish is a candidate for a gate only if
  its heading already points through it within ~45°. Choosing it never needs a turn, so a fish
  never U-turns to go through an arch. It reads as "the fish chose the doorway", not "the fish was
  summoned".
- **No dwell, and no hidden budget.** A fish in a gate is visible the whole way, so it doesn't count
  against §5.4's hidden budget.
- **Retrofit, not rebuild.** The fence arches, Torii Gate and Castle Ruin gain `shelter.interior`
  cells for their openings, the way the Hollow Log was retrofitted. The derivation does the rest.

#### 12.4.1 Built (2026-10-02): shelter kinds

The arches and the Torii Gate are gates; the Castle Ruin's windows are not done. Where it departs
from the above:

- **A kind field after all.** `shelter.kind` is `hollow` (the default, so every existing file is
  unchanged), `open` or `gate` (`CosmeticStructure.ShelterKind`, `Shelter.Kind` in the engine).
  "A run shorter than the fish is a gate" could not be derived, because a gate has to differ in
  its hull: a hollow's hull is solid to every fish not using it, and an arch's hull is the whole
  arch, opening included. So a **gate has no hull**: its parts are not carved out of the obstacles,
  the posts stay solid, and the opening is open water to every fish. Validation: a gate needs two
  opposite horizontal mouths.
- **Who uses what.** Hiding is a temperament, so hollows stay opt-in (§3.3). A gate or an `open`
  shelter hides nobody, so every free swimmer that fits may use one. A fish that never hides runs
  the visit clock only in a domain that holds one, so every other domain is bitwise unchanged.
- **Hidden budget, cover, homes.** Only a hollow counts against the budget, is a startle's cover,
  or is a lurker's home. A full budget closes the hollows only. Separation is filtered only by a
  hollow's walls: a fish in a gate or on the gazebo's floor still keeps apart from those round it.
- **Gates.** Offered only with the heading within 45° of straight through and the mouth ahead
  (`headingThrough`), and only when the far mouth is one the fish can leave by. Approached at
  patrol speed, not a visitor's hurry. On reaching the interior, `ENTER` goes straight to `EXIT`
  by the far mouth: no `INSIDE`, no dwell. No length gate.
- **Interiors that start on the sand.** An opening's interior begins at the sand, 0.025 below the
  swim volume's floor, so "every corner in the water" disabled every gate. Usability is now judged
  on the part of the interior in the water.
- **The gazebo** is `open`, its interior the plus of cells under the roof at y = 1 (the corner
  posts and the lantern stay out). Its hull makes the gazebo solid to fish not visiting it, so none
  wanders under the roof by accident any more. Measured, 4 seeds × 5 min × 6 visitors: a lone
  tank at 0.15 without the shelter had 59% of fish-time under the roof; at 0.13 with it, none
  stuck, no fish in an obstacle; 3x1x1 40-43 visits, 3x2x2 50.

- **The arches' lantern is soft (2026-10-03, the owner's call).** Owner observation: a shoal split
  across an arch oscillated against it, trying to rejoin and never crossing. An arch spans its
  whole tank (3 cells wide, beam to the lid, the gaps beside its posts closed as narrower than the
  wall margin), so its doorway is the only way through, and the lantern hanging in the middle of it
  plus the posts' avoidance margins left only a strip by the sand. `soft_cosmetic` now takes
  `#minecraft:lanterns`, which also lets fish brush through the Drowned Pagoda's 24 lanterns and
  the Wax Skull Candle's. A gate's opening may now take in a part's cell
  (`CosmeticStructure.shelterPartCells`), so the arches' opening runs up through the lantern to the
  beam; the loader can't see tags, so `ShelterKindTest` holds every such part soft. Measured, 8
  fish of one species, 6 seeds × 5 min: in a 1x1x2, the shoal split across the arch 84% → 34% of
  the time, crossings 6 → 585; in a lone tank, crossings 82 → 458. **None of those crossings were
  gate visits**: the gate's clock (§5.3, 60 s) and heading rule make it too rare to matter to a
  split shoal. If the oscillation persists in game, the next step is a rejoin trigger: a fish whose
  shoal is mostly beyond a gate takes it, with no clock and no heading rule.

**Two exit bugs found on the way, both older than the kinds:**

- **Pass-through to a far mouth facing the glass** (from §12.4's mouth choice). `throughMouth`
  checked the far mouth's corridor but not its staging point, which `findCover` and `homeMouth`
  both check. A pipe in a 3x2x2 sent visitors out against the glass, where `EXIT` could never
  finish: 114 of 240 thirty-second windows stalled, fish pinned for up to 150 s. Now held to the
  same tests as an entry. `ObstacleInvariantTest` gained a 3x2x2 case, which fails without it.
- **Staging points in the wall margin.** A staging point 0.004 inside the water passed "in the
  water", but the wall's push held a fish 0.09 short of it (a fence arch in a lone tank, in
  `EXIT` for the rest of the run). A staging point must now be `VISIT_WALL_MARGIN` from the walls
  (`stagingReachable`, every mouth choice). And `EXIT` has a timeout like `ENTER`'s: past it, a
  fish half a body clear of its mouth is let go, so nothing can hold one in `EXIT` for good.
- `ShelterGateTest` (straight through, by fish that never hide, never turning more than 45° to
  take one; never cover), `ShelterKindTest` (shipped kinds, gates open both ways, the gazebo on
  all four sides, a one-mouthed gate fails to load).

### 12.5 Triggers: a structure that reacts

Two new pieces. Both are client-only and purely visual, so the §1.1 non-goals hold:

1. **Engine events.** The engine appends to a small per-tick event list: *fish i reached anchor k
   of structure s*. It's derived from engine state, so it's deterministic under the headless
   harness and testable there. The renderer drains the list each frame.
2. **Moving parts.** A structure's parts can be grouped under a **hinge** (a pivot, an axis and an
   angle) or a **slide** (an offset), driven by a 0→1 value the renderer eases. The group rests
   closed. On an event, it opens, holds, then closes. Particles go through the existing
   `TankBubbleEmitter`. Nothing about the reaction is stored or synced: two players may see
   different clams open, as they already see different fish in the log.

The anchor is marked during capture like the interior (§3.2), with a different invisible block,
`minecraft:barrier`. The fish uses the usual state machine: `APPROACH` to a staging point facing
the anchor, a short nose-in (the `ENTER` motion, stopped at the anchor), a brief hold while the
reaction starts, then `EXIT`.

**The pilot is the Giant Clam**, which already ships. Its top shell is the hinged group, and it lets
out a burst of bubbles at full open. It's the smallest complete case: one hinge, one anchor, one
particle burst. A chest, bubbler or lantern is the same three pieces with different content. A
chest would be built from block parts with a hinged lid, not from the vanilla chest block entity,
because cosmetic parts are static block models.

**Considered and rejected: reactions with gameplay effects**, such as a chest that yields an item
or a trigger that feeds fish. They would need the server, and the engine is client-only and not
authoritative. They would also turn decoration into a mechanic, against §1.1's "never a need".

### 12.6 Perch, graze, territory (later)

These are noted to keep the base general, not designed yet:

- **Perch:** a staging point on a structure's top face, with a dwell and no interior. Gobies
  sitting on a boulder.
- **Graze:** a short run of nose-in touches along a surface, using the `nibble` pose. It needs
  surface samples from the obstacle boxes of §12.3, which is one more reason obstacles come first.
- **Territory:** a lurker (§5.5) whose home has an intruder within a body length chases it a short
  way, then returns. This is the first fish-on-fish reaction, so it overlaps Tier 3 of
  [`fish-swarm-realism.md`](fish-swarm-realism.md) and belongs in that design.

### 12.7 Order

1. **Clay Pipe + pass-through.** The smallest engine change, and it proves multi-mouth shelters.
2. **Obstacles (§12.3)**, with the every-cosmetic invariant matrix, promoting decorated lone tanks
   (open question 6).
3. **Gates**, retrofitted onto the arches, Torii Gate and Castle Ruin. Only worth doing after 2. *Arches and Torii Gate built 2026-10-02 (§12.4.1); the Castle Ruin's windows remain.*
4. **Rarity tiers and the rare budget (§12.2)**, then the **Giant Clam trigger** as the pilot for
   events and moving parts.
5. **Phase 5 shelter cosmetics** (Rock Cave, Moray Rock, Coconut Half), alongside any of the above.
6. **Dens** (§6), then perch and graze.

### 12.8 Open questions

Numbered on from §11.

6. ~~**Lone tanks and obstacles.**~~ **Decided (2026-10-02): promote.** A lone tank holding a
   solid cosmetic and a swimmer runs the planar engine, as §7 does for shelters, so fish stop
   clipping everywhere. The cost is that most decorated lone tanks change their look. Rejected:
   keeping lone tanks ghosting through cosmetics, which leaves the clipping where most players keep
   fish. Obstacles shrink the free water, and the crowded 1×1×1 planar case already mills as a
   torus (§7), so a crowded, heavily decorated lone tank gets an in-game look before this ships.
7. **The soft-cosmetic list.** Which shipped blocks count as soft? First pass: leaves, kelp,
   seagrass, coral fans, vines, petals.
8. **Rates.** The tier means in §12.2 are guesses until the first look.

### 12.9 Bigger fish, and several shelters in one structure (built 2026-10-03)

The shelters before this were sized for small fish. The second wave is sized by band, using the
§5.2 rules, so the size of a shelter decides who uses it:

| Band | Rendered length | A hollow or open shelter needs | Examples |
|---|---|---|---|
| small | up to 0.20 | mouth 0.08, run 0.18 | goby, tetra, glass catfish |
| medium | 0.21 to 0.37 | mouth 0.15, run 0.34 | loach, oscar, knifefish, bichir |
| large | 0.38 to 0.55 | mouth 0.22, run 0.50 | trout, pike, gar, idol, eel |
| giant | 0.59 to 0.76 | gates only | sunfish, manta, paddlefish, sawfish |

A fish needs open water about a body length outside a mouth to line up on it (the staging point,
§5.1), so a large fish uses shelters only in a group, and only where a mouth faces along the
group's long axis. Floor shelters for large fish are built with their mouths facing sideways
(along x) for that reason. Gates set no length limit, so a big enough gate takes every fish.

**Staging room is the real size limit for spans.** A mouth must stand at least `L + 0.02 + 0.20`
(the body length, the staging clearance and the wall margin) in from the glass it faces. The first
cut of the spanning shelters put their doors at the ends of the box: the cathedral's portal was
0.19 from the glass, the ziggurat's maws 0.25. Small fish still got in, but no big fish ever did.
`ShelterBandVisitTest` caught it. It runs a shoal of the size each shelter was built for in the
group that shelter needs. The fix is water in front of every door: the cathedral and ziggurat
became 4-long spans with the building in the middle, the warren a 3-long, and the galleon's stern
moved 0.8 in. The Sea Arch's gate became the window's middle slice: a deeper passage leaves too
little water on either side in a two-deep tank.

**Lone tanks bound the floor pieces.** A floor shelter also has to leave a lone tank a wall
margin of water around and above it, as the gazebo found. Otherwise `ObstacleInvariantTest`'s hard
backstop engages. That sized the Bell (scale 0.05), the Grotto's hill, and the Seat, which is why
the Seat tops out at 0.50: a seat for longer fish is wider than a lone tank can spare.

**Several shelters per structure.** `shelters` is a list. A single shelter is still written as
`shelter`, so no older file changed. Each shelter derives on its own, treating the other shelters'
cells as open water. Validation rejects two shelters that share a cell, and rejects a shelter
whose interior lies in another hollow or open shelter's hull, because that hull is solid to every
fish not using that shelter. A gate has no hull, so a gate's opening can sit next to anything.
Everything that read one shelter now reads them all: obstacle carving, `TankShelters`, and the
capture command (which still writes one).

**Pockets are filled with the hulls solid.** `CosmeticObstacles` used to fill pockets and then
carve the hull. A large box-shaped hull, such as the ribcage's or the mangrove nursery's, can wall
off water against the other parts. Fish not using that shelter can never reach that water, but it
wasn't filled. Now the pockets are filled with every non-gate hull counted as solid, and then the
hulls are carved. Structures with small hulls come out the same.

**`min_length`.** This is an optional field on a shelter: the shortest fish, in blocks of rendered
length, that may use it. The engine's `Shelter.minLength` is checked by `findCover` (visits and
startles) and by `lurkerFits` (homes). Small fish skip the shelter and look for the next one, so it
needs no other behaviour. The Leviathan's Seat uses it: 0.40, capacity 1.

**The cosmetics.** They are generated by `tools/shelter-structure-gen/gen.py`, not captured,
because their interiors have to be exact to the cell. The Whale Fall's second shelter is added in
`tools/span-structure-gen/gen.py`. `ShelterSizeBandsTest` checks that each shelter fits the band it
was built for.

| Cosmetic | Where | Shelter(s) | Fits |
|---|---|---|---|
| Amphora | floor, 0.06 | hollow through the neck, 3x3, 11 long | up to 0.45 (a lurker's home) |
| Drowned Bell | floor, 0.05 | hollow throat, 5x5, 7 deep | up to 0.38 |
| Mangrove Knees | floor, 0.065 | open, under the root mat | up to 0.5 |
| Basalt Grotto | floor, 0.065 | hollow cave, 4x4, 8 deep | up to 0.57 (the eel) |
| Moon Gate | floor, 0.045 | gate, 7 wide and 9 tall; soft blossom rounds the rim without adding mouths | every fish |
| Leviathan's Seat | floor, 0.05 | open, capacity 1, `min_length` 0.40 | 0.40 to 0.50 only |
| Sea Arch | span 2x2x2 | gate through the middle of a 0.6 x 0.9 window | up to 0.6 (staging room, not the gate) |
| Sunken Ziggurat | span 4x2x1 | hollow tunnel between the serpent heads, 2.4 long; open sanctum on top | tunnel up to 0.62; sanctum small only |
| Capsized Galleon | span 3x2x1 | hollow lair, mouth at the broken stern | up to about 0.55 (staging room); for big lurkers |
| Whale Fall | span 4x2x2 | adds an open shelter under the ribcage | every fish |
| Drowned Cathedral | span 4x2x2 | the nave is a gate; the belfry is open; three chapels are hollows | large, medium, small |
| Coral Warren | span 3x2x1 | three gates at three heights, two behind soft curtains of coral fan | small, medium, small, all at once |

Open questions:

1. **Do gates hide?** The Coral Warren's passages run through the rock, so a fish in one is out of
   sight, but gates don't count against the hidden budget. A shoal streaming through all three at
   once can briefly hide more fish than the budget allows. If that looks wrong, give gates a
   "covered" flag that counts.
2. **Part counts.** The spans have 1,000 to 1,660 parts, compared with 1,080 for the pagoda.
   Their render cost has not been measured.
3. **The cathedral's chapels are seldom visited** (a few entries in 15 minutes of a small-fish
   shoal, against dozens in the belfry). They face the front glass, and the nave and belfry are
   nearer most fish's paths. That may be the right rarity for a hidden corner.

### 12.10 Occupied cells: claim only what stands on the sand (2026-10-03)

The second wave claimed far more of the floor grid than it filled. A floor piece claimed every
cell any of its voxels reached, at any height, so a seagrass tuft, a roof overhang or the flare of
a base took a whole cell. The Moon Gate took six cells and stands on three. A span claimed every
cell under its lowest layer of parts, soft ones included. Claimed cells refuse other cosmetics
and are walls to crawling fish (`TankFloors.blockedCells`), so players couldn't set plants against
a building or put two structures close together.

`footprint_cells` had been doing two jobs. It is the **extent**: every cell must land on the grid,
so a structure's parts never poke through the glass. It was also the **claim**. The new optional
field **`occupied_cells`** splits these. It is the subset that blocks. Without it, the whole
footprint blocks, so older files behave as before. For a span, `occupied_cells` counts floor cells
across the whole box (tank × 3 + cell, facing south as authored) and turns with the box. Without
it, the span's footprint is still derived from its lowest parts.

**The rule** (`tools/shelter-structure-gen/footprint_audit.py`): a cell is occupied when solid
(not soft) parts lower than 0.3 blocks above the sand cover at least 12% of the cell's central
half. That is where a plant in the cell would clip. The anchor cell stays occupied, unless the
structure has `bypass_anchor_cell_requirement`. The rule only ever frees cells: it never adds one
the old footprint lacked. 12% keeps the Torii's posts (15%), the Giant Clam (17%) and the
Caterpillar (21%). It frees the Amphora's base flare (10%) and everything only seagrass or an
overhang reached. The slight overlaps this allows are intended.

Both generators run the rule on every export (`footprint_audit.with_occupied`).
Hand-captured structures were given the field with
`python tools/shelter-structure-gen/footprint_audit.py --apply <name...>`, which splices it in
as text and leaves the rest of the file as it was. `python tools/shelter-structure-gen/footprint_sheet.py`
draws every footprint before and after, top down, into `preview/footprints.png`.

| Structure | Cells before → after |
|---|---|
| Amphora | 9 → 3 |
| Drowned Bell | 9 → 2 |
| Mangrove Knees | 9 → 7 |
| Basalt Grotto | 9 → 6 |
| Moon Gate | 6 → 3 |
| Leviathan's Seat | 9 → 4 |
| Amethyst Geode, Fallen Column | 9 → 5, 8 → 5 |
| Clay Pipe, Hollow Log | 8 → 2, 8 → 3 |
| Spruce Gazebo | 9 → 1 (the roof overhangs; the floor is one cell) |
| Torii Gate, Sunken Anchor, Lighthouse, Castle Ruin | 5 → 3, 6 → 4, 2 → 1, 6 → 5 |
| Sea Arch | 23 → 12 |
| Sunken Ziggurat, Capsized Galleon | 18 → 16, 18 → 11 |
| Drowned Cathedral, Coral Warren | 21 → 15, 12 → 8 |
| Whale Fall | 63 → 55 |
| Drowned Pagoda | 32 (its plinth fills the box) |

**Anchors.** Structures are stored by anchor cell. A span stores itself at (0,0) of its anchor
tank, whether or not it occupies that cell, and the Castle Ruin doesn't occupy its own anchor.
A floor structure anchored there would have replaced the existing one. That was possible before:
the Galleon's (0,0) was never claimed. Freeing cells makes it more likely, so placement now treats
an anchor key already taken as occupied, and a forced placement clears it.

Notes:

- `OccupiedCellsTest` checks that authored span cells turn with the box exactly as derived ones
  do, at every rotation. It also checks the codec rules: floor occupied cells lie in the footprint
  and keep the anchor, and span cells lie on the box's floor.
- Each span link stores its tank's cells. A span placed before this keeps its old, larger claim in
  its non-anchor tanks until it is picked up and placed again. That is harmless: it is the old
  behaviour.
- Nothing stops another structure's parts standing in front of a shelter mouth now. The single-cell
  cosmetics freed cells take aren't swim obstacles, so plants don't matter, but two structures
  placed close together can block each other's approach corridors. That is the player's arrangement,
  not a lone-tank invariant.

### 12.11 Second pass: off the box (2026-10-03)

The Sea Arch, Leviathan's Seat, Capsized Galleon and Sunken Ziggurat were rebuilt to read as
objects, not extrusions. The new tools are in `tools/shelter-structure-gen/sculpt.py`:

- **`Openings`** carves each shelter's interior and the water in front of its mouths, then walls
  every other face. A noisy surface then can't leak a mouth or seal one. `keep` is what decoration
  must leave alone.
- **`Ramp`** is an ordered run of blocks from light to dark, close in brightness and hue, dithered
  between steps. Each voxel gets a tone from light and weather: sunlit tops pale, undersides and
  hollow mouths dark, a gradient toward the sand. Per-voxel dice read as camouflage; a ramp reads
  as one material. `tools/shelter-structure-gen/swatch.py` measures a block texture's mean colour
  to pick the steps.
- **`bevel`** turns exposed edges into stairs and slabs.

Under the tank's water fog, pale blocks of one family (every sandstone) look alike and terracotta
turns mauve. Contrast has to come from brightness and from saturated life (coral).

| Structure | What changed | Parts | Occupied cells |
|---|---|---|---|
| Sea Arch | Sculpted from noise. Tilted strata with soft beds worn back, a wave-cut notch, a flared window, a limestone ramp. **Eight shelters**: the window (gate), a sea cave at the back of a cove (lurker), a keyhole through the headland and an eye through the stack (pass-through), a fallen slab (open), three pigeonholes | 700 → 1,087 | 12 → 11 |
| Leviathan's Seat | A real vertebra: barrel centrum with a lipped seat, neural arch round an open canal, articular knobs, a raked spine, blade arms curling up. Bone ramp, a fallen rib and vertebra | 260 → 328 | 4 → 6 |
| Capsized Galleon | A real hull section (tumblehome, full bilge) that lists and hogs. Strakes with butt joints, wales, recessed ports, sprung planks showing frames, copper weathering teal → sage → brown, a hole torn amidships with ribs against the water, a sand drift | 1,001 → 1,470 | 11 → 20 |
| Sunken Ziggurat | Talud-tablero tiers with projecting cornices and recessed panels of faded stucco, a stairway cut into the face, a pierced roof comb, a slumped corner and scree, fanged and crested serpent heads, vines from the ledges | 1,663 → 2,393 | 16 → 22 |

Floor-level interiors must stay about 0.16 blocks from the glass. The voxel domain's floor-wall
corner is rounded, and an interior corner outside it makes the shelter unusable without any
error (`FlockEngine` `shelterUsable`). The Sea Arch's cave sits on a rock sill for this. The
Ziggurat keeps kelp between the sanctum's columns: without it the one-voxel gaps became mouths
only the smallest fish fit, and `ObstacleShelterAccessTest` flagged them as unreliable.


### 12.12 Tunnels are gates to fish that never hide (2026-10-03)

A **tunnel** is a hollow with two horizontal mouths facing within ~60° of opposite ways (the Clay
Pipe, the Sunken Ziggurat's tunnel, the Sea Arch's keyhole and eye). A fish with no shelter tag
now takes one exactly as it takes a gate (`FlockEngine.passesThrough`): only when it is already
heading through (within 45°) with a way out the far side, at its own pace, with no length limit,
straight out without stopping, and hidden nowhere, so it doesn't count against the hidden budget.
Its height must still fit the mouth (0.4 × length ≤ the mouth's narrower side). Tagged fish
(visitor, skittish, lurker) keep visiting tunnels as hollows: in, linger, usually out the far
end. A hollow whose second opening isn't a way through, like the Hollow Log's top hole, stays a
hide for everyone.

Before this, a fish that never hides only ran its visit clock when the tank held a gate or an
open shelter, so a lone Clay Pipe was never even considered. Tunnels now switch the clock on too.

**Lurkers prefer dens.** A lurker resting in a tunnel's mouth shuts the way through for everyone
else, since its doorway is closed to other fish whether or not it is home. So `claimHomes` gives
a lurker the nearest one-mouth den that fits it, and a tunnel only when no den fits. Claims
already made are kept.

`ShelterBandVisitTest` covers untagged transit (Clay Pipe 45 entries, Ziggurat tunnel 9, Sea Arch
tunnels 1; the arch's run front to back through a shallow box, so a fish seldom happens to be
heading straight through) and `aLurkerPrefersADenToATunnel` (the Sea Arch's lurker takes the
cave over the keyhole and the eye).

**Cast widened to half the species (2026-10-03).** 15 more free swimmers tagged, so 33 of 64
use shelters: lurkers bull trout, giraffe cichlid, chaunacops, golden mahseer (12 in all); visitors
betta, bluegill, sulphur molly, waterfall climbing cave fish, blind cavefish, red-bellied piranha,
greenstripe barb (15); skittish indian glassy fish, orange australe killifish, golden trout, yellowstripe grunt (6).
Left out on body shape or habit: discus, lionfish, ocean sunfish and the moorish idols (tall), the
seahorse and leafy sea dragon (upright), glass catfish (an open-water schooler).
Only `horizontal_swim` fish move freely, so only they can use shelters at all.
