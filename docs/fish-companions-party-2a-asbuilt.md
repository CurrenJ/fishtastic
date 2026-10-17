# Party Phase 2a as built: size-picked leader (D9) and slowest-member pacing (D10)

Fork 2a, worktree `D:/GitHub/fishtastic/.claude/worktrees/agent-ab3925cd07dc281da`, base
`6db92f87`. `:fishsim` only, uncommitted. Raw output: `build/2a/` in the worktree
(`base.txt`, `on.txt`, `off.txt`, `suite.txt`, `fp-final.txt`, sorted fingerprint files).

## What changed

- **D9, `PartyGrouping`:** a group's members come out in **formation order**: smallest first,
  ties to the lower slot (`FORMATION_ORDER`). The first member leads.
  - New accessors `Group.leaderSlot()` (the formation leader's party slot) and
    `Group.lowestSlot()`. Groups are still ordered by their lowest slot.
  - `slots()` and `lengths()` are in formation order. `seed()` is the leader's member seed.
  - Identical members tie, so the lowest slot leads, exactly as before.
- **D10, explicit pace cap (opt-in):** `CompanionTunables.groupPaceCap`, **default false**.
  - When on, `BenthicGroup` gives its leader `GroundBody.paceCap` = the slowest member's `pace`,
    if any member is strictly slower.
  - `GroundBody.stride` then limits the leader's rhythm speed by `paceCap / pace`. That's a cap on
    the asked speed inside the existing heading-first walk, with no lerp.
  - New package accessors in `CompanionAgent`: `groundPace()`, `groundPaceCap(double)`,
    `groundPaceCap()`.
- **Schools are untouched.** A swimmer's gait (`Gait.of`) doesn't depend on its length, so every
  school member already has the same natural pace, and D10 is a structural no-op for schools.
- **Harness:**
  - `MixedGroupProbe` gains the reversed orders (`big 60/40/25`, `crowd 18/12/8`, `crowd 30/40/50`,
    `crowd 50/40/30`), `-PtweakA` support, and per-seed `SEED` lines.
  - `GroupRun` takes tunables.
- **Tests:**
  - `PartyGroupingTest` +3: smallest leads and followers come smallest first, with
    `leaderSlot`/`lowestSlot`/seed; size ties go to the lower slot and groups keep lowest-slot
    order; the cap is set only for a bigger leader, is never set for identical members, and is
    off by default.
  - `MixedGroundInvariantTest`: the gap is closed (below).

## Why the cap is off by default

D9 delivers D10 by construction. A ground body's pace is `sqrt(clamp(length, 0.1, 0.8) / 0.3)`,
which never decreases with size. Smallest first therefore means the leader is always the slowest
walker, and the group moves at its slowest member's pace. Every smallest-first config
(line 30/40/50, crowd 8/12/18, crowd 30/40/50) is **bitwise identical with the cap on or off**.

The cap only engages for a big-leader order, which D9 never produces. There it is a trade-off,
not a clear win (8 equal seeds, cap off → on, line 50/40/30):

| Seeds better / worse | Cap off → on |
|---|---|
| sprint_away fades | 4 → 0 (seed 1); 1 better / 0 worse |
| worst underfoot | 2 better / **6 worse** (seed 2: 34 → 43, over the 36 floor on `walk_backward#2`) |
| worst line order | 4 / 4 |
| worst deep overlap | 4 / 4 |
| crowd 18/12/8, sprint fades | 0 better / **7 worse** (the leader itself falls behind a sprinting owner) |

Per the A/B rule (one tail shorter, another longer: report, don't ship), it stays opt-in. It's kept
for the record and in case a future leader rule stops putting the slowest walker in front.

## D9 direction: smallest first vs biggest first (8 equal seeds per config)

Per-seed counts are "smallest-first better on / worse on" out of 8 seeds.

**Lines (30/40/50 vs 50/40/30, cap off):** smallest first is clearly better.

| Metric | Small first, mean \| worst | Big first, mean \| worst | Seeds better / worse |
|---|---|---|---|
| sprint_away fades | 0 every seed | 4 (seed 1) | 1 / 0 |
| worst underfoot | 5.7 \| 22 | 9.3 \| 43 | 7 / 1 |
| worst deep overlap | 9.8 \| 28 | 16.9 \| 53 | 6 / 2 |
| worst line order % | 4.0 \| 20.2 | 5.7 \| 45.4 | 7 / 1 |
| lag (blocks) | 1.70 \| 3.01 | 2.00 \| 3.38 | — |
| all fades, every script | 0.46 \| 7 | 0.29 \| 4 | 1 / 5 |

Small-first's extra fades are on non-formation scripts (stairs). They are inside the identical
0.4 line's envelope (mean 0.57, worst 6).

**Crowds:** a trade-off. Not a clear reversal, so smallest first is kept.

| Metric | Slugs 8/12/18 vs 18/12/8 | 30/40/50 vs 50/40/30 |
|---|---|---|
| worst underfoot | small better 8 / 0 (worst 15 vs 19) | 5 / 2 |
| worst deep overlap | small **worse** 0 / 8 (worst 67 vs 37) | 1 / 7 (38 vs 36) |
| lag mean | 1.30 vs 2.24 | 1.67 vs 2.05 |
| sprint fades | ~20 a seed either way (existing crowd sprint exemption) | 0 vs up to 2 |

**Schools:** smallest first is equal or slightly better.

| Metric | 9/16/24 vs 24/16/9 | 25/40/60 vs 60/40/25 |
|---|---|---|
| runs outside the trio floor (of 184) | 3 vs 4 (3 / 2 seeds) | **10 vs 14** (4 / 2 seeds) |
| worst straggle s | 7.45 vs 11.2 (4 / 4) | 17.05 vs 18.5 (5 / 3) |
| worst body overlap % | 3.69 vs 3.40 | 3.63 vs 6.79 |
| worst impact b/s | 5.85 vs 5.07 (4 / 4) | 6.19 vs 5.11 (4 / 4) |

## MIXED_SPRINT_FADE: closed

Under D9 a line walks smallest first, which gave **0 sprint fades on all 8 seeds** (the identical
0.4 line also has 0).
- `MixedGroundInvariantTest`: `SPRINT_FADES` is removed, so lines allow no fades on any formation
  script.
- The old "line 50/40/30" config is now "line 50/40/30 by slot", grouped through `PartyGrouping`
  as the game does, so it walks 30/40/50.
- Crowds keep their pre-existing sprint exemption: tiny slugs can't keep up with a sprint in
  either order.

## Verification

- `:fishsim:test`: **470 tests, 431 passed, 0 failed, 39 skipped** (opt-in probes).
- **Fingerprints: bitwise.** `FingerprintProbe -PfpAll=1` 654/654 and `GroupFingerprintProbe`
  276/276 are identical to the `6db92f87` baseline, both with the cap forced on and at the final
  default (off).
- No fingerprint row has mixed members, so no row was expected to change. D9 changes behaviour only
  for mixed-size parties built through `PartyGrouping`. The probe's explicit-length runs show the
  effect.
- No Minecraft imports, Java 17, `FlockEngine` untouched.

## For 1c

- Use `Group.leaderSlot()` for the formation leader, not the lowest slot. Use `slots()` to map
  members to party slots: SCHOOL follower `i` is `slots()[i + 1]`, GROUND member `i` is `slots()[i]`.
- Use `lowestSlot()` (or list order) for where the group sits in the party.
- The party lead (slot 0) can now be a school follower. Anything the lead "owns" (fishing dock,
  view priority) must look up the body by slot, not assume it's the group leader.
- The group seed is the leader's member seed. A school led from slot 1 or 2 has a different seed
  from today's single-stack trio. That's expected.

## Open questions

1. **Crowd member overlap.** Smallest-first crowds stack more (slugs: worst deep overlap 67 vs 37,
   worse on 8 of 8 seeds), while being better underfoot and in lag. Accept, or give crowds their
   own order (e.g. biggest in the middle)? Not changed without the owner.
2. **The 0.1 pace clamp** makes every member under 10 cm the same walking speed, so a 6/8/9 cm
   group's leader is "slowest" only by tie. That's harmless, but it means size order among very
   small benthics is cosmetic.
