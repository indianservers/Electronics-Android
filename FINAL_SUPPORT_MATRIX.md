# Final support matrix

Labels: **Full** means the advertised behavior is implemented and covered by tests. **Functional** means the core path works and some peripherals are basic or metadata-only. **Simplified** means an educational model. **Preview** means visible but not simulated.

## Boards
| Board | Level |
|---|---|
| Arduino Uno R3 | Full (GPIO, ADC, PWM, Arduino subset; buses Basic) |
| Arduino Nano | Full (same; A6/A7 analog only) |
| Arduino Mega 2560 | Functional (extra UARTs labeled) |
| Raspberry Pi Pico | Full |
| Raspberry Pi Pico W | Functional (Wi-Fi metadata) |
| Raspberry Pi Pico 2 | Functional (Pico-compatible subset) |
| NodeMCU DEVKIT V1.0 | Functional |
| Wemos D1 Mini | Functional |
| ESP32-DevKitC V4 | Functional (DAC unsupported) |
| ESP32-C3-DevKitM-1 | Functional |
| Arduino Nano 33 IoT | Functional (3.3 V, not a 5 V Nano) |

## Firmware
- Arduino subset: all boards.
- MicroPython subset: Pico, Pico W, Pico 2, NodeMCU, D1 Mini, ESP32-DevKitC V4, ESP32-C3.
- Not simulated: Wi-Fi, Bluetooth, MQTT, cloud.

## Buses
UART, I²C, SPI: **Basic** event model on boards that expose those pins.

## Instruments
| Instrument | Level |
|---|---|
| DC operating point | Full for linear networks; Simplified for semiconductors |
| Transient | Functional (RC and RL golden tests) |
| AC / Bode | Simplified |
| Oscilloscope | Functional |
| Multimeter | Functional |
| Function generator | Functional for exposed waveforms |
| Logic analyzer | Functional for captured digital frames |

## Projects
Nine launchable firmware/sample projects. Two Preview cards with no circuit.

## Guide
Ten lessons: first LED, switch, voltage, current, Ohm’s law, series, parallel, LED polarity, short, open.
