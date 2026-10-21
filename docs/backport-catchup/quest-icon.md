# Quest log icon fallback — 2026-10-21

The default anglerfish in QuestLogScreen.objectiveConditionIcon is replaced by the existing empty 16x16 PageSurface. This surface starts with an empty draw-command list and draws only that list, so the fallback draws nothing. Existing species (including actual anglerfish objectives), cycling tag, weather, time and supported zone icons retain their behavior. Unknown zone art also falls through to the empty surface. Row positioning is unchanged.

Identical two-line source/comment changes are applied on:

| Branch | Worktree | Compilation |
|---|---|---|
| 26.1.2 | D:/GitHub/fishtastic | Passed, Java 25, 16s |
| port/1.21.1 | D:/GitHub/fishtastic-worktrees/port-1.21.1 | Passed, Java 21, 20s |
| port/1.20.1 | D:/GitHub/fishtastic-worktrees/mc-1.20.1 | Passed, Gradle Java 21 / compiler target Java 17, 16s |

Each :common:compileJava executed freshly; fishsim was up-to-date. Evidence under build/backport-catchup/quest-icon/: source-bat-compile.log, 1211-compile.log, 1201-compile-jdk21.log. Existing deprecation/unchecked warnings remain. Compilation plus source/render-path inspection verifies this small presentation change; no in-game visual test was performed.

The initial source sandbox launcher stalled at java -version and was stopped. Initial 1.20.1 attempts failed before compilation due to invalid historical Java paths and then Loom requiring JVM 21. Those logs are retained; the final Java 21 launch passed while preserving the branch's configured Java 17 target.

Port checklists record this scoped parity change. Overall parity markers, versions, releases and profiles are unchanged. Owner requested commits after verification; commit results are recorded in the progress tracker. No pushes requested.

Commit follow-up: required pre-commit unit checks and Fabric GameTests passed on all three branches (378 source; 372 per port). Signed commit creation failed on all three because the configured 1Password SSH signer returned errors. No commits were created. Retry signed commits after the owner unlocks the signer; do not bypass signing or hooks.

Retry succeeded on both ports with hooks enabled: port/1.21.1 aa60aaa9cb22fea4553dd20c907824f4c95f168b; port/1.20.1 3bd39c5347c396100c702ca2784a45244af397d4. Fresh Fabric GameTests passed 372/372 per port. Source signed commit follows with this report and the tracker; see Git history for its identity. No pushes.

Source retry also succeeded with required hook checks and 378/378 Fabric GameTests; all three SSH signatures independently verified. This source commit includes the final completion record. All requested implementation and backport work is committed; no pushes.
