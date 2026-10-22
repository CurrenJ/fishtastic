# Forge support progress

Forge support shipped in Fishtastic 2.1.2. The package-by-package implementation log and interim
status table have been removed; this page is the current release and continuation state.

## Current release state (2026-10-22)

- Release 2.1.2 completed for all eight loader/version artifacts and ten allow-listed profiles.
  The [release record](release-2.1.2.md) contains artifact hashes, file IDs, and publication evidence.
- The 1.21.1 and 1.20.1 working versions are 2.1.3. The owner instructed that 2.1.3 remain
  unpublished while further changes are added. The 26.1.2 line remains at 2.1.2 until that line
  next changes.
- No Forge-support implementation, publication, or deployment operation is currently in progress.
- Do not touch backport parity markers for Forge work; this support track has its own scope and
  evidence tree under `build/forge-support/`.

For future Forge changes, read the [implementation reference](forge-support-implementation-plan.md)
and repository `AGENTS.md` before editing. Re-run the relevant loader build, GameTests, dedicated
server/client checks, and installed-artifact verification for the changed surface. Treat ignored
`build/forge-support/` output as operation evidence, not as the current status record.
