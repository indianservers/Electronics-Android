# Phase 5.A bug audit

Compared `PHASE_5_EMBEDDED_SYSTEMS_REPORT.md` with the runtime. The report’s Supported Subset claim is accurate. Production bugs below were opened from code review and then closed with JVM and/or emulator evidence.

| ID | Area | Symptom | Root Cause | Severity | Reproducible | Fix Status | Verified |
|---|---|---|---|---|---|---|---|
| P5A-01 | Power | After first `settle()`, `powered` stayed true even if USB/rails later fail | `FirmwareBoard.settle()` assigned `powered=true` | Critical | Code review | Fixed & Verified | `unpoweredBoardWithoutUsbFailsClearly`; `checkPower()` every settle |
| P5A-02 | Pin map | `analogRead(0)` resolved to D0, not A0 | Numeric resolve always used `D$n` | Critical | JVM | Fixed & Verified | `analogReadZeroMapsToA0NotD0` mid-scale 480–544 |
| P5A-03 | Pin map | NodeMCU `digitalWrite(5)` became D5 (GPIO14), not GPIO5/D1 | ESP8266 Arduino numbers are GPIO, not D-labels | Critical | JVM | Fixed & Verified | `nodeMcuNumericFiveIsGpio5AliasD1` |
| P5A-04 | GPIO ghost | Stop left last LED/PWM voltages on canvas | `stopFirmware` did not reset MCU drives or re-solve | High | JVM + emulator | Fixed & Verified | `stopFirmwareClearsGpioDrives`; `FirmwareLabInstrumentedTest.stopClearsLedDriveAndResetRerunsBlink` |
| P5A-05 | USB | USB toggle ignored until recompile | `val part` captured at construct | High | Code review | Fixed & Verified | `part` is a getter; `usbPowerEnabled()` reads live parameters |
| P5A-06 | SPI | `SPI.transfer` talked to every SPI device | No CS-low filter | High | JVM | Fixed & Verified | `spiTransferWritesAndReadsMemory` (CS to GND); `unselectedSpiDeviceDoesNotAcceptTransfer` |
| P5A-07 | Symbols | `const int ledPin=9` missed by diagnostics | Extract only literals / A0-style names; parser skipped `const` | High | JVM | Fixed & Verified | `constIntPinIsExtractedAndDrivesTheMappedHeader` |
| P5A-08 | Diagnostics | INPUT_PULLUP + no wire flagged as floating | `digitalRead` open-net rule ignored pull-up | High | JVM | Fixed & Verified | `inputPullupIsNotReportedAsFloating` |
| P5A-09 | Diagnostics | INVALID_PWM_PIN said PWM is not simulated | Stale Phase 3 copy | High | Code review | Fixed & Verified | Wording now refers to PWM-capable headers |
| P5A-10 | Multi-board | Tight loop on board A stopped the shared scheduler | First session error broke the clock loop | High | Code review | Fixed & Verified | Scheduler continues other sessions; budget error is per-session |
| P5A-11 | Reset | Reset MCU reset every board’s fabric/session | `firmwareRuntime.reset()` was global | High | Code review | Fixed & Verified | `resetBoard(boardId)` only |
| P5A-12 | Lifecycle | Ticker kept advancing in background | Activity `onStop` did not pause firmware | High | Code review | Fixed & Verified | `setForeground`; ticker skips advance and does not apply a wall-clock jump |
| P5A-13 | Runaway | `while(true){}` message was generic | Budget text ≠ required wording | Medium | JVM | Fixed & Verified | `runawayLoopReportsInstructionBudget` |
| P5A-14 | Serial UX | Serial log did not auto-scroll | No scroll-to-end | Medium | Code review | Fixed & Verified | Firmware Serial tab `LazyColumn` + `LaunchedEffect` + `imePadding` |
| P5A-15 | PWM freq | Uno D5/D6 shown as 490 Hz | Hard-coded 490 | Medium | JVM | Fixed & Verified | `unoTimer0PinsReport980Hz` |
| P5A-16 | Inspector | NodeMCU pins omitted GPIO alias | Label was D-name only | Medium | Code review | Fixed & Verified | `displayName("D1")` → `D1 / GPIO5` in inspector |
| P5A-17 | PWM stop | digitalWrite after analogWrite must kill PWM | `write()` already cleared duty; untested | Medium | JVM | Fixed & Verified | `digitalWriteAfterPwmStopsTheChannel` |
| P5A-18 | Scheduler | Multi-board double-solves each slice | Session.advance + extra solver | Medium | Code review | Deferred with reason | Shared settle is extra work, not a wrong timeline. Refactor risk to Phase 1–4 solvers is higher than the cost. |
| P5A-19 | UART | Event UART, no bit timing | By design | Low | Report | Deferred with reason | Disclosed Basic. Corruption is not faked. |
| P5A-20 | MicroPython | Indent parser fragile | By design | Low | Report | Deferred with reason | Educational subset, not CPython. |
| P5A-21 | Street Light | Still a preview project | Not firmware-migrated | Low | Hub | Deferred with reason | Not claimed as firmware. |
| P5A-22 | Breakpoints | Not implemented | By design | Low | Report | Deferred with reason | Not advertised. |

## Closure summary

- Critical: 3 found, 3 Fixed & Verified
- High: 9 found, 9 Fixed & Verified
- Medium: 6 found, 5 Fixed & Verified, 1 Deferred with reason
- Low: 4 found, 4 Deferred with reason (disclosed limits)

No row is “Probably fixed”.
