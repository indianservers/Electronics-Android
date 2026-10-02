package com.indianservers.circuitssimulator.domain

object IcParts {
    val pinNames=mapOf(
        Kind.LM358 to listOf("OUT1","IN1−","IN1+","V−","IN2+","IN2−","OUT2","V+"),
        Kind.LM741 to listOf("OFFSET1","IN−","IN+","V−","OFFSET2","OUT","V+","NC"),
        Kind.COMPARATOR to listOf("IN+","IN−","GND","OUT","V+"),
        Kind.REGULATOR_7805 to listOf("IN","GND","OUT"),
        Kind.REGULATOR_LM317 to listOf("ADJ","OUT","IN"),
        Kind.L293D to listOf("EN12","IN1","OUT1","GND","GND","OUT2","IN2","VM",
            "EN34","IN3","OUT3","GND","GND","OUT4","IN4","VLOGIC"),
        Kind.ULN2003 to listOf("IN1","IN2","IN3","IN4","IN5","IN6","IN7","GND",
            "COM","OUT7","OUT6","OUT5","OUT4","OUT3","OUT2","OUT1"),
        Kind.SHIFT_74HC595 to listOf("QB","QC","QD","QE","QF","QG","QH","GND",
            "QH′","SRCLR","SRCLK","RCLK","OE","SER","QA","VCC"),
        Kind.COUNTER_CD4017 to listOf("Q5","Q1","Q0","Q2","Q6","Q7","Q3","GND",
            "Q8","Q4","Q9","CARRY","CLKEN","CLK","RESET","VDD")
    )
    private fun data(part:String,url:String,pins:List<String>,notes:String,
                     packageName:String="PDIP")=DatasheetMetadata(
        manufacturer="Texas Instruments",partNumber=part,packageName=packageName,
        referenceUrl=url,sourceNotes=notes,physicalPinOrder=pins)
    val definitions=listOf(
        Definition(Kind.LM358,"Dual operational amplifier with shared explicit supply pins",listOf(
            Parameter("gain","Open-loop gain","",100000.0,100.0,1e7),
            Parameter("outputResistance","Output resistance","Ω",10.0,.1,1000.0)),
            datasheet=data("LM358","https://www.ti.com/lit/ds/symlink/lm358.pdf",pinNames.getValue(Kind.LM358),
                "Dual op-amp pin order from TI. Rail and common-mode limits are approximated."),
            keywords=listOf("amplifier","dual opamp"),applications=listOf("Sensor amplification"),
            limitations=listOf("No bandwidth, slew, input bias or common-mode failure model.")),
        Definition(Kind.LM741,"Single op-amp with explicit power rails and physical DIP pins",listOf(
            Parameter("gain","Open-loop gain","",100000.0,100.0,1e7),
            Parameter("outputResistance","Output resistance","Ω",20.0,.1,1000.0)),
            datasheet=data("LM741","https://www.ti.com/lit/ds/symlink/lm741.pdf",pinNames.getValue(Kind.LM741),
                "Offset-null and NC pins are shown but not functionally modeled."),
            keywords=listOf("opamp","amplifier"),applications=listOf("Analog amplification"),
            limitations=listOf("Offset-null, bandwidth, slew and input bias are not modeled.")),
        Definition(Kind.COMPARATOR,"Open-collector comparator; use an external pull-up",listOf(
            Parameter("thresholdOffset","Input offset","V",0.0,-.1,.1)),
            keywords=listOf("voltage comparator","threshold","open collector"),
            applications=listOf("Threshold detection"),
            limitations=listOf("Generic model, not a named manufacturer IC.")),
        Definition(Kind.REGULATOR_7805,"Fixed 5 V positive linear regulator with dropout",listOf(
            Parameter("dropout","Dropout voltage","V",2.0,.1,5.0),
            Parameter("outputResistance","Output resistance","Ω",.5,.01,100.0)),
            datasheet=data("LM7805","https://www.ti.com/product/LM7800",pinNames.getValue(Kind.REGULATOR_7805),
                "5 V regulator family; dropout is adjustable in this educational model.","TO-220"),
            keywords=listOf("5V","linear regulator","power"),applications=listOf("5 V rail"),
            limitations=listOf("No current limit, thermal shutdown or ripple rejection.")),
        Definition(Kind.REGULATOR_LM317,"Adjustable regulator maintains 1.25 V between OUT and ADJ",listOf(
            Parameter("referenceVoltage","OUT-to-ADJ reference","V",1.25,1.0,1.5),
            Parameter("dropout","Minimum headroom","V",3.0,.1,6.0),
            Parameter("outputResistance","Output resistance","Ω",.5,.01,100.0)),
            datasheet=data("LM317","https://www.ti.com/lit/ds/symlink/lm317.pdf",pinNames.getValue(Kind.REGULATOR_LM317),
                "External resistor network sets output voltage; 1.25 V nominal reference.","TO-220"),
            keywords=listOf("adjustable regulator","power"),applications=listOf("Adjustable supply"),
            limitations=listOf("No current limit, thermal shutdown or ripple rejection.")),
        Definition(Kind.L293D,"Four half-bridge motor-driver outputs controlled by input and enable pins",emptyList(),
            datasheet=data("L293D","https://www.ti.com/lit/ds/symlink/l293.pdf",pinNames.getValue(Kind.L293D),
                "Quadruple half-H driver; separate logic and motor supplies."),
            keywords=listOf("H bridge","motor","driver"),applications=listOf("Motor drive"),
            limitations=listOf("Idealized rail switching; clamp diodes, saturation and thermal limits omitted.")),
        Definition(Kind.ULN2003,"Seven open-collector Darlington sink channels",emptyList(),
            datasheet=data("ULN2003A","https://www.ti.com/lit/ds/symlink/uln2003a.pdf",pinNames.getValue(Kind.ULN2003),
                "Seven channels; COM is clamp-diode common, not a power input."),
            keywords=listOf("driver","transistor array","relay"),applications=listOf("Relay and stepper coils"),
            limitations=listOf("Clamp diodes and transistor saturation curves omitted.")),
        Definition(Kind.SHIFT_74HC595,"8-bit serial shift register with storage latch and output enable",emptyList(),
            datasheet=data("CD74HC595","https://www.ti.com/lit/ds/symlink/cd74hc595.pdf",
                pinNames.getValue(Kind.SHIFT_74HC595),"SRCLK and RCLK are rising-edge triggered; OE is active low."),
            keywords=listOf("shift register","serial","parallel","digital"),applications=listOf("Output expansion"),
            limitations=listOf("3.3 V educational logic levels; timing and current limits omitted.")),
        Definition(Kind.COUNTER_CD4017,"Ten-state Johnson decade counter with decoded outputs",emptyList(),
            datasheet=data("CD4017B","https://www.ti.com/lit/ds/symlink/cd4017b.pdf",
                pinNames.getValue(Kind.COUNTER_CD4017),"Positive clock edge while CLKEN is low; active-high RESET."),
            keywords=listOf("decade counter","sequencer","digital"),applications=listOf("Light sequencing"),
            limitations=listOf("3.3 V educational logic levels; voltage-dependent timing omitted."))
    )
}
