$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$serial='emulator-5580'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$device=(& $adb -s $serial emu avd name | Select-Object -First 1).Trim()
if($device -ne 'Codex_Circuits_Domain'){throw "Unexpected emulator: $device"}
& $adb -s $serial install -r (Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk')
if($LASTEXITCODE -ne 0){throw 'Application installation failed'}
& $adb -s $serial install -r (Join-Path $repo 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
if($LASTEXITCODE -ne 0){throw 'Test installation failed'}
$names=@('PhaseTwoLifecycleInstrumentedTest','PhaseTwoProjectsInstrumentedTest','AdvancedProjectsInstrumentedTest','BoardHardwareInstrumentedTest','ProjectStoreInstrumentedTest','CircuitJsonInstrumentedTest','MotionSensorInstrumentedTest','PhysicalArtworkInstrumentedTest')
$classes=($names | ForEach-Object {'com.indianservers.circuitssimulator.'+$_}) -join ','
$args=@('-s',$serial,'shell','am','instrument','-w','-r','-e','listener','com.indianservers.circuitssimulator.DeviceEvidenceListener','-e','class',$classes,'com.indianservers.circuitssimulator.test/androidx.test.runner.AndroidJUnitRunner')
$args | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'android-direct-command.json')
& $adb @args *> (Join-Path $PSScriptRoot 'android-direct-run.log')
exit $LASTEXITCODE
