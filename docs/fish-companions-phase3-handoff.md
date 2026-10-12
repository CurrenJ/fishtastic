We're continuing the fish companion rewrite in D:\GitHub\fishtastic (branch 26.1.2). Start Phase 3: putting the sim into the game.

Read first:
- docs/fish-companions-sim-plan.md:
  - §3.2 CompanionWorld, the input interface
  - §3.5 the adapter (the only Minecraft-aware code)
  - §3.6 multiplayer
  - §5 Phase 3 scope
  - §6 keep / rewrite / delete table
  - §10 Phase 2 as built, including the owner's gate answers in §10.6
- docs/fish-companions.md §3 (display modes), §3.1 (held-item reactions), §4 (personality), §6 (mobs and cats), §7 (player interactions) and §8 (equipping and sync). The motion approach and the fishing reactions (§5) are superseded or deferred.
- Memory: project_companion_sim_rewrite, project_glide_needs_refactor, project_fish_companions, and the companion feedback memories (calm baseline, always swims, faces velocity, speed variety, validate before claiming, metrics as proxies, solo playtest commands, runClient needs a fresh build, NeoForge create-world deadlock).

Where things stand:
- Phase 2 passed the owner's gate on 2026-10-11 (commits 9abd1f76..ad2c361b). The owner said: "the held item reactions look really good and def read". On the temperaments: "already nicely balanced on all these fronts". A left-shoulder crab watching the item from its own hand is fine. Settling after ~30 s idle is fine.
- What exists in fishsim/src/main/java/grill24/fishsim/companion/:
  - CompanionWorld: one snapshot per tick, holding owner, blocks, points of interest, the Held category and hurt.
  - PointOfInterest: HOSTILE, CAT, DROPPED_ITEM or COMPANION, each with a stable id.
  - Drives: curiosity, wariness and rest, following R7.2 (dwell, habituation, cooldown).
  - Mind goals: LOITER, SETTLE, ESCORT, CATCH_UP, SHOW_OFF, INSPECT, HIDE, GUARD, NUZZLE.
  - Bodies: Body, PerchBody and DriftBody. CompanionGroup handles trios.
  - Temperament: BASELINE plus the ten from the data pack, looked up with Temperament.named(id) using the temperament JSON file name.
  - PoseHint: what the animator should show. Values: EXCITED, BEG, NOSE, TILT, MILD, WATCH, HIDE, DUCK, SLEEPY, PUFF, BITE, PEEK, NUZZLE, TREMBLE.
  - CompanionAgent exposes poseHint(), lookTarget(), riding()/onOwner(), appear()/fade() and the interpolated render pose.
- Parked by the owner. Don't tune either unless the owner reopens it:
  - GLIDE: needs a tank and companion redesign done together.
  - DRIFT: only glass_squid, flapjack_octopus and portuguese_man_o_war drift. They still need some in-game behaviour; ask the owner how to treat them (for example the current first cut, or FREE_SWIM for now).
- common/ still runs the old ClientCompanion in game. Old code:
  - common/.../client/companion/: ClientCompanion, ClientCompanions, CompanionPerchLayer, CompanionPip*. About 2.7k lines.
  - common/.../client/renderer/: CompanionFlock (its follow-box frame is to be deleted) and CompanionFishPose with the FishAnimator coupling (kept).
- Old reaction mapping, useful for the adapter's Held mapping: ClientCompanion's `reactions` list (rod via FishtasticItemTags.FISHING_RODS, bait via FISHING_BAIT, same species, PREDATOR_FISH, name tag, DataComponents.FOOD).
- FishProfile has `temperament` (an optional ResourceKey<Temperament>), a locomotion class and an animation mode.

Phase 3 scope (plan §5 and §3.5):
- CompanionWorldAdapter builds CompanionWorld each client tick:
  - LocalVoxels: a non-empty collision shape counts as solid; also record the water bits.
  - Points of interest from entity queries: hostiles, cats/ocelots, item entities, other players' companions.
  - The main-hand Held category, and an owner-hurt event.
  - OwnerState: eye, velocity, yaw/pitch, sprinting, a teleport/dimension flag, the camera perspective, FOV and inWater (water at the chest).
- Switch ClientCompanions to CompanionAgent/CompanionGroup:
  - Trio when FREE_SWIM and under 20 cm (CompanionGroup.isTrio).
  - Seed shared across clients from the bonded stack.
  - Interpolate per frame.
- Rendering:
  - The world-render hook; CompanionPerchLayer for riding perchers.
  - FishAnimator fed world-space pose (yaw, pitch, bank, speedFactor, tail phase, appear for the fade).
  - PoseHints mapped onto the existing pose dispatch where it already has an equivalent; list the rest for the owner rather than inventing animation blind.
- Delete the old motion core (the plan §6 delete column):
  - ClientCompanion's motion and behaviour.
  - CompanionFlock's box frame.
  - Blend weights.
  - Dev probes tied to the old controller.
  Keep: bond/equip/sync, the name/pet/feed/emote packets, the Companions tab, the encyclopedia, and the parked dock/PiP renderer files.
- Debug, per the owner's solo-playtest rule:
  - An overlay with goal, drive bars, pose hint and temperament.
  - Commands/triggers to force a drive, set a temperament, spawn a phantom companion and fake points of interest.
- Both loaders.
- Gate: the owner's solo playtest. Measure LocalVoxels sampling cost (plan §7 risk 3) and report it.

Decide or ask along the way:
- How DRIFT species behave in game while DRIFT is parked (ask the owner).
- What "hurt" means in multiplayer: only the local player's companion sees its own owner hurt, or synced.
- Other players' companions as points of interest: build them from the synced companions ClientCompanions already tracks.

Lessons to keep:
- Bodies are heading-first; tail-first must stay 0. Never lerp or whisk a companion to a spot. The fade and attachment to the owner (the parrot pattern) are the only placements allowed. Keep the baseline calm and put the liveliness into reactions.
- Find causes by tracing, never by guessing. Judge every behaviour change with an equal-grounds 8-seed A/B; adopt only clear wins. Measure the noise floor (two near-identical variants) before reading obstacle-scene contacts or impacts. Max-of-N metrics are noisy, and tails need about 40 seeds.
- Metrics are guard rails. If a metric disagrees with what a body visibly does, fix the metric openly and report both numbers.
- Changing a sim behaviour in Phase 3 must leave the 378 fingerprints (FingerprintProbe -PfpAll) unchanged unless it's meant to. Phase 3 should be adapter and rendering work, not sim tuning.
- Never touch FlockEngine; its goldens are bitwise-locked. companion/ ships in the mod jar; harness, render and viewer don't. No Minecraft imports in :fishsim. Java 17 --release.
- In-game verification is the owner's eye. runClient needs a fresh build (let runClient build it). Don't relaunch a hung client more than once; report the loader unverified. jstack a NeoForge create-world hang.

Commands:
  ./gradlew :fishsim:test --tests 'grill24.fishsim.companion.*'   (about 25 s; temperament rows use the baseline's 40-seed envelope)
  ./gradlew :fishsim:runCompanionViewer   (owner-facing: temperament picker; H/Z/C/I/O/K/X keys in manual mode)
  ./gradlew :fishsim:runCompanionHeadless -PsimArgs="--temperament docile --script idle_fixed"
  Probes, all opt-in with -PsimStdout --rerun:
    FingerprintProbe [-PfpAll=1]
    SeedSweepProbe (-PsweepClass/-PsweepTemperament/-PtweakA/-PtweakB/-Psweep=all)
    TraceProbe
    ImpactProbe
    OwnerSpaceProbe
    TemperamentSweepProbe (-PsweepSeeds)
    TemperamentFeatureProbe
    Curiosity/Wariness/RestProbe
  ./gradlew :fabric:runClient / :neoforge:runClient

Housekeeping:
- Don't git add -u/-A, because tracked common/logs get swept in. Stage explicit paths.
- The pre-commit hook runs unit tests and game tests (about 3 minutes). Run the commit in the background and wait for it before editing tracked files.
- If commit signing fails ("1Password: agent returned an error"), never bypass it. Ask the owner to unlock 1Password and wait.
- In this shell, heredocs containing apostrophes break. Write patch scripts to a file under %TEMP% with the Write tool and run them from there.
- The tree was clean at ad2c361b at the end of the last session.

Begin by proposing the Phase 3 order and the gate for each step, then build.
