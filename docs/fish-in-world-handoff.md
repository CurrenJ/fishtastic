# Fish in World — continuation handoff

**2026-10-16 cleanup status:** the phase acceptance below is historical. The pre-backport changes
passed final validation: unit suites, both loaders' GameTests, targeted companion/ambient scenes
and final jar inspection. The water, population-continuity and stable-water rebuild gates are closed.
Resume from [audit §9.4](audit-2026-10-15-companions-and-fish-in-world.md#94-final-verification-and-acceptance-2026-10-16)
for the complete evidence matrix and remaining limitations. Steps 3–6 are recorded by the cleanup commit; no backport
has started. Five switches await owner visual review, and all dirty legacy worktrees are preserved.

Updated 2026-10-13. **Phases 0–7 implemented; automated gates complete.** The interrupted Phase 5
population work was finished, then client settings, production activation, lifecycle cleanup,
context boundaries, performance counters and final acceptance were completed. No publication,
deployment or human gameplay sign-off occurred. The default 50% density remains provisional;
measured cold CPU spikes deserve ordinary playtesting (see Phase 6).

## Start here

1. [Feature contract](fish-in-world.md)
2. [Implementation plan](fish-in-world-implementation-plan.md)
3. [Phase 5 population, transitions and final evidence](fish-in-world-phase5.md)
4. [Phase 6 settings, lifecycle and measured performance](fish-in-world-phase6.md)
5. [Phase 7 acceptance and dedicated verification](fish-in-world-phase7.md)

Earlier closed records remain authoritative for their milestones:
[Phase 0](fish-in-world-phase0.md), [Phase 1](fish-in-world-phase1.md),
[Phase 2](fish-in-world-phase2.md), [Phase 3](fish-in-world-phase3.md),
[Phase 4](fish-in-world-phase4.md). Phase 4's independent review/fix-round evidence is preserved.

## Current behavior

Normal clients automatically show local ambient fish in nearby loaded water, using the server's
fishing pool and final weights. No held rod uses the standard-worms visual baseline; held supported
rods use their real setup. Active-session context precedence is preserved. No visible individual
determines catches or grants progression. There are no saved/targetable fish entities.

Settings are in `fishtastic-client.properties`: `ambientFishEnabled=true`, `ambientFishDensity=50`.
Both loaders offer local commands:

```
/fishtasticambient enabled <true|false>
/fishtasticambient density <0-100>
/fishtasticambient debug
```

Zero density/off stops all ambient work and releases state. Density bounds discovery, context
origins, simulation and rendering together. The development harness remains explicit and
suppresses ordinary activation in older fixture scenes.

## Important fixes from this continuation

- Authoritative `OK`/empty pools retire old fish; unknown states cannot spawn and have bounded grace.
- Revival can cancel only domain departure, never eligibility or lifetime retirement.
- Geometry changes rebuild visual representability, including a bounded whole-body fit probe.
- Bounded per-slot choices preserve original expiry across re-entry/equipment toggling. RNG-position
  retention alone was insufficient; choices are now retained too. No ineligible cached choice can spawn.
- Water is partitioned by surface biome and shared fishing zones. Disconnected lower spans are
  omitted rather than shown with the surface pool's possibly incorrect context.
- Deep-water swimmers use the known band bottom without inventing a seabed; bottom species require
  a known floor. Resource/tag reload, dimension, disconnect and teleport cleanup invalidate sessions.
- Observable time/weather/moon and cast-session/hook changes request bounded context refreshes.

Shared tank visuals and motion stay in use. The visual mixture is 75% raw / 25% square-root
probability, lifetimes were 300–700 ticks for new choices (now 1800–3000, `AmbientTuning`, after the
2026-10-13 playtest tuning), retirement is 40 ticks after acceptance,
and retained state is bounded to 600 ticks / sixteen patches / four contexts × thirty-two slots.

## Final validation

- fishsim: 442 tests, 36 existing skips, zero failures/errors.
- common: 854 tests, one existing skip, zero failures/errors.
- Fabric GameTests 347/347; NeoForge 341/341.
- Both loaders: 23/23 population checks and 15/15 lifecycle checks, plus the shader check.
  NeoForge's final lifecycle-only rerun also passed fifteen checks with the final instrumentation.
- Excluded fish disappeared 42 ticks after the accepted context; common identities survived rod,
  bait/charm and quality-only switches. Both loaders restored retained state after brief re-entry.
- Fresh Fabric dedicated pair: six context checks passed; client/server fingerprints matched,
  old equipment responses were discarded, seven dispatches over 168 ticks stayed below eleven.
  Zero ambient client classes loaded on the dedicated server. One pre-existing tank mesh snapshot
  record remains the documented Phase 4 exception. The disposable server has been stopped.
- `build` passed; new ambient classes verified in both packaged 2.0.1 jars. `git diff --check` passed.
- Eight curated shore/underwater/coexistence images are under `fish-in-world-phase5/screenshots/`.
  Underwater images were reviewed; shore water can obscure fish and is not sole appearance proof.

CPU results are machine-specific. Reference host: Ryzen 9 5900X / RTX 3080 Ti, Java 25, 1536 MB
client heap, four visible processors, 960×540, 30 FPS cap. Pond interval means: Fabric tick 0.061 ms
/ submission 0.026 ms; final NeoForge lifecycle rerun 0.164 / 0.067 ms. Cold-session peaks reached
16.4 / 7.5 ms on Fabric and 57.7 / 26.5 ms on NeoForge. Context-manager work is measured separately
and must be added; these are CPU counters, not GPU or frametime-percentile benchmarks. Both larger
fixtures reached 2401 columns / eight fish inside all work/count limits. See Phase 6 for details.

## Workspace and future work

Checkout `D:/GitHub/fishtastic`, branch `26.1.2`. Changes remain uncommitted, including the earlier
interrupted implementation's staged/unstaged files. Preserve staged state, companion work and
unrelated logs. Never sweep `common/logs/latest.log` or broad untracked paths into a commit.
Read AGENTS.md and relevant canonical modding-guide topics before changing systems.

Evidence: final suite/build log and loader scene logs under `fish-in-world-phase5/`; fresh dedicated
logs/init script under `fish-in-world-phase7/`. Disposable run directories are ignored. Run one
client at a time; Windows locks the common dev jar while clients run, so package after they exit.

No implementation phase is waiting to be resumed. Next useful work is human gameplay tuning:
watch density/crowding, rare-sighting expectations, conservative fit omissions, stacked-water
omissions and cold CPU spikes. Do not call these short automated sessions a universal performance
or human release sign-off.

Post-playtest update: see [small-island visibility adjustment](fish-in-world-island-playtest.md).
Ambient display now uses the upper four blocks of water and lets large connected patches use
the existing global density budget. Earlier eight-fish performance captures are historical.

Population cap tuning on 2026-10-13: caps are now 32 at default 50% and 64 at 100%.
Discovery budgets, small-pool volume scaling and lifetimes are unchanged. See the island
playtest record for validation of the increased cap.

Further tuning: new-choice lifetimes are 90–150 seconds (two-minute mean); exclusions still
retire promptly. Floor residents spawn at the actual bed. Retirement rendering holds the
pose and shrinks around model centre, or a planted creature's base. Population long-run
captures use 600 one-second polls to cover the longer turnover interval.

Short-movement retirement fix: see [early retirement](fish-in-world-early-retirement.md).
Patch selection uses nearest water and keeps nearby occupied patches; discovery merges
keep the oldest key instead of sorting packed coordinates. New fragments no longer replace
an established body's key during westward expansion.
