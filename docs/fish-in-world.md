# Fish in World — feature specification draft

Status: feature design contract; phased implementation is tracked in the linked implementation plan.

Implementation outline: [fish-in-world-implementation-plan.md](fish-in-world-implementation-plan.md).

## Intent and player promise

Nearby water contains locally simulated fish drawn from Fishtastic's fishing species pool. The feature makes fishing locations feel alive and lets players learn which species they can catch through observation.

**Seeing a species means it is eligible to be caught in that water under the displayed fishing context. Its visible abundance is approximate.** While a rod is held, that context uses the rod's actual setup. Without a held rod, it uses standard worms as a visual baseline: a sighting indicates what could be caught there with standard worms, rather than predicting a bare-hook cast. Not seeing a species does not mean it is absent from the fishing pool. A cast still rolls its rewards through the existing fishing system; a visible individual is not a promised catch.

### Decisions confirmed by the designer

- Visible fish communicate catchable species, with approximate abundance.
- Apply the player's full current fishing context only while a rod is equipped.
- Without a held rod, default to the standard-worms species pool for visual variety, with local environmental conditions and neutral personal equipment modifiers.
- In-world fish should look nearly identical to fish in the tank simulation. Reuse the existing tank simulation and rendering as much as possible.
- Equipment transitions resolve the new context immediately, retain compatible individuals, retire excluded individuals through a short transition, and adjust weight-only abundance changes gradually. Brief switching/re-entry must not reset the population into fresh rare-fish rolls.

Everything below is a recommendation for review, including numerical tuning values.

## Critical gameplay review

The strongest part of this idea is turning environmental fishing rules into something players can observe. The main risk is teaching the wrong expectations: a conspicuous rare fish suggests either an easy catch or an individually targetable creature. Keep species eligibility truthful, preserve rarity differences, and avoid fishing animations that appear to hook a visible individual.

Personal equipment creates a second tension: the same water may look different to different players. Treat these fish as local visual representations of fishing opportunities. In multiplayer, species mixes and individual positions can differ between clients. This is acceptable for this scope; shared, targetable fish would require a different design.

Observation overlaps with the Angler's Almanac, which currently reveals the three highest-weighted species. Preserve its value as an immediate, explicit, ranked report of actual fishing weights. Ambient fish provide a partial, unlabeled sample over time and should never display a ranked probability readout or automatically unlock encyclopedia entries.

The pool represents game rules, not biological realism. If existing rules allow a species in an artificial pond, this feature should generally allow a sighting there too. Do not silently introduce a second habitat eligibility system to make the visuals more realistic.

## Fishing context

### Spatial source

Use nearby water locations as the fishing origin, rather than the player's standing position. Existing fishing resolves biome, elevation-based zones, weather, time, and moon conditions at the hook.

Maintain world-anchored local water patches. Split or separately sample patches where fishing context differs; a river and adjacent ocean must not inherit one player-wide pool. A patch's sampling position represents where a hook would rest, generally near the water surface. Fish swimming deeper must not accidentally change that surface fishing context into cave fishing.

A displayed fish must remain within water where its species is eligible under its patch's current context. Fish may cross a context boundary only if eligible on the other side. Limit scanning to loaded nearby terrain; never load chunks to populate fish.

### Equipment source

| State | Species context |
|---|---|
| No rod held | Local environmental context using the standard-worms bait definition (`fishtastic:worms`, currently `BaitEffect.WORMS`), with neutral personal equipment modifiers. This is the visual baseline, independent of owning worms. |
| Rod held, no active cast | Preview a cast using that rod, its installed bait/hook/charm, and applicable player modifiers. |
| Active cast | Use the casting rod and the cast's resolved context for the hook's patch. Keep the context snapshot that produced the session's rewards while that session is active. |

Proposed hand priority: active casting rod, otherwise main-hand rod, otherwise offhand rod. A rod merely carried in inventory does not activate the preview. Use the same equipment resolution as real casting; if current casting behavior differs, resolve that discrepancy before implementing the feature. Unsupported rods use the standard-worms visual baseline until their fishing behavior has a supported resolver.

Resolve the default through the normal standard-worms candidate filters and species weights, including local environment modifiers; do not maintain a separate list of decorative species. The existing bait filter excludes vanilla fish for standard worms. The visual baseline does not equip, grant, consume, or require worms, and does not change subsequent fishing rewards. A supported held rod with no bait uses its actual no-bait pool; the standard-worms substitution applies only to the default visual baseline.

"Full context" means all inputs that affect species eligibility or species weights. Bonuses that affect quality, treasure, trash, target count, or difficulty alone do not change the visible species mix or density.

Previewing does not consume bait, damage upgrades, award progression, roll rewards, or trigger world-changing effects. A Storm Charm changes the population after an actual cast changes the weather; merely holding it must not summon a storm or claim the storm is already active. An active session's hook patch may briefly differ from neighboring patches if the session uses an earlier context snapshot.

## Species selection and visual weighting

Resolve the same candidate list and effective weights as fishing: fish tags, bait-exclusive pools, the bait rule excluding vanilla fish, zone eligibility, environment multipliers, bait modifiers, charm modifiers, and applicable custom item weight behavior. Share a side-effect-free resolver between fishing, the Almanac, and ambient fish so those paths cannot drift apart.

Only eligible species with positive effective weights can be selected. Hard eligibility gates remain hard gates. Do not infer eligibility from an environment multiplier alone: current Fishtastic fish weights are clamped to at least one after zone filtering.

Let `w_i` be each species' final effective fishing weight and `p_i = w_i / sum(w)` its probability conditional on a fish reward. Suggested visual selection:

```
q_i = sqrt(w_i) / sum(sqrt(w))
p_visual_i = 0.75 * p_i + 0.25 * q_i
```

This puts most of the distribution on actual fishing odds and a smaller part on a more varied distribution. It preserves ordering and never adds excluded species. Apply it after existing bait flattening, without changing actual catch weights. Keep the blend tunable; start here and compare against unmodified weights before deciding whether more flattening is needed.

Illustrative three-species pool, not measured game balance:

| Effective weight | Actual fish share | Suggested visual share |
|---:|---:|---:|
| 100 | 90.1% | 85.2% |
| 10 | 9.0% | 12.3% |
| 1 | 0.9% | 2.4% |

Do not guarantee every eligible species appears, force a rare fish into each patch, or sample species uniformly. Variation should emerge over time. Initial spawning and replacement use the same weighted policy, with comparable lifetimes across species; species-specific longevity would otherwise distort occupancy.

For the first version, sample individual fish slots rather than whole schools. Later school spawning must account for school size in the weighting: choosing a species once and then adding ten individuals multiplies its visible abundance. Motion may still form loose same-species groups among existing individuals.

## Density, placement, and movement

Density depends on usable local water volume and visual readability, not species weight totals or fishing success rates. Tiny pools contain fewer fish; open water can contain more. No valid water or no eligible representable species means no ambient fish, with no unrelated fallback species.

Suggested prototype limits: a 24-block horizontal radius, at most 32 simulated individuals, and gradual spawning/replacement. These are tuning hypotheses, not measured performance claims. Add an explicit vertical bound and per-tick terrain-query budget during implementation.

- Spawn only where a fish's full rendered body fits inside water and clear space, including clearance from the surface and floor. A waterlogged cell alone is insufficient.
- Respect terrain, fluid surfaces, plants/collision shapes, and disconnected pools. No travel through dry land or relocation between pools.
- Keep movement anchored to world positions as the player walks. The local simulation boundary should not look like an invisible aquarium wall.
- Match the tank simulation's species appearance and motion: the same models/textures, calibrated body scale, animation profiles, tail beats, turns, banking, bobbing, drift, and bottom-associated poses. Use plausible species sizes without presenting quality tiers or record-size rewards.
- Support swimmers, drifting species, and bottom-associated species through appropriate movement. Do not turn every catchable species into a midwater swimmer. An unsupported species may be omitted from the visual sample; that omission must not affect fishing eligibility.
- If a species cannot fit in a patch, omit its visual instance rather than shrink it implausibly. Sightings therefore sample the locally representable subset of the catch pool.
- Use ordinary world lighting, depth, fog, and occlusion. A rare fish should not become a glowing marker visible through terrain or water at unlimited depth.

The tank simulation is the visual reference, not merely an asset source. For the same species, body size, and comparable motion, the in-world fish should look nearly identical to its tank counterpart. Differences should come from the surrounding world, lighting, fog, and terrain/domain constraints. Assess the first visual prototype both from shore and underwater, including side-by-side comparisons with tank fish; free camera angles and world-water rendering need their own acceptance check.

## Population stability and updates

Preserve existing fish positions, headings, animation phases, and species while their patch and eligibility remain valid. Do not resample the population every frame, on camera rotation, or whenever the player moves one block.

Weight-only changes affect future spawning and gradual replacement. Hard eligibility changes retire invalid individuals promptly through a short fade or departure entirely within valid space. Never morph a fish into another species. Bound this transition so a newly excluded species does not remain a misleading long-lived sighting.

Reflect equipment or environment eligibility changes within two seconds after accepting the
updated context. Playtesting on 2026-10-13 requested more continuity: new fish now receive
90–150-second lifetimes (two minutes on average), and weight-only composition changes follow
that slower turnover. Invalid water or excluded species can still retire sooner.

Cache populations and random state by world patch and equipment context for a bounded period. Equipping, unequipping, turning away, crossing the radius boundary, or briefly walking away must not give an immediate fresh rare-fish roll. Natural replacement continues over time; this is visual stability, not a gameplay cooldown. Teleports, dimension changes, disconnects, and resource/datapack changes invalidate the appropriate state.

## Interaction and progression

Ambient individuals have no hitboxes, drops, health, bucket capture, targeting, persistence in world saves, or effect on catch rolls. Catching does not remove a visible individual or deplete a location. No sighting grants encyclopedia completion, quests, XP, companion unlocks, or item quality information.

Swimming near fish may eventually cause a small local avoidance response. That response must not imply a fishing penalty. Bobber attraction, bite animations on particular individuals, and targeted catches are deferred because they create expectations beyond the agreed species-level promise.

Offer a separate ambient-fish toggle and density setting. Turning the feature off changes no fishing outcomes. Vanilla fish entities continue to behave normally; test crowding where they coexist, and reduce decorative density if necessary without altering the actual pool.

## Technical direction

The server resolves species/context snapshots where necessary to match authoritative datapacks and player state. The client owns ambient individuals, movement, interpolation, lifetime, and rendering. Send pool/context changes, not individual transforms every tick. Validate and bound any client-originated patch requests and avoid server-side population simulation.

Reuse the tank simulation's `fishsim` engine and locomotion behavior as the default implementation. Reuse the tank's species-to-simulation descriptors, size calibration, animation transforms, and per-fish render-state preparation/submission wherever practical. Extract shared helpers from tank-specific classes when necessary so both contexts use the same behavior; do not copy these systems into an independently tuned ambient simulation.

Add a thin ambient adapter for population lifecycle, world coordinates, natural-water domains, terrain invalidation, and local light sampling. Terrain/domain queries and boundary management may need extensions, but those should preserve the established tank motion and appearance. Companion voxel queries can supply reusable world-query infrastructure without introducing companion owner-following behavior. Keep simulation ticking separate from render extraction. Client density settings must bound simulation work, terrain scanning, and draw work together. Shared refactoring must preserve existing tank behavior.

Use bounded caches, terrain invalidation, context versioning, and profiling counters for fish count, sampled cells, pool resolves, packet volume, simulation time, and rendering time. Confirm dedicated-server safety and both loader paths.

## Acceptance criteria

1. For every displayed species, the shared fishing resolver allows that species at the represented water origin under the same context. Verify river/ocean boundaries, elevation zones, weather, time, moon, bait-exclusive pools, and charm overrides.
2. Without a held rod, the resolved candidate list and pre-visual-flattening weights match standard-worms fishing in the same environment, regardless of whether the player owns worms. A supported held rod uses its actual setup, including its no-bait pool when applicable; carrying it does not activate that setup. Cast-triggered effects occur only on real casts. Previewing never mutates fishing resources or advances its random reward sequence.
3. Statistical sampling matches the configured visual distribution over many independent draws. Longer runs verify that lifetimes and replacement do not introduce a second abundance bias.
4. Walking, hand switching, recentering, and repeated entry do not cause whole-population resets or immediate repeated rare-species rolls.
5. Full bodies remain in water and clear of collision geometry, including shallow pools, waterfalls, flowing water, waterlogged obstacles, caves, and disconnected pools. Block changes and chunk unloading cannot leave fish swimming in air.
6. Shore and underwater views remain readable without sprites visibly clipping the surface, flickering, ignoring fog, or exposing species through terrain. Test vanilla entities and companion/tank visuals alongside the feature.
7. Disabling ambient fish leaves fishing results and progression unchanged. Multiplayer accepts local visual differences, while server/client context agreement is verified.
8. Terrain queries, context requests, caches, and individuals stay within explicit caps. Profile combined tank/companion/ambient scenes before setting release defaults; agree a reference-machine performance budget before sign-off.
9. Side-by-side tank/world comparisons for the same species and size show nearly identical body scale, animation, poses, and motion character under comparable conditions. Cover swimming, gliding, drifting, and bottom-associated modes. In a matching test domain with the same descriptors, seed, and parameters, shared simulation behavior agrees; any shared refactoring preserves existing tank behavior.

## Delivery scope

First, build a visual spike in river, ocean, and shallow artificial water using the existing tank engine/rendering and a fixed, known pool. Establish visual parity with tank fish, body containment, sprite readability, stable movement, and acceptable cost. Then connect the shared fishing resolver, the standard-worms default, and actual held-rod contexts. Finally tune visual flattening and population turnover through observed play, including whether rare sightings feel exciting and whether players incorrectly expect to catch the fish they can see.

Version one covers local water, contextual species sampling, gradual lifecycle, appropriate supported locomotion, and settings. Defer lava populations, targeting, shared persistent populations, breeding, food chains, depletion, explicit fish spotting rewards, and new progression systems.

## Verified repository anchors

- `common/src/main/java/grill24/fishtastic/server/FishingMinigameManager.java`: `generateTargetsForOrigin`, `resolveEnvironment`, `getFishPool`, `computeTopWeightedFish`, and cast-triggered Storm Charm handling.
- `common/src/main/java/grill24/fishtastic/item/FishtasticFishItem.java`: `getFishingLootWeight`, existing bait rarity flattening, `isZoneEligible`, and species/quality sampling.
- `common/src/main/java/grill24/fishtastic/data/FishProfile.java`: environment multipliers and elevation/biome zone resolution.
- `fishsim/src/main/java/grill24/fishsim/core/FlockEngine.java`: existing tank simulation and locomotion engine to reuse; natural-water domain integration still needs verification.
- `common/src/main/java/grill24/fishtastic/client/renderer/TankFlockAdapter.java`, `FishTankBlockEntityRenderer.java`, and `FishAnimator.java`: tank descriptor mapping, size calibration, per-fish render states, and animation/render transforms to share.
- `common/src/main/java/grill24/fishtastic/FishtasticItems.java` and `common/src/main/java/grill24/fishtastic/component/BaitEffect.java`: standard `fishtastic:worms` registration and bait definition for the default visual context.
- `fishsim/src/main/java/grill24/fishsim/companion/LocalVoxels.java` and `common/src/main/java/grill24/fishtastic/client/companion/CompanionWorldAdapter.java`: cached nearby block/water queries and Minecraft adapter boundary.
