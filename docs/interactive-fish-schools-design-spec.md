# Interactive fish schools

**Status:** Phase A implemented; initial tuning and content defaults are recorded below.

**Date:** 2026-10-24

**Purpose:** Record the player experience, shipped Phase A contract, implementation boundaries, and tuning decisions.

This feature introduces occasional shared schools of individually catchable fish alongside the existing decorative ambient population. A player spots the subtle sparkle on a catchable fish, casts nearby, watches it approach the bobber, and plays the normal fishing minigame. That individual occupies one of the minigame's existing target slots. Catching that target removes that individual from the school.

## 1. Decision status

The following decisions were established in the design conversation:

- Most visible ambient fish remain decorative during the initial rollout.
- Occasional schools contain actual catchable individuals, distinguished by a subtle sparkle or equivalent particle cue.
- A specific world fish can approach the bobber and become one target in the regular minigame.
- It replaces an existing target rather than adding a bonus target.
- The minigame does not specially identify which target came from the school.
- Catching that target removes the corresponding individual from the shared school.
- Any supported Fishtastic rod and bait combination can catch the marked fish, including a setup with no bait.
- The bound school target bypasses bait's species-pool restriction. Other targets follow ordinary fishing rules.
- Relevant bait and equipment effects still apply to the session and catch.
- School appearance is governed by the fish's existing environmental profile: habitat, biome, time, weather, moon, and other applicable natural conditions.
- The first release extends the existing ambient system. Replacing all decorative fish with authoritative populations is a later decision.

The rules below define the shipped Phase A behavior. The choices in section 18 resolve the initial open questions; values remain configurable where noted and can be retuned after playtesting.

## 2. Goals and scope

### 2.1 Goals

1. Give the player a visible reason to choose where to cast.
2. Connect a particular fish seen in the water to a particular possible reward.
3. Make catching fish visibly change a shared school.
4. Make school sightings express existing fish ecology and rarity.
5. Preserve the regular minigame, tackle choices, reward systems, and progression.
6. Support multiplayer competition for a finite opportunity without duplicate catches.
7. Keep world simulation, discovery, rendering, networking, and saved state bounded.

### 2.2 Initial release scope

The initial release contains uncommon, bounded school visits in valid loaded water. Each visit has a species, a finite set of members, a lifetime, and local movement. Members swim together, can approach nearby bobbers, and leave when the visit ends.

"Migratory" describes a school arriving, moving locally, and departing. Continental routes, simulation through unloaded terrain, breeding, food webs, permanent depletion, and a global population economy are future possibilities. They are not prerequisites for this release.

Schools use a curated set of existing species. Existing fish remain obtainable through their normal fishing paths; school sightings are not required for collection completion. A later feature may deliberately introduce school-exclusive content, but that would need a separate balance decision.

## 3. Existing systems and compatibility

The current ambient contract is documented in [Ambient fish](fish-in-world.md). Decorative fish are client simulations whose composition can reflect the player's held rod or active cast. Different players may see different decorative populations. Those visuals do not affect actual catches.

The new authoritative schools coexist with that system. Decorative fish keep their current behavior and receive no interactive sparkle. Authoritative school members have shared identities and availability, even when nearby clients have different decorative populations.

Relevant current code:

| System | Current responsibility | Design implication |
|---|---|---|
| [`FishProfile`](../common/src/main/java/grill24/fishtastic/data/FishProfile.java) | Habitat zones, environmental weights, size, temperament references, swarm and render data | Reuse its ecological and presentation definitions. |
| [`FishingPoolResolver`](../common/src/main/java/grill24/fishtastic/server/FishingPoolResolver.java) | Shared environment, candidate filtering, species weights, and fishing context | Reuse shared natural rules while excluding personal tackle from school generation. |
| [`FishtasticFishItem`](../common/src/main/java/grill24/fishtastic/item/FishtasticFishItem.java) | Species weights and coupled quality/size sampling | Preserve normal catch distributions for the forced species. |
| [`FishingMinigameManager`](../common/src/main/java/grill24/fishtastic/server/FishingMinigameManager.java) | Target generation, session ownership, rewards, progression, and equipment costs | Integrate the school member into one target and use the ordinary completion path. |
| [`FishingHookMixin`](../common/src/main/java/grill24/fishtastic/mixin/FishingHookMixin.java) | Supported rod retrieval and minigame transition | Connect the visible approach to a real bite and the existing session transition. |
| [`BaitEffect`](../common/src/main/java/grill24/fishtastic/component/BaitEffect.java), [`FishtasticItems`](../common/src/main/java/grill24/fishtastic/FishtasticItems.java) | Pool filters, affinities, quality, target counts, and minigame tradeoffs | Bypass species selection only for the school slot; retain applicable effects. |

These are integration references, not a prescribed class layout. The implementation plan should resolve exact extension points against the checkout being implemented.

## 4. Player experience

### 4.1 Discovery

The player encounters a small school moving through water. Its catchable members produce a restrained but readable sparkle. The effect means **this individual can be approached with a supported fishing setup**. It does not promise a successful catch, a quality tier, or bonus loot.

Every member of an interactive school is catchable while available. Most fish in the broader ambient world remain decorative. Mixing indistinguishable decorative members into an interactive school is excluded from the first release because it would make the cue harder to interpret.

The cue follows the fish. A supplementary surface glint may help discovery when the member is submerged, provided it stays spatially connected to the actual opportunity. Its design must remain legible against water effects, daylight, darkness, and common particle settings.

### 4.2 Casting and approach

The player casts near an available member. The bobber must be in valid, reachable water. A nearby fish can become interested and swim toward it. The server reserves that member to that hook during the approach.

The first release uses proximity and water reachability to select a candidate, with a stable tie break. It does not require aiming at a fish's hitbox or equipping a matching bait. One hook can attract at most one school member at a time.

Reaching the bobber produces the normal bite signal and opportunity to reel in. The approach is a visible part of fishing; it does not award the fish or open the minigame automatically. A bounded interest/approach delay prevents casting directly on a member from becoming an instant-catch loop.

### 4.3 Minigame

Reeling in the school bite starts the normal minigame. The fish becomes one ordinary fish target among the session's targets. Its position in the target list is randomized, and it uses its normal species temperament and quality-related difficulty.

No special border, target position, label, or icon identifies the school binding. Existing equipment that reveals information about normal targets continues to function. The feature must not suppress those effects merely to preserve uncertainty.

The player may catch the school target, other targets, both, or neither. Catching another fish of the same species does not count as catching the bound individual.

### 4.4 Resolution

If the player catches the bound target, its reward goes through the usual fishing reward path, and that member is permanently removed from the visit. Nearby players see one fewer fish.

If the bound target escapes or the session is abandoned, it yields no school reward. The member can return to its school after a short recovery period if the visit is still active and the water remains valid. Otherwise it departs without being awarded to anyone.

## 5. Ecology and school generation

### 5.1 Preserve the meaning of current conditions

Current profiles distinguish **habitat eligibility** from **environmental weighting**. Habitat zones are hard restrictions. Biome, time, weather, and moon entries generally modify weight; they are not automatically exclusive spawning requirements. The current ordinary species-weight function also floors weights to at least one.

The school system must preserve that distinction. A fish with a full-moon preference should be more likely to form a school under a full moon. That preference alone must not silently become a full-moon-only gate. Likewise, cold-biome affinity must influence appearance using the profile's existing meaning rather than being reinterpreted as a temperature restriction.

If designers want a species to be strictly day-only or moon-exclusive for schools, that requires an explicit, documented eligibility rule. Adding such rules is a content/schema decision, not something inferred from a multiplier. The initial design does not redefine normal fishing eligibility.

### 5.2 Shared natural context

School generation samples the real server environment at validated water, including dimension, biome, habitat zones, world time, local precipitation/weather, and moon phase. It uses the same underlying profile definitions and environmental calculations as fishing.

No held rod, bait, charm, personal inventory, player luck, or active cast is part of the school's natural species-selection context. In particular:

- A personal effect that treats a cast as night does not make night-favored schools appear for that player.
- A personal species-weight bonus does not alter the shared school.
- An effect that actually changes world weather or time can influence future school formation because the world itself changed.
- The existing ambient standard-worms preview baseline is not a reason to require or consume worms for schools.

The implementation must explicitly separate natural environmental inputs from tackle inputs. Passing a player's current fishing context wholesale into school generation would violate this contract.

### 5.3 Generation sequence

1. A server schedule considers a loaded, validated water region that has an interested nearby player.
2. Region cooldowns, active-school limits, and global budgets determine whether an encounter attempt is allowed.
3. A separate encounter-frequency roll determines whether a visit occurs. A favorable species pool does not guarantee a school.
4. Candidate species are restricted to the curated school-enabled set and valid habitat.
5. Candidates use their base/profile rarity and natural environmental weights, without tackle bonuses or rarity flattening.
6. One species is selected for the visit. School size is separately bounded and tuned; selecting a rare species does not automatically justify a large school of it.
7. The server creates stable school/member identities, local movement state, a visit deadline, and the environmental snapshot that explained formation.

No eligible species, invalid geometry, missing required data, or exhausted budgets results in no visit. Discovery must not invent a fallback species merely to populate an area.

### 5.4 Spawn location and movement

The server validates connected water volume, collision clearance, usable depth, and space for the selected species' presentation. Existing natural-water validation should be reused where appropriate. Whether a player built or enclosed a qualifying pool must not be guessed from block placement history; generation rules must use an explicit water-validity policy.

First-release schools remain within a bounded connected water region and compatible habitat. Movement is the shared fish sim's schooling (the engine ambient fish and tanks use), run on the server with client copies in lockstep; see [the motion plan](interactive-fish-schools-motion-plan.md). It must not cross dry blocks, travel through walls, transfer between disconnected or vertically stacked pools, or follow a bobber into invalid habitat.

A school never loads chunks to continue its route. If a path becomes unusable, it can select another valid local route or retire gracefully.

### 5.5 Changing conditions

The formation snapshot explains why the school appeared. A change in a positive environmental weight does not reroll existing members, switch their species, or cancel a catch. A full-moon-favored school can finish its bounded visit after its favorable weighting changes.

Hard habitat validity and any future explicit eligibility windows are different: a school begins departure when those rules cease to permit it. New approaches stop; already committed minigames can finish. If strict time windows are added, visit deadlines should normally fit within them.

This refines the earlier discussion of ending visits at condition boundaries: only actual restrictions create a required boundary. A soft preference does not.

## 6. School population and visit lifecycle

### 6.1 Finite opportunity

Each visit creates a finite member list. Caught members do not respawn within that visit, and no new members are added to compensate for catches. An empty school ends early.

The visit remains independent of how many players see it. Additional viewers do not create another copy, increase its member count, or refresh its lifetime.

### 6.2 Visit states

| State | Behavior |
|---|---|
| Active | Members move locally; available members can approach hooks. |
| Departing | No new reservations; free members leave or fade along a valid departure path. |
| Expired | Free members are gone; existing in-flight bindings are retained until resolution. |
| Closed | No members remain active or bound; retain only bounded scheduling/depletion metadata. |

The default clock is server world game time. Unloaded visits retain their original deadline; they do not gain a fresh lifetime when observed again. Server shutdown does not simulate offline ecology. Reloading an area must not replay missed encounter attempts in a burst.

## 7. Member, hook, and session lifecycle

### 7.1 Member states

| State | Meaning and allowed transition |
|---|---|
| Available | Swimming with its school and eligible for a hook reservation. |
| Approaching | Reserved to one valid hook and moving toward it. Can become bite-ready or recover. |
| Bite-ready | At the hook with an ordinary bite opportunity. Reeling in can create a bound session. |
| In-session | Reserved to one session/target index; no free world copy can be hooked. |
| Recovering | Failed or interrupted attempt; temporarily unavailable before returning. |
| Caught | Successful target resolution; removed permanently from the visit. |
| Departed | Removed without a reward because the visit or habitat ended. |

Reservation ownership and state changes are server decisions. Member identity survives approach, minigame, and recovery. A recovered fish is not a newly generated reward opportunity.

### 7.2 Bite ownership

While a school approach owns a hook, it controls that hook's bite transition. The ordinary bite path must not independently create another bite, reward, or session for the same hook. If the school approach fails, ordinary fishing resumes through a defined recovery path.

Approach has a maximum duration and bounded distance. Leaving range, losing connected water, moving the hook into an invalid state, or losing the owning player aborts the approach. A brief delay before retry prevents rapid ownership churn.

If a normal bite has already committed, the hook cannot also reserve a school member. Reeling in before a school bite is ready follows the existing unsuccessful retrieval behavior and releases its reservation.

### 7.3 Transition to the minigame

At valid retrieval, the server atomically transfers reservation ownership from the live hook to the session and records the exact target index. Hook removal after retrieval is expected and must not release the session's fish.

During the fight, the member is absent from the freely swimming population. It remains recorded as in-session rather than caught. On failure, its return is visible where practical; on success, there is no returning world member.

### 7.4 Cancellation and expiry

Disconnect, death, dimension change, session replacement, invalid rod/session state, or timeout releases an unresolved binding without awarding its fish. The fish recovers if its visit remains valid; otherwise it departs.

Visit expiry never revokes a valid in-flight catch. The member can finish its bound minigame after the visit's free population departs. A failed fish cannot resurrect an expired visit.

Each session has a bounded resolution lifetime so abandoned clients cannot hold fish indefinitely. The timeout must exceed the supported minigame duration plus reasonable network tolerance; its numeric value belongs in implementation tuning.

## 8. Target generation and reward identity

### 8.1 One replacement

The normal tackle-dependent target-count calculation runs, including minimums, maximums, and bonuses or penalties. Exactly one existing slot is selected uniformly for the school target. The total target count does not increase.

The selected slot becomes one fish reward for the reserved species. Its discarded normal outcome produces no reward, quest progress, or side effect. It can replace a fish, trash, or treasure result. The remaining slots follow normal category/species rules; no special luck or rarity adjustment is applied to them.

If a session cannot be created with at least one valid slot, no school catch is committed and the reservation is released. Debug/bulk fishing simulation must not consume real school members unless an explicit administrative scenario asks it to.

### 8.2 Hidden binding

The session binds `{school identity, member identity, session identity, target index}`. This association is authoritative even if multiple targets happen to have the same species, size, or quality.

There is no additional school-target marker in the minigame. Normal target information and existing reveal equipment remain available. A client must not select a reward species or substitute a different member by reporting an identity.

### 8.3 Quality and size

The agreed identity guarantee is that the same world individual and species become the bound reward. The proposed initial policy rolls quality and size together at session creation using the normal species distributions and applicable tackle quality bias. The school sparkle grants no quality boost.

Before binding, world rendering uses species-appropriate presentation and approximate scale. It must not advertise an exact measured size or quality that the eventual stack can contradict. Once bound, the session's reward is fixed and any relevant presentation can use its resolved attributes.

To prevent repeated failures from rerolling one member until it becomes a jackpot, each member receives stable reward randomness. Retrying with equivalent reward-affecting tackle inputs produces the same quality and size outcome. Different tackle may legitimately improve that distribution; it must not generate a fresh independent reward seed on every attempt. Species, member identity, and resolved session rewards cannot be rerolled by equipment swapping after session creation.

This is a proposed compromise that preserves existing bait value. Guaranteeing an exact visible centimeter size and quality at initial school spawn would require a different reward model and is deferred for explicit review.

### 8.4 Awarding and progression

The bound fish uses the ordinary server reward pipeline for item delivery, overflow, fish piles, catch records, encyclopedia discovery, quests, companion progression, and XP where those systems already apply.

The school feature adds no discovery reward just for seeing, approaching, reserving, or failing to catch the fish. There is no parallel school loot roll or additional item drop.

Catch conditions for quests use the existing session snapshot and actual equipped bait identity. A fish caught using a day-to-night personal effect follows the existing quest/session semantics; its original school formation snapshot does not rewrite the catch's context. A bait-use objective may count the actual equipped bait under existing rules even though that bait did not select the school species.

## 9. Tackle contract

"Any tackle" means any rod setup already supported by Fishtastic's normal minigame path. It includes no bait and a bait whose normal pool excludes the school species. Vanilla rods or third-party rods outside that path are not implicitly granted new integration by this design.

| Effect | School target | Other targets / session |
|---|---|---|
| Bait's exclusive species pool | Does not veto the bound fish. | Applies normally. |
| Bait/charm/personal species weights and rarity flattening | Do not select or reroll the bound species. | Apply normally. |
| Bait target-count bonus/penalty | No extra school slot is added. | Normal total count is calculated first. |
| Treasure and trash odds | One selected slot becomes the school fish regardless of its ordinary category. | Remaining slots retain normal odds. |
| Quality bias from bait/hook | Applies through normal coupled quality/size sampling. | Applies normally. |
| Matching group target-speed or catch-progress modifiers | Apply to the fish according to its actual tags. | Apply normally to relevant targets. |
| Session-wide changes, such as a smaller bobber | Apply to the whole encounter. | Apply normally. |
| Bait saving, consumption, and equipment wear | Follow normal session completion rules once. | No additional school fee or duplicate consumption. |
| Reveal, delivery, pile, or other downstream effects | Apply where normally supported. | Apply normally. |

Equipping bait does not increase school spawn rate, school size, or member attraction radius in the first release. It remains valuable for normal pool control, other target slots, quality, and session effects.

Examples:

- A bare supported rod can catch a sparkling mod fish even though ordinary bare-hook fishing has a different pool and category balance.
- Blazed Grub can hook a non-exotic school member in the replaced slot. Other normal fish slots remain exotic-only, and its ordinary treasure/quality behavior still applies where relevant.
- Gummy Worms' quality bias can help the bound fish; its species rarity flattening has no work to do for an already identified member.
- Frenzy Bait's matching fish movement penalty still applies when the bound member belongs to that group.

## 10. Balance contract

### 10.1 The encounter's benefit

The reward is an opportunity to target a known visible species, access it with any supported setup, and replace one uncertain target with a fish. Approaching the school can also provide a more deliberate bite opportunity. None of these guarantees the player will catch that target.

Replacing a slot rather than adding one limits output, but it is not balance-neutral. A normal slot might have been trash, treasure, or another fish. A school visit also packages several opportunities of one species into one location. Frequency, member count, duration, and approach timing must account for that concentration.

For a rough category check, if an ordinary slot has fish probability `p`, replacement increases expected fish targets in that cast by `1 - p` and removes the selected slot's expected trash/treasure outcome. Actual economic value also depends on species, quality, catch success, and any change to casts per minute.

### 10.2 Guardrails

- Visits are uncommon and finite, with caps shared by all players in a region.
- At most one school target is bound per cast, even when multiple schools overlap.
- No school-specific quality, XP, sale-value, quest, or loot multiplier is added.
- A rare school species uses its ordinary natural rarity unless an explicitly reviewed content rule says otherwise.
- Rare species may need smaller school sizes or lower encounter admission rates; the species draw alone does not bound total rare-fish yield.
- Baitless school fishing is an intentional exception. Its total yield must remain constrained by encounter availability and the unchanged remaining target slots.
- No free replacement members appear after a successful catch.
- Failed catches preserve member identity and reward randomness.
- Entering a new chunk, reconnecting, changing tackle, or adding another viewer does not reset scheduling or depletion.

### 10.3 Scheduling and farming resistance

Encounter attempts belong to stable server regions, not player login sessions or client discovery requests. Each eligible scheduling opportunity advances a persisted next-attempt deadline, including attempts that produce no school. A visit's cooldown/deadline is established when scheduled, not only after players finish catching it.

Region keys must prevent oscillating across a boundary from creating alternating schools in the same water. Nearby regions also need an overlap/exclusion policy and dimension/global caps. Exploring genuinely new water can reveal new opportunities; repeatedly reloading the same water cannot manufacture them.

Exact scheduler geometry and cooldown values belong in the implementation plan. The observable invariant is stable opportunity per place and time, independent of viewer churn.

## 11. Multiplayer, authority, and completion

### 11.1 Shared ownership

Nearby players observe the same school species, member identities, availability, and depletion. Rendered poses can interpolate differently, but a fish cannot be available to one client after the server has reserved or caught it for another.

The first valid server reservation wins simultaneous interest. Later hooks may attract another available member or continue ordinary fishing. One player can reserve only the member attached to their current valid hook/session; reservations cannot be accumulated across rods.

### 11.2 Completion integrity

All validation, reservation changes, and reward commitment occur through one authoritative server execution path. Completion requires the correct player, active session, bound member, and target index. Duplicate indices, stale sessions, duplicate packets, and replayed completion cannot award another copy.

The member's successful resolution and eligibility for reward delivery must be committed together in the session flow. If the binding is invalid, the school target must not pay out; independently valid ordinary targets can still resolve under existing rules.

Current minigame results are client-reported target indices. This feature must preserve existing session validation and add binding integrity; it does not claim to independently verify every client input or prove that the full minigame was played honestly. A broader anti-cheat redesign is separate work.

### 11.3 Reload and failure

School/member records and scheduling metadata survive ordinary saves, chunk reloads, and restarts. Caught members must not return after reload. Unfinished approaches/sessions are not restored as active clients after restart; unresolved members recover or depart based on saved visit validity.

World schools and scheduling are saved per dimension. A live minigame keeps its immutable target stacks in the existing in-memory `FishingMinigameManager` session; the saved reservation records its session and target index, not a restorable client fight. On reload, in-flight sessions are discarded and members recover. Successful completion commits the member as caught before the ordinary reward pipeline runs. Normal gameplay and orderly save/restart are duplicate-safe. An arbitrary process failure between independent Minecraft saves has no exactly-once guarantee: a mismatched world/player save could lose or duplicate a reward. Minecraft does not provide a transaction spanning world data and player inventory; a persistent reward ledger and recovery protocol would be needed to close that gap.

## 12. Presentation, accessibility, and settings

### 12.1 Readable states

Every school member in the water shows the interactive cue (its sparkle trail) until it is caught or leaves with a departing visit, including while it approaches, bites and recovers (owner decision, 2026-10-25). An approaching member visibly swims to its bobber; competing hooks are excluded by the server's reservation, not by the cue. Bite-ready uses recognizable normal bite feedback. An in-session member has no catchable free copy.

Visual departure should look like the school leaving or fading out of local observation. It must not produce item drops or imply that an uncaught member was awarded. Catch and departure are distinct server outcomes even if a distant viewer only notices the population shrink.

### 12.2 Independent visual budget

The existing decorative `ambientFishEnabled` and density options cannot silently disable authoritative opportunities or change their rarity. Add an explicit presentation policy for interactive schools, with a restrained default visual budget and an optional enhanced cue.

Reducing decorative density to zero still permits the player to discover an interactive opportunity. A minimal cue/member representation must remain available even with reduced particles. A fully hidden client presentation, if offered, must be an explicit user choice and must not alter the server population.

No color-only distinction is sufficient. The cue needs motion, shape, or another non-color component. Standard supported graphics settings must not make the mechanic depend entirely on particles that the player may have disabled.

### 12.3 Learning the rule

Provide a concise in-game explanation in an existing suitable help surface: sparkling wild fish can be approached by casting nearby; one of the normal targets represents that fish; ordinary catches continue alongside it. Avoid a new mandatory tutorial sequence for the first release.

The Almanac remains a forecast of normal fishing candidates under the current tackle. It should not be silently changed to claim that bait can normally roll a school-only exception. If the UI lists a nearby school opportunity later, it must label that as a separate observed opportunity.

## 13. Logical data and synchronization contract

The implementation may choose suitable entities or server-managed records. Regardless of representation, these logical facts are required:

| Record | Required information |
|---|---|
| Region scheduling | Stable region/dimension key, next eligible attempt, generation/visit ownership, bounded recent depletion history. |
| School | Unique identity, species ID, region and movement bounds, creation/departure deadlines, state, formation environment, member list. |
| Member | Unique identity, school identity, stable reward seed, movement state, availability state, recovery deadline, optional reservation. |
| Reservation | Owning player, hook identity or session identity, lease/deadline, lifecycle generation. |
| Session binding | Session/member identities and exact target index are persisted with the reservation. The active session owns the immutable target snapshot in memory; the member state persists caught/recovered/departed resolution. |

Save stable identifiers and registry keys rather than transient client objects. Missing or removed datapack species must retire affected unbound members safely. Already resolved session snapshots need an explicit invalid-registry fallback that cannot create a substitute rare reward.

*(Being replaced: see [the motion plan](interactive-fish-schools-motion-plan.md) §4 step 3.)* The implementation sends bounded full snapshots on a 10-tick cadence to players within the configured 48-block tracking range, processing at most 32 players per batch. Snapshots include the dimension and current visible members; in-session, caught, departed, unloaded, or invalid-water members are omitted. A late joiner sees the remaining shared population, not the original full member list. The play connection's ordered delivery and complete snapshots avoid a separate client mutation/request protocol.

The server owns meaningful position and reachability. Clients may use existing fish simulation for schooling detail and interpolation, but cosmetic motion must stay close enough to authoritative positions for casting to feel accurate. A client-only fish wandering far from the server's capture area would violate the experience.

Broadcast updates only to nearby relevant players. Spawn and depletion do not depend on client requests, and requests cannot identify arbitrary distant water or reserve a claimed fish without server validation. Packets, lists, and request rates have explicit bounds.

## 14. Performance and world behavior

Independent caps bound two scheduler candidates per 20 ticks, eight hook checks per five ticks, candidate water searches (400 columns, 4400 connected cells), active schools and members, path work, tracked members (64 per snapshot), snapshot batches (32 players), render range, update cadence, and retained region metadata. A cap on rendering alone does not bound server work.

Use loaded regions around players, staggered updates, bounded searches, and cached validation where safe. Dense player groups must share discovery/scheduling work rather than multiplying the population. Dedicated servers must not load client renderer classes.

Unloading a chunk suspends local simulation without forcing it to remain loaded. Resumption respects saved deadlines and depletion. Bound minigames retain enough member state to complete after the original water chunks unload.

Water destruction, freezing, drainage, obstruction, dimension removal, and invalid geometry cause recovery/departure rather than spawning fish on land or issuing fallback rewards. Configuration disablement stops new visits and safely drains existing reservations; already valid sessions can finish.

## 15. Content and tuning surface

Use one documented source for school-specific content. Keep natural ecological rules in `FishProfile`; school metadata should describe encounter suitability and limits rather than duplicate biome/time/weather/moon tables.

The initial school content needs a curated species allowlist or equivalent opt-in. A new catchable species must not automatically become a schooling species before its visual behavior, water requirements, and yield are reviewed.

Required tuning dimensions include:

| Area | Values to choose during planning and playtesting |
|---|---|
| Frequency | Scheduling interval, encounter chance, regional cooldown, overlap radius, active-school caps. |
| Population | Per-species member range and hard total caps, especially for rare species. |
| Visit | Active lifetime, departure duration, tracking range, unload behavior. |
| Interaction | Interest radius, reachable-water test, minimum and maximum approach duration, bite window, recovery delay. |
| Reliability | Reservation lease, session timeout, snapshot/revision and retry policy. |
| Presentation | Sparkle density/contrast, reduced-effects fallback, school spacing, approximate species scale. |
| Budgets | Per-tick geometry/simulation work, members per observer, update cadence, packet limits, saved-record retention. |

Server configuration controls opportunity and simulation budgets. Client configuration controls presentation. Neither client settings nor the number of connected viewers changes species availability or generates extra fish.

## 16. Acceptance scenarios

These are requirements for the later implementation plan, not claims of tests already run.

| Scenario | Required result |
|---|---|
| Fish has a hard habitat-zone restriction | It cannot form a school outside compatible habitat. |
| Fish has a day/full-moon/cold-biome weight preference | School selection reflects that same preference without inventing a hard gate. |
| Two players hold different bait/charms | They observe the same interactive school despite differing decorative fish or normal pools. |
| World weather changes | Future formation weights reflect the world; personal preview effects do not. |
| Player uses no bait | An eligible school member can approach; remaining targets use bare-hook rules. |
| Blazed Grub meets a non-exotic school | One bound non-exotic target is possible; remaining ordinary fish targets retain the exotic filter. |
| Bait changes target count or bobber size | Normal session effects apply; school still replaces exactly one slot. |
| Uniform replacement selects a treasure/trash slot | That slot becomes one fish; no discarded reward leaks through. |
| Another target rolls the same species | Only catching the bound index depletes the member. |
| Fish approaches visibly | It reaches the actual bobber through valid connected water and drives one bite transition. |
| Hook is across a wall or in stacked water | No invalid attraction or reservation occurs. |
| Normal bite already owns the hook | No school binding or second session is created. |
| Two players attract the same member | Exactly one reservation succeeds. |
| Catch succeeds | One member is consumed and its normal reward/progression occurs once. |
| School target escapes but other targets are caught | Ordinary rewards resolve; school member recovers without a school reward. |
| Equivalent retry after failure | Member identity and reward outcome do not reroll. |
| Equipment changes after session creation | Bound reward, target count, and difficulty remain the session snapshot. |
| Visit expires during a fight | Existing valid binding can finish; free fish depart and no new approach starts. |
| Disconnect/death/session timeout | Reservation releases, no unearned school reward appears, and no permanent lock remains. |
| Duplicate/stale/out-of-range completion report | No duplicate fish, depletion, or progression. |
| Area unloads or server restarts normally | Deadline, cooldown, and depletion survive; no fresh full school is generated from the old visit. |
| Reconnects and region-boundary movement repeat | Encounter opportunity is not reset. |
| Late viewer enters range | It sees only the remaining available/bound/departing population. |
| Decorative density is zero or particles are reduced | Interactive opportunity remains discoverable under its explicit presentation policy. |
| School member is merely seen | No catch record, encyclopedia unlock, quest progress, XP, or items are granted. |
| School water becomes invalid | Unbound members retire safely; no movement through walls or fallback item award. |
| Debug/bulk ordinary fishing simulation runs | It does not reserve or deplete real schools. |
| Dedicated server and supported loaders run the feature | Shared authority and reward behavior agree; client classes remain client-only. |

Manual playtesting should assess cue readability, whether the approach feels connected to the bite, whether hidden target binding creates satisfying uncertainty, competition between nearby anglers, school scarcity, and bait's continued value. Balance evaluation must compare rewards per time as well as rewards per cast.

## 17. Phased rollout

### Phase A: Interactive schools alongside existing ambient fish

Deliver the complete discovery → approach → bite → minigame → depletion loop with a small curated species set, authoritative shared visits, normal progression, and the persistence/budget rules above. Decorative populations retain their current contract.

This phase is not complete if the world fish is merely an animation for an unrelated random catch, if catches cannot deplete a shared member, or if reloads regenerate the opportunity.

### Phase B: Content and migration depth

After gameplay and balance review, expand suitable species, distinct school formations, local routes, ecology-driven timing, and richer departure behavior. Any forecast, quest, or equipment interaction receives its own explicit design rather than emerging accidentally from spawn code.

### Phase C: Ambient fish 2.0 decision

Use evidence from server cost, player understanding, catch yield, and school frequency to decide whether more ambient populations should become shared or interactive. A unified renderer/simulation presentation can evolve earlier; universal persistence and catchability are separate product decisions.

## 18. Resolved Phase A decisions

1. **Initial species and school sizes.** The curated `fish_school_enabled` tag contains Bluegill, Bridle Shiner, Neon Tetra, and Yellowstripe Grunt. Schools contain 2–4 members, subject to the configured per-dimension capacity. Profiles with base weight above 25 use a minimum of 3; other eligible profiles use a minimum of 2. Members begin within 4 blocks horizontally and 2 blocks vertically of the selected water anchor, with at least one block between their centers. Rarer content can be added only by editing the curated tag after its visuals and habitat are reviewed.
2. **Encounter numbers.** Each visited 128×128-block region starts with a persisted 1,200–2,399 tick delay, then gets one opportunity every 24,000 ticks with a 12% encounter roll. Failed opportunities advance the schedule. Defaults cap active schools at 6 per dimension and 16 across loaded dimensions, with 24 members per dimension. A 64-block exclusion radius limits overlap. Visits remain active for 12,000 ticks and depart for 400 ticks. These values and all interaction timings are in `fishtastic-server.properties` under `schools.*`; loaded region history is retained up to 60 in-game days and 4,096 records per dimension.
3. **Quality/size identity policy.** The member stores a stable reward seed. Quality and size use the normal fish profile distribution, with the equipped bait and hook quality bias captured when the minigame starts. An equivalent retry reproduces that member's quality and size. The world model uses the species' mean size and does not promise the exact eventual stack size or quality.
4. **Strict ecological windows.** There are no school-only time, weather, or moon gates. Candidate selection uses the server's natural environment and the existing fishing resolver with no bait, charm, or personal equipment. Existing habitat eligibility and environmental weighting keep their current meanings.
5. **Water eligibility.** A qualifying cell is loaded, source water with an empty collision shape. Cells must connect face-to-face inside a bounded region up to 16 blocks horizontally, 6 down, and 4 up; discovery is capped at 400 columns (each kept to its full depth in that box, at most 4400 cells) and requires at least 6 cells. The cap bounds horizontal spread rather than volume: a 256-cell cap made a deep lake a blob ~4 blocks across, and bobbers cast 2-3 blocks from a fish fell outside it (2026-10-25). Small and player-built pools qualify if they meet those rules and the species' habitat zones. There is no sky-access requirement or additional dimension allowlist; a dimension can host a school only when its natural resolver and fish profiles return an eligible candidate. Frozen or obstructed cells stop qualifying; unsupported habitat causes departure. No chunk is loaded to continue a school.
6. **Save/crash policy.** Each dimension stores schools and region deadlines in level `SavedData`. Caught members and failed attempts survive ordinary saves. In-flight hook/session ownership is cleared into recovery on reload because a live hook and client minigame cannot be restored. On successful completion, the member is marked caught before the ordinary reward pipeline runs, so duplicate reports cannot pay twice. This is duplicate-safe during normal play and orderly saves. An arbitrary process failure between the independent world and player saves can lose or duplicate a reward; stronger crash recovery would need a persistent reward ledger and recovery protocol.
7. **Visual cue and help.** Available members leave a trail of small animated motes (six symmetric sprite sheets, tinted in code; see the motion plan) with the existing species model; the cue is geometry, so it is not disabled by reduced particle settings or decorative ambient density. `/fishtasticambient schools visible <true|false>` hides or shows schools locally without changing server opportunities. `/fishtastic schools` explains the mechanic in game. The display defaults to visible.

Phase A code lives in `FishSchoolManager`/`FishSchoolSavedData`, `FishingMinigameManager`, `FishSchoolClientRuntime`, and `FishSchoolSyncPacket`. The implementation keeps decorative ambient fish unchanged. The remaining work for content expansion and any broader ambient migration belongs to Phases B and C above; in-water readability and reward-rate balance still need ordinary gameplay feedback.
