# Fish companions world: sim implementation and verification

Measured on 2026-10-15 against the [world rework plan](fish-companions-world.md). This records the Minecraft-free implementation and its technical checks. It does **not** sign off the owner's readability, species-table or tank decisions. Loader builds, GameTests and in-game verification are recorded separately by the integration task.

## As built

- `CompanionWorld` carries immutable `WorldFeature` inputs and an explicit game tick. Features have a kind and stable ID; music carries source, start, length, last beat and normalized pitch. Existing constructors remain compatible. `Ambient.darkness` is 0 for bright, 1 for dark and NaN for unknown.
- `Mind.NOTICE` participates in goal commitment, threat and owner-following rules. Empty features plus `Ambient.NEUTRAL` consume no new randomness. Perchers watch and lean without taking a feature detour. Walkers require a dry, visible bank and a ground path for water; the bank search is cached for up to 20 ticks while the owner remains nearby.
- Indexed warmth uses the existing species-sized inspection steering under the Notice goal. The original bask, warming radius and warmth-hold calculation remain shared. `Ambient.warmth` is retained for compatibility fixtures; production warmth arrives as features.
- Water visits swim inward from the bank before descending, then rise again through the existing motor. Acquisition is limited to 3.6 blocks and destinations to the owner's 3.8-block local envelope. A walker uses checked ballistic outward and return arcs, with a swim impulse on actual water contact. No reaction places or teleports the fish.
- Lights require emission at least 10 and owner darkness above 0.45. Temperament controls loops, edge peeks, still ambusher holds and slow passes. A full-moon preference suppresses lights only when the ambient is clear and lacks `sky:blocked`.
- Songs attract the fish to the source when the owner is within 8 blocks; distant sources cause a dance beside the owner. Escorting fish can dance without surrendering their travel goal. The groove lasts up to 50 ticks after leaving. Notes bypass the noticing ramp and emit once per new beat; swimmer targets and physical walker hop heights follow pitch. Jukebox timing is a steady groove, not inferred song beats.
- Ordinary feature visits habituate per kind/ID, with 30-second cooldowns and novelty recovering over minutes. Ground water visits use a 60-second cooldown and a short performance to protect following and visibility. Songs and note beats bypass ordinary visit cooldowns; warmth retains its existing bask completion rules.
- Each member owns its mood transitions and emotes, including school and ground-group followers. Homecoming and water joy have visible beats; water joy does not add hidden locomotion multipliers. Cold has frost/shiver, glum has droop and a clearance-checked idle sink, sleepy has a drawn pose, rain glee has rolled/rising laps and splashes, and non-Nether fish emit steam in the Nether. The hidden cold/glum/sleepy curiosity, band and dwell multipliers were removed.
- Emotes are immutable one-tick outputs. The viewer retains a separate 16-tick marker history, so frames between beats still show events; production particles consume only the current tick. `noticedFeature()` exposes the active source for source-specific runtime checks.

## Retained test result

The final focused command was:

```powershell
$env:JAVA_HOME='C:/Users/curre/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2'
.\gradlew.bat :fishsim:test --tests '*WorldFeatureTest' --tests '*MoodTest' --tests '*CompanionInvariantTest' --tests '*FingerprintProbe' -PsimStdout -PfpAll=true
```

**53 tests passed, 0 failed, 0 skipped; build successful in 1m 21s.** This includes all three fingerprint probes, the existing pooled mood seen/fade guard, companion invariants, all new feature tests, exact indexed/legacy warmth trajectory comparisons and mixed-member regressions. This focused result is distinct from the final full standard suite run by the integration task. Earlier full-suite runs exposed the walker-water visibility defect; they are not recorded here as successful full-suite runs.

The old sleepy test's fixed 8% speed reduction assertion depended on the retired hidden dwell lever. It now retains the earlier-rest assertion and requires over 200 visibly `SLEEPY` samples in a 600-tick run.

Local evidence, relative to the repository root (under ignored build output):

- `fishsim/build/world-validation/world-final-probes.txt`: complete final focused console log.
- `fishsim/build/world-validation/feature-metrics.txt`: all matrix and fixed-camera measurements below.
- `fishsim/build/world-validation/fingerprint-world-before.txt`: original baseline run log, captured before sim edits.
- `fishsim/build/world-validation/fingerprints-before.txt` and `fingerprints-after.txt`: sorted fingerprint rows.
- `fishsim/build/world-validation/fingerprints-comparison.txt`: **1,038 before; 1,038 after; bitwise equal: True**.

The comparison includes `FP`, `FPA`, `FPG` and `FP2` rows: single agents, all configured body variants, ground groups and two-owner runs. Only neutral ambient/no-feature trajectories are required to remain identical; feature and mood behavior intentionally changes.

## Feature guard matrix

Each cell pools every `OwnerScript.NAMES` script, seeds 1-3, and 1,200 ticks per run. Water baseline and feature runs have the same real pond geometry. The guard stays at no more than **1 percentage point** pooled seen loss and no more than **3 additional unforced fades**. Teleport, elytra, creative flight and the intentionally chaotic corridor are excluded from the fade total, as in the existing guard. Percher here is benthic without a walking gait; walker uses `GroundGait.CRAWL`.

**All 25 cells pass.** Seen figures are percentages; fades are feature/baseline totals. These are pooled guards, not promises about every individual seed.

| Body | Feature | Seen | Baseline seen | Fades |
|---|---|---:|---:|---:|
| Swimmer | Water | 26.45% | 21.78% | 0/0 |
| Swimmer | Light | 27.66% | 24.95% | 0/0 |
| Swimmer | Jukebox | 27.14% | 24.95% | 0/0 |
| Swimmer | Notes | 25.98% | 24.95% | 0/0 |
| Swimmer | Warmth | 27.24% | 24.95% | 0/0 |
| Drifter | Water | 22.71% | 18.86% | 0/0 |
| Drifter | Light | 27.09% | 21.43% | 0/0 |
| Drifter | Jukebox | 24.88% | 21.43% | 0/0 |
| Drifter | Notes | 23.55% | 21.43% | 0/0 |
| Drifter | Warmth | 25.20% | 21.43% | 0/0 |
| Percher | Water | 13.22% | 13.22% | 0/0 |
| Percher | Light | 11.61% | 11.61% | 0/0 |
| Percher | Jukebox | 11.85% | 11.61% | 0/0 |
| Percher | Notes | 11.62% | 11.61% | 0/0 |
| Percher | Warmth | 11.10% | 11.61% | 0/0 |
| Walker | Water | 15.13% | 14.99% | 23/24 |
| Walker | Light | 17.11% | 16.90% | 0/0 |
| Walker | Jukebox | 22.80% | 16.90% | 0/0 |
| Walker | Notes | 16.13% | 16.90% | 0/0 |
| Walker | Warmth | 16.91% | 16.90% | 0/0 |
| Anchored | Water | 0.31% | 0.31% | 0/0 |
| Anchored | Light | 1.25% | 1.25% | 0/0 |
| Anchored | Jukebox | 1.38% | 1.25% | 0/0 |
| Anchored | Notes | 1.25% | 1.25% | 0/0 |
| Anchored | Warmth | 1.25% | 1.25% | 0/0 |

The anchored absolute seen percentages remain low even though their guard passes. This is a concrete limitation of these first-person harness runs and cannot establish readability for anchored reactions.

Fixed-camera swimmer runs (`idle_fixed`, seeds 1-5, 1,200 ticks) give the following additional reaction-share proxy. The no-feature baseline seen value is 35.28%; all feature runs have zero fades.

| Feature | Seen | Reaction share of visible ticks |
|---|---:|---:|
| Water | 71.55% | 42.53% |
| Light | 51.47% | 35.25% |
| Jukebox | 65.97% | 100.00% |
| Notes | 70.82% | 100.00% |
| Warmth | 59.92% | 39.03% |

Reaction share counts active Notice behavior, distinctive poses and emotes. Continuous music can therefore reach 100%; this does not prove an owner can identify a reaction.

## Physical and behavioral proofs

`WorldFeatureTest.swimmersAndDriftersActuallyEnterTheWaterThenSwimOut` runs both classes over seeds 1-5 for 1,200 ticks. Every run must enter a cell classified as real water, subsequently return above the surface, emit 1-3 sparse splashes that each coincide with actual water contact, and have zero leash events. This caught a real defect: an early downward target remained over the solid bank, so the motor correctly refused to dip despite displaying a pose. The final arc swims inward as it descends.

`walkerWaterExcursionUsesCheckedArcsAndReturnsToTheBank` requires a physical airborne phase, actual water contact, no solid penetration, and return onto the original bank within 0.5 horizontal blocks after 120 ticks. `groundNotePulseHeightFollowsPitch` requires real launch/landing impulses and over 0.25 blocks greater peak height for the high note.

`indexedWarmthRetainsTheBaskTrajectoryAndHold` compares legacy ambient warmth with indexed warmth at every tick for swimmer, drifter and benthic bodies of lengths 0.2 and 0.6, over 1,000 ticks each: positions, mood levels, heat hold and bask diagnostics are equal. Other tests cover immediate single-shot note beats, a song first heard while travelling, owner departure, moon/roof/darkness conditions, no dry dip at an out-of-band water feature, and independent school/ground-member moods and emotes.

## Viewer and headless review

Start the existing viewer with `:fishsim:runCompanionViewer`. **U** cycles `water`, `light`, `jukebox`, `notes`, `warm`, `cold`, `glum`, `sleepy`, `glee`, `homecoming` and `steam`. Existing **V** mood fixtures remain available. The interactive viewer currently draws its diagnostic overlay unconditionally; use the headless `--overlay-off` export for the saved overlay-free harness evidence.

Example headless export:

```powershell
.\gradlew.bat :fishsim:runCompanionHeadless '-PsimArgs=--script idle_fixed --feature water --overlay-off --ticks 480 --gif-ticks 480 --out build/world-scenes/water'
```

All eleven initial overlay-free exports are saved locally under `fishsim/build/world-scenes/<fixture>/`: `idle_fixed.gif`, `idle_fixed-sheet.png`, and `metrics.csv`. The water export was regenerated after the actual-contact fix. Its 480-tick seed-1 run has 85% seen, zero penetrations/fades and reaction share 0.575. The final longer five-seed guard figures above are the appropriate comparison for regressions.

The images use the software harness renderer, not Minecraft models or production particles. Terrain, camera direction and fish size affect how much a sheet shows. Source-specific runtime assertions should inspect `noticedFeature()` and actual contact/events rather than accepting any `NOSE`, `WATCH` or `DANCE` pose as proof of a particular feature. Owner naming of overlay-free viewer and in-game reactions remains required.
