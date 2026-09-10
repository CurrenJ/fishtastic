<#
.SYNOPSIS
  Recomputes each catchable fish's render_calibration and writes it into its fish_profile JSON.

.DESCRIPTION
  The tank renderer (FishTankBlockEntityRenderer) scales each fish's item model so its cm-based
  ItemSize maps to true block-scale length (1 block = 1 metre). That scale depends on how much of
  the fish's square texture canvas its actual art occupies, and whether its animation mode applies
  the 45-degree "diagonal texture" roll (horizontal_swim / upright_float / upright_sit) that widens
  a diagonally-painted fish's on-screen span by sqrt(2).

  This script measures both per species by scanning the texture's alpha channel for the opaque
  silhouette's bounding extent — along whichever canvas diagonal is longer for diagonal-corrected
  modes (fish are painted nose-to-top-right by convention, i.e. along x-y, but the larger of x-y
  and x+y is used so a fish painted the other way is still measured correctly), along the canvas
  edge otherwise — and writes the resulting calibration constant into the species'
  fish_profile/<name>.json as "render_calibration". Re-running it is safe: an existing field is
  updated in place rather than duplicated, and every other field in the file is left untouched.

  For upright_sit species it additionally measures "pivot_fraction": how far below the item's
  centre the art's lowest visible pixel sits, which is how far the creature must be lifted to stand
  on the sand. The renderer used to assume half the item — right for art that reaches the bottom of
  its canvas, and a visible float for art that stops short (trapania_scurra fills half its canvas
  vertically and hovered by roughly its own visible height).

  Run this whenever a fish's texture, animation mode, or diagonal_texture flag changes, and after
  adding a new species via the add-fish-species skill (its fish_profile falls back to a flat
  0.8 slope — see FishProfile.DEFAULT_RENDER_CALIBRATION — until this script has measured it).

.NOTES
  Windows-only (System.Drawing). See the fish-tank-scale-audit review this generalizes from for the
  full methodology and per-species before/after numbers.
#>

Add-Type -AssemblyName System.Drawing

$repoRoot   = Split-Path -Parent $PSScriptRoot
$profileDir = Join-Path $repoRoot "common\src\main\resources\data\fishtastic\fishtastic\fish_profile"
$texDir     = Join-Path $repoRoot "common\src\main\resources\assets\fishtastic\textures\item\fish"

$diagonalModes = @("horizontal_swim", "upright_float", "upright_sit")
$sqrt2 = [Math]::Sqrt(2)

$summary = @()

Get-ChildItem $profileDir -Filter *.json | Sort-Object Name | ForEach-Object {
    $file = $_
    $name = $_.BaseName
    $raw = Get-Content $file.FullName -Raw
    $json = $raw | ConvertFrom-Json

    $mode = "horizontal_swim"
    $diagonalTexture = $true
    if ($json.animation) {
        if ($json.animation.mode) { $mode = $json.animation.mode }
        if ($json.animation.PSObject.Properties.Name -contains "diagonal_texture") {
            $diagonalTexture = [bool]$json.animation.diagonal_texture
        } else {
            # Matches the default in FishAnimationConfig's codecs for each mode.
            $diagonalTexture = ($mode -in $diagonalModes)
        }
    }
    $usesDiagonalRotation = $diagonalTexture -and ($mode -in $diagonalModes)

    $texPath = Join-Path $texDir "$name.png"
    if (-not (Test-Path $texPath)) {
        Write-Warning "$name : no texture found at $texPath, skipping"
        return
    }

    $bmp = [System.Drawing.Bitmap]::FromFile($texPath)
    $w = $bmp.Width
    $h = $bmp.Height

    $minX = [int]::MaxValue; $maxX = -1; $minY = [int]::MaxValue; $maxY = -1
    $minSum = [int]::MaxValue; $maxSum = -1
    $minDiff = [int]::MaxValue; $maxDiff = [int]::MinValue
    # Lowest visible point of the art in item-local units (item spans -0.5..+0.5, +up), AFTER any
    # roll the mode applies. This is what an upright pose has to be lifted by to stand on the sand
    # rather than hover above it; see FishAnimationConfig.UprightSit.pivotFraction.
    $minLocalY = [double]::MaxValue

    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $a = $bmp.GetPixel($x, $y).A
            if ($a -gt 10) {
                # Test the pixel's own corners, not its centre: the visible edge of the art is the
                # edge of its lowest pixel, and after a 45 degree roll the lowest point of a pixel
                # is one of its corners.
                foreach ($cx in @($x, $x + 1)) {
                    foreach ($cy in @($y, $y + 1)) {
                        $u = $cx / $w - 0.5
                        $v = 0.5 - $cy / $h
                        # Axis.ZP.rotationDegrees(-45) maps (u,v) -> v' = (v - u) / sqrt(2).
                        $localY = if ($usesDiagonalRotation) { ($v - $u) / $sqrt2 } else { $v }
                        if ($localY -lt $minLocalY) { $minLocalY = $localY }
                    }
                }
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
                $s = $x + $y
                if ($s -lt $minSum) { $minSum = $s }
                if ($s -gt $maxSum) { $maxSum = $s }
                $d = $x - $y
                if ($d -lt $minDiff) { $minDiff = $d }
                if ($d -gt $maxDiff) { $maxDiff = $d }
            }
        }
    }
    $bmp.Dispose()

    if ($maxX -lt 0) {
        Write-Warning "$name : texture has no opaque pixels, skipping"
        return
    }

    $N = [Math]::Max($w, $h)
    $axisFraction = [Math]::Max($maxX - $minX + 1, $maxY - $minY + 1) / $N
    # A fish's body can be painted along either diagonal (nose-to-top-right, the documented
    # convention, runs along x-y; nose-to-top-left would run along x+y). The 45-degree rotation
    # maps a square's two diagonals onto its two axes simultaneously and stretches both by the
    # same sqrt(2), so whichever diagonal the art actually follows becomes the long axis after
    # rotation — take the larger of the two rather than assuming one fixed chirality.
    $mainDiagFraction = if ($N -gt 1) { ($maxSum - $minSum) / (2.0 * ($N - 1)) } else { 1.0 }
    $antiDiagFraction = if ($N -gt 1) { ($maxDiff - $minDiff) / (2.0 * ($N - 1)) } else { 1.0 }
    $diagFraction = [Math]::Max($mainDiagFraction, $antiDiagFraction)

    $operativeFraction = if ($usesDiagonalRotation) { $diagFraction } else { $axisFraction }
    $correctionMult = if ($usesDiagonalRotation) { $sqrt2 } else { 1.0 }

    $renderCalibration = [Math]::Round(1.0 / ($correctionMult * $operativeFraction), 2)
    $pivotFraction = [Math]::Round(-$minLocalY, 4)

    # Surgical text insertion: preserves the file's existing formatting/field order exactly. If a
    # render_calibration field already exists (re-run), replace its value in place instead of
    # inserting a duplicate.
    $existingPattern = [regex]'(?m)^(\s*)"render_calibration"\s*:\s*[0-9.]+\s*,?\s*$'
    if ($existingPattern.IsMatch($raw)) {
        # Replace only the first occurrence (the top-level field), in case a coincidental match
        # exists elsewhere in the file.
        $newRaw = $existingPattern.Replace($raw, "`$1`"render_calibration`": $renderCalibration,", 1)
    } else {
        # Insert right after the file's opening brace (always the top-level object's first field)
        # — located by index rather than regex so nested "{`n" occurrences (animation/swarm blocks)
        # are never touched.
        $braceIdx = $raw.IndexOf('{')
        $newlineIdx = $raw.IndexOf("`n", $braceIdx)
        $insertAt = $newlineIdx + 1
        $newRaw = $raw.Substring(0, $insertAt) + "  `"render_calibration`": $renderCalibration,`n" + $raw.Substring($insertAt)
    }

    # upright_sit is the one mode whose creature stands ON the floor with the item pivoted about
    # its centre, so it is the one that needs the measured pivot. floor_sit lies flat (its sprite
    # plane IS the sand), and planted authors its own plant_depth.
    if ($mode -eq "upright_sit") {
        $pivotPattern = [regex]'(?m)^(\s*)"pivot_fraction"\s*:\s*[0-9.]+\s*(,?)\s*$'
        if ($pivotPattern.IsMatch($newRaw)) {
            $newRaw = $pivotPattern.Replace($newRaw, "`$1`"pivot_fraction`": $pivotFraction`$2", 1)
        } else {
            # Insert as the first field of the animation block, right after its "mode" line, so the
            # file's existing formatting and field order are otherwise untouched.
            $modePattern = [regex]'(?m)^(\s*)"mode"\s*:\s*"upright_sit"\s*,?\s*$'
            $m = $modePattern.Match($newRaw)
            if ($m.Success) {
                $indent = $m.Groups[1].Value
                $insertAt = $m.Index + $m.Length
                $eol = if ($newRaw.Substring($insertAt).StartsWith("`r`n")) { "`r`n" } else { "`n" }
                $newRaw = $newRaw.Substring(0, $insertAt) + $eol + $indent + "`"pivot_fraction`": $pivotFraction," + $newRaw.Substring($insertAt)
            } else {
                Write-Warning "$name : upright_sit but no `"mode`" line found, pivot_fraction not written"
            }
        }
    }

    [System.IO.File]::WriteAllText($file.FullName, $newRaw, (New-Object System.Text.UTF8Encoding($false)))

    $summary += [pscustomobject]@{
        Name        = $name
        Mode        = $mode
        Diagonal    = $usesDiagonalRotation
        CanvasFill  = [Math]::Round($operativeFraction, 2)
        Calibration = $renderCalibration
        Pivot       = if ($mode -eq "upright_sit") { $pivotFraction } else { "" }
    }
}

$summary | Sort-Object Calibration | Format-Table -AutoSize
Write-Output "Updated $($summary.Count) fish_profile files."
