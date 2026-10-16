param([string]$Repo = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$properties = @{}
Get-Content -LiteralPath (Join-Path $Repo 'gradle.properties') | ForEach-Object {
    if ($_ -match '^\s*(mod_version|minecraft_version)\s*=\s*(\S+)') { $properties[$matches[1]] = $matches[2] }
}
foreach ($loader in @('fabric', 'neoforge')) {
    $jar = Join-Path $Repo "$loader/build/libs/fishtastic-$loader-$($properties.mod_version)+$($properties.minecraft_version).jar"
    $archive = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $names = @($archive.Entries | ForEach-Object { $_.FullName })
        $excluded = @($names | Where-Object { $_ -match '^grill24/(fishsim/(harness|viewer|render)|fishtastic/client/selftest)/' })
        if ($excluded.Count) { throw "Development classes in ${loader}: $($excluded -join ', ')" }
        if ($names -notcontains 'grill24/fishsim/companion/MoodPresets.class') { throw "Missing MoodPresets in $loader" }
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notlike 'grill24/*.class') { continue }
            $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::Latin1)
            try { $classBytes = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($classBytes -match 'grill24/fishsim/(harness|viewer|render)/') {
                throw "Reference to excluded fishsim code: $loader $($entry.FullName)"
            }
            if ($entry.FullName -like 'grill24/fishtastic/client/ambient/*.class' -and
                    $classBytes -match 'grill24/fishtastic/client/selftest/') {
                throw "Ambient production class references self-test: $loader $($entry.FullName)"
            }
        }
        Write-Output "$loader packaging PASS: MoodPresets present; dev classes excluded; no excluded fishsim or ambient-to-selftest references"
    } finally { $archive.Dispose() }
}
