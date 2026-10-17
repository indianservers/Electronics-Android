param([switch]$Android)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location -LiteralPath $repo
try {
    # Serialize Gradle invocations: JVM workers hold runtime JARs open on Windows.
    & .\gradlew.bat testDebugUnitTest --console=plain 2>&1 |
        Tee-Object -FilePath (Join-Path $PSScriptRoot 'jvm-run.log')
    if ($LASTEXITCODE -ne 0) { throw 'JVM validation failed; inspect jvm-run.log.' }
    Copy-Item -LiteralPath (Join-Path $repo 'app/build/test-results/testDebugUnitTest') -Destination (Join-Path $PSScriptRoot ('jvm-results-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))) -Recurse
    if ($Android) {
        & .\gradlew.bat connectedDebugAndroidTest '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.class=com.indianservers.circuitssimulator.AdvancedProjectsInstrumentedTest,com.indianservers.circuitssimulator.BoardHardwareInstrumentedTest,com.indianservers.circuitssimulator.ProjectStoreInstrumentedTest,com.indianservers.circuitssimulator.CircuitJsonInstrumentedTest' --console=plain 2>&1 |
            Tee-Object -FilePath (Join-Path $PSScriptRoot 'android-run.log')
        if ($LASTEXITCODE -ne 0) { throw 'Android validation failed; inspect android-run.log.' }
        $androidArchive=Join-Path $PSScriptRoot ('android-results-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
        New-Item -ItemType Directory -Force $androidArchive | Out-Null
        Get-ChildItem (Join-Path $repo 'app/build/outputs/androidTest-results/connected') -Recurse -Filter '*.xml' |
            Copy-Item -Destination $androidArchive
        $adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
        & $adb exec-out run-as com.indianservers.circuitssimulator cat files/advanced-project-results.json |
            Set-Content -LiteralPath (Join-Path $androidArchive 'advanced-project-results.json') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Could not retrieve Android project evidence.' }
        & (Join-Path $PSScriptRoot 'finalize-results.ps1')
    }
} finally { Pop-Location }
