package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.BoardRegistry
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.domain.electricalConnections
import com.indianservers.circuitssimulator.domain.isBoard
import kotlin.math.abs

enum class DiagnosticLevel { INFO, WARNING, ERROR }
data class CircuitDiagnostic(val code:String,val level:DiagnosticLevel,val message:String,
                             val componentId:String?=null)

/** Actionable topology and operating-point checks; this does not replace a solver run. */
object CircuitDiagnostics {
    fun inspect(circuit:Circuit,result:DcResult?=null):List<CircuitDiagnostic> {
        val issues=mutableListOf<CircuitDiagnostic>()
        val parts=circuit.components
        if(parts.isEmpty()) return listOf(CircuitDiagnostic("EMPTY",DiagnosticLevel.INFO,
            "Add a source, a load, and a ground to begin."))
        if(parts.none { it.kind==Kind.GROUND }) issues+=CircuitDiagnostic("NO_GROUND",DiagnosticLevel.ERROR,
            "Add a ground reference so voltages have a defined zero.")
        if(parts.none { it.kind in setOf(Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR,Kind.CLOCK,
                Kind.SINGLE_CELL,Kind.BATTERY_PACK,Kind.VARIABLE_DC_SUPPLY,Kind.DC_CURRENT_SOURCE,
                Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR,
                Kind.LOGIC_INPUT) })
            issues+=CircuitDiagnostic("NO_SOURCE",DiagnosticLevel.INFO,
                "There is no independent electrical source in this circuit.")

        val terminals=parts.flatMap { p -> (0 until p.terminalCount).map { TerminalRef(p.id,it) } }
        val parent=terminals.associateWith { it }.toMutableMap()
        fun root(t:TerminalRef):TerminalRef {
            val p=parent.getValue(t)
            if(p==t) return t
            val r=root(p);parent[t]=r;return r
        }
        circuit.electricalConnections().forEach { (a,b) -> if(a in parent && b in parent) parent[root(a)]=root(b) }
        if(parts.any { it.kind==Kind.GROUND }) {
            val islandParent=terminals.associateWith { it }.toMutableMap()
            fun islandRoot(t:TerminalRef):TerminalRef {
                val p=islandParent.getValue(t)
                if(p==t) return t
                val r=islandRoot(p);islandParent[t]=r;return r
            }
            fun join(a:TerminalRef,b:TerminalRef) { islandParent[islandRoot(a)]=islandRoot(b) }
            circuit.electricalConnections().forEach { (a,b) -> if(a in islandParent && b in islandParent) join(a,b) }
            parts.forEach { p -> (1 until p.terminalCount).forEach { join(TerminalRef(p.id,0),TerminalRef(p.id,it)) } }
            parts.groupBy { islandRoot(TerminalRef(it.id,0)) }.values
                .filter { group -> group.none { it.kind==Kind.GROUND } &&
                    group.any { p -> circuit.wires.any { it.start.componentId==p.id || it.end.componentId==p.id } } }
                .forEach { group -> issues+=CircuitDiagnostic("FLOATING_ISLAND",DiagnosticLevel.ERROR,
                    "${group.joinToString { it.reference }}: this connected group has no path to ground.",group.first().id) }
        }
        val idealSources=parts.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE) &&
            it.value("internalResistance")<=0.0 }
        val voltageEdges=mutableMapOf<TerminalRef,MutableList<Pair<TerminalRef,Double>>>()
        idealSources.forEach { p ->
            val plus=root(TerminalRef(p.id,0));val minus=root(TerminalRef(p.id,1))
            if(plus!=minus) {
                val voltage=p.value("voltage")
                voltageEdges.getOrPut(minus) { mutableListOf() }.add(plus to voltage)
                voltageEdges.getOrPut(plus) { mutableListOf() }.add(minus to -voltage)
            }
        }
        val potentials=mutableMapOf<TerminalRef,Double>()
        var contradictory=false
        voltageEdges.keys.forEach { start ->
            if(start !in potentials) {
                potentials[start]=0.0
                val queue=ArrayDeque<TerminalRef>();queue.addLast(start)
                while(queue.isNotEmpty()) {
                    val at=queue.removeFirst();val voltage=potentials.getValue(at)
                    voltageEdges[at].orEmpty().forEach { (next,change) ->
                        val expected=voltage+change
                        val previous=potentials[next]
                        if(previous==null) { potentials[next]=expected;queue.addLast(next) }
                        else if(abs(previous-expected)>1e-6) contradictory=true
                    }
                }
            }
        }
        if(contradictory) issues+=CircuitDiagnostic("SOURCE_CONFLICT",DiagnosticLevel.ERROR,
            "Ideal voltage sources impose conflicting voltages on the same wire network.")
        val connected=circuit.wires.flatMap { listOf(it.start,it.end) }.toSet()
        parts.filter { it.kind!=Kind.JUNCTION && it.kind!=Kind.GROUND }.forEach { p ->
            val relevant=if(p.kind.isBoard) {
                val board=BoardRegistry.boards.getValue(p.kind)
                listOfNotNull(
                    board.pins.indexOfFirst { it.name in board.supplyPins }.takeIf { first ->
                        first>=0 && board.pins.indices.none { i -> board.pins[i].name in board.supplyPins &&
                            TerminalRef(p.id,i) in connected } },
                    board.pins.indexOfFirst { it.name=="GND" }.takeIf { first ->
                        first>=0 && board.pins.indices.none { i -> board.pins[i].name.startsWith("GND") &&
                            TerminalRef(p.id,i) in connected } })
            } else 0 until p.terminalCount
            val missing=relevant.filter { TerminalRef(p.id,it) !in connected }
            if(missing.isNotEmpty()) issues+=CircuitDiagnostic("UNWIRED_PIN",DiagnosticLevel.INFO,
                "${p.reference}: pin ${missing.joinToString()} is not wired.",p.id)
        }
        parts.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR,
            Kind.SINGLE_CELL,Kind.BATTERY_PACK,Kind.VARIABLE_DC_SUPPLY,
            Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR) }.forEach { p ->
            if(root(TerminalRef(p.id,0))==root(TerminalRef(p.id,1)))
                issues+=CircuitDiagnostic("SOURCE_SHORT",DiagnosticLevel.ERROR,
                    "${p.reference}: its positive and negative terminals are directly connected.",p.id)
        }
        parts.filter { it.kind==Kind.SWITCH && !it.closed }.forEach { p ->
            issues+=CircuitDiagnostic("OPEN_SWITCH",DiagnosticLevel.INFO,
                "${p.reference}: the open contact interrupts this path.",p.id)
        }
        if(result?.error!=null) issues+=CircuitDiagnostic("SOLVER",DiagnosticLevel.ERROR,
            result.error+result.progress?.likelyComponentId?.let { id ->
                parts.firstOrNull { it.id==id }?.let { " Check ${it.reference} and its connections." }
            }.orEmpty(),result.progress?.likelyComponentId)
        if(result?.error==null) parts.filter { it.kind.isBoard }.forEach { boardPart ->
            val board=BoardRegistry.boards.getValue(boardPart.kind)
            board.pins.forEachIndexed { index,pin ->
                val voltage=result?.nodeVoltages?.get(TerminalRef(boardPart.id,index))
                if(voltage!=null && pin.maxVoltage!=null &&
                    TerminalRef(boardPart.id,index) in connected &&
                    (voltage>pin.maxVoltage+.2 || voltage<(pin.minVoltage ?: 0.0)-.2))
                    issues+=CircuitDiagnostic("BOARD_PIN_VOLTAGE_${index}",DiagnosticLevel.WARNING,
                        "${boardPart.reference} ${pin.name}: ${"%.2f".format(voltage)} V is outside the documented ${pin.minVoltage}–${pin.maxVoltage} V input range.",boardPart.id)
            }
        }
        if(result?.error==null) parts.forEach { p ->
            val reading=result?.readings?.get(p.id) ?: return@forEach
            when(p.kind) {
                Kind.LED -> {
                    if(reading.voltage< -0.1) issues+=CircuitDiagnostic("REVERSE_LED",DiagnosticLevel.WARNING,
                        "${p.reference}: LED is reverse biased; check its orientation.",p.id)
                    if(abs(reading.current)>p.value("maxCurrent")) issues+=CircuitDiagnostic("LED_CURRENT",DiagnosticLevel.WARNING,
                        "${p.reference}: current exceeds its configured limit.",p.id)
                }
                Kind.RESISTOR,Kind.LAMP -> if(reading.power>p.value("rating"))
                    issues+=CircuitDiagnostic("POWER_RATING",DiagnosticLevel.WARNING,
                        "${p.reference}: dissipation exceeds its configured rating.",p.id)
                Kind.CAPACITOR,Kind.ELECTROLYTIC -> {
                    if(abs(reading.voltage)>p.value("maxVoltage"))
                        issues+=CircuitDiagnostic("CAP_VOLTAGE",DiagnosticLevel.WARNING,
                            "${p.reference}: voltage exceeds its configured rating.",p.id)
                    if(p.kind==Kind.ELECTROLYTIC && reading.voltage< -0.1)
                        issues+=CircuitDiagnostic("REVERSE_ELECTROLYTIC",DiagnosticLevel.WARNING,
                            "${p.reference}: polarized capacitor is reverse biased.",p.id)
                }
                Kind.FUSE -> if(reading.health==Health.FAILED_OPEN)
                    issues+=CircuitDiagnostic("OPEN_FUSE",DiagnosticLevel.WARNING,
                        "${p.reference}: fuse has opened after overload.",p.id)
                    else if(abs(reading.current)>p.value("currentRating"))
                        issues+=CircuitDiagnostic("FUSE_CURRENT",DiagnosticLevel.WARNING,
                            "${p.reference}: current exceeds its configured rating.",p.id)
                else -> Unit
            }
        }
        return issues.distinctBy { it.code to it.componentId }
    }
}
