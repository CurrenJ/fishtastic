# World fish exploration: future design outline

**Status:** Exploratory outline. This is not an implementation contract. Establish the
product goals and guardrails here before choosing a population algorithm or tuning values.
The existing ambient-fish and interactive-encounter contracts remain authoritative until
this direction is reviewed and accepted. Owner decisions E1–E19 (2026-10-30) settle the visit
model, sites, ambient identity and turnover, synchronization, observation and density target; see
[Owner decisions](#owner-decisions-2026-10-30).

## Purpose

Fishtastic already has two useful but separate world-fish systems:

- Ambient fish give nearby natural water visual life. Their populations are approximate,
  client-local and temporary; sightings do not currently record discovery.
- Interactive schools and visits are shared, finite fishing opportunities. Their fish can
  approach a bobber and be caught through the normal fishing loop.

The future direction explored here is to give aquatic places a recognizable identity, so
players can explore to discover fish, return to places they know, and encounter interactive
fish as part of the local ecology. It intentionally makes exploration a rewarding secondary
fishing strategy: a player who checks multiple waters should find more finite interactive
opportunities and greater ecological variety than a player who stays at one dock.

That benefit is deliberate. The protection for the existing game is not reward parity between
the two styles. It is that ordinary stationary fishing remains complete and retains its strengths
in control, casting uptime, deliberate targeting and mastery. Exploration must add reasons to
move through the world without making bait, conditions, tackle or focused fishing obsolete.

See [ambient fish](fish-in-world.md), [interactive fish schools](interactive-fish-schools-design-spec.md),
[in-world fish encounters](in-world-fish-encounters-design-spec.md), and
[Fishopedia](encyclopedia-redesign/README.md) for the current system contracts.

## Intended meta shift

The intended addition is a reason to **notice, investigate and revisit habitats**. A player
should be able to learn which fish are present in different waters and how encounters respond to
conditions, then decide whether to keep travelling, stop for an opportunity, or return later under
better conditions.

This creates two complementary fishing strategies:

| Stationary precision fishing | Exploratory opportunity fishing |
|---|---|
| High casting uptime | Trades casting time for travel and observation |
| Deliberate biome, condition, bait and tackle choices | Responds to the fish and visits the world presents |
| Best for pursuing a chosen species, condition, quest or record | Best for finding more interactive visits, variety and surprises |
| Predictable and repeatable | Place-based, finite and discovery-driven |
| Can access all fish and catch-earned progression | Offers additional opportunities, but no exclusive requirement |

Exploration is a reconnaissance and opportunity layer that enriches fishing, not an independent
non-fishing progression track. It should intentionally improve the rate at which a travelling
player finds interactive catches. That improvement comes from checking multiple independent
aquatic places, not from a hidden bonus to ordinary cast probabilities, travel distance, payouts
or quality.

Neither style is required for the other. A stationary player can complete the fishing game from
carefully chosen waters. An exploratory player accepts travel and search time in exchange for
more finite events, broader encounters and useful knowledge about places.

### Constructive meta contract

The feature is successful when it adds a new strategic strength without making one style best
at every objective:

- Exploration may be better for interactive visits per hour, variety, opportunistic discoveries
  and learning memorable fishing places.
- Stationary fishing should remain better for sustained casting, deliberately targeting one
  species, exploiting known conditions, completing exact catch requirements and pursuing mastery.
- Bait and tackle continue to control ordinary targets and relevant session effects. A visible
  encounter may provide one known species opportunity, but it does not replace the value of a
  deliberately configured fishing setup.
- Held equipment never changes which fish individuals or interactive visits exist. The world
  presents the opportunity; equipment affects fishing only after the player acts on it.
- Time, weather, moon and habitat affect ecological activity as well as ordinary fishing. The
  exploration layer should make those conditions more visible, not less important.
- No fish, catch reward, quest, mastery threshold or Fishopedia completion requires an ambient
  observation or interactive visit unless a future explicitly exploration-focused objective is
  separately reviewed.
- A valuable water location is allowed to become player knowledge, a destination or a shared
  coordinate. Finite stock, visit scheduling and specialization should prevent it from becoming
  an unlimited answer to every fishing goal.

## Goals

- Make returning to natural water reveal how fish presence and activity respond to habitat and
  conditions.
- Make discovering new habitats a meaningful way to find new fish and field-guide information.
- Reward checking multiple waters with more finite interactive opportunities than waiting at one
  location, while preserving stationary fishing's control and casting uptime.
- Make a passing inspection worthwhile during ordinary travel; exploration should not require a
  dedicated survey tool or prolonged waiting before a water can reveal an active opportunity.
- Connect ambient fish, field-guide discovery and interactive visits into one understandable
  ecology.
- Preserve time, moon, weather, biome and tackle as meaningful parts of fishing.
- Make interactive visits feel like events in a place's ecology, with shared identity and
  finite stock.
- Prefer rules derived from existing fish-profile data before adding more per-species fields.
- Keep world discovery bounded by loaded water and the existing performance budgets.

## Non-goals

- Replacing the catch pool, fishing odds, minigame, bait, tackle or catch-reward pipeline.
- Making every ambient fish catchable; ambient fish can exist as individuals without all becoming
  fishing targets.
- Making exploration a mandatory toll before a species can be caught through fishing.
- Granting fish, currency, items, catch counts or quest completion solely for seeing a fish.
- Making travel distance itself a hidden luck multiplier.
- Making exploration the strongest strategy for targeted catches, every quest, mastery and
  economic progress at once.
- Letting waiting at one location eventually reveal the wider habitat's entire possible roster.
- Requiring upkeep, feeding, population quotas, or a new resource-management loop.
- Revealing exact spawn coordinates or claiming that a sighting guarantees a catch.
- Using the observing player's held rod, bait, hook, charms or personal modifiers to create,
  reroll or reshape the local ecology.

## Principles and guardrails

### 1. Keep fishing's existing strengths intact

The current fishing resolver remains the authority for ordinary catch eligibility and catch
weights. The existing effects of habitat, time, weather, moon and tackle must remain legible and
useful. Exploration does not need equal rewards per hour, but its advantage should be specific:
it reveals more finite interactive opportunities and ecological variety. A carefully configured
cast should remain the stronger tool for pursuing a chosen result.

Sightings add a separate observation state to the guide. They must not silently count as catches,
satisfy catch quests, award fishing rewards or unlock catch-earned progression. Observation is a
durable knowledge reward; catching remains the source of catch records, detailed mastery and
catch-earned rewards. Catching a species also grants every general identity and guide-information
unlock that observation would have granted, so observation can reveal a species early but is never
a separate requirement for stationary completion.

### 2. Give individual fish continuity

Fish are individual records in Fishtastic's fish simulation, with a species identity, lifecycle
and position in valid water. They are not Minecraft `Entity` instances; rendering presents the
simulation fish visually. Normal movement may carry one simulated fish through nearby valid water.
Player movement, chunk loading and crossing arbitrary coordinates do not create a replacement fish
or reset that individual's state. There is no abstract neighboring-region membership for a fish to
transfer between.

This design does not require a fixed spatial grid, a saved roster assigned to each grid cell, or a
record for an entire connected lake or river. Habitat and environmental conditions can influence
which individuals appear, remain active or leave. If the scheduler needs spatial bookkeeping for
performance or encounter-rate control, that is an internal mechanism; it does not define fish
identity, movement or population boundaries.

### 3. Exploration means learning places, not farming movement

Novel discoveries should come from encountering fish in meaningfully different waters and
ecological contexts, not crossing arbitrary cell boundaries, teleporting, or walking back and
forth. Waiting at one spot should not be the optimal way to discover every species eligible in the
wider habitat. Fish remain individuals while they move naturally through nearby valid water.

Exploration should create visible information and finite opportunities, not an invisible bonus
to ordinary catch probability. Checking several distinct waters may reveal more active visits than
watching one water; repeatedly entering, leaving or reloading the same area must not create fresh
opportunities.

Visit cadence is shared, finite and independent of player arrival, chunk loading, login,
teleportation or client discovery requests. Visits follow deterministic windows at chunk-rotating
sites (E1, E2): arriving reveals a visit that was already scheduled and never creates one, so
crossing any boundary cannot create, reroll or speed up an opportunity. Depletion is persisted.
Unloaded fish do not need to be simulated continuously.

The scheduling key decides only where and when a visit begins. It does not define a fish's
territory or movement; visit fish move through validated connected water across any chunk line.

### 4. Separate presence, activity and catchability

The design should distinguish:

- **Presence:** which individual fish are in valid water and which species they represent.
- **Activity:** which present fish are visible or behaviorally active under current conditions.
- **Catchability:** what the fishing system permits and weights for a cast right now.

These may share inputs, but they are different facts. The field guide and presentation need a
consistent visual grammar:

- An ordinary ambient fish means that an individual of that species is present or passing through
  valid water; it does not mean the fish is catchable.
- Activity behavior means that a present fish is visible or active under the current conditions.
- The established interactive cue means that this particular authoritative fish can be caught.

The field guide and visual cues should never use **available** or equivalent language for a
decorative observation. Players must be able to learn this distinction through play rather than
having to read the design contract.

Ambient fish presence is not a replacement catch pool. A species may remain eligible through
ordinary fishing when no individual of that species is currently visible. Ambient absence never
makes an otherwise eligible ordinary catch impossible.

### 5. Separate ecology from player equipment

Fish presence, individual movement, ecological activity and interactive-visit formation are world
properties. They may use actual fish position, valid water, habitat, dimension, time, weather, moon
and other shared world conditions. They must not use the observing player's held rod, bait, hook,
charms or personal fishing modifiers.

In particular, equipment must not change or reroll:

- which fish individuals are present or active;
- whether a visit forms, its species, shape or member count;
- visit frequency, schedule, lifetime or depletion;
- school attraction or interest range; or
- the fish individuals present or active for other players.

Interactive members roll their species, quality and size when the visit forms. Those traits belong
to the world individual, drive its quality-tinted catchability trail and remain fixed through
approach, failed attempts and later catches. Player equipment cannot improve or reroll them.

Equipment begins affecting the fishing session when a player casts or binds an existing
interactive individual. Supported target-count, fight, reveal, consumption, wear, delivery and
quest effects may still apply, along with ordinary generation for every non-bound target. Quality
bias applies to ordinary targets but not to the already-formed interactive member. Equipment
swapping must not change any part of the member's reward identity.

This deliberately replaces the implemented ambient system's tackle-responsive population preview
for the future exploration model. The Fishopedia and other fishing-information surfaces explain
what a setup can catch; the water represents a shared ecology. A future lure that explicitly
summons or attracts a visit would require its own visible cost, tradeoff and design review rather
than inheriting this behavior implicitly from held equipment. Removing the preview is an owner
decision (E9); [ambient fish](fish-in-world.md) keeps describing the shipped behavior until this
design is implemented.

### 6. Reuse profile data carefully

The current profiles contain zones, base weights, and biome, time, weather and moon weights.
Zones provide a broad eligibility gate. The weight fields currently influence fishing outcomes;
they are not all hard presence or activity rules. In particular, a missing biome weight is not
an explicit statement that a fish is absent from that biome, and base catch weight is not
automatically a measure of ecological population.

Start with these fields as documented heuristics and calibrate them against authored species lore
and generated fish presence. Expose where a heuristic is weak; do not silently redefine catch
semantics. Add per-species ecology fields only where calibration shows existing profiles cannot
express the intended behavior.

### 7. Preserve the ambient / encounter distinction

Ambient fish and interactive visits remain distinct. Ambient fish are individuals in the fish
simulation with bounded local movement; seeing one does not make it catchable. Interactive visits
remain server-authoritative, shared and finite, and continue selecting species from the existing
natural fishing pool. Ambient fish presence does not replace or bias that pool. Each visit creates
its own shared fish identities and finite members through the existing catch pipeline.

The visit contract still needs a bounded, server-owned cadence; E1, E2 and E5 define it as
deterministic windows at chunk-rotating sites with deterministic neighbour suppression. Rarity
admission may reject a candidate without a fallback visit. A visit's authored shape and member
bounds determine finite yield; the number of ambient fish shown does not.

Ambient fish counts do not determine visit member count, replenish a visit or guarantee that a
visible fish will become interactive. A depleted visit stays depleted. A location can be valuable
and memorable without continuously paying out while occupied.

Ambient individuals are derived from place, world seed and time rather than stored (E6), so
every client and the server agree on which fish exist without saving or sending a roster.
Target behavior is for nearby players to see the same simulated fish identities, presence and
positions. Do not maintain that agreement with constant server-authoritative position packets.
Evaluate deterministic client-side simulation from shared setup/seeds and behavior inputs, with
sparse integrity checks or recovery, as used by the [existing school motion design](interactive-fish-schools-motion-plan.md).
Measure packet traffic, server and client cost, replay time, late-join behavior, cross-client drift
and animation smoothness at realistic ambient-fish counts. If no bounded approach preserves smooth
motion and resource budgets, require shared identity and presence only; allow decorative movement
and exact positions to differ between clients. Rendering settings must not change observation
eligibility or interactive-school opportunity.

### 8. Make discovery honest and understandable

The guide should distinguish **observed** from **caught** and must not present an observation as
a guaranteed fishing opportunity. A player may inspect a wild ambient or interactive fish through
a spyglass to reveal its identity and broad habitat before catching it. This is optional: catching
still grants the same general identity information. Merely loading, passing near or rendering a
fish does not record it. No additional survey item, scan minigame or prolonged hidden timer is
required.

The proposed information ladder is:

| State | Information depth |
|---|---|
| Unknown | Existing silhouette and broad discovery hints |
| Observed | Name, portrait, broad habitat and an observed mark |
| Caught | All observed information plus detailed catch conditions, catch records, lore and catch-earned reward progress |
| Mastered | Existing threshold rewards and record-driven accomplishments |

Observation may record broad place or habitat knowledge if the UI can present it without exposing
exact coordinates or becoming a map-management system. Seeing another player's catch does not
count as observing a wild fish. The client identifies the visible fish and sends its stable fish ID;
the server validates the observation and stores discovery for that player. Several players may
independently observe the same shared visit before one catches it. An ordinary catch grants the
general observed identity/information state even if the player never used a spyglass, but does not
invent a wild-sighting location record.

Interactive visits already communicate themselves through the fish model, synchronized leaps and
quality-tinted catchability sparkles. The design adds no persistent surface beacon, automatic alert
or passive observation credit. Noticing and investigating those authored cues is part of the
exploration gameplay.

Spyglass observation deliberately supersedes in-world encounter decision O5 and the Fishopedia
rule that every uncaught fish is a silhouette (E7): an observed species shows its name, portrait
and broad habitat. Plain look-at names stay out. The existing Fishopedia layout is unchanged (E19).
The invariant is that observation offers useful knowledge without substituting for catch
progression.

### 9. Keep the world and server bounded

Do not load chunks solely to populate or inspect fish. Individual discovery, persistence,
simulation, synchronization and rendering must have explicit caps. The model should preserve the
identity and lifecycle of fish that can be observed or caught without continuously simulating
unloaded water. Fish state belongs to the fish simulation's data and persistence; the design does
not introduce Minecraft `Entity` instances or a saved roster assigned to a spatial grid.

## Conceptual model to evaluate

This is a working model for discussion, not a selected implementation:

| Layer | Possible responsibility | Expected continuity |
|---|---|---|
| Simulation fish | Species, identity, lifecycle and position in valid water | Stable while present; may move through nearby valid water |
| Ecological activity | Visibility and behavior affected by time, weather or moon | Changes with conditions without replacing fish individuals |
| Interactive visit | A finite, shared catch opportunity selected from the existing natural fishing pool | Server-owned cadence plus persisted depletion; members remain distinct from decorative ambient fish |
| Field guide | Separate observation and catch records, with clear information depth | Persistent per-player knowledge |

The intended player loop is:

1. Travel for ordinary Minecraft activity or deliberate exploration.
2. Notice a body of water and recognize or record some of its ecology.
3. Decide whether to continue travelling, stop for a visible interactive opportunity, or return
   under a more promising condition.
4. Catching an interactive member uses the ordinary fishing and reward pipeline and depletes the
   finite visit.
5. Once the opportunity is exhausted, another visit depends on the shared bounded cadence and
   natural-pool selection; changing coordinates alone does not immediately replenish it.

Spyglass observation can record a species before a catch under the proposed information ladder;
catching grants the same general identity information without requiring observation. The fish
individual lifecycle, bounded cadence, rarity admission and recurrence values remain to be settled
before this working model becomes an implementation contract. Interactive visit species continue
to come from the existing natural fishing pool.

## Success criteria

Before implementation is accepted, playtests should show that:

- Fish individuals retain identity while present and move naturally through nearby valid water;
  returning to familiar waters can reveal familiar fish when they remain there.
- Exploring a different habitat can reveal new fish or useful guide information more reliably
  than waiting at one location.
- A player who checks multiple independent waters finds meaningfully more interactive visits than
  a player who watches one water for the same duration.
- A passing player can recognize that a water has an active opportunity without needing to stop
  and wait for a hidden survey timer.
- Entering, leaving, reloading or crossing arbitrary coordinates does not create fish or increase
  visit generation.
- Time and moon changes create meaningful activity differences without arbitrarily replacing
  individual fish.
- Equipping, swapping or removing rods, bait, hooks, charms or personal modifiers does not change
  which fish individuals exist or are active, visit cadence/composition, member quality/size or
  attraction behavior.
- Stationary fishing remains complete and is the stronger strategy for sustained casting and
  deliberately targeting a chosen species, condition, quest or record.
- Exploration is advantageous for visit discovery and variety without becoming the strongest
  strategy for every fishing objective.
- Observation alone does not masquerade as a catch or directly change ordinary cast odds and
  rewards; the deliberate benefit comes from finding additional finite visits.
- Interactive visits remain understandable, shared, finite and duplicate-safe.
- Changes in density or reduced-visual settings do not change discovery rules or encounter odds.
- Players who have not read the documentation can explain the difference between an ambient fish
  and a catchable interactive fish after normal exposure to the feature.
- Looking directly at a wild fish through a spyglass records observation once; passive proximity,
  chunk loading and seeing an inventory stack do not. Catching the species grants the same general
  identity information without requiring spyglass use.
- Bait use, condition planning and exact-catch quest strategies remain materially useful after
  players learn efficient exploration routes.
- Server cost, loaded-water reads, saved state and network traffic remain within explicit budgets.
- Shared fish positions use deterministic client simulation with sparse setup/input/checkpoint
  traffic; if that cannot preserve animation smoothness and stay within budgets, exact positions
  are not a requirement.

Balance evaluation must compare objectives rather than demand equal rewards. Record at minimum:

- interactive visits discovered per hour for stationary and travelling players;
- travel/search time versus casting time;
- time to catch one deliberately chosen species;
- ordinary and interactive fish caught per hour;
- rare-species and high-quality yield per hour;
- condition-, bait- and species-specific quest progress;
- new observations per distinct water, per habitat and per hour; and
- the likelihood that a player continues travelling after depleting a visit.

Numerical thresholds for these measures, rare sightings, visit frequency, lifetime and
catch-per-time are open. They should be set from representative stationary, incidental-travel and
deliberate-route playtests, not selected in this outline. A healthy result may give the explorer
several times more interactive visits while the stationary angler remains substantially better
at a deliberately chosen target.

## Owner decisions (2026-10-30)

- **E1. Visit model: deterministic windows.** Each site has visit windows over world time,
  `[t0 + kT, t0 + kT + L)`. A window is active when `hash(site, seed, k)` passes the visit chance
  and the window is neither suppressed (E5) nor depleted. Arriving reveals whatever window is
  active at that moment; it never creates, rerolls or speeds up one. Only depletion is saved as
  visit state. The accepted bounded exception is a recent per-dimension weather log, which saves
  world weather input so the visit uses its window-start weather. Visits exist only in loaded water.
- **E2. Sites: every water chunk, rotating start.** Every chunk containing qualifying water is a
  site. For each window, the hash also picks the water cell in that chunk where the visit begins,
  so over time every water in a chunk takes its turn. The chunk is an invisible scheduling key
  only: visit fish swim wherever connected water and their leash allow, across chunk lines.
  Flood-filled water bodies were rejected because they get split into arbitrary sub-areas whose
  hidden boundaries show up as odd behavior.
- **E3. Equal chance per water chunk.** A chunk holding a small pond has the same visit chance as
  a chunk of open ocean. Ponds are relatively generous; that is acceptable.
- **E4. Minimum water, no species-fit policy.** Water below a handful of connected cells (about 6)
  hosts no visits; ambient fish still swim there. No dedicated small-water species filtering: a
  good density calculation makes the mismatch rare, and the implementation may handle the residue.
- **E5. Deterministic spacing.** No visit-forming cap may depend on which chunks happen to be
  loaded. An active window is suppressed when a chunk within radius R holds a higher-ranked active
  window in the same period, so every client and the server reach the same answer. Performance
  caps may remain as protection but must not bind in normal play.
- **E6. Ambient fish are seeded, not stored.** Ambient individuals derive from place, world seed
  and time. They carry stable IDs every client and the server agree on, familiar fish return when
  conditions repeat, and saved data does not grow with explored water. Every water has ambient life.
- **E7. Spyglass observation supersedes encounter O5.** Observing a fish through a spyglass
  reveals its name, portrait and broad habitat; observed species no longer show as silhouettes.
  Plain look-at names remain excluded.
- **E8. Density target (revised 2026-10-30 with E15).** Originally a travelling-player rate. Visit
  length links the travelling and stationary rates, so the anchor is now the stationary rate plus
  a minimum fishable visit length (E15). The travelling rate that results is measured and checked
  against the success criteria.
- **E9. Tackle-responsive ambient preview removed.** Ambient fish show the shared ecology; the
  Fishopedia explains what a setup can catch.
- **E10. Spawn-time quality tint is intended.** Visible quality lets explorers choose high-quality
  visits; this is part of the exploration payoff. Track high-quality yield per hour in balance
  evaluation.
- **E11. Mid-window reveal: seeded mid-state.** A visit revealed partway through its window places
  its fish at seeded spots near the start cell, as if they had been there a while. Members whose
  seeded departure time has passed are gone. No unloaded time is replayed, and every client gets
  the same result.
- **E12. Sync: visits exact, ambient loose.** Interactive visit fish stay in deterministic
  lockstep because bites and bobbers depend on position. Ambient fish share identity and rough
  area only; their exact motion may differ between clients.
- **E13. Ambient turnover: staggered lifespans.** Each seeded ambient fish has its own seeded
  arrival and departure times in overlapping slots, so only a few come and go at once. Leavers swim
  out of view and arrivals swim in. Condition changes shift which slots are active gradually,
  never all at once.
- **E14. Changed inputs: re-derive, keep depletion.** After water edits, datapack or profile
  changes, or mod updates, ambient fish and future windows re-derive from the new inputs. Saved
  depletion stays keyed by site and window, so a depleted visit stays depleted. An active visit
  whose water becomes invalid departs normally.
- **E15. Starting density: tuned through playtests.** The initial 2026-10-30 baseline was about 4
  visits per hour stationary with 2-minute visits. The owner requested visits around 5 minutes and
  reported that duration feels good overall, then asked to increase rates. The rate-up candidate
  keeps `T=7200`, `L=6000`, and `R=2`, and raises chance from `0.016` to `0.024`; the estimated
  stationary rate rises from 1.9 to 2.7/hour, and travel passes rise from 21/40 to 29/56 per hour at
  2.5/5 blocks/s. These remain model estimates pending a rate-focused playtest; the density target
  is still open.
  See the implementation plan, P3 and Step 0 findings.
- **E16. Spyglass aim: hold-on assist.** A generous aim cone around the crosshair; keeping a fish
  in it for about 1 second records the observation and shows a small Fishopedia toast.
- **E17. Observed entry: name, portrait, broad habitat** and an observed mark. No coordinates or
  location log. Catch conditions, lore and records still wait for the catch.
- **E18. Presence calibration: report, then patch.** A headless report predicts the ambient roster
  per habitat from existing zones and weights. It is reviewed against species lore, and an ecology
  field is added only for species the report shows wrong.
- **E19. No Fishopedia layout changes.** Observation notifies the player once (the E16 toast) and
  unlocks the corresponding existing fields (name, portrait, broad habitat) on the current screen.
  No new layout, panel or observed-state view.

## Open decisions

1. **Visit rate tuning (owner playtest 2026-10-30).** The initial Step 0 baseline was `T=3600`,
   `L=2400`, chance `0.016`, `R=2`. The owner tested a five-minute duration (`T=7200`, `L=6000`) and
   says it looks good overall, then asked to increase visit rates. Current rate-up candidate: keep
   `T=7200`, `L=6000`, `R=2`, and raise chance to `0.024`. With a 48-block range and 50% water share,
   the model estimates 2.7 stationary starts/hour, travel passes of 29/hour at 2.5 blocks/s and
   56/hour at 5 blocks/s, and 20.1% availability at a random instant. These are estimates; playtest
   the increased rate before treating the density as settled.

## Next design gate

The intended constructive meta shift, the distinction between presence / activity /
catchability, the existing natural pool for interactive visits, the separation of shared ecology
from player equipment, and the models in E1–E19 are established by this outline. Implementation
follows the [implementation plan](world-fish-exploration-implementation-plan.md). Playtests then tune the values against the success criteria, including stationary
precision fishing, incidental exploration during ordinary travel and an optimized multi-water
route. Do not begin species-by-species balancing until the predicted rare-yield effects are
reviewed.
