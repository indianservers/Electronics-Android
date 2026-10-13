package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.TruthTableAnalyzer
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class CircuitTruthTableTest {
    private fun gate(kind:Kind):Circuit {
        val a=PlacedComponent(id="a",kind=Kind.LOGIC_INPUT,reference="IN1",x=0f,y=0f)
        val b=PlacedComponent(id="b",kind=Kind.LOGIC_INPUT,reference="IN2",x=0f,y=0f)
        val gate=PlacedComponent(id="g",kind=kind,reference="U1",x=0f,y=0f)
        val out=PlacedComponent(id="o",kind=Kind.LOGIC_OUTPUT,reference="OUT1",x=0f,y=0f)
        return Circuit("Gate",listOf(a,b,gate,out),listOf(
            Wire(start=TerminalRef("a",0),end=TerminalRef("g",0)),
            Wire(start=TerminalRef("b",0),end=TerminalRef("g",1)),
            Wire(start=TerminalRef("g",2),end=TerminalRef("o",0))))
    }
    @Test fun exhaustiveBasicGates() {
        val patterns=mapOf(Kind.AND_GATE to "0001",Kind.OR_GATE to "0111",Kind.XOR_GATE to "0110",
            Kind.NAND_GATE to "1110",Kind.NOR_GATE to "1000",Kind.XNOR_GATE to "1001")
        patterns.forEach { (kind,expected) ->
            val result=TruthTableAnalyzer.analyze(gate(kind))
            assertEquals(kind.name,expected,result.rows.joinToString("") { if(it.outputs.single()==LogicState.HIGH) "1" else "0" })
        }
    }
    @Test fun undrivenOutputsAreHighImpedance() {
        val circuit=gate(Kind.AND_GATE)
        val result=TruthTableAnalyzer.analyze(circuit.copy(wires=circuit.wires.dropLast(1)))
        assertTrue(result.rows.all { it.outputs.single()==LogicState.HIGH_Z })
    }
    @Test fun rejectsFeedbackAndSequentialParts() {
        val circuit=gate(Kind.AND_GATE)
        val feedback=circuit.copy(wires=circuit.wires+Wire(start=TerminalRef("g",2),end=TerminalRef("g",0)))
        assertTrue(runCatching { TruthTableAnalyzer.analyze(feedback) }.isFailure)
        assertTrue(runCatching { TruthTableAnalyzer.analyze(gate(Kind.D_FLIP_FLOP)) }.isFailure)
    }
    @Test fun conflictingInputDriversProduceUnknown() {
        val circuit=gate(Kind.AND_GATE)
        val conflict=circuit.copy(wires=circuit.wires+Wire(start=TerminalRef("a",0),end=TerminalRef("b",0)))
        assertEquals(LogicState.UNKNOWN,TruthTableAnalyzer.analyze(conflict).rows[1].outputs.single())
    }
    @Test fun namedNetsConnectSeparateWireSegments() {
        val circuit=gate(Kind.AND_GATE)
        val junction=PlacedComponent(id="j",kind=Kind.JUNCTION,reference="J1",x=0f,y=0f)
        val first=circuit.wires.first()
        val named=circuit.copy(components=circuit.components+junction,
            wires=circuit.wires.drop(1)+first.copy(end=TerminalRef("j",0),label="A")+
                Wire(start=TerminalRef("g",0),end=TerminalRef("j",0),label="a"))
        assertEquals(TruthTableAnalyzer.analyze(circuit).rows,TruthTableAnalyzer.analyze(named).rows)
    }
}
