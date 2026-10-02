# Phase 5.B — Board bugs

| Board | Bug | Severity | Root Cause | Fix | Verified |
|---|---|---|---|---|---|
| All | Generic left/right pin split, same teal rectangle | High | Artwork ignored board geometry | Per-board `boardWidth`/`boardHeight`/`pcbColor`, USB nub, MCU block, antenna bar, pin anchors | Unit geometry tests; compile |
| Mega | Pins listed D0–D53, not physical headers | High | GPIO-sorted list used for drawing | Electrical index kept; `megaPlaces()` left analog/power, mid D0–21, far D22–53 | Pin-map tests |
| Uno | Missing IOREF; SDA/SCL only hardcoded A4/A5 | Medium | Incomplete header + special-case nets | IOREF appended; `pin.signal` joins SDA↔A4, SCL↔A5 | `unoSdaSharesA4Net` |
| Pico | `LED_BUILTIN` / `digitalWrite(25)` failed | High | GP25 not in header list | Non-connectable onboard GP25 appended | Alias + LED_BUILTIN tests |
| Pico W | Same artwork/LED as Pico | High | Copied Pico definition | WL_LED onboard; antenna; Wi-Fi metadata-only | Alias test |
| NodeMCU | Inspector hid GPIO aliases | Medium | `displayName` special-cased poorly | `D1 / GPIO5` from aliases + gpioNumber | Resolve(5)==D1 |
| ESP32 | Flash D2 shared GPIO2 with IO2 | High | `D2` parsed as GPIO2 | Reserved flash pins have no GPIO number | Pin-map uniqueness |
| Mega | SDA aliased to 18 (Uno I²C) | High | Shared Arduino alias helper | Mega SPI/I²C/UART aliases gated | resolve(18)==D18 |
| Mega | D10/D11 labeled SS/MOSI | Medium | Uno SPI aliases applied | Mega SPI is D50–D53 only | Alias test |
| All 3.3 V | 5 V GPIO wording generic | Medium | Shared over-voltage text | Exact board/pin title | Intelligence test |
| ESP32 | pinMode(34, OUTPUT) compiled | Medium | pinMode skipped capability check | OUTPUT mode requires DIGITAL_OUTPUT | Compile-time reject |
| Firmware | Wi-Fi advertised as not-simulated even when hardware present | Low | Matrix always NOT_SIMULATED | Hardware → METADATA | PhaseFiveEmbeddedTest |
| Catalog instrumented | Expected 57 board+phase1 parts | Medium | Four new boards | Expect 61 | Re-run |
| D1 Mini | `pins.size>=29` failed | Low | Compact 16-pin board | Threshold 16 | BoardPlatformTest |

Open / accepted limitations are in `PHASE_5B_IOT_BOARDS_REPORT.md`.
