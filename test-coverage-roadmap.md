# Fishtastic — Test Coverage Roadmap

**Purpose:** this is a handoff document for whoever (human or agent) picks up test-writing next. It replaces re-deriving "what should I test and how" from scratch — every item below was verified against the actual source (method signatures, real blockers, real APIs confirmed to exist in this MC version), not guessed.

**Status (updated after each work session):**
- ✅ Tier 0 (0.1 MathUtil/Utility, 0.2 RodBaitContents) — done.
- ✅ Tier 1 (1.1 FishingTarget, 1.2 PlayerQuestState, 1.3 ItemEffect conditions, 1.4 FishTank) — done.
- ✅ Tier 2.1 (TutorialManager) — done. **Important discovery for whoever does 2.2/2.3 next:** `helper.makeMockServerPlayerInLevel()` crashes on NeoForge as soon as any code sends a custom packet during player-join (Fishtastic's `OnDatapackSyncEvent` listener does this unconditionally — see `FishtasticNeoForge.java:75-79`), because the mock connection skips NeoForge's configuration-phase handshake that registers payload channels. Fixed with a NeoForge-only `neoforge/src/testmod/java/grill24/fishtastic/neoforge/gametest/NeoForgeTestPlayers.java` that calls `NetworkRegistry.configureMockConnection()` (a `@VisibleForTesting` NeoForge API made for exactly this) before `placeNewPlayer`. Shared test methods that need a player now take a `Supplier<ServerPlayer> mockPlayer` parameter instead of calling the helper directly — Fabric's wrapper passes `helper::makeMockServerPlayerInLevel`, NeoForge's wrapper passes `() -> NeoForgeTestPlayers.makeMockServerPlayerInLevel(helper)`.
- ✅ Tier 2.2 (QuestTracker) — done, no player needed (matching logic + `getActiveDailies` are both pure/registry-only). **Correction to this doc's original plan:** "widen to package-private" doesn't actually work — shared test logic lives in `grill24.fishtastic.gametest`, a *different* package from `grill24.fishtastic.server`, so package-private access fails. Made `matchesObjective` `public` instead (same justification as the `WormBinBlockEntity.canDeposit()` precedent this doc already cites — that precedent is `public`, not package-private). Built the throwaway `Registry<Quest>` fixture for `getActiveDailies` tests via `new MappedRegistry<>(FishtasticRegistries.QUEST_REGISTRY_KEY, Lifecycle.stable())` + `.register(key, quest, RegistrationInfo.BUILT_IN)` — no datapack/server needed.
- ✅ Housekeeping: `FishingLootHelper.java` (0 bytes, dead) deleted.
- ✅ Tier 2.3 (networking round-trips) — done. Covered `StartFishingMinigamePacket` (nested record, enum-by-ordinal, ItemStack list, data components), `PurchaseShopEntryPacket` (smoke test), and `QuestSyncPacket` (maps of a clean record vs. maps with ItemStack values). Confirms the harness mechanics this doc called out: build a `RegistryFriendlyByteBuf` from `helper.getLevel().registryAccess()`, never compare `ItemStack`/records-containing-ItemStack via `.equals()` (compare item/count/components individually), but DO use `.equals()` directly for nested records with no ItemStack fields (e.g. `PhaseRule`, `PlayerQuestState.QuestProgress`) since their generated `equals()` is already structural and correct.
- ✅ Tier 3.2 (`ShopEntry.getActiveDailyShop`) — done, same throwaway-`MappedRegistry` fixture pattern as `QuestTracker.getActiveDailies`.
- ✅ Tier 3.1 (`FishingMinigameManager` validation seam) — done. The doc's step 1 spike worked exactly as hoped: `new FishingHook(player, level, 0, 0)` + `level.addFreshEntity(hook)` is sufficient — `FishingHook`'s constructor calls `setOwner(player)`, which (per vanilla `updateOwnerInfo`) sets `player.fishing` automatically, no manual field assignment needed. `startSession`/`cancelSession` are tested end-to-end against the real RNG-driven `generateTargets` path (real fish-profile/temperament registries, real `FISHING_TREASURE` loot table — all present in the GameTest server, no datapack faking required). For `handleMinigameComplete`'s validation (the actual trust boundary), added one test-only public seam method, `FishingMinigameManager.seedSessionForTest(player, List<List<ItemStack>>)`, which builds an `ActiveSession`/`ServerFishingTarget` directly with caller-supplied reward stacks — bypassing RNG entirely — and inserts it into `activeSessions`. This was preferred over widening `ActiveSession`/`ServerFishingTarget` visibility (which would just relocate the same problem, per the 2.2 package-private lesson already in this doc) because it keeps the internals private and gives tests a one-line way to pin exact reward contents. Covered: out-of-range/negative indices ignored without throwing; a session is single-use (removed after its first completion report, even an all-invalid one — replaying the same id afterward is a safe no-op); a mismatched/unknown session id never touches the real active session; bait is consumed only when something was actually awarded. **Confirmed and flagged, not fixed:** `handleMinigameComplete`'s sub-20-tick check (`timeTaken < 20`) only logs a warning and does **not** withhold the reward — `handleMinigameCompleteGrantsRewardsEvenWhenCompletedInUnderTwentyTicks` pins this down as current behavior with a comment flagging it as a real product question (should fast completions be rejected, not just logged?) rather than silently treating it as correct. Worth a deliberate decision from whoever owns this system. All 8 new tests pass on both `:neoforge:runGametest` and `:fabric:runGametest` (109/109 each).
- ⬜ Tier 3.3 (datagen output validation, optional) — not started, low priority per this doc.

Companion doc: `test-coverage-report.html` (visual dashboard of current state — open it first for the big picture; this doc is the backlog that fixes the gaps it shows).

**How to use this:** work top to bottom. Tier 0 is free — no blockers, pure logic, do it in one sitting to build momentum. Tier 1 is the highest-value work and is now provably tractable (see "Discovery" notes below). Tier 2/3 need either a small investigation spike or a small production-code seam before tests can be written — that's called out explicitly so nobody burns an afternoon assuming pure black-box testing will work.

---

## Conventions — read before writing anything

This project's only test infrastructure is Minecraft's GameTest framework (no JUnit/JaCoCo). Follow the existing pattern exactly:

1. **Shared logic** goes in a new or existing file under `common/src/testmod/java/grill24/fishtastic/gametest/`. Plain `public static void methodName(GameTestHelper helper)` methods. No platform annotations. End with `helper.succeed()`.
2. **Wire into Fabric**: add a one-line `@GameTest(structure = "fabric-gametest-api-v1:empty")` wrapper in `fabric/src/testmod/java/grill24/fishtastic/gametest/FishtasticFabricGameTests.java` that delegates to the shared method.
3. **Wire into NeoForge**: add one `register(event, env, "snake_case_name", maxTicks, Class::method)` call in `neoforge/src/testmod/java/grill24/fishtastic/neoforge/gametest/NeoForgeGameTestRegistration.java`.
4. **maxTicks**: default `200` for synchronous tests. Use `helper.runAfterDelay(n, () -> {...})` + a larger `maxTicks` for anything timing-dependent (see `WormBinGameTests.conversionTakesBaseTicks` for the pattern).
5. **Run**: `./gradlew :fabric:runGametest` and `./gradlew :neoforge:runGametest`. Both must pass — that's the whole point of the shared-logic-thin-wrapper design.
6. **Assertions**: always `helper.assertTrue(condition, "message describing what must be true and why")` — every existing test message states the invariant, not just "test failed."

If a test needs **no world/block state at all** (pure data/logic), follow `ItemComponentGameTests.java` / `FishCatchDataGameTests.java` — just construct objects and assert. If it needs a **block + block entity**, follow `WormBinGameTests.java` — `helper.setBlock(...)` then `helper.getBlockEntity(...)`.

---

## Tier 0 — do first (zero blockers, builds momentum)

### 0.1 — `MathUtil` / `Utility`
**Files to create:** new `common/src/testmod/java/grill24/fishtastic/gametest/MathUtilGameTests.java`.
**Why:** zero Minecraft dependency, the cheapest tests in the entire codebase, currently at 0% for no good reason.
**Test ideas:**
- `lerp(0, 10, 0.5) == 5` for both float and double overloads; `t=0` returns start, `t=1` returns end.
- `clamp` for all three overloads (float/double/int): value below min → min, above max → max, in-range → unchanged.
- `easeInOutQuad(0) == 0`, `easeInOutQuad(1) == 1`, and the midpoint behavior the doc comment describes (accelerate to 0.5, decelerate after).
- `easeOutCubic(0) == 0`, `easeOutCubic(1) == 1`.
- `easedLerp` with `t->t` (identity) equals plain `lerp`.
- `Utility.ft("foo")` produces an `Identifier` with namespace `fishtastic` and path `foo`.
- `Utility.interpolateColor` at `t=0`/`t=1` returns the start/end vector exactly.

### 0.2 — `RodBaitContents`
**File to extend:** `ItemComponentGameTests.java` (it already tests the other 3 components — this is the 4th, currently the only untested one in that file's domain).
**Test ideas:** `RodBaitContents.EMPTY.isEmpty()` is true; wrapping a non-empty `ItemStack` makes `isEmpty()` false; `copyStack()` returns an equal-but-distinct `ItemStack` (mutating the copy must not affect the original).

---

## Tier 1 — critical gaps, now provably tractable

### Discovery that changes the plan
While scoping this roadmap, two things were found that make "Fishing Minigame Core" much less scary than the coverage report's risk matrix suggests:

- **`FishingTarget` (util/, 907 lines) is almost entirely pure, deterministic, client-shareable state-machine logic** — it does *not* need a world, a player, or a registry. It has overloaded constructors that accept an explicit `initialPosition`/`difficulty` (so tests can be deterministic instead of fighting RNG), a `tick(float bobberPosition, float bobberSize)` driver, and clean query methods: `isCaught()`, `hasFailed()`, `getCatchProgress()`, `getMovementPattern()`, `getState()`, `isAnimationComplete()`. This is the single highest-value untested class in the mod and it's as easy to test as `ItemComponentGameTests`.
- A **stale, uncommitted agent worktree** (`.claude/worktrees/agent-a489accc7960588af/common/src/test/java/grill24/fishtastic/test/MultiItemPhysicsTest.java`) contains an abandoned smoke-test `main()` method exercising exactly this class (`new FishingTarget(rewards, category, random)` → `updateCatchProgress(true)` → `tick()` → `startCollectionAnimation(x,y)` → `getPhysicsSimulations()`). It has **no assertions** (just `System.out.println`) and was never wired into the testmod, but it's a working usage reference — read it before writing real tests so you don't have to re-discover the call sequence. Don't copy it as-is; convert each `println` checkpoint into a real `helper.assertTrue`.

### 1.1 — `FishingTarget` state machine
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/FishingTargetGameTests.java`.
**Why:** the core skill-check/animation logic behind every fishing catch in the game; 907 lines, zero coverage, and — per the discovery above — no blockers.
**Test ideas:**
- `updateCatchProgress` with high `overlapQuality` repeatedly → `isCaught()` becomes true (`catchProgress >= 1.0`).
- `updateCatchProgress` with low/zero `overlapQuality` repeatedly → `hasFailed()` becomes true (`catchProgress <= 0.0`).
- `pickRandom(difficulty, roll)` — since `roll` is an explicit parameter (not internal RNG), boundary rolls should deterministically map to specific `MovementPattern` values; pin down at least the two ends of the distribution.
- Construct with each `MovementPattern` reachable via the difficulty-keyed constructor and confirm `tick()` doesn't throw and `getPosition()` stays within expected bounds (the tick* private methods aren't directly testable, but their effects via `getPosition()`/`getState()` are).
- `startCollectionAnimation(x, y)` → `getPhysicsSimulations().size()` equals reward item count; `isAnimationComplete()` is false immediately after, becomes true after enough `tick()` calls.
- `startFailAnimation()` → eventually `isAnimationComplete()`.

### 1.2 — `PlayerQuestState` (covers both "Quests" AND "Shop/Economy" critical gaps)
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/PlayerQuestStateGameTests.java`.
**Why:** this is pure in-memory logic with **no world/registry/player dependency at all** — same shape as the already-well-tested `FishCatchSavedData`. It also happens to contain the entire shop purchase guard (`purchase()`), which is the actual economy-integrity logic the coverage report flagged as a medium-priority gap. One file closes two gaps.
**Test ideas (model directly on `FishCatchDataGameTests`'s guard-clause style):**
- `getProgress` on an untouched quest returns the documented default (`count=0, lastReset=-1, completed=false, claimed=false`).
- `incrementCount` raises `currentCount`; `completed` flips true exactly when `currentCount >= objective.targetCount()`.
- `canClaim` is false before completion, false after `claim()` is called once (claimed guard), true exactly between completion and claim.
- `claim` adds the token reward to `getTokenBalance()` and sets `claimed=true` without resetting `currentCount`.
- `resetDailyIfNeeded`: progress resets to 0 when `lastResetGameDay < currentDay`; no-ops when called again the same day.
- **`purchase()` — the economy guard, test all three branches:** returns `false` and balance unchanged when `tokenBalance < entry.cost()`; returns `false` and balance unchanged when `purchaseCounts >= entry.maxPurchases()` (and confirm `maxPurchases() == 0` means unlimited); returns `true`, deducts `cost`, and increments the purchase count on success.
- `getProgressSnapshot()` / `getPurchaseCountSnapshot()` reflect prior mutations.
- You'll need a constructible `Quest`/`QuestObjective`/`ShopEntry` fixture — these are plain records with public constructors (see `common/src/main/java/grill24/fishtastic/data/Quest.java`, `QuestObjective.java`, `ShopEntry.java`), so just build minimal instances by hand; no datapack/registry needed for this class's own tests.

### 1.3 — `ItemEffect` + condition classes
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/ItemEffectConditionGameTests.java` (or fold into `ItemComponentGameTests.java` — your call, both are "pure ItemStack logic" in spirit).
**Why:** a small rule engine (`AndCondition`, `ComponentCondition`, `ComponentValueCondition`, `ItemTagCondition`, plus `ItemEffect.matches()` which ANDs all of a single effect's conditions and additionally checks `enabled`). All four condition classes only touch `ItemStack` + `BuiltInRegistries.DATA_COMPONENT_TYPE` (a static built-in registry, always available — no datapack dependency). This is the most mechanically "unit-testable" untested code in the project.
**Important — don't bother with `ItemEffectManager`:** it's a different class and is genuinely not testable here — it calls `Minecraft.getInstance()` and `mc.level.registryAccess()` directly, which don't exist on a GameTest server. Leave it as manual-QA/client territory; only test the conditions and `ItemEffect.matches()` itself.
**Test ideas:**
- `ItemTagCondition.matches` true/false based on whether the stack is in the given tag (use an existing real tag, e.g. `ItemTags.FISHES` wrapped as the condition's `Identifier`).
- `ComponentCondition.matches` true iff the stack has the named component (e.g. set `FishtasticDataComponents.ITEM_SIZE` then check `has_component` on its id; check the false case on an unset stack too).
- `ComponentValueCondition.matches` against a component whose codec serializes to a JSON object with the targeted field — `FishQuality` is a good fixture since its component is already exercised elsewhere in the test suite.
- `AndCondition`: empty condition list → vacuously true; all-true list → true; one-false-among-many → false (short-circuit-or-not doesn't matter, just confirm `allMatch` semantics).
- `ItemEffect.matches()`: `enabled=false` → always false regardless of conditions; `enabled=true` with all conditions true → true; one failing condition → false. (Construct `ItemEffect` directly — the outline/render fields can be passed dummy/default values since `matches()` never touches them.)

### 1.4 — Fish Tank container & cosmetic logic
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/FishTankGameTests.java`.
**Why:** `FishTankBlockEntity` (848 lines, the largest BE in the mod) has a `Container` implementation and a cosmetic-placement map that are pure data operations once the block entity exists — exactly the same shape as the already-tested `WormBinBlockEntity`. This doesn't touch rendering/composite-model code (still out of scope), just the inventory/cosmetic state.
**Pattern:** `helper.setBlock(pos, FishtasticBlocks.FISH_TANK.value())` then `helper.getBlockEntity(pos, FishTankBlockEntity.class)` — same as `WormBinGameTests.placeWormBin`.
**Test ideas:**
- `addItem(stack)` into an empty tank succeeds; `isEmpty()`/`hasItems()` flip accordingly; `getFirstItem()` returns it.
- `addItem` merges into an existing matching stack up to `getMaxStackSize()` before falling back to a new slot (mirrors vanilla container stacking — confirm the merge-then-shrink behavior in `addItem(stack, rotation, player)`).
- `addItem` returns `false` and leaves the tank unchanged once all `CONTAINER_SIZE` (27) slots are full.
- `extractItem()` removes the **last** non-empty slot (the implementation is explicitly LIFO — `for (i = items.size()-1; i >= 0; i--)`) and returns `ItemStack.EMPTY` when the tank is empty.
- `getFirstItemRotation()` reflects the rotation passed when slot 0 was filled, and is unaffected by later additions to other slots.
- `setCosmetic`/`getCosmetics`/`removeCosmetic` round-trip for a `CosmeticGridCell` key.
- `getOpenFaces`/`setFaceOpen`/`setOpenFaces` round-trip (this feeds `updateConnections`, which is render/world-adjacency logic — leave that part out of scope, just verify the face-state bookkeeping itself).

---

## Tier 2 — high value, needs a small spike first

### Discovery: player-in-a-GameTest is possible, but nothing in this repo does it yet
`GameTestHelper.makeMockServerPlayerInLevel()` exists in this MC version (confirmed in the decompiled source at `modding-guide/resources/minecraft-merged-...-26.1.2-sources/net/minecraft/gametest/framework/GameTestHelper.java:368`) and returns a real `ServerPlayer` wired into the test level with a real (test) `Connection`. **It's marked `@Deprecated(forRemoval = true)`** — it still works now, but expect it to need replacing on a future MC version bump; don't be alarmed by the deprecation warning, just don't architect around it long-term. No existing Fishtastic test uses this — you'll be the first, so budget time to confirm it behaves as expected (e.g., does `player.level().getServer()` resolve correctly from it, since `TutorialManager`/`QuestTracker` both go through that path).

### 2.1 — `TutorialManager`
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/TutorialManagerGameTests.java`.
**Why:** the newest feature on this branch (`d019610 Implement fishing and quests flow tutorial`), a clean linear state machine over `TutorialStep`, currently at zero coverage.
**Approach:** `ServerPlayer player = helper.makeMockServerPlayerInLevel();` then drive the hooks directly — `onItemCrafted`, `onBaitLoaded`, `onHookCast`, `isTutorialSession`, `onMinigameStarted`, `onMinigameComplete`, `onQuestClaimed`, `advanceStep` — and assert `TutorialManager.getStep(player)` after each.
**Test ideas:**
- Crafting a `COPPER_FISHING_ROD` while in the default/`WAITING_FOR_CAST` step advances to `BAIT_LOAD` and grants 8 worms (check inventory). Crafting it again (already past that step) is a no-op.
- `onBaitLoaded` only advances from `BAIT_LOAD`, not from any other step (guard clause — this is exactly the "only fires from the right state" pattern the existing test suite already favors).
- `advanceStep(player, fromStep)` is a no-op if the player's current step doesn't match `fromStep` (stale/replayed client packet protection — this is a security-relevant guard, worth a dedicated test).
- Walk the full documented chain once end-to-end: `MINIGAME_INTRO → MINIGAME_CONTROL → MINIGAME_CATCH → CATCH_RESULT → QUEST_INTRO → QUEST_CLAIM → SHOP_BROWSE → COMPLETE`, confirming each `advanceStep` call lands on the right next step per the `switch` in `TutorialManager.advanceStep`.
- `onQuestClaimed` only advances `QUEST_CLAIM → SHOP_BROWSE` when the claimed quest ID matches `TutorialManager.TUTORIAL_QUEST_KEY` ("fishtastic:tutorial/first_catch") — confirm a non-matching quest ID is a no-op.

### 2.2 — `QuestTracker.onCatch` / `onCatchBatch`
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/QuestTrackerGameTests.java`.
**Why:** drives all quest progress in the game; zero coverage.
**Blocker to resolve first:** the actual matching predicate, `matchesObjective(QuestObjective, ItemStack, Holder<Biome>, TimeOfDay, WeatherCondition)`, is `private static` in `QuestTracker`. Two options, pick one:
  - **(a, preferred, smallest diff)** Widen it to package-private (`static boolean matchesObjective(...)`, drop `private`) so a test in the same Gradle module can call it directly with hand-built `QuestObjective` fixtures — no registry, no player, no server needed for this part. This mirrors how the rest of the codebase already exposes just enough surface for testing (e.g. `WormBinBlockEntity.canDeposit()`/`canAerate()` are public specifically so tests can probe state).
  - **(b)** Test only through the public `onCatch`/`onCatchBatch` entrypoints against a *real* shipped quest (e.g. `TutorialManager.TUTORIAL_QUEST_KEY`) using `helper.makeMockServerPlayerInLevel()` — exercises more integration surface but couples the test to specific datapack content that could change independently.
  Recommend (a) for fast, focused coverage of the matching rules themselves (species/tag/quality/biome/time/weather conditions, each independently and combined), and (b) for one or two true end-to-end smoke tests (a catch that should and shouldn't progress a real quest).
**Test ideas (once (a) is done):** each `QuestObjective` field (`targetSpecies`, `targetSpeciesTag`, `minQuality`, `biomeCondition`, `timeCondition`, `weatherCondition`) independently gates a match when present and is ignored when `Optional.empty()`; `minQuality` is an ordinal floor (a catch one tier below the requirement fails, exactly at or above passes).
**Also worth testing directly (no blocker, already public-ish):** `QuestTracker.getActiveDailies(registry, currentDay)` — same-day calls are stable/deterministic (seeded by `new Random(currentDay)`), different days can differ, and the result never exceeds `ACTIVE_DAILY_COUNT` (4) even with a larger registry. You'll need a small in-test `Registry<Quest>` — check how other registry-dependent tests in sibling mods build a throwaway registry for this shape of test (see `rock-reactors`'s codec-driven registry patterns per the modding guide), or construct a minimal `MappedRegistry` by hand.

### 2.3 — Networking round-trip tests
**File to create:** `common/src/testmod/java/grill24/fishtastic/gametest/PacketRoundTripGameTests.java`.
**Why:** 10 custom `StreamCodec` payloads, zero round-trip verification. Bugs here are invisible in single-player testing and only surface as multiplayer desync — exactly the failure mode manual QA is worst at catching.
**Mechanics (this is the part that needs spelling out — nothing in the existing suite does this yet):**
- The codecs are typed `StreamCodec<RegistryFriendlyByteBuf, T>`, not plain `ByteBuf` — you need a registry-aware buffer. Build one from inside the test: `RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());` then `PACKET.STREAM_CODEC.encode(buf, original)` followed by `T decoded = PACKET.STREAM_CODEC.decode(buf);`.
- **Gotcha:** vanilla `ItemStack` does not override `equals()`. Don't `assertEquals`/compare stacks directly — compare `getItem()`, `getCount()`, and relevant components individually (the existing test suite already does this style of field-by-field comparison for records, e.g. `FishCatchDataGameTests` comparing `bestSize()`/`bestQuality()` rather than whole-object equality).
**Priority targets (pick 2–3 to start, in order of "exercises the most codec machinery per test"):**
- `StartFishingMinigamePacket` — exercises a nested record (`TargetData`), a `List<>` wrapper, an enum-by-ordinal codec, and an `ItemStack` list all in one packet. Best single test for catching a codec regression anywhere in that stack.
- `PurchaseShopEntryPacket` — simplest packet, good smoke test that the harness pattern itself works before tackling the complex one.
- `QuestSyncPacket` or `LeaderboardResponsePacket` — both carry collections of custom records (`PlayerQuestState.QuestProgress` already has its own `STREAM_CODEC`, useful to verify in isolation too).

---

## Tier 3 — valuable but needs a production-code seam, not just tests

### 3.1 — `FishingMinigameManager` (the actual trust-boundary validation)
**Why this is hard, stated plainly:** `startSession`/`generateTargets` need a `ServerPlayer` holding a `CopperFishingRod` (with a bait component) *and* a live `FishingHook` entity assigned to `player.fishing` (the vanilla field `generateTargets` reads via `player.fishing`), plus real `FishProfile`/`Temperament`/loot-table registry data — all gettable in a GameTest world, but nobody has wired this combination together before. Worse, `handleMinigameComplete`'s validation logic (the actual security-relevant part — does it correctly reject a tampered `caughtTargetIndices` list?) operates on the private `activeSessions` map with no test seam: there is no way to inspect or pre-seed a session from outside the class today.
**Recommended approach — don't try to test this black-box. Add a minimal seam first:**
1. Spike `startSession` end-to-end with `helper.makeMockServerPlayerInLevel()` + `helper.spawn(EntityType.FISHING_BOBBER, pos)` assigned to `player.fishing` (check whether that field has a public setter or needs a different approach — this is genuinely unverified, confirm before committing to the design) + a rod+bait `ItemStack` in the mock player's hand. If `startSession` returns a valid (non `-1`) session ID, the hard part is solved.
2. For `handleMinigameComplete`'s validation specifically, consider adding a small package-private accessor (e.g. a test-only `@VisibleForTesting`-style method that exposes whether a given index list would be accepted, or simply make `ActiveSession`/`activeSessions` package-visible) so a test can assert: out-of-range indices are ignored (already has a `Fishtastic.LOGGER.warn` path — confirm it doesn't throw and doesn't award anything), a stale/already-removed session is a no-op, and `timeTaken < 20` ticks logs a warning but **does not block the reward** (read the code again before assuming this is intentional — it might be a real gap worth flagging back to the project owner rather than just testing the current behavior as correct).
3. Given the complexity, treat this as its own follow-up task rather than bundling it with Tier 1/2 — it will likely surface a real design question (should sub-20-tick completions be rejected, not just logged?) that's worth a product decision, not just a test.

### 3.2 — `ShopEntry.getActiveDailyShop`
**Quick add alongside 1.2** — pure static function, same shape as `QuestTracker.getActiveDailies`: deterministic per day (seeded `Random(currentDay)`), capped at `DAILY_SHOP_COUNT` (4), stable within a day, can differ across days.

### 3.3 — Datagen output validation
Lower priority and a different testing style entirely (not GameTest — these run at build time via `runDatagen`/`runData`, producing files under `src/main/generated`). If picked up, the right shape is a lightweight check that generated recipe/loot-table/tag JSON is non-empty and parses, not a GameTest. Treat as optional cleanup, not a priority.

---

## Explicitly out of scope (don't spend time here)

- **`ItemEffectManager`, all of `client/`, all of `mixin/`** — render-thread/`Minecraft.getInstance()`-dependent, structurally unreachable from a server-side GameTest. This is permanent manual-QA territory, not a backlog item.
- **Commands, Registration/Compat glue** — low complexity, low blast radius, fine to leave untested unless time is genuinely free.

## Housekeeping (do anytime, trivial)

- Delete `common/src/main/java/grill24/fishtastic/server/FishingLootHelper.java` — confirmed 0 bytes, zero references anywhere in the codebase.
- The two stale agent worktrees under `.claude/worktrees/` containing the abandoned `MultiItemPhysicsTest.java` are not part of this branch and weren't created by this task — leave cleanup of those to whoever manages worktree lifecycle; they're referenced above only as a usage-pattern reference for Tier 1.1.

---

## Suggested first PR scope

Tier 0 (0.1, 0.2) + Tier 1.1 (`FishingTarget`) + Tier 1.2 (`PlayerQuestState`) is a well-sized, self-contained first pass: zero production-code changes required, closes the single biggest risk item from the coverage report (`FishingTarget`), and closes the Shop/Economy gap as a side effect of testing quest state. Tier 1.3/1.4 are a natural second PR. Tier 2 and 3 each deserve their own PR given the investigation/seam work involved.
