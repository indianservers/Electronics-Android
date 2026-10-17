package com.indianservers.circuitssimulator.domain

/** Simulator carriers with an implicit calibrated probe; header is not the bare module pin order. */
object ChemistrySensorParts {
    data class Profile(val kind:Kind,val part:String,val address:Int,val key:String,val unit:String,
        val normal:Double,val minimum:Double,val maximum:Double,val decimals:Int,val conversionMicros:Long,
        val revision:String,val file:String)
    val profiles=listOf(
        Profile(Kind.EZO_PH,"EZO-pH",99,"ph","pH",7.0,.001,14.0,3,900000,"V6.2, October 2026","pH"),
        Profile(Kind.EZO_EC,"EZO-EC",100,"conductivity","µS/cm",1000.0,.07,500000.0,3,600000,"V6.7, July 2026","EC"),
        Profile(Kind.EZO_DO,"EZO-DO",97,"oxygen","mg/L",8.0,0.0,100.0,2,600000,"V5.8, March 2025","DO")
    ).associateBy { it.kind }
    val pins=listOf("VCC","GND","SDA","SCL")
    val pinNames=profiles.mapValues { pins }
    val limitations=listOf(
        "Four-pin simulated I2C carrier, preconfigured in I2C mode with an implicit calibrated probe. Header order VCC, GND, SDA, SCL is not the module package order. External pull-ups required; 3.3–5.5 V supply.",
        "Single reading R command, ASCII result, conversion delay, response statuses and one-shot response consumption. Other commands return syntax error; calibration, UART mode, address-change commands and temperature/salinity/pressure compensation are not implemented.",
        "Measurement properties are independent calibrated probe-domain stimuli, not a chemical reaction, electrode voltage, salinity or oxygen-solubility simulation. EC-only and mg/L-only outputs. Invalid/nonfinite stimuli inject a simulator error response (2); this is a test policy, not a manufacturer fault guarantee.",
        "Conversion result is latched at the R command. The configured module is treated as already booted; cold-start/EEPROM boot delays, probe stabilization, drift, cross-interference and isolation are not modeled. Educational 8 kOhm supply load is not manufacturer current consumption."
    )
    fun metadata(p:Profile)=DatasheetMetadata(manufacturer="Atlas Scientific",partNumber=p.part,
        packageName="Four-pin simulated calibrated-probe carrier",referenceUrl="https://files.atlas-scientific.com/${p.file}_EZO_Datasheet.pdf",
        revision=p.revision,physicalPinOrder=pins,lastVerifiedUtc="2026-10-16",
        sourceNotes="Mode-specific I2C supply diagrams specify 3.3–5.5 V. I2C R delay: pH V6.2 p47 = 900 ms (the cover/UART p21 says 800 ms); EC V6.7 p48 and DO V5.8 p46 = 600 ms. Module is explicitly preconfigured in I2C mode; factory default UART is not emulated.")
    val definitions=profiles.values.map { p -> Definition(p.kind,"${p.part} calibrated chemistry I2C carrier",
        listOf(Parameter("address","I2C address","",p.address.toDouble(),1.0,127.0),
            Parameter(p.key,"Calibrated probe ${p.unit}",p.unit,p.normal,p.minimum,p.maximum)),
        datasheet=metadata(p),keywords=listOf("chemistry","I2C",p.part,p.unit),
        applications=listOf("Chemistry monitoring with independent probe stimuli"),limitations=limitations) }
}
