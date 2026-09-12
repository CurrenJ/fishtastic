# Critical Review — *Real-Time Fish Swarm Simulation for Minecraft*

**Source:** `minecraft_fish_simulation_architecture_report.pdf` (12 sections, ~2 pages)
**Reviewer:** Claude Code · **Date:** 2026-08-20
**Verdict:** *Directionally correct, but not actionable as written.* The report is a competent
generic boids primer with Minecraft vocabulary layered on top. It never states what problem it
solves, never budgets a cost, and never engages with the three constraints that actually decide
whether this ships in a Minecraft mod: the client/server authority split, the fixed 20 Hz tick, and
the cost of block lookups. Sections 2–8 are fine as a textbook. Sections 1, 9, 10 and 11 are where
the real design decisions live, and those are the thinnest.

---

## 1. What the report gets right

Worth saying plainly, because the rest of this document is critique:

- **Steering over pathfinding is the correct call.** A\* per fish per tick is the wrong tool for
  continuous, non-goal-directed motion, and the report is right to reject it up front.
- **The four-layer decomposition (§1) is a good skeleton.** Environment → school → individual →
  animation is the standard separation and it survives contact with reality.
- **Temporary escape targets (§5) is the most valuable paragraph in the document.** Wall jitter is
  the failure mode that kills naive boids-in-a-box, and hysteresis via a short-lived committed
  target is the right fix. Most boids write-ups omit it entirely.
- **The topological hint in §7** — leading fish detect obstacles first, the response propagates
  through alignment — is a real insight about why schools look alive, not just a performance note.
- **§10 animation coupling** is correct and routinely skipped. Swim frequency tied to speed and bank
  tied to lateral acceleration is most of the perceived quality, for very little code.

---

## 2. The structural problem: no requirements, so every answer is the maximal one

The report opens with "For a Minecraft mod, the recommended approach is…" without ever establishing:

| Unanswered question | Why every downstream decision depends on it |
|---|---|
| **What is being simulated?** Aquarium ambience? Open-ocean schools? Catchable fish? | Determines whether these are entities, block-entity render state, or particles |
| **How many fish, in what volume?** 12 in a 1 m³ tank, or 400 across 128 m of ocean? | At N ≈ 30 the spatial hash (§3) is dead weight; at N ≈ 2000 it is mandatory |
| **Server-authoritative or client-cosmetic?** | Decides whether §§2–8 need networking, persistence and determinism *at all* |
| **What is the frame/tick budget?** | Nothing in the report is falsifiable without one |

Because none of these are pinned, the report recommends the union of all possible solutions: spatial
hashing *and* voxel probes *and* flood-fill *and* a school controller *and* four LOD tiers *and* GPU
instancing. For most plausible versions of this feature, at least half of that is complexity that
will never pay for itself.

**Revision:** open by picking one of three product shapes and committing to it.

- **(A) Contained ambience** — fish inside a bounded, player-built volume (aquarium/tank). The
  domain is a known box. Client-only. No networking, no persistence, no determinism requirement.
- **(B) World ambience** — schools in open water as decoration. Needs voxel sensing and LOD, but can
  still be client-only and non-persistent.
- **(C) Gameplay entities** — fish that can be caught, fed, startled, or killed. Needs server
  authority, entity registration, network sync, persistence, mob-cap interaction — a different
  project in kind, not in degree.

The architecture for (A) is perhaps 15% of the document's scope. The architecture for (C) is 300% of
it, because everything the report omits is the hard part. Presenting one architecture as the answer
to all three is the report's central flaw.

---

## 3. Minecraft-specific errors and omissions

### 3.1 "Near fish: 20–60 Hz" (§1) is not a thing on the server

Minecraft's server tick is a fixed 20 Hz. There is no 60 Hz simulation tier available to server
logic. The table's update-rate column therefore implies one of two things, and the report does not
say which:

- a **render-thread simulation**, which is legitimate and cheap — but is by definition
  non-authoritative, must not affect gameplay, and must not be relied on for anything two players
  need to agree about; or
- a **server simulation at 60 Hz**, which would require a custom sub-tick loop and is not something
  a mod should attempt.

This matters more than it looks. **A simulation whose behavior depends on frame rate is a bug**, not
a tuning parameter: a player at 30 fps and a player at 240 fps must see fish that swim at the same
speed and turn at the same rate. The report never mentions fixed-timestep integration with
render-side interpolation, which is the standard fix and belongs in the requirements, not in a
footnote.

### 3.2 The voxel sensing layer (§4) has no cost model, and it is the expensive part

"Sample a short 3D volume around and ahead of each fish" with "a fan of candidate directions or
probes" is where all the CPU goes. A modest configuration — 9 probe directions × 4 samples per probe
× 100 fish × 20 Hz — is **72,000 block-state lookups per second**, each a chunk lookup, a section
lookup and a palette indirection. That is not obviously affordable, and the report does not
acknowledge it as the dominant term in the whole design.

Three things are missing:

1. **Cache the occupancy field, not the queries.** Rasterize the local region once into a bitset
   (one bit per block: traversable / not), invalidate on block update, and let every fish read the
   bitset. This turns a chunk-palette lookup into a bit test, and is the difference between "works"
   and "doesn't."
2. **World reads off the main thread are not safe** in the general case. If the sim runs on the
   render thread (per §3.1), the report has to say how it reads the world. The cached bitset also
   solves this: snapshot on the main thread, read anywhere.
3. **Vanilla already ships raycasting and voxel collision** (`Level#clip` / `ClipContext`,
   `VoxelShape`). The report reinvents both without arguing why the built-ins don't suffice.
   Sometimes they genuinely don't — but that argument needs making, not skipping.

### 3.3 "GPU/instanced rendering" (§11, step 10) is not available off the shelf

Modern Minecraft rendering goes through the vanilla submit-node / render-type pipeline. There is no
public instancing API. Instanced fish means either depending on a third-party rendering library
(loader-specific, perennially version-lagging) or writing a custom pipeline with its own shaders — a
project comparable in size to the simulation itself. Listing it as "step 10" implies it is a late
optimization you can reach for. It is not.

### 3.4 Animation (§10) assumes a rigged 3D fish

"Body bend" and "bank" presuppose a skeletal or per-vertex-deformable model. Many Minecraft fish
representations are flat textured quads. If the fish are sprites, §10's advice is largely
inapplicable and needs replacing with the sprite-equivalent constraints — chiefly that a broadside
sprite only reads correctly within a limited yaw window, and a sprite fish turning to face the
camera degenerates into a line.

### 3.5 Entirely absent topics

None of these appear in the report, and each can invalidate the design:

- **Client/server authority and network cost.** N fish × (position, velocity) at any meaningful rate
  is real bandwidth. If the answer is "it's client-only," that is a *huge* simplification and
  deserves to be the report's headline rather than an omission.
- **Chunk load/unload and player teleport.** What happens to a school when its chunk unloads? When a
  player arrives at a fresh chunk, do fish pop into existence mid-flock?
- **Persistence.** Does school state survive a world reload? (For ambience it must not need to — say
  so.)
- **Entity budget / mob cap**, if these are real entities.
- **Water that isn't a clean solid/water binary.** Flowing water, waterlogged blocks, bubble columns,
  and above all the water/air surface. The surface is where a naive containment test visibly fails,
  and the "traversable water" classifier in §4 has to handle all of it.

---

## 4. Technical critique of the simulation core

### 4.1 The headline steering equation (§2) is the fragile formulation

```
desiredSteering = separation·Ws + alignment·Wa + cohesion·Wc + schoolDirection·Wschool
                + obstacleAvoidance·Wavoid + environment·Wenv
```

A flat weighted sum of six vectors is the version of boids everyone writes first and then fights
forever. Its known failure modes:

- **Weight fighting.** Cohesion and separation partially cancel; the residual is small and noisy, so
  tuning one weight silently retunes the effective strength of every other one.
- **Avoidance can be outvoted.** When five other terms point at a wall, `Wavoid` has to be enormous
  to win — and then it dominates in open water too, and the fish look nervous everywhere.
- **Unbounded magnitude.** Nothing in the equation clamps the result. The stability of boids comes
  almost entirely from max-force / max-speed clamping and a max turn rate, none of which the report
  mentions anywhere.

**Revision:** state it as an *acceleration* subject to explicit limits, and use priority arbitration
rather than pure summation for the safety-critical terms:

```
a = clamp(Σ soft terms, maxForce)          # separation, alignment, cohesion, wander, school dir
if containment/avoidance active:           # hard term — overrides, does not add
    a = blend(a, avoidance, urgency)       # urgency ∈ [0,1] derived from clearance
v = clampMagnitude(v + a·dt, minSpeed, maxSpeed)
heading = rotateTowards(heading, v, maxTurnRate·dt)
```

The distinction between *soft terms that sum* and *hard terms that override* is the single most
useful correction to make to §2.

### 4.2 Metric neighbors vs. topological neighbors (§3)

The report specifies a radius-based neighborhood. Real fish schools are **topological** — each fish
tracks its ~6–7 nearest neighbors regardless of distance, which is why schools stay coherent under
predator attack instead of shattering. Topological neighborhoods also bound per-fish work to a
constant by construction, which is a better performance story than the spatial hash. This is a free
quality win the report misses.

### 4.3 The spatial hash (§3) is premature for realistic N

O(N²) is a scary asymptote and an irrelevant one at N = 20–50: a full all-pairs pass over 40 fish is
1,600 squared-distance tests — comfortably under 10 µs. Putting the spatial grid at **step 2** of the
implementation sequence, *before* separation/alignment/cohesion exist at step 3, means building and
debugging infrastructure before there is anything to profile. Correct order: get flocking right with
brute force, measure, add the grid when N demands it.

### 4.4 LOD (§9) misses the cheapest technique and ignores the transition

The distance-tier table is standard and fine, but:

- **Temporal LOD is missing, and it is the best-value technique available.** Round-robin the fish
  across ticks — update ⅓ each tick, interpolate the rest — for a 3× cut with no visible difference
  and none of the pop risk of distance tiers.
- **Tier hand-off is unaddressed.** A school running on school-level state that a player walks toward
  must be promoted to full per-fish simulation without visibly snapping. That needs seeded
  reconstruction (deterministic from a hash, so the promoted state is plausible) or hysteresis bands.
  The report presents four tiers and no transitions between them.
- **Visibility beats distance.** A tank behind the player at 3 m needs less simulation than one in
  front at 20 m. Gating on "is this being rendered this frame" is both cheaper and more accurate than
  a distance check.

### 4.5 Flood-fill (§8) is very likely dead code

A bounded escalation planner invoked only when every directional probe fails is reasonable *in
principle* and a poor use of implementation budget *in practice*: it is the hardest thing in the
document to get right, it fires rarely, and its failure mode — a fish stuck in a hole for a second —
is cosmetically trivial. The pragmatic alternative is a stuck detector: if displacement over N ticks
falls below a threshold, pick a random clear direction and commit to it for a second. Twenty lines
instead of two hundred, and visually indistinguishable.

### 4.6 The school controller (§6) is underspecified where it matters

The mode table is fine, but the interesting questions go unanswered: how are schools *formed* and
*dissolved*? Can they merge or split? Is membership fixed at spawn? What owns a school whose fish
span a chunk boundary? A fixed roster with no merge/split is much simpler and probably sufficient —
but that is a decision the report should make rather than leave implied.

---

## 5. Revised implementation sequence

The report's sequence front-loads infrastructure and defers the things that determine whether the
feature is any good. Proposed replacement:

| # | Step | Why here |
|---|---|---|
| 0 | **Write the requirements**: product shape (A/B/C), target N, containing volume, budget in ms, authority model | Everything else is unfalsifiable without this |
| 1 | Single fish: fixed-timestep integration, max speed, max turn rate, render-side interpolation | Frame-rate independence is unfixable later without rewriting everything above it |
| 2 | Containment against the *simplest possible* domain — a box, or a list of AABBs | Proves the hard/soft arbitration split before any voxel work exists |
| 3 | Separation, alignment, cohesion — brute force, topological k-nearest | Where "does it look alive?" is actually answered. Nothing before this is worth optimizing |
| 4 | Wander / school direction as a slow noise field | Turns a clump into a school |
| 5 | Animation coupling: swim rate from speed, bank from turn rate | Cheapest large quality gain in the list |
| 6 | **Measure** at target N | Gate: nothing below happens unless the numbers demand it |
| 7 | Temporal LOD (round-robin) + visibility gating | Best cost/benefit, no visual risk |
| 8 | Cached voxel occupancy bitset + probe scoring | Only if the domain is genuinely voxel-shaped (shape B/C) |
| 9 | Escape targets + stuck detector | Polish for the failure cases §§4–5 worry about |
| 10 | Spatial hash | Only when profiling shows neighbor search is the top cost |
| 11 | School modes (feed/flee/regroup), predator interaction | Content, not infrastructure — needs a gameplay reason to exist first |

Flood-fill and GPU instancing are removed. They should be re-proposed with evidence if steps 6 and 10
show a need.

---

## 6. Summary of recommended revisions

1. **Add a requirements section** committing to one product shape, a target fish count, a volume and
   a millisecond budget. Retitle the document to match the shape it chose.
2. **Add a "Minecraft execution model" section**: authority (client vs server), fixed 20 Hz tick with
   render interpolation, thread safety of world reads, chunk lifecycle, network cost — or an explicit
   statement that the sim is client-only cosmetic and therefore exempt from most of it.
3. **Reformulate §2** as clamped acceleration with hard/soft term arbitration, not a flat weighted sum.
4. **Add topological neighbors** to §3, and demote the spatial hash to a profiling-gated optimization.
5. **Add a cost model to §4** and lead with the cached occupancy bitset rather than per-fish
   per-tick world queries.
6. **Add temporal LOD and tier hand-off** to §9; add visibility gating alongside distance.
7. **Replace flood-fill (§8) with a stuck detector**; move flood-fill to an appendix of ideas.
8. **Qualify §10** for the case where fish are flat sprites rather than rigged models.
9. **Reorder §11** per the table above, with an explicit measurement gate before any optimization.
10. **Remove the GPU instancing recommendation**, or replace it with an honest account of its cost.

---

*Companion document:* [Fishtastic feasibility & fit analysis](fish-simulation-feasibility.md) —
whether a simulation like this can work in **this** mod specifically, and where it would live.

*Implementation:* [Fish simulation handoff](../fish-simulation-handoff.md).
