# Party Phase 2: an evidence review (2026-10-14)

Before Phase 2 of [`fish-companions-party.md`](fish-companions-party.md) starts, every §6–§8
design decision is checked against measurement. Two questions:
- What already works and should be kept, or built on?
- Which Phase 2 items fix a problem we can actually measure, and which were guesses?

Owner context: a same-species party using the trio formation looked good in game (2026-10-14).
That's the bar to beat, not a freeze.

**Outcome (owner, 2026-10-14):**
- **D13:** different-species free swimmers form a mixed shoal (candidate B).
- **D14:** no attention budget (candidate E).
- The re-scoped plan is folded into the spec's §6 and §9.

---

## 1. Method

`PartyBaselineProbe` (`:fishsim` test, opt-in) runs a mixed party **the way the game does today,
with no party layer**:
- each group is its own unit;
- each unit sees the other units' leaders as `COMPANION` points of interest, within 16 blocks
  (`ClientCompanions`);
- every unit is also run **solo** on the same script and seed, so each measure compares "in the
  party" with "alone".

To keep the step order from mattering, each unit sees the others as they were at the end of the
previous tick.

The run covers 8 parties × 30 owner scripts (the 23 swimmer scripts + 7 ground scripts) × 4 seeds
× 2 minutes = 120 runs per party, baseline temperament.

Measures:

| Measure | Meaning |
|---|---|
| Inter-unit drawn overlap % | Ticks where bodies of two **different** units are drawn through each other (CompanionRun's school body-overlap formula). The trio school's own figure is 0.33% (1b) |
| Within 0.25 b % | Ticks where two units' bodies are within 0.25 blocks |
| Perch clash | Ticks where two units ride the **same** perch |
| ≥2 / all 3 in view % | Attention crowding |
| Seen % party vs solo | Does the party starve a member of view time? |
| INSPECT % party vs solo | Do party-mates inspect each other? |
| Leash fades | Does the party cost follow quality? |

Reproduce:

```
./gradlew :fishsim:test --tests "grill24.fishsim.companion.PartyBaselineProbe" -PsimStdout -PsweepSeeds=4
  # -PtweakB=homes   party-wide perch homes (candidate A below)
  # -PsweepClass=<substring> one party; shoal() is the candidate-B run
```

---

## 2. Baseline: the party with no party layer

| Party | Overlap % mean \| worst | Within 0.25 b % | Perch clash runs (ticks) | All 3 in view % | INSPECT % party vs solo (per unit) | Seen % party vs solo (per unit) |
|---|---|---|---|---|---|---|
| 3 free swimmers 40/30/25 | **6.34 \| 44.4** (corridor) | 19.3 | 0 | 6.5 | 5.4–5.7 vs 1.2–1.3 | 22.8/25.1/24.2 vs 24.3/24.5/25.6 |
| school pair + free swimmer | 2.84 \| 36.0 (corridor) | 9.9 | 0 | — | 3.0–3.1 vs 1.3 | 24.0/25.6 vs 24.0/25.0 |
| school pair + crab | 0.03 \| 0.7 | 0.5 | 0 | — | 2.4–3.3 vs 0.4–1.3 | 23.5/22.7 vs 24.0/23.2 |
| crab + drifter + glider | 1.18 \| 8.2 | 2.2 | 0 | 3.2 | 4.9–9.5 vs 0.3–1.7 | 22.2/23.4/23.8 vs 22.9/24.3/22.9 |
| starfish pair + crab | **12.34 \| 68.6** | 19.9 | **57 (26,526)** | — | 2.0–2.1 vs 0.4 | ±0.2 |
| crab + octopus + slug | **18.39 \| 64.2** | 27.6 | **111 (43,840)** | 4.1 | 4.3–4.9 vs 0.3–0.4 | ±0.2 |
| 3 anchored | **96.69 \| 100** | 97.4 | **120 (830,569)** | 0.2 | 0 | ±0 |
| 2 drifters + glider | 2.16 \| 12.0 | 5.2 | 0 | 6.0 | 6.7–7.9 vs 1.3–1.7 | 22.4/**43.4**/22.2 vs 23.6/**48.7**/22.9 |

Leash fades were the same in the party as solo in every row (e.g. 343 vs 346 for three swimmers).

### What the numbers say

1. **Different classes mostly keep out of each other's way already.** Crab + drifter + glider
   overlaps 1.2%, and school + crab 0.03%. The class stations (GLIDE wide and high, DRIFT behind
   a shoulder, ground behind the feet) separate them with no party rule.
2. **Same-class units collide.** Three free swimmers overlap 6.3% (19× the trio school's 0.33%),
   and up to 44% in the corridor. Two different ground benthics overlap 12–18%.
3. **Perches clash hard:**
   - three anchored fish sit on top of each other on the back 97% of the time (all are homed on
     `BACK`);
   - different-species ground benthics share a perch in 48–93% of runs (homes are `seed % 3`,
     and solos wander between shoulders).
4. **Nobody is starved of view time.** Every member's seen % is within 1.5 points of solo. The
   one exception is a JET drifter next to a BELL drifter (43.4 vs 48.7).
5. **Party-mates do inspect each other**, at about 4–7× the solo INSPECT time. That's still only
   about 5–9% of the time, because curiosity already habituates (`Drives`: revisit ×0.6, fatigue
   per inspection).
6. **All three in view at once happens 3–6.5% of the time.** The worst runs reach 22–30%, on
   idle scripts.

---

## 3. The strongest parts we already have (keep and build on)

| Part | Evidence | What Phase 2 should do with it |
|---|---|---|
| **Group formations** (school, line, crowd) | Trio overlap 0.33%; mixed 9/16/24 0.27%; a **mixed-species 25/30/40 shoal 0.42%** (below); owner-accepted in game | Reuse it; don't rebuild it. It's the best anti-overlap mechanism we have |
| **Smallest-first leader (D9)** | 2a: line underfoot 22 vs 43, sprint fades 4 → 0 | Keep |
| **Class stations** | Mixed-class parties: 0.03–1.2% overlap with no party layer | Phase 2 needs **no** general station allocation |
| **BenthicGroup's fixed home perches** (`BenthicGroup.java:57`, `groupSlot`) | 0 shared perches in every group run (1b) | **Generalise it to the whole party** (candidate A). This is the allocator, already written |
| **Curiosity habituation** | Mutual INSPECT is bounded at about 5–9% with no new rule | Familiarity can be a small tuning step on top, not a new system |
| **Following quality is independent per unit** | Leash fades and CATCH_UP are unchanged (±2 points) in the party | No shared-pacing rule is needed between units |

---

## 4. Candidates for improvement, ranked by evidence

### A. Party-wide perch homes (strongest; measured)

Each percher gets its own home once, when the party is built. Anchored fish choose first (D5:
head, then the shoulders), then the rest take what's left. Formation members keep their own
homes. This is the same mechanism `BenthicGroup` already uses, extended across units.

A/B (`-PtweakB=homes`; nothing else changed, solo shoulder-wander left on):

| Party | Overlap % | Perch clash runs | Clash ticks |
|---|---|---|---|
| 3 anchored | 96.69 → **0.38** | 120 → **0** | 830,569 → **0** |
| starfish pair + crab | 12.34 → 4.78 | 57 → 34 | 26,526 → 6,009 (−77%) |
| crab + octopus + slug | 18.39 → 12.57 | 111 → 108 | 43,840 → 25,618 (−42%) |
| every other party | unchanged | 0 | 0 |

- **Fully solves anchored.** This also delivers D5 (anchored → head first).
- **Halves to quarters the ground clashes.** The rest come from transient moves: solo
  shoulder-wander (`PerchBody.wantedSlot`, which a formation already disables) and hand hops.
  These need a **lease**: a transient move takes a free perch or doesn't happen.
- **This replaces §6.2's per-tick priority allocator.** There are 4 perches for at most
  3 bodies, so homes never run out. The "perch full → fade at ground station" rule change is
  then only reachable during a transient lease, so the fade can just wait.

### B. Same-class swimmers: a mixed-species shoal vs a separation term

Three different free swimmers overlap 6.3% (worst 44%). Two fixes:

| | Mixed-species shoal (reuse `CompanionGroup`) | Separation steering term (§6.1) |
|---|---|---|
| Measured | **0.42% mean, 4.5% worst** (15× and 10× better); leader seen 24.8% (unchanged); worst straggle 10.5 s | Not built, no data |
| Builds on | The formation you accepted in game | New code inside the swim |
| Costs | One mind leads the shoal, so followers lose their own decisions (and the species temperament, if it differs). The preview rule "matching species group up" needs a second line, e.g. "mixed shoal" | Each fish keeps its own mind; risk of the "hop" and jitter the variance metrics already warned about |

**Recommendation:** let the owner compare both in the viewer, with the same seeds. The shoal has
the evidence, and separation has the personality argument. This is a design choice, not a tuning
one.

### C. Different-species ground benthics

They still overlap 4.8–12.6% after homes, on the ground. §6.1's "share the foot trail as one
queue" fits this. Its analogue of B, building a mixed-species `BenthicGroup` line, is **untested**.
Measure both before choosing; mixed lines of one species already measure well (1b/2a).

### D. Party-mate familiarity (§7): real, modest, cheap

INSPECT rises from about 1.3% to 5–9%, a measured but not constant cost. A lower novelty or a
pre-habituated fatigue for party-mates fixes it, and a solo can't change, because a solo has no
party-mates. Do it, but as tuning, not a system. Keep the one "greeting" inspect the spec likes.

### E. Attention budget (§6.3): weak evidence, don't build yet

Seen % isn't starved, and all three in view is 3–6.5% of the time. The worst idle runs (22–30%)
may be fine or may look busy; that's a question for the owner's eye, not a measured defect. The
spec's "beats and peeks" also lumps different things together: threat peeks should never be
throttled. **Demote it to "watch in the viewer"**, and build it only if the owner sees crowding.

### F. Dropped or deferred

- **General station allocation** for mixed classes: no evidence of need (§2.1). Keep stations
  only for the same-class cases in B and C.
- **Shared perception** (§6.4): purely a performance change, and unprofiled. Defer to
  performance work.
- **Lone anchored to the head** (D5): comes free with A, as the intended fingerprint change.

---

## 5. Spec defects to fix regardless

- **Name clash:** §6's sim class `CompanionParty` collides with the common storage record of
  the same name. Use `PartyLayer`.
- **§4 contradicts D9:** "the lead leads any group it's in" is wrong. The smallest fish leads,
  and the lead may be a follower.
- **Determinism:** members read each other's state from the previous tick only, as this probe
  does, so the step order can't change results.
- **Units, not bodies:** a school is one agent in game, so party rules (homes, separation) act on
  the unit. Followers keep their formation.

---

## 6. Proposed Phase 2, re-scoped by evidence

| Step | Content | Evidence | Fingerprints |
|---|---|---|---|
| 2b | Promote `PartyBaselineProbe` to the Phase 2 metric suite; viewer shows a multi-unit party | This review | Unchanged |
| 2c | Party-wide perch homes + transient leases (A) | 97% → 0.4% anchored overlap; −42 to −77% ground clashes | Intended ANCHORED change (D5), behind a switch |
| 2d | Familiarity tuning (D) | INSPECT 4–7× solo | Solo bitwise |
| 2e | Owner viewer A/B: mixed shoal vs separation (B), and foot-trail queue vs mixed line (C) | B: shoal measured; C: to measure | Solo and groups bitwise |
| — | Attention budget only if the owner sees crowding in 2e's viewer pass; shared perception deferred | — | — |

---

## 7. Caveats

- **Drawn overlap is a proxy.** Fish passing through each other briefly is natural; a school
  measures 0.33%. Judge it by its level against the school, and confirm in the viewer, not as a
  target to minimise.
- **Coverage is limited:** baseline temperament only, 4 seeds, generic class lengths and no real
  species profiles. A probe of the ground candidate C hasn't been run.
- **The homes A/B is a test hook**, set by reflection on `PerchBody.home`/`slot`, not a
  production change. Solo shoulder-wander was left on, so the residual clashes show what leases
  must handle.
- **The JET drifter's seen drop** (48.7 → 43.4) is the only starvation signal. Look at it in the
  viewer before deciding it matters.
