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

Final sim, fingerprint, client, performance and render results will be recorded after completion.

## Owner review

- [Species traits](fish-companions-habitats.md): derived from existing fields, with no new override block.
- Readability: inspect first-person, overlay-off moving captures before approving.
- Tanks (W9): remain deferred until the owner decides after the rest of the work.
