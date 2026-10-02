# Phase 1 component platform report

## Scope and counts

The original `Kind` catalog contained 52 components. This phase adds 50 non-board components and seven development boards, for 109 total registered kinds. Home, Explore Components, and the designer picker now read `ComponentRegistry`; search also uses that registry. All 57 additions have pin and parameter metadata, are placeable and wireable, and survive Android JSON save/reload. The 50 component models are educational approximations and are shown as **Simplified Model**.

## Validation matrix

Each row represents a distinct new `Kind`, rather than an alias. `P/W` is placement and pin-level wiring; `S` is electrical or digital simulation; `Pr` is relevant properties; `R` is Android JSON round-trip. `✓` records an automated test plus the implementation path. `UI†` means the individual card was not manually traversed in the emulator; the representative UI test is tracked below.

| New component | P/W | S | Pr | R | Status |
| --- | :---: | :---: | :---: | :---: | --- |
| Single-cell battery | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Battery pack | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Variable DC supply | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| DC current source | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| AC voltage source | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Sine-wave generator | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Square-wave generator | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Pulse generator | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Rheostat | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| PTC thermistor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Variable capacitor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Schottky diode | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Red LED | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Green LED | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Blue LED | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Photodiode | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Normally-closed push button | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Buffer | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Tri-state buffer | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| SR latch | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| D latch | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| JK flip-flop | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 2:1 multiplexer | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 1:2 demultiplexer | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 4:2 priority encoder | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 2:4 decoder | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Logic input | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Logic output | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Active buzzer | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Speaker | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Servo motor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Solenoid | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 4-way DIP switch | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| DPDT relay | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Reed switch | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| LM35 temperature sensor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Linear Hall sensor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Phototransistor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Darlington NPN | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| SCR thyristor | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| TRIAC | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| LM358 dual op-amp | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| LM741 op-amp | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| Comparator | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 7805 regulator | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| LM317 regulator | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| L293D motor driver | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| ULN2003 driver | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| 74HC595 shift register | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |
| CD4017 decade counter | ✓ | ✓ | ✓ | ✓ | Simplified Model; UI† |

## Development boards

| Board | Header pins | Logic | GPIO test | Save/load | Simulation limit |
| --- | ---: | ---: | :---: | :---: | --- |
| Arduino Uno R3 | 29 | 5 V | ✓ | ✓ | Manual input/low/high GPIO; no sketch runtime |
| Arduino Nano | 30 | 5 V | ✓ | ✓ | Manual input/low/high GPIO; no sketch runtime |
| Arduino Mega 2560 | 78 | 5 V | ✓ | ✓ | Manual input/low/high GPIO; no sketch runtime |
| Raspberry Pi Pico | 40 | 3.3 V | ✓ | ✓ | Manual input/low/high GPIO; no MicroPython runtime |
| Raspberry Pi Pico W | 40 | 3.3 V | ✓ | ✓ | Manual input/low/high GPIO; Wi-Fi is metadata only |
| NodeMCU ESP8266 | 30 | 3.3 V | ✓ | ✓ | Manual input/low/high GPIO; no Lua/Arduino runtime |
| ESP32 DevKitC V4 | 38 | 3.3 V | ✓ | ✓ | Manual input/low/high GPIO; no firmware or radio runtime |

Board header counts are the pin definitions in this catalog. Some board aliases share an electrical net. A GPIO output drives through an educational 50 Ω source only when a suitable supply and ground are connected. Input, PWM, ADC, UART, SPI, and I²C capabilities are documented, but ADC conversion, PWM waveforms, and bus transactions are future work. The board inspector exposes each pin and its known input voltage range. Diagnostics warns when a wired input is obviously outside that range.

## Verification

- `testDebugUnitTest --offline` passes (115 JVM tests), including per-part DC/transient circuits for all 17 source/passive/diode additions, all 10 electromechanical additions, all three semiconductor switches, seven analog ICs, two digital ICs, and 11 digital blocks. Board tests exercise powered high GPIO, input mode, and unpowered behavior for all seven boards.
- `PhaseOneCatalogInstrumentedTest` passed on `emulator-5556`: all 57 additions were placed through `SimulatorViewModel`, connected, edited, moved, removed with wire cleanup, searched, and JSON round-tripped on Android.
- The existing catalog UI test's isolated digital picker case passed on `emulator-5556`. The Explore → NodeMCU → Designer → canvas placement test passed on the isolated `emulator-5560`. A visual review of the running Home and Explore screens found catalog cards clipping their status line; card height was increased to 158 dp.
- AC small-signal analysis explicitly rejects additions without an AC model. DC/transient or digital simulation is the Phase 1 support path for them.
- The final isolated emulator run passed all 19 Android instrumentation tests. The [Home screen](phase1_home.png) and [Explore Components screen](phase1_visual.png) were captured from the running app for visual review.

## Known model limits

All 50 new parts use **Simplified Model** status. Colored LEDs use piecewise forward drops; the illustration responds to calculated forward current. The Darlington, SCR, TRIAC, relays, actuators, regulators, op-amps, motor driver, logic blocks, and digital ICs omit specified second-order behavior; each part's details panel states its applicable limit. The servo maps a manual signal voltage to angle; PWM pulse timing and mechanics are not modeled. Speaker and buzzer have electrical response and visual state but do not synthesize audio. Boards do not execute code. This work is Phase 1 of 6; the firmware IDE and richer bus simulation belong to later phases.

## Pinout and device references

Board pin maps were checked against [Arduino Uno R3](https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf), [Nano](https://docs.arduino.cc/resources/pinouts/A000005-full-pinout.pdf), [Mega 2560](https://docs.arduino.cc/resources/pinouts/A000067-full-pinout.pdf), [Pico](https://datasheets.raspberrypi.com/pico/pico-datasheet.pdf), [Pico W](https://datasheets.raspberrypi.com/picow/pico-w-datasheet.pdf), [NodeMCU DevKit](https://github.com/nodemcu/nodemcu-devkit-v1.0/blob/master/README.md), and [ESP32 DevKitC](https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html). IC metadata links to the relevant manufacturer data sheets in `IcParts.kt`; those data sheets identify pin names and roles, while the simulation models remain educational approximations.
