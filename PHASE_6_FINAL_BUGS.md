# Phase 6 final bugs

| ID | Area | Severity | Bug | Root Cause | Fix | Verified |
|---|---|---|---|---|---|---|
| P6-01 | Projects | Medium | Street Light and Smart Irrigation looked like timed projects but had no circuit | `launch` was null while the card still described a working build | Cards say Preview and “not included yet” | Source |
| P6-02 | Settings | Low | Settings implied a general product slogan without model limits | Dialog text | States educational approximation and on-device storage | Source |
| P6-03 | Persistence | Low | Corrupt autosave message was vague | Generic catch text | “This circuit could not be fully loaded.” | Source |
| P6-04 | Transient | Low | Spec RC circuit (5 V, 1 kΩ, 100 µF) was not the fixture already tested | Existing RC test uses the sample circuit | Added golden test at one time constant | JVM test |

No Critical or High defects were found in this pass beyond issues already fixed in Phase 5.B. `FINAL_PHASE_STATUS.md` is historical and contradicts later firmware work; it was not rewritten as a live status file.
