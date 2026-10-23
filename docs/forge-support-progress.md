# Forge support continuation

Forge support shipped in Fishtastic 2.1.2 for 26.1.2 and 1.21.1 alongside Fabric
and NeoForge; 1.20.1 retains its existing Forge support. The
[implementation reference](forge-support-implementation-plan.md) defines acceptance
checks. No Forge operation is currently in progress.

## Release records

[Release 2.1.2](release-2.1.2.md) records artifact hashes, upload IDs and profile
deployment for that release. A local 2.1.3 release record reviewed during cleanup
reported successful submissions, without independently establishing moderation or
public visibility; that record is not retained in this checkout. The old instruction
to hold 2.1.3 unpublished is historical, not a current release instruction.
This checkout's working version is in `gradle.properties`; historical release
records are not a version registry for other branches.

For future changes, inspect the pinned checkout, repository guidance and exact
loader sources. Re-run affected build, GameTest, installed client/server and
packaging gates. Evidence goes under `build/forge-support/`; interrupted runs
remain unverified. Keep support evidence separate from backport parity accounting.
This cleanup does not advance any parity or acceptance marker.
