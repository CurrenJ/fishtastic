# Fishing minigame audit

Static review recorded 2026-11-01. The source checkout was `26.1.2` at `b6595f24`; the maintained
backports were `port/1.21.1` at `2250cef8` and `port/1.20.1` at `f722c5f2`.

## Findings

### 1. Obsidian Fishing Rod gear is not read by the minigame client — fix in progress

The server accepts both rods through the `fishtastic:fishing_rods` tag, but
`FishingMinigameClientHandler` checks only `COPPER_FISHING_ROD` when it reads equipped bait, hook,
and charm. With the Obsidian rod, the client therefore omits the gear display and bait-affinity
modifiers even though the server generated the session with that rod's equipment. Fix the client
lookup through the shared fishing-rod tag and cover the Obsidian rod in the existing GameTest on all
three MC lines. Release validation is tracked in `backport-catchup-progress.md` under 2.1.15.

### 2. Completion uses whichever rod is held when results arrive — open

The server snapshots cast inputs, but completion re-reads the currently held rod and charm for bait
consumption, upgrade durability, and delivery behavior. A player who changes rods while the
minigame animation is active can have the replacement rod charged or its charm applied to the
earlier cast. Bind completion costs and effects to the cast's equipment before treating this as
fixed.

### 3. Fishing-manager weak keys are retained by their values — open

`FishingMinigameManager.INSTANCES` is a `WeakHashMap<ServerLevel, FishingMinigameManager>`, while
each value keeps a strong `ServerLevel` field. That reference cycle prevents weak keys from being
collected and can retain old levels across integrated-server world changes in one game process.
Remove managers on server shutdown or use an ownership scheme that does not point back to the key.

### 4. Catch results are client-trusted — design caveat

`handleMinigameComplete` accepts the client's reported indices when they belong to the active
session. A completion under 20 ticks is logged but still awarded. The manager labels this behavior
`TRUST-BASED`; keep it as-is when client trust is the intended policy, and revisit it if multiplayer
anti-cheat becomes a requirement.

## Validation record

No tests were run during the initial static audit. The Obsidian rod fix, regression assertion,
version commits, full builds, release smoke results, and any CurseForge publication outcome are
recorded in `docs/backport-catchup-progress.md`.
