package com.indianservers.circuitssimulator.domain

object LightSensorParts {
    const val source="https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/6165/bh1750fvi-e.pdf"
    val pins=listOf("VCC","GND","SDA","SCL")
    val pinNames=mapOf(Kind.BH1750_LIGHT to pins)
    val limitations=listOf("Four-pin simulated carrier ties DVI to VCC; ADDR strap is address 0x23 or 0x5C. Bare IC pins: VCC 1, ADDR 2, GND 3, SDA 4, DVI 5, SCL 6.",
        "Nominal high-resolution continuous 0x10 and one-shot 0x20 modes at default MTreg=69 only. 120 ms typical conversion is deterministic; firmware should allow the documented 180 ms maximum. Low resolution, mode2 and MTreg commands are unsupported and NACK.",
        "Raw count = nearest integer(lux * 1.2), saturated at 65535. Nominal quantization is distinct from the 0.96..1.44 count/lux accuracy limits at the datasheet test condition. Optical spectrum, angle, window, noise and accuracy spread are omitted.",
        "Before first conversion the data register is zero (page 4); there is no invented ready/status flag. Invalid/nonfinite stimuli produce an empty read as a simulator fault policy. Startup capacitor/DVI sequencing is not simulated.",
        "DC supply uses the existing educational 8 kohm peripheral load, not the 120 uA typical / 190 uA maximum operating or 0.01 uA typical power-down current. No physical power-integrity certification.")
    val metadata=DatasheetMetadata(manufacturer="ROHM Semiconductor",partNumber="BH1750FVI",revision="2011.11 Rev.D / No.11046EDT01",
        referenceUrl=source,packageName="Four-pin simulated I2C carrier",physicalPinOrder=pins,lastVerifiedUtc="2026-10-17",
        sourceNotes="Manufacturer-authored technical note retained from distributor mirror. Pages 1-2: operating VCC 2.4..3.6 V; absolute maximum VCC 4.5 V is not an operating rating; SDA 3 mA at VOL<=0.4 V; absolute maximum sink 7 mA. Pages 5-7: reset, addressing, high-resolution command/timing and big-endian count / 1.2 lux. DVI tied to VCC in this carrier.")
    val definitions=listOf(Definition(Kind.BH1750_LIGHT,"BH1750FVI nominal high-resolution ambient illuminance",listOf(
        Parameter("address","ADDR strap (35 or 92)","",35.0,35.0,92.0),
        Parameter("illuminanceLux","Incident illuminance","lx",300.0,0.0,100000.0)),datasheet=metadata,
        keywords=listOf("ROHM","BH1750","lux","light","I2C"),applications=listOf("Ambient light monitoring"),limitations=limitations))
}
