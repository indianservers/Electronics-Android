package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.simulation.SignalSample
import com.indianservers.circuitssimulator.simulation.SignalTrace
import com.indianservers.circuitssimulator.simulation.SignalMeasurements
import com.indianservers.circuitssimulator.simulation.AnalysisCsv
import com.indianservers.circuitssimulator.simulation.ScopeAnalysis
import com.indianservers.circuitssimulator.simulation.TriggerEdge
import com.indianservers.circuitssimulator.simulation.SpectralBin
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private fun channelTrace(state:SimulatorState,terminal:TerminalRef?,name:String):SignalTrace? {
    val frames=state.transient?.frames ?: return null
    if(terminal==null || frames.isEmpty()) return null
    val samples=frames.map { SignalSample(it.timeSeconds,it.nodeVoltages[terminal] ?: 0.0) }
    return SignalTrace(name,"Voltage","V",samples)
}

private fun probeLabel(state:SimulatorState,terminal:TerminalRef?,channel:String):String =
    terminal?.let { probe ->
        val reference=state.circuit.components.firstOrNull { it.id==probe.componentId }?.reference
        val label=state.circuit.wires.firstOrNull { it.label!=null &&
            (it.start==probe || it.end==probe) }?.label
        if(label!=null) "$channel  $label" else if(reference!=null) "$channel  $reference:${probe.index}" else "$channel  Attached"
    } ?: "Tap wire → Attach $channel"

@Composable
fun OscilloscopeSheet(state:SimulatorState,model:SimulatorViewModel) {
    val context=LocalContext.current
    val ioScope=rememberCoroutineScope()
    var pendingCsv by remember { mutableStateOf("") }
    var triggerMode by rememberSaveable { mutableIntStateOf(0) }
    var triggerLevel by rememberSaveable { mutableFloatStateOf(.5f) }
    var cursorA by rememberSaveable { mutableFloatStateOf(.25f) }
    var cursorB by rememberSaveable { mutableFloatStateOf(.75f) }
    var showFft by rememberSaveable { mutableStateOf(false) }
    var showXy by rememberSaveable { mutableStateOf(false) }
    val ch1=remember(state.transient,state.scopeCh1) { channelTrace(state,state.scopeCh1,"CH1") }
    val ch2=remember(state.transient,state.scopeCh2) { channelTrace(state,state.scopeCh2,"CH2") }
    val ch3=remember(state.transient,state.scopeCh3) { channelTrace(state,state.scopeCh3,"CH3") }
    val ch4=remember(state.transient,state.scopeCh4) { channelTrace(state,state.scopeCh4,"CH4") }
    val ch1Samples=ch1?.samples.orEmpty()
    val firstTime=ch1Samples.firstOrNull()?.timeSeconds ?: 0.0
    val lastTime=ch1Samples.lastOrNull()?.timeSeconds ?: 1.0
    val duration=(lastTime-firstTime).coerceAtLeast(.001)
    val minVoltage=ch1Samples.minOfOrNull { it.value } ?: 0.0
    val maxVoltage=ch1Samples.maxOfOrNull { it.value } ?: 1.0
    val threshold=minVoltage+(maxVoltage-minVoltage)*triggerLevel
    val triggerTime=if(triggerMode==0) null else ScopeAnalysis.trigger(ch1Samples,threshold,
        if(triggerMode==1) TriggerEdge.RISING else TriggerEdge.FALLING,firstTime+duration*.05)
    val visibleWindow=triggerTime?.let { crossing ->
        val width=duration*.5
        val start=(crossing-width*.1).coerceIn(firstTime,lastTime-width)
        start..(start+width)
    } ?: firstTime..lastTime
    val firstCursor=visibleWindow.start+(visibleWindow.endInclusive-visibleWindow.start)*min(cursorA,cursorB)
    val secondCursor=visibleWindow.start+(visibleWindow.endInclusive-visibleWindow.start)*max(cursorA,cursorB)
    val measurement=ScopeAnalysis.cursors(ch1Samples,firstCursor,secondCursor)
    val spectrum=remember(ch1) { ch1?.let { ScopeAnalysis.spectrum(it.samples) }.orEmpty() }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri!=null) ioScope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingCsv) } }
        }
    }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=540.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .background(Muted,RoundedCornerShape(4.dp)))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Oscilloscope",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                Text(if(state.running) "Pause" else "Run",Modifier.clickable { model.toggleRun() }.padding(7.dp),
                    color=Blue,fontSize=12.sp)
                Text("Reset",Modifier.clickable { model.resetTime() }.padding(7.dp),color=Blue,fontSize=12.sp)
                Text("CSV",Modifier.clickable {
                    val frames=state.transient?.frames.orEmpty()
                    if(frames.isNotEmpty()) {
                        pendingCsv=AnalysisCsv.scope(frames,listOf(state.scopeCh1,state.scopeCh2,
                            state.scopeCh3,state.scopeCh4))
                        export.launch("scope-trace.csv")
                    }
                }.padding(7.dp),color=Blue,fontSize=12.sp)
                Text(if(showFft) "Trace" else "FFT",Modifier.clickable { showFft=!showFft }.padding(7.dp),
                    color=Blue,fontSize=12.sp)
                Text(if(showXy) "Time" else "XY",Modifier.clickable { showXy=!showXy }.padding(7.dp),
                    color=Blue,fontSize=12.sp)
                Text("✕",Modifier.clickable { model.showScope(false) }.padding(7.dp),color=Muted)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(probeLabel(state,state.scopeCh1,"CH1"),
                    color=Mint,fontSize=10.sp)
                Text("Disconnect",Modifier.clickable { model.detachScope(1) },color=Muted,fontSize=10.sp)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Trigger: ${listOf("Auto","Rising","Falling")[triggerMode]}",
                    Modifier.clickable { triggerMode=(triggerMode+1)%3 }.padding(5.dp),color=Blue,fontSize=11.sp)
                Text("Auto-scale",Modifier.clickable { triggerMode=0;cursorA=.25f;cursorB=.75f }.padding(5.dp),
                    color=Blue,fontSize=11.sp)
                if(triggerMode!=0) {
                    Slider(triggerLevel,{triggerLevel=it},Modifier.weight(1f))
                    Text("${"%.2f".format(java.util.Locale.US,threshold)} V",color=Muted,fontSize=10.sp)
                }
            }
            if(triggerMode!=0) Text(triggerTime?.let { "Triggered at ${"%.2f".format(java.util.Locale.US,it*1000)} ms" }
                ?: "No crossing at this level",color=Muted,fontSize=10.sp)
            if(showXy) XyScopeChart(ch1Samples,ch2?.samples.orEmpty(),
                Modifier.fillMaxWidth().height(170.dp)) else
                SignalChart(ch1,state.elapsedSeconds,
                    Modifier.fillMaxWidth().height(110.dp),Mint,visibleWindow,firstCursor to secondCursor,triggerTime)
            ch1?.let { trace ->
                SignalMeasurements.analyze(trace.samples)?.let { stats ->
                    ScopeStatistics("CH1",stats)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("A",color=Color(0xFFFFDA75),fontSize=11.sp)
                Slider(cursorA,{cursorA=it},Modifier.weight(1f))
                Text("B",color=Color(0xFFFF8AC8),fontSize=11.sp)
                Slider(cursorB,{cursorB=it},Modifier.weight(1f))
            }
            measurement?.let { value ->
                Text("Δt ${"%.2f".format(java.util.Locale.US,value.deltaTime*1000)} ms   "+
                    "ΔV ${"%.3f".format(java.util.Locale.US,value.deltaValue)} V",
                    color=TextIce,fontSize=11.sp)
            }
            if(showFft) {
                val peak=spectrum.maxByOrNull { it.amplitude }
                Text(peak?.let { "FFT peak ${"%.1f".format(java.util.Locale.US,it.frequencyHz)} Hz · "+
                    "${"%.3f".format(java.util.Locale.US,it.amplitude)} V" } ?: "FFT unavailable",
                    color=TextIce,fontSize=11.sp)
                SpectrumChart(spectrum,Modifier.fillMaxWidth().height(90.dp))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(probeLabel(state,state.scopeCh2,"CH2"),
                    color=Blue,fontSize=10.sp)
                Text("Disconnect",Modifier.clickable { model.detachScope(2) },color=Muted,fontSize=10.sp)
            }
            SignalChart(ch2,state.elapsedSeconds,
                Modifier.fillMaxWidth().height(92.dp),Blue,visibleWindow)
            ch2?.let { trace ->
                SignalMeasurements.analyze(trace.samples)?.let { ScopeStatistics("CH2",it) }
            }
            listOf(Triple(3,state.scopeCh3,ch3),Triple(4,state.scopeCh4,ch4)).forEach { (number,probe,trace) ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(probeLabel(state,probe,"CH$number"),color=if(number==3) Supply else Color(0xFFFF8AC8),fontSize=10.sp)
                    Text("Disconnect",Modifier.clickable { model.detachScope(number) },color=Muted,fontSize=10.sp)
                }
                SignalChart(trace,state.elapsedSeconds,Modifier.fillMaxWidth().height(74.dp),
                    if(number==3) Supply else Color(0xFFFF8AC8),visibleWindow)
            }
        }
    }
}

@Composable
private fun XyScopeChart(x:List<SignalSample>,y:List<SignalSample>,modifier:Modifier=Modifier) {
    Canvas(modifier.background(Panel,RoundedCornerShape(9.dp)).padding(12.dp)) {
        val count=min(x.size,y.size)
        if(count<2) return@Canvas
        val minX=x.take(count).minOf { it.value };val maxX=x.take(count).maxOf { it.value }
        val minY=y.take(count).minOf { it.value };val maxY=y.take(count).maxOf { it.value }
        val spanX=(maxX-minX).coerceAtLeast(1e-9)
        val spanY=(maxY-minY).coerceAtLeast(1e-9)
        drawLine(Muted.copy(alpha=.5f),Offset(0f,size.height/2),Offset(size.width,size.height/2),1f)
        drawLine(Muted.copy(alpha=.5f),Offset(size.width/2,0f),Offset(size.width/2,size.height),1f)
        val path=Path()
        for(index in 0 until count) {
            val px=((x[index].value-minX)/spanX*size.width).toFloat()
            val py=(size.height-(y[index].value-minY)/spanY*size.height).toFloat()
            if(index==0) path.moveTo(px,py) else path.lineTo(px,py)
        }
        drawPath(path,Mint,style=androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
    }
    Text("X: CH1 ${"%.2f".format(java.util.Locale.US,x.minOfOrNull { it.value } ?: 0.0)}–"+
        "${"%.2f".format(java.util.Locale.US,x.maxOfOrNull { it.value } ?: 0.0)} V    "+
        "Y: CH2 ${"%.2f".format(java.util.Locale.US,y.minOfOrNull { it.value } ?: 0.0)}–"+
        "${"%.2f".format(java.util.Locale.US,y.maxOfOrNull { it.value } ?: 0.0)} V",
        color=Muted,fontSize=10.sp)
}

@Composable
private fun SpectrumChart(bins:List<SpectralBin>,modifier:Modifier=Modifier) {
    Canvas(modifier.background(Panel,RoundedCornerShape(9.dp)).padding(8.dp)) {
        if(bins.isEmpty()) return@Canvas
        val maximum=(bins.maxOfOrNull { it.amplitude } ?: 0.0).coerceAtLeast(1e-12)
        val width=size.width/bins.size
        bins.forEachIndexed { index,bin ->
            val x=(index+.5f)*width
            val height=(bin.amplitude/maximum).toFloat()*size.height
            drawLine(Mint,androidx.compose.ui.geometry.Offset(x,size.height),
                androidx.compose.ui.geometry.Offset(x,size.height-height),width.coerceAtMost(4f))
        }
    }
}

@Composable
private fun ScopeStatistics(channel:String,stats:com.indianservers.circuitssimulator.simulation.SignalStatistics) {
    fun number(v:Double)="%.2f".format(java.util.Locale.US,v)
    Text("$channel  Max ${number(stats.maximum)}  Min ${number(stats.minimum)}  Vpp ${number(stats.peakToPeak)}  Mean ${number(stats.mean)} V",
        color=Muted,fontSize=9.sp,maxLines=1)
    Text("RMS ${number(stats.rms)} V   f ${stats.frequencyHz?.let { number(it) } ?: "—"} Hz   "+
        "T ${stats.periodSeconds?.let { number(it*1000) } ?: "—"} ms   "+
        "Duty ${stats.dutyCycle?.let { number(it*100) } ?: "—"}%",
        color=Muted,fontSize=9.sp,maxLines=1)
}
