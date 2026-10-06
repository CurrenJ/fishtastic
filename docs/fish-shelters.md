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
| **Whale Fall** (span; skull shipped 2026-10-02) | the two eye sockets | fish up to ~0.31 blocks | the first spanning shelter. `gen.py` emits the skull's widest part (cells 5..7 x 1..2 x 4..10, capacity 3); the eyes face each other, so fish swim in one and out the other. Only that part, because the engine confines a fish to the interior's bounding box and the whole hollow narrows toward the snout. Between the ribs is still to come |

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
3. **Gates**, retrofitted onto the arches, Torii Gate and Castle Ruin. Only worth doing after 2.
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
