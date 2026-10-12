# GUI Workbench

A local, parameterized Minecraft GUI texture designer. Open `../mc-gui-designer.html` in a browser; no server or installation is needed. Source development uses `node tools/mc-gui-designer/serve.mjs` and <http://127.0.0.1:8767>.

## Asset provenance

**No Mojang assets are bundled with this tool.** Furnace, crafting, hopper, chest, and companion presets are original procedural constructions following familiar Minecraft panel and slot conventions. The grey palette, beveled frames, progress shapes, heart, and small preview bitmap alphabet are implemented in `renderer.mjs`. They do not load, extract, trace, or redistribute Minecraft textures or fonts. Preview labels use a small original bitmap alphabet, not Minecraft's font. In-game text uses the game's normal font at runtime.

PNG import is optional and local. Imported pixels are embedded in saved design JSON; nothing is uploaded or automatically written into the repository. Do not commit Mojang sprites or design JSON containing their pixels. The shipped `companions.json` contains procedural elements only, and `companions.png` is generated entirely from them.

## Editing

- Start with a preset or compose multiple panels on one atlas. Atlas sizes range from 16 to 2048 pixels.
- Add panels, individual slots, slot grids, buttons, rectangles, separators, arrows, flames, progress bars, hearts, labels, or your own PNG sprites.
- Select a layer or canvas element. Drag to move; drag its lower-right handle to resize. Grid resizing changes the row/column count.
- Set exact position, size, bevel thickness, rounded corners, slot style, container indices, colors, fill direction, fill amount, button state, or sprite nine-slice borders in the inspector.
- Snap to 1, 2, 4, 9, or 18 pixels. Arrow keys nudge; Shift nudges 10 pixels. Undo/redo, duplicate, reorder, show/hide, lock, and align to panel are available.
- Labels are guides by default and excluded from PNGs. Enable “Bake into texture” to include the original preview font. Game titles and interactive buttons should usually be rendered by the screen instead.
- Preview zoom never changes export resolution. Transparent atlas regions and sprite transparency are preserved.
- Browser-local saves survive reloads. Save JSON for portable designs, embedded sprites, and backups. Opening JSON is undoable.

## Exports and scripts

The browser exports a native-resolution PNG atlas, complete design JSON, layout metadata, and Java slot-position scaffolding. Layout metadata includes panel regions, slot item origins, labels, and individual element bounds. Choose the correct container for each generated Java slot; the output does not implement inventory behavior or register menus.

Browser PNG export and the dependency-free Node CLI use the **same pixel renderer**:

```powershell
node tools/mc-gui-designer/build.mjs
node tools/mc-gui-designer/export.mjs tools/mc-gui-designer/companions.json common/src/main/resources/assets/fishtastic/textures/gui/companions.png
node --test tools/mc-gui-designer/renderer.test.mjs
```

Generate another preset directly:

```powershell
node tools/mc-gui-designer/export.mjs --preset furnace furnace.png furnace.json
```

The companion preset is a 176 × 202 panel on a transparent 256 × 256 atlas: companion item origin `(20,34)`, inventory `(8,120)` with 18-pixel spacing, and hotbar `(8,178)`. The heart region is `(48,24,9,9)`. These positions match `CompanionMenu` and `CompanionScreen`.
