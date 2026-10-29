# Legendary fish breach celebration

**Status:** Runtime, visual implementation and vanilla audio fallback in progress; original audio and in-game review remain open
**Date:** 2026-10-28
**Scope:** Interactive-school fish whose rolled catch quality is Legendary
**Working name:** Heaven Opens

## 1. Summary

When a Legendary-quality interactive school fish breaches the surface, its ordinary jump becomes a
brief, extravagantly joyful spectacle: a prismatic takeoff, a rainbow following the flight, a huge
flare and field of stars around the apex, a cyan-and-pink dreamlike screen wash, a jubilant musical
sting, and a glittering re-entry.

The fish and every effect owned by the celebration enter isolated slow motion as the fish approaches
the apex. The slowdown is real within the jump system: the synchronized school simulation advances
a higher, longer jump arc than an ordinary breach (§7.8) through a deterministic variable-rate
playback clock. The fish pose,
tail animation, catchability trail, rainbow, custom particles, flare, rays and other celebration
animation all use that clock. Minecraft, other fish, unrelated particles, player input and fishing
gameplay continue at normal speed.

This is a presentation reward for noticing a vanishingly rare fish. It does not alter jump frequency,
quality rolls, catch odds, availability, bite behavior or rewards.

## 2. Player experience

The player sees the normal gold trail of a Legendary school member rise toward the surface. As its
body breaks the water, an oversized cyan-white splash opens into a saturated rainbow that frames the
fish's path. Harp notes rise and the fish begins to move progressively more slowly.

Near the apex, the fish hangs in an isolated pocket of slow time. A pink-gold flare opens behind it,
soft rays turn across the view, and large four-point stars accumulate around the silhouette. The
world keeps moving normally, making it clear that the fish alone has become momentous. A choir and
bell land at the apex while the color treatment reaches its brightest cyan, pink and gold.

The fish accelerates smoothly back toward ordinary playback during the descent. It re-enters through
three rainbow rings and a glitter fountain, then glides underwater and resumes its normal school
motion. The wash, rays and remaining stars resolve over roughly another second without interrupting
the player.

The intended emotional register is completely sincere visual excess: a fantastical airbrushed ocean
postcard treating one fish jump as the happiest event in creation.

## 3. Goals

1. Make a Legendary fish's breach instantly recognizable as an exceptionally rare event.
2. Reproduce the reference mood through color, light, sparkles, rainbow imagery and jubilant sound
   without displaying or copying the reference image.
3. Create convincing isolated slow motion by retiming the fish and every celebration-owned visual,
   rather than merely running a normal jump behind a slow overlay.
4. Preserve deterministic school replay and multiplayer agreement.
5. Keep all fishing authority and outcomes unchanged.
6. Make the full treatment optional and provide a calm accessible presentation.
7. Build the timing system as a reusable jump playback profile, without turning the whole world or
   arbitrary particles into a time-scaled simulation.

## 4. Non-goals

- No global game-speed, tick-rate, camera or player-input slowdown.
- No pausing entities, weather, unrelated particles, other school members or the fishing minigame.
- No change to quality odds, school generation, jump rolls, cooldowns or candidate selection.
- No guarantee that a Legendary member will jump before its visit departs.
- No reward, advancement, quest credit or catch progress for witnessing the event.
- No camera takeover, forced viewpoint, field-of-view change or automatic tracking of the fish.
- No literal use of the supplied meme image or third-party music.
- No hot-air balloons, ornamental frame, full-screen title or background dolphin chorus in this pass;
  those belong to the deferred maximal treatment in §17.
- No first-pass support for decorative ambient fish. They do not have authoritative member quality.

## 5. Eligibility and authority

### 5.1 Eligible fish

The feature applies only when all of the following are true:

- the member belongs to an interactive school;
- its server-owned rolled `FishQuality.Quality` is `LEGENDARY`;
- it is making the existing synchronized surface jump; and
- the jump actually reaches its outward surface crossing.

Species rarity is not the trigger. A common species with Legendary catch quality qualifies; a rare
species with Common, Uncommon, Rare or Epic quality does not.

### 5.2 Server decision

The server already knows each member's quality when it selects a jump candidate. It stamps the jump's
playback profile into `FishSchoolSim.SurfaceGoal`:

| Profile | Meaning |
|---|---|
| `NORMAL` | Existing jump timing and presentation. |
| `LEGENDARY_CINEMATIC` | Deterministic apex slowdown and eligibility for the Heaven Opens client presentation. |

The profile is part of `FishSchoolWire.INPUT`, replay history and the simulation checksum. Clients do
not infer authoritative motion from their local quality map. The map remains useful for presentation
validation and tint selection, but a corrupt or stale client cannot choose a different jump clock.

The direct debug/test `SurfaceJump` input receives the same profile so tests can force either path.
Unknown wire values are rejected or safely decoded as `NORMAL`, following the selected compatibility
policy when implementation begins; they must never silently become cinematic.

### 5.3 Presentation trigger

The cinematic client presentation begins on the actual outward surface crossing, not when the server
rolls, when the member starts ascending, or when the jump commits. An aborted goal therefore produces
no rainbow, flash or sound. The deterministic slowdown may begin at the crossing and is already part
of the stamped arc state.

World-space effects may be created for every tracked qualifying crossing. The screen treatment and
foreground sound additionally require:

- interactive schools are locally visible;
- the camera is in the same dimension;
- the crossing is within 32 blocks; and
- the fish is in or close to the camera frustum and is not fully occluded by solid geometry.

The exact visibility probe is an implementation detail to validate in playtest. Failure to establish
visibility suppresses the HUD treatment; it does not alter the synchronized jump.

## 6. Event timeline

The timeline is described in jump source time. Its wall-clock length expands near the apex according
to §7. Initial values are tuning targets, not content guarantees.

### 6.1 Swim-up and run-in

The existing surface goal, approach speed and Hermite run-in remain unchanged; arc fitting tries the
bigger legendary arc of §7.8 first. The player
can read the member's ordinary Legendary gold catchability trail while it ascends. No celebratory cue
fires early, so a goal that is cancelled for a bobber or cannot fit an arc still ends silently.

### 6.2 Surface exit

At the outward crossing:

- replace the ordinary splash for this member with a larger custom prismatic splash;
- create the rainbow flight ribbon and its event controller;
- begin the cyan/pink screen wash and soft radial glow;
- start the original positional musical sting;
- emit gold, cyan, pink and white stars around the exit;
- begin easing the jump playback rate down from 1×.

The event has no hit stop and never freezes entirely.

### 6.3 Ascent

The fish, tail phase, gold trail, ribbon reveal, droplets, stars, glow, rays and flare all advance by
the celebration clock's scaled delta. Saturation and flare intensity build as playback slows. The
rainbow connects the takeoff point to the portion of the arc the body has already traversed; it does
not draw the unreached descent in advance.

### 6.4 Apex

The minimum playback rate spans a narrow band around the geometric apex:

- the fish is level and remains visibly moving rather than frozen;
- the lens flare reaches peak size and brightness behind the fish;
- two soft ray layers counter-rotate at their slow-time rate;
- several deliberately oversized four-point stars resolve around the silhouette;
- the screen wash reaches its strongest cyan/pink/gold balance;
- the choir chord and bell mark the apex;
- the rainbow is at maximum saturation.

No text is shown in the default design. A caption can be reconsidered after the visual treatment is
playtested; the current feature should first succeed without explaining its joke.

### 6.5 Descent and re-entry

Playback eases back to 1× before the inward crossing. Re-entry produces:

- three expanding rainbow-tinted surface rings;
- a wide cyan-white-and-gold glitter crown;
- custom bubbles carried down along the heading;
- a final low bell or glassy resolve; and
- a short fade of the HUD wash, rays, rainbow and remaining stars.

The existing underwater glide and relocation remain part of the synchronized arc. The event is done
once its owned visuals have expired; the fish need not wait for the HUD fade.

## 7. Isolated slow motion

### 7.1 Two clocks

Each cinematic jump has two notions of time:

- **presentation time** advances one tick per school simulation step and follows real game time;
- **source time** identifies the point sampled on the existing geometric `SurfaceJumpArc`.

For a normal jump, source time advances by one tick for each presentation tick. For a cinematic jump,
source time advances by the current playback rate. The rate is a pure deterministic function of the
current source phase. It is never zero and never runs backward.

`JumpPhase` stores the previous and current source times. On each deterministic simulation step it
advances source time using `StrictMath`, clamps it to the arc end, and includes the time state and
playback profile in its fingerprint. Render interpolation samples between the previous and current
source times. A replay from setup therefore reconstructs the same pose bit for bit.

### 7.2 Initial playback curve

Let `q` be normalized ballistic-flight progress: 0 at outward crossing, 0.5 at the geometric apex and
1 at inward crossing. The first tuning profile is:

| Source phase | Playback rate target |
|---|---:|
| Run-in and exact outward crossing | 1.00× |
| Early ascent | ease from 1.00× |
| `q = 0.38` | reach 0.22× |
| `q = 0.38–0.62` | 0.22× apex band |
| Late descent | ease toward 1.00× |
| Exact inward crossing and glide | 1.00× |

The shoulders use a smooth cubic or quintic easing function so position and perceived speed do not
step abruptly. The profile must be monotonic and symmetric around the apex in the first pass. The
slowest rate, shoulder positions and apex-band width are named tuning constants and are covered by
tests. They are not initially exposed as a player slider.

The target is roughly 3–4 seconds from commit through glide for the common 0.5-block breach, with
about 1.5–2 seconds of visibly airborne motion. Measurements from the implemented curve, rather than
these estimates, set the final values.

### 7.3 Why this belongs in the simulation

A client-only retime would leave the visible fish behind its deterministic engine state, make landing
relocation occur before the body arrived, and allow two observers to see different motion. Applying
the profile inside `FishSchoolSim` makes the longer jump canonical for that event while leaving the
underwater engine and all unrelated systems at normal speed.

The geometric arc remains expressed in source time. Arc fitting, water containment, sky clearance,
entry location and final relocation therefore do not change merely because playback is slower; only
the profile's own arc size (§7.8) moves them.

### 7.4 Duration and goal hold

`durationTicks` currently acts as the physical arc-fit budget and indirectly sizes the goal hold.
Cinematic playback requires those concepts to be separated:

- retain a **source-duration budget** for fitting the geometric arc (initially 64 source ticks);
- derive a bounded **presentation-duration budget** from the selected playback profile;
- hold the nose errand for the presentation budget plus the existing handoff slack; and
- raise or split the current 80-tick bound so a valid slow-motion jump is not rejected while hostile
  wire values remain bounded.

The initial hard ceiling is 160 presentation ticks. Exceeding it is an invalid profile/arc combination
and must fall back before the jump begins, never strand a member in a goal.

### 7.5 Event-owned delta time

Every movable or animated visual created for the celebration advances with:

`eventDelta = currentSourceTime - previousSourceTime`

This includes particle position, particle age, sprite-sheet frame, rotation, scale envelopes, ribbon
reveal, flare pulse, ray rotation, star twinkle and the airborne section of the member's gold trail.
At 0.22×, these elements receive about 0.22 ticks of animation for one world tick. Emission based on
distance carries fractional remainder so slow motion neither starves nor multiplies the trail.

The HUD fade after re-entry may return to real-time delta once the jump source clock has ended. That
keeps the cleanup prompt and prevents a long-lived overlay after the fish is back underwater.

### 7.6 Controlled-particle boundary

Vanilla particles cannot be assigned a per-instance delta time. The Legendary path must therefore
not emit `ParticleTypes.SPLASH` or `ParticleTypes.BUBBLE` for celebration-owned motion. Its splash,
bubbles, stars, glitter and rings use a Fishtastic-owned event-particle collection that accepts
fractional delta time.

Unrelated vanilla particles continue normally. Existing school trail motes created for the jumping
member during the cinematic are tagged with the event and use its delta; motes belonging to other
members, and old motes emitted before the crossing, remain at 1×.

This boundary is intentional. The feature promises to slow everything the jump celebration controls,
not to seize arbitrary particles that happen to overlap it.

### 7.7 Audio timing

Minecraft sound instances do not provide a reliable arbitrary time-stretch that preserves pitch.
The implementation must not pretend the whole audio waveform shares fractional delta time. Instead,
the original sting is authored for the selected canonical playback curve:

- a positional takeoff/harp layer starts at the outward crossing;
- an apex choir/bell layer starts when source time crosses the apex;
- a re-entry resolve starts at the inward crossing; and
- an optional quiet looping shimmer can bridge the variable apex band.

Phase-triggered layers keep audio synchronized if tuning changes modestly. Live player-adjustable
slowdown is deferred because it would require a different audio strategy and would break canonical
school playback.

The current implementation uses short positional vanilla note-block, amethyst-chime and bell layers
at exit, apex and re-entry. They use the ambient sound category, are played only for the nearest
visible foreground event, and are quieter with reduced effects enabled. These cues are a temporary
resource-safe fallback; an authored original sound family remains a polish goal.


### 7.8 A bigger, longer arc

Owner request 2026-10-29: the legendary leap should also be physically bigger than a school's usual
jump, not only slower. `SurfaceJumpPlaybackProfile` carries the arc size alongside the rate curve:

| Profile | Breach height the server asks for | Reach scale | Launch pitch | Air reach at that apex |
|---|---|---|---|---|
| `NORMAL` | 0.5 blocks | 1.0 | 60° | about 1.2 blocks |
| `LEGENDARY_CINEMATIC` | 1.25 blocks | 1.5 | about 49° | about 4.3 blocks |

- The reach scale is applied inside `FishSchoolSim`'s arc fitter, so it is part of the stamped,
  checksummed input and every copy fits the same arc. The fitter's reach, run-in and apex fallbacks
  still apply (multiplied by the scale), so a small pond gets a smaller legendary arc rather than none.
- The breach height travels in the goal's existing `height` field; the sim still raises it for a long
  fish and caps it at `MAX_SURFACE_JUMP_HEIGHT` (2 blocks).
- The server's sky-clearance check sizes its sweep from the same height and reach scale. Where the big
  arc is blocked it retries the ordinary 0.5-block height at the same launch point, still cinematic.
- A legendary goal gets the 80-source-tick fitting budget (`MAX_SURFACE_JUMP_TICKS`); the 1.25-block
  arc takes about 60 with the longest run-in. Slowed playback stays well inside the 160-tick
  presentation ceiling.

## 8. Visual direction

### 8.1 Palette

The palette deliberately exceeds the normal Legendary gold vocabulary:

| Role | Initial colors |
|---|---|
| Water light | cyan-white, pale azure |
| Rainbow | saturated red through violet, biased toward cyan and pink on screen |
| Flare | white core, pink halo, warm gold secondary ring |
| Stars and glitter | white, gold, cyan, hot pink |
| Screen wash | cyan shadows, pink highlights, restrained gold center |

The gold catchability trail remains gold. It still communicates quality, while the rainbow and cyan
water effects communicate the one-off breach celebration.

### 8.2 World-space layers

- **Prismatic exit:** a body-scaled sheet and crown built from custom event particles.
- **Rainbow ribbon:** a camera-independent curved strip following sampled arc positions, softly
  emissive and translucent, with width scaled by fish body length.
- **Fish halo:** a soft billboard glow behind the body, occluded with the world.
- **Apex flare:** layered billboards along the camera-to-fish axis; bright but not a full white frame.
- **Orbiting stars:** a small number of large readable shapes plus finer glitter.
- **Re-entry rings:** flat surface geometry or controlled sprites, never ordinary quality-tinted cue
  rings.
- **Glitter crown and bubbles:** custom particles with event delta and body-length scaling.

The rainbow and flare must not make an unavailable or caught fish appear to remain in the water. They
are presentation residue only and fade promptly if the member leaves.

### 8.3 Screen-space layers

- a low-alpha cyan/pink color wash;
- a soft vignette that brightens rather than darkens the center;
- two counter-rotating ray textures derived from or shared with the existing catch celebration;
- several screen-space star glints positioned around the projected fish; and
- a short, sub-white apex bloom.

The fish remains a world object. The HUD does not draw a duplicate fish, cut away from the player's
view or fake a different camera angle. Existing celebration glow and ray assets may be reused where
they fit, but the feature needs its own timing controller and must not instantiate
`CatchCelebration`, whose semantics and lifecycle belong to landed rewards.

### 8.4 No camera shake by default

Option B aims for blissful excess rather than impact violence. It uses light, saturation and layered
motion, not camera shake. A tiny HUD-only shimmer can be considered in playtest, but world camera
orientation is never modified.

## 9. Audio direction

Create a distinct `legendary_breach` sound family rather than reuse `catch_legendary`. Reusing the
catch sound would imply the player received the fish.

The original 2–4 second cue should combine:

- an ascending harp or glass arpeggio at takeoff;
- airy synth shimmer through the slowdown;
- a jubilant, nonverbal choir chord and bright bell at the apex; and
- a soft descending glass/bell resolution at re-entry.

The sound is spatially anchored to the breach, with the apex layer allowed slightly more presence so
the audiovisual beat reads. It uses an appropriate existing sound category and respects Minecraft's
category volume. No dialogue or caption is required.

Only the nearest visible foreground celebration plays the full cue when simultaneous schools breach.
Other qualifying jumps may retain quiet positional water/glitter sounds. A replay, renderer rebuild or
late join never replays a historical takeoff sound.

## 10. Rendering and simulation architecture

### 10.1 Simulation changes

Introduce a bounded `SurfaceJumpPlayback` value carried by `SurfaceGoal` and debug `SurfaceJump`.
`FishSchoolManager.startSurfaceGoal` chooses it from the server-owned member quality. `Goal` and
`JumpPhase` retain it. The checksum covers the profile and accumulated source-time state.

Refactor `JumpPhase` from `arc.sample(step - startStep)` to a deterministic advancing source clock.
Expose enough phase data for presentation without exposing mutable internals, for example:

- profile;
- previous and current source time;
- source-time exit, apex and entry thresholds;
- interpolated arc pose;
- outward/inward crossing events; and
- event delta for the current render/tick interval.

Normal playback must remain equivalent to current poses, crossings and duration. The acceptance target
is that `NORMAL` advances source time by exactly 1.0 and preserves existing behavior after the
intentional fingerprint schema update.

### 10.2 Client event controller

Add a client-only `LegendaryBreachPresentation` owned alongside each tracked school copy. It is keyed
by school epoch plus member UUID so replay or UUID reuse cannot alias an old event. It owns:

- controlled particles and their deterministic visual seed;
- ribbon and world-space light state;
- screen-treatment envelopes;
- phase-trigger latches for sound;
- previous/current source times; and
- cleanup/fade state after entry or interruption.

`FishSchoolClientRuntime` starts it only when a `LEGENDARY_CINEMATIC` outward crossing is observed in
live advancement. Its world submission follows the existing school render path. A small common HUD
renderer exposes the screen layers; Fabric, NeoForge and Forge register it beside the existing HUD
hooks.

Random visual variation is seeded from school ID, epoch, member ID and jump start step. It is locally
deterministic for stable captures but does not enter the authoritative simulation checksum.

### 10.3 Fish rendering

`GroupFishDraw` continues to draw the real member using `surfaceJumpPose`. No duplicate model is
introduced. The existing wet texture remains appropriate. Tail phase comes from the retimed arc and
therefore slows with the body.

Lighting for the fish should include the celebration's local emissive/glow treatment without making
the model full-bright through solid blocks. If modifying the fish render pipeline is too invasive for
the first pass, the halo may provide the brightness while the model retains world lighting; this is a
playtest decision, not permission to render through occluders.

### 10.4 Assets

Expected new or adapted assets:

- rainbow ribbon texture or procedural gradient;
- layered lens-flare sprites;
- large four-point star and fine glitter sheets;
- optional prismatic ring texture;
- any new HUD wash texture if a procedural fill is insufficient; and
- original takeoff, apex and re-entry sound assets.

Editable source art and deterministic generators belong under the appropriate `tools/` workflow;
generated previews and captures belong under ignored `build/` evidence.

## 11. Accessibility and settings

The existing `reducedCelebrationEffects` preference governs the first implementation so users have
one predictable celebration accessibility control.

### Full presentation

- synchronized isolated slowdown;
- rainbow, controlled particles and gold trail;
- screen wash, bloom, rays, flare and animated stars;
- layered sound cue.

### Reduced presentation

- the same synchronized fish motion, because playback is authoritative and cannot differ per client;
- a still or slowly revealed low-alpha rainbow;
- a modest gold/cyan world glow and re-entry ring;
- no screen flash, color wash, spinning rays, dense glitter or screen-space stars;
- a quieter simple takeoff/apex/re-entry chime, subject to ordinary sound settings.

### Hidden schools

When `fishSchoolsVisible=false`, no fish, world celebration, HUD treatment or celebration audio is
presented locally. The server simulation and its canonical playback continue unchanged.

Minecraft particle status also reduces the count of optional controlled particles. The fish, rainbow
silhouette and essential re-entry ring remain readable without relying on a dense particle field.

If playtest shows users want the meme treatment independently of catch celebrations, add a dedicated
`legendaryBreachEffects` setting with `FULL`, `REDUCED` and `OFF`. Do not add it preemptively unless the
shared preference proves inadequate.

## 12. Multiplayer and replay behavior

- Every tracking client receives the stamped playback profile and reconstructs the same retimed fish
  pose, crossings, landing relocation and completion step.
- Presentation particle placement need not be network-identical, but its phase and broad timing must
  agree because they derive from the shared source clock.
- A client that begins tracking before exit can play the event normally.
- A client that catches up or joins after exit initializes the current rainbow/halo state without
  retroactively firing the takeoff splash, flash or sound.
- A client that joins after entry sees no event unless a short real-time cleanup fade is still live;
  the default is to omit historical residue.
- Replica rebuild during flight reconstructs the current pose and non-burst visual state, clears
  one-shot latches, and does not replay sounds or particle bursts.
- Epoch replacement drops the old event and its screen treatment immediately or through a maximum
  four-tick visual fade.
- Multiple schools can own world-space events simultaneously. Each client chooses at most one
  visible event as its foreground HUD/audio celebration, preferring the nearest; it does not merge
  clocks or restart the foreground event every frame.

## 13. Performance budgets

Initial per-visible-event targets:

| Resource | Full target | Reduced target |
|---|---:|---:|
| Controlled particles alive | 160 maximum | 32 maximum |
| Large world star/flare billboards | 16 maximum | 4 maximum |
| Rainbow ribbon samples | 48 maximum | 24 maximum |
| HUD textured layers | 8 maximum | 2 maximum |
| Foreground sound layers | 3 one-shots + optional shimmer | 3 quiet one-shots |

The controller uses fixed caps and oldest/least-important eviction. Slow motion stretches particle
wall-clock lifetime, so budgets are based on alive count, not normal-speed emission assumptions.
Ribbon geometry is sampled once from the arc and progressively revealed; it is not rebuilt at high
resolution every frame.

No new per-frame network traffic is allowed. The profile travels once in the existing stamped input.

## 14. Failure and interruption behavior

| Situation | Required behavior |
|---|---|
| Goal aborts before outward crossing | No celebration is created. |
| Member starts a bobber approach during ascent | Existing jump cancellation wins; event-owned motion folds/fades with the fish and clears within four ticks. |
| Member is caught or leaves during the event | World and HUD effects stop promptly; no orphan rainbow or sound restart. |
| Water or clearance changes before commit | Existing fit/goal failure remains silent. |
| Water changes after commit | Existing synchronized cancellation/landing safety remains authoritative; effects follow its result. |
| Client hides schools mid-event | Visuals and event audio stop immediately; no server state changes. |
| Reduced effects changes mid-event | Apply the safer setting immediately where practical; never restart the event. |
| More than one eligible event overlaps | All may retain bounded world effects; only the chosen foreground event owns HUD and full audio. |
| Renderer or resource reload occurs | Drop GPU state safely; reconstruct non-burst state only if the school replica is still current. |
| Profile would exceed duration ceiling | Reject/fall back before launch; do not leave the goal active indefinitely. |

## 15. Verification

### 15.1 Unit tests

Extend `SurfaceJumpArcTest`, `FishSchoolSimTest`, `FishSchoolJumpReplayTest` and wire tests to prove:

- `NORMAL` retains current arc geometry, duration, crossings, pose and handoff behavior;
- cinematic source time is strictly increasing and bounded;
- rate begins and ends at 1×, reaches the configured minimum near the apex, and has no discontinuity;
- for a given arc, the geometric apex, exit, entry, landing point and end speed are unchanged by
  playback, and the legendary arc peaks higher and lands farther than a normal one (§7.8);
- cinematic presentation duration stays within its hard ceiling;
- goal hold covers the full presentation duration plus handoff slack;
- cancellation cannot advance or refire a crossing;
- profile and source-clock state participate in the checksum;
- a replica joining before, during and after the apex remains in lockstep;
- wire roundtrip preserves both playback profiles and rejects invalid values; and
- normal and cinematic direct debug jumps can be forced independently.

### 15.2 Client render self-test

Extend `fishschooljump` or add a focused `legendarybreach` scene that spawns a forced Legendary member
and captures:

1. ordinary swim-up with gold trail;
2. outward prismatic splash;
3. early slowdown;
4. pre-apex, apex and post-apex frames demonstrating variable displacement;
5. peak flare, rainbow, rays and stars;
6. re-entry rings and glitter;
7. underwater handoff and cleared HUD;
8. reduced-effects rendering;
9. hidden-schools suppression;
10. a mid-flight replica rebuild with no repeated burst; and
11. a forced cancellation with prompt cleanup.

The scene records source time, presentation step, playback rate, active controlled-particle count,
foreground-event identity and replica mismatch count. Assertions must establish the slowdown from
measured per-frame source delta, not only from screenshots.

### 15.3 Manual playtest

- Observe from shore, underwater, above the fish and at the edge of the 32-block range.
- Verify that the isolated slowdown reads clearly while waves, unrelated particles and other fish
  remain at normal speed.
- Check daylight, night, rain, common shader settings and each Minecraft graphics mode.
- Confirm the flare never renders through terrain and the wash does not hide fishing UI.
- Test full and reduced effects for flash, motion and color sensitivity.
- Test two players observing the same jump and two nearby schools jumping at once.
- Decide whether the 0.22× apex rate feels blissful, frozen or merely laggy.
- Confirm the audio reads as witnessing a fish, not receiving a catch reward.

## 16. Rollout

### Phase A — deterministic playback

Add the playback profile to the goal/wire path, separate source and presentation durations, implement
the source clock and prove normal/cinematic replay and landing parity. Use debug markers only.

### Phase B — world celebration

Add the controlled-particle system, rainbow ribbon, flare/halo, prismatic crossings and retimed gold
trail. Validate visibility, caps, interruption and reduced effects.

### Phase C — screen and audio polish

Add HUD wash/rays/stars, original layered audio, foreground-event arbitration and final tuning. Run
cross-loader render scenes and multiplayer playtest before enabling natural Legendary jumps.

Each phase should remain independently testable. Do not couple lockstep correctness to unfinished art.

## 17. Deferred ideas

The maximal “Blessed Broadcast” treatment remains deliberately available for a later pass:

- background dolphin silhouettes;
- animated corner clouds;
- tiny hot-air balloons;
- ornamental full-screen frame;
- bouncing `LEGENDARY FISH MOMENT` title;
- denser confetti, hearts and shooting stars;
- additional competing lens flares; and
- a longer musical cue with an unnecessary triumphant key change.

These are additions to the same event controller, not requirements for Option B. They should be
evaluated only after the fish, slow-motion clock, rainbow and core audiovisual beat are satisfying.

## 18. Open tuning decisions

1. Is 0.22× the right minimum, or should the apex reach 0.15× or remain closer to 0.3×?
2. How wide should the minimum-rate band be before it reads as a pause rather than slow motion?
3. Should slowdown begin exactly at surface exit or slightly during the final run-in?
4. Should the rainbow persist through the underwater glide or end at the re-entry rings?
5. How strong can the screen wash be without compromising water and fishing-UI readability?
6. Does the fish model need temporary celebration lighting, or is a world-space halo sufficient?
7. Should the shared reduced-celebration setting remain the permanent control or be split later?
8. Should the foreground range remain 32 blocks after positional-audio playtesting?

## 19. Implementation snapshot (2026-10-28)

The synchronized profile and the first procedural visual pass are implemented. The earlier sections
remain the product direction and acceptance contract; details below record what this checkout does
today and which review steps remain.

### Implemented

- Server-owned Legendary catch quality stamps `LEGENDARY_CINEMATIC` into the existing surface-goal
  input. The profile is serialized, validated, replayed and included in simulation checksums.
- `NORMAL` uses the prior one-source-tick-per-step timing, including the original first-frame
  interpolation. Cinematic source time eases to `0.22x` by normalized flight phase `0.38`, holds
  through `0.62`, then eases back to `1x`; the presentation duration is capped at 160 ticks.
- Exit and entry crossings remain deterministic simulation events. Their short client-observation
  history is pruned every step, including when no jump is active.
- Each client school copy can own bounded, seeded Legendary presentation state. The world pass draws
  a gradually revealed rainbow bead ribbon, apex orbit stars, controlled exit/entry bursts and three
  re-entry rings. Airborne gold trail motes belonging to the jump use the fractional source delta.
- Fabric, NeoForge and Forge register the shared cyan/pink wash, rays and projected star glints.
  Reduced effects retain the synchronized fish motion and small world treatment while dropping the
  wash/rays and limiting particles.
- A pink/gold radial apex flare is drawn from the existing emissive sprite sheets.
- Rays and stars art pass (owner request 2026-10-29): `tools/legendary-breach-gen/gen.py` generates
  three soft white-in-alpha textures (`legendary_sunburst`, `legendary_sparkle` four-point,
  `legendary_sparkle8` eight-point hero), blur on. The HUD rays are two counter-turning sunburst
  layers (cyan, pink) drawn through `GUI_TEXTURED_GLOW` (vanilla GUI textured with source-alpha/one
  blending), replacing the solid 2px bars; the rays taper, start clear of the fish and fade before
  the screen edge. World sparkles use `WORLD_SPARKLE_GLOW` (emissive translucent entity, same glow
  blend, no depth write, Iris-assigned), each a tinted star with a near-white core. The apex keeps an
  eight-point hero star on the fish; the fixed 12-star orbit is replaced by a seeded field of 13 stars
  (3 eight-point heroes) that each pop in, swell, flare and shrink on their own source-time cycle and
  are reborn somewhere new, so they twinkle in slow motion near the apex. Takeoff burst glints and the
  HUD plus signs became the same twinkling sparkles. The rainbow ribbon still uses the glint beads.
- The rainbow ribbon no longer vanishes at re-entry: its beads are cached each frame, completed to the
  entry point on landing, then dissolve over 24 source ticks (well inside the 36-tick glide plus
  cleanup), the fade sweeping from the takeoff end to the landing with each bead shrinking and
  drifting up. A cancelled flight fades the whole ribbon with the 4-tick cleanup instead.
- Event particles and re-entry rings draw their age lerped through the last tick
  (`age - delta * (1 - partialTick)`), as their positions already were. Sizes, twinkle, spin and ring
  radii had stepped at 20 Hz, which read as a stutter on the takeoff sparkles rising with the fish.
  Measured first: the apex star field's anchor tracks the drawn fish exactly every frame, so it was
  not the cause.
- Positional note-block chime, amethyst chime and bell layers trigger at the outward crossing, apex
  and re-entry. Only the nearest visible foreground event plays them; reduced effects lowers their
  volume. No catch-reward cue is reused.
- The `legendarybreach` render self-test scene spawns an all-Legendary school, calls the natural
  quality-based debug jump path, captures the leap with the HUD enabled, and checks that the
  controller and custom particles appeared without lockstep mismatches.

### Original audio and review remain open

No feature-specific sound event or audio resource is registered. The repository currently has no
authored, legally clean takeoff/apex/re-entry source recording or supported original-audio authoring
workflow for this feature. The remaining media work is to author or source an original licensed cue,
provide its editable source and generated OGG files, then replace the vanilla event layers while
keeping the existing phase triggers and foreground arbitration.

No in-game capture or manual cross-loader/multiplayer review has been completed yet. The new scene
is available for that review; screenshots should confirm the HUD stays legible, the ribbon and apex
flare are visible in water, the slower arc reads as intentional motion, positional cues land at the
expected phases, and cancellation/late-join cleanup is prompt.
The implementation currently uses the existing school sparkle/ripple sprite sheets and procedural
geometry rather than adding image assets. Exact flare balance remains tuning work.
