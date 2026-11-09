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

## Post-marker follow-up: `8794a541..00df5ff9`

Updated: 2026-10-29. The nine source commits after `8794a541` are all accounted for. Four product
commits were adapted together on each port; the 2.1.12 version bump was already present in the
clean target baselines. Source progress and release-evidence commits are excluded by design. Both
target markers now advance to `00df5ff9`; the automated result matrix is
[`2026-10-29/post-hellbender-parity/result-matrix.md`](../../build/backport-catchup/2026-10-29/post-hellbender-parity/result-matrix.md).

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `402892eb` Bump Fishtastic to 2.1.12 | Ported before this follow-up; present at target baselines | `164a2954` | `eceec535` |
| `551c131a` Record hellbender and 2.1.9-2.1.11 follow-ups | Excluded: source progress tracker | — | — |
| `328e0d2d` Show the hellbender's sheen texture out of water | Ported | `50899c1e` | `55087776` |
| `aac4f749` Hinge ground walkers' mood pitch at the tail | Ported | `50899c1e` | `55087776` |
| `7f1a4e93` Record wet/dry texture commit | Excluded: source progress tracker | — | — |
| `ae41c11b` Keep benthics on the ground when their owner looks down | Ported | `50899c1e` | `55087776` |
| `766d8227` Record 2.1.12 release bump | Excluded: source progress tracker | — | — |
| `636b2eb1` Add legendary fish breach celebrations | Ported | `50899c1e` | `55087776` |
| `00df5ff9` Record published 2.1.11 backport jars | Excluded: source release evidence only | — | — |

Validation: 1.21.1 common 926 tests (0 failures/errors, 1 skipped), fishsim 516 passed/63 skipped,
all three loader builds and testmod compiles, all three GameTest suites 389/389, datagen unchanged,
and the `legendarybreach` scene 10/10. 1.20.1 common 939 tests (0 failures/errors, 1 skipped),
fishsim 516 passed/63 skipped, tank-shape-gen 21,955, Fabric/Forge builds and testmod compiles,
GameTests 389/389 and 390/390, datagen unchanged, and `legendarybreach` 10/10. The first 1.21.1
render capture found that legacy GUI blits did not enable alpha blending; the explicit blend fix
was visually confirmed in final captures on both targets. See the result matrix for evidence paths.

Checklist marker commits: 1.21.1 `a5c88846`; 1.20.1 `13dec3b1`. Owner-only real-play,
multiplayer, and tank-frame equivalence checks remain open; they are not claimed as passed by the
automated matrix.

## Post-marker follow-up: `00df5ff9..5bf9c6fc`

Updated: 2026-10-31. Every source commit after `00df5ff9` through `5bf9c6fc` is accounted for.
The two product changes in this range are adapted and committed on both targets. Feature commits:
1.21.1 `361eefe79e9710c656c5ba55feaf6d9091c85f2c`; 1.20.1
`51f1573d47c0e0097ebd954f704c9ba9c44595fe`. Checklist marker commits: 1.21.1
`193067207ca072e828a13052ed4bef2377639097`; 1.20.1 `d6ed5a7f799246ef8fe618a017417a3196eee276`.
Both markers now read `5bf9c6fc`.

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `860887a0` Record 2026-10-29 backport parity catch-up | Excluded: source progress/ledger documentation only | — | — |
| `c23199d5` Fix legendary breach glow and ray transparency | Present at target baselines before this follow-up | Present | Present |
| `4d1b8b8e` Bump Fishtastic to 2.1.13 | Present at target baselines before this follow-up | Present | Present |
| `49bd1201` Refresh source commit reference | Excluded: documentation/reference maintenance; targets retain local source references | — | — |
| `afaa10f0` Add world fish exploration: windowed visits, seeded ambient fish and spyglass observation | Ported in target-adapted feature commit | `361eefe7` | `51f1573d` |
| `22fc4d73` Record 2.1.12 release follow-up | Excluded: source progress tracker only | — | — |
| `5bf9c6fc` Make school trail motes obvious in vanilla | Ported in target-adapted feature commit | `361eefe7` | `51f1573d` |

Validation: 1.21.1 common tests, all three loader compiles, all three testmod compiles, and all
three GameTest suites passed; the final three-loader compile after the rendering adaptation passed.
Fabric datagen passed with 5,515 entries unchanged, 101 authored entries preserved, zero copied,
and no generated-resource diff.
1.20.1 common tests, Fabric/Forge compiles, both testmod compiles, both GameTest suites, and the
final common/loader compile passed. Fabric datagen on 1.20.1 passed with zero copied files. Forge
`runData` reported zero providers/files, and the generated-output diff is empty across Forge and
common generated-resource paths, passing the documented G-B4 no-op expectation. Its JVM remained
alive on parked executor threads after data generation and was stopped after capturing the thread
dump; clean process exit is unverified due to the known FML shutdown hang. The run log and dump are under
`build/backport-catchup/2026-10-30/world-fish-exploration/1.20.1-exploration/`. No generated-resource
changes were found. The target feature commits and separate marker commits are recorded above. No
push was made.
`git diff HEAD --check` passes on both target worktrees. No target-side client visual review or
two-client owner playtest was run; those owner checks remain open in the feature handoff.

## Catch-up 2026-11-03 (`5bf9c6fc..ab9d675f`)

Both markers now read `ab9d675f` (marker commits 1.21.1 `16f537aa`, 1.20.1 `db220af6`).

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `accae9b8` Record committed backport parity through 5bf9c6fc | Excluded: source docs only | — | — |
| `d5b06e30` Record final backport catch-up checkpoint | Excluded: source docs only | — | — |
| `b6595f24` Cylindrical bite reach, bump to 2.1.14 | Present at target baselines | `2250cef8` | `f722c5f2` |
| `469c9a92` Fix Obsidian rod gear detection | Present at target baselines | `208269bb` | `3a314d7a` |
| `e45cb395` Bump Fishtastic to 2.1.15 | Present at target baselines | `be9a89ca` | `f18e72fe` |
| `242e37ae` Record 2.1.15 release validation checkpoint | Excluded: source docs only | — | — |
| `1aa8595d` Record 2.1.15 CurseForge publication | Excluded: source docs only | — | — |
| `532b266c` Bump Fishtastic to 2.1.16 | Present at target baselines | `7556a7dd` | `d98a37f4` |
| `b00530f7` Fix water-edit visit GameTest racing the level clock | Present at target baselines | `5f2661d0` | `6f5292df` |
| `d1a3150c` Record 2.1.16 CurseForge release | Excluded: source docs only | — | — |
| `eeef4183` Fill ambient fish ahead of a travelling player | Ported in target-adapted feature commit | `1cd1990e` | `523daecb` |
| `d2e3bb94` Carry ambient fish across water patch merges | Ported in target-adapted feature commit | `1cd1990e` | `523daecb` |
| `9137491e` Give fish species cruise speeds in open water and tanks | Ported in target-adapted feature commit | `1cd1990e` | `523daecb` |
| `3bd9f653` Spawn ambient fish in species groups | Ported in target-adapted feature commit | `1cd1990e` | `523daecb` |
| `ab9d675f` Bump Fishtastic to 2.1.17 | Ported (cherry-pick) | `f1d8e0b8` | `8a2c7da3` |

Validation and evidence: `docs/backport-catchup-progress.md` (2026-11-03 entry),
`build/backport-catchup/2026-11-03/`.

## Catch-up 2026-11-03 PM (`ab9d675f..6ad0be04`)

Both markers now read `6ad0be04` (marker commits 1.21.1 `2ffdb071`, 1.20.1 `e9d303ec`).

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `169323be` Record 2.1.17 CurseForge release | Excluded: source docs only | — | — |
| `098d5d2a` Keep dealing a carried fish pile into the same slot | Ported | `fee03791` | `e183defd` |
| `a3f9713e` Show player heads on the Cleanup page and raise its payout to 250 | Ported | `d11ce974` | `2480fb93` |
| `c5dad8b6` Cover turning marine compost with an item in hand | Ported; 1.20.1 adapted into a fix (test was red there) | `31a83d86` | `8f0a4687` |
| `6efdde0f` Make the fish tank assembly an axe block | Ported | `02ba4647` | `a1e8471f` |
| `3106d794` Reach the real surface in a deep visit's water | Ported | `43662fcc` | `2a20770c` |
| `07828200` Climb to the bobber at full speed on a school errand | Ported | `1d77740c` | `f3bf7ee2` |
| `15b870f8` Cast the deep-visit GameTest straight over a fish | Ported (identical) | `d3208938` | `ca61d3e4` |
| `13c3d794` Give every Fishtastic book when the fishing tutorial begins | Ported | `7e54b03a` | `4d917bcf` |
| `715c16f1` Bump Fishtastic to 2.1.18 | Ported | `70549e12` | `12c1fc4b` |
| `1a3194e7` Keep unlisted fish out of the ambient roster | Ported | `261b1873` | `f3d0e0e0` |
| `dc982089` Bump Fishtastic to 2.1.19 | Ported | `7d0bd45a` | `ccbbc2dc` |
| `87919639` Silhouette undiscovered fish and name them ??? in every GUI | Ported | `e73ed16e` | `6a94fd46` |
| `4cb9572a` Let the undiscovered-fish tooltip trim replace getTooltipLines' result | Ported | `4e83e88f` | `fb01c8a9` |
| `b2eb2d7f` Group Blazed Grub and Gummy Worms with the other baits in the creative tab | Ported | `3c9f8fa0` | `f189d7e9` |
| `6ad0be04` Bump Fishtastic to 2.1.20 | Ported | `cc8a7a59` | `9c2e878f` |

Validation and evidence: `docs/backport-catchup-progress.md` (2026-11-03 `6ad0be04` entry).

## Catch-up 2026-11-03 evening (`6ad0be04..25648221`)

Both markers now read `25648221` (marker commits 1.21.1 `a0349bb6`, 1.20.1 `1c3adcf6`).

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `1fd903e1` Record backport parity through 6ad0be04 | Excluded: source docs only | — | — |
| `25648221` Make vanilla flowers, shrubs and plants placeable as tank cosmetics | Ported, adapted (version block lists) | `67259cfb` | `9dd1e220` |

Validation and evidence: `docs/backport-catchup-progress.md` (2026-11-03 `25648221` entry).

## Companions screen port 2026-11-09 (`c357151f..b5c933cc`)

Markers unmoved (still `25648221`); the commits between it and `c357151f` were ported as
recorded in `docs/backport-catchup-progress.md` but not re-audited here.

| 26.1.2 source commit | Disposition | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `c357151f` Add an animated showcase-card overlay tool for mod videos | Excluded: version-independent video tooling | — | — |
| `42095b5b` Resolve plain borderless and clear glass tank textures on Forge | Not applicable: the ports' shared `FishTankGeometry` bake already fills the blockstate redirects on every loader | — | — |
| `73bc2c5c` Replace the companion container with a Captain's Log companions screen | Ported, adapted (one commit with the next two) | `5056a334` | `5eb9677a` |
| `0a7405e8` Make the companions screen scrollbars draggable | Ported, adapted (Gelatin 1.0.40 per branch) | `5056a334` | `5eb9677a` |
| `b5c933cc` Add keyboard navigation and narration to the companions screen | Ported, adapted | `5056a334` | `5eb9677a` |

Adaptations: no picture-in-picture renderer on either port, so card portraits draw the live fish
directly into the GUI with a 3D pose (`CompanionPortrait`); GuiGraphics/PoseStack and int-based
input signatures; `GameNarrator.sayNow`; the scene's vanilla-narration and game-narrator capture
checks are dropped (the ports never carried those helpers); the port `companiontutorial` scene now
matches the source's help-menu version. 1.20.1 additionally uses its `FishtasticPayload`/`BufCodec`
seam for the new packets, `withStyle` colours, `SharedConstants` char filtering and
`isSameItemSameTags`.

Validation: 1.21.1 game tests 410/410 on Fabric, Forge and NeoForge; 1.20.1 411/411 Fabric and
412/412 Forge. `companionbond` + `companiontutorial` scenes: 40/40 checks on Fabric for both
ports, screenshots reviewed (evidence under each worktree's `build/verification/two-client/fabric/`).
Not covered: scenes on the Forge/NeoForge clients and an owner look in a live game.

