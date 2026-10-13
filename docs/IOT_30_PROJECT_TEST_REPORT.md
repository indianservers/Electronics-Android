# 30 IoT project scenario test report

Date: 12 October 2026

The 30 executable test fixtures cover local IoT sensing and control using the simulator virtual clock. They are test scenarios, not 30 new project cards. No physical hardware, Wi-Fi, cloud service, wall-clock latency, or Android UI validation was performed; no Android device was connected.

Initial run: 26 passed, 4 failed. All four motion fixtures failed to produce startup telemetry after session reset. I2C completion timestamps and waveform state survived the firmware clock reset.

Fix: FirmwareSession.reset now releases the resetting controller through ProtocolFabric.resetController. Its completion time, I2C route/held transaction, SPI configuration, UART endpoint/busy state, queued incoming deliveries and owned waveform edges are cleared. Peer controller state is preserved. ControllerResetTest verifies that isolation.

Final verification: all 30 scenarios passed. Full unit test suite and debug APK assembly passed.

Checks: analog transfer and quantized ADC serial readings before/after live stimulus changes; motion telemetry sign reversal over connected I2C; street-light LED current and console decisions after illuminance changes; irrigation servo PWM and valve decisions after potentiometer changes; reset behavior; finite solved node voltages and no firmware/solver errors. Analog scenarios use three operating points per sensor and a further live edit. Their application labels describe monitoring fixtures; complete cloud or alarm workflows are outside these tests.

| Scenario | Result |
| --- | --- |
| TMP35 Cold storage | PASS |
| TMP35 Room monitor | PASS |
| TMP35 Heat alarm | PASS |
| TMP36 Cold storage | PASS |
| TMP36 Room monitor | PASS |
| TMP36 Heat alarm | PASS |
| TMP37 Cold storage | PASS |
| TMP37 Room monitor | PASS |
| TMP37 Heat alarm | PASS |
| MCP9700 Cold storage | PASS |
| MCP9700 Room monitor | PASS |
| MCP9700 Heat alarm | PASS |
| MCP9701 Cold storage | PASS |
| MCP9701 Room monitor | PASS |
| MCP9701 Heat alarm | PASS |
| AD22100S Cold storage | PASS |
| AD22100S Room monitor | PASS |
| AD22100S Heat alarm | PASS |
| AD22103K Cold storage | PASS |
| AD22103K Room monitor | PASS |
| AD22103K Heat alarm | PASS |
| HIH-4030-001 Cold storage | PASS |
| HIH-4030-001 Room monitor | PASS |
| HIH-4030-001 Heat alarm | PASS |
| L3GD20 motion telemetry | PASS |
| LSM6DSOX motion telemetry | PASS |
| LIS3DH motion telemetry | PASS |
| ADXL345 motion telemetry | PASS |
| Automatic street lighting | PASS |
| Irrigation valve controller | PASS |

Total unit tests: 309

Reproduce: `gradlew.bat testDebugUnitTest assembleDebug`

Test sources: `IotThirtyProjectsTest.kt` and `ControllerResetTest.kt`. Gradle HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

