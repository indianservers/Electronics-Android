# Phase 5.B — Board audit (before changes)

Inspected catalog, `BoardRegistry`, canvas artwork, `FirmwareBoard.resolve`, board inspector, featured projects, and `BoardPlatformTest`. Official sources are listed in `PHASE_5B_BOARD_REFERENCES.md`.

No additional board kinds exist in the catalog beyond the seven below (no S3/C3/CAM/Leonardo/Due/MKR/micro:bit/Teensy/XIAO/STM32/D1 Mini yet).

## Visible boards

| Kind | Catalog title | Product string | MCU | Logic | Pins | Layout claim |
|---|---|---|---|---|---|---|
| ARDUINO_UNO | Arduino Uno R3 | UNO R3 | ATmega328P | 5.0 V | 29 | Sequential D0–D13 then power/analog |
| ARDUINO_NANO | Arduino Nano | Nano | ATmega328 | 5.0 V | 30 | Breadboard walk D1,D0,RESET… |
| ARDUINO_MEGA | Arduino Mega 2560 | Mega 2560 | ATmega2560 | 5.0 V | 78 | **GPIO-sorted**, notes admit “grouped for readability” |
| RASPBERRY_PICO | Raspberry Pi Pico | Pico | RP2040 | 3.3 V | 40 | Official 40-pin DIP walk |
| RASPBERRY_PICO_W | Raspberry Pi Pico W | Pico W | RP2040 | 3.3 V | 40 | **Identical pin list and artwork to Pico** |
| NODEMCU_ESP8266 | NodeMCU ESP8266 | DEVKIT V1.0 | ESP8266 | 3.3 V | 30 | Official DEVKIT walk; D/GPIO aliases after 5.A |
| ESP32_DEVKIT | ESP32 DevKitC V4 | ESP32-DevKitC V4 | ESP32-WROOM-32E | 3.3 V | 38 | DevKitC-style including flash pins |

## Cross-cutting defects

| ID | Finding | Severity |
|---|---|---|
| B5B-01 | All boards drawn as the same rounded rectangle + USB nub. Pin anchors are a generic left/right split, not board-shaped headers. | High |
| B5B-02 | Mega pin order is D0–D53 then analogs — not the physical headers. | High |
| B5B-03 | Uno missing IOREF; SDA/SCL are extra pins joined only by hardcoded A4/A5 nets in `Circuit.electricalConnections`. | Medium |
| B5B-04 | Pico `digitalWrite(25)` / `LED_BUILTIN` cannot resolve — GP25 (onboard LED) is not in the header list. | High |
| B5B-05 | Pico W reuses Pico artwork/pins; no antenna / wireless disclosure on the body. LED on Pico W is WL_GPIO0, not GP25. | High |
| B5B-06 | `displayName` only special-cases NodeMCU. Uno D13 does not show GPIO13; ESP32 IO21 does not show aliases. | Medium |
| B5B-07 | Firmware resolve hard-codes families instead of board aliases. New boards would miss mapping. | High |
| B5B-08 | Inspector is a flat pin table. No pinout filter, find-pin, specs/references, hardware-vs-simulator matrix. | Medium |
| B5B-09 | 3.3 V boards accept 5 V GPIO wiring with only a generic over-voltage diagnostic, not board-specific wording. | Medium |
| B5B-10 | ESP32 flash pins D0–D3/CMD/CLK are listed as header pins (correct for DevKitC V4 module edge) but labeled like user GPIO in places. | Medium |
| B5B-11 | NodeMCU SD-card pins S0–SK are reserved (good) but inspector still lists them as if useful. | Low |
| B5B-12 | No `LED_BUILTIN`, no onboard LED/button model, no USB connector as a power node. | Medium |
| B5B-13 | Support matrix claims GPIO/ADC/PWM Full for every board including NodeMCU PWM (D0 excluded) and ESP32 LEDC. Honest enough if inspector stays Basic on buses. | Low |
| B5B-14 | Pin hit radius is 26–32 px, but Mega/ESP pins are 13 px apart — easy mis-tap when zoomed out. | Medium |
| B5B-15 | Board rotation is enabled; generic anchors rotate, but there is no board-specific geometry test. | Medium |

## Per-board notes

### Arduino Uno R3 (A000066)
- PWM 3,5,6,9,10,11 — **correct**. UART D0/D1 — **correct**. SPI 10–13 — **correct**. I²C A4/A5 + duplicate SDA/SCL header — **present and netted**.
- Missing IOREF. Power header order in the list is not the physical JANALOG walk.
- Visual does not look like Uno (USB-B, barrel jack, ATmega328P, 16U2).

### Arduino Nano (A000005)
- A6/A7 analog-only — **correct**. Header walk matches common pinout PDF more closely than Uno.
- Still uses Uno-teal rectangle, not the compact DIP module.

### Arduino Mega 2560
- PWM set 2–13,44–46 — **correct**. UART 0/1/14–19 — **correct**. SPI 50–53 — **correct**. I²C 20/21 — **correct**.
- Serial1/2/3 exist in metadata only; runtime `serialPorts()` returns a single Serial pair.
- Visual/order are not the real Mega.

### Raspberry Pi Pico
- GP26–28 ADC — **correct**. GP25 LED missing. PWM marked on all GP — acceptable (RP2040 PWM on nearly all GPIO). UART/I²C/SPI sets are hardware-capable, not “supported runtime mapping” vs “MCU capability”.
- VBUS/VSYS/3V3_EN present. Regulator path still simplified.

### Raspberry Pi Pico W
- Same GPIO as Pico. Wi-Fi **Not simulated** (honest). Identity/artwork/LED differ in hardware and are not reflected.

### NodeMCU DEVKIT V1.0
- D1=GPIO5 mapping fixed in 5.A. A0 range undocumented (honest). D0 no PWM — **correct**. Boot-sensitive D3/D4/D8 not warned.

### ESP32-DevKitC V4 (WROOM-32E)
- Input-only 34/35/36/39 — **correct**. Flash pins reserved — **correct**. ADC on documented channels — **reasonable**. I²C/SPI only tagged on default pins though hardware is flexible — inspector should say “default runtime mapping”.
- DAC GPIO25/26 exist in hardware; runtime has **no DAC API** — must not label as simulated DAC.

## Boards not present (and add/skip decision)

| Board | Decision |
|---|---|
| Wemos D1 Mini | **Add — Functional.** Same ESP8266 subset as NodeMCU, distinct aliases/layout. |
| ESP32-C3 DevKit | **Add — Functional.** Arduino subset + 3.3 V GPIO/ADC; Wi-Fi hardware-only. |
| Arduino Nano 33 IoT | **Add — Functional.** 3.3 V SAMD21, Arduino subset; Wi-Fi not simulated. |
| Raspberry Pi Pico 2 | **Add — Functional.** Same 40-pin GP map; RP2350 identity; same subset runtime. |
| ESP32-S3 / CAM / Leonardo / Due / MKR / micro:bit / XIAO / STM32 / Teensy | **Not added.** Camera, AVR USB, SAM, micro:bit, and STM32 would be decorative or need a different runtime. |

## Constraint

Existing saved circuits store `TerminalRef.index`. **Do not reorder existing pin lists.** Append new pins (IOREF, GP25, …) and place pins with visual anchors.
