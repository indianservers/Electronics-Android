# Phase 5.A — Strengthening report

Hardening pass over Phase 5 only. Phase 6 was not started. No large unrelated feature set was added.

## 1. Initial bugs found

22 rows in `PHASE_5A_BUG_AUDIT.md` (P5A-01…22). The Phase 5 report’s Supported Subset claim matched the code. The remaining issues were real: invisible power after settle, analog/GPIO alias mistakes, ghost GPIO after stop, SPI without CS, missing `const` pins, floating-input false positives, global reset, background ticker, and stale PWM copy.

## 2. Critical bugs

- **P5A-01** `settle()` no longer forces `powered=true`. Power is re-checked from USB and rails every settle/reset.
- **P5A-02** `analogRead(0)` maps to **A0** on AVR. `digitalWrite(0)` still maps to **D0**.
- **P5A-03** NodeMCU numeric `5` / `GPIO5` maps to **D1**, not D5.

## 3. High-priority bugs

Ghost GPIO after stop, USB `part` getter, SPI CS filter, `const int` symbol extraction, INPUT_PULLUP exemption, PWM diagnostic wording, multi-board scheduler abort, global Reset MCU, background ticker.

## 4. Runtime corrections

- `FirmwareBoard.part` is a live getter (USB / deletion-safe).
- `resolve(value, analog)` distinguishes analog channels, AVR analog channel numbers, NodeMCU GPIO, Pico GP, ESP32 IO.
- `FirmwareSession.stop()` resets the MCU so outputs do not remain electrically active.
- `stopFirmware()` does a synchronous DC solve so the canvas updates immediately, then recalculates.

## 5. Scheduler fixes

- Background: `MainActivity.onStop` → `setForeground(false)`. The ticker still updates its timestamp so resume does not apply a wall-clock jump.
- Multi-board: one board’s instruction-budget error no longer `break`s the shared clock.
- Reset MCU calls `resetBoard(id)` only. Circuit Reset remains `resetTime()`.
- Runaway `while(true){}` stops with `Execution halted: program exceeded runtime instruction budget.`

## 6. GPIO fixes

- OUTPUT still Thevenin-drives the solver. Stop/reset clears modes to INPUT (no drive).
- `digitalWrite` clears PWM duty (verified).
- INPUT_PULLUP remains a 30 kΩ rail stamp, not a hidden boolean.

## 7. ADC fixes

- `analogRead(n)` uses analog-channel mapping.
- Quantization remains `floor(V/Vref * levels)` clamped to `0…levels-1` (1023 for 10-bit).

## 8. PWM fixes

- Uno/Nano D5/D6 = 980 Hz; other AVR PWM = 490 Hz; Mega D4/D13 = 980 Hz.
- Duty 0 / 255 and mid duties still produce HIGH/LOW frames plus reported duty.
- Mode change and reset disable the channel.

## 9. UART / I²C / SPI

- UART remains **Basic** event bus (no fake bit corruption).
- I²C scanner / environment temperature / duplicate-address return `4` unchanged and still pass.
- SPI now requires CS < 0.8 V. Unselected devices do not accept MOSI. Multiple CS-low records contention and does not merge MISO.

## 10. Sensors / displays / actuators

- LM35 / LDR still flow environment → stamped part → node → ADC/firmware.
- I²C temperature still prints environment °C, not a canned number.
- Servo remains 50 Hz pulse-driven. Motor/relay/display models were not rewritten; no new decorative peripherals.

## 11. Instruments

- Scope / FG / meter / analyzer code paths were audited, not replaced.
- FG square-wave transient test still passes.
- Serial pause still freezes the UI log; new output uses a bounded console and auto-scrolls when not paused.

## 12. Code editor

- `const` / `static` globals parse into assignments.
- Source symbols follow those aliases for pin highlighting and intelligence.
- Compile errors still carry line numbers for unsupported APIs and invalid PWM pins.

## 13. Power model

- USB Power is a visible board parameter (default on for placed boards).
- Compile no longer injects `usbPower=true` when the board’s USB switch is off.
- Unpowered board (USB off + 0 V rail) fails with an explicit unpowered message.

## 14. Multi-board

- Independent sessions on one virtual clock.
- Per-board reset. Shared UART fabric still exchanges when nets match.

## 15. Persistence

- Schema 4 firmware + environment unchanged. Source is still written onto the circuit document before compile.

## 16. UI

- Board inspector shows NodeMCU `D1 / GPIO5`.
- Serial send field uses `imePadding`; log is a `LazyColumn` that follows the last line.

## 17. Performance

No invented 10-minute memory numbers. Existing `PerformanceSmokeTest` still passes. Observations printed by that test are host-dependent and are not treated as budgets. Firmware ticker still only advances when the activity is foreground and firmware is running.

## 18. Regression

Featured MCU samples still come from `FirmwareExamples` (`SampleCircuits.blinkingLed/buttonLed/trafficLight/servoControl/temperatureMonitor`). No scripted-controller fallback was added.

## 19. Tests added

`PhaseFiveEmbeddedTest`: analogRead(0), NodeMCU GPIO5, const pin, pull-up, runaway budget, PWM after digitalWrite, stop ghost GPIO, Uno 980 Hz, unselected SPI.

`FirmwareLabInstrumentedTest.stopClearsLedDriveAndResetRerunsBlink`.

## 20. Tests run

- `testDebugUnitTest`: **171 tests, 0 failures, 0 errors, 0 skipped** (39 classes).
- `PhaseFiveEmbeddedTest`: **27 tests, 0 failures**.
- `connectedDebugAndroidTest` on emulator-5554 (Medium_Phone API 17): **31/31 passed**, including `FirmwareLabInstrumentedTest` blink golden and stop/reset.

## 21. Manual / emulator scenarios

On emulator-5554:

1. Load Blinking LED → Circuit Designer → Firmware Lab → Run.
2. Confirm Serial `LED on` / `LED off`, D13 HIGH/LOW frames, LED current toggling.
3. Stop: LED current drops; firmware not running.
4. Reset MCU → Run: blink resumes without leftover jobs.

Full Compose navigation sweep (Home / Components / Guide / Saved) was not re-clicked by hand in this pass; those screens were not the regression surface for the firmware fixes. Instrumented blink/stop/reset is the device evidence for the runtime.

## 22. Remaining limitations

- UART/I²C/SPI are event-based. Baud mismatch does not corrupt bytes.
- MicroPython indent parser is educational.
- Street Light is still a preview (not firmware).
- No breakpoints / step-debug of native C++.
- Multi-board still double-solves some slices (P5A-18).
- Wireless remains not simulated on Pico W / NodeMCU / ESP32.
- Oscilloscope aliasing at very high PWM vs sample rate is still a disclosed instrument limit.

## Support matrix (revalidated)

| Board | GPIO | ADC | PWM | UART | I²C | SPI | Runtime | Verified |
|---|---|---|---|---|---|---|---|---|
| Arduino Uno | Full | Full 10-bit | Full (D5/D6 980 Hz, others 490 Hz) | Basic | Basic | Basic | Supported subset | JVM golden A–H + emulator blink/stop/reset |
| Arduino Nano | Full | Full 10-bit (A6/A7 analog-only) | Full | Basic | Basic | Basic | Same AVR | JVM metadata + resolve |
| Arduino Mega | Full | Full 10-bit | Full (D4/D13 980 Hz) | Basic | Basic | Basic | Same runtime | JVM metadata |
| Pico | Full | Full 12-bit GP26–28 | Full | Basic | Basic | Basic | Arduino + MicroPython subsets | JVM languages |
| Pico W | Full | Full 12-bit | Full | Basic | Basic | Basic | Same as Pico | Wi-Fi not simulated |
| NodeMCU ESP8266 | Full (D / GPIO aliases) | Full 12-bit A0 | Basic | Basic | Basic | Basic | Arduino + MicroPython subsets | JVM GPIO5→D1 |
| ESP32 DevKit | Full (input-only / reserved metadata) | Full 12-bit | Full duty | Basic | Basic | Basic | Arduino + MicroPython subsets | JVM metadata |

**No capability was advertised as Full that is only Basic.** SPI stays Basic (now CS-correct). Wi-Fi stays Not simulated.

## Peripheral / instrument matrix

Unchanged honesty: sensors/displays/servo use the existing physical/protocol pipeline; instruments read solver traces; Serial terminal remains a protocol/UI surface, not a hidden global tap.
