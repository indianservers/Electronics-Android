# Competitive feature implementation plan

Audit date: 11 October 2026. Scope: the 39 ranked gaps from the competitor comparison.
Status is tracked per deliverable. A phase is complete only when its acceptance checks pass.

User decision: no backend for this release. Hosted community, cloud synchronization, LMS integration and an online AI tutor are deferred to a future decision. Continue the offline features without service placeholders or fake network functionality.

## Current implementation status

The first implementation increment is in the app. The entire three-phase roadmap is not complete.

| Deliverable | Status |
|---|---|
| Additive selection, select all/clear, group move/rotate/delete | Implemented |
| Cut/copy/paste, internal wiring, identity remapping, copied-net isolation | Implemented |
| Saved flat circuit blocks with board programs and memory | Implemented |
| Persistent snap and grid-spacing controls | Implemented |
| Saved manual wire waypoints and automatic-route reset | Implemented through coordinate editor; direct waypoint dragging pending |
| PNG route export and schema 6 backward-compatible JSON | Implemented |
| Continuous tuning for existing component sliders | Implemented; broader controls and throttling pending |
| Combinational truth tables with X/Z and unsupported-circuit rejection | Implemented |
| Hierarchical custom ICs and theme migration | Pending |
| Graded learning, exams, puzzles, new components and remaining offline Phase 2 work | Pending |
| Offline Phase 3 specialist/device/language work | Pending |
| Backend-dependent integrations | Deferred by user |

Validation: 239 unit tests passed; debug app and instrumentation APK builds passed. Eight Android JSON/schema/route checks passed, and both editor tests passed after correcting the test's scrolling to horizontally hidden controls. The editor tests verify clipboard/undo, route connectivity, toolbar wiring and snapping. Device tests ran on Phase1_CircuitReview, Android API 37.1. These checks validate the implemented increment, not the remaining roadmap.

## Phase 1 — Build and reuse circuits efficiently

Goal: make larger circuits comfortable to build without compromising electrical topology.

1. Selection and clipboard (audit 2–3): additive component selection, select all/clear, group move/rotate/delete, cut/copy/paste. Remap component, wire, firmware and peripheral-memory identities. Preserve internal connectivity and isolate copied named nets from their originals. One undo entry per operation.
2. Grid (4): saved snap preference and spacing, consistent placement/movement snapping, matching rendered grid.
3. Circuit reuse (1): first deliver saved flat circuit blocks; subsequently add hierarchical instances, named ports, internal inspection, versioned definitions and a cycle/depth guard. Flat blocks are not custom ICs.
4. Routing (9): persisted manual waypoints, route reset and point editing. Coordinate editing first, direct dragging subsequently. Routing must never alter connectivity.
5. Appearance (10): light/dark/system selection throughout the hub, canvas, artwork and instruments; migrate hard-coded colors before claiming light-mode support.
6. Live tuning (18): shared preview/commit path, bounded update frequency, one undo entry for a drag, continue reactive state when topology is unchanged.
7. Supporting validation: migration tests, malformed-import rejection, undo/redo checks, clipboard cross-net isolation, board-program copy, tablet and accessibility smoke checks.

Exit: all operations exposed in the app, old projects load, new projects round-trip, electrical readings remain correct after presentation-only edits, build and relevant tests pass.

## Phase 2 — Digital experiments and structured learning

Goal: teach and verify digital designs using the same behavior as the simulator.

1. Truth tables (5): discover named logic inputs/outputs, enumerate up to eight inputs, evaluate each row deterministically, show unknown/high-impedance states and reject sequential or unsupported circuits instead of guessing.
2. Lessons and challenges (6, 12–13): gates, De Morgan, Boolean algebra, half/full adders, multiplexers, decoders, comparators, registers and counters; truth-table targets, mismatch highlighting, gate budgets and Gray-code Karnaugh maps. Persist actual completion criteria.
3. Assessments (11): offline question bank, timed sessions, result review, resumable state and learning history. Daily puzzles (19): date-seeded targets, verified solvability, streaks and optional local notification controls.
4. Digital library (27–36): three-input gates, BCD decoder, Schmitt trigger, ROM editor, PLA/PAL editors, latch variants, delay timers, LED matrix and fourteen-segment display. Implement terminal definitions, drawing, parameter validation, state/reset semantics and solver integration together.
5. Signal interaction (17, 25–26): sampled finger-drawn waveforms with interpolation and import bounds, capacitor-charge view based on solver state, optional shake impulse with lifecycle-safe sensor subscription.
6. Validation: exhaustive combinational tables; edge/reset tests for stateful parts; feedback/unknown handling; correct and incorrect student submissions; deterministic puzzle dates and assessment scoring.

Exit: visible learning activities are executable and graded; every new catalog part has a working model; no unsupported circuit is presented as a valid truth table.

## Phase 3 — Offline specialist workspaces and device integration

Goal: expand offline capabilities after local design and simulation are reliable. The hosted work below is a deferred extension, not a current release requirement.

1. Device integration (15–16): opt-in light/proximity/accelerometer/battery and available environmental sensors, capability checks, vibration/flashlight outputs, runtime permission handling, lifecycle cleanup and manual fallback inputs.
2. Community and sync (7–8): choose backend and authentication, implement project revisions, offline queue, conflict copies, account deletion, upload validation, discovery/search, attribution and reporting/moderation. Local JSON sharing can ship before the service; it is not cloud sync.
3. Languages (20): extract hard-coded strings, Android resource translations, locale picker, pseudolocale/RTL checks and consistent unit formatting. Select supported languages before publishing translations.
4. Industrial training (14): contactors, overloads, AC motor/control models, house-wiring exercises, fault injection and progress tracks. Validate training scenarios separately from the existing DC motor model.
5. Schools (21–22): provider-specific LMS adapter, instructor assignments, grading exports and completion certificates. Define certificate eligibility and distinguish completion from professional certification.
6. Tutor (23): build circuit facts and explanations locally; configure a server-side model provider for optional conversation, consent and redaction. Never embed service credentials in the APK or label canned explanations as an AI tutor.
7. 3D learning (24): breadboard hole connectivity, schematic-to-board mapping, touch controls and accessible 2D fallback; benchmark low-end devices before rollout.
8. Specialist analog parts (37–39): DIAC, tunnel diode and spark gap with documented educational models, convergence checks and reference circuits.

Deferred service decisions: backend/account provider, hosting/budget, moderation policy, LMS provider and tutor provider. User has explicitly deferred these integrations. Local circuit sharing, offline explanations and completion certificates can proceed without them. Remote production deployment and store publication are separate release actions.

Exit for current offline release: permission denial is handled; translated screens are complete; industrial models and teaching claims are validated; new features have truthful support labels. Future hosted extension requires cross-device sync with offline recovery and service-specific tests.

## Verification and release sequence

Run targeted domain/model tests during each increment, then testDebugUnitTest and assembleDebug. Run connectedDebugAndroidTest on an available emulator/device for schema, UI and persistence checks. Build release artifacts only after passing the phase exit checks. Preserve existing user release artifacts. No date/effort estimate is claimed until the first implementation slice and runtime checks establish a baseline.
