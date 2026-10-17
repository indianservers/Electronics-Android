# Known limitations

This is an interactive electronics and embedded-systems simulator. It is not a SPICE replacement and not a substitute for checking a real circuit when the design is safety-critical.

## Solver
- Modified nodal analysis with a 1e-12 S diagonal shunt (GMIN) and damped Newton updates.
- Capacitors are open at DC. Inductors are short at DC.
- Diode, LED, BJT, and MOSFET models are piecewise or educational fits, not manufacturer SPICE decks.
- Op-amps saturate at their modeled rails. The 555 is a simplified astable, not a full external RC timing network.
- RLC is simulated. It is not certified as a Full resonance lab.
- AC analysis is a linear frequency sweep of supported parts.

## Boards and firmware
- Nineteen boards. The eight additions are Leonardo, Micro, Due, UNO R4 Minima, Pico 2 W, ESP32-S3-DevKitC-1 v1.1 N8R8, ESP32-C6-DevKitC-1 v1.2, and ESP32-S2-Saola-1 v1.2 WROVER. Their pin maps and nominal hardware profiles are audited; execution remains a bounded firmware subset, not CPU instruction emulation.
- ADC conversions on the new boards are nominal linear approximations. Due and UNO R4 use Arduino's default 10-bit reads; higher hardware resolutions require APIs outside the supported subset. S2 WROVER revision 0 uses the documented 13-bit default; later S2 revisions differ.
- Addressable RGB LEDs, USB HID/JTAG, DAC output, native MCU CAN controllers, radio/network stacks, and chip-specific register programming are not simulated. S2 WROVER is a legacy EOL module. Main headers and SPI/ICSP connectors are modeled; USB and debug/test pads are not general wire terminals.
- UART, I²C, and SPI are event buses. Bit timing is not claimed. External MCP2515/MCP2562FD carriers add a bounded standard-frame CAN transaction model; this does not enable native MCU CAN or certify CAN physical-layer behavior.
- Mega Serial1/2/3 are labeled. The runtime executes Serial on D0/D1.
- ESP32 DAC is hardware-only. Wi-Fi and Bluetooth are not simulated.
- Onboard regulators are educational power rules, not transistor models.
- RESET and BOOT do not enter a simulated bootloader.

## Instruments and learning
- Scope, meter, and generators read the solver. They are not calibrated lab instruments.
- Ten Guide lessons are interactive. Two project cards (Automatic Street Light, Smart Irrigation) are Preview and do not open a circuit.
- Battery electrochemistry, voltage-derived charge percentage and thermal destruction are not modeled. The shunt-monitoring project performs firmware coulomb counting from a stated initial charge and capacity. Overload warnings and fuse I²t opening are.

## Persistence and privacy
- Circuits, firmware, and guide progress stay on the device. The manifest requests no network permission.
- Schema version is 6. A corrupt autosave shows “This circuit could not be fully loaded.” and does not replace the in-memory circuit with partial data from that failed decode.

## Engineering project validation
- `testing/engineering-validation/projects` contains 20 executable thermal/occupancy project variants per board. Each has eight independently stimulated LM75B module instances and two PIR instances, I²C, wired UART, an output, firmware and fault checks.
- `PASSED_MODELED_SCOPE` in the original project archive applies to its thermal/occupancy subsystem. That historical archive does not gain new capabilities from application titles. The domain-specific continuation has separate circuits and evidence under `testing/engineering-validation/domain-projects`; its limitations are described in `domain-model-scope.txt`.
- LM75B local stimuli and removable module pull-ups are configurable; existing files retain the shared environment and enabled module pull-ups by default. Invalid temperature stimuli produce an empty simulated read; that is a model policy, not predicted physical failure behavior.

## Domain-specific monitoring models
- EZO-pH, EZO-EC and EZO-DO expose calibrated probe-domain inputs through a timed single-reading I2C subset. Reaction/electrode chemistry, calibration commands and environmental compensation are omitted.
- SCD41 implements a periodic CO2 measurement subset with CRC. ZE07-CO and MPX5010 expose steady-state nominal analog transfers; probe warm-up, response lag and output-stage/supply-current behavior are omitted. CO, CO2 and pressure are independent quantities.
- INA219 reads solved external-shunt voltages, with documented register scaling and a restricted 12-bit paired-conversion mode. Other ADC modes, filtering and accuracy spread are omitted. Firmware charge bookkeeping is not a battery-chemistry model.
- MCP2515/MCP2562FD carriers support a separate logic rail, transceiver rail, standard one-shot TX0 frames, polling receive buffers and nominal transaction timing. Wired connectivity, termination and matching configured bit rate are required by the model. Arbitration, stuffing, CRC error injection, retries, filters, extended/RTR frames and fault-confinement counters are unsupported.
- Motion projects use polling-register subsets and optional sinusoidal stimulus. Complementary roll, 1D kinematic integration, vibration RMS/trend and hydrostatic depth are explicit firmware algorithms. They do not imply full rigid-body dynamics, useful-life prediction, fluid dynamics or disaster-propagation physics.
- Carrier headers are explicitly simulated headers, not bare IC package pinouts. Nominal model tolerance, physical manufacturer accuracy and safety certification are distinct. See the saved circuit specifications and manufacturer-source audit.
