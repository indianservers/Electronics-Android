# Canvas intelligence enhancements

The canvas now runs these 20 additional checks through the existing live diagnostic engine. Review opens explanations and available fix previews; Locate highlights the affected components, pins and wires. A resolved issue clears its highlight and enters the existing resolved-issue history. Live warnings can be disabled in diagnostics.

1. Missing wire endpoint: detects references to deleted components and offers wire removal.
2. Invalid terminal: detects out-of-range, reserved, NC and internal-only endpoints.
3. Duplicate wire: detects identical terminal pairs, independent of wire direction.
4. Redundant path: detects an unlabeled wire whose endpoints remain joined without it; offers removal.
5. Bypassed passive: detects a resistor, capacitor, inductor or resistive sensor with both terminals on one net.
6. Dangling junction: identifies a junction with only one attached wire.
7. Duplicate reference: identifies ambiguous component reference names, ignoring case and surrounding whitespace.
8. Invalid saved property: validates catalog parameter ranges, finite values, integer choices and rail ordering.
9. Isolated component: identifies an unwired part and points to the exact-pin connection workflow.
10. Floating MOSFET gate: detects a wired drain/source with no external gate connection.
11. Open BJT base: detects a wired collector/emitter with no external base connection.
12. Missing supply: identifies unconnected named supply inputs on partially wired devices.
13. Missing return: identifies unconnected GND/AGND pins on partially wired devices.
14. UART RX-to-RX: detects receivers joined without a named TX terminal on the net.
15. Reversed IoT sensor power: checks V+ minus GND using each sensor's physical pin mapping.
16. IoT sensor operating supply: compares solved supply voltage with the existing manufacturer profile range.
17. IoT ADC compatibility: detects analog sensor outputs connected to GPIOs without ADC capability.
18. Analog output collision: detects multiple VOUT terminals joined on one electrical net.
19. Same-net meter probes: explains why coincident voltage probes measure zero differential voltage.
20. Same-net scope channels: identifies redundant voltage traces.

Checks use electrical connectivity, including named wire nets and internally shared pads, rather than canvas position. Missing power/control checks apply to partially wired devices, so newly placed parts receive a single isolated-part suggestion instead of a list of missing supplies. Development boards are excluded from generic supply checks because USB and internal rails are modeled separately. UART checks use explicitly named RX/TX terminals; board UART routing and firmware compatibility remain covered by the existing diagnostics.

Only successful solver results supply voltage evidence. Sensor range checks use the eight existing IoT profiles; they describe nominal simulator operating conditions, not predicted physical damage. Gate/base checks identify absent connections, not the adequacy of an attached bias circuit. Output collisions require separate inputs or intentional multiplexing. Labeled duplicate wires do not offer automatic removal because removing their label may disconnect remote net segments.

Regression tests cover all 20 checks, negative cases, safe removal connectivity, labeled nets, all eight sensor pin mappings, failed-solve suppression, and the live Review/Locate/repair workflow.
