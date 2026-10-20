# Fishopedia info-page redesign: implementation plan

> 2026-10-20. Implements the approved design: [README.md](README.md) (page),
> [bubbles/PLAN.md](bubbles/PLAN.md) (portrait), [gelatin-spike.md](gelatin-spike.md) (gelatin-ui rules).
> Branch `26.1.2` only. Built by Sonnet 5.5 agents in phases, each gated by the lead's verification.
> **Nothing is committed** without the owner asking.

## 0. Scope

**In:**
- the info page rewrite
- portrait bubbles + dithered plates + pause toggle (global pref) + click plume (with sound) + claim burst
- one-object disc ↔ portrait flight, prev/next browsing
- per-page Claim + disc Claim-all (new server packet)
- plain-word verdict
- temperament (Fight)
- coin ladder
- self-tests and unit tests

**Out:**
- the 1.21.1 / 1.20.1 backport branches (a later parity catch-up)
- the disc's own visual design (only the Claim-all button is added)
- renaming catch quality (owner: don't)

**Must keep working:**
- `FishEncyclopediaScreen.selectOnNextOpen` (Quest Log deep link, silhouettes included)
- the encyclopedia tutorial overlay (`instanceof FishEncyclopediaScreen`) + Dismiss hint button
- the JEI full-screen handler
- live sync refresh (`FishEncyclopediaSyncPacket` handler swap)
- Esc closes the page before the screen
- bond → `CompanionScreen.open`

## 1. Architecture

New package `grill24.fishtastic.client.encyclopedia` (client-only):

| Class | Role | Phase |
|---|---|---|
| `EncyclopediaWords` | Pure. `effectWord(mult, tier)` → GREAT/GOOD/SLIGHT/POOR/BAD; `spot(combined)` → GREAT/GOOD/FAIR/POOR. Bands per README §6 table. | 1A |
| `EnvSnapshot` | Client environment: zones at the player, biome holder, effective time of day (charm night override, moved here from the screen), weather, moon phase. `fromClient()`. | 1A |
| `CatchVerdict` | `compute(profile, habitsUnlocked, env)` → kind (CANT / CAN_LOCKED / GREAT / GOOD / FAIR / POOR), headline, helps/hurts factor names. The combined value uses `FishProfile.computeEnvironmentMultiplier`, so it matches the server. | 1A |
| `FishPageModel` | Immutable view model for one species. Name reveal; catches; milestones sorted by this fish's thresholds (ties in canonical order) with coins/reached/claimed; unclaimed coins; rarity (tier index, best-pool share → "~1 in N", pool label); size range; records; fight (temperament + mean difficulty); baits (derived from items with `BAIT_EFFECT` whose affinity/required group contains the fish); biome chips; time/weather/moon maps; lore; zones; theme zone = `zones().get(0)`; bond status. | 1A |
| `EncyclopediaCoins` | Coin-ladder icon for an amount: 1 coin / 2 coins / small pile 3–7 / medium 8–19 / large 20+ (no three-coin stack). | 1A |
| `network/ClaimAllEncyclopediaRewardsPacket` | C→S, `Optional<Identifier> fishId` (empty = whole book). The server claims every unlocked, unclaimed section through the existing claim path (tokens included), then sends **one** sync. Registered like `ClaimEncyclopediaRewardPacket` on both loaders. | 1A |
| `portrait/PortraitTheme` | 6 themes (plate texture, rate/rise multipliers, drift, specials: snow / embers / blobs, micro-bubble colours). | 1B |
| `portrait/PortraitBubbles` | Pure sim per bubbles/PLAN.md §2–4a: kinds, wobble, pops, plumes, welcome plume, warm start, caps 32/56, click cooldown 0.35 s, `click(x, y)` → boolean (fired), `claimBurst()`, `paused`, `reducedEffects`, seeded RNG, dt clamp 0.1 s. | 1B |
| `portrait/PortraitBackLayer`, `PortraitFrontLayer`, `PortraitToggle` | gelatin elements. Back: plate texture + back bubbles, scissored. Front: front bubbles + transparent click input (plume + sound). Toggle: 9×9, pause/play icon, tooltip "Pause/Play animation / Applies to every fish" wrapped at canvas scale k, flips `FishtasticClientConfig.encyclopediaPortraitAnimations`. | 1B |
| `FishEncyclopediaScreen` (rewrite of the info page; disc kept) | 416×232 `FreeformContainer` canvas at integer k. Header / left page / right page (tabs) / footer track + Claim. Icon layer + flight with scale compensation + landing snap. Prev/next slide. Disc Claim-all. Live verdict refresh (4×/s). | 2 |
| `RenderSelfTest` scenes | `portrait` (1B), the reworked `encyclopedia` (3) | 1B, 3 |

**gelatin-ui rules** (from the spike, mandatory):
1. `setTargetScale(k, false)` for any scale that must be exact (`scale()` animates).
2. The canvas and layers are `FreeformContainer`, with integer positions.
3. Portrait z-order: plate (back layer) < icon layer < front/input layer < toggle.
4. Tooltips: an unscaled wrapper around content at scale k, with its own background.
5. After `reparentTo`, set local scale = global-before ÷ new parent's global scale.
6. Snap the flight to the exact slot and integer scale at ~0.45 s, both ways.

## 2. Phases, owners, gates

| Phase | Owner | Runs | Owns (may edit) | Gate (verified by the lead) |
|---|---|---|---|---|
| **0** groundwork | lead | n/a | lang (all keys below), plate textures, config key, this plan | builds |
| **1A** model + rules + packet | Sonnet agent A | ∥ with 1B | `client/encyclopedia/*` except `portrait/`; `network/ClaimAllEncyclopediaRewardsPacket` + its registration sites; unit tests under `test/.../client/encyclopedia/` | `:common:compileJava`, `:common:test` green; both loaders compile |
| **1B** portrait | Sonnet agent B | ∥ with 1A | `client/encyclopedia/portrait/*`; `RenderSelfTest` (adds the `portrait` scene); unit tests under `test/.../client/encyclopedia/portrait/` | unit tests green; `portrait` self-test captures match the prototype GIFs; clip check exact |
| **2** page | Sonnet agent C | after 1A + 1B | `FishEncyclopediaScreen` (+ small helpers in `client/encyclopedia/page/` if needed); may add lang keys | compiles; the lead opens it in the self-test at 4 configs, compares with the mockups, and checks every "must keep" item |
| **3** verification scene | Sonnet agent D | after 2 | `RenderSelfTest` (`encyclopedia` scene rework), `EncyclopediaCanvasSpike` removal | all checks pass on **Fabric and NeoForge**; captures for the owner |
| **4** review | lead | after 3 | n/a | code review, full `./gradlew build`, report |

**Concurrency rules for agents:**
- Never edit a file outside your list.
- If compile errors appear *only* in files you don't own, the other agent is mid-edit: wait ~1 min and retry, never "fix" them.
- Only the agent that owns `RenderSelfTest` in a phase may launch the client.
- Never `git add` / commit / stash / reset.
- Clear the `fabric/run/fishtastic_render_selftest` marker after every client run.

## 3. Phase 0 deliverables (lead)

- `FishtasticClientConfig.isEncyclopediaPortraitAnimations()` / `setEncyclopediaPortraitAnimations(boolean)`, key `encyclopediaPortraitAnimations`, default `true`, documented in `DEFAULT_FILE_CONTENTS` (pattern: `shapeGalleryOpen`).
- `assets/fishtastic/textures/gui/encyclopedia/plate_<zone>.png` × 6, from `bubbles/plates/`.
- Lang keys (`en_us.json`), prefix `screen.fishtastic.encyclopedia.`: header, tabs, verdict, rows, effect words, milestones, claim, portrait toggle, tooltips, time/weather/moon/zone names, temperament names, biome-tag names (`fishtastic.biome_tag.<ns>.<path>`), pool labels. The exact list is appended at the end of `en_us.json` (hand-written; no datagen). Agents may add more keys at the end; never reformat the file.

## 4. Acceptance (owner-visible)

- Every species' page fits 426×240 with no scrolling, text crisp at GUI scales 1–4 (self-test fit + crispness checks).
- The fish flies disc ↔ portrait as one object at every GUI scale, with no size jump and snapping on arrival. Prev/next slides; close after browsing returns the shown fish to its own slot.
- Verdict and rows use plain words and update live as time, weather and position change.
- Claim (page) and Claim all (disc) pay the same tokens as the old per-section claims, in one sync.
- Portrait: themes, dither, bubbles, front depth, click plume + pop sound, claim burst (skipped with reduced effects), pause toggle remembered across restarts.
- Old behaviours in "must keep" all work.

## 5. Risks

| Risk | Mitigation |
|---|---|
| The page rewrite is one large file change | Phase 2 starts only after 1A/1B APIs are fixed; the lead reviews before phase 3 |
| Client-side odds drift from server maths | The verdict calls `FishProfile.computeEnvironmentMultiplier`, the same function the server uses |
| Claim-all double-awards | The server reuses `PlayerQuestState.claimEncyclopediaReward` (idempotent per section) and re-checks thresholds |
| NeoForge-only issues | Phase 3 runs the scene on both loaders |
| Parallel agents' builds interfere | Ownership rules + retry rule above; only one client launch at a time |
