# Circuit Simulator for Android

Native Kotlin/Jetpack Compose circuit workspace. The initial screen loads a solved 9 V battery → switch → 330 Ω resistor → red LED circuit. Everything runs locally.

## Architecture

- `domain`: versioned circuit concepts, component registry and parameter metadata, sample circuits, engineering units.
- `simulation`: independent modified nodal DC solver with Newton iteration for diode junctions, short/floating topology checks, calculated voltage/current/power, and basic rating alerts.
- `data`: versioned JSON document. Save writes to app-private storage; relaunch loads the saved circuit.
- `ui`: ViewModel state/history and native Compose workspace, catalog, details sheet, and Canvas component drawings.

Electrical topology is defined by terminal references and wires; canvas coordinates only control presentation. Each component shows its actual electrical pins even when it is not selected. Blue pins are open; green pins have wires. Battery and other polarized pins are labeled `+` and `−`; three-terminal devices show their pin roles. A wire crossing does not create an electrical connection. Tap one pin and then another, or drag between pins, to connect their exact electrical terminal indices. Drag onto an existing wire to create an explicit junction.

## Controls

Tap a component body to inspect it. Tapping a switch body toggles its contact immediately. Use the component drawer or catalog to select a part, then tap the canvas to place it. Placement alone never makes an electrical connection. Tap a pin to arm a wire, then tap a second pin to complete it; tapping the armed pin again cancels. You can also drag from one pin to another. Drag a component body to move it; its connected wires follow the pins. The details sheet supports editing, rotation and removal. Use the top bar for undo, redo, pause and save. Pinch or use the zoom controls to change scale.

## DC model and limits

Resistors, ideal DC sources, closed/open switches, a high impedance voltmeter, low resistance ammeter, inductor DC short, diode/LED Shockley junctions, and a fixed resistance lamp are supported. Capacitors are DC open circuits. Junction voltage limiting and damping aid convergence; the model is an educational generic model, not a verified manufacturer device. Ratings flag excessive resistor power and LED current.

Transient and AC analysis, actual filament temperature, realistic battery chemistry, manufacturer part models, transistor models, and microcontroller/code execution are deferred. No datasheet claims are attached to generic components.

## Verification

Run `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug --no-configuration-cache`. `connectedDebugAndroidTest` needs an emulator/device. The Android instrumented test verifies JSON round trips and preservation of the operating point.
