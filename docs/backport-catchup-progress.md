# Backport parity progress

The original pinned catch-up backlog is complete on `port/1.21.1` and `port/1.20.1`.

The free-daily-reroll backport is implemented on source and both port branches. All eight client
launch smokes are recorded, including the merged 1.21.1 Forge module.
The 2026-10-23 docs/tooling catch-up is complete; the
isolated solo render check (`scripts/run_render_checks.py fabric --solo`) passed 14/14 on each
port (evidence under each worktree's `build/verification/two-client/fabric/`).

## Current parity state

- **Completed operation (2026-10-23):** Extracted the authoritative reroll action into a shared
  callable operation and added shop_reroll_interaction GameTest coverage for free use, denied
  second use without tokens, escalating paid costs, changed draws, and the next in-game day's reset.
  The interaction check is registered across all eight builds. Each final run completed
  successfully:
  - 26.1.2 Fabric: selected reroll check, 1/1 passed.
  - 26.1.2 NeoForge: reroll check plus loader base test, 2/2 passed.
  - 26.1.2 Forge: reroll check plus loader base test, 2/2 passed.
  - 1.21.1 Fabric: full suite, 378/378 passed; the optional test filter is unsupported by this
    older Fabric registration path.
  - 1.21.1 NeoForge: full suite, 378/378 passed; registration auto-discovered the suite.
  - 1.21.1 Forge: selected reroll check, 1/1 passed.
  - 1.20.1 Fabric: full suite, 378/378 passed; the optional test filter is unsupported by this
    older Fabric registration path.
  - 1.20.1 Forge: full suite, 379/379 passed; registration auto-discovered the suite.
  Evidence, including raw GameTest logs, available JUnit reports, and the result matrix:
  build/backport-catchup/2026-10-23/free-daily-shop-reroll/shop-interaction/. An initial Forge
  run on source and 1.21.1 exposed a missing runGametest dependency on forgeCommonJar; the
  dependency was added and both final Forge reruns rebuilt the shared jar and passed. Earlier
  unsupported/incomplete selection attempts and pre-fix stale-jar runs are unverified and are not
  counted. Do not advance parity markers.
- Pinned source backlog: complete through `48fffdfbda1687cd42d59cb9fea0540e77aec982`.
- Parity markers: source `fa103289`; 1.21.1 commit `9230042e`; 1.20.1 commit `68e2c77b`.
- The scoped cosmetic-placement follow-up is complete without advancing those markers. Source
  commits `83fd745d` and `933214c5` map to 1.21.1 commits `9481dc9b` and `f9097e39`, and 1.20.1
  commits `edf20f2c` and `d44d7ee0`.
- The completed follow-up passed focused compilation and Fabric GameTests. No push was made.
- Docs/tooling catch-up (2026-10-23), also without advancing the markers: source `cd43f8cf`,
  `2b1ed674` and `ea4ec7da` map to 1.21.1 `8d721dbb`, `7b61f058` and `1120f02a`, and 1.20.1
  `dc24388f`, `10f82391` and `81044ce2`. Docs that never existed on a port stay absent; each port
  keeps its own `fish-tank-rendering.md` and `quest-system.md`. The `RenderSelfTest` split was
  re-applied to each port's adapted members by per-member three-way merge (the source split is
  purely mechanical; port-only scene bodies are unchanged). `scripts/twoclient.init.gradle` keeps
  1.20.1's flat `generator-settings`. Every commit passed the port pre-commit hook (unit tests and
  Fabric GameTests). Evidence and the split tooling: `build/backport-catchup/2026-10-23/`.
- **Completed follow-up (2026-10-23):** Ported the source working-tree change “one free quest shop
  reroll per in-game day” to `port/1.21.1` at `1120f02a`, then carry that result to
  `port/1.20.1` at `81044ce2`. Keep paid reroll escalation separate from the free-use flag and
  advance neither parity marker. Evidence paths:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.21.1/` and
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.20.1/`. 1.21.1 target edits began
  after the plan and topic 19/20 review.
- **Checkpoint:** The 1.21.1 state, sync, shop UI, packet callers, platform sync handlers, strings,
  documentation, and nested `StreamCodec` packet record are adapted. The first 26.1.2 Fabric
  compile caught a packet-composite arity error; the nested record fix is applied on source and
  1.21.1. Source 26.1.2 Forge and NeoForge, all three loaders on merged `port/1.21.1`, and both
  loaders on `port/1.20.1` launched after their fixes. The merged 1.21.1 Forge handler now forwards
  the daily reroll nonce and availability to the client cache. An initial attempt used a separate
  Forge-support worktree by mistake; the correct port checkout was then compiled and launched.
  Evidence:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/client-smoke/matrix.md`.
- **Completed operation (2026-10-23):** Adapted the free daily quest-shop reroll from the completed
  1.21.1 working tree to `port/1.20.1` at `81044ce2`, including saved state/legacy codec fallback,
  server sync, shop draw nonce, UI labels/costs, packet constructors, Fabric/Forge handlers, and
  port-local quest-system docs. Preserve the existing untracked
  `docs/fish-companions-twoclient/`. Evidence path:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.20.1/`. Do not run builds/tests or
  advance parity markers in this operation.
- **Checkpoint:** 1.20.1 implementation is adapted, including its nested shop-reroll packet codec
  for the fixed-arity `BufCodec` API. The unrelated untracked directory is preserved. Both Fabric
  and Forge clients launched and loaded Fishtastic screens/network/model resources. Evidence note:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/1.20.1/operation.md`. Run the normal
  port validation gates before treating either backport as fully verified; leave both parity
  markers unchanged.
- **Completed smoke correction (2026-10-23):** The Forge support docs were current: Forge 1.21.1
  had already merged into `port/1.21.1` (PR #7). Corrected the matrix to use that merged checkout.
  Its first Forge compile found a stale `QuestClientCache.update` call; updated
  `forge/src/main/java/grill24/fishtastic/forge/FishtasticForgeClient.java` to forward
  `packet.shopRefreshNonce()` and `packet.freeShopRerollAvailable()`, then reran
  `:forge:runClient` with Java 21. Compilation succeeded and Fishtastic initialized screens/network
  and loaded model resources/texture atlases before Ctrl+C. Updated evidence:
  `build/backport-catchup/2026-10-23/free-daily-shop-reroll/client-smoke/matrix.md` and
  `build/forge-support/2026-10-23/free-daily-reroll-client-smoke/README.md`. The separate
  `codex/forge-1.21.1` startup run was extra and is not counted. Keep parity markers unchanged;
  next run normal port validation gates if needed.
- **Completed documentation correction (2026-10-23):** Both port copies of `docs/backport-plan.md`
  had the stale September loader matrix. Updated each current matrix to include the merged Forge
  modules and added a current-support note citing Forge support in 2.1.2, PR #7 (1.21.1) and PR #8
  (26.1.2), while retaining D2 as a historical planning decision. No parity markers advanced.
- The six A/B switches remain enabled at their current defaults pending owner visual review.

## Remaining owner checks

These are the outstanding manual parity checks recorded at the last checkpoint:

- Real-play feel.
- F04 tank-frame equivalence.
- Real multiplayer beyond the scripted scene.
- Loading an old world in `WAITING_FOR_CAST`.
- F02 create-world behavior.

The later cosmetic-placement work does not close these checks.

## Resuming parity work

Read the complete
[feature ledger](backport-catchup/features.md), and the
[source commit ledger](backport-catchup/source-commit-ledger.json). Follow the backport-specific
instructions in `AGENTS.md` and use the target branches' existing porting plans and the canonical
modding guide's topics 19 and 20.

Before a new operation, record its exact action and evidence path here. Record its result and next
action at each checkpoint and before ending the session. An interrupted operation is unverified.
Do not advance a ported-through marker until the complete pinned backlog is accounted for. Preserve
unrelated edits and target runtime files.

## Scope and safeguards

Carry final source behavior while preserving Minecraft/loader adaptations; account
for superseded intermediate changes rather than replaying them. Target branch
plans remain under `docs/backport-plan.md` and `docs/backport-pass2/` on those
branches. Canonical guide topics 19 and 20 cover backport APIs and workflow.
The visual switches `driftBob`, `bobRetime`, `bobMindTurn`, `bobCalm`,
`partySwimSeparation` and `otherPlayerClearance` retain their current defaults
until owner review. Publication/profile deployment are separate operations.
