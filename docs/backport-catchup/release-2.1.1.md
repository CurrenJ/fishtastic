# Fishtastic 2.1.1 release — 2026-10-18

Owner authorized all six files and selected version 2.1.1.

Changelog: Fixed Gelatin UI being treated as optional: all loaders now report it as a required dependency, and CurseForge installs it as a required dependency.

All three branch builds and all three publishCurseForge tasks passed. Six final jars were checked for CRC, exact version, bundled tutorial/simulation classes and required Gelatin UI >=1.0.37+Minecraft metadata. Fabric uses depends; NeoForge uses type=required; Forge uses mandatory=true. Every upload configuration was checked at execution time for the gelatin-ui requiredDependency relation. Post-upload hashes match the verified pre-upload artifacts.

| Minecraft | Loader | CurseForge file | SHA-256 |
|---|---|---|---|
| 26.1.2 | fabric | [9245776](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9245776) | `2ce3278167fd851b8d0ff551046f19a6c62903df7fc4bb945fc332b9f8eedd44` |
| 26.1.2 | neoforge | [9245777](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9245777) | `330a462d0e581180c497b74aa6bcb5a269aac1fad0a6b598e49b059c07fdf41d` |
| 1.21.1 | fabric | [9245780](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9245780) | `e4a4f33021d790e1b15a85f6a0a162f4e7857f29f93b74fecae2fa3ec8ffbb22` |
| 1.21.1 | neoforge | [9245782](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9245782) | `b9041150233d7584126f045659e18274b5172cef1af4593ae9e4659200f5acad` |
| 1.20.1 | fabric | [9245783](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9245783) | `681f0fca43b706bf854c7b877e09b738c4b418c4cbc0d1c70ea5ca1bd5e7cb57` |
| 1.20.1 | forge | [9245784](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/9245784) | `a7d7fcbeb6678ae68ee3fc15e4012715f521ba4787dc92b8f380b6e1a3c7dfb3` |

All six returned file IDs confirm successful submissions. Moderation and public availability are not independently verified. No retries, duplicate uploads, commits, pushes or profile deployment. Existing unrelated source ambient edits, documentation, and port runtime files were preserved. No parity marker changed.

Raw build/publish logs, runtime upload configurations, returned file IDs and hashes: `build/release-2.1.1/`. Source and both port worktrees contain the 2.1.1 version, dependency and changelog edits, ready for review.
