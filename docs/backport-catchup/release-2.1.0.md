# Fishtastic 2.1.0 release — 2026-10-18

Owner confirmed playtesting on every version and authorized publication.

Changelog: Added ambient fish, companions, and interactive fish tank cosmetic decorations

All three build runs and all three publishCurseForge runs exited 0. Six final release artifacts passed metadata, CRC, bundled-class and SHA-256 checks. Each publishing task submitted two Release files to project 1513269 using the exact changelog override, with Minecraft, loader, Java and client/server tags.

| Minecraft | Loader | SHA-256 |
|---|---|---|
| 26.1.2 | fabric | `52bd201326cc9b3fa09ecd7b725c990c85af1954a9521702a79d44cd7d1b07a3` |
| 26.1.2 | neoforge | `cd014bfad8407f7db4bf2667097c9305f4b6ff856d1001e1cff0304b493351a2` |
| 1.21.1 | fabric | `c803f8f59f075a76016b84a9a4cec695dc42e9fd4988a1feda62e4f570b74577` |
| 1.21.1 | neoforge | `1c20bfd97bfd307ab88fd827be9830e6586413964375a22eee8511752ec152e5` |
| 1.20.1 | fabric | `6d7e1dcd36368b9ad70321daa4e9c48e53e77eb77fe04deb915b31ccb393acef` |
| 1.20.1 | forge | `866e8d93ea693c01e6657efd2dbd60fc2feb15c37e91901030ce5d34d37415f4` |

Signed release commits (only gradle.properties and CHANGELOG.md):

- 26.1.2: `0b84e7e327e9317ebd77c6fb61d418bb33329cdf`; SSH signature independently verified.
- port/1.21.1: `e06344aa053db4f2886f3b0dc68dfb46241bcad0`; SSH signature independently verified.
- port/1.20.1: `e7ff95df387db09b63412a39a06eab4969488330`; SSH signature independently verified.

Required pre-commit unit and Fabric GameTest hooks passed on all three branches; no hooks/signing bypassed. No pushes. Unrelated local files preserved; staging empty.

Public verification limitation: default plugin logs omit INFO-level returned file IDs. All three publication tasks succeeded, but individual file IDs, moderation state and live public visibility could not be independently checked: public web output was cached and read-only network checks returned HTTP 403. No uploads were retried. Do not repeat publication to recover IDs.

Raw evidence: `build/release-2.1.0/` (artifact manifest, build/publish/commit logs, submission results and verified commit records).

[CurseForge files](https://www.curseforge.com/minecraft/mc-mods/fishtastic/files/all)
