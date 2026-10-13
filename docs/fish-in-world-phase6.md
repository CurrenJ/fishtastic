# Fish in World — Phase 6: settings, cleanup and performance

Updated 2026-10-13. Implementation and automated gate complete on both loaders.

Normal clients automatically discover and populate nearby water using authoritative fishing
contexts. The existing `fishtastic-client.properties` system stores `ambientFishEnabled=true`
and `ambientFishDensity=50`. Existing files receive these defaults through the getters. Both
loaders register local commands (no server permission, packet or gameplay change):

```
/fishtasticambient enabled <true|false>
/fishtasticambient density <0-100>
/fishtasticambient debug
```

Commands persist preferences and apply them immediately. Editing the properties file follows the
existing convention: restart to read edits. Zero density stops the runtime. The harness uses
non-persistent overrides and suppresses ordinary activation in older fixture scenes.

## One density budget

`AmbientBudget` sets discovery, context origins, simulation and rendering bounds together.
Population targets also depend on approximate usable water volume: sum of each displayed span's
height minus a 0.25-block allowance, divided by 12 blocks per slot, scaled by density. Maximum
the configured global individual budget per patch, with stricter representability and whole-body placement gates.

| Density | Radius | Columns/tick | Cells/tick | Shown patches/context origins | Individuals |
|---|---:|---:|---:|---:|---:|
| 0 | 0 | 0 | 0 | 0 | 0 |
| 25 | 12 | 12 | 128 | 1 | 16 |
| 50 (default) | 17 | 24 | 256 | 2 | 32 |
| 100 | 24 | 48 | 512 | 4 | 64 |

Population caps doubled on 2026-10-13 at the user's request. Discovery budgets and the
water-volume formula are unchanged: a patch needs 768 usable blocks cubed to reach the
full cap at 50% or 100%. Retained choices now support 64 slots per patch. Measurements
below predate this increase; see the island playtest record for new validation.

Vertical discovery stays bounded to eight blocks above / sixteen below the player; the cache has
at most 4096 columns, and domains at most 8192 grid cells. One domain rebuild and two membership
changes per tick remain the upper bounds. Requests retain the protocol's minimum 20-tick cadence.

## Lifecycle and truthful contexts

- Disable/zero density releases domains, discovery, retained choices, species visuals, render
  scratch and context state. No further discovery reads, simulations, submissions or requests.
- Disconnect, level changes, tag/datapack and resource reload hooks invalidate the session.
  Enabled clients restart automatically in the next usable level; late responses cannot apply.
- Movement exceeding 64 blocks in one tick clears population and context state as a teleport.
  Short trips outside discovery retain choices for 600 ticks / at most sixteen patches.
- Fish in unloaded chunks stop rendering immediately and retire; patch origins skip unloaded
  columns before environment classification or requests.
- Pending/stale/rejected contexts cannot spawn. Residents have a 100-tick grace followed by the
  short invalidation fade. Current `OK`/empty pools retire all residents immediately through the
  eligibility fade; these are different states.
- Client-observable time/weather/moon categories, cast session identity and hook position trigger
  bounded refreshes, alongside equipment, geometry and the periodic check.
- Connected water is partitioned by surface biome and the shared fishing zone classification.
  Lower disconnected spans are omitted: their cave/biome context may differ from the surface
  origin. Deep-water swimmers use the known band's bottom without inventing a seabed; benthic
  and anchored species require a known floor.

## Measurements and evidence

`ambientlifecycle` exercises the automatic release path on both loaders, including coexisting
tank fish, a companion and vanilla salmon, a larger water fixture, an authoritative empty pool,
disable/late response, half-density re-enable, reload, weather, dimension round trip, teleport and
zero density. It records discovery bounds, simulation/discovery CPU time, domain build time,
render submission CPU time and encoded context payload bytes. Context-manager CPU time is
reported separately because geometry signature construction and refresh checks happen there.

Reference host: AMD Ryzen 9 5900X, NVIDIA RTX 3080 Ti. Isolated clients: Java 25, 1536 MB heap,
four visible processors, 960×540, 30 FPS cap, render distance six, simulation distance five.
These are measurements on the available machine; they are not GPU timings or a guarantee for
other machines. The conservative 50% default is provisional pending ordinary playtesting.

Final results: Fabric and NeoForge passed all fifteen lifecycle checks (plus the common shader
check). Fabric's full run also passed all 23 population checks; NeoForge's full run passed the
same 23, then its final lifecycle-only rerun passed fifteen again. Both runs had zero failures.

| CPU measure (milliseconds) | Fabric final | NeoForge final lifecycle rerun |
|---|---:|---:|
| Pond interval mean ambient tick, 601 ticks | 0.061 | 0.164 |
| Pond interval mean render submission, ~900 frames | 0.026 | 0.067 |
| Session mean through larger-water fixture | 0.145 | 0.426 |
| Session peak ambient tick, includes cold start | 16.368 | 57.655 |
| Session peak submission, includes cold start | 7.477 | 26.454 |
| Mean domain build | 0.066 | 0.119 |
| Peak domain build | 1.342 | 1.136 |
| Context-manager mean through pond | 0.074 | 0.156 |
| Context-manager peak through pond | 3.882 | 21.077 |

Both larger-water fixtures reached 2401 cached columns / eight individuals, staying inside every
count/query budget. Context-manager timings are separate from runtime timings: they must be
added when assessing total ambient CPU work. The dedicated all-water run recorded context-manager
mean 0.737 ms / peak 9.860 ms during streaming. These short sessions are not steady-state
frametime-percentile benchmarks. Cold-session spikes, especially NeoForge's 58 ms tick / 26 ms
submission peaks, remain a concrete tuning concern for ordinary playtesting; low means do not
remove that concern.

Encoded payload counters (excluding protocol framing/compression): Fabric recorded 1392 request
bytes / 12328 response bytes by the pond checkpoint, including its earlier population scene.
NeoForge's lifecycle-only run recorded 288 / 2640 bytes there. The dedicated run recorded 348 /
3616 bytes, including a deliberately injected stale response. Disabled clients made no new
requests, reads, engine steps or submissions over the 80-tick idle window. Evidence resides in `fish-in-world-phase5/` alongside the shared capture
init script. Run one client at a time; package jars after clients exit because Windows locks the
common dev jar while clients use it.


Post-playtest update: see [small-island visibility adjustment](fish-in-world-island-playtest.md).
Ambient display now uses the upper four blocks of water and lets large connected patches use
the existing global density budget. Earlier eight-fish performance captures are historical.
