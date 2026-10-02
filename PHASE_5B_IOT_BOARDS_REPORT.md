# Phase 5.B — IoT / development-board report

Phase 5.B strengthens datasheet-backed board identity. Phase 6 was not started.

## 1. Original boards
Arduino Uno R3, Arduino Nano, Arduino Mega 2560, Raspberry Pi Pico, Raspberry Pi Pico W, NodeMCU ESP8266, ESP32 DevKitC V4.

## 2. Boards audited
Those seven, plus catalog/runtime/inspector/projects/guide consumers. See `PHASE_5B_BOARD_AUDIT.md`.

## 3. Board definitions corrected
Single `BoardDefinition` / `BoardPin` in `BoardModels.kt` + `BoardRegistry.kt`. UI, solver, firmware, diagnostics, and Projects all consume that map. Electrical pin **indices are unchanged** (saved `TerminalRef.index` stays valid). Visual layout uses `nx`/`ny`.

## 4. Boards added
| Kind | Product | Support |
|---|---|---|
| `WEMOS_D1_MINI` | LOLIN D1 Mini | Functional |
| `ESP32_C3_DEVKIT` | ESP32-C3-DevKitM-1 | Functional |
| `ARDUINO_NANO_33_IOT` | Nano 33 IoT (3.3 V SAMD21) | Functional |
| `RASPBERRY_PICO_2` | Pico 2 (RP2350, Pico-compatible header) | Functional |

## 5. Boards removed/hidden
None. Not added (would be decorative): ESP32-S3, ESP32-CAM, Leonardo, Due, MKR, micro:bit, XIAO, STM32, Teensy.

## 6. Board visual changes
Family-colored PCB, USB nub, optional antenna exclusion, MCU block, type-colored headers, onboard LED circle (non-wireable). Hit boxes use `boardWidth`/`boardHeight`; non-connectable pins skipped; larger board tap radius.

## 7. Pinout corrections
Uno: official digital/power walk + IOREF. Nano: DIP walk; A6/A7 analog-only. Mega: physical columns. Pico: official 40-pin + GP25 LED. Pico W: WL_LED. NodeMCU: D-label ↔ GPIO. ESP32: DevKitC V4 38-pin; 34–39 input-only; flash reserved. C3: official J1/J3. D1 Mini: 16-pin map.

## 8. Power-model changes
Per-board `PowerDomain` (USB/VIN/VBUS/VSYS/5V/3V3). USB Power remains explicit. Runtime still refuses to start when unpowered. Regulator path is educational, not SPICE.

## 9. ADC changes
Board `adcBits` + `adcReferenceVoltage` drive `EducationalMcu`. Pico GP26–28 / A0–A2. ESP32 Arduino A0=VP (GPIO36). NodeMCU/D1 Mini A0 (D1 Mini 3.2 V max). Nano 33 12-bit 3.3 V.

## 10. PWM changes
Uno/Nano 3,5,6,9,10,11 only. Mega 2–13,44–46. Pico almost all GP. ESP32 LEDC on output GPIOs. NodeMCU all GPIO except D0. `analogWrite` rejected on non-PWM pins.

## 11. UART changes
Default Serial pair per board. Mega inspector lists Serial1/2/3 (D18/19, D16/17, D14/15) as hardware; runtime still drives Serial on D1/D0.

## 12. I²C changes
Uno A4/A5 and SDA/SCL headers share nets. Mega D20/D21. ESP32 default IO21/IO22 (other GPIO MCU-capable; runtime uses defaults). Pico GP0–23 tagged MCU-capable.

## 13. SPI changes
Uno D11–13/D10. Mega D50–53. ESP32 default 18/19/23/5. NodeMCU D5–D8.

## 14. Runtime changes
`FirmwareBoard.resolve` uses `BoardDefinition.resolve`. `LED_BUILTIN` is per-board. Languages follow `BoardFamily`. Wi-Fi/BLE: hardware metadata, simulator METADATA/UNSUPPORTED. No cloud/MQTT.

## 15. Datasheet sources
`PHASE_5B_BOARD_REFERENCES.md`. No fabricated revisions. No PDFs embedded.

## 16. Diagnostics added
Exact-pin 5 V-on-3.3 V wording; input-only output reject; unsupported PWM; boot/strapping when wired; Mega TX aliases; `BoardReplacement` compare (no auto-migrate).

## 17. Migrations
Kind names unchanged (`ESP32_DEVKIT`, `ARDUINO_UNO`, …). No ID rename. New kinds are additive. Pin indices stable; IOREF/GP25/WL_LED appended.

## 18. Tests
JVM `testDebugUnitTest`: **180 passed, 0 failed**. New `PhaseFiveBBoardTest` (aliases, pin maps, ADC/PWM, power, replacement, diagnostics). Instrumented re-run on emulator-5554 after catalog count fix.

## 19. Manual / emulator verification
emulator-5554 (Medium_Phone API 17). Instrumented suite executed (33 tests). Firmware lab, Circuit JSON, intelligence, and new board save/reload instrumented cases included. Explore UI focus flake addressed by removing `closeSoftKeyboard`.

## 20. Remaining limitations
- Mega Serial1/2/3 are labeled, not independently executed.
- ESP32/Pico I²C/SPI/UART remapping UI is not a full pin-matrix editor; inspector shows defaults vs MCU capability.
- Wi-Fi/BLE/network are not simulated.
- DAC is hardware-only (not faked as PWM).
- Onboard RESET/BOOT buttons are not interactive bootloader simulations.
- ICSP is not a separate connector (SPI is D11–13/D10).
- VIN regulation is educational.
- Board comparison is a compatibility summary, not a silent remapper.
- Photorealistic silkscreen is avoided for readability.

## Support matrix

| Board | Visual | Pinout | Power | GPIO | ADC | PWM | UART | I²C | SPI | Firmware | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| UNO R3 | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Arduino subset | **Full** (wired GPIO/ADC/PWM) |
| Nano | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Arduino subset | **Full** |
| Mega 2560 | Functional | Full | Simplified | Full | Full | Full | Basic* | Basic | Basic | Arduino subset | **Functional** (extra UARTs metadata) |
| Pico | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Arduino + MP subset | **Full** |
| Pico W | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | same + Wi-Fi metadata | **Functional** |
| NodeMCU V1.0 | Functional | Full | Simplified | Full | Full | Basic | Basic | Basic | Basic | Arduino + MP | **Functional** |
| ESP32-DevKitC V4 | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Arduino + MP | **Functional** |
| D1 Mini | Functional | Full | Simplified | Full | Full | Basic | Basic | Basic | Basic | Arduino + MP | **Functional** |
| ESP32-C3-DevKitM-1 | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Arduino + MP | **Functional** |
| Nano 33 IoT | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Arduino subset | **Functional** |
| Pico 2 | Functional | Full | Simplified | Full | Full | Full | Basic | Basic | Basic | Pico subset | **Functional** |

\* Mega extra UARTs: hardware yes, simulator Basic/metadata.

No board is marked Full unless GPIO/ADC/PWM/power/runtime were exercised. Mega/Pico W/ESP/C3/D1 Mini/Nano 33/Pico 2 stay Functional.

## High-risk fact check

| Board | Property | App value | Official source | Verified |
|---|---|---|---|---|
| UNO R3 | Logic | 5 V | Arduino A000066 datasheet | Yes |
| UNO R3 | PWM | D3, D5, D6, D9, D10, D11 | A000066 pinout | Yes |
| UNO R3 | I²C | A4/SDA and A5/SCL same nets | A000066 pinout | Yes |
| Nano | A6/A7 | Analog input only | A000005 pinout | Yes |
| Mega 2560 | UART | Serial D0/D1, Serial1 D18/D19, Serial2 D16/D17, Serial3 D14/D15 | A000067 pinout | Yes (runtime executes Serial only) |
| Pico | ADC | GP26–GP28 | Pico datasheet / R3 pinout | Yes |
| Pico | LED | GP25 | Pico datasheet | Yes |
| Pico W | LED | WL_GPIO0, not GP25 | Pico W datasheet | Yes |
| NodeMCU | D1 | GPIO5 | NodeMCU DEVKIT V1.0 README | Yes |
| D1 Mini | A0 max | 3.2 V | LOLIN D1 Mini docs | Yes |
| ESP32-DevKitC V4 | Input-only | GPIO34, 35, 36, 39 | Espressif ESP32 datasheet / DevKitC V4 guide | Yes |
| ESP32-DevKitC V4 | DAC | GPIO25/26 hardware only | ESP32 datasheet | Yes (not simulated) |
| ESP32-C3-DevKitM-1 | Headers | J1/J3 pin order | Espressif C3 DevKitM-1 guide | Yes |
| Nano 33 IoT | Logic | 3.3 V SAMD21, not 5 V Nano | Arduino Nano 33 IoT docs | Yes |

## Test counts

- JVM `testDebugUnitTest`: 180 passed, 0 failed.
- Emulator `connectedDebugAndroidTest` on emulator-5554 (Medium_Phone, API 17): 33 passed, 0 failed after the catalog expectation was updated from 57 to 61 boards-plus-parts and the Explore focus flake was removed.
- A later repeat in this session could not start: the Gradle wrapper tried to re-download `gradle-9.5.0-bin.zip` and the connection timed out.
