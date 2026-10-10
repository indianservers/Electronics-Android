package com.indianservers.circuitssimulator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.data.ProjectStore
import com.indianservers.circuitssimulator.data.SavedProject
import com.indianservers.circuitssimulator.data.GuideStore
import com.indianservers.circuitssimulator.guide.*
import com.indianservers.circuitssimulator.intelligence.*
import com.indianservers.circuitssimulator.firmware.*
import com.indianservers.circuitssimulator.simulation.embedded.GpioReading
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.TransientResult
import com.indianservers.circuitssimulator.simulation.TransientFrame
import com.indianservers.circuitssimulator.simulation.TransientCheckpoint
import com.indianservers.circuitssimulator.simulation.TransientSolver
import com.indianservers.circuitssimulator.simulation.CanvasDigitalSimulator
import com.indianservers.circuitssimulator.simulation.CanvasDigitalSession
import com.indianservers.circuitssimulator.simulation.appendTransient
import com.indianservers.circuitssimulator.simulation.MeterMode
import com.indianservers.circuitssimulator.simulation.AcSolver
import com.indianservers.circuitssimulator.simulation.AcSweep
import com.indianservers.circuitssimulator.simulation.SweepScale
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import kotlin.math.max

data class FirmwareUiState(
    val open:Boolean=false,val source:String="",val boardId:String?=null,
    val language:FirmwareLanguage=FirmwareLanguage.ARDUINO_SUBSET,
    val compiled:Boolean=false,val running:Boolean=false,val error:FirmwareDiagnostic?=null,
    val timeMicros:Long=0,val currentLine:Int=0,val console:List<String> = emptyList(),
    val pins:Map<String,GpioReading> = emptyMap(),val variables:Map<String,String> = emptyMap(),
    val boardPins:Map<String,Map<String,GpioReading>> = emptyMap(),
    val boardRuntime:Map<String,String> = emptyMap(),
    val busLog:List<String> = emptyList(),val displays:Map<String,String> = emptyMap(),
    val framebuffers:Map<String,List<Int>> = emptyMap(),
    val peripheralInspectors:Map<String,Map<String,String>> = emptyMap(),
    val highlightedPin:String?=null,val serialPaused:Boolean=false,
    val analyzerOpen:Boolean=false
)

data class SimulatorState(
    val circuit: Circuit = SampleCircuits.led(), val result: DcResult = DcResult(),
    val selectedId: String? = null, val selectedWireId: String? = null,
    val contextId: String? = null, val placement: Kind? = null, val catalogOpen: Boolean = false,
    val running: Boolean = true, val message: String? = null, val zoom: Float = 1f,
    val panX: Float = 0f, val panY: Float = 0f,
    val transient: TransientResult? = null, val elapsedSeconds: Double = 0.0,
    val scopeOpen:Boolean=false, val scopeCh1:TerminalRef?=null, val scopeCh2:TerminalRef?=null,
    val scopeCh3:TerminalRef?=null,val scopeCh4:TerminalRef?=null,
    val meterOpen:Boolean=false,val meterMode:MeterMode=MeterMode.DC_VOLTAGE,
    val meterPlus:TerminalRef?=null,val meterCommon:TerminalRef?=null,val meterCurrentWireId:String?=null,
    val projectsOpen:Boolean=false,val projects:List<SavedProject> = emptyList(),
    val diagnosticsOpen:Boolean=false,val samplesOpen:Boolean=false,
    val projectId:String?=null,val recoveryAvailable:Boolean=false,
    val bodeOpen:Boolean=false,val acSweep:AcSweep?=null,val acStartHz:Double=10.0,
    val acStopHz:Double=100000.0,val acInput:TerminalRef?=null,val acOutput:TerminalRef?=null,
    val recent: List<Kind> = listOf(Kind.BATTERY,Kind.RESISTOR,Kind.LED,Kind.SWITCH,Kind.LAMP),
    val favorites:Set<Kind> = emptySet(),
    val measurementsVisible:Boolean=false,val parameterAnalysisOpen:Boolean=false,
    val simulationSpeed:Double=1.0,
    val guide:GuideSession?=null,val guideResumeId:String?=null,val guideResumeStep:Int=0,
    val guideCompleted:Set<String> = emptySet(),val guideOnboardingSeen:Boolean=false,
    val intelligence:IntelligenceReport=IntelligenceReport(emptyList(),emptyList(),"",0),
    val issueHistory:List<IssueTransition> = emptyList(),
    val intelligenceMode:ExplanationMode=ExplanationMode.STANDARD,
    val liveWarnings:Boolean=true,val diagnosticWhy:Boolean=false,val highlightedIssueId:String?=null,
    val highlightedParts:Set<String> = emptySet(),val highlightedPins:Set<TerminalRef> = emptySet(),
    val highlightedWires:Set<String> = emptySet(),val intelligenceExplanation:String?=null,
    val firmware:FirmwareUiState=FirmwareUiState(),
    val logicAnalyzerOpen:Boolean=false,val benchAudioEnabled:Boolean=false
)

class SimulatorViewModel(app: Application) : AndroidViewModel(app) {
    private val solver = DcSolver()
    private val transientSolver = TransientSolver()
    private val digitalSimulator = CanvasDigitalSimulator(transientSolver)
    private val acSolver = AcSolver()
    private val fileName = "current_circuit.json"
    private val preferences=app.getSharedPreferences("simulator_ui",0)
    private val projectStore=ProjectStore(app)
    private val guideStore=GuideStore(app)
    private var preGuideCircuit:Circuit?=null
    private var preGuideProjectId:String?=null
    private var preGuideRecoveryAvailable:Boolean=false
    private val autosaveMutex=Mutex()
    private val benchAudio=NativeBenchAudio()
    fun toggleBenchAudio() {
        _state.value=_state.value.copy(benchAudioEnabled=!_state.value.benchAudioEnabled)
        if(!_state.value.benchAudioEnabled)benchAudio.close()
    }
    private val _state = MutableStateFlow(SimulatorState())
    val state = _state.asStateFlow()
    private val undo = ArrayDeque<Circuit>()
    private val redo = ArrayDeque<Circuit>()
    private var liveParameterOrigin:Circuit?=null
    private var solveVersion = 0
    private var liveCheckpoint:TransientCheckpoint?=null
    private var liveDigitalSession:CanvasDigitalSession?=null
    private var liveCircuit:Circuit?=null
    private var advanceInFlight=false
    private var firmwareSession:FirmwareSession?=null
    private val firmwareRuntime=RuntimeScheduler()
    private var firmwareAdvanceInFlight=false
    @Volatile private var foregroundActive=true
    private var firmwareDiagnosticCounter=0
    private val buttonPulses=mutableMapOf<String,Job>()
    private var canvasSize = Size.Zero

    init {
        val recentNames=preferences.getString("recent_kinds",null)
        if(recentNames!=null) _state.value=_state.value.copy(recent=recentNames.split(',').mapNotNull {
            runCatching { Kind.valueOf(it) }.getOrNull()
        }.take(6))
        _state.value=_state.value.copy(favorites=preferences.getString("favorite_kinds","")
            .orEmpty().split(',').mapNotNull { runCatching { Kind.valueOf(it) }.getOrNull() }.toSet())
        _state.value=_state.value.copy(simulationSpeed=preferences.getFloat("simulation_speed",1f)
            .toDouble().coerceIn(.25,4.0))
        _state.value=_state.value.copy(liveWarnings=preferences.getBoolean("intelligence_live",true),
            intelligenceMode=runCatching { ExplanationMode.valueOf(preferences.getString("intelligence_mode","STANDARD")!!) }
                .getOrDefault(ExplanationMode.STANDARD))
        try {
            val file = app.getFileStreamPath(fileName)
            if (file.exists()) _state.value = _state.value.copy(circuit = CircuitJson.decode(file.readText()))
        } catch (_: Exception) { _state.value = _state.value.copy(message = "This circuit could not be fully loaded.") }
        _state.value=_state.value.copy(recoveryAvailable=projectStore.recovery()!=null)
        val activeGuide=guideStore.load()?.first?.takeUnless { it.complete }
        _state.value=_state.value.copy(guideResumeId=activeGuide?.lessonId,guideResumeStep=activeGuide?.stepIndex ?: 0,
            guideCompleted=LessonCatalog.lessons.filter { guideStore.completion(it.id)>0 }.map { it.id }.toSet(),
            guideOnboardingSeen=guideStore.onboardingSeen())
        recalculate()
        viewModelScope.launch {
            var previous=SystemClock.elapsedRealtimeNanos()
            while(true) {
                delay(50)
                val now=SystemClock.elapsedRealtimeNanos()
                val delta=(now-previous)/1e9
                previous=now
                val current=_state.value
                if(!foregroundActive) continue
                if(current.firmware.running) advanceFirmware((delta*current.simulationSpeed*1_000_000).toLong())
                else if(current.running && current.transient?.error==null && liveCheckpoint!=null)
                    advanceLive(delta*current.simulationSpeed)
            }
        }
    }

    private fun edit(next: Circuit,preserveReactive: Boolean = false) {
        finishParameterPreview()
        val old = _state.value.circuit
        if (old == next) return
        val sameTopology=old.components.map { it.id to it.kind }==next.components.map { it.id to it.kind } &&
            old.wires==next.wires
        if(sameTopology) firmwareRuntime.sessions.values.forEach { runCatching { it.board.updateCircuit(next) }
            .onFailure { firmwareSession=null } }
        else firmwareSession=null
        if(firmwareSession==null)
            _state.value=_state.value.copy(firmware=_state.value.firmware.copy(running=false,compiled=false))
        undo.addLast(old); if (undo.size > 100) undo.removeFirst(); redo.clear()
        if(!sameTopology) { liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null }
        _state.value = retainValidProbes(_state.value.copy(circuit = next, message = null,acSweep=null,
            transient=if(sameTopology) _state.value.transient else null,
            elapsedSeconds=if(sameTopology) _state.value.elapsedSeconds else 0.0,
            highlightedIssueId=null,highlightedParts=emptySet(),highlightedPins=emptySet(),
            highlightedWires=emptySet()),next)
        if(_state.value.guide!=null) guideUpdate(GuideEvent(when {
            next.components.size>old.components.size -> GuideEventType.COMPONENT_PLACED
            next.components.size<old.components.size -> GuideEventType.COMPONENT_DELETED
            next.wires.size>old.wires.size -> GuideEventType.WIRE_CREATED
            next.wires.size<old.wires.size -> GuideEventType.WIRE_DELETED
            else -> GuideEventType.PROPERTY_CHANGED
        })) else autosave(next)
        recalculate(preserveReactive)
    }
    private fun retainValidProbes(state:SimulatorState,circuit:Circuit):SimulatorState {
        val parts=circuit.components.associateBy { it.id }
        val wires=circuit.wires.map { it.id }.toSet()
        fun valid(ref:TerminalRef?):TerminalRef?=ref?.takeIf {
            val part=parts[it.componentId]
            part!=null && it.index in 0 until part.terminalCount
        }
        return state.copy(scopeCh1=valid(state.scopeCh1),scopeCh2=valid(state.scopeCh2),
            scopeCh3=valid(state.scopeCh3),scopeCh4=valid(state.scopeCh4),
            meterPlus=valid(state.meterPlus),meterCommon=valid(state.meterCommon),
            meterCurrentWireId=state.meterCurrentWireId?.takeIf { it in wires },
            acInput=valid(state.acInput),acOutput=valid(state.acOutput))
    }
    private fun autosave(circuit:Circuit) { viewModelScope.launch(Dispatchers.IO) {
        autosaveMutex.withLock { projectStore.autosave(circuit) }
    } }
    private fun guideUpdate(event:GuideEvent,result:DcResult?=null) {
        val current=_state.value
        val session=current.guide ?: return
        val lesson=LessonCatalog.byId[session.lessonId] ?: return
        val updated=GuidedFlowEngine.reconcile(lesson,session,GuideSnapshot(current.circuit,result,
            current.running,current.meterMode,current.meterPlus,current.meterCommon,
            current.meterCurrentWireId,current.elapsedSeconds),event)
        _state.value=current.copy(guide=updated,guideResumeStep=updated.stepIndex,
            guideCompleted=if(updated.complete) current.guideCompleted+session.lessonId else current.guideCompleted)
        runCatching { guideStore.save(updated,current.circuit) }
        if(updated.complete && !session.complete) guideStore.markComplete(session.lessonId,updated.completedAtMillis!!)
    }
    fun startLesson(id:String,practice:Boolean=false) {
        val lesson=LessonCatalog.byId[id] ?: return
        if(!_state.value.guideOnboardingSeen) markGuideOnboardingSeen()
        if(_state.value.guide==null) { preGuideCircuit=_state.value.circuit;preGuideProjectId=_state.value.projectId
            preGuideRecoveryAvailable=_state.value.recoveryAvailable }
        val circuit=lesson.initialCircuit()
        val session=GuideSession(id,practice=practice)
        ++solveVersion;undo.clear();redo.clear();liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value=_state.value.copy(circuit=circuit,projectId=null,guide=session,running=false,recoveryAvailable=false,
            guideResumeId=id,guideResumeStep=0,selectedId=null,selectedWireId=null,placement=null,meterOpen=false,
            meterPlus=null,meterCommon=null,meterCurrentWireId=null,result=DcResult(),message=null)
        runCatching { guideStore.save(session,circuit) }
        recalculate()
    }
    fun resumeLesson():Boolean {
        val saved=guideStore.load() ?: return false
        if(saved.first.complete) return false
        if(_state.value.guide==null) { preGuideCircuit=_state.value.circuit;preGuideProjectId=_state.value.projectId
            preGuideRecoveryAvailable=_state.value.recoveryAvailable }
        ++solveVersion;undo.clear();redo.clear();liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value=_state.value.copy(circuit=saved.second,projectId=null,guide=saved.first,running=false,recoveryAvailable=false,
            guideResumeStep=saved.first.stepIndex,
            selectedId=null,selectedWireId=null,placement=null,meterOpen=false,
            meterPlus=null,meterCommon=null,meterCurrentWireId=null,result=DcResult(),message=null)
        recalculate();return true
    }
    fun exitGuide() {
        if(_state.value.guide==null) return
        val restored=preGuideCircuit ?: SampleCircuits.led()
        ++solveVersion;undo.clear();redo.clear();liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value=_state.value.copy(circuit=restored,projectId=preGuideProjectId,guide=null,
            recoveryAvailable=preGuideRecoveryAvailable,
            guideResumeId=guideStore.load()?.first?.takeUnless { it.complete }?.lessonId,
            selectedId=null,selectedWireId=null,placement=null,meterOpen=false,
            meterPlus=null,meterCommon=null,meterCurrentWireId=null,result=DcResult())
        preGuideCircuit=null;preGuideProjectId=null
        recalculate()
    }
    fun restartLesson() { _state.value.guide?.let { startLesson(it.lessonId,it.practice) } }
    fun guideHint() { val session=_state.value.guide ?: return
        val lesson=LessonCatalog.byId[session.lessonId] ?: return
        val updated=GuidedFlowEngine.hint(lesson,session)
        _state.value=_state.value.copy(guide=updated)
        runCatching { guideStore.save(updated,_state.value.circuit) }
    }
    fun guideAnswer(answer:String)=guideUpdate(GuideEvent(GuideEventType.QUESTION_ANSWERED,answer),_state.value.result)
    fun guideContinue()=guideUpdate(GuideEvent(GuideEventType.CONTINUED),_state.value.result)
    fun saveGuideCopy() {
        val session=_state.value.guide ?: return
        if(!session.complete) return
        val circuit=_state.value.circuit.copy(name=LessonCatalog.byId[session.lessonId]?.title ?: _state.value.circuit.name)
        runCatching { projectStore.save(null,circuit) }.onSuccess {
            _state.value=_state.value.copy(projects=projectStore.list(),message="Saved a copy to My Circuits.")
        }.onFailure { postMessage("Could not save the circuit copy.") }
    }
    fun markGuideOnboardingSeen() { guideStore.markOnboardingSeen();_state.value=_state.value.copy(guideOnboardingSeen=true) }
    private fun replaceDocument(circuit:Circuit,projectId:String?=null,message:String?=null) {
        finishParameterPreview()
        firmwareSession=null
        undo.clear();redo.clear()
        liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value=_state.value.copy(circuit=circuit,projectId=projectId,message=message,result=DcResult(),
            selectedId=null,selectedWireId=null,contextId=null,placement=null,
            projectsOpen=false,diagnosticsOpen=false,samplesOpen=false,scopeOpen=false,meterOpen=false,bodeOpen=false,
            parameterAnalysisOpen=false,
            scopeCh1=null,scopeCh2=null,scopeCh3=null,scopeCh4=null,
            meterPlus=null,meterCommon=null,meterCurrentWireId=null,
            acInput=null,acOutput=null,acSweep=null,transient=null,elapsedSeconds=0.0,
            intelligence=IntelligenceReport(emptyList(),emptyList(),"",0),issueHistory=emptyList(),highlightedIssueId=null,
            highlightedParts=emptySet(),highlightedPins=emptySet(),highlightedWires=emptySet(),
            intelligenceExplanation=null,firmware=FirmwareUiState())
        autosave(circuit)
        recalculate()
    }
    fun restoreSession() {
        val circuit=projectStore.recovery() ?: return
        replaceDocument(circuit,message="Working session restored.")
        _state.value=_state.value.copy(recoveryAvailable=false)
    }
    fun dismissRecovery() {
        projectStore.discardRecovery()
        _state.value=_state.value.copy(recoveryAvailable=false)
    }
    fun showProjects(open:Boolean) {
        _state.value=_state.value.copy(projectsOpen=open,samplesOpen=false,
            projects=if(open) projectStore.list() else _state.value.projects)
    }
    fun refreshProjects() { _state.value=_state.value.copy(projects=projectStore.list()) }
    fun showDiagnostics(open:Boolean,why:Boolean=false) {
        _state.value=_state.value.copy(diagnosticsOpen=open,diagnosticWhy=why,projectsOpen=false,samplesOpen=false,bodeOpen=false,
            scopeOpen=false,meterOpen=false,selectedId=null,selectedWireId=null)
        if(open) checkCircuit()
    }
    fun checkCircuit() {
        val snapshot=_state.value
        val version=solveVersion
        viewModelScope.launch {
            val report=withContext(Dispatchers.Default) { CircuitIntelligence.analyze(IntelligenceInput(
                snapshot.circuit,snapshot.result,snapshot.running,snapshot.meterMode,snapshot.meterPlus,
                snapshot.meterCommon,snapshot.meterCurrentWireId,
                listOfNotNull(snapshot.scopeCh1,snapshot.scopeCh2,snapshot.scopeCh3,snapshot.scopeCh4),
                snapshot.intelligenceMode,firmwareIntel(snapshot))) }
            if(version==solveVersion && _state.value.circuit==snapshot.circuit) updateIntelligence(report)
        }
    }
    private fun updateIntelligence(report:IntelligenceReport) {
        val prior=_state.value.intelligence.issues.associateBy { it.id }
        val currentIds=report.issues.map { it.id }.toSet()
        val resolved=prior.values.filter { it.id !in currentIds && it.ruleId !in setOf("SOLVER_FAILURE") }
            .map { IssueTransition(it,true,System.currentTimeMillis()) }
        _state.value=_state.value.copy(intelligence=report,
            issueHistory=(_state.value.issueHistory+resolved).takeLast(30))
    }
    fun setIntelligenceMode(mode:ExplanationMode) {
        _state.value=_state.value.copy(intelligenceMode=mode)
        preferences.edit().putString("intelligence_mode",mode.name).apply()
        checkCircuit()
    }
    fun setLiveWarnings(enabled:Boolean) {
        _state.value=_state.value.copy(liveWarnings=enabled)
        preferences.edit().putBoolean("intelligence_live",enabled).apply()
    }
    fun focusIssue(issue:IntelligenceIssue) {
        val current=_state.value.intelligence.issues.firstOrNull { it.id==issue.id } ?: return
        _state.value=_state.value.copy(highlightedIssueId=current.id,
            highlightedParts=current.componentIds,highlightedPins=current.pins,
            highlightedWires=current.wireIds,diagnosticsOpen=false,selectedId=null,selectedWireId=null)
    }
    fun clearIssueFocus() { _state.value=_state.value.copy(highlightedIssueId=null,
        highlightedParts=emptySet(),highlightedPins=emptySet(),highlightedWires=emptySet()) }
    fun explainPart(id:String) {
        val current=_state.value
        _state.value=current.copy(intelligenceExplanation=ExplanationGenerator.partRole(IntelligenceInput(
            current.circuit,current.result,current.running),id))
    }
    fun previewPartRemoval(id:String) {
        val current=_state.value;val version=solveVersion
        viewModelScope.launch {
            val preview=withContext(Dispatchers.Default) {
                ExplanationGenerator.removalPreview(IntelligenceInput(current.circuit,current.result),id) }
            if(version==solveVersion) _state.value=_state.value.copy(intelligenceExplanation=preview)
        }
    }
    fun clearIntelligenceExplanation() { _state.value=_state.value.copy(intelligenceExplanation=null) }
    fun applySmartFix(fix:SmartFix) {
        val before=_state.value.circuit
        val valid=CircuitIntelligence.analyze(IntelligenceInput(before,_state.value.result,_state.value.running)).issues
            .any { fix in it.fixes }
        if(!valid) { postMessage("That fix no longer matches the circuit. Check again.");return }
        val next=when(fix) {
            is SmartFix.RemoveWire -> before.copy(wires=before.wires.filterNot { it.id==fix.wireId })
            is SmartFix.ReverseLed -> {
                val links=before.wires.filter { it.start.componentId==fix.ledId || it.end.componentId==fix.ledId }
                val pins=links.groupBy { if(it.start.componentId==fix.ledId) it.start.index else it.end.index }
                if(pins[0]?.size!=1 || pins[1]?.size!=1) { postMessage("LED wiring changed; check again.");return }
                before.copy(wires=before.wires.map { wire -> if(wire !in links) wire else wire.copy(
                    start=if(wire.start.componentId==fix.ledId) wire.start.copy(index=1-wire.start.index) else wire.start,
                    end=if(wire.end.componentId==fix.ledId) wire.end.copy(index=1-wire.end.index) else wire.end) })
            }
            is SmartFix.AddGround -> {
                val source=before.components.firstOrNull { it.id==fix.sourceId } ?: return
                if(before.components.any { it.kind==Kind.GROUND }) return
                val ground=PlacedComponent(kind=Kind.GROUND,reference=before.nextReference(Kind.GROUND),
                    x=source.x+130f,y=source.y+190f)
                before.copy(components=before.components+ground,wires=before.wires+
                    Wire(start=TerminalRef(source.id,1),end=TerminalRef(ground.id,0)))
            }
            is SmartFix.SetValue -> {
                val part=before.components.firstOrNull { it.id==fix.componentId } ?: return
                val spec=ComponentRegistry.definitions.getValue(part.kind).parameters.firstOrNull { it.key==fix.key } ?: return
                if(fix.value !in spec.min..spec.max) return
                before.copy(components=before.components.map { if(it.id==part.id)
                    it.copy(parameters=it.parameters+(fix.key to fix.value)) else it })
            }
            is SmartFix.InsertLedResistor -> {
                val wire=before.wires.firstOrNull { it.id==fix.wireId && it.label==null } ?: return
                val a=before.components.firstOrNull { it.id==wire.start.componentId } ?: return
                val b=before.components.firstOrNull { it.id==wire.end.componentId } ?: return
                val middleX=(a.x+b.x)/2f;val middleY=(a.y+b.y)/2f
                val location=listOf(-120f,120f,-180f,180f,-240f,240f).map { middleX to (middleY+it) }
                    .firstOrNull { (x,y) -> x in 80f..920f && y in 100f..800f &&
                        before.components.all { part -> kotlin.math.hypot((part.x-x).toDouble(),
                            (part.y-y).toDouble())>125.0 } }
                    ?: run { postMessage("No clear space for a resistor near this wire. Place it manually.");return }
                val resistor=PlacedComponent(kind=Kind.RESISTOR,reference=before.nextReference(Kind.RESISTOR),
                    x=location.first,y=location.second,parameters=mapOf("resistance" to fix.ohms))
                before.copy(components=before.components+resistor,
                    wires=before.wires.filterNot { it.id==wire.id }+listOf(
                        Wire(start=wire.start,end=TerminalRef(resistor.id,0)),
                        Wire(start=TerminalRef(resistor.id,1),end=wire.end)))
            }
        }
        if(next==before) return
        edit(next)
        _state.value=_state.value.copy(issueHistory=(_state.value.issueHistory+
            IssueTransition(_state.value.intelligence.issues.firstOrNull { fix in it.fixes }
                ?: return,false,System.currentTimeMillis())).takeLast(30),
            message="Applied: ${fix.label}. Undo is available.")
    }
    fun showSamples(open:Boolean) {
        _state.value=_state.value.copy(samplesOpen=open,projectsOpen=false,diagnosticsOpen=false,
            bodeOpen=false,scopeOpen=false,meterOpen=false,selectedId=null,selectedWireId=null)
    }
    fun toggleMeasurements() {
        _state.value=_state.value.copy(measurementsVisible=!_state.value.measurementsVisible)
    }
    fun openDiagnosticComponent(id:String) {
        _state.value=_state.value.copy(diagnosticsOpen=false,selectedId=id,selectedWireId=null)
    }
    fun newCircuit() {
        if(_state.value.guide!=null) exitGuide()
        replaceDocument(Circuit("Untitled Circuit",emptyList(),emptyList()))
    }
    fun saveProject(name:String,asNew:Boolean=false) {
        if(_state.value.guide!=null) {
            if(_state.value.guide?.complete==true) saveGuideCopy() else postMessage("Finish the lesson to save a copy.")
            return
        }
        val trimmed=name.trim().take(80)
        if(trimmed.isEmpty()) { postMessage("Enter a project name before saving.");return }
        val circuit=_state.value.circuit.copy(name=trimmed,peripheralMemory=firmwareRuntime.fabric.persistentMemory()+_state.value.circuit.peripheralMemory.filterKeys {
            it !in firmwareRuntime.fabric.persistentMemory() && _state.value.circuit.components.any { p -> p.id==it } })
        val id=runCatching { projectStore.save(if(asNew) null else _state.value.projectId,circuit) }
            .getOrElse { postMessage("Project could not be saved: ${it.message ?: "Storage error"}");return }
        _state.value=_state.value.copy(circuit=circuit,projectId=id,projects=projectStore.list(),message="Project saved.")
        if(_state.value.guide==null) autosave(circuit)
    }
    fun openProject(id:String) {
        if(_state.value.guide!=null) exitGuide()
        val circuit=runCatching { projectStore.open(id) }.getOrElse {
            postMessage("Project could not be opened: ${it.message ?: "Invalid file"}")
            return
        }
        replaceDocument(circuit,projectId=id)
    }
    fun importCircuit(circuit:Circuit) {
        if(_state.value.guide!=null) exitGuide()
        replaceDocument(circuit,message="Project imported. Save it to keep a named copy.")
    }
    fun postMessage(message:String) { _state.value=_state.value.copy(message=message) }
    fun duplicateProject(id:String) {
        runCatching { projectStore.duplicate(id) }.onSuccess { showProjects(true) }
            .onFailure { postMessage("Project could not be copied: ${it.message ?: "Invalid file"}") }
    }
    fun deleteProject(id:String) {
        projectStore.delete(id)
        _state.value=_state.value.copy(projects=projectStore.list(),projectId=if(_state.value.projectId==id) null else _state.value.projectId)
    }
    private fun recalculate(preserveReactive: Boolean = false) {
        if(firmwareSession!=null) { ++solveVersion;advanceFirmware(0);return }
        val version = ++solveVersion
        val circuit = _state.value.circuit
        val priorTrace=_state.value.transient
        val priorCheckpoint=liveCheckpoint
        val capInitial=if(priorCheckpoint!=null || preserveReactive) circuit.components.filter { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC }
            .mapNotNull { p -> _state.value.result.readings[p.id]?.voltage?.let { p.id to it } }.toMap() else emptyMap()
        val indInitial=if(priorCheckpoint!=null || preserveReactive) circuit.components.filter { it.kind==Kind.INDUCTOR }
            .mapNotNull { p -> _state.value.result.readings[p.id]?.current?.let { p.id to it } }.toMap() else emptyMap()
        viewModelScope.launch {
            val hasDigital=circuit.components.any { it.kind.isDigital }
            val hasReactive=hasDigital || circuit.components.any { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC || it.kind==Kind.INDUCTOR || it.kind==Kind.FUNCTION_GENERATOR || it.kind==Kind.FUSE || it.kind==Kind.TRANSFORMER || it.kind==Kind.DC_MOTOR }
            val solved = withContext(Dispatchers.Default) {
                val dc=if(hasReactive) null else solver.solve(circuit)
                val needsStress=dc!=null && dc.error==null && circuit.components.any { p ->
                    p.kind==Kind.RESISTOR && dc.readings[p.id]?.health==com.indianservers.circuitssimulator.simulation.Health.OVERLOAD
                }
                if(hasReactive || needsStress) {
                    val step=liveStep(circuit)
                    val duration=step*2
                    val session=if(hasDigital) digitalSimulator.session(circuit,
                        priorCheckpoint?.timeSeconds ?: 0.0) else null
                    val slice=if(session!=null) session.advance(duration,step,priorCheckpoint,capInitial,indInitial)
                        else transientSolver.simulate(circuit,duration,step,capInitial,indInitial,
                            checkpoint=priorCheckpoint)
                    val trace=appendTransient(priorTrace,slice)
                    val result=if(slice.error==null) slice.frames.lastOrNull()?.asDcResult() ?: DcResult()
                        else DcResult(error=trace.error)
                    Triple(result,trace,session)
                } else Triple(dc!!,null,null)
            }
            if (version == solveVersion) {
                liveCheckpoint=solved.second?.checkpoint
                liveDigitalSession=solved.third
                liveCircuit=circuit
                firmwareRuntime.fabric.observe(circuit,solved.first,(liveCheckpoint?.timeSeconds?.times(1_000_000))?.toLong() ?: 0)
                _state.value = _state.value.copy(result = solved.first,
                    transient = solved.second,elapsedSeconds=liveCheckpoint?.timeSeconds ?: 0.0)
                val snapshot=_state.value
                val report=withContext(Dispatchers.Default) { CircuitIntelligence.analyze(IntelligenceInput(
                    circuit,solved.first,snapshot.running,snapshot.meterMode,snapshot.meterPlus,
                    snapshot.meterCommon,snapshot.meterCurrentWireId,
                    listOfNotNull(snapshot.scopeCh1,snapshot.scopeCh2,snapshot.scopeCh3,snapshot.scopeCh4),
                    snapshot.intelligenceMode)) }
                if(version==solveVersion && _state.value.circuit==circuit) updateIntelligence(report)
                if(_state.value.guide!=null) guideUpdate(GuideEvent(GuideEventType.SOLVER_UPDATED),solved.first)
            }
        }
    }

    private fun liveStep(circuit:Circuit):Double {
        val fastest=circuit.components.filter { it.kind==Kind.FUNCTION_GENERATOR || it.kind==Kind.CLOCK }
            .maxOfOrNull { it.value("frequency") } ?: 0.0
        return if(fastest>0.0) min(.002,1.0/(fastest*20.0)) else .002
    }

    private fun advanceLive(requestedSeconds:Double) {
        if(advanceInFlight) return
        val checkpoint=liveCheckpoint ?: return
        val circuit=_state.value.circuit
        if(liveCircuit?.copy(name=circuit.name)!=circuit) return
        val step=liveStep(circuit)
        val duration=requestedSeconds.coerceIn(step,step*400.0)
        val version=solveVersion
        val session=liveDigitalSession
        advanceInFlight=true
        viewModelScope.launch {
            try {
                val slice=withContext(Dispatchers.Default) {
                    runCatching {
                        if(session!=null) session.advance(duration,step,checkpoint)
                        else transientSolver.simulate(circuit,duration,step,checkpoint=checkpoint)
                    }.getOrElse { TransientResult(error=it.message ?: "Live simulation failed.") }
                }
                if(version==solveVersion && _state.value.circuit==circuit) {
                    val next=appendTransient(_state.value.transient,slice)
                    liveCheckpoint=slice.checkpoint
                    _state.value=_state.value.copy(transient=next,
                        elapsedSeconds=slice.checkpoint?.timeSeconds ?: _state.value.elapsedSeconds,
                        result=if(slice.error==null) slice.frames.lastOrNull()?.asDcResult() ?: _state.value.result
                            else DcResult(error=slice.error))
                    if(_state.value.benchAudioEnabled && foregroundActive)runCatching {
                        benchAudio.play(com.indianservers.circuitssimulator.simulation.BenchAudio.pcm(circuit,next.frames))
                    }
                }
            } finally { advanceInFlight=false }
        }
    }
    fun select(id: String?) { _state.value = _state.value.copy(selectedId = id,selectedWireId=null,contextId=null) }
    fun selectWire(id: String?) { _state.value = _state.value.copy(selectedId=null,selectedWireId=id,contextId=null) }
    fun showContext(id: String?) { _state.value = _state.value.copy(selectedId=id,selectedWireId=null,contextId=id) }
    fun dismissContext() { _state.value = _state.value.copy(contextId=null) }
    fun setPlacement(kind: Kind?) {
        _state.value = _state.value.copy(placement = kind, catalogOpen = false, selectedId = null,
            recent = if(kind==null) _state.value.recent else (listOf(kind)+_state.value.recent.filterNot { it==kind }).take(6))
        if(kind!=null) persistRecent()
    }
    fun showCatalog(open: Boolean) { _state.value = _state.value.copy(catalogOpen = open) }
    fun showBode(open:Boolean) {
        _state.value=_state.value.copy(bodeOpen=open,scopeOpen=false,meterOpen=false,samplesOpen=false,selectedId=null)
        if(open) runAcSweep()
    }
    fun showParameterAnalysis(open:Boolean) {
        _state.value=_state.value.copy(parameterAnalysisOpen=open,bodeOpen=false,scopeOpen=false,meterOpen=false,
            samplesOpen=false,diagnosticsOpen=false,projectsOpen=false,selectedId=null,selectedWireId=null)
    }
    fun setAcNodes(input:TerminalRef?,output:TerminalRef?) {
        _state.value=_state.value.copy(acInput=input,acOutput=output)
    }
    fun setAcRange(start:Double,stop:Double) {
        if(start.isFinite() && stop.isFinite() && start>0 && stop>=start)
            _state.value=_state.value.copy(acStartHz=start,acStopHz=stop)
    }
    fun runAcSweep() {
        val current=_state.value
        val version=solveVersion
        viewModelScope.launch {
            val sweep=withContext(Dispatchers.Default) {
                acSolver.sweep(current.circuit,current.acStartHz,current.acStopHz,121,SweepScale.LOGARITHMIC)
            }
            if(version==solveVersion) _state.value=_state.value.copy(acSweep=sweep)
        }
    }
    fun showScope(open:Boolean) { _state.value=_state.value.copy(scopeOpen=open,samplesOpen=false,meterOpen=if(open) false else _state.value.meterOpen,
        selectedId=null,selectedWireId=null) }
    fun showMeter(open:Boolean) { _state.value=_state.value.copy(meterOpen=open,samplesOpen=false,scopeOpen=if(open) false else _state.value.scopeOpen,
        selectedId=null,selectedWireId=null) }
    fun setMeterMode(mode:MeterMode) { _state.value=_state.value.copy(meterMode=mode)
        guideUpdate(GuideEvent(GuideEventType.METER_CHANGED),_state.value.result) }
    fun attachMeter(which:String,wireId:String) {
        val wire=_state.value.circuit.wires.firstOrNull { it.id==wireId } ?: return
        _state.value=when(which) {
            "plus" -> _state.value.copy(meterPlus=wire.start,selectedWireId=null,meterOpen=true)
            "common" -> _state.value.copy(meterCommon=wire.start,selectedWireId=null,meterOpen=true)
            else -> _state.value.copy(meterCurrentWireId=wireId,selectedWireId=null,meterOpen=true,
                meterMode=MeterMode.DC_CURRENT)
        }
        guideUpdate(GuideEvent(GuideEventType.PROBE_ATTACHED),_state.value.result)
    }
    fun attachScope(channel:Int,wireId:String) {
        val wire=_state.value.circuit.wires.firstOrNull { it.id==wireId } ?: return
        _state.value=when(channel) {
            1 -> _state.value.copy(scopeCh1=wire.start,selectedWireId=null,message="CH1 attached")
            2 -> _state.value.copy(scopeCh2=wire.start,selectedWireId=null,message="CH2 attached")
            3 -> _state.value.copy(scopeCh3=wire.start,selectedWireId=null,message="CH3 attached")
            4 -> _state.value.copy(scopeCh4=wire.start,selectedWireId=null,message="CH4 attached")
            else -> _state.value
        }
    }
    fun detachScope(channel:Int) {
        _state.value=when(channel) {
            1 -> _state.value.copy(scopeCh1=null)
            2 -> _state.value.copy(scopeCh2=null)
            3 -> _state.value.copy(scopeCh3=null)
            4 -> _state.value.copy(scopeCh4=null)
            else -> _state.value
        }
    }
    fun clearMessage(message:String) { if(_state.value.message==message) _state.value=_state.value.copy(message=null) }
    fun place(x: Float, y: Float) {
        val kind = _state.value.placement ?: return
        add(kind,x,y)
        _state.value = _state.value.copy(placement = null)
    }
    fun add(kind: Kind,x: Float,y: Float) {
        val old = _state.value.circuit
        val p = PlacedComponent(kind = kind, reference = old.nextReference(kind), x = x, y = y)
        edit(old.copy(components = old.components + p))
        _state.value = _state.value.copy(selectedId = p.id,selectedWireId=null,
            recent=(listOf(kind)+_state.value.recent.filterNot { it==kind }).take(6))
        persistRecent()
    }
    private fun persistRecent() { preferences.edit().putString("recent_kinds",_state.value.recent.joinToString(",") { it.name }).apply() }
    fun clearRecent() { _state.value=_state.value.copy(recent=emptyList());persistRecent() }
    fun toggleFavorite(kind:Kind) {
        val current=_state.value.favorites
        val next=if(kind in current) current-kind else current+kind
        _state.value=_state.value.copy(favorites=next)
        preferences.edit().putString("favorite_kinds",next.joinToString(",") { it.name }).apply()
    }
    fun duplicate(id: String) {
        val old=_state.value.circuit
        val source=old.components.firstOrNull { it.id==id } ?: return
        val copy=source.copy(id=java.util.UUID.randomUUID().toString(),reference=old.nextReference(source.kind),
            x=source.x+60f,y=source.y+60f)
        edit(old.copy(components=old.components+copy))
        select(copy.id)
    }
    fun disconnect(id: String) {
        val old=_state.value.circuit
        edit(old.copy(wires=old.wires.filterNot { it.start.componentId==id || it.end.componentId==id }))
        dismissContext()
    }
    fun removeWire(id: String) {
        val old=_state.value.circuit
        edit(old.copy(wires=old.wires.filterNot { it.id==id }))
        selectWire(null)
    }
    fun setWireLabel(id:String,text:String) {
        val label=text.trim().takeIf { it.isNotEmpty() }
        if(label!=null && !Regex("[A-Za-z][A-Za-z0-9_ -]{0,23}").matches(label)) {
            postMessage("Net labels must start with a letter and use at most 24 letters, digits, spaces, _ or -.")
            return
        }
        val circuit=_state.value.circuit
        if(circuit.wires.none { it.id==id }) return
        edit(circuit.copy(wires=circuit.wires.map { if(it.id==id) it.copy(label=label) else it }))
    }
    fun move(id: String, x: Float, y: Float) {
        val old = _state.value.circuit
        edit(old.copy(components = old.components.map { if (it.id == id) it.copy(x = x, y = y) else it }))
    }
    fun rotate(id: String) {
        val old = _state.value.circuit
        edit(old.copy(components = old.components.map { if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it }))
    }
    fun resize(id: String, sizeScale: Float) {
        if (!sizeScale.isFinite()) return
        val old=_state.value.circuit
        val checked=sizeScale.coerceIn(.6f,1.8f)
        edit(old.copy(components=old.components.map { if(it.id==id) it.copy(sizeScale=checked) else it }))
    }
    fun resetBoardConfiguration(id:String) {
        val old=_state.value.circuit
        val part=old.components.firstOrNull { it.id==id } ?: return
        if(!part.kind.isBoard) return
        val kept=part.parameters.filterKeys { it=="usbPower" }
        edit(old.copy(components=old.components.map { if(it.id==id) it.copy(parameters=kept) else it }))
        firmwareSession=null
    }
    fun remove(id: String) {
        val old = _state.value.circuit
        edit(old.copy(components = old.components.filterNot { it.id == id },
            wires = old.wires.filterNot { it.start.componentId == id || it.end.componentId == id }))
        select(null)
    }
    fun connect(a: TerminalRef, b: TerminalRef) {
        if (a == b || a.componentId == b.componentId) return
        val old = _state.value.circuit
        val parts=old.components.associateBy { it.id }
        if(a.index !in 0 until (parts[a.componentId]?.terminalCount ?: 0) ||
            b.index !in 0 until (parts[b.componentId]?.terminalCount ?: 0)) return
        if (old.wires.any { (it.start == a && it.end == b) || (it.start == b && it.end == a) }) return
        edit(old.copy(wires = old.wires + Wire(start = a, end = b)))
    }
    fun connectToJunction(start: TerminalRef, x: Float, y: Float, splitWireId: String? = null) {
        val old = _state.value.circuit
        if (old.components.none { it.id == start.componentId }) return
        val junction = PlacedComponent(kind=Kind.JUNCTION,reference=old.nextReference(Kind.JUNCTION),
            x=(x/10f).toInt()*10f,y=(y/10f).toInt()*10f)
        val terminal = TerminalRef(junction.id,0)
        val split = old.wires.firstOrNull { it.id == splitWireId }
        val wires = old.wires.filterNot { it.id == splitWireId } +
            (if (split==null) emptyList() else listOf(Wire(start=split.start,end=terminal,label=split.label),
                Wire(start=terminal,end=split.end,label=split.label))) +
            Wire(start=start,end=terminal)
        edit(old.copy(components=old.components+junction,wires=wires))
    }
    fun setParameter(id: String, key: String, value: Double) {
        val old = _state.value.circuit
        val p = old.components.firstOrNull { it.id == id } ?: return
        val spec = ComponentRegistry.definitions.getValue(p.kind).parameters.firstOrNull { it.key == key } ?: return
        if (!value.isFinite()) return
        val checked=value.coerceIn(spec.min,spec.max)
        if((p.kind==Kind.OPAMP || p.kind==Kind.IDEAL_OPAMP) &&
            ((key=="lowerRail" && checked>=p.value("upperRail")) ||
                (key=="upperRail" && checked<=p.value("lowerRail")))) return
        edit(old.copy(components = old.components.map {
            if (it.id == id) it.copy(parameters = it.parameters + (key to checked)) else it
        }))
    }
    fun previewPotentiometerPosition(id:String,position:Double) {
        if(!position.isFinite()) return
        val current=_state.value.circuit
        val part=current.components.firstOrNull { it.id==id && it.kind==Kind.POTENTIOMETER } ?: return
        val rounded=(kotlin.math.round(position.coerceIn(0.0,1.0)*100.0)/100.0)
        if(kotlin.math.abs(part.value("position")-rounded)<1e-9) return
        if(liveParameterOrigin==null) liveParameterOrigin=current
        _state.value=_state.value.copy(circuit=current.copy(components=current.components.map {
            if(it.id==id) it.copy(parameters=it.parameters+("position" to rounded)) else it
        }),acSweep=null)
        recalculate()
    }
    fun finishParameterPreview() {
        val origin=liveParameterOrigin ?: return
        liveParameterOrigin=null
        val current=_state.value.circuit
        if(origin!=current) {
            undo.addLast(origin)
            if(undo.size>100) undo.removeFirst()
            redo.clear()
            if(_state.value.guide!=null) guideUpdate(GuideEvent(GuideEventType.PROPERTY_CHANGED)) else autosave(current)
        }
    }
    fun toggleSwitch(id: String) {
        if(_state.value.circuit.components.any { it.id==id && it.kind in setOf(Kind.PUSH_BUTTON,Kind.NC_PUSH_BUTTON) }) {
            pulseButton(id);return
        }
        val old = _state.value.circuit
        edit(old.copy(components = old.components.map { if (it.id == id && it.kind in setOf(Kind.SWITCH,Kind.SPDT_SWITCH)) it.copy(closed = !it.closed) else it }),preserveReactive=true)
        guideUpdate(GuideEvent(GuideEventType.SWITCH_TOGGLED,id),_state.value.result)
    }
    fun toggleDipSwitch(id:String,channel:Int) {
        if(channel !in 1..4) return
        val part=_state.value.circuit.components.firstOrNull { it.id==id && it.kind==Kind.DIP_SWITCH_4 } ?: return
        val key="switch$channel"
        setParameter(id,key,if(part.value(key)>=.5) 0.0 else 1.0)
    }
    private fun pulseButton(id:String) {
        buttonPulses.remove(id)?.cancel()
        val current=_state.value.circuit
        val button=current.components.firstOrNull { it.id==id && it.kind in setOf(Kind.PUSH_BUTTON,Kind.NC_PUSH_BUTTON) } ?: return
        _state.value=_state.value.copy(circuit=current.copy(components=current.components.map {
            if(it.id==id) it.copy(closed=button.kind==Kind.PUSH_BUTTON) else it }))
        recalculate(preserveReactive=true)
        buttonPulses[id]=viewModelScope.launch {
            delay(250)
            val latest=_state.value.circuit
            if(latest.components.any { it.id==id && it.kind in setOf(Kind.PUSH_BUTTON,Kind.NC_PUSH_BUTTON) }) {
                _state.value=_state.value.copy(circuit=latest.copy(components=latest.components.map {
                    if(it.id==id) it.copy(closed=button.kind==Kind.NC_PUSH_BUTTON) else it }))
                recalculate(preserveReactive=true)
            }
            buttonPulses.remove(id)
        }
    }
    fun showFirmware(open:Boolean,boardId:String?=null) {
        val board=_state.value.circuit.components.firstOrNull { it.kind.isBoard && it.id==(boardId ?: _state.value.firmware.boardId) }
            ?: _state.value.circuit.components.firstOrNull { it.kind.isBoard }
        if(open && board==null) { postMessage("Place a supported board on the circuit first.");return }
        if(open && board!=null && _state.value.firmware.boardId!=board.id) {
            firmwareSession=firmwareRuntime.sessions[board.id]
            val attached=_state.value.circuit.firmware.firstOrNull { it.boardId==board.id }
            val language=attached?.language ?: BoardSupport.defaultLanguage(board.kind)
            val key="firmware_${board.kind.name}_${_state.value.circuit.name}"
            val source=attached?.source ?: preferences.getString(key,null) ?: FirmwareExamples.starter(board.kind,
                _state.value.circuit.name,language)
            _state.value=_state.value.copy(firmware=FirmwareUiState(open=true,source=source,boardId=board.id,
                language=language,compiled=firmwareSession!=null,pins=firmwareSession?.board?.latestPins.orEmpty(),
                boardPins=_state.value.firmware.boardPins))
        } else _state.value=_state.value.copy(firmware=_state.value.firmware.copy(open=open))
    }
    fun setFirmwareLanguage(language:FirmwareLanguage) {
        val board=_state.value.circuit.components.firstOrNull { it.id==_state.value.firmware.boardId } ?: return
        if(language !in BoardSupport.languages(board.kind)) return
        firmwareSession=null
        _state.value=_state.value.copy(firmware=_state.value.firmware.copy(language=language,compiled=false,
            source=FirmwareExamples.starter(board.kind,_state.value.circuit.name,language)))
        persistFirmwareSource(_state.value.firmware.source)
    }
    fun loadFirmwareExample(source:String,language:FirmwareLanguage) {
        firmwareSession=null
        _state.value=_state.value.copy(firmware=_state.value.firmware.copy(source=source,language=language,
            compiled=false,running=false,error=null))
        persistFirmwareSource(source)
    }
    fun setFirmwareSource(source:String) {
        if(source.length>20000) return
        firmwareSession=null
        persistFirmwareSource(source)
        val current=_state.value
        _state.value=current.copy(firmware=current.firmware.copy(source=source,compiled=false,running=false,
            error=null,timeMicros=0,console=emptyList(),pins=emptyMap()))
    }
    private fun persistFirmwareSource(source:String) {
        val current=_state.value
        val boardId=current.firmware.boardId ?: return
        val board=current.circuit.components.firstOrNull { it.id==boardId } ?: return
        preferences.edit().putString("firmware_${board.kind.name}_${current.circuit.name}",source).apply()
        val attachment=FirmwareAttachment(boardId,current.firmware.language,source,board.value("usbPower")>=.5,
            BoardRegistry.boards.getValue(board.kind).hardware.profileId)
        val firmware=current.circuit.firmware.filterNot { it.boardId==boardId }+attachment
        _state.value=current.copy(circuit=current.circuit.copy(firmware=firmware))
    }
    fun highlightBoardCapability(partId:String,capability:PinCapability?) {
        val board=_state.value.circuit.components.firstOrNull { it.id==partId } ?: return
        val definition=BoardRegistry.boards[board.kind] ?: return
        _state.value=_state.value.copy(highlightedPins=if(capability==null) emptySet() else
            definition.pins.mapIndexedNotNull { i,pin ->
                if(capability in pin.capabilities) TerminalRef(board.id,i) else null }.toSet())
    }
    fun highlightFirmwarePin(pin:String?,boardId:String?=null) {
        _state.value=_state.value.copy(firmware=_state.value.firmware.copy(highlightedPin=pin),
            highlightedPins=pin?.let { name ->
                val board=_state.value.circuit.components.firstOrNull { it.id==(boardId ?: _state.value.firmware.boardId) }
                    ?: return@let emptySet()
                val index=BoardRegistry.boards.getValue(board.kind).index(name)
                if(index>=0) setOf(TerminalRef(board.id,index)) else emptySet()
            } ?: emptySet())
    }
    fun describeBoardPin(ref:TerminalRef) {
        val part=_state.value.circuit.components.firstOrNull { it.id==ref.componentId && it.kind.isBoard } ?: return
        val board=BoardRegistry.boards.getValue(part.kind)
        val pin=board.pins.getOrNull(ref.index) ?: return
        val reading=_state.value.firmware.boardPins[part.id]?.get(pin.name) ?:
            if(_state.value.firmware.boardId==part.id) _state.value.firmware.pins[pin.name] else null
        val volts=_state.value.result.nodeVoltages[ref]?.let { "%.2f V".format(it) } ?: "not measured"
        postMessage("${part.reference} · ${board.displayName(pin.name)} · $volts · "+
            (reading?.let { "${it.mode} ${it.level} ${if(it.damaged) "DAMAGED" else ""}" } ?: pin.functions.joinToString(" / "))+
            (pin.maxVoltage?.let { " · input limit $it V" } ?: ""))
    }
    fun setUsbPower(id:String,enabled:Boolean) {
        val current=_state.value.circuit
        edit(current.copy(components=current.components.map { if(it.id==id) it.copy(parameters=it.parameters+("usbPower" to if(enabled) 1.0 else 0.0)) else it },
            firmware=current.firmware.map { if(it.boardId==id) it.copy(usbPower=enabled) else it }))
        firmwareSession=null
        ++solveVersion
        _state.value=_state.value.copy(firmware=_state.value.firmware.copy(running=false,compiled=false))
        recalculate()
    }
    fun setEnvironment(update:EnvironmentState) {
        _state.value=_state.value.copy(circuit=_state.value.circuit.copy(environment=update))
        firmwareSession?.environment=update
        firmwareRuntime.sessions.values.forEach { it.environment=update }
        firmwareRuntime.sessions.values.forEach { it.board.updateCircuit(_state.value.circuit) }
        recalculate(preserveReactive=true)
    }
    fun enqueueSerial(text:String,port:String="Serial",baud:Int=9600) {
        val boardId=_state.value.firmware.boardId ?: return
        val id=if(port=="Serial") boardId else "$boardId:$port"
        val endpoint=firmwareRuntime.fabric.uart[id]
        if(endpoint==null) { postMessage("Initialize $port in your firmware before sending.");return }
        if(endpoint.baud!=baud) { postMessage("Monitor baud $baud does not match $port ${endpoint.baud}.");return }
        runCatching { firmwareRuntime.fabric.monitorSend(_state.value.circuit,boardId,port,baud,text) }
            .onFailure { postMessage(it.message ?: "Monitor is unavailable") }
    }
    fun setSerialPaused(paused:Boolean) {
        _state.value=_state.value.copy(firmware=_state.value.firmware.copy(serialPaused=paused))
    }
    fun showLogicAnalyzer(open:Boolean) {
        _state.value=_state.value.copy(logicAnalyzerOpen=open,firmware=_state.value.firmware.copy(analyzerOpen=open,
            open=if(open) false else _state.value.firmware.open),scopeOpen=false,meterOpen=false)
    }
    fun compileFirmware():Boolean {
        val current=_state.value
        val boardId=current.firmware.boardId ?: return false
        return try {
            persistFirmwareSource(current.firmware.source)
            val circuit=_state.value.circuit
            val usbOn=circuit.components.firstOrNull { it.id==boardId }?.value("usbPower")?.let { it>=.5 } ?: false
            firmwareRuntime.compile(if(circuit.firmware.any { it.boardId==boardId }) circuit
                else circuit.copy(firmware=circuit.firmware+FirmwareAttachment(boardId,current.firmware.language,
                    current.firmware.source,usbOn)))
            if(firmwareRuntime.sessions.isEmpty()) {
                val board=FirmwareBoard(circuit,boardId)
                val program=if(current.firmware.language==FirmwareLanguage.MICROPYTHON_SUBSET)
                    MicroPythonSubset.compile(current.firmware.source,board)
                else ArduinoSubset.compile(current.firmware.source,board)
                val session=FirmwareSession(program,board,firmwareRuntime.fabric,circuit.environment,
                    firmwareRuntime.peripherals)
                firmwareRuntime.attach(session)
            }
            firmwareSession=firmwareRuntime.sessions[boardId] ?: firmwareRuntime.sessions.values.first()
            ++solveVersion;liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
            val latest=_state.value
            _state.value=latest.copy(running=false,transient=null,
                firmware=latest.firmware.copy(compiled=true,running=false,error=null,
                    timeMicros=0,currentLine=0,console=emptyList(),pins=emptyMap(),variables=emptyMap()))
            true
        } catch(error:Exception) {
            firmwareSession=null
            _state.value=current.copy(firmware=current.firmware.copy(compiled=false,running=false,
                error=FirmwareDiagnostic(error.message?.substringAfter("Line ")?.substringBefore(":")?.toIntOrNull() ?: 0,
                    error.message ?: "Firmware could not compile.")))
            false
        }
    }
    fun runFirmware() {
        if((firmwareSession==null || _state.value.firmware.error!=null) && !compileFirmware()) return
        _state.value=_state.value.copy(running=false,
            firmware=_state.value.firmware.copy(running=true,error=null))
        advanceFirmware(0)
    }
    fun pauseFirmware() { _state.value=_state.value.copy(firmware=_state.value.firmware.copy(running=false));benchAudio.close() }
    fun stopFirmware() {
        benchAudio.close()
        firmwareRuntime.stop();firmwareSession?.stop();firmwareSession=null
        ++solveVersion
        val idle=solver.solve(_state.value.circuit)
        _state.value=_state.value.copy(result=idle,
            firmware=_state.value.firmware.copy(running=false,compiled=false,pins=emptyMap()))
        recalculate()
    }
    fun resetFirmware(boardId:String?=null) {
        val session=boardId?.let(firmwareRuntime.sessions::get) ?: firmwareSession ?: if(compileFirmware()) firmwareSession else null
        firmwareRuntime.resetBoard(session?.board?.boardId)
        ++solveVersion
        recalculate()
        _state.value=_state.value.copy(transient=null,elapsedSeconds=(session?.timeMicros ?: 0)/1_000_000.0,
            firmware=_state.value.firmware.copy(running=false,error=null,timeMicros=session?.timeMicros ?: 0,
                currentLine=0,console=emptyList(),pins=emptyMap(),variables=emptyMap(),
                boardPins=_state.value.firmware.boardPins-filterNotNullBoardId(session?.board?.boardId)))
    }
    fun setForeground(active:Boolean) { foregroundActive=active;if(!active)benchAudio.close() }
    private fun filterNotNullBoardId(id:String?)=listOfNotNull(id).toSet()
    fun stepFirmware() {
        if(firmwareSession==null && !compileFirmware()) return
        advanceFirmware(1000)
    }
    fun clearFirmwareConsole() {
        firmwareSession?.clearConsole()
        _state.value=_state.value.copy(firmware=_state.value.firmware.copy(console=emptyList()))
    }
    private fun firmwareIntel(state:SimulatorState):FirmwareIntel {
        val usages=state.circuit.firmware.associate { item ->
            val board=state.circuit.components.firstOrNull { it.id==item.boardId }
            val parsed=runCatching {
                val fb=FirmwareBoard(state.circuit,item.boardId)
                val program=if(item.language==FirmwareLanguage.MICROPYTHON_SUBSET)
                    MicroPythonSubset.compile(item.source,fb) else ArduinoSubset.compile(item.source,fb)
                SourceSymbols.extract(program,fb)
            }.getOrDefault(emptyList())
            item.boardId to parsed
        }
        return FirmwareIntel(
            sources=state.circuit.firmware.associate { it.boardId to it.source }+
                (state.firmware.boardId?.let { mapOf(it to state.firmware.source) } ?: emptyMap()),
            usages=usages,
            pins=state.firmware.boardPins+mapOfNotNull(state.firmware.boardId,state.firmware.pins),
            missingI2cPullups=!firmwareRuntime.fabric.hasI2cPullups(state.circuit) &&
                state.circuit.components.any { it.kind in setOf(Kind.I2C_TEMP_SENSOR,Kind.I2C_LCD,Kind.OLED_SSD1306,Kind.I2C_EEPROM) },
            duplicateAddresses=firmwareRuntime.fabric.duplicateAddresses())
    }
    private fun mapOfNotNull(id:String?,pins:Map<String,GpioReading>)=
        if(id==null || pins.isEmpty()) emptyMap() else mapOf(id to pins)
    private fun advanceFirmware(deltaMicros:Long) {
        val session=firmwareSession ?: return
        if(firmwareAdvanceInFlight) return
        firmwareAdvanceInFlight=true
        val version=solveVersion
        viewModelScope.launch {
            try {
                val next=withContext(Dispatchers.Default) {
                    if(firmwareRuntime.sessions.size>1) firmwareRuntime.advance(deltaMicros.coerceIn(0,10_000_000))
                    else {
                        val single=session.advance(deltaMicros.coerceIn(0,10_000_000))
                        CombinedAdvance(single.frames,mapOf(session.board.boardId to single.console),
                            single.error,single.timeMicros,mapOf(session.board.boardId to single.currentLine),
                            mapOf(session.board.boardId to single.variables),
                            mapOf(session.board.boardId to (single.frames.lastOrNull()?.pins ?: emptyMap())),
                            firmwareRuntime.fabric.log.toList(),firmwareRuntime.fabric.displayText())
                    }
                }
                if(version!=solveVersion || firmwareSession!==session) return@launch
                val prior=_state.value
                val frames=(prior.transient?.frames.orEmpty()+next.frames.map { frame ->
                    TransientFrame(frame.timeMicros/1_000_000.0,frame.result.nodeVoltages,
                        frame.result.readings,frame.result.digitalStates) }).takeLast(4000)
                val latest=next.frames.lastOrNull()
                if(prior.benchAudioEnabled && foregroundActive)runCatching {
                    benchAudio.play(com.indianservers.circuitssimulator.simulation.BenchAudio.pcm(prior.circuit,frames))
                }
                val boardId=prior.firmware.boardId
                _state.value=prior.copy(circuit=prior.circuit.copy(peripheralMemory=firmwareRuntime.fabric.persistentMemory()),result=latest?.result ?: prior.result,
                    transient=TransientResult(frames=frames),elapsedSeconds=next.timeMicros/1_000_000.0,
                    firmware=prior.firmware.copy(running=prior.firmware.running && next.error==null,
                        error=next.error,timeMicros=next.timeMicros,
                        currentLine=next.currentLines[boardId] ?: prior.firmware.currentLine,
                        console=if(prior.firmware.serialPaused) prior.firmware.console
                            else next.console[boardId] ?: next.console.values.firstOrNull().orEmpty(),
                        pins=next.pins[boardId] ?: latest?.pins ?: prior.firmware.pins,
                        boardPins=next.pins,
                        boardRuntime=firmwareRuntime.sessions.mapValues { (_,runtime) ->
                            "${runtime.board.powerState} · line ${runtime.currentLine}"+
                                (runtime.board.powerMessage?.let { " · $it" } ?: "") },
                        variables=next.variables[boardId] ?: prior.firmware.variables,
                        busLog=next.busLog.takeLast(80).map { "${it.timeMicros} ${it.kind} ${it.summary}" },
                        displays=next.displays,framebuffers=firmwareRuntime.fabric.framebuffers(),
                        peripheralInspectors=firmwareRuntime.fabric.inspectors()))
                if(latest!=null && (++firmwareDiagnosticCounter%10==0 || next.error!=null)) checkCircuit()
            } finally { firmwareAdvanceInFlight=false }
        }
    }
    fun toggleRun() { if(_state.value.firmware.running) { pauseFirmware();benchAudio.close();return }
        if(firmwareSession!=null && _state.value.firmware.compiled) { runFirmware();return }
        _state.value = _state.value.copy(running = !_state.value.running)
        guideUpdate(GuideEvent(if(_state.value.running) GuideEventType.SIMULATION_STARTED else GuideEventType.SIMULATION_STOPPED),_state.value.result) }
    fun setSimulationSpeed(speed:Double) {
        if(speed !in listOf(.25,1.0,4.0)) return
        _state.value=_state.value.copy(simulationSpeed=speed)
        preferences.edit().putFloat("simulation_speed",speed.toFloat()).apply()
    }
    fun sendTerminal(id:String,text:String) {
        runCatching { firmwareRuntime.fabric.terminalSend(_state.value.circuit,id,text) }
            .onFailure { postMessage(it.message ?: "Terminal is unavailable") }
    }
    fun setEmbeddedMode(advanced:Boolean) {
        val circuit=_state.value.circuit.copy(settings=_state.value.circuit.settings.copy(advancedEmbedded=advanced))
        _state.value=_state.value.copy(circuit=circuit);autosave(circuit)
    }
    fun scanI2cBus() {
        val session=firmwareSession ?: run { postMessage("Compile firmware and initialize I²C first.");return }
        if(session.board.boardId !in firmwareRuntime.fabric.i2cRoutes) { postMessage("Initialize I²C in firmware first.");return }
        val found=firmwareRuntime.fabric.i2cScan(_state.value.circuit,session.board.boardId)
        postMessage("Scanning I²C: "+found.joinToString { "0x${it.toString(16)}" }+" · ${found.size} devices found")
    }
    fun guidePeripheralWiring(id:String) {
        val part=_state.value.circuit.components.firstOrNull { it.id==id } ?: return
        val board=_state.value.circuit.components.firstOrNull { it.kind.isBoard }?.let { BoardRegistry.boards[it.kind] }
        val hint=when(part.kind) {
            Kind.I2C_TEMP_SENSOR,Kind.I2C_EEPROM,Kind.I2C_LCD,Kind.OLED_SSD1306 ->
                "SDA → ${board?.hardware?.i2cPins?.first ?: "board SDA"}; SCL → ${board?.hardware?.i2cPins?.second ?: "board SCL"}. Connect compatible VCC and common GND; check pull-ups and address."
            Kind.SPI_MEMORY -> "MOSI/MISO/SCK → ${board?.hardware?.spiPins?.take(3)?.joinToString() ?: "board SPI"}; wire a separate GPIO to CS. Use 3.3 V power and compatible logic."
            Kind.SERIAL_TERMINAL -> "Terminal TX → board RX; terminal RX ← board TX; connect common GND. Match baud/framing and logic voltage."
            Kind.ULTRASONIC -> "Use 5 V VCC and common GND; drive TRIG for ≥10 µs. Measure ECHO width; level-shift its 5 V output for 3.3 V boards."
            else -> "Connect VCC and common GND. Wire OUT to a compatible board input; wait for PIR warm-up."
        }
        postMessage(hint)
    }
    fun clearTerminal(id:String) { firmwareRuntime.fabric.clearTerminal(id) }
    fun inspectMemory(id:String,address:Int,count:Int=64):List<Int> {
        val memory=firmwareRuntime.fabric.i2cDevices.firstOrNull { it.ownerId==id }?.memory
            ?: firmwareRuntime.fabric.spiDevices.firstOrNull { it.ownerId==id }?.memory
            ?: _state.value.circuit.peripheralMemory[id].orEmpty()
        return List(count.coerceIn(1,256)) { memory[address+it] ?: 255 }
    }
    fun editPeripheralMemory(id:String,address:Int,value:Int) {
        val part=_state.value.circuit.components.firstOrNull { it.id==id } ?: return
        val limit=when(part.kind) { Kind.I2C_EEPROM -> 256;Kind.SPI_MEMORY -> 4194304;else -> return }
        if(address !in 0 until limit || value !in 0..255) { postMessage("Memory address or byte is outside the device range.");return }
        val modelMemory=firmwareRuntime.fabric.i2cDevices.firstOrNull { it.ownerId==id }?.memory
            ?: firmwareRuntime.fabric.spiDevices.firstOrNull { it.ownerId==id }?.memory
        val current=modelMemory ?: _state.value.circuit.peripheralMemory[id].orEmpty()
        if(address !in current && current.size>=65536) { postMessage("Flash storage budget reached; erase data before importing more bytes.");return }
        modelMemory?.set(address,value)
        val stored=_state.value.circuit.peripheralMemory
        val updated=_state.value.circuit.copy(peripheralMemory=stored+(id to (stored[id].orEmpty()+(address to value))))
        _state.value=_state.value.copy(circuit=updated,firmware=_state.value.firmware.copy(peripheralInspectors=firmwareRuntime.fabric.inspectors()))
        autosave(updated)
    }
    fun resetTime() {
        ++solveVersion
        liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value=_state.value.copy(elapsedSeconds=0.0,transient=null)
        recalculate()
    }
    fun zoom(delta: Float) { _state.value = _state.value.copy(zoom = (_state.value.zoom * delta).coerceIn(.5f, 3f)) }
    fun transformGesture(factor:Float,previousCentroid:Offset,currentCentroid:Offset,canvas:Size) {
        if(canvas.width<=0f || canvas.height<=0f) return
        val old=_state.value
        val baseScale=min(canvas.width/1000f,canvas.height/900f)
        val oldScale=baseScale*old.zoom
        val newZoom=(old.zoom*factor).coerceIn(.5f,3f)
        val newScale=baseScale*newZoom
        fun base(scale:Float)=Offset((canvas.width-1000f*scale)/2f,
            (canvas.height-900f*scale).coerceAtLeast(0f)*.5f)
        val oldOrigin=base(oldScale)+Offset(old.panX,old.panY)
        val world=(previousCentroid-oldOrigin)/oldScale.coerceAtLeast(.001f)
        val desired=currentCentroid-world*newScale
        val pan=desired-base(newScale)
        _state.value=old.copy(zoom=newZoom,panX=pan.x,panY=pan.y)
    }
    fun pan(dx: Float, dy: Float) { _state.value = _state.value.copy(panX = _state.value.panX + dx, panY = _state.value.panY + dy) }
    fun resetView() { _state.value = _state.value.copy(zoom = 1f, panX = 0f, panY = 0f) }
    fun setCanvasSize(size:Size) { canvasSize=size }
    fun fitCircuitView() {
        val size=canvasSize
        val parts=_state.value.circuit.components
        if(size.width<=0f || size.height<=0f || parts.isEmpty()) { resetView();return }
        val minX=parts.minOf { it.x }-100f
        val maxX=parts.maxOf { it.x }+100f
        val minY=parts.minOf { it.y }-100f
        val maxY=parts.maxOf { it.y }+100f
        val baseScale=min(size.width/1000f,size.height/900f)
        val desiredScale=min((size.width-48f).coerceAtLeast(1f)/(maxX-minX),
            (size.height-48f).coerceAtLeast(1f)/(maxY-minY))
        val zoom=(desiredScale/baseScale).coerceIn(.5f,3f)
        val scale=baseScale*zoom
        val base=Offset((size.width-1000f*scale)/2f,
            (size.height-900f*scale).coerceAtLeast(0f)*.5f)
        val centered=Offset(size.width/2f-(minX+maxX)*scale/2f,
            size.height/2f-(minY+maxY)*scale/2f)
        _state.value=_state.value.copy(zoom=zoom,panX=centered.x-base.x,panY=centered.y-base.y)
    }
    fun undo() {
        if (undo.isEmpty()) return
        redo.addLast(_state.value.circuit)
        val circuit=undo.removeLast()
        liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value = retainValidProbes(_state.value.copy(circuit = circuit, selectedId = null,
            transient=null,elapsedSeconds=0.0),circuit)
        if(_state.value.guide!=null) guideUpdate(GuideEvent(GuideEventType.WIRE_DELETED)) else autosave(_state.value.circuit)
        recalculate()
    }
    fun redo() {
        if (redo.isEmpty()) return
        undo.addLast(_state.value.circuit)
        val circuit=redo.removeLast()
        liveCheckpoint=null;liveDigitalSession=null;liveCircuit=null
        _state.value = retainValidProbes(_state.value.copy(circuit = circuit, selectedId = null,
            transient=null,elapsedSeconds=0.0),circuit)
        if(_state.value.guide!=null) guideUpdate(GuideEvent(GuideEventType.WIRE_CREATED)) else autosave(_state.value.circuit)
        recalculate()
    }
    fun openRunnableProject(circuit:Circuit,run:Boolean=false) {
        loadSample(circuit)
        val attachment=circuit.firmware.firstOrNull()
        if(attachment==null) { postMessage("This project has no executable code.");return }
        showFirmware(true,attachment.boardId)
        if(run) runFirmware()
    }
    fun loadSample(circuit: Circuit) { if(_state.value.guide!=null) exitGuide();replaceDocument(circuit) }
    override fun onCleared() { benchAudio.close();super.onCleared() }
}
