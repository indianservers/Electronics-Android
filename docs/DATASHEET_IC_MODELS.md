# NE555, LM358 and L293D models

These are nominal 25 °C behavioral macromodels with physical package pins. They do not reproduce every production tolerance, temperature curve or transistor. Manufacturer ratings and the simulator's out-of-range policy are separate.

## Manufacturer references

- [TI NE555, SLFS022K, March 2026](https://www.ti.com/lit/ds/symlink/ne555.pdf): PDIP-8 pin table, electrical characteristics, functional table, astable and monostable application circuits.
- [TI LM358, SLOS068AB, October 2024](https://www.ti.com/lit/ds/symlink/lm358.pdf): PDIP-8 pin table and section 5.7 for the original LM358. LM358B/BA specifications are **not** substituted.
- [TI L293D, SLRS008D, January 2016](https://www.ti.com/lit/ds/symlink/l293.pdf): PDIP-16 pin table, recommended conditions, electrical characteristics and driver truth table.

## NE555P

Package pins 1–8: GND, TRIG, OUT, RESET, CONT, THRES, DISCH, VCC. All eight pins are connectable. There is no configured internal oscillator frequency: external resistors and the solver's capacitor history determine timing.

The internal divider is 5 kΩ from VCC to CONT and 10 kΩ from CONT to ground. The latch sets below CONT/2, resets above CONT, and retains state between those thresholds. Low TRIG takes priority over THRES; RESET below nominal 0.7 V takes priority over both. External CONT loading changes both thresholds. Low output enables the discharge path, fitted to 0.15 V at 8 mA. Input and discharge leakage use nominal electrical-table values.

The source stage is a Thevenin fit with 1 V headroom and 7 Ω resistance, giving 3.3 V at a 5 V supply and 100 mA source load. The sink stage interpolates electrical-table VOL/IOL points, including the higher drop at heavy loads. Quiescent current interpolates the 5 V/15 V high/low test values. Output loads above 200 mA produce overload warnings; this is not simulated short-circuit protection.

Latch decisions use the last accepted transient voltages and remain fixed during Newton iterations. Threshold and reset latency can therefore be one requested time step. Use a step much smaller than the shortest timing interval and reduce it to check convergence. Startup from an uncharged capacitor has a longer first astable pulse than the steady-state waveform. DC displays the reset startup state; oscillation requires transient simulation. Outside 4.5–16 V, active output drive is disabled and a warning is shown. That policy does not predict real undervoltage behavior. Propagation delay, noise, temperature dependence and damage are omitted.

## Original LM358

Package pins 1–8: OUT1, IN1−, IN1+, V−, IN2+, IN2−, OUT2, V+. Each amplifier has separate pole history. The model supports a 3–30 V supply span and nominal 25 °C input range from V− to V+−1.5 V. An invalid channel becomes high impedance and produces a package warning; this explicitly avoids predicting behavior outside the characterized input range.

Defaults are 100,000 open-loop gain, 700 kHz gain-bandwidth, 0.3 V/µs slew, +3 mV deterministic offset and 20 nA outward input bias. Offset sign is a model choice, not a promise about individual manufactured parts. The dominant-pole state uses backward Euler with pole angular frequency `2π × GBW / gain`, then slew and rail limiting. The 50 Ω output-stage fit adds load-dependent swing; nominal source/sink limits are 30/20 mA. Supply current includes a nominal 700 µA package quiescent load and output/input currents. All port currents conserve charge.

The output swing fit uses 5 mV low-side and 1.5 V high-side internal headroom. It approximates the 5 V application region; it does not fit all 30 V load curves, low-voltage sinking behavior or short-circuit test conditions. Current limits are nominal macromodel bounds rather than universal datasheet guarantees. Noise, drift, overload recovery, capacitive-load stability, channel mismatch and AC/Bode analysis are omitted. Use time-domain sine excitation for the supported bandwidth model. Unused amplifier inputs must be terminated appropriately.

DC uses gain continuation and Newton regularization to initialize feedback loops. The final equations use the selected gain; failure to reach it is reported as non-convergence. Increasing gain beyond its nominal value can require increasing the iteration limit. Transient integration does not advance internal history during Newton iterations. Pole histories and the 555 latch survive live simulation checkpoints.

## L293D

All 16 PDIP pins are exposed. Ground pins 4, 5, 12 and 13 are internally joined. VLOGIC is pin 16 and VM is pin 8. Enables control channel pairs; disabling makes their active stages high impedance while leaving inductive clamp paths present.

Input HIGH requires at least 2.3 V and LOW is at most 1.5 V. Undefined enable/input levels disable the affected drive and generate a warning. Active operation requires a 4.5–7 V logic supply and VM between VLOGIC and 36 V. These are simulator policies outside the valid envelope, not predicted chip faults.

Nominal source drop is `0.8 V + I × 1 Ω`; sink drop is `I × 2 Ω`. At 600 mA these reproduce the table's 1.4/1.2 V typical drops. Active stages use linear Thevenin fits, including extrapolation near no-load and reverse-current conditions. The separate clamp fits approach 0.7 V plus 1 Ω, reproducing approximately 1.3 V at 600 mA. A small rounded knee keeps floating inductive nodes numerically solvable. These fits are not diode recovery or transistor curves.

State-dependent supply bias interpolates the datasheet's all-HIGH, all-LOW and all-disabled currents across four channels. Source output current is drawn directly from VM. The reading's current is **VM pin current**, not load current or the sum of differently powered rails. Dissipation sums all package port powers. A static temperature estimate uses the PDIP 36.4 °C/W thermal resistance at 25 °C ambient; board layout, heat sinks and thermal dynamics are not simulated. Above 600 mA per output the model warns of overload; it does not invent current limiting or thermal shutdown. Propagation times, switching loss, shoot-through and diode reverse recovery are omitted.

## Working samples

Open **Sample circuits** and select:

- **NE555 RC Astable**: 5 V, RA=10 kΩ, RB=100 kΩ, C=1 µF; ideal steady-state period about 145.6 ms and high interval 76.2 ms. Nominal leakage, discharge resistance and time-step sampling cause small differences. Use a 0.2 ms or smaller step for quantitative checks.
- **NE555 RC Monostable**: 5 V, R=100 kΩ, C=1 µF; a low trigger pulse starts a nominal `ln(3) × RC`, approximately 110 ms, pulse. The generator repeats once per second. Use a 0.1 ms or smaller step.
- **LM358 Sensor Amplifier x10**: 5 V supply, 0.2 V ±0.05 V at 100 Hz, 90 kΩ/10 kΩ feedback and 10 kΩ output load. Expect approximately 2.03 V DC output with a 0.5 V sine amplitude. The unused channel is a grounded follower. Use sub-microsecond steps when inspecting slew or high-frequency response.
- **L293D PWM Motor**: 5 V logic, 9 V motor supply, 100 Hz/60% enable PWM, 30 Ω/20 mH armature with back EMF and inertia. Expect forward rotation, finite drive loss and clamp recirculation during disabled intervals. Use a 50 µs or smaller step.

The existing **555 Counter RGB** sample now uses the externally wired eight-pin timer and still exercises analog-to-digital clock sensing.

## Persistence and validation

Circuit schema is 7. Older circuits without a 555 still load normally. Older three-pin 555 projects are rejected with a specific repair message rather than interpreting RESET/GND as new package pins or fabricating an external RC network. The original saved document is not rewritten by a failed decode. Rebuild those timer sections from the new samples before saving a schema-7 project.

`DatasheetIcTest` checks physical pin numbers, comparator priority, RC timing and capacitor scaling, monostable pulse width, reset discharge, amplifier gain/offset/slew/bandwidth/common-mode/current bounds, bridge direction/braking/coasting, clamp test points, power balance, PWM motor motion, checkpoints, JSON round trips and legacy rejection. Existing IC and mixed-signal tests exercise the migrated timer/counter sample.

Run `:app:testDebugUnitTest` and `:app:assembleDebug` for regression validation. On Windows/JBR, a long temporary path can prevent Java's Unix-domain loopback initialization; a short `jdk.net.unixdomain.tmpdir` JVM property avoids that local environment issue.

Validation on 2026-11-02: 420 tests across 73 suites passed, including 17 dedicated datasheet IC tests; `assembleDebug` passed. All unit-test suites were enabled, with the long firmware project matrix scoped using `VALIDATION_BOARD=ARDUINO_UNO` and `VALIDATION_PROJECT=1`. The unrestricted board/project run was stopped before completion; it is not claimed as passing. Generated tracked project fixtures were restored after validation.
