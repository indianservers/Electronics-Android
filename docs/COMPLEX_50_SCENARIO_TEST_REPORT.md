# 50 complex simulation scenarios

The executable fixtures in `ComplexFiftyScenariosTest.kt` cover five families of ten scenarios. Each has at least two microcontrollers and multiple sensor inputs or peripheral devices. The UART family also includes three-controller chains and Arduino Uno/Nano combinations. Five variants in every family reverse the controller enumeration order.

| Family | Scenarios | Configuration and assertions |
| --- | ---: | --- |
| Distributed sensor interlock | 10 | Two Uno controllers, two analog temperature/humidity sensors and two potentiometer inputs. Independently calculated ADC values, a four-iteration accumulator, nested boolean conditions, a wired remote interlock, LED current, inhibit transitions and reset recovery. |
| UART sensor relay | 10 | Two or three Uno/Nano controllers, temperature and limit inputs, binary control packets, conditional forwarding, receive counters and LED current. Five baud rates from 9,600 to 115,200. Live hot/inhibited transitions, sender reset and full reset. |
| Independent I2C networks | 10 | Two ESP32 controllers with separate buses, each carrying a temperature sensor and EEPROM. Identical addresses on independent nets, ACK results, signed half-degree samples, distinct persisted markers, live environment changes and reset isolation. |
| Multi-stage logic network | 10 | Two ESP32 controllers, four analog inputs, XOR/AND/NOT/OR gates, a multiplexer and a D flip-flop. The firmware clock has 200 microsecond LOW pulses and 2 ms HIGH intervals. Boolean and stored-bit results, actual gate/flip-flop voltages, LED current, live input transitions and resets. The combined variants cover all 16 input combinations. |
| Mixed I2C SPI monitoring | 10 | Two ESP32 controllers, a motion sensor, temperature sensor, EEPROM, SPI flash and two analog interlock inputs. Signed motion changes, temperature words, EEPROM ACK/data, JEDEC identity, compound alarm conditions, physical LED current and reset recovery. All four motion models are exercised. |

Every scenario also verifies equal microcontroller clocks, the expected advanced virtual time, finite solved node voltages and absence of firmware/electrical errors. These are automated simulator fixtures, not newly published project cards.

## Issues found and fixed

1. **Shared frames lost digital outputs.** The multi-controller scheduler ended each slice with an analog-only DC solve. Firmware could read a HIGH gate result while the final canvas frame reported 0 V at that gate. Shared frames now use the existing digital circuit session, preserving gate and sequential outputs.
2. **False backwards-clock diagnostics.** Repeated additions to the floating-point electrical clock could run slightly ahead of the scheduler's equivalent integer timestamp. The next shared observation then appeared to move backwards. Firmware board steps now derive their electrical time from integer microseconds.
3. **Peer controllers missed short clock pulses.** Executing one controller ahead for a whole 2 ms slice allowed it to finish a pulse before another controller observed it. Multi-controller execution now runs work due at the common timestamp and advances the electrical clocks together to the next firmware, bus, PWM or digital event. Flip-flop tests verify stored bits under both controller orders.

The dense solver also skips elimination for exact zero coefficients. Unconnected MCU pins create many such coefficients; skipping their arithmetic leaves the linear equations unchanged. `DenseLinearSolverTest` verifies a 100-node sparse system with floating-pin leakage, voltage-source pivoting and singular-system rejection.

The first fixture run had 40 failures. Thirty came from the test fixtures interpreting potentiometer position in the opposite direction to the model; those fixtures were corrected. The ten logic scenarios then exposed the shared-frame and clock defects. Adding stored-bit checks exposed the missed-pulse defect. This count does not represent 40 separate simulator bugs.

## Verification

Final verification: **50/50 complex scenarios passed**. The full suite completed with **362 tests, 0 failures, 0 errors and 0 skipped** across 55 test classes. Debug APK assembly succeeded in the same Gradle invocation.

Reproduce all tests and build the debug APK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Run only the 50 complex scenarios:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ComplexFiftyScenariosTest'
```

Gradle HTML results: `app/build/reports/tests/testDebugUnitTest/index.html`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Scope

Execution uses the simulator's deterministic virtual clock and educational device models. Wall-clock real-time performance, physical hardware accuracy, Wi-Fi/cloud networking and Android UI/device behavior were not validated. No Android device or emulator was connected. Protocol and circuit behavior beyond the supported firmware subsets is not implied.

## Individual results

Raw timings are recorded in [COMPLEX_50_SCENARIO_RESULTS.csv](COMPLEX_50_SCENARIO_RESULTS.csv). One I2C case recorded a 35,044.489-second wall-time outlier despite passing its functional checks. These elapsed measurements are observations, not a wall-clock performance guarantee.

| Scenario | Result | Recorded elapsed seconds |
| --- | --- | ---: |
| Distributed sensor interlock 1 | PASS | 1.004 |
| Distributed sensor interlock 2 | PASS | 0.908 |
| Distributed sensor interlock 3 | PASS | 0.99 |
| Distributed sensor interlock 4 | PASS | 0.958 |
| Distributed sensor interlock 5 | PASS | 0.923 |
| Distributed sensor interlock 6 | PASS | 1.014 |
| Distributed sensor interlock 7 | PASS | 0.995 |
| Distributed sensor interlock 8 | PASS | 0.928 |
| Distributed sensor interlock 9 | PASS | 0.888 |
| Distributed sensor interlock 10 | PASS | 0.897 |
| UART sensor relay 1 | PASS | 10.658 |
| UART sensor relay 2 | PASS | 3.231 |
| UART sensor relay 3 | PASS | 11.026 |
| UART sensor relay 4 | PASS | 3.228 |
| UART sensor relay 5 | PASS | 10.588 |
| UART sensor relay 6 | PASS | 3.142 |
| UART sensor relay 7 | PASS | 10.635 |
| UART sensor relay 8 | PASS | 3.13 |
| UART sensor relay 9 | PASS | 11.217 |
| UART sensor relay 10 | PASS | 3.433 |
| Independent I2C networks 1 | PASS | 26.245 |
| Independent I2C networks 2 | PASS | 26.736 |
| Independent I2C networks 3 | PASS | 35044.489 |
| Independent I2C networks 4 | PASS | 52.522 |
| Independent I2C networks 5 | PASS | 40.01 |
| Independent I2C networks 6 | PASS | 43.322 |
| Independent I2C networks 7 | PASS | 37.813 |
| Independent I2C networks 8 | PASS | 38.313 |
| Independent I2C networks 9 | PASS | 35.185 |
| Independent I2C networks 10 | PASS | 36.621 |
| Multi-stage logic network 1 | PASS | 3.533 |
| Multi-stage logic network 2 | PASS | 3.094 |
| Multi-stage logic network 3 | PASS | 3.307 |
| Multi-stage logic network 4 | PASS | 3.315 |
| Multi-stage logic network 5 | PASS | 3.226 |
| Multi-stage logic network 6 | PASS | 3.136 |
| Multi-stage logic network 7 | PASS | 3.001 |
| Multi-stage logic network 8 | PASS | 2.768 |
| Multi-stage logic network 9 | PASS | 2.966 |
| Multi-stage logic network 10 | PASS | 3.01 |
| Mixed I2C SPI monitoring 1 | PASS | 89.254 |
| Mixed I2C SPI monitoring 2 | PASS | 83.737 |
| Mixed I2C SPI monitoring 3 | PASS | 100.168 |
| Mixed I2C SPI monitoring 4 | PASS | 108.713 |
| Mixed I2C SPI monitoring 5 | PASS | 89.77 |
| Mixed I2C SPI monitoring 6 | PASS | 110.615 |
| Mixed I2C SPI monitoring 7 | PASS | 101.683 |
| Mixed I2C SPI monitoring 8 | PASS | 106.911 |
| Mixed I2C SPI monitoring 9 | PASS | 96.642 |
| Mixed I2C SPI monitoring 10 | PASS | 99.213 |
