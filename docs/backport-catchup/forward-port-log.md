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
