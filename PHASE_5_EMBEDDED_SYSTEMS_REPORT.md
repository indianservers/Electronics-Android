# Phase 5 — Embedded Systems, Firmware, Peripherals & Advanced Instruments

Phase 5 is implemented as a **Supported Subset** runtime, not a Full Runtime AVR/ESP/RP2040 emulator. Static example code is not labeled executable. Phase 6 has not been started.

Evidence: `gradlew testDebugUnitTest` — **162 tests, 0 failures**. `FirmwareLabInstrumentedTest` — **passed on emulator-5554** (sdk_gphone16k_x86_64, API 17). Blink in the running app produced LED current, D13 high/low frames, and Serial `LED on` / `LED off` from executed statements.

## Audit (before replacement)

| Area | Before Phase 5 | After Phase 5 |
|---|---|---|
| Arduino subset AST interpreter | Real parser/IR, not keyword matching | Extended: while/for, Wire/SPI/Servo/tone/pulseIn/map/hex, library rejection |
| EducationalMcu GPIO/ADC/PWM | Thevenin drive, thresholds, 10-bit ADC, PWM edges | Unchanged electrical core; USB drive + environment-stamped sensors |
| FirmwareSession virtual clock | Real delays/PWM edges | Shared `RuntimeScheduler` for multiple boards |
| UART / I²C / SPI | Absent | Event-based `ProtocolFabric` |
| Featured MCU projects | Mixed / scripted risk | Blink, Button, Traffic, Servo, Temperature, I²C, UART use `FirmwareAttachment` |
| Instruments | Scope trigger/cursors, meter, FG | Auto/rising/falling, auto-scale, FG presets, logic analyzer + bus log |
| Persistence | Schema 3 | Schema 4: firmware + environment |

## Runtime architecture

```
firmware/
  FirmwareRuntime.kt     BoardRuntime, BoardSupport, CombinedAdvance, execution tiers
  RuntimeScheduler.kt    multi-board virtual clock + shared ProtocolFabric
  FirmwareBoard.kt       pin map, USB power, env-stamped settle, BoardRuntime
  FirmwareSession.kt     IR interpreter, Serial, Wire, SPI, Servo, pulseIn
  ArduinoSubset.kt       lexer/parser/validator
  MicroPythonSubset.kt   bounded indent parser → same IR
  ProtocolBuses.kt       UART/I²C/SPI events, devices, traces
  PeripheralRuntime.kt   ultrasonic echo, PIR, servo first-order, env apply
  SourceSymbols.kt       pin/API extraction
  FirmwareExamples.kt    executable examples + golden circuits
```

Electrical GPIO remains `EducationalMcu`. The solver and MCU share `FirmwareSession.advance` / `RuntimeScheduler.advance` on **simulation microseconds**, not Android wall-clock callbacks. The UI ticker only chooses how many virtual microseconds to request.

Tiers:

- **Full Runtime** — not claimed.
- **Supported Subset** — Arduino C++ subset on all boards; MicroPython subset on Pico / Pico W / ESP8266 / ESP32.
- **Static Example** — unused for shipped featured firmware projects.

## Languages and APIs

### Arduino subset (executable)

`pinMode`, `digitalWrite`, `digitalRead`, `analogRead`, `analogWrite`, `delay`, `millis`, `micros`, `map`, `constrain`, `hex`, `tone`, `noTone`, `pulseIn`, `Serial.begin/print/println/write/available/read`, `Wire.begin/beginTransmission/write/endTransmission/requestFrom/read/available`, `SPI.begin/transfer`, `Servo.attach/write/read`.

`if` / `else` / `while` / `for`, `int`/`long`/`float`/`double`/`bool`/`String`, `Servo name;`.

Rejected at compile with a line-numbered message: `#include <WiFi.h>` and other `unsupportedLibraries`, unknown APIs, wrong arity, PWM on a non-PWM pin (example: Uno `analogWrite(2, 128)`).

Not supported: arbitrary C++, Arduino Library Manager, WiFi/BLE, interrupts as callable ISRs, full Servo/Wire/SPI timing, `attachInterrupt`.

### MicroPython subset (Pico / ESP only)

`Pin`, `Pin.value`, `ADC.read` / `read_u16`, `PWM` duty/freq (via IR), `time.sleep` / `sleep_ms`, `print`, `I2C.scan` / `writeto` / `readfrom`, `UART.write` / `read`.

Not CPython. Indentation parser is educational and fragile. No REPL in this phase.

## Board support matrix

| Board | Language | GPIO | ADC | PWM | UART | I²C | SPI | Firmware runtime | Manual / device |
|---|---|---|---|---|---|---|---|---|---|
| Arduino Uno | Arduino subset | Full | Full 10-bit | Full 490 Hz | Basic | Basic | Basic | Supported subset | Emulator blink + JVM golden |
| Arduino Nano | Arduino subset | Full | Full 10-bit | Full | Basic | Basic | Basic | Same AVR mapping | JVM board metadata |
| Arduino Mega 2560 | Arduino subset | Full | Full 10-bit | Full | Basic (multi-UART metadata) | Basic | Basic | Same runtime | JVM board metadata |
| Raspberry Pi Pico | Arduino + MicroPython subsets | Full | Full 12-bit | Full | Basic | Basic | Basic | Supported subset | JVM languages |
| Pico W | Arduino + MicroPython subsets | Full | Full 12-bit | Full | Basic | Basic | Basic | Same as Pico | Wi-Fi **not simulated** |
| ESP8266 NodeMCU | Arduino + MicroPython subsets | Full | Full 12-bit | Basic | Basic | Basic | Basic | Supported subset | D-label / GPIO aliases in metadata |
| ESP32 DevKit | Arduino + MicroPython subsets | Full | Full 12-bit | Full LEDC-style duty | Basic | Basic | Basic | Supported subset | Input-only / reserved pins in metadata |

Disclosure in Board Inspector: GPIO/ADC bits, UART/I²C/SPI Basic, Wi-Fi Not simulated.

## GPIO / ADC / PWM / timers

- **GPIO** drives and samples **solved nodes**. Modes: INPUT (no strong drive), OUTPUT (≈50 Ω Thevenin), INPUT_PULLUP (≈30 kΩ to Vlogic). Thresholds: LOW ≤ 0.3·Vlogic, HIGH ≥ 0.7·Vlogic, else UNDEFINED (runtime error on `digitalRead`). Overcurrent / I²t damage remains in `EducationalMcu`; Phase 3 firmware diagnostics consume live `GpioReading`.
- **ADC** reads node voltage, clamps to `0…Vlogic`, quantizes to board bits (10 AVR / 12 others). Over-range produces a diagnostic when `analogRead` is used and node voltage exceeds logic voltage.
- **PWM** is time-varying digital output at configured Hz/duty. Oscilloscope/transient frames capture HIGH and LOW edges. Average DC is a consequence, not a substitute.
- **Timers** are the shared virtual clock: `delay`/`millis`/`micros`, PWM edges, servo 50 Hz pulses, ultrasonic echo width. No per-project timers.
- **Interrupts** are not exposed as working APIs. `pulseIn` is a cooperative wait on the virtual clock.

USB Power is a visible `usbPower` parameter (default on). Firmware will not run if the board is unpowered and USB is off.

## UART / I²C / SPI

Event-based educational buses. Baud/bit periods are **metadata only**.

| Peripheral | Runtime | Electrical model | Inspector | Diagnostics | Tested |
|---|---|---|---|---|---|
| UART | Byte queues on TX→RX nets | Weak TX/RX leakage; Serial Terminal TX/RX/GND | Serial Monitor + bus log | TX-TX / Serial TX open | JVM UART pair; blink Serial on device |
| I²C | Address, write, read, ACK/NACK, scan | SDA/SCL leakage; explicit pull-ups preferred | Bus tab | Missing pull-up (disclosed auto-logical fallback), duplicate address | Scanner + temp + conflict |
| SPI | `SPI.transfer` to selected devices | CS/MOSI/MISO/SCK leakage | Bus tab / analyzer | Trace CS/TX/RX | Byte store/recall |
| Serial Terminal | UART peer | 3-pin component | Serial + bus | — | Wiring + fabric |
| I²C temp 0x48 | Register/word from environment °C | VCC/GND + SDA/SCL | Display/bus | Address conflict | JVM |
| I²C LCD / OLED | Text buffer from printable bytes | Same | Firmware display lines | — | Scanner discovery |
| I²C EEPROM | 256-byte map | Same | — | — | Model + rebuild |
| SPI memory | Sequential byte store | 6-pin | Trace | — | JVM |

I²C devices: temperature sensor, LCD backpack (text), SSD1306 text buffer, EEPROM. SPI: memory (and 74HC595 accepted as a SPI target). No fake catalog entries without bus behavior.

## Sensors, displays, motors

Environment panel appears only for relevant sensors: temperature, light, distance, motion, with Cold/Room/Hot and Dark/Indoor/Sun presets.

| Item | Path | Notes |
|---|---|---|
| LM35 | Environment °C → `temperatureC` → 10 mV/°C Thevenin → ADC | Firmware converts ADC to °C |
| LDR | Environment lux → resistance → divider | Solver, not a slider-to-variable |
| Potentiometer | Wiper position → voltage → ADC | Existing electrical pot |
| Button | Canvas closed → node → `digitalRead` | INPUT_PULLUP lab |
| Ultrasonic | TRIG edge → echo width from `distanceCm` | `pulseIn` can measure; acoustic physics omitted |
| PIR | `motion` + hold time → OUT drive | Environment flag |
| OLED / I²C LCD | I²C printable bytes → 32-char buffer | Not full graphics / HD44780 timing |
| Servo | 50 Hz pulse width → first-order angle | `Servo.write` → pulse, not a magic angle pin |
| DC motor / L293D / relay | Existing electrical models | PWM average speed; modest inertia already in motor model |
| Buzzer / tone | `tone` → 50% PWM at frequency | Indication; audio optional |

7-segment / parallel LCD / DHT-as-exact / keypad / stepper were not added as decorative fakes.

## Instrument support matrix

| Instrument | Core function | Live signal | Controls | Measurements | Tested |
|---|---|---|---|---|---|
| Oscilloscope | Multi-channel traces from solver frames | Circuit nodes | Auto/rising/falling trigger, auto-scale, cursors, FFT/XY | Δt, ΔV, stats, FFT peak | PWM high/low frames; FG square transient |
| Multimeter | Existing DC V/I/R/continuity | Probes | Mode | Live readings | Prior instrument tests |
| Function generator | Circuit source | Solver | Sine/square/triangle/saw/pulse + 1 Hz / 100 Hz / 1 kHz / 10 kHz presets | V/I | Square transient |
| Logic analyzer | Digital HIGH/LOW summary + protocol log | Firmware/solver frames | Open from instruments | Event decode (event-based) | Bus log in JVM UART/I²C/SPI |
| Serial Monitor | Runtime `Serial.*` | Firmware console | Pause, clear, send to RX | Timestamped by sim µs in bus log | Device blink; JVM Serial |
| Power / USB | USB toggle + VIN/5V wiring | Board power check | USB Power chip | Unpowered diagnostic | JVM unpowered test |

## Project migrations

| Project | Mode | Fallback |
|---|---|---|
| Blinking LED | Supported subset firmware | None — compile/runtime error if invalid |
| Push Button LED | Firmware + physical switch | None |
| Traffic Light | Firmware delays | None |
| Servo Control | Servo subset + ADC pot | None |
| Temperature Monitor | LM35 + environment + ADC + Serial | None |
| I2C Scanner | Wire scan | None |
| UART Link | Two Unos, shared fabric | None |
| Automatic Street Light / Smart Irrigation | Still Phase 4 preview / non-firmware | Not migrated |

## Tests

**JVM (`PhaseFiveEmbeddedTest` + `FirmwareRuntimeTest`)**

- A Blink — code → D13 → resistor → LED current
- B Button — pull-up + `digitalRead` → LED
- C Analog — pot midpoint 480–544; ground 0; 9 V clamp 1023
- D PWM — 0/25/50/75/100% electrical edges + reported duty
- E Servo — 50 Hz pulse on D9
- F Temperature — environment 27.3 °C → Serial ~27
- G UART — board A text → board B GPIO
- H I²C — scan 0x48/0x3C, env temperature word, address conflict 4
- SPI transfer 0xA1/0x23
- Traffic sequence from code
- Unsupported `WiFi.h` and invalid PWM pin
- Reset preserves source, clears console/time
- Unpowered + USB off fails clearly
- Code/LED pin mismatch diagnostics
- FG square on transient

**Device:** `FirmwareLabInstrumentedTest` on emulator-5554 — Firmware Lab open, Run, ≥1 s virtual time, LED on/off currents, D13 >3 V and <1 V, Serial on/off.

## Performance

Blink and UART pair stay in the 10 000-operation budget with `delay()`. PWM edge frames are capped (2500/advance, 4000 UI). Serial log 300 lines, bus log 400 events. Firmware advances on `Dispatchers.Default`. A simple blink does not add a second solver loop on the UI thread.

## Limitations and debt

- No native AVR/ESP/RP2040 instruction emulator.
- UART/I²C/SPI are not bit-accurate; no UART/I²C waveform decoder from pin edges.
- MicroPython indent parser and Pico blink example are best-effort.
- Multi-board scheduler still settles per session then again globally.
- Editor find/undo is the Compose text field; no GDB, no breakpoints, no 60 FPS line flash.
- Code→pin highlight is pin-list / symbol based, not a full editor token tap map.
- Serial plotter, REPL, Wi-Fi, BLE, `attachInterrupt`, Library Manager — not in this phase.
- Street Light / Irrigation remain non-firmware previews.

## Phase 6 readiness

Phase 5 acceptance for a **supported-subset** embedded lab is met: coordinated clock, real GPIO/ADC/PWM through the solver, Serial, basic UART/I²C/SPI, migrated core Arduino projects, golden tests, and one device blink run. Phase 6 should harden accuracy, UX, performance, and remaining instrument polish — not invent a full silicon emulator unless that is explicitly scoped.
