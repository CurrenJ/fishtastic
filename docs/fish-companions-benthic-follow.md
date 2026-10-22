# Fish companions: benthics on the ground

Status: ground-follow behavior is implemented and wired into the game. The owner's live gameplay
review and block-sampling cost check remain open; see §10. Builds on the
[companion simulation architecture](fish-companions-sim-plan.md).

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

## 8. Current implementation state

Ground walking, terrain-aware movement, perch transitions, line and crowd formations, player
scatter, in-view beats, and recovery from blocked approaches are implemented. The remaining visual
and gameplay checks are listed in §10; earlier phase plans are retired.
## 9. Owner answers (2026-10-11)

- **Q1. Riding and elytra exception:** yes, climb on or fade onto the perch at once (§6).
- **Q2. Plaice:** a flat ripple-glide on the ground (§4).
- **Q3. Large solos on the head:** fine as is: "just right".
- **Q4. Stepped-on scatter:** for any player, not just the owner (§5).
- **Q5. Tumble art:** a landing squash and a turn is enough; no flip-over (§3).


## 10. Remaining review

The feature still needs a gameplay pass in a normal world. Check:

- Plaice nose-first orientation and whether its ripple reads as a glide.
- Whether the push-off swell on slugs, spider crab, and octopus reads as lively; tune with
  `/fishtastic companion swell <gain>` if needed.
- Whether pillar recovery returns a blocked companion to movement or its perch cleanly.
- Block-sampling cost with `/fishtastic companion debug true` enabled.
- Whether a crowd resting tighter than it walks is acceptable. Ask the owner before changing it.

Known behavior gaps to watch are starfish lines fading on stairs and benthics passing too close to
or through the owner's feet while approaching a hand visit. The open stair and alongside problems
are not permission to restore a rejected steering experiment. More performance work is only needed
if the in-game cost line shows a problem. NeoForge GameTests were not run at the last recorded
integration checkpoint.

Commands: `/fishtastic companion ground | climb | tumble | drowsy | hurt | swell [gain] | debug <bool>`.
`drowsy` is visible only while the owner stands still.

### Code map

| Area | Responsibility |
|---|---|
| `fishsim/.../companion/GroundBody.java` | Ground movement, terrain contact, hops, gait, and ripple timing |
| `GroundMind.java` | Follow, catch-up, fade, formations, in-view beats, and blocked-approach recovery |
| `PerchBody.java` | Perch slots, hops, climb/dismount, tumble, and attention timing |
| `Mind.java` | Ground-target desires and feature-specific behavior |