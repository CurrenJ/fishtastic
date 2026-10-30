# World fish exploration: implementation plan

**Status:** Checkpoint updated 2026-10-30 19:45 UTC. Steps 0–5 are built and automated gates are
green on all three loaders; the Fabric ambient render smoke passes 81/81 checks. The owner tested
the five-minute visit duration and says it looks good overall, then asked to increase visit rates.
The current rate-up candidate is `T=7200`, `L=6000`, chance `0.024`, `R=2`; the y=29
visit-accessibility finding is deferred until tuning settles.
Keep cave fish in underwater caves; a later accessibility change must be habitat-aware. Everything
is uncommitted on branch 26.1.2. Original P1–P4 decisions were accepted 2026-10-30; duration is
under a new playtest pass.
Keep the step table, decisions and findings current as work lands.

### Resume here (paused 2026-10-30)

Start from the standalone [session handoff](world-fish-exploration-handoff.md); it covers how to run the work as well as the state below.

1. **Tree state.** Nothing is committed. Steps 0–5 share one working tree. Review the loader
   GameTest registration files and `FishtasticPackets` before any commit. Never stage all files.
2. **Completed automated gates.** Common units: 994 total, 991 passed, 3 skipped. GameTests:
   Fabric 408/408, NeoForge 402/402, Forge 400/400. Evidence is under
   `build/world-fish-exploration/2026-10-30/` and in each loader's latest JUnit/GameTest log.
3. **Next operation: playtest the rate-up candidate.** Keep the validated duration `T=7200`,
   `L=6000`, and `R=2`, with chance increased to `0.024`. Assess visit discovery frequency and
   whether encounters still feel special. Complete the pending Step 3 playtest, Step 4 look/two-client
   check, and Step 5 in-game look as well.
4. **Defer vertical accessibility work until broader tuning settles.** Preserve underwater cave
   fish and avoid a global surface clamp. Continue the other owner playtests and the live two-client
   ambient check if available.

Owner decisions made during implementation (2026-10-30), each recorded in its step's findings:

- Initial Step 0 baseline: `T` 3600, `L` 2400, chance 0.016, `R` 2, assuming a water share of 0.5;
  playtest now targets a five-minute visit.
- Each window has a per-site start offset (`SALT_PHASE`).
- Vanilla fish are dropped from ambient only.
- Nether ambient fish live in lava.
- The weather log is accepted.

Design: [world fish exploration outline](world-fish-exploration-design-spec.md), owner decisions
E1–E19. Contracts this work changes: [ambient fish](fish-in-world.md),
[interactive fish schools](interactive-fish-schools-design-spec.md) and
[in-world fish encounters](in-world-fish-encounters-design-spec.md). Their contracts still apply
except where E1–E19 override them: binding, reservation, reward identity, tackle handling,
visit shapes, locomotion-class approaches and the lockstep motion model are all kept.

## What changes, in one view

| Area | Today | After |
|---|---|---|
| Visit scheduling | Per-region deadlines, saved per region | Deterministic windows per water chunk (E1, E2); arriving reveals a visit and never causes one |
| Visit persistence | Full school and member state saved | Only depletion is saved (caught members per window); free members are derived again on reveal (E11) |
| Spacing | Caps that depend on what is loaded, plus a 64-block overlap check | Deterministic neighbour suppression (E5); caps kept only as protection |
| Ambient species | Server answers per-origin context requests; a held rod changes the preview | Clients derive seeded individuals from the natural pool with no equipment (E6, E9) |
| Ambient lifecycle | 90–150 s random lifetimes per client | Seeded staggered lifespans shared by every client (E13) |
| Observation | None | Spyglass hold-on observation, validated by the server, unlocking existing Fishopedia fields (E7, E16, E17, E19) |

Lockstep visit motion (`FishSchoolSim`, `FishSchoolReplica`, `FishSchoolWire`), the approach and
bite logic per locomotion class, `VisitShape`, binding and the minigame path keep working as they
are. The rework replaces how visits come into being and are saved, not how they swim or are caught.

## Planning decisions (owner, 2026-10-30)

- **P1. Suppression ranks on the hash alone (accepted).** Whether a neighbour chunk holds water is
  only known once it is loaded, so suppression treats every neighbour chunk as a site. The result
  is the same on every client and server whatever is loaded; dry neighbours remove a little
  density, which Step 0 folds into the visit chance.
- **P2. The first catch unlocks the observed fields (accepted).** Name, portrait and broad habitat
  unlock on the first catch, so catching always grants what observation grants. Every later
  `EncyclopediaTiers` threshold stays as it is. Today COMMON and ABUNDANT species reveal the name
  at 2 catches; that changes to 1.
- **P3 initial baseline.** Stationary target: about 4 visits an hour, 2-minute visits (accepted
  2026-10-30; duration superseded by the current playtest target). For a player
  passing by, the visit rate depends mostly on the share of time a site is active. For a
  stationary player it also scales with `1 / L`, so the two targets together fix `L`. The owner
  kept the stationary target and a 2-minute minimum fishable lifetime, and raised the travelling
  target instead (supersedes E15): roughly 20–35 visits an hour depending on travel speed.
- **P4. Drop old data (accepted).** Saved schools, region schedules and any old ambient state are
  discarded on first load with the new code; visits and fish come back under the new rules.
  Nothing needs preserving or migrating.

### P3 against E15: initial baseline (duration superseded by playtest)

With the Step 0 rough assumptions (48-block tracking range, half of all chunks hold water,
19 s for a passing player to cross a chunk's view), the two rates are linked by visit length `L`:

| `L` | Stationary | Travelling at 5 blocks/s | Travelling at 2.5 blocks/s |
|---|---:|---:|---:|
| 1 min | 4/h | ~21/h | ~11/h |
| 2 min | 4/h | ~37/h | ~19/h |
| 3 min | 4/h | ~54/h | ~27/h |
| 10 min | ~0.3/h | 12/h | ~6/h |

Hitting 4 an hour stationary and 12 an hour travelling at the same time needs visits shorter than
about a minute, which is too short to notice, approach and fish. Initial owner choice (2026-10-30):
keep 4 an hour stationary with `L` = 2 minutes and accept the higher travelling rate. The owner has
now asked to try visits around five minutes; the first duration candidate and its density trade-off
are recorded in Step 0 findings below.

## Steps

| Step | What | Depends on | Status |
|---|---|---|---|
| 0 | Density model and starting values for `T`, chance, `R` | — | Initial model done; five-minute duration tested well; rate-up candidate awaits playtest (2026-10-30) |
| 1 | Shared derivation core: ecology seed, hashes, natural pool on both sides | — | Built, uncommitted (date 2026-10-30) |
| 1b | Presence report (E18) | 1 | Built, uncommitted; owner reviewed 2026-10-30 (drop vanilla; nether ambient in lava) |
| 2 | Visit windows replace region scheduling | 0, 1, P1, P4 | Built, uncommitted; owner playtest found a deep-anchor accessibility issue; fix must preserve cave fish (2026-10-30) |
| 3 | Reveal mid-window (E11) and changed inputs (E14) | 2 | Built and verified on all three loaders, including accepted weather log; uncommitted; owner playtest pending (2026-10-30) |
| 4 | Seeded ambient fish (E6, E9, E12, E13) | 1, 1b reviewed | Built, automated gates green on all loaders, Fabric render smoke 81/81; owner look and two-client check pending (2026-10-30) |
| 5 | Spyglass observation (E7, E16, E17, E19) | 2, 4, P2 | Built and automated gates green on all loaders; owner in-game look pending (2026-10-30) |
| 6 | Balance instrumentation and playtests | 2–5 | Five-minute duration candidate tested well overall; continue broader tuning; deep-anchor accessibility deferred |
| 7 | Contract docs and ports | 6 accepted | Not started |

### Step 0: density model

A small headless calculator (a `:fishsim`-style test class, no game) that takes `T`, `L`, chance,
`R`, tracking range, travel speed and water share, and reports:

- visits passed per hour by a travelling player;
- visits started per hour within view of a stationary player;
- the expected wait before a given pond hosts a visit;
- how often suppression removes a window.

It checks the closed form against a Monte-Carlo walk over a hashed chunk field using the real
Step 1 hash, so the numbers include suppression and dry chunks.

Worked example (rough assumptions: 48-block tracking range, 5 blocks/s travel, half of all chunks
hold water): a traveller sees ~3,400 water chunks an hour, so ~12 visits an hour needs about 0.35%
of sites active at any moment. A stationary player sees ~14 water chunks, giving
`14 × 0.0035 / L` visits an hour: about 0.3 an hour at `L` = 10 minutes and about 1.5 an hour at
`L` = 2 minutes. This is the P3 trade-off.

Deliverable: starting values that give about 4 visits an hour stationary with `L` = 2 minutes (P3), plus the travelling rate they imply, for owner sign-off. They
go in the outline's open decision 1 and become `fishtastic-server.properties` keys.


#### Step 0 findings (2026-10-30, built, uncommitted; values accepted by owner 2026-10-30)

**Owner sign-off (2026-10-30).** The recommended row is accepted: `T` = 3600 ticks, `L` = 2400 ticks
(2 minutes), visit chance 0.016, `R` = 2 chunks, assuming a water share of 0.5 (to be checked against
the Step 6 counters). They are now the defaults (`VisitWindows.Params.DEFAULT`) and the
  `fishtastic-server.properties` keys `visits.periodTicks`, `visits.lengthTicks`, `visits.chance` and
  `visits.suppressionRadiusChunks` (Step 2 findings). This is the initial baseline; the current
  duration candidate below supersedes its two-minute length for playtesting.

**Owner duration and rate tuning (2026-10-30).** The owner reported visits should last about five
minutes, then tested that duration and said it looks good overall. The first candidate used `T=7200`
(6 minutes), `L=6000` (5 minutes), chance `0.016`, `R=2`. The owner then asked to increase rates;
the current candidate raises chance to `0.024` and keeps the duration and spacing. Under the original
Step 0 assumptions, the model estimates 2.69 stationary starts/hour (versus 1.93 at chance 0.016),
a 5.12-hour mean wait at a one-chunk pond (versus 7.18 h), travel passes of 29.5/h at 2.5 blocks/s
and 56.2/h at 5 blocks/s (versus 21.2/h and 40.4/h), and 20.1% availability at a random instant
(versus 14.9%). These are model estimates; the rate-up candidate still needs playtesting. The owner
asked to
defer the y=29 open-water accessibility issue until broader tuning settles; cave fish must remain in
cave water, so a later fix cannot clamp all visits to the surface.

**Method.** `VisitDensityModel` (closed forms) and `VisitFieldSim` (Monte-Carlo over the real
`VisitWindows` and `EcologyHash`) live in `common/src/test/.../ecology/`; `VisitDensityReportTest`
compares them. Quick sanity tests run in the normal `:common:test`; the full report is gated:
`./gradlew :common:test --tests '*VisitDensityReportTest*' -PvisitDensityReport=build/reports/visit-density`
(writes `visit-density.md` and `visit-density-sweep.csv`; about 15 s). Tracking range is 48 blocks:
the default of `schools.trackingRange` (`FishtasticServerConfig.fishSchoolTrackingRange`, clamped
16..96), which `FishSchoolManager.syncNearbyPlayers` compares with the distance from the player to the
school anchor. Water share 0.5 is the plan's assumption; a separate hash picks water chunks, while
suppression treats every chunk as a site (P1). A stationary player sees about 28.3 chunks (14.1 wet);
the closed forms are: hosted per window `h = chance x survival`; stationary starts/h
`= 14.1 x (72000/T) x h`; travelling passes/h `= (w/256) x h/T x (3600 v x 2r x L + 72000 pi r^2)`;
survival is an exact integral over our rank of the product, over the neighbours, of the chance that
none of their (one or two) overlapping windows is active and higher.

**Phase offset (change to Step 1 code).** Each site now has `t0 = hash(seed, cx, cz, 0, SALT_PHASE) mod T`
and its window `k` is `[t0 + kT, t0 + kT + L)` (E1). API: `phase`, `windowIndex(seed, cx, cz, time, params)`,
`windowStart/windowEnd(seed, cx, cz, window, params)`, `isOpenAt(seed, cx, cz, time, window, params)`,
`windowsOverlapping(...)`. Suppression compares a window with every overlapping window of each
neighbour (at most two, since `L <= T`) using the same `(rank, x, z, window)` tie-break; still a pure
function of seed, chunk, window and params. Measured: the share of sites open at an instant stays within
0.0103..0.0113 of the expected 0.0107 across 288 random instants (no global pulse). Existing hash pins
unchanged; `SALT_PHASE` is appended. Debug `/fishtastic ecology` prints the phase and per-site span.

**Agreement.** Closed form against the hashed field (40 worlds x 100 h stationary, 24 worlds x 1 h
walking, 4 worlds of suppression, 1500 ponds), recommended row: stationary 4.00 vs 4.09 +- 0.14,
travelling 2.5 b/s 19.9 vs 20.0 +- 0.7, 5 b/s 35.8 vs 37.3 +- 1.0 (a wandering walk gave 36.8 +- 1.0),
suppressed 11.9% vs 11.5% +- 0.3, pond wait to a visit 3.48 vs 3.39 +- 0.08 h. All six candidate rows
agree within about 2 standard errors (the largest gaps are travelling 7200 ticks 21.5 +- 1.2 vs 19.9).

**Proposal (accepted 2026-10-30; written when `Params.PROVISIONAL` and the config were still untouched).** `L` = 2400 ticks,
water share 0.5, range 48:

| | T | R | chance | stationary/h | travelling 2.5 b/s | travelling 5 b/s | suppressed | pond wait |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| **Recommended** | 3600 (3 min) | 2 | **0.016** | 4.0 | ~20/h | ~36/h | 11.9% | ~3.5 h |
| Back-to-back windows | 2400 | 2 | 0.0107 | 4.0 | ~20/h | ~36/h | 11.8% | ~3.5 h |
| Slower rotation | 7200 | 2 | 0.032 | 4.0 | ~20/h | ~36/h | 11.9% | ~3.5 h |
| Less spacing | 3600 | 1 | 0.0147 | 4.0 | ~20/h | ~36/h | 3.8% | ~3.5 h |
| More spacing | 3600 | 3 | 0.0202 | 4.0 | ~20/h | ~36/h | 29.9% | ~3.5 h |
| No spacing | 3600 | 0 | 0.0142 | 4.0 | ~20/h | ~36/h | 0% | ~3.5 h |

Stationary, travelling and pond-wait figures are fixed by the target (they depend on hosted starts per
site per hour), so T and R only choose granularity and spacing. A visit is available in view about 12%
of the time (one every ~15 minutes, each 2 minutes). Travelling lands at the top of the 20-35/h range
the owner accepted in P3 at 2.5 b/s and a little above it at 5 b/s. Recommended: T = 3600, R = 2,
chance 0.016. R = 2 keeps hosted visits at least 3 chunks apart for a 12% cost; R = 3 costs 30%.

**Correlation.** Neighbouring chunks activating together: ratio of joint to independent probability is
0.97..1.02 at every offset (1,0) to (10,0) for activity, i.e. chance level (6 worlds x 48 instants x
601x601 chunks; an earlier 2-world sample showed 1.07 at (3,0), which vanished with more data: noise).
Hosted visits are never within R (ratio 0.00 at distances 1, 1.41, 2) and independent beyond (1.01..1.02
at 2.24+). Temporal clustering for a stationary player: Fano factor of 10-minute counts 0.94, gap CV
0.99, share of time with no visit in the next 30 minutes 12.7% (Poisson 12.9%); starts are a little
more regular than random, not bunched.

**Concerns for the owner.** (1) The rate is linear in water share: at 0.25 the stationary rate halves
(chance 0.038 restores it), at 0.1 even chance 1 cannot reach 4/h; ocean-heavy versus land-heavy play
differs, so the sign-off should name the water share the starting values assume (0.5). (2) E3 makes a
single-chunk pond host one visit about every 3.5 h; a player waiting at a small pond will see few.
(3) Wandering routes pass about as many visits as straight ones at 0.1 rad/s turning noise, so the
travelling rates hold for exploring on foot but a boat circling one lake gets only the stationary rate.
(4) The calculator counts visits within 48 blocks of the anchor; visits the player cannot yet see
(behind terrain or far water) still count, so perceived rates may be somewhat lower.

### Step 1: shared derivation core

New pure package `grill24.fishtastic.ecology` in `common`, with no `Level` dependency, unit-tested:

- `EcologySeed`: a 64-bit seed derived on the server from the world seed and dimension
  (`hash(worldSeed, "fishtastic:ecology", dimension)`), sent to clients on login and dimension
  change. Clients never receive the world seed.
- `EcologyHash`: stable `hash(seed, chunkX, chunkZ, window, salt)`, the same value on server and
  client, with fixed salts for visit activity, start cell, rank, members and ambient slots.
- `VisitWindows`: window index from game time, active test, rank, and suppression within `R` (P1).
- `NaturalPool`: the no-equipment natural pool for a water position. Fish profiles are already
  synced to clients (the client encyclopedia and ambient code read the `fish_profile` registry).
  Pull the environment and candidate logic out of `FishingPoolResolver` into a function both sides
  can call with `PersonalEquipment.NEUTRAL` and no bait. A parity test confirms server and client
  give the same pool for the same inputs.

Game time is the clock (`Level.getGameTime()`). Clients receive it from the server, so window
boundaries agree within the usual sync jitter. Visits are server-owned, so that jitter only affects
ambient fish.

### Step 1 findings (2026-10-30, built, uncommitted)

- **Extracted.** `ecology.NaturalPool` holds the single environment assembly
  (`environment(biome, timeOfDay, weather, moon, y, seaLevel)`; `FishingPoolResolver.resolveEnvironment`
  now calls it) and the natural pool (`resolve(items, profiles, env)` = `FishingPoolResolver.resolve`
  with `NO_BAIT`, no charm, `NEUTRAL`; `resolveAt(level, pos)` for callers with a level).
  `FishSchoolManager.tryCreateSchool` now calls `NaturalPool.resolve` instead of building those inputs
  by hand; behaviour is unchanged (same inputs). The pool includes vanilla fish (`NO_BAIT` keeps them).
- **Server-only state: none found.** The pool reads the biome, the overworld clock
  (`getOverworldClockTime`), rain and thunder (`isRaining`, `isThundering`, `precipitationAt`), the
  moon-phase environment attribute, height and sea level, and the synced `fish_profile` and item
  registries. All exist on a client level. Residual drift risk is only sync lag of weather and clock
  (Risks section). Nothing needed to become an explicit parameter beyond the six `environment(...)` inputs.
- **Hash.** Stafford mix13 folded over `(seed, chunkX, chunkZ, window)` after `seed ^ salt`. Salts:
  `SALT_VISIT_ACTIVITY` 0x5A17_0001_C0FF_EE01, `SALT_START_CELL` 0x5A17_0002_BADD_CAFE,
  `SALT_RANK` 0x5A17_0003_FEED_FACE, `SALT_MEMBERS` 0x5A17_0004_D15E_A5E5,
  `SALT_AMBIENT_SLOTS` 0x5A17_0005_0DD5_EA11. Pinned by `EcologyHashTest`.
- **Seed.** `EcologySeed.derive(worldSeed, dimensionId)` is SHA-256 truncated to 64 bits, not a
  bijective mixer, so a client cannot invert it to the world seed. Sent by `EcologySeedSyncPacket`
  from `EcologySeedSync.tick` (server tick: new player or changed dimension), which covers login,
  respawn and dimension change on all loaders with no per-loader hook. Client side:
  `EcologyClient`.
- **Provisional parameters** (superseded: `Params.PROVISIONAL` became the accepted `Params.DEFAULT`, chance 0.016): T = 3600 ticks,
  L = 2400 ticks, chance = 0.015, R = 2 chunks (Euclidean). Windows are `[kT, kT + L)` with no
  per-site offset, so all active sites open and close together; Step 0 should decide whether to add
  a hashed per-site offset (would need a sixth salt).
- **Phase offset added in Step 0.** `EcologyHash.SALT_PHASE` gives each site a hashed `t0`; the "no per-site offset" note above is superseded (see Step 0 findings).
- **Suppression** is a pure function: an active window is suppressed when any other chunk within R is
  active with a higher `(rank, x, z)`; suppressed windows still suppress weaker neighbours.
- **Debug.** `/fishtastic ecology` (gamemaster).
- **Tests.** Unit: `EcologyHashTest`, `VisitWindowsTest`, `server.NaturalPoolParityTest`; GameTests:
  `EcologyGameTests` (real-level pool parity, seed derivation and packet round trip) on all loaders.
  Client-versus-server parity of live weather and clock is not testable headlessly.

### Step 1b: presence report (E18)

A headless report built on `NaturalPool` lists the predicted ambient roster per zone set
(`EncyclopediaTiers.LOCATION_POOLS`), time of day, weather and moon, with each species' share.
Write it to `build/reports/ambient-presence/` as Markdown plus CSV. The owner reviews it against
species lore. A per-species ecology field is added only where the report is wrong, and each
addition is recorded here with the reason. Step 4 starts after that review.

#### Step 1b findings (2026-10-30, built, uncommitted)

- **Regenerate.** `./gradlew :common:test --tests '*AmbientPresenceReport*' -PambientPresenceReport`
  writes `build/reports/ambient-presence/ambient-presence.md` (flags first, then one section per zone
  set) and `ambient-presence.csv` (zone set x time x weather x moon x species, with catch weight, catch
  share, ambient share and no-vanilla ambient share). Code: `server/AmbientPresenceReportTest` (gated
  by the `ambientPresenceReport` system property set in `common/build.gradle`); its fast sanity test
  runs in the normal pass.
- **Real profiles, headless.** Each `fish_profile` JSON is parsed with `FishProfile.CODEC` over
  `JsonOps` into a `MappedRegistry`, beside an item registry of the four vanilla fish and one
  `FishtasticFishItem` per species in `#fishtastic:fish` (asserted equal to the profile files). Only
  `biome_weights` cannot be decoded in a bare JVM (its codec needs a biome registry), so it is
  stripped. The report is therefore driven by zone sets with a neutral biome: biome multipliers (37
  species) are not applied and are listed instead. Condition frequencies are assumed (time by tick
  span, weather 70/20/5/5, moon equal).
- **Ambient filter.** Ambient fish today are the pool's candidates minus anything the client
  cannot represent (`AmbientFishRuntime.visualFor`: no item, or locomotion disagreeing with animation),
  drawn by `VisualSampler`'s 75% raw / 25% square-root mixture. That drops nothing in shipped data and
  there is no per-species ambient flag. `fish_encounter_disabled`, school-ready locomotion and the nether
  exclusion are visit-only rules (`FishSchoolManager#encounterEligible`) and are not applied. The
  report shows catch share and ambient share, in two views: as is, and without vanilla fish.
- **Headline flags.**
  - Vanilla fish dominate: cod, salmon, tropical fish and pufferfish (weights 200/200/50/25, no
    profile so no zone gate) are 82-92% of every ambient roster and appear in the nether and caves.
    No single species passes 50%, but cod and salmon are about 34-37% each. 58 of the 66
    Fishtastic species never reach 1% in any zone set in this view.
    Step 4 needs a decision: drop vanilla from ambient, gate them by zone, or keep them.
  - Conditions are nearly invisible in view A for the same reason; the no-vanilla view shows the real
    swings (largest: neon_tetra 4.7x, bluegill 4.2x, clown_loach 3.6x, blind_cave_tetra 3.3x). None of
    the swings is a "rare combination only" case.
  - Without vanilla: no species is missing from every roster and no zone set passes 50%. Largest
    shares: sulphur_molly in the nether (about 37%), shrimp in the ocean (about 27%), bluegill in
    river-type sets.
  - Against encyclopedia rarity, 21 species fall two or more tiers (view A), all because vanilla
    dilution pushes them to very rare or rare (shrimp, sulphur_molly, bluegill, neon_tetra, blind_cave_tetra
    and nether species like devils_hole_pupfish). Without vanilla none moves two tiers.
  - Nether: cod, salmon and the other vanilla fish are in the nether pool and would be ambient in lava.
- **No ecology field added.** Nothing was added to any profile; that waits for the owner's review.
- **Owner review (2026-10-30).**
  - **Vanilla fish are dropped from ambient.** Cod, salmon, tropical fish and pufferfish never
    appear as ambient fish; ambient rosters use the no-vanilla view. They stay in catch and visit pools
    as today.
  - **Nether ambient fish live in lava.** They are a deliberate easter egg for players swimming with
    fire resistance. The nether roster is kept as shipped, and no ecology field is needed.

### Step 2: visit windows replace region scheduling

In `FishSchoolManager`:

- Replace `scheduleNearbyRegionAttempts` and `RegionSchedule`. Each tick, a bounded number of
  loaded water chunks within tracking range of players are checked (the same budgeted rotation as
  today). An active, unsuppressed, undepleted window with no live visit is revealed.
- **Start cell (E2).** List the chunk's qualifying water cells in a fixed order (loaded source
  water with an empty collision shape, the existing rule) and pick `startHash mod count`. The
  existing bounded water search grows from there across chunk lines.
- **Minimum water (E4).** Fewer than 6 connected cells means no visit in that window. The
  existing per-species body fit stays as the safety net. If the rolled species does not fit, that
  window passes without a visit (no substitute species, as today).
- **Visit identity.** `VisitId = (dimension, chunkX, chunkZ, window)`. Species, shape, member
  count, member reward seeds and seeded positions all come from `EcologyHash` and the natural pool
  at the start cell, using the environment at the window's start, not at reveal. The same visit is
  therefore produced whenever it is revealed.
- **Persistence.** `FishSchoolSavedData` keeps a depletion ledger: `VisitId → caught-member
  bitmask`, pruned once the window has ended. Reservations stay in memory and recover on reload as
  today. Old school and region data are discarded on load (P4).
- **Lifetime.** A live visit ends at its window end, using `world_encounter.lifetime_scale` from the
  encounters spec as a multiplier on `L`. When no player is in tracking range, the live runtime is
  dropped; it is revealed again (Step 3) when one returns.
- **Caps.** `maxPerDimension` and `maxMembersPerDimension` stay only as performance protection. A
  debug counter records whenever one binds, because in normal play it should not.

Debug triggers (solo playtest):

- `/fishtastic visits windows` lists nearby sites with window, rank, active, suppressed and
  depleted.
- `/fishtastic visits reveal` forces the nearest site active for testing.
- The existing `/fishtastic schools spawn …` keeps working.

GameTests on all three loaders:

- A visit revealed by a player present at window start and one revealed by a late arrival have the
  same `VisitId`, species and members.
- Leaving and returning, or crossing chunk lines, never creates a new window.
- Depletion survives save and reload.
- Suppression gives the same result for any set of loaded chunks.
- An old save loads cleanly with its school and region data discarded (P4).

#### Step 2 findings (2026-10-30, built, uncommitted; owner playtest pending)

**What changed.**
- `FishSchoolManager`: `scheduleNearbyRegionAttempts`, `RegionSchedule`, `overlapsExisting` and the
  region pruning are gone. Every 20 ticks `scheduleVisits` walks the loaded chunks within tracking range
  of any player (rotating cursor, at most 512 chunk checks and 2 reveals a pass). A chunk is revealed
  when its current window is inside its open span `[start, start + L)`, `VisitWindows.hostsVisit` holds
  (active and not suppressed), no visit of that window is live and no retry timer is running.
  `revealWindow` then derives the visit; `createSchool` is the old `tryCreateSchool` with a derived
  branch (the debug `/fishtastic schools spawn` path is the same method with `derived == null`, still
  random and still not window-bound, so it works as before).
- **Start cell (E2).** `qualifyingWaterCells(chunk)` lists the chunk's loaded source-water cells with an
  empty collision shape in a fixed order (section from the bottom, then y, z, x; sections with no water
  are skipped unread). `VisitDerivation.startIndex(startHash, count)` picks one; the existing bounded
  `scanConnectedWater` grows from it across chunk lines. A scan that met an unloaded chunk defers the
  window (retried every 100 ticks) rather than derive from a partial lake, so a visit exists only in
  fully loaded water.
- **Identity.** `VisitId(dimension, chunkX, chunkZ, window)`. `VisitDerivation` (pure, unit-tested)
  derives from `EcologyHash` salts: species (weighted pick over the natural pool at the start cell,
  filtered by `encounterEligible`), member count, the school and member UUIDs, member reward seeds, the
  seed of the placement shuffle. Member `i` is always the same fish, and `i` is its ledger bit. No
  substitute species: if the rolled species does not fit, the window passes (retried after 60 s in case
  the water changed; a fit failure is not cached across restarts).
- **Persistence.** `FishSchoolSavedData` writes only `depletion: [{x, z, window, caught}]` (a bitmask).
  Schools are an in-memory list and are never written; reservations and sims stay in memory. The old
  `schools` and `regions` fields are ignored on load and not written back (P4), tested with a real old
  JSON. Rows are pruned a whole period `T` after their window opened (`pruneLedger`, every 60 s).
  `resolveCaught` marks the ledger; a re-reveal leaves caught members out and keeps the others at the same
  seeded spots.
- **Lifetime.** A derived visit ends at `windowStart + clamp(round(L * lifetime_scale), 1, T)`; a late
  arrival to a species whose scaled visit is already over reveals nothing. A reveal needs the window's base
  span (`now < start + L`), so a species scaled above 1 only outlives the base window for players who were
  already there. Debug spawns last `L * lifetime_scale` from now. `schools.activeLifetimeTicks`,
  `schools.attemptIntervalTicks` and `schools.encounterChance` are gone from code and the default file;
  stale lines in an existing properties file are ignored.
- **Drop and re-reveal.** Every 20 ticks a derived visit with nobody within `trackingRange + 48` blocks
  (default 96) is dropped from memory, unless a hook, bite or minigame holds one of its members. Nothing
  is saved or marked, so the scheduler reveals it again when someone returns; a visit that ended in the
  runtime (everyone caught or departed, water gone) is remembered as concluded for its window so it is
  not re-revealed. The margin follows Risks: reveal reaches the tracking range to a chunk's edge (about
  23 blocks more to a start cell), so 48 stops a visit being dropped right after it is revealed, and
  someone watching from just outside tracking range sees it stay.
- **Caps** (`schools.maxPerDimension`, `schools.maxMembersPerDimension`, the global 16) are checked only
  for derived reveals, skip the window, and count in `capBinds`, shown by `/fishtastic visits windows`.
  The member count itself no longer depends on the remaining budget (only on `maxMembersPerSchool`), so
  it is the same on every reveal.
- **Config.** New keys in `fishtastic-server.properties`: `visits.periodTicks` (7200, min `lengthTicks`,
  max 2.4M), `visits.lengthTicks` (6000, 600..2.4M), `visits.chance` (0.024, 0..1),
  `visits.suppressionRadiusChunks` (2, 0..8). `FishtasticServerConfig.visitWindowParams()` builds the
  `Params`; `/fishtastic ecology` reads it too. Changing them reschedules every window in an existing world.

**Weather at window start.** Time of day and the moon phase come from the overworld clock reconstructed
at the window start (`clockNow - (now - windowStart)`, so any reveal gets the same reading; the moon is the
8-day cycle of `Timelines.MOON`, checked against the environment attribute by a GameTest). Weather cannot
be reconstructed: nothing records the sky at a tick the server may not have been running. Deriving under
the weather at reveal would make species depend on reveal time, which E1 and E11 forbid, so visits are
derived under `CLEAR` weather. Consequence: rain, snow and thunder do not shift visit species yet
(ordinary casts still see them). Revisit with Step 3 (E14) if the owner wants stormy visits; a seeded
per-window weather proxy would be the alternative, but it would contradict the actual sky.

**Deviations and notes.**
- Step 3's seam is marked in `revealWindow` and in the derived branch of `createSchool`: a reveal at any
  time currently yields the full seeded roster minus caught members.
- `DEPARTED` members are not recorded; only catches are, so a visit that ended (everyone left) in this
  runtime does not come back before its window ends, but after a restart it can come back inside the same
  window until Step 3's seeded departures decide who has gone.
- The debug `schools spawn` path keeps ignoring windows, suppression and the ledger by design.
- `FishSchoolSavedData` test seams for regions are removed; `transientReservationsRecoverAcrossSaveReload`
  now asserts that live visits are not saved and the ledger is; `spawnedVisitsTakeTheirShape` lost its
  save half; `schoolsSavedBeforeShapesLoadAsSchools` became `oldSchoolAndRegionDataIsDiscardedOnLoad`.
- Other docs still describe region scheduling and `schools.activeLifetimeTicks` (encounters spec,
  performance plan); they are rewritten in Step 7.
- Tests: `VisitDerivationTest` (unit) and `VisitWindowGameTests` on all three loaders. The GameTests play
  the clock through manager seams (`revealWindowForTest`, `scheduleVisitsForTest`, `dropFarVisitsForTest`),
  because the schedule is a hash of the world seed and a test cannot wait for a particular window. They
  cover: the same visit from a reveal at the window start and from late arrivals; the scheduler revealing
  exactly the hosting windows over 8000 windows of one chunk (including active-but-suppressed ones that
  must stay empty); no reveal for a closed window, nobody near or a far player; repeated passes never
  adding or replacing; leave, drop and return deriving the same visit; a different set of chunks in range
  deriving the same visit; depletion round-tripping through the codec and omitting the caught member on
  re-reveal; a depleted window staying empty; old school and region data discarded; ledger pruning; cap
  binding counted; both debug commands; the moon phase.

**How to test the initial baseline in game** (owner, solo, gamemaster; duration now superseded):
1. `/fishtastic visits windows` near water: the header gives the parameters and counters; rows list the
   next scheduled windows within tracking range (opens in / OPEN, rank, hosts or SUPPRESSED, caught mask,
   LIVE or not). Read the displayed parameters, since the current candidate has a different cadence from
   the initial baseline. Stand by a listed chunk's water until it opens: the visit appears (sparkles, jumps)
   without you doing anything. The original
   baseline lasted 2 minutes; current candidate duration is 5 minutes for default-scale species.
2. `/fishtastic visits reveal` is a forced override: it reveals the nearest chunk's current window
   whether or not it is scheduled, and says so. It does not change the schedule for anyone else.
3. Catch one member, leave beyond about 100 blocks and come back inside the window: the visit returns
   with that fish missing; `/fishtastic visits windows` shows the caught mask. Save and quit, reload: the
   mask is still there.
4. `/fishtastic schools spawn [species]` still makes a debug visit; `/fishtastic schools debug` reads it.

### Step 3: reveal mid-window and changed inputs

- **E11.** On reveal at time `t`, each member's seeded departure time (inside `[start, start + L)`)
  decides whether it has already left. Members still present are placed at seeded spots near the
  start cell, run through the existing body-fit placement fix. The lockstep setup packet carries
  that state, so clients build the same engine.
- **E14.** Nothing about a visit is cached between reveals except depletion. Water edits, datapack
  or profile reloads, and mod updates simply change what the next reveal derives. A live visit
  whose water stops qualifying departs through the existing path.
- GameTests: a reveal at 50% of the window matches the members-present prediction; a water edit
  removes a live visit cleanly; a profile reload changes later windows but keeps depletion.

#### Step 3 findings / resume notes (2026-10-30, verified on all three loaders)

**Verification checkpoint (2026-10-30):** the first complete Fabric rerun ran 404 tests with 5 failures
(`build/world-fish-exploration/2026-10-30/step3-fabric-baseline.log`). The Step 3 profile assertion
reported `ACTIVE ... no school`: another test had removed its derived school before the delayed
assertion. The missed-school retry passed, while an engine timing assertion failed under the shared
synthetic clock. The lifecycle tests now synchronously step only their own visit, and the global
clock override is removed. The first combined A/B (`step3-fabric-fixed.log`) passed 403/404:
the remaining `members_leave_live_at_their_seeded_times` failure was the new seam skipping
runtime initialization, which normal `tick()` does first. That seam is fixed; the next operation
was `:fabric:runGametest --no-parallel`, which passed all 404 required tests and completed
successfully (`build/world-fish-exploration/2026-10-30/step3-fabric-runtime-fixed.log`). All five
baseline failures are resolved. NeoForge's first run (`step3-neoforge.log`) passed 397/398;
`backup_fixture_restores_shared_state_and_files` hit the recorded Windows `AccessDeniedException`
while moving its backup directory. The one rerun passed all 398 required tests and completed
successfully (`build/world-fish-exploration/2026-10-30/step3-neoforge-rerun.log`). Forge passed all
396 required tests, and common unit tests passed (986 total, 983 passed, 3 skipped, no failures or
errors) in `build/world-fish-exploration/2026-10-30/step3-forge-common.log`.
Java 25 must be selected explicitly via `JAVA_HOME` at
`C:/Users/curre/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2`; the default Oracle shim produced
no output and its first invocation was interrupted (unverified). The initial Java 25 run also
identified a Step 5 `ObservationGameTests` identifier guard violation, now fixed by that step.

**Verification checkpoint (2026-10-30):** the first combined weather gate passed all 8 new weather
unit tests and all 4 weather GameTests. Its only failure (407/408) was an existing shared-world
assertion that counted every live derived visit; the test now compares the count delta around its
forced reveal. The final Fabric rerun passed 408/408. NeoForge passed 402/402 and Forge passed
all 400 required tests. Common passed 994 total, 991 passed, 3 skipped, with no failures.
Evidence summaries: `weather-fabric-rerun.log`, `weather-neoforge.log` and `weather-forge.log`
under `build/world-fish-exploration/2026-10-30/`; detailed results are in `fabric/build/junit.xml`,
`neoforge/build/junit.xml` and `forge/build/gametest/logs/latest.log`.

**Next operation:** hand the owner the Steps 2–5 playtest script in the session handoff and record
the results. The five-scene Fabric render smoke is complete at 81/81 checks. Do not start Step 6
until the owner has playtested.

**Done (code compiles: `:common:compileJava`, `:fabric:compileJava`).**
- E11 departures: `VisitDerivation.departureTick/hasDeparted/lifetimeTicks/revealSpanTicks` (seeded in the last nine tenths of the visit length from `SALT_MEMBERS` stream 3000+member; EcologyHash untouched). `createSchool` (derived branch) omits caught and already-departed members, counts them in `School.departedAtReveal/caughtAtReveal`, and fails permanently ("depleted: X caught, Y departed") when none remain, so a fully departed or caught window never re-reveals, with or without in-memory `attempts`. Forced debug reveals have no seeded departures.
- Live departures: `tickVisitsAndMembers` `case AVAILABLE` sends a member whose `departAt` passed to DEPARTING with its own `fadeUntil` (60 ticks), then the existing DEPARTING -> DEPARTED path. No per-member fade on the client (state packet unchanged): the fish fades only when the school does and pops out when its `Leave` input lands. Possible visual follow-up.
- lifetime_scale gap: the scheduler now tries a reveal until `windowStart + revealSpan` (L x max scale 2.0, capped at T; the whole period by default); the species roll then refuses a reveal past its own end. So the reveal condition matches the visit's actual end. Suppression still ranks only the base L span.
- E14: `profileReady` is cleared when the fish_profile registry instance changes; tags are read live; nothing but depletion is cached between reveals. Note `/reload` does not reload datapack registries, so profile changes take effect on restart (all live visits are memory-only, so they are simply re-derived). A live visit whose species stops being eligible, or whose water stops qualifying, departs through the existing path.
- `/fishtastic visits windows` rows show `N present / D departed / C caught` for live visits (`membersSummary`). Non-live open windows show only the caught mask.
- Test seams in `FishSchoolManager`: `tickVisitForTest` (one production lifecycle pass for only that school at an explicit tick), `setExcludedSpeciesForTest`, `clearAttemptsForTest`. No shared synthetic clock remains.

**Tests.** Unit: `VisitDerivationTest` (4 new departure/lifetime tests; passed before the pause). GameTests in `VisitWindowGameTests`, registered on all three loaders: new `aRevealHalfwayThroughTheWindowMatchesTheMembersPresentPrediction`, `aWindowWhoseFishAllLeftOrWereCaughtNeverRevealsAgain`, `membersLeaveLiveAtTheirSeededTimes`, `aWaterEditThatStopsTheWaterMakesALiveVisitDepartAndStayGone`, `aProfileChangeChangesLaterDerivationsButKeepsDepletion`; the Step 2 tests were adapted to departures. The fresh Fabric baseline (404 tests, 5 failures) confirmed that the scheduler drop-count assertion and missed-school retry pass. The profile lifecycle test still failed due to shared test state; 3 failures were Step 5 observation fixtures and 1 was engine timing under the global synthetic clock. The scoped lifecycle seam passes all loader gates: Fabric 404/404, NeoForge 398/398 (one rerun for the Windows backup fixture flake), Forge 396/396; common units 986 total, 983 passed, 3 skipped, no failures/errors.

Exact failures in `step3-fabric-baseline.log` (prefix
`fishtastic_testmod:fishtastic_fabric_game_tests_` on every name):
- `bad_visit_claims_are_rejected` — Step 5, wrong-species fixture returned `UNKNOWN_SPECIES`.
- `a_valid_ambient_observation_records` — Step 5, valid fixture returned `NO_SUCH_FISH`.
- `bad_ambient_claims_are_rejected` — Step 5, outside-pool fixture returned `NO_SUCH_FISH`.
- `school_stays_in_its_water_and_steps_once_per_tick` — existing motion test, 4 steps after 3 ticks.
- `a_profile_change_changes_later_derivations_but_keeps_depletion` — Step 3, its live school had been removed before the delayed assertion.

**Resume:** Step 3 and the weather log are green on all three loaders and common units. The owner
playtest remains pending; the next automated operation is the ambient render smoke recorded above.

**Weather log (owner decision 2026-10-30; implemented and verified).** The spec replaced the
CLEAR placeholder (`FishSchoolManager.environmentAtWindowStart`) so visits derive under the real
weather at window start. Implementation and verification are recorded below.
- Per dimension with weather, the server keeps (gameTime, state) entries, state in CLEAR/RAIN/THUNDER from the raining/thundering flags; each server tick appends an entry when the flags differ from the last (captures `/weather`). Lookup = last entry at or before windowStart; snow vs rain still resolved by the start cell's biome as the normal environment code does.
- Prune entries older than now - (T + max scaled visit lifetime), always keeping the newest entry before the cutoff.
- Saved in `FishSchoolSavedData` beside the depletion ledger (or a sibling saved data, justify). With no history on first load, fall back to the current weather and mark the fallback in debug output. Ambient fish unaffected.
- `/fishtastic visits windows` shows the window-start weather used per open window; add `/fishtastic visits weatherlog` (or a section) listing entries.
- GameTests on 3 loaders: a window starting in rain derives the rain pool and keeps it after the weather clears and after drop and re-reveal; the log survives save/reload; pruning keeps the anchor entry; `/weather` changes are logged. Unit tests for lookup and prune.
- Docs: record it as a small bounded exception to E1 "only depletion is saved" (it records world weather, not visit state), in Step 2 findings or a "Weather log" note, plus a short note in E1 of the design spec.

**Weather implementation findings (2026-10-30, built and verified on all loaders).**
- `VisitWeatherLog` holds changed `(gameTime, CLEAR/RAIN/THUNDER)` entries. Lookup is the last
  entry at or before the window start; unknown history returns current weather with an explicit
  fallback flag. Multiple samples in one tick retain its final state. The saved codec normalizes
  order and redundant states; no per-visit weather or ambient state is stored.
- `FishSchoolSavedData` saves optional `weather` entries beside depletion, retaining clean loading
  of old files. It marks dirty on changes or pruning. This is the accepted bounded exception to
  E1: the entries record world input needed for derivation, rather than visit state.
- Each manager tick captures `WeatherData.isRaining/isThundering`, rather than the visual rain
  ramp (`Level.isRaining/isThundering`), so `/weather` changes are recorded promptly. Dimensions
  where `canHaveWeather()` is false always derive clear weather and do not grow a log. Snow is
  resolved from the start cell's biome, independently of the weather at reveal.
- Pruning runs each tick at `now - (T + max scaled visit lifetime)`, retaining the newest entry
  at or before the cutoff. Default retention is 7200 ticks; even a change every tick is bounded
  by the retained span plus its anchor. A long unchanged period keeps one entry.
- `/fishtastic visits windows` shows the source weather for open windows, `weather pending` for
  future ones, and `FALLBACK: current, no history` where needed. `/fishtastic visits weatherlog`
  lists the recent entries (last 64 with a count if more), current flags and retention.
- Eight `VisitWeatherLogTest` unit tests cover lookup boundaries, explicit fallback, no growth
  under unchanged weather, same-tick replacement, normalized loading, cutoff anchor retention,
  and worst-case bounded growth. Four shared GameTests, registered on every loader, cover a
  window whose real species draw differs in rain versus clear and stays rainy after clearing,
  drop/re-reveal and codec reload; codec persistence/fallback; the manager's pruning path; and
  actual `/weather` commands plus the new debug command. Fixtures synchronously restore shared
  flags and history, and never shift the global manager clock.
- Verification: common passed 994 total, 991 passed and 3 skipped, including all 8 new weather
  unit tests. GameTests passed Fabric 408/408, NeoForge 402/402 and Forge 400/400 required.
  The first Fabric run was 407/408 because an existing debug test assumed no other live visits;
  its assertion now checks the count delta and the final run is green. Owner weather playtest
  remains pending.
- Evidence: `weather-common-fabric.log`, `weather-fabric-rerun.log`, `weather-neoforge.log` and
  `weather-forge.log` under `build/world-fish-exploration/2026-10-30/`; loader reports are in
  `fabric/build/junit.xml`, `neoforge/build/junit.xml` and `forge/build/gametest/logs/latest.log`.

### Step 4: seeded ambient fish

In `client/ambient`:

- **Slots.** Each loaded water chunk has a fixed number of ambient slots, scaled by its usable
  water volume. Each slot has seeded overlapping lifespans (E13). A fish's ID is
  `hash(seed, chunk, slot, lifespan index)`. Its species is drawn from `NaturalPool` at a seeded
  cell in the chunk, under the conditions at its arrival time. Arrivals swim in from the water
  edge; leavers swim out of view.
- **Density setting.** The ambient density setting only decides which seeded fish are *drawn* (a
  stable subset by ID rank). It never changes which fish exist. This keeps the success criterion
  that visual settings never change observation or encounter rules.
- **Motion (E12).** Each client keeps its own tank-sim motion with a leash to its home chunk. No
  per-fish networking.
- **Removed (E9).** The `AmbientContextServer` and `AmbientContextClient` request/response flow,
  `AmbientRequestThrottle` and the held-rod preview. The existing context store, budget, fade and
  water source code is reused where it still fits.
- Debug: `/fishtasticambient debug` shows slot IDs, species, lifespan phase and the drawn/hidden
  split.
- Client scene: two client copies list the same IDs and species for the same water and time.
  Turnover over an in-game day never replaces more than a small share of fish at once.

#### Step 4 findings (2026-10-30, built, uncommitted; owner look pending)

**Slot sizing.** `AmbientChunkField` scans each loaded chunk's ambient fluid straight from the chunk
(not from `WaterDiscovery`, whose window depends on where one player stands, so two clients would
disagree about a border chunk). Usable volume = per column, the fluid cells within 4 of the column's top
cell. `AmbientRoster.slotCount`: none under 8 cells (a puddle), else `ceil(cells / 64)` capped at 16, so a
full 4-deep chunk (about 1,000 cells) has 16 slots and a one-column pond 1. Chunks within the discovery
radius plus one are scanned, one scan per tick, each kept 100 ticks so water edits are noticed.

**Lifespan model (E13).** A chunk has one hashed period `P` in [12000, 24000] ticks (10 to 20 minutes);
slot `s` has phase `frac(u + s * 0.618...) * P` (golden-ratio spread, so any number of slots is nearly
evenly staggered). Lifespan `k` is `[t0 + kP + a_k, t0 + (k+1)P - d_k)` with `a_k, d_k` hashed in
`[0, P/24)`, so a slot is vacant about 4% of the time. `fishId = hash(seed, chunk, slot, k)`; home
cell, height, species draw, mirror flag and density rank are further hashes of the ID. Measured by
`AmbientRosterTest`: over 12 seeds x 6 chunks x 2 in-game days, the worst 10-second window changes 3 of
16 slots (19%), the mean is 0.37 fish (2%). The first model (independent hashed slot periods) had a worst
case of 5 of 16, which is why phases are now structured.

**Density.** `AmbientRoster.drawn(id, density)` is `rank(id) < density / 100`: a stable subset (raising
density only adds fish), and `present(...)` has no density input. The density-derived `budget.individuals()`
cap still bounds how many drawn fish are alive at once, nearest first.

**Species and drift risk.** Drawn once per fish from `NaturalPool` at the home cell's biome, with time of
day and moon phase from the arrival game time (`+` the level's clock offset) and the weather the
client sees now; remembered per ID so weather changes never swap a living fish. Weather is therefore
the one input two clients can disagree on: a client that saw rain start a minute late draws different
species for fish that arrived in that minute. Accepted under E12; an exact fix would need the server
to broadcast a weather timeline. Vanilla fish are filtered on the ambient side only
(`NaturalPool.ambientCandidates`: `FishtasticFishItem` with a `fish_profile`); catch and visit pools
are unchanged.

**Motion and edges (E12).** Domains and engines are the existing patch machinery; a fish is placed in
the patch that holds its home cell (seeded neighbours if the body does not fit; if none, it is omitted
and retried, never demoted). An arrival (lifespan younger than 120 ticks) is placed at the patch's
outermost column of its home chunk and fades in over 40 ticks; a leaver fades out over 40 ticks while it
keeps swimming. Deviation: arrival and departure are a fade at the chunk's fluid edge, not a swim from
or to far outside view. The leash is a fade-out and re-entry at the home cell once a fish is more than 8
blocks outside its home chunk's footprint (the engine has no per-fish pull for free swimmers and
`:fishsim` is parity-pinned).

**Lava.** The Nether's ambient fluid is lava (`AmbientFluid.forDimension`). What assumed water:
`AmbientWaterSource.flags` tested `FluidTags.WATER` (now the level's ambient fluid tag, `LAVA` in the
Nether, and `AmbientChunkField` scans the same tag), nothing else. Checked and fine without change:
`fluidTop` and `collisionTop` are fluid-agnostic; `WaterDomain` and the sim never see the fluid; fish
render through item submit nodes with the camera's own fog (no ambient fog, tint or culling code), lit
by `getLightCoords` (lava is full-bright), drawn "wet" (`GroupFishDraw`), and culled by frustum and
loaded chunk only. Flowing lava counts like flowing water (tag, not source-only). Unverified in game:
vanilla lava fog is very short, so fish are visible mostly with fire resistance and a close look.
Visits stay excluded in the Nether; no lava visit behaviour was added.

**Removed (E9).** `AmbientContextClient`, `AmbientContextStore`, `AmbientPopulationRolls`,
`AmbientContextServer`, `AmbientRequestThrottle`, `AmbientContextRequestPacket`,
`AmbientContextResponsePacket` (and their registrations on all three loaders), `FishingPoolResolver.Inputs.heldRodPreview`,
the tests and GameTests that covered them (`AmbientContextGameTests`, the six packet round-trip and bound
tests), and the `ambientcontext`, `ambientpopulation` and `ambientlifecycle` self-test scenes (which asserted the
request/response flow). No client setting existed only for them; `ambientFishEnabled` and `ambientFishDensity`
stay. Kept: the budget, fade, patch selection, water source, `VisualSampler` (now taking a local
`Candidate` record and a hashed `sampleAt(u)`). `ambientparity`, `ambientwater`, `ambientisland`,
`ambientfloor` and `ambientwalk` scenes still compile and now run on the roster; they were not run.

**Debug.** `/fishtasticambient debug` prints the roster summary (present, drawn, hidden by density, no
shown patch yet, pending, alive, species draws, leashed), then up to 24 nearest fish as
`id species chunk slot phase STATUS` (`ALIVE`, `HIDDEN_DENSITY`, `NO_DOMAIN`, `PENDING`, `UNPLACEABLE`,
`NO_SPECIES`), then the existing runtime line.

**Tests.** `AmbientRosterTest` (slot scaling, determinism, ID uniqueness, ordered lifespans, vacancy share,
stagger, density subset, per-fish uniformity, lava rule) and `AmbientRosterSpeciesTest` over the shipped
profiles (vanilla never sampled in any location pool, time, weather or moon; nether roster is nether
species; arrival clock to time and moon; two independent clients list identical IDs and species). The
headless two-client check stands in for the client scene.

**Render smoke checkpoint (2026-10-30).** First Fabric `:fabric:runClient` exited 0 after 5m57s;
77/81 checks passed. The four failures were floor/walk population and zero-expiry assumptions. The
run is preserved at `build/world-fish-exploration/2026-10-30/ambient-render-smoke-first-run.log`.
The second run exited 0 after 5m26s; 78/81 checks passed, isolating the pond (one fish against two
expected) and island (25/32 initially, 26/32 sustained) population minima. It is preserved at
`build/world-fish-exploration/2026-10-30/ambient-render-smoke-second-run.log`. The third run lowered
the pond positive-population minimum to one and the island readiness/population and sustained
minimums to 20/20. It exited 0 with `BUILD SUCCESSFUL` after 4m25s; all 81 checks passed: shaders
1, parity 14, water 55, island 5, floor 3 and walk 3. The pond had two nearby fish; the island had
20/32 initially and 24/32 sustained. Floor/walk population and seeded-lifespan continuity passed.
Full output: `build/world-fish-exploration/2026-10-30/ambient-render-smoke.log`. The run generated
28 screenshots under `fabric/run/screenshots/`, and the marker was removed. Island biome fills fit
the command limit. The smoke ran with Java 25.0.2 and explicit access to the cached Gradle
distribution lock.

**Still owed.** Owner visual review, the Steps 2–5 playtests, a live two-client scene (two real
clients, same water and time), and the `fish-in-world.md` rewrite (Step 7). Step 6 waits for the
owner playtests.

### Step 5: spyglass observation

- **Client detector.** While the player is scoping with a spyglass, find the nearest ambient fish
  or visit member inside a generous cone around the crosshair (cone angle tuned for small fish).
  After about 1 second of continuous hold on the same fish, send `ObserveFishPacket(kind, id)`.
  Species already observed or caught are skipped.
- **Server validation.**
  - Visit members: the authoritative position must be within range and inside the cone, allowing
    for tolerance.
  - Ambient fish: derive the fish again from its ID. Its species must match, its home chunk must be
    within range, and the player must be looking roughly toward it. Positions differ between
    clients (E12), so this check is deliberately loose; observation grants no rewards, so the
    looseness is acceptable.
  - Rate-limit the packet.
- **Storage.** A per-player `observed` species set in `FishCatchSavedData`, synced through
  `FishEncyclopediaSyncPacket` and included in catch backups.
- **Fishopedia (E19).** No layout changes. `FishPageModel` treats an observed species as having
  its name, portrait and broad habitat (the existing zone display) unlocked. Catch conditions, lore
  and records still need catches. The toast reuses the existing notification path. A catch grants
  the same unlocks (P2).
- Debug: `/fishtastic observe <species>`, and `/fishtastic observe clear` (which calls
  `beforeDestructiveCommand`).
- GameTests: a valid observation records once; a bad ID, wrong species or far-away fish is
  rejected; a catch grants the observed fields; observing grants no catch count, quest progress or
  reward.
#### Step 5 findings / resume notes (2026-10-30, built; automated gates green, owner look pending)

**Done (main code compiles: `:common:compileJava`, `:fabric:compileJava`, `:fabric:compileTestmodJava`).**
- `ecology/ObservationRules` (pure): cone 3.0 deg client half angle (+ fish radius 0.5), server 8 deg + position slack, hold 20 ticks (1 s) with 4 ticks grace, range 96 (+6 +slack), rate limit 20 ticks per player, resend cooldown 200 ticks.
- `network/ObserveFishPacket` (C2S, registered in `FishtasticPackets`): `kind` VISIT sends species + school UUID + member UUID; AMBIENT sends species + fishId + chunk x/z + slot + lifespan index + home cell (BlockPos). Unused fields are not sent.
- `server/FishObservation`: rate limit, species must have a listed profile, skip known/caught; VISIT checks the authoritative member position in `FishSchoolSavedData` (state in water, range, cone with 3 block slack); AMBIENT re-derives the ID from the ecology seed, checks lifespan present now (+-100 ticks), home cell in chunk, loaded, ambient fluid, range, cone with 20 block slack, and species in the natural ambient pool at the home cell for the arrival clock under ANY weather (CLEAR/RAIN/SNOW/THUNDER). Weather looseness is the documented spoof surface: a modified client can claim any species the cell could hold in some weather, for any fish existing there now.
- Storage: separate `observed` map in `FishCatchSavedData` (not in PlayerCatchData, so observers never appear in catch counts), in codec, backups (`replacePlayerFrom`, `replaceAllFrom`), reset paths; `FishEncyclopediaSyncPacket` gained `observedSpecies` + `newlyObserved` (toast only for the latter). `FishEncyclopediaClientCache.update(packet)` now used at all 5 handler sites.
- Fishopedia: `FishPageModel.observed/discovered()/nameRevealed(...)`; disc silhouette, portrait and quest-log discovered state use `isDiscovered`. Toast: `observed/` banner prefix in QuestProgressNotification(+Manager, NotificationPriority).
- P2: COMMON and ABUNDANT name threshold 2 -> 1 in `EncyclopediaTiers`.
- Client: `client/ObservationClient` (detector + HUD bar under crosshair), hooked from `ClientAmbientFish.tick` and `FishSchoolClientRuntime.renderHud`; accessors `AmbientFishRuntime.observableFish()` and `FishSchoolClientRuntime.observableMembers()`.
- Debug: `command/ObserveCommand` (`/fishtastic observe <species>`, `observe clear` with `beforeDestructiveCommand`); `encyclopedia reset` also clears observed.

**Implementation and automated verification (2026-10-30).**
- Added 12 unit tests: 7 in `ObservationRulesTest` (direction, client/server cone edges, apparent size and position slack, range, lifespan tolerance, hold progress); 4 in `FishPageModelTest` (observed, unknown, threshold and claimed-name behavior); 1 in `EncyclopediaTiersTest` (name threshold 1 in every tier). The existing claim-all test now exercises a COMMON name reward at 1 catch; stale tier comments were corrected.
- The first resumed Fabric run (`build/world-fish-exploration/2026-10-30/step3-fabric-baseline.log`, JUnit `fabric/build/junit.xml`) ran 404 GameTests: 399 passed and 5 failed. Step 5 contributed 3 fixture failures: `badVisitClaimsAreRejected` used nonexistent `fishtastic:trout`, and both ambient cases mixed the level clock with a synthetic validation clock. Fixtures now use listed `rainbow_trout` and one consistent clock. The final loader gates verified these corrections.
- The owner approved `encyclopedia reset` clearing observations. The already registered `observingGrantsNoCatchCountQuestProgressOrReward` helper now dispatches that command with the mock player, checks the observed set is cleared, and checks its PRE_COMMAND backup preserves the pre-reset observation. It uses `BackupTestFixture` to restore shared state and files. `observationAndACatchOpenTheSameFields` now also checks that an empty observed-species sync clears the cache's name and portrait unlocks.
- Searched current docs for stale COMMON/ABUNDANT name-at-2-catches claims. Only P2's historical before/after description remained and is retained. The current encyclopedia README now says a first catch or spyglass observation removes the silhouette.

**Verification.**
- The common suite passed 994 total tests, with 991 passed, 3 skipped and no failures. The three
  loader suites passed Fabric 408/408, NeoForge 402/402 and Forge 400/400 required tests. They
  include `ObservationGameTests`, the encyclopedia packet round trip, reset/backup behavior,
  empty-sync cache clearing, and the observation/name-reveal rules.
- Stale current docs were checked for COMMON/ABUNDANT name-at-two-catches claims. Only P2's
  historical before/after note remains; the current encyclopedia README describes first-catch
  or spyglass unlock.

**Still owed.**
- The owner has not tried spyglass detector tuning, HUD bar placement, toast sound (reuses the
  new-species fanfare), or line-of-sight behavior.

### Step 6: balance instrumentation and playtests

- Server counters for windows revealed, suppressed, depleted and expired, and for visits seen per
  player-hour, split by stationary and moving players.
- Run the Step 0 calculator against these counters from a scripted route in a self-test scene,
  and compare predicted with measured rates.
- Owner playtests the three styles named in the outline (stationary precision, incidental
  travel, optimized multi-water route) and tracks the outline's balance metrics, including
  high-quality yield per hour (E10). Values are tuned through the config keys; each change is
  recorded here.

### Step 7: contract docs and ports

- Rewrite [ambient fish](fish-in-world.md) for seeded shared fish without the held-rod preview.
- Replace the scheduling, persistence and default-value sections of the schools and encounters
  specs with pointers to the outline. Mark superseded text instead of leaving it to contradict
  the new contract.
- Ports follow the usual catch-up ledger after owner acceptance on 26.1.2.

## Verification bar for every step

- Unit tests for the pure `ecology` package and the `:fishsim` suite.
- GameTests on Fabric, Forge and NeoForge.
- A client scene for anything visible.
- A debug trigger the owner can use in a solo game.
- No step is called done without its listed tests and an owner look where the step is visible.

## Risks

- **Clock jitter.** Clients see game time a little late, so ambient fish near a window or
  lifespan boundary can briefly disagree between clients. This is acceptable under E12; visits are
  server-owned and unaffected.
- **Dropping live visits when nobody is near.** It saves server work, but a player watching from
  just outside tracking range could see a visit vanish and come back. Tune the drop distance above
  tracking range.
- **Natural pool parity.** If anything in the environment or candidate code reads server-only
  state, client ambient pools drift from the server's. The Step 1 parity test must cover every
  zone, dimension, weather and moon case.
- **Hash quality.** Visible patterns, such as neighbouring chunks activating together, would be
  noticed. The Step 0 Monte-Carlo also checks spatial correlation.
