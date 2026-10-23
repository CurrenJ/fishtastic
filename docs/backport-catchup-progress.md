# Backport parity progress

The original pinned catch-up backlog is complete on `port/1.21.1` and `port/1.20.1`. No backport
operation is currently in progress.

## Current parity state

- Pinned source backlog: complete through `48fffdfbda1687cd42d59cb9fea0540e77aec982`.
- Parity markers: source `fa103289`; 1.21.1 commit `9230042e`; 1.20.1 commit `68e2c77b`.
- The scoped cosmetic-placement follow-up is complete without advancing those markers. Source
  commits `83fd745d` and `933214c5` map to 1.21.1 commits `9481dc9b` and `f9097e39`, and 1.20.1
  commits `edf20f2c` and `d44d7ee0`.
- The completed follow-up passed focused compilation and Fabric GameTests. No push was made.
- The six A/B switches remain enabled at their current defaults pending owner visual review.

## Remaining owner checks

These are the outstanding manual parity checks recorded at the last checkpoint:

- Real-play feel.
- F04 tank-frame equivalence.
- Real multiplayer beyond the scripted scene.
- Loading an old world in `WAITING_FOR_CAST`.
- F02 create-world behavior.

The later cosmetic-placement work does not close these checks.

## Resuming parity work

Read the complete
[feature ledger](backport-catchup/features.md), and the
[source commit ledger](backport-catchup/source-commit-ledger.json). Follow the backport-specific
instructions in `AGENTS.md` and use the target branches' existing porting plans and the canonical
modding guide's topics 19 and 20.

Before a new operation, record its exact action and evidence path here. Record its result and next
action at each checkpoint and before ending the session. An interrupted operation is unverified.
Do not advance a ported-through marker until the complete pinned backlog is accounted for. Preserve
unrelated edits and target runtime files.

## Scope and safeguards

Carry final source behavior while preserving Minecraft/loader adaptations; account
for superseded intermediate changes rather than replaying them. Target branch
plans remain under `docs/backport-plan.md` and `docs/backport-pass2/` on those
branches. Canonical guide topics 19 and 20 cover backport APIs and workflow.
The visual switches `driftBob`, `bobRetime`, `bobMindTurn`, `bobCalm`,
`partySwimSeparation` and `otherPlayerClearance` retain their current defaults
until owner review. Publication/profile deployment are separate operations.
