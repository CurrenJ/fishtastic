# Interactive fish schools: motion on the shared fish sim

Plan and progress log for moving interactive school motion onto the one fishsim `FlockEngine`
that ambient fish and tanks already use. The gameplay contract stays in
[interactive-fish-schools-design-spec.md](interactive-fish-schools-design-spec.md); this document
owns *how school fish move* and the work to get there. Keep the status table current.

## 1. Why

The first implementation moved school fish with its own math and it read as weird and disjointed:

- the server moved free members only every 5 ticks, by grid-neighbour waypoints (90°/180° turns,
  near-vertical moves spinning the yaw);
- it sent snapshots every 10 ticks, and the client closed 28% of the gap per tick, so each fish
  surged and stalled twice a second;
- the approach to a bobber was a separate fixed-speed dart (~20× free speed);
- sparkles spawned from per-tick displacement, so they pulsed with the surge.

**Owner requirement:** no additional fish movement math or simulation for schools. Reuse the engine.

## 2. Owner decisions

| # | Decision | Date |
|---|----------|------|
| M1 | Server-authoritative `FlockEngine` per school; clients run an identical copy in lockstep (no streamed positions, no client easing/leash). | 2026-10-24 |
| M2 | No watcher/startle for school engines: each client has a different nearby player, so it would diverge. | 2026-10-24 |
| M3 | Departure fades out over the departure window (60 ticks) instead of a new swim-away behaviour. | 2026-10-24 |
| M4 | Accept ~0.2 s client bite lag from running the copy a few ticks behind the server. | 2026-10-24 |
| M5 | Interest radius 8 → 5, plus a host-errand approach speed multiplier (`schools.approachSpeedScale`, default 2.0); `approachMaxTicks` 240 → 400. | 2026-10-24 |
| M6 | Tank fish differing between multiplayer clients is fine and intended — no action. | 2026-10-24 |
| M7 | Bites were unreliable in the owner's playtest at radius 5: interest radius back to 8, approach speed ×2.5, `approachMaxTicks` 600 (an 8-block approach takes ~17-18 s). Supersedes M5's values. | 2026-10-25 |
| M8 | The sparkle trail stays on until the fish is caught (hooked out of the water) or leaves with its departing visit; it no longer stops while the fish approaches, bites or recovers. | 2026-10-25 |
| M9 | The sparkle trail's colour shows the fish's quality. Each member's quality is fixed when the school forms (derived from its reward seed, so older saves get qualities too), and catching it gives exactly that quality. Bait and hook quality bias no longer apply to school fish, so the trail always matches the catch; `forcequality` still overrides. | 2026-10-25 |

## 3. Design

- **One shared class**, `common/.../school/FishSchoolSim.java`, runs on the server and on every
  client copy: a `WaterDomain` built from the visit's water, a `FlockEngine(Tunables.GROUP)`, the
  member↔engine-index mapping and five inputs — `Leave`, `Rejoin`, `Approach`, `Withdraw`, `Water`.
  Built from a `Setup` (origin, columns, species `FishSpec`, seed, placements). All values are
  floats in the school's own frame (blocks from its anchor), so both sides apply identical numbers.
- **Determinism:** the engine is a pure function of setup + inputs at step counts
  (`SchoolLockstepTest`). Its trig calls are `StrictMath`, so results are bit-identical across
  platforms (an ulp of difference grows to 10 cm in ~4 s otherwise). Goldens were unchanged.
- **Bobber approach** is the engine's existing nose-trigger errand: `Shelter.noseAnchor` +
  `Trigger.hostSent` + `FlockEngine.sendToTrigger(s, fish, speedScale)`. The bite is the engine's
  touch event. The engine never times a host errand out; the server gives up by withdrawing.
- **Server** (`FishSchoolManager`): one sim per school per *epoch*. An epoch starts when the school
  is created or first ticked after a load, from saved positions; it is not persisted. The engine
  steps exactly once per game tick however often the manager is ticked. Every input is recorded,
  stamped with the sim step it was applied before (`StampedInput`), for client replay.
- **Client** (step 3): a copy per tracked school, built from a setup packet plus every input so
  far; replays to the server's step under a per-tick budget, then steps once per tick a few ticks
  behind the server. Drawn through the same per-fish path as ambient fish.

## 4. Status

| Step | What | Status |
|------|------|--------|
| 0 | Lockstep feasibility spike (`fishsim/.../SchoolLockstepTest`): bit-identical copies over a 12,000-step visit, ~22 ms full replay; `StrictMath` in `FlockEngine`. | Done 2026-10-24 |
| 1 | fishsim additions: `WaterDomain.rebuildShelters`, `Shelter.noseAnchor`, `Trigger.hostSent`, `FlockEngine.sendToTrigger` (+ speed scale); host errands survive shelter-list changes; `triggerFish` renumbered on rebuild (`HostTriggerTest`). | Done 2026-10-24 |
| 2 | Server on the engine: `FishSchoolSim`, manager rewrite (old wander/approach/departure movement deleted), saved-data waypoint fields removed, config M5, `/fishtastic schools spawn` and `debug` (op), `FishSchoolSimTest`, GameTests incl. `schoolStaysInItsWaterAndStepsOncePerTick`. | Done 2026-10-24 |
| 3 | Network + client copy: `FishSchoolSetupPacket` / `FishSchoolInputPacket` / `FishSchoolStatePacket` (old `FishSchoolSyncPacket` deleted), `FishSchoolWire` codecs, `FishSchoolReplica` (delay, budgeted replay, late-input and fingerprint replays), `GroupFishDraw` shared with ambient, sparkles from engine motion, client easing deleted; `/fishtasticambient schools debug`; `/fishtastic schools spawn [species]`. Tests: `FishSchoolReplicaTest` (mid-visit join, late input, client stall, wire round-trip). | Done 2026-10-24 |
| 4a | `FishSchoolScenes` self-test spawns a real school on the integrated server (its scripted sine motion is gone) and checks the copy: on Fabric, caught up at once, 421 steps with 0 replays and 0 fingerprint mismatches, 72 frames with the school rendered. A follow-camera close-up (60 frames, 2 ticks apart) shows the sparkle trail; frames land in `fabric/run/screenshots/selftest-fabric-fishschoolgif-closeup-*.png`. | Done 2026-10-24 |
| 4b | Owner's in-game look (solo): motion, approach pace at ×2, bite, sparkle cue, departure fade. | **Waiting on owner** |
| 4d | Bite-approach polish from the owner's look (2026-10-25, §5 "Bite approach probe"): hold faces the anchor, anchor measured from the surface, nose-in quickened. Uncommitted; going above ×2.5 is the owner's call. | **Built, owner look pending** |
| 4e | Quality trail tint (M9): `FishSchoolSavedData.qualityOf`, qualities in `FishSchoolSetupPacket`, per-quality mote palettes in `FishSchoolClientRuntime` (Common keeps the sea-glass cyans), and the bite burst drawn in the biting fish's palette. To look at them: `/fishtastic schools spawn <species> <quality\|mixed>`, or the `fishschoolgif` scene's `quality=` parameter. Tests: `FishSchoolQualityTest`; 389/389 Fabric GameTests. Uncommitted. | **Built, owner look pending** |
| 4f | Sprint bubbles: while a fish is on its bobber errand (engine `SHELTER_APPROACH`/`SHELTER_ENTER`), it sheds the tanks' bubbles in their exertion size mix (`TankBubbleEmitter.exertionBubble`) at random points from nose to tail. About one per 0.09 blocks swum; each bubble keeps 30% of the fish's motion and pops at the water surface. Client only (`FishSchoolClientRuntime.sprintBubbles`). The first version used vanilla bubbles, which the owner found too big. Seen in the `fishschoolbite` scene. Uncommitted. | **Built, owner look pending** |
| 4c | Two-client check (real multiplayer copies) and NeoForge/Forge runs of the scene. | Not started |
| — | Ports (1.21.1, 1.20.1). `FishSchoolSim` uses Java 21 pattern switches: rewrite for 1.20.1 (Java 17). | Not started |

### Step 3 plan (done; kept for reference)

1. **Shared draw path.** Extract ambient's per-fish draw (cull, `GroupFishPose`, model resolve,
   fade, light, submit) and its back-to-front sort into a client helper; ambient and schools both
   call it.
2. **Packets** (server → client):
   - *setup*: school id, epoch, species, the `Setup`, every `StampedInput` so far, the server step;
     sent when a player starts tracking a school or an epoch begins;
   - *inputs*: new `StampedInput`s, sent as soon as they are applied (not batched);
   - *state* (every 10 ticks, replacing `FishSchoolSyncPacket`): per tracked school the server
     step, each member's visual state (cue), the departure deadline, and a position checksum at
     that step; schools no longer listed are dropped.
3. **Client copy** (`FishSchoolClientRuntime` rewrite): per school a `FishSchoolSim`; target step =
   last server step + ticks since − a small delay; apply inputs when the copy reaches their step;
   an input for a step already passed, or a checksum mismatch, triggers a replay from setup (logged
   once). Replay runs under a per-tick step budget and the school is hidden until it has caught up.
4. **Visuals:** cue and sparkles from the engine's per-tick motion; departure fade over the
   window; tail beat from engine speed (no constant-speed animator call).
5. **Tests:** client-copy unit test (late join, late input, checksum match), packet round-trip
   codec tests, existing GameTests green.

## 5. Findings worth keeping

- Fingerprints must be taken at one fixed point on both sides: right after a step, before that
  step's inputs. Inputs can reach the server outside its tick (a player reeling in), so a
  fingerprint taken at sync time would include inputs the copy has not applied yet.
- A refused approach is still logged as an input (both sides must run it); the server waits 40
  ticks before offering that hook a fish again, and an epoch whose log nears the packet bound is
  retired for a fresh one, so a client's replay stays bounded.

- The engine's visit approach pace is ~0.19 blocks/s; a host errand at ×2 measured 0.33–0.38.
- A floating bobber sits above the head-room fish keep under the surface; anchors must be placed
  where the whole anchor box is in the water (`FishSchoolSim.anchorPoint`), or the engine refuses
  every send. Found by the live bite GameTest via `debugState`.
- The live bite GameTest must accept whichever member bites: which fish goes is the swim's call.
- Sparkle trail (owner feedback 2026-10-24: "very short dense trail"): one mote per 0.12 blocks
  swum, living 3.5-5 s with slower drift; the old 56-mote cap per school had been deleting motes
  long before their lifetime, so it is now 128 and never reached in practice
  (`FishSchoolClientRuntime.MOTE_SPACING`, `MOTE_LIFETIME`). Awaiting the owner's look.
- **Bites were cancelled by the bobber bobbing** (found by the `fishschoolbite` self-test,
  2026-10-25): the server judged the bobber's water at the block holding its centre, which is the
  air above the surface about half the time, so approaches restarted every 2-9 ticks with
  "bobber out of the water". It is now judged half a block under the bobber
  (`FishSchoolManager.hookWaterCell`); after the fix the only restarts are the full
  approach / bite-window / recovery cycle (~155 ticks). Likely cause of the owner's unreliable bites.
- **Bobbers beside a fish got no bite** (owner report 2026-10-25: "bobber 1-3 blocks away, it wanders
  off over 30 seconds"). A school's water was a 3D flood fill from its anchor capped at 256 cells.
  In deep water that is a blob ~4 blocks across. The fish swim inside it, but a bobber cast just
  past its edge is "not in any active school's water" and is never offered to them. Measured by
  the `fishschoolreach` self-test (25x25 pool, 6 deep, still bobbers):
  - Before: bobber cells outside the school's water were 2/32 at 1 block, 9/32 at 2 blocks and
    13/32 at 3 blocks; 7/8 trials bit, and the one miss was the outside bobber.
  - Fix: the cap is now 400 columns, each to full depth (4400-cell safety bound), about 11 blocks out.
  - After: 0/24 outside at every distance; 8/8 trials bit, in 5.8-12 s.

  Still unmeasured: real casts (a flying, settling bobber) rather than placed ones, and lakes
  wider than the region, which still has an edge ~11 blocks out.
  `/fishtastic schools debug log true` (`FishSchoolManager.logApproaches`) logs every approach start, bite and failure with its reason.
- `/fishtastic schools debug` reports the caller's own bobber ("Your bobber: ..."), computed by
  `hookChoice`, the same function the reservation uses.
- A spectator cannot hold a line (`FishingHook.shouldStopFishing`), so the bite scene casts as a
  hovering creative player.
- **Sparkles sat off the drawn body vertically** (verified with `markers=true`: blue = engine
  centre, green = drawn centre): the renderer's bob (`FishAnimator` yBob) moves the drawn fish
  about half a body height from the engine position. Motes are now shifted by each tick's
  drawn-minus-engine offset. Spacing and direction still come from the engine's path.
- **Bite burst** (owner request, 2026-10-25): on the client copy's own touch event, a flash at the
  nose, a ring of 20 motes spreading out and a column of 14 rising past the bobber; never replayed
  for a copy catching up (`FishSchoolReplica.tick(events)`). Self-test `fishschoolbite` films it
  (`view=above` films from a fishing player's height: trail, ring and column read through the surface).
  **Reworked into a fountain** (owner request, 2026-10-25): after the nose flash, a burst from the
  water surface above the bite (`waterSurfaceAbove`). There are 26 droplets, 6 of them tall central
  jets, under 3x vanilla gravity. Each droplet that falls back below the surface sinks and fades
  over 8 ticks. As it goes under it leaves a flat ripple ring on the water, two tiny kicked-up
  droplets and vanilla `SPLASH` particles (`splash`). Droplets landing over non-water just fade.
  The old ring and column are gone. Awaiting the owner's look.
- **Mote sprites and tints** (owner request, 2026-10-25): six 8-frame sheets (glint, pearl bubble,
  sea glass, star twinkle, plankton orb, ripple ring), all symmetric (the generator script was
  removed; the PNGs are now the source). They are greyscale so the vertex colour sets the hue: each mote picks
  a random sheet and a tint from `DEFAULT_MOTE_TINTS` (the old cyans). The bite flash is always the
  glint. `/fishtasticambient schools tint <RRGGBB...>|reset` overrides the tints locally (dev), and
  the `fishschoolgif` close-up takes `tint=...` (both override the quality tints, M9) and `quality=...`.
- Player tracking is tied to the `ServerPlayer` instance, not the UUID: a relog or respawn between
  two syncs would otherwise leave a client with no copy that the server thinks it has.

### Bite approach probe (2026-10-25)

The owner reported bites that sometimes looked wrong: a lazy approach, a fish facing away from the
bobber when it got there, a bite 1-2 blocks under the surface. A throwaway headless probe ran 40
approaches (bluegill, 3-deep 13 × 13 pond, nearest member sent, ×2.5) and logged speed, heading
against the bearing to the anchor, and depth every tick.

- **Facing away: a real bug, 40/40.** Each fish touched the anchor facing it, then turned a full
  180° about 1.4 s into the hold. Holding the anchor creeps back and forth along the axis at
  ~0.02 blocks/s, which is above `PLANAR_YAW_MIN_SPEED`, so the yaw followed the backward creep.
  Fix: `FlockEngine.facesIn`. A fish in `SHELTER_INSIDE` at a `TRIGGER` faces along the mouth
  normal, the same way `facesOut` turns resting lurkers (this also covers tank clam anchors).
  After the fix 0/40 turned away and the hold stayed within 1°. Parity goldens unchanged.
- **Deep bites: the anchor was set while the bobber was still sinking.** A reservation is made on
  the first tick the bobber's cell is water. Vanilla scales velocity by (0.3, 0.2, 0.3) when the
  bobber enters water, then pulls it back up only weakly (`vy -= d·rand·0.2`, drag 0.92). A
  model of those constants gives a 0.25-1.0 block sink for a hard cast. The anchor stayed at that
  depth because the drift check is horizontal only. Not measured live. Fix:
  `FishSchoolSim.anchorPoint` starts its search from the column surface (`WaterDomain.surfaceAt`)
  when the bobber is under it. In the probe a bobber 0.8 below the surface now anchors where a
  floating one does.
- **Lazy approach: pace, not a bug.** Peak speed 0.27-0.55 blocks/s at ×2.5 (the engine is
  tank-scale: GROUP patrol 0.07, maxSpeed 0.20), and about 1.5 s of creeping at base
  `ENTER_SPEED` at the end, because only the swim to the staging point was quickened. Fix: the
  host errand's scale now covers the line-up and nose-in too (axis speed and `noseSteering`, cap
  raised in `SHELTER_ENTER`). Nose-in went from 30 to 15 ticks at ×2 (mean touch 174 → 160
  ticks). Probe at ×3: mean touch 114 ticks, peak 0.50, 3/40 overshoot the anchor by a few cm.
  At ×4: 97 ticks, 14/40 overshoot. Going past ×2.5 is the owner's call.
- During the swim the fish faced its bearing to within a few degrees, except when it started
  within about 1.5 blocks and had to turn around.

## 6. Verification commands

```
./gradlew :fishsim:test --tests "grill24.fishsim.SchoolLockstepTest" --tests "grill24.fishsim.HostTriggerTest"
./gradlew :common:test --tests "grill24.fishtastic.school.FishSchoolSimTest"
./gradlew :fabric:runGametest
```

In game (op): `/fishtastic schools spawn [species]` near water, cast nearby, `/fishtastic schools debug`
(server state), `/fishtasticambient schools debug` (this client's copies: step vs target, replays,
fingerprint mismatches).
