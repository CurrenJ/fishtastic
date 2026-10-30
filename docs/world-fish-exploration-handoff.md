# World fish exploration: session handoff

**Updated 2026-10-30 19:53 UTC.** This file is the single starting point for resuming. Read it
fully, then follow "Resume order". Update it, or replace it with a newer handoff, whenever work
pauses again.

## What we are doing

We are implementing the world fish exploration rework on branch `26.1.2`. The documents behind it:

- Design: [world-fish-exploration-design-spec.md](world-fish-exploration-design-spec.md), with
  owner decisions E1–E19.
- Plan: [world-fish-exploration-implementation-plan.md](world-fish-exploration-implementation-plan.md),
  with Steps 0–7 and decisions P1–P4. The step table and each step's "findings / resume notes"
  record the exact state, down to the file and line. This handoff summarizes them and adds how to
  work.

In one line: visits come from deterministic per-chunk windows, and arriving reveals a visit but
never causes one. Only depletion is saved, plus the accepted weather log. Ambient fish are
seeded and shared by every client, and a spyglass observation unlocks Fishopedia fields.

## How to run the work (owner instruction)

- **Delegate the implementation.** Implementation is done by Sonnet 5.5 sub-agents (Agent tool,
  `model: "sonnet"`). The main thread stays clear: it briefs, checks in, reviews reports, takes
  owner decisions and keeps the docs current. Don't implement large pieces in the main thread.
- **Brief each agent fully.**
  - It should read `AGENTS.md`, the plan doc (its own step plus the findings of the steps it
    builds on) and the relevant design decisions.
  - Give it the step scope and every owner decision that binds it.
  - Name the files other agents are editing at the same time.
  - Include the "Agent rules" block below.
  - Ask for a concise final report: files, behaviour, test counts per loader, deviations, and an
    in-game test script for the owner.
- **Continuing an agent.** A finished agent can be resumed with SendMessage and keeps its context.
  That suits follow-on work in the same files, as Step 3 followed Step 2. Old agent IDs do not
  survive into a new session, so start new agents there.
- **Running two agents at once.** Parallel agents are fine when their files are mostly disjoint.
  Expected side effects:
  - gradle lock waits;
  - occasional `EOFException` or `NoSuchFileException` in `common/build/test-results` (rerun);
  - transient compile failures from the other agent's half-written files.
- **Check reports.** Read every report critically. Earlier reports had a wrong premise, such as
  "past weather can't be reconstructed". Raise real design calls with the owner instead of letting
  an agent pick silently.

### Agent rules (paste into every brief)

- Do NOT commit; never `git add -A` or `-u`, because the tracked `common/logs/latest.log` gets swept in.
- No game client launch unless the brief explicitly asks for a self-test scene.
- Verify with `:common:test`, compileJava on fabric, forge and neoforge, and
  `runGametest --no-parallel` on Fabric, NeoForge and Forge, reporting exact counts.
  - The NeoForge gametest JVM may not exit after passing; watch for the pass line.
  - Forge `backup_fixture_restores_shared_state_and_files` sometimes fails with a Windows
    `AccessDeniedException`; rerun once and say so.
  - Don't pipe builds into `tail`.
  - Don't kill java or gradle processes you didn't start.
- Every feature ships an in-game debug trigger, because the owner tests in a live solo game.
  Destructive commands call `beforeDestructiveCommand`.
- Keep the plan doc current: update your step's row and a "Step N findings" subsection. Re-read
  the file right before editing, and touch only your own step's parts.
- Match the surrounding code style and comment density.

### Owner working preferences that matter here

- Commit only when the owner asks. Once steps are green, propose commits split by step. The
  owner has not asked for any yet.
- Every step needs its listed tests plus an owner look or playtest where it is visible.
  "Built" is not "done".
- Validate before claiming a fix: run an A/B comparison on equal grounds. A metric is a proxy, not
  the goal.
- Self-tests: the owner wants them run proactively, with no hard time caps; fail fast on evidence.
- The owner may be short on usage. Pause at points that compile, with resume notes.

## State at handoff (all uncommitted on 26.1.2)

| Step | State |
|---|---|
| 0 Density model | Initial model done; five-minute duration tested well, and the rate-up candidate `chance=0.024` awaits playtest. Step 0 assumes water share 0.5. |
| 1 Derivation core | Built and tested (`ecology` package, seed sync, `/fishtastic ecology`). |
| 1b Presence report | Built; the owner reviewed it. |
| 2 Visit windows | Built and verified; owner playtest surfaced a visit-accessibility issue: a forced reveal anchored at y=29 while the owner was swimming near y=64. |
| 3 Mid-window reveal, changed inputs and weather log | Automated tests green: Fabric 408/408, NeoForge 402/402, Forge 400/400 required. Owner playtest pending. |
| 4 Seeded ambient fish | Automated tests green on all loaders; Fabric render smoke passes 81/81; owner look and two-client check pending. |
| 5 Spyglass observation | Unit and GameTests green on all loaders; owner in-game look pending. Owner approved encyclopedia reset clearing observations. |
| 6 Balance, 7 Docs and ports | Five-minute duration tested well; rate-up candidate awaits playtest. Continue outstanding owner checks; defer vertical-accessibility changes until tuning settles. |

## Owner decisions made during implementation (2026-10-30)

- **Initial Step 0 values.** `T` 3600, `L` 2400, chance 0.016 and `R` 2 were the accepted baseline;
  the owner has since asked to try visits around five minutes.
- **Duration playtest (2026-10-30).** The owner tested the five-minute candidate and says it looks
  good overall. The owner then asked to increase visit rates overall. Try chance `0.024` while keeping
  `T=7200`, `L=6000`, and `R=2`; the stationary encounter-rate target remains open.
- **Per-site window phase.** Each site's windows are `[t0 + kT, t0 + kT + L)`, with
  `t0 = hash(SALT_PHASE) mod T`, so all sites do not open on the same tick. Suppression compares
  overlapping neighbour windows.
- **Vanilla fish are dropped from ambient.** Cod, salmon, tropical fish and pufferfish never
  appear as ambient fish. They stay in catch and visit pools.
- **Nether ambient fish live in lava**, as an easter egg for players with fire resistance. The
  nether roster stays as shipped, and no visits are formed in lava.
- **The weather log is accepted.** It is a small, pruned (gameTime, state) list per dimension, so
  visits derive under the weather at window start instead of the current CLEAR placeholder. It is
  a bounded exception to E1's "only depletion is saved", because it records world weather, not
  visit state. The full spec is in the plan under "Step 3 findings / resume notes".
- **Encyclopedia reset clears observations.** The owner approved this alongside clearing catch data.
- **Cave fish remain part of the intended experience.** Do not solve open-water findability with a
  blanket surface-only rule; keep cave visits and cave-appropriate species discoverable in cave water.

## Visit duration and rate tuning (2026-10-30)

- **Duration playtest result:** the owner says the five-minute duration looks good overall. The first
  candidate was `T=7200` (6 minutes), `L=6000` (5 minutes), chance `0.016`, `R=2`.
- **Rate-up candidate to playtest:** keep `T=7200`, `L=6000`, and `R=2`, and increase chance to
  `0.024` (50% higher before suppression). Default profile lifetime scale is 1; configured species
  scales can shorten or lengthen visits, capped at one period.
- Under Step 0 assumptions (48-block range, 50% water share), the model estimates about 2.7
  stationary starts/hour, a 5.1-hour mean wait at a single one-chunk pond, 29.5 travel passes/hour at
  2.5 blocks/s, 56.2/hour at 5 blocks/s, and a 20.1% chance of at least one open visit in view at a
  random moment. At chance `0.016`, the corresponding estimates were 1.9/hour, 7.2 hours, 21.2/hour,
  40.4/hour, and 14.9%. These are model estimates, not playtest measurements.

## Resume order

1. **Playtest the rate-up candidate.** Check `/fishtastic visits windows` for `T=7200`, `L=6000`,
   chance `0.024`, and `R=2`. The five-minute duration tested well overall; now assess whether the
   increased rate improves finding visits without making them too common. Also continue the pending
   Step 3 playtest, Step 4 look/two-client check, and Step 5 in-game look.
2. **Defer the deep-anchor accessibility change until broader tuning settles.** The owner wants
   cave fish to remain in underwater caves; any later findability fix must preserve cave visits and
   avoid a blanket surface-depth clamp. Continue the other Steps 2–5 playtests and the live
   two-client ambient check if available. Step 7's `fish-in-world.md` rewrite is still planned.
3. **Before a commit**, review the shared loader GameTest registrations and `FishtasticPackets`.
   Nothing has been staged or committed. The owner has not asked for commits; propose them split
   by step after the playtest checkpoint.
4. **Model/environment notes.** The environment did not offer Sonnet 5.5; the Step 3 and Step 5
   agents used GPT-6.1 Sol. The smoke ran on Java 25.0.2 after granting Gradle access to its cached
   distribution lock; the Oracle `java` shim and a sandbox-denied cache attempt did not launch a
   client. The complete successful process output is at the evidence path above.

## Ambient render smoke checkpoint (2026-10-30)

- First run: Fabric `:fabric:runClient` exited 0 after 5m57s; 77/81 checks passed. The four
  failures were brittle floor/walk population and zero-expiry assumptions. Preserved at
  `build/world-fish-exploration/2026-10-30/ambient-render-smoke-first-run.log`.
- Second run: Fabric `:fabric:runClient` exited 0 after 5m26s; 78/81 checks passed. It isolated the
  remaining fixture-count assumptions: the pond had one resident against two expected, and the
  island had 25/32 then 26/32 residents. Preserved at
  `build/world-fish-exploration/2026-10-30/ambient-render-smoke-second-run.log`.
- Third run: the pond minimum is one and the island readiness/population and sustained minimums are
  20/20. Fabric `:fabric:runClient` exited 0 with `BUILD SUCCESSFUL` after 4m25s; all 81 checks
  passed: shaders 1, parity 14, water 55, island 5, floor 3 and walk 3. The pond reported two
  nearby fish; island population was 20/32 initially and 24/32 sustained. Floor/walk population and
  seeded-lifespan continuity passed. Full output is
  `build/world-fish-exploration/2026-10-30/ambient-render-smoke.log`; the marker was removed.
- The third run saved 28 screenshots under `fabric/run/screenshots/`. Spot checks of the pond shore,
  island shore and eel bed show rendered fish; the owner visual review remains due. The smoke used
  Java 25.0.2 and the explicitly requested Gradle cache access; no unit or loader GameTests were run
  during this render-smoke checkpoint.
- **Owner visit playtest finding (2026-10-30; deferred).** `/fishtastic visits reveal` produced a visit at
  y=29 while the owner was swimming near y=64; the fish were difficult to locate. This confirms the
  deep-anchor behavior can occur through the same derivation used by naturally scheduled visits.
  Code evidence: `FishSchoolManager.revealWindow` chooses a start cell from all qualifying water
  cells (`FishSchoolManager.java:1139-1141`), and `createSchool` seats members within ±2 Y of it
  (`FishSchoolManager.java:1657`). The surface-jump selection only includes members within 4 blocks
  of the surface (`FishSchoolManager.java:120, 529-549`). Exact world, species, and command output
  were not captured. The owner wants cave fish to remain in underwater caves; a blanket surface-only
  placement rule is out. The owner chose to revisit open-water findability after duration tuning settles.
- `ambient-render-smoke-first-attempt.log` and `ambient-render-smoke-cache-denied.log` retain the
  incomplete startup and sandbox-denied attempt evidence. The latter required the explicit Java 25
  path because the shell's Oracle Java shim hung.

## Pre-commit review fixes (2026-10-30)

A pre-commit review of the uncommitted tree found ten issues; all are fixed in the main thread.

- **Deep visits revealed and dropped in one pass.** `dropFarVisits` now measures horizontal
  distance like the reveal range; the 3D test dropped deep start cells the same pass that revealed
  them, every pass. Possibly related to the y=29 findability report, which stays deferred.
- **Ambient scan starvation.** `AmbientChunkField` remembers chunks found unloaded and retries them
  after `UNLOADED_RETRY_TICKS` (20) instead of letting one win every tick.
- **Server spyglass check.** `FishObservation` refuses with `NOT_SCOPING` unless `player.isScoping()`,
  checked last so other refusals keep their reason. The observation GameTests now scope and cover it.
- **Weather log rewound time.** `VisitWeatherLog.record` drops entries after a rewound game time
  instead of throwing every tick.
- **Suppression over the hosted span (rate impact).** `VisitWindows.isSuppressed` now judges overlap
  over `hostedSpanTicks` = min(T, L × 2), the longest a visit can live, instead of L. No shipped
  species uses `lifetime_scale` above 1 yet, but E5 must hold for any profile. With the default
  T=7200, L=6000, chance 0.024, R=2, survival drops from 0.793 to 0.759, so every Step 0 rate
  estimate falls by about 4.3% (stationary about 2.7 to 2.6 starts/hour). `VisitDensityModel`
  now uses the same span and still matches the hashed field.
- **World switch.** `EcologyClient` and `ObservationClient` clear when the client has no level.
  `EcologySeedSync` holds the server and players weakly and resends to a player object that
  reconnected between ticks; `FishObservation`'s rate map is pruned.
- **Cleanup.** School add/remove no longer mark the saved data dirty (schools are not saved);
  `qualifyingWaterCells` reads the section's fluid before the level lookups and stores packed
  longs (same cells, same order); the windows report shares the scheduler's chunk-range helper;
  observe packets are built only for the hold that completes; `-PambientPresenceReport=<dir>` is
  honoured.

## Known deviations and risks to show the owner

- **Ambient arrivals and leavers fade in place.** They fade at the edge of the home chunk rather
  than swimming in from, or out to, beyond view.
- **The ambient leash is a fade-out.** A fish more than 8 blocks outside its chunk fades out and
  re-enters at home, because `:fishsim` is pinned by parity tests.
- **Ambient fish use the client's current weather,** so a client that sees a weather change late
  can briefly draw different species.
- **Old windows without saved weather history fall back to the current weather.** The debug
  report marks the fallback; once the log has started, it retains the window-start state.
- **Ambient observation validation is deliberately loose.** The server accepts any species the
  home cell could hold under any weather for a live fish ID. A modified client can unlock that
  species, but observation grants no catch count, quest progress or reward.
- **Visit members leaving mid-window pop out.** There is no per-member fade, only the school-level
  fade; this is a possible visual follow-up.
- **`/reload` does not reload `fish_profile` registries.** Profile changes take effect on restart.
- **Small ponds wait a long time.** The initial baseline estimated a 3.5 h mean wait for a one-chunk
  pond; the current rate-up candidate estimates 5.1 h under the same Step 0 assumptions.
- **A player circling one lake gets only the stationary rate.**
- **Stale docs.** The encounters spec and the schools performance plan still describe region
  scheduling and `schools.activeLifetimeTicks`. Step 7 rewrites them.

## Owner playtest script (automated gates are green; visual review remains)

1. **Visits.** At a lake, run `/fishtastic visits windows` and wait at a chunk marked "hosts a
   visit". The visit should appear on its own, last about 5 minutes for default-scale species and
   thin out as members leave.
   `/fishtastic visits reveal` forces one. Catch a fish, go more than 100 blocks away and come back
   inside the window: that fish should still be missing. Save and reload: still missing.
2. **Weather.** Run `/weather rain` before a window opens. That visit should keep rain species
   after the rain stops.
3. **Ambient.** Run `/fishtasticambient density 100` and `/fishtasticambient debug`: no vanilla
   fish should appear. At density 25 and back at 100, the same IDs should return.
4. **Nether.** Run `/effect give @s fire_resistance 600` and swim in a lava lake. Nether species
   should be visible up close, and no visits should form.
5. **Spyglass.** Hold the spyglass on an ambient fish or a visit member for about 1 s. A toast
   appears, and the Fishopedia shows the name and portrait without any catch.
   `/fishtastic observe clear` resets it.
6. **Two clients, if available.** Both should list the same ambient IDs and species.

## Key code map

- `common/.../ecology/`:
  - `EcologyHash` (salts, including `SALT_PHASE`; the pinned outputs must not change)
  - `EcologySeed`, `EcologyClient`
  - `VisitWindows` (`Params.DEFAULT`)
  - `VisitId`, `VisitDerivation`
  - `NaturalPool`
  - `AmbientRoster`, `AmbientFluid`
  - `ObservationRules`
- Server:
  - `server/FishSchoolManager` (scheduler, reveal, departures, `environmentAtWindowStart`)
- `server/FishSchoolSavedData` (depletion ledger and bounded weather history)
  - `server/EcologySeedSync`
  - `server/FishObservation`
  - `config/FishtasticServerConfig.visitWindowParams()`
- Client:
  - `client/ambient/*` (`AmbientChunkField`, `AmbientFishRuntime`)
  - `client/ObservationClient`
- Commands: `command/EcologyCommand`, `command/VisitsCommand`, and the observe commands.
- Reports (gated test runs):
  - `./gradlew :common:test --tests '*VisitDensityReportTest*' -PvisitDensityReport=build/reports/visit-density`
  - `./gradlew :common:test --tests '*AmbientPresenceReport*' -PambientPresenceReport`
