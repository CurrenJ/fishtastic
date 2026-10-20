# Phase 2 handoff (info-page rewrite) - state as of this note

Nothing is committed. Branch 26.1.2. On 2026-10-20, `./gradlew :common:compileJava :common:test :fabric:compileJava :neoforge:compileJava` completed successfully (`BUILD SUCCESSFUL`, 11 tasks up-to-date) after the bait-name and EncyclopediaCoins centring edits.

## Done
- `client/FishEncyclopediaScreen.java` rewritten: disc kept + "Claim all N" button; 416x232 canvas at integer k; flight with scale compensation + snap at 0.45 s; prev/next slide; close-after-browse returns the shown icon; selectOnNextOpen opens with the icon placed straight in the slot (no flight); live refresh 4x/s (EnvSnapshot + bond status); sync handler swap kept; Esc closes page first; Left/Right browse.
- New `client/encyclopedia/page/`: `PageSurface` (draw-command leaf), `PageIcons`, `ScaledTooltip`, `HitArea`, `PageButton`, `EncyclopediaPageLayout` (the whole page layout, pixel-for-pixel from render_mockups.py).
- `FishSphereContainer`: added `markSelected(icon)` and `parkSilently(icon)` for browsing.
- `RenderSelfTest.queueEncyclopediaScene` reworked: 5 configs (1920x1080 GUI 4/2/1, 1280x720 GUI 3, and 320x240 GUI 1), disc, flight, browse, close, deep link of the 5 fixtures, betta notes tab, and a compact-card assertion. All checks passed on both loaders.
- Phase-1 fix: `EncyclopediaCoins.icon` placed children at (0,0) but `ManualContainer` centres children on the given point, so the coin was 8 px up-left. Now centred.
- Silhouette implementation fix is present in `SilhouetteItemButton`: gelatin routes magnified items through HiResItems, bypassing the atlas shader hook, so page-scale uncaught fish use a black-tinted fish sprite. The unmodified atlas silhouette path is retained at disc scale. The temporary `EncyclopediaCanvasSpike` harness was removed after its findings were folded into the real encyclopedia scene.
- Phase-3 verification is complete: the Fabric and NeoForge `encyclopedia` scenes each ran 41 encyclopedia checks with 0 failures across GUI scales 4/3/2/1 plus the 320x240 compact case. The uncaught Portuguese man o' war is a black silhouette in all full-spread capture sets, and the compact card is fully contained in the 320x240 capture. Evidence is in `fabric/run/logs/latest.log`, `fabric/run/screenshots/selftest-fabric-encyclopedia-*.png`, `neoforge/run/logs/latest.log`, and `neoforge/run/screenshots/selftest-neoforge-encyclopedia-*.png`.

## Remaining review
- Fade on prev/next is slide-only (no per-element alpha in gelatin). Page fade-in is a curtain rectangle.
- Not covered by the unattended scene: sounds, OS-level real-mouse hover, and final visual polish of the compact card beyond the captured 320x240 fixture. The compact card is implemented and its layout/activation are loader-tested; these are follow-up playtest checks rather than loader-gate failures.
- Remember: delete `<run dir>/fishtastic_render_selftest` after every client run; captures are under the corresponding loader's `run/screenshots/` directory.
