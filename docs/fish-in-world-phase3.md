# Fish in World - Phase 3 record (one side-effect-free fishing pool resolver)

Session continuation checkpoint: [fish-in-world-handoff.md](fish-in-world-handoff.md).
Plan: [fish-in-world-implementation-plan.md](fish-in-world-implementation-plan.md) §Phase 3.
Phase 2 (bounded natural-water domains) is closed; see [fish-in-world-phase2.md](fish-in-world-phase2.md).
Status: **gate closed 2026-10-13** (all gate checks are test-based; an independent read-only review
round confirmed behavior identity — clean — and the should-fix it found, a missing fingerprint
pool-sensitivity test, plus its nits were resolved in a follow-up round. No client-visible change,
so no capture review applies and no client was launched).

**No phases past 3 are in this work.** No networking, no ambient pool selection, no density
policy, no settings: nothing in normal play reads the new snapshot or resolver yet. Phase 3 is an
extraction + capture that keeps real catch behaviour bit-identical.

## What Phase 3 adds

One resolver answers *"given this environment and this equipment, which species are in the pool
right now and with what final raw weight?"* Real fishing, the Almanac preview, and (from Phase 4)
ambient context snapshots all consume the same answer, so they cannot drift apart.

- **`FishingPoolResolver`** (`common/.../server/FishingPoolResolver.java`) — static, RNG-free:
  - `EnvironmentContext(biome, timeOfDay, weather, moonPhase, zones)` — lifted out of
    `FishingMinigameManager`'s old private record (the old `zone` field is now `zones`, a set).
  - `PersonalEquipment(inventoryDiamonds)` + `NEUTRAL`, `fromInventory(Player)`,
    `fromHookOwner(@Nullable FishingHook)` — the diamond count is now an explicit input instead of
    being read back out of `LootParams.THIS_ENTITY` inside the item.
  - `Inputs(baitEffect, charmEffect, personalEquipment)` + `of(...)`,
    `standardWormsBaseline()`, `heldRodPreview(rod, equipment | holder)`, `vanillaAllowed()`.
  - `WeightedFish(item, weight)` — the lifted candidate shape, final raw `int` weight.
  - `SpeciesContext(environment, candidates, fingerprint)` — candidates in pool order, immutable.
  - `SessionSnapshot(origin, rodId, inputs, species)` — what a live session retains.
  - `resolveEnvironment(level, pos, charmEffect)`, `candidatePool(...)`, `resolve(...)`,
    `topWeighted(context, count)`.
- **`FishingMinigameManager`** now delegates: `generateTargetsForOrigin` builds the same loot
  params as before, resolves the environment and inputs, calls
  `FishingPoolResolver.resolve(...)`, and rolls rewards through the resolver's candidate list with
  `FishtasticFishItem.sampleRandomFish` unchanged (`nextInt(totalWeight)` -> quality -> size order
  preserved). `computeTopWeightedFish` (Almanac preview) consumes `topWeighted` of the same
  context. `startTutorialSession` and `seedSessionForTest` build an environment but no snapshot.
- **Session capture**: `startSession` stores a `SessionSnapshot` built from the run that generated
  the targets — `origin` (hook position, or the session block center when there is no hook), the
  cast rod's registry id (diagnostics only, not hashed), the exact `Inputs`, and the
  `SpeciesContext` (environment, ordered candidates with weights, fingerprint). Exposed read-only
  via a new `getSessionSnapshot(UUID)` seam that Phase 4 will serve from. Seeded (tutorial/test)
  sessions deliberately capture `null`: they never resolve a pool.
- **`AcuteIapsisItem`** overrides `getAdditionalWeight(int diamondBonus)` — the hook-dependent
  input is now a parameter. Note for future readers: the old comment claimed "10 extra weight per
  diamond", but the arithmetic has always been +1 per diamond item; preserved deliberately and
  now documented in the class.

### Ordering and RNG guarantees

- `candidates()` is in `#minecraft:fishes` tag order after filtering — never re-sorted.
  `sampleRandomFish` and `topWeighted` both depend on it; ranking ties keep pool order (stable
  sort).
- `resolve` consumes no random numbers, ever. A preview cannot shift the catch RNG sequence.
- Real roll path: `nextInt(totalWeight)` first, then quality (`sampleRandomQuality`), then
  `applyQualityAndSize` — same call order and same `RandomSource` as before the extraction, so
  seeded replays draw identically (pinned by the byte-identical dump below and by the resolver
  test that replays a seeded `sampleRandomFish`).

### Explicit input modes

| Mode | Inputs | Notes |
|---|---|---|
| standard-worms baseline | `Inputs.standardWormsBaseline()` = registered `BaitEffect.WORMS` + local environment + `PersonalEquipment.NEUTRAL` | an inventory full of diamonds changes nothing — the baseline is neutral *by construction*, not by accident |
| held-rod preview | `Inputs.heldRodPreview(rod, equipment)` — the rod's actual bait slot (empty = `NO_BAIT`) and charm slot, plus whatever equipment the caller declares | reads no hook and consumes nothing; the hook slot is deliberately ignored (see below) |
| active-session snapshot | not an input mode — the session keeps the `SpeciesContext` it already resolved | never re-derived from later equipment; seeded sessions hold `null` |

`heldRodPreview(rod, Player)` documents that the holder's own carried diamonds stand in for the
hook-owner lookup for the ordinary case (the player holding the rod is the one who would cast it);
nothing else about a cast is simulated.

## Decisions, and what was rejected

| Decision | Alternatives rejected, and why |
|---|---|
| Resolver takes `int diamondBonus`, not the hook or player | Passing the live hook keeps the resolver reading world state; an `int` is the smallest honest input, and `PersonalEquipment.fromHookOwner` keeps the historical lookup in exactly one place. |
| `Inputs` carries no hook field | `HookEffect` has **no species-affecting field at all** (quality bias, trash delta, treasure delta only). A hook input would be a decorative parameter that can never change the answer. Documented rather than faked. |
| Baseline uses `PersonalEquipment.NEUTRAL`, not the caller's inventory | "No rod held" is a statement about *equipment*, not about the player: the spec says neutral personal equipment. A baseline that read the inventory would silently make the no-rod pool depend on carried diamonds. |
| Preview reads the charm *slot's* `CHARM_EFFECT`, never fires effects | A Storm Charm is consumed by `startSession` *before* pool resolution and carries no `CHARM_EFFECT`, so a preview structurally cannot trigger a storm or consume the charm. Tested. |
| Snapshot stored on the session, not recomputed on demand | The requirement is explicitly "STORED result, not a re-resolve": later equipment must not rewrite what generated the rewards. |
| Fingerprint over a canonical string of *effective* inputs + final weights (FNV-1a 64) | Hashing raw profile content was rejected: a quality-only profile edit must not invalidate a species context (that is the point of the fingerprint). Hashing the weights covers profile/datapack edits that actually change the answer, and tag membership covers membership edits. |
| Weight-level fingerprint, not species-list hash | Weight-only changes must be *detectable* by later phases (they alter abundance gradually, not eligibility), and they are the most common equipment effect. |
| Environment stored once, inside `SpeciesContext` | `ActiveSession` previously would have duplicated environment + snapshot; storing the snapshot and reading `snapshot.species().environment()` keeps one copy and makes "the environment the targets were rolled from" impossible to desync from the environment the packets report. |
| Tutorial/test-seeded sessions capture `null` | They hand out scripted rewards and never resolve a pool; capturing a fabricated context would be a lie Phase 4 could act on. |

### Species-affecting inputs (the exact fingerprint contents)

Hashed, in this order: biome (registry id, or identity hash for direct/test holders), time-of-day,
weather, moon phase, the sorted zone set, bait species fields (exclusive pool tag location,
`modFishMultiplier`, `rarityFlattening`, and each group affinity's tag/multiplier/
non-member-multiplier/rarity-exponent, floats via `floatToIntBits`), charm species fields
(night-multiplier bonus + the same affinity shape), diamond count, and the ordered candidate
`id:weight` pairs.

Deliberately **not** hashed, because they cannot change the candidate list or its weights: bait
luck/treasure/trash/target-count/quality-bias/small-bobber, charm input-force/outline/trash/
treasure/auto-pile/bait-save/sunset-extension, every `HookEffect` field, player luck, forced
temperament/difficulty/quality, target count, and the casting rod's identity (diagnostics only).
Quality, difficulty, treasure and size are rolled *downstream* of species selection.

Two subtleties worth keeping:

- `forceNightFishing` (Luna Charm) is species-affecting only *through* the environment's
  time-of-day: `resolveEnvironment` pins `timeOfDay = NIGHT` and the fingerprint hashes that. The
  flag itself is not hashed (pinned by test).
- `BaitEffect.fromStack` scales by the bait stack's `FISH_QUALITY`, so bait quality **is**
  species-affecting and is captured — because the *effective* (scaled) `BaitEffect` is what gets
  hashed.

## Tests

Characterization-first: a throwaway probe test dumped 15 scenarios (pool order, weights, Almanac
top-3, and seeded `draws=` replays) from the pre-refactor code; after the refactor the same probe
produced a **SHA256-identical dump** before it was deleted and its values baked into the permanent
tests.

- `common/build/phase3-characterization-before.txt` (pre-refactor)
- `common/build/phase3-characterization.txt` (post-refactor)
- both `9AF758BCBF98102134661133D805BCB1B079ACE6E20E1363D86D582FFCE87AEA`

`FishingPoolResolverTest` (plain JUnit, headless registries) — **24 tests, 0 failures, 0 errors**
(`common/build/test-results/test/TEST-grill24.fishtastic.server.FishingPoolResolverTest.xml`),
backed by a new `FishingPoolFixture` that builds small synthetic `ITEM`/`FISH_PROFILE` registries
with real vanilla `Items` registered under their own keys (so the vanilla branch runs for real),
tag order fixed, and the mod's data component types registered reflectively the way the loader
entrypoints do (the fixture leaves those built-in registries re-opened for the rest of the test
JVM; its class javadoc documents that):

1. `standardWormsBaselinePinsPoolOrderWeightsAndRanking` — WORMS river day: 20/12/8/6/10/8 in tag
   order with vanilla interleaved; Almanac top-3 = river_darter, exotic_blenny, profileless_minnow.
2. `baselineModeCarriesNeutralPersonalEquipment`
3. `zoneGateAndEnvironmentSamplingChangeThePool`
4. `noBaitKeepsVanillaInterleavedAndPinsVanillaWeights` — cod 200, salmon 200, tropical 50,
   puffer 25 with mod fish at 3/1/1/1/1/1.
5. `nullBaitKeepsVanillaAndLeavesModFishUnscaled` — same pool, mod weights unscaled (20/12/8/6/10/8).
6. `noBaitNightLunaPinsNightMultiplierOnBothBranches` — 250/250/62/31 + 1/2/1/1/1/1.
7. `duskRainMultipliesRiverDarterAndLeavesOthers` — darter 20 -> 80.
8. `lunaCharmRaisesNightWeightsAndReordersTheTopThree` — blenny, darter, minnow.
9. `bananaCharmBoostTouchesOnlyTheYellowGroup` — cyprinid 8 -> 12.
10. `blazedGrubExclusivePoolNarrowsToExoticFish` — pool = exotic_blenny only.
11. `gummyWormsFlatteningTouchesOnlyCuratedGroupMembers` — blenny 12 -> 11, darter untouched.
12. `diamondBonusAffectsOnlyAcuteIapsisAndMovesTheFingerprint` — iapsis 6 -> 13 with 7 diamonds.
13. `rankingKeepsPoolOrderForTiesAndIsStableAcrossRepeatedResolves`
14. `heldRodPreviewReadsTheBaitAndCharmSlots`
15. `heldRodPreviewIgnoresTheHookSlotEntirely`
16. `heldRodPreviewWithAnEmptyBaitSlotResolvesLikeNoBait`
17. `resolvingAPreviewConsumesNoRandomNumbers`
18. `fingerprintIsStableAndSensitiveToEverySpeciesAffectingInput`
19. `fingerprintIgnoresInputsThatCannotChangeSpecies`
20. `fingerprintHashesTheResolvedPoolItself` — same environment and inputs, only one profile's base
    weight moved (via `FishingPoolFixture.profilesWithBaseWeight`) -> fingerprint differs. Added by
    the review fix round: before it, deleting the whole `|pool=` hash section kept every other test
    green.
21. `fingerprintExactValueIsPinned` — one exact FNV-1a 64 value (`0xFCF605A39DF5AAB9`) for the
    RIVER_DAY baseline, with a comment that it may only move when a species-affecting input is
    added/removed/reordered or the fixture itself changes. The fixture biome is registry-keyed so
    the pin is stable across JVM runs.
22. `inputsRequireExplicitPersonalEquipment` — `Inputs.of(WORMS, null, null)` throws NPE at
    construction, not later at resolve time.
23. `candidatePoolIsImmutable`
24. `seededRewardDrawsReproduceThePreRefactorSequence` — the dump's `draws=` replays made
    permanent: all **15 scenarios** (12 draws each through `sampleRandomFish`, pinning species,
    quality and size at three decimals, e.g. `profileless_minnow:COMMON:66.541`) with the dump's
    per-scenario seed (42L; 7L for `worms_river_night_luna`) and quality bias (0.0; 2.0 for gummy
    worms and four-leaf; 0.5 for blazed grub). This is the durable RNG-order pin the
    characterisation probe no longer provides.

GameTests (registered on both loaders, shared bodies in
`common/src/testmod/.../FishingMinigameManagerGameTests.java`):

- `start_session_captures_resolved_species_snapshot` — a real cast with a real hook: snapshot
  non-null, origin equals the hook position, candidates non-empty with every weight >= 1, the
  environment equals an independent `resolveEnvironment` at that position, re-resolving with the
  stored inputs reproduces the fingerprint, rod id recorded.
- `personal_equipment_reads_carried_diamonds_and_baseline_stays_neutral` — 7 carried diamonds are
  counted from inventory and via the hook owner, and the standard-worms baseline still reports 0.
- `snapshot_records_main_hand_rod_when_both_hands_hold_rods` — pins the known main-hand-first seam
  (see limitations) by recording the rod the manager actually resolved with.
- `test_seeded_session_captures_no_species_snapshot` — seeded sessions hold `null`.

## Files

New:

- `common/src/main/java/grill24/fishtastic/server/FishingPoolResolver.java`
- `common/src/test/java/grill24/fishtastic/server/FishingPoolResolverTest.java`
- `docs/fish-in-world-phase3.md` (this file)

Modified:

- `common/.../server/FishingMinigameManager.java` — delegates to the resolver; `GeneratedTargets`
  carries `(targets, species, inputs, origin)` — the origin the pool was actually resolved at is
  carried out of `generateTargetsForOrigin` and stored in the snapshot, never re-derived from the
  hook/session position (review fix round); `ActiveSession` stores the snapshot; new
  `getSessionSnapshot(UUID)`; deleted the private environment record, instance
  `resolveEnvironment`, `getFishPool` and the old preview ranking.
- `common/.../item/FishtasticFishItem.java` — `getAdditionalWeight(int)` instead of
  `(LootParams)`; `sampleRandomFish`/`getFishingLootWeight` take resolver types.
- `common/.../item/AcuteIapsisItem.java` — diamond count becomes an explicit override input.
- `common/src/test/java/grill24/fishtastic/server/FishingPoolFixture.java` — component and
  rod/carrier-item support for the new tests, plus a `profilesWithBaseWeight` variant registry and
  a registry-keyed fixture biome (so the exact fingerprint pin is stable across JVM runs).
- `common/src/testmod/.../FishingMinigameManagerGameTests.java` — 4 new tests.
- `fabric/src/testmod/.../FishtasticFabricGameTests.java`,
  `neoforge/src/testmod/.../NeoForgeGameTestRegistration.java` — registrations.
- `docs/fish-in-world-handoff.md`, `docs/fish-in-world-implementation-plan.md` — status and seam
  updates.

Not touched (hard constraint): no client/networking/density/settings work, no ambient-runtime
edits, and no change to catch behaviour — the dump hash above is the evidence for the latter.

## Recon findings recorded for later phases

- `HookEffect` has no species-affecting field; the resolver therefore has no hook input at all.
- Every produced weight goes through `Math.max(1, ...)`, so no zero-weight candidate exists today
  — the Almanac's `weight > 0` filter is a no-op in practice, kept for safety.
- The Almanac's `LootParams`/luck plumbing was species-irrelevant; dropping it from the weight
  math changes nothing (covered by the dump).
- A Storm Charm is consumed in `startSession` before pool resolution, and carries no
  `CHARM_EFFECT`, so `heldRodPreview` cannot trigger or consume one.
- `BaitEffect.fromStack` bakes the bait stack's quality into the effect, so bait quality is
  species-affecting and is captured through the effective effect.
- `forceNightFishing` affects species only via the environment's time-of-day.
- `AcuteIapsisItem`'s old comment ("10 extra weight per diamond") never matched its arithmetic
  (+1 per diamond); preserved, documented, and now pinned by test 12.
- `Identifier` construction must route through `Ids.of(...)`; the `:common:idConstructionGuard`
  and `:common:java17ApiGuard` checks scan test sources too.

## Validation

Run 2026-10-13, in the plan's order (JUnit XML as the source of truth, not build exit codes). All
green; nothing in this phase needs a client. Counts below are the review fix round's re-run (the
round touched only common sources and docs).

| Command | Result |
|---|---|
| `.\gradlew.bat :fishsim:test :common:test` | fishsim 437 tests / 36 skipped / 0 failures (UP-TO-DATE — no fishsim sources or inputs touched); common **803 tests / 802 passed / 1 skipped / 0 failures / 0 errors** (35 suites), of which `FishingPoolResolverTest` is **24 tests** |
| `.\gradlew.bat :fabric:runGametest` | **332/332 passed**, 0 failures, 0 errors, 0 skipped — `fabric/build/junit.xml`; all four Phase 3 tests present by name (`start_session_captures_resolved_species_snapshot`, `personal_equipment_reads_carried_diamonds_and_baseline_stays_neutral`, `snapshot_records_main_hand_rod_when_both_hands_hold_rods`, `test_seeded_session_captures_no_species_snapshot`) |
| `.\gradlew.bat :neoforge:runGametest` | **326/326 passed**, 0 failures — re-run after the fix round because that round touched the manager; NeoForge's runner writes no per-test JUnit XML, so "my tests ran" is verified by count: the registration file holds 325 `register(...)` calls plus 1 `minecraft:default` test, exactly the 326 executed, and 322 before this phase's four additions |
| `.\gradlew.bat build` | **BUILD SUCCESSFUL** (exit 0); final XML: common 803 tests / 1 skipped / 0 failures / 0 errors, fishsim 437 / 36 skipped / 0 failures |

## Limitations and known edges

- The fingerprint is only comparable within one world session when a biome `Holder` has no
  registry key (direct/test holders hash by identity).
- Headless tests can only vary biome through the fixture's single shared (now registry-keyed) biome
  holder, so biome sensitivity is pinned co-variantly (via weights and the fingerprint) rather than
  through two distinct real biomes.
- `PersonalEquipment.fromInventory` vs. the hook owner are claimed equivalent only for the
  ordinary case where the rod holder casts; an explicitly different owner would need Phase 4 to
  pass the real owner.
- Known seam carried into Phase 4: fishing picks the main-hand rod first, while a real offhand
  cast is reported with an explicit hand. The snapshot records what the manager actually resolved
  with (`rodId`), so a later phase can reconcile instead of guessing.
- `getSessionSnapshot` is a read seam only; nothing in play consumes it yet.
- `AcuteIapsisItem.getAdditionalWeight` was narrowed from public to protected during the
  extraction (it is only ever called by `FishtasticFishItem`). The 1.21.1 and 1.20.1 port branches
  are out of scope here: before porting this change they must confirm no external subclass or
  caller overrides/calls it from another package (the narrowing is a source-level break for any
  such call site, though there is none in this tree).
- Coverage gap (noted by the review, not fixed): no GameTest asserts the tutorial path's null
  snapshot (`startTutorialSession`), and none asserts that a captured snapshot's `inputs()` are
  the ones recorded — `start_session_captures_resolved_species_snapshot` re-resolves from the
  stored inputs but does not fail if `inputs()` itself were empty/null. Seeded sessions' null
  snapshot is covered; the tutorial path rides the same code with no assertion of its own.

## Phase 3 gate

| Requirement | Status |
|---|---|
| Resolver output matches pre-refactor fishing and Almanac behaviour across representative contexts | met — SHA256-identical characterization dump; 15 scenarios |
| Baseline matches standard-worms fishing | met — Worms + local environment + neutral equipment, pinned at 20/12/8/6/10/8 |
| Previews consume nothing, trigger no storms, do not advance catch RNG | met — preview takes no `RandomSource`; Storm Charm structurally invisible to it |
| Existing catches retain their behaviour | met — same order, weights, `nextInt(totalWeight)`/quality/size sequence; real-roll path untouched downstream |
| Explicit inputs including hook-dependent parts; no `LootParams` in the resolver | met — `PersonalEquipment` input; `ActiveSession` capture; tutorial/seeded sessions hold `null` |
| Fingerprint over species-affecting inputs only | met — species-only canonical hash; quality/treasure/target-count/luck excluded and pinned by test 19 |
