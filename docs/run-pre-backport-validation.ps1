param(
    [string[]]$Scenes = @('ambientparity', 'ambientwater', 'ambientcontext', 'ambientpopulation',
        'ambientfloor', 'ambientwalk', 'ambientisland', 'ambientlifecycle',
        'companionbond,companionsim,companionparty,companioneel,companionscenes,companionworld,companionlod',
        'companionmoods,companionparticles,companionworldcost'),
    [switch]$SkipSuites
)

# Test runs and logs stay outside the tracked evidence. Both loaders run the same scene in their
# separate disposable worlds. Complete all builds before clients open the shared development jar.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repo
$javaHomeLine = Get-Content -LiteralPath (Join-Path $repo 'gradle.properties') |
    Where-Object { $_ -match '^org.gradle.java.home=' } | Select-Object -First 1
if (!$javaHomeLine) { throw 'No pinned Java toolchain in gradle.properties' }
$java = Join-Path ($javaHomeLine.Substring($javaHomeLine.IndexOf('=') + 1)) 'bin/java.exe'
if (!(Test-Path -LiteralPath $java)) { throw "Pinned Java is missing: $java" }
$out = Join-Path $repo ('build/cleanup/validation-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $out -Force | Out-Null
$wrapperArgs = @('-classpath', 'gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain')
$tasks = @(':fabric:assemble', ':neoforge:assemble')
if (!$SkipSuites) {
    $tasks = @(':fishsim:test', ':common:test', ':fabric:runGametest', ':neoforge:runGametest') + $tasks
}
& $java @wrapperArgs @tasks --console=plain *> (Join-Path $out 'suites-package.log')
if ($LASTEXITCODE -ne 0) { throw "Suites/build failed: $out/suites-package.log" }
& (Join-Path $PSScriptRoot 'check-cleanup-packaging.ps1') -Repo $repo |
    Tee-Object -FilePath (Join-Path $out 'packaging.txt')

$failed = $false
$summary = @()
foreach ($scene in $Scenes) {
    $tag = $scene.Split(',')[0]
    # A source edit between scenes must never make the parallel launches compile against a shared
    # output directory/jar while the other build is rewriting it. Prebuild both loaders in one
    # Gradle invocation, with no clients open. Unchanged inputs stay up to date.
    & $java @wrapperArgs :fabric:assemble :neoforge:assemble --console=plain *> (Join-Path $out "$tag-prebuild.log")
    if ($LASTEXITCODE -ne 0) { throw "Scene prebuild failed: $out/$tag-prebuild.log" }
    $jobs = foreach ($loader in @('fabric', 'neoforge')) {
        $log = Join-Path $out "$tag-$loader.log"
        Start-Job -ScriptBlock {
            param($repo, $java, $loader, $scene, $log)
            Set-Location -LiteralPath $repo
            & $java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain `
                ":${loader}:runClient" -I docs/fish-companions-twoclient/twoclient.init.gradle `
                -PtwoRole=solo "-PtwoLoader=$loader" "-PtwoScenes=$scene" --console=plain *> $log
            [pscustomobject]@{ Loader = $loader; ExitCode = $LASTEXITCODE; Log = $log }
        } -ArgumentList $repo, $java, $loader, $scene, $log
    }
    $jobs | Wait-Job | Out-Null
    foreach ($job in $jobs) {
        $result = Receive-Job -Job $job
        if (!$result -or $job.State -ne 'Completed') { throw "Validation job failed: $tag ($($job.State))" }
        $lines = Get-Content -LiteralPath $result.Log
        $passes = @($lines | Select-String '\[selftest\] CHECK .*: PASS').Count
        $failures = @($lines | Select-String '\[selftest\] CHECK .*: FAIL').Count
        $complete = @($lines | Select-String '\[selftest\] complete, stopping').Count
        $missing = @($scene.Split(',') | Where-Object {
            # This scene predates the scene-name convention for assertion labels.
            $prefix = if ($_ -eq 'companionbond') { 'bond' } else { $_ }
            $name = [regex]::Escape($prefix)
            !($lines | Select-String "\[selftest\] CHECK $name\.")
        })
        $ok = $result.ExitCode -eq 0 -and $passes -gt 0 -and $failures -eq 0 -and $complete -eq 1 -and $missing.Count -eq 0
        if (!$ok) { $failed = $true }
        $row = "$tag $($result.Loader) pass=$passes fail=$failures complete=$complete exit=$($result.ExitCode) missing=$($missing -join ',') ok=$ok"
        $summary += $row
        Write-Output $row
        $summary | Set-Content -LiteralPath (Join-Path $out 'summary.txt')
        Remove-Job -Job $job
    }
}
Write-Output "Evidence: $out"
if ($failed) { throw 'Self-test assertions failed or scenes did not finish; see summary.txt' }
