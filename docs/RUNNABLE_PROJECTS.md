# Runnable projects

Every Projects card launches an actual circuit with an editable firmware attachment.
**Code** opens its program without running it. **Run project** compiles and starts that
program, then opens **Output** automatically. Code, Output, Pins and Bus remain available;
Compile, Run/Pause, Step, Reset and Stop work on every tab. Edits run through the same
interpreter and electrical solver as other firmware. Saving a circuit preserves its code.

| Project | Live output | Interaction |
|---|---|---|
| Blinking LED | D13 LED toggles; serial reports LED on/off | Change delays or pin writes in Code |
| Push Button LED | D2 input controls D13 LED; OPEN/PRESSED output | Toggle the wired switch |
| Traffic Light | D8/D9/D10 sequence; RED/AMBER/GREEN output | Change timings in Code |
| Servo Control | A0 reads the pot; D9 PWM sets servo target; serial angle | Adjust potentiometer position |
| Temperature Monitor | LM35 output enters A0; serial temperature | Change environment temperature |
| I2C Scanner | Firmware probes actual wired addresses; found-device output | Disconnect or change a device/address |
| ESP32 OLED | GPIO21/22 I2C writes SSD1306 RAM; visible blinking rectangle and transaction status | Edit drawing bytes or disconnect SDA/SCL |
| Pico ADC | GP26 pot input changes GP15 LED PWM; serial ADC counts | Adjust potentiometer position |
| NodeMCU LDR | Actual LDR divider feeds A0; D4 LED and light-state output | Change environment light |
| Automatic Street Light | Same wired LDR control, with lamp on in darkness | Change environment light |
| Smart Irrigation | Wired A0 moisture-control potentiometer drives D9 servo valve; OPEN/CLOSED output | Adjust the pot; it represents adjustable moisture input, not a calibrated soil sensor |

The NodeMCU example includes a conservative series divider and uses an ADC threshold
appropriate to its board profile. OLED code sends display commands and pixel data;
it no longer substitutes a scanner for drawing. The Pico program reads GP26 and
actually writes PWM to GP15.

Programs execute the supported Arduino subset in native Kotlin. This is not a full
C++ toolchain, and the app does not upload firmware to a physical board. Outputs are
computed from executed code, wired electrical nodes and peripheral state.

Validation: all 229 JVM tests pass, including execution/serial output for all eleven
projects and input-dependent OLED, LDR, PWM and valve behavior. Android instrumentation
checks the Projects Code/Run actions, editing code and observing the edited program's
actual serial output, plus the project electrical acceptance cases and board editor.
