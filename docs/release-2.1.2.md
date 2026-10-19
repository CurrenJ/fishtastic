# Fishtastic 2.1.2 release — 2026-10-19

Owner authorized a patch release of the PR-review fixes and a full eight-artifact republish.

## Changelog

- Fish tank shape and material edits redraw immediately instead of leaving the old model on screen.
- Non-standard tank shapes, every connection layout and the corner/edge pieces render on Forge.
- Decorated tanks no longer reuse another tank's decoration mesh; tinted decorations keep colours.
- A reconnecting player's companion party reaches their own client and is announced to players
  already on the server.
- Forge no longer logs "Unknown custom packet payload" for Fishtastic messages.
- Celebration rays no longer draw over notification banners.

## Why every file was republished

Most live 2.1.1 files predated at least one shipped fix:

- 2.1.1 source revisions: Forge 26.1.2 `d0f9f03c`, Forge 1.21.1 `7ab5bbf7`; the six
  Fabric/NeoForge/Fabric-Forge files were built before the render-regression fixes.
- 1.20.1's published files predated both the celebration-depth fix and the new self-party sync fix.
- Only the 26.1.2 Fabric/NeoForge files were behaviourally unchanged; they were rebuilt from the
  same tree so 2.1.2 denotes one source state across the matrix.

## Builds

`mod_version = 2.1.2` on all three lines. Per branch: `:fishsim:jar` plus every platform `:build`.
All three builds succeeded (`build/release-2.1.2/*-build.log`).

All eight final jars passed CRC, version (`2.1.2+<mc>`), required-Gelatin metadata, and bundled
tutorial/simulation class checks (`build/release-2.1.2/verify.log`, `artifacts.json`).

Known, pre-existing packaging note: the 1.21.1 Fabric/NeoForge and 1.20.1 Fabric/Forge jars still
contain the same eight `client/selftest` entries they contained in 2.1.1. They are inert in
production. The 26.1.2 jars and the 1.21.1 Forge jar exclude them.

## Published files

| Minecraft | Loader | CurseForge file | SHA-256 |
|---|---|---|---|
| 26.1.2 | Fabric | [9261658](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261658) | `094465d646c0dfc3ae01d9f9315666b2ff2a557f383b161b308de60b89548461` |
| 26.1.2 | NeoForge | [9261659](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261659) | `bafb5e6c77e9de17a44467e4fe687c21946394756cc4c11fc63bd1504a75023e` |
| 26.1.2 | Forge | [9261661](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261661) | `8d3b2a369be2b06a443ecae58ae4f6714e2a8d6b00e8855053474b70f0184e89` |
| 1.21.1 | Fabric | [9261663](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261663) | `22125fe636fdb24cf793058567e64c045bd9643573ff616c77defe901b446d52` |
| 1.21.1 | NeoForge | [9261664](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261664) | `bb6d572c32ef1bc5cff8bb0cfee9b7b95d7e58c12ff8c4cbc80fc5a4df9a3fdd` |
| 1.21.1 | Forge | [9261668](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261668) | `76dad43aa3b81509fe66e7ac1f937930434b3fa9333ee0206a7298b6fc5e2a3e` |
| 1.20.1 | Fabric | [9261670](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261670) | `d80c933cb9f4cb1034fb92230d163eff2128355eac53560d0f24c48c0d5ec177` |
| 1.20.1 | Forge | [9261671](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9261671) | `f82004723c96ca11f670f8533258a0b3e367c07705ab389f1eeb1ec0f48b0af2` |

Every upload required CurseForge slug `gelatin-ui` (required dependency). Gelatin UI stays at
1.0.38; no dependency floor changed in this release.

Source commits: 26.1.2 `a0e97218`, 1.21.1 `1ef9b5ec`, 1.20.1 `779ff197`.

## Test-profile deployment

All ten allow-listed Fishtastic profiles were updated with `scripts/sync_jars.py --repo fishtastic
--skip-build`: F2ALPHA, F2ALPHA [N], F2 26.1.2 Fabric/NeoForge/Forge, F2 1.21.1
Fabric/NeoForge/Forge, F2 1.20.1 Fabric/Forge. Post-check: each profile has exactly one Fishtastic
2.1.2 jar whose SHA-256 matches the built artifact, plus one Gelatin UI 1.0.38 jar. Replaced jars
were backed up under `D:/Curse Forge/Minecraft/_profile_backups/`:
`20261019-234921` (26.1.2), `20261019-234925` (1.21.1), `20261019-234930` (1.20.1).

Returned file IDs confirm submission; moderation and public availability are not independently
verified. No upload retries or duplicate files were created.
