package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.canvas.*
import com.indianservers.circuitssimulator.intelligence.IssueSeverity
import com.indianservers.circuitssimulator.guide.LessonCatalog
import com.indianservers.circuitssimulator.guide.LessonCriterion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SimulatorScreen(model: SimulatorViewModel,onHome:(()->Unit)?=null) {
    val state by model.state.collectAsState()
    val density=LocalDensity.current
    val context=LocalContext.current
    val focusManager=LocalFocusManager.current
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var canvasMode by rememberSaveable { mutableStateOf(CanvasInteractionMode.CONNECT) }
    DisposableEffect(fullScreen,context) {
        val activity=context.findActivity()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,it.window.decorView) }
        controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if(fullScreen) controller?.hide(WindowInsetsCompat.Type.systemBars())
        else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val selected = state.circuit.components.firstOrNull { it.id == state.selectedId }
    LaunchedEffect(state.message) {
        state.message?.let { message -> delay(2500);model.clearMessage(message) }
    }
    var canvasBounds by remember { mutableStateOf<Rect?>(null) }
    var trayDrag by remember { mutableStateOf<Pair<Kind,Offset>?>(null) }
    var pendingWireLabelId by remember { mutableStateOf<String?>(null) }
    var wireLabelText by remember { mutableStateOf("") }
    fun finishTrayDrag(kind: Kind,screen: Offset) {
        val bounds=canvasBounds ?: return
        if(!bounds.contains(screen)) return
        val local=screen-bounds.topLeft
        val scale=min(bounds.width/1000f,bounds.height/900f)*state.zoom
        val origin=Offset((bounds.width-1000f*scale)/2+state.panX,
            (bounds.height-900f*scale).coerceAtLeast(0f)*.5f+state.panY)
        val world=(local-origin)/scale.coerceAtLeast(.001f)
        model.add(kind,(world.x/10f).roundToInt()*10f,(world.y/10f).roundToInt()*10f)
    }
    BackHandler(fullScreen || onHome!=null || state.catalogOpen || state.scopeOpen || state.meterOpen || state.bodeOpen || state.parameterAnalysisOpen || state.projectsOpen || state.diagnosticsOpen || state.samplesOpen || state.firmware.open || selected != null || state.selectedWireId != null || state.placement != null) {
        when { state.firmware.open -> model.showFirmware(false);
            state.projectsOpen -> model.showProjects(false); state.diagnosticsOpen -> model.showDiagnostics(false);
            state.samplesOpen -> model.showSamples(false);
            state.catalogOpen -> model.showCatalog(false); state.bodeOpen -> model.showBode(false);
            state.parameterAnalysisOpen -> model.showParameterAnalysis(false); state.scopeOpen -> model.showScope(false);
            state.meterOpen -> model.showMeter(false); selected != null -> model.select(null);
            state.selectedWireId!=null -> model.selectWire(null); state.placement!=null -> model.setPlacement(null);
            fullScreen -> fullScreen=false; else -> onHome?.invoke() }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Navy, surface = Panel, primary = Blue,
        onBackground = TextIce, onSurface = TextIce, secondary = Mint)) {
        Box(Modifier.fillMaxSize().background(Navy)) {
            if (state.catalogOpen) Column(Modifier.fillMaxSize()) {
                if(state.message!=null) Box(Modifier.statusBarsPadding()) { WorkspaceMessage(state.message) }
                Box(Modifier.weight(1f)) {
                    CatalogScreen(state.recent,state.favorites,
                        onBack = { model.showCatalog(false) },onChoose = model::setPlacement,
                        onClearRecent=model::clearRecent,onToggleFavorite=model::toggleFavorite)
                }
            }
            else BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val maxNoticeHeight=maxHeight * .35f
                val wide=maxWidth >= 600.dp && maxWidth > maxHeight
                val drawerVisible=selected==null && !state.firmware.open && !state.logicAnalyzerOpen &&
                    !state.projectsOpen && !state.diagnosticsOpen && !state.samplesOpen && !state.bodeOpen &&
                    !state.parameterAnalysisOpen && !state.scopeOpen && !state.meterOpen
                Column(Modifier.fillMaxSize()) {
                WorkspaceHeader(state, model,onHome,wide,fullScreen) { fullScreen=!fullScreen }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    CanvasInteractionMode.entries.forEach { mode ->
                        FilterChip(selected=canvasMode==mode,onClick={canvasMode=mode},label={Text(mode.title)})
                    }
                    Text("2 fingers: pan / zoom",color=Muted,fontSize=11.sp)
                    FilterChip(state.circuit.settings.advancedEmbedded,{model.setEmbeddedMode(!state.circuit.settings.advancedEmbedded)},
                        label={Text(if(state.circuit.settings.advancedEmbedded) "Advanced" else "Beginner")})
                }
                Column(Modifier.fillMaxWidth().heightIn(max=maxNoticeHeight)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(4.dp),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    if(hasDcWirePolarity(state)) FlowLegend(Modifier.padding(horizontal=8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),
                        horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        StatusCard(state,model)
                        SimulationControl(state,model)
                        MeasurementCard(state)
                        if(fullScreen) ActionText("+", "Add part", { model.showCatalog(true) },Blue)
                        ActionText("−", "Zoom out", onClick = { model.zoom(.8f) })
                        Text("${(state.zoom * 100).toInt()}%",color=Muted,fontSize=13.sp)
                        ActionText("+", "Zoom in", onClick = { model.zoom(1.25f) })
                        ActionText("⛶", "Reset view", onClick = { model.resetView() })
                    }
                    EnvironmentPanel(state,model,Modifier.padding(horizontal=8.dp).widthIn(max=320.dp))
                    WorkspaceMessage(state.message)
                    state.result.error?.takeIf { state.guide==null || state.running }?.let { error ->
                        Surface(Modifier.fillMaxWidth().padding(horizontal=8.dp),
                            color=Color(0xFF4E242B),shape=RoundedCornerShape(10.dp)) {
                            Text(error,Modifier.padding(10.dp),color=Color(0xFFFFC8C8),fontSize=13.sp)
                        }
                    }
                    if(state.highlightedIssueId!=null) Surface(Modifier.fillMaxWidth().padding(horizontal=8.dp)
                        .focusHighlight().clickable { model.clearIssueFocus() },
                        color=Panel,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Blue)) {
                        Text("Issue highlighted · tap to clear",Modifier.padding(10.dp),color=Blue,fontSize=11.sp)
                    }
                    state.placement?.let { kind -> Surface(Modifier.fillMaxWidth().padding(horizontal=8.dp),
                        color=Panel,shape=RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("Place ${kind.title}",Modifier.weight(1f),color=TextIce)
                            TextButton(onClick={finishTrayDrag(kind,canvasBounds?.center ?: Offset.Zero)}) {
                                Text("Place in center")
                            }
                        }
                    } }
                    if(state.guide!=null) GuideHud(state,model,onExit={onHome?.invoke()})
                    state.contextId?.let { id ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .background(Panel,RoundedCornerShape(13.dp)).padding(5.dp),
                            horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            ContextAction("↻","Rotate") { model.rotate(id);model.dismissContext() }
                            ContextAction("−","Smaller") {
                                state.circuit.components.firstOrNull { it.id==id }?.let { model.resize(id,it.sizeScale-.15f) }
                            }
                            ContextAction("+","Larger") {
                                state.circuit.components.firstOrNull { it.id==id }?.let { model.resize(id,it.sizeScale+.15f) }
                            }
                            ContextAction("⧉","Duplicate") { model.duplicate(id) }
                            ContextAction("⌁","Disconnect") { model.disconnect(id) }
                            ContextAction("⌫","Delete") { model.remove(id) }
                            ContextAction("⚙","Properties") { model.select(id) }
                        }
                    }
                    state.selectedWireId?.let { id ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .background(Panel,RoundedCornerShape(13.dp)).padding(5.dp),
                            horizontalArrangement=Arrangement.Center) {
                            ContextAction("①","CH1") { model.attachScope(1,id);model.showScope(true) }
                            ContextAction("②","CH2") { model.attachScope(2,id);model.showScope(true) }
                            ContextAction("③","CH3") { model.attachScope(3,id);model.showScope(true) }
                            ContextAction("④","CH4") { model.attachScope(4,id);model.showScope(true) }
                            ContextAction("+","V+") { model.attachMeter("plus",id) }
                            ContextAction("−","COM") { model.attachMeter("common",id) }
                            ContextAction("A","A") { model.attachMeter("current",id) }
                            ContextAction("N","Label") {
                                pendingWireLabelId=id
                                wireLabelText=state.circuit.wires.firstOrNull { it.id==id }?.label.orEmpty()
                            }
                            ContextAction("⌫","Delete") { model.removeWire(id) }
                        }
                    }

                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CircuitCanvas(state, model, Modifier.fillMaxSize().onKeyEvent { event ->
                        if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                            Key.DirectionLeft -> { model.pan(40f,0f);true }
                            Key.DirectionRight -> { model.pan(-40f,0f);true }
                            Key.DirectionUp -> { model.pan(0f,40f);true }
                            Key.DirectionDown -> { model.pan(0f,-40f);true }
                            Key.Enter,Key.DirectionCenter -> {
                                state.placement?.let { finishTrayDrag(it,canvasBounds?.center ?: Offset.Zero) };true
                            }
                            Key.Back,Key.Escape -> { focusManager.clearFocus();true }
                            else -> false
                        }
                    }
                        .focusHighlight().focusable().onGloballyPositioned { coordinates ->
                        val top=coordinates.positionInRoot()
                        canvasBounds=Rect(top,top+Offset(coordinates.size.width.toFloat(),coordinates.size.height.toFloat()))
                      },interactionMode=canvasMode)

                }
                if(wide && drawerVisible && !fullScreen) ComponentDrawer(model::setPlacement,{model.showCatalog(true)},
                    onDrag={kind,point -> trayDrag=point?.let {kind to it}},
                    onDrop={kind,point -> finishTrayDrag(kind,point)},vertical=true,
                    guideFocus=state.guide?.let { session ->
                        (LessonCatalog.byId[session.lessonId]?.steps?.getOrNull(session.stepIndex)?.criterion as? LessonCriterion.Count)?.kind
                    })
                }
                if(state.firmware.open) FirmwareSheet(state,model)
                else if(state.logicAnalyzerOpen) LogicAnalyzerSheet(state,model)
                else if(state.projectsOpen) ProjectSheet(state,model)
                else if(state.diagnosticsOpen) DiagnosticsSheet(state,model)
                else if(state.samplesOpen) SampleBrowser(model)
                else if(state.bodeOpen) BodeSheet(state,model)
                else if(state.parameterAnalysisOpen) ParameterAnalysisSheet(state,model)
                else if(state.scopeOpen) OscilloscopeSheet(state,model)
                else if(state.meterOpen) MultimeterSheet(state,model)
                else if (selected == null && !wide && !fullScreen) ComponentDrawer(onChoose = model::setPlacement, onAll = { model.showCatalog(true) },
                    guideFocus=state.guide?.let { session ->
                        val criterion=LessonCatalog.byId[session.lessonId]?.steps?.getOrNull(session.stepIndex)?.criterion
                        (criterion as? LessonCriterion.Count)?.kind },
                    onDrag={ kind,point -> trayDrag=point?.let { kind to it } },onDrop={ kind,point -> finishTrayDrag(kind,point) })
                else if(selected!=null) Column {
                    Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick={model.explainPart(selected.id)}) { Text("Why this part?") }
                        TextButton(onClick={model.previewPartRemoval(selected.id)}) { Text("What if removed?") }
                        val count=state.intelligence.issues.count { selected.id in it.componentIds }
                        if(count>0) TextButton(onClick={model.showDiagnostics(true)}) {
                            Text("$count issue${if(count==1) "" else "s"}") }
                    }
                    ComponentDetails(selected,state,model)
                }
            }
            }
            trayDrag?.let { (kind,point) ->
                val half=with(density) { 35.dp.toPx() }
                Surface(Modifier.offset { IntOffset((point.x-half).roundToInt(),
                    (point.y-half).roundToInt()) }.size(70.dp),color=Panel,
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(2.dp,Blue)) {
                    ComponentThumbnail(kind,Modifier.fillMaxSize())
                }
            }
            state.intelligenceExplanation?.let { explanation -> AlertDialog(
                onDismissRequest=model::clearIntelligenceExplanation,
                title={Text("Circuit explanation")},text={Text(explanation)},
                confirmButton={TextButton(onClick=model::clearIntelligenceExplanation) { Text("Done") }}) }
            if(state.recoveryAvailable) AlertDialog(onDismissRequest=model::dismissRecovery,
                title={Text("Restore working session?")},
                text={Text("An autosaved circuit is available from your previous session.")},
                confirmButton={TextButton(onClick=model::restoreSession) { Text("Restore") }},
                dismissButton={TextButton(onClick=model::dismissRecovery) { Text("Keep current") }})
            pendingWireLabelId?.let { id -> AlertDialog(onDismissRequest={pendingWireLabelId=null},
                title={Text("Net label")},
                text={ Column {
                    Text("Wires with the same label connect electrically, even when drawn apart.")
                    OutlinedTextField(wireLabelText,{ wireLabelText=it },label={Text("Label")},singleLine=true)
                } },
                confirmButton={TextButton(onClick={model.setWireLabel(id,wireLabelText);pendingWireLabelId=null}) {
                    Text("Apply")
                }},
                dismissButton={TextButton(onClick={pendingWireLabelId=null}) { Text("Cancel") }}) }
        }
    }
}

@Composable
private fun FlowLegend(modifier: Modifier = Modifier) {
    Surface(modifier=modifier,color=Panel.copy(alpha=.94f),shape=RoundedCornerShape(12.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.padding(horizontal=11.dp,vertical=7.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(13.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("● + side",color=Supply,fontSize=11.sp)
                Text("● − return",color=Return,fontSize=11.sp)
                Text("➜ current",color=TextIce,fontSize=11.sp)
            }
            Text("Wire color: voltage  ·  Arrow: current direction",color=Muted,fontSize=10.sp)
        }
    }
}

private fun hasWarning(state:SimulatorState)=state.result.error!=null ||
    state.intelligence.issues.any { it.severity==IssueSeverity.CRITICAL ||
        it.severity==IssueSeverity.ERROR || it.severity==IssueSeverity.WARNING }

@Composable
private fun ContextAction(symbol:String,label:String,onClick:()->Unit) {
    Column(Modifier.widthIn(min=52.dp).heightIn(min=48.dp).focusHighlight().clickable(onClick=onClick).padding(horizontal=5.dp,vertical=6.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text(symbol,color=Blue,fontSize=21.sp)
        Text(label,color=TextIce,fontSize=9.sp,maxLines=1)
    }
}

@Composable
private fun SimulationControl(state: SimulatorState,model: SimulatorViewModel,modifier: Modifier=Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(Modifier.focusHighlight().clickable { open=true },color=Panel.copy(alpha=.92f),shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,Color(0xFF294159))) {
            Row(Modifier.padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("▂▆█",color=Mint,fontSize=14.sp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Simulation",color=TextIce,fontSize=12.sp)
                    Text(if(hasWarning(state)) "Warning" else if(state.firmware.running)
                        "Firmware ${"%.2f".format(java.util.Locale.US,state.elapsedSeconds)} s" else if(state.running)
                        "${"%.2f".format(java.util.Locale.US,state.elapsedSeconds)} s · ${state.simulationSpeed}×"
                        else "Paused · ${"%.2f".format(java.util.Locale.US,state.elapsedSeconds)} s",
                        color=if(hasWarning(state)) Color(0xFFFFB25E) else Mint,fontSize=11.sp)
                }
                Spacer(Modifier.width(14.dp))
                Text("⌄",color=Muted,fontSize=18.sp)
            }
        }
        DropdownMenu(open,onDismissRequest={open=false}) {
            DropdownMenuItem(text={Text("Oscilloscope")},onClick={model.showScope(true);open=false})
            if(state.circuit.components.any { it.kind.isBoard })
                DropdownMenuItem(text={Text("Firmware Lab")},
                    onClick={model.showFirmware(true);open=false})
                DropdownMenuItem(text={Text("Logic analyzer")},
                    onClick={model.showLogicAnalyzer(true);open=false})
            DropdownMenuItem(text={Text("Multimeter")},onClick={model.showMeter(true);open=false})
            DropdownMenuItem(text={Text(if(state.benchAudioEnabled)"Mute bench sound" else "Enable bench sound")},onClick={model.toggleBenchAudio();open=false})
            DropdownMenuItem(text={Text("Analyze · Frequency Response")},onClick={model.showBode(true);open=false})
            DropdownMenuItem(text={Text("Analyze · Parameter sweep")},onClick={model.showParameterAnalysis(true);open=false})
            DropdownMenuItem(text={Text("Projects")},onClick={model.showProjects(true);open=false})
            DropdownMenuItem(text={Text("Check Circuit")},onClick={model.showDiagnostics(true);open=false})
            DropdownMenuItem(text={Text("Why isn't this working?")},onClick={model.showDiagnostics(true,true);open=false})
            DropdownMenuItem(text={Text("Explain Circuit")},onClick={model.showDiagnostics(true);open=false})
            DropdownMenuItem(text={Text("Sample circuits")},onClick={model.showSamples(true);open=false})
            DropdownMenuItem(text={Text("Fit circuit to view")},onClick={model.fitCircuitView();open=false})
            DropdownMenuItem(text={Text(if(state.measurementsVisible) "Hide canvas readings" else "Show canvas readings")},
                onClick={model.toggleMeasurements();open=false})
            DropdownMenuItem(text={ Text("Reset time") },onClick={model.resetTime();open=false})
            listOf(.25,1.0,4.0).forEach { speed ->
                DropdownMenuItem(text={ Text("Simulation speed ${speed}×"+
                    if(state.simulationSpeed==speed) " ✓" else "") },
                    onClick={model.setSimulationSpeed(speed);open=false})
            }
        }
    }
}

@Composable
private fun WorkspaceHeader(state: SimulatorState, model: SimulatorViewModel,onHome:(()->Unit)?=null,
    compact:Boolean=false,fullScreen:Boolean=false,onFullScreen:()->Unit) {
    Row(Modifier.fillMaxWidth().height(if(compact || fullScreen) 56.dp else 75.dp).background(Color(0xFF091827)).padding(horizontal = 8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(if(onHome==null) "∿" else "‹", color=Color(0xFF9BDFFF), fontSize=30.sp,
            modifier=Modifier.clickable(enabled=onHome!=null) { onHome?.invoke() }.padding(end=8.dp))
        Column(Modifier.weight(1f)) {
            Text("Circuit Simulator", color=TextIce, fontSize=18.sp, fontWeight=FontWeight.SemiBold,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("LEARN  ·  BUILD  ·  EXPLORE", color=Muted, fontSize=8.sp, letterSpacing=1.sp)
        }
        ActionText(if(fullScreen) "⊡" else "⛶",if(fullScreen) "Exit full" else "Full screen",onFullScreen,Blue)
        ActionText("↶", "Undo", model::undo)
        ActionText("↷", "Redo", model::redo)
        ActionText("⟲", "Rewind", model::resetTime)
        ActionText(if (state.running) "Ⅱ" else "▷",if (state.running) "Pause" else "Run", model::toggleRun,
            if (state.running) Blue else Mint)
        ActionText("▣", "Save", { if(state.projectId==null) model.showProjects(true) else model.saveProject(state.circuit.name) }, Blue)
    }
}

@Composable
private fun StatusCard(state: SimulatorState,model:SimulatorViewModel) {
    val report=state.intelligence
    val attention=state.liveWarnings && (report.urgent>0 || report.warnings>0 || report.suggestions>0)
    Surface(Modifier.focusHighlight().clickable { model.showDiagnostics(true) },color=Panel.copy(alpha=.9f),
        shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("●",color=if (attention) Color(0xFFFFB25E) else Mint,fontSize=14.sp)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(if(state.transient!=null) "Transient Circuit" else "DC Circuit",
                    color=TextIce,fontSize=14.sp,fontWeight=FontWeight.Medium)
                Text(if(attention) report.status else if (state.running) "Tap to check" else "Paused · tap to check",
                    color=Muted,fontSize=11.sp)
            }
        }
    }
}

@Composable
private fun MeasurementCard(state: SimulatorState) {
    val source = state.circuit.components.firstOrNull { it.kind == Kind.BATTERY || it.kind == Kind.SOURCE || it.kind == Kind.FUNCTION_GENERATOR }
    val rawCurrent = source?.let { state.result.readings[it.id]?.current?.let(::abs) } ?: 0.0
    val current = if (rawCurrent < 1e-9) 0.0 else rawCurrent
    val sourceVoltage = source?.let { state.result.readings[it.id]?.voltage }
    val sourcePower = source?.let { state.result.readings[it.id]?.power }
    val led = state.circuit.components.firstOrNull { it.kind == Kind.LED }
    val ledConducting = led?.let { abs(state.result.readings[it.id]?.current ?: 0.0) > 1e-5 } ?: false
    Surface(color=Panel.copy(alpha=.9f),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.padding(horizontal=12.dp,vertical=9.dp)) {
            Text("V  ${sourceVoltage?.let { EngineeringUnits.format(it,"V") } ?: "—"}",color=TextIce,fontSize=12.sp)
            Text("I  ${if(state.result.error==null) EngineeringUnits.format(current,"A") else "—"}",color=TextIce,fontSize=12.sp)
            Text(if (led != null) "LED  ${if (ledConducting) "ON" else "OFF"}" else
                "P  ${sourcePower?.let { EngineeringUnits.format(abs(it),"W") } ?: "—"}",
                color=if (ledConducting) Mint else Muted,fontSize=12.sp)
        }
    }
}

@Composable
fun ActionText(symbol: String, description: String, onClick: () -> Unit, tint: Color = Muted) {
    Column(Modifier.widthIn(min=48.dp).heightIn(min=48.dp).focusHighlight().semantics { contentDescription = description }.clickable(onClick=onClick).padding(horizontal=2.dp,vertical=5.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text(symbol,color=tint,fontSize=23.sp,lineHeight=25.sp)
        Text(description,color=Muted,fontSize=8.sp,maxLines=1)
    }
}

@Composable
private fun ComponentDrawer(onChoose: (Kind)->Unit,onAll:()->Unit,
    guideFocus:Kind?=null,onDrag:(Kind,Offset?)->Unit,onDrop:(Kind,Offset)->Unit,vertical:Boolean=false) {
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column((if(vertical) Modifier.width(152.dp).fillMaxHeight().verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth()).padding(top=10.dp,bottom=10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .background(Muted.copy(alpha=.65f),RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text(if(vertical) "Add" else "Add Component",color=TextIce,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text("View all  ›",Modifier.focusHighlight().clickable(onClick=onAll).padding(8.dp),color=Blue,fontSize=13.sp)
            }
            Spacer(Modifier.height(7.dp))
            val cards: @Composable () -> Unit = {
                listOf(Kind.BATTERY,Kind.RESISTOR,Kind.CAPACITOR,Kind.LED,Kind.SWITCH,Kind.LAMP).forEach { kind ->
                    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
                    var dragPoint by remember { mutableStateOf(Offset.Zero) }
                    Column(Modifier.width(if(vertical) 128.dp else 64.dp).focusHighlight().onGloballyPositioned { cardOrigin=it.positionInRoot() }
                        .pointerInput(kind,cardOrigin) {
                            detectDragGestures(onDragStart={ start ->
                                dragPoint=cardOrigin+start;onDrag(kind,dragPoint)
                            },onDrag={ change,amount ->
                                change.consume();dragPoint+=amount;onDrag(kind,dragPoint)
                            },onDragEnd={onDrop(kind,dragPoint);onDrag(kind,null)},
                                onDragCancel={onDrag(kind,null)})
                        }.clickable { onChoose(kind) },horizontalAlignment=Alignment.CenterHorizontally) {
                        Surface(color=Panel,shape=RoundedCornerShape(13.dp),border=BorderStroke(if(kind==guideFocus) 2.dp else 1.dp,
                            if(kind==guideFocus) HubCyan else Color(0xFF2A455E))) {
                            ComponentThumbnail(kind,Modifier.size(56.dp))
                        }
                        Text(kind.title,color=TextIce,fontSize=11.sp,maxLines=1)
                    }
                }
            }
            if(vertical) Column(horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(8.dp)) { cards() }
            else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) { cards() }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun Modifier.focusHighlight(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.onFocusChanged { focused=it.isFocused }
        .border(if(focused) 2.dp else 0.dp,if(focused) Blue else Color.Transparent,RoundedCornerShape(8.dp))
}

@Composable
private fun WorkspaceMessage(message: String?) {
    message?.let {
        Surface(Modifier.fillMaxWidth().padding(horizontal=8.dp),color=Panel,
            shape=RoundedCornerShape(10.dp)) {
            Text(it,Modifier.padding(10.dp),color=TextIce,fontSize=13.sp)
        }
    }
}
