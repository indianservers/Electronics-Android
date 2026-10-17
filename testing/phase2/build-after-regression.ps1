$ErrorActionPreference='Stop'
$flag=Join-Path $PSScriptRoot 'regression-archive-path.txt'
while(!(Test-Path -LiteralPath $flag)){Start-Sleep -Seconds 5}
$archive=(Get-Content -LiteralPath $flag -Raw).Trim()
if(Test-Path -LiteralPath (Join-Path $archive 'FAILED.txt')){throw 'Regression failed; inspect and repair before Android build'}
$log=Get-Content -LiteralPath (Join-Path $archive 'gradle-run.log') -Raw
if($log -notmatch 'BUILD SUCCESSFUL'){throw 'Regression has not succeeded'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $repo
try{& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain *> (Join-Path $PSScriptRoot 'android-debug-build.log');$code=$LASTEXITCODE}
finally{Pop-Location}
Write-Output "Debug APK build exit: $code"
exit $code
