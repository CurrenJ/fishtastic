# Benthic follow: handoff (2026-10-12, implementation resumed)

Read with docs/fish-companions-benthic-follow.md (§12–§25 are the implementation and experiment record).

## Latest continuation: pillar recovery (§25)

The main tree now recovers stationary approaches aimed inside tall pillars. After
40 ticks of actual lack of progress it tries a checked hop to a rear station, then
releases to ordinary ground behavior with a retry cooldown. An unavailable escape
uses the existing fade to the perch after 60 stalled ticks. Owner motion/turns
reset recovery; cached floor and route answers are revalidated.

Twenty deterministic seeds physically escape; full-agent tests verify that recovery
does not leave a new cached-target stall and that companions follow when the owner
walks away. The 60-seed A/B changes only large-crab cellar seed 15: one extra fade
resolves a traced stationary approach, with 109 fewer walking-visible ticks but
less underfoot time and a shorter longest unseen stretch. §25 records the tradeoff.
The general under-feet gap and remaining stair fades are still open.

Validation logs: `build/benthic-pillar-validation.txt`,
`build/benthic-pillar-fingerprints.txt`, `build/benthic-pillar-gametest.txt`.
All sim/common tests and both builds pass; all 447 protected fingerprints match;
all 317 required Fabric game tests pass. The sim suite has 364 tests, 336 passed
and 28 diagnostic skips. Strengthened recovery coverage also passes separately.
Owner-world playtest remains pending.

## Earlier resume outcome (now integrated in the main tree)

- **D / §22:** no runtime fix accepted. The alongside stall and under-feet gap remain open;
  per-seed regressions rejected the saved steering candidates, and a narrow retry failed actual
  pillar recovery. Rejected patches and probes remain in fork D's untracked `build/`.
- **E / §23:** formation members retain the motor's guard against unrequested drops. Over
  20 equal seeds, stair fades fall from 103 to 93 for lines and 35 to 27 for crowds; no seed
  gains fades. Member overlap increases slightly within existing bounds. Remaining fades persist.
- **F / §24:** look-ahead caps were rejected for visibility regressions. Repeated identical
  footprint checks are reused within each full route scan instead: 77.6% fewer footprint
  evaluations with identical trajectories and metrics over 144 paired runs. Block sample peaks
  remain unchanged; this is an optimization of planner work, not a cap on block queries.
- Added `BenthicStairsDropTest` and `BenthicFootprintScanTest`. Main-tree sim tests pass:
  357 total, 330 passed, 27 diagnostic skips; common tests: 766, zero failures. Both platform
  builds pass. All 447 protected fingerprints match `build/fp-base-p2.txt`.
- Final `:fabric:runGametest`: all 317 required tests pass with the integrated changes.
- Evidence: `build/benthic-resume-final-tests.txt`, `build/benthic-resume-fp-final.txt`, and
  `build/benthic-resume-final-gametest.txt`. In-game visual/cost checks and NeoForge game tests
  remain unverified.

The stopped-fork inventory below is the historical starting point; §22–§24 supersede it.

## Committed on 26.1.2 (all signed; hook passed)

| Commit | What |
|---|---|
| 07920354 | §12–§18: transitions, terrain repairs, groups, PLAYER scatter, ground desires, in-view beats |
| eee6090c | §19–§21: under-feet diagnosis (no fix), formations completed, game integration |
| aee461ea | Owner playtest fixes: plaice walks 45° off (floor_sit `diagonal_texture`), plaice ripple (floor_sit `ripple`), upright benthic swell ×2.5 (`/fishtastic companion swell <gain>`) |

Main tree: clean except the tracked `common/logs/latest.log` (never stage it).

## Owner feedback so far (in-game, 2026-10-12)

- Starfish look great and lively; the plaice offset and the missing liveliness on the others are fixed in aee461ea (owner said "looks good" to the plan). **Not yet re-checked in game after aee461ea.**
- Open owner question: crowds bunch tighter at rest than moving (spec §4 says the opposite). Ask before changing.

## Three forks stopped mid-work (UNCOMMITTED, unvalidated; all based on aee461ea)

Each sits in a git worktree under `.claude/worktrees/`. Treat their diffs as experiments: nothing there has passed the validation bar, and none should be merged without re-running it.

| Fork | Worktree | Task | State when stopped |
|---|---|---|---|
| D | `agent-a56df7c46afe3d887` | Come-alongside: (1) permanent stall when the alongside spot is behind a pillar; (2) under-feet gap (§18.3/§19) | Edits to GroundBody/GroundMind/PerchBody + `ComeAlongsideProbe`. Was mid 20-seed A/B ("mode 0 vs 16"); its walkability fix now rejects only 7 ticks over 24 stair runs (genuine wall crossings). Results not yet read. |
| E | `agent-a13ae92a64bc41c84` | Starfish line fades 5–8×/run on stairs_up_down (§20) | Edits to CompanionAgent/GroundMind + `StairsFadeProbe`, `GroupAnkleProbe`. Tracing: follower 2 stranded on the lower floor beside the stairs (x≈2.35) with member 1 a block up (x≈1.97); next step was reading the stair geometry. No root cause confirmed. |
| F | `agent-a1900010bb3d9b749` | Cap the beat planner's ~50-block look-ahead (rare ~170-sample ticks) | Edit to GroundMind + `BenthicSpikeAttributionProbe`, a `cap-sweep.sh` sweep and outputs (`cap-sweep.txt`, `spike-attr-uncapped.txt`) in the worktree root. Sweep was being set up; no cap chosen. |

Inspect with `git -C .claude/worktrees/agent-<id> diff` and read the probes. To resume, relaunch a fork per row with the same brief (spec sections reserved: §22 D, §23 E, §24 F), or salvage the diff by hand.

## Validation bar (unchanged)

- `./gradlew :fishsim:test` all green.
- `FingerprintProbe -PsimStdout -PfpAll=1`: all 447 non-BENTHIC lines identical to `build/fp-base-p2.txt` (grep FP lines, drop ` BENTHIC `, sort, diff). The baseline file is untracked in the main tree's `build/`.
- Behaviour changes: per-seed equal-ground A/B with an on/off switch (a pooled total once showed a fake gain that per-seed reversed in 9/12).
- `:common:test`, `:fabric:build :neoforge:build`, `:fabric:runGametest`. If `:common:jar` is locked, it's the owner's running client: don't kill it.

## Gotchas learned this session

- Fork worktrees were created at a stale commit (44064cc5) in round one; check `git log -1` in every fork.
- Commits are signed via 1Password: the prompt appears after the ~3 min pre-commit hook (unit tests + Fabric gametests) and needs the owner. A failed sign shows `1Password: failed to fill whole buffer` and no commit.
- Stage by explicit path; never `git add -A/-u`.

## Still open beyond the forks

Owner in-game playtest of §21 (checklist in spec §21.4) and of aee461ea; real in-game sampling cost (overlay cost line); NeoForge gametests (known hang, skipped by the hook).
