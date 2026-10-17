package com.indianservers.circuitssimulator.domain
object GasSensorParts {
    val pins=listOf("VCC","GND","SDA","SCL")
    val pinNames=mapOf(Kind.SCD41_CO2 to pins)
    val limitations=listOf("Four-pin simulated carrier with VDD tied to VCC and external pull-ups; not the bare LGA pinout.",
        "Periodic 5 s measurement mode, start/stop, data-ready status, read measurement and CRC-8. Other commands NACK. Fixed I2C address 0x62.",
        "Calibrated CO2, temperature and humidity stimuli; no gas transport, response lag, calibration, drift, noise or self-heating physics. Only specified temperature range and CO2 output range are accepted; invalid input produces no sample (simulator fault policy).",
        "Educational 8 kOhm DC load does not model the documented 205 mA peak supply demand. A CO2 sensor does not identify CO, combustible gases, smoke or particulate pollution.")
    val metadata=DatasheetMetadata(manufacturer="Sensirion",partNumber="SCD41",packageName="Four-pin simulated I2C carrier",
        physicalPinOrder=pins,referenceUrl="https://sensirion.com/media/documents/48C4B7FB/67FE0194/CD_DS_SCD4x_Datasheet_D1.pdf",
        revision="Version 1.7, April 2025",lastVerifiedUtc="2026-10-16",sourceNotes="Sections 2.1, 3.6, 3.12: 5 s periodic measurement, CRC polynomial 0x31 initial 0xFF. Output range 0–40000 ppm differs from specified accuracy range 400–5000 ppm.")
    val definitions=listOf(Definition(Kind.SCD41_CO2,"SCD41 photoacoustic CO2 / temperature / humidity polling carrier",listOf(
        Parameter("address","Fixed I2C address","",98.0,98.0,98.0),
        Parameter("co2Ppm","Calibrated CO2","ppm",800.0,0.0,40000.0),
        Parameter("temperatureC","Calibrated temperature","°C",25.0,-10.0,60.0),
        Parameter("humidityPercent","Calibrated humidity","% RH",50.0,0.0,95.0)),datasheet=metadata,
        keywords=listOf("CO2","gas","I2C","SCD41"),applications=listOf("CO2 ventilation monitoring"),limitations=limitations))
}
