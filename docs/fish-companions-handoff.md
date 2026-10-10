# Fish Companions — Implementation Handoff

> **Read this first to resume.** Current as of 2026-10-10 (evening): **v1 complete** (phases 0–6),
> first-playtest polish shipped, and **playtest round 2 in flight** (the fixes land as §20 and a new
> commit; this doc will be reconciled when they do). Branch: `26.1.2`. Nothing is pushed.

**To resume in a new session, say:**
> Read `docs/fish-companions-handoff.md` and continue the fish companions work from the next item,
> one fork at a time, verifying each with the headless render self-tests.

---

## 1. Where things stand

| Item | Status | Commit |
|---|---|---|
| 0. Spike: rendering, docking, sim reuse | ✅ Done, both loaders | `76629f06` |
| 1. Encyclopedia thresholds derived from rarity | ✅ Done | `703a09b1` |
| — NeoForge create-world deadlock fix | ✅ Done | `9b203ace` |
| 2. Data and sync (slot, bond/unequip, multi-player rendering) | ✅ Done, both loaders | `787a4097` |
| 3. Display modes (+ peeks, held-item reactions, trios, water, settings) | ✅ Done, both loaders | `92626048` |
| 4. Bonding UI (tab, picker, real gate, unlocks, FTUE, banners) | ✅ Done, both loaders | `802470b9` |
| 5. Fishing reactions (line, dock, bar-state, catch/escape, others' view) | ✅ Done, both loaders | `ce429e94` |
| 6. Pet-to-pet v1: G1, G2 — **completes v1** (R8.1) | ✅ Done, both loaders | `c9756261` |
| Post-playtest polish: calm idle, view-decoupling, face-velocity, solo commands | ✅ Done, both loaders | `37406056` |
| **Playtest round 2**: motion jitter, tank-faithful facing (sim-rate cap), slowed pet-to-pet scenes, tab listing filter, picker scroll bug | ✅ Done — Fabric run 5 + NeoForge: 119/119 checks, 0 failures each, on the final tree (see fish-companions.md §20); parent records the hash | — |
| Later: personality (§4), world (§6) and player (§7) reactions; scenes G3–G6; signature companions | Not started | — |
| Tank interactions (E3, E4) | Deferred by owner — do not build | — |

**v1 = phases 0–5 plus scenes G1/G2** (R8.1). Everything was built fork-per-phase, verified
headlessly on Fabric + NeoForge, parent-reviewed and committed. The owner is now playtesting in
game and filing rounds of feedback — see §6 for how rounds are worked.

---

## 2. Source-of-truth documents

| File | What it is |
|---|---|
| [`fish-companions.md`](fish-companions.md) | **The spec + as-built log.** §1–§12 design; §13 Phase 0 findings/pitfalls; §14–§18 phases 2–6 as built; §19 post-v1 polish; §20 round 2 (pending). |
| [`fish-companion-ideas.md`](fish-companion-ideas.md) | Owner's yes/no answers to the original idea list (A1–I4). Rejected items stay rejected. |
| [`fish-companion-review.md`](fish-companion-review.md) | Owner's yes/no answers to the two-reviewer pass (R1.1–R8.3). |
| [`encyclopedia-tier-review.md`](encyclopedia-tier-review.md) | Phase 1 tier table per species. |

**Owner decisions that aren't obvious from the spec text:**
- Cosmetic only. Fishing reactions are the top priority (A1).
- Minimum drawn length while following is **0.25 blocks** (`ClientCompanion.MIN_FOLLOW_LENGTH`);
  larger fish true to scale. Docked fish are a fixed screen size.
- The docked fish is a **real 3D perspective model on the HUD** through the picture-in-picture
  renderer — never regress to flat GUI items.
- Docking in first **and** third person; "wait at the bobber" defaults **off** (it docks).
- Pet-to-pet scenes are **server-scheduled**, **per-companion cooldown only**, no per-pair history.
- The bond needs a **fully complete** encyclopedia entry; per species, never totalled, ≤50 catches.
- Unlisted fish are quest-only companions; the encyclopedia names the unlocking quest.
- Reuse the fish sim; only adapt what the open world forces.
- **Motion taste (playtest-derived):** calm baseline / lively reactions (no frantic idle bob);
  the fish is a **separate entity** — resting paths must not be locked to camera rotation; the
  fish must **face its world velocity** like the tank sim does; motion must be **smooth as butter**
  (round 2: kill the small high-frequency jitter). These are recorded as memory notes too.
- **Round 2 decision:** the Companions tab lists a species **only when the bond is unlocked and
  the player carries a fish of that species right now** (plus the equipped companion, so Unequip/
  rename stay reachable). Catch-progress lines therefore live in the main encyclopedia Fish tab.
- **Rejected:** ghost phasing, triggering cosmetic reactions, "met species" stamps, a shop
  purchase, a curio slot, retiring to a tank.

---

## 3. Code map (current)

**Server**
| Path | Role |
|---|---|
| `companion/CompanionStorage` (+ per-loader `*Impl`) | Per-player companion `ItemStack` attachment (NeoForge `AttachmentType`, Fabric `AttachmentRegistry`); `copyOnDeath`, synced. |
| `companion/Companions` | Operations: `bond` (full-component match in inventory, then Piles of Fish), `unequip`, `equip`, `rename`. Results `BONDED/NOT_A_FISH/LOCKED/NOT_CARRIED`. |
| `companion/CompanionBondGate` | Real gate: `refresh` then read the stored set. Server-side only. |
| `companion/CompanionBondUnlocks` | Derives new unlocks (complete entry via `EncyclopediaTiers`; quests carrying `unlocks_companion` completed/claimed) and stores them. |
| `companion/CompanionFishingOutcome` | Pure `NONE/CAUGHT/ESCAPED` from `(hadFishTarget, caughtFish)`, shared by both derivations. |
| `companion/CompanionSceneType` | G1/G2 + shared timing (duration/path ticks). |
| `server/CompanionSceneManager` | Schedules pet-to-pet scenes: range 6, per-companion busy+cooldown 1200t, priority = enum order, blocks on minigame or line-out; one packet to players ≤128 blocks. In-memory only. |
| `server/FishCatchSavedData`, `server/PlayerQuestState` | Unlock set (`unlocked_companion_species`, `optionalFieldOf`), banner queue, companion FTUE step. |
| `server/FishingMinigameManager` | Sessions; resolves outcomes and sends `CompanionFishingEventPacket` to nearby players. |
| `tutorial/CompanionTutorialManager/Step` | One-step FTUE: first unlock → INTRO; tab open → COMPLETE. |
| `data/Quest.unlocksCompanion`, `data/EncyclopediaTiers` | Gate inputs (quest field; server-side derived thresholds). |
| `network/` | `BondCompanionPacket`, `UnequipCompanionPacket`, `RenameCompanionPacket`, `CompanionFeedbackPacket`, `CompanionTutorialSync/Advance`, `CompanionScenePacket`, `CompanionFishingEventPacket`, `QuestSyncPacket` (carries the unlocked set + new-unlock delta). |
| `command/CompanionCommand` | Dev tree, see §7. **Never use `Minecraft.player` here** — forces `LocalPlayer` resolution at class verification and kills Fabric `runGametest`; client actions live in `ClientCompanions`. |
| `util/FishingMinigameAnimation` | Publishes `CompanionAnchors` (bar rect, gear/zone column edges+heights, bobber sprite, target + progress); `companionDockAnchor`; `devCatchTarget`. |
| `util/IFishingHookExtension` (+ `mixin/FishingHookMixin`) | `fishtastic$isBiting` (vanilla synced `DATA_BITING`) + dev `fishtastic$forceBite`. |

**Client**
| Path | Role |
|---|---|
| `client/companion/ClientCompanion` | One per visible companion: display-mode anchors on its own slow frame, peeks/tricks/held-item reactions/duck/hand hop, fishing states (line wait, nibble, dock blend, bar-state reactions, outcome, fly-past), pet-to-pet scene playback, visibility + facing metrics, dev seams. |
| `client/companion/ClientCompanions` | Manager per level; scene/fishing packet landing; dev actions (trick/peek/outcome/catch demo/scene/phantom) and the phantom harness. |
| `client/renderer/CompanionFlock` | The invisible sim box: FOLLOW (open box, class-sized), WATER, DOCKED (single-tank binary, side-on); trios; presentation clocks (`SWIM_CLOCK_RATE`, `IDLE_CLOCK_RATE`); world-velocity facing (`FACE_FADE_*`); pose dispatch through `FishAnimator`. |
| `client/renderer/CompanionFishPose` | Pose-only draw for perches (no engine). |
| `client/companion/CompanionPerchLayer` + `mixin/AvatarRendererMixin` + `mixin/accessor/LivingEntityRendererInvoker` | Player render layer for benthic/anchored perches (both loaders, mannequin-filtered). |
| `client/companion/CompanionPipRenderer/RenderState` + `util/IGuiGraphicsExtension` + `mixin/GuiGraphicsMixin` | Perspective PiP renderer for the docked fish. |
| `client/FishEncyclopediaScreen` (+ `client/CarriedFish`, `client/CompanionClientState`, `client/CompanionTutorialClientHandler`) | Companions tab, status lines, carried-fish picker, rename, FTUE side panel. |
| `client/QuestClientCache` + `QuestProgressNotification(Manager)` | Unlocked-set cache; `companion_bond/<species>` banners (NEW_SPECIES tier). |
| `client/FishtasticClientConfig` | The three settings (all default off) + dev-only test overrides. |
| Loader clients (Fabric/NeoForge) | Render submit hooks, PiP registration, client tick, HUD element, packet handler registration, disconnect resets. |
| `client/selftest/RenderSelfTest` | Opt-in scenes: `companion`, `companionparade`, `companionpeek`, `companionhand`, `companionbond`, `companionfishing`, `companionscenes`, `companionpolish` (+ round-2 checks). |

**Tests**: testmod gametests — `CompanionGameTests` (bond/pile/swap/death), `CompanionBondGameTests`
(gate, banners, fake description, rename), `CompanionSceneGameTests` (scheduler rules), 
`CompanionQuestUnlockGameTests`, `CarriedFishGameTests`, `PacketRoundTripGameTests` — **315/315 on
Fabric**; unit tests incl. `CompanionBondUnlocksTest`, `PlayerQuestStateCompanionBondsTest`,
`CompanionFishingOutcomeTest`.

---

## 4. What's verified — and what isn't

**Verified (headless, both loaders):** every phase and the polish — 315/315 gametests, and the full
opt-in companion scene suite (104 checks, 0 failures on Fabric and NeoForge for the polish run).
Key measured numbers: FP visibility 38–52% of ticks; wiggle tail 3.14/3.71 Hz raw → 1.72/2.04 Hz
presented; view-decoupling 90° flick moves the fish 1.5–3.2 blocks (old) → 0.1–0.3 (new);
face-dot mean 0.36 → 0.69–0.77 (**round 2 reworks this to tank-style strictness**).

**Not verified / open:**
- **No live two-client test anywhere.** Remote-player companion sync, perches on other players,
  scene playback across two real clients — all rest on the loaders' sync contract, packet
  round-trip tests and single-client injections. A manual two-player check is the honest next step.
- Every tuning constant awaits the owner's in-game judgment (§7).
- NeoForge+Sodium / other shaderpacks untested (the dock and hop use the world pass, which renders
  correctly under the dev client's Iris pack; the early hand-pass approach did not).
- Round 2 items are in flight as this doc is written.

---

## 5. What's next

1. **Finish playtest round 2** (in flight): jitter, facing rework, tab filter, picker scroll — §20.
2. **Owner in-game pass** over the round-2 build; tune constants (§7).
3. **Then the "Later" plan**, one fork each, in roughly this order (all still specced in
   [`fish-companions.md`](fish-companions.md) §4/§6/§7/§10):
   - Player-triggered interactions (§7): pet (sneak-right-click / emote key / tab), feed with bait,
     the emote keybind, sneak-to-show-name. (F1–F4; R2.3; the name field already exists.)
   - Personality (§4): dwell → habituation → cooldown on persistent triggers (R7.2).
   - World reactions (§6): biome/zone moods (R7.1), mob reactions, day/night/moon behaviour.
   - Scenes G3–G6 (§10) in the scheduler's priority order.
   - Signature companions (§8.3): the six unlisted fish's quests (author `unlocks_companion` in
     quest JSON — the UI path is built and only needs data) + one extra interaction each.
4. **Do not build** tank interactions (E3/E4) — deferred by owner.

---

## 6. How to work: proven method

**One fork per unit of work.** Files are shared, so never run two forks in the same tree at once.
The parent reviews each fork's report, then commits (stage by explicit path, commit in background).
Playtest rounds are worked the same way: a single fork prompt carrying every item, verified and
committed together.

**Every fork prompt must carry these rules.** Each one comes from a real incident.
- **Builds:** `./gradlew :fabric:compileJava :neoforge:compileJava`, `./gradlew build`,
  `:common:test`, `:fabric:runGametest`. **NeoForge `runGametest` never exits** — don't wait on it;
  `compileTestmodJava` is acceptable evidence there. `runClient` builds whatever it loads; never
  pipe a build into `tail` when you need its exit status.
- **Self-tests (`client/selftest/RenderSelfTest`):** armed by `<run dir>/fishtastic_render_selftest`
  (scene names, one per line; add new `companion*` scenes to `OPT_IN_SCENES`). **Delete the marker
  on both loaders after every run** — it persists and hijacks the owner's playtest. Screenshots →
  `<run dir>/screenshots/selftest-<loader>-<scene>-<shot>.png`; checks print
  `[selftest] CHECK name: PASS|FAIL`. Iterate on Fabric; one NeoForge run at the end. Review your
  own screenshots (PIL crops into a temp dir outside the repo, `python -I`) and pair them with
  `[selftest]` numbers — that is what found every bug so far.
- **Hangs:** one relaunch max, then one `jstack` (JDK
  `C:/Users/curre/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin`; client =
  `TransformerRuntime` in `jps -l`), kill, report. The NeoForge create-world deadlock is fixed by
  warming codec classes (`WARMED_UP` pattern); a new class in a deadlock gets warmed the same way.
  Helper: `C:/Users/curre/AppData/Local/Temp/spiketools/scene_run.sh` (`<runs> <scene>`, 90 s hang
  guard + jstack; temp file — recreate if gone).
- **Client-class trap:** never use `Minecraft.player` in `CompanionCommand` (LocalPlayer resolves at
  class verification; Fabric refuses client classes on a server → `runGametest` dies at command
  registration). Client actions live in `ClientCompanions`.
- **Backups:** a dev command that mutates persisted progression calls
  `FishCatchBackups.beforeDestructiveCommand` first.
- **Git:** forks never commit; never `git add -A/-u` (tracked `common/logs/latest.log`). The
  pre-commit hook runs `:common:test` + `:fabric:runGametest` (~1–3 min) — commit in background.
  Commits are **signed via 1Password; the owner approves each prompt**. Both "failed to fill whole
  buffer" and "agent returned an error" mean it was locked/unapproved — the commit is NOT made but
  the index survives; retry once the owner unlocks.
- **Empirical debugging:** prove root cause live before fixing; treat "this fix needs new plumbing"
  as a smell. Metrics are proxies: pair every variance/rate metric with a rate-of-change check
  (a "maximise smoothness" metric once shipped a visible hop).
- **Style:** match local comment density; keep the common vs platform split; write the as-built
  section in `fish-companions.md` and update this handoff in the same change.

**Rendering pitfalls already solved (spec §13/§15/§16).** Don't rediscover them: GUI-pass submits
ignore the model-view stack (fold view rotation into the PiP projection); carry-over domain rebuilds
clamp-and-fade; the planar engine turns nose-on in a thin box (single-tank model fixes it); the swim
gate needs a nominal short sim length; PiP rects are integer GUI px with an off-axis crop; the
perch layer frame's y=0 is the shoulder line; `VBox#scaleToWidth` upscales too (width anchor);
the minigame bar's bare constructor stages treasure targets (clear before a demo).

---

## 7. Dev commands and the tuning constants

**Solo-test command kit** (dev-only, single-player; `ON|OFF|GIVE` etc. also work in multiplayer):
`/fishtastic companion` — `on|off`, `give <player> <species> [size]`, `minigame|stopgame`,
`catchcount <player> <species> <count>`, `unlock <player> <species>`, `resetbonds <player>`,
`trick`, `peek`, `outcome caught|escaped`, `catchdemo` + `catch`, `scene g1|g2|stop`,
`phantom <species> [size]|off`, `hideothers|hideown|waitatobber <bool>`.

**Named constants the owner tunes in game:**
- Calm idle: `CompanionFlock.SWIM_CLOCK_RATE = 0.55`, `IDLE_CLOCK_RATE = 0.6`.
- Reaction rates (rad/tick): `ClientCompanion.CHEER_RATE 0.25`, `NERVOUS_RATE 0.9`,
  `DRIFT_PULSE_RATE 0.35`, `BENTHIC_BOUNCE_RATE 0.3`.
- Own frame (view-decoupling): `FRAME_TURN_STILL = 0.6`, `FRAME_TURN_MOVING = 4.0` °/tick.
- Facing: `FACE_FADE_LOW/HIGH = 0.005/0.05` blocks/tick (being reworked in round 2).
- Dock: lane from `FishingMinigameAnimation.companionDockAnchor`; `DOCK_FISH_LENGTH`, `DOCK_TICKS`;
  docked motion amounts `DOCK_BOB_LIMIT/DOCK_BOUNCE/DOCK_LEAN_OUT/DRIFT_SHRINK/GLIDE_*` (R3.3).
- Trio threshold `CompanionFlock.TRIO_MAX_CM = 20`; min drawn length `MIN_FOLLOW_LENGTH = 0.25`.
- Scenes: `CompanionSceneManager.RANGE = 6`, `COOLDOWN_TICKS = 1200`; per-scene
  `durationTicks`/`pathTicks` on `CompanionSceneType`; `HEART_TICK/HEART_SCALE/HEART_RISE`.
- Peek/trick schedules: `PEEK_*`, `TRICK_*` in `ClientCompanion`.

---

## 8. Known gaps

- **No live two-client test** (§4) — the single biggest unverified surface.
- The owner's in-game pass is ongoing; round 2 is being fixed as this is written, and more rounds
  can be expected — work them as one fork per round.
- All tuning constants are first-guess values pending the owner's play (§7).
- View-bob-matched projection at the dock handoff is still untested while walking (no pop observed
  standing still).
- The `companionpolish`/round-2 metrics are dev seams; don't let them leak into production paths
  beyond the documented dev switches.
