# Benthic follow: handoff (updated 2026-10-12, after d8d552f7)

Benthic companions (crabs, slugs, starfish, plaice, octopus) follow their owner **on the
ground**, in a solo or a group of up to 3, instead of always riding a shoulder. The design and
as-built record is `docs/fish-companions-benthic-follow.md` (§1–§11 design, §12–§25 as built and
experiments). This file is the entry point: read **Start here**, then the section of the spec
for whatever you pick up.

## Start here

- **Branch `26.1.2`, HEAD `d8d552f7`.** Everything below is committed (signed). Nothing about
  benthic follow is uncommitted except the diagnostic `fishsim/.../BenthicShapeProbe.java`
  (untracked, harmless; commit it with the next batch or delete it).
- **Headless sim (`fishsim`) is feature-complete for the spec's Phases 1–5**: ground walking,
  terrain, climb/dismount/tumble, groups (line/crowd), any-player scatter, ground desires,
  in-view beats, pillar recovery. **The game wires it all in** (§21), with plaice and swell fixes
  from the owner's first playtest (5b1f9645).
- **Green at HEAD:** `:fishsim:test` 364 tests (336 passed, 28 diagnostic skips), `:common:test`,
  both loader builds, 317/317 Fabric game tests, all 447 protected fingerprints identical.
- **Nothing since 5b1f9645 has been seen in game.** The owner's playtest is the gating step.

**First move next session:** ask the owner for (or wait on) the playtest of d8d552f7
(checklist below), then take the top of the work queue.

## Waiting on the owner

1. **In-game playtest of HEAD.** The spec §21.4 checklist, plus:
   - Plaice: nose leads its walk (was 45° off), and the ripple reads as a glide, not a crawl.
   - Slugs, spider crab and octopus: does the push-off swell now read as lively? Tune live with
     `/fishtastic companion swell <gain>` (default 2.5; bare `swell` prints it). If the owner
     settles on a value, make it the default in `CompanionRender.DEFAULT_UPRIGHT_SWELL`.
   - Pillar recovery (§25): in a pillared cellar, a companion that can't reach its alongside spot
     now hops back or fades to the perch instead of standing still.
   - Real block-sampling cost: the debug overlay's cost line (`/fishtastic companion debug true`).
2. **Open question:** a crowd bunches tighter at rest (0.34–1.09 blocks) than moving
   (0.59–1.49), the opposite of spec §4's "spread out when stopped". Bug, or fine as is?
   Ask before changing.

Debug commands (single player): `/fishtastic companion ground | climb | tumble | drowsy | hurt |
swell [gain] | debug <bool>`. `drowsy` only holds while the owner stands still: walking drains rest
in ~1.5 s, after which nothing visible happens.

## Work queue (prioritized)

1. **Starfish line still fades on stairs** (§20, §23). §23 kept formation members from walking off
   edges they didn't ask to drop: 20-seed stair fades went 103→93 (line) and 35→27 (crowd), with no
   seed worse. It's still the biggest group gap; in game, starfish blink onto the shoulders on a
   staircase. Fork E's trace before the fix: follower 2 stranded on the lower floor beside the
   stairs (x≈2.35) while member 1 is a block up (x≈1.97). **First step:** run `StairsFadeProbe`
   (from fork E's worktree, see Leftovers; not yet in the main tree) on a fading seed and read the
   stair geometry at the strand point. Rejected already (fork B): slow-down-behind-predecessor,
   leader separation, earlier crowd separation.
2. **Under-feet come-alongside** (§18.3, §19, §22). A solo benthic coming up for a hand hop can
   walk through, crouch in, or hop through the owner's feet. Mechanisms (60-seed trace, spider
   cellar): crouch slide 322 ticks, crossing 197, hop-through ~143, plus the spider's swing-in
   (turning radius ~2.2 at cruise). **Every candidate so far is rejected** (guard on current
   position, front waypoint, tangent, speed caps, braking/turning variants: §19.2, §22.1). Untried
   lead: an approach that keeps its speed, e.g. a wider alongside spot for big bodies with a
   predicted stop point. Experiment patch: `docs/benthic-come-alongside-experiment.patch`.
3. **§25 trade-off to watch.** On one cellar seed (spider size, seed 15), recovery turns a
   stationary approach into a fade: −109 walking-visible ticks, but less underfoot time and a
   shorter longest unseen stretch. Acceptable as recorded; revisit if the owner sees fades in
   cellars.
4. **Beat planner peaks** (§24). The rare ~170-block-sample ticks remain. Look-ahead caps were
   rejected for visibility regressions; the repeated-footprint reuse cut planner work by 77.6% with
   identical trajectories, but not the block queries. Only worth more if the in-game cost line
   shows a problem.
5. **Owner-dependent:** crowd spread at rest (question above); a "sometimes wavy" line via
   pace-dependent leader meander (spec §4; the first attempt caused a follower fade and was removed).
6. **NeoForge game tests** have never been run on this work (known `runGametest` hang; the hook
   skips them).

## Code map

| Where | What |
|---|---|
| `fishsim/.../companion/GroundBody.java` | Ground motor: heading-first walk, steps/hops/drops, flight, gait rhythm, `shape` (push-off envelope) and `tailPhase` (ripple clock) |
| `GroundMind.java` | Land modes (idle/follow/catch-up/fade), foot trail and lag, potter spots, owner/player scatter, formation steering, in-view beats (`planBeat`, `beatsEnabled` switch), alongside recovery (§25) |
| `PerchBody.java` | Perch slots, hops up/down, climb/dismount/tumble, attention clock (`unseenTicks`, beat aftermath) |
| `Mind.java` (`thinkGround`) | Ground desires: INSPECT/HIDE/GUARD/NUZZLE as floor targets |
| `BenthicGroup.java` | Leader + followers, line/crowd stations, slot allocation, staggered climbs |
| `CompanionAgent.java` | Public face: constructors (`GroundGait` = ground path), counters, dev triggers |
| `harness/companion/OwnerScript.java`, `CompanionRun.java` | Scripted owners (`GROUND_NAMES`), metrics (`groundMetrics()`) |
| `common/.../client/companion/ClientCompanion.java` | Game side: gait/group from `FishProfile`, steps the group, draws members, overlay |
| `CompanionWorldAdapter.java`, `OwnerFallTracker.java` | Builds the sim's world each tick (blocks, players, riding/elytra, falls) |
| `client/renderer/CompanionRender.java`, `FishAnimator.java` | Pose: floor lift, `applyBenthic`, floor_sit `diagonal_texture`/`ripple`, upright swell gain |
| `CompanionPerchLayer.java` | Up to 3 riders on shoulders/head |
| `data/FishProfile.java` | `companion_group {count ≤3, line|crowd}`, `companion_gait {crawl|ripple}` |
| Profiles | `starfish` (line of 3), `trapania_scurra` + `willans_chromodoris` (crowds of 3), `plaice` (ripple, diagonal) |

## Commands

```
./gradlew :fishsim:test                                   # sim suite (probes skipped)
./gradlew :fishsim:test --tests '*FingerprintProbe*' -PsimStdout -PfpAll=1 > build/fp.txt
./gradlew :fishsim:runCompanionViewer                     # owner viewer: BENTHIC classes, benthic_park, keys G/P/B/K/M/E/J
./gradlew :fishsim:test --tests '*BenthicBeatProbe.perSeed' -PsimStdout -Ptrace=walk_flat -PsweepTemperament=sprinter
./gradlew :common:test :fabric:build :neoforge:build      # don't use -x test: it also skips :common:test
./gradlew :fabric:runGametest
```

Fingerprint check: grep the `FP` lines from the output and from `build/fp-base-p2.txt` (untracked,
main tree), drop lines containing ` BENTHIC `, sort, diff: the 447 must match. BENTHIC lines are
expected to change.

## Rules that held this work together

- **Equal-ground A/B, per seed**, with an on/off switch for every behaviour change. A pooled
  6-seed total once showed a gain that per-seed reversed in 9 of 12. A change that shortens one
  tail and lengthens another is rejected, not shipped.
- **Protected paths stay bitwise**: FREE_SWIM, GLIDE, DRIFT, ANCHORED, PERCHER, TRIO.
- **Never relax existing terrain/group contracts** to land a new behaviour.
- **Owner preferences:** lively through visible shape (the starfish's swell is the bar), calm idle;
  companions always swim/walk (no lerping); speed variety reads as alive; fades land on the perch.
- **Commits:** stage explicit paths (never `git add -A/-u`; `common/logs/latest.log` is tracked).
  The pre-commit hook runs unit tests + Fabric game tests (~3 min); signing then goes through
  1Password and needs the owner's approval. `1Password: failed to fill whole buffer` means the
  prompt timed out and nothing was committed.
- **Locked `:common:jar`** means the owner's game client is running: don't kill it; use the
  `compileJava` tasks and say the jar/gametest steps are pending.
- **Forks:** run them in worktrees, check `git log -1` first (round-one worktrees started at a
  stale 44064cc5), pre-assign spec section numbers, merge one at a time and rerun the full suite
  after each.

## Commit history

| Commit | What |
|---|---|
| 95a320fd | Phase 1: ground body, foot trail, catch-up/fade, plaice glide (headless) |
| adc30659 | §12–§18: transitions, terrain repairs, groups, PLAYER scatter, ground desires, in-view beats |
| 2b6c48a4 | §19–§21: under-feet diagnosis (no fix), formations completed, game integration |
| 5b1f9645 | Owner playtest fixes: plaice orientation (`diagonal_texture`), plaice `ripple`, upright swell ×2.5 |
| d8d552f7 | §22–§25: under-feet experiments rejected, stair edge-drop guard for formations, beat-planner scan reuse, pillar recovery |

## Latest results in detail (§22–§25)

- **§22, alongside stall and under-feet:** no runtime fix accepted; per-seed regressions rejected
  fork D's steering candidates. Rejected patches and probes are in fork D's worktree `build/`.
- **§23, formations on stairs:** members keep the motor's guard against unrequested drops; stair
  fades 103→93 (lines) and 35→27 (crowds) over 20 equal seeds, no seed worse; member overlap up
  slightly, within bounds. `BenthicStairsDropTest`.
- **§24, beat planner:** caps rejected for visibility; repeated footprint checks reused within a
  route scan (77.6% fewer evaluations, identical trajectories over 144 paired runs).
  `BenthicFootprintScanTest`.
- **§25, pillar recovery:** after 40 ticks stalled at an alongside spot inside a tall solid, with
  the owner still: a checked hop to a rear station, then back to normal behaviour with a 100-tick
  cooldown; otherwise the perch fade after 60 stalled ticks. Owner movement or turning resets it.
  `BenthicAlongsideStallTest` (20 pillar seeds escape by tick 150), `AlongsideRecoveryProbe`
  (60 seeds; only spider cellar seed 15 changes). §25's closing "No commits were made" predates
  the commit: it is in d8d552f7.
- Evidence logs (main tree `build/`): `benthic-pillar-validation.txt`,
  `benthic-pillar-fingerprints.txt`, `benthic-pillar-gametest.txt`,
  `benthic-resume-final-tests.txt`, `benthic-resume-fp-final.txt`,
  `benthic-resume-final-gametest.txt`.

## Leftovers on disk (not part of the build)

- Six fork worktrees under `.claude/worktrees/agent-*` (bases adc30659 or 5b1f9645). Their useful
  outcomes are committed; they still hold evidence (fork D's `build/` logs: `d-final60-all.txt`,
  `d-final-release2.txt`, `d-trace15-final.txt`) and probes not in the main tree (`StairsFadeProbe`,
  `GroupAnkleProbe` in `agent-a13ae92a64bc41c84`; `BenthicSpikeAttributionProbe`, `cap-sweep.*` in
  `agent-a1900010bb3d9b749`). Copy out anything wanted, then remove with
  `git worktree remove` (ask the owner first).
- Untracked and unrelated to benthic follow: `common/.../menu/CompanionMenu.java`,
  `textures/gui/companions.png`, `tools/mc-gui-designer/` (a separate companion-GUI effort),
  `.agents/`, `.codex/`, `AGENTS.md`, and the obsolete `docs/fish-companions-phase3-handoff.md`.
