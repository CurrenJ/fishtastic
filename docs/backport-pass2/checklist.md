# Backport checklist

> The tracked ledger for the 1.21.1 and 1.20.1 backports, following the potions-plus parity ledger's pattern. **Update it in the same commit as the work.** Plan: [`../backport-plan.md`](../backport-plan.md) (pass 1), [`README.md`](README.md) (pass 2).
> Status: `[ ]` not started · `[~]` in progress · `[x]` done · `[-]` dropped (with reason). The commit column holds the commit that finished the item, on the branch in the section header.

## Ported-through markers

Every 26.1.2 commit up to and including the marker is present on the branch, either ported or deliberately skipped (listed in the forward-port log). To bring a branch up to date: `git log --oneline <marker>..26.1.2`, port each commit, then move the marker.

| Branch | Ported through (`26.1.2` commit) | Updated | State |
|---|---|---|---|
| `port/1.21.1` | **`4b0e818e`** | 2026-10-06 | Pass 2 written; rebased onto the S5/S6/S6c seams. A1–A5 done; **G1 passed 2026-09-25**, **A6.1/A6.2 green 2026-09-25** (263/263 gametests both loaders, hook stage `full`), **A6.3 playtest found and fixed 2 defects** (missing vanilla sprite; GUI depth vs paint order) — both confirmed in-game 2026-09-25. **G2 done 2026-09-25** (`port/excludes.txt` + portstub scaffolding removed; `port/1.20.1` cut). **A7 done 2026-09-27**: `2.0.1+1.21.1` published as a release-type CurseForge file (fabric + neoforge, tagged `Java 21`); then advanced past `1d88b8e3` (FishTankFrameType removal, ported as `6c1ababa`) — `c656ea44` is a 26.1.2-only build fix (skipped). **Marker advanced to `76986ade` 2026-10-01** (forward-port pass, tip `6ee52aae`): the 17 commits `faf47828`..`76986ade` are ported (rows below); the merge `05565f7b` that follows on 26.1.2 is content-identical to `76986ade` (branch housekeeping only). Green bar on the tip: `:common:test` 87/87, `:fabric:runGametest` 282/282, `:neoforge:runGametest` 282/282, `:fabric:runDatagen` left `git status --porcelain` empty, and both `runServer` smokes reached `Done`. `:neoforge:runData` cannot run on this branch — a pre-existing failure of the `data` run config (`Could not find Forge run template with name 'dataServer'`, identical on a rerun; the block's `serverData()` workaround targets the 26.1.2 line's Loom 1.14.476, this branch is on Loom 1.17, and no commit in this pass touches run configuration). Datagen here is Fabric-owned: `:fabric:runDatagen` ends with `copyGeneratedAssetsToCommon`, and `neoforge/src/main/generated` does not exist. **Owner decision 2026-10-02: accepted as-is — Fabric drives datagen on this line; no fix planned.** Owner look still owed on: the tall/lava bar target art, tank cosmetics in a real world, and the NeoForge interior-light floor being block-light-only. **Marker advanced to `4b0e818e` 2026-10-06** (the 27 commits `aef3b9d0`..`4b0e818e`, rows below; `573cddfa` skipped): `:common:test` 725, `:fishsim:test` 283, `:tools:tank-shape-gen:test` 21955, 0 failures; `:fabric:runGametest` and `:neoforge:runGametest` 282/282; `:fabric:runDatagen` clean; both `runServer` smokes `Done`; render self-test on both loaders (13 default scenes + spanpreview, bakebench over all 55 structures, cosmeticpreview over the 24 new cosmetics incl. reactions held open) 275/276, the one failure a harness fault fixed in `ebf2974d` and re-verified. |
| `port/1.20.1` | **`4b0e818e`** | 2026-10-06 | Cut from `port/1.21.1` at G2 (`911495f7`), worktree `D:\GitHub\fishtastic-worktrees\mc-1.20.1`. **Track B essentially complete**: B1–B5 and B6.1 done (263/263 `:forge:runGametest` and `:fabric:runGametest` green; `port/excludes.txt` reached zero). B6.2's green bar + owner playtest done (bobber reel-in bug found and fixed); the `2.0.1+1.20.1` release was published 2026-09-27 as a release-type CurseForge file (fabric + forge, tagged `Java 17`); G3 (lockstep) remains. Includes `26.1.2` `7839f271` (inherited) and `1d88b8e3` (FishTankFrameType removal, ported as `eded9f9e`); `c656ea44` is a 26.1.2-only build fix (skipped). **Marker advanced to `76986ade` 2026-10-01** (forward-port pass, tip `23b8016d`): the 17 commits `faf47828`..`76986ade` are ported (rows below) except `e0bb057e`, a documented skip (1.20.1 has no `TickRateManager` / `/tick freeze`); the merge `05565f7b` that follows on 26.1.2 is content-identical to `76986ade`. Green bar on the tip: `:common:cleanTest :common:test` 95/95, `:forge:runGametest` 282/282, `:fabric:runGametest` 282/282, `:fabric:runDatagen` left `git status --porcelain` empty, and both `runServer` smokes reached `Done` (Fabric 6.643s, Forge 6.293s). `:forge:runData` also runs to completion (its providers write 0 files — datagen here is Fabric-owned) then hangs at shutdown, the documented environment flake on this branch; killed, tree left clean. **Two 2026-10-02 drift-cleanup commits then closed the pass-2 three-way review**: `ead33322` fixed `drowned_pagoda.json`'s last part, still `minecraft:oxidized_lightning_rod`, an id 1.20.1 does not have (the part silently never became a block) — it is now `minecraft:lightning_rod`, byte-identical to `port/1.21.1`'s copy, and the `spanpreview` scene re-ran clean over it; `2647f61e` closed the remaining parity drift (`withBakedColor`, `TINTED_COSMETICS_PROPERTY`, `BlockEntityNbt.child`/`readChild` plus the nine `[FishTankBE.loadAdditional]` log tags, `pose`/import order, the renderer's snapshot/bench/span-share members made static, and the cosmetic mesh's packed normal zeroed as 1.21.1 does). The bar was re-run on the result with identical counts (95/95, 282/282 both loaders, datagen clean, both servers `Done`). **A third 2026-10-02 fix, `ff451aba`, is 1.20.1-only (API-forced):** the owner-reported opaque GUI highlight — this line's `ShaderInstance` force-applies the json's `blend` mode (absent = disabled) inside `BufferUploader.drawWithShader`, overriding each effect's `enableBlend()`, so all three alpha-ramp GUI programs (`gui_item_highlight`, `gui_item_silhouette`, `gui_texture_outline`) drew at full alpha; they now declare vanilla's translucent blend (the two bake passes verified as deliberate non-blend). Highlight verified before/after on both loaders, `outline` scene green on Fabric, 95/95; `port/1.21.1` must not mirror the blend blocks (its `ShaderInstance` never touches blend). Owner look still owed on: the tall/lava bar target art, the tank interior-light floor (including its block-light-only deviation from 26.1.2), the spanning tank cosmetics in a real world, and the now-blended GUI highlight falloff (`ff451aba`). **Marker advanced to `4b0e818e` 2026-10-06** (the 27 commits, rows below; `573cddfa` skipped): `:common:test` 733, `:fishsim:test` 283, `:tools:tank-shape-gen:test` 21955, 0 failures; `:fabric:runGametest` and `:forge:runGametest` 282/282; `:fabric:runDatagen` clean; both `runServer` smokes `Done`; render self-test on both loaders (13 default scenes + spanpreview + bakebench over all 55 structures) 55/55 checks. Owner look owed on the substituted Ziggurat/Galleon. |
| gelatin-ui `mc/1.21.1` | gelatin `26.1.2` @ **`5ae6aa4`** (1.0.31) | 2026-09-24 | **ported** (34 commits, tip `20c9f68`, clean). Currently published as `1.0.32+1.21.1` (its own independent bump for a later fix, see 2026-09-26 note below). |
| gelatin-ui `mc/1.20.1` | gelatin `26.1.2` @ **`5ae6aa4`** (1.0.31) | 2026-09-26 | **ported and active** (this row was stale — branch exists and is the most-published of the three). Currently `1.0.37+1.20.1`. |

**Rule:** a marker only moves forward, and only when the branch builds and its current gate passes. Once G3 is reached, D6 (lockstep) applies: a 26.1.2 release isn't cut until both markers equal its commit.

**2026-09-26 — gelatin-ui version audit.** Checked whether `1.0.31` (master) / `1.0.32` (`mc/1.21.1`) / `1.0.37` (`mc/1.20.1`) meant the port branches were feature-ahead of master. They aren't: diffing all three branches' commit *messages* (not hashes — each branch has independently-committed duplicates of the same changes, presumably cherry-picked rather than merged, so hashes differ but content doesn't) shows master has exactly one commit none of the others do ("Update to MC version 26.1.2" — master-specific, not portable). Every real feature/bugfix commit already exists on all three. The version numbers had simply been bumped independently, per-branch, whenever that branch needed its own mavenLocal publish — not from a shared lineage. **Decision (owner, 2026-09-26): going forward, bump `mod_version` on all three gelatin-ui branches together for every release**, even when a fix is platform-specific, so the number stays a trustworthy parity signal instead of noise. Same rule applies to fishtastic's own `mod_version`, which is already in sync at `2.0.1` across all three branches — keep it that way.

**2026-09-27 — release-candidate deploy + owner smoketest.** The release `2.0.1` builds for all three lines were rebuilt and deployed into their CurseForge profiles (from worktrees `mc-1.20.1`, `port/1.21.1` and `26.1.2`; `F2F Cam` deliberately excluded, pinned to `2.0.0`). gelatin-ui was moved to the unified `1.0.37` on every line in the same pass, so no profile now pairs a fishtastic with an older gelatin-ui than it was compiled against. **Owner ran a general smoketest on all three versions (both loaders each) the same day — all passed.** This is a smoketest, not a feature pass: A6.3 and B6.2 remain the per-feature playtest records. Cutting and publishing each release (`publishCurseForge`): `2.0.1+26.1.2` was already live before this pass; `2.0.1+1.21.1` and `2.0.1+1.20.1` were then published as release-type CurseForge files on 2026-09-27, each carrying a one-line changelog. **Worth knowing for next time:** the task prints *no upload confirmation* at default log level — a successful run shows only game-version slug bookkeeping plus `BUILD SUCCESSFUL`, so a quiet log is not a skipped upload.

## Forward-port log

26.1.2 commits after `3b8427e4` and what each branch did with them. Add a row per commit as it lands on 26.1.2.

| `26.1.2` commit | Summary | `port/1.21.1` | `port/1.20.1` |
|---|---|---|---|
| `cf643423` | S5: Java 21-only library calls replaced with Java 17 equivalents | included via rebase | inherits |
| `afa6d4dc` | S5 guard: `java17ApiGuard` bytecode check on common/fabric/neoforge | included via rebase | inherits |
| `616b6566` | S6: `FishMoonPhase` + `FishtasticPermissions.gamemaster()` | included via rebase | inherits |
| `f896635b` | Book recipes datagen-owned again (`runDatagen` clean) | included via rebase | inherits |
| `f096fc8d` | NeoForge gametest discovery fixed (Loom mod group named `main`; 257 tests run) | included via rebase | inherits |
| `24203a87` | S6c: resource id construction routed through `util/Ids` (135 calls, 43 files) | included via rebase | inherits |
| `33986055` | S6c guard: `idConstructionGuard`; guard script renamed to `gradle/backport-guards.gradle` | included via rebase | inherits |
| `7839f271` | Stale in-place quest banner and JEI zero-size gui properties (the two A4 findings) | `e33f5792` (cherry-pick) | inherits |
| `1d88b8e3` | Remove dead FishTankFrameType registry (superseded by open-ended FishTankMaterials block system) | `6c1ababa` (ported) | `eded9f9e` (ported) |
| `c656ea44` | Pin project-local Java 25 toolchain via `org.gradle.java.home` (26.1.2-only build fix) | skipped (build-only) | skipped (build-only) |
| `faf47828` | Move `deep_sea_bait` recipe into the datagen recipe provider | `609690ec` (ported) | `11e11cfe` (ported) |
| `cfddd15d` | Pin JSON to LF via `.gitattributes` to stop `runDatagen` line-ending churn | `ac94d60f` (ported) | `6ff971f6` (ported) |
| `5e26c375` | Remove the GitHub workflows; wire CurseForge tags and a `-Pbeta` pre-release arg | `b8d5b834` (ported) | `e33418d2` (ported) |
| `b345b33e` | Bump `gelatinui_version` to `1.0.37+26.1.2` to match unified gelatin-ui release | `c7b37f7a` (`1.0.37+1.21.1`) | `981e700f` (`1.0.37+1.20.1`) |
| `7576a7ed` | Render each group's fish from the tank they are in | `c74092e4` (ported) | `4ebde82b` (ported) |
| `31adba43` | Port the render self-test harness to 26.1.2, and add a per-tank group scene | `b8acb288` (ported) | `368efe1e` (ported) |
| `97a26b53` | Let the Fish Tank Assembly refit an existing tank's shape and materials | `74a09123` (ported) | `6e3f0443` (ported) |
| `75d961b3` | Highlight compatible items in inventories, and use it to guide the bait tutorial | `9178af23` (ported) | `a81487a3` (ported) |
| `17dde5de` | Give the legendary catch reveal a fuller celebration | `fcb09410` (ported) | `d4672c87` (ported) |
| `ef118b31` | Add discovery celebration polish, reduced effects, and a longer skippable hold | `e954315b` (ported) | `5c2a74a9` (ported) |
| `01da18c1` | Add selectable fishing bar and bobber styles | `569e3525` (ported) | `43eb1367` (ported) |
| `a86b349b` | Add column, hanging and spanning tank cosmetics, baked into the chunk mesh | `125bc550` (ported) | `b91c79df` (ported) |
| `0b46b99e` | Light fish tank interiors without lighting the room around them | `9bcaee30` (ported; NeoForge bakes a block-light-only floor rather than 26.1.2's sky-inclusive one — owner confirmation pending; Sodium/Iris untested) | `6c3f2934` (ported; both loaders bake a block-light-only floor, and the scene's `/tick freeze` framing is dropped — 1.20.1 has no such command) |
| `59df0b3d` | Give the tall fishing bar styles their own fish target art | `db4fc653` (ported as a GUI sprite: 1.21.1 has no `DataComponents.ITEM_MODEL` to swap) | `e69631e5` (ported as a GUI sprite via the port-only `IGuiGraphicsExtension#fishtastic$renderSprite`) |
| `e0bb057e` | Freeze tank flocks while the world is frozen | `1c7aeccb` (ported; freeze confirmed headless on both loaders) | skipped — 1.20.1 has no `TickRateManager`/`/tick freeze` (0 occurrences in the mappings) |
| `55ae36cf` | mcp-bridge: re-read .env on every bridge call | `eeac4ef8` (ported; repo tooling, not packaged in the mod) | `4f5d8db9` (ported; same reasoning) |
| `76986ade` | Add span structure preview renders | `f8632c72` (ported; the two PNGs are bit-identical) | `23b8016d` (ported; the two PNGs are bit-identical) |
| `aef3b9d0` | Make the tall fishing bar the default, pick styles per session | `7e02ca58` (ported; `undiscoveredSpecies` stays `ResourceLocation`) | `c9d70cd8` (ported; the new `barContext` field joins the `BufCodec`) |
| `e743a40a` | Add eight tank cosmetics built through the MCP session | `2102b339` (ported; `iron_chain` → `chain`; item models from datagen, no `items/` definitions) | `be7a4c2b` (ported; as 1.21.1) |
| `573cddfa` | Use 26.1 gamerule names in the self-test world setup | skipped — the camelCase rules are this line's names | skipped — as 1.21.1 |
| `5ba0c2a6` | Add the fish shelter spec (cosmetic idea #22) | `f7f2ec77` (ported verbatim) | `47a7c67c` (ported verbatim) |
| `3af027c1` | Add shelter data and plumbing (fish shelters Phase 0) | `8afeb96b` (ported; `registryOrThrow`) | `a9ee2d03` (ported) |
| `a87aa189` | Make shelter hulls solid and promote lone tanks (Phase 1) | `c14fd35d` (ported) | `b4dc00be` (ported) |
| `5fd7e989` | Add the shelter visit state machine (Phase 2) | `63e2de44` (ported) | `8b3d1a07` (ported) |
| `3149cdb4` | Startle skittish fish into cover (Phase 3) | `8ea1d2f9` (ported verbatim) | `0c82c625` (ported verbatim) |
| `a6bdece3` | Give lurkers a home and a mouth pose (Phase 4) | `7570ea20` (ported verbatim) | `d65ea875` (ported verbatim) |
| `d54ae069` | Keep a bobbing fish inside its shelter's hollow | `05d81677` (ported) | `f417af07` (ported; `FishAnimator`'s pattern switches are `instanceof` chains on Java 17) |
| `b31def99` | Opt the shelter cast in (Phase 5) | `72b4eb7f` (ported verbatim) | `94be9160` (ported verbatim) |
| `6faacad3` | Spec affordances and cosmetic obstacles for shelters | `0359f7cf` (ported verbatim) | `6c48f0bc` (ported verbatim) |
| `9dbbc094` | Make skittish fish dash for cover, nearest first, and flinch otherwise | `14c4d9af` (ported verbatim) | `5899e9d0` (ported verbatim) |
| `e056654f` | Let fish swim through the Clay Pipe and the whale's skull | `925e08d5` (ported) + `64c68bfa` (port-only follow-up: the Clay Pipe item model) | `78945630` + `a0216334` (as 1.21.1) |
| `be54e6f7` | Make shelter visits common, and let startles bump approaching visitors | `c5e3cf30` (ported verbatim) | `18d27647` (ported verbatim) |
| `18e6a9d2` | Make swimmers steer round the solid parts of every cosmetic | `522652a8` (ported; `registryOrThrow`; `leaf_litter` out of the soft-only test) | `547adc1d` (ported; Forge tag-reload hook; `tags/blocks/`; tag drops `leaf_litter`, `short_grass` → `grass`; 1.20.1 DFU `getOrThrow`) |
| `24d7d3b3` | Let a shift-click clear the way for a tank cosmetic | `59867c23` (ported; `ItemStackUseOnMixin` verbatim; preview keeps its world-render shape) | `b9804c6c` (ported; `Block#use` wording) |
| `5de81c7a` | Let fish swim through the arches and the Torii Gate, and visit the gazebo | `cfb580dc` (ported; no pale oak arch; `#minecraft:lanterns` → lantern + soul lantern; `leaf_litter` dropped from the tag; stray `common/logs/latest.log` left out) | `08a48dff` (ported; as 1.21.1) |
| `df9660b6` | Add shelters for bigger fish, and let structures claim only the floor they stand on | `a7233474` (ported; `iron_chain` → `chain`; cosmeticpreview span framing) | `e0c473ec` (ported; no cosmeticpreview scene, D4; 1.20.1 DFU `getOrThrow`) |
| `3af111cc` | Sculpt the Sea Arch, Leviathan's Seat, Galleon and Ziggurat | `a4a8c38c` (ported; `iron_chain` → `chain`) | `df79e56c` (ported; owner-chosen substitutes: Ziggurat `polished_tuff` → `tuff`, `tuff_bricks` → `cracked_stone_bricks`, `tuff_brick_stairs` → `stone_brick_stairs`; Galleon `waxed_oxidized_copper_trapdoor` → `warped_trapdoor`) |
| `8bc2cc82` | Stop walking every floor-structure part every frame, and measure what cosmetics cost to draw | `e8c708ca` (ported; `CosmeticCostBench` on `RenderType` layers; no `inactivityFpsLimit`) | `31a30772` (ported; `hasRenderedAllChunks`) + a port-only fix counting each quad under its own `layer()`, which landed by mistake inside `f27b456b` (the `4b0e818e` row) |
| `aaaa4abb` | Cap a tank group's fish by what it holds | `c5764296` (ported) | `8eaa3639` (ported; `tanksim` look ray via `getLookAngle` + port `interactionRange`) |
| `185003d0` | Let untagged fish swim tunnels like gates, keep lurkers in dens, and tag half the species | `7259aa16` (ported verbatim) | `9110b6c6` (ported verbatim) |
| `7091e6d5` | Let fish set off cosmetics: a clam, strongbox, shrine and gatehouse that react | `6272667e` (ported; reaction parts through `renderSingleBlock`; `ReactionBoostSyncPacket` as on 26.1.2) | `8588ad1d` (ported; `FishtasticPayload` packet; `flatXmap` for `Codec#validate`; Forge client hook) |
| `6da15866` | Make badges on the sticker sheet | `4e3f6b5d` (ported; both `.gitignore` entries kept) | `5cc41dfe` (ported) |
| `2324825b` | Add the Reef-Crowned Skull cosmetic and redo the clam, shrine and gatehouse art | `12279763` (ported) | `51817038` (ported) |
| `4b0e818e` | Update the shelters handoff to the redated commit SHA | `1918780a` (ported verbatim) | `f27b456b` (ported; also carries the `CosmeticCostBench` layer fix, see `8bc2cc82`) |

`05565f7b`, the merge that follows `76986ade` on 26.1.2, is content-identical to `76986ade` (`git diff 76986ade 05565f7b` is empty), so it carries nothing to port.

Port-only follow-ups in the `4b0e818e` pass: `64c68bfa` / `a0216334` (the Clay Pipe item model), `ebf2974d` (1.21.1: two cosmeticpreview self-test harness fixes - shot timing, and the item into `hotbar.0`; the latter applies to 26.1.2 too), `16bb2f13` / `24fe71ff` (runtime `common/logs` changes taken back out). On 1.20.1 the `soft_cosmetic` tag drops `leaf_litter` and reads `grass` for `short_grass`; the Ziggurat and Galleon carry the owner-chosen block substitutes (2026-10-06).

---

## Seams on `26.1.2` (track P2)

| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| S1 | `FishtasticItemData` facade | [x] | `a285e76f`..`3b8427e4` | |
| S1b | `HeadProfile` record for `setHeadProfile` | [ ] | | optional (track B, B2.1) |
| S1c | `FishtasticItemPatch` wrapper for reward patches | [ ] | | recommended (B2.5), D9 family |
| S2 | Packet codec locality | [x] | (already true) | confirmed by the B3.1 inventory: one `STREAM_CODEC` field per type |
| S3 | `fishsim` + `tank-shape-gen` at `--release 17` | [x] | `2b181f3c` | |
| S4 | Rendering logic apart from output | [~] | | the swarm, animator and bubbles are already split. Remaining: the `ItemEffect` data/render split (A2.5). |
| S5 | Java 17 library calls replaced | [x] | `cf643423`, guard `afa6d4dc` | 19 `getFirst` + 7 `Math.clamp` + 1 `SequencedMap` local (the other N7 hits were Java 8 `Comparator#reversed` / `Deque` calls). `java17ApiGuard` keeps it that way. |
| S6 | `FishMoonPhase` enum + `FishtasticPermissions.gamemaster()` | [x] | `616b6566` | 21 `requires` sites (20 commands + `mcp/McpBridgeCommand`). |
| S6c | `util/Ids` for resource id construction | [x] | `24203a87`, guard `33986055` | 135 calls in 43 files (`of` 77, `withDefaultNamespace` 45, `parse` 8, `tryParse` 5). `idConstructionGuard` keeps it that way. Backports change only the 4 `Ids` bodies; A2.1's `Identifier` type rename (100 files) still happens. |

## Decisions

| ID | Status |
|---|---|
| D1–D7 | settled (pass 1) |
| D8 Sunset Postcard parity mechanism | **decided 2026-09-24: full parity** |
| D9 Seams S5/S6 on 26.1.2 first | **decided 2026-09-24: yes** |
| D10 Shared gametest harness | **decided 2026-09-24: yes** |
| D11 Loom 1.17 / Gradle 9.5 on the port branches | **decided 2026-09-24: yes** |

---

## Track A: `port/1.21.1`

### A1: Scaffolding (gate G-A1, then hook stage → `compile`)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A1.1 | Gradle 9.5, Loom 1.17 remap, mojmap, toolchain 21, refmap names | [x] | A1 commit | Loom 1.17.493. Refmaps `fishtastic-<module>-refmap.json`; the common config's `refmap` key is injected Fabric-side only (see track A, A1 "Done"). |
| A1.2 | `gradle.properties` versions, mod metadata ranges | [x] | A1 commit | gelatin 1.0.16 until G-1.21.1. |
| A1.3 | Delete cool-cam and `mcp/**` and their call sites | [x] | A1 commit | 23 files; call sites in both entrypoints, both client entrypoints, `FishtasticCommand`. |
| A1.4 | Artifact names `2.0.1+1.21.1` (already the format; verify) | [x] | A1 commit | Verified. `publishCurseForge` now uploads `remapJar` (shadowJar is the named dev jar on the remapping Loom). |
| A1.5 | Jar-level refmap check (N8) | [~] | A1 commit | Wiring verified: the Fabric jar's `fishtastic.mixins.json` names `fishtastic-common-refmap.json`. No refmap files yet (no mixins compile at A1); re-check both files are in the Fabric jar at G-A2. |
| G-A1 | Gate: fishsim 163+1, tank-shape-gen 21,955, both jars build, the probe loads on both loaders | [x] | A1 commit | All green 2026-09-24. Both servers reach `Done` with the probe line; the only errors are 26.1.2 data (string ingredients, unregistered items), which A2/A3 fix. |

### A2: Core and server (gate G-A2, then hook stage → `unit`)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A2.0 | `port/excludes.txt` (62 files) + `portstub` (12 stubs) | [x] | A2 commit | The 62, plus render-only files the import scan missed (particles, glint render-type registration, `FishPileIcons`, `ClientTankFlocks`, `TankBubbleEmitter`, `IrisCompat`, `CosmeticTransformLoader`, `FishingHookRendererMixin`, `LevelRendererMixin`), Fabric datagen (A3) and the testmods (A6.1). 6 stubs, not 12 (see track A, A2 "Done"). |
| A2.1 | `Identifier` type rename (100 files; `Ids` bodies keep their factory names), registry access (35 + 13), permissions (1 line in `FishtasticPermissions`, S6), `setId` (4) | [x] | A2 commit | Plus `ResourceKey#identifier()` → `location()`. |
| A2.2 | Registration: BE types, `@EventBusSubscriber` bus | [~] | A2 commit | NeoForge `BlockEntityType.Builder`. The `bus = MOD` change is in the testmod, which waits for A6.1. |
| A2.3 | BEs (6), blocks (5), items (8): 1.21.1 signatures | [x] | A2 commit | `util/BlockEntityNbt`, `util/InteractionResults`. Plan correction: `PASS` → `SKIP_DEFAULT_BLOCK_INTERACTION`. |
| A2.4 | `FishCatchSavedData` → `SavedData.Factory` | [x] | A2 commit | File is `data/fishtastic_fish_catches.dat` (1.21.1 storage takes flat names). |
| A2.5 | Components: `TooltipDisplay`, `TooltipProvider`, `ItemStackTemplate`, `BREAK_SOUND`, `ResolvableProfile`; `ItemEffect` split | [x] | A2 commit | `ItemEffect` is data-only here; its render half is A5.4's `ItemEffectRenderData`. |
| A2.6 | Networking: Fabric `playS2C/playC2S`; `SetDayRatePayload` | [x] | A2 commit | Named `SetDayRatePacket` (tree convention). `util/StreamCodecs` for the 3 packets over 6 fields. |
| A2.7 | `MarineCompostRecipe` + serializer | [x] | A2 commit | |
| A2.8 | `FishingHookMixin` descriptors; `FishMoonPhase.at` (1 line, S6); `getDayTime` | [x] | A2 commit | Lava redirect now targets `BlockState#is(Block)`; verified at runtime on Fabric (the AP doesn't flag the old descriptor). |
| A2.8.c | Sunset Postcard: tickTime accumulator mixins + rate sync (D8) | [~] | A2 commit | Mechanism in place (both mixins resolve in the refmap). The 0.5-rate gametest comes with A6.1. |
| A2.9 | Commands, config, menus back in | [x] | A2 commit | |
| A2.10 | Unit tests: 64 of 78 (FishSphereContainerTest waits for A4) | [x] | A2 commit | 64 passed. |
| G-A2 | Gate | [x] | A2 commit | common compiles, 64 + 163/1 + 21,955 tests, both platforms compile, common refmap has all 7 server/client mixins. Server smoke run: both loaders boot through registration, then stop on 13 cosmetic structures that use post-1.21.1 blocks (A3, needs an owner decision). |

### A3: Datagen and resources (gate G-A3)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A3.1 | Fabric datagen providers on the FAPI 0.116 APIs | [x] | A3 commit | Tags use `getOrCreateTagBuilder` (the reference sources show `tag`, a genSources naming artifact; the jar has `getOrCreateTagBuilder`). Quest/shop providers need `FishtasticDataGenerator.registryElementsPathProvider`: 1.21.1 vanilla datagen writes registry elements without the `<ns>/` directory that FAPI/NeoForge load from. Rod and alert models carry `overrides`; the `minecraft:cast` and `fishtastic:has_alert` item properties still need client registration (A4/A5). |
| A3.2 | Regenerate recipes/advancements/loot/models, and review the diff against the expectation table | [x] | A3 commit | Advancements, loot, tags, blockstates, `models/block`: 0 diffs. Recipes: 72 generated + hand-authored `deep_sea_bait` converted by hand (73 of 74; `marine_compost` unchanged). 5 reviewed by hand. `data/fishtastic/fishtastic/**` differs only by the cosmetics decision. |
| A3.3 | Delete `assets/fishtastic/items/` (180); fix `copy_assets_to_common.py` `EXCLUDE` | [x] | A3 commit | `EXCLUDE` is now empty. |
| A3.4 | 0 × `Unable to load model` in the log | [x] | A3 commit | 0 on Fabric `runClient`. The one model warning is the `fish_tank` blockstate (A5.2). |
| A3.5 | Check the synthesized pack formats (34/48) | [x] | A3 commit | Not logged; both loaders synthesize from `getPackVersion` (FAPI `ModResourcePackUtil`, NeoForge `ResourcePackLoader`), which is 34/48 on 1.21.1. |
| A3.c | Cosmetics decision (owner, 2026-09-24): regular lantern on the fence arches; drop the pale oak arch and the leaf litter cosmetic; drop leaf litter from `birch_tree` | [x] | A3 commit | Port-branch diff: `FENCE_ARCH_WOOD_TYPES` without `pale_oak`, no `COSMETIC_LEAF_LITTER`, 4 JSONs + 2 lang keys removed, 1 part out of `birch_tree`. |
| — | Stray `data/fishtastic/{cosmetic_structure,item_effect}` checked on 26.1.2 | [x] | report only | Not leftovers: `CosmeticStructureProvider` and `ItemEffectProvider` write there (plain `createPathProvider`). Nothing reads them; the registry copies are hand-synced and the 4 item_effect copies have drifted. 26.1.2 fix: `createRegistryElementsPathProvider`, then delete the dirs. Not changed here. |
| G-A3 | Gate | [x] | A3 commit | Datagen diff matches the table (see track A, A3 "Done"). Both servers reach `Done` with no errors; `ServerLevelTickTimeMixin` applies (`required`, `defaultRequire: 1`). |

### G-1.21.1: gelatin-ui (branch `mc/1.21.1`)
| ID | Item | Status | Commit (gelatin) |
|---|---|---|---|
| G1.1 | Worktree + branch from `origin/main` (`6ff90c8`) | [x] | worktree `D:\GitHub\gelatin-ui-worktrees\mc-1.21.1` |
| G1.2 | Cherry-pick the 27 non-render commits (skip `9e93297`) | [x] | `f5f7415`..`77982a5` (27 picks, one per `26.1.2` commit) |
| G1.3 | `4cb61bc`, `f78bc6b`, `4f22fdc` render-line fixes | [x] | `f50fa3e`, `058d57c`, `52b2856` |
| G1.4 | Posed player on `RemotePlayer` (`d407ee6`) | [x] | `4b6f482` |
| G1.5 | `HiResItems` as a no-op (`36c7307`, `5ae6aa4`) | [x] | `f99d8ae`, `024b771` |
| G-G1 | Gate + `publishToMavenLocal 1.0.31+1.21.1` | [x] | `20c9f68` (tip; 34 commits) |

### A4: GUI (gate G-A4)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| A4.1 | Switch to gelatin `1.0.31+1.21.1` | [x] | A4 commit | `gelatinui_version`, resolving from mavenLocal. |
| A4.2 | `GuiGraphicsExtractor` → `GuiGraphics`, 62 `pose()` sites, blit/text/item calls (14 files) | [x] | A4 commit | Plus `extractBackground` → `renderBg` and `extractImage`/`getHeight` → `renderImage`/`getHeight()` — different names *and* parameter orders from 26.1. The 11-arg `blit` also swaps (w,h) before (u,v). |
| A4.3 | Input records → raw signatures; `KeyMapping.Category`; Fabric HUD/tooltip/keybinding API names | [x] | A4 commit | `KeyMapping.Category` landed in A2 instead: `FishtasticKeyBinds.CATEGORY` is already the plain string `KeyMapping.Category.register` derives on 26.1. FAPI 0.116.7 has only `HudRenderCallback` (confirmed: no `HudElementRegistry`/`VanillaHudElements` in the sources), so the three HUD layers share one callback in the 26.1 registration order — and vanilla's toasts, rendered after `Gui.render`, now cover them. |
| A4.4 | JEI 19.18 compat (4 files) | [x] | A4 commit | No `AbstractRecipeCategory`, so both categories implement `IRecipeCategory` with their own blank drawable. |
| G-A4 | Gate: 78 unit tests; every screen opens on both loaders | [x] | A4 commit | 78/78; every screen opened through its real path on both loaders, and both item predicates verified against their override models (see track A, A4 "Done"). Server smoke clean on both loaders too. |

### A5: Rendering (design notes: `track-a5-rendering-1.21.1.md`)
| ID | Item | Status | Commit | Notes |
|---|---|---|---|---|
| — | `26.1.2` `7839f271`: in-place quest banner update, JEI zero-size gui properties (the two A4 findings, fixed on 26.1.2 first) | [x] | `e33f5792` | Verified on the port by the self-test's `fixes` scene: the in-place banner reads `5 / 10` with `Complete!`; JEI logs 0 `Received invalid gui properties` on NeoForge (was 2 per open). |
| A5.0 | `FishtasticShaders` seam, `FishtasticRenderTypes` | [x] | A5 checkpoint 1 | `RenderType.create` is `private` in vanilla, not reachable by subclassing: one AW line (NeoForge's AT and FAPI's AW already widen it at runtime). The seam ships the two bake programs with F2's per-program uniform lists. |
| A5.5 | Particles (10 classes) | [x] | A5 checkpoint 1 | 9 files (`util/SparkleParticle` already compiled). `TextureSheetParticle` + `setSprite`; providers pick from `level.getRandom()` (1.21.1 passes no `RandomSource`). |
| A5.1 | Tank BER + pile BER, `TankFlockAdapter` | [x] | A5 checkpoint 1 | Also `ClientTankFlocks`, `TankBubbleEmitter`, `CosmeticTransformLoader`. A fresh snapshot per frame, as 26.1.2. No culling override (26.1.2 has none). NeoForge dev needed `:fishsim` in the Loom `main` mod group. |
| A5.2 | Tank model: NeoForge `IDynamicBakedModel` | [x] | A5 checkpoint 2 | Shared port-only `client/compositemodel/FishTankGeometry` loads the 5,048 fragments and every block's texture once per reload (the bakery is only safe in the bake phase); meshing threads only run `FaceBakery`. `"loader"` model + vanilla blockstate; item via `ItemOverrides`, one render pass per layer. |
| A5.2f | Tank model: **Fabric FRAPI (new code, N3)** | [x] | A5 checkpoint 2 | `UnbakedModel` resolved for both `block/fish_tank` and `item/fish_tank` by a `ModelLoadingPlugin` (a `BlockModel` can't have a custom parent); `emitBlockQuads`/`emitItemQuads` with per-layer blend modes. |
| A5.2x | See-through blocks' chunk layers (found by A5.2) | [x] | A5 checkpoint 2 | 26.1 derives layers from texture alpha; 1.21.1 draws everything solid unless told. `client/FishtasticBlockRenderLayers`: the 32 stained glass blocks translucent, `clear_glass`/`borderless_glass` cutout (checked against the textures). They were opaque in the world on both loaders since A2. |
| A5.3 | BEWLR items, item properties (`cast`, `has_alert`, `pile_size`), treasure chest | [x] | A5 checkpoint 3 | One port-only dispatcher, `client/renderer/FishtasticItemRenderers`, behind Fabric `BuiltinItemRendererRegistry` and NeoForge `IClientItemExtensions`: Pile of Fish (flat fan, or pile block), 31 structure cosmetics, treasure chest. **No `pile_size` property**: 26.1.2 has no size-based icon, only a per-stack model swap, now a vanilla `CUSTOM_MODEL_DATA` marker the renderer reads. `cast`/`has_alert` were already done in A4. |
| A5.6 | Fishing line hand, pose, held-item scale hooks | [x] | A5 checkpoint 4 | `FishingHookRendererMixin` wraps `is(Items.FISHING_ROD)` in `getPlayerHandPos` (`require = 0`: NeoForge's `canPerformAction` patch already covers modded rods); `HumanoidModelMixin` reads the entity in `setupAnim(LivingEntity, …)`; `ItemInHandLayerMixin` brackets `ItemInHandRenderer.renderItem` in `renderArmWithItem`. 26.1.2's `ItemInHandRendererMixin` is the held item's **outline**, not a scale: deleted, A5.4's `ItemRenderer.render` hook covers it. The podium puppet is matched by gelatin's class name. |
| A5.4a | Glint (ancestor `ItemRendererMixin`, `RenderBuffersMixin`) | [x] | A5 checkpoint 5 | The ancestor's two mixins back with their shapes unchanged (`3b8b2c31`). `RenderBuffersMixin`'s fixed-buffer local must be declared `Map`: naming `SequencedMap` emits a class constant `java17ApiGuard` bans. `FishtasticGlintState` shrinks to two thread-locals; 26.1's render-state mixins are deleted. |
| A5.4b | Outline atlas + GUI outline + world outline (spike code; findings F2–F5) | [x] | A5 checkpoint 5 | Two `TextureTarget`s, LRU slots, bake at `GameRenderer.render` HEAD; the GUI ring is blitted from that atlas through `GuiGraphicsMixin` (1.21.1 has no vanilla GUI item atlas to sample), and the world outline draws after `ItemRenderer.render`'s `-0.5` translate, over `ITEM_ENTITY_TARGET` (F4, Fabulous). |
| A5.4c | GUI shader effects (silhouette, black outline, texture outline) | [x] | A5 checkpoint 5 | Two `ShaderInstance` programs (`gui_item_silhouette`, `gui_texture_outline`) with plain uniforms per draw. The black gear ring needs no program of its own: `FishtasticOutlineStyle.BLACK`, baked into the same atlas. **Deviation:** `outline_debug_uv` is not ported — its codec field parses, nothing reads it, no shipped effect sets it. |
| A5.7 | `IrisCompat` no-op; `LevelRendererMixin` deleted; HUD layers above vanilla toasts (owner decision 2026-09-24); cosmetic-capture gizmos | [x] | A5 checkpoints 5–6 | All three port-only items back and the three `port/excludes.txt` lines dropped. The gizmos are a world-render pass on both loaders; the HUD layers draw after vanilla's toasts through a `GameRendererMixin` inject, only while no screen is open. **A4's premise for that decision was wrong** — 26.1.2's toasts cover its HUD too (see track A5, A5.7). |
| G1 | Spike criteria on production code, both loaders, + Fabulous, GUI scales 1/2/4, Iris on NeoForge, 512-tank stress | [x] | G1 commit | **Passed 2026-09-25.** Base half (all 11 scenes, fixed 1600x900 window): NeoForge 21.1.209 34 checks / Fabric API 0.116.7 34 checks, 0 FAIL on each, 0 `Unable to load model`, 0 exceptions; 46 shots per loader. Iris half (`tank`+`outline` under Iris 1.8.8 + Sodium 0.6.13 + Complementary Reimagined 5.9.3): **both** loaders 15 checks, 0 FAIL, 0 `Unable to load model`, 0 exceptions, 14 shots each; the pack proved live by differencing each committed shot against its non-Iris base (100.00% / 98.79% / 97.28% of pixels differing, mean abs delta 38.89 / 53.50 / 6.87). `:common:test` 78/78 on a forced `--rerun`. fishsim `runHeadless` byte-identical to a `26.1.2` (`7839f271`) worktree across all 5 artefacts. G1's curated evidence is 26 base + 3 Iris shots per loader; `docs/port-evidence/1.21.1/` also carries the 7 per-checkpoint A5 shots — 65 files, 41 MB (weight flagged in track A5). |

### A6: Gametests and verification (gate G2, then hook stage → `full`)
| ID | Item | Status | Commit |
|---|---|---|---|
| A6.1 | Shared `FishtasticGameTests` harness (D10) + `fishtastic_empty` structure | [x] | A6.1 commit | **Done 2026-09-25, green on both loaders.** One shared class of 263 wrappers (262 generated by `build/a61-gen-shared-harness.py`, which reproduces it byte for byte, plus the Sunset Postcard test) on vanilla `@GameTest(template = "fishtastic_empty")`; `FishtasticTestSupport` is the per-platform mock-player seam. NeoForge's `@GameTestHolder("fishtastic")` + `@PrefixGameTestTemplate(false)` ride on that one class via PORT-ONLY compile-only stubs in `common/src/neoforge-gametest-annotations` (`neoforgeGametestAnnotationsApi`, taken as `testmodCompileOnly` by both platforms), and the all-air 8x8x8 ships twice - `data/fishtastic/structure/` and `data/minecraft/structure/` - because NeoForge prepends the holder namespace while Fabric parses the bare template; both are in src/testmod/resources, so neither ships. Deletes the 1,416-line Fabric harness, the 702-line 26.1-only NeoForge registration (NF 21.1 has no `TestData`/`GameTestInstance`/`TestEnvironmentDefinition`) and the 91-line `GameTestStructureProvider`; `port/excludes.txt` is empty. **Verified: `:fabric:runGametest` 263/263 and `:neoforge:runGametest` 263/263, 0 failures on each**, and the six Fabric-only tests now run on both. Also lands A2.8.c's Sunset Postcard gametest. See track A, "A6.1 as built". |
| A6.2 | Green bar: 78 / 163+1 / 21,955 / 263 Fabric / 263 NeoForge / datagen clean / fishsim byte-identical | [x] | A6.2 commit | **All measured 2026-09-25, each on a forced re-run** (`--rerun`, or `cleanTest test` where `--rerun` was ignored - a cached count is not a gate): `:common:test` **78** (0 failures, 0 skipped); `:fishsim:test` **164 = 163 + 1 skipped**; `:tools:tank-shape-gen:test` **21,955** (all passed); `:fabric:runGametest` **263/263** and `:neoforge:runGametest` **263/263** (262 shared + the Sunset Postcard test); `:fabric:runDatagen` executes and leaves `git status --porcelain` **empty**, which is what proves the `GameTestStructureProvider` deletion is complete; fishsim byte-identical to `26.1.2` (G1). **Counts corrected:** the 26.1.2 harness has **262** `@GameTest` methods, not 263 (that count included a javadoc mention), and the older Fabric report's 263 also carried the Fabric API's own `minecraft:always_pass`, which the shared harness no longer picks up. The NeoForge task is **`:neoforge:runGametest`**, not `:neoforge:runGameTestServer`. **A1.5 refmaps:** `fishtastic.mixins.json` is the only config with mixins, and the Fabric jar injects `refmap = fishtastic-common-refmap.json` into it (`fabric/build.gradle:110`); that file is present in the built jar. `fishtastic-fabric.mixins.json` also names `fishtastic-fabric-refmap.json`, which is **not** emitted - inert, because that config's `client`/`mixins` lists are empty (the Fabric module has no mixins), and it is the source of the dev-time warning "Reference map 'fishtastic-fabric-refmap.json' ... could not be read". |
| A6.3 | Owner playtest on both loaders (+ Sunset Postcard, outline tiers) | [x] | A6.3-fixes commit | **Playtest run 2026-09-25: two defects found, both fixed, both re-checked and confirmed in-game 2026-09-25.** (1) Both tooltips blitted `minecraft:container/bundle/slot_background`, a 24x24 sprite only `26.1.2` ships, so 1.21.1 drew `MissingSprite` behind the ghost bait/hook/charm icons — now 1.21.1's own 18x20 `container/bundle/slot`, at native size (owner's choice), so the slot row is 58px where `26.1.2`'s is 76px. (2) The leaderboard podium ordered by **depth, not paint order** — all three reported symptoms (player behind pedestal, fish pile behind podium block, block stack out of order) are one cause: 1.21.1's GUI pose is a real 3D `PoseStack` and every leaf bakes in its own z (vanilla items 150, gelatin's posed player 100, vanilla entity-in-inventory 50), while `26.1.2`'s 2D pose writes no depth at all. Fixed with a new opt-in gelatin seam, `IUIElement#setZOffset` — **bumps the dependency to `gelatinui 1.0.32+1.21.1`** (gelatin `mc/1.21.1` `e00acee`), with `PODIUM_BLOCK_Z_STEP = 32` / `PODIUM_PLAYER_Z_OFFSET = 400`. See track A, "A6.3 as found". **A6.3 done; G2 is next.** |
| G2 | `port/excludes.txt` and `portstub/` deleted; cut `port/1.20.1` | [x] | G2 commit | **Scaffolding removed 2026-09-25**: `port/excludes.txt` (empty at G2) and `gradle/port-excludes.gradle` deleted, the `build.gradle` apply removed. No `src/portstub/java` directories existed at G2 (A5 took the last stub). `common/src/neoforge-gametest-annotations/` is port-only but load-bearing (A6.1) and was left in place. `gw build -x test` succeeds and `:fabric:runDatagen` leaves `git status --porcelain` empty. `port/1.20.1` cut from this tip; see track B. |

### A7: Release
| ID | Item | Status | Commit |
|---|---|---|---|
| A7.1 | Changelog, CurseForge `2.0.1+1.21.1` — local `publishCurseForge` (no CI: workflows deleted 2026-09-27). Beta `2.0.1b1+1.21.1` (fabric + neoforge) published 2026-09-27 and playtested on both loaders the same day. Release `2.0.1+1.21.1` rebuilt and deployed to both 1.21.1 profiles 2026-09-27 and smoketested on both loaders (see the dated note above). | [x] | published 2026-09-27 (`publishCurseForge`, release type) |
| A7.2 | `fishtastic-worktrees/build-all.ps1` | [ ] | |

---

## Track B: `port/1.20.1`

### B1: Scaffolding
| ID | Item | Status | Commit |
|---|---|---|---|
| B1.1 | Java 17, Forge 47.4.x module (`neoforge/` → `forge/`), FAPI 0.92.12, JEI 15 | [x] | `1b148dc0`, `6920aa20` |
| B1.2 | Java 17 audit: **no-op**, done on 26.1.2 by S5 (`cf643423`); verify `java17ApiGuard` + `--release 17` compile | [x] | no-op, verified by B1 compile |
| G-B1 | Gate | [x] | `6920aa20` |

### B2: Item data
| ID | Item | Status | Commit |
|---|---|---|---|
| B2.1 | `ComponentKey<T>` + defaults + normalization; facade bodies; `ComponentKeyTest` | [x] | `7c2681a2` |
| B2.2 | `BundleContents` port (+ 11 import changes) | [x] | `e49452e1` |
| B2.3 | Tooltip providers, `appendHoverText`, `ItemStackMixin` | [x] | `1f32ac0e` |
| B2.4 | Effect conditions (expected: no change) | [x] | `2d24152b` |
| B2.5 | `FishtasticItemPatch` (94 data files unchanged) | [x] | `c873ab61` |
| B2.6 | Tank BE ↔ item: `setPlacedBy`, `fishtastic:copy_tank_data`, `getCloneItemStack`; browser GUI; Pile-of-Fish tank-click | [x] | `3beaae20`, `c2f15cbe`, `e12ee599`, `fba26e16` |
| B2.7 | S1 items 1, 6, 7, 8 cleanup (registration graph → `FishtasticItems` off excludes) | [x] | `f1440108`, `1fd4023d`, `4bba72a0`, `0cdaec33`, `6f6ffb2b`, `7b717eac` |
| G-B2 | Gate | [x] | `7b717eac` |

### B3: Networking
| ID | Item | Status | Commit |
|---|---|---|---|
| B3.1 | `BufCodec` / `BufCodecs` shim + `FishtasticPayload`; sed over 49 files | [x] | `4e02cfb8` |
| B3.2 | Fabric `PacketType`/`FabricPacket` registrar; Forge `SimpleChannel` registrar | [x] | `eab1bb65` |
| B3.3 | Datapack registry sync (expected: no change) | [x] | `b50faff6` |
| G-B3 | `PacketRoundTripGameTests` green on both loaders | [x] | `b50faff6` |

### B4: Data and resources
| ID | Item | Status | Commit |
|---|---|---|---|
| B4.1 | Plural folders; gametest structure → `structures/` | [x] | `e6658628`; `4d1ee2bc` (charm items are intentionally shop-only, not a recipe gap) |
| B4.2 | 1.20.1 datagen (recipes, advancements, loot); JSON-based compost serializer | [x] | `74b0027c`, `e6658628`, `5cd7b11f`, `bf475e83` |
| B4.3 | `blitSprite` → `blit`; 1.20.2+ vanilla ids audit | [x] | absorbed into B4/B5 bulk |
| G-B4 | Datagen diff as expected | [x] | `013d1d07` |

### G-1.20.1: gelatin-ui
| ID | Item | Status | Commit (gelatin) | Notes |
|---|---|---|---|---|
| G2.1 | Branch `mc/1.20.1` from `mc/1.21.1` at G-G1 | [x] | 6 commits, tip `3f9ba6e` | Worktree `D:\GitHub\gelatin-ui-worktrees\mc-1.20.1`, branch `mc/1.20.1`, from `mc/1.21.1` `e00acee` (tip after A6.3's z-offset seam, not just the older G-G1 tip). |
| G2.2 | Java 17, Forge module, menus (`IForgeMenuType`, `NetworkHooks`) | [x] | `3f9ba6e` | Scaffolding from `1506a7b` now proven by a green `:forge:build`/`:fabric:build`, not just source-verified. Two real environment blockers found and fixed before any compile could even start: (1) this machine's shared `~/.gradle/gradle.properties` had `org.gradle.java.home` pinned to a JDK 25 install (for some other, unrelated project) that silently overrode every project's own JDK, including this one's — Gradle 8.14/9.5.0 can't parse JDK 25 class files at all; removed globally, per-project pins belong in the project, not the user's global config. (2) The Gradle wrapper was still on `8.14` (inherited unchanged from the `mc/1.21.1` cut point) — Loom 1.17-SNAPSHOT needs Gradle 9.x (`Configuration#extendsFrom(Provider[])` etc.), bumped to `9.5.0` to match `port/1.20.1`/`port/1.21.1`. `architectury_api_version=9.2.14` is now **verified**: it's the only version under `base_version=9.2` (branch `1.20`, `supported_version=1.20(.1)`) on maven.architectury.dev. Still open: `fabric.mod.json`'s hardcoded `"java": ">=21"` / `"fabricloader": ">=0.17.2"` (should template via `${...}` like the rest of that file, matching Fishtastic's own precedent) — cosmetic, doesn't block the build. |
| G2.3 | Sprites → textures; posed-player skins on the 1.20.1 `SkinManager` | [x] | `3f9ba6e` | `:common`, `:forge`, `:fabric` all build clean (`:forge:build`/`:fabric:build` produce `remapJar`s). Turned out to be much bigger than the 2-file estimate — 59 errors across 13 files on first real compile, confirming port surface must be measured by compiling, not grepping. `HoverEventActionMixin` (`196e6e0`): 1.20.1's `HoverEvent.Action` has no `Codec` at all (Gson-based `Function<JsonElement,T>`), so the custom action itself just uses the public constructor directly — no mixin needed there. The real gap was the private static final `LOOKUP` map (no public mutation point); fixed with `@Shadow @Final @Mutable` + reassigning a copy at the tail of `<clinit>` (legal via `PUTSTATIC` even though the field is final — unlike reflection, which JPMS blocks on JDK 17+ for stripping `Field`'s own final modifier). `GuiGraphicsMixin` ported to 1.20.1's `vertex(...).color(...).endVertex()` builder chain. `ClientItemStacksTooltip`'s `blitSprite`/`withDefaultNamespace` turned out to be dead code (`68c5ddd`) — track-b's "sprites → textures" note for `SpriteRenderMode`/nine-slice paths turned out to be a non-issue too (no `blitSprite` call sites remain anywhere in `common` post-fix). `GelatinUIScreen` (`5ec4eae`): `mouseScrolled` is 3-arg pre-1.21 (no horizontal scroll), and `renderTransparentBackground` doesn't exist as a vanilla hook at all on 1.20.1 (nothing calls it automatically — now an explicit call at the top of `render()`). Player-skin trio (`af63401`) matches the design track-b already sketched (line ~406): no `PlayerSkin`/`ResolvableProfile` on 1.20.1, override `getSkinTextureLocation()`/`getModelName()` instead of `getSkin()`; rebuilt around `SkinManager#registerSkins` (same callback API `PlayerInfo#registerTextures` uses). **Scope cut:** public API changed `.profile(ResolvableProfile)` → `.profile(GameProfile)` — profiles must already carry a UUID, name-only resolution isn't supported (1.20.1's client has no `GameProfileRepository`). Remaining `ResourceLocation.fromNamespaceAndPath` → `new ResourceLocation(ns, path)` swapped across 8 files (`3f9ba6e`). **Not yet checked in-game:** `PlayerAvatarRenderer`'s pixel placement (the vertical-centering offset had no direct 1.20.1 equivalent, approximated via an outer pose translate) and the mixin's actual runtime behavior (compile+remap succeeded; hasn't been launched). |
| G-G2 | Gate + `publishToMavenLocal 1.0.33+1.20.1` (later bumped to 1.0.37) | [x] | gelatin `3f5da81`, fishtastic `060d56c6` | `publishToMavenLocal` run this session — signing plugin's `useGpgCmd()` fails without a local gpg binary (matches the earlier gelatin publish issue), so `signing { ... }` in gelatin-ui's root `build.gradle` was temporarily commented out, `mod_version` bumped `1.0.32`→`1.0.33` (repo's convention: never reuse a version string), published, then the signing block reverted. All three modules (`gelatinui-common`, `-forge`, `-fabric`) landed in `~/.m2` as `1.0.33+1.20.1`. Fishtastic's `gelatinui_version` in `gradle.properties` bumped to match, and the three commented-out `modImplementation`/`modCompileOnly`/`modTestImplementation` lines in `forge/build.gradle`, `fabric/build.gradle`, `common/build.gradle` uncommented. Verified with a real (`--rerun-tasks`) `:common:compileJava :forge:compileJava :fabric:compileJava` and `:common:test`, both green — the dependency actually resolves and links, not just configures. |

### B5: Remaining API deltas
| ID | Item | Status | Commit |
|---|---|---|---|
| B5.1 | `ResourceLocation` construction (the 4 `util/Ids` bodies only, S6c), BE/SavedData NBT, block `use` merge, `hurtAndBreak`, advancements, loot data | [x] | `7c2681a2` (Ids, pulled forward); rest absorbed into B4/B5 |
| B5.2 | Vertex builder (6 sites), shaders, BER bounds on the BE | [x] | absorbed into B4/B5 |
| B5.3 | Forge wiring: `RegistryObjectHolder`, event buses, config, models, `initializeClient`, overlays, menu screens | [x] | `2f46d5b8`; `eded9f9e` (dead `FishTankFrameType` removed instead of implemented) |
| B5.4 | `[-]` Fishing enchantment helpers: dropped, the tree doesn't call `EnchantmentHelper` | [-] | |
| B5.5 | Creative tabs, `Item.Properties` defaults | [x] | absorbed into B4/B5 |

### B6: Gametests and release (gate G3)
| ID | Item | Status | Commit |
|---|---|---|---|
| B6.1 | Shared harness on Forge 47 (`@GameTestHolder`, enabled namespaces) + Fabric 0.92 | [x] | `dc4ae88f`, `08369f43` |
| B6.2 | Green bar: 78+ / 163+1 / 21,955 / 263 × 2 / datagen clean; owner playtest; release `2.0.1+1.20.1` | [x] | `4396e8a1`, `a0958a94` (green bar + playtest done, bobber bug fixed `32c255cc`); release `2.0.1+1.20.1` rebuilt and deployed to both 1.20.1 profiles 2026-09-27 and smoketested on both loaders (see the dated note above); published 2026-09-27 (`publishCurseForge`, release type) |
| G3 | Lockstep begins (D6) | [ ] | |
