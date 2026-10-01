package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.indianservers.circuitssimulator.domain.TerminalRef
import com.indianservers.circuitssimulator.domain.EngineeringUnits
import com.indianservers.circuitssimulator.simulation.AnalysisCsv
import com.indianservers.circuitssimulator.simulation.AcPoint
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlin.math.log10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BodeSheet(state:SimulatorState,model:SimulatorViewModel) {
    val context=LocalContext.current
    val ioScope=rememberCoroutineScope()
    var pendingCsv by remember { mutableStateOf("") }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri!=null) ioScope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingCsv) } }
        }
    }
    val terminals=state.circuit.components.filter { it.terminalCount>1 }.flatMap { p ->
        (0 until p.terminalCount).map { TerminalRef(p.id,it) to "${p.reference}:${it}" }
    }
    val defaultInput=state.circuit.components.firstOrNull { it.kind.name=="FUNCTION_GENERATOR" }
        ?.let { TerminalRef(it.id,0) } ?: terminals.firstOrNull()?.first
    val input=state.acInput ?: defaultInput
    val output=state.acOutput ?: state.circuit.components.filter { it.terminalCount>1 && it.id!=defaultInput?.componentId }
        .maxByOrNull { it.x }
        ?.let { TerminalRef(it.id,0) } ?: terminals.lastOrNull()?.first
    var startText by remember(state.acStartHz) { mutableStateOf(state.acStartHz.toString()) }
    var stopText by remember(state.acStopHz) { mutableStateOf(state.acStopHz.toString()) }
    val sweep=state.acSweep
    val points=if(input!=null && output!=null) sweep?.points.orEmpty().mapNotNull { p ->
        p.gain(input,output)?.let { p to it }
    } else emptyList()
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().height(440.dp).padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Frequency Response",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                Text("CSV",Modifier.clickable {
                    if(input!=null && output!=null && points.isNotEmpty()) {
                        pendingCsv=AnalysisCsv.bode(sweep!!.points,input,output)
                        export.launch("frequency-response.csv")
                    }
                }.padding(7.dp),color=Blue,fontSize=11.sp)
                Text("✕",Modifier.clickable { model.showBode(false) }.padding(7.dp),color=Muted)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AcNodePicker("Input",input,terminals,Modifier.weight(1f)) { model.setAcNodes(it,output) }
                AcNodePicker("Output",output,terminals,Modifier.weight(1f)) { model.setAcNodes(input,it) }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(startText,{ startText=it },Modifier.weight(1f),label={Text("Start Hz")},singleLine=true)
                OutlinedTextField(stopText,{ stopText=it },Modifier.weight(1f),label={Text("Stop Hz")},singleLine=true)
                Text("Run",Modifier.clickable {
                    val a=startText.toDoubleOrNull();val b=stopText.toDoubleOrNull()
                    if(a!=null && b!=null && a>0 && b>=a) { model.setAcRange(a,b);model.runAcSweep() }
                }.padding(7.dp),color=Blue)
            }
            if(sweep?.error!=null) Text(sweep.error,color=Color(0xFFFFB25E),fontSize=11.sp)
            Text("Magnitude · dB",color=Mint,fontSize=11.sp)
            BodePlot(points,true,Modifier.fillMaxWidth().weight(1f))
            Text("Phase · degrees",color=Blue,fontSize=11.sp)
            BodePlot(points,false,Modifier.fillMaxWidth().weight(1f))
            if(points.isNotEmpty()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(EngineeringUnits.format(points.first().first.frequencyHz,"Hz"),color=Muted,fontSize=10.sp)
                Text(EngineeringUnits.format(points[points.size/2].first.frequencyHz,"Hz"),color=Muted,fontSize=10.sp)
                Text(EngineeringUnits.format(points.last().first.frequencyHz,"Hz"),color=Muted,fontSize=10.sp)
            }
            Text("${points.size} points · logarithmic frequency · Vout/Vin",color=Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun AcNodePicker(label:String,selected:TerminalRef?,items:List<Pair<TerminalRef,String>>,
                         modifier:Modifier,onSelect:(TerminalRef)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Text("$label: ${items.firstOrNull { it.first==selected }?.second ?: "Select"} ▾",
            Modifier.fillMaxWidth().clickable { expanded=true }.padding(vertical=9.dp),color=TextIce,fontSize=11.sp)
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {
            items.forEach { (terminal,name) ->
                DropdownMenuItem(text={Text(name)},onClick={onSelect(terminal);expanded=false})
            }
        }
    }
}

@Composable
private fun BodePlot(points:List<Pair<AcPoint,com.indianservers.circuitssimulator.simulation.Complex>>,
                     magnitude:Boolean,modifier:Modifier) {
    Canvas(modifier.background(Panel,RoundedCornerShape(9.dp))) {
        val left=10f;val right=size.width-10f;val top=8f;val bottom=size.height-8f
        if(right<=left || bottom<=top) return@Canvas
        for(i in 0..4) {
            val x=left+(right-left)*i/4
            drawLine(Color(0xFF294159),Offset(x,top),Offset(x,bottom),1f)
        }
        for(i in 0..2) {
            val y=top+(bottom-top)*i/2
            drawLine(Color(0xFF294159),Offset(left,y),Offset(right,y),1f)
        }
        if(points.size<2) return@Canvas
        val x0=log10(points.first().first.frequencyHz);val x1=log10(points.last().first.frequencyHz)
        val values=points.map { (_,gain) -> if(magnitude) 20*log10(gain.magnitude.coerceAtLeast(1e-15)) else gain.phaseDegrees }
        val lo=values.minOrNull() ?: return@Canvas;val hi=values.maxOrNull() ?: return@Canvas
        val span=(hi-lo).coerceAtLeast(1.0)
        val path=Path()
        points.forEachIndexed { k,(point,_) ->
            val x=left+((log10(point.frequencyHz)-x0)/(x1-x0).coerceAtLeast(1e-12)*(right-left)).toFloat()
            val y=bottom-((values[k]-lo)/span*(bottom-top)).toFloat()
            if(k==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path,if(magnitude) Mint else Blue,style=Stroke(2.5f))
    }
}
