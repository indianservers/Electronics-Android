param([switch]$Android,[switch]$UseExistingJvmResults,[string]$JvmArchive,[string]$JvmFocusedArchive)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location -LiteralPath $repo
try {
    if (-not $UseExistingJvmResults) {
        Remove-Item Env:VALIDATION_BOARD -ErrorAction SilentlyContinue
        Remove-Item Env:VALIDATION_PROJECT -ErrorAction SilentlyContinue
        & .\gradlew.bat :app:testDebugUnitTest --console=plain 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'domain-full-jvm.log')
        if($LASTEXITCODE -ne 0){throw 'Full JVM regression failed.'}
    }
    if($JvmArchive) {$jvm=(Resolve-Path -LiteralPath $JvmArchive).Path} else {
        $jvm=Join-Path $PSScriptRoot ('domain-jvm-results-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
        Copy-Item -LiteralPath (Join-Path $repo 'app/build/test-results/testDebugUnitTest') -Destination $jvm -Recurse
    }
    $jvmArgs=@('--verify-jvm','--jvm',$jvm)
    if($JvmFocusedArchive){$jvmArgs+=@('--jvm-focused',(Resolve-Path -LiteralPath $JvmFocusedArchive).Path)}
    & python (Join-Path $PSScriptRoot 'domain-evidence.py') @jvmArgs
    if($LASTEXITCODE -ne 0){throw 'JVM regression has unresolved failures or incomplete coverage.'}
    & python (Join-Path $PSScriptRoot 'domain-evidence.py') --stage
    if($LASTEXITCODE -ne 0){throw 'Domain circuit staging failed.'}
    if($Android) {
        $classes='com.indianservers.circuitssimulator.DomainProjectsInstrumentedTest,com.indianservers.circuitssimulator.AdvancedProjectsInstrumentedTest,com.indianservers.circuitssimulator.BoardHardwareInstrumentedTest,com.indianservers.circuitssimulator.ProjectStoreInstrumentedTest,com.indianservers.circuitssimulator.CircuitJsonInstrumentedTest,com.indianservers.circuitssimulator.MotionSensorInstrumentedTest,com.indianservers.circuitssimulator.PhysicalArtworkInstrumentedTest'
        & .\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.listener=com.indianservers.circuitssimulator.DeviceEvidenceListener' "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --console=plain 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'domain-android.log')
        $testExit=$LASTEXITCODE
        $androidArchive=Join-Path $PSScriptRoot ('domain-android-results-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
        New-Item -ItemType Directory -Force $androidArchive | Out-Null
        Get-ChildItem (Join-Path $repo 'app/build/outputs/androidTest-results/connected') -Recurse -Filter '*.xml' | Copy-Item -Destination $androidArchive
        $adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
        foreach($name in @('domain-project-results.json','advanced-project-results.json','instrumented-device-results.xml')) {
            & $adb -s emulator-5556 exec-out run-as com.indianservers.circuitssimulator cat "files/$name" | Set-Content -LiteralPath (Join-Path $androidArchive $name) -Encoding utf8
            if($LASTEXITCODE -ne 0){throw "Could not export $name; retained APKs and XML remain available."}
        }
        Get-FileHash -Algorithm SHA256 (Join-Path $repo 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),(Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk') | Select-Object Path,Hash | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $androidArchive 'apk-hashes.json') -Encoding utf8
        if($testExit -ne 0){throw 'Android tests failed. Raw XML and direct device JSON were preserved.'}
        $finalArgs=@('--jvm',$jvm,'--android',$androidArchive)
        if($JvmFocusedArchive){$finalArgs+=@('--jvm-focused',(Resolve-Path -LiteralPath $JvmFocusedArchive).Path)}
        & python (Join-Path $PSScriptRoot 'domain-evidence.py') @finalArgs
        if($LASTEXITCODE -ne 0){throw 'Evidence validation failed.'}
    }
} finally {Pop-Location}
