# Fish Companions — The World Around You (Phase 5 rework)

**Written:** 2026-10-15 · **Status: implementation in progress; owner review gates remain open.**

This replaces the shipping plan for sim Phase 5 ([fish-companions-sim-plan.md](fish-companions-sim-plan.md)
§12). Phase 5 as built (the ambient moods) is **not** signed off as shipped. Its code is kept as the base
for this rework; §12.7's gate questions are superseded by the readability gate here (§7).

---

## 0. Owner decisions (2026-10-15)

| # | Decision |
|---|---|
| **W1** | **The flip is accepted.** Reactions to the blocks and features around the player become the main content. Moods shrink to a thin, readable tone layer on top. |
| **W2** | **Particles yes, sounds deferred** (no capacity to make them now, and they risk being annoying). Particles respect the vanilla particle setting (spec R7.5). |
| **W3** | **Tanks (E3, E4) stay deferred** to the end of this work. Once everything else is done, we decide whether we still want them (§8, W9). |
| **W4** | **A `companion` block in `fish_profile` is allowed, but only if there's a genuine need.** First weigh what the existing fields (temperament, zones, biome/time/weather/moon weights) can already carry (§5). |
| **W5** | **First features: the jukebox, water nearby, and lights at night**, plus the existing campfire bask ported over. |
| **W6** | **Performance is part of this work, and it is measured.** The block scanning gets cached and spread out over ticks, with numbers before and after (§6). |

---

## 1. Why: the audit of Phase 5 as built

1. **Most moods are built to be invisible.** The changes are capped at about ±10–20% on idle pace,
   dwell, curiosity and band. The acceptance bar ("seen % within a point of no mood") measured what a
   mood costs, never whether anyone can see it. Glum and sleepy have no look (`SLEEPY` is undrawn,
   §11.2), and walkers and perchers only ever show a pose. What reads today: the shiver, the moon
   gaze, and the occasional glad lap.
2. **The inputs are global state, not the world around the player.** Biome, temperature, weather and
   time change over minutes to hours, so a steady mood is background noise. The only local input,
   the 6-block warmth scan with its bask, is also the only part that reads as a creature deciding
   something.
3. **Mostly negative, and the common case shows nothing.** Three of the five feelings are bad ones.
   Being at home means nothing bothers the fish, with no reward for going there. Homes come from
   catch tuning: bluegill's only home is cherry grove, lionfish counts as cold-hardy because of
   `snow: 2.0`, and 28 of 64 species have no `biome_weights`, so they're never at home anywhere.
4. **Water, the biggest change in a fish's world, is ignored.** Only `PerchBody` reads `inWater`.
5. **Spec items not built:** steam in the Nether (E2). No particles at all.

---

## 2. Principles

- **Readable in about 5 s, first person, with the overlay off.** If a reaction can't pass that, it
  either gets a look of its own or it gets cut.
- **Show things when they change.** Arriving at a jukebox, the lights going out, entering home water:
  each gets a clear beat of 2–4 s. Steady states fade to a light tint that doesn't nag.
- **Upbeat first.** Most reactions are delight or curiosity. Bad moods have a cause the player can see
  and, where possible, a fix (cold → a fire).
- **Every move is a swim** (feedback: companions always swim). A reaction that needs the fish
  somewhere is a goal that swims it there, under the band and the owner-leaving override. A fish
  never stays behind at a feature when its owner walks off.
- **Temperament colours the reaction, and the species picks what it cares about** (§5). The same
  jukebox reads differently on a skittish fish and an aggressive one.
- **Headless first.** Each reaction is built and judged in `:fishsim` (viewer scene, probes) before it
  goes into the game. With no features and a neutral ambient, the fingerprints stay bitwise identical.
- **Seen % and fades are a guard, not the goal** (feedback: metrics are proxies). The goal is the
  readability gate (§7).
- **Every feature ships with an in-game dev trigger** (feedback: solo playtest commands).
- **Cosmetic only** (spec A2).

---

## 3. Architecture

### 3.1 World features (sim side)

- `WorldFeature(Kind kind, long id, Vec pos, ...)` in `grill24.fishsim.companion`. It's a typed thing
  in the world the fish can notice. `id` is stable while the feature exists (its block position
  packed), so habituation and cooldowns are per feature.
- Kinds for this work: `WARMTH` (moved here from `Ambient.warmth`), `WATER_SURFACE`, `LIGHT`,
  `MUSIC`. Later candidates (not in scope): `BUBBLE_COLUMN`, `RAIN_EDGE`, `COVER`, `TANK`.
- Payload per kind: `LIGHT` has its emission level; `MUSIC` has its source (a jukebox song with
  start tick and length, or a note block), its last beat tick and the beat's pitch; `WATER_SURFACE`
  has the zone of its water (ocean or river) where known.
- `CompanionWorld` gets `features()`. Like `Ambient.NEUTRAL`, an empty list keeps every existing
  run bitwise identical.
- The ambient keeps the slow inputs (place, temperature, weather, time, moon). It gains one fast
  input, **darkness at the owner** (the brightness there, 0..1), which the lights reaction needs. It
  also stops carrying warmth, which becomes `WARMTH` features.

### 3.2 The Notice goal

The bask is already an inspection of `PointOfInterest.Kind.WARMTH` that never habituates. Generalise
it into one **Notice** goal in the mind:

1. **Pick.** Each feature in reach scores interest = the species' base interest in that kind (§5) ×
   temperament × mood × novelty (habituation per feature id, recovering over minutes). The best score
   over a threshold wins, held by the mind's commitment rules.
2. **Approach.** It swims there and holds at the kind's distance. The usual band applies: when the
   owner moves off, the goal ends, the same way inspections end now.
3. **Perform.** A short per-kind behaviour (§4), shown as pose hints plus emotes (§3.3).
4. **Cooldown.** Per feature, so one jukebox doesn't absorb the fish for the whole song (§4.3 has
   the exception).

By class:
- **Swimmers and drifters** go to the feature.
- **Walkers** go if there's a ground path (the bask already does this).
- **Perchers and anchored fish** don't leave the owner. They **lean and WATCH** toward the feature,
  which turns the "percher shivers at a fire it can't reach" gap (§12.6) into a request: the fish
  asks to be taken there.

### 3.3 Emotes and particles (client side)

- The sim gets a second output next to `PoseHint`: **emotes**. These are one-shot events
  (`HOMECOMING`, `SPLASH`, `NOTE`, `SIGH`, `SNORE`, `STEAM`, `FROST`, ...) with a position.
- A new client `CompanionParticles` turns emotes into vanilla or fishtastic particles. It is
  rate-limited per fish, off at the MINIMAL particle setting and reduced at DECREASED. It never
  spawns inside the camera's near field, so a fish close to the player's face doesn't fog the view.
- Headless, the viewer draws emotes as markers so they can be judged without the game.

### 3.4 Moods, slimmed

Keep `Mood`, `Habitat`, per-member moods (spec §6, D13), the bask, the shiver and the moon gaze.
Change the rest:
- **Transition beats:** a change of mood fires an emote and a short pose beat. After that the mood
  is a light tint.
- **Every mood gets its own look:**

  | Mood | Look |
  |---|---|
  | cold | shiver plus frost puffs |
  | glum | droop, sinks lower, a sighed bubble now and then |
  | sleepy | `SLEEPY` finally drawn, plus slow rising bubbles |
  | glee | rolls and leaps; splashes in rain |
  | a non-Nether fish in the Nether | steam (E2) |
  | homecoming | a burst of bubbles and hearts, a lap, then content |

- **New: homecoming and in its element.** Entering a home place (§5) fires the homecoming beat. The
  owner in water, and above all in the fish's own zone of water, is joy.
- **Cut the hidden number changes** that have no look backing them: curiosity, band and dwell on
  glum/sleepy/cold. A lever stays only if an A/B shows it adds to what a mood visibly reads as.

---

## 4. The features

### 4.1 Water nearby

- **Trigger:** a water surface cell (water with open air above) within reach of the owner, and the
  owner not in water.
- **Swimmers:** drift to the edge and hover just over the surface, nose down. Now and then a **dip**:
  a short arc into the water and out again, with a `SPLASH` emote (splash particles and bubbles).
  Interest is higher for water of the fish's own zone (an ocean fish over the sea, a river fish over
  a river), since zones are real for water (§5).
- **Drifters:** drift over the water and sink their tentacles towards it.
- **Walkers:** go to the edge and peer in, and hop in and out too (owner, §9 Q1).
- **Perchers:** lean towards the water.
- **Owner in water:** the joy beat from §3.4. Real swimming beside the owner (E1) stays as the
  existing motion does today. A better water body is a candidate for later, not in scope.
- **Guard:** the dip must keep seen % (the fish goes under). We measure it and cap how often it
  happens.

### 4.2 Lights at night

- **Trigger:** darkness at the owner (night outdoors, or a cave or unlit room at any time) and a
  light-emitting block within reach (`getLightEmission() >= 10`: torches, lanterns, glowstone, sea
  lanterns, froglights, lit redstone lamps; soul lights count too).
- **By temperament** (no new data, §5):

  | Temperament | Reaction |
  |---|---|
  | curious (high curiosity: wily, ambusher) | moth: lazy loops round the light, nose to it |
  | skittish | keeps to the edge of the light; peeks in, backs off |
  | ambusher | hangs just outside the light, motionless (the anglerfish feel) |
  | docile | settles near the light close to the owner |
  | ghost | slow passes through the light |
  | others | the moth loop, milder |

- **Species:** night-active fish (`time_weights` night > 1) are drawn more strongly. Day fish that are
  sleepy at night still notice the light, but drowsily.
- **Priority:** outdoors under a full moon, a moonstruck species prefers the moon gaze to a light.
- **Particles:** an occasional faint glint at the light, so the "attention" reads in the dark.

### 4.3 The jukebox (and note blocks)

- **Detection is event-driven, with no scan.** The client is told when a jukebox song starts and
  stops at a position (`LevelEventHandler` events 1010 and 1011, `JukeboxSong.lengthInSeconds`), and
  each note block hit arrives as a block event (`NoteBlock.triggerEvent` runs on the client, carrying
  pitch). Like vanilla, a song already playing when the player arrives isn't heard and isn't danced to.
- **Behaviour** (owner, §9 Q2): if the owner is within a distance threshold of the jukebox (starting
  at about 8 blocks, tuned in the viewer), the fish goes to it and dances near it. Otherwise it dances
  where it is. A dance is a bob-and-roll loop. The song has no beat
  data, so the jukebox dance is a steady groove that doesn't pretend to be in time. **Note blocks are
  in time:** each note is a hop pulse whose height follows its pitch, plus a `NOTE` emote (the
  vanilla note particle, coloured by pitch).
- **By temperament:**

  | Temperament | Dance |
  |---|---|
  | metronome (`regular` = 1) | perfectly regular |
  | erratic | off-beat and twitchy |
  | aggressive | headbangs |
  | docile | sways close to the owner |
  | skittish | dances from further off |
  | ghost | slow, large sways |

- **Cooldown exception:** a song keeps the fish for its length, but the band still wins. If the owner
  walks off, the fish follows, dancing in place a few seconds more.

### 4.4 Warmth (ported)

The bask, the warmth hold and the shiver stay as built (§12.2–12.3), moved onto `WARMTH` features and
the Notice goal. Behaviour is identical: `MoodTest` and the `companionmoods` self-test still pass.

---

## 5. Where species traits come from (W4)

The question: can the existing fields carry companion traits, or is a new `companion` block needed?

**Temperament** (10 shared temperaments over 64 species) is personality: *how* a fish reacts. It
already has everything the reaction styles above need (curiosity, wariness, stillness, regular,
guard, peekOut and so on). **Reaction styles come from temperament, with no new data.** Temperament
can't say *where* a fish belongs, because it's shared across unrelated species.

**Ecology** (where a fish is at home, its climate, its hours) can mostly come from fields we already
have, if we read them better than Phase 5 does:

| Source | Use for the companion |
|---|---|
| `zones` NETHER, CAVE (12 species), HIGH_ALTITUDE (8) | Real places on land too: `Zone.resolve` detects cave biomes and Y bands, and snowy peaks always count as high altitude. Phase 5 only uses NETHER |
| `zones` OCEAN, DEEP_OCEAN, RIVER | Real **when the owner is in water** (or when judging a water surface): "its own water" |
| `biome_weights` ≥ 1 | Homes, as now. Small catch bonuses read as "loves cherry groves", which is charming rather than wrong |
| Climate | HOT from the Nether; COLD from HIGH_ALTITUDE, `is_snowy_peaks`, cold/frozen ocean tags. Drop the `snow` weight rule (it makes lionfish cold-hardy) |
| `time_weights` | Sleepy off-hours (as now), and night fish drawn to lights |
| `weather_weights`, `moon_weights` | As now |

**Recommendation: no new block yet.** Step W4 (§8) improves the derivation and prints a per-species
table (home places, climate, hours, which features it favours). The owner reviews it. Any species
that still reads wrong is the genuine need, and only then do we add an optional override. The
profile already has `companion_group`, `companion_gait` and `companion_drift`; any new data should
fold into one `companion` object rather than add a fourth sibling.

---

## 6. Performance (W6)

### 6.1 What it costs today (from reading the code, not yet measured)

- The warmth scan reads 13 × 7 × 13 = **1,183 block states per scan**.
- **The scan is per companion, not per owner** (`CompanionAmbient.Sampler` lives on each
  `ClientCompanion`). A party scans the same area once per unit, and **other players' companions
  scan as well**.
- It rescans every 20 ticks, **and whenever the owner has moved more than 3 blocks** since the last
  scan, so a sprinting owner rescans roughly every 11 ticks.
- Adding light and water to this design as-is would multiply that cost. Water in particular is
  everywhere near the sea.
- What's already cheap: the motion sampling (`LocalVoxels`, plan §11.5: 0 reads per tick standing,
  3 on average walking, peak 25), and block-change invalidation (`LevelRendererMixin` →
  `ClientCompanions.onBlockChanged`).

### 6.2 Design: one shared feature index

- **One `WorldFeatureIndex` per client level**, shared by every companion and owner. Companions
  query it by radius and never scan themselves.
- **Indexed per chunk section (16³).** Each section is classified once, then kept current by deltas.
- **Palette pre-filter.** `LevelChunkSection.hasOnlyAir()` and `maybeHas(isFeature)` check the
  section's block palette (a handful of entries) before touching any block. Sections with no
  candidate state are skipped without reading their 4,096 cells, which is most sections in most
  terrain. If the palette is the global one, `maybeHas` says "maybe" and the section is scanned.
- **Deltas, not rescans.** A block change (the existing mixin hook) reclassifies just that position.
  A chunk load marks its sections dirty; a chunk unload drops them. Standing still costs **zero**
  block reads.
- **Amortised.** Dirty sections are processed nearest-first under a per-tick budget (a cap on block
  reads, tuned from the W0 numbers), so walking into new terrain never spikes a tick.
- **Water is summarised, not listed.** Each section keeps a coarse grid of surface points (one per
  4 × 4 column cell at most), not every water cell, so an ocean doesn't flood the index.
- **Event features cost nothing to find:** jukeboxes and note blocks (§4.3).
- **Darkness** is one brightness read at the owner per tick.

### 6.3 How it's measured

- **W0 (before anything changes):** add counters (block reads, µs) to the warmth scan and record
  them in a self-test scene, `companionworldcost`. Scenarios: standing; walking and sprinting a fixed
  path; a party of three; a second (phantom) owner. Terrain: superflat, a village, a lush cave, an
  ocean shore, the Nether.
- **W1 gate:** the same scenes with the index, equal grounds (same paths, same seeds, both trees,
  feedback: validate before claiming). Report block reads per tick (mean and peak) and the worst tick
  in µs. Targets: zero reads standing, and a peak bounded by the budget. Same warm points found as
  before.

---

## 7. Gates and verification

- **Fingerprints:** with no features and `Ambient.NEUTRAL`, `FingerprintProbe -PfpAll` is bitwise
  identical after every step.
- **Guard:** `CompanionInvariantTest` and `MoodTest`'s pooled seen/fade bar keep their envelopes,
  extended to each new feature scene.
- **New probe metric: reaction share,** the share of the fish's on-screen time spent showing a
  distinctive reaction (pose, emote or Notice behaviour). It's a proxy that rises when reactions are
  visible; it doesn't replace the look.
- **The readability gate (owner):** viewer and in-game clips with the overlay off. The owner names
  what the fish is reacting to. A reaction that can't be named gets a stronger look, or is cut.
- **In game:** a self-test scene per feature (both loaders), and dev triggers:
  `/fishtastic companion feature jukebox|notes|water|light|warm` places or fakes the feature next to
  the player; `/fishtastic companion mood ...` stays.

---

## 8. Steps

| Step | Builds | Gate |
|---|---|---|
| **W0** Measure | Cost counters on the current warmth scan; `companionworldcost` self-test scene; baseline table | Numbers recorded here (§6.3) |
| **W1** Feature index | `WorldFeatureIndex` (per level, per section, palette pre-filter, deltas, budget); warmth served from it; `Sampler` scans removed | Equal-grounds A/B against W0; `companionmoods` still passes on both loaders |
| **W2** Notice goal | `WorldFeature`, `CompanionWorld.features()`, Notice goal, emote output, viewer markers; bask ported; perchers lean and WATCH | Fingerprints bitwise; `MoodTest` and invariants pass; bask unchanged |
| **W3** Particles | `CompanionParticles` (rate limits, particle setting, near-field guard) | Self-test spawns each emote; no particles at MINIMAL |
| **W4** Habitat | Better derivation (§5); per-species table for the owner | **Owner reviews the table**; decides if an override is needed. **Passed 2026-10-15: table approved, no override.** |
| **W5** Readability pass | Transition beats, a look per mood, steam, homecoming, joy in water; invisible levers cut (A/B) | Reaction share up; **readability gate** |
| **W6** Water nearby | §4.1, headless scene first | Guard plus **readability gate** |
| **W7** Lights at night | §4.2, darkness input | Guard plus **readability gate** |
| **W8** Jukebox and note blocks | §4.3, event hooks on both loaders | Guard plus **readability gate** |
| **W9** Revisit tanks | Owner decides whether E3/E4 is still wanted (W3) | Owner |

W0 and W1 come first, so the cost of everything after them is known and bounded from the start.

---

## 9. Owner answers (2026-10-15)

1. **Water dip for walkers:** yes. Walkers (crab, starfish) hop in and out too, not only swimmers
   and drifters.
2. **Jukebox:** go to the jukebox when the owner is within the distance threshold; otherwise dance
   where it is.
3. **Lights:** the temperament table in §4.2 is accepted.
4. **Particle density:** start sparse (one emote per beat) and raise it after the readability gate.
