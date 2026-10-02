# Phase 6 final release report

## 1. Architecture
Kotlin/Compose app. `domain` holds circuits and `BoardRegistry`. `simulation` holds DC, transient, AC, and digital coordination. `firmware` interprets a supported subset. `intelligence` and `guide` read the same circuit. `data/CircuitJson` is schema 4.

## 2–3. Solvers
DC is damped MNA with a 1e-12 S shunt. Transient has RC and RL comparisons to exponentials, including 5 V → 1 kΩ → 100 µF at τ = 0.1 s. AC is a linear sweep. Semiconductor models stay Simplified.

## 4. Components
Catalog kinds are registered with pin metadata. Support is Simplified unless a test treats the part as ideal passives.

## 5. Boards
Eleven boards. Full: Uno, Nano, Pico. Functional: Mega, Pico W, Pico 2, NodeMCU, D1 Mini, ESP32-DevKitC V4, ESP32-C3-DevKitM-1, Nano 33 IoT.

## 6–7. Firmware and buses
Arduino subset on every board. MicroPython subset on RP2040/RP2350/ESP families. UART, I²C, SPI are Basic event buses.

## 8–10. Sensors, displays, actuators
LDR, thermistor, LM35, potentiometer, ultrasonic/PIR metadata parts, OLED/LCD event displays, servo pulses, relay coil, DC motor inertia. Unpowered firmware does not drive GPIO.

## 11. Instruments
Scope, meter, function generator, Bode, logic capture of digital frames.

## 12–14. Guide, intelligence, projects
10 lessons. Intelligence covers shorts, LED polarity, GPIO domain, motors, UART, I²C addresses, meters. 9 launchable projects. 2 Preview cards.

## 15–16. Persistence
Schema 4. Corrupt open is rejected with a user message. Kind names were not renamed in 5.B, so older saves of the original seven boards still match.

## 17. Performance
Not re-benchmarked on device in this pass. Solver work stays off the Compose draw path; live simulation uses bounded slices.

## 18. Accessibility
Existing content descriptions remain. Font scale and contrast were not rechecked on a device in this pass.

## 19–20. Tests
`testDebugUnitTest`: 181 tests, 0 failures, 0 errors (2026-10-02). Includes `fiveVoltOneKiloOhmHundredMicrofaradMatchesTau`. Instrumented tests were not re-run in this pass; the last emulator run in Phase 5.B was 33/33. Device soak, font scale, and a full lesson click-through were not repeated.

## 21. Release build
`assembleDebug` and `assembleRelease` both succeeded. Release output is `app/build/outputs/apk/release/app-release-unsigned.apk` (no custom keystore; R8 optimization is off). Debug output is `app/build/outputs/apk/debug/app-debug.apk`.

## 22–23. Bugs
See `PHASE_6_FINAL_BUGS.md`. No new Critical or High defects.

## 24–26. Limitations and blockers
See `KNOWN_LIMITATIONS.md`. No open release blocker in the core LED, board blink, save, or guide paths that the existing tests cover. Device font-scale, 10-minute soak, and a signed store build were not done.
