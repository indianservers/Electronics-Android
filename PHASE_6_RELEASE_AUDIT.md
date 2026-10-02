# Phase 6 — Release audit

Compared phase reports with current source. `FINAL_PHASE_STATUS.md` is stale (it still says firmware, UART, and board canvas are missing). Those exist after Phases 5–5.B. `PHASE_4_PROJECTS_REPORT.md` is not in the repo.

| Area | Claimed status | Actual status | Bugs found | Fix status | Verified |
|---|---|---|---|---|---|
| DC solver | Educational MNA | Working. GMIN `1e-12` on diagonal. Damping 0.65/0.35. Singular circuits return an error. | None new | Documented | Existing divider/diode/transistor tests |
| Transient | RC/RL analytic tests | Working Backward Euler / trapezoidal. | Golden 5 V / 1 kΩ / 100 µF case was not the exact spec circuit | Added `fiveVoltOneKiloOhmHundredMicrofaradMatchesTau` | Unit test |
| AC | Bode exists | Complex sweep for supported linear parts. Not a full SPICE AC deck. | None | Left as Simplified | `AcSolverTest` |
| Components | Large catalog | Place/wire/simulate for registered kinds. Models are simplified. | None blocking | Status stays Simplified where models are educational | Registry validation |
| Boards | 11 boards, Phase 5.B | One `BoardRegistry`. Uno/Nano/Pico Full. Others Functional. | None new in this pass | Prior 5.B fixes kept | `PhaseFiveBBoardTest`, `BoardPlatformTest` |
| Firmware | Arduino subset + MicroPython subset | Real interpreter, not keyword scripts. Wi-Fi not executed. | None new | Kept | `FirmwareRuntimeTest`, `PhaseFiveEmbeddedTest` |
| Guide | 10 lessons | Criteria are circuit checks, not canned success. | None new | Kept | `GuidedFlowEngineTest` |
| Projects | Featured firmware projects | 9 launchable. Street Light and Irrigation had no circuit but looked like timed projects. | Misleading preview cards | Labeled Preview | Source review |
| Intelligence | Phase 3 rules | Board, GPIO, meter, I²C rules present. | None new | Kept | `CircuitIntelligenceTest` |
| Instruments | Scope, meter, Bode | Measured from solver nodes. | None new | Kept | `MultimeterTest`, `ScopeAnalysisTest` |
| Persistence | Schema v4 | JSON encode/decode, autosave catch. | Message was generic | Now: “This circuit could not be fully loaded.” | `CircuitJson` instrumented tests previously passed |
| UI | Compose hub + canvas | Dark theme. Settings text overstated “real” without limits. | Marketing line in Settings | Honest simulation note | Compile |
| Performance | Prior smoke numbers | Host solver times only. No new device profile this pass. | Not re-profiled | Recorded as host-only | `PerformanceSmokeTest` if present |
| Accessibility | Content descriptions on catalog/canvas | Partial. Font-scale and contrast not rechecked on device this pass. | Open | Documented | Prior labels remain |

## Dead or misleading UI
- Automatic Street Light and Smart Irrigation do not launch a circuit. They now say Preview.
- No “Coming Soon” buttons in production Kotlin.
- `ParameterAnalysis` uses a seeded `Random` for tolerance Monte Carlo, which is deterministic given the seed. It is not a fake meter reading.
