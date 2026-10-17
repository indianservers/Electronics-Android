$ErrorActionPreference='Stop'
$primary=(Get-Content (Join-Path $PSScriptRoot 'android-archive-path.txt') -Raw).Trim()
if(!(Test-Path (Join-Path $primary 'run-method.json'))){throw 'Archive primary run before retry'}
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$taskDeviceEpochText=(& $adb -s emulator-5580 shell date +%s).Trim()
if($LASTEXITCODE -ne 0 -or $taskDeviceEpochText -notmatch '^\d+$'){throw 'Cannot record recovery start'}
@{deviceEpoch=[long]$taskDeviceEpochText;primaryArchive=$primary;apkInstallPerformed=$false;recordedHostUtc=(Get-Date).ToUniversalTime().ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'android-motion-recovery-start.json')
$taskMotionArgs=@('-s','emulator-5580','shell','am','instrument','-w','-r','-e','listener','com.indianservers.circuitssimulator.DeviceEvidenceListener','-e','class','com.indianservers.circuitssimulator.MotionSensorInstrumentedTest#gyroPropertiesEditRealMotionAndPreserveWiringWithUndo','com.indianservers.circuitssimulator.test/androidx.test.runner.AndroidJUnitRunner')
$taskMotionArgs | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'android-motion-recovery-command.json')
& $adb @taskMotionArgs *> (Join-Path $PSScriptRoot 'android-motion-recovery.log')
exit $LASTEXITCODE
