package com.indianservers.circuitssimulator.domain

object ElectromechanicalParts {
    val pinNames=mapOf(
        Kind.BUZZER to listOf("+","−"),
        Kind.SPEAKER to listOf("+","−"),
        Kind.SERVO_MOTOR to listOf("V+","GND","SIG"),
        Kind.SOLENOID to listOf("+","−"),
        Kind.DIP_SWITCH_4 to listOf("1A","1B","2A","2B","3A","3B","4A","4B"),
        Kind.DPDT_RELAY to listOf("COIL+","COIL−","COM1","NO1","NC1","COM2","NO2","NC2"),
        Kind.REED_SWITCH to listOf("A","B"),
        Kind.LM35_SENSOR to listOf("V+","VOUT","GND"),
        Kind.HALL_SENSOR to listOf("V+","VOUT","GND"),
        Kind.PHOTOTRANSISTOR to listOf("C","E")
    )
    private fun p(key:String,label:String,unit:String,default:Double,min:Double,max:Double)=
        Parameter(key,label,unit,default,min,max)
    val definitions=listOf(
        Definition(Kind.BUZZER,"Active buzzer draws current when voltage exceeds its start threshold",listOf(
            p("resistance","Operating resistance","Ω",120.0,10.0,10000.0),
            p("startVoltage","Start voltage","V",3.0,.1,24.0)),
            keywords=listOf("alarm","sound","beeper"),applications=listOf("Audible alerts"),
            limitations=listOf("Electrical load and on/off state only; audio is not synthesized.")),
        Definition(Kind.SPEAKER,"Moving-coil speaker modeled as a resistive-inductive winding",listOf(
            p("resistance","Voice-coil resistance","Ω",8.0,1.0,1000.0),
            p("inductance","Voice-coil inductance","H",.0005,1e-6,1.0)),
            keywords=listOf("audio","loudspeaker"),applications=listOf("Audio loads"),
            limitations=listOf("Acoustic radiation and cone motion are not modeled.")),
        Definition(Kind.SERVO_MOTOR,"Powered servo with signal-dependent target position and supply current",listOf(
            p("idleCurrent","Idle supply current","A",.02,.001,1.0),
            p("stallCurrent","Moving/stall current","A",.3,.01,5.0)),
            keywords=listOf("RC servo","actuator","motor"),applications=listOf("Position control"),
            limitations=listOf("Angle follows 1–2 ms pulses at 50 Hz when firmware drives SIG; first-order motion only.")),
        Definition(Kind.SOLENOID,"Resistive-inductive coil with current-derived actuation",listOf(
            p("resistance","Coil resistance","Ω",24.0,1.0,10000.0),
            p("inductance","Coil inductance","H",.05,1e-5,100.0),
            p("pullInCurrent","Pull-in current","A",.1,.001,10.0)),
            keywords=listOf("electromagnet","actuator"),applications=listOf("Linear actuation"),
            limitations=listOf("Mechanical stroke and magnetic saturation are not modeled.")),
        Definition(Kind.DIP_SWITCH_4,"Four electrically independent manual SPST contacts",(1..4).map {
            p("switch$it","Switch $it (0 open, 1 closed)","",0.0,0.0,1.0) },
            keywords=listOf("DIP","configuration","switch bank"),applications=listOf("Configuration inputs")),
        Definition(Kind.DPDT_RELAY,"Isolated coil drives two changeover contact sets",listOf(
            p("coilResistance","Coil resistance","Ω",120.0,1.0,1e6),
            p("pullInVoltage","Pull-in voltage","V",5.0,.1,100.0)),
            keywords=listOf("relay","double pole","changeover"),applications=listOf("Motor reversal"),
            limitations=listOf("Contact bounce and coil inductance omitted.")),
        Definition(Kind.REED_SWITCH,"Magnetic field closes a two-terminal contact",listOf(
            p("field","Magnetic field present (0/1)","",0.0,0.0,1.0)),
            keywords=listOf("magnetic","door sensor","switch"),applications=listOf("Position sensing")),
        Definition(Kind.LM35_SENSOR,"10 mV/°C analog output while powered from 4–30 V",listOf(
            p("temperatureC","Sensed temperature","°C",25.0,0.0,150.0)),
            datasheet=DatasheetMetadata("Texas Instruments","LM35","TO-92",
                "https://www.ti.com/lit/ds/symlink/lm35.pdf",
                "10 mV/°C; 4–30 V supply. Educational output model; no accuracy or thermal lag simulation.",
                physicalPinOrder=listOf("V+","VOUT","GND")),
            keywords=listOf("temperature","thermometer","analog sensor"),
            applications=listOf("Temperature measurement")),
        Definition(Kind.HALL_SENSOR,"Generic ratiometric linear Hall voltage sensor",listOf(
            p("fieldMilliTesla","Magnetic flux density","mT",0.0,-100.0,100.0),
            p("sensitivity","Sensitivity","V/mT",.01,.0001,.1)),
            keywords=listOf("magnetic","field","position sensor"),
            applications=listOf("Magnetic position sensing"),
            limitations=listOf("Generic educational transfer function; no manufacturer specification claimed.")),
        Definition(Kind.PHOTOTRANSISTOR,"Light-controlled collector-emitter current sink",listOf(
            p("illuminanceLux","Illumination","lx",100.0,0.0,100000.0),
            p("currentAt100Lux","Collector current at 100 lx","A",.001,1e-6,.1),
            p("darkResistance","Dark resistance","Ω",1e8,1e4,1e12)),
            keywords=listOf("photo sensor","light","transistor"),
            applications=listOf("Optical switching"),
            limitations=listOf("Current scales linearly with lux; spectral response omitted."))
    )
}
