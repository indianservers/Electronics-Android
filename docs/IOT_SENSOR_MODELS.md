# Analog IoT sensor models

Eight new parts appear under Sensors and are searchable by IoT or manufacturer part number. Connect V+ and GND to an appropriate supply and VOUT to a high-impedance board ADC. Temperature and humidity are editable component stimuli. No I²C address or digital driver is required.

Verified on 2026-10-12 against the manufacturer-authored documents linked below. These models reproduce nominal steady-state equations, not every physical datasheet characteristic. T is Celsius; RH is actual relative humidity in percent; VS is voltage measured between the sensor supply and ground.

| Part / package | Supply (V) | Modeled T (°C) | Nominal VOUT (V) |
|---|---|---|---|
| TMP35 / TO-92 | 2.7–5.5 | 10–125 | 0.010 T |
| TMP36 / TO-92 | 2.7–5.5 | −40–125 | 0.500 + 0.010 T |
| TMP37 / TO-92 | 2.7–5.5 | 5–100 | 0.020 T |
| MCP9700 / TO-92 | 2.3–5.5 | −40–125 | 0.500 + 0.010 T |
| MCP9701 / TO-92 | 3.1–5.5 | −10–125 | 0.400 + 0.0195 T |
| AD22100S / TO-92 | 4–6.5 | −50–150 | (VS / 5) (1.375 + 0.0225 T) |
| AD22103K / TO-92 | 2.7–3.6 | 0–100 | (VS / 3.3) (0.250 + 0.028 T) |
| HIH-4030-001 / SMD | 4–5.8 | −40–85 | VS (0.16 + 0.0062 RH (1.0546 − 0.00216 T)) |

The TO-92 parts use physical pins 1 V+, 2 VOUT, 3 GND. HIH-4030 terminals are GND, VOUT, V+ from left to right looking at the sensing face, per Figure 5. Canvas terminal positions are schematic; labels identify the connections. Package choices matter: other packages may have different pin orders.

Sources:

- [Analog Devices TMP35/TMP36/TMP37, Rev H](https://www.analog.com/media/en/technical-documentation/data-sheets/tmp35_36_37.pdf): page 1 transfer/ranges/pins; page 3 supply specification. Model supply current uses the 50 µA ceiling, not a typical value.
- [Microchip MCP970X, DS20001942L](https://ww1.microchip.com/downloads/aemDocuments/documents/MSLD/ProductDocuments/DataSheets/MCP970X-Family-Data-Sheet-DS20001942.pdf): pages 2–4 pin order, supply, 6 µA typical current, offset, slope and tested temperature range. MCP9701's documented accuracy range starts at −10°C; extended operation is not modeled.
- [Analog Devices AD22100, Rev D](https://www.analog.com/media/en/technical-documentation/data-sheets/ad22100.pdf): pages 3 and 5 transfer, supply, grade-specific ranges and TO-92 pins. S grade selected for −50–150°C; 500 µA typical current.
- [Analog Devices AD22103, Rev B](https://www.analog.com/media/en/technical-documentation/data-sheets/AD22103.pdf): pages 2–4 transfer, supply, pins and range; 500 µA typical current.
- [Honeywell HIH-4030/31, 009021-4-EN, manufacturer document hosted by Pololu](https://www.pololu.com/file/0J324/HIH-4030-datasheet.pdf): page 2 first-order humidity curve, temperature compensation, supply and 200 µA typical current; page 5 terminal layout. Temperature compensation is inverted to derive sensor RH from the user-entered true RH. Individual calibration examples are not used as universal coefficients.

DC and transient simulation share the same nominal output stamp. Supply current is a constant nominal load while operating. The output has a numerical 1 Ω resistance for stable ADC-load simulation; it does not reproduce the physical output-stage impedance or current limit. Device tolerance, nonlinearity, noise, startup timing, thermal response, self-heating, shutdown, damage and protection are omitted. HIH-4030 assumes non-condensing conditions; its recommended combined temperature/humidity envelope, light sensitivity, hysteresis, long-term drift and calibration spread are omitted.

Outside documented modeled supply/temperature limits, the output becomes a weak leakage branch. This is a simulator convention, not a prediction of hardware behavior. AC analysis explicitly reports these parts as unsupported. Unit tests check manufacturer nominal points, endpoints, ratiometric scaling, humidity compensation, operating bounds, metadata and circuit-level DC/transient outputs.
