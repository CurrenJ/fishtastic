# Fish in World — Phase 5: weighted populations and equipment transitions

Updated 2026-10-13. Automated gate closed on Fabric and NeoForge. Production activation and
lifecycle controls are recorded in [Phase 6](fish-in-world-phase6.md).

## Population policy

`VisualSampler` consumes the server's final raw fishing weights. It restricts to supported
visuals, locomotion/size gates and a bounded whole-body fit probe before normalizing, then uses
75% raw probability / 25% square-root probability. No fishing weights or catch RNG change.
At most 32 deterministic column centers are probed per species; omissions are counted explicitly.
Each patch has its own SplitMix64 stream (`AmbientRandom`), separate from catches and pose jitter.

Individual slots receive independent weighted choices, never unchecked schools. Compatible fish
retain IDs, species, positions, headings and animation state across context changes. Excluded fish
retire through the shared world render path's 40-tick shrink fade. Quality-only changes do not
rebuild samplers or alter residents. Weight-only changes affect replacements through natural
turnover, not an immediate population reset. Geometry changes rebuild representability even
when the authoritative fingerprint stays the same.

New choices have uniformly sampled lifetimes of 1800–3000 ticks, independent of species (90–150
seconds, two minutes on average), as requested during playtesting on 2026-10-13. Ordinary
turnover shifts weight-only abundance over that longer interval. Re-entry choices keep
their original expiry and therefore only their remaining lifetime.

`OK` with an empty candidate list retires everyone. Pending/stale/rejected states block spawning
and allow a 100-tick grace, followed by a short invalidation fade. Unknown and empty are distinct.
Domain revival can cancel only domain-loss retirement, never eligibility or lifetime retirement.

## Stable re-entry and equipment choices

Restoring RNG position alone did not prevent a fresh set of draws on re-entry. The resumed
implementation adds `AmbientPopulationRolls`: thirty-two bounded slots per patch, with at most four
context records. Eligible choices carry across weight changes; returning to a previous context
reuses its unexpired choice. Excluded choices cannot spawn. Natural expiry allows new draws.

Dropped patches retain their stream, slot choices and bookkeeping for at most 600 ticks and
sixteen patches. No live render states or individuals survive a dropped domain. The original
expiry clock keeps turning while absent. Full world changes/reloads/disconnects and movements
above 64 blocks in a tick clear the cache. The retention scene uses a shorter trip outside the
radius; its earlier 136-block teleport correctly triggered the new teleport cleanup and was fixed.

## Validation

| Check | Final result |
|---|---|
| fishsim JUnit | 442 tests, 0 failures/errors, 36 existing skips |
| common JUnit | 854 tests, 0 failures/errors, 1 existing skip |
| Fabric GameTests | 347/347 passed |
| NeoForge GameTests | 341/341 passed |
| `ambientpopulation`, Fabric | 23/23 passed |
| `ambientpopulation`, NeoForge | 23/23 passed |
| Packaged build | passed; new ambient classes verified in both 2.0.1 jars |

Sampler tests use up to 200,000 draws, check the independently computed mixture and distinguish
it from raw odds. Turnover tests pin species-independent lifetimes and long-run occupancy.
Roll-cache regressions alternate contexts hundreds of times without new draws, reject excluded
choices, preserve expiries on re-entry, and permit natural turnover.

Both clients measured 42 ticks from applied exclusion to the last excluded resident disappearing.
Quality-only switches retained every tracked identity with zero sampler rebuilds/exclusions.
Across 2400 ticks, Fabric recorded 34 births / 29 lifetime retirements, NeoForge 30 / 27. Their
observed bluegill occupancy was 0.245 / 0.231 against configured visual share 0.254; all species
with visual share at least 0.13 appeared. This small live window is a coarse smoke check, not a
precise statistical balance claim. Retained state restored on both loaders without reseeding.

Evidence: `fish-in-world-phase5/final-validation.log`, `capture-fabric-final.log`,
`capture-neoforge-final.log`, and eight curated images in `screenshots/`. The final NeoForge
lifecycle rerun has its own `capture-neoforge-lifecycle-final.log`. Disposable worlds are ignored.

## Gate table

| Clause | Evidence | Status |
|---|---|---|
| Rod/no-rod, bait/charm retain common identities and motion | both loaders' applied/carry/nonexcluded checks | PASS |
| Exclusions retire within measured transition | 42 ticks after acceptance on both loaders | PASS |
| Long-run occupancy follows visual weights | sampler/turnover statistical tests plus live coarse windows | PASS |
| Quality-only changes leave population untouched | same fingerprint/weights, zero sampler rebuilds, all tracked IDs retained | PASS |

Representability, density, spatial context boundaries, deep water and production cleanup are
covered in Phase 6. The world fish are entirely client-local; no progression or targetable
entities were added. Human gameplay tuning and GPU performance remain outside these CPU checks.

Post-playtest update: see [small-island visibility adjustment](fish-in-world-island-playtest.md).
Ambient display now uses the upper four blocks of water and lets large connected patches use
the existing global density budget. Earlier eight-fish performance captures are historical.
