<#
Launches an installed Forge 26.1.2 client from a directory produced by the Forge installer.

The Forge installer only writes Forge's own libraries and the merged Forge client jar; the vanilla
libraries and the parent version JSON normally come from the official launcher. This script fetches
the missing vanilla metadata/artifacts from Mojang, extracts the Windows natives, and starts the
client. It is validation tooling only and ships with nothing.

Usage:
  ./scripts/run-installed-forge-client.ps1 -ClientDir <dir> [-Username SmokeA] [-QuickPlay localhost:25603]
#>
param(
    [Parameter(Mandatory = $true)][string]$ClientDir,
    [string]$Username = "Dev",
    [string]$QuickPlay = "",
    [string]$McVersion = "26.1.2",
    [string]$ForgeVersion = "26.1.2-forge-64.1.0",
    [string[]]$SystemProps = @(),
    [string]$AssetsDir = (Join-Path $env:USERPROFILE ".gradle/caches/fabric-loom/assets"),
    [string]$AssetIndex = "26.1.2-30",
    [string]$Java = "",
    [switch]$PrepareOnly
)

$ErrorActionPreference = "Stop"
if (-not $Java) {
    $Java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/java.exe" } else { (Get-Command java -ErrorAction Stop).Source }
}
$client = (Resolve-Path $ClientDir).Path
$versions = Join-Path $client "versions"
$libraries = Join-Path $client "libraries"
$forgeVersion = $ForgeVersion
$mcVersion = $McVersion
$forgeJsonPath = Join-Path $versions "$forgeVersion\$forgeVersion.json"

if (-not (Test-Path $forgeJsonPath)) { throw "Forge version JSON not found: $forgeJsonPath" }
$forge = Get-Content $forgeJsonPath -Raw | ConvertFrom-Json

function Get-VanillaVersionJson {
    $target = Join-Path $versions "$mcVersion\$mcVersion.json"
    if (Test-Path $target) { return $target }

    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    $manifest = Invoke-RestMethod "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    $entry = $manifest.versions | Where-Object { $_.id -eq $mcVersion } | Select-Object -First 1
    if (-not $entry) { throw "Minecraft $mcVersion not found in the version manifest" }
    Invoke-WebRequest -Uri $entry.url -OutFile $target
    return $target
}

function Test-LibraryAllowed($lib) {
    if (-not $lib.rules) { return $true }
    $allowed = $false
    foreach ($rule in $lib.rules) {
        $matchesOs = (-not $rule.os) -or (-not $rule.os.name) -or ($rule.os.name -eq "windows")
        if ($matchesOs) { $allowed = ($rule.action -eq "allow") }
    }
    return $allowed
}

function Save-Artifact($relativePath, $url) {
    $dest = Join-Path $libraries ($relativePath -replace "/", "\")
    if (Test-Path $dest) { return $dest }
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    if (-not $url) { throw "Missing download URL for $relativePath" }
    Invoke-WebRequest -Uri $url -OutFile $dest
    return $dest
}

$vanillaJson = Get-VanillaVersionJson
$vanilla = Get-Content $vanillaJson -Raw | ConvertFrom-Json

$classpath = New-Object System.Collections.Generic.List[string]
$nativeJars = New-Object System.Collections.Generic.List[string]

foreach ($lib in @($vanilla.libraries) + @($forge.libraries)) {
    if (-not (Test-LibraryAllowed $lib)) { continue }
    if ($lib.downloads.artifact.path) {
        $path = Save-Artifact $lib.downloads.artifact.path $lib.downloads.artifact.url
        $classpath.Add($path)
    }
    if ($lib.natives -and $lib.natives.windows) {
        $classifier = $lib.natives.windows
        $nativePath = $lib.downloads.classifiers.$classifier.path
        $jar = Save-Artifact $nativePath $lib.downloads.classifiers.$classifier.url
        $nativeJars.Add($jar)
    }
}

$nativeDir = Join-Path $client "natives"
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
foreach ($jar in $nativeJars) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -eq "" -or $entry.FullName.StartsWith("META-INF/")) { continue }
        $dest = Join-Path $nativeDir $entry.Name
        if (-not (Test-Path $dest)) { [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true) }
    }
    $zip.Dispose()
}

if ($PrepareOnly) {
    Write-Output "Prepared $($classpath.Count) classpath entries and $($nativeJars.Count) native jars."
    return
}

$gameArgs = @(
    "--username", $Username,
    "--version", $forgeVersion,
    "--gameDir", $client,
    "--assetsDir", $AssetsDir,
    "--assetIndex", $AssetIndex,
    "--accessToken", "0",
    "--userType", "legacy",
    "--versionType", "release"
)
if ($QuickPlay) { $gameArgs += @("--quickPlayMultiplayer", $QuickPlay) }
$gameArgs += $forge.arguments.game

$jvmArgs = @(
    "-Xms512m", "-Xmx2G",
    "-Djava.library.path=$nativeDir",
    "-Djava.net.preferIPv6Addresses=system"
)
foreach ($prop in $SystemProps) { $jvmArgs += "-D$prop" }
$javaMajor = (& $Java -version 2>&1 | Select-Object -First 1) -replace '.*"(\d+).*', '$1'
if ([int]$javaMajor -ge 24) { $jvmArgs += "-XX:+UseCompactObjectHeaders" }

& $Java @jvmArgs -cp ($classpath -join ";") $forge.mainClass @gameArgs
