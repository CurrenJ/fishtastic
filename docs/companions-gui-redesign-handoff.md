# Companion GUI redesign and GUI Workbench handoff

Date: 2026-10-12. Workspace: `D:\GitHub\fishtastic`. No commits or pushes were made during this session.

## User intent and decisions

The user wants to discard the old companion GUI and make companions easier to discover, equip, switch, return, and rename. We reviewed two entry points: a Bond/Choose fish action on each encyclopedia entry, and holding a configurable bond key over an eligible inventory fish. The agreed direction combines both, removes the separate companion catalogue tab, and keeps a compact Your companion management entry point.

The user then specified a vanilla-style inventory GUI: grey panel, normal inventory/hotbar slots, and a place for bonded fish. It should open from an encyclopedia Bond button and also from the bond shortcut when managing an already active companion. Players can unequip from that screen. The current mechanics equip one physical item, so the new screen has **one companion slot**. A visual trio still represents one item.

For texture creation, the user first requested scripts with the furnace GUI as a stylistic starting point, then expanded the request into an extremely versatile web-based tool for generating parameterized Minecraft GUIs. They explicitly require that **no Mojang assets be committed alongside the tool**.

The latest instruction was to wrap up and write this handoff. Do not treat the companion implementation as fully runtime-verified.

## Completed: reusable GUI Workbench

Portable deliverable: `tools/mc-gui-designer.html`. Open this file directly in a browser; it contains its own styles and scripts and needs no server. Development source is under `tools/mc-gui-designer/`:

- `index.html`, `style.css`, `app.mjs`: editor UI, state management, interaction, and exports.
- `renderer.mjs`: shared dependency-free pixel renderer, presets, schema validation, slot coordinates, and Java scaffolding.
- `export.mjs`: dependency-free Node PNG writer using the shared renderer.
- `build.mjs`: regenerates the portable standalone HTML.
- `serve.mjs`: loopback-only development server with explicit JavaScript module MIME types.
- `renderer.test.mjs`: eight meaningful tests, all passing.
- `companions.json`: saved procedural design used to generate the companion screen texture.
- `README.md`: usage, exports, asset provenance, and regeneration instructions.

Features implemented:

- Furnace, crafting, hopper, chest, and companion presets.
- Panels with adjustable bevels and corners; normal/large slots; configurable slot grids.
- Buttons with normal/pressed/disabled states, rectangles, separators, arrows, flames, hearts, progress bars, labels, and imported PNG sprites.
- Exact position/dimension controls; drag, resize, snap, nudge, alignment, duplication, ordering, visibility, movement locking, and undo/redo.
- Grid row/column counts, cell size, spacing, and first container index.
- Custom palette; progress values/directions; sprite nearest-neighbor scaling and nine-slice borders.
- Transparent atlas, 16–2048-pixel atlas dimensions, pixel-grid overlay, and preview zoom.
- Browser-local persistence; complete JSON save/load; PNG export; layout metadata and Java slot-position scaffolding.
- Labels are preview guides unless explicitly baked into the exported texture. The guide font is an original small bitmap alphabet, not Minecraft's font.

Browser verification performed on the development source:

- Page loaded and rendered successfully with the explicit-MIME Node server.
- Furnace preset visually inspected.
- Selected Player inventory, changed the column count, aligned it to the panel, and verified generated Java positions changed accordingly.
- A screenshot was viewed during QA. No final screenshot is required to use the tool.

Not yet checked end-to-end: the portable HTML in a browser, browser download/save/import flows, pointer resizing across all element types, small-screen layout, and large imported sprite performance. Renderer-level tests cover pixel output, validation, sprite transparency/nine-slicing, and PNG encoding.

## Asset provenance: no Mojang assets

**Confirmed for this session's additions:** no Mojang PNG, font file, extracted jar asset, or other bundled game asset was copied into the repository. The presets draw original procedural shapes using familiar vanilla GUI conventions. No furnace texture was extracted or copied. The tool does not fetch assets from Minecraft or a remote source.

`common/src/main/resources/assets/fishtastic/textures/gui/companions.png` is generated entirely from the procedural elements in `tools/mc-gui-designer/companions.json`. The shipped preset has no sprite layers or embedded imported pixels. One renderer test explicitly checks that all shipped presets and the saved companion design contain no imported sprite elements.

The optional PNG import feature embeds user-imported pixels in saved JSON. It does not upload them or automatically add files to Git. Future work must not commit Mojang sprites, or JSON containing those pixels. An automatic renderer check only proves that our shipped procedural designs contain no imports; it cannot establish ownership of arbitrary future user imports.

## Implemented: companion GUI replacement

New files:

- `common/src/main/java/grill24/fishtastic/menu/CompanionMenu.java`: real vanilla `AbstractContainerMenu`, one persistent companion slot and 36 player inventory/hotbar slots.
- `common/src/main/java/grill24/fishtastic/client/CompanionScreen.java`: vanilla `AbstractContainerScreen`, generated grey texture, rename and Return to inventory controls, and a paginated strip for selecting fish inside Piles of Fish.
- `common/src/main/java/grill24/fishtastic/client/CompanionBondInput.java`: physical-key polling for the inventory shortcut and live tooltip hold meter.
- `common/src/main/java/grill24/fishtastic/network/OpenCompanionMenuPacket.java`: server opens the menu; optional species context is a highlight only.
- `common/src/main/resources/assets/fishtastic/textures/gui/companions.png`: generated 256×256 atlas containing the 176×202 panel.

Existing files modified:

- `FishtasticMenuTypes`, `FishtasticClientSetup`, and both loader client entry points register the new menu, screen, and keybind.
- `FishtasticPackets` registers the new open-menu payload.
- `FishEncyclopediaScreen`: removed the old companion tab and modal picker. Your companion opens the new menu; each species entry shows its bond state and a Choose fish action when unlocked.
- `SilhouetteItemButton`: adds a small heart badge for an unlocked species whose fish is currently carried. The heart is drawn from our generated companion atlas.
- `FishtasticKeyBinds`: B by default. Press B in the world to open management. In inventory, hold it over an eligible loose fish for ~800 ms.
- `AbstractContainerScreenMixin`: inventory-only tooltip addition and hold-input polling using the actual screen offsets, including recipe-book shifts.
- `Companions`: added a menu persistence setter and an inventory-room check before bond replacement, avoiding incidental drops of the previous companion on a full inventory.
- `CompanionFeedbackPacket`: added INVENTORY_FULL as an appended outcome and wire mapping.
- `en_us.json`: new screen/keybind/tooltip strings and updated first-use tutorial text.
- `RenderSelfTest`: replaced the obsolete companion-tab scene with a scene exercising the real menu through container inputs: open, shift-equip, same-species switch, rename, and return. **This scene was not run.**

Important implementation details:

- The physical fish is stored in the existing companion attachment. Slot changes persist a copy through `Companions.setFromMenu`; they do not run the old equip-and-return operation again.
- Menu broadcast pulls external attachment changes into the slot, including rename and pile selection. A guard suppresses recursive persistence during that refresh.
- Ordinary slot clicks and drags use vanilla handling. Shift-click is overridden to exchange exactly one fish. Vanilla normally loops quick-move while the source retains the same item type; allowing that loop would repeatedly swap same-species companions.
- Client slot eligibility is installed during client initialization through a predicate, keeping client classes out of the dedicated-server menu load path. Server placement rechecks the existing bond gate.
- The pile strip uses the existing validated `BondCompanionPacket` path: a stack description, not a trusted slot or an invented item. It groups identical components and offers nine groups per page.
- Default shortcut behavior: hold over an eligible fish to equip/switch. If its **species matches the active companion**, holding opens management instead. The active specimen itself is absent from normal inventory, so the current implementation uses species identity, not exact specimen identity. This should be reviewed with the user if they expect a different interaction.
- Hold completion requires a fresh press, stable hovered slot/components, an empty cursor, and continued eligibility. Moving off, releasing, or opening inventory while already holding the key prevents completion. A completed hold cannot repeat until release.

Texture/menu coordinate contract:

| Element | Position / dimensions |
| --- | --- |
| Panel | 176×202 at atlas origin |
| Atlas | 256×256 |
| Companion frame | `(75,29)`, 26×26 (horizontally centred) |
| Companion item origin | `(80,34)` |
| Inventory first item origin | `(8,120)`, 18-pixel spacing, 9×3 |
| Hotbar first item origin | `(8,178)` |
| Heart atlas regions | `(180,0)` 8×7 screen header; `(190,0)` 5×4 encyclopedia badge. Draw at 1:1 or whole multiples only (half sizes drop rows at odd GUI scales); outside the panel on transparent atlas space (encyclopedia badge and screen header) |

## Verification completed

All passed:

```powershell
node --check tools/mc-gui-designer/app.mjs
node --check tools/mc-gui-designer/renderer.mjs
node --test tools/mc-gui-designer/renderer.test.mjs
node tools/mc-gui-designer/build.mjs
.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava --console=plain
git diff --check
```

The Java compile covers the rewritten client self-test source, but does not execute it. Existing NeoForge deprecation warnings remain. No game-test server, Minecraft client, full build, or jar deployment was run in this session.

The final small inventory-room refinement mirrors the first loose-stack match used by `takeOneMatching`, so it does not wrongly assume a later single-item stack will be removed. Final compilation is rerun as part of wrapping up.

## Progress 2026-10-12 (second session)

- `CompanionMenuGameTests` added (11 tests: click equip/return, cursor swap, number-key swap, drag, shift-click stack, same-species shift-click, full inventory, locked fish, close with cursor fish, outside-bond staleness, outside rename), registered on both loaders. Fabric: all 328 pass. NeoForge: compiles, not run.
- Fixed: a fish clicked onto the companion auto-piled (`SizedItemClickMixin`) into the slot, bypassing `mayPlace` — the companion slot now handles PICKUP itself; double-click collect-all skips it.
- Fixed: the slot mirror refreshed only on broadcast, so a same-tick bond packet + click could duplicate the old companion — the mirror is now pulled before every click.
- `.run/GUI Designer.run.xml`: IntelliJ one-click launcher for the portable tool.
- Fixed: typing the inventory key (E) in the rename box closed the screen. A focused name box now consumes every key except Escape (anvil pattern), so Q and 1–9 no longer act on slots either. Enter submits the rename. Compiles on all three projects; not yet tried in game.
- Layout pass: Return button and the idle shift-click hint removed (shift-click/click the slot to return). Slot centred; header (heart + "Your companion", or plain "No companion") centred above it and the fish name centred below, drawn in code. The header heart is no longer baked into the panel. Pile-page arrows moved to either side of the slot. Texture regenerated; renderer tests 8/8; all three projects compile. Not yet seen in game.

Items 1 and 3–11 below remain open; item 2 is done apart from the NeoForge run.

## Next work: prioritize runtime verification and correctness

1. Run the `companionbond` render self-test scene on NeoForge, then Fabric. It is selected by a `fishtastic_render_selftest` marker in the run directory containing `companionbond`. Inspect the harness instructions/run configuration before launching; do not overwrite an existing marker or unrelated world. The harness creates/recreates its own self-test world.
2. Add shared server game tests for actual menu clicks: click-to-equip, click-to-return, cursor swap, number-key swap, drag distribution, shift-click stacks, same-species replacement, full inventory, locked fish rejection, close with a fish on the cursor, and attachment updates while a menu is open. Register on both loaders using the repository's existing testmod pattern.
3. Review the menu storage lifecycle for duplication/loss: slot mutations, cursor return on close, full inventories, disconnect/death, and outside-GUI dropping. Compilation alone cannot establish this.
4. Exercise pile selection with multiple species and component-distinct fish, more than nine groups, locked fish, empty/depleted piles, stale descriptions, inventory-full replacement, and live inventory changes.
5. Exercise hold cancellation and rebinds, including recipe-book offsets, mouse-button bindings, focus loss, server refusal, and holding while opening inventory. SCANCODE bindings are not supported by the current physical polling; add that if required.
6. Inspect companion-screen labels, controls, and error feedback at multiple GUI scales. The status line currently truncates feedback to a narrow width; make the full error accessible. A Return button can remain active when no inventory room exists and simply do nothing—add clear disabled-state feedback/tooltip.
7. Improve the species-selection context: matching loose fish are highlighted, but the screen does not yet explicitly title itself Choose a [species], and the pile strip currently shows all carried pile fish. Review whether grouping all piles in one strip is clear enough or whether selecting a particular pile would be better.
8. Finish encyclopedia polish: heart badges currently reflect unlock/carry state at construction/cache refresh. They should also track inventory changes while the encyclopedia stays open. Locked quest-only entries use generic hidden-quest copy; restore named quest links where authored. The management header has a generic label rather than showing the active fish's name/icon.
9. Audit first-use/unlock wording outside `en_us.json` for references to the discarded tab. `CompanionTutorialClientHandler.onCompanionsTabOpened` is reused when the new screen opens; rename that API/comment when cleaning up.
10. Update the canonical companion design document once behavior is accepted. `docs/fish-companions.md` still describes the old tab/picker; use this handoff for the current implementation direction.
11. Complete browser checks for the portable tool and exports/imports. Consider richer parameter macros, multi-selection, precision progress/state atlas generation, and better font-preview behavior as future versatility improvements, after verifying the current tool.

## Commands and source-of-truth rules

```powershell
# Build the portable browser tool after changing its source.
node tools/mc-gui-designer/build.mjs

# Regenerate the companion texture from the retained design.
node tools/mc-gui-designer/export.mjs tools/mc-gui-designer/companions.json common/src/main/resources/assets/fishtastic/textures/gui/companions.png

# Browser development server (loopback only).
node tools/mc-gui-designer/serve.mjs 8767
```

Do not regenerate `companions.json` from the preset after manually editing the saved design, unless resetting it is intended. The portable HTML is generated; edit its source files and rebuild rather than hand-editing the packaged file.

Read `AGENTS.md` and relevant canonical topics under `D:\GitHub\modding-guide` before continuing. Topic files 04, 07, 08, and 18 were read this session. Minecraft 26.1.2 APIs encountered: `ContainerInput`, `handleContainerInput`, `GuiGraphicsExtractor`, and `SimpleContainer.setChanged` (no listener API). Both loaders compile with Java 25.

## Workspace and preview state

Changes are uncommitted. The workspace also contains pre-existing/unrelated `.agents/`, `.codex/`, AGENTS.md, logs, simulation files, and companion handoff notes. `docs/fish-companions-benthic-handoff.md` changed during this session through other work; it was not edited by this task. Do not revert or include unrelated files indiscriminately.

The development browser visited `http://127.0.0.1:8767/`. Temporary preview servers were started on 8765, 8766, and 8767 while diagnosing Windows MIME/socket behavior; they are stopped during wrap-up. Restart the Node server if continuing browser development. The portable HTML remains usable without those servers.
