$ErrorActionPreference='Stop'
if(!(Test-Path (Join-Path $PSScriptRoot 'android-archive-path.txt'))){throw 'Archive the completed primary device run first'}
$primary=(Get-Content (Join-Path $PSScriptRoot 'android-archive-path.txt') -Raw).Trim()
if(!(Test-Path (Join-Path $primary 'run-method.json'))){throw 'Missing independently verified primary run'}
$taskPrimaryMethod=Get-Content (Join-Path $primary 'run-method.json') -Raw | ConvertFrom-Json
if($taskPrimaryMethod.recoveryRequiredFor){
    $taskRecoveryLink=Join-Path $primary 'motion-recovery.json'
    if(!(Test-Path -LiteralPath $taskRecoveryLink)){throw 'Archive the actual passing motion recovery first'}
    $taskRecovery=(Get-Content -LiteralPath $taskRecoveryLink -Raw | ConvertFrom-Json).archive
    $taskRecoveryMethod=Get-Content (Join-Path $taskRecovery 'run-method.json') -Raw | ConvertFrom-Json
    if($taskRecoveryMethod.status -ne 'PASSED_UNCHANGED_PIXELCOPY_RECOVERY' -or $taskRecoveryMethod.primaryArchive -ne $primary){throw 'Motion recovery does not reconcile the failed primary test'}
}
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$apk=Join-Path $PSScriptRoot 'lifecycle-device-build/app-debug-androidTest.apk'
if(!(Test-Path $apk)){throw 'Targeted APK has not been built'}
& $adb -s emulator-5580 install -r -t $apk
if($LASTEXITCODE -ne 0){throw 'Targeted test APK install failed'}
$taskDeviceEpochText=(& $adb -s emulator-5580 shell date +%s).Trim()
if($LASTEXITCODE -ne 0 -or $taskDeviceEpochText -notmatch '^\d+$'){throw 'Cannot capture supplemental device start time'}
@{deviceEpoch=[long]$taskDeviceEpochText;recordedHostUtc=(Get-Date).ToUniversalTime().ToString('o');testApkSha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant();scope='Captured after installing the supplemental test APK and before starting actual instrumentation'} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'android-supplemental-run-start.json')
$classes='com.indianservers.circuitssimulator.PhaseTwoLifecycleInstrumentedTest,com.indianservers.circuitssimulator.DomainProjectsInstrumentedTest'
$taskInstrumentArgs=@('-s','emulator-5580','shell','am','instrument','-w','-r','-e','listener','com.indianservers.circuitssimulator.DeviceEvidenceListener','-e','class',$classes,'com.indianservers.circuitssimulator.test/androidx.test.runner.AndroidJUnitRunner')
$taskInstrumentArgs | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'android-lifecycle-rerun-command.json')
& $adb @taskInstrumentArgs *> (Join-Path $PSScriptRoot 'android-lifecycle-rerun.log')
exit $LASTEXITCODE
