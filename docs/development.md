# Development

This branch targets Minecraft 26.1.2 and Java 25. Exact versions and loader inclusion
are in `gradle.properties`, `build.gradle` and `settings.gradle`; do not copy version
pins from historical plans. The root includes common, Fabric, NeoForge, Forge,
Minecraft-free fishsim, and standalone tank-shape generator/previewer modules.
Gelatin UI is required. Canonical shared modding patterns live in the sibling
`modding-guide` repository; exact pinned Minecraft/loader sources win over examples.

## Build and tests

```sh
./gradlew build --no-parallel
./gradlew :common:test :fishsim:test :tools:tank-shape-gen:test
./gradlew :fabric:runGametest
./gradlew :neoforge:runGametest
./gradlew :forge:runGametest
```

Windows uses `gradlew.bat`. Set JAVA_HOME to the intended JDK when launching the
wrapper; `org.gradle.java.home` chooses the build JVM. A personal JDK path may be
supplied in local Gradle configuration rather than copied to another machine.

Run multi-loader GameTests with `--no-parallel` to avoid the shared Architectury
agent-jar race. `gradle/gametest-world.gradle` selects a UUID universe under each
loader's `build/gametest-worlds/`. Success removes that universe; failure retains
it at the logged path. JUnit output is `<loader>/build/junit.xml`.

Backup GameTests use synchronous `BackupTestFixture`, fresh saved data and isolated
backup files, restoring original state on failure. Do not schedule delayed assertions
inside that fixture. Keep real ticker/timing coverage for compost. Backport bytecode
guards in `gradle/backport-guards.gradle` run through check/test wiring.

`scripts/git-hooks/pre-commit` runs unit and Fabric GameTests; NeoForge is deliberately
not run there because of recorded hangs. Enable with `git config core.hooksPath
scripts/git-hooks` (a checkout-relative path). A hook result does not validate the
whole loader/installed-artifact matrix.

## Resources: one merge workflow

```sh
./gradlew :fabric:runDatagen
python scripts/sync_generated_resources.py --dry-run
```

The successful datagen task finalizes with `:fabric:syncGeneratedResources`.
`scripts/run_datagen.bat` is a convenience wrapper for the same task and forwards
arguments. Failed datagen must not sync stale output. Direct sync needs existing
`fabric/src/main/generated/{assets,data}`. Python is `python` on Windows, `python3`
on POSIX. The merge does not require third-party Python packages.

Authority rules:

- Ordinary generated assets, tags, recipes and loot data merge into common. Existing
  generated counterparts are replaced, but destination-only files are never pruned.
- `assets/fishtastic/items/fish_tank.json` is a protected custom item-model definition.
  Datagen's generic stub must never overwrite it.
- Custom registries use runtime paths `data/<entry-namespace>/fishtastic/<registry>/`.
  The merge maps provider paths there. New entries are copied; existing authored
  registry entries win, preserving lore, effects, shelter metadata and hand-built art.
- To deliberately replace emitted entries after reviewing the provider and diff, use
  `python scripts/sync_generated_resources.py --overwrite-registry shop_entry` (or the
  relevant registry). This never deletes entries absent from generation. In particular,
  the cosmetic provider emits only arches/lamps, not every shipped structure.
- Remove renamed/obsolete files explicitly after checking IDs/references. Neither a
  successful generator nor a missing generated file proves authored data is obsolete.

The resource-copy tests run with `python scripts/test_sync_generated_resources.py`.
NeoForge's `:neoforge:runData` covers platform-specific generation such as test
structures. Checked-in models remain build inputs; removing them requires a proven
clean-checkout generation path across all loaders.

## Content and verification tools

[tools/README.md](../tools/README.md) lists maintained authoring/diagnostic entry points.
Keep editable art and deterministic generators. Previews, screenshots, worlds, logs,
benchmark CSVs and per-run evidence belong in ignored output, usually `build/`.
Never interpret an old passing log as a current test result. Preserve worlds and local
configurations when cleaning disk.
