package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.guide.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test

class GuidedFlowEngineTest {
    private fun snap(c:Circuit,run:Boolean=true,mode:MeterMode=MeterMode.DC_VOLTAGE,
                     plus:TerminalRef?=null,common:TerminalRef?=null,wire:String?=null)=
        GuideSnapshot(c,DcSolver().solve(c),run,mode,plus,common,wire,0.0)
    private fun wire(c:Circuit,a:Kind,ap:Int,b:Kind,bp:Int):Circuit {
        val first=c.components.first { it.kind==a };val second=c.components.first { it.kind==b }
        return c.copy(wires=c.wires+Wire(start=TerminalRef(first.id,ap),end=TerminalRef(second.id,bp)))
    }

    @Test fun firstLedProgressesFromActualCircuitAndRegressesAfterDeletion() {
        val lesson=LessonCatalog.byId.getValue("first-led")
        var circuit=lesson.initialCircuit()
        var session=GuideSession(lesson.id)
        assertEquals(0,GuidedFlowEngine.reconcile(lesson,session,snap(circuit)).stepIndex)
        listOf(Kind.BATTERY,Kind.RESISTOR,Kind.LED).forEach { kind ->
            circuit=circuit.copy(components=circuit.components+PlacedComponent(kind=kind,
                reference=circuit.nextReference(kind),x=100f,y=100f))
            session=GuidedFlowEngine.reconcile(lesson,session,snap(circuit))
        }
        assertEquals(3,session.stepIndex)
        circuit=wire(circuit,Kind.BATTERY,0,Kind.RESISTOR,0)
        circuit=wire(circuit,Kind.RESISTOR,1,Kind.LED,0)
        circuit=wire(circuit,Kind.LED,1,Kind.BATTERY,1)
        circuit=wire(circuit,Kind.BATTERY,1,Kind.GROUND,0)
        session=GuidedFlowEngine.reconcile(lesson,session,snap(circuit,false))
        assertEquals(8,session.stepIndex)
        session=GuidedFlowEngine.reconcile(lesson,session,snap(circuit),GuideEvent(GuideEventType.SIMULATION_STARTED))
        assertTrue(session.complete)
        circuit=circuit.copy(wires=circuit.wires.filterNot { it.start.componentId==circuit.components.first { p -> p.kind==Kind.LED }.id })
        val fresh=GuidedFlowEngine.reconcile(lesson,GuideSession(lesson.id,8),snap(circuit))
        assertTrue(fresh.stepIndex<8)
    }

    @Test fun faultsAreDiagnosedAndRealRepairClearsThem() {
        val reversed=LessonTemplates.led(fault="reversed_led")
        assertFalse(GuidedFlowEngine.meets(LessonCriterion.WorkingLed(),snap(reversed)))
        val open=LessonTemplates.led(fault="open")
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.IssuePresent("UNWIRED_PIN"),snap(open)))
        val repaired=wire(open,Kind.RESISTOR,1,Kind.LED,0)
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.WorkingLed(),snap(repaired)))
        val short=LessonTemplates.led(fault="short")
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.IssuePresent("SOURCE_SHORT"),snap(short)))
        val noShort=short.copy(wires=short.wires.filterNot { it.id=="guide-short-wire" })
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.IssueAbsent("SOURCE_SHORT"),snap(noShort)))
    }

    @Test fun seriesAndParallelTopologyRecognizeEquivalentPinOrderAndRejectGaps() {
        val source=LessonTemplates.resistorStart("Topology")
        val r1=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=400f,y=300f)
        val r2=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=600f,y=300f)
        val base=source.copy(components=source.components+listOf(r1,r2))
        val v=base.components.first { it.kind==Kind.SOURCE }
        val exactSeries=base.copy(wires=base.wires+listOf(
            Wire(start=TerminalRef(v.id,0),end=TerminalRef(r1.id,1)),
            Wire(start=TerminalRef(r1.id,0),end=TerminalRef(r2.id,1)),
            Wire(start=TerminalRef(r2.id,0),end=TerminalRef(v.id,1))))
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.SeriesResistors,snap(exactSeries)))
        assertFalse(GuidedFlowEngine.meets(LessonCriterion.SeriesResistors,
            snap(exactSeries.copy(wires=exactSeries.wires.dropLast(1)))))
        val parallel=base.copy(wires=base.wires+listOf(
            Wire(start=TerminalRef(v.id,0),end=TerminalRef(r1.id,0)),
            Wire(start=TerminalRef(v.id,1),end=TerminalRef(r1.id,1)),
            Wire(start=TerminalRef(v.id,0),end=TerminalRef(r2.id,1)),
            Wire(start=TerminalRef(v.id,1),end=TerminalRef(r2.id,0))))
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.ParallelResistors,snap(parallel)))
        assertFalse(GuidedFlowEngine.meets(LessonCriterion.SeriesResistors,snap(parallel)))
    }

    @Test fun measurementsRequireCorrectMeterAndRunningSimulation() {
        val c=LessonTemplates.led()
        val battery=c.components.first { it.kind==Kind.BATTERY }
        val resistor=c.components.first { it.kind==Kind.RESISTOR }
        val batterySnapshot=snap(c,plus=TerminalRef(battery.id,0),common=TerminalRef(battery.id,1))
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.ProbeVoltage(Kind.BATTERY,min=8.0,max=10.0),batterySnapshot))
        assertFalse(GuidedFlowEngine.meets(LessonCriterion.ProbeVoltage(Kind.RESISTOR,min=5.0,max=8.0),batterySnapshot))
        assertFalse(GuidedFlowEngine.meets(LessonCriterion.ProbeVoltage(Kind.BATTERY,min=8.0,max=10.0),batterySnapshot.copy(running=false)))
        val resistorVoltage=snap(c,plus=TerminalRef(resistor.id,1),common=TerminalRef(resistor.id,0))
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.ProbeVoltage(Kind.RESISTOR,min=5.0,max=8.0),resistorVoltage))
        val current=snap(c,mode=MeterMode.DC_CURRENT,wire="guide-battery-resistor")
        assertTrue(GuidedFlowEngine.meets(LessonCriterion.ProbeCurrent(.005,.03),current))
        assertFalse(GuidedFlowEngine.meets(LessonCriterion.ProbeCurrent(.005,.03),current.copy(meterCurrentWireId=null)))
    }

    @Test fun hintsAdvanceOneLevelAndAllPublishedLessonsHaveDistinctSteps() {
        assertEquals(10,LessonCatalog.lessons.size)
        LessonCatalog.lessons.forEach { lesson ->
            assertTrue(lesson.steps.isNotEmpty())
            assertEquals(lesson.steps.size,lesson.steps.map { it.id }.toSet().size)
        }
        val lesson=LessonCatalog.byId.getValue("first-led")
        val start=GuideSession(lesson.id)
        val once=GuidedFlowEngine.hint(lesson,start)
        val twice=GuidedFlowEngine.hint(lesson,once)
        assertEquals(1,once.hintsFor(lesson.steps.first()))
        assertEquals(2,twice.hintsFor(lesson.steps.first()))
    }

    @Test fun allTenLessonFlowsCanReachCompletionUsingSolverStates() {
        fun go(id:String,c:Circuit,session:GuideSession=GuideSession(id),
               event:GuideEvent?=null,run:Boolean=true,mode:MeterMode=MeterMode.DC_VOLTAGE,
               plus:TerminalRef?=null,common:TerminalRef?=null,wire:String?=null)=
            GuidedFlowEngine.reconcile(LessonCatalog.byId.getValue(id),session,
                snap(c,run,mode,plus,common,wire),event)
        fun ref(c:Circuit,kind:Kind,pin:Int,occurrence:Int=0)=
            TerminalRef(c.components.filter { it.kind==kind }[occurrence].id,pin)
        fun runEvent()=GuideEvent(GuideEventType.SIMULATION_STARTED)

        val voltage=LessonTemplates.led()
        var s=go("measure-voltage",voltage,event=runEvent())
        assertEquals(1,s.stepIndex)
        s=go("measure-voltage",voltage,s,plus=ref(voltage,Kind.BATTERY,0),common=ref(voltage,Kind.BATTERY,1))
        s=go("measure-voltage",voltage,s,plus=ref(voltage,Kind.RESISTOR,0),common=ref(voltage,Kind.RESISTOR,1))
        s=go("measure-voltage",voltage,s,plus=ref(voltage,Kind.LED,0),common=ref(voltage,Kind.LED,1))
        assertTrue("measure-voltage",s.complete)

        s=go("measure-current",voltage,event=runEvent())
        s=go("measure-current",voltage,s,mode=MeterMode.DC_CURRENT,wire="guide-battery-resistor")
        assertTrue("measure-current",s.complete)

        val ohm=LessonTemplates.ohm()
        fun change(c:Circuit,kind:Kind,key:String,value:Double)=c.copy(components=c.components.map {
            if(it.kind==kind) it.copy(parameters=it.parameters+(key to value)) else it })
        val ohm1=change(change(ohm,Kind.SOURCE,"voltage",5.0),Kind.RESISTOR,"resistance",1000.0)
        s=go("ohms-law",ohm1,run=false)
        assertEquals(2,s.stepIndex)
        s=go("ohms-law",ohm1,s,runEvent())
        s=go("ohms-law",ohm1,s,mode=MeterMode.DC_CURRENT,wire="guide-source-load")
        val ohm2=change(ohm1,Kind.RESISTOR,"resistance",2000.0)
        s=go("ohms-law",ohm2,s,mode=MeterMode.DC_CURRENT,wire="guide-source-load")
        assertTrue("ohms-law",s.complete)

        val base=LessonTemplates.resistorStart("Two resistors")
        val r1=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=400f,y=300f)
        val r2=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=600f,y=300f)
        val v=base.components.first { it.kind==Kind.SOURCE }
        val series=base.copy(components=base.components+listOf(r1,r2),wires=base.wires+listOf(
            Wire(start=TerminalRef(v.id,0),end=TerminalRef(r1.id,0)),
            Wire(start=TerminalRef(r1.id,1),end=TerminalRef(r2.id,0)),
            Wire(start=TerminalRef(r2.id,1),end=TerminalRef(v.id,1))))
        s=go("series-resistors",series,run=false)
        assertEquals(3,s.stepIndex)
        s=go("series-resistors",series,s,runEvent())
        s=go("series-resistors",series,s,plus=TerminalRef(r1.id,0),common=TerminalRef(r1.id,1))
        s=go("series-resistors",series,s,plus=TerminalRef(r2.id,0),common=TerminalRef(r2.id,1))
        assertTrue("series-resistors",s.complete)
        val parallel=base.copy(components=base.components+listOf(r1,r2),wires=base.wires+listOf(
            Wire(start=TerminalRef(v.id,0),end=TerminalRef(r1.id,0)),
            Wire(start=TerminalRef(v.id,1),end=TerminalRef(r1.id,1)),
            Wire(start=TerminalRef(v.id,0),end=TerminalRef(r2.id,0)),
            Wire(start=TerminalRef(v.id,1),end=TerminalRef(r2.id,1))))
        s=go("parallel-resistors",parallel,run=false)
        assertEquals(3,s.stepIndex)
        s=go("parallel-resistors",parallel,s,runEvent())
        s=go("parallel-resistors",parallel,s,plus=TerminalRef(r1.id,0),common=TerminalRef(r1.id,1))
        s=go("parallel-resistors",parallel,s,plus=TerminalRef(r2.id,0),common=TerminalRef(r2.id,1))
        assertTrue("parallel-resistors",s.complete)

        val reversed=LessonTemplates.led(fault="reversed_led")
        s=go("led-polarity",reversed,event=runEvent())
        assertEquals(1,s.stepIndex)
        s=go("led-polarity",reversed,s,GuideEvent(GuideEventType.QUESTION_ANSWERED,"LED polarity"))
        s=go("led-polarity",voltage,s)
        assertTrue("led-polarity",s.complete)
        val short=LessonTemplates.led(fault="short")
        s=go("short-circuit",short,run=false)
        assertEquals(1,s.stepIndex)
        s=go("short-circuit",short,s,GuideEvent(GuideEventType.QUESTION_ANSWERED,"Bypass wire"),run=false)
        val repairedShort=short.copy(wires=short.wires.filterNot { it.id=="guide-short-wire" })
        s=go("short-circuit",repairedShort,s,run=false)
        s=go("short-circuit",repairedShort,s)
        assertTrue("short-circuit",s.complete)
        val open=LessonTemplates.led(fault="open")
        s=go("open-circuit",open,run=false)
        assertEquals(1,s.stepIndex)
        s=go("open-circuit",open,s,GuideEvent(GuideEventType.QUESTION_ANSWERED,"Open path"),run=false)
        s=go("open-circuit",wire(open,Kind.RESISTOR,1,Kind.LED,0),s)
        assertTrue("open-circuit",s.complete)

        val switchBase=LessonTemplates.groundOnly("Switch")
        val b=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=100f,y=200f)
        val sw=PlacedComponent(kind=Kind.SWITCH,reference="S1",x=300f,y=200f)
        val r=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=500f,y=200f)
        val led=PlacedComponent(kind=Kind.LED,reference="D1",x=700f,y=200f)
        val g=switchBase.components.first()
        val closed=switchBase.copy(components=switchBase.components+listOf(b,sw,r,led),wires=listOf(
            Wire(start=TerminalRef(b.id,0),end=TerminalRef(sw.id,0)),
            Wire(start=TerminalRef(sw.id,1),end=TerminalRef(r.id,0)),
            Wire(start=TerminalRef(r.id,1),end=TerminalRef(led.id,0)),
            Wire(start=TerminalRef(led.id,1),end=TerminalRef(b.id,1)),
            Wire(start=TerminalRef(b.id,1),end=TerminalRef(g.id,0))))
        s=go("switch-led",closed,run=false)
        assertEquals(10,s.stepIndex)
        s=go("switch-led",closed,s,runEvent())
        val opened=closed.copy(components=closed.components.map { if(it.id==sw.id) it.copy(closed=false) else it })
        s=go("switch-led",opened,s,GuideEvent(GuideEventType.SWITCH_TOGGLED))
        assertEquals(13,s.stepIndex)
        s=go("switch-led",closed,s,GuideEvent(GuideEventType.SWITCH_TOGGLED))
        assertTrue("switch-led",s.complete)
    }
}
