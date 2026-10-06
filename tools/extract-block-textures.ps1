<#
Extracts vanilla block textures from the Loom-cached Minecraft client jar into
tools/block_textures/, for the sticker sheet's "Load Block Folder" button.
The output folder is gitignored: Mojang's textures must not be committed.

  pwsh tools/extract-block-textures.ps1            # 26.1.2
  pwsh tools/extract-block-textures.ps1 -Version 1.21.1
#>
param([string]$Version = '26.1.2')
$ErrorActionPreference = 'Stop'

$jarDir = Join-Path $HOME ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/$Version"
$jar = Get-ChildItem $jarDir -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object Name -NotMatch 'sources' | Select-Object -First 1
if (-not $jar) { throw "No client jar for $Version under $jarDir. Run a Gradle build for that version first." }

$out = Join-Path $PSScriptRoot 'block_textures'
New-Item -ItemType Directory -Force $out | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$prefix = 'assets/minecraft/textures/block/'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $n = 0
    foreach ($entry in $zip.Entries) {
        if (-not $entry.FullName.StartsWith($prefix) -or -not $entry.FullName.EndsWith('.png')) { continue }
        if ($entry.FullName.Substring($prefix.Length).Contains('/')) { continue } # top level only
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $out $entry.Name), $true)
        $n++
    }
} finally {
    $zip.Dispose()
}
Write-Host "Extracted $n block textures from $($jar.Name) to $out"
