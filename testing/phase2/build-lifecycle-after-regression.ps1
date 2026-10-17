$ErrorActionPreference='Stop'
$previous=(Get-Content (Join-Path $PSScriptRoot 'pre-playback-regression-archive-path.txt') -Raw).Trim()
$flag=Join-Path $PSScriptRoot 'regression-archive-path.txt'
while($true){
    $archive=if(Test-Path -LiteralPath $flag){(Get-Content -LiteralPath $flag -Raw).Trim()}else{''}
    if($archive -and $archive -ne $previous -and !(Test-Path -LiteralPath (Join-Path $archive 'FAILED.txt')) -and (Test-Path -LiteralPath (Join-Path $archive 'gradle-run.log'))){
        if((Get-Content (Join-Path $archive 'gradle-run.log') -Raw) -match 'BUILD SUCCESSFUL'){break}
    }
    Start-Sleep -Seconds 5
}
if(Test-Path -LiteralPath (Join-Path $archive 'FAILED.txt')){throw 'Current-source regression failed'}
if((Get-Content (Join-Path $archive 'gradle-run.log') -Raw) -notmatch 'BUILD SUCCESSFUL'){throw 'Regression did not complete successfully'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $repo
try{& .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon '-Dorg.gradle.jvmargs=-Xmx1024m' --console=plain *> (Join-Path $PSScriptRoot 'lifecycle-debug-build.log');$code=$LASTEXITCODE}
finally{Pop-Location}
if($code -ne 0){exit $code}
$out=Join-Path $PSScriptRoot 'lifecycle-device-build'
New-Item -ItemType Directory -Path $out -Force | Out-Null
Copy-Item (Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk') $out
Copy-Item (Join-Path $repo 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') $out
Copy-Item (Join-Path $repo 'app/src/androidTest/java/com/indianservers/circuitssimulator/PhaseTwoLifecycleInstrumentedTest.kt') $out
Copy-Item (Join-Path $repo 'app/src/androidTest/java/com/indianservers/circuitssimulator/DomainProjectsInstrumentedTest.kt') $out
$oldHash=(Get-FileHash (Join-Path $PSScriptRoot 'primary-device-build/app-debug.apk')).Hash
$newHash=(Get-FileHash (Join-Path $out 'app-debug.apk')).Hash
if($oldHash -ne $newHash){throw 'Production APK changed between primary and targeted test builds'}
Write-Output "Targeted lifecycle test APK ready: $out"
