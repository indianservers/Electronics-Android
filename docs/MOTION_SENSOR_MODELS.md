# Motion sensor models

Four additional sensors are available under Sensors. Each has a ready-wired ESP32 example in Samples → Sensors, editable axis properties, a searchable exact-pin picker, manufacturer links in Specifications, and live device inspection during firmware execution.

| Sensor | Measurement | Valid 7-bit address straps | Supply with VDDIO tied to VCC | Manufacturer datasheet |
|---|---|---|---|---|
| L3GD20 | Three-axis angular velocity | 0x6A / 0x6B | 2.4–3.6 V | [ST, DocID022116 Rev 2](https://www.st.com/resource/en/datasheet/l3gd20.pdf) |
| LSM6DSOX | Three-axis gyroscope and accelerometer | 0x6A / 0x6B | 1.71–3.6 V | [ST, DS12814 Rev 4](https://www.st.com/resource/en/datasheet/lsm6dsox.pdf) |
| LIS3DH | Three-axis acceleration | 0x18 / 0x19 | 1.71–3.6 V | [ST, DocID17530 Rev 2](https://www.st.com/resource/en/datasheet/lis3dh.pdf) |
| ADXL345 | Three-axis acceleration | 0x53 LOW / 0x1D HIGH | 2.0–3.6 V | [Analog Devices, Rev G](https://www.analog.com/media/en/technical-documentation/data-sheets/adxl345.pdf) |

Verified against the linked manufacturer documents on 2026-10-12. ADXL345 accepts only its two documented addresses; intervening numeric values are rejected by Properties.

## Carrier pins

These are explicit simulator carriers with a four-pin header: electrical index 0 = VCC, 1 = GND, 2 = SDA, 3 = SCL. This header is not presented as the bare chip package or a particular commercial breakout. VCC ties the chip's analog and I/O supplies together. CS is strapped HIGH for I2C; SA0/ALT ADDRESS is selected by the address property. The carrier has no voltage regulator, level shifter, or internal SDA/SCL pull-ups. Use a compatible 3.3 V controller and external pull-ups (examples use 4.7 kOhm). Supply and signal limits gate actual I2C responses.

Specifications include the relevant bare-chip pin connections:

| Chip | VCC connections | GND connections | SDA | SCL | CS / address select |
|---|---|---|---|---|---|
| L3GD20 | Vdd 16, Vdd_IO 1 | 13 | 3 | 2 | 5 / 4 |
| LSM6DSOX, Mode 1 | VDD 8, VDDIO 5 | 6, 7 | 14 | 13 | 12 / 1 |
| LIS3DH | Vdd 14, Vdd_IO 1 | 5, 12 | 6 | 4 | 8 / 7 |
| ADXL345 | VS 6, VDD I/O 1 | 2, 4, 5 | 13 | 14 | 7 / 12 |

These are signal mappings, not complete physical PCB schematics: use the datasheet for all reserved pins, decoupling, layout and assembly requirements. Package pin numbers are separate from carrier header indices. Moving or rotating the canvas artwork does not remap chip-axis properties or electrical terminals.

## Supported register subsets

- **L3GD20:** WHO_AM_I at 0x0F returns 0xD4. CTRL_REG1 (0x20) controls power and axis enables; CTRL_REG4 (0x23) selects ±250/500/2000 deg/s with nominal 8.75/17.5/70 mdps per count, BDU and byte order. Signed output words occupy 0x28–0x2D; legacy I2C bursts set subaddress bit 7. Status is available at 0x27.
- **LSM6DSOX:** WHO_AM_I returns 0x6C. CTRL1_XL (0x10) and CTRL2_G (0x11) control power and ranges; CTRL3_C (0x12) supports IF_INC, BDU and immediate reset/reboot. Gyro output is 0x22–0x27, acceleration 0x28–0x2D, status 0x1E. Acceleration range encoding is 2/16/4/8 g; gyro includes the ±125 deg/s override. Default IF_INC is enabled.
- **LIS3DH:** WHO_AM_I returns 0x33. CTRL_REG1 (0x20) and CTRL_REG4 (0x23) implement power, axis enable, low-power/normal/high-resolution encoding, range, BDU and supported byte order. Output is left-aligned 8/10/12-bit signed data at 0x28–0x2D. The ±16 g high-resolution sensitivity is 12 mg/count. Set subaddress bit 7 for bursts.
- **ADXL345:** DEVID at 0x00 returns 0xE5. BW_RATE (0x2C), POWER_CTL (0x2D) and DATA_FORMAT (0x31) support measurement enable, stored rate settings, range, fixed/full resolution and justification. Signed little-endian output is 0x32–0x37; bursts increment automatically. Nominal full-resolution scale is 3.9 mg/count, with signed clipping; INT_SOURCE exposes the polling data-ready indication.

## Motion properties and limits

Angular velocity uses degrees/second; acceleration uses g. Default stationary acceleration is X = 0, Y = 0, Z = +1 g, including gravity. Inputs are per device and expressed in chip-axis coordinates; they are not integrated into position or orientation. Firmware selects measurement ranges; values beyond those ranges clip. The editor stimulus bounds cover the chip's largest supported range, not the currently selected firmware range.

Samples refresh on reads, independently of physical ODR timing. Each burst is coherent; BDU retains a word until both bytes have been read. Status indicates enabled polling channels and does not reproduce physical sample-ready/overrun timing. Power loss resets volatile controls and identity remains read-only. Unsupported register writes fail without partially modifying controls; unsupported register reads return no bytes. Physical FIFO, interrupt pins, filters, self-test, temperature outputs, startup timing, noise, drift, SPI/I3C, sensor-hub features and AI/fusion are outside this subset. The electrical load is an educational 8 kOhm resistor, not manufacturer current consumption. AC analysis explicitly excludes these digital peripherals.

Validation covers catalog/property integrity, address straps, exact identities, signed conversion, all scale families, LIS3DH resolution, byte order, burst addressing, BDU, reset/power loss, rejected unsupported operations, real firmware transactions, missing pull-ups/power/ground, crossed SDA/SCL, native properties editing, sample discovery and save/load preservation.
