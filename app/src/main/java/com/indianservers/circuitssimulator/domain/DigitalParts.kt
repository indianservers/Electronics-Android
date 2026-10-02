package com.indianservers.circuitssimulator.domain

object DigitalParts {
    val pinNames=mapOf(
        Kind.BUFFER_GATE to listOf("IN","OUT"),
        Kind.TRI_STATE_BUFFER to listOf("IN","EN","OUT"),
        Kind.SR_LATCH to listOf("S","R","Q"),
        Kind.D_LATCH to listOf("D","EN","Q"),
        Kind.JK_FLIP_FLOP to listOf("J","K","CLK","Q"),
        Kind.MULTIPLEXER_2 to listOf("A","B","SEL","OUT"),
        Kind.DEMULTIPLEXER_2 to listOf("IN","SEL","Y0","Y1"),
        Kind.ENCODER_4 to listOf("I0","I1","I2","I3","Q0","Q1"),
        Kind.DECODER_2 to listOf("A","B","Y0","Y1","Y2","Y3"),
        Kind.LOGIC_INPUT to listOf("OUT"),
        Kind.LOGIC_OUTPUT to listOf("IN")
    )
    fun outputs(kind:Kind):IntRange=when(kind) {
        Kind.DEMULTIPLEXER_2 -> 2..3
        Kind.ENCODER_4 -> 4..5
        Kind.DECODER_2 -> 2..5
        Kind.LOGIC_OUTPUT -> IntRange.EMPTY
        else -> (pinNames.getValue(kind).size-1)..(pinNames.getValue(kind).size-1)
    }
    val definitions=pinNames.keys.map { kind ->
        val summary=when(kind) {
            Kind.BUFFER_GATE -> "Copies a digital input to a finite-drive output"
            Kind.TRI_STATE_BUFFER -> "Drives the input when enabled; otherwise high impedance"
            Kind.SR_LATCH -> "Set/reset memory element with active-high S and R"
            Kind.D_LATCH -> "Level-sensitive D storage while EN is high"
            Kind.JK_FLIP_FLOP -> "Rising-edge J/K storage; J=K=1 toggles"
            Kind.MULTIPLEXER_2 -> "Selects A or B with one digital select input"
            Kind.DEMULTIPLEXER_2 -> "Routes a digital input to one of two outputs"
            Kind.ENCODER_4 -> "Encodes the highest asserted input as a two-bit binary value"
            Kind.DECODER_2 -> "Decodes a two-bit input to one of four outputs"
            Kind.LOGIC_INPUT -> "Manually switchable 3.3 V logic stimulus"
            Kind.LOGIC_OUTPUT -> "High-impedance logic-level indicator"
            else -> error("Unknown digital part")
        }
        val parameters=when(kind) {
            Kind.LOGIC_INPUT -> listOf(Parameter("state","Output level (0 low, 1 high)","",0.0,0.0,1.0))
            Kind.LOGIC_OUTPUT -> emptyList()
            else -> listOf(Parameter("delay","Propagation delay","s",1e-5,1e-6,.1))
        }
        Definition(kind,summary,parameters,keywords=listOf("logic","digital",kind.title),
            applications=listOf("Digital logic experiments"),
            limitations=listOf("3.3 V CMOS-like educational thresholds; no chip-specific timing or supply pins."))
    }
}
