$ErrorActionPreference='Stop'
$archive=(Get-Content (Join-Path $PSScriptRoot 'domain-android-direct-archive-path.txt') -Raw).Trim()
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$logPath=Join-Path $archive 'device-testrunner.log'
$snapshot=& $adb -s emulator-5556 logcat -d --pid=11573 -s TestRunner:I
if($LASTEXITCODE -ne 0){throw 'Device log transport unavailable; previous snapshots remain intact.'}
$previous=if(Test-Path -LiteralPath $logPath){Get-Content -LiteralPath $logPath}else{@()}
@($previous)+@($snapshot) | Select-Object -Unique | Set-Content -LiteralPath $logPath -Encoding utf8
$merged=Get-Content -LiteralPath $logPath -Raw
if($merged -notmatch 'run finished: 50 tests,') {
    $alive=& $adb -s emulator-5556 shell pidof com.indianservers.circuitssimulator
    if($LASTEXITCODE -ne 0 -or '11573' -notin (($alive -join ' ') -split '\s+')) {
        throw 'Original instrumentation PID11573 is no longer alive; completion evidence is missing.'
    }
}
foreach($name in @('domain-project-results.json','advanced-project-results.json')) {
    $raw=& $adb -s emulator-5556 exec-out run-as com.indianservers.circuitssimulator cat "files/$name" 2>$null
    if($LASTEXITCODE -ne 0){continue}
    try {$rows=($raw -join "`n")|ConvertFrom-Json -ErrorAction Stop} catch {continue}
    $raw | Set-Content -LiteralPath (Join-Path $archive $name) -Encoding utf8
    $counts=$rows|Group-Object status|Select-Object Name,Count
    Write-Output "$name : $($counts|ConvertTo-Json -Compress)"
    $rows|Select-Object -Last 1 project,status,elapsedMillis|ConvertTo-Json -Compress
}
Get-Content -LiteralPath $logPath -Tail 2
