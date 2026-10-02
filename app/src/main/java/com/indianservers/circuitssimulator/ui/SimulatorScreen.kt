package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    BackHandler(onHome!=null || state.catalogOpen || state.scopeOpen || state.meterOpen || state.bodeOpen || state.parameterAnalysisOpen || state.projectsOpen || state.diagnosticsOpen || state.samplesOpen || state.firmware.open || selected != null || state.selectedWireId != null || state.placement != null) {
        when { state.firmware.open -> model.showFirmware(false);
            state.projectsOpen -> model.showProjects(false); state.diagnosticsOpen -> model.showDiagnostics(false);
            state.samplesOpen -> model.showSamples(false);
            state.catalogOpen -> model.showCatalog(false); state.bodeOpen -> model.showBode(false);
            state.parameterAnalysisOpen -> model.showParameterAnalysis(false); state.scopeOpen -> model.showScope(false);
            state.meterOpen -> model.showMeter(false); selected != null -> model.select(null);
            state.selectedWireId!=null -> model.selectWire(null); state.placement!=null -> model.setPlacement(null);
            else -> onHome?.invoke() }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Navy, surface = Panel, primary = Blue,
        onBackground = TextIce, onSurface = TextIce, secondary = Mint)) {
        Box(Modifier.fillMaxSize().background(Navy)) {
            if (state.catalogOpen) CatalogScreen(state.recent,state.favorites,
                onBack = { model.showCatalog(false) },onChoose = model::setPlacement,
                onClearRecent=model::clearRecent,onToggleFavorite=model::toggleFavorite)
            else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                WorkspaceHeader(state, model,onHome)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CircuitCanvas(state, model, Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                        val top=coordinates.positionInRoot()
                        canvasBounds=Rect(top,top+Offset(coordinates.size.width.toFloat(),coordinates.size.height.toFloat()))
                    })
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top) { StatusCard(state,model); MeasurementCard(state) }
                    if(state.guide!=null) GuideHud(state,model,onExit={onHome?.invoke()},
                        modifier=Modifier.align(Alignment.TopCenter).padding(top=69.dp))
                    if(state.highlightedIssueId!=null) Surface(Modifier.align(Alignment.TopCenter)
                        .padding(top=64.dp).clickable { model.clearIssueFocus() },
                        color=Panel,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Blue)) {
                        Text("Issue highlighted · tap to clear",Modifier.padding(8.dp),color=Blue,fontSize=11.sp)
                    }
                    if (hasDcWirePolarity(state)) {
                        FlowLegend(Modifier.align(Alignment.TopCenter).padding(top=112.dp))
                        EnvironmentPanel(state,model,Modifier.align(Alignment.TopEnd).padding(top=112.dp,end=10.dp).widthIn(max=220.dp))
                    }
                    Row(Modifier.align(Alignment.BottomEnd).padding(12.dp),verticalAlignment = Alignment.CenterVertically) {
                        ActionText("−", "Zoom out", onClick = { model.zoom(.8f) })
                        Text("${(state.zoom * 100).toInt()}%", color = Muted, fontSize = 13.sp)
                        ActionText("+", "Zoom in", onClick = { model.zoom(1.25f) })
                        ActionText("⛶", "Reset view", onClick = { model.resetView() })
                    }
                    SimulationControl(state,model,Modifier.align(Alignment.BottomStart).padding(12.dp))
                    state.contextId?.let { id ->
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom=68.dp)
                            .fillMaxWidth().horizontalScroll(rememberScrollState())
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
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom=68.dp)
                            .fillMaxWidth().horizontalScroll(rememberScrollState())
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
                    state.result.error?.takeIf { state.guide==null || state.running }?.let { error -> Surface(Modifier.align(Alignment.BottomStart).padding(start=12.dp,bottom=70.dp),
                        color = Color(0xFF4E242B),shape = RoundedCornerShape(12.dp)) {
                        Text(error, Modifier.padding(12.dp), color = Color(0xFFFFC8C8), fontSize = 13.sp)
                    } }
                    state.placement?.let { kind -> Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp),
                        color = Panel, shape = RoundedCornerShape(12.dp)) {
                        Text("Tap canvas to place ${kind.title}", Modifier.padding(12.dp),color=TextIce)
                    } }
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
                else if (selected == null) ComponentDrawer(onChoose = model::setPlacement, onAll = { model.showCatalog(true) },
                    guideFocus=state.guide?.let { session ->
                        val criterion=LessonCatalog.byId[session.lessonId]?.steps?.getOrNull(session.stepIndex)?.criterion
                        (criterion as? LessonCriterion.Count)?.kind },
                    onDrag={ kind,point -> trayDrag=point?.let { kind to it } },onDrop={ kind,point -> finishTrayDrag(kind,point) })
                else Column {
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
            trayDrag?.let { (kind,point) ->
                val half=with(density) { 35.dp.toPx() }
                Surface(Modifier.offset { IntOffset((point.x-half).roundToInt(),
                    (point.y-half).roundToInt()) }.size(70.dp),color=Panel,
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(2.dp,Blue)) {
                    ComponentThumbnail(kind,Modifier.fillMaxSize())
                }
            }
            state.message?.let { msg -> Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=8.dp),
                color=Panel,shape=RoundedCornerShape(12.dp)) { Text(msg,Modifier.padding(12.dp),color=TextIce) } }
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
    Column(Modifier.widthIn(min=52.dp).clickable(onClick=onClick).padding(horizontal=5.dp,vertical=6.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text(symbol,color=Blue,fontSize=21.sp)
        Text(label,color=TextIce,fontSize=9.sp,maxLines=1)
    }
}

@Composable
private fun SimulationControl(state: SimulatorState,model: SimulatorViewModel,modifier: Modifier=Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(Modifier.clickable { open=true },color=Panel.copy(alpha=.92f),shape=RoundedCornerShape(14.dp),
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
private fun WorkspaceHeader(state: SimulatorState, model: SimulatorViewModel,onHome:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().height(75.dp).background(Color(0xFF091827)).padding(horizontal = 8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(if(onHome==null) "∿" else "‹", color=Color(0xFF9BDFFF), fontSize=30.sp,
            modifier=Modifier.clickable(enabled=onHome!=null) { onHome?.invoke() }.padding(end=8.dp))
        Column(Modifier.weight(1f)) {
            Text("Circuit Simulator", color=TextIce, fontSize=18.sp, fontWeight=FontWeight.SemiBold,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("LEARN  ·  BUILD  ·  EXPLORE", color=Muted, fontSize=8.sp, letterSpacing=1.sp)
        }
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
    Surface(Modifier.clickable { model.showDiagnostics(true) },color=Panel.copy(alpha=.9f),
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
    Column(Modifier.widthIn(min=38.dp).semantics { contentDescription = description }.clickable(onClick=onClick).padding(horizontal=2.dp,vertical=5.dp),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Text(symbol,color=tint,fontSize=23.sp,lineHeight=25.sp)
        Text(description,color=Muted,fontSize=8.sp,maxLines=1)
    }
}

@Composable
private fun ComponentDrawer(onChoose: (Kind)->Unit,onAll:()->Unit,
    guideFocus:Kind?=null,onDrag:(Kind,Offset?)->Unit,onDrop:(Kind,Offset)->Unit) {
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .background(Muted.copy(alpha=.65f),RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("Add Component",color=TextIce,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text("View all  ›",Modifier.clickable(onClick=onAll),color=Blue,fontSize=13.sp)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(Kind.BATTERY,Kind.RESISTOR,Kind.CAPACITOR,Kind.LED,Kind.SWITCH,Kind.LAMP).forEach { kind ->
                    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
                    var dragPoint by remember { mutableStateOf(Offset.Zero) }
                    Column(Modifier.width(58.dp).onGloballyPositioned { cardOrigin=it.positionInRoot() }
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
        }
    }
}
