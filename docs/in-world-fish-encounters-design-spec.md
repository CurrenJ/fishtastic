# In-world fish encounters

**Status:** Written design decisions accepted. §13 tracks implementation progress and owner in-game review separately; accepting the design does not mean an in-game review has passed. GLIDE and lava visits remain later work. Owner decisions O1–O7 and the §3.2 shape table were approved 2026-10-26 (§12).

**Date:** 2026-10-26

**Purpose:** Widen interactive schools from four curated species to most of the roster, and add
solitary and small-group visits alongside schools. This spec builds on
[Interactive fish schools](interactive-fish-schools-design-spec.md). Every contract there still
holds unless a section below says otherwise: authority, binding, reward identity, tackle, persistence,
multiplayer and performance.

## 1. Summary

An in-world encounter is a rare, shared event. Every so often, catchable fish arrive in nearby water.
The player notices them and almost always goes fishing. Today that event is always a school and is
always one of four common species. After a few sightings the player knows what they're getting.

This proposal keeps the event rare and random and widens what it can be:

- **Any species whose movement style is ready can appear**, drawn from the same natural pool as now.
  Most sightings stay common. Now and then one is something special.
- **A visit has a shape:** solitary, pair, loose group or school. The shape comes from the species,
  so a lone fish and a school feel different and signal different things.
- **Players can recognize that a visit arrived**, and nearby players can notice it. Species
  identification before a catch is not promised.

## 2. Framing

The design rests on how players actually meet the feature:

| Fact | Consequence |
|---|---|
| A visit comes about once per ~10 minutes per region (`schools.attemptIntervalTicks=6000`, `schools.encounterChance=0.5`). | It's an event, not a fishing mode. Blind casting remains the main loop. |
| The species is a natural no-bait roll at the spot. | Players can't pick or wait for a species, so showing rare fish doesn't let them target the catch table. |
| Players almost always fish a sighting. | The reward ceiling and the surprise matter more than the decision to engage. |
| A school catch is an ordinary catch (no quality, XP or loot multiplier; base spec §10.2). | The thrill comes from the species and the moment, not from a bonus. |

The base spec's §18.2 still quotes the older 24,000-tick / 12% defaults. The values above are the
shipped defaults since 056bec03.

### 2.1 Goals

1. Make each sighting a small lottery: usually familiar, occasionally exciting.
2. Show species ecology through visit shape. Schooling fish arrive in schools; predators and
   oddballs arrive alone.
3. Let a player who spots a fish know roughly what they're looking at before the catch.
4. Keep total encounter frequency and server cost about where they are now.

### 2.2 Non-goals

- No change to blind casting, bait pools, the Almanac or the encyclopedia thresholds.
- No school-exclusive species or in-world-only rewards (the base spec's §2.2 exclusion stands).
- No player-controlled targeting: no lures that summon species, no "call a fish" items.
- No lava encounters in this spec (see §5.3).

## 3. Visit shapes

A visit picks a species first, as now, then a shape, then a member count.

| Shape | Members | Formation | Reads as |
|---|---|---|---|
| Solitary | 1 | Wanders alone | "Something notable is here." |
| Pair | 2 | Loose, often staggered | A small, intimate find |
| Loose group | 2–4 | Wide spacing, independent headings | Common reef or river life |
| School | 3–6 (cap `schools.maxMembersPerSchool`) | Tight, aligned flock (current behaviour) | Abundance; several easy catches |

### 3.1 Authored shape per species

Each species declares its shape in its profile's `world_encounter` block (§8). The shape is authored,
not derived (O4, decided): a derived rule put 28 of 41 species alone, including natural schoolers such
as Red-bellied Piranha. Temperament is not an input, because it describes minigame fight style, not
how a fish lives.

Constraints:

- Only FREE_SWIM species may be a `school`. Other classes can be solitary, pair or loose group.
- A species with no authored shape falls back to its `base_weight`: ≥ 25 school, 8–24 loose group,
  ≤ 7 solitary. This is a safety net for new species, and the add-fish-species checklist should still
  author a shape.

### 3.2 Proposed shape table

Approved by the owner 2026-10-26. Shapes reflect each animal's real behaviour, softened toward solitary for
rare, large or flagship species so a lone fish stays a notable sighting.

| Shape | Species |
|---|---|
| School (8) | Bluegill, Bridle Shiner, Greenstripe Barb, Neon Tetra, Red-bellied Piranha, Blind Cave Tetra, Glass Catfish, Indian Glassy Fish |
| Loose group (14) | Yellowstripe Grunt, Yellowline Goby, Rainbow Trout, Clown Loach, European Grayling, Arctic Char, American Paddlefish, Longnose Gar, Parrotfish, Blind Cavefish, Waterfall Climbing Cave Fish, Garden Eel *(colony)*, Portuguese Man o' War, Starfish |
| Pair (9) | Lined Seahorse, Moorish Idol, Neon Goby, Oscar, Golden Trout, Golden Mahseer, Royal Garden Eel, Trapania scurra, Willan's Chromodoris |
| Solitary (21) | Northern Pike, Lionfish, Coelacanth, Electric Eel, Ocean Sunfish, Largetooth Sawfish, Leafy Sea Dragon, Angler Fish, Chaunacops, Lizardfish, Betta, Black Ghost Knifefish, Bull Trout, Ornate Bichir, Ophisternon candidum, Rainfordia, Flapjack Octopus, Glass Squid, Common Octopus, Japanese Spider Crab, Plaice |

GLIDE species (Giant Manta Ray, Frozen Giant Manta Ray, Acute Iaspis) and the nether species get
shapes when their phases open (§10).

### 3.3 Why rare species lean solitary

- **The shape signals the find.** If rare species tend to come alone, a single fish in the water is
  exciting before the player sees what it is.
- **It caps rare yield without a special rule.** The base spec warned that "the species draw alone
  does not bound total rare-fish yield" (§10.2). A rare species arriving as one fish caps each rare
  sighting at one catch. Rare species authored as pair or school (Piranha, Moorish Idol, Royal Garden
  Eel) should be checked against the yield estimate in §7.
- **It's believable.** Large predators and oddities read wrong in a tight school.

Because the species roll is weighted, most visits still come from common species and most visits
stay schools or groups. Shape mainly changes how the uncommon visits look.

## 4. Approach and bite by locomotion class

The school sim already runs a species' own locomotion class (`FishSchoolManager` builds the `FishSpec`
from the resolved species). However, the approach-to-bobber behaviour was built and tuned on
free-swimming schoolers only. Each class needs its own approach that looks natural. A class becomes
eligible only once its approach is accepted in game.

| Class | Species (non-nether) | Approach proposal | Readiness |
|---|---|---|---|
| FREE_SWIM | 41 | Current approach. Large solitary fish may need a slower, wider final turn. | Built (Phase 1) |
| ANCHORED | 2 (garden eels) | Reversed: the fish never travels. A bobber landing within 2 blocks (horizontally) of an emerged fish triggers a bite after a short delay. A colony can host several. | Built (Phase 3) |
| DRIFT | 3 | Slow drift toward the bobber with a longer `approachMaxTicks`. The bite is a pulse onto the bobber. | Built (Phase 3) |
| BENTHIC | 6 | Bottom bite (§4.4, O6 decided): crawls along the floor to beneath the bobber; the bobber dips. Nothing rises. | Built (Phase 4) |
| GLIDE | 3 | Not eligible. Blocked on the GLIDE redesign. | Parked |

Counts come from the 2026-10-26 `fish_profile` data. Of the 51 free swimmers, nine are nether-only
(excluded for now, §5.3) and Shrimp is denied (§5.2).

### 4.1 Large bodies

Some free swimmers are big: ocean sunfish, sawfish, paddlefish and longnose gar. The current minimum
water size (6 connected cells) is far too small for them. Water validation must use the species'
`WaterDomain.BodyBox` fit, the same gate tanks use, so a large fish never arrives in a pond it can't
turn in. If no fit is found, the visit doesn't happen. There is no fallback species (base spec §5.3).

### 4.2 Solitary approach feel

A lone fish has no school to return to after a failed attempt. On recovery it keeps wandering locally
until the visit ends. Its visit lifetime may be shorter than a school's, so a missed rare fish
doesn't linger for ten minutes.

### 4.3 ANCHORED colonies

Garden eels are the non-swimming class that most clearly lives in groups. Their colony is placed on a valid
sand or gravel floor patch and stays put. The ANCHORED retract/emerge reaction to watchers
(locomotion doc) can work like stalking: a player who approaches too fast makes the colony retract,
and a retracted fish won't bite until it re-emerges.

### 4.5 Bite reach is a cylinder (2026-10-31)

A fish takes a bobber when it is within `schools.biteReachHorizontal` (default 4) of it sideways and at
most `schools.biteReachDepth` (default 12) below it, or up to 1 block above it. The bobber's depth is not
the player's to control, so depth does not limit reach; only its position over the fish is the skill. When
several fish qualify, the one nearest sideways wins (depth only breaks near-ties). Eels are sideways-only
at `EEL_REACH` (2). Replaces the old 3D `schools.interestRadius` (8), which cut sideways reach as the fish
got deeper.

The visit water scan reaches 12 blocks below its seed (was 6) so deep fish exist at all. A column whose
water continues under the scan is marked `truncated`, which gives it no floor: before this, the scan's
bottom edge was treated as ground and a floor creature (garden eel) in medium-deep water was placed
floating above the real sand.

### 4.4 BENTHIC bottom bite

Floor dwellers never leave the floor. A benthic member is interested in a bobber when the water
column below the bobber reaches a floor cell the member can crawl to through connected water, within
the bite reach horizontally (§4.5) and a bounded depth below the bobber (12 blocks). It crawls to
the floor cell directly beneath the bobber, pauses, and the bobber dips with the normal bite feedback.
The line is implied to reach the bottom; nothing is drawn rising.

- The fish stays visible on the floor during the bite window, so the player sees which fish took it.
- If the column below the bobber is too deep, blocked or not connected to the member's floor, there is
  no interest. The bobber is not pulled toward the fish.
- Recovery after a failed attempt is a crawl away, never a snap.
- Tackle is unchanged. Any supported rod setup can take a bottom bite (base spec §9).

## 5. Eligibility

### 5.1 Derived rule replaces the curated tag

A species can appear when all of these hold:

1. Its item is a `FishtasticFishItem`.
2. Its locomotion class is marked ready (§4). This is a code-side list, not per species.
3. It's in the natural no-bait pool at the spot (unchanged).
4. The water passes the body-fit check for that species (§4.1).
5. It isn't in the new deny tag.

`fish_school_enabled` becomes an **allow override**: a species in it is eligible even if its class
isn't marked ready yet, for staged testing. A new `fish_encounter_disabled` tag excludes species
the rule would otherwise include. Base spec §15 required that a new species not become eligible
automatically before review. That intent is kept by reviewing per class instead of per species.
Adding a fish to a ready class still needs a quick in-game look before release, recorded in the
add-fish-species checklist.

### 5.2 Exclusion candidates

- **Shrimp** and **Fried Shrimp** are excluded through `fish_encounter_disabled` (O2, decided).
  Shrimp has weight 100, the highest of any profile, so it would dominate sightings in its habitat.
  Fried Shrimp is cooked food, not a live fish.
- **Molten Moorish Idol.** The flagship species. It's nether-only, so it's out for now (§5.3), but
  it should be a deliberate decision when lava arrives.

### 5.3 Nether / lava

School water must currently be source **water** (`FishSchoolManager` water-cell check). The nine
nether-zone species are caught in lava, so they can't appear until a lava domain exists. That means
lava cell validation, a lava-safe cue, and confirming the sim and renderer behave in lava. It's a
later phase, not part of this spec.

## 6. Recognising the encounter

### 6.1 Recognising an arrival (decided)

Showing the species name when a player looks at a fish is out of scope for now (O5, decided).
The design promises that players can recognize a visit, not that they can identify its species
before catching it. The fish model, arrival cue (§6.3) and quality cue (§6.2) may help players
recognize the event; species identity becomes certain through the catch. The idea on file—show the
name for species already caught and `???` for species never caught—remains deferred.

### 6.2 The sparkle shows quality, not rarity (decided)

The sparkle represents the fish's quality, as it does today; it does not encode species rarity
(O7, owner decision 2026-10-26). The one change: creatures that barely move (garden eels, a crab
between scuttles, a drifting jellyfish) lay no swim trail, so they give off a slow stationary
sparkle in their quality's tint instead. Every catchable fish stays visibly marked.

### 6.3 Arrival cue

At ~1 visit per 10 minutes, a player who's building or mining will miss most of them. When a visit
forms within tracking range of a player, the water surface above the arrival point shows a brief
burst of ripples or splashes.

**No sound.** Automatic and ambient effects stay silent in this mod (sounds are for player actions).
The arrival cue is visual only, and a client toggle can disable it.

## 7. Balance

- **Frequency is unchanged.** Shape changes how many fish a visit contains, not how often visits
  happen. Visit count, scheduling and region caps stay as they are.
- **Estimate rare yield for every visit shape.** For each rare species, estimate catches per hour
  from visits/hour × P(species) × expected catches per visit. Include solitary, pair, group and
  school shapes using their member bounds; a solitary visit contributes at most one. Set acceptable
  yield bounds from those estimates before considering the balance contract complete.
- **Common yield drops slightly.** Some former schools become pairs or groups. That's fine; the
  value of a sighting moves from volume to surprise.
- **No in-world-only bonus** (O1, decided). The reward stays an ordinary catch.
- **The encyclopedia pace is unaffected in practice.** One or two extra catches per visit is noise
  against the ≤ 50-catch thresholds.

## 8. Data and schema

Optional `world_encounter` block on `FishProfile`. Every field is optional; a missing `shape` uses
the weight fallback (§3.1):

```json
"world_encounter": {
  "shape": "solitary",
  "members": { "min": 1, "max": 1 },
  "lifetime_scale": 0.5
}
```

- `shape`: `solitary | pair | loose_group | school`. Authored per species (§3.1); `school` only for FREE_SWIM.
- `members`: clamped to the shape's range and the server cap.
- `lifetime_scale`: multiplies `schools.activeLifetimeTicks`.

Ecology stays in the existing biome, time, weather, moon and zone tables. This block only describes
the encounter (base spec §15).

Saved data adds `shape` to the school record. Old saves load as `school`. The wire format adds the
shape so clients pick the right formation.

## 9. Performance

- Visit frequency and caps are unchanged (§7), so server scheduling cost doesn't grow.
- Members per dimension (`schools.maxMembersPerDimension=24`) is unchanged. Solitary visits use
  fewer members than schools.
- Body-fit water checks for large species reuse the bounded water search. A big-fish candidate that
  fails fit is rejected without a wider search.
- New classes (DRIFT, BENTHIC, ANCHORED) run in the existing `FishSchoolSim`. Measure them with the
  existing `FishSchoolPerf` phases before enabling.

## 10. Phased rollout

Each phase ships with a debug trigger the owner can use in a solo game. The existing
`spawnForDebug` path gains a `shape` argument.

| Phase | Content | Gate |
|---|---|---|
| 1 | Shapes (§3) and derived eligibility (§5) for FREE_SWIM water species; body-fit check (§4.1); `world_encounter` schema | Owner looks at solitary vs school sightings and at a large-bodied fish in game |
| 2 | Recognition: stationary sparkle for slow creatures and arrival ripples (§6.2–6.3) | Readability playtest in day, night and murky water |
| 3 | ANCHORED colonies, then DRIFT (built 2026-10-26) | One class at a time, each accepted in game |
| 4 | BENTHIC bottom bite (§4.4) (built 2026-10-26) | Accepted in game for a crawler (crab) and a flat fish (plaice) |
| Later | GLIDE (after the GLIDE redesign), lava encounters (§5.3) | Separate specs |

Ports follow the usual catch-up ledger after each phase is accepted on 26.1.2.

## 11. Acceptance scenarios

| Scenario | Required result |
|---|---|
| Species with an authored shape rolls | Visit uses that shape; solitary yields at most one catch |
| Species with no authored shape rolls | Weight fallback (§3.1) applies |
| Common schooling species rolls | Visit is a school, as today |
| Species class not marked ready | Never appears unless in the allow-override tag |
| Species in the deny tag | Never appears |
| Large fish rolls in a small pond | No visit; no substitute species |
| Garden eel colony | Never moves; bobber nearby triggers a bite; retracts when rushed |
| Bobber floats above a crab within depth range | Crab crawls beneath it; bobber dips; crab never rises |
| Bobber above water too deep or not connected to the floor | No benthic interest |
| Solitary fish escapes | Recovers and wanders until its (shorter) lifetime ends |
| Shrimp or Fried Shrimp rolls | Excluded by the deny tag; never appears |
| Visit forms near a player | Silent visual surface cue; can be disabled client-side |
| Old save with schools | Loads; existing schools continue as `school` shape |
| Everything in base spec §16 | Still holds |

## 12. Owner decisions

### Decided (2026-10-26)

- **O1. In-world reward:** nothing extra. A sighting's catch is an ordinary catch.
- **O2. Shrimp and Fried Shrimp:** excluded through `fish_encounter_disabled`.
- **O3. Yellowstripe Grunt:** loose group, with no school override.
- **O5. Look-at name:** excluded for now (§6.1). Spyglass observation in the
  [world fish exploration outline](world-fish-exploration-design-spec.md) (E7, 2026-10-30)
  deliberately supersedes this for spyglass use; plain look-at names stay excluded.
- **O4. Visit shape:** authored per species (option B), with the weight rule only as a fallback for
  species without one. Temperament is not an input. §3.2 shape table approved 2026-10-26.
- **O6. Floor dwellers:** bottom bite (option B, §4.4) for all six BENTHIC species.
- **O7. Sparkle meaning:** quality only, never rarity (§6.2).

No open owner decisions.

## 13. Progress

### Phase 1 (built 2026-10-26, uncommitted, owner look pending)

What shipped:

- `VisitShape` (`common/.../school/VisitShape.java`): the four shapes, their member ranges, spawn
  spacing, the rarity fallback, the "only free swimmers school" rule, and each shape's flocking set.
  School and solitary keep `Tunables.GROUP`, so shipped schools move exactly as before. Pair and
  loose group are first-pass values (weaker alignment and cohesion, longer neighbour range), not yet
  tuned in the headless harness.
- `FishProfile.world_encounter` (`shape`, `members`, `lifetime_scale`), and the approved §3.2
  shapes written into all 52 profiles.
- Eligibility by rule (`FishSchoolManager.encounterEligible`): a Fishtastic fish, not in
  `fishtastic:fish_encounter_disabled` (Shrimp, Fried Shrimp), and either a ready class (FREE_SWIM
  only) with a non-nether habitat, or in the allow override `fishtastic:fish_school_enabled` (now
  empty). The same check gates spawning, keeps a visit alive on rescan, and gates the minigame payout.
- Body fit: a visit forms only if its water passes the species' own engine size gate, and departs if
  edits shrink the water below it. Large fish therefore skip small ponds.
- Saved visits record their shape (old saves load as `school`); the shape is in the setup packet so
  client copies build the same engine.
- Debug: `/fishtastic schools spawn <species> <quality|any|mixed> <shape>`.

Differences from earlier behaviour worth knowing:

- Schools now need at least 3 fish (Phase A allowed 2). With the default `maxMembersPerSchool=4` a
  school is 3–4 fish.
- If the dimension's member budget cannot fit a shape's minimum, no visit forms (Phase A shrank to 2).
- `lifetime_scale` is supported but no species sets it yet; every visit keeps the 12,000-tick lifetime.

Verification:

- Unit tests: `VisitShapeTest` (8), `WorldEncounterTest` (5), plus the existing school sim and
  replica tests, all pass.
- GameTests pass on all three loaders: Fabric 392/392, Forge 384/384, NeoForge 386/386, including
  `encounterEligibilityFollowsTheRule`, `spawnedVisitsTakeTheirShape` and
  `schoolsSavedBeforeShapesLoadAsSchools`. `liveSchoolApproachesAndTriggersOwnedBite` now restores
  the mock player's bobber link each check (the fix the 1.20.1 port already carried); without it the
  test failed once on Forge with `ownerHasHook=false`.
- Client scene `fishschoolshapes` (Fabric, isolated run): pike as solitary (1), bluegill pair (2),
  clown loach loose group (2), bluegill school (4); all 7 checks pass and all four client copies held
  zero lockstep mismatches over 261 steps. Frames are in the run's `solo/screenshots/`.

Phase 1's gate is still the owner's in-game look at a lone fish, a pair, a loose group and a
school, and at a large fish (pike, sunfish) in open water. Ports follow after acceptance.

### Phases 2–4 (built 2026-10-26, uncommitted, owner look pending)

Built ahead of the earlier phases' in-game gates, at the owner's request to implement the whole
proposal. Each phase's own gate (§10) is still the owner's look.

Phase 2, recognition:

- Stationary sparkle (§6.2, O7): a member that moves under 0.02 blocks a tick gives off one rising
  glint every 12 ticks in its quality's tint. Swimmers almost never trigger it; eels, resting crabs
  and drifters do. No rarity cue.
- Arrival ripples (§6.3): the setup packet carries the visit's age; a client receiving a visit
  under 100 ticks old draws three spreading quality-tinted rings over each fish plus a few vanilla
  splash particles. Silent. Client toggle `fishSchoolArrivals` /
  `/fishtasticambient schools arrivals <true|false>`, default on.

Phases 3–4, the other classes. All four ready classes are now eligible (GLIDE stays parked):

- Engine (`:fishsim` `FlockEngine`): a per-fish seek goal (`seek`, `clearSeeks`) the crawl and drift
  steps steer toward, and a `shapeDrive` read-out. Off unless set, so tanks step exactly as before
  (fishsim suite 575 tests, 0 failed).
- `FishSchoolSim`: a non-swimmer's `Approach` becomes a seek, not the swimmers' nose errand. Arrival
  and the hold are judged after each step from shared state, so every copy fires the same touch and
  let-go at the same step. Floor creatures are placed on the floor under their saved spot.
  `goalFor` picks the goal by class: the nose point (swimmers, drifters), the floor under the bobber
  within 8 blocks (crawlers, the bottom bite), or the eel's own spot when the bobber is within
  1.5 blocks and the eel is out.
- Crawlers walk at 0.35 blocks/s with no pauses on the way and get twice the approach time;
  drifters are carried at 0.2 blocks/s and get three times.
- Eel stalking (§4.3): a new lockstep `Watcher` input. The server sends it when the nearest
  sprinting player within 8 blocks of an eel visit changes (or moves half a block); the engine's own
  startle then ducks eels within 3 blocks of them, and a ducked eel takes no bobber.

Superseded by §4.5 (scan now 12 blocks, unknown beds get no floor). Old limit: a visit's water scan reaches 6 blocks below where it starts, so a crawler or eel needs a
floor within that depth to have any floor at all. In practice floor creatures appear in shallow
water (shores, reefs, shallow lagoons) and not over deep open ocean, and the 8-block bottom bite is
capped by the scan. Deepening the scan for floor classes is possible later at some scan cost.

Verification:

- Unit tests: `FishSchoolClassesTest` (7): crawler bottom bite, the 8-block depth limit, drifter
  carried to the bait, eel reach and emergence, eel ducking from a rusher, two copies of each class
  agreeing through seeks, watchers and a member leaving, and the `Watcher` wire round trip.
- GameTests: `crawlerTakesABottomBite`, `drifterIsCarriedToTheBait`,
  `eelTakesABobberOverItsBurrow` drive a real bobber through the server to an owned bite and a
  bound minigame session. These are ocean animals and the test world is plains, so the fixture
  skips habitat with `ignoreHabitatForTest`; the habitat rule itself is unchanged.
- GameTests on all three loaders after Phases 2–4: Fabric 395/395, Forge 387/387, NeoForge 389/389;
  `:common:test` and the fishsim suite pass.
- Client scene `fishschoolclasses` (Fabric): an eel colony (3), a man o' war group (3) and a spider
  crab (1) spawned; arrival cue shown for all 3 visits; 91 stationary glints; every copy in
  lockstep (zero mismatches).
- Client scene `fishschoolcrab` (Fabric, run on its own): the crab walked 2.9 blocks along the
  floor and took a bottom bite after 196 ticks; 137 frames filmed. Run after `fishschoolclasses` in
  one launch, it failed because its probe followed the earlier scene's crab; the scenes share a pool
  and are meant to run in separate launches.


### Fix: big swimmers frozen at the surface (2026-10-26, owner report)

A longnose gar visit in the owner's world swam in place just below the surface, never bit, and
stayed stuck across reloads. Root cause, reproduced headlessly: visits place fish on block centres,
and in the top water layer a swimmer longer than about 0.4 blocks has part of its body above the
surface. The engine refuses every step from a spot where the body does not fit, so the fish animated
but never moved. Bluegill (0.27 blocks) always fit, which is why Phase A never showed it; Phase 1
made big swimmers eligible. The saved position was reused on load, so it persisted.

Fix in `FishSchoolSim`: a swimmer or drifter placed where its body does not fit is moved to the
nearest point where it does (straight down first). This runs identically on every copy and frees
already-stuck fish on their next load. A visit also no longer forms in water too shallow for its
species' body anywhere (`hosts` now checks vertical room, not only horizontal run). Regression
tests in `VisitShapeTest`; Fabric GameTests 395/395 and the `fishschoolshapes` scene (zero lockstep
mismatches) pass after the fix.

Owner confirmed the fix in game, 2026-10-26.
