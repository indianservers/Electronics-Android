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
- Addressable RGB LEDs, USB HID/JTAG, DAC output, CAN, radio/network stacks, and chip-specific register programming are not simulated. S2 WROVER is a legacy EOL module. Main headers and SPI/ICSP connectors are modeled; USB and debug/test pads are not general wire terminals.
- UART, I²C, and SPI are event buses. Bit timing is not claimed.
- Mega Serial1/2/3 are labeled. The runtime executes Serial on D0/D1.
- ESP32 DAC is hardware-only. Wi-Fi and Bluetooth are not simulated.
- Onboard regulators are educational power rules, not transistor models.
- RESET and BOOT do not enter a simulated bootloader.

## Instruments and learning
- Scope, meter, and generators read the solver. They are not calibrated lab instruments.
- Ten Guide lessons are interactive. Two project cards (Automatic Street Light, Smart Irrigation) are Preview and do not open a circuit.
- Battery percentage and thermal destruction are not modeled. Overload warnings and fuse I²t opening are.

## Persistence and privacy
- Circuits, firmware, and guide progress stay on the device. The manifest requests no network permission.
- Schema version is 4. A corrupt autosave shows “This circuit could not be fully loaded.” and does not replace the in-memory circuit with partial data from that failed decode.
