# Development board hardware and runtime audit

Checked against the primary references below on 2026-10-04. This audit concerns the
documented executable subset, not native AVR/ARM/Xtensa/RISC-V instruction emulation.
Each Kind has a dedicated, stable hardware profile ID. Existing saved terminal
indices remain intact; missing UNO/Mega power and ground headers were appended.
The board drawing, hit testing, solver terminal, pin inspector and firmware resolve
through the same registry pin. NC, power, reset and reserved pins are not generic GPIO.

## Priority board profiles

| Board / selected revision | Modeled main headers | ADC / voltage | PWM and GPIO limits | Physical UART / I2C / SPI defaults | Boot and power |
|---|---|---|---|---|---|
| UNO Rev3 / A000066, ATmega328P | 32, excluding ICSP connectors | 6 ADC inputs, 10 bit, 5 V reference | D3/5/6/9/10/11; external interrupts D2/3 | Serial D1/D0; A4/A5; D11/D12/D13/D10 | 5 V logic; USB or direct rail; ideal VIN regulator in 7–12 V recommended range |
| Mega 2560 Rev3 / A000067 | 86, excluding ICSP connectors | A0–A15, 10 bit, 5 V | 15 PWM pins; external interrupts D2/3/18/19/20/21 | Serial D1/D0, Serial1 D18/D19, Serial2 D16/D17, Serial3 D14/D15; D20/D21; D51/D50/D52/D53 | USB, direct 5 V or VIN; active-low reset |
| Nano Classic / A000005, ATmega328P | 30 | A0–A7, 10 bit; A6/A7 analog-only | Six AVR PWM pins, D2/3 interrupts | Serial D1/D0; A4/A5; D11/D12/D13/D10 | 5 V; Mini-B USB; ideal VIN regulator |
| NodeMCU DEVKIT V1.0 / ESP-12E | 30 | A0, 10 bit; 220k/100k divider gives 0–3.2 V header range, bare ADC 0–1 V | Software PWM includes GPIO16; GPIO16 has no GPIO interrupt or pull-up, supports pull-down | Serial TX/RX; Serial1 D4 TX-only; D2/D1; D7/D6/D5/D8 | 3.3 V; GPIO0/2 HIGH and GPIO15 LOW for normal boot; EN/RST; VIN 5 V policy |
| ESP32-DevKitC V4 / WROOM-32E | 38 | Exposed ADC-capable GPIO, 12-bit linear educational conversion | GPIO34–39 input-only with no internal pulls; flash pins reserved; 16 LEDC hardware channels | Serial TX/RX and Serial2 IO17/IO16; IO21/IO22; IO23/IO19/IO18/IO5 | 3.3 V; GPIO0 HIGH and GPIO12 LOW startup checks; EN reset; board power LED, no GPIO2 user LED |
| Raspberry Pi Pico / RP2040 | 40 + internal GP25 LED endpoint | GP26–28, 12 bit, 3.3 V; MicroPython ADC0–2 map to these GPIOs | Shared PWM slice frequency; GPIO interrupts; pull-up/down | Arduino Serial is USB CDC, Serial1 GP0/GP1, Serial2 GP4/GP5; MicroPython UART0/1; GP4/GP5; GP3/GP0/GP2/GP1 | VSYS 1.8–5.5 V, USB or 3V3_OUT; RUN and 3V3_EN |
| Raspberry Pi Pico W / RP2040 + CYW43439 | 40 + virtual WL_LED endpoint | Same exposed ADC pins as Pico | Same GPIO/PWM/interrupt subset | Same default buses as Pico | LED uses CYW43439 WL_GPIO0, not GP25; wireless network stack is not emulated |

UART pairs are TX/RX, I2C pairs SDA/SCL, SPI lists MOSI/MISO/SCK/CS.
UNO/Mega use silkscreen names, not an invented unified physical pin-number sequence.
Pico/Nano retain their physical header numbering. RP2040 alternate I2C roles must
belong to the same controller; arbitrary ESP32 UART remapping is rejected explicitly.
The Mega paired digital header is drawn in two columns.

## Manufacturer and implementation references

- [UNO R3 datasheet](https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf)
  and [full pinout](https://docs.arduino.cc/resources/pinouts/A000066-full-pinout.pdf).
- [Mega 2560 datasheet](https://docs.arduino.cc/resources/datasheets/A000067-datasheet.pdf)
  and [full pinout](https://docs.arduino.cc/resources/pinouts/A000067-full-pinout.pdf);
  [ATmega2560 manufacturer resources](https://www.microchip.com/en-us/product/ATmega2560).
- [Nano Classic datasheet](https://docs.arduino.cc/resources/datasheets/A000005-datasheet.pdf)
  and [pinout](https://docs.arduino.cc/resources/pinouts/A000005-full-pinout.pdf).
- [NodeMCU V1.0 open hardware and resistor BOM](https://github.com/nodemcu/nodemcu-devkit-v1.0),
  [schematic](https://github.com/nodemcu/nodemcu-devkit-v1.0/blob/master/NODEMCU_DEVKIT_V1.0.PDF),
  [ESP8266EX datasheet](https://documentation.espressif.com/0a-esp8266ex_datasheet_en.html),
  [Arduino core GPIO/PWM/UART reference](https://arduino-esp8266.readthedocs.io/en/latest/reference.html).
- [ESP32-DevKitC V4 guide and headers](https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html),
  [ESP32 datasheet](https://documentation.espressif.com/esp32_datasheet_en.html)
  and [WROOM-32E module variants](https://documentation.espressif.com/esp32-wroom-32e_esp32-wroom-32ue_datasheet_en.html).
  Flash capacity is unspecified without the module suffix: hardware offers 4/8/16 MB.
- [Pico series reference](https://www.raspberrypi.com/documentation/microcontrollers/pico-series.html),
  [RP2040 datasheet](https://datasheets.raspberrypi.org/rp2040/rp2040-datasheet.pdf)
  and [MicroPython reference](https://www.raspberrypi.com/documentation/microcontrollers/micropython.html).

The native Datasheet tab opens these references and shows the selected revision and
verification date. GPIO/ADC/PWM/timer/UART/I2C/SPI/power/electrical-limit tabs
distinguish hardware resources from implemented operations.

## Additional existing board variants

LOLIN D1 Mini, ESP32-C3-DevKitM-1, Nano 33 IoT and Pico 2 retain separate profiles,
pin maps and executable starters. They are not aliases for UNO or ESP32-WROOM.
Their extended internal peripherals do not carry the priority-board acceptance audit.
Unknown resource values are left unspecified rather than filled with estimates.

- C3 has 4 MB in-package flash, 160 MHz CPU, 400 KiB SRAM; its GPIO8 addressable
  RGB LED protocol and UART1 remapping are unsupported. See the
  [C3 board guide](https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32c3/esp32-c3-devkitm-1/user_guide.html)
  and [chip datasheet](https://documentation.espressif.com/esp32-c3_datasheet_en.html).
- Nano 33 IoT is 3.3 V SAMD21: Serial is USB CDC, Serial1 D1/D0. The NINA, IMU,
  crypto devices and VIN regulator limits are not modeled. See its
  [official datasheet](https://docs.arduino.cc/resources/datasheets/ABX00027-datasheet.pdf).
- Pico 2 has 150 MHz processors, 520 KiB SRAM, 4 MB flash and the exposed
  Pico-compatible GPIO interfaces. Its PIO/security/instruction sets are metadata.
  See the [manufacturer specification](https://www.raspberrypi.com/products/raspberry-pi-pico-2/).

## Execution and electrical policies

- Arduino: parsed setup/loop, variables, arithmetic, branches, while/for loops,
  zero-argument void helpers, named GPIO interrupt handlers, GPIO/ADC/PWM,
  millis/micros/delays, Serial ports, Wire, SPI and Servo subset.
  No arbitrary C++, pointer access, unsupported libraries or network calls.
- MicroPython: actual indentation-aware parsed program, approved machine/time
  imports, assignments, branches, while loops, zero-argument helpers, Pin,
  ADC, PWM, UART, I2C, SPI, byte lists/indexing, print and virtual-clock sleeps.
  Unsupported syntax, modules, methods, pins and controller mappings are diagnosed.
  No host Python process, filesystem, network or Android reflection access.
- GPIO drive uses finite output impedance (50 ohms). The damage accumulation
  threshold, generic fallback current threshold, 12k reset/strap pulls and 5 V
  header acceptance within ±5% are explicit educational policies, not
  manufacturer device characterization.
- Supply checks halt execution when unpowered or invalid. Reset/enable pins held
  LOW release GPIO through reset and report a diagnostic. Release the fault and
  run/upload again. MCU reset restarts program variables; in a multi-board
  circuit it preserves the shared clock and the other board's state.
- Current recommendations differ by board. Aggregate GPIO limits, where
  specified, halt on source/sink overcurrent. RP2040 12 mA is a configurable
  drive-strength setting, not an absolute maximum; the 50 mA aggregate limit
  is manufacturer documented. Reset clears educational damage state.
- PWM generates electrical edge frames, not averaged DC. ADC samples solved
  nodes. ADC nonlinearity, acquisition time, attenuation configuration, noise,
  and internal temperature channels are not simulated.
- UART, I2C and SPI now emit electrical bit edges. UART checks frame settings;
  I2C uses open-drain outputs and real external or modeled module pull-ups;
  SPI memory implements a bounded W25Q32JV command subset. See
  [the embedded ecosystem audit](EMBEDDED_ECOSYSTEM_AUDIT.md) for the current
  protocol coverage and remaining timing/electrical limitations.
- A bounded instruction/frame budget stops busy programs. Firmware stepping and
  circuit solves run off the UI thread. Parsing also bounds expression nesting.
- Per-board language, source, USB choice and profile ID persist in project JSON;
  older files without profile IDs still load. Runtime variables and RAM are
  intentionally restarted after loading.

## Verification

BoardHardwareAcceptanceTest and its Android counterpart exercise:

1. Seven profile pin/alias/electrical mappings.
2. Mega D22, D40, A10 and two independent Serial ports; UNO rejects them.
3. Pico indentation/imports, GP15 toggle, branches and exact virtual timing.
4. Physical ADC voltage and true PWM pulses.
5. Invalid pins, unsafe imports, wrong object methods and shared PWM conflicts.
6. NodeMCU D1→GPIO5, 3.3 V output and startup strap faults.
7. ESP32 input-only/pull restrictions and 5 V damage indication.
8. Pico W wireless LED mapping; unavailable header GP25.
9. Powered, connected I2C and disconnected/powerless NACKs.
10. UNO→ESP32 HELLO over a wired voltage divider; ground/baud failures.
11. Unpowered/reset-held board and externally powered VIN regulator.
12. Named interrupt handler triggered by a physical edge, preserving delay.
13. MicroPython I2C sensor bytes derived from environment; SDA disconnect NACK.
14. ESP32 Serial2, PWM/ADC and NodeMCU TX-only Serial1.
15. Actual LED current on/off and Pico potentiometer ADC movement.
16. ESP32 SPI memory accepts connected transfer and rejects missing clock.
17. Reset one board while preserving another board's state and global time.
18. All 11 existing board starters execute; Pico USB and physical UART differ.
19. SDA/A4 share GPIO state and cannot bypass I2C ownership through aliases.
20. Aggregate overload, busy-loop frame limits and invalid syntax diagnose safely.

Native workspace tests verify the selected board gets its own code, switching
preserves both programs, project JSON round-trips profile IDs, structured reference
topics render, official source buttons are clickable, and Code opens that board.
Gesture tests retain one-finger pin connection and two-finger movement.

Final verification (2026-10-04): debug APK and instrumentation APK built;
208 unit tests passed with zero failures. The final APK passed 28 tests on
`emulator-5554`: 20 hardware acceptance, 2 board workspace and 6 canvas gesture
tests. The native board reference screenshot was also visually inspected.
Build command: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --console=plain`.
Emulator results: `app/build/board-acceptance-emulator.log`.

Physical time accuracy, full MCU instruction execution, timer registers, Wi-Fi,
Bluetooth, PIO programs, touch/DAC, arbitrary third-party sensor libraries and
Linux Raspberry Pi SBC operating systems are outside this implementation.
There is no DHT component or Linux SBC Kind in the current catalog.

## Eight additional boards audited on 2026-10-12

The catalog now has 19 development boards. These additions share the executable
firmware subset and electrical solver, with separate hardware profiles and pin maps.
Header silkscreen names identify terminals. Espressif connector numbers are local
to J1/J2/J3; Arduino headers use silkscreen names instead of fabricated global pin
numbers. SPI/ICSP pin numbers follow the six-pin connector drawing. Canvas spacing
is illustrative, not a dimensioned PCB footprint. USB/debug/test pads are excluded.

| Selected board | Terminals, including NC/internal endpoints | Logic / default ADC read | Key differences |
|---|---|---|---|
| Leonardo A000057 / ATmega32U4 | 32 main + 6 ICSP | 5 V / 10-bit | I2C D2/D3; SPI ICSP MISO D14, SCK D15, MOSI D16; Serial USB CDC, Serial1 D1/D0; seven PWM pins |
| Micro A000053 / ATmega32U4 | 34 main + 6 ICSP | 5 V / 10-bit | Two NC pads; SPI also exposed on D14–D17; A6–A11 share D4/D6/D8/D9/D10/D12; 2.5 KiB SRAM |
| Due A000062 / SAM3X8E | 86 main + 6 SPI | 3.3 V / 10-bit Arduino default | 54 digital, 12 ADC, two DAC and two CAN pads; SPI separate from D50–D52; SDA1/SCL1 separate from D20/D21 |
| UNO R4 Minima ABX00080 / RA4M1 | 32 main + 6 ICSP | 5 V / 10-bit Arduino default | 48 MHz, 256 KiB flash, 32 KiB SRAM; six PWM pins; 8 mA GPIO recommendation; 14-bit ADC hardware and A0 DAC distinguished from runtime |
| Pico 2 W / RP2350 + CYW43439 | 40 main + virtual WL_LED | 3.3 V / 12-bit | 150 MHz, 4 MiB flash, 520 KiB SRAM; GP26–28 ADC; WL_GPIO0 LED, not GP25; PIO2 hardware metadata |
| ESP32-S3-DevKitC-1 v1.1 N8R8 | 44 J1/J3 positions | 3.3 V / 12-bit | 8 MiB flash + 8 MiB octal PSRAM; GPIO35–37 reserved; GPIO46 supports output; v1.1 RGB LED GPIO38 |
| ESP32-C6-DevKitC-1 v1.2 / WROOM-1 | 32 J1/J3 positions | 3.3 V / 12-bit | Two NC positions; UART GPIO16/17; core Wire defaults GPIO23/22 and SPI GPIO19/20/21/18; RGB LED GPIO8 |
| ESP32-S2-Saola-1 v1.2 / WROVER revision 0 | 42 J2/J3 positions | 3.3 V / 13-bit | GPIO26 reserved for PSRAM CS, GPIO46 input-only; RGB LED GPIO18; Wi-Fi but no Bluetooth; legacy EOL module |

ADC transfer is a nominal linear approximation: reference 5 V on the AVR/R4
boards, 3.3 V on Due/Pico 2 W/C6, 3.1 V on S3 and 2.5 V on S2. The latter two
represent a fixed high-attenuation setting. Noise, calibration, sample/hold timing
and ADC2 contention are omitted. Configurable ADC resolution/attenuation APIs are
outside the subset. Due and R4 hardware resolution is not confused with Arduino's
default read resolution. S2 revision 0's 13-bit default follows the ADC-112 workaround.
The conservative 3 mA Due GPIO policy is not a claim that every SAM3X pin has the
same physical current rating. Power regulation and current/damage remain educational.

All new boards advertise functional subset execution. Radios, addressable RGB LED
protocols, USB HID/JTAG, DAC output, CAN, Wire1/LP I2C, CPU instructions and register
programming remain hardware metadata or unsupported. Blink uses an external GPIO4
LED on the new Espressif boards, rather than treating their RGB LED as a plain LED.
Public GPIO APIs resolve aliases and reject pins already claimed by a bus.

Primary sources used for these maps and profiles:

- Arduino [Leonardo pinout](https://docs.arduino.cc/resources/pinouts/A000057-full-pinout.pdf),
  [Micro pinout](https://docs.arduino.cc/resources/pinouts/A000053-full-pinout.pdf),
  [ATmega32U4 datasheet](https://ww1.microchip.com/downloads/en/devicedoc/atmel-7766-8-bit-avr-atmega16u4-32u4_datasheet.pdf)
  and [official AVR core mapping](https://raw.githubusercontent.com/arduino/ArduinoCore-avr/master/variants/leonardo/pins_arduino.h).
- Arduino [Due pinout](https://docs.arduino.cc/resources/pinouts/A000056-full-pinout.pdf),
  [Due datasheet](https://docs.arduino.cc/resources/datasheets/A000062-datasheet.pdf)
  and [SAM core mapping](https://raw.githubusercontent.com/arduino/ArduinoCore-sam/master/variants/arduino_due_x/variant.h).
- Arduino [R4 Minima pinout](https://docs.arduino.cc/resources/pinouts/ABX00080-full-pinout.pdf),
  [datasheet](https://docs.arduino.cc/resources/datasheets/ABX00080-datasheet.pdf)
  and [Renesas core mapping](https://raw.githubusercontent.com/arduino/ArduinoCore-renesas/main/variants/MINIMA/pins_arduino.h).
- Raspberry Pi [Pico 2 W datasheet, Release 2](https://datasheets.raspberrypi.com/picow/pico-2-w-datasheet.pdf)
  and [RP2350 datasheet](https://pip-assets.raspberrypi.com/categories/1214-rp2350/documents/RP-008373-DS-2-rp2350-datasheet.pdf).
- Espressif [S3 v1.1 header guide](https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32s3/esp32-s3-devkitc-1/user_guide_v1.1.html),
  [C6 v1.2 header guide](https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32c6/esp32-c6-devkitc-1/user_guide.html),
  [S2 Saola guide](https://docs.espressif.com/projects/esp-idf/en/v4.4/esp32s2/hw-reference/esp32s2/user-guide-saola-1-v1.2.html),
  [S2 schematic](https://dl.espressif.com/dl/schematics/ESP32-S2-SAOLA-1_V1.1_schematics.pdf)
  (drawing title identifies v1.2; it resolves the guide's J3 row-number typo),
  and [WROVER module datasheet](https://www.espressif.com/sites/default/files/documentation/esp32-s2-wrover_esp32-s2-wrover-i_datasheet_en.pdf).
- Espressif official Arduino core defaults:
  [S3](https://raw.githubusercontent.com/espressif/arduino-esp32/master/variants/esp32s3/pins_arduino.h),
  [C6](https://raw.githubusercontent.com/espressif/arduino-esp32/master/variants/esp32c6/pins_arduino.h),
  [S2](https://raw.githubusercontent.com/espressif/arduino-esp32/master/variants/esp32s2/pins_arduino.h)
  and [ADC API / revision-0 workaround](https://docs.espressif.com/projects/arduino-esp32/en/latest/api/adc.html).

`AdditionalBoardsTest` checks independent header sequences, terminal counts,
aliases, actual ADC channels, unique coordinates, revision/module restrictions,
GPIO high/low voltages, PWM phases, default UART identities, unpowered boards,
I2C reads from a wired powered temperature sensor, and JEDEC reads through the
correct SPI connector. Direct 5 V SPI signals are rejected by the W25Q32JV model.
Existing registry, firmware starter, pin layout and electrical tests also include
the new boards. Android emulator interaction and native instruction emulation are
not covered by these JVM tests.
