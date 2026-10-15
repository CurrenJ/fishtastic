# Companion world implementation and validation

Work started 2026-10-15 against [the agreed phase 5 rework](fish-companions-world.md).
Owner gates (habitat review, reaction readability, and the final tank decision) are separate from automated checks.

## Reproduce

Use the Java 25 toolchain pinned in `gradle.properties`. On this host the Oracle `java` shim on PATH
hangs; invoking the pinned `bin/java.exe` with `-classpath gradle/wrapper/gradle-wrapper.jar
org.gradle.wrapper.GradleWrapperMain` works.

```
gradlew :common:test :fabric:compileJava :neoforge:compileJava :fabric:compileTestmodJava :neoforge:compileTestmodJava
gradlew :fishsim:test
gradlew :fishsim:test --tests '*FingerprintProbe*' -PsimStdout -PfpAll=1
gradlew :fabric:runClient -I docs/fish-companions-world-validation.init.gradle -PworldLoader=fabric -PworldScenes=companionworld,companionparticles,companionmoods,companionworldcost
gradlew :neoforge:runClient -I docs/fish-companions-world-validation.init.gradle -PworldLoader=neoforge -PworldScenes=companionworld,companionparticles,companionmoods,companionworldcost
```

PowerShell requires quoting the comma-containing `-PworldScenes=...` argument. The init script uses
`<loader>/build/companion-world-validation`, never the ordinary play directory. Remove the
`fishtastic_render_selftest` marker there after each run. Captures and client logs are runtime files
under those ignored build directories.

## Evidence

Initial integrated checks: common JUnit 858 passed, one skipped; both loaders' main and testmod
sources compiled, including identifier and Java 17 API guards. Log:
`build/companion-world-integrated-checks.log`.

**Client self-tests (2026-10-15).** The first full Fabric run (20:00) failed 4 of 115 checks. All four were real defects, now fixed:

- `companionworld.water.physical_contact`: a swimmer never entered the self-test's 1x1 ground-level pool. The pool is narrower than the sim pond, and two causes were confirmed headlessly:
  - The dip's inward swing put the target inside the far wall. `Notice` now pulls the swing back until the target, plus a 0.2-block margin, is in real water.
  - Below the rim the fish lost line of sight to the owner's chest, so `Mind` treated it as left behind and switched to `CATCH_UP`. While the fish keeps up with a dip (last pose `DIP`), that rule is waived inside a 4-block band.
  - `WorldFeatureTest.swimmersDipIntoTheInGameSelfTestPool` reproduces the exact fixture.
- `companionworld.glum.pose`: `CompanionAgent.poseFor` stripped a solo fish's own DROOP and GLEE through `Mood.memberPose`, so the client never drew them. It now returns a solo or leader fish's own pose unchanged. `WorldFeatureTest.aSoloFishShowsItsOwnDroopToTheClient` fails without the fix.
- `companionworldcost.warmth_lava_cells_exact` and `warmth_cached_delta_fallback`: the index filled its cell map in arbitrary order, so it broke equal-distance ties differently from the old scanner. Candidates are now fed in the legacy X/Y/Z scan order.

After the fixes:

- **Fabric:** 52/52 for `companionworld`, `companionparticles` and `companionmoods` (21:26). The 21:08 run had passed all of `companionworldcost` and every other check except glum, which was then fixed.
- **NeoForge:** 115/115 for all four scenes (21:31).
- **Sim:** the focused suite passes 55/55, with fingerprints bitwise equal to the pre-work baseline (1,038 rows) and all 25 guard-matrix cells passing.

The full suites, run after all fixes (21:45), are green. `:fishsim:test` ran 556 tests: 497 passed, 59 skipped (opt-in probes) and 0 failed. `:common:test` ran 859: 858 passed, 1 skipped and 0 failed.

The Fabric crash at 19:29 (`Failed to load class file for ...MoodScenes`) came from fishsim classes being rebuilt while a client was running. Don't run Gradle tests during a client run. Cost results are in [fish-companions-world-cost.md](fish-companions-world-cost.md).

## Owner review

- [Species traits](fish-companions-habitats.md): derived from existing fields, with no new override block. **Approved by the owner 2026-10-15 (W4 gate passed).**
- Readability: inspect first-person, overlay-off moving captures before approving.
- Tanks (W9): remain deferred until the owner decides after the rest of the work.
