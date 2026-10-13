package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.*
import java.util.Locale

/** Topology checks run even before simulation; voltage checks require a successful solve. */
internal object CanvasIntelligence : DiagnosticRule {
    override val id = "CANVAS"
    override val priority = 25
    val features = listOf("MISSING_ENDPOINT", "INVALID_TERMINAL", "DUPLICATE_WIRE", "REDUNDANT_WIRE",
        "BYPASSED_PASSIVE", "DANGLING_JUNCTION", "DUPLICATE_REFERENCE", "INVALID_PROPERTY",
        "ISOLATED_COMPONENT", "FLOATING_GATE", "FLOATING_BASE", "MISSING_SUPPLY_PIN",
        "MISSING_RETURN_PIN", "UART_RX_RX", "IOT_REVERSE_SUPPLY", "IOT_SUPPLY_RANGE",
        "IOT_NON_ADC", "ANALOG_OUTPUT_COLLISION", "METER_SAME_NET", "SCOPE_SAME_NET")

    override fun inspect(input: IntelligenceInput, facts: CircuitFacts): List<IntelligenceIssue> {
        val circuit = input.circuit
        val issues = mutableListOf<IntelligenceIssue>()
        fun emit(rule: String, key: String, title: String, detail: String,
                 parts: Set<String> = emptySet(), pins: Set<TerminalRef> = emptySet(),
                 wires: Set<String> = emptySet(), severity: IssueSeverity = IssueSeverity.WARNING,
                 category: IssueCategory = IssueCategory.CONNECTIVITY, fixes: List<SmartFix> = emptyList(),
                 evidence: List<DiagnosticEvidence> = emptyList()) {
            issues += IntelligenceIssue("$rule:$key", rule, severity, category, title, detail, detail,
                parts, pins, wires, evidence, fixes)
        }
        fun external(ref: TerminalRef) = facts.onNet(ref).any { it.componentId != ref.componentId }
        fun wired(part: PlacedComponent) = (0 until part.terminalCount).any { external(facts.pin(part,it)) }
        fun removal(wire: Wire) = listOf(SmartFix.RemoveWire(wire.id,"Remove this wire",
            "Remove the highlighted wire. Review the preview; Undo restores it."))

        val seen = mutableSetOf<Set<TerminalRef>>()
        circuit.wires.forEach { wire ->
            val ends = setOf(wire.start,wire.end)
            val missing = ends.filter { it.componentId !in facts.parts }
            val invalid = ends.filter { ref -> facts.parts[ref.componentId]?.let {
                !ComponentPins.canConnect(it,ref.index) } == true }
            when {
                missing.isNotEmpty() -> emit("MISSING_ENDPOINT",wire.id,"Wire has a missing component",
                    "An endpoint refers to a deleted or missing component. Remove this stale wire.",
                    wires=setOf(wire.id),severity=IssueSeverity.ERROR,fixes=removal(wire))
                invalid.isNotEmpty() -> emit("INVALID_TERMINAL",wire.id,"Wire uses an unavailable pin",
                    "A pin index is invalid, reserved, NC or internal-only. Reconnect to a supported header pin.",
                    parts=ends.map { it.componentId }.toSet(),wires=setOf(wire.id),
                    severity=IssueSeverity.ERROR,fixes=removal(wire))
                !seen.add(ends) -> emit("DUPLICATE_WIRE",wire.id,"Duplicate wire",
                    "These exact terminals already have another wire. Keep one connection.",
                    parts=ends.map { it.componentId }.toSet(),pins=ends,wires=setOf(wire.id),
                    severity=IssueSeverity.SUGGESTION,fixes=if(wire.label.isNullOrBlank()) removal(wire) else emptyList())
                wire.label.isNullOrBlank() && wire.end in ComponentPins.net(
                    circuit.copy(wires=circuit.wires.filter { it.id!=wire.id }),wire.start) ->
                    emit("REDUNDANT_WIRE",wire.id,"Wire forms a redundant path",
                        "These terminals remain electrically joined through other wires or internal shared pads. Removing this wire preserves the net.",
                        parts=ends.map { it.componentId }.toSet(),pins=ends,wires=setOf(wire.id),
                        severity=IssueSeverity.SUGGESTION,fixes=removal(wire))
            }
        }
        circuit.components.groupBy { it.reference.trim().uppercase(Locale.ROOT) }.values
            .filter { it.size>1 }.forEach { group -> emit("DUPLICATE_REFERENCE",group.first().id,
                "Repeated component reference", "${group.first().reference} identifies ${group.size} parts. Give each part a unique reference to distinguish readings and wiring.",
                parts=group.map { it.id }.toSet(),severity=IssueSeverity.SUGGESTION,category=IssueCategory.CONFIGURATION) }

        circuit.components.forEach { part ->
            val pins = (0 until part.terminalCount).map { facts.pin(part,it) }
            val labels = pins.associateWith { ComponentPins.name(part,it.index) }
            if(part.kind in setOf(Kind.RESISTOR,Kind.CAPACITOR,Kind.ELECTROLYTIC,Kind.INDUCTOR,
                    Kind.VARIABLE_CAPACITOR,Kind.LDR,Kind.THERMISTOR,Kind.PTC_THERMISTOR) &&
                facts.same(pins[0],pins[1])) emit("BYPASSED_PASSIVE",part.id,"${part.reference} is bypassed",
                "Both terminals are on the same net, so this part cannot have a voltage across it. Inspect the highlighted connections.",setOf(part.id),pins.toSet())
            if(part.kind==Kind.JUNCTION && circuit.wires.count { it.start==pins[0] || it.end==pins[0] }==1)
                emit("DANGLING_JUNCTION",part.id,"${part.reference} is a wire stub",
                    "This junction has only one wire. Continue wiring from it or remove the unused stub.",setOf(part.id),pins.toSet(),severity=IssueSeverity.SUGGESTION)
            ComponentRegistry.definitions.getValue(part.kind).parameters.forEach { parameter ->
                ComponentProperties.validate(part,parameter,part.value(parameter.key)).error?.let { error ->
                    emit("INVALID_PROPERTY","${part.id}:${parameter.key}","${part.reference}: invalid ${parameter.label}",
                        "$error Open Properties to correct the saved value.",setOf(part.id),severity=IssueSeverity.ERROR,
                        category=IssueCategory.CONFIGURATION,evidence=listOf(DiagnosticEvidence(parameter.key,part.value(parameter.key).toString())))
                }
            }
            if(!wired(part) && part.kind !in setOf(Kind.GROUND,Kind.JUNCTION) && !part.kind.isBoard)
                emit("ISOLATED_COMPONENT",part.id,"${part.reference} is not connected",
                    "No terminal shares a net with another component. Open Pins and choose an exact destination to connect it.",
                    setOf(part.id),severity=IssueSeverity.SUGGESTION)
            if(wired(part)) {
                if(part.kind in setOf(Kind.NMOS,Kind.PMOS) && !external(pins[1]))
                    emit("FLOATING_GATE",part.id,"${part.reference} gate is floating",
                        "The drain or source is wired, but the gate has no external connection. Connect a control signal and a defined bias path; physical gate charge is not predicted here.",
                        setOf(part.id),setOf(pins[1]),category=IssueCategory.ANALOG)
                if(part.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON) && !external(pins[1]))
                    emit("FLOATING_BASE",part.id,"${part.reference} base is open",
                        "The collector or emitter is wired, but the base has no external connection. Add an appropriate base drive and bias network.",
                        setOf(part.id),setOf(pins[1]),category=IssueCategory.ANALOG)
                if(!part.kind.isBoard) {
                    labels.filterValues { it in setOf("V+","VCC","VDD","VLOGIC","VM") }.keys.filterNot(::external).forEach { ref ->
                        emit("MISSING_SUPPLY_PIN","${part.id}:${ref.index}","${part.reference} ${labels[ref]} needs power",
                            "Other pins are wired, but this supply input has no external connection. Use the component's documented supply rail.",setOf(part.id),setOf(ref),category=IssueCategory.POWER)
                    }
                    labels.filterValues { it in setOf("GND","AGND") }.keys.filterNot(::external).forEach { ref ->
                        emit("MISSING_RETURN_PIN","${part.id}:${ref.index}","${part.reference} ${labels[ref]} needs a return",
                            "Other pins are wired, but this ground pin has no external connection. Connect the appropriate supply and signal return.",setOf(part.id),setOf(ref),category=IssueCategory.GROUNDING)
                    }
                }
            }
            IotSensorParts.profiles[part.kind]?.let { profile ->
                val supply = pins[profile.supplyPin]; val ground = pins[profile.groundPin]
                val voltage = facts.voltage(supply)?.let { a -> facts.voltage(ground)?.let { a-it } }
                if(voltage!=null && external(supply) && external(ground)) {
                    if(voltage<-.05) emit("IOT_REVERSE_SUPPLY",part.id,"${part.reference} supply is reversed",
                        "V+ minus GND is %.3g V. Reconnect the supply using the physical pin names.".format(Locale.US,voltage),
                        setOf(part.id),setOf(supply,ground),severity=IssueSeverity.ERROR,category=IssueCategory.POLARITY)
                    else if(voltage<profile.supplyMin-.001 || voltage>profile.supplyMax+.001)
                        emit("IOT_SUPPLY_RANGE",part.id,"${part.reference} supply is outside its operating range",
                            "V+ minus GND is %.3g V; this ${profile.part} model requires ${profile.supplyMin}–${profile.supplyMax} V. Its output is released outside that range.".format(Locale.US,voltage),
                            setOf(part.id),setOf(supply,ground),category=IssueCategory.POWER,
                            evidence=listOf(DiagnosticEvidence("Operating range","${profile.supplyMin}–${profile.supplyMax} V",RatingSource.MANUFACTURER)))
                }
                val output = pins[profile.outputPin]
                facts.onNet(output).filter { it.componentId!=part.id }.forEach { ref ->
                    val board = facts.parts[ref.componentId]?.takeIf { it.kind.isBoard } ?: return@forEach
                    val pin = BoardRegistry.boards.getValue(board.kind).pins.getOrNull(ref.index) ?: return@forEach
                    if(pin.type==PinType.GPIO && PinCapability.ANALOG_INPUT !in pin.capabilities)
                        emit("IOT_NON_ADC","${part.id}:${board.id}:${ref.index}","${part.reference} needs an ADC pin",
                            "VOUT is connected to ${board.reference} ${pin.name}, which has no analog input capability. Choose a documented ADC header pin.",setOf(part.id,board.id),setOf(output,ref),category=IssueCategory.ANALOG)
                }
            }
        }
        val outputs = circuit.components.flatMap { part ->
            (0 until part.terminalCount).filter { ComponentPins.name(part,it)=="VOUT" }
                .map { facts.pin(part,it) } }
        outputs.groupBy { facts.net(it) }.values.filter { group -> group.map { it.componentId }.distinct().size>1 }
            .forEach { group -> emit("ANALOG_OUTPUT_COLLISION",group.sortedBy { it.componentId }.first().componentId,
                "Analog sensor outputs share a net", "Multiple VOUT signals are joined. Use separate ADC inputs or a suitable multiplexer; this model does not predict output-stage damage.",
                group.map { it.componentId }.toSet(),group.toSet(),category=IssueCategory.ANALOG) }
        val receivers = circuit.components.flatMap { part -> (0 until part.terminalCount)
            .filter { ComponentPins.name(part,it)=="RX" }.map { facts.pin(part,it) } }
        receivers.groupBy { facts.net(it) }.values.filter { it.size>1 }.forEach { group ->
            val net = facts.onNet(group.first())
            if(net.none { ref -> facts.parts[ref.componentId]?.let { ref.index in 0 until it.terminalCount && ComponentPins.name(it,ref.index)=="TX" }==true })
                emit("UART_RX_RX",group.first().componentId,"UART receivers have no transmitter",
                    "RX pins share a net without a TX pin. For UART communication, connect each receiver to the intended transmitter and share the signal ground.",
                    group.map { it.componentId }.toSet(),group.toSet(),category=IssueCategory.DIGITAL)
        }
        if(input.meterPlus!=null && input.meterCommon!=null && facts.same(input.meterPlus,input.meterCommon))
            emit("METER_SAME_NET","meter","Meter probes are on the same net",
                "Both probes share an electrical node. A differential voltage measurement here is zero; choose two distinct nets to measure a voltage drop.",
                pins=setOf(input.meterPlus,input.meterCommon),severity=IssueSeverity.SUGGESTION,category=IssueCategory.MEASUREMENT)
        input.scopeProbes.groupBy { facts.net(it) }.values.filter { it.size>1 }.forEachIndexed { index, group ->
            emit("SCOPE_SAME_NET",index.toString(),"Scope channels observe the same net",
                "These probes share an electrical node, so their voltage traces should coincide. Move a channel to another net when comparing signals.",
                pins=group.toSet(),severity=IssueSeverity.SUGGESTION,category=IssueCategory.MEASUREMENT)
        }
        return issues
    }
}
