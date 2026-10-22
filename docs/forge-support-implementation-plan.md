# Forge support implementation reference

Forge support is shipped for the supported release lines. See [Forge support progress](forge-support-progress.md) for current versions and release state. This file is the short acceptance reference for future Forge changes; it does not record the completed implementation diary.

## Scope and working rules

| Minecraft line | Forge scope |
|---|---|
| 1.21.1 | Forge module alongside Fabric and NeoForge |
| 26.1.2 | Forge module alongside Fabric and NeoForge |
| 1.20.1 | Existing Forge support; retain its behavior |

Before work, read repository AGENTS.md and the progress page. Use the canonical modding guide and inspect the exact pinned Minecraft, Forge, Loom, Gradle, and dependency sources for the checkout. Do not assume NeoForge events or rendering APIs exist unchanged in Forge. Keep common and fishsim code loader-neutral and isolate Forge API use in Forge adapters.

Forge work has its own evidence under build/forge-support/. Keep that evidence and progress separate from the backport parity trackers. Do not store launch scripts, run configurations, save worlds, or runtime output in docs.

## Package gates

Complete and record the applicable gate before advancing. F00–F11 and G01–G03 are the package identifiers referenced by repository guidance.

| Gate | Acceptance |
|---|---|
| F00 Baseline | Pin source revisions, dirty state, dependencies, JVM and wrapper; inventory current tests and reserve isolated evidence paths. |
| F01 Toolchain | Establish a compatible toolchain and show existing loaders still build and launch. |
| F02 Forge artifact | Build minimal Forge modules for both target lines; prove development and installed client/server launches, transformations, access rules and mixins. |
| G01 Gelatin registration | Verify consumer menu registration order, server-opened menus and existing loader behavior. |
| G02 Gelatin rendering | Verify screens, tooltips, codecs and the version-specific high-resolution item path; keep client classes off dedicated servers. |
| G03 Local dependency | Resolve matching Gelatin artifacts from a clean local repository and verify metadata and launches; do not publish publicly as part of this gate. |
| F03 Registration and server | Verify static and datapack registries, holders, components, configs, commands, lifecycle hooks, join and reload synchronization. |
| F04 Transport | Verify typed payload registration, codec context, direction, thread, sender, validation, and every send path. |
| F05 Companion persistence | Verify party mutation, visibility, removal, tracking, dimension changes, death, reconnect, restart and the documented world-switch policy. |
| F06 Tank rendering | Prove a placed, held and inventory tank, including materials, lighting, cosmetics, updates and reload. |
| F07 World rendering | Prove ambient fish and companion rendering through the version-appropriate world render hooks and lifecycle. |
| F08 Client completion | Verify screens, input, effects, tutorials, particles, tanks, shop, quest book, encyclopedia and leaderboard. |
| F09 Test discovery | Confirm Forge testmod discovery, isolated worlds and repeatable focused GameTests without cross-loader leakage. |
| F10 Production parity | Run the relevant loader matrix and clean installed-jar client/server checks; retain fresh results for the tested source. |
| F11 Release readiness | Check final artifacts, hashes, metadata, dependencies and support claims. Release only when separately authorized. |

## Minimum evidence

For each affected version, retain the exact commands, source revision, toolchain, exit status and concise logs under build/forge-support/. Failed runs and interrupted operations remain unverified.

At minimum, cover:

- Dedicated-server class loading and a clean installed client/server launch.
- Static and datapack registrations, synchronization after join/reload, and invalid-reference handling.
- Packet routing with the correct registry context, game thread, sender, direction and input validation.
- Existing saves, companion state, reconnect and lifecycle transitions.
- Tank, world-fish and companion visuals, plus UI and item effects where affected.
- Test discovery, packaging contents, dependency metadata and regressions in the other supported loaders.

Use the repository's current Gradle tasks and tooling. Confirm task names against the pinned checkout; historical command examples and old run outputs are not fresh evidence.
