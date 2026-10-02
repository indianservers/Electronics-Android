package com.indianservers.circuitssimulator.guide

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.intelligence.CircuitIntelligence
import com.indianservers.circuitssimulator.intelligence.IntelligenceInput
import kotlin.math.abs

enum class LessonDifficulty { BEGINNER, INTERMEDIATE, ADVANCED }
enum class GuideEventType {
    COMPONENT_PLACED, COMPONENT_MOVED, COMPONENT_DELETED, WIRE_CREATED, WIRE_DELETED,
    PROPERTY_CHANGED, SWITCH_TOGGLED, SIMULATION_STARTED, SIMULATION_STOPPED,
    METER_CHANGED, PROBE_ATTACHED, SOLVER_UPDATED, QUESTION_ANSWERED, CONTINUED
}
data class GuideEvent(val type:GuideEventType,val detail:String="",val atMillis:Long=System.currentTimeMillis())

sealed interface LessonCriterion {
    data class Count(val kind:Kind,val count:Int=1):LessonCriterion
    data class SourceGrounded(val source:Kind=Kind.BATTERY):LessonCriterion
    data class SourceToResistor(val source:Kind=Kind.BATTERY):LessonCriterion
    data class Link(val from:Kind,val to:Kind,val fromPin:Int?=null,val toPin:Int?=null):LessonCriterion
    data object ResistorToLedAnode:LessonCriterion
    data class LedCathodeToSource(val source:Kind=Kind.BATTERY):LessonCriterion
    data class LedSeries(val source:Kind=Kind.BATTERY,val withSwitch:Boolean=false):LessonCriterion
    data object SeriesResistors:LessonCriterion
    data object ParallelResistors:LessonCriterion
    data class WorkingLed(val minAmps:Double=.001,val maxAmps:Double=.03):LessonCriterion
    data class LedOff(val maxAmps:Double=1e-5):LessonCriterion
    data class RunStarted(val requireWorking:Boolean=false):LessonCriterion
    data class SwitchToggled(val closed:Boolean):LessonCriterion
    data class PropertyRange(val kind:Kind,val key:String,val min:Double,val max:Double):LessonCriterion
    data class ProbeVoltage(val kind:Kind,val occurrence:Int=0,val min:Double=0.0,val max:Double=100.0):LessonCriterion
    data class ProbeCurrent(val min:Double,val max:Double):LessonCriterion
    data class IssuePresent(val code:String):LessonCriterion
    data class IssueAbsent(val code:String):LessonCriterion
    data class Answer(val choice:String):LessonCriterion
    data object Continue:LessonCriterion
}

data class LessonStep(
    val id:String,val instruction:String,val criterion:LessonCriterion,
    val why:String,val hints:List<String> = emptyList(),
    val focusKinds:Set<Kind> = emptySet(),val focusPins:List<GuidePin> = emptyList(),
    val choices:List<String> = emptyList()
)
data class GuidePin(val kind:Kind,val pin:Int)
data class GuidedLesson(
    val id:String,val title:String,val summary:String,val category:String,
    val difficulty:LessonDifficulty=LessonDifficulty.BEGINNER,
    val objectives:List<String>,val initialCircuit:()->Circuit,
    val steps:List<LessonStep>,val completionNote:String,
    val allowedKinds:Set<Kind> = emptySet()
)
data class GuideSession(
    val lessonId:String,val stepIndex:Int=0,val hintLevels:Map<String,Int> = emptyMap(),
    val startedAtMillis:Long=System.currentTimeMillis(),val completedAtMillis:Long?=null,
    val eventLog:List<GuideEvent> = emptyList(),val feedback:String?=null,
    val practice:Boolean=false
) {
    val complete get()=completedAtMillis!=null
    fun hintsFor(step:LessonStep)=hintLevels[step.id] ?: 0
}
data class GuideSnapshot(
    val circuit:Circuit,val result:DcResult?,val running:Boolean,
    val meterMode:MeterMode,val meterPlus:TerminalRef?,val meterCommon:TerminalRef?,
    val meterCurrentWireId:String?,val elapsedSeconds:Double
)

/** Reusable, topology-driven flow engine. No screen coordinates or lesson-specific UI logic. */
object GuidedFlowEngine {
    private class Nets(private val circuit:Circuit) {
        private val parent=circuit.components.flatMap { part ->
            (0 until part.terminalCount).map { TerminalRef(part.id,it) }
        }.associateWith { it }.toMutableMap()
        private fun root(t:TerminalRef):TerminalRef {
            val p=parent[t] ?: return t
            if(p==t) return t
            val r=root(p);parent[t]=r;return r
        }
        init { circuit.electricalConnections().forEach { (a,b) ->
            if(a in parent && b in parent) parent[root(a)]=root(b)
        } }
        fun same(a:TerminalRef,b:TerminalRef)=a in parent && b in parent && root(a)==root(b)
        fun same(a:PlacedComponent,ap:Int,b:PlacedComponent,bp:Int)=
            same(TerminalRef(a.id,ap),TerminalRef(b.id,bp))
    }

    private fun Circuit.one(kind:Kind)=components.firstOrNull { it.kind==kind }
    private fun Circuit.all(kind:Kind)=components.filter { it.kind==kind }

    private fun seriesLed(c:Circuit,n:Nets,sourceKind:Kind,withSwitch:Boolean):Boolean {
        val source=c.one(sourceKind) ?: return false
        val led=c.one(Kind.LED) ?: return false
        val resistor=c.all(Kind.RESISTOR).firstOrNull() ?: return false
        val grounded=c.all(Kind.GROUND).any { n.same(source,1,it,0) }
        if(!grounded || !n.same(led,1,source,1)) return false
        val switch=if(withSwitch) c.one(Kind.SWITCH) ?: return false else null
        return (0..1).any { rp ->
            val input=if(switch==null) n.same(source,0,resistor,rp)
                else (0..1).any { sp -> n.same(source,0,switch,sp) &&
                    n.same(switch,1-sp,resistor,rp) }
            input && n.same(resistor,1-rp,led,0) &&
                !n.same(source,0,source,1) && !n.same(resistor,0,resistor,1)
        }
    }

    private fun resistorNetwork(c:Circuit,n:Nets,parallel:Boolean):Boolean {
        val source=c.one(Kind.SOURCE) ?: return false
        val rs=c.all(Kind.RESISTOR)
        if(rs.size<2 || c.all(Kind.GROUND).none { n.same(source,1,it,0) }) return false
        return rs.indices.any { i -> rs.indices.any { j ->
            if(i==j) false else if(parallel) (0..1).any { a -> (0..1).any { b ->
                n.same(source,0,rs[i],a) && n.same(source,1,rs[i],1-a) &&
                    n.same(source,0,rs[j],b) && n.same(source,1,rs[j],1-b) &&
                    !n.same(source,0,source,1)
            } } else (0..1).any { a -> (0..1).any { b ->
                n.same(source,0,rs[i],a) && n.same(rs[i],1-a,rs[j],b) &&
                    n.same(rs[j],1-b,source,1) && !n.same(source,0,source,1) &&
                    !n.same(rs[i],0,rs[i],1) && !n.same(rs[j],0,rs[j],1)
            } }
        } }
    }

    fun reading(snapshot:GuideSnapshot):MeterResult? {
        if(!snapshot.running) return null
        val meter=Multimeter()
        return when(snapshot.meterMode) {
            MeterMode.DC_VOLTAGE -> if(snapshot.meterPlus!=null && snapshot.meterCommon!=null)
                meter.voltage(snapshot.circuit,snapshot.meterPlus,snapshot.meterCommon,snapshot.elapsedSeconds) else null
            MeterMode.DC_CURRENT -> snapshot.meterCurrentWireId?.let {
                meter.current(snapshot.circuit,it,snapshot.elapsedSeconds) }
            else -> null
        }
    }

    fun meets(criterion:LessonCriterion,snapshot:GuideSnapshot,event:GuideEvent?=null):Boolean {
        val c=snapshot.circuit
        val n=Nets(c)
        return when(criterion) {
            is LessonCriterion.Count -> c.all(criterion.kind).size>=criterion.count
            is LessonCriterion.SourceGrounded -> c.one(criterion.source)?.let { source ->
                c.all(Kind.GROUND).any { n.same(source,1,it,0) } } ?: false
            is LessonCriterion.SourceToResistor -> c.one(criterion.source)?.let { source ->
                c.all(Kind.RESISTOR).any { r -> n.same(source,0,r,0) || n.same(source,0,r,1) } } ?: false
            is LessonCriterion.Link -> c.all(criterion.from).any { a ->
                c.all(criterion.to).any { b ->
                    (criterion.fromPin?.let { listOf(it) } ?: (0 until a.terminalCount).toList()).any { ap ->
                        (criterion.toPin?.let { listOf(it) } ?: (0 until b.terminalCount).toList()).any { bp ->
                            n.same(a,ap,b,bp) } } } }
            LessonCriterion.ResistorToLedAnode -> c.one(Kind.LED)?.let { led ->
                c.all(Kind.RESISTOR).any { r -> (0..1).any { pin -> n.same(r,pin,led,0) } } } ?: false
            is LessonCriterion.LedCathodeToSource -> c.one(Kind.LED)?.let { led ->
                c.one(criterion.source)?.let { source -> n.same(led,1,source,1) } } ?: false
            is LessonCriterion.LedSeries -> seriesLed(c,n,criterion.source,criterion.withSwitch)
            LessonCriterion.SeriesResistors -> resistorNetwork(c,n,false)
            LessonCriterion.ParallelResistors -> resistorNetwork(c,n,true)
            is LessonCriterion.WorkingLed -> snapshot.running && snapshot.result!=null && snapshot.result.error==null &&
                c.all(Kind.LED).any { led ->
                    (snapshot.result.readings[led.id]?.current ?: 0.0) in criterion.minAmps..criterion.maxAmps }
            is LessonCriterion.LedOff -> snapshot.result!=null && snapshot.result.error==null && c.all(Kind.LED).any { led ->
                abs(snapshot.result.readings[led.id]?.current ?: Double.POSITIVE_INFINITY)<=criterion.maxAmps }
            is LessonCriterion.RunStarted -> event?.type==GuideEventType.SIMULATION_STARTED &&
                snapshot.running && (!criterion.requireWorking ||
                    (snapshot.result!=null && snapshot.result.error==null))
            is LessonCriterion.SwitchToggled -> event?.type==GuideEventType.SWITCH_TOGGLED &&
                c.all(Kind.SWITCH).any { it.closed==criterion.closed }
            is LessonCriterion.PropertyRange -> c.all(criterion.kind).any {
                it.value(criterion.key) in criterion.min..criterion.max }
            is LessonCriterion.ProbeVoltage -> {
                val part=c.all(criterion.kind).getOrNull(criterion.occurrence)
                val plus=snapshot.meterPlus;val common=snapshot.meterCommon
                if(!snapshot.running || part==null || plus==null || common==null ||
                    snapshot.meterMode!=MeterMode.DC_VOLTAGE || part.terminalCount<2 ||
                    !((n.same(plus,TerminalRef(part.id,0)) && n.same(common,TerminalRef(part.id,1))) ||
                        (part.kind==Kind.RESISTOR && n.same(plus,TerminalRef(part.id,1)) &&
                            n.same(common,TerminalRef(part.id,0))))) false
                else reading(snapshot)?.let { it.message==null && it.value!=null &&
                    (if(part.kind==Kind.RESISTOR) abs(it.value) else it.value) in criterion.min..criterion.max } ?: false
            }
            is LessonCriterion.ProbeCurrent -> {
                val selected=c.wires.firstOrNull { it.id==snapshot.meterCurrentWireId }
                if(!snapshot.running || snapshot.meterMode!=MeterMode.DC_CURRENT || selected==null ||
                    selected.label!=null || c.all(Kind.RESISTOR).none { r ->
                        selected.start.componentId==r.id || selected.end.componentId==r.id }) false
                else reading(snapshot)?.let { it.message==null && it.value!=null &&
                    abs(it.value) in criterion.min..criterion.max } ?: false
            }
            is LessonCriterion.IssuePresent -> CircuitDiagnostics.inspect(c,snapshot.result).any { it.code==criterion.code }
            is LessonCriterion.IssueAbsent -> snapshot.result!=null && snapshot.result.error==null &&
                CircuitDiagnostics.inspect(c,snapshot.result).none { it.code==criterion.code }
            is LessonCriterion.Answer -> event?.type==GuideEventType.QUESTION_ANSWERED &&
                event.detail==criterion.choice
            LessonCriterion.Continue -> event?.type==GuideEventType.CONTINUED
        }
    }

    private fun reversible(criterion:LessonCriterion)=criterion is LessonCriterion.Count ||
        criterion is LessonCriterion.SourceGrounded || criterion is LessonCriterion.SourceToResistor ||
        criterion is LessonCriterion.Link ||
        criterion==LessonCriterion.ResistorToLedAnode ||
        criterion is LessonCriterion.LedCathodeToSource || criterion is LessonCriterion.LedSeries ||
        criterion==LessonCriterion.SeriesResistors || criterion==LessonCriterion.ParallelResistors

    fun reconcile(lesson:GuidedLesson,session:GuideSession,snapshot:GuideSnapshot,
                  event:GuideEvent?=null):GuideSession {
        if(session.complete) return session
        var index=session.stepIndex.coerceIn(0,lesson.steps.size)
        for(i in 0 until index) if(reversible(lesson.steps[i].criterion) &&
            !meets(lesson.steps[i].criterion,snapshot)) { index=i;break }
        while(index<lesson.steps.size && meets(lesson.steps[index].criterion,snapshot,event)) index++
        val log=if(event==null) session.eventLog else (session.eventLog+event).takeLast(40)
        val completed=if(index==lesson.steps.size) System.currentTimeMillis() else null
        val issues=CircuitDiagnostics.inspect(snapshot.circuit,snapshot.result)
        val intelligence=if(event!=null && snapshot.circuit.components.isNotEmpty())
            CircuitIntelligence.analyze(IntelligenceInput(snapshot.circuit,snapshot.result,snapshot.running)).issues
        else emptyList()
        val feedback=when {
            event?.type==GuideEventType.QUESTION_ANSWERED && index==session.stepIndex ->
                "That answer does not explain the circuit yet. Inspect the connections and try again."
            intelligence.any { it.ruleId=="SOURCE_SHORT" } -> intelligence.first { it.ruleId=="SOURCE_SHORT" }.summary
            intelligence.any { it.ruleId=="LED_REVERSED" } -> intelligence.first { it.ruleId=="LED_REVERSED" }.summary
            intelligence.any { it.ruleId=="LED_NO_RESISTOR" } -> intelligence.first { it.ruleId=="LED_NO_RESISTOR" }.summary
            issues.any { it.code=="SOURCE_SHORT" } -> "Potential short circuit: the source terminals share a wire path. Remove the bypass wire."
            issues.any { it.code=="LED_CURRENT" } -> "LED current is too high. Add or increase a series resistor."
            issues.any { it.code=="REVERSE_LED" } -> "The LED is reverse biased. Check its anode and cathode connections."
            issues.any { it.code.startsWith("BOARD_PIN_VOLTAGE") } -> "A board input is outside its documented voltage range."
            snapshot.running && snapshot.result?.error!=null -> snapshot.result.error
            event?.type==GuideEventType.WIRE_CREATED && index==session.stepIndex ->
                "The new wire has not completed this step. Check the highlighted pins and the path through each component."
            else -> null
        }
        return session.copy(stepIndex=index,completedAtMillis=completed,eventLog=log,feedback=feedback)
    }

    fun hint(lesson:GuidedLesson,session:GuideSession):GuideSession {
        val step=lesson.steps.getOrNull(session.stepIndex) ?: return session
        val next=(session.hintsFor(step)+1).coerceAtMost(step.hints.size)
        return session.copy(hintLevels=session.hintLevels+(step.id to next))
    }
}
