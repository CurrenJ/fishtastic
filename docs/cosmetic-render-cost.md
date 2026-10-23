# Cosmetic rendering benchmark: 3 October 2026

Historical measurements on one machine, not current performance guarantees:
Ryzen 9 5900X, 32 GB RAM, RTX 3080 Ti, Windows 11. Client: Fabric, Minecraft
26.1.2, Sodium 0.9.1 and Iris 1.11.3, with Complementary Reimagined r5.9.3 plus
IRLights for shader runs. Most runs were uncapped at 2560x1440. NeoForge client
measurements failed at world creation; no renderer result here applies to it or Forge.

## Findings worth retaining

- Baked static cosmetics had no frame/GPU cost distinguishable from noise without
  shaders in the tested wide views (up to 126,252 quads). This is not proof of zero
  cost in other scenes, hardware or close-up fragment-heavy views.
- Shader runs showed GPU cost, with floor quads costlier than span quads. Floor
  neighbor-face culling reduced geometry but its measured saving was inconclusive:
  paired confidence intervals crossed zero, and GPU performance-level switching
  biased even A/A controls. The temporary culling experiment was removed.
- Scanning every floor structure part twice per frame cost CPU, especially for
  mushroom blocks whose `rotate(NONE)` still sets properties. Caching
  `CosmeticStructure.liveParts()` removed the dependency on total part count/block
  choice. At 36 Leviathan's Seats the historical scan delta fell from
  +0.586 +/- 0.018 ms to -0.000 +/- 0.019 ms. `LivePartsTest` checks equivalence.
- Cold-cache bake time was roughly proportional to quads and paid on meshing
  threads, shared for equal snapshots. Vertex memory scales with quads per section
  copy; it is not shared between separate chunk meshes.

## Reproduction

`bakebench` records structure/layer counts and cold-cache bake time. `scalebench`
compares two arms in paired rounds with alternating order. `ab=mesh` compares
NONE/MESH; `ab=scan` compares live-part scans on/off and is now a regression check.
Always include empty A/A scenes, record actual resolution/shaders, and retain
per-round results rather than treating frames as independent trials.

Example `fishtastic_render_selftest` marker in an isolated dev run directory:

```text
scalebench
frames=3000
rounds=6
label=noshaders
fullscreen=true
ab=mesh
structure=empty
structure=leviathans_seat
spanCounts=1,4,9
floorCounts=9,36
```

Analyze with:

```sh
python tools/perfbench/bake_table.py <run>/logs/latest.log
python tools/perfbench/scale_analyze.py <run>/fishtastic_bench <run>/logs/latest.log
```

`tools/perfbench/analyze.py` summarizes the simpler `perfbench` scene. Old CSVs
were local run output, not a required repository input. Reproduce fresh evidence
for new code. Not measured: other hardware, installed NeoForge/Forge rendering,
close-ups, reliable Sodium rebuild latency, Iris vertex stride, index-buffer/JVM
cache memory, and several unexplained small timing effects.
