# Backport feature ledger

The original pinned backlog through source snapshot `48fffdfb` is complete on both target branches.
The source and target commit-level accounting remains in
[source-commit-ledger.json](source-commit-ledger.json). Exact marker commits and outstanding owner
checks are in [backport-catchup-progress.md](../backport-catchup-progress.md).

This ledger preserves the feature inventory and dependency order without the completed stage logs,
test transcripts, or screenshot paths. Read the commit ledger when source provenance or an exact
target change is needed.

| ID | Stage | Feature | Status |
|---|---:|---|---|
| F01 | 2 | Rarity-derived encyclopedia tiers | Complete on 1.21.1 and 1.20.1 |
| F02 | 2 | Codec startup warmup | Included on both ports; the remaining create-world check is owner-only |
| F03 | 2 | Final headless simulation, safety, and companion minds | Complete on both ports |
| F04 | 2 | Shared fish visuals and pose extraction | Complete on both ports; tank-frame equivalence remains an owner check |
| F05 | 2 | Fishing-pool resolver and shared context | Complete on both ports |
| F06 | 2 | Fresh GameTest worlds and bounded fixtures | Complete on both ports |
| F07 | 3 | Companion storage, bonds, and unlocks | Complete on both ports |
| F08 | 3 | Party, inventory menu, and names | Complete on both ports |
| F09 | 4 | Companion movement and render adapters | Complete on both ports |
| F10 | 4 | Party grouping, perches, hand visits, and predator/prey behavior | Complete on both ports |
| F11 | 4 | Cross-player scenes and other-player clearance | Complete on both ports; real multiplayer beyond the scripted scene remains an owner check |
| F12 | 4 | Companion moods, habitats, and world perception | Complete on both ports |
| F13 | 5 | Ambient-fish server context, protocol, water, and population | Complete on both ports |
| F14 | 5 | Ambient-fish client simulation, rendering, and performance | Complete on both ports |
| F15 | 6 | Fishing, encyclopedia, and companion onboarding | Complete on both ports |
| F16 | 7 | Resource parity, packaging, full validation, and source accounting | Complete on both ports |

Do not infer owner approval of the six visual A/B switches from the implementation status; their
defaults remain enabled pending review. Do not advance a parity marker until all source changes are
accounted for, as described in the progress tracker.

## Post-marker feature follow-up (2026-10-29)

The source changes after marker `8794a541` are complete on both ports as of source `00df5ff9`:

| Source change | `port/1.21.1` | `port/1.20.1` |
|---|---|---|
| Hellbender wet/dry sheen | `50899c1e` | `55087776` |
| Ground-walker pitch hinge and benthic grounding | `50899c1e` | `55087776` |
| Legendary fish breach celebration | `50899c1e` | `55087776` |

The per-commit source-only exclusions and validation evidence are recorded in
[`forward-port-log.md`](forward-port-log.md) and the 2026-10-29 result matrix. The target-local
ported-through markers now name `00df5ff9`; manual owner checks remain open.
