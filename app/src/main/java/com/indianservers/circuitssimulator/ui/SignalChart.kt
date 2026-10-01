package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.simulation.SignalTrace
import com.indianservers.circuitssimulator.simulation.ScopeAnalysis
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlin.math.abs
import kotlin.math.max

@Composable
fun SignalChart(trace: SignalTrace?, elapsedSeconds: Double, modifier: Modifier = Modifier,
                traceColor:Color=Mint,visibleWindow:ClosedFloatingPointRange<Double>?=null,
                cursorTimes:Pair<Double,Double>?=null,triggerTime:Double?=null) {
    Column(modifier.background(Panel,RoundedCornerShape(12.dp)).padding(9.dp)) {
        Text(if(trace==null) "Trace unavailable" else "${trace.quantity} · ${trace.channelId} (${trace.unit})",
            color=TextIce,fontSize=11.sp)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left=35f;val right=size.width-8f;val top=10f;val bottom=size.height-19f
            if(right<=left || bottom<=top) return@Canvas
            val samples=trace?.samples.orEmpty()
            val startTime=visibleWindow?.start ?: (samples.firstOrNull()?.timeSeconds ?: 0.0)
            val endTime=visibleWindow?.endInclusive ?: max(samples.lastOrNull()?.timeSeconds ?: 1.0,.001)
            val span=(endTime-startTime).coerceAtLeast(.001)
            val visibleSamples=if(samples.size>1 && visibleWindow!=null) buildList {
                ScopeAnalysis.valueAt(samples,startTime)?.let { add(com.indianservers.circuitssimulator.simulation.SignalSample(startTime,it)) }
                addAll(samples.filter { it.timeSeconds>startTime && it.timeSeconds<endTime })
                ScopeAnalysis.valueAt(samples,endTime)?.let { add(com.indianservers.circuitssimulator.simulation.SignalSample(endTime,it)) }
            } else samples
            val maxValue=max(1.0,visibleSamples.maxOfOrNull { abs(it.value) } ?: 1.0)
            val minValue=minOf(0.0,visibleSamples.minOfOrNull { it.value } ?: 0.0)
            val range=(maxValue-minValue).coerceAtLeast(.001)
            for(i in 0..4) {
                val x=left+(right-left)*i/4f
                drawLine(Color(0xFF203B50),Offset(x,top),Offset(x,bottom),.8f)
            }
            for(i in 0..3) {
                val y=top+(bottom-top)*i/3f
                drawLine(Color(0xFF203B50),Offset(left,y),Offset(right,y),.8f)
            }
            drawLine(Muted,Offset(left,top),Offset(left,bottom),1.5f)
            drawLine(Muted,Offset(left,bottom),Offset(right,bottom),1.5f)
            if(visibleSamples.size>1) {
                val path=Path()
                visibleSamples.forEachIndexed { index,sample ->
                    val x=left+(((sample.timeSeconds-startTime)/span)*(right-left)).toFloat()
                    val y=bottom-(((sample.value-minValue)/range)*(bottom-top)).toFloat()
                    if(index==0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                drawPath(path,traceColor.copy(alpha=.16f),style=Stroke(8f))
                drawPath(path,traceColor,style=Stroke(2.5f))
            }
            if(minValue<0.0) {
                val zero=bottom-(((0.0-minValue)/range)*(bottom-top)).toFloat()
                drawLine(Muted.copy(alpha=.5f),Offset(left,zero),Offset(right,zero),1f)
            }
            fun timeX(time:Double)=left+(((time-startTime)/span)*(right-left)).toFloat()
            if(elapsedSeconds in startTime..endTime)
                drawLine(Blue.copy(alpha=.75f),Offset(timeX(elapsedSeconds),top),Offset(timeX(elapsedSeconds),bottom),1.5f)
            triggerTime?.takeIf { it in startTime..endTime }?.let {
                drawLine(Color(0xFFFFB25E),Offset(timeX(it),top),Offset(timeX(it),bottom),2f)
            }
            cursorTimes?.let { (a,b) ->
                listOf(a to Color(0xFFFFDA75),b to Color(0xFFFF8AC8)).forEach { (time,color) ->
                    if(time in startTime..endTime)
                        drawLine(color,Offset(timeX(time),top),Offset(timeX(time),bottom),2f)
                }
            }
            drawContext.canvas.nativeCanvas.apply {
                val paint=android.graphics.Paint(3).apply {
                    color=android.graphics.Color.rgb(151,174,201);textSize=22f
                }
                drawText("${"%.1f".format(maxValue)}",2f,top+7f,paint)
                drawText("${"%.1f".format(minValue)}",2f,bottom,paint)
                drawText("${"%.0f".format(startTime*1000)} ms",left,bottom+19f,paint)
                drawText("${"%.0f".format(endTime*1000)} ms",right-95f,bottom+19f,paint)
            }
        }
    }
}
