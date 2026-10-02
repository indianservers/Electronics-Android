# Phase 3 — Circuit Intelligence implementation report

## Scope and status

This phase adds an offline, deterministic circuit intelligence layer over the existing circuit model, DC/live solver output, board metadata, and virtual instruments. It is integrated into Circuit Check, canvas highlighting, context actions, and Guide feedback. It does not simulate firmware protocols or use a remote AI service. Phase 4 has not been started.

The implementation covers the main reusable engine and 27 diagnostic rule IDs. One acceptance item remains unverified: the requested 15 *manual UI* fault checks. Sixteen distinct fault scenarios have instead been exercised in an emulator by instrumentation, with one direct UI inspection of evidence and fix preview. This is useful device evidence but is not a claim of 15 manual UI walkthroughs.

## Architecture

- `intelligence/DiagnosticModels.kt`: severity, category, confidence, evidence and source attribution, affected parts/pins/wires, reversible fix descriptions, insights, and report status.
- `intelligence/CircuitFacts.kt`: cached wire-equivalence nets, connection and voltage queries, low-resistance paths, and LED series/bypass facts.
- `intelligence/CircuitIntelligence.kt` and `CompatibilityRules.kt`: seven local rule groups in an explicit registry. Root causes are ordered first and redundant solver failure is suppressed when a known topology cause is present. The older diagnostic inspector is retained only for source conflict and floating island evidence.
- `intelligence/PatternRecognizer.kt`: LED resistor, voltage divider, RC time constant, 555 setting, and resistor power insights; circuit summary, part-role explanation, and a cloned-circuit removal preview.
- `SimulatorViewModel`: analyzes after each solve and on explicit Circuit Check, rejects stale results, keeps a session resolution history, and applies smart fixes through the existing edit/Undo path. Loading a new circuit clears old findings and history.
- `DiagnosticsSheet`, `SimulatorScreen`, and `CircuitCanvas`: prioritized Circuit Check, evidence and model notes, explanation levels, issue count, affected pin/part/wire glow, context actions, preview-before-apply fixes, and an unobtrusive status entry point. Guide feedback uses the same rule output for its LED and short-circuit lessons.

## Diagnostic coverage matrix

`U` means an automated JVM rule test; `D` means an instrumented Android app scenario. `—` means no dedicated automated assertion yet. A rule may be exercised indirectly in other scenarios. Smart fixes are offered only where their target is unambiguous.

| Rule ID | Main evidence | Coverage | One-tap fix |
|---|---|---|---|
| NO_GROUND | Ground count, source | U, D | Add ground to single source |
| SOURCE_SHORT | Low-resistance path, source voltage | U, D | Remove direct bypass wire |
| SOURCE_CONFLICT | Legacy source constraints | U | — |
| FLOATING_ISLAND | Legacy connectivity | U | — |
| SOLVER_FAILURE | Error and iteration detail | U, suppression | — |
| DISCONNECTED_LOAD | Unwired required pins | U, D | — |
| SOURCE_UNCONNECTED | Unwired source pin | U | — |
| LED_REVERSED | Solved LED voltage/current | U, D | Swap two unique LED wires |
| LED_NO_RESISTOR | Resistor bypass, source and LED model | U, D | Insert estimated series resistor |
| LED_OVERCURRENT | Current vs configured limit | U, modeled reading | Raise unique series resistor value |
| POWER_OVERLOAD | Power/rating or live failed-open state | U, D | — |
| CAP_OVER_VOLTAGE | Solved voltage vs configured rating | U, D | — |
| CAP_REVERSED | Electrolytic pin voltage | U | — |
| GPIO_OVERVOLTAGE | Solved pin voltage vs board metadata | U, D | — |
| INVALID_GPIO_MODE | Stored mode vs pin capabilities | U | — |
| INVALID_PWM_PIN | Stored PWM mode vs pin capabilities | U | — |
| GPIO_DIRECT_MOTOR | Shared GPIO and motor node | U, D | — |
| GPIO_DIRECT_RELAY | Shared GPIO and relay node | U, D | — |
| INVALID_ADC_PIN | Sensor output on non-ADC pin | U, D | — |
| MISSING_COMMON_GROUND | Sensor signal with separate ground nets | U, D | — |
| UART_TX_TX | Two transmitter pins on one net | U, D | — |
| AMMETER_ACROSS_SOURCE | Meter across source terminals | U, D | — |
| OHMMETER_POWERED | Resistance mode and running circuit | U | — |
| VOLTMETER_IN_SERIES | Meter in a simple source/load path | U, D | — |
| UNPOWERED_IC | Explicit supply pins unwired | U, D | — |
| OUTPUT_CONTENTION | Opposing configured GPIO outputs on one net | U | — |
| FLOATING_GPIO | Simulated UNKNOWN digital input | U | — |

The registry has **7 rule groups and 27 emitted rule IDs**. It is a foundation, not a claim of exhaustive circuit diagnosis. Some checks intentionally require a solved operating point; others work from topology alone.

## Smart recommendations and evidence

The direct LED recommendation estimates a forward drop using the app's LED model at 75% of its configured current limit, computes minimum resistance from the configured source voltage, and rounds up to a listed value. The calculation, assumptions, and source of ratings appear in evidence. This is an educational estimate, not a component-specific datasheet limit.

Five fix types are modeled: remove an unambiguous bypass wire, reverse an LED's two unique wires, add a reference ground to a single source, set a component value, and insert a series LED resistor where there is clear canvas space. Every offered action is revalidated against the current circuit, shown in a confirmation preview, and goes through Undo. The first four circuit-changing fix paths (bypass, reverse, ground, insert) were applied and undone in Android tests. The generic set-value path is implemented but has no dedicated Android test yet.

Evidence includes solver readings, topology observations, configured values, board metadata, and model notes. The UI can focus highlighted parts, pins, and wires; it does not yet draw a full reconstructed current path or cross-link every issue to a datasheet page.

## Verification

- `assembleDebug`, `assembleDebugAndroidTest`, and `testDebugUnitTest --offline`: passed; **138 JVM tests** across the project, including **17 Phase 3 JVM tests**.
- Phase 3 Android instrumentation on `emulator-5560`: six tests cover fix/Undo cycles, one Circuit Check UI evidence/preview flow, and a device sweep of 12 additional fault assertions. Across those and the four fix scenarios, **16 distinct faulty circuit conditions** were detected in the running app.
- Negative cases: valid LED series resistor, open switch not flagged as a short, parallel voltmeter not flagged as series, correctly rated capacitor not flagged, independent source/divider circuit not flagged as source conflict.
- Performance: a 102-component, 102-wire circuit took **18 ms** for a standalone intelligence analysis in one local JVM test run. This is a sample measurement, not a device-wide latency guarantee.

## Known limits and remaining work

- The requested 15 manual UI fault walkthroughs are still outstanding; instrumented scenarios do not replace human visual review of all 15.
- The `SetValue` fix needs a dedicated Android Undo test; the rule and offered value fix have a JVM regression case.
- GPIO current limits, I2C/SPI protocol behavior, firmware-driven PWM, breadboard connectivity, diode/transistor/MOSFET stress, comprehensive op-amp/555 analysis, and oscilloscope probe coaching are outside current rule coverage. Board checks use local board metadata and modeled voltages; they are not physical-hardware safety certification.
- The series voltmeter rule recognizes a simple one-source/one-load path. Complex networks may need graph-level meter-path analysis.
- Live thermal failure can change a resistor's present power to zero. The overload finding is retained when the solver marks it `FAILED_OPEN`, but a full causal history of all component failures is not modeled.
- The app remains offline for the intelligence flow. Projects can call the same engine later; there is no dedicated project-wide batch checker yet.

Phase 4 may build on these models after the remaining Phase 3 manual checks and targeted regression cases are completed.
