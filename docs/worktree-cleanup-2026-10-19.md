# Worktree cleanup — 2026-10-19

Owner approved preserving unfinished work separately, recording released dependency
metadata and durable notes, and removing obsolete checkouts. No port parity or
acceptance marker is advanced by this cleanup.

## Retained work

- Fishtastic active checkouts: 26.1.2 (`codex/forge-26.1.2`), 1.21.1
  (`codex/forge-1.21.1`), and 1.20.1 (`port/1.20.1`), all version 2.1.1.
- Gelatin UI active checkouts: 26.1.2 (`codex/forge-26.1.2`), 1.21.1
  (`codex/forge-1.21.1`), and 1.20.1 (`mc/1.20.1`), all version 1.0.38.
- `codex/ambient-debug-filming`: the unfinished ambient debug spawn/clear commands,
  runtime support and `docs/fish-in-world.md` changes, separated from the release
  branch. Snapshot parent: `03fd54b75b24a57c36979d8dd6e44c06f369e990`.
- `codex/companion-squid-jet-candidate`: the separate squid candidate, retained at
  `C:/Users/curre/.codex/worktrees/companion-squid/fishtastic`. Snapshot parent:
  `8c99327cb051875dbf84d761f98ea4abc38ff252`. The candidate remains pending owner acceptance. This cleanup does not merge or approve it.

The two feature branches and the recovery branches below are signed, single-parent
**recovery snapshots**, including untracked source files. They deliberately capture
unfinished work without running release acceptance checks. They were created using
an alternate Git index, leaving original staging state undisturbed until archival.
They are not ready-to-release feature commits.

## Archived agent experiments

All 15 old agent checkout bases were ancestors of the current 26.1.2 release branch.
Their dirty changes were not assumed redundant: each full nonignored working tree
was captured on a recovery branch before its checkout was removed.

Each ID below maps to `codex/recovery/2026-10-19/<ID>`:

| Experiment | Agent IDs |
| --- | --- |
| Drift / jet | `agent-a067360c3459c7010`, `agent-a8431d790a2f79b95`, `agent-ade33160daf81c512`, `agent-ae312f1a58c82c0f5` |
| Benthic movement / performance | `agent-a08898232d0db3098`, `agent-aaab1c471b961a8ac`, `agent-aaf4f7cf536eaeb48`, `agent-a13ae92a64bc41c84`, `agent-a1900010bb3d9b749`, `agent-a56df7c46afe3d887` |
| Companion party | `agent-a310606aaf814cdb4`, `agent-a47b5047676c4a41b`, `agent-ab3925cd07dc281da`, `agent-acd3a5afecb020a61`, `agent-ae2f7a3f6e212e0fb` |

The original agent branch refs are retained as well. The clean rendering spike
checkout was removed, retaining `spike/1.21.1-rendering` at
`362b525589d6ef53d8456d54253ba5a6e5a93a35`.

## Local recovery files

The repository's common Git directory contains
`cleanup-archives/2026-10-19/`, with:

- `recovery-snapshots.json`: exact base, snapshot commit and tree IDs for each branch.
- Per-experiment staged and unstaged binary patches, original status and ignored-file inventory.
- ZIP archives of ignored local files where present, including configuration and runtime
  files. Rebuildable `build/`, `.gradle/`, `node_modules/`, and
  `.architectury-transformer/` contents were excluded.
- Before/after inventory and validation evidence. Private local configuration stays
  outside tracked documentation.

For this checkout the directory is
`D:/GitHub/fishtastic/.git/cleanup-archives/2026-10-19/`.
It survives Gradle cleaning but is local: remote pushes of source branches do not
back up these private archives. No existing stash was dropped or rewritten
(29 Fishtastic, two Gelatin UI).

To resume an experiment, create a worktree from its named branch, for example:

```powershell
git worktree add D:/GitHub/fishtastic-worktrees/recovered-drift codex/recovery/2026-10-19/agent-a067360c3459c7010
```

The snapshot is the combined working state; the saved staged/unstaged patches
record the earlier staging split. Do not apply those patches on top of the snapshot
as if they were additional changes.

## Release and hygiene changes

Record the previously released required-Gelatin metadata and CurseForge dependency
settings on each Fishtastic version branch. Preserve the current backport progress and ledgers, research, ideas and technical references without changing historical claims.
Untrack generated port `common/logs/latest.log` files while keeping them on disk;
ignore generated logs, local agent tooling and scratch files. Apply matching local
tooling ignore rules to the three Gelatin UI checkouts. Preserve all local configs.

Normal release/documentation cleanup commits use the existing signing configuration
and pre-commit hooks. Recovery branches are local; nothing is published by cleanup.
