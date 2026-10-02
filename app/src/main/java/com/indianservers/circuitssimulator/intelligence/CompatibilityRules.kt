package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.MeterMode
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import java.util.Locale
import kotlin.math.abs

private fun v(value:Double)="%.3g V".format(Locale.US,value)

internal object BoardCompatibilityRule:DiagnosticRule {
    override val id="BOARD_COMPATIBILITY"
    override val priority=10
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val issues=mutableListOf<IntelligenceIssue>()
        val boards=input.circuit.components.filter { it.kind.isBoard }
        boards.forEach { part ->
            val board=BoardRegistry.boards.getValue(part.kind)
            board.pins.forEachIndexed { index,pin ->
                val ref=facts.pin(part,index)
                if(ref !in facts.connectedPins) return@forEachIndexed
                val actual=facts.voltage(ref)
                if(actual!=null && pin.maxVoltage!=null &&
                    (actual>pin.maxVoltage+.2 || actual<(pin.minVoltage ?: 0.0)-.2))
                    issues+=IntelligenceIssue("GPIO_OVERVOLTAGE:${part.id}:$index","GPIO_OVERVOLTAGE",
                        IssueSeverity.ERROR,IssueCategory.BOARD_COMPATIBILITY,
                        "${pin.name} on this ${board.product} is ${board.logicVoltage} V logic and sees ${v(actual)}",
                        "${part.reference} ${board.displayName(pin.name)} measured ${v(actual)}, above the modeled ${v(pin.maxVoltage)} GPIO/ADC input range.",
                        "Use a level shifter or divider. ${board.product} I/O is ${board.logicVoltage} V. The simulator does not model on-board protection or damage.",
                        setOf(part.id),setOf(ref),evidence=listOf(
                            DiagnosticEvidence("Solved pin voltage",v(actual)),
                            DiagnosticEvidence("Modeled input range","${v(pin.minVoltage ?: 0.0)}–${v(pin.maxVoltage)}",RatingSource.GENERIC_MODEL),
                            DiagnosticEvidence("Board metadata",board.product,RatingSource.MANUFACTURER)),
                        modelNote="Based on the Phase 1 board pin model; consult the linked board specification for hardware limits.")
                val mode=part.value("gpio_${pin.name}").toInt()
                if(mode in 1..2 && PinCapability.DIGITAL_OUTPUT !in pin.capabilities)
                    issues+=IntelligenceIssue("INVALID_GPIO_MODE:${part.id}:$index","INVALID_GPIO_MODE",
                        IssueSeverity.ERROR,IssueCategory.GPIO,"${pin.name} cannot be a digital output",
                        "This pin is configured as output in the saved circuit but board metadata does not support output.",
                        "Select a GPIO pin with DIGITAL_OUTPUT capability or change this pin to input mode.",
                        setOf(part.id),setOf(ref),evidence=listOf(DiagnosticEvidence("Pin capabilities",pin.capabilities.joinToString())),
                        modelNote=board.notes)
                if(mode==3 && PinCapability.PWM !in pin.capabilities)
                    issues+=IntelligenceIssue("INVALID_PWM_PIN:${part.id}:$index","INVALID_PWM_PIN",
                        IssueSeverity.ERROR,IssueCategory.GPIO,"${pin.name} does not support PWM",
                        "The stored mode requests PWM, but this board pin lacks PWM capability.",
                        "Choose a PWM-capable pin. analogWrite is only accepted on PWM-capable headers.",
                        setOf(part.id),setOf(ref),evidence=listOf(DiagnosticEvidence("Pin capabilities",pin.capabilities.joinToString())))
                if(pin.warnings.any { it.contains("boot",ignoreCase=true) || it.contains("strapping",ignoreCase=true) })
                    issues+=IntelligenceIssue("BOOT_PIN:${part.id}:${pin.name}","BOOT_PIN",
                        IssueSeverity.WARNING,IssueCategory.BOARD_COMPATIBILITY,
                        "${pin.name} is boot-sensitive on ${board.product}",
                        pin.warnings.first(),
                        "Avoid a strong external pull that holds this pin in the wrong state at reset. Bootloader behavior is not simulated.",
                        setOf(part.id),setOf(ref))
            }
        }
        val loads=input.circuit.components.filter { it.kind in setOf(Kind.DC_MOTOR,Kind.RELAY,Kind.DPDT_RELAY) }
        boards.forEach { boardPart ->
            val definition=BoardRegistry.boards.getValue(boardPart.kind)
            definition.pins.forEachIndexed { index,pin ->
                if(PinCapability.DIGITAL_OUTPUT !in pin.capabilities) return@forEachIndexed
                val gpio=facts.pin(boardPart,index)
                if(gpio !in facts.connectedPins) return@forEachIndexed
                loads.forEach { load ->
                    val loadPin=(0..1).firstOrNull { facts.same(gpio,facts.pin(load,it)) } ?: return@forEach
                    val motor=load.kind==Kind.DC_MOTOR
                    issues+=IntelligenceIssue("DIRECT_GPIO_LOAD:${boardPart.id}:$index:${load.id}",
                        if(motor) "GPIO_DIRECT_MOTOR" else "GPIO_DIRECT_RELAY",IssueSeverity.WARNING,
                        IssueCategory.GPIO,"${pin.name} directly drives ${load.reference}",
                        "${boardPart.reference} ${pin.name} shares a wire node with ${load.reference} ${if(motor) "motor" else "coil"} pin $loadPin.",
                        "The board output model has limited drive and no dedicated motor or relay driver. Use a suitable driver stage and separate load supply as required. Coil flyback protection should be considered for physical hardware.",
                        setOf(boardPart.id,load.id),setOf(gpio,facts.pin(load,loadPin)),
                        evidence=listOf(DiagnosticEvidence("Direct connection","${pin.name} ↔ ${load.reference} pin $loadPin"),
                            DiagnosticEvidence("Board logic rail",v(definition.logicVoltage),RatingSource.GENERIC_MODEL)),
                        confidence=.95f,modelNote="No manufacturer GPIO current limit is assumed.")
                }
            }
        }
        val sensors=input.circuit.components.filter { it.kind in setOf(Kind.LM35_SENSOR,Kind.HALL_SENSOR) }
        sensors.forEach { sensor ->
            boards.forEach { board ->
                val definition=BoardRegistry.boards.getValue(board.kind)
                definition.pins.forEachIndexed { index,pin ->
                    if(!facts.same(facts.pin(sensor,1),facts.pin(board,index))) return@forEachIndexed
                    if(PinCapability.ANALOG_INPUT !in pin.capabilities)
                        issues+=IntelligenceIssue("INVALID_ADC_PIN:${board.id}:$index:${sensor.id}",
                            "INVALID_ADC_PIN",IssueSeverity.WARNING,IssueCategory.ANALOG,
                            "${pin.name} is not an analog input",
                            "${sensor.reference} analog output is connected to ${board.reference} ${pin.name}.",
                            "Use a board pin with ANALOG_INPUT capability to read this modeled analog sensor output.",
                            setOf(board.id,sensor.id),setOf(facts.pin(board,index),facts.pin(sensor,1)),
                            evidence=listOf(DiagnosticEvidence("Sensor signal","VOUT"),
                                DiagnosticEvidence("Pin capabilities",pin.capabilities.joinToString())))
                    val grounded=definition.pins.indices.any { bi ->
                        PinCapability.GROUND in definition.pins[bi].capabilities &&
                            facts.same(facts.pin(board,bi),facts.pin(sensor,2)) }
                    if(!grounded)
                        issues+=IntelligenceIssue("MISSING_COMMON_GROUND:${board.id}:${sensor.id}",
                            "MISSING_COMMON_GROUND",IssueSeverity.WARNING,IssueCategory.GROUNDING,
                            "${board.reference} and ${sensor.reference} need a common reference",
                            "The signal is connected, but sensor GND is not on a board GND net.",
                            "Connect sensor GND to a board GND pin so the signal voltage has a common reference.",
                            setOf(board.id,sensor.id),setOf(facts.pin(sensor,2)),
                            evidence=listOf(DiagnosticEvidence("Signal path","${sensor.reference} VOUT ↔ ${board.reference} ${pin.name}"),
                                DiagnosticEvidence("Ground nets","separate")))
                }
            }
        }
        for(i in boards.indices) for(j in i+1 until boards.size) {
            val a=boards[i];val b=boards[j]
            val aa=BoardRegistry.boards.getValue(a.kind);val bb=BoardRegistry.boards.getValue(b.kind)
            val aTx=aa.pins.indices.filter { aa.pins[it].name in setOf("TX","D1","D18","D16","D14") &&
                PinCapability.UART in aa.pins[it].capabilities }
            val bTx=bb.pins.indices.filter { bb.pins[it].name in setOf("TX","D1","D18","D16","D14") &&
                PinCapability.UART in bb.pins[it].capabilities }
            for(ai in aTx) for(bi in bTx) if(facts.same(facts.pin(a,ai),facts.pin(b,bi)))
                issues+=IntelligenceIssue("UART_TX_TX:${a.id}:$ai:${b.id}:$bi","UART_TX_TX",
                    IssueSeverity.WARNING,IssueCategory.DIGITAL,"UART TX is wired to TX",
                    "${a.reference} ${aa.pins[ai].name} and ${b.reference} ${bb.pins[bi].name} share the same net.",
                    "UART transmitters normally connect to receivers (TX → RX), with a shared ground and compatible logic voltage. Event-based UART still requires a valid TX→RX pair.",
                    setOf(a.id,b.id),setOf(facts.pin(a,ai),facts.pin(b,bi)),
                    evidence=listOf(DiagnosticEvidence("Wired pins","TX ↔ TX")))
        }
        return issues
    }
}

internal object MeasurementRule:DiagnosticRule {
    override val id="MEASUREMENT"
    override val priority=25
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val issues=mutableListOf<IntelligenceIssue>()
        val sources=input.circuit.components.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE) }
        input.circuit.components.filter { it.kind==Kind.AMMETER }.forEach { meter ->
            sources.forEach { source -> if(facts.same(source,0,meter,0) && facts.same(source,1,meter,1) ||
                facts.same(source,0,meter,1) && facts.same(source,1,meter,0))
                issues+=IntelligenceIssue("AMMETER_ACROSS_SOURCE:${meter.id}:${source.id}",
                    "AMMETER_ACROSS_SOURCE",IssueSeverity.CRITICAL,IssueCategory.MEASUREMENT,
                    "Ammeter is across ${source.reference}",
                    "${meter.reference} connects directly between the source terminals.",
                    "An ammeter has very low modeled resistance and belongs in series with a branch. Remove this connection before measuring current.",
                    setOf(meter.id,source.id),setOf(facts.pin(meter,0),facts.pin(meter,1)),
                    evidence=listOf(DiagnosticEvidence("Meter arrangement","parallel across source")))
            }
        }
        if(input.meterMode==MeterMode.RESISTANCE && input.running &&
            input.meterPlus!=null && input.meterCommon!=null && sources.isNotEmpty())
            issues+=IntelligenceIssue("OHMMETER_POWERED:circuit","OHMMETER_POWERED",
                IssueSeverity.WARNING,IssueCategory.MEASUREMENT,"Resistance mode on an energized circuit",
                "The virtual ohmmeter probes are attached while simulation is running.",
                "Pause power before interpreting resistance. The virtual resistance calculation suppresses sources and does not represent a powered ohmmeter reading.",
                pins=setOf(input.meterPlus,input.meterCommon),
                evidence=listOf(DiagnosticEvidence("Meter mode","Resistance"),DiagnosticEvidence("Simulation","running")))
        input.circuit.components.filter { it.kind==Kind.VOLTMETER }.forEach { meter ->
            val source=sources.singleOrNull() ?: return@forEach
            val load=input.circuit.components.singleOrNull { it.kind==Kind.RESISTOR || it.kind==Kind.LAMP }
                ?: return@forEach
            if(facts.same(source,0,meter,0) && facts.same(meter,1,load,0) &&
                facts.same(load,1,source,1) && !facts.same(source,0,load,0))
                issues+=IntelligenceIssue("VOLTMETER_IN_SERIES:${meter.id}","VOLTMETER_IN_SERIES",
                    IssueSeverity.WARNING,IssueCategory.MEASUREMENT,"Voltmeter appears in series",
                    "${meter.reference} is between source + and ${load.reference}; it is not across a part.",
                    "A voltmeter has high modeled resistance. Connect its probes across the part whose voltage you want to measure.",
                    setOf(meter.id,source.id,load.id),setOf(facts.pin(meter,0),facts.pin(meter,1)),
                    evidence=listOf(DiagnosticEvidence("Observed path","${source.reference} → ${meter.reference} → ${load.reference}")))
        }
        return issues
    }
}

internal object LogicRule:DiagnosticRule {
    override val id="LOGIC"
    override val priority=22
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val issues=mutableListOf<IntelligenceIssue>()
        input.circuit.components.filter { it.kind in IcParts.pinNames }.forEach { part ->
            val names=IcParts.pinNames.getValue(part.kind)
            val power=names.indices.filter { names[it].replace('−','-') in setOf("VCC","VDD","VS","V+") }
            val grounds=names.indices.filter { names[it].replace('−','-') in setOf("GND","VSS","V-") }
            if(power.isNotEmpty() && grounds.isNotEmpty() &&
                (power.none { facts.pin(part,it) in facts.connectedPins } ||
                    grounds.none { facts.pin(part,it) in facts.connectedPins }))
                issues+=IntelligenceIssue("UNPOWERED_IC:${part.id}","UNPOWERED_IC",
                    IssueSeverity.WARNING,IssueCategory.LOGIC,"${part.reference} supply pins are incomplete",
                    "At least one required supply or ground pin has no wire.",
                    "Connect the modeled power and ground pins before interpreting logic outputs.",
                    setOf(part.id),power.plus(grounds).map { facts.pin(part,it) }.toSet(),
                    evidence=listOf(DiagnosticEvidence("Power pins",power.joinToString { names[it] }),
                        DiagnosticEvidence("Ground pins",grounds.joinToString { names[it] })))
        }
        val boards=input.circuit.components.filter { it.kind.isBoard }
        val driven=boards.flatMap { board -> BoardRegistry.boards.getValue(board.kind).pins.mapIndexedNotNull { index,pin ->
            if(PinCapability.DIGITAL_OUTPUT in pin.capabilities && board.value("gpio_${pin.name}").toInt() in 1..2 &&
                facts.pin(board,index) in facts.connectedPins)
                Triple(board,index,board.value("gpio_${pin.name}").toInt()) else null
        } }
        for(i in driven.indices) for(j in i+1 until driven.size) {
            val a=driven[i];val b=driven[j]
            if(a.third!=b.third && facts.same(facts.pin(a.first,a.second),facts.pin(b.first,b.second)))
                issues+=IntelligenceIssue("OUTPUT_CONTENTION:${a.first.id}:${a.second}:${b.first.id}:${b.second}",
                    "OUTPUT_CONTENTION",IssueSeverity.ERROR,IssueCategory.LOGIC,
                    "Two outputs drive opposite levels on one net",
                    "${a.first.reference} and ${b.first.reference} are both configured as outputs on the same wire node.",
                    "Separate the outputs or change their modes. The board model may report a solver conflict; physical hardware could be damaged.",
                    setOf(a.first.id,b.first.id),setOf(facts.pin(a.first,a.second),facts.pin(b.first,b.second)),
                    evidence=listOf(DiagnosticEvidence("Output modes","${a.third} vs ${b.third}")))
        }
        val solved=input.result?.takeIf { it.error==null }
        if(solved!=null) boards.forEach { board ->
            val def=BoardRegistry.boards.getValue(board.kind)
            def.pins.forEachIndexed { index,pin ->
                val ref=facts.pin(board,index)
                if(ref in facts.connectedPins && board.value("gpio_${pin.name}").toInt()==0 &&
                    solved.digitalStates[ref]==LogicState.UNKNOWN &&
                    PinCapability.DIGITAL_INPUT in pin.capabilities)
                    issues+=IntelligenceIssue("FLOATING_GPIO:${board.id}:$index","FLOATING_GPIO",
                        IssueSeverity.SUGGESTION,IssueCategory.GPIO,"${pin.name} has undefined logic state",
                        "The connected input is neither driven HIGH nor LOW in the current simulation.",
                        "Drive the input, or add a pull-up/pull-down path when appropriate. An externally driven signal outside the model is not visible to this check.",
                        setOf(board.id),setOf(ref),
                        evidence=listOf(DiagnosticEvidence("Simulated logic state","UNKNOWN")),confidence=.8f)
            }
        }
        return issues
    }
}
