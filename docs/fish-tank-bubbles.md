# Fish Tank Bubbles — Simulation-Driven Particles

**Written:** 2026-09-11 · **Scope:** client-side particle emission driven by the `:fishsim`
flock engine's per-fish state. The engine itself is untouched apart from read-only probes; the
golden/parity locks ([`GoldenTrajectoryTest`] / [`ParityTest`]) are binding and unaffected.

Companion to [`fish-sim-locomotion.md`](fish-sim-locomotion.md) (the locomotion classes whose
envelopes this reads) and [`fish-tank-rendering.md`](fish-tank-rendering.md) (the frames the draw
loops use, which the emitter has to reproduce).

---

## 1. Intent

Fish should passively shed bubbles as they swim, vigorous swimming should shed noticeably more,
and a big swarm should read as busier than a small one *because it has more fish*, not because
anything counts the fish. Every bubble has a cause in the simulation: a breath, a wake, a burst
kicking off, a jelly contracting, an eel flinching. Nothing is on a timer.

## 2. Particle vocabulary

Four sizes, smallest most common. Textures live in `textures/particle/`; the three new types are
registered in `FishtasticParticleTypes` next to `TANK_BUBBLE` and share one class,
`TankMicroBubbleParticle`, parameterised per size.

| Particle | Type | Sprites | Quad size | Lifetime | Rise |
|---|---|---|---|---|---|
| `tiny_bubble` | new | `tiny_bubble_white` ×2, `tiny_bubble_blue` ×1 (random pick) | ≈0.0055 | 15–35 ticks, shrinks out over the last 8 | 0.004–0.010/t |
| `small_bubble` | new | `small_bubble` | ≈0.011 | 40–80 ticks, shrinks out over the last 12 | 0.010–0.020/t |
| `medium_bubble` | new | `medium_bubble` (an 8×8 slightly smaller take on vanilla's bubble) | 0.04 × 0.35–1.15 — a wide spread up to half the chest-cosmetic size | 60–110 ticks | 0.012–0.024/t |
| `tank_bubble` | existing | vanilla `bubble` | chest-cosmetic base × 0.12–0.8 (spread widened from 0.2–0.8; the chest shares it) | until the glass | existing |

**Size is rolled, not assigned.** Every spawned bubble picks its type from a
(tiny : small : medium : tank_bubble) weight table, so the smallest is always the most common and
the full vanilla-size one the rarest. What the context decides is *which table*:

| Table | tiny | small | medium | tank_bubble | Used for |
|---|---|---|---|---|---|
| `PASSIVE_WEIGHTS` | 85 | 10 | 4 | 1 | breath, wake, crawler sand kick |
| `EXERTION_WEIGHTS` | 55 | 28 | 12 | 5 | burst / pulse clusters, eel startle, a glider's breath |

Why the white/blue split is a random pick rather than a positional signal: at 1px and quad size
≈0.02 the colour isn't legible as meaning; it reads as shimmer variety. Cheap to bias later.

**Pop.** Vanilla's `BubbleParticle` never pops — its `bubble_pop` is a separate five-frame
particle other systems spawn at a fixed size. So `tank_bubble` and `medium_bubble` spawn
`tank_bubble_pop` (`TankBubblePopParticle`, vanilla's `bubble_pop_0..4` sprites) when they die,
sized 1.4× the bubble that burst. Tiny and small have already shrunk to nothing and don't pop.

**Aux convention** (all three): `xAux`/`zAux` = initial horizontal drift, `yAux` = absolute world
Y to pop at. The micro-bubbles inherit 30 % of the emitting fish's velocity and bleed it off at
×0.85/tick, so a trail visibly streams off a moving fish instead of appearing as static pips. No
physics — the tank interior has no fluid, and a bubble in a stack has to pass through the tank's
own geometry on the way up. OPAQUE layer, same as `TankBubbleParticle`.

## 3. Emission model

`TankBubbleEmitter` runs once per client tick per **rendered** tank, from
`ClientTankFlocks.tickAll()` immediately after that flock's `step()`. It reads engine arrays,
writes nothing to the engine, and takes randomness from `ClientLevel.getRandom()` — never the
engine's noise stream.

### 3.1 Passive (FREE_SWIM, GLIDE)

```
vigor  = clamp((speedFactor(i) − 0.6) / 0.9, 0, 1)       // the engine's own speed→animation map
p_tick = P_REST + (P_CRUISE − P_REST) · vigor²            // 1/120 at rest … 1/30 flat out
p_tick *= size                                            // size = clamp(length / 0.3, 0.25, 3)
GLIDE:  p_tick *= 0.5, rolled from the exertion mix       // huge animal, fewer larger bubbles
```

A passive roll spawns `sized(1)` bubbles (passive mix) at the **nose** (0.4 × length along the
forward vector). When `vigor > 0.6` a coin flip adds `sized(1)` more at the **tail**
(−0.45 × length) — the wake.

**Size drives both frequency and quantity.** `size = length / 0.3` (clamped 0.25–3) multiplies
the per-tick probability *and* every cluster count: `sized(n) = n · size`, rounded
stochastically (a 1.5× fish releases two bubbles half the time) and never below one. A fish twice
the reference length therefore emits twice as often and twice as much per emission — four times
the bubbles overall — which is what makes a big fish read as big without any special casing.

### 3.2 Exertion events (all classes)

The engine's `burstDrive` envelope is universal — a swimmer's thrust, a drifter's pulse, a
crawler's scuttle all integrate into it — but what *drives* it differs, and so does the onset
signal:

- **FREE_SWIM, GLIDE**: the burst-and-coast phase clock wraps from ~1 back to 0 at thrust
  onset, so **event = `burstPhase(i) < prevBurstPhase[i]`**.
- **DRIFT, BENTHIC**: the phase is never advanced for them (every fish carries a burst step, but
  only `advanceBurst` moves the phase) — the pulse and the scuttle run on their own seed-derived
  clocks (`pulsePhase`, `dwellPhase`) and push the envelope 0→1 directly. **Event = `burstDrive(i)` rising through 0.5** (`ENVELOPE_ONSET`); with the drift
  attack rate of 4/s that lands ~0.17 s into the contraction, when the squeeze is visible.
- **Anchored**: **`anchorTimer(i)` crossing from ≤ 0 to > 0** — the retract starting.

| Class | Trigger | Emits |
|---|---|---|
| FREE_SWIM | burst onset | `sized(1–3)` at the tail, exertion mix |
| DRIFT | pulse onset | `sized(4–7)` in a ring at the bell top (0.3 × length above centre), drifting outward at 0.6× the burrow poof's speed, exertion mix — the water the contraction squeezes out |
| BENTHIC | scuttle onset | `sized(1–2)` at the floor, passive mix — a subtle sand kick |
| ANCHORED | retract onset | `sized(8–13)` in a ring around the burrow, each drifting outward on its own bearing (0.006–0.016 blocks/tick, bleeding off), exertion mix — the poof of a garden/royal eel snapping back into its hole |
| GLIDE | — | nothing (a wingbeat cadence would be constant noise); passive only |
| STATIC | — | nothing |

Edge memory (`prevBurstPhase[]`, `prevBurstDrive[]`, `prevAnchorTimer[]`) lives in a `TankBubbleEmitter.State` per
engine on the adapter. It is resized and resampled whenever the fish count changes; a rebuild
reindexes fish, so the one spurious edge that can produce is accepted rather than tracked.

### 3.3 Swarm scaling and budget

N fish roll N times, so density scales with population for free. To keep a 512-fish group sane:

```
budget = clamp(2 + N / 16, 2, 20) spawns per tick per engine
```

Events are processed first and always spend from the budget — a burst you can see should always
bubble. Passive emission is then thinned by `min(1, remaining / Σ p_tick)` applied to every fish's
probability, so density saturates smoothly instead of hard-cutting whichever fish sit late in the
array.

### 3.4 LOD

No emission beyond 32 blocks camera→tank; inside that, every probability (events included) is
scaled by `1 − dist / 32`. `Level.addParticle` already honours the vanilla Particles option
(Decreased/Minimal), so there is no mod config.

## 4. Coordinates

The emitter works at tick time from `posL/posY/posD`, not from the interpolated render scratch,
so it needs the same origin + rotation the draw loops apply:

- **Lone tank:** `FlockEngine.toRender` (the rotation `interpolate` applies, now factored out as
  the inverse of `toLocal`), then `(0.5, computeBaseY(anim, openDown, length), 0.5)` — the
  per-pose baseline `FishTankBlockEntityRenderer` translates by.
- **Group anchor:** `toRender` (identity today, but not a law) plus the adapter's
  `groupOffset{X,Y,Z}`.

Forward vector = normalised `(velL, velD)` through `toRender` when moving, else the binary
`heading` along lateral — so a wake trails the direction actually travelled and a hovering fish
still has a nose.

**Pop ceiling** per spawn: the tank cell the bubble spawns in (a group's columns can differ in
height) → `topOfConnectedTankStack` → `+ TANK_CEILING_Y`; falls back to the emitting tank's own
column if the spawn point lands outside any tank.

## 5. Engine surface (`:fishsim`, behaviour-neutral)

- `toRender(l, y, d, out)` — inverse of `toLocal`.
- `burstPhase(i)`, `burstDrive(i)`, `anchorTimer(i)` — read-only probes of existing state.

No new arrays, no step changes. `interpolate` was left byte-identical rather than refactored to
call `toRender`, so the bitwise locks cannot move.

## 6. Files

| File | Role |
|---|---|
| `client/renderer/TankBubbleEmitter.java` | the whole emission model; every rate is a named constant at the top |
| `client/particle/TankMicroBubbleParticle.java` | `tiny_bubble` / `small_bubble` / `medium_bubble` particle + providers |
| `client/renderer/TankFlockAdapter.java` | owns the two `State`s |
| `client/util/ClientTankFlocks.java` | calls `emit` after `step` for flocks extracted last tick |
| `FishtasticParticleTypes`, both client initialisers | registration |
| `client/particle/TankBubblePopParticle.java` | the sized pop, spawned by the two vanilla-style bubbles on death |
| `assets/fishtastic/particles/{tiny,small,medium}_bubble.json`, `tank_bubble_pop.json`, `textures/particle/*` | assets |

## 7. Tuning knobs and what they do

| Constant | Effect |
|---|---|
| `P_REST`, `P_CRUISE` | baseline ambient density vs how much faster swimming stands out |
| `SIZE_REFERENCE_LENGTH`, `SIZE_WEIGHT_*` | the length that counts as 1.0 and the clamp on the linear size factor (rate × count) |
| `WAKE_VIGOR` | speed at which the tail trail appears |
| `PASSIVE_WEIGHTS`, `EXERTION_WEIGHTS` | the size mix per context; the last column is how often the vanilla-size bubble shows up |
| `BUDGET_*` | swarm saturation point |
| `LOD_RADIUS` | how far away tanks still bubble |
| `DRIFT_INHERIT` | how visibly a trail streams off a moving fish |

## 8. Verification

- `:fishsim:test` unchanged (accessors only) — passes.
- `ExertionProbeTest` pins the onset signal per class (swimmer phase wrap, drifter/crawler
  envelope crossing) so a change to how a class drives its envelope can't silently mute it.
- All three modules compile.
- **Pending an in-game look:** the rate constants are first guesses. Expect to tune `P_CRUISE`,
  `WAKE_VIGOR` and the burst cluster size first; the budget should only bite on large groups.
