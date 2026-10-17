$ErrorActionPreference='Stop'
$validationRoot=$PSScriptRoot
$jvmArchive=Get-ChildItem -LiteralPath $validationRoot -Directory -Filter 'jvm-results-*' | Sort-Object Name -Descending | Select-Object -First 1
$androidArchive=Get-ChildItem -LiteralPath $validationRoot -Directory -Filter 'android-results-*' | Sort-Object Name -Descending | Select-Object -First 1
if (!$jvmArchive -or !$androidArchive) { throw 'Both archived execution results are required.' }
$jvm=[ordered]@{tests=0;failures=0;errors=0;skipped=0;suites=0}
$advancedTests=0;$complexTests=0
Get-ChildItem -LiteralPath $jvmArchive.FullName -Filter '*.xml' | ForEach-Object {
    [xml]$document=Get-Content -LiteralPath $_.FullName
    $jvm.tests += [int]$document.testsuite.tests
    $jvm.failures += [int]$document.testsuite.failures
    $jvm.errors += [int]$document.testsuite.errors
    $jvm.skipped += [int]$document.testsuite.skipped
    $jvm.suites++
    if ($document.testsuite.name -eq 'com.indianservers.circuitssimulator.AdvancedProjectsTest') { $advancedTests=[int]$document.testsuite.tests }
    if ($document.testsuite.name -eq 'com.indianservers.circuitssimulator.ComplexFiftyScenariosTest') { $complexTests=[int]$document.testsuite.tests }
}
$android=@(Get-Content -LiteralPath (Join-Path $androidArchive.FullName 'advanced-project-results.json') -Raw | ConvertFrom-Json)
$boards=@(Import-Csv -LiteralPath (Join-Path $validationRoot 'board-capabilities.csv'))
$required=$boards.Count*20
if ($jvm.failures -or $jvm.errors -or $jvm.skipped -or $jvm.tests -lt $required) { throw 'Full JVM execution has not passed.' }
if ($advancedTests -ne $required -or $complexTests -ne 50) { throw 'The full project matrix and interrupted integration regression must both be present.' }
if ($android.Count -ne $required -or @($android | Where-Object status -ne 'PASSED_MODELED_SCOPE').Count) { throw 'Android project execution has not passed for every project.' }
$androidById=@{}
$android | ForEach-Object { $androidById[$_.project]=$_.status }
$projectRows=@(Import-Csv -LiteralPath (Join-Path $validationRoot 'project-records.csv'))
if ($projectRows.Count -ne $required) { throw 'Project coverage count does not match the board registry.' }
foreach ($row in $projectRows) {
    $id=$row.board_id.ToLowerInvariant()+'-'+$row.project_number.PadLeft(2,'0')
    $status=Get-Content -LiteralPath (Join-Path $validationRoot "projects/$id/result.txt") -TotalCount 1
    if ($status -ne 'PASSED_MODELED_SCOPE') { throw "Missing successful JVM project evidence: $id" }
    $row.status=$status
    $row.android_runtime=$androidById[$id]
    $specPath=Join-Path $validationRoot "projects/$id/spec.json"
    $spec=Get-Content -LiteralPath $specPath -Raw | ConvertFrom-Json
    $fullStatus=if ($spec.scope -match 'unsupported') { 'BLOCKED_UNMODELED_DOMAIN_FUNCTIONS' } else { 'MODELED_SUBSYSTEM_ONLY' }
    $row | Add-Member -NotePropertyName modeled_execution_status -NotePropertyValue $status -Force
    if ($fullStatus -eq 'BLOCKED_UNMODELED_DOMAIN_FUNCTIONS') { $row.status='BLOCKED' }
    $row | Add-Member -NotePropertyName full_project_status -NotePropertyValue $fullStatus -Force
    $row | Add-Member -NotePropertyName full_project_limitation -NotePropertyValue $spec.scope -Force
    $spec | Add-Member -NotePropertyName fullDomainStatus -NotePropertyValue $fullStatus -Force
    $spec | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $specPath -Encoding utf8
}
$projectRows | Export-Csv -LiteralPath (Join-Path $validationRoot 'project-records.csv') -NoTypeInformation -Encoding utf8
$boardByProduct=@{}
$boards | ForEach-Object { $boardByProduct[$_.product]=$_.board_id }
$boardRows=@(Import-Csv -LiteralPath (Join-Path $validationRoot 'board-results.csv'))
foreach ($row in $boardRows) {
    $prefix=$boardByProduct[$row.board].ToLowerInvariant()+'-'
    $count=@($android | Where-Object { $_.project.StartsWith($prefix) -and $_.status -eq 'PASSED_MODELED_SCOPE' }).Count
    if ($count -ne 20) { throw "Incomplete Android board coverage: $($row.board)" }
    $row | Add-Member -NotePropertyName android_passed_modeled_scope -NotePropertyValue $count -Force
    $row | Add-Member -NotePropertyName validation_scope -NotePropertyValue 'THERMAL_OCCUPANCY_SUBSYSTEM_ONLY' -Force
    $blocked=@($projectRows | Where-Object { $_.board_id -eq $boardByProduct[$row.board] -and $_.full_project_status -eq 'BLOCKED_UNMODELED_DOMAIN_FUNCTIONS' }).Count
    $row | Add-Member -NotePropertyName full_domain_blocked -NotePropertyValue $blocked -Force
    $modeled=@($projectRows | Where-Object { $_.board_id -eq $boardByProduct[$row.board] -and $_.modeled_execution_status -eq 'PASSED_MODELED_SCOPE' }).Count
    $row | Add-Member -NotePropertyName modeled_subsystem_passed -NotePropertyValue $modeled -Force
    $row.passed=$modeled-$blocked
    $row.blocked=$blocked
}
$boardRows | Export-Csv -LiteralPath (Join-Path $validationRoot 'board-results.csv') -NoTypeInformation -Encoding utf8
$scenarios=0;$checks=0
Get-ChildItem -LiteralPath (Join-Path $validationRoot 'projects') -Recurse -Filter result.txt | ForEach-Object {
    Get-Content -LiteralPath $_.FullName | Where-Object { $_ -match '^[^,]+,[0-9]+,[0-9]+$' } | ForEach-Object {
        $scenarios++;$checks += [int]($_ -split ',')[1]
    }
}
$summary=[ordered]@{
    generatedAtUtc=[DateTimeOffset]::UtcNow.ToString('o')
    validationScope='THERMAL_OCCUPANCY_SUBSYSTEM_ONLY'
    fullDomainValidation='NOT_CLAIMED'
    boards=$boards.Count; projects=$required; modeledSensorPlacements=$required*10
    jvm=$jvm; projectScenarios=$scenarios; projectAssertions=$checks
    androidPassedProjects=$android.Count; androidProjectScenarios=($android | Measure-Object scenarios -Sum).Sum
    androidEvidenceType=if($android[0].evidenceType){$android[0].evidenceType}else{'DIRECT_PER_PROJECT_REPORT'}
    physicalHardwareValidation='NOT_EXECUTED'
    fullDomainBlockedProjects=@($projectRows | Where-Object full_project_status -eq 'BLOCKED_UNMODELED_DOMAIN_FUNCTIONS').Count
    jvmArchive=$jvmArchive.Name; androidArchive=$androidArchive.Name
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $validationRoot 'verification-summary.json') -Encoding utf8
Compress-Archive -LiteralPath (Join-Path $validationRoot 'projects') -DestinationPath (Join-Path $validationRoot 'advanced-projects.zip') -CompressionLevel Optimal -Force
$summary | ConvertTo-Json -Depth 6
