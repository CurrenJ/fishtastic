# Fishtastic 2.1.7 release — 2026-10-25

This release updates the three maintained Minecraft lines to one shared version and publishes all
eight supported loader artifacts as release files.

## Changelog

- Added interactive fish schools. Catchable members approach nearby bobbers and can be caught
  through the regular fishing minigame. Catching one removes it from the shared school.
- School fish keep their quality, shown by the animated mote trail; bite movement and effects have
  been polished.
- Added the coelacanth and one free quest-shop reroll per in-game day.
- Reworked the fish encyclopedia and quest book with improved small-window layouts, keyboard
  navigation, and narrator support. Tutorial help is optional and available from the pause menu.
- Cosmetic placement works on any grid cell with enough room and targets the tank sand so kelp
  does not obscure placement.

## Version commits

| Minecraft | Branch | Commit |
|---|---|---|
| 26.1.2 | `26.1.2` | `074659af` |
| 1.21.1 | `port/1.21.1` | `341e6453` |
| 1.20.1 | `port/1.20.1` | `73fe6fe6` |

All three commits set `mod_version = 2.1.7` and add the release changelog entry.

## Builds and artifact checks

The `:fishsim:jar` task and every supported platform `:build` task succeeded. Evidence is in
`build/release-2.1.7/*-build.log`. All eight release jars passed version metadata, required
Gelatin UI metadata, fish simulation class, and tutorial class checks. CRC32 and SHA-256 values:

| Minecraft | Loader | Jar | CRC32 | SHA-256 |
|---|---|---|---|---|
| 26.1.2 | Fabric | `fishtastic-fabric-2.1.7+26.1.2.jar` | `5b3650d6` | `c9f3d03ef2ebbade76fbdb8f596533797330e0f9f96c8ed158230cee9a95c8d3` |
| 26.1.2 | NeoForge | `fishtastic-neoforge-2.1.7+26.1.2.jar` | `fd401520` | `19c9fa39f2ced2ddfc7da26532ab88e5833e9ce3a197d00c68b4f3d6b75a8ef3` |
| 26.1.2 | Forge | `fishtastic-forge-2.1.7+26.1.2.jar` | `babab4a8` | `0b192a888175dd9757fce04b328e8ed0c33bd6dd70311af6d9c0751328be0965` |
| 1.21.1 | Fabric | `fishtastic-fabric-2.1.7+1.21.1.jar` | `f9ae9415` | `1231c5d0a796143670fc8afa4f0fe7e92c0ed91aeee219f05f46edbc9774b639` |
| 1.21.1 | NeoForge | `fishtastic-neoforge-2.1.7+1.21.1.jar` | `576eff6d` | `cc8e8a0a6ecec2ec4a0eeafe9ee3308e609119d220b6dba452ad7693358308d2` |
| 1.21.1 | Forge | `fishtastic-forge-2.1.7+1.21.1.jar` | `9904b7c1` | `4b3ef7f199d245f9d2d7a826a12a4441c25483df25a3a45d8f1af9eeca391a1b` |
| 1.20.1 | Fabric | `fishtastic-fabric-2.1.7+1.20.1.jar` | `34f943ba` | `bd24da1e646c0f3dcbc39894071a3302003d079bf8fe52dc995cf92c29387b10` |
| 1.20.1 | Forge | `fishtastic-forge-2.1.7+1.20.1.jar` | `4bc9a501` | `58d2947151fe91ec1906bf697406d3a1ac05a8056402d8b0ad947ec8294f20e2` |

### Source working-tree change during publication

After commit `074659af`, the source working tree changed the fish-school defaults to
`attemptIntervalTicks=6000` and `encounterChance=0.5`. The source publish task recompiled and
uploaded jars with those values. The 1.21.1 and 1.20.1 jars retain `24000` and `0.12`. The source
changes remain uncommitted; the three artifacts are therefore not behaviorally identical for
fish-school encounter frequency.

## CurseForge submissions

`publishCurseForge` completed successfully as a release on each branch: three artifacts for
26.1.2, three for 1.21.1, and two for 1.20.1. The task logs are in
`build/release-2.1.7/*-publish.log`. Each upload includes the required `gelatin-ui` dependency.

The Gradle task did not print CurseForge file IDs. The authenticated file-list API check returned
403, so file IDs and public availability were not independently verified. No upload was retried.
