param([ValidateRange(1,3)][int]$ParallelForks=3,[switch]$LowMemory)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$repo=[IO.Path]::GetFullPath((Join-Path $root '..\..'))
$baseline=(Get-Content (Join-Path $repo 'testing/engineering-validation/domain-full-archive-path.txt')).Trim()
$classes=Get-ChildItem -LiteralPath $baseline -Filter '*.xml' | ForEach-Object {([xml](Get-Content $_.FullName -Raw)).testsuite.name} | Sort-Object -Unique
if($classes.Count -lt 60){throw 'Missing baseline test inventory'}
$arguments=@(':app:testDebugUnitTest','--console=plain')
if($LowMemory){$arguments+=@('--no-daemon','-Dorg.gradle.jvmargs=-Xmx1024m')}
foreach($name in $classes){$arguments+=@('--tests',$name)}
foreach($name in @('Bh1750ModelTest','UartContinuousPrintTest','FirmwareExpressionRegressionTest','PhaseTwoInventoryTest','FirmwarePlaybackTest')){
    $arguments+=@('--tests',('com.indianservers.circuitssimulator.'+$name))
}
$arguments | ConvertTo-Json | Set-Content (Join-Path $root 'phase1-regression-command.json')
Remove-Item Env:VALIDATION_BOARD -ErrorAction SilentlyContinue
Remove-Item Env:VALIDATION_PROJECT -ErrorAction SilentlyContinue
$env:VALIDATION_PARALLEL_FORKS=[string]$ParallelForks
@{parallelForks=$ParallelForks;lowMemory=[bool]$LowMemory;recordedUtc=(Get-Date).ToUniversalTime().ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $root 'phase1-regression-environment.json')
Push-Location $repo
$result=1
$invocationError=$null
try {
    & .\gradlew.bat @arguments *> (Join-Path $root 'phase1-regression-run.log')
    $result=$LASTEXITCODE
}catch {$invocationError=$_ | Out-String;$result=1}finally {Pop-Location}
$archive=Join-Path $root ('phase2-phase1-regression-results-'+(Get-Date -Format yyyyMMdd-HHmmss))
New-Item -ItemType Directory -Path $archive | Out-Null
Get-ChildItem (Join-Path $repo 'app/build/test-results/testDebugUnitTest') -Filter '*.xml' | Copy-Item -Destination $archive
Copy-Item (Join-Path $root 'phase1-regression-run.log') (Join-Path $archive 'gradle-run.log')
Copy-Item (Join-Path $root 'phase1-regression-command.json') $archive
Copy-Item (Join-Path $root 'phase1-regression-environment.json') $archive
if($invocationError){$invocationError | Set-Content (Join-Path $archive 'invocation-error.txt')}
Copy-Item (Join-Path $repo 'app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar') (Join-Path $archive 'compiled-production-classes.jar')
$domains=Join-Path $archive 'domain-project-results';New-Item -ItemType Directory -Path $domains | Out-Null
Get-ChildItem (Join-Path $repo 'testing/engineering-validation/domain-projects') -Directory | ForEach-Object {
    $target=Join-Path $domains $_.Name;New-Item -ItemType Directory -Path $target | Out-Null
    foreach($name in @('circuit.json','result.txt','assertion-count.json')){
        $path=Join-Path $_.FullName $name;if(Test-Path -LiteralPath $path){Copy-Item -LiteralPath $path -Destination $target}
    }
}
$advanced=Join-Path $archive 'advanced-project-results';New-Item -ItemType Directory -Path $advanced | Out-Null
Get-ChildItem (Join-Path $repo 'testing/engineering-validation/projects') -Directory | ForEach-Object {
    $target=Join-Path $advanced $_.Name;New-Item -ItemType Directory -Path $target | Out-Null
    foreach($name in @('circuit.json','firmware.ino','spec.json','scope.txt','result.txt')){
        $path=Join-Path $_.FullName $name;if(Test-Path -LiteralPath $path){Copy-Item -LiteralPath $path -Destination $target}
    }
}
if($result -ne 0){'FAILED: retain raw evidence; repair and rerun affected identities.' | Set-Content (Join-Path $archive 'FAILED.txt')}
$archive | Set-Content (Join-Path $root 'regression-archive-path.txt')
Write-Output $archive
exit $result
