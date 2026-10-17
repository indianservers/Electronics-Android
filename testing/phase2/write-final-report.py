"""Write the human report only from verified captured results."""
import json,csv
from pathlib import Path
r=Path(__file__).resolve().parent
s=json.loads((r/'phase2-verification-summary.json').read_text(encoding='utf-8'))
rows=list(csv.DictReader((r/'phase2-project-results.csv').open(encoding='utf-8',newline='')))
c=s['classificationCounts'];a=Path(s['androidArchive'])
perf=[];draw_table=[]
for p in (a/'visual-qa').rglob('*performance.json'):
    perf.append(f'- `{p.relative_to(a).as_posix()}`: actual emulator measurements, retained unchanged.')
    if p.name=='performance.json':
        data=json.loads(p.read_text(encoding='utf-8'))
        if isinstance(data,list):
            draw_table=['| Components | Native draw mean (ms) | P95 (ms) | Process PSS (KiB) |','| ---: | ---: | ---: | ---: |']
            draw_table += [f"| {d['components']} | {d['nativeDrawMeanMs']:.3f} | {d['nativeDrawP95Ms']:.3f} | {d['processPssKb']} |" for d in data]
report=f'''# Phase 2 engineering validation results

Full application acceptance is **{s['fullApplicationAcceptance']}**. This campaign discovered {s['boards']} production Android board profiles and classified all {s['requestedProjects']} requested slots. It built and executed {s['executedModeledProjects']} saved modeled circuits on JVM and Android. The remaining {c.get('BLOCKED_UNMODELED_DOMAIN_FUNCTIONS',0)} slots have explicit domain blockers and contain specifications rather than fictitious circuits or firmware. No project is classified PASSED_FULL_SCOPE.

## Executed scope

| Classification | Projects |
| --- | ---: |
| PASSED_MODELED_SCOPE | {c.get('PASSED_MODELED_SCOPE',0)} |
| MODELED_SUBSYSTEM_ONLY | {c.get('MODELED_SUBSYSTEM_ONLY',0)} |
| BLOCKED_UNMODELED_DOMAIN_FUNCTIONS | {c.get('BLOCKED_UNMODELED_DOMAIN_FUNCTIONS',0)} |

Each board has 20 classified request slots and 16 executed modeled circuits. Project-specific circuits and firmware are in `projects/`; each specification names the actual supported function and omissions. Sensor placements total {s['sensorPlacements']}; {s['individuallyVerifiedSensorInstances']} are individually verified by the expanded per-instance harnesses. Placement count is not independent-coverage count. The inherited 152 domain projects do not establish full per-instance Phase 2 isolation/oracle coverage and remain subsystem-only. The greenhouse and building fixtures also lack complete actuator/plant behavior.

## Actual execution totals

| Evidence | Result |
| --- | --- |
| JVM modeled application projects | {s['phase2LatestUniqueProjectTests']} unique passing project tests |
| JVM project scenarios | {s['projectScenarios']['jvm']} |
| Android modeled application projects | {s['executedModeledProjects']} saved assets executed |
| Android project scenarios | {s['projectScenarios']['android']} |
| Recorded JVM project assertions | {s['recordedProjectAssertions']['jvm']} |
| Recorded Android project assertions | {s['recordedProjectAssertions']['android']} |
| Current-source Phase 1 JVM regression | {s['phase1JvmRegression']['tests']} tests, {s['phase1JvmRegression']['failures']} failures, {s['phase1JvmRegression']['errors']} errors, {s['phase1JvmRegression']['skipped']} skipped |
| Direct Android JUnit run | {s['android']['tests']} methods, {s['android']['failures']} failures, {s['android']['errors']} errors, {s['android']['skipped']} skipped |
| Supplemental domain and real-navigation lifecycle run | {s['androidTargetedRerun']['tests']} methods, {s['androidTargetedRerun']['failures']} failures |
| Unchanged motion screenshot recovery | {s['androidMotionRecovery']['tests']} method, {s['androidMotionRecovery']['failures']} failures; original primary failure retained |
| Accepted unique Android methods | {s['androidAcceptedUniqueMethods']} from {s['androidRawMethodExecutions']} raw method executions; original numeric-only lifecycle method superseded |
| Additional legacy domain projects on Android | {s['supplementalLegacyDomainRegression']['projects']} projects / {s['supplementalLegacyDomainRegression']['scenarios']} scenarios / {s['supplementalLegacyDomainRegression']['recordedSharedValidatorAssertions']} shared-validator assertions |
| Separate native AVR compilation | {s['nativeCompilationCounts'].get('COMPILED_NATIVE',0)} controller sketches compiled; CPU execution NOT_EXECUTED |
| Web unit regression | {s['webUnitRegression']['numPassedTests']}/{s['webUnitRegression']['numTotalTests']} passed |

Scenario totals refer to the 304 Phase 2 projects, excluding earlier aggregate-regression scenarios and prototype reruns. Assertion totals count actual explicit scenario assertions and dynamically executed shared-validator checks. They exclude internal engine guards and JUnit infrastructure. Older null JVM counters are reconciled against fresh current-source Phase 1 executions only after canonical circuit equality, scenario-list length and hashes are checked; the original raw results remain unchanged. See `jvm-assertion-reconciliation.json`. Inherited project-level counts do not imply complete per-assertion expected/observed traces. A class-bytecode audit verifies that all earlier core classes are identical: only the added UI playback helper and ViewModel wiring differ. The current-source full regression and three dedicated playback tests separately exercise that repair; exact differences are retained in `jvm-runtime-class-reconciliation.json`.

## Interfaces, models and electrical evidence

The circuits exercise I2C transactions with physical pull-ups and modeled supply/ground checks, ADC sampling with actual conditioning and shunt/load topologies, UART waveforms and baud faults, GPIO/PIR interfaces, generic ultrasonic trigger/echo pulse measurement, and selected SPI/CAN vehicle communication. Detailed scenarios retain actual readback, node voltages, timeouts and protocol diagnostics where supported. CAN is a custom standard-frame exchange, not OBD-II compliance or exhaustive CAN arbitration/error behavior.

The new BH1750FVI model implements the documented nominal default-MTreg high-resolution subset with conversion/reset behavior and an explicit carrier pin mapping. SCD41 CO2/temperature/humidity are actually read and CRC-checked. Generic PIR/ultrasonic devices remain labeled generic. Reviewed manufacturer parameters and approximations are documented in the sensor matrix, retained PDFs and provenance files. The 150-kind registry export is an inventory, not full manufacturer certification. Ideal rails and educational peripheral current loads do not establish power integrity, regulator current limits, tolerance distributions or physical damage predictions.

The parking project contains two independent sonar bays and eight thermal channels. Its 20 us runtime polling creates an explicit numerical quantization bound. It does not represent ten parking bays or an acoustic propagation/crosstalk model. Fire, medical, motor, energy, disaster, vehicle and robotics labels are bounded by each recorded subsystem scope; missing smoke/flame, patient physiology, degradation physics, AC metering, propagation, OBD-II and mechanical feedback are not claimed.

## Android and rendering

The direct AndroidJUnitRunner logs and actual on-device listener callbacks agree on every started/finished method. All 304 APK circuit asset hashes match the JVM circuits and device result records. Installed APK bytes were read back and checked against each run's frozen build APKs. The primary run includes the earlier 380-project aggregate test, board mapping, persistence/JSON, motion and physical artwork suites, plus the new 304-project suite. The supplemental run executes all ten original domain groups and their 190 actual circuits, completing the full original Android regression suite. These additional legacy runs are reported separately and do not inflate the 304 Phase 2 design/scenario/assertion totals.

The primary run retains one real failure: Android PixelCopy timed out while capturing the motion-properties test. The unchanged method then passed all checks, including its fresh screenshot and final specifications assertion, on the same app/test APKs. The recovered screenshot was manually reviewed. No production correction or root-cause claim is made for this intermittent capture result; its original raw failure remains marked in the primary archive.

Visual review also rejected the primary run's numerically passing lifecycle fixture because direct setContent bypassed the app's saved navigation. It is superseded by the separately recorded real Circuit Designer navigation and visible-workspace checks. The 55 accepted primary methods, one unchanged motion recovery and 11 supplemental methods form 67 accepted unique checks from 69 raw method executions. No raw JUnit XML is edited or synthesized. The production APK is byte-identical across these runs; test APKs and installed readbacks are independently retained. See `android-acceptance-reconciliation.json`. Earlier ANR-overlay and frame-budget attempts remain under `failures/` and are not accepted as passing lifecycle evidence.

The accepted lifecycle test records actual orientation changes, activity recreation, background/foreground transitions and retained ten-sensor readings, with the actual workspace visible after every transition. It retains expected/observed values for all ten channels at {s['androidLifecycleReadingEvidence']['checkpoints']} checkpoints ({s['androidLifecycleReadingEvidence']['recordedNumericComparisons']} numeric comparisons), separate from project assertion totals. Its four reviewed screenshots are under `{s['lifecycleRerunArchive']}/visual-qa/`; the primary fixture's earlier screenshots are superseded. Low-memory pressure and physical devices were not executed. Rendering and performance artifacts are retained with the primary device archive; emulator timings under an uncontrolled host workload are not claims about physical-device frame rate. Device qualification is limited to the recorded Android 15/API35 emulator.

The measured emulator configuration was {s['androidRuntimeConfiguration']['runtimeCpuCount']} virtual CPUs and {s['androidRuntimeConfiguration']['configuredRuntimeRamMiB']} MiB RAM; the guest kernel reported {s['androidRuntimeConfiguration']['kernelMemTotalKiB']:,} KiB total memory after reservations. Raw commands and results are retained in `runtime-memory-profile.json`.

The lifecycle visual review is bounded to workspace and reading retention. Dense default panels leave a small canvas area, and system-bar background/contrast changes after recreation remain unqualified visual limitations. These captures do not establish full-project small-screen legibility or continuous firmware cycle progression after every transition.

{chr(10).join(perf) if perf else 'No performance file was located; do not claim measured performance.'}

{chr(10).join(draw_table)}

The native bitmap draw measurements exclude GPU work and Compose recompositions. Median end-to-end project-validation suite time was {s['validationSuiteTimings']['jvm']['medianMillis']:.3f} ms on JVM and {s['validationSuiteTimings']['android']['medianMillis']:.3f} ms on Android. These suites include fault loops and export/report I/O; they are not real-time simulation speed measurements. Summed per-project durations overlap when workers run concurrently and are not campaign wall time. Raw per-project durations are retained in the project results table and records.

## Native and web results

Native targets are Arduino Uno, Nano, Mega, Leonardo and Micro with the provisioned Arduino AVR toolchain. Adaptations make setup state persistent, use explicit int32_t register arithmetic and SDK-specific APIs, and retain separate sketches/compiler logs/ELF/HEX files. All recorded source/sketch/binary hashes were checked. AVR floating-point output parity and native CPU/hardware execution remain unexecuted. Other board SDKs were not provisioned.

The corresponding web repository is `C:/Indian Servers/Electronics Lab`; its real registry contains 26 boards. The actual production importer rejected all 304 Android circuits at the JSON schema boundary. The other 76 designs have no executable Android circuit. A registry/source gap audit also found no matching LM75B, SCD41, INA219, LSM6DSOX, EZO, ZE07 or MPX5010 part models. BH1750 and ADXL345 catalog entries exist, but their presence does not establish register, timing, electrical or firmware parity. A JSON-envelope converter alone cannot execute the same circuits faithfully. See `phase2-web-results/part-model-gap-audit.json`. Therefore shared-circuit solver, firmware, fault and numerical parity are NOT_ESTABLISHED. The web's own 1182-test regression passed. All 12 browser smoke combinations (Chrome, Edge, Firefox and WebKit at three viewports) retain FAILED_VERIFICATION because the live session backend rejects the local origin via CORS. Page/tool/reload actions completed, but the console/network failures were not filtered or mocked. WebKit additionally emitted six page-error events referencing the same blocked session endpoint; Chrome, Edge and Firefox recorded zero page errors.

## Repairs and unresolved risks

`phase2-bugs-fixed.md` documents retained pre-fix traces and corrections for UART consecutive-print loss, unary precedence, blocking pulseIn behavior, UI playback quantum/frame-budget handling, the selected accelerometer-range oracle, BH1750 reset behavior, live-topology/navigation/asset-read fixtures and native AVR integer-width assumptions. `phase2-known-limitations.md` records all remaining domain/platform/qualification gaps.

Unimplemented irrigation/soil response, complete weather instrumentation, cell-state/balancing/protection feedback and production-flow designs block four categories on every board. Partial sensor diversity and inherited isolation/oracle coverage, missing actuator physics, partial manufacturer audit, incompatible web schema, backend CORS, unexecuted native CPUs and physical-device qualification remain release risks. Passing modeled executions do not resolve these requirements.

## Evidence locations

- Android raw archive: `{s['androidArchive']}`
- Accepted lifecycle rerun: `{s['lifecycleRerunArchive']}`
- Full JVM regression archive: `{s['regressionArchive']}`
- Initial project archive: `{s['initialProjectArchive']}`
- Affected project archive: `{s['affectedProjectArchive']}`
- Web raw records: `phase2-web-results/`
- Native compiler records and binaries: `native-compilation-results.json`, `native-sketches/`, `native-builds/`
- Preserved Phase 1 evidence: `phase1-preserved/` and original `testing/engineering-validation/` archives

The requested twelve deliverables are present in this directory. `phase2-projects.zip` packages actual circuits, firmware, specifications, result records, tables, manufacturer reference URLs/provenance/hashes, native artifacts and web results. Retained manufacturer PDFs are indexed separately in `manufacturer-datasheet-index.json`; full source documents and large raw JVM/Android archives remain separate for independent review. Existing release AAB and user MainActivity changes were preserved; only debug Android packages were built.
'''
(r/'phase2-final-report.md').write_text(report,encoding='utf-8')
print(r/'phase2-final-report.md')
