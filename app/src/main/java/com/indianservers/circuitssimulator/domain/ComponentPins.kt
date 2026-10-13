package com.indianservers.circuitssimulator.domain

/** Electrical indices never change when a package pin number or canvas position differs. */
object ComponentPins {
    fun name(part:PlacedComponent,index:Int):String {
        require(index in 0 until part.terminalCount)
        return if(part.kind.isBoard) BoardRegistry.boards.getValue(part.kind).pins[index].name
            else standard(part,index)
    }
    fun canConnect(part:PlacedComponent,index:Int):Boolean {
        if(index !in 0 until part.terminalCount) return false
        if(part.kind.isBoard) {
            val pin=BoardRegistry.boards.getValue(part.kind).pins[index]
            return pin.connectable && pin.type!=PinType.RESERVED
        }
        return name(part,index)!="NC"
    }
    fun packageNumber(part:PlacedComponent,index:Int):Int? {
        if(part.kind.isBoard) return BoardRegistry.boards.getValue(part.kind).pins[index].physicalNumber
        val data=ComponentRegistry.definitions.getValue(part.kind).datasheet
        if(data.packageName?.let { Regex("TO.?92|TO.?220|DIP|SOIC",RegexOption.IGNORE_CASE).containsMatchIn(it) }!=true) return null
        fun key(value:String)=when(value) { "Anode"->"A";"Cathode"->"K";else->value }
        val label=key(name(part,index))
        if(data.physicalPinOrder.getOrNull(index)?.let(::key)==label) return index+1
        val matches=data.physicalPinOrder.mapIndexedNotNull { i,p -> if(key(p)==label) i+1 else null }
        return matches.singleOrNull()
    }
    fun role(part:PlacedComponent,index:Int):String {
        if(part.kind.isBoard) return BoardRegistry.boards.getValue(part.kind).beginnerHint(BoardRegistry.boards.getValue(part.kind).pins[index])
        val name=name(part,index)
        return when(name) {
            "GND","AGND","V−"->"Ground / negative supply return"
            "V+","VCC","VDD","VM","VLOGIC"->"Power supply input"
            "VOUT","OUT","OUT1","OUT2"->"Output signal"
            "C"->if(part.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON)) "Collector" else "Signal terminal"
            "B"->if(part.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON)) "Base control input" else "Terminal B"
            "E"->if(part.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON)) "Emitter" else "Signal terminal"
            "D"->if(part.kind in setOf(Kind.NMOS,Kind.PMOS)) "Drain" else "Data input"
            "G"->if(part.kind in setOf(Kind.NMOS,Kind.PMOS,Kind.SCR,Kind.TRIAC)) "Gate control input" else "Signal terminal"
            "S"->"Source"
            "A"->if(part.kind in setOf(Kind.DIODE,Kind.RECTIFIER,Kind.ZENER,Kind.SCHOTTKY_DIODE,Kind.PHOTODIODE,Kind.SCR)) "Anode" else "Terminal A"
            "K"->"Cathode"
            "W"->"Adjustable wiper output"
            "SDA"->"I2C data; needs a pull-up to the correct logic rail"
            "SCL"->"I2C clock; needs a pull-up to the correct logic rail"
            "MOSI"->"SPI data into the peripheral"
            "MISO"->"SPI data from the peripheral"
            "SCK"->"SPI clock"
            "CS"->"Chip select; active LOW"
            "TX"->"Transmit; connect to the receiver's RX"
            "RX"->"Receive; connect to the transmitter's TX"
            "RESET","SRCLR"->"Reset input"
            "NC"->"No connection; leave unwired"
            "COM"->"Common terminal"
            "+"->if(part.kind==Kind.OPAMP || part.kind==Kind.IDEAL_OPAMP) "Non-inverting input" else "Positive terminal"
            "−"->if(part.kind==Kind.OPAMP || part.kind==Kind.IDEAL_OPAMP) "Inverting input" else "Negative terminal"
            else->if(name.startsWith("IN")) "Input signal" else if(name.startsWith("OUT") || name.startsWith("Q")) "Output signal" else "Electrical terminal"
        }
    }
    fun connectionError(circuit:Circuit,a:TerminalRef,b:TerminalRef):String? {
        val parts=circuit.components.associateBy { it.id }
        val first=parts[a.componentId] ?: return "The starting component is no longer in the circuit."
        val second=parts[b.componentId] ?: return "The target component is no longer in the circuit."
        if(!canConnect(first,a.index) || !canConnect(second,b.index)) return "Choose a connectable pin. Reserved, NC and internal-only pins cannot be wired."
        if(a==b) return "Choose a different pin."
        if(a.componentId==b.componentId && b in net(circuit.copy(wires=emptyList()),a))
            return "These pads are already the same internal connection."
        if(circuit.wires.any { setOf(it.start,it.end)==setOf(a,b) }) return "These pins already have a wire."
        return null
    }
    fun net(circuit:Circuit,start:TerminalRef):Set<TerminalRef> {
        val adjacency=mutableMapOf<TerminalRef,MutableSet<TerminalRef>>()
        circuit.electricalConnections().forEach { (a,b) ->
            adjacency.getOrPut(a) { mutableSetOf() }.add(b)
            adjacency.getOrPut(b) { mutableSetOf() }.add(a)
        }
        val seen=mutableSetOf(start)
        val queue=ArrayDeque<TerminalRef>();queue.add(start)
        while(queue.isNotEmpty()) adjacency[queue.removeFirst()].orEmpty().forEach {
            if(seen.add(it)) queue.add(it)
        }
        return seen
    }
    private fun standard(p:PlacedComponent,pin:Int):String = when(p.kind) {
    in DigitalParts.pinNames.keys -> DigitalParts.pinNames.getValue(p.kind)[pin]
    in ElectromechanicalParts.pinNames.keys -> ElectromechanicalParts.pinNames.getValue(p.kind)[pin]
    in SemiconductorSwitchParts.pinNames.keys -> SemiconductorSwitchParts.pinNames.getValue(p.kind)[pin]
    in IcParts.pinNames.keys -> IcParts.pinNames.getValue(p.kind)[pin]
    in PhaseFiveParts.pinNames.keys -> PhaseFiveParts.pinNames.getValue(p.kind)[pin]
    Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR,Kind.ELECTROLYTIC,Kind.LED,
    Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED,Kind.SINGLE_CELL,Kind.BATTERY_PACK,
    Kind.VARIABLE_DC_SUPPLY,Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,
    Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR,Kind.DC_CURRENT_SOURCE -> if(pin==0) "+" else "−"
    Kind.DIODE,Kind.RECTIFIER,Kind.SCHOTTKY_DIODE,Kind.PHOTODIODE -> if(pin==0) "A" else "K"
    Kind.ZENER -> if(pin==0) "A" else "K"
    Kind.VCCS,Kind.VCVS,Kind.CCCS,Kind.CCVS -> listOf("C+","C−","O+","O−")[pin]
    Kind.TRANSFORMER -> listOf("P+","P−","S+","S−")[pin]
    Kind.RELAY -> listOf("C+","C−","COM","NO","NC")[pin]
    Kind.RGB_LED -> listOf("R","G","B","K")[pin]
    Kind.SPDT_SWITCH -> listOf("COM","A","B")[pin]
    Kind.TIMER_555 -> listOf("RESET","GND","OUT")[pin]
    Kind.D_FLIP_FLOP -> listOf("D","CLK","Q")[pin]
    Kind.T_FLIP_FLOP -> listOf("T","CLK","Q")[pin]
    Kind.COUNTER_4 -> listOf("CLK","Q0","Q1","Q2","Q3")[pin]
    Kind.ADC_2 -> listOf("VIN","GND","D0","D1")[pin]
    Kind.DAC_2 -> listOf("D0","D1","OUT","GND")[pin]
    Kind.SEVEN_SEGMENT -> listOf("A","B","C","D","E","F","G","K")[pin]
    Kind.NPN_BJT,Kind.PNP_BJT -> listOf("C","B","E")[pin]
    Kind.NMOS,Kind.PMOS -> listOf("D","G","S")[pin]
    Kind.OPAMP,Kind.IDEAL_OPAMP -> listOf("+","−","OUT")[pin]
    Kind.POTENTIOMETER -> listOf("A","W","B")[pin]
    Kind.CLOCK -> "OUT"
    Kind.NOT_GATE -> if(pin==0) "IN" else "OUT"
    Kind.AND_GATE,Kind.OR_GATE,Kind.NAND_GATE,Kind.NOR_GATE,Kind.XOR_GATE,Kind.XNOR_GATE ->
        listOf("A","B","OUT")[pin]
    Kind.GROUND -> "GND"
    Kind.JUNCTION -> ""
    else -> "${pin+1}"
}

}
