# Companion world feature costs

The production index is shared per client level. Discovery and block-change deltas share a strict **512 block reads per client tick** cap. Each water candidate includes the read above it in that cap. Owners and parties query the same index; querying never reads blocks. A fully classified, unchanged area costs zero indexed block reads while standing.

The W0 scanner is retained only as `CompanionAmbient.Sampler.sampleForCost`. It uses the original 13 x 7 x 13 scan, 20-tick interval and movement >3 Manhattan blocks, with cumulative actual-read and elapsed-nanosecond counters. The production companion has no sampler. This permits equal-ground A/B measurements from the same loaded level, terrain, tick and owner path in one binary rather than inferring performance from two different worlds.

The `companionworldcost` render self-test records old scanner versus index readings. Each timing is wall-clock CPU elapsed time measured using `System.nanoTime`; index time includes section discovery, palette queries, classification, delta handling and uncached feature lookup/filter/sort. The harness queries each distinct owner before collecting the tick cost. Feature queries are shared across party units and remain cached across unchanged standing ticks. Legacy time includes only scan work, matching what the old sampler did. Ambient/brightness and collision sampling are separate from index block-read counts.

## Measurement status

Real client measurements have not yet been collected. No synthetic timing results are presented as in-game evidence. Required runs are standing, fixed walking/sprinting paths, party of three and a phantom second owner in superflat, a village, a lush cave, an ocean shore and the Nether, with exact fixture/seed coordinates recorded alongside output.

Warmth parity uses the original cuboid (+/-6 X/Z, +/-3 Y), nearest block per 4-block cell and four nearest cell representatives. Equal-distance representatives retain the original X/Y/Z scan tie order, and equal-distance cell ordering retains the same Long-key HashMap iteration and stable distance sort. Water is stored as a compact 4096-bit section surface mask and exposed as at most 16 feature points per section, one per 4 x 4 column. A delta can therefore replace a removed representative without rereading the section.
