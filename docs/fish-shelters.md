# Shelters, obstacles and reactive cosmetics

These client-only visual systems are implemented. They do not create fish needs,
maintenance stats or authoritative server behavior. Clients may see different
visits. Movement is steering with timeouts, not pathfinding. Current schemas live
in `fishtank/CosmeticStructure` and `CosmeticReaction`; engine behavior lives in
`:fishsim`. Profiles and shipped structures are authoritative for current content.

## Authoring shelters

A structure may define `shelter` for one interior or `shelters` for several:

```json
"shelter": {
  "interior": [{ "x": 1, "y": 1, "z": 1 }],
  "kind": "hollow",
  "capacity": 1,
  "min_length": 0.0
}
```

`interior` uses integer build-grid cells before structure scale, not tank grid
coordinates. `capacity` is optional (default at least one, based on interior cell
count); `min_length` is an optional minimum rendered body length in blocks.
Rotation/scale must match the footprint and rendered parts. Spans use their box's
build origin (`SpanStructures.buildOrigin`), not an arbitrary member tank.

The loader derives mouths by flood-filling toward outside water, without flooding
below the sand. Sealed interiors fail validation. Mouths have a center, inward
normal and opening size. Each non-gate interior has a hull; other shelters' cells
are treated as water during derivation, but overlapping interiors or interiors
inside another solid hull are invalid. Capture strips `structure_void` markers
from rendered parts and uses them as a single shelter interior. Procedural
multi-shelter designs use the generators under `tools/` for exact interiors.

| Kind | Eligibility and geometry |
|---|---|
| hollow (default) | Opt-in shelter species; solid hull; counts toward hidden budget; can be a lurker home |
| open | Any fitting free swimmer; visible resting visit; no hidden budget or home |
| gate | Any fitting free swimmer; two opposite horizontal mouths, no hull, no dwell or interior-length gate |

A gate's posts remain solid obstacles while its opening is ordinary water. A gate
may contain a soft part such as a lantern; `ShelterKindTest` verifies such parts
are soft. Do not derive gate semantics from a short run: kind affects the hull.

Species opt in to hollow behavior with `"swarm": { "shelter": "visitor" }`,
`skittish`, or `lurker`. Absent does not forbid ordinary gate/open visits.
Shelter users must be FREE_SWIM; pose alone is not a sufficient eligibility test.

## Fit, visits and startles

Mouth height must admit `0.4 * renderedLength`. Hollow/open interiors require
`length <= 1.1 * longestRun`; lurkers may protrude, requiring only `0.6 * length`.
Honor `min_length`. A staging point roughly one body length outside the mouth must
be reachable, clear of other obstacles/hulls and outside the wall margin. Large
shelters need water in front of their doors: a big opening flush against glass is
not usable. Sand-level interiors are tested on their submerged portion.

The state machine is ROAMING -> APPROACH -> ENTER -> INSIDE -> EXIT -> cooldown.
An unreachable approach times out. Rebuilds carry matching visit state; removing
a shelter releases its occupants. During entry/interior movement, a user is
confined to the shelter corridor instead of pushed out by its hull. Other solid
parts of the structure still obstruct it.

- Visitors run seeded per-fish visit clocks. An urge lasts briefly while they seek
  a nearby fitting shelter; current timing lives in engine constants.
- Skittish fish startle when the watcher lunges closer, not merely stands nearby.
  Cover is allocated nearest-first across a tick; fish without a place flinch.
  Startle recovery and cooldown prevent repeated reactions to a stationary player.
- Lurkers claim a fitting home, preferring a one-mouth den to a tunnel; an existing
  valid claim is retained. Without a home they behave as visitors. Their mouth pose
  is visible, with occasional sorties before returning home.
- Hidden occupancy is bounded by `max(1, floor(0.25 * swimmers))`; mouth-resting
  lurkers and open/gate visits do not consume it. Dwell and post-exit cooldown keep
  tanks visibly stocked regardless of fish count.
- A pass-through exit prefers a reachable opposite mouth. Gates are offered in
  the current direction of travel (within about 45 degrees) and never cause a
  U-turn just to take an arch. Entry and exit both check staging reachability;
  exit has a timeout so a wall margin cannot hold a fish forever.

Confinement includes the sprite's half-height. `FlockEngine.renderBobRoom` reports
continuous headroom, and `FishAnimator` caps render bob and nose tilt against it.
A correct center position alone does not prevent the visible fish clipping a bore.

## Obstacles and occupancy

`CosmeticObstacles` rasterizes block outline shapes at quarter-build-unit resolution
and greedily merges exact boxes. `fishtastic:soft_cosmetic` excludes foliage and
other intentionally passable parts. Do not round half-cell offsets or impose a
small box-count cap: both can close real openings or misalign collision and art.
Boxes are cached per structure, cleared on tag reload, and binned per domain cell;
`ObstacleBinningEquivalenceTest` guards equivalence to unbinned queries.

Fill unreachable pockets with non-gate hulls solid before carving shelter interiors.
This prevents initial scatter from placing fish in sealed cavities. A blocked
mouth is unavailable. `closeGaps` probes submerged sides rather than growing an
above-water beam into a wall. Do not add obstacle-climbing or generic stuck-target
retargeting without evidence: previous attempts worsened jerk or acted on targets
the planar wander model does not have.

Cosmetic changes rebuild obstacles, floor and shelters from the cosmetic fingerprint,
not the group distance field. Lone tanks with solid parts and swimmers are promoted
to the planar path; the undecorated binary golden remains locked. Crowded lone tanks
and narrow doorways can still produce lingering/chatter; the owner obstacle review
remains open. Invariant coverage at six fish is not evidence for arbitrary stocking.

`occupied_cells` claims what actually contacts the sand, rather than an overhead
bounding rectangle. Keep footprint rotation consistent with placement and rendering.
Use `footprint_audit.py` and `footprint_sheet.py` to inspect claims; do not overwrite
hand-authored structures by running unrelated datagen providers.

## Reactions

`CosmeticReaction` binds either a nose anchor (`min`, `max`, `facing`) or a locked
gate shelter to an animated reaction. Nose visits create a trigger target; a locked
gate remains obstructed until its reaction opens it. Engine events are keyed by
structure/tank/anchor identity and drained into `ClientCosmeticReactions`.

Clocks are per structure (`mean_seconds`, default 240), with at most one active
reaction per domain and a 30-second rest. A larger population must not multiply
rare spectacle. Grouped parts use hinge motion (`pivot`, `axis`, `degrees`, optional
`sway_ticks`), slide motion (`offset`) or glow. They are excluded from the static
chunk mesh and drawn per frame; only genuinely moving/glowing parts should be grouped.
The renderer owns `open_ticks`, `hold_ticks`, `close_ticks`, sounds and bubbles.

Shipped reactive designs include Giant Clam, Sunken Strongbox, Wayside Shrine and
Sunken Gatehouse. `tools/shelter-structure-gen/reactive.py` defines them. Preview
with `cosmeticpreview` and `reactionOpen=1`; `/fishtastic cosmetic reactionboost
<multiplier>` accelerates clocks for the session, with `1` restoring normal timing.
It does not accelerate the domain rest period.

Openings need roughly 0.4 blocks of clearance above sand; tall floor structures
must leave roaming lanes. Extra depth belongs near the sand or above the swim
band. Test the actual design, size band, rotation and span: nominal mouth size is
not enough when staging water or wall margins are missing.

## Verification and deferred work

Run `:fishsim:test` and `:common:test`, including shelter fit/visit/kind/pass-through,
bob-room, obstacle geometry/access/corridor, rotation and invariant coverage.
`ObstacleExport` writes actual geometry for the standalone harness:

```sh
./gradlew :common:test --tests '*ObstacleExport*' -PexportObstacles=build/obstacles
```

Use `:fishsim:runHeadless --obstacles` via its `simArgs` option and the live render
scenes for visibility. Generated previews are local output, not acceptance records.
Obstacle behavior in crowded decorated lone tanks still needs owner visual review.
Benthic dens, perch/graze behavior and shelter-specific quest objectives are future
ideas, not implemented contracts; see the [backlog](backlog.md).
