# Fish swarm model

The planar group model's realism work is implemented. This page records the shipped behavior and
the boundary between current behavior and future ideas; probe histories and tuning tables have been
removed.

## Shipped behavior

- Each fish gets seeded, aperiodic wander and small per-fish trait differences.
- Alignment follows unit headings with a separate weak speed-matching term. Near walls, alignment
  and burst strength fade to avoid fighting containment.
- Fish use a burst-and-coast cycle. The planar model has a turn-rate limit and anticipatory
  separation based on predicted closest approach; the single-tank binary 2.5D model remains
  unchanged.
- A proposed field-of-view filter was removed because it had no measurable effect at tank scale.
  Do not restore it without a behavior signal the filter can actually change.
- The smallest tanks can mill into a torus. This is intentional emergent behavior; judge its look in
  game rather than optimizing a metric toward a presumed ideal formation.

## Tuning boundaries

Metrics are diagnostic. They can explain a behavior but are not acceptance targets. Keep seeded
repeatability and wall-containment tests, and check representative tank views after motion changes.
The per-species behavior-profile idea remains unstarted; add authoring controls only for a species
whose behavior cannot be expressed by the class defaults.

Collective vertical movement, a startle impulse that propagates through the school, and additional
formation styles remain future ideas. Tank group capacity and render cost are covered by
[fish-tank-group-scaling.md](fish-tank-group-scaling.md).
