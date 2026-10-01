package com.indianservers.circuitssimulator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.data.ProjectStore
import com.indianservers.circuitssimulator.data.SavedProject
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.simulation.TransientResult
import com.indianservers.circuitssimulator.simulation.TransientSolver
import com.indianservers.circuitssimulator.simulation.MeterMode
import com.indianservers.circuitssimulator.simulation.AcSolver
import com.indianservers.circuitssimulator.simulation.AcSweep
import com.indianservers.circuitssimulator.simulation.SweepScale
import android.os.SystemClock
import kotlinx.coroutines.delay
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

data class SimulatorState(
    val circuit: Circuit = SampleCircuits.led(), val result: DcResult = DcResult(),
    val selectedId: String? = null, val selectedWireId: String? = null,
    val contextId: String? = null, val placement: Kind? = null, val catalogOpen: Boolean = false,
    val running: Boolean = true, val message: String? = null, val zoom: Float = 1f,
    val panX: Float = 0f, val panY: Float = 0f,
    val transient: TransientResult? = null, val elapsedSeconds: Double = 0.0,
    val scopeOpen:Boolean=false, val scopeCh1:TerminalRef?=null, val scopeCh2:TerminalRef?=null,
    val meterOpen:Boolean=false,val meterMode:MeterMode=MeterMode.DC_VOLTAGE,
    val meterPlus:TerminalRef?=null,val meterCommon:TerminalRef?=null,val meterCurrentWireId:String?=null,
    val projectsOpen:Boolean=false,val projects:List<SavedProject> = emptyList(),
    val diagnosticsOpen:Boolean=false,val samplesOpen:Boolean=false,
    val projectId:String?=null,val recoveryAvailable:Boolean=false,
    val bodeOpen:Boolean=false,val acSweep:AcSweep?=null,val acStartHz:Double=10.0,
    val acStopHz:Double=100000.0,val acInput:TerminalRef?=null,val acOutput:TerminalRef?=null,
    val recent: List<Kind> = listOf(Kind.BATTERY,Kind.RESISTOR,Kind.LED,Kind.SWITCH,Kind.LAMP),
    val measurementsVisible:Boolean=false,val parameterAnalysisOpen:Boolean=false
)

class SimulatorViewModel(app: Application) : AndroidViewModel(app) {
    private val solver = DcSolver()
    private val transientSolver = TransientSolver()
    private val acSolver = AcSolver()
    private val fileName = "current_circuit.json"
    private val preferences=app.getSharedPreferences("simulator_ui",0)
    private val projectStore=ProjectStore(app)
    private val autosaveMutex=Mutex()
    private val _state = MutableStateFlow(SimulatorState())
    val state = _state.asStateFlow()
    private val undo = ArrayDeque<Circuit>()
    private val redo = ArrayDeque<Circuit>()
    private var liveParameterOrigin:Circuit?=null
    private var solveVersion = 0
    private var canvasSize = Size.Zero

    init {
        val recentNames=preferences.getString("recent_kinds",null)
        if(recentNames!=null) _state.value=_state.value.copy(recent=recentNames.split(',').mapNotNull {
            runCatching { Kind.valueOf(it) }.getOrNull()
        }.take(6))
        try {
            val file = app.getFileStreamPath(fileName)
            if (file.exists()) _state.value = _state.value.copy(circuit = CircuitJson.decode(file.readText()))
        } catch (_: Exception) { _state.value = _state.value.copy(message = "Saved circuit could not be opened.") }
        _state.value=_state.value.copy(recoveryAvailable=projectStore.recovery()!=null)
        recalculate()
        viewModelScope.launch {
            var previous=SystemClock.elapsedRealtimeNanos()
            while(true) {
                delay(50)
                val now=SystemClock.elapsedRealtimeNanos()
                val delta=(now-previous)/1e9
                previous=now
                val current=_state.value
                val transient=current.transient
                if(current.running && transient?.frames?.isNotEmpty()==true) {
                    val end=transient.frames.last().timeSeconds
                    val periodic=current.circuit.components.any { it.kind==Kind.FUNCTION_GENERATOR } &&
                        current.circuit.components.none { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC || it.kind==Kind.INDUCTOR }
                    val elapsed=if(periodic) (current.elapsedSeconds+delta)%end
                        else (current.elapsedSeconds+delta).coerceAtMost(end)
                    val frame=transient.frameAt(elapsed)
                    if(frame!=null) _state.value=current.copy(elapsedSeconds=elapsed,result=frame.asDcResult())
                }
            }
        }
    }

    private fun edit(next: Circuit,preserveReactive: Boolean = false) {
        finishParameterPreview()
        val old = _state.value.circuit
        if (old == next) return
        undo.addLast(old); if (undo.size > 100) undo.removeFirst(); redo.clear()
        _state.value = retainValidProbes(_state.value.copy(circuit = next, message = null,acSweep=null),next)
        autosave(next)
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
            meterPlus=valid(state.meterPlus),meterCommon=valid(state.meterCommon),
            meterCurrentWireId=state.meterCurrentWireId?.takeIf { it in wires },
            acInput=valid(state.acInput),acOutput=valid(state.acOutput))
    }
    private fun autosave(circuit:Circuit) { viewModelScope.launch(Dispatchers.IO) {
        autosaveMutex.withLock { projectStore.autosave(circuit) }
    } }
    private fun replaceDocument(circuit:Circuit,projectId:String?=null,message:String?=null) {
        finishParameterPreview()
        undo.clear();redo.clear()
        _state.value=_state.value.copy(circuit=circuit,projectId=projectId,message=message,
            selectedId=null,selectedWireId=null,contextId=null,placement=null,
            projectsOpen=false,diagnosticsOpen=false,samplesOpen=false,scopeOpen=false,meterOpen=false,bodeOpen=false,
            parameterAnalysisOpen=false,
            scopeCh1=null,scopeCh2=null,meterPlus=null,meterCommon=null,meterCurrentWireId=null,
            acInput=null,acOutput=null,acSweep=null)
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
    fun showDiagnostics(open:Boolean) {
        _state.value=_state.value.copy(diagnosticsOpen=open,projectsOpen=false,samplesOpen=false,bodeOpen=false,
            scopeOpen=false,meterOpen=false,selectedId=null,selectedWireId=null)
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
        replaceDocument(Circuit("Untitled Circuit",emptyList(),emptyList()))
    }
    fun saveProject(name:String,asNew:Boolean=false) {
        val trimmed=name.trim().take(80)
        if(trimmed.isEmpty()) { postMessage("Enter a project name before saving.");return }
        val circuit=_state.value.circuit.copy(name=trimmed)
        val id=runCatching { projectStore.save(if(asNew) null else _state.value.projectId,circuit) }
            .getOrElse { postMessage("Project could not be saved: ${it.message ?: "Storage error"}");return }
        _state.value=_state.value.copy(circuit=circuit,projectId=id,projects=projectStore.list(),message="Project saved.")
        autosave(circuit)
    }
    fun openProject(id:String) {
        val circuit=runCatching { projectStore.open(id) }.getOrElse {
            postMessage("Project could not be opened: ${it.message ?: "Invalid file"}")
            return
        }
        replaceDocument(circuit,projectId=id)
    }
    fun importCircuit(circuit:Circuit) {
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
        val version = ++solveVersion
        val circuit = _state.value.circuit
        val capInitial=if(preserveReactive) circuit.components.filter { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC }
            .mapNotNull { p -> _state.value.result.readings[p.id]?.voltage?.let { p.id to it } }.toMap() else emptyMap()
        val indInitial=if(preserveReactive) circuit.components.filter { it.kind==Kind.INDUCTOR }
            .mapNotNull { p -> _state.value.result.readings[p.id]?.current?.let { p.id to it } }.toMap() else emptyMap()
        viewModelScope.launch {
            val hasReactive=circuit.components.any { it.kind==Kind.CAPACITOR || it.kind==Kind.ELECTROLYTIC || it.kind==Kind.INDUCTOR || it.kind==Kind.FUNCTION_GENERATOR || it.kind==Kind.FUSE }
            val solved = withContext(Dispatchers.Default) {
                val dc=if(hasReactive) null else solver.solve(circuit)
                val needsStress=dc!=null && dc.error==null && circuit.components.any { p ->
                    p.kind==Kind.RESISTOR && dc.readings[p.id]?.health==com.indianservers.circuitssimulator.simulation.Health.OVERLOAD
                }
                if(hasReactive || needsStress) {
                    val fastest=circuit.components.filter { it.kind==Kind.FUNCTION_GENERATOR }
                        .maxOfOrNull { it.value("frequency") } ?: 0.0
                    val duration=if(fastest>0.0) min(1.0,max(.02,10.0/fastest)) else 1.0
                    val step=min(.002,duration/500.0)
                    val trace=transientSolver.simulate(circuit,duration,step,capInitial,indInitial)
                    val result=if(trace.error==null) trace.frameAt(0.0)?.asDcResult() ?: DcResult()
                        else DcResult(error=trace.error)
                    result to trace
                } else dc!! to null
            }
            if (version == solveVersion) _state.value = _state.value.copy(result = solved.first,
                transient = solved.second,elapsedSeconds=0.0)
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
    fun setMeterMode(mode:MeterMode) { _state.value=_state.value.copy(meterMode=mode) }
    fun attachMeter(which:String,wireId:String) {
        val wire=_state.value.circuit.wires.firstOrNull { it.id==wireId } ?: return
        _state.value=when(which) {
            "plus" -> _state.value.copy(meterPlus=wire.start,selectedWireId=null,meterOpen=true)
            "common" -> _state.value.copy(meterCommon=wire.start,selectedWireId=null,meterOpen=true)
            else -> _state.value.copy(meterCurrentWireId=wireId,selectedWireId=null,meterOpen=true,
                meterMode=MeterMode.DC_CURRENT)
        }
    }
    fun attachScope(channel:Int,wireId:String) {
        val wire=_state.value.circuit.wires.firstOrNull { it.id==wireId } ?: return
        _state.value=if(channel==1) _state.value.copy(scopeCh1=wire.start,selectedWireId=null,
            message="CH1 attached to selected wire") else _state.value.copy(scopeCh2=wire.start,
            selectedWireId=null,message="CH2 attached to selected wire")
    }
    fun detachScope(channel:Int) {
        _state.value=if(channel==1) _state.value.copy(scopeCh1=null) else _state.value.copy(scopeCh2=null)
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
            autosave(current)
        }
    }
    fun toggleSwitch(id: String) {
        val old = _state.value.circuit
        edit(old.copy(components = old.components.map { if (it.id == id && it.kind == Kind.SWITCH) it.copy(closed = !it.closed) else it }),preserveReactive=true)
    }
    fun toggleRun() { _state.value = _state.value.copy(running = !_state.value.running) }
    fun resetTime() {
        val current=_state.value
        val frame=current.transient?.frameAt(0.0)
        _state.value=current.copy(elapsedSeconds=0.0,result=frame?.asDcResult() ?: current.result)
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
        _state.value = retainValidProbes(_state.value.copy(circuit = circuit, selectedId = null),circuit)
        autosave(_state.value.circuit)
        recalculate()
    }
    fun redo() {
        if (redo.isEmpty()) return
        undo.addLast(_state.value.circuit)
        val circuit=redo.removeLast()
        _state.value = retainValidProbes(_state.value.copy(circuit = circuit, selectedId = null),circuit)
        autosave(_state.value.circuit)
        recalculate()
    }
    fun loadSample(circuit: Circuit) { replaceDocument(circuit) }
}
