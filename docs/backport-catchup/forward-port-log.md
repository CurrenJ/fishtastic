# Forward-port log: `fa103289..e21bc4a3`

Updated: 2026-10-25. The range contains 21 source commits: 13 were ported to both target
branches, and 8 are source-only progress or tooling commits excluded by design. The latest code
state is validated by the fish-school polish result matrix under
`build/backport-catchup/2026-10-25/fish-school-polish/result-matrix.md`.

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `c00a10b2` Update backport docs | Excluded: source progress tracker only | — | — |
| `6aba7bed` Record advanced port parity markers | Excluded: source progress tracker only | — | — |
| `0045805e` Clarify current parity marker state | Excluded: source progress tracker only | — | — |
| `16d04e34` Refresh source commit reference | Excluded: source progress tracker only; branch-local reference updates are tracked separately | — | — |
| `782f3c5e` Bump Fishtastic to 2.1.4 | Ported | `b6e6fca8` | `dc749269` |
| `83fd745d` Place structure cosmetics on any grid cell that fits | Ported | `9481dc9b` | `edf20f2c` |
| `933214c5` Aim floor cosmetics at the sand so kelp can't hide cells behind it | Ported | `f9097e39` | `d44d7ee0` |
| `cd43f8cf` docs: prune completed feature artifacts | Ported | `8d721dbb` | `dc24388f` |
| `2b1ed674` Prune stale documentation and consolidate development tooling | Ported | `7b61f058` | `10f82391` |
| `ea4ec7da` Refresh fishing minigame test rationale | Ported | `1120f02a` | `81044ce2` |
| `e2618bb0` Add daily free quest shop reroll | Ported | `7312aafa` | `eea36ef8` |
| `7fa116b6` Bump Fishtastic to 2.1.5 | Ported | `b586f5b6` | `fe7dc985` |
| `c20dac1a` Add coelacanth | Ported | `c0f8415e` | `8ba9199d` |
| `3cde87e4` Bump Fishtastic to 2.1.6 | Ported | `801b1004` | `704dbfee` |
| `3834ead3` Publish Forge jars from publishCurseForge | Ported | `ba12b1a3` | `e529cd35` |
| `71214244` Add release-environment smoke test for CurseForge profiles | Excluded: source-only release-smoke tooling | — | — |
| `fc17047b` Record coelacanth backport and release follow-ups in parity progress | Excluded: source progress tracker only | — | — |
| `a31a9298` Add interactive fish schools | Ported | `08e5042d` | `e9a0fab0` |
| `1e4945bf` docs(backport): record interactive fish schools catch-up | Excluded: source progress tracker only | — | — |
| `c311019d` fix(release-smoke): ignore optional mixin probes | Excluded: source-only release-smoke tooling | — | — |
| `e21bc4a3` Polish fish school bites; colour trails by quality | Ported | `d8a09e1e` | `aaab94d3` |

The following target-local documentation commits are also present after the previous markers:
1.21.1 `a677837b` / 1.20.1 `00fbc8f5` refresh their source-commit reference; 1.21.1 `62b2b838` /
1.20.1 `77c0f5fd` update the supported-loader matrix. They do not represent additional source
feature commits.

## Post-marker follow-ups: `e21bc4a3..8794a541`

Updated: 2026-10-27. These source commits landed after the markers and were carried as scoped
follow-ups. The markers stay at `e21bc4a3`. Every commit in the range is accounted for, so the
markers can advance to `8794a541` without further porting. Per-follow-up validation is in
`docs/backport-catchup-progress.md`.

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `626cb00f` docs(backport): reconcile catch-up ledger through e21bc4a3 | Excluded: source progress tracker only | — | — |
| `074659af` Bump Fishtastic to 2.1.7 | Ported | `341e6453` | `73fe6fe6` |
| `ef84f32d` docs(release): record Fishtastic 2.1.7 | Excluded: source release notes only | — | — |
| `056bec03` Balance interactive fish school encounter odds | Ported | `f30329a5` | `e1788e7f` |
| `30c8549b` docs(backport): record school odds commits | Excluded: source progress tracker only | — | — |
| `ed3dbfd6` Open interactive in-world visits to most species with authored shapes | Ported | `5ffca717` | `bbfe1fed` |
| `99325a8f` Prepare Fishtastic 2.1.9 release | Ported | `783dec27` | `9fbc8479` |
| `603ade5a` Let wild school fish leap out of the water now and then | Ported | `785ba2c8` | `7e19cfa4` |
| `cdad5be3` Bump Fishtastic to 2.1.10 | Ported | `6b9dbfb2` | `6735c5f1` |
| `091e2c58` Make school visits always succeed and surface jumps come every 30-60s | Ported | `88a6ce2f` | `82c3da88` |
| `21eadae1` Bump Fishtastic to 2.1.11 | Ported | `7bb45c3b` | `eafc4d16` |
| `8794a541` Add hellbender | Ported (26.1-only `items/hellbender.json` dropped) | `eba88004` | `1e7802ad` |

## Current validation evidence

- 1.21.1 head `d8a09e1e`: common 878 tests (0 failures, 1 skipped), fishsim 516 passed/59
  skipped, all three loader main/testmod compiles, and Fabric/NeoForge/Forge GameTests 383/383.
- 1.20.1 head `aaab94d3`: common 891 tests (0 failures, 1 skipped), fishsim 516 passed/59
  skipped, Fabric/Forge main/testmod compiles, and Fabric/Forge GameTests 383/383 and 384/384.
- The 1.20.1 mixed-quality school render passed all checks with zero replica mismatches and 72
  real-renderer frames. Logs and capture paths are in the linked result matrix.
- The pass-two checklist history records earlier validations for the pinned backlog and intermediate
  catch-ups. Manual owner checks remain open; they are not treated as passed by these automated
  gates.

The markers were recorded by 1.21.1 commit `0c35f75c` and 1.20.1 commit `861c251f`. Follow-up
checklist-link commits are 1.21.1 `bce3788c` and 1.20.1 `1ba2391b`; both hooks passed their unit
and Fabric GameTest stages. Both target worktrees are clean of tracked changes. The pre-existing
untracked `docs/fish-companions-twoclient/` is preserved on 1.20.1. No branch was pushed.

## Post-marker follow-up: `8794a541..73560a7e`

Updated: 2026-10-29. The nine source commits after `8794a541` are all accounted for. Four product
commits were adapted together on each port; the 2.1.12 version bump was already present in the
clean target baselines. Source progress and release-evidence commits are excluded by design. Both
target markers now advance to `73560a7e`; the automated result matrix is
[`2026-10-29/post-hellbender-parity/result-matrix.md`](../../build/backport-catchup/2026-10-29/post-hellbender-parity/result-matrix.md).

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `d0627f7a` Bump Fishtastic to 2.1.12 | Ported before this follow-up; present at target baselines | `13c270d5` | `df1e5b4a` |
| `551c131a` Record hellbender and 2.1.9-2.1.11 follow-ups | Excluded: source progress tracker | — | — |
| `94bf3a3a` Show the hellbender's sheen texture out of water | Ported | `340f76d0` | `02702a4b` |
| `55fecf75` Hinge ground walkers' mood pitch at the tail | Ported | `340f76d0` | `02702a4b` |
| `04dacb24` Record wet/dry texture commit | Excluded: source progress tracker | — | — |
| `a13eb827` Keep benthics on the ground when their owner looks down | Ported | `340f76d0` | `02702a4b` |
| `b9586bcf` Record 2.1.12 release bump | Excluded: source progress tracker | — | — |
| `f7d34c5f` Add legendary fish breach celebrations | Ported | `340f76d0` | `02702a4b` |
| `73560a7e` Record published 2.1.11 backport jars | Excluded: source release evidence only | — | — |

Validation: 1.21.1 common 926 tests (0 failures/errors, 1 skipped), fishsim 516 passed/63 skipped,
all three loader builds and testmod compiles, all three GameTest suites 389/389, datagen unchanged,
and the `legendarybreach` scene 10/10. 1.20.1 common 939 tests (0 failures/errors, 1 skipped),
fishsim 516 passed/63 skipped, tank-shape-gen 21,955, Fabric/Forge builds and testmod compiles,
GameTests 389/389 and 390/390, datagen unchanged, and `legendarybreach` 10/10. The first 1.21.1
render capture found that legacy GUI blits did not enable alpha blending; the explicit blend fix
was visually confirmed in final captures on both targets. See the result matrix for evidence paths.

Checklist marker commits: 1.21.1 `5e825fe6`; 1.20.1 `775d776d`. Owner-only real-play,
multiplayer, and tank-frame equivalence checks remain open; they are not claimed as passed by the
automated matrix.
