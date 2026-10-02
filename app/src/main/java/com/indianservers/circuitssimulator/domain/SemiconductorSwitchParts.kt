package com.indianservers.circuitssimulator.domain

object SemiconductorSwitchParts {
    val pinNames=mapOf(Kind.DARLINGTON to listOf("C","B","E"),
        Kind.SCR to listOf("A","G","K"),Kind.TRIAC to listOf("MT2","G","MT1"))
    val definitions=listOf(
        Definition(Kind.DARLINGTON,"Two cascaded NPN junctions provide very high current gain",listOf(
            Parameter("is","Effective junction saturation current","A",1e-12,1e-18,1e-8),
            Parameter("bf","Forward current gain","",1000.0,20.0,10000.0),
            Parameter("br","Reverse current gain","",1.0,.1,100.0),
            Parameter("vaf","Early voltage","V",100.0,1.0,1000.0)),
            keywords=listOf("transistor","NPN","high gain"),applications=listOf("High-gain switching"),
            limitations=listOf("Educational two-junction Ebers-Moll approximation; no named manufacturer part.")),
        Definition(Kind.SCR,"Gate-triggered unidirectional latching power switch",listOf(
            Parameter("triggerVoltage","Gate trigger voltage","V",.7,.1,3.0),
            Parameter("holdingCurrent","Holding current","A",.01,.0001,1.0),
            Parameter("onResistance","On resistance","Ω",1.0,.01,100.0),
            Parameter("gateResistance","Gate resistance","Ω",1000.0,1.0,1e6)),
            keywords=listOf("thyristor","silicon controlled rectifier"),applications=listOf("Controlled rectification"),
            limitations=listOf("Simplified latch; no turn-on dynamics or dv/dt triggering.")),
        Definition(Kind.TRIAC,"Bidirectional gate-triggered latching AC switch",listOf(
            Parameter("triggerVoltage","Gate trigger voltage","V",.7,.1,3.0),
            Parameter("holdingCurrent","Holding current","A",.01,.0001,1.0),
            Parameter("onResistance","On resistance","Ω",1.0,.01,100.0),
            Parameter("gateResistance","Gate resistance","Ω",1000.0,1.0,1e6)),
            keywords=listOf("AC switch","thyristor","dimmer"),applications=listOf("AC load control"),
            limitations=listOf("Simplified symmetric quadrants; no commutation dynamics."))
    )
}
