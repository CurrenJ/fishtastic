# Forge ports — handoff from the 2026-10-19 session

**Why this file exists.** The original chat (`01a1554a-fb09-7263-85df-4229cbe132bc`)
ran on `gpt-6.1-sol` with ChatGPT sign-in. `~/.codex/config.toml` was later swapped to
`deepseek-flash` under the `deepseek` provider, so reopening that thread now fails with

```
400 ... The 'deepseek-flash' model is not supported when using Codex with a ChatGPT account.
```

The transcript is intact at
`C:/Users/curre/.codex/sessions/2026/10/19/rollout-2026-10-19T18-52-19-01a1554a-fb09-7263-85df-4229cbe132bc.jsonl`,
but it cannot execute under the current provider. This document carries the working state
into a fresh thread.

**Read first:** `docs/forge-support-progress.md` (the last section is the live pending
operation), `docs/forge-support-implementation-plan.md`, then this file. Per `AGENTS.md`,
this is Forge-support work, not backport parity work: do not touch `docs/backport-catchup*`
or advance backport markers.

## Checkouts and branches

Verified at handoff time (2026-10-19, ~21:55 local):

| Repo | Path | Branch | Tip | State |
|---|---|---|---|---|
| Fishtastic 26.1.2 | `D:/GitHub/fishtastic` | `codex/forge-26.1.2` | `b20a7eaf` | dirty, 11 paths |
| Fishtastic 1.21.1 | `D:/GitHub/fishtastic-worktrees/mc-1.21.1` | `codex/forge-1.21.1` | `7ab5bbf7` | dirty, 4 files |
| Fishtastic 1.20.1 | `D:/GitHub/fishtastic-worktrees/mc-1.20.1` | `port/1.20.1` | `fba740af` | 1 file dirty |
| Gelatin 26.1.2 | `D:/GitHub/gelatin-ui` | `codex/forge-26.1.2` | `0caed87` | clean |
| Gelatin 1.21.1 | `D:/GitHub/gelatin-ui-worktrees/mc-1.21.1` | `codex/forge-1.21.1` | `93f2e99` | clean |
| Gelatin 1.20.1 | `D:/GitHub/gelatin-ui-worktrees/mc-1.20.1` | `mc/1.20.1` | `3bb6397` | clean |
| Squid candidate | `C:/Users/curre/.codex/worktrees/companion-squid/fishtastic` | `codex/companion-squid-jet-candidate` | `450689a1` | untouched, owner acceptance pending |

All active Fishtastic branches are at version **2.1.1**; all active Gelatin branches are at
**1.0.38**. Every branch above is level with its `origin` counterpart unless listed as dirty.

## Finished earlier in that session

- Committed and pushed the four Forge port branches:
  Fishtastic `643411a4` (26.1.2) and `f3635876` (1.21.1);
  Gelatin `8b9d4ed` (26.1.2) and `8c3ccaf` (1.21.1).
- Pushed Gelatin's previously unpublished base branches `mc/1.21.1` and `mc/1.20.1`.
- Aligned versions to Fishtastic 2.1.1 / Gelatin 1.0.38 on 26.1.2, 1.21.1 and 1.20.1.
- Fixed the reported Forge rendering bugs and verified them with the owner in-game:
  wrong world transform for ambient fish, stepped companion interpolation, and celebration
  rays writing depth over notification banners (1.21.1), plus the banner fix on 1.20.1.
  Fixed jars were redeployed to the CurseForge test profiles.
- Published Gelatin UI 1.0.38 (eight loader/version uploads) and synced the matching jars
  into all 14 CurseForge profiles that use Gelatin UI, backing up the replaced jars;
  release record: `D:/GitHub/gelatin-ui/docs/releases/1.0.38.md`.
- Published Fishtastic 2.1.1 Forge: 26.1.2 = CurseForge file `9260058`,
  1.21.1 = file `9260060`.
- Worktree cleanup: 15 agent experiments preserved as 17 signed recovery snapshots,
  16 obsolete checkouts removed, all 31 pre-existing stashes untouched. Details:
  `docs/worktree-cleanup-2026-10-19.md`. Do not delete or rewrite the recovery branches.
- Untracked three ignored files on all three active Gelatin branches through normal commits.

## The in-flight task

Owner request: *"Fix all these issues? Fix and validate the fixes. Make sure that these
issues do not exist on any of the other Fishtastic ports or supported modloaders."*

Six findings came from the review of PR
[#7 Forge 1.21.1 Port](https://github.com/CurrenJ/fishtastic/pull/7) and
[#8 Forge 26.1.2 Port](https://github.com/CurrenJ/fishtastic/pull/8).

| # | Finding | Implementation now in the working tree | State |
|---|---|---|---|
| 1 | 26.1.2 model-data refresh hook was empty, so material/shape/cosmetic edits could stay stale | `ForgeRegistrationApi.requestModelDataUpdate` now calls the BE hook and marks the block dirty via `levelRenderer.setBlocksDirty` | Probe passes; **world rendering still stale — see blocker 1** |
| 2 | Returning player's persisted companion party was not sent to already-connected observers | New `CompanionPartySyncPacket.onPlayerJoin` broadcasts the joining player's party; both Forge login hooks call it | Compiles; **runtime regression not run** |
| 3 | 26.1.2 nonstandard tanks always rendered standard geometry, and corner/edge fragments were omitted | New `FishTankModelResources` resolves every shape × 64 permutations plus corner/edge fragments; `FishTankBakedModel` composites them | Direct probes pass; world render not confirmed |
| 4 | Baked-model cache key ignored cosmetic contents, so tanks reused the first decoration mesh | Cosmetics are baked per `ModelData` outside the body cache; `TankCosmeticMesh` cache is cleared on re-bake | Probes pass |
| 5 | Tinted cosmetic quads were discarded, so leaves/ferns lost their pieces | Tinted quads are retained and re-issued with tint indices from the new `FishTankCosmeticTints`; block colour handlers are registered in `FishtasticForgeClient` | Probes pass (foliage retained, biome colour matches) |
| 6 | 1.21.1 handled packets fell through to vanilla, producing "Unknown custom packet payload" warnings | `ForgePacketRegistrar` calls `context.setPacketHandled(true)` in a `finally` block | Compiles, 372/372 GameTests pass; **client-warning check not re-run** |

Cross-loader audit so far: 1.20.1 already broadcasts a joining player's party and already
marks packets handled; Fabric/NeoForge use tracking-aware companion attachments. That audit
was source inspection only — no Fabric/NeoForge runtime runs were done for this task.

## Uncommitted changes (leave in place; they are the work)

Fishtastic 26.1.2 (`D:/GitHub/fishtastic`), 8 modified + 3 untracked, +458/−129:

- `common/.../client/selftest/RenderSelfTest.java` — adds the `forgereview` scene and
  `partyseed` / `partyobserver` / `partyreturn` scenes.
- `common/.../network/CompanionPartySyncPacket.java` — adds `onPlayerJoin`.
- `docs/forge-support-progress.md` — records the pending operation.
- `forge/.../architectury/forge/ForgeRegistrationApi.java` — model-data refresh.
- `forge/.../forge/FishtasticForge.java` — login hook now calls `onPlayerJoin`.
- `forge/.../forge/FishtasticForgeClient.java` — tint-source registration and
  `FishTankModelResources.bake`.
- `forge/.../forge/fishtank/FishTankBakedModel.java` — shape/fragment composition,
  cache-key and tinted-cosmetic rewrite.
- `forge/.../forge/fishtank/FishTankItemModel.java` — shared dependency resolution.
- New: `forge/.../client/selftest/ForgeReviewChecks.java`,
  `forge/.../fishtank/FishTankCosmeticTints.java`,
  `forge/.../fishtank/FishTankModelResources.java`.

Fishtastic 1.21.1 (`D:/GitHub/fishtastic-worktrees/mc-1.21.1`), +39/−4:

- `common/.../client/selftest/RenderSelfTest.java` — party regression scenes.
- `common/.../network/CompanionPartySyncPacket.java` — `onPlayerJoin`.
- `forge/.../architectury/forge/ForgePacketRegistrar.java` — `setPacketHandled(true)`.
- `forge/.../forge/FishtasticForge.java` — login hook.

Fishtastic 1.20.1 (`D:/GitHub/fishtastic-worktrees/mc-1.20.1`), +21/−1:

- `common/.../client/selftest/RenderSelfTest.java` — party regression scenes only; no
  production change was needed there.

## Evidence from the interrupted run

All under `D:/GitHub/fishtastic/build/forge-pr-fixes/`:

| File | What it shows |
|---|---|
| `26.1.2-compile.log`, `26.1.2-probes-compile-2.log` | Production and probe compile succeeded (the first probe compile failed on a signature, then passed). |
| `26.1.2-render-final.log` | `forgereview` client run. All 12 phase-1 checks (live data, geometry, fragments, cosmetics, tints) and all 3 post-edit checks pass. Still contains the failing visual result below. |
| `first-render-run/selftest-forge-forgereview-edited_vitrine.png` | After editing the tank to `VITRINE` with a diamond frame, the world still shows a **standard oak tank**. This is the open rendering bug. |
| `1.21.1-tests-build.log` | `:common:test`, `:fabric:build`, `:neoforge:build`, `:forge:build` — BUILD SUCCESSFUL. |
| `1.20.1-tests-build.log` | `:common:test`, `:fabric:build`, `:forge:build` — BUILD SUCCESSFUL. |
| `1.21.1-gametests.log` | `372 GAME TESTS COMPLETE`; all 372 required tests passed. |
| `operation.md` | Exact `runClient` invocation for the 26.1.2 review scene. |
| `26.1.2-model-path.log` | Copy of the review-scene run used while tracing the chunk path. |

The `forgereview` command recorded in `operation.md` is:

```
./gradlew.bat :forge:runClient -I docs/fish-companions-twoclient/twoclient.init.gradle \
  -PtwoLoader=forge -PtwoRole=solo "-PtwoScenes=forgereview,shapes" --console=plain
```

## Open blockers and next steps

1. **Stale world rendering on Forge 26.1.2 (highest priority).**
   Direct `collectParts(..., ModelData)` calls produce correct shape/fragment meshes, and
   `ModelDataManager.getAtOrEmpty(pos)` returns the edited shape and materials. The chunk
   mesh nevertheless stays on the old model. Evidence points at invalidation, not geometry:
   the selftest edits the tank on the **server**, so the client-side branch of
   `requestModelDataUpdate` never runs; the client BE receives fields through
   `loadAdditional` (which sets fields directly and does not request a model-data update or
   mark the chunk dirty). Check, in order:
   - whether the chunk mesher calls `collectParts` with empty `ModelData` (temporary log);
   - whether the client BE sync path needs `requestModelDataUpdate()` in
     `FishTankBlockEntityForge#loadAdditional` / `onDataPacket`;
   - whether `setBlocksDirty` runs on the render thread after the sync.
   Acceptance: rerun `forgereview`; the screenshot must show the vitrine shape with the
   diamond frame. A probe pass alone is not acceptance.
2. **Party login regression is unverified.** The three scenes compile but were never run.
   They need a real two-client session (seed the party, join an observer while the owner is
   offline, then bring the owner back). Repeat per loader/version as applicable.
3. **Re-check the 1.21.1 packet warning.** Confirm the "Unknown custom packet payload"
   warnings are gone in a client log during quest/tutorial/companion sync traffic.
4. **26.1.2 full test matrix is outstanding.** Only compile + client scene ran after the
   final edits. Run `:common:test`, `:fishsim:test`, `:tools:tank-shape-gen:test`,
   `:fabric:assemble`, `:neoforge:assemble`, `:forge:build`, and the Forge GameTests.
5. **Cross-loader confirmation remains source-level.** If the owner wants runtime proof,
   run the equivalent tank/party scenes on Fabric and NeoForge for 26.1.2 and 1.21.1.
6. **Known scene noise, probably pre-existing.** The `shapes` scene logged 301
   `could not resolve texture(s) for frame=minecraft:waxed_copper_block` warnings and fell
   back to the default model. The texture helper is unchanged from `HEAD`, so treat this as
   a pre-existing scene/material limitation until proven otherwise — do not conflate it
   with blocker 1.
7. **CI diagnostics were unavailable** because the GitHub review connector is not
   connected. PR states above were observed from the working tree, not from CI.

## Guardrails for the next thread

- Before starting any operation, record it (command + evidence path) in
  `docs/forge-support-progress.md`, and save the result at each checkpoint.
- Treat interrupted runs as unverified. Do not advance any F00–F11 / G01–G03 gate or
  backport marker from this handoff.
- Keep the recovery snapshots, the recovery bundle under Git metadata, and all stashes.
- Commit per branch (26.1.2, 1.21.1, 1.20.1) and keep Gelatin out unless its code changes.
- The Codex config is currently swapped to DeepSeek. Do not attempt to resume the original
  OpenAI thread while that swap is active; use this file instead.

## Suggested opening prompt

> Read `docs/forge-ports-2026-10-19-handoff.md` and continue at "Open blockers and next
> steps" item 1. The working tree already contains the six PR-review fixes; diagnose why the
> Forge 26.1.2 chunk mesh keeps the old tank model, fix it, and verify with the `forgereview`
> screenshot before running the rest of the matrix.
