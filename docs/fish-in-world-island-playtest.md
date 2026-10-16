# Fish in World — small-island visibility adjustment

Updated 2026-10-13 after the user's ordinary ocean-island playtest.

## Observed evidence

`neoforge/run/logs/latest.log` at 10:52:27 showed `indivs=8/16`, a 1333-column ocean patch
with eight residents and a valid 22-species authoritative context. The small second patch had
no residents and was retrying placement. This establishes that the population had not stopped;
the original debug output did not record individual positions/depths or the ray's retirement
reason, so it cannot establish exactly why that particular ray faded or which fish were visible.

The implementation scattered fish through the whole discovered vertical band, up to sixteen
blocks below the player, and capped every connected patch at eight individuals. That produced
an unnecessarily sparse, deep display in a large ocean. The adjustment keeps the visual domain
in the upper four blocks of each water column and allows one large patch to use the existing
global density budget. Default 50% remained at most sixteen individuals and full density at most
thirty-two in this build (the caps were raised to 32 / 64 later the same day, see below). Small pools still scale by volume. The artificial lower face is marked open
and supplies no invented seabed, so bottom-associated species below that display band are omitted.
Fishing weights, eligibility, RNG and rewards are unchanged.

`AmbientPopulationRolls` now supports thirty-two bounded slots per patch. Diagnostics include
world submission count, retirement-reason totals, actual per-patch targets and the nearest three
fish's coordinates, depth, fade and retirement reason. This replaces the misleading fixed `/8`
target in a patch whose density/volume target could already be lower than eight.

## Validation

- `:common:test`: 854 tests, zero failures/errors, one existing skip.
- Both loader shadow jars rebuilt successfully after the isolated client exited.
- New `ambientisland` NeoForge scene: deep water, a small stone island, normal 50% production
  settings; all five checks passed: population, depth, world submissions, global budget and
  sustained occupancy. The scene reached sixteen residents and still had sixteen after 800
  additional ticks; active depths stayed within four blocks of the surface.
- Curated shore/underwater images are in `fish-in-world-phase5/screenshots/`. The shore shot shows
  small fish silhouettes through ordinary water. The underwater wide view is partly occluded by
  the island wall; submission/depth/occupancy assertions supply the stronger automated evidence.
- Runtime mean through that scene: 0.407 ms; submission mean 0.057 ms. Cold peaks 35.651 / 30.245 ms.
  These are session CPU counters on the existing reference machine, not GPU/frame percentile claims.

Evidence: `fish-in-world-phase5/island-fix-validation.log`, `capture-island-fix.log`, and the two
`selftest-neoforge-ambientisland-*` images. The new case was captured on NeoForge; Fabric compiled
and packaged the shared changes but did not run a second island capture.

Reopen the normal dev client and revisit the same island. If visibility remains poor, run
`/fishtasticambient debug`; depths and retirement reasons are now available in its chat log.

## Population cap increase

The user requested a higher maximum on 2026-10-13. Caps now scale to 16 / 32 / 64
individuals at 25% / 50% / 100%. Retained choices support all 64 slots. Discovery bounds
and usable-volume targets are unchanged, so small pools retain their previous targets;
large patches need 768 usable blocks cubed to reach the cap at 50% or 100%.
The sixteen-fish measurements above describe the earlier build.

The isolated NeoForge island scene passed all five checks at the increased default cap:
32 residents at the population and sustained checkpoints. Runtime mean at the final checkpoint
was 0.494 ms; submission mean 0.091 ms; session peaks 55.629 / 15.528 ms. These counters
predate the subsequent lifetime/fade changes and remain CPU measurements, not GPU benchmarks.
Evidence: `fish-in-world-phase5/capture-density-increase.log`.

## Continuity and placement

Further playtesting requested a two-minute average lifetime. New choices now last 1800–3000
ticks (90–150 seconds), followed by the existing two-second expiry fade. Eligibility and
water invalidation still retire early. Species-independent lifetime sampling and retained
choice expiry remain intact. Weight-only composition changes consequently take longer.

Garden eels were spawned at a random height in the water band, and anchored locomotion
preserved that height. Floor residents now spawn on the actual bed; representability probes
also check that bed height instead of midwater. This applies to crawlers as well as eels.

Retirement rendering now freezes the fish's pose and shrinks around the resolved item model's
centre; planted creatures shrink around their buried base. Re-entry revival clears the frozen
pose. Regression checks cover off-centre geometry, planted base invariance, floor contact on
flat and slab beds, lifetime distribution and long-run species abundance.

Validation: common regression suite has 857 tests, zero failures/errors and one existing skip.
The isolated NeoForge `ambientfloor` scene passed population, eel-bed and continuity checks
(plus shader loading). Five garden eels had zero bed clearance; the final checkpoint sustained
64 fish with zero expiry retirements during the approximately forty-second idle interval.
Runtime/submission session means at that checkpoint were 0.466 / 0.110 ms, peaks 37.446 /
16.417 ms. Four patches were shown. Invalid placements still retired normally (14 observed).
These are short-session CPU counters on the reference host, not full performance benchmarks.

The first eel check failed because an earlier deep fixture left lower disconnected water and
unknown beds. Replacing that fixture with unsupported sand then stalled vanilla falling-block
ticks; the verified isolated client was stopped. A fresh capture with a supported sand floor
passed. Evidence: `fish-in-world-phase5/capture-floor-continuity-final.log`; the earlier attempts
and thread dump are retained for diagnosis. Fade pivot invariance is verified by unit tests;
the close eel screenshot was taken inside startle range and does not show an emerged eel clearly.

Both loader shadow jars rebuilt successfully with the final shared changes, after correcting
the new scene's `List.getFirst()` call to respect the repository's Java 17 backport guard.
Final checks: `fish-in-world-phase5/playtest-tuning-final-validation.log`.
