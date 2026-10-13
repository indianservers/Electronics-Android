# Circuit Simulator for Android

Native Kotlin/Jetpack Compose circuit workspace. Open Circuit Designer from the home screen to start with a 9 V battery → switch → 330 Ω resistor → red LED circuit. Everything runs locally.

## Architecture

- `domain`: versioned circuit concepts, component registry, `BoardRegistry`, sample circuits, engineering units.
- `simulation`: modified nodal DC, resumable transient, AC, and mixed-signal coordination with electrical readings and rating alerts.
- `firmware`: Arduino-subset and MicroPython-subset interpreters on placed boards.
- `guide` and `intelligence`: lessons and diagnostics that read the same circuit.
- `data`: JSON document, schema version 6 (reads versions 1–6). Save writes to app-private storage; a corrupt file is rejected.
- `ui`: ViewModel state/history and native Compose workspace, catalog, board inspector, and canvas drawings.

Electrical topology is defined by terminal references and wires; canvas coordinates only control presentation. Each component shows its actual electrical pins even when it is not selected. Blue pins are open; green pins have wires. Battery and other polarized pins are labeled `+` and `−`; three-terminal devices show their pin roles. A wire crossing does not create an electrical connection. Tap one pin and then another, or drag between pins, to connect their exact electrical terminal indices. Drag onto an existing wire to create an explicit junction.

## Controls

Gyroscope and accelerometer sensors now include L3GD20, LSM6DSOX, LIS3DH and ADXL345. Samples → Sensors contains ready-wired ESP32 motion labs; edit angular velocity/acceleration in the sensor Properties tab and read signed samples through I2C firmware. Manufacturer datasheets, carrier-to-chip pin mappings and the supported register subsets are documented in [motion sensor models](docs/MOTION_SENSOR_MODELS.md).

The live **Circuit checks** canvas strip summarizes errors, warnings and suggestions. **Review** opens explanations and fix previews; **Locate** highlights the most urgent finding. Twenty new checks cover wiring integrity, floating transistor controls, missing supplies/returns, IoT sensor power and ADC compatibility, output conflicts and redundant probes. See [canvas intelligence](docs/CANVAS_INTELLIGENCE.md) for the exact checks and model limits. Resolved findings clear their canvas highlight and enter diagnostic history.

Every component now uses **Properties**, **Pins**, and **Specifications** tabs. The property editor accepts engineering units (such as `4.7 kΩ`, `100 µF` and `3 s`), scientific notation, percent positions/duty cycles, and hex I²C addresses. It preserves small values and precision, shows valid ranges, and rejects invalid units, fractional discrete settings, out-of-range values and contradictory supply rails. Apply commits one undoable edit; Reset values restores defaults. Model tuning parameters are grouped separately from everyday settings. Legacy temperature/light stimuli also update the shared firmware Environment when edited.

The Pins tab shows named electrical terminals, package pin numbers where documented, roles, connected destinations and solved readings. **Connect** opens a searchable exact-pin picker, or **Pick on canvas** arms that terminal. NC, reserved and internal-only endpoints cannot start a new wire. Different connectable pins on the same component can be jumpered; internally shared pads remain the same net. Package numbering is separate from solver indexing: for example, 2N3904 collector is physical pin 3 while the solver retains its C/B/E electrical order. Moving, rotating and resizing do not remap wires. Manufacturer references and model limits are in Specifications; boards also retain their detailed reference guide.

The editor toolbar supports additive group selection, select all, cut/copy/paste, group rotation/movement/deletion, and saved flat Circuit blocks. Blocks insert editable parts, internal wires, board programs and peripheral memory with new identities; copied named nets are isolated from existing nets. Blocks are not hierarchical custom ICs. Snap and grid spacing are saved on the device. Wire actions include Route: one `x, y` waypoint per line; clear the field to restore automatic routing. JSON and circuit PNG exports preserve manual routes.

Truth table enumerates 1–8 Logic inputs and reads Logic outputs for supported combinational gates and blocks. It uses the simulator's existing logic functions, excludes propagation delays, displays X/Z for unknown/undriven nets, and rejects sequential, feedback and analog circuits. The [three-phase roadmap](docs/THREE_PHASE_ROADMAP.md) lists the full competitive-feature scope and acceptance criteria; these first increments do not complete all three phases.

Projects now have **Code** and **Run project** actions. Code opens the saved editable program; Run compiles and executes it, opens live output, and drives the connected circuit. Run/Pause, Step, Reset and Stop stay available on every firmware tab. See [runnable projects](docs/RUNNABLE_PROJECTS.md) for each example and its interactive output.

Tap a component body to inspect it. Tapping a switch body toggles its contact; tapping a push button closes it for a 250 ms pulse. Use the component drawer or catalog to select a part, then tap the canvas to place it. Placement alone never makes an electrical connection. Tap a pin to arm a wire, then tap a second pin to complete it; tapping the armed pin again cancels. You can also drag from one pin to another. Connect is the default mode: one finger connects pins, while two fingers pan and zoom. Select Move parts to drag component bodies; their connected wires follow the pins. View mode supports pin inspection without editing. The details sheet supports editing, rotation and removal. Use the top bar for undo, redo, rewind, pause and save. The Simulation menu sets 0.25×, 1×, or 4× speed and toggles on-canvas readings. Live traces grow as the circuit runs; parameter and switch edits continue from the current electrical state. Pinch or use the zoom controls to change scale. Attach up to four oscilloscope channels from wire actions; the scope has time traces, CH1-versus-CH2 XY display, and four-channel CSV export.

## DC model and limits

The analog solver includes resistors, independent and controlled sources (VCVS, VCCS, CCVS, CCCS), switches, relays, a coupled transformer, a Zener knee, RGB and seven-segment LED branches, and an armature/inertia DC motor. Transient simulation carries winding current, motor speed, and reactive states across live slices. AC small-signal analysis covers the expanded analog models. Capacitors are DC open circuits and inductors/transformer windings are DC shorts. Junction voltage limiting and damping aid convergence; these are educational approximations, not verified manufacturer device models. Ratings flag excessive resistor power and LED current.

The 3.3 V logic engine includes gates, a simplified astable 555, D/T flip-flops, a four-bit counter, and two-bit ADC/DAC conversion. Logic and converters use the simulator reference ground and finite output resistance. The RGB and seven-segment symbols show energized branches.

Placed boards keep separate hardware profiles and saved programs. The board inspector has structured references and live pin readings; the code workspace selects a specific board instance. Arduino and MicroPython execute supported structured subsets against actual circuit nodes. UART, I²C, and SPI require connected, powered devices and generate electrical edge frames. Display RAM, EEPROM, and flash commands run through the connected bus. The native peripheral inspector, memory editor, scanner and analyzer expose those states. See [the board hardware audit](docs/BOARD_HARDWARE_AUDIT.md) for verified revisions, tested behavior and explicit policies. See [the embedded ecosystem audit](docs/EMBEDDED_ECOSYSTEM_AUDIT.md) for device protocols, acceptance evidence and remaining limits. Wi-Fi is not simulated. Models are educational approximations. See `FINAL_SUPPORT_MATRIX.md`, `KNOWN_LIMITATIONS.md`, and `CHANGELOG.md`.

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
