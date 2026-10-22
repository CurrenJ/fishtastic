# Backport parity plan

The pinned source backlog through `48fffdfb` is complete on the existing `port/1.21.1` and
`port/1.20.1` branches. Their recorded parity marker is source `fa103289`; see
[backport-catchup-progress.md](backport-catchup-progress.md) for exact target commits and the
remaining owner checks. There is no active implementation stage.

## Scope and references

The catch-up carries the final source behavior to the existing ports while retaining necessary
Minecraft-version and loader adaptations. Superseded intermediate implementations are accounted for
in the feature and commit ledgers; they are not replayed.

For future parity work, use these as the durable references:

- [Feature ledger](backport-catchup/features.md): feature-by-feature scope, target status, validation,
  and deliberate version differences.
- [Source commit ledger](backport-catchup/source-commit-ledger.json): source provenance and
  changed-path accounting required before a marker can advance.
- [Progress tracker](backport-catchup-progress.md): current operation, evidence path, outcome, and
  next action.
- Target branch plans under `docs/backport-plan.md` and `docs/backport-pass2/`.
- The backport playbook and API references in `D:\GitHub\modding-guide`, topics 19 and 20.

Complete the full pinned backlog and account for all source changes before advancing a marker.
Keep the six switches at their current defaults until the owner reviews them: `driftBob`,
`bobRetime`, `bobMindTurn`, `bobCalm`, `partySwimSeparation`, and `otherPlayerClearance`.

Publication and profile deployment are separate from parity implementation.
