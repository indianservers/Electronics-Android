package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.ln

interface DiagnosticRule {
    val id:String
    val priority:Int
    fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue>
}

private fun number(value:Double,unit:String="")="%.3g".format(Locale.US,value)+if(unit.isEmpty()) "" else " $unit"
private fun issue(rule:String,key:String,severity:IssueSeverity,category:IssueCategory,title:String,
                  summary:String,explanation:String,parts:Set<String> = emptySet(),
                  pins:Set<TerminalRef> = emptySet(),wires:Set<String> = emptySet(),
                  evidence:List<DiagnosticEvidence> = emptyList(),fixes:List<SmartFix> = emptyList(),
                  confidence:Float=1f,note:String?=null)=IntelligenceIssue("$rule:$key",rule,severity,category,
    title,summary,explanation,parts,pins,wires,evidence,fixes,confidence,note)

private object FoundationalRule:DiagnosticRule {
    override val id="FOUNDATION"
    override val priority=0
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val c=input.circuit;val issues=mutableListOf<IntelligenceIssue>()
        if(c.components.isEmpty()) return emptyList()
        if(c.components.none { it.kind==Kind.GROUND }) {
            val source=c.components.singleOrNull { it.kind in setOf(Kind.BATTERY,Kind.SOURCE) }
            issues+=issue("NO_GROUND","circuit",IssueSeverity.ERROR,IssueCategory.GROUNDING,
                "No ground reference","Node voltages have no defined zero.",
                "A ground reference is required by this solver. Connect a Ground symbol to a reference node; the negative terminal of a single DC source is a common choice.",
                source?.let { setOf(it.id) } ?: emptySet(),
                evidence=listOf(DiagnosticEvidence("Ground symbols","0")),
                fixes=source?.let { listOf(SmartFix.AddGround(it.id,"Ground ${it.reference} negative",
                    "Add a Ground symbol and wire it to ${it.reference} −. This defines 0 V and can be undone.")) } ?: emptyList())
        }
        c.components.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE,Kind.SINGLE_CELL,
            Kind.BATTERY_PACK,Kind.VARIABLE_DC_SUPPLY,Kind.FUNCTION_GENERATOR) && it.terminalCount>=2 }
            .forEach { source ->
                val path=facts.lowResistancePath(facts.pin(source,0),facts.pin(source,1))
                if(path!=null) {
                    val direct=listOfNotNull(facts.directWire(facts.pin(source,0),facts.pin(source,1)))
                    val removable=direct.singleOrNull()
                    issues+=issue("SOURCE_SHORT",source.id,IssueSeverity.CRITICAL,IssueCategory.POWER,
                        "Low-resistance path across ${source.reference}",
                        "The source terminals are connected through ${if(path.isEmpty()) "wires" else path.joinToString { it.reference }}.",
                        "This path bypasses the intended load. The educational solver may reject an ideal short; current is not estimated unless source resistance is modeled.",
                        setOf(source.id)+path.map { it.id },setOf(facts.pin(source,0),facts.pin(source,1)),
                        direct.map { it.id }.toSet(),listOf(
                            DiagnosticEvidence("Source voltage",number(source.value("voltage"),"V"),RatingSource.CIRCUIT_SETTING),
                            DiagnosticEvidence("Low-resistance path",if(path.isEmpty()) "wire-only" else path.joinToString { it.reference })),
                        removable?.let { listOf(SmartFix.RemoveWire(it.id,"Remove bypass wire",
                            "Remove only the direct source bypass wire ${it.id.take(8)}. The action is reversible with Undo.")) } ?: emptyList())
                }
            }
        val legacy=CircuitDiagnostics.inspect(c,input.result)
        legacy.filter { it.code in setOf("SOURCE_CONFLICT","FLOATING_ISLAND") }.forEach { old ->
            issues+=issue(old.code,old.componentId ?: "circuit",IssueSeverity.ERROR,
                if(old.code=="SOURCE_CONFLICT") IssueCategory.POWER else IssueCategory.CONNECTIVITY,
                if(old.code=="SOURCE_CONFLICT") "Conflicting voltage sources" else "Floating circuit island",
                old.message,old.message,old.componentId?.let(::setOf) ?: emptySet(),
                evidence=listOf(DiagnosticEvidence("Analysis","wire graph and source constraints")))
        }
        if(input.result?.error!=null && issues.none { it.ruleId in setOf("NO_GROUND","SOURCE_SHORT","SOURCE_CONFLICT","FLOATING_ISLAND") })
            issues+=issue("SOLVER_FAILURE","circuit",IssueSeverity.ERROR,IssueCategory.SIMULATION,
                "Simulation could not solve this circuit","The current operating point is unavailable.",
                "The solver could not determine a consistent operating point. Check floating nodes, source connections, and recently changed components.",
                input.result.progress?.likelyComponentId?.let(::setOf) ?: emptySet(),
                evidence=listOf(DiagnosticEvidence("Solver detail",input.result.error),
                    DiagnosticEvidence("Iterations",input.result.progress?.iterations?.toString() ?: "not reported")))
        return issues
    }
}

private object ConnectivityRule:DiagnosticRule {
    override val id="CONNECTIVITY"
    override val priority=30
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val c=input.circuit;val issues=mutableListOf<IntelligenceIssue>()
        c.components.filter { it.kind in setOf(Kind.LED,Kind.RESISTOR,Kind.LAMP,Kind.DC_MOTOR,
            Kind.RELAY,Kind.SERVO_MOTOR,Kind.LM35_SENSOR,Kind.HALL_SENSOR) }.forEach { part ->
            val needed=when(part.kind) {
                Kind.RELAY,Kind.DC_MOTOR,Kind.LED,Kind.RESISTOR,Kind.LAMP -> listOf(0,1)
                Kind.SERVO_MOTOR,Kind.LM35_SENSOR,Kind.HALL_SENSOR -> listOf(0,2)
                else -> emptyList()
            }
            val absent=needed.filter { facts.pin(part,it) !in facts.connectedPins }
            if(absent.isNotEmpty() && c.wires.any { it.start.componentId==part.id || it.end.componentId==part.id })
                issues+=issue("DISCONNECTED_LOAD",part.id,IssueSeverity.WARNING,IssueCategory.CONNECTIVITY,
                    "${part.reference} has an open connection",
                    "Required pin${if(absent.size>1) "s" else ""} ${absent.joinToString()} ${if(absent.size>1) "are" else "is"} unwired.",
                    "The component cannot have the expected complete current or power path until the missing connection is made. An intentionally open switch is not treated as a broken wire.",
                    setOf(part.id),absent.map { facts.pin(part,it) }.toSet(),
                    evidence=listOf(DiagnosticEvidence("Unwired pins",absent.joinToString())))
        }
        c.components.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE) }.forEach { source ->
            val absent=(0..1).filter { facts.pin(source,it) !in facts.connectedPins }
            if(absent.isNotEmpty()) issues+=issue("SOURCE_UNCONNECTED",source.id,
                IssueSeverity.SUGGESTION,IssueCategory.CONNECTIVITY,"${source.reference} is not fully connected",
                "Source pin ${absent.joinToString()} has no wire.",
                "Connect both source terminals to a closed load path when you want the circuit to operate.",
                setOf(source.id),absent.map { facts.pin(source,it) }.toSet())
        }
        return issues
    }
}

private object LedRule:DiagnosticRule {
    override val id="LED"
    override val priority=20
    private val standard=listOf(100.0,120.0,150.0,180.0,220.0,270.0,330.0,390.0,470.0,
        560.0,680.0,820.0,1000.0,1200.0,1500.0,1800.0,2200.0,2700.0,3300.0,4700.0)
    private fun dropAt(part:PlacedComponent,current:Double):Double {
        val thermal=part.value("ideality")*8.617333262145e-5*(part.value("temperatureC")+273.15)
        return (thermal*ln(1.0+current/part.value("isat").coerceAtLeast(1e-22))).coerceIn(.5,2.5)
    }
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val issues=mutableListOf<IntelligenceIssue>()
        input.circuit.components.filter { it.kind==Kind.LED }.forEach { led ->
            val reading=input.result?.takeIf { it.error==null }?.readings?.get(led.id)
            val source=facts.sourceForLed(led)
            if(reading!=null && reading.voltage<-.1) {
                val links=input.circuit.wires.filter { it.start.componentId==led.id || it.end.componentId==led.id }
                val eachPin=links.groupBy { if(it.start.componentId==led.id) it.start.index else it.end.index }
                val fix=if(eachPin[0]?.size==1 && eachPin[1]?.size==1) listOf(SmartFix.ReverseLed(led.id,
                    "Swap LED connections","Exchange the wires on ${led.reference} anode and cathode. Undo restores the original wiring.")) else emptyList()
                issues+=issue("LED_REVERSED",led.id,IssueSeverity.WARNING,IssueCategory.POLARITY,
                    "${led.reference} is reverse biased","Its anode is below its cathode in the solved circuit.",
                    "LED current is blocked in this direction in the simplified diode model. Check the anode (+) and cathode (−) wiring.",
                    setOf(led.id),setOf(facts.pin(led,0),facts.pin(led,1)),
                    evidence=listOf(DiagnosticEvidence("Anode minus cathode",number(reading.voltage,"V")),
                        DiagnosticEvidence("LED current",number(reading.current,"A"))),fixes=fix,
                    note="Simplified educational diode model")
            }
            if(source!=null && facts.bypassesResistor(facts.pin(source,0),facts.pin(led,0))) {
                val direct=input.circuit.wires.singleOrNull { wire ->
                    (wire.start==facts.pin(source,0) && wire.end==facts.pin(led,0)) ||
                        (wire.end==facts.pin(source,0) && wire.start==facts.pin(led,0)) }
                val isolatedDirect=direct?.let { wire ->
                    val without=input.circuit.copy(wires=input.circuit.wires.filterNot { it.id==wire.id })
                    val after=CircuitFacts(without,null)
                    !after.bypassesResistor(after.pin(source,0),after.pin(led,0))
                } ?: false
                val supply=source.value("voltage")
                val target=(led.value("maxCurrent")*.75).coerceAtLeast(.001)
                val assumedDrop=dropAt(led,target)
                val minimum=(supply-assumedDrop)/target
                val proposed=standard.firstOrNull { it>=minimum } ?: standard.last()
                val fixes=if(direct!=null && isolatedDirect && direct.label==null &&
                    supply>assumedDrop && minimum<=standard.last())
                    listOf(SmartFix.InsertLedResistor(direct.id,proposed,
                        "Insert ${number(proposed,"Ω")} series resistor",
                        "Replace the direct ${source.reference} + to ${led.reference} anode wire with a ${number(proposed,"Ω")} resistor and two wires. Assumes ${number(supply,"V")} supply, ${number(assumedDrop,"V")} LED drop, and ${number(target,"A")} target current. Undo restores the wire."))
                    else emptyList()
                issues+=issue("LED_NO_RESISTOR",led.id,IssueSeverity.WARNING,IssueCategory.COMPONENT_STRESS,
                    "No resistor in ${led.reference} supply path","The source can reach the LED anode without a current-limiting resistor.",
                    "Add a resistor in series with the LED. A bypass wire around an existing resistor has the same effect.",
                    setOf(source.id,led.id),setOf(facts.pin(led,0)),
                    evidence=listOf(DiagnosticEvidence("Supply setting",number(supply,"V"),RatingSource.CIRCUIT_SETTING),
                        DiagnosticEvidence("LED forward drop used for estimate",number(assumedDrop,"V"),
                            RatingSource.GENERIC_MODEL),
                        DiagnosticEvidence("Target current",number(target,"A"),RatingSource.CIRCUIT_SETTING)),
                    fixes=fixes,confidence=.95f,note="Resistor estimate assumes a single LED branch and no other load.")
            }
            if(reading!=null && abs(reading.current)>led.value("maxCurrent") &&
                source!=null && !facts.bypassesResistor(facts.pin(source,0),facts.pin(led,0))) {
                val resistor=facts.seriesResistor(source,led)
                val target=(led.value("maxCurrent")*.75).coerceAtLeast(.001)
                val minimum=((source.value("voltage")-dropAt(led,target))/target).coerceAtLeast(1.0)
                val proposed=standard.firstOrNull { it>=minimum } ?: standard.last()
                issues+=issue("LED_OVERCURRENT",led.id,IssueSeverity.WARNING,IssueCategory.COMPONENT_STRESS,
                    "${led.reference} current exceeds its setting",
                    "Calculated current ${number(abs(reading.current),"A")} exceeds ${number(led.value("maxCurrent"),"A")}.",
                    "Increase the series resistance or lower the supply. The limit comes from this educational LED model, not a particular manufacturer's datasheet.",
                    setOfNotNull(led.id,source.id,resistor?.id),
                    evidence=listOf(DiagnosticEvidence("LED current",number(abs(reading.current),"A")),
                        DiagnosticEvidence("Configured maximum",number(led.value("maxCurrent"),"A"),RatingSource.CIRCUIT_SETTING),
                        DiagnosticEvidence("Resistor estimate",number(proposed,"Ω"),RatingSource.GENERIC_MODEL)),
                    fixes=if(resistor!=null && proposed>resistor.value("resistance")) listOf(
                        SmartFix.SetValue(resistor.id,"resistance",proposed,"Set ${resistor.reference} to ${number(proposed,"Ω")}",
                            "Increase ${resistor.reference} resistance to the nearest listed value above ${number(minimum,"Ω")}; target ≈${number(target,"A")}. Undo restores its value.")) else emptyList(),
                    note="Simplified LED model; target is 75% of its configured current limit.")
            }
        }
        return issues
    }
}

private object StressRule:DiagnosticRule {
    override val id="STRESS"
    override val priority=15
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val result=input.result?.takeIf { it.error==null } ?: return emptyList()
        return input.circuit.components.mapNotNull { part ->
            val reading=result.readings[part.id] ?: return@mapNotNull null
            when(part.kind) {
                Kind.RESISTOR,Kind.LAMP -> if(reading.power>part.value("rating") ||
                    reading.health==Health.FAILED_OPEN)
                    issue("POWER_OVERLOAD",part.id,IssueSeverity.WARNING,IssueCategory.COMPONENT_STRESS,
                        if(reading.health==Health.FAILED_OPEN) "${part.reference} failed open after thermal stress"
                        else "${part.reference} exceeds power rating",
                        if(reading.health==Health.FAILED_OPEN)
                            "The thermal model has opened this part after overload. Its present power is ${number(reading.power,"W")}."
                        else "Dissipation ${number(reading.power,"W")} exceeds ${number(part.value("rating"),"W")}.",
                        "Select a component with a suitable power rating or change the circuit so less power is dissipated.",
                        setOf(part.id),evidence=listOf(DiagnosticEvidence("Solved power",number(reading.power,"W")),
                            DiagnosticEvidence("Configured rating",number(part.value("rating"),"W"),RatingSource.CIRCUIT_SETTING),
                            DiagnosticEvidence("Modeled condition",reading.health.name)),
                        note="The rating is the component's configured value; thermal conditions are simplified.") else null
                Kind.CAPACITOR,Kind.ELECTROLYTIC -> when {
                    abs(reading.voltage)>part.value("maxVoltage") -> issue("CAP_OVER_VOLTAGE",part.id,
                        IssueSeverity.WARNING,IssueCategory.COMPONENT_STRESS,"${part.reference} exceeds voltage rating",
                        "Solved voltage ${number(abs(reading.voltage),"V")} exceeds ${number(part.value("maxVoltage"),"V")}.",
                        "Use a capacitor with a higher voltage rating or reduce the applied voltage.",setOf(part.id),
                        evidence=listOf(DiagnosticEvidence("Solved magnitude",number(abs(reading.voltage),"V")),
                            DiagnosticEvidence("Configured rating",number(part.value("maxVoltage"),"V"),RatingSource.CIRCUIT_SETTING)))
                    part.kind==Kind.ELECTROLYTIC && reading.voltage<-.1 -> issue("CAP_REVERSED",part.id,
                        IssueSeverity.WARNING,IssueCategory.POLARITY,"${part.reference} polarity is reversed",
                        "Its positive pin is ${number(reading.voltage,"V")} below the negative pin.",
                        "Reconnect the polarized capacitor with its positive terminal at the higher potential.",
                        setOf(part.id),setOf(facts.pin(part,0),facts.pin(part,1)),
                        evidence=listOf(DiagnosticEvidence("Pin 0 minus pin 1",number(reading.voltage,"V"))))
                    else -> null
                }
                else -> null
            }
        }
    }
}

/** Registry order is explicit; root causes are sorted and downstream solver noise is suppressed. */
object DiagnosticRuleRegistry {
    val rules:List<DiagnosticRule> = listOf(FoundationalRule,StressRule,LedRule,ConnectivityRule,
        BoardCompatibilityRule,MeasurementRule,LogicRule,FirmwareDiagnostics)
    val count get()=rules.size
}

object CircuitIntelligence {
    fun analyze(input:IntelligenceInput):IntelligenceReport {
        val started=System.nanoTime();val facts=CircuitFacts(input.circuit,input.result)
        val emitted=DiagnosticRuleRegistry.rules.sortedBy { it.priority }.flatMap { it.inspect(input,facts) }
        val issues=emitted.distinctBy { it.id }.filterNot { candidate ->
            candidate.ruleId=="DISCONNECTED_LOAD" && emitted.any { it.ruleId=="NO_GROUND" } &&
                input.result?.error!=null
        }.sortedWith(compareBy<IntelligenceIssue> { it.severity.ordinal }.thenBy { it.ruleId }.thenBy { it.id })
        val insights=PatternRecognizer.recognize(input,facts)
        val summary=ExplanationGenerator.summary(input,facts,insights)
        return IntelligenceReport(issues,insights,summary,(System.nanoTime()-started)/1_000_000)
    }
}
