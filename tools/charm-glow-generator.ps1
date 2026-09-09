<#
.SYNOPSIS
  Generates bespoke procedural glint-overlay textures for rod-slot charms (and similar
  CHARM_EFFECT items), one PNG per entry in $targets below.

.DESCRIPTION
  Each charm's quality-glow texture (the "epic shader" enchant-style shimmer, driven by
  RenderPipelines.GLINT via ItemEffect/RenderTypeFactory) used to be a hue-shifted copy of the
  shared epic_quality_glow.png. This script instead synthesizes a genuinely unique, tileable
  cloud/splotch pattern per charm from scratch: layered periodic value-noise (fBm, quintic-smoothed
  bilinear lattice interpolation), domain-warped for the smeared/streaked look, colourized around a
  target hue with a touch of per-pixel hue jitter for painterly variety.

  GLINT's blend function is additive AND SQUARES the source colour before adding
  (BlendFunction.GLINT = SRC_COLOR, ONE — result = src*src + dst), and the pipeline's vertex format
  carries no colour attribute, so there is no runtime alpha/opacity knob for this effect at all.
  The only lever is pixel brightness baked into the texture, and because the contribution is
  squared, hitting an on-screen strength of $TargetOpacity means scaling texture brightness by
  sqrt($TargetOpacity) — that scale is applied per-pixel during generation via $brightnessScale
  below, so the emitted PNGs are already at the right strength; no separate post-process pass
  needed.

  Generation runs at $GenSize and upscales to $FinalSize with high-quality bicubic interpolation —
  both cheaper than generating at full resolution and it softens the noise into the same painterly
  blur the tier textures (epic/rare/uncommon/legendary_quality_glow.png) have.

  To add a new charm: add a "<item_name>" = "<AARRGGBB-less RRGGBB with FF alpha, e.g. FFAA44FF>"
  entry to $targets, matching the outline_color chosen for that charm's item_effect JSON (see
  common/src/main/resources/data/fishtastic/fishtastic/item_effect/<name>_glow.json), then re-run.
  Existing entries are safe to re-run too — each is fully regenerated (new random pattern each
  time; seeds are derived from the name for reproducibility within one run, not pinned across
  runs), so only re-run entries you actually want to change.

.NOTES
  Windows-only (System.Drawing). Pure PowerShell — no Python/ImageMagick dependency. Slow-ish:
  System.Drawing pixel loops in PowerShell are not fast, expect ~15-25s per charm at the defaults
  (9 charms ≈ 3-4 minutes total). Run in the background if driving this from an agent.

  Two footguns hit during development, both fixed here — worth not reintroducing:
    - `New-Object 'double[,]'` does NOT reliably build a real rectangular array in PowerShell; it
      silently produces something that throws "Cannot index into a null array" deep in the pixel
      loop. Use `[double[,]]::new($rows, $cols)` instead (see New-Lattice).
    - Seed arithmetic (`$baseSeed * 7 + ...`) routinely overflows Int32; PowerShell silently widens
      to Int64/Double rather than wrapping or throwing, so the overflow only surfaces later as a
      binding error when the too-large value hits New-Lattice's `[int]$seed` parameter. Clamp-Seed
      masks back down to 31 bits before that happens.
#>

param(
    # Optional list of item ids (keys of $targets) to (re)generate. Omit to regenerate everything.
    # Use this when adding one new charm so existing shipped textures aren't disturbed by a fresh
    # random pattern, e.g.: .\charm-glow-generator.ps1 -Only sunset_postcard_charm
    [string[]]$Only
)

Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$outDir   = Join-Path $repoRoot "common\src\main\resources\assets\fishtastic\textures\misc\charm_glow"
$genSize   = 128      # generate at this resolution
$finalSize = 256      # upscale to this resolution (adds a soft painterly blur)
$targetOpacity = 0.25 # on-screen glint strength; see .DESCRIPTION for why sqrt() is applied below

if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

# name (bare item id) -> target ARGB hex. Keep in sync with the outline_color chosen in that
# charm's item_effect/<name>_glow.json — this is the same colour, just as opaque 0xFFRRGGBB.
$targets = [ordered]@{
    "amethyst_charm"     = "FFAA44FF"
    "bait_buddy_charm"   = "FFFF6EC7"
    "banana_charm"       = "FFFFD400"
    "crystal_ball_charm" = "FFAEE9FF"
    "four_leaf_charm"    = "FF4CAF50"
    "luna_charm"         = "FFC8C8D2"
    "anglers_almanac"    = "FFAA6C39"
    "little_fish_box"    = "FFC98A4B"
    "storm_charm"        = "FF5AC8FA"
    "sunset_postcard_charm" = "FFF77B20"
}

$namesToGenerate = if ($Only) { $Only } else { $targets.Keys }

function Stable-Hash([string]$s) {
    $h = 5381
    foreach ($c in $s.ToCharArray()) {
        $h = (($h * 33) -bxor [int][char]$c) -band 0x7fffffff
    }
    return [int]$h
}

# Keeps derived seeds within Int32 range (New-Lattice takes [int]$seed); the multiplications
# below routinely overflow 32 bits, which PowerShell silently widens to Int64/Double instead of
# wrapping — left unclamped, the overflow surfaces later as a confusing parameter-binding error.
function Clamp-Seed([long]$n) {
    return [int]($n -band 0x7fffffff)
}

function New-Lattice([int]$cells, [int]$seed) {
    $rnd = New-Object System.Random($seed)
    # NOT `New-Object 'double[,]' $cells,$cells` — that does not reliably build a real
    # rectangular array in PowerShell (see .NOTES).
    $lat = [double[,]]::new($cells, $cells)
    for ($i = 0; $i -lt $cells; $i++) {
        for ($j = 0; $j -lt $cells; $j++) {
            $lat[$i, $j] = $rnd.NextDouble()
        }
    }
    return , $lat
}

function Smooth([double]$t) {
    return $t * $t * $t * ($t * ($t * 6.0 - 15.0) + 10.0)
}

# Periodic (tileable) bilinear value-noise sample. fx,fy in [0,1).
function Sample-Lattice($lat, [int]$cells, [double]$fx, [double]$fy) {
    $gx = $fx * $cells
    $gy = $fy * $cells
    $x0 = [Math]::Floor($gx)
    $y0 = [Math]::Floor($gy)
    $tx = $gx - $x0
    $ty = $gy - $y0
    $x0i = [int]$x0 % $cells
    $y0i = [int]$y0 % $cells
    $x1i = ($x0i + 1) % $cells
    $y1i = ($y0i + 1) % $cells
    $sx = Smooth $tx
    $sy = Smooth $ty
    $v00 = $lat[$y0i, $x0i]; $v10 = $lat[$y0i, $x1i]
    $v01 = $lat[$y1i, $x0i]; $v11 = $lat[$y1i, $x1i]
    $a = $v00 + ($v10 - $v00) * $sx
    $b = $v01 + ($v11 - $v01) * $sx
    return $a + ($b - $a) * $sy
}

function Rgb-From-Hsl([double]$hue, [double]$s, [double]$l) {
    if ($s -le 0) {
        $v = [Math]::Round([Math]::Max(0.0, [Math]::Min(1.0, $l)) * 255.0)
        return @($v, $v, $v)
    }
    $hue = (($hue % 360.0) + 360.0) % 360.0
    $c = (1 - [Math]::Abs(2 * $l - 1)) * $s
    $hp = $hue / 60.0
    $x = $c * (1 - [Math]::Abs(($hp % 2) - 1))
    $r1 = 0.0; $g1 = 0.0; $b1 = 0.0
    if ($hp -lt 1) { $r1 = $c; $g1 = $x; $b1 = 0 }
    elseif ($hp -lt 2) { $r1 = $x; $g1 = $c; $b1 = 0 }
    elseif ($hp -lt 3) { $r1 = 0; $g1 = $c; $b1 = $x }
    elseif ($hp -lt 4) { $r1 = 0; $g1 = $x; $b1 = $c }
    elseif ($hp -lt 5) { $r1 = $x; $g1 = 0; $b1 = $c }
    else { $r1 = $c; $g1 = 0; $b1 = $x }
    $m = $l - $c / 2.0
    $r = [Math]::Round([Math]::Max(0.0, [Math]::Min(1.0, $r1 + $m)) * 255.0)
    $g = [Math]::Round([Math]::Max(0.0, [Math]::Min(1.0, $g1 + $m)) * 255.0)
    $b = [Math]::Round([Math]::Max(0.0, [Math]::Min(1.0, $b1 + $m)) * 255.0)
    return @($r, $g, $b)
}

function Hue-From-Rgb([int]$r, [int]$g, [int]$b) {
    $rf = $r / 255.0; $gf = $g / 255.0; $bf = $b / 255.0
    $max = [Math]::Max($rf, [Math]::Max($gf, $bf))
    $min = [Math]::Min($rf, [Math]::Min($gf, $bf))
    if ($max -eq $min) { return 0.0 }
    $d = $max - $min
    $hue = 0.0
    if ($max -eq $rf) { $hue = (($gf - $bf) / $d) % 6.0 }
    elseif ($max -eq $gf) { $hue = (($bf - $rf) / $d) + 2.0 }
    else { $hue = (($rf - $gf) / $d) + 4.0 }
    $hue *= 60.0
    if ($hue -lt 0) { $hue += 360.0 }
    return $hue
}

$octaveCellsList = @(3, 6, 12, 24)
$octaveAmps      = @(1.0, 0.5, 0.28, 0.14)
$totalAmp = ($octaveAmps | Measure-Object -Sum).Sum

# result = src*src + dst (see .DESCRIPTION) — scale brightness by sqrt() so the *contribution*
# lands at $targetOpacity, not the raw pixel value.
$brightnessScale = [Math]::Sqrt($targetOpacity)

foreach ($name in $namesToGenerate) {
    if (-not $targets.Contains($name)) {
        Write-Warning "Skipping '$name' - not present in `$targets"
        continue
    }
    $hex = $targets[$name]
    $tr = [Convert]::ToInt32($hex.Substring(2, 2), 16)
    $tg = [Convert]::ToInt32($hex.Substring(4, 2), 16)
    $tb = [Convert]::ToInt32($hex.Substring(6, 2), 16)
    $targetHue = Hue-From-Rgb $tr $tg $tb

    $baseSeed = Stable-Hash $name

    $mainLattices = @()
    for ($k = 0; $k -lt $octaveCellsList.Length; $k++) {
        $mainLattices += , (New-Lattice $octaveCellsList[$k] (Clamp-Seed ([long]$baseSeed * 7 + $k * 101 + 1)))
    }
    $warpXLat = New-Lattice 3 (Clamp-Seed ([long]$baseSeed * 13 + 2))
    $warpYLat = New-Lattice 3 (Clamp-Seed ([long]$baseSeed * 17 + 3))
    $hueLat   = New-Lattice 3 (Clamp-Seed ([long]$baseSeed * 19 + 4))

    $warpStrength = 0.22
    $hueJitter = 22.0  # degrees

    $small = New-Object System.Drawing.Bitmap $genSize, $genSize, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $genSize, $genSize)
    $data = $small.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $stride = $data.Stride
    $bytes = New-Object byte[] ($stride * $genSize)

    for ($y = 0; $y -lt $genSize; $y++) {
        $fy = $y / [double]$genSize
        $rowOff = $y * $stride
        for ($x = 0; $x -lt $genSize; $x++) {
            $fx = $x / [double]$genSize

            $wx = Sample-Lattice $warpXLat 3 $fx $fy
            $wy = Sample-Lattice $warpYLat 3 $fx $fy
            $sfx = (($fx + $warpStrength * ($wx - 0.5)) % 1.0 + 1.0) % 1.0
            $sfy = (($fy + $warpStrength * ($wy - 0.5)) % 1.0 + 1.0) % 1.0

            $sum = 0.0
            for ($k = 0; $k -lt $octaveCellsList.Length; $k++) {
                $sum += $octaveAmps[$k] * (Sample-Lattice $mainLattices[$k] $octaveCellsList[$k] $sfx $sfy)
            }
            $v = $sum / $totalAmp

            # Contrast/threshold remap: mostly-black field with bright colored splotches
            $v2 = ($v - 0.34) / (0.66 - 0.34)
            if ($v2 -lt 0) { $v2 = 0.0 } elseif ($v2 -gt 1) { $v2 = 1.0 }
            $bright = [Math]::Pow($v2, 1.6)

            $hueOff = ($hueJitter * 2.0) * ((Sample-Lattice $hueLat 3 $fx $fy) - 0.5)
            $hue = $targetHue + $hueOff

            $lVal = $bright * 0.92 * $brightnessScale
            $satPeak = 1.0
            if ($bright -gt 0.55) { $satPeak = 1.0 - 0.65 * (($bright - 0.55) / 0.45) }
            $sat = 0.9 * $satPeak

            $rgb = Rgb-From-Hsl $hue $sat $lVal

            $i = $rowOff + $x * 3
            $bytes[$i]     = [byte]$rgb[2]
            $bytes[$i + 1] = [byte]$rgb[1]
            $bytes[$i + 2] = [byte]$rgb[0]
        }
    }

    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $data.Scan0, $bytes.Length)
    $small.UnlockBits($data)

    $final = New-Object System.Drawing.Bitmap $finalSize, $finalSize, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($final)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($small, 0, 0, $finalSize, $finalSize)
    $g.Dispose()
    $small.Dispose()

    $outPath = Join-Path $outDir "$name.png"
    $final.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $final.Dispose()
    Write-Output "Wrote $outPath (seed=$baseSeed, hue=$([Math]::Round($targetHue,1)))"
}
