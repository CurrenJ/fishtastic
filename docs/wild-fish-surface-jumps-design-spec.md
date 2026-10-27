# Wild fish surface jumps

**Status:** Interactive-school goal-based jumps for all free-swimming fish are implemented (§12.5),
with the reworked leap motion and splashes (§12.6); unit tests pass and the `fishschooljump` render
self-test is the end-to-end check; in-game playtest pending. Ambient fish behavior remains design-only.

**Date:** 2026-10-27

**Purpose:** Add occasional, species-specific breaches to wild ambient fish so natural water
feels more alive and observant players get a small moment of surprise.

## 1. Summary

Some ambient fish sometimes leave the water briefly, arc above the surface, and splash back in.
The behavior is rare, harmless, and authored by species. A jump can make a nearby fish population
feel active, but it does not identify a catchable individual or change fishing outcomes.

The first release applies to decorative ambient fish in natural water. Those fish are client-side
visuals today, so jumps are local visual events: nearby players may not see the same jump. Interactive
fish encounters and tank fish are outside the first release. An extension path for interactive
encounters is assessed in §12.

## 2. Goals

1. Make rivers, lakes, and other supported natural water feel less static.
2. Give a few species a recognizable surface behavior.
3. Reward attention with a brief, readable moment without adding a required minigame.
4. Preserve the existing distinction between ambient sightings and shared, catchable fish encounters.
5. Keep the behavior light enough to scale with existing ambient fish settings and budgets.

## 3. Non-goals

- No jumps in fish tanks.
- No changes to fish eligibility, fishing pools, catch odds, bait value, quality, rewards, or progression.
- No new item drops, fish damage, stranding, escape, or catchable airborne fish.
- No item or player action that summons or guarantees a jump.
- No new server-owned wildlife population or synchronized world event.
- No predator simulation in the initial release.

## 4. Player experience

The player is near natural water and notices a fish approach the surface. It briefly breaks the water,
makes a short arc, and returns with a restrained splash. The event is over quickly; the fish resumes
its normal movement.

Most events are a single fish. A small subset of species may have a rare follow-up in which one nearby
fish of the same species jumps shortly afterward. This should read as a brief school reaction, not a
repeating loop.

A jump tells the player that this species is present in the local ambient population. It does not
promise a catch or improve a cast. This is consistent with [Ambient fish](fish-in-world.md), where a
sighting indicates possible eligibility but not a targetable individual.

## 5. Scope and relationship to existing fish

The initial behavior belongs to the client-side ambient fish described in
[Ambient fish](fish-in-world.md). It follows their existing density and enable settings and does not
load chunks or request extra fish.

[Interactive fish encounters](in-world-fish-encounters-design-spec.md) are deferred from the first
release. Their visual cues already communicate catch opportunity; any extension must preserve the
encounter's existing server authority, reward identity, and catch behavior. §12 assesses a way to
synchronize a cosmetic jump through the existing school simulation.

## 6. Behavior contract

### 6.1 Species authorship

Each species is explicitly assigned a jump tendency. The initial values are:

| Tendency | Intended use |
|---|---|
| None | Species that do not normally breach, or whose movement does not read clearly as a jump. |
| Rare | A surprising occasional breach for a suitable species. |
| Occasional | A more characteristic behavior, still bounded by the local event cap. |

Temperament is not an input: it describes fishing minigame behavior, not surface ecology. Species
with a tendency of None never jump. New fish default to None until their behavior is reviewed.

The first content pass should favor fish whose real or familiar behavior supports a clear leap,
such as trout, char, grayling, and mahseer species. This is a shortlist for review, not an automatic
species assignment.

### 6.2 Surface and landing conditions

A jump may start only when the fish is in valid ambient water and the water surface has enough clear
space for that species' animation. Do not start under ice, a solid overhang, or a low ceiling. The
landing point must be valid connected water.

The motion is visual. It must not move a fish onto dry land, through a solid block, or into a state
where it can take damage or be collected. If the visual cannot complete safely because the water or
space changed, it ends at the original water position.

The first release uses species tendency and open-space eligibility; it adds no hard time-of-day,
weather, moon, or biome gates. Existing natural habitat and environmental rules continue to decide
which ambient species may be shown.

### 6.3 Frequency and grouping

The event should feel rare enough to be noticed, but not so rare that players assume it is broken.
As an initial playtest target, an attentive player at a well-populated location with default ambient
density should see roughly one to three jump events in ten minutes. Sparse locations and species with
low jump tendency may produce none.

Use a local event cap so a dense population cannot produce overlapping jumps. Give each fish a
substantial cooldown, and keep a follow-up school jump inside the same capped event. Frequency is a
tuning value, not a guarantee or a reward schedule.

The jump is not triggered by a fishing bobber, cast, bait, player interaction, or the completion of
an encounter. A nearby player does not guarantee or prevent the behavior.

## 7. Presentation

- Keep the full fish silhouette visible above the surface long enough to recognize, with a short arc
  rather than a high or distant launch.
- Pair the motion with a small surface splash or ripple. Do not use the sparkle cue reserved for
  interactive catchable fish.
- Keep sound subtle and local. Particle reduction may hide optional splash particles, but should not
  suppress the fish animation itself.
- The event must not pause normal movement for nearby fish or obscure fishing UI.
- Jumps follow the existing ambient-fish enable and density settings. The first release adds no
  separate jump toggle.

## 8. Multiplayer and gameplay meaning

Ambient fish populations and movement are local visualizations, so a jump is local to each client.
Two players standing together may see different jump timing. The behavior does not create an
authoritative entity or change what either player can catch.

The existing ambient species sighting remains the only gameplay meaning: that species may be
eligible in nearby natural water under the current fishing context. A jump is not stronger evidence
than the sighting and must not imply a bonus catch chance.

## 9. Acceptance scenarios

| Scenario | Required result |
|---|---|
| Species has tendency None | It never jumps. |
| Eligible species swims under open surface water | It can occasionally make a brief, readable jump. |
| Fish is under ice, a low ceiling, or a solid overhang | No jump begins. |
| Water changes during a jump | The animation ends safely in water; the fish cannot strand or take damage. |
| Ambient fish is disabled or density is zero | No jump animation or jump-selection work occurs. |
| Ambient density is reduced | Jump volume falls with the visible ambient population; it does not repopulate water. |
| Several eligible fish share a dense area | The local event cap prevents a constant or overlapping stream of jumps. |
| Species is authored for a rare follow-up | At most one nearby same-species fish follows within that event. |
| Two players observe the same ambient population | Each may see different timing; no network encounter or reward state is created. |
| Player casts where a fish jumped | The ordinary fishing rules and catch result apply. |
| Player sees an interactive school nearby | Its existing availability cue and encounter behavior remain unchanged. |
| Particle settings reduce splash effects | The fish animation remains visible; optional particles follow normal settings. |

## 10. Rollout

### Phase A: Single-species-authored ambient jumps

Add the species tendency, safe surface-space check, short animation, splash, cooldown, and local event
cap for a small reviewed roster. Playtest visibility from shore and boat, event frequency at default
and reduced ambient density, and whether the animation reads clearly at different water depths.

### Phase B: Species variants and rare follow-ups

After Phase A feedback, add distinct jump styles or rare same-species follow-ups where they improve
recognition. Keep the same event cap and cosmetic-only contract.

### Later extension: Interactive encounter fish

The recommended extension is the synchronized presentation event in §12. It still needs a multiplayer
readability playtest before being enabled for interactive fish. It must not alter encounter authority,
fishing mechanics, or rewards.

## 11. Playtest decisions to settle

1. Which species belong in the initial Rare and Occasional groups?
2. Does the one-to-three-events-per-ten-minutes target feel noticeable without becoming repetitive?
3. Do rare same-species follow-ups add delight, or make the behavior feel scripted?
4. Are the jump height, arc, splash, and sound readable from shore without competing with fishing?

## 12. Interactive-school extension investigation

**Finding:** Interactive fish can jump without converting them into Minecraft entities or changing
their fishing simulation. The preferred solution is a server-scheduled presentation event layered
over the existing deterministic school simulation.

Interactive school members are positions in a FishSchoolSim, not independent Minecraft entities.
The server advances the school, while tracked clients replay the same setup and stamped inputs. The
shared [GroupFishDraw](../common/src/main/java/grill24/fishtastic/client/renderer/GroupFishDraw.java)
path renders both ambient fish and school members. These are useful extension points.

### 12.1 Options

| Option | Advantages | Costs and risks | Assessment |
|---|---|---|---|
| Client-local render animation | Smallest change; no network or sim changes. | Players see different timing for the same shared encounter; late joins and visual debugging are less predictable. | Keep for ambient fish, whose timing is already local. Not preferred for interactive fish. |
| Server-stamped presentation event | Uses the existing school input/replay path; every tracking client sees the same event. Engine positions and fishing logic stay underwater. | Adds one input type, transient per-member presentation state, render support, and a surface-clearance check. | **Recommended.** |
| Airborne movement in fishsim | Could model a full swim-up, breach, and re-entry. | WaterDomain currently constrains motion to fitted water. Leaving it would affect movement, body-fit rules, saved positions, bobber approaches, and bite timing; air collision and safe landing become simulation rules. | Too broad for a cosmetic behavior. Consider only if airborne movement later has gameplay consequences. |
| Convert school members to real entities | Would inherit vanilla entity movement and tracking. | Replaces the current shared school simulation, requires entity lifecycle and networking, and risks duplicating the existing visuals and catch identity. | Reject for this feature. |

### 12.2 First pass (species-gated launch band; superseded by §12.5)

1. The server chooses a member and stamps a SurfaceJump input at the current school step. It only
   schedules available members, and only for species authored to jump.
2. The server checks the member's current water surface and confirms enough clear space above it for
   the selected arc. Only start when the member is already in a shallow launch band; do not draw a
   fish rising several blocks from deep water. WaterDomain.surfaceAt can identify the top of the
   visit's water; the server world must still validate already-loaded blocks above it because the
   domain stores water, not overhead geometry. This check must not load chunks.
3. SurfaceJump is a presentation state, not a new fish position. The member keeps its normal
   underwater FlockEngine position and remains governed by ordinary catch and approach rules.
4. The client derives the jump phase from the stamped start step, duration, and height, then applies a
   temporary render offset through GroupFishDraw/GroupFishPose. The draw bounds and lighting sample
   must follow the raised pose. Keep the first arc short so the school sim can continue its ordinary
   swim underneath without the fish appearing to travel far through the air. A small neutral splash
   can mark takeoff and landing.
5. The existing quality-tinted catchability trail remains distinct from the splash. The jump must
   not replace, recolor, or hide the cue that says the member is available to approach.
6. If the member starts an approach, leaves the sim, is caught, or departs, the jump ends cleanly.
   It must not delay the bite or make the member unavailable.

The event can travel through FishSchoolInputPacket; that packet already carries stamped
FishSchoolSim.Input values and is used for client replay. No per-frame jump packet or streamed
position is needed. FishSchoolWire.INPUT needs a bounded codec case for the new input. The school
fingerprint currently covers positions, so the jump phase must also be included in deterministic
verification or explicitly represented in the periodic visual state.

The opt-in `fishschooljump` render self-test forces one live-school jump in a shallow open pool,
captures 18 frames across the arc, and checks that the replayed client sees the jump, reaches a
raised pose, lands, and stays in lockstep. Run it with
`python scripts/run_render_checks.py fabric --solo --scenes fishschooljump`.

### 12.3 Content and tuning (as implemented, superseded by §12.5)

The first interactive pass used a species-authored `surface_jump` tendency (rare or occasional), a
25% / 65% visit roll, at most one event per visit, and a 0.9-block launch band. That tuning is
replaced by the goal-based rework in §12.5. The behavior is not selected from fishing temperament and
adds no biome, time, weather, or moon gates.

### 12.5 Rework: goal-based jumps for all free-swimming fish

**Status:** Specified with the owner and implemented 2026-10-27; in-game playtest pending.

**Why:** The 0.9-block launch band made the jump a coincidence. A fish that was deeper than the band
when the roll happened produced no candidates, the roll was marked resolved, and the visit never got
another attempt. The rework makes the jump something the fish decides to do and swims to.

**Eligibility.** Interactive jumps are not species-gated. The `surface_jump` profile field and the
tendency tiers are removed (FishProfile and the six profile JSONs). A visit is eligible when its
locomotion is `FREE_SWIM`. Benthic, anchored, drift, and any other class never jump. This applies to
interactive school fish only; the ambient design in §6.1 keeps its species tendency until that
feature is built, and the two should be reconciled then.

**Flow.**

1. **Roll.** After the visit's deterministic delay and at each recheck interval, the server rolls once
   with a flat 60% chance. The roll does not depend on any member's depth.
2. **Pick.** From `AVAILABLE` members whose depth below the surface is at most 4 blocks, choose one
   deterministically, then choose a launch point just under the surface above a water column that
   passes the loaded-chunk, collision-free clearance check for the body plus the breach height. If no
   member or no column qualifies, nothing is started and the visit stays eligible for a later roll.
3. **Ascend.** The server stamps a new `SurfaceGoal` input naming the member and launch point. The sim
   steers only that member there through the engine's nose errand, the same one a bobber uses, at
   `GOAL_SPEED_SCALE` (3x). The fish is never teleported and the rest of the school keeps its normal
   behavior. The launch point is the first of up to eight tries: straight above the fish, then
   scattered within 3 blocks of it, each needing a point just under the surface with clear, loaded
   space above.
4. **Launch.** On arrival (the errand's touch) the sim itself starts the 32-tick presentation arc, so
   no second input is needed and every copy starts it on the same step. The fish holds its nose at the
   point for the arc plus 4 ticks, and the goal ends when it has backed off and is free. Height is the
   breach height above the launch point.
5. **Abort.** The goal ends with no jump if the member starts an approach (the sim calls the goal off
   itself, so a bobber always wins), leaves the sim, the water changes so the errand cannot continue,
   or it has not arrived within the timeout. An abort never delays a bite or makes the member
   unavailable, and shows nothing special to the player.

**Cap and repeats.**

- A visit may jump more than once. Only one goal or jump is active per visit at a time.
- Only a completed jump counts. An aborted or timed-out goal does not, and neither does an empty pick.
- After a completed jump, or after a failed roll, the next roll waits a cooldown. Initial value: one
  to four minutes, drawn per roll from a generator seeded by the visit and the game time.
- A goal that was called off, or a successful roll that finds no member or launch point, is not a
  completed jump and uses no cooldown, but the visit waits a short retry pause (10 seconds) before
  rolling again so a stuck pool does not retry every recheck.
- The saved state replaces the single used-event flag with the tick of the next allowed roll
  (`next_surface_jump_at`), so the cooldown holds across reloads. The goal and jump poses stay
  transient, so a fish never resumes midair, and a goal in progress at reload is dropped. A completed
  jump is one whose fish reached the launch point and was not called off during the arc.

**Cue.** The ascent uses the member's normal pose and its quality-tinted catchability trail unchanged.
No extra hint is added in this pass.

**Tuning knobs (all initial values).**

| Knob | Initial value |
|---|---|
| Roll chance | 60% |
| Max depth to pick a member | 4 blocks |
| Ascent speed | 3x (`GOAL_SPEED_SCALE`) |
| Goal arrival timeout | 400 ticks |
| Retry pause after a called-off goal or empty pick | 200 ticks |
| Cooldown between rolls | 1 to 4 minutes, seeded spread |
| Breach height / arc length | 0.4 blocks / 32 ticks (superseded by §12.6: 0.5 blocks, about 47 ticks) |

**Measured ascent time.** In `FishSchoolSimTest` probing, a two-block climb plus about three blocks of
sideways swim took 530-650 ticks at 1x, 275-320 at 2x, 190-220 at 3x and 150-170 at 4x. That is why
the ascent is 3x and the timeout 400 ticks (a four-block climb at 3x is roughly 300). The 200-tick
timeout first specified would have called off nearly every goal.

**Wire and verification.** `FishSchoolWire.INPUT` gains a bounded `SurfaceGoal` case (`SurfaceJump`
remains for tests and replay). The school fingerprint covers each goal's id, member and arrival as
well as the jump phase, so replay divergence is caught. Arrival is the errand's touch event, which
the sim already forwards to the server like a bobber's. `fishschooljump` now builds a three-block pool,
sends a goal through the debug seam, waits for the fish to arrive, and photographs the arc.

### 12.6 Jump motion rework (2026-10-27)

**Status:** Implemented; unit tests pass; in-game look pending.

**Why:** The owner found the first arc weak: a level fish bobbing straight up and down on a
sine, with no lean, no travel and one small splash.

**Motion.** `SurfaceJumpArc` is the drawn body's path in sim-local coordinates. It is pure and
deterministic, so every copy draws the same jump.

| Phase | What the fish does |
|---|---|
| Commit | While the fish swims in toward its launch point, it commits once it is within 1.2 blocks horizontally, no more than 1 block deep, and heading at the point. The jump starts from its live position and velocity. |
| Run-in (6 to 24 ticks, about 8 typically) | A Hermite curve from the swim it took over to the exit point at the launch point, arriving at the exit velocity. It speeds up, curves upward and leaves the water at 60°. Its tail beat ramps up. |
| Flight | Under real gravity (9.8 m/s², one block per metre), the body's centre peaks at the breach height. |
| Glide (16 ticks) | Water drag bleeds the forward speed to 25%. The dive slows faster than the forward run, so the fish levels out about half a block under the surface and does not climb back up. |

Position and velocity are continuous throughout, including the hand-over from the engine's
swim. Over the run-in, pitch and yaw ease in from the engine's own pose (level, facing its swim)
to the velocity's own angles. The fish therefore leans back gradually as it curves up, leaves
the water at full lean, is level at the apex and noses down into the water. A 0.5-block breach
takes about 37 ticks from commit to hand-back, and the fish lands about 2 blocks past the launch
point.

**Why the commit (2026-10-27, owner):** the first rework let the engine bring the fish to a stop
at the launch point, and a separate wind-up then reared it back from rest. The seam between the
sim's arrival and the scripted rear-up read as unnatural. Committing on the way in lets the fish
swim straight into the leap. If a fish reaches the point without committing (it came up from
straight below, or was not heading at the point), it leaps from where it is, as a fallback.

**Heading.** The heading is the line the fish swims in on to the launch point. When the point is
straight above the fish, the fish leaps the way it already faces. The nose anchor faces the same
way, so the fish arrives already lined up. `FishSchoolSim.goalHeading` gives this heading to the
server and the sim alike.

**Hand-off.** Through the jump, the engine fish carries on to the launch point and holds there.
The drawn body follows the arc wherever the engine fish is. On landing,
the sim moves the engine fish to where it was drawn (`FlockEngine.relocate`). The fish keeps the
glide's end speed along its heading, and the goal ends there. The fish therefore swims on from
the landing instead of being pulled back to its launch point. The relocation is deterministic,
so lockstep holds. A cancelled jump folds its pose back into the engine fish over 4 ticks and is
not relocated.

**Fit.** At the commit, the sim tries arcs from the longest down: apex scale (1, 0.6), then reach
(1, 0.6, 0.3), then run-in scale (1, 0.7). The first arc whose underwater samples are all in the
water, whose re-entry surface is within 0.35 blocks of the launch surface, and whose length fits
the goal's duration wins. If none fits at the commit, the sim tries again on later ticks as the
fish swims closer. If none fits when the fish reaches the point, the goal ends with no jump.
The server's swim-up timeout counts a committed fish as arrived (`goalStarted`). The server's sky check now
covers body-sized boxes every half block along the flight, from launch to re-entry, up to the
apex plus the body. The apex is never below `0.25 + 0.5 × body length`, so a long fish clears.

**Splashes.** The exit and re-entry points are where the body's centre crosses the surface.
`surfaceJumpCrossings` reports them, and both scale with the fish:

- **Exit:** a narrow sheet of droplets drawn up and forward after the fish, a ring, and vanilla
  splash particles.
- **Re-entry:** a wider crown thrown out round the entry point, three rings, more splash
  particles, and bubbles carried down after the fish.

The tints are plain water whites, never the quality tint. The splashes are silent.

**Trail and bite cue.** Reaching the launch point is an engine touch, the same nose errand a
bobber uses. The client no longer plays the bite fountain for it, because that read as a bite
before the fish left the water. The `fishschooljump.no_bite_burst` check guards this. From the first tick of
a jump to the tick it lands, the catchability trail is laid along the drawn body's path, so it
follows the arc. It is not laid along the held engine fish, and it does not draw a line across
the relocation. Stationary glints and sprint bubbles are paused for the leap.

**Lockstep and replay.** The commit, the arc fit and the landing relocation are all decided
inside `FishSchoolSim.step` from engine state and the domain that every copy shares. They use
`StrictMath` for transcendental functions, and the checksum covers each jump's arc fingerprint,
each goal's started flag, and the relocated positions. A copy that catches up by replaying the
setup and inputs therefore reproduces the jump exactly. The client's splash and trail
bookkeeping resets while a copy is catching up, so a replay never re-fires old splashes.
`FishSchoolJumpReplayTest` checks the following:

- A copy that joins at any point (before the goal, during the swim-up, during the run-in, flight
  or glide, or after landing) has no mismatches and draws bit-identical poses.
- A copy forced to rebuild during the flight replays the whole jump exactly.

Epoch changes (input limit, unwatched renewal, reload) drop a goal in flight on the server and
on every client together, so they stay consistent. A watched fish caught mid-arc at an
input-limit renewal would snap back to its engine position, but that is rare.

**Debug trigger:** `/fishtastic schools jump` (gamemaster) sends an available fish from the
nearest school to breach now. It skips the roll and the cooldown; the swim up, the fit and the
jump are the real ones.

**Tuning knobs:** launch angle 60°, commit distance 1.2 blocks, commit depth 1 block, commit
alignment 0.5, run-in 6 to 24 ticks, glide 16 ticks, glide end speed 25%, dive decay 0.35 per tick, breach height 0.5 blocks (server), duration budget 64 ticks.

### 12.4 Additional acceptance scenarios

| Scenario | Required result |
|---|---|
| Two clients track the same school | They render the same member's jump at the same simulation phase. |
| A client starts tracking during a jump | Setup replay reaches the correct active or completed phase without position mismatch. |
| Member is approaching, recovering, departing, or caught | It does not start a new jump; an active presentation ends without changing fishing state. |
| Candidate has a blocked or low surface | The server does not schedule the jump. |
| Fish is rendered above the water | Its server position remains in valid water and its ordinary catch binding is unchanged. |
| Jump begins near a bite | The jump cannot delay, duplicate, or suppress the normal bite transition. |
| `fishschooljump` render self-test | The rendered school member rises and lands, and its replayed copy reports no fingerprint mismatch. |

Rework scenarios (§12.5):

| Scenario | Required result |
|---|---|
| Visit locomotion is not `FREE_SWIM` | It never starts a goal or jump, whatever the species. |
| Any `FREE_SWIM` species | It can jump; there is no species gate. |
| Chosen member is deeper than 4 blocks | It is not picked; if no member qualifies the roll starts nothing and a later roll may still succeed. |
| Member is 2 to 4 blocks down when rolled | It swims up to a launch point, then jumps on arrival. |
| Goal not reached within the timeout | It ends silently, no jump, and does not count toward the visit's jumps. |
| Member starts an approach or is caught mid-ascent | The goal ends; the bite and catch proceed unchanged. |
| Launch point loses clearance before arrival | The goal aborts without a jump. |
| A jump completes | The visit may roll for another after the cooldown; never two active at once. |
| Reload during a goal | The goal is dropped, and the cooldown tick persists. |
| Two clients track the same visit | Both see the same ascent and the same jump phase, with no fingerprint mismatch. |
