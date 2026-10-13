# Early retirement during short movement

Updated 2026-10-13 following the user's gameplay report.

The normal NeoForge client log at 11:43:38 recorded 218 domain-loss retirements, six invalid
placements, two open-face exits, zero exclusions and 19 expiries. The selected patches held
four and seven columns while the near scan reported 225 water columns; occupancy was zero.
At 11:43:52 those same tiny patches held one resident. Context responses were current, with
zero timeouts. These cumulative counters establish domain loss as the dominant retirement
path in the session; they do not identify the reason for every individual sighting.

Display selection used each patch's historical seed position, which can be far from water
beside the player. Small nearby fragments could displace an inhabited ocean. Merges selected
the numerically smallest packed key: a newer western fragment has a negative key and could
replace an established body's key and fixed coordinate frame.

Selection now uses the nearest cached water column, with occupied live patches retaining
their slots while water remains within ten horizontal blocks. Empty slots prefer nearest
water, then larger patches on equal distance. Rebuild and spawn scheduling use those same
water distances. Merges keep the oldest patch's key and frame, using creation instance as
the deterministic tie-break. Enable, density, context eligibility and discovery budgets
remain unchanged. Ordinary lifetimes are still 90–150 seconds; genuine exits and invalid
water can retire fish sooner. Merging two established patches can still retire the absorbed
patch's residents; this change prevents new fragments replacing an established population.

Regressions cover nearest-water selection with a distant seed, occupied-patch retention
across small steps and release beyond ten blocks, and westward merges across a packed-key
quantum boundary. The isolated `ambientwalk` scene populates an ocean, moves four blocks
out and back in one-block steps, then checks domain-loss counters and surviving identities.

Validation: fishsim 442 tests with zero failures (36 existing skips); common 859 tests with
zero failures/errors (one existing skip). The initial unrestricted test launch exhausted
Windows commit memory; the complete rerun used one worker, 64–384 MB test heaps and four
visible processors. Evidence: `fish-in-world-phase5/patch-continuity-tests-bounded.log`.

The isolated NeoForge movement scene passed all three checks plus shader loading. All 29
initial residents survived the four-block walk out and back; final occupancy was 32/32.
There were zero domain-loss, invalid, open-face, exclusion or expiry retirements throughout
that run. The patch retained its key while cached columns grew from 1225 to 1365. This is
a short continuity test, not a universal performance benchmark: session mean tick/submission
CPU was 0.908 / 0.157 ms, with cold peaks 91.771 / 26.287 ms, using a 1024 MB client heap
while the normal dev client remained open. Evidence: `fish-in-world-phase5/capture-patch-continuity.log`.
