# Fish companions

The three-slot party and solo playtest were accepted on 15 October 2026. This is the
current architecture and behavior reference. [Drifter movement](fish-companions-drift.md)
has its own tuning contract. Automated verification does not close the owner reviews below.

## Ownership and simulation

- Each of three slots holds one bonded fish item and produces one body. All slots are open
  from the start. One fish never creates automatic duplicates.
- Identity is `(owner UUID, slot)`. Slot 1 is the party lead, with first claim on a hand visit;
  it is not necessarily the leader of a school.
- Party state belongs to `CompanionStorage`, survives death, and is separate from catch-data
  backups. An old single-companion save migrates into slot 1.
- Bonding and shift-click fill the first empty slot. A full party opens management rather
  than silently replacing a fish. Every species remains bond-gated.
- `:fishsim` owns deterministic agents, goals, bodies and group behavior. `CompanionWorld`
  is a snapshot interface; `CompanionWorldAdapter` supplies terrain, owner state and features.
  `ClientCompanions` owns client lifecycle and visual integration; `FishAnimator` supplies poses.
- Each client simulates synchronized party state locally. Scenes share a start event and
  clock; different clients need self-consistent views, not bit-identical trajectories.
- Bodies move through bounded motors, not scripted follow orbits or position lerps. Fast
  travel beyond their reach uses a scale fade. Preserve the Minecraft-free simulation boundary.

## Formations and shared space

| Members | Behavior |
|---|---|
| Same-species FREE_SWIM | Pair or school, from actual occupied slots |
| Different-species FREE_SWIM | Mixed shoal, partitioned into predators and non-predators |
| Same-species ground BENTHIC | Line or crowd, from the species' `companion_group.formation` |
| Other class/species combinations | Independent units sharing the owner's space |

`companion_group.count` is deprecated and ignored: party items determine body count.
The smallest fish leads a shoal, with slot order breaking ties; the group paces to its
slowest member. Followers retain their own sizes, quality, calibration and seeded bodies,
but share the leader's decisions. Predators never shoal with non-predators; different
predator species may shoal together.

`PartyLayer` works on units (solo fish or formations), using other units' previous-tick
state so stepping order cannot affect results. Keep solo and identical-member formation
fingerprints unchanged unless a behavior change is intentional.

Perchers receive distinct home perches when the party is built. Anchored fish choose the
head, then shoulders; other members use remaining shoulders, head or back. Transient
perches and hands are leased rather than shared. Fades return to a member's own home.
Ground walkers from different units separate while keeping their own minds; they do not
join a forced mixed-species line. Hand visits are approximately once per minute, with
the longest-waiting eligible member next. Slow walkers' lower visibility and some catch-up
lag are accepted tradeoffs. Do not add an attention budget or suppress mutual curiosity
without a demonstrated gameplay problem.

## Menu and item transactions

The menu explains the resulting formation. Each pencil button names its own slot; Enter
commits and Escape cancels. Moving or replacing that slot's fish cancels editing. Text
focus consumes keys. Pile-strip selection asks the server to find a fish identical in
every component and place it on an empty cursor; stale requests and occupied cursors are
rejected. A cancelled selection returns as a loose fish, not automatically to its pile.
Closing uses normal inventory-return/drop handling.

## Ground movement

Benthics normally walk on land and swim when the owner's chest is wet. `GroundBody` owns
terrain contact, hops and gait; `GroundMind` owns following, formations and recovery;
`PerchBody` owns climb/dismount, perches and tumbles. Foot-height breadcrumbs describe the
owner's path. Lines follow the preceding member's trail; crowds use separation and
cohesion without alignment. Plaice use a flat ripple glide rather than a walking crouch.

- Half-block rises are walked; one-block rises use clearance-checked hops. Drops up to
  three blocks land with squash. Walking remains heading-first and size-limited.
- Idle members mill around the feet and may climb after sustained rest. Walking six net
  horizontal blocks within four seconds, at least ten seconds after climbing, dismounts
  them. Mining, pacing and turning in place should not cause repeated transitions.
- Lag beyond about four blocks triggers scurry bursts. Beyond about sixteen blocks, or
  three seconds with no reachable trail, fade onto a perch rather than repeatedly falling
  behind on the ground. Riding and elytra travel send members to perches immediately.
- Damage or a hard landing causes a tumble, landing squash and turn, not a flip-over.
- Any nearby player's feet may scatter walkers. Inspect, hide, guard, nuzzle and hand
  visits use ground targets; occasional overtakes, roadside stops and turn reveals keep
  them visible. Gait drives shape; idle and perched members retain a slow breath.
- Mood poses on the ground (2026-10-28): a walker on the floor only lifts its head, hinged at
  the tail's floor contact (`CompanionRender.applyGroundHinge`). Nose-down moods (dip, droop,
  sleepy, dance's down half) leave it flat. A `floor_sit` fish also drops the mood roll and
  keeps only the shiver. Before this, the centre-pivot pitch buried a hellbender's head up to
  ~0.11 blocks on a dip. Swimming and perched poses are unchanged. Owner look pending
  (`/fishtastic companion mood glum|sleepy|moon|glee`).

## World reactions and social scenes

Fish react cosmetically to nearby water, darkness/lights, music and warmth. Species traits,
temperament, mood, novelty and habituation shape reactions. Swimmers/drifters approach;
walkers require a path; perchers/anchored fish look or lean. Reactions stop as the owner
leaves. Emotes and particles are rate-limited and respect particle settings.

Features have stable identities. A per-level shared discovery index is capped at 512
block reads per client tick; unchanged classified areas need no discovery reads while
standing. Neutral world input must preserve baseline fingerprints. Habitat rules derive
from shipped profiles: water zones require a wet owner, while cave, altitude and Nether
homes also apply on land. Biome weights below one are excluded; climate comes from home
biomes/zones, not snow catch weights. The species table was approved without overrides
on 15 October 2026; do not maintain a second manual copy of profile-derived data.

Cross-player scenes currently take FREE_SWIM participants only. The same-species ring is
horizontal above head height; crossing paths separate vertically. Paths use fixed speed
with derived periods, and group followers keep formation. Scenes begin with a short greet,
override ordinary laps and suspend the party predator scene; emergencies may refuse them.
Cancellation is local when owners/partners leave or the party rebuilds. Fishing reactions,
mob reactions, tank reactions and cross-player scenes for other classes remain deferred.

## Verification and remaining owner review

Use `:fishsim:runCompanionViewer` for motion and `:fishsim:runCompanionHeadless` for
scripted captures. See [verification tooling](../tools/README.md) for isolated game scenes.
Tests cover terrain contact, penetration, heading, bounded motion, repeatability,
frame-rate independence, ownership, transactions and group behavior. Metrics explain
behavior; the viewer and normal play decide whether it reads clearly.

Open owner checks:

- Ground movement in a normal world: plaice orientation/ripple, push-off swell, pillar
  recovery, and block-sampling cost with `/fishtastic companion debug true`.
- Starfish lines may fade on stairs; ground hand approaches may pass too near the owner's
  feet. These gaps do not authorize restoring rejected steering experiments. A resting
  crowd tighter than its walking formation needs owner review before changing.
- Cross-player ring pivot, heart size/placement and approach clearance. Scripted solo
  and two-client checks were recorded as passing; an owner two-client check is optional.
- Portuguese man o' war bob review remains open; see the drifter reference.

Useful ground commands: `/fishtastic companion ground|climb|tumble|drowsy|hurt`,
`/fishtastic companion swell <gain>`, and `/fishtastic companion debug <bool>`.
`drowsy` is visible only while the owner stands still.
