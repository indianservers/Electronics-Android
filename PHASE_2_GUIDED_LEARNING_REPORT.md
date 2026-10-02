# Phase 2 Guided Learning Report

## Scope and status

Phase 2 adds an offline interactive Guide to the existing Android circuit simulator. Ten lessons are available. They use the production canvas, DC solver, diagnostics, and multimeter. Phase 3 was not started.

The ten scripted lesson flows pass unit tests, and the first LED lesson passes a runtime ViewModel end-to-end test on an emulator. One touch UI test covers opening Guide, placing the first part, hints, restart, exit, and resume. **The requirement to manually finish each of the ten lessons through touch UI has not been met.** Treat that as the remaining acceptance gate before declaring Phase 2 fully verified.

## Files created

- `app/src/main/java/com/indianservers/circuitssimulator/guide/GuidedFlowEngine.kt` — reusable step criteria, topology checks, meter criteria, progression, hints, feedback, and event log.
- `app/src/main/java/com/indianservers/circuitssimulator/guide/LessonCatalog.kt` — structured lesson definitions.
- `app/src/main/java/com/indianservers/circuitssimulator/guide/LessonTemplates.kt` — editable starting circuits and fault examples.
- `app/src/main/java/com/indianservers/circuitssimulator/data/GuideStore.kt` — atomic active-lesson snapshot plus completion preferences.
- `app/src/main/java/com/indianservers/circuitssimulator/ui/GuideScreen.kt` — onboarding, lesson catalog, progress, resume, and in-simulator HUD.
- `app/src/test/java/com/indianservers/circuitssimulator/GuidedFlowEngineTest.kt` — progression, topology, measurements, faults, hints, and all ten scripted completions.
- `app/src/androidTest/java/com/indianservers/circuitssimulator/GuideUiTest.kt` — touch UI navigation and first placement flow.
- `app/src/androidTest/java/com/indianservers/circuitssimulator/GuideStoreInstrumentedTest.kt` — stored circuit and progress round trip.
- `app/src/androidTest/java/com/indianservers/circuitssimulator/GuideRuntimeInstrumentedTest.kt` — first LED completion and live voltage-probe integration.

## Files changed

- `ui/AppRoot.kt`: Guide entry on Home and bottom navigation, Guide routing, and corrected Projects wording.
- `ui/HubSections.kt`: project preview wording no longer promises unavailable tutorials.
- `ui/SimulatorScreen.kt`: guided HUD and highlighted component tray card; initial paused solver errors are hidden during a lesson.
- `ui/CircuitCanvas.kt`: subtle halos on the current step's component pins.
- `ui/SimulatorViewModel.kt`: guide session lifecycle, real simulator events, isolated save behavior, progress, restart, resume, completion, and Save a Copy.

The working tree already contained substantial Phase 1 and earlier changes when this phase began. They were preserved.

## Architecture and validation

Each `GuidedLesson` has a real `Circuit` template and ordered `LessonStep` definitions. A step contains its instruction, explanation, progressive hints, optional pin focus, and one typed criterion. `GuidedFlowEngine` evaluates a `GuideSnapshot` with actual circuit connectivity and solver state. It does not use screen coordinates, hard-coded component IDs, or a tap count to validate wiring.

The ViewModel forwards component placement and deletion, wire creation and deletion, parameter changes, switch toggles, Run state, probe changes, and solver updates. It records recent events, advances when the active criterion is true, and regresses reversible structural steps when a required connection or part is removed. It gives direct feedback for source shorts, reverse LEDs, excess LED current, wrong answers, and non-matching wires.

Topology checks cover source grounding, source-to-resistor connections, resistor-to-LED anode, LED return, complete LED series loops with optional switch, and two-resistor series and parallel networks. Electrical nets come from the same `Circuit.electricalConnections()` model used by the simulator. Voltage and current steps call the production `Multimeter` and require correct meter mode, relevant probes or branch wire, running state, and a reading within a tolerance. Resistor voltage checks accept either physical pin orientation.

## Lessons available

| # | Lesson | Steps | Main validation |
|---|---|---:|---|
| 1 | Light Your First LED | 10 | Placement, series wiring, Run, useful LED current |
| 2 | Control an LED with a Switch | 15 | Switched series path, open/dark and closed/lit |
| 3 | Measure Voltage with a Multimeter | 4 | DC V across source, resistor, and LED |
| 4 | Measure Current | 2 | Real virtual ammeter in a resistor branch |
| 5 | Explore Ohm's Law | 6 | Parameter edits and 5 mA / 2.5 mA readings |
| 6 | Resistors in Series | 6 | Two-part series topology and two voltage drops |
| 7 | Resistors in Parallel | 6 | Parallel topology and equal branch voltage |
| 8 | Why Isn't the LED Lighting? | 3 | Reverse-polarity diagnosis and solved repair |
| 9 | Find the Short Circuit | 4 | Source-short diagnosis, bypass removal, LED verification |
| 10 | Find the Broken Connection | 3 | Open-pin diagnosis and connected repair |

All ten are structured interactive lessons; no preview-only lessons were added to Guide. The UI also shows difficulty, step count, completion marks, and active progress. Completion offers practice again, next lesson, Guide home, and Save a Copy.

## Persistence and project isolation

`GuideStore` atomically saves the lesson ID, step index, hints, recent event log, and the real circuit document. The user can leave Guide and resume after navigation or process recreation. Starting a lesson preserves the normal working circuit and project identity in memory; leaving restores them. Guide edits bypass normal project autosave. A completed circuit enters Saved Circuits only through Save a Copy. Restart resets the lesson template and step state. The pre-Guide ordinary working circuit persists through the existing normal circuit save path.

## Verification

- `./gradlew.bat testDebugUnitTest --offline` — passed.
- `./gradlew.bat assembleDebug assembleDebugAndroidTest --offline` — passed.
- `GuideUiTest`, `GuideStoreInstrumentedTest`, and `GuideRuntimeInstrumentedTest` on isolated `Phase1_CircuitReview` Android emulator (`emulator-5560`) — four tests passed.
- Unit flow test reached completion for all ten lessons using real solver and multimeter snapshots. It also checks incorrect probe placement, stopped simulation, missing wires, reverse LED, source short, hints, and topology variants.
- The app was launched on the emulator and Home, Guide catalog, and first-lesson HUD were visually inspected at phone size. The first component was placed through touch UI in the instrumented test.

### Manual touch completion status

| Lesson | Full touch UI completion |
|---|---|
| Light Your First LED | Not yet; runtime ViewModel completion passed |
| Remaining nine | Not yet; scripted solver flow passed |

This distinction matters: engine tests and ViewModel integration show the criteria are satisfiable, but they do not verify that every pin, meter control, and hint can be comfortably reached by a learner on each device size. A full ten-lesson touch walkthrough, including a tablet pass, remains the Phase 2 acceptance task.

## Known limits and deferred work

- Only ten lessons are available. The brief recommends twelve to fifteen, but ten is its stated minimum.
- The Guide HUD can be collapsed and scrolled on a phone; tablet layout and all lesson overlays have not been manually reviewed.
- Only the active unfinished lesson is resumable. Starting a different lesson replaces that active snapshot; completed lessons retain completion marks.
- A resumed lesson starts paused with meter probes detached. The learner must tap Run and reconnect probes where needed.
- Full touch UI completion of all ten lessons is outstanding, so readiness for Phase 3 should be considered provisional until that acceptance check passes.
