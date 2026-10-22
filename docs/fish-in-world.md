# Ambient fish

**Status:** Implemented. This page is the current feature contract; code is authoritative if it differs. Human playtest review remains open.

## Player-facing behavior

Ambient fish are local visual representations of species eligible at nearby natural water under the current fishing context. A sighting indicates possible eligibility, not a guaranteed catch or a targetable individual. Visible abundance is approximate, and absence does not mean a species is unavailable.

The server resolves fishing context from the validated water origin and authoritative player/world state. With an active cast nearby, it uses that cast's captured species context. Otherwise, a supported held Fishtastic rod uses its actual preview context; without one, the visual baseline is the standard-worms pool with neutral personal equipment modifiers. This baseline does not require or consume worms.

Population mixes can differ between clients. Fish are decorative and client-simulated: they have no hitboxes, drops, saved persistence, capture, progression effects, encyclopedia unlocks, or effect on catch rolls. The feature does not replace the Almanac's ranked fishing information.

Composition changes gradually when equipment or context changes, and brief movement or re-entry does not reroll the whole population. New choices have 90–150 second lifetimes; invalid water and species that become ineligible can retire sooner. Natural water whose fishing context cannot be established is omitted rather than assigned a guessed species pool.

The feature reuses tank fish simulation, descriptors, animation, and rendering. One density policy bounds local discovery, context requests, simulation, and rendering together. It does not load chunks to populate fish.

## Settings and local commands

Client settings live in fishtastic-client.properties:

| Setting | Default | Effect |
|---|---:|---|
| ambientFishEnabled | true | Enables local ambient fish. |
| ambientFishDensity | 50 | Scales discovery and population; valid range is 0–100. |

The individual cap is 64 at 100% density and 32 at the default 50%; usable water volume may lower the actual population. Density 0 disables ambient work.

Available on both loaders:

    /fishtasticambient enabled <true|false>
    /fishtasticambient density <0-100>
    /fishtasticambient debug

## Validation and remaining review

Implementation checks recorded at feature close passed for the simulation/common code, both loaders' GameTests and client scenes, dedicated-server loading, and packaged jars. This records engineering validation, not human release sign-off or publication.

Remaining owner review is ordinary gameplay for crowding and rare sightings, shore visibility, conservative geometry omissions, disconnected or stacked water, and cold-session CPU spikes.
