# Embedded ecosystem audit

Audit date: 2026-10-04. Scope is the actual 121-entry registry, including eight Embedded-category devices and existing sensor, actuator, logic and board groups. This is a bounded educational simulator. The requested roadmap also names families absent from the catalog; those remain unimplemented, as listed below. Module headers are intentional, rather than raw-IC package pinouts.

## Shipped bus and timed devices

| Device / selected model | Implemented behavior | Electrical requirements | Tests and remaining limits |
|---|---|---|---|
| I2C temperature / LM75B module | 0x48–0x4F; temperature/config/THYST/TOS registers; signed 9-bit 0.5°C; pointer and shutdown | 3–5.5 V; actual SDA/SCL, common ground; 4.7 kΩ module pull-ups | Negative temperature, shutdown, repeated START and waveform decoding tested. Temperature updates on reads; conversion latency, OS output and fault queue omitted. |
| EEPROM / AT24C02C module | 256 bytes, 8-byte page wrap, sequential reads, WP parameter, 5 ms BUSY/NACK | 1.7–5.5 V; external pull-ups; 0x50–0x57 | Firmware page write/read, busy polling, WP and Android serialization tested. No wear or interrupted-write effects; backing bytes change immediately. |
| OLED / SSD1306 128×64 module | 1,024-byte GDDRAM; command/data control bytes; page/column/horizontal/vertical addressing; display on/off, inverse and all-on | Selected 2.7–3.6 V module; 4.7 kΩ pull-ups; 0x3C/0x3D | Firmware byte writes and native framebuffer/inspector tested. Write-only; scrolling, fade and analog contrast omitted. |
| LCD / PCF8574 + HD44780 backpack | P0 RS/P1 RW/P2 E/P3 backlight/P4–7 data; E falling-edge nibble latch; init, DDRAM/CGRAM, cursor, clear, entry/display modes | Selected 4.5–5.5 V module; 4.7 kΩ pull-ups; 0x20–0x27 | Nibble latch and text DDRAM tested. Write-only; busy flag, instruction delays and display shifts omitted. Custom glyph RAM renders a placeholder. |
| SPI memory / W25Q32JV module | 4 MiB address space, erased FF; JEDEC EF4016; WREN/WRDI; WEL/BUSY; READ/FAST_READ; 256-byte page program; 4/32/64 KiB and chip erase; basic protection | 2.7–3.6 V; modes 0/3; actual MOSI/MISO/SCK/CS, common ground | Firmware commands, CS, readback, erase, persistence and decoded MISO tested; MicroPython byte-list clock bursts tested. Conservative 50 MHz admission policy, but electrical edge resolution limits actual traces to 500 kHz. Protection conservatively blocks all writes. No quad IO, SFDP, suspend, wear or interrupted-write effects. |
| Serial terminal / virtual UART | Timed start/data/parity/stop; queues, baud/frame match; ASCII/HEX, line endings, pause, clear/copy | TX is a 5 V finite-resistance driver; requires TX/RX wires and common ground; terminal TX needs a divider for 3.3 V receivers | Bidirectional delayed delivery and LOW waveform tested. Eligibility is checked when scheduling; noise and mid-frame disconnects are not decoded into RX. |
| PIR / HC-SR501 family | Power-on warm-up, motion environment, hold/retrigger; actual 3.3 V OUT | 4.5–12 V, ground-referenced output; power loss resets state | Warm-up, hold and power-loss output tested. Generic module policy; optical coverage and thermal noise omitted. |
| Ultrasonic / HC-SR04 family | TRIG >=10 µs; 200 µs response latency; distance ×58 µs ECHO width | 4.5–5.5 V; actual 5 V echo can overvoltage a 3.3 V MCU | Short-trigger rejection and 100 cm/5,800 µs echo tested. Beam, blind zone, no-target timeout and recommended inter-ping spacing omitted. |

## Electrical interfaces and scheduling

- Net identity uses wire endpoints and explicit junctions. Coordinates and crossings never identify buses. Power, common ground and controller pin mapping gate discovery.
- I2C HIGH releases the output driver. External resistors or modeled module pull-ups stamp the solver; weak MCU internal pull-ups do not qualify as adequate bus pull-ups. START/repeated START/STOP, address/data and ACK/NACK produce captured electrical transitions. Connected duplicate addresses NACK. The scanner probes 1–126 through transactions and observes write-cycle BUSY. Concurrent controllers are conservatively rejected while another owns the bus; bitwise arbitration, capacitance, rise-time, noise and slave clock stretching remain unsupported.
- SPI runs eight physical clock cycles per byte; CS controls command framing. CS rising commits program/erase requests and starts BUSY. MISO releases after transfers. Integer-microsecond scheduling imposes a minimum 1 µs half-period; admitted faster clocks are quantized.
- UART edges use integer microseconds, validate frame settings and mask 5–8 data bits. RX becomes available after frame completion. USB CDC is separately identified virtual USB transport; USB-UART bridge boards drive their real RX net for monitor input.
- Firmware GPIO reuses the canvas digital engine for gates, mux/decoder logic, latches, flip-flops, counters and oscillators. Delayed digital events enter the firmware schedule. Generic logic uses a 3.3 V CMOS-like domain. A connected gate-to-flip-flop firmware test includes reset.
- Interpreters remain supported Arduino/MicroPython subsets, rather than complete binary runtimes. Third-party display/storage libraries are not implemented. Board revisions, clocks, pin aliases and limits are in [the board audit](BOARD_HARDWARE_AUDIT.md).

## Other shipped ecosystem components

| Group | Current behavior and limits |
|---|---|
| LM35, NTC/PTC, LDR, photodiode, phototransistor, Hall, reed, potentiometer | Environment temperature/lux/magnetic field update actual analog stamps; ADC reads solved nodes. Existing selected-stamp tests. Educational transfer functions without calibration/noise. |
| Buttons, NC button, switches, SPDT, DIP | Actual contact paths and user pulse/state interaction. Existing contact/UI coverage; no bounce. |
| 74HC595 | Actual SER/SRCLK/RCLK/SRCLR/OE, shift/latch, eight finite outputs and QH prime; 2–6 V. Firmware wired-output test. GPIO device, not SPI memory. Aggregate package-current damage and simultaneous-edge silicon timing omitted. |
| CD4017, 555, clocks, gates, mux/demux, encoder/decoder, latches, counters | Retained educational event-engine state on solved nets. Existing logic/mixed-signal tests plus firmware integration. 555 remains a simplified three-pin astable. |
| Two-bit ADC/DAC | Actual analog/digital conversion and finite output resistance; educational 3.3 V converters, not bus expander ICs. |
| Servo | Actual SIG pulse width and refresh validation, powered response and angle smoothing. Existing servo tests. Torque, stall, backlash and complete current dynamics omitted. |
| DC motor, relay/DPDT relay, solenoid | Existing armature/coils/contact analog models; transient solver retains motor/reactive state. Existing tests. Firmware uses DC samples, so inertia/coil transients are not fully coupled into firmware time. |
| L293D, ULN2003 | Actual supply/input/output/ground analog stamps and existing driver tests. Simplified thermal and simultaneous-current behavior. |
| LED/colors, RGB, seven-segment, lamp | Brightness/branches derive from solved results; existing analog/artwork tests. No new LED matrix controller. |
| Buzzer/speaker | Optional bounded PCM from powered active-buzzer oscillator or solved speaker terminal voltage with DC removal. Off/on, clipping and DC silence tested. Native AudioTrack added; physical audio output and acoustic response not verified. No host microphone access. |
| Scope/analyzer/meters | Four actual wire probes and solved traces. I2C/SPI/UART decoding reads captured node transitions; selectable SPI mode/UART baud. Real I2C restart/address and SPI JEDEC decoding tested. Undersampled captures cannot decode. UART decoder is 8N1; transport supports additional framing. |
| Regulators/passives/semiconductors | Existing educational analog support for supplies, dividers and switching. No PMIC register model or battery chemistry added. |

## Native UI, persistence and resource policies

The inspector shows live power, address/controller state, reference and model limits. Memory exposes actual HEX/ASCII reads, edits, copying and bounded HEX imports. OLED drawing uses GDDRAM pixels; LCD text comes from latched DDRAM. Beginner mode offers wiring guidance; advanced mode changes assistance, not electrical rules. Scanner/errors/guidance use the top message area. Previous full-screen, landscape/tablet layouts and one-finger Connect/two-finger pan remain.

Schema 5 stores sparse EEPROM/flash bytes by component ID, environment state and mode preference. Reset/power loss clears volatile state/display RAM and retains NVM. Android tests exercise actual JSON reload and memory editor behavior. Stored/programmed flash is bounded to 65,536 bytes per device within its 4 MiB address space; erased bytes are implicit FF. Exceeding this resource budget reports an error. Queue, command, waveform-history, instruction and frame budgets also apply. Persistence does not emulate wear or partially completed writes.

## Requested families absent from the catalog

No new catalog families were added. FRAM/SRAM, SD/FAT, RTC, BME/BMP humidity/pressure, gas modules, IMU/accelerometer/gyro/magnetometer bus chips, GPS, RFID/NFC, keypad, rotary encoder, dedicated stepper mechanics, TFT, LED matrix, microphone, IR transmitter/receiver, I2C mux, bus ADC/DAC/GPIO expanders, watchdog and register-controlled PMIC remain unimplemented. Generic parts and ULN2003 do not count as implementations of these absent devices. Full 100-section roadmap acceptance and physical-device parity are not claimed.

## Verification result

`assembleDebug`, `assembleDebugAndroidTest` and all 224 JVM unit tests passed.
On Android emulator `emulator-5554`, 46 instrumentation tests passed across
EmbeddedEcosystem (16), BoardHardware (20), CanvasGesture (6), EmbeddedWorkspace
(2) and BoardWorkspace (2). Native UI checks include OLED RAM/inspector updates
and memory edits surviving JSON reload. The final model-label update and debug
APK rebuild passed unit checks; it does not change electrical behavior.

## Source references

References checked on 2026-10-04 where available:
[TI LM75B](https://www.ti.com/lit/ds/symlink/lm75b.pdf),
[Microchip AT24C01C/02C](https://ww1.microchip.com/downloads/en/DeviceDoc/AT24C01C-AT24C02C-I2C-Compatible-Two-Wire-Serial-EEPROM-1Kbit-2Kbit-20006111A.pdf),
[Winbond W25Q32JV revision C](https://www.winbond.com/resource-files/w25q32jv%20spi%20revc%2008302016.pdf),
[Solomon SSD1306](https://www.solomon-systech.com/product/ssd1306/),
[manufacturer SSD1306 datasheet mirror](https://gabotronics.com/download/datasheets/ssd1306.pdf),
[TI PCF8574](https://www.ti.com/lit/ds/symlink/pcf8574.pdf),
[TI CD74HC595](https://www.ti.com/lit/ds/symlink/cd74hc595.pdf),
[TI L293/L293D](https://www.ti.com/lit/ds/symlink/l293.pdf).
HC-SR04/501 use generic module policies; the supplier HC-SR501 PDF could not be independently retrieved in this audit and is not verified silicon documentation.
