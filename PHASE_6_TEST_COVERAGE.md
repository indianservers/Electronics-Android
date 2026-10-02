# Phase 6 test coverage

| Subsystem | Unit | Instrumented | UI | Manual this pass |
|---|---|---|---|---|
| DC / diodes / transistors | `DcSolverTest`, `DiodeModelTest`, `SemiconductorModelTest`, `OpAmpTest` | — | — | Not re-tapped on device |
| Transient RC/RL | `TransientSolverTest` including 5 V / 1 kΩ / 100 µF | — | — | Analytic comparison in JVM |
| AC | `AcSolverTest` | — | — | — |
| Digital | `DigitalEngineTest`, `CanvasDigitalSimulatorTest` | — | — | — |
| Boards | `BoardPlatformTest`, `PhaseFiveBBoardTest` | `PhaseFiveBBoardInstrumentedTest` | Explore place NodeMCU | Prior emulator pass |
| Firmware | `FirmwareRuntimeTest`, `PhaseFiveEmbeddedTest` | `FirmwareLabInstrumentedTest` | — | Prior emulator pass |
| Intelligence | `CircuitIntelligenceTest` | `CircuitIntelligenceInstrumentedTest` | — | — |
| Guide | `GuidedFlowEngineTest` | `GuideRuntimeInstrumentedTest`, `GuideUiTest` | — | Not every lesson clicked this pass |
| Persistence | — | `CircuitJsonInstrumentedTest`, `ProjectStoreInstrumentedTest` | — | — |
| Instruments | `MultimeterTest`, `ScopeAnalysisTest` | `InstrumentInteractionTest` | — | — |
| Catalog | `RegistryValidationTest` | `PhaseOneCatalogInstrumentedTest` | `PhaseOneExploreUiTest` | — |

Component-by-component place/wire/save rows are covered for Phase 1 parts by `PhaseOneCatalogInstrumentedTest` (61 definitions including 11 boards). A hand-filled row for every catalog kind was not repeated in this pass.
