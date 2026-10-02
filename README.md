# Circuit Simulator for Android

Native Kotlin/Jetpack Compose circuit workspace. Open Circuit Designer from the home screen to start with a 9 V battery → switch → 330 Ω resistor → red LED circuit. Everything runs locally.

## Architecture

- `domain`: versioned circuit concepts, component registry, `BoardRegistry`, sample circuits, engineering units.
- `simulation`: modified nodal DC, resumable transient, AC, and mixed-signal coordination with electrical readings and rating alerts.
- `firmware`: Arduino-subset and MicroPython-subset interpreters on placed boards.
- `guide` and `intelligence`: lessons and diagnostics that read the same circuit.
- `data`: JSON document, schema version 4. Save writes to app-private storage; a corrupt file is rejected.
- `ui`: ViewModel state/history and native Compose workspace, catalog, board inspector, and canvas drawings.

Electrical topology is defined by terminal references and wires; canvas coordinates only control presentation. Each component shows its actual electrical pins even when it is not selected. Blue pins are open; green pins have wires. Battery and other polarized pins are labeled `+` and `−`; three-terminal devices show their pin roles. A wire crossing does not create an electrical connection. Tap one pin and then another, or drag between pins, to connect their exact electrical terminal indices. Drag onto an existing wire to create an explicit junction.

## Controls

Tap a component body to inspect it. Tapping a switch body toggles its contact; tapping a push button closes it for a 250 ms pulse. Use the component drawer or catalog to select a part, then tap the canvas to place it. Placement alone never makes an electrical connection. Tap a pin to arm a wire, then tap a second pin to complete it; tapping the armed pin again cancels. You can also drag from one pin to another. Drag a component body to move it; its connected wires follow the pins. The details sheet supports editing, rotation and removal. Use the top bar for undo, redo, rewind, pause and save. The Simulation menu sets 0.25×, 1×, or 4× speed and toggles on-canvas readings. Live traces grow as the circuit runs; parameter and switch edits continue from the current electrical state. Pinch or use the zoom controls to change scale. Attach up to four oscilloscope channels from wire actions; the scope has time traces, CH1-versus-CH2 XY display, and four-channel CSV export.

## DC model and limits

The analog solver includes resistors, independent and controlled sources (VCVS, VCCS, CCVS, CCCS), switches, relays, a coupled transformer, a Zener knee, RGB and seven-segment LED branches, and an armature/inertia DC motor. Transient simulation carries winding current, motor speed, and reactive states across live slices. AC small-signal analysis covers the expanded analog models. Capacitors are DC open circuits and inductors/transformer windings are DC shorts. Junction voltage limiting and damping aid convergence; these are educational approximations, not verified manufacturer device models. Ratings flag excessive resistor power and LED current.

The 3.3 V logic engine includes gates, a simplified astable 555, D/T flip-flops, a four-bit counter, and two-bit ADC/DAC conversion. Logic and converters use the simulator reference ground and finite output resistance. The RGB and seven-segment symbols show energized branches.

Placed boards run a supported firmware subset. UART, I²C, and SPI are event-level. Wi-Fi is not simulated. Models are educational approximations. See `FINAL_SUPPORT_MATRIX.md`, `KNOWN_LIMITATIONS.md`, and `CHANGELOG.md`.

## Adding a part of the product

- Component: add a `Kind`, registry metadata, artwork, and a model stamp. Do not invent a SPICE claim.
- Board: append pins in `BoardRegistry` (do not reorder existing electrical indices) and set hardware vs simulator support.
- Project: add a `SampleCircuits` builder and a `ProjectItem` with a real `launch`. Leave `launch` null only for an explicit Preview card.
- Lesson: add a `GuidedLesson` whose criteria can fail.
- Diagnostic: add a rule id and a test that stays quiet on a valid circuit.
- Firmware API: implement it in the subset interpreter and reject it when the board capability is unsupported.

## Verification

```
gradlew.bat testDebugUnitTest
gradlew.bat connectedDebugAndroidTest
gradlew.bat assembleDebug assembleRelease
```

`connectedDebugAndroidTest` needs an emulator or device. Release has no custom keystore; the project release type does not enable R8. Do not treat `FINAL_PHASE_STATUS.md` as current — it predates the firmware runtime.
