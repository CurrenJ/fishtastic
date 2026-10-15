# Companion world feature costs

The production index is shared per client level. Discovery and block-change deltas share a strict **512 block reads per client tick** cap. Each water candidate includes the read above it in that cap. Owners and parties query the same index; querying never reads blocks. A fully classified, unchanged area costs zero indexed block reads while standing.

The W0 scanner is retained only as `CompanionAmbient.Sampler.sampleForCost`. It uses the original 13 x 7 x 13 scan, 20-tick interval and movement >3 Manhattan blocks, with cumulative actual-read and elapsed-nanosecond counters. The production companion has no sampler. This permits equal-ground A/B measurements from the same loaded level, terrain, tick and owner path in one binary rather than inferring performance from two different worlds.

The `companionworldcost` render self-test records old scanner versus index readings. Each timing is wall-clock CPU elapsed time measured using `System.nanoTime`; index time includes section discovery, palette queries, classification, delta handling and uncached feature lookup/filter/sort. The harness queries each distinct owner before collecting the tick cost. Feature queries are shared across party units and remain cached across unchanged standing ticks. Legacy time includes only scan work, matching what the old sampler did. Ambient/brightness and collision sampling are separate from index block-read counts.

## Measured (2026-10-15)

One `companionworldcost` run per loader: Fabric 21:08-21:21 and NeoForge 21:31-21:44, logs under `<loader>/build/companion-world-validation/logs/latest.log`. Each covers five terrains (superflat, village, lush cave, ocean shore and Nether fixtures), each in five scenarios. "Steady" is after the index's first discovery of the area; "discovery" is that first scan. Reads are deterministic. The microsecond figures are single samples, so treat them as indicative. The fixture coordinates were not logged. The table shows Fabric; NeoForge follows it.

| Scenario | Old reads/tick, mean / peak | Index steady reads, mean / peak | Old worst | Index steady worst |
|---|---|---|---|---|
| Standing | 59 / 1,183 | 0 / 0 in every terrain | 57-192 us | 15-103 us |
| Walking | 65 / 1,183 | 0 / 0 in three terrains; 83.5 / 511 in ocean shore and Nether | 61-198 us | 49-189 us |
| Sprinting | 83 / 1,183 | 0 / 0 in three terrains; 75 / 511 in ocean shore and Nether | 61-195 us | 49-264 us |
| Party of three | 177 / 3,549 | 0 / 0 | 164-197 us | 20-44 us |
| Phantom second owner | 118 / 2,366 | 0 / 0 | 111-169 us | 17-118 us |

The old scanner's reads are identical in every terrain: a fixed 1,183-block scan per owner every 20 ticks (or after a move of more than 3 blocks). Index discovery peaked at 511 reads in every case and took 64-472 us, apart from one 4.2 ms first scan in the lush cave.

- **Wins:** standing, parties and second owners cost zero reads once classified, and the index's worst ticks for parties and second owners are roughly 3-8x cheaper than the old scanner's. The old cost grew with each owner; the index is shared.
- **Budget:** the 512-read cap held everywhere.
- **No win:** walking and sprinting at the ocean shore and in the Nether, where moving keeps discovering sections. The mean reads are about the same as or above the old scanner's, with a lower peak, and the worst ticks are no better.
- **NeoForge** shows the same pattern. Steady index reads are 0 except when walking or sprinting at the ocean shore and in the Nether (83.2 and 64.6 mean, 511 peak). Index steady worst ticks were 22-143 us for parties and second owners, against 121-240 us for the old scanner. Discovery took 67-462 us, apart from a 1.3 ms lush-cave first scan.
- **Open:** the lush-cave first discovery is the slowest scan on both loaders (4.2 ms Fabric, 1.3 ms NeoForge; one sample each). Worth a look before tuning anything else.

Warmth parity uses the original cuboid (+/-6 X/Z, +/-3 Y), nearest block per 4-block cell and four nearest cell representatives. Candidates are fed to the cell map in the legacy X/Y/Z scan order. That keeps both the per-cell tie and the cell order on equal distances the same as the old scan: colliding `Long` keys iterate in insertion order before the stable distance sort. The first Fabric run showed this mattered: the index chose the mirror-image fourth cell until candidates were ordered. Water is stored as a compact 4096-bit section surface mask and exposed as at most 16 feature points per section, one per 4 x 4 column. A delta can therefore replace a removed representative without rereading the section.
