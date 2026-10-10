# Fish Companions — Implementation Handoff

> **Read this first to resume.** Written 2026-10-10 at the end of Phase 2; updated after phases 3–4.
> Everything needed to start the next phase is here or linked from here. Branch: `26.1.2`. Nothing
> is pushed.

**To resume in a new session, say:**
> Read `docs/fish-companions-handoff.md` and continue the fish companions implementation from the
> next phase, one fork per phase, verifying each with the headless render self-tests.

---

## 1. Where things stand

| Phase | Status | Commit |
|---|---|---|
| 0. Spike: rendering, docking, sim reuse | ✅ Done, verified on both loaders | `76629f06` |
| 1. Encyclopedia thresholds derived from rarity | ✅ Done | `703a09b1` |
| — NeoForge create-world deadlock fix (blocked NeoForge testing) | ✅ Done | `9b203ace` |
| 2. Data and sync (per-player slot, bond/unequip, multi-player rendering) | ✅ Done, verified on both loaders | `787a4097` |
| **3. Display modes** | ✅ Done, verified on both loaders (see fish-companions.md §15) | `92626048` |
| **4. Bonding UI** | ✅ Done, verified on both loaders (see fish-companions.md §16) | `802470b9` |
| 5. Fishing reactions | ⏭ **Next** | — |
| 6. Pet-to-pet (v1: G1, G2) | Not started | — |
| Later: personality, world and player reactions; scenes G3–G6; signature companions | Not started | — |
| Planned improvements: tank interactions (E3, E4) | **Deferred by owner — do not build** | — |

The active session goal was "complete all phases using forks and verify using self render tests".
The owner paused it after Phase 2 to get this handoff; Phase 3 landed as `92626048` and Phase 4 as
`802470b9` (2026-10-10), one fork each, verified on both loaders. Next fork: Phase 5.

---

## 2. Source-of-truth documents

| File | What it is |
|---|---|
| [`fish-companions.md`](fish-companions.md) | **The spec.** §1–§12 design, §13 Phase 0 findings and pitfalls, §14 Phase 2 as built, §15 Phase 3 as built, §16 Phase 4 as built. Follow it. |
| [`fish-companion-ideas.md`](fish-companion-ideas.md) | Owner's yes/no answers to the original idea list (IDs A1–I4). Rejected items stay rejected. |
| [`fish-companion-review.md`](fish-companion-review.md) | Owner's yes/no answers to the two-reviewer pass (IDs R1.1–R8.3), with dev notes. |
| [`encyclopedia-tier-review.md`](encyclopedia-tier-review.md) | Phase 1 tier table per species. |

**Owner decisions that aren't obvious from the spec text:**
- Cosmetic only. Fishing reactions are the top priority (A1).
- Minimum drawn length while following is **0.25 blocks**; larger fish stay true to scale. Docked fish are a fixed screen size.
- The docked fish is a **real 3D perspective model drawn on top of the HUD** through the picture-in-picture renderer. The owner pushed back on "3D can't be on the HUD", so don't regress to flat GUI items.
- Docking happens in first **and** third person. The "wait at the bobber" setting defaults to **off**, i.e. the fish docks.
- Pet-to-pet scenes are **server-scheduled**, with a **per-companion cooldown only**. No per-pair history (R5.4 was rejected).
- The bond requires a **fully complete** encyclopedia entry. Thresholds are per species, never totalled across species, and capped at 50 catches.
- Unlisted fish (`#fishtastic:unlisted_fish`) are **quest-only** companions. The encyclopedia must name the unlocking quest.
- Reuse the fish sim's logic. Don't write new motion code where the sim already does it.
- **Rejected:** ghost phasing, triggering cosmetic reactions, "met species" stamps, a shop purchase, a curio slot, retiring to a tank.

---

## 3. Code map (as of Phase 2)

| Path | Role |
|---|---|
| `common/.../companion/CompanionStorage` | `@ExpectPlatform` get/set of the per-player companion `ItemStack` |
| `neoforge/.../companion/neoforge/CompanionStorageImpl` | `AttachmentType<ItemStack>`: `copyOnDeath`, synced to the owner and to trackers |
| `fabric/.../companion/fabric/CompanionStorageImpl` | `AttachmentRegistry`: persistent, `copyOnDeath`, `syncWith(all)`; empty means "not attached" |
| `common/.../companion/Companions` | Server operations: `bond` (full-component match in inventory, then in Piles of Fish), `unequip`, `equip` |
| `common/.../companion/CompanionBondGate` | **Phase 4 stub**: `isUnlocked` returns true |
| `common/.../network/BondCompanionPacket`, `UnequipCompanionPacket` | C2S requests |
| `common/.../client/companion/ClientCompanions` | One `ClientCompanion` per visible player with a companion; `addPhantom` is a dev hook for tests |
| `common/.../client/companion/ClientCompanion` | Follow box, dock blend, world submit, HUD submit (per-player version of the old spike) |
| `common/.../client/companion/CompanionPipRenderer`, `CompanionPipRenderState` | Perspective picture-in-picture renderer for the docked fish |
| `common/.../client/renderer/CompanionFlock` | Sim wrapper: planar group engine in a 3×2×3 follow box; single-tank binary engine when docked |
| `FishingMinigameAnimation.companionDockAnchor` | Dock lane anchor in GUI px (left of the gear column, below its icons) |
| `IGuiGraphicsExtension.fishtastic$addPictureInPicture` (+ `GuiGraphicsMixin`) | Queues the PiP draw (vanilla keeps `guiRenderState` private) |
| Loader hooks | NeoForge: `SubmitCustomGeometryEvent`, `RegisterPictureInPictureRenderersEvent`, client tick, `RenderGuiEvent.Post`. Fabric: `LevelRenderEvents.COLLECT_SUBMITS`, `PictureInPictureRendererRegistry`, `END_CLIENT_TICK`, HUD element |
| `command/CompanionCommand` | Dev-only: `/fishtastic companion on\|off\|give <player> <species> [size]\|minigame\|stopgame` |
| testmod `CompanionGameTests` | 9 gametests registered on both loaders; the death test uses a per-loader respawn hook |
| `client/selftest/RenderSelfTest` scene `companion` | Opt-in headless render scene: follow, dock, handoff, turn, third person, bond/unequip, phantom second companion |

---

## 4. Remaining phases: what to build

Each phase lists its scope, the spec sections, acceptance criteria, and notes from what's been learned so far.

### Phase 3: Display modes (spec §2, §3, §3.1, §11 settings)
- **All five classes:**
  - `FREE_SWIM` orbits.
  - `GLIDE` makes overhead arcs and a dust swoop.
  - `DRIFT` uses a spring lag, pulses, and glows at night.
  - `BENTHIC` **perches on the head or shoulder via a player render layer**, like vanilla's parrot.
  - `ANCHORED` pokes out of the back.
  - `animation.mode` refines each one.
- **Trios:** `FREE_SWIM` fish under ~20 cm come as three, reusing the sim's swarm offsets.
- **Water:** in water, drop the air anchor and use the sim's own locomotion.
- **Peeks are mandatory.** Phase 0 and Phase 2 screenshots on both loaders show the free-wandering fish **out of a level first-person view most of the time**.
  - Add periodic swim-into-view peeks.
  - Perchers hop off to show themselves.
  - Hop onto the hand, or just above the hotbar, when the player looks down. That's the hand pass, not the HUD.
  - Add held-item reactions (§3.1 table).
- **Settings:** hide other players' companions (default off); hide your own in first person (default off, ignored while docked); wait at the bobber (default off).
- **Acceptance:**
  - A "companion parade" self-test scene shows every class correctly.
  - In first person, the companion is visible to its owner at regular intervals: count frames in which it's on screen over a timed window.
- **Notes:**
  - `CompanionFlock.submit` currently gives non-swimmers the tank idle pose. The non-swimmer locomotions need open-world anchoring. Perches use the player layer, not the world hook (§11).
  - Respect vanilla's particle setting.

### Phase 4: Bonding UI (spec §8.2, §8.3, §8.4)
- A Companions tab in the encyclopedia (gelatin-ui). The picker lists only fish **carried**: inventory plus Pile of Fish contents. Rows collapse only when **every** component matches.
- The **send-a-description** packet already exists, and the server re-finds the fish.
- Fill in `CompanionBondGate`:
  - **Normal species:** a fully complete entry, checked with the server-side `EncyclopediaTiers` derivation.
  - **Unlisted:** a quest unlock.
  - **Persistence:** store unlocked bonds as a set per player so they **never re-lock** (R6.4). Put it next to `PlayerQuestState.claimedEncyclopediaRewards`, and use `optionalFieldOf` so old saves still load.
- **Status lines:**
  - "Bond: N/M catches", where M is the lore threshold.
  - "Bond unlocks via quest: <name>", with a link.
  - "Bond available" + Bond button / "Your companion" + Unequip button.
- **Naming:** use the item's custom name, plus a name field in the tab.
- **Discovery:** a first-unlock tutorial, then a banner for each new bond unlock.

### Phase 5: Fishing reactions (spec §5, §5.1, §5.2)
- **Casting:** cast, wait and nibble happen at the bobber in the world. Benthic fish ride the rod tip.
- **Docking:** already works through the PiP. Dock in the lanes beyond the gear and zone-icon columns: publish their outer edges and heights from `FishingMinigameAnimation`. Never cover the bobber zone or the fish target.
- **Bar-state reactions per class (§5.2).** Motion while docked is never idle and slows at times. How much it moves is tuned in game (R3.3 is still open).
- **Catch:** join the existing hero celebration, greet the hero fish on a same-species catch, and fly past toward the camera **only on tiered celebrations** (ended by the skip key). Otherwise give a quiet return.
- **Escape:** droop and return.
- **Server events:** others see the companion stay at the bobber in the world, with mood reactions. Catch outcome and escape need server→tracker events.

### Phase 6: Pet-to-pet, v1 = G1 and G2 (spec §10)
- **Scheduling:** the server schedules scenes, one small packet per scene to trackers. Each companion is in at most one scene at a time, with a per-companion cooldown and nothing saved. A greeting plays when two companions come into range and neither is on cooldown. No scene starts while either owner is in the minigame (the server knows). Use a fixed priority order.
- **G1 (same species):** synced loop plus a bubble heart.
- **G2 (two free swimmers):** figure-eight around both players.
- **Testing:** headless runs have one real player, so extend the phantom/fake-remote harness. It may need a dev-only fake server player, or a scene-level fake whose packets are injected.

### Later
- **Personality (§4):** dwell, habituation and cooldown on persistent triggers (R7.2).
- **World (§6):** moods based on the species' own zones and biomes.
- **Player (§7):** pet and feed, including through the emote key and the Companions tab (R2.3); the emote keybind.
- **Scenes G3–G6.**
- **Signature companions:** hidden quests for the six unlisted fish, with extra interactions.

---

## 5. How to work: proven method

**One fork per phase.** Phases share files, so don't run them in parallel in the same tree. The parent reviews the report, then commits.

**Every fork prompt must carry these rules.** Each one comes from a real incident.
- **Builds:**
  - Use `./gradlew :fabric:compileJava :neoforge:compileJava`, `./gradlew build`, `:common:test` and `:fabric:runGametest`.
  - **NeoForge `runGametest` finishes its tests, but the JVM never exits.** Don't wait on it. `compileTestmodJava` passing is acceptable evidence.
  - `runClient` builds whatever it loads. Never pipe a build into `tail` when you need its exit status.
- **Self-tests (`client/selftest/RenderSelfTest`):**
  - A run is armed by `<run dir>/fishtastic_render_selftest`, which contains scene names, one per line. It's opt-in: add `companion*` scenes to `OPT_IN_SCENES`.
  - **Delete the marker after every run, on both loaders.** It persists, and it will hijack the owner's playtest.
  - Screenshots land in `<run dir>/screenshots/selftest-<loader>-<scene>-<shot>.png`, and checks print `[selftest] CHECK name: PASS|FAIL`.
  - Run on Fabric while iterating, and run NeoForge **once** per phase at the end.
  - Review your own screenshots: downscale or crop with PIL into a temp dir outside the repo (`python -I`).
  - Log diagnostic state with `[selftest]` lines. Seeing numbers next to the pictures is what found every Phase 0 bug.
- **Hangs:** **don't relaunch a hung client repeatedly.**
  - Take one `jstack` (JDK at `C:/Users/curre/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin`; the client is the `TransformerRuntime` entry in `jps -l`), kill it, then report or fix.
  - The NeoForge create-world deadlock is fixed by warming codec classes (`WARMED_UP` in `FishAnimationConfig`, `ItemEffectCondition` and `CosmeticStructure`). If a jstack shows a new class in a deadlock, warm it the same way.
  - Useful script: [`C:/Users/curre/AppData/Local/Temp/spiketools/scene_run.sh`](file:///C:/Users/curre/AppData/Local/Temp/spiketools/scene_run.sh) `<runs> <scene>` runs NeoForge with a 90 s hang guard and automatic jstack. It's a temp file, so recreate it if it's gone.
- **Git:**
  - Forks never commit. Never `git add -A/-u`: tracked `common/logs/latest.log` gets swept in.
  - Stage by explicit path.
  - The pre-commit hook runs `:common:test` and `:fabric:runGametest` (~2–5 min). Run commits in the background.
  - Commits are signed via 1Password: **the owner must approve each prompt**. "1Password: failed to fill whole buffer" means it was locked or unapproved, so retry once the owner says.
- **Style:** match the surrounding code's comment density. Keep common vs platform split. Update `fish-companions.md` with a short "Phase N as built" section.

**Rendering pitfalls already solved (spec §13).** Don't rediscover them.
- GUI-pass feature submits **ignore the model-view stack**. Fold the view rotation into the PiP projection and keep the pose world-oriented, so lighting matches.
- A carry-over rebuild into a smaller sim domain **pins a fish that starts outside it**. Clamp it in and fade the offset.
- The planar engine in a thin box still turns nose-on to the camera. Use the single-tank model for side-on.
- The tank model's swim gate needs a nominal short sim length. The drawn size is separate.
- PiP rects are integer GUI px. The off-axis crop of the camera projection keeps sub-pixel placement exact.

---

## 6. Known gaps

- No live two-client test yet. Remote-player sync relies on the loaders' attachment sync contract. Phase 6 needs a real multi-player harness, or a manual check by the owner.
- View-bob-matched projection at the handoff is untested while walking. There was no visible pop when standing still.
- How much the docked fish moves (R3.3) is to be tuned in game by the owner.
- The owner has seen none of this in a real playthrough yet. Every verification so far is headless.
