package com.indianservers.circuitssimulator.domain
object PowerSensorParts {
    val pins=listOf("VCC","GND","SDA","SCL","IN+","IN-")
    val pinNames=mapOf(Kind.INA219_POWER to pins)
    val limitations=listOf("Six-pin simulated carrier, not the IC package order. External shunt resistor required; IN+ and IN- sense actual solver nodes. Independent 3–5.5 V logic supply; sense common mode 0–26 V.",
        "12-bit continuous shunt+bus mode (configuration 0x399F), power-down, reset, calibration, signed shunt/current and bus/power registers. Other ADC configurations are rejected. Nominal 1064 µs paired conversions; no filtering, noise or conversion-clock tolerance.",
        "Nonfinite or outside-common-mode inputs inject an invalid-conversion/OVF indication as a simulator policy, not a damage prediction. Signed reverse current is tested; reverse-energy power accounting is outside the validated subset. Projects use positive currents.",
        "Educational 8 kOhm logic supply load and 1 Tohm sense leakage; not a datasheet current-consumption model. No battery chemistry or proprietary state-of-charge estimator. Firmware charge integration requires a stated initial charge and capacity.")
    val metadata=DatasheetMetadata(manufacturer="Texas Instruments",partNumber="INA219",packageName="Six-pin simulated I2C shunt carrier",
        physicalPinOrder=pins,referenceUrl="https://www.ti.com/lit/ds/symlink/ina219.pdf",revision="SBOS448G, December 2015",
        lastVerifiedUtc="2026-10-16",sourceNotes="Sections 8.5/8.6: 10 µV shunt LSB, 4 mV bus LSB, current=shunt×cal/4096, power=current×bus/5000. Sense voltage is solver-derived, not a canned current property.")
    val definitions=listOf(Definition(Kind.INA219_POWER,"INA219 shunt voltage, current and power I2C carrier",
        listOf(Parameter("address","I2C strap address","",64.0,64.0,79.0)),datasheet=metadata,
        keywords=listOf("power","current","INA219","shunt","I2C"),applications=listOf("Solar power and charge integration"),limitations=limitations))
}
