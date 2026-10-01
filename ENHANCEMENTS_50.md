# 50 important enhancements audit — 2026-10-01

This is a code and emulator audit of the current Android app. **Done** means the bounded enhancement was implemented and verified. **Partial** means usable code exists but an acceptance condition remains. **Open** means it is still missing. A row marked Done does not imply that the surrounding product area is complete.

| # | Enhancement | Priority | Status | Evidence or next acceptance condition |
| ---: | --- | --- | --- | --- |
| 1 | Search the entire component catalog from any selected category | High | Done | `CatalogScreen`; emulator search for thermistor while Basic was selected returned NTC thermistor. |
| 2 | Preserve catalog search and category across activity recreation | Medium | Done | `rememberSaveable` for both controls in `CatalogScreen`. |
| 3 | Let users clear recently used components | Medium | Done | Clear action and persisted empty recents in `CatalogScreen` and `SimulatorViewModel`. |
| 4 | Keep the touched world point fixed during pinch zoom | High | Done | `transformGesture` now uses the same vertical origin as `CircuitCanvas`. |
| 5 | Fit the whole circuit into the visible canvas | High | Done | Fit action in Simulation menu; bounds include component margins. |
| 6 | Show circuit diagnostics in an inspectable sheet | High | Done | `DiagnosticsSheet` and Simulation menu action. |
| 7 | Detect missing ground, missing source, and unwired pins | High | Done | `CircuitDiagnostics` topology checks and JVM tests. |
| 8 | Detect direct source shorts and open switches | High | Done | `CircuitDiagnostics` topology checks. |
| 9 | Flag reverse LEDs, overcurrent, overvoltage, and power-rating violations | High | Done | Operating-point checks in `CircuitDiagnostics`; LED warning seen in emulator. |
| 10 | Jump from a component diagnostic to that component | Medium | Done | Diagnostic row selects component details. |
| 11 | Export a circuit as a portable JSON document | High | Done | Android document picker; emulator exported `LED_Demo.circuit.json`. |
| 12 | Import a circuit JSON document | High | Done | Android document picker; emulator imported the exported LED circuit. |
| 13 | Bound streamed imports before JSON parsing | High | Done | Two-million-character limit in `ProjectSheet`. |
| 14 | Validate imported IDs, coordinates, parameters, wires, and solver settings | High | Done | `CircuitJson` checks; malformed-document Android tests. |
| 15 | Make project and autosave writes atomic | High | Done | `ProjectStore` uses Android `AtomicFile`. |
| 16 | Discard a rejected recovery session | High | Done | `discardRecovery`; emulator relaunch no longer repeated the prompt after Keep current. |
| 17 | Reset project identity when loading a sample or importing a document | High | Done | `replaceDocument` prevents a later Save from overwriting the prior project. |
| 18 | Clear undo/redo history when switching documents | High | Done | `replaceDocument` clears both stacks. |
| 19 | Clear probes that refer to removed parts or wires | Medium | Done | `retainValidProbes` on edits, undo, and redo. |
| 20 | Surface project save, open, and copy failures | Medium | Done | View-model messages replace silent open/copy failure and save exceptions. |
| 21 | Verify every bundled sample survives JSON round-trip | High | Done | Android instrumentation iterates all 15 current samples. |
| 22 | Make the catalog, project sheet, and diagnostics fully screen-reader navigable | High | Partial | Added descriptive button semantics and labels for key actions; TalkBack traversal and all controls still need verification. |
| 23 | Add keyboard and switch-access controls for canvas editing | Medium | Open | Place, move, connect, delete, and undo without touch. |
| 24 | Add multi-select, align, and distribute on the canvas | Medium | Open | Selection persists across transforms and is undoable. |
| 25 | Add named nets and visible wire labels | Medium | Done | Matching labels connect in DC/transient/AC/meter topology; schema v3 round-trip, canvas/PDF labels, scope and meter names; JVM and Android tests. Series current on a labeled stub now explains why a different branch is needed. |
| 26 | Add explicit orthogonal wire bend handles | Medium | Open | User routing survives movement, serialization, and undo. |
| 27 | Group sample circuits in a searchable browser | Medium | Done | Categorized 15-sample browser replaces the long menu; emulator search opened Function Generator. |
| 28 | Adapt editor panels to tablet and landscape layouts | High | Open | No clipped controls at supported window sizes. |
| 29 | Show a selectable voltage and current overlay on the canvas | Medium | Done | Simulation menu toggles per-component V/I labels; LED sample overlay matched meter values in emulator. |
| 30 | Add a navigator/minimap for larger circuits | Low | Open | Tapping the map centers the chosen area. |
| 31 | Identify electrically isolated groups before solve | High | Done | Diagnostics reports wired groups with no path to ground; sample and isolation JVM tests. |
| 32 | Identify contradictory ideal-voltage-source loops | High | Done | Voltage-constraint graph reports conflicting source voltages; parallel-source JVM test. |
| 33 | Explain nonlinear solver failure and offer convergence diagnostics | High | Partial | Solver reports iteration count, largest iterate update, tolerance, and a likely nonlinear component; nonlinear residual and detailed limiting-device proof remain. |
| 34 | Use adaptive transient steps with local error control | High | Open | Stable RC/switch results across tolerances. |
| 35 | Add parameter sweeps and tolerance analysis | Medium | Done | 41-point bounded DC parameter sweep, plotted probe voltage, CSV export, and seeded 100-sample resistor tolerance summary; JVM tests and emulator UI check. |
| 36 | Couple component temperature to electrical behavior | High | Open | Thermal state changes model parameters across time. |
| 37 | Put digital gates and four-state wires on the canvas | High | Open | Editable mixed digital/analog circuit round-trips. |
| 38 | Coordinate digital events with transient analog timesteps | High | Open | Deterministic reactive mixed-signal acceptance circuits. |
| 39 | Add an MCU board component with labeled electrical pins | High | Open | Board can wire into the circuit and save pin assignments. |
| 40 | Add an isolated, bounded firmware runtime and editor | High | Open | Deterministic GPIO/ADC/PWM program with resource limits. |
| 41 | Add UART transmit, receive, and terminal display | High | Open | Bidirectional timing and framing tests. |
| 42 | Add I²C open-drain bus and sensor transactions | High | Open | Address, ACK, clock stretching, and repeated-start tests. |
| 43 | Add SPI controller, peripheral, and protocol view | Medium | Open | CPOL/CPHA modes and chip-select timing tests. |
| 44 | Add an electrical and mechanical DC motor model | High | Open | Startup, stall, back EMF, and thermal tests. |
| 45 | Add PWM, timer, and interrupt peripherals | High | Open | Timer edges align with electrical simulation. |
| 46 | Add oscilloscope trigger and time/voltage cursors | Medium | Done | Rising/falling trigger, threshold, two time cursors, Δt and ΔV; JVM tests and emulator 10 Hz trace. |
| 47 | Add FFT and frequency-domain scope measurements | Medium | Done | Hann-windowed spectrum and peak display; JVM sine/harmonic test; emulator 10 Hz peak measured 10 Hz. |
| 48 | Export a circuit image and measurement report | Medium | Done | PNG/PDF generation passes Android test; buffered DocumentsUI exports produced a visually checked 1600×1000 PNG and a valid one-page A4 PDF on the isolated emulator. |
| 49 | Recover interrupted project writes and reveal unreadable files | High | Done | `AtomicFile.openRead` restores backups; damaged JSON stays visible as Unreadable project; Android test. Arbitrarily damaged content is not reconstructed. |
| 50 | Benchmark large-circuit rendering and solver latency on devices | High | Open | Published device/profile measurements and regression thresholds. |

## Verification for this pass

- Offline Gradle `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: successful; 73 JVM tests passed.
- Android instrumentation on isolated `emulator-5558`: 12 tests passed on the final full run. An earlier run had an activity teardown timeout; its targeted rerun and subsequent full runs passed.
- Manual emulator: global catalog search, diagnostics warning, JSON export/import, rejected-recovery relaunch, sample browser, oscilloscope trigger/cursors/FFT, canvas reading overlay, and parameter sweep/tolerance display passed. The PDF renderer was checked with Poppler.

The current count is **31 Done, 2 Partial, 17 Open**. The existing `FINAL_PHASE_STATUS.md` describes the broader product gaps and should be read alongside this scoped enhancement audit.
