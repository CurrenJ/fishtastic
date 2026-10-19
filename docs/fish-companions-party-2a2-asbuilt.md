# Party Phase 2a′ as built: crowds with the biggest in the middle (for spec §14.1)

Fork 2a′. Worktree `D:/GitHub/fishtastic/.claude/worktrees/agent-a47b5047676c4a41b`. Base
`b06bbb25` + the 2a patch. `:fishsim` only. Uncommitted.

Owner request (2026-10-13): "Try the biggest in the middle" for crowds. Lines and schools keep
smallest first (D9).

## Outcome

**Not adopted: the default stays smallest first.** Biggest-in-the-middle fixes the crowd member
overlap that 2a reported, but it gives back the underfoot and lag gains smallest first had. That's
a trade-off, not a clear win, so per the A/B rule it ships behind a switch, off:
`CompanionTunables.crowdBiggestMiddle`.

## What "middle" means for a crowd

In `BenthicGroup`, a crowd's member 0 is its **centre anchor**: the crowd centre is that member's
position, and followers 1 and 2 stand either side of it (±right, set back 0.6 spacing). So the
middle of a crowd is the anchor.
- **A1 (the switch):** the biggest is the centre anchor, and the other two follow smallest first.
  Order 18/8/12 for an 8/12/18 party. Ties for biggest go to the lower slot, so identical members
  keep today's order.
- **A2:** A1 plus the 2a pace cap (`groupPaceCap=true`), so the big anchor walks at the slowest
  member's pace and D10 still holds.
- A pair has no middle and keeps smallest first. Lines are unaffected.

The other reading, a smallest leader with the biggest beside it in the middle of the row, would
need new crowd station geometry that shifts the crowd sideways off the owner's trail. It wasn't
built.

## Equal-ground A/B (8 seeds × every ground script, baseline temperament)

Each cell is the mean | worst of the per-seed value, then the seeds better / worse than smallest
first. Lower is better for every metric. `maxDeep`/`maxOverlap` are member-overlap ticks,
`maxUnderfoot` is ticks under the owner's feet, and `sumMeanLag` is the sum over scripts of the
followers' mean distance from the anchor.

**Slugs (8/12/18 cm)**

| Metric | Smallest first | Biggest first | A1 biggest middle | A2 biggest middle + cap |
|---|---|---|---|---|
| maxDeep | 46.8 \| 67 | 26.1 \| 37 (8/0) | 28.1 \| 45 (7/1) | 28.4 \| 39 (8/0) |
| maxOverlap | 137 \| 174 | 80.9 \| 109 (8/0) | 96.6 \| 146 (7/1) | 90.0 \| 137 (7/1) |
| maxUnderfoot | 7.1 \| 15 | 13.0 \| 19 (0/8) | 14.1 \| 18 (1/7) | 15.2 \| 27 (1/6) |
| sumMeanLag | 9.1 \| 10.3 | 15.6 \| 16.6 (0/8) | 15.5 \| 16.5 (0/8) | 11.4 \| 12.4 (0/8) |
| maxLag | 9.6 \| 10.2 | 11.0 \| 13.4 (0/8) | 10.5 \| 11.3 (1/7) | 10.1 \| 12.6 (3/5) |
| fades | 20.9 \| 22 | 20.1 \| 23 (4/3) | 20.5 \| 23 (3/1) | 24.0 \| 27 (0/8) |
| sprintFades | 20.0 \| 21 | 18.9 \| 20 (5/0) | 19.5 \| 20 (4/1) | 20.4 \| 21 (0/3) |

**Bigger crowd (30/40/50 cm)**

| Metric | Smallest first | Biggest first | A1 biggest middle | A2 biggest middle + cap |
|---|---|---|---|---|
| maxDeep | 27.0 \| 38 | 22.1 \| 36 (7/1) | 15.6 \| 21 (8/0) | 18.2 \| 24 (7/1) |
| maxOverlap | 68.5 \| 108 | 51.9 \| 72 (7/1) | 48.2 \| 63 (7/1) | 54.8 \| 71 (7/1) |
| maxUnderfoot | 21.8 \| 35 | 22.6 \| 31 (2/5) | 24.4 \| 36 (4/4) | 26.4 \| 31 (3/5) |
| sumMeanLag | 11.7 \| 13.2 | 14.3 \| 15.4 (0/8) | 14.4 \| 15.4 (0/8) | 12.8 \| 13.7 (0/8) |
| maxLag | 8.8 \| 13.5 | 11.7 \| 13.9 (0/8) | 11.6 \| 14.1 (1/7) | 9.3 \| 13.2 (2/6) |
| fades | 2.6 \| 5 | 2.9 \| 6 (3/4) | 2.8 \| 6 (4/2) | 1.6 \| 3 (5/2) |
| sprintFades | 0 \| 0 | 0.8 \| 2 (0/3) | 1.0 \| 4 (0/3) | 0.5 \| 2 (0/2) |

**Reading it:**
- **Overlap:** both readings fix it. On the bigger crowd, A1 is the best of every order (worst
  deep overlap 21 against 38). On slugs it is roughly biggest first's level (worst 45 against 67).
- **Underfoot:** worse for slugs on 7 of 8 seeds (A1) and 6 of 8 (A2). For the bigger crowd it's a
  tie on A1 (4/4) and worse on A2.
- **Lag:** worse on every seed for both families with A1. The cap (A2) recovers most of it but not
  all, and it adds fades: slugs worse on 8 of 8, while the bigger crowd is better on 5 of 8.
- **Sprint fades:** the bigger crowd goes from none to 1–4 on 3 seeds for A1.

So no reading keeps smallest first's underfoot and lag while fixing the overlap. The raw files
are `build/2a2/probe-capoff.txt` and `probe-capon.txt` (worktree), and the comparison script is
`build/2a2/cmp.py`.

## What changed

- `CompanionTunables.crowdBiggestMiddle` (default false), documented with the measurement.
- `PartyGrouping.group(party, tunables)`: a new overload. `group(party)` uses the defaults, so the
  existing API is unchanged. With the switch on, a three-member crowd moves its biggest member to
  the front (the centre anchor).
- `Group.leaderSlot()` doc: the leader is a crowd's biggest under the switch.
- `MixedGroupProbe`: two new configs (`crowd 18/8/12 bigmid`, `crowd 50/30/40 bigmid`) and per-seed
  `maxOverlap`, `sumMeanLag` and `maxLag` lines for every ground config.
- `PartyGroupingTest`: +1 test. It covers:
  - the biggest at the centre, with followers smallest first;
  - identical members unchanged;
  - a tie for biggest going to the lower slot;
  - pairs and lines unaffected;
  - the switch off by default, giving smallest first.

## Verification

- `:fishsim:test`: 471 tests, 432 passed, 0 failed, 39 skipped.
- **Fingerprints bitwise against post-2a:**
  - default: 930/930 (654 `FingerprintProbe -PfpAll=1` + 276 `GroupFingerprintProbe`);
  - switch forced on: 654/654 (`-PtweakA=crowdBiggestMiddle=true`). The fingerprint harness never
    groups through `PartyGrouping`, so the switch can't reach it.
- No Minecraft imports, Java 17, `FlockEngine` untouched.

## For 1c

No API change for the client: keep calling `PartyGrouping.group(members)`. The new overload only
matters if the switch is turned on.

## Open

- The crowd overlap is still there at the default. Another lever, not tried here: keep smallest
  first but widen the crowd's side stations or separation range for mixed sizes. Right now
  `spacing(0, i)` uses the anchor–follower mean length, so a small anchor packs big followers
  closer. That would target the overlap without changing who paces the crowd.
