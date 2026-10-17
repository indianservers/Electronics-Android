package com.indianservers.circuitssimulator.domain

/** Nominal DC transfer functions; package pins are listed in physical pin-number order. */
object IotSensorParts {
    data class Profile(val kind: Kind, val manufacturer: String, val part: String,
        val packageName: String, val pins: List<String>, val supplyMin: Double,
        val supplyMax: Double, val temperatureMin: Double, val temperatureMax: Double,
        val offset: Double, val slope: Double, val referenceSupply: Double?,
        val current: Double, val url: String, val revision: String,
        val stimulusKey:String="temperatureC",val stimulusUnit:String="°C",val stimulusMin:Double=temperatureMin,
        val stimulusMax:Double=temperatureMax,val stimulusDefault:Double=25.0) {
        val supplyPin get() = pins.indexOf("V+")
        val outputPin get() = pins.indexOf("VOUT")
        val groundPin get() = pins.indexOf("GND")
    }
    private const val tmp = "https://www.analog.com/media/en/technical-documentation/data-sheets/tmp35_36_37.pdf"
    private const val mcp = "https://ww1.microchip.com/downloads/aemDocuments/documents/MSLD/ProductDocuments/DataSheets/MCP970X-Family-Data-Sheet-DS20001942.pdf"
    private val to92 = listOf("V+", "VOUT", "GND")
    val profiles = listOf(
        Profile(Kind.ZE07_CO,"Winsen","ZE07-CO","Three-pin simulated analog carrier",to92,5.0,12.0,-10.0,55.0,.4,.0032,null,0.0,
            "https://www.winsen-sensor.com/d/files/manual/ze07-co.pdf","Version 1.7, 19 October 2020","coPpm","ppm",0.0,500.0,0.0),
        Profile(Kind.MPX5010_PRESSURE,"NXP Semiconductors","MPX5010","Three-pin simulated pressure carrier",to92,4.75,5.25,0.0,85.0,.04,.09,1.0,0.0,
            "https://www.nxp.com/docs/en/data-sheet/MPX5010.pdf","Rev. 14, 17 May 2024","pressureKpa","kPa",0.0,10.0,2.0),
        Profile(Kind.TMP35_SENSOR,"Analog Devices","TMP35","TO-92",to92,2.7,5.5,10.0,125.0,0.0,.01,null,50e-6,tmp,"H"),
        Profile(Kind.TMP36_SENSOR,"Analog Devices","TMP36","TO-92",to92,2.7,5.5,-40.0,125.0,.5,.01,null,50e-6,tmp,"H"),
        Profile(Kind.TMP37_SENSOR,"Analog Devices","TMP37","TO-92",to92,2.7,5.5,5.0,100.0,0.0,.02,null,50e-6,tmp,"H"),
        Profile(Kind.MCP9700_SENSOR,"Microchip Technology","MCP9700","TO-92",to92,2.3,5.5,-40.0,125.0,.5,.01,null,6e-6,mcp,"DS20001942L"),
        Profile(Kind.MCP9701_SENSOR,"Microchip Technology","MCP9701","TO-92",to92,3.1,5.5,-10.0,125.0,.4,.0195,null,6e-6,mcp,"DS20001942L"),
        Profile(Kind.AD22100_SENSOR,"Analog Devices","AD22100S","TO-92",to92,4.0,6.5,-50.0,150.0,1.375,.0225,5.0,500e-6,
            "https://www.analog.com/media/en/technical-documentation/data-sheets/ad22100.pdf","D"),
        Profile(Kind.AD22103_SENSOR,"Analog Devices","AD22103K","TO-92",to92,2.7,3.6,0.0,100.0,.25,.028,3.3,500e-6,
            "https://www.analog.com/media/en/technical-documentation/data-sheets/AD22103.pdf","B"),
        Profile(Kind.HIH4030_SENSOR,"Honeywell","HIH-4030-001","SMD (sensing face view, left to right)",listOf("GND","VOUT","V+"),4.0,5.8,-40.0,85.0,.16,.0062,1.0,200e-6,
            "https://www.pololu.com/file/0J324/HIH-4030-datasheet.pdf","009021-4-EN")
    ).associateBy { it.kind }
    val pinNames = profiles.mapValues { it.value.pins }
    val definitions = profiles.values.map { profile ->
        val humidity = profile.kind == Kind.HIH4030_SENSOR
        val equation = if(profile.kind==Kind.ZE07_CO) "VOUT = 0.4 V + CO ppm × 1.6 V / 500 ppm"
            else if(profile.kind==Kind.MPX5010_PRESSURE) "VOUT = VS × (0.09 × pressure kPa + 0.04)"
            else if (humidity) "VOUT = VS × (0.16 + 0.0062 × RH × (1.0546 − 0.00216 × T))"
            else "VOUT = ${profile.offset} V + ${profile.slope} V/°C × T" +
                (profile.referenceSupply?.let { "; multiplied by VS / $it V" } ?: "")
        Definition(profile.kind,"Analog IoT ${if(humidity) "humidity" else if(profile.stimulusKey!="temperatureC") profile.stimulusUnit else "temperature"} sensor; $equation",listOf(
            Parameter("temperatureC","Sensed temperature","°C",25.0,profile.temperatureMin,profile.temperatureMax)
        ) + (if(profile.stimulusKey!="temperatureC") listOf(Parameter(profile.stimulusKey,"Calibrated ${profile.stimulusUnit}",profile.stimulusUnit,profile.stimulusDefault,profile.stimulusMin,profile.stimulusMax)) else emptyList()) + if(humidity) listOf(Parameter("humidityPercent","Relative humidity","% RH",50.0,0.0,100.0)) else emptyList(),
            datasheet=DatasheetMetadata(manufacturer=profile.manufacturer,partNumber=profile.part,
                packageName=profile.packageName,referenceUrl=profile.url,revision=profile.revision,
                physicalPinOrder=profile.pins,lastVerifiedUtc=if(profile.stimulusKey!="temperatureC")"2026-10-16" else "2026-10-12",
                sourceNotes="$equation. Operating supply ${profile.supplyMin}–${profile.supplyMax} V. " +
                    (if(profile.kind==Kind.TMP37_SENSOR) "Above 85 °C, supply must be at least 3.0 V (Rev H, Table 1). " else "") +
                    (if(profile.stimulusKey!="temperatureC") "Steady-state nominal analog output only: probe warm-up, response lag, supply load and output-stage current limits are omitted. ZE07-CO UART is not implemented; the carrier exposes Vin (module pin 15), DAC (10), and ground (5/14). MPX5010 carrier exposes Vs, Vout and GND; consult the chosen manufacturer package for pin numbers." else "TMP3x supply load uses the 50 µA specification ceiling; other loads use typical current.")),
            keywords=listOf("IoT","analog","ADC",if(humidity) "humidity" else if(profile.stimulusKey!="temperatureC") profile.stimulusUnit else "temperature",profile.part),
            applications=listOf("IoT environmental sensing through a board ADC"),
            limitations=listOf("Nominal steady-state transfer only. Accuracy spread, noise, thermal lag, startup, self-heating and output current limits are omitted.",
                "Output uses a numerical 1 Ω source resistance for high-impedance ADC loads, not a fitted output stage.",
                "Outside operating supply or temperature range the simulation releases the output; this is not a hardware failure prediction.") +
                if(humidity) listOf("Non-condensing environment only; recommended humidity/temperature envelope, hysteresis, drift and per-device calibration are not modeled.")
                else if(profile.kind==Kind.MPX5010_PRESSURE) listOf("The supported nominal-transfer temperature band is restricted to 0–85 °C. This does not claim the sensor's complete datasheet ambient-temperature coverage.")
                else emptyList())
    }
}
