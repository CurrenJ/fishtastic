# Development tools

Run commands from the repository root. Exact build/version setup and resource authority
are in [development](../docs/development.md). Generated previews and run evidence are
ignored; editable source designs and generators are retained.

## Authoring

| Tool | Entry point and output |
|---|---|
| Tank shapes | `:tools:tank-shape-gen:test`, `:tools:tank-shape-previewer:run`; see [tank guide](../docs/fish-tanks.md) |
| Shelter/span structures | `python tools/shelter-structure-gen/gen.py <design> --preview` or `python tools/span-structure-gen/gen.py --preview`; writes datapack JSON and local previews |
| Skull/reactive sculptures | `skull.py` is standalone; `reactive.py` is loaded by shelter gen. `sculpt.py` is shared construction logic |
| Footprints | `footprint_audit.py` inspects claims (`--apply` writes); `footprint_sheet.py` renders them. `ortho.py` and `swatch.py` support visual authoring |
| Fish calibration | `pwsh tools/fish-render-calibration.ps1`; updates calibration/pivots after texture/animation changes; Windows System.Drawing |
| Charm glows | `pwsh tools/charm-glow-generator.ps1 -Only <charm>`; creates effect textures. Patterns are not stable across separate runs; omit Only only to regenerate all intentionally |
| Profile Studio | Open [fish-profile-studio.html](fish-profile-studio.html); choose the local repo/resources folder. Reads/writes fishing data; review exported diffs. It is not a complete editor for every newer simulation field |
| GUI workbench | `node tools/mc-gui-designer/serve.mjs`; see its [README](mc-gui-designer/README.md). `companions.json` is editable source; `mc-gui-designer.html` is the intentionally checked-in portable distribution, rebuilt with `build.mjs` |
| Sticker sheet | Open [sticker_sheet.html](sticker_sheet.html); optional block-folder input from `pwsh tools/extract-block-textures.ps1 -Version <mc>`. Extracted Mojang textures stay ignored |
| MCP bridge | `npm ci --prefix tools/mcp-bridge`, then `node tools/mcp-bridge/index.js`; local `.env` follows `.env.example` and the in-game MCP session. Tokens/settings remain local |

Structure/preview rendering uses Pillow, NumPy and Matplotlib where imported; install
those in your Python environment. Generation writes authored JSON: use named designs
and inspect changes rather than running every generator casually. Aseprite/ASE files,
`title.bbmodel` and the README banner are source art, not unused runtime assets.

## Simulation diagnostics

```sh
./gradlew :fishsim:runViewer
./gradlew :fishsim:runCompanionViewer
./gradlew :fishsim:runHeadless -PsimArgs="--seed 42 --ticks 1200 --out build/sim-review"
./gradlew :fishsim:runCompanionHeadless -PsimArgs="--script walk --seed 1 --ticks 1200"
```

Regression tests remain under normal `test` tasks. Measurement probes are opt-in with
`-PsimStdout`; they produce diagnostics, not correctness verdicts. Trace modes share
one runner (select exactly `*companion.TraceProbe`):

```sh
./gradlew :fishsim:test --tests '*companion.TraceProbe' -PsimStdout -Ptrace=about_face:1:0:600:5
./gradlew :fishsim:test --tests '*companion.TraceProbe' -PsimStdout -PtraceMode=drift -Ptrace=walk -PdriftStyle=jet -PsweepTicks=600 -PsweepSeeds=1
```

Other modes: `stroke` (bell tick window via `-PtracePos=940,990`), `warmth`, and
`wariness` (`-Ptrace=hostile_approach:1`). Motion uses `-PsweepClass` and optional
`-PtracePos`; drift/stroke accept `-PtweakA`. Keep unique sweeps, invariants and
fingerprint probes; do not delete a diagnostic merely because it has no assertions.

## Isolated game scenes

```sh
python scripts/run_render_checks.py fabric --solo --scenes questbook
python scripts/run_render_checks.py neoforge
python scripts/run_render_checks.py forge --prepare-only
```

The launcher creates a fresh UUID run under `build/verification/two-client/<loader>/`,
uses `scripts/twoclient.init.gradle`, and preserves logs, screenshots and worlds there.
Default solo scene is `companionscenes`; default two-client scene is `twoclient`.
It rejects failures, empty check sets, missing/duplicate completion and nonzero exits.
Use `--port` for a different local port pair and `--timeout` for a slower machine.
The dedicated test server binds localhost and is stopped via its own RCON session;
cleanup targets only processes started by this invocation, never an arbitrary port owner.
Clients open game windows; the Gradle helpers do not open terminal windows.

For manual launches use the init script with a shared `-PtwoRunId=<id>`, `twoLoader`,
`twoPort` and roles `server`, `a`, `b` (or `solo`). Roles have separate run folders.
Markers select scenes/parameters; a persistent marker hijacks subsequent launches
in that folder, which is why normal dev run directories are avoided.

`RenderSelfTest` owns dispatch, queue/capture utilities and baseline rendering scenes.
`AmbientScenes`, `CompanionScenes`, `BenchmarkScenes`, `QuestBookScenes` and
`EncyclopediaScenes` keep larger fixture families separate. All stay in the excluded
`client/selftest` package; release jars must contain none of them.

Benchmark scenes use `tools/perfbench/{analyze,bake_table,scale_analyze}.py`; see the
[historical methodology](../docs/cosmetic-render-cost.md). `scripts/rcon.ps1` supports
manual local server commands. `scripts/run-installed-forge-client.ps1` prepares/launches
an installer-created client; provide target assets and Java paths for your machine.
Installed-artifact checks remain separate from dev scene acceptance.

## Local agent tooling

Local agent instructions, skills and settings remain ignored. Contributor build,
resource and verification instructions live in the tracked development guide.

## Release smoke test

```sh
python tools/release-smoke/run_matrix.py                # all profiles in matrix.json
python tools/release-smoke/run_matrix.py 1.20.1 Forge   # filter by profile words
```

Boots the built **release** jars inside each CurseForge profile (launched directly from
CurseForge's `Install/` dir, offline, in a scratch copy under `build/release-smoke/`; the
profile itself is only read) and quick-plays the tank template world in `worlds/<mc>/`.
It catches packaging-only failures (shading, mixin, refmap) that dev runs never show. PASS
= joined the world and survived 30s with no fatal log lines, no errors from our code and no
crash report. Load errors fail fast; a client parked on a screen fails after 60s of log
silence with `stuck.png`. Exit 3 (`PASS_WITH_FINDINGS`) means the run only booted after
`--accept-experimental` confirmed vanilla's experimental-settings screen (keys are posted to
the game window, never sent as global input).

Template worlds are local and ignored. Create one per Minecraft version from that branch's
checkout: write `tank` to `fabric/run/fishtastic_render_selftest`, run `:fabric:runClient`,
**delete the marker**, then
`python tools/release-smoke/make_template.py <checkout>/fabric/run/saves/fishtastic_render_selftest tools/release-smoke/worlds/<mc>`.
