package com.indianservers.circuitssimulator.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.EngineeringUnits
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.simulation.CircuitDiagnostics
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.ui.canvas.terminalPosition
import java.io.OutputStream
import java.util.Locale
import kotlin.math.min

/** Self-contained overview and measurement report for a circuit snapshot. */
object CircuitReport {
    private const val WIDTH=1600
    private const val HEIGHT=1000

    fun renderBitmap(circuit:Circuit,result:DcResult):Bitmap {
        val bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.rgb(7,21,34))
        val parts=circuit.components
        if(parts.isEmpty()) {
            val paint=Paint(3).apply { color=Color.WHITE;textSize=48f }
            canvas.drawText("Empty circuit",80f,120f,paint)
            return bitmap
        }
        val minX=parts.minOf { it.x }-110f;val maxX=parts.maxOf { it.x }+110f
        val minY=parts.minOf { it.y }-100f;val maxY=parts.maxOf { it.y }+100f
        val scale=min((WIDTH-100f)/(maxX-minX),(HEIGHT-110f)/(maxY-minY))
        canvas.translate((WIDTH-(maxX-minX)*scale)/2f-minX*scale,
            (HEIGHT-(maxY-minY)*scale)/2f-minY*scale)
        canvas.scale(scale,scale)
        val paint=Paint(3).apply { strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        val byId=parts.associateBy { it.id }
        paint.color=Color.rgb(53,169,235);paint.strokeWidth=5f;paint.style=Paint.Style.STROKE
        circuit.wires.forEach { wire ->
            val a=byId[wire.start.componentId]?.let { terminalPosition(it,wire.start.index) }
            val b=byId[wire.end.componentId]?.let { terminalPosition(it,wire.end.index) }
            if(a!=null && b!=null) {
                canvas.drawLine(a.x,a.y,b.x,a.y,paint)
                canvas.drawLine(b.x,a.y,b.x,b.y,paint)
                wire.label?.let { label ->
                    val x=if(kotlin.math.abs(b.x-a.x)>=kotlin.math.abs(b.y-a.y)) (a.x+b.x)/2f else b.x
                    val y=if(kotlin.math.abs(b.x-a.x)>=kotlin.math.abs(b.y-a.y)) a.y else (a.y+b.y)/2f
                    paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.textSize=19f
                    val width=paint.measureText(label)+16f
                    paint.color=Color.rgb(25,62,85)
                    canvas.drawRoundRect(x-width/2f,y-28f,x+width/2f,y+6f,7f,7f,paint)
                    paint.color=Color.rgb(175,228,255)
                    canvas.drawText(label,x,y-4f,paint)
                    paint.color=Color.rgb(53,169,235);paint.style=Paint.Style.STROKE
                }
            }
        }
        parts.forEach { part ->
            if(part.kind==Kind.JUNCTION) return@forEach
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(24,48,68)
            canvas.drawRoundRect(part.x-68f,part.y-38f,part.x+68f,part.y+38f,13f,13f,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=Color.rgb(106,198,245)
            canvas.drawRoundRect(part.x-68f,part.y-38f,part.x+68f,part.y+38f,13f,13f,paint)
            paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER
            paint.color=Color.rgb(230,242,255);paint.textSize=22f
            canvas.drawText(part.reference,part.x,part.y-6f,paint)
            paint.color=Color.rgb(170,202,225);paint.textSize=17f
            canvas.drawText(part.kind.title.take(15),part.x,part.y+20f,paint)
            paint.color=Color.rgb(125,197,241)
            repeat(part.terminalCount) { pin ->
                val point=terminalPosition(part,pin)
                canvas.drawCircle(point.x,point.y,7f,paint)
            }
        }
        return bitmap
    }

    fun writePng(output:OutputStream,circuit:Circuit,result:DcResult) {
        val bitmap=renderBitmap(circuit,result)
        try { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) { "PNG encoding failed" } }
        finally { bitmap.recycle() }
    }

    fun writePdf(output:OutputStream,circuit:Circuit,result:DcResult) {
        val document=PdfDocument()
        val paint=Paint(3)
        var pageNumber=0
        var page:PdfDocument.Page?=null
        var y=0f
        fun newPage(title:String) {
            page?.let(document::finishPage)
            pageNumber++
            page=document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
            val canvas=page!!.canvas
            canvas.drawColor(Color.WHITE)
            paint.color=Color.rgb(14,37,58);paint.textSize=18f;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(title,30f,42f,paint)
            paint.typeface=android.graphics.Typeface.DEFAULT
            y=65f
        }
        fun line(value:String,color:Int=Color.rgb(35,51,68)) {
            if(y>804f) newPage("${circuit.name} · continued")
            paint.color=color;paint.textSize=10f
            page!!.canvas.drawText(value.take(105),30f,y,paint)
            y+=16f
        }
        try {
            newPage(circuit.name.take(48))
            line("Components ${circuit.components.size}   Wires ${circuit.wires.size}   Analysis ${circuit.settings.analysis}")
            line("Tolerance ${circuit.settings.tolerance}   Max iterations ${circuit.settings.maxIterations}")
            result.error?.let { line("Solver: $it",Color.RED) }
            val bitmap=renderBitmap(circuit,result)
            try {
                val destination=android.graphics.Rect(30,115,565,445)
                page!!.canvas.drawBitmap(bitmap,null,destination,paint)
            } finally { bitmap.recycle() }
            y=465f
            line("COMPONENT READINGS",Color.rgb(14,37,58))
            circuit.components.filter { it.kind!=Kind.JUNCTION }.forEach { part ->
                val reading=result.readings[part.id]
                val values=if(reading==null) "—" else
                    "${EngineeringUnits.format(reading.voltage,"V")}   "+
                        "${EngineeringUnits.format(reading.current,"A")}   "+
                        EngineeringUnits.format(reading.power,"W")
                line(String.format(Locale.US,"%-8s %-25s %s",part.reference,part.kind.title,values))
            }
            val issues=CircuitDiagnostics.inspect(circuit,result)
            if(issues.isNotEmpty()) {
                y+=8f
                line("DIAGNOSTICS",Color.rgb(14,37,58))
                issues.forEach { line("${it.level}: ${it.message}") }
            }
            page?.let(document::finishPage)
            page=null
            document.writeTo(output)
        } finally { document.close() }
    }
}
