# Connected tank budgets and rendering

The current limits are `TankGroups.RENDER_MAX_GROUP_SIZE = 512` tanks and
`RENDER_MAX_GROUP_FISH = 1024` simulated fish. Tank count bounds discovery/domain
work; fish count bounds simulation. Neither is a measured GPU frame-time promise.

## Membership, caches and quota

`ClientTankGroups` caches group membership and the shared `VoxelDomain` by membership
epoch. Inventory/cosmetic updates must not rebuild the expensive distance field
unless membership changes. Cosmetic floor, obstacle and shelter data have their
own fingerprint-driven updates.

`TankGroups.perTankFishCap` computes a water-filling cap from actual member counts:
the largest C for which `sum(min(fish_i, C)) <= RENDER_MAX_GROUP_FISH`. A group
already under budget is uncapped. Over-budget groups trim busy members rather than
freezing fish in thinly stocked members, retaining the documented one-fish floor.
`TankFishBudget` and `GroupSplit` make the simulation and local-display passes
agree. A traversal-dependent running total can render fish twice or omit them.

## Neighbor indexing

`NeighborGrid` prunes distant candidates using a radius derived from the largest
interaction reach, including anticipatory separation. Indexing start-of-step
positions while reading live integrated positions also requires one tick of travel
padding. Candidates are emitted in ascending fish index via a bitset so float
summation order matches the brute-force model exactly.

`SpatialIndexEquivalenceTest` compares raw float bits with brute force;
`GoldenTrajectoryTest` and voxel goldens must not change for indexing refactors.
Dense tanks still contain genuinely close neighbors, so indexing cannot guarantee
linear cost under arbitrarily increasing density.

## Rendering follows fish location

`TankGroupFlock` owns the shared runtime independently of the elected anchor.
`GroupOwners` partitions space by nearest tank, and each block entity renders the
fish in its bucket. This keeps a visible fish in the same chunk section as its
renderer and supplies local lighting. Rendering the whole group from the anchor
made every fish disappear when the anchor's section was culled.

Each bucket preserves the engine's back-to-front order. Ordering across separate
block entities follows vanilla section dispatch rather than one global fish sort.
Ownership must be total and unique: no unowned fish and no double submission.
`GroupOwnersTest`, `GroupSplitTest` and `TankGroupsBudgetTest` guard these rules.
The render self-test `pertank` scene exercises culling and lighting.

## Evidence and limitations

The 512-tank change was accepted in game on a 100-plus-tank build on 10 September
2026. Historical CPU measurements showed strong spatial-index gains at dispersed
stocking, but dense groups remained much more expensive. They do not establish
current GPU cost or validate saturation of either limit.

The distance-transform redesign, a 4,096-tank cap and off-screen simulation LOD
were not implemented. Off-screen fish still step even though rendering is culled.
`VoxelDomain.avoidance`'s vertical margin is not a harmless one-line correction:
GROUP inherited a narrow unused margin; applying it caused containment failures.
Changing it requires deliberate group tuning and review of the voxel golden.

Reproduce CPU scaling with:

```sh
./gradlew :fishsim:test --tests '*StepScalingProbe*' -PsimStdout
```

For rendering limits, saturate the fish budget in a real build and measure frame
times, including shaders where supported. Use `/fishtastic tanksim` for group size,
fit gates, caps and per-slot STATIC/group verdicts before diagnosing frozen fish.
