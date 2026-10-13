# Fish in World — Phase 7: acceptance and release record

Updated 2026-10-13. Automated acceptance complete; no human gameplay sign-off claimed.

## Acceptance matrix

| Concern | Evidence | Status |
|---|---|---|
| Shared tank appearance/motion, ordinary light/fog/depth | Phase 1 parity captures and regression tests; current underwater/coexistence captures | PASS: prior parity gate plus reviewed current underwater captures |
| Loaded-water discovery, containment and budgets | Phase 2 tests/captures, context boundary and deep-water regressions, large-water lifecycle scene | PASS: both loaders |
| Rod/no-rod, bait and charm transitions | `ambientpopulation` identity carry, exclusion fade, legal pool and quality no-op checks on both loaders | PASS: both loaders |
| Visual weights vs raw fishing odds | Independent mixture/statistical tests and live long-run occupancy; resolver characterization unchanged | unit tests green |
| No accelerated rolls through switching/re-entry | `AmbientPopulationRollsTest` alternates pools hundreds of times without draws; original expiries survive re-entry; bounded runtime retention scene | PASS: unit tests and both loader scenes |
| Environmental and spatial boundaries | Shared biome/zone context partitions, context-change revalidation test, live weather invalidation | PASS: unit test and both loader scenes |
| Authoritative snapshots and multiplayer isolation | Phase 4 dedicated server/client fingerprint agreement, stale response rejection, packet bounds and both loaders' GameTests | PASS: fresh dedicated run and 347/347 Fabric, 341/341 NeoForge GameTests |
| Active cast snapshot precedence and side-effect-free previews | Phase 3/4 resolver/session GameTests; client watches cast session/hook changes | prior gate closed |
| Enable/density/disable and lifecycle | Both loaders' automatic lifecycle scene, zero work while off, reload, dimension, teleport, stale session | PASS: both loaders |
| Performance with tanks, companions and vanilla fish | Reference-machine pond and larger-water CPU counters, bounded discovery, encoded payload bytes | PASS: counts bounded; CPU results and cold spikes recorded in Phase 6 |
| Packaged release jars | `build`, jar entry verification | PASS: build and new-class entry checks in both jars |

## Final tuning and unsupported cases

The visual mixture remains 75% raw probability / 25% square-root probability. The baseline
plains/worms pool recorded raw bluegill share 0.286 vs visual 0.254, and ornate bichir 0.004 vs
0.007. Flattening changes visibility, never catches or Almanac rankings. Statistical sampler
tests use 200,000 draws and distinguish the configured mixture from raw odds; live eight-slot
windows only establish a coarse occupancy check, not precise balance.

Eligibility retirement fades for 40 ticks after the accepted context; requests/network latency
add to equipment-to-disappearance time. Full density allows 64 individuals / four patches;
default density is 50%. Lifetimes are uniform 1800–3000 ticks for new choices, averaging two
minutes as requested during playtesting. Re-entry uses the
remaining original lifetime, never a newly extended lifetime. Retention: 600 ticks / sixteen
patches, up to four context choices per slot and sixty-four slots per patch.

Unsupported visuals are omitted before normalization, including missing assets/locomotion,
species that fail the domain gate or bounded whole-body fit probe, bottom-associated species
without a known floor, and disconnected lower water spans whose context differs from the surface
sample. The fit probe samples at most 32 column centers and may conservatively omit a species
that could fit at a different point. Catch eligibility remains unchanged.

Performance counters measure CPU discovery/simulation/context management and render submission,
not GPU rendering or frametime percentiles. Ordinary gameplay and crowded multiplayer still
benefit from a human playtest. No progression, targeting, entities, captures or drops are added.

## Dedicated verification and final evidence

The fresh Fabric dedicated-server/client pair passed all six `ambientcontext` checks, plus its
shader check. Worms fingerprint `cccdcaaea6a64311` (16 candidates) and held-rod fingerprint
`60f4be1d9fdd42ba` (20) matched server logs. The synthetic pre-switch response was discarded as
`DROPPED_STALE_CONTEXT`; seven dispatches over 168 ticks stayed below the bound of eleven.
The server's class-load log contains zero ambient client classes. It does contain the same
pre-existing `TankCosmeticMesh$Snapshot` record documented in Phase 4; no new server/client
coupling was introduced. The isolated server was stopped after the client completed. Its Gradle log ends with external termination from cleanup (the launcher had closed stdin), not a failed acceptance assertion.

Evidence: `fish-in-world-phase5/final-validation.log`, the final Fabric/NeoForge population and
lifecycle logs and curated screenshots there; `fish-in-world-phase7/dedicated-client.log` and
`dedicated-server.log`. The dedicated init script uses isolated directories and never deletes
existing worlds. JUnit XML totals: fishsim 442 / 36 skipped, common 854 / 1 skipped, no failures
or errors. `git diff --check` passed. Both 2.0.1 release jars contain `AmbientBudget`,
`AmbientPopulationRolls`, `AmbientClientCommands`, `AmbientFishRuntime` and `AmbientContextClient`.

Current screenshots were reviewed from underwater: fish are visible in water with ordinary
lighting/depth and the familiar tank sprites/poses. Shore images are retained as context evidence;
the opaque water surface makes them unsuitable as the only appearance proof. Release human
playtesting should focus on density, rare-sighting expectations, stacked-water omissions and the
cold CPU spikes recorded in Phase 6. No publication, deployment or human sign-off occurred.

Post-playtest update: see [small-island visibility adjustment](fish-in-world-island-playtest.md).
Ambient display now uses the upper four blocks of water and lets large connected patches use
the existing global density budget. Earlier eight-fish performance captures are historical.
