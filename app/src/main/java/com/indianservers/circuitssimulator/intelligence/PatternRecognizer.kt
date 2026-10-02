package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import java.util.Locale
import kotlin.math.abs

object PatternRecognizer {
    fun recognize(input:IntelligenceInput,facts:CircuitFacts):List<CircuitInsight> {
        val c=input.circuit;val insights=mutableListOf<CircuitInsight>()
        c.components.filter { it.kind==Kind.LED }.forEach { led ->
            val source=facts.sourceForLed(led) ?: return@forEach
            val resistor=facts.seriesResistor(source,led) ?: return@forEach
            if(!facts.bypassesResistor(facts.pin(source,0),facts.pin(led,0)))
                insights+=CircuitInsight("LED_SERIES:${led.id}","LED with series resistor",
                    "${source.reference} feeds ${led.reference} through ${resistor.reference}. The resistor limits forward current; the LED returns to the source negative terminal.",
                    setOf(source.id,resistor.id,led.id),listOf(
                        DiagnosticEvidence("Resistor",String.format(Locale.US,"%.3g Ω",resistor.value("resistance"))),
                        DiagnosticEvidence("Solved LED current",input.result?.readings?.get(led.id)?.current?.let {
                            String.format(Locale.US,"%.3g A",it) } ?: "unavailable")))
        }
        val sources=c.components.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE) }
        val resistors=c.components.filter { it.kind==Kind.RESISTOR }
        sources.forEach { source ->
            for(i in resistors.indices) for(j in resistors.indices) {
                if(i==j) continue
                val top=resistors[i];val bottom=resistors[j]
                for(a in 0..1) for(b in 0..1) {
                    if(facts.same(source,0,top,a) && facts.same(top,1-a,bottom,b) &&
                        facts.same(bottom,1-b,source,1) && !facts.same(source,0,source,1)) {
                        val mid=facts.pin(top,1-a)
                        val ideal=source.value("voltage")*bottom.value("resistance")/
                            (top.value("resistance")+bottom.value("resistance"))
                        val actual=facts.voltage(mid)
                        insights+=CircuitInsight("DIVIDER:${source.id}:${top.id}:${bottom.id}",
                            "Voltage divider",
                            "${top.reference} and ${bottom.reference} divide ${source.reference}. An unloaded estimate at their junction is ${String.format(Locale.US,"%.3g",ideal)} V${actual?.let { "; the solver gives ${String.format(Locale.US,"%.3g",it)} V with the present connections" } ?: ""}.",
                            setOf(source.id,top.id,bottom.id),listOf(
                                DiagnosticEvidence("Unloaded estimate",String.format(Locale.US,"%.3g V",ideal)),
                                DiagnosticEvidence("Solved node",actual?.let { String.format(Locale.US,"%.3g V",it) } ?: "unavailable")))
                    }
                }
            }
        }
        val capacitors=c.components.filter { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC }
        resistors.forEach { resistor -> capacitors.forEach { capacitor ->
            if((0..1).any { rp -> (0..1).any { cp -> facts.same(resistor,rp,capacitor,cp) } }) {
                val tau=resistor.value("resistance")*capacitor.value("capacitance")
                insights+=CircuitInsight("RC:${resistor.id}:${capacitor.id}","RC time constant",
                    "${resistor.reference} and ${capacitor.reference} share a node. Their ideal RC time constant is ${String.format(Locale.US,"%.3g",tau)} s; actual response depends on the rest of the circuit.",
                    setOf(resistor.id,capacitor.id),listOf(DiagnosticEvidence("R × C",String.format(Locale.US,"%.3g s",tau))))
            }
        } }
        c.components.filter { it.kind==Kind.TIMER_555 }.forEach { timer ->
            insights+=CircuitInsight("TIMER:${timer.id}","555 timing setting",
                "${timer.reference} uses a configured oscillator frequency of ${String.format(Locale.US,"%.3g",timer.value("frequency"))} Hz in this simplified model. External RC timing is not inferred from wiring.",
                setOf(timer.id),listOf(DiagnosticEvidence("Configured frequency",
                    String.format(Locale.US,"%.3g Hz",timer.value("frequency")))))
        }
        val power=input.result?.takeIf { it.error==null }?.readings.orEmpty()
        val hottest=c.components.filter { it.kind==Kind.RESISTOR }.mapNotNull { part ->
            power[part.id]?.power?.let { part to it } }.maxByOrNull { it.second }
        if(hottest!=null && hottest.second>0.0)
            insights+=CircuitInsight("RESISTOR_POWER:${hottest.first.id}","Highest resistor dissipation",
                "${hottest.first.reference} dissipates ${String.format(Locale.US,"%.3g",hottest.second)} W in the current solved state.",
                setOf(hottest.first.id),listOf(DiagnosticEvidence("Solved power",
                    String.format(Locale.US,"%.3g W",hottest.second))))
        return insights.distinctBy { it.id }
    }
}

object ExplanationGenerator {
    fun summary(input:IntelligenceInput,facts:CircuitFacts,insights:List<CircuitInsight>):String {
        val c=input.circuit
        if(c.components.isEmpty()) return "The canvas is empty. Add a source and a load to begin."
        val led=insights.firstOrNull { it.id.startsWith("LED_SERIES:") }
        if(led!=null) return led.explanation
        val divider=insights.firstOrNull { it.id.startsWith("DIVIDER:") }
        if(divider!=null) return divider.explanation
        val sources=c.components.filter { it.kind in setOf(Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR) }
        val loads=c.components.filter { it.kind in setOf(Kind.LED,Kind.LAMP,Kind.DC_MOTOR,Kind.RESISTOR,
            Kind.SERVO_MOTOR,Kind.RELAY) }
        return "${c.components.size} components and ${c.wires.size} wires. " +
            (if(sources.isEmpty()) "No independent source is present. " else "${sources.size} source${if(sources.size==1) "" else "s"} present. ")+
            (if(loads.isEmpty()) "No modeled load is present." else "${loads.size} modeled load${if(loads.size==1) "" else "s"} present.")+
            (if(input.result?.error!=null) " Solver state: ${input.result.error}" else "")
    }

    fun partRole(input:IntelligenceInput,partId:String):String {
        val part=input.circuit.components.firstOrNull { it.id==partId } ?: return "Select a component in this circuit."
        val facts=CircuitFacts(input.circuit,input.result)
        if(part.kind==Kind.RESISTOR) {
            val linked=input.circuit.components.firstOrNull { led -> led.kind==Kind.LED &&
                input.circuit.components.any { source -> source.kind in setOf(Kind.BATTERY,Kind.SOURCE) &&
                    facts.seriesResistor(source,led)?.id==part.id } }
            if(linked!=null) return "${part.reference} is in series with ${linked.reference} and limits its forward current. The present resistance is ${String.format(Locale.US,"%.3g",part.value("resistance"))} Ω."
        }
        if(part.kind==Kind.SWITCH) return "${part.reference} ${if(part.closed) "closes" else "opens"} a wired path. Its effect depends on the current path around it."
        if(part.kind.isBoard) return "${part.reference} is a ${BoardRegistry.boards.getValue(part.kind).product} board model. Its connected pins provide power and manually configured GPIO behavior; firmware is not executed."
        val reading=input.result?.readings?.get(part.id)
        return "${part.reference} is a ${part.kind.title} in this circuit.${reading?.let {
            " Solved voltage ${String.format(Locale.US,"%.3g",it.voltage)} V and current ${String.format(Locale.US,"%.3g",it.current)} A."
        } ?: ""} ${ComponentRegistry.definitions.getValue(part.kind).description}"
    }

    /** A clone is solved; the user's circuit and Undo history are not touched. */
    fun removalPreview(input:IntelligenceInput,partId:String):String {
        val part=input.circuit.components.firstOrNull { it.id==partId } ?: return "Component is unavailable."
        val after=input.circuit.copy(components=input.circuit.components.filterNot { it.id==partId },
            wires=input.circuit.wires.filterNot { it.start.componentId==partId || it.end.componentId==partId })
        val solved=DcSolver().solve(after)
        return if(solved.error!=null)
            "If ${part.reference} were removed, the cloned circuit would not solve: ${solved.error} The actual circuit has not changed."
        else "If ${part.reference} were removed, the cloned circuit would still solve. ${after.wires.size} wires would remain; inspect the resulting currents before deciding. The actual circuit has not changed."
    }
}
