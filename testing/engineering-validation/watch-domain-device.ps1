$ErrorActionPreference='Stop'
$archive=(Get-Content (Join-Path $PSScriptRoot 'domain-android-direct-archive-path.txt') -Raw).Trim()
$last=''
while($true) {
    try {
        $lines=& (Join-Path $PSScriptRoot 'collect-domain-device.ps1')
        $state=$lines -join "`n"
        if($state -ne $last){Write-Output $state;$last=$state}
        $log=Get-Content -LiteralPath (Join-Path $archive 'device-testrunner.log') -Raw
        if($log -match 'run finished: 50 tests, (\d+) failed, (\d+) ignored') {
            if($Matches[1] -ne '0' -or $Matches[2] -ne '0'){throw 'Completed device run has failures or ignored tests.'}
            break
        }
    } catch {
        if($_.Exception.Message -eq 'Completed device run has failures or ignored tests.'){throw}
        Write-Warning "Collector transport retry: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 45
}
$full=(Get-Content (Join-Path $PSScriptRoot 'domain-full-archive-path.txt') -Raw).Trim()
$focused=(Get-Content (Join-Path $PSScriptRoot 'domain-focused-archive-path.txt') -Raw).Trim()
& python (Join-Path $PSScriptRoot 'domain-evidence.py') --jvm $full --jvm-focused $focused --android $archive --android-logcat
exit $LASTEXITCODE
