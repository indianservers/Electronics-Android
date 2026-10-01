package com.indianservers.circuitssimulator.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

val Navy = Color(0xFF071522)
val Panel = Color(0xFF102236)
val Blue = Color(0xFF188CFF)
val Mint = Color(0xFF31E69A)
val TextIce = Color(0xFFDDEAFF)
val Muted = Color(0xFF8FA9CA)

private val bandPalette = listOf(Color(0xFF171717),Color(0xFF6D351D),Color(0xFFC9402A),
    Color(0xFFDF8236),Color(0xFFE4C548),Color(0xFF3A8F5B),Color(0xFF2876BB),
    Color(0xFF713D8C),Color(0xFF808080),Color(0xFFEAEAEA))
private fun resistorBands(ohms: Double): List<Color> {
    val r=ohms.coerceAtLeast(1.0)
    val exponent=(floor(log10(r)).toInt()-1).coerceIn(0,9)
    val significant=(r/10.0.pow(exponent)).roundToInt().coerceIn(10,99)
    return listOf(bandPalette[significant/10],bandPalette[significant%10],bandPalette[exponent],Color(0xFFCDA84B))
}

fun localTerminalOffset(p: PlacedComponent, pin: Int): Offset {
    require(pin in 0 until p.terminalCount)
    val horizontal = p.kind in setOf(Kind.RESISTOR,Kind.LDR,Kind.THERMISTOR,Kind.FUSE,Kind.SWITCH,
        Kind.DIODE,Kind.RECTIFIER,Kind.INDUCTOR,Kind.AMMETER,Kind.VOLTMETER)
    return if (p.kind == Kind.GROUND) Offset(0f, -48f)
        else if (p.kind == Kind.JUNCTION) Offset.Zero
        else if (p.kind==Kind.OPAMP || p.kind==Kind.IDEAL_OPAMP) when(pin) {
            0 -> Offset(-67f,-22f); 1 -> Offset(-67f,22f); else -> Offset(67f,0f)
        }
        else if(p.kind==Kind.POTENTIOMETER) when(pin) {
            0 -> Offset(0f,-62f); 1 -> Offset(67f,0f); else -> Offset(0f,62f)
        }
        else if (p.terminalCount==3) when(pin) {
            0 -> Offset(0f,-62f)
            1 -> Offset(-62f,0f)
            else -> Offset(0f,62f)
        }
        else if (horizontal) Offset(if (pin == 0) -67f else 67f, 0f)
        else Offset(0f, if (pin == 0) -62f else 62f)
}

fun terminalName(p: PlacedComponent, pin: Int): String = when(p.kind) {
    Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR,Kind.ELECTROLYTIC,Kind.LED -> if(pin==0) "+" else "−"
    Kind.DIODE,Kind.RECTIFIER -> if(pin==0) "A" else "K"
    Kind.NPN_BJT,Kind.PNP_BJT -> listOf("C","B","E")[pin]
    Kind.NMOS,Kind.PMOS -> listOf("D","G","S")[pin]
    Kind.OPAMP,Kind.IDEAL_OPAMP -> listOf("+","−","OUT")[pin]
    Kind.POTENTIOMETER -> listOf("A","W","B")[pin]
    Kind.GROUND -> "GND"
    Kind.JUNCTION -> ""
    else -> "${pin+1}"
}

fun terminalPosition(p: PlacedComponent, pin: Int, rotationDegrees:Float = p.rotation.toFloat()): Offset {
    val local = localTerminalOffset(p,pin)
    val rad = Math.toRadians(rotationDegrees.toDouble())
    return Offset(p.x + (local.x * cos(rad) - local.y * sin(rad)).toFloat(),
        p.y + (local.x * sin(rad) + local.y * cos(rad)).toFloat())
}

fun DrawScope.drawComponent(p: PlacedComponent, selected: Boolean, intensity: Float = 0f,
    rotationDegrees:Float = p.rotation.toFloat(), connectedPins:Set<Int> = emptySet()) {
    translate(p.x, p.y) {
        rotate(rotationDegrees, pivot = Offset.Zero) {
            if (selected) drawCircle(Blue.copy(alpha = .14f), 92f)
            when (p.kind) {
                Kind.OPAMP,Kind.IDEAL_OPAMP -> {
                    val triangle=Path().apply { moveTo(-43f,-47f);lineTo(-43f,47f);lineTo(51f,0f);close() }
                    drawPath(triangle,Panel)
                    drawPath(triangle,Color(0xFF9BDFFF),style=Stroke(3f))
                    drawLine(Color.LightGray,Offset(-67f,-22f),Offset(-43f,-22f),4f)
                    drawLine(Color.LightGray,Offset(-67f,22f),Offset(-43f,22f),4f)
                    drawLine(Color.LightGray,Offset(51f,0f),Offset(67f,0f),4f)
                    drawLine(Mint,Offset(-30f,-22f),Offset(-18f,-22f),2f)
                    drawLine(Mint,Offset(-24f,-28f),Offset(-24f,-16f),2f)
                    drawLine(Blue,Offset(-30f,22f),Offset(-18f,22f),2f)
                }
                Kind.NPN_BJT,Kind.PNP_BJT -> {
                    drawCircle(Panel,39f)
                    drawCircle(Color(0xFF9BDFFF),39f,style=Stroke(3f))
                    drawLine(Color.LightGray,Offset(-62f,0f),Offset(-16f,0f),5f)
                    drawLine(Color.LightGray,Offset(-16f,-25f),Offset(-16f,25f),5f)
                    drawLine(Color.LightGray,Offset(-16f,-13f),Offset(0f,-25f),5f)
                    drawLine(Color.LightGray,Offset(0f,-25f),Offset(0f,-62f),5f)
                    drawLine(Color.LightGray,Offset(-16f,13f),Offset(0f,25f),5f)
                    drawLine(Color.LightGray,Offset(0f,25f),Offset(0f,62f),5f)
                    val arrow=if(p.kind==Kind.NPN_BJT) 1f else -1f
                    drawLine(Mint,Offset(-arrow*10f,27f),Offset(0f,26f),4f)
                    drawLine(Mint,Offset(0f,26f),Offset(-arrow*2f,16f),4f)
                }
                Kind.NMOS,Kind.PMOS -> {
                    drawCircle(Panel,39f)
                    drawCircle(Color(0xFF9BDFFF),39f,style=Stroke(3f))
                    drawLine(Color.LightGray,Offset(-62f,0f),Offset(-22f,0f),5f)
                    drawLine(Color.LightGray,Offset(-16f,-27f),Offset(-16f,27f),4f)
                    drawLine(Color.LightGray,Offset(-2f,-28f),Offset(-2f,28f),5f)
                    drawLine(Color.LightGray,Offset(-2f,-28f),Offset(0f,-62f),5f)
                    drawLine(Color.LightGray,Offset(-2f,28f),Offset(0f,62f),5f)
                    if(p.kind==Kind.PMOS) drawCircle(Panel,6f,Offset(-24f,0f),style=Stroke(2f))
                }
                Kind.FUNCTION_GENERATOR -> {
                    drawLine(Color.LightGray,Offset(0f,-62f),Offset(0f,-38f),5f)
                    drawLine(Color.LightGray,Offset(0f,38f),Offset(0f,62f),5f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF255A83),Panel)),39f)
                    drawCircle(Color(0xFF9BDFFF),39f,style=Stroke(3f))
                    val wave=Path().apply {
                        moveTo(-25f,0f);cubicTo(-16f,-23f,-8f,-23f,0f,0f)
                        cubicTo(8f,23f,16f,23f,25f,0f)
                    }
                    drawPath(wave,Color(0xFF91E7FF),style=Stroke(4f))
                }
                Kind.BATTERY, Kind.SOURCE -> {
                    drawLine(Muted, Offset(0f, -62f), Offset(0f, 62f), 7f)
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF070D13), Color(0xFF667580), Color(0xFF161F2A))),
                        Offset(-29f, -47f), Size(58f, 94f), androidx.compose.ui.geometry.CornerRadius(8f))
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF8D410B), Color(0xFFFFCE65), Color(0xFF9C4C0D))), Offset(-29f,-47f),Size(58f,22f))
                    drawOval(Brush.horizontalGradient(listOf(Color(0xFF9C5B17),Color(0xFFFFDD85),Color(0xFF87420F))),
                        Offset(-29f,-51f),Size(58f,12f))
                    drawOval(Brush.verticalGradient(listOf(Color(0xFFF8FAFF),Color(0xFF566473))),
                        Offset(-10f,-57f),Size(20f,8f))
                    drawRect(Brush.horizontalGradient(listOf(Color.LightGray, Color.White, Color.DarkGray)), Offset(-31f,36f),Size(62f,10f))
                    drawLine(Color.White, Offset(-15f,-39f),Offset(-15f,-29f),2f)
                    drawLine(Color.White, Offset(-20f,-34f),Offset(-10f,-34f),2f)
                    drawLine(Color.White, Offset(-18f,33f),Offset(-10f,33f),2f)
                }
                Kind.RESISTOR -> {
                    drawLine(Color(0xFFC5D5E6), Offset(-67f,0f),Offset(67f,0f),6f)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE9C5), Color(0xFFAB8061), Color(0xFF5C3F30))),
                        Offset(-48f,-19f),Size(96f,38f),androidx.compose.ui.geometry.CornerRadius(13f))
                    val colors = resistorBands(p.value("resistance"))
                    listOf(-28f,-10f,10f,32f).forEachIndexed { index, x ->
                        drawRect(colors[index], Offset(x,-18f), Size(7f,36f))
                    }
                }
                Kind.POTENTIOMETER -> {
                    drawLine(Color(0xFFC5D5E6),Offset(0f,-62f),Offset(0f,62f),5f)
                    drawRoundRect(Panel,Offset(-18f,-40f),Size(36f,80f),
                        androidx.compose.ui.geometry.CornerRadius(9f))
                    drawRoundRect(Color(0xFFCDA36A),Offset(-18f,-40f),Size(36f,80f),
                        androidx.compose.ui.geometry.CornerRadius(9f),style=Stroke(3f))
                    val wiperY=-35f+70f*p.value("position").toFloat()
                    drawLine(Mint,Offset(67f,0f),Offset(47f,0f),5f)
                    drawLine(Mint,Offset(47f,0f),Offset(24f,wiperY),5f)
                    drawLine(Mint,Offset(26f,wiperY-8f),Offset(13f,wiperY),4f)
                    drawLine(Mint,Offset(26f,wiperY+8f),Offset(13f,wiperY),4f)
                }
                Kind.LDR,Kind.THERMISTOR -> {
                    drawLine(Color(0xFFC5D5E6),Offset(-67f,0f),Offset(67f,0f),5f)
                    drawRoundRect(Panel,Offset(-40f,-20f),Size(80f,40f),
                        androidx.compose.ui.geometry.CornerRadius(9f))
                    drawRoundRect(if(p.kind==Kind.LDR) Color(0xFFFFCF70) else Color(0xFF6DD6E9),
                        Offset(-40f,-20f),Size(80f,40f),
                        androidx.compose.ui.geometry.CornerRadius(9f),style=Stroke(3f))
                    val zig=Path().apply { moveTo(-30f,0f);lineTo(-20f,-11f);lineTo(-10f,11f)
                        lineTo(0f,-11f);lineTo(10f,11f);lineTo(20f,-11f);lineTo(30f,0f) }
                    drawPath(zig,TextIce,style=Stroke(3f))
                    if(p.kind==Kind.LDR) {
                        drawLine(Color(0xFFFFCF70),Offset(-35f,-39f),Offset(-18f,-24f),3f)
                        drawLine(Color(0xFFFFCF70),Offset(-8f,-39f),Offset(9f,-24f),3f)
                    }
                }
                Kind.FUSE -> {
                    drawLine(Color(0xFFC5D5E6),Offset(-67f,0f),Offset(67f,0f),5f)
                    drawRoundRect(Color(0xFFDDE7EE),Offset(-37f,-18f),Size(74f,36f),
                        androidx.compose.ui.geometry.CornerRadius(8f))
                    drawRoundRect(Color(0xFF243B4B),Offset(-28f,-14f),Size(56f,28f),
                        androidx.compose.ui.geometry.CornerRadius(5f))
                    drawLine(Color(0xFFFFC071),Offset(-25f,0f),Offset(25f,0f),4f)
                }
                Kind.SWITCH -> {
                    drawLine(Color(0xFFAABBCF),Offset(-67f,0f),Offset(-43f,0f),5f)
                    drawLine(Color(0xFFAABBCF),Offset(43f,0f),Offset(67f,0f),5f)
                    drawCircle(Color(0xFFB8CEE8), 12f, Offset(-42f,0f))
                    drawCircle(Color(0xFFB8CEE8), 12f, Offset(42f,0f))
                    drawLine(Color(0xFFE1ECFB),Offset(-42f,0f),if (p.closed) Offset(42f,0f) else Offset(22f,-36f),7f)
                }
                Kind.LED -> {
                    val light = Color(0xFFFF454C)
                    if (intensity > .01f) drawCircle(Brush.radialGradient(listOf(light.copy(alpha = intensity * .42f),Color.Transparent)),75f)
                    drawLine(Color(0xFFCDDAE9),Offset(-10f,13f),Offset(-10f,62f),5f)
                    drawLine(Color(0xFFCDDAE9),Offset(10f,13f),Offset(10f,62f),5f)
                    drawLine(Color(0xFFCDDAE9),Offset(0f,-62f),Offset(0f,-30f),5f)
                    drawRoundRect(Brush.verticalGradient(listOf(light.copy(alpha=.9f),light.copy(alpha=.55f),Color(0xFF4E1420))),
                        Offset(-24f,-43f),Size(48f,62f),androidx.compose.ui.geometry.CornerRadius(22f))
                    drawCircle(Color.White.copy(alpha=.55f),7f,Offset(-9f,-26f))
                    drawRect(light,Offset(-29f,13f),Size(58f,10f))
                }
                Kind.DIODE,Kind.RECTIFIER -> {
                    drawLine(Color(0xFFC7D3DF),Offset(-67f,0f),Offset(67f,0f),5f)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5B6470),Color(0xFF111820),Color(0xFF05080D))),
                        Offset(-39f,-17f),Size(78f,34f),androidx.compose.ui.geometry.CornerRadius(7f))
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF71808F),Color(0xFFE2E8EC),Color(0xFF8793A0))),
                        Offset(25f,-17f),Size(8f,34f))
                    drawLine(Color.White.copy(alpha=.22f),Offset(-28f,-10f),Offset(20f,-10f),2f)
                }
                Kind.LAMP -> {
                    val glow = Color(0xFFFFC777)
                    if (intensity > .01f) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha=intensity*.45f),Color.Transparent)),80f)
                    drawLine(Color(0xFFBFCADB),Offset(0f,-62f),Offset(0f,-43f),5f)
                    drawLine(Color(0xFFBFCADB),Offset(0f,39f),Offset(0f,62f),5f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE8AD),Color(0xFFAD6831),Color(0xFF765A3B))),38f,Offset(0f,-8f))
                    drawCircle(Color(0xFFFFF2CE).copy(alpha=.7f),38f,Offset(0f,-8f),style=Stroke(3f))
                    drawLine(Color(0xFFFFF5D5),Offset(-12f,-13f),Offset(0f,1f),3f)
                    drawLine(Color(0xFFFFF5D5),Offset(0f,1f),Offset(12f,-13f),3f)
                    drawRect(Brush.horizontalGradient(listOf(Color.DarkGray,Color.LightGray,Color.DarkGray)),Offset(-20f,27f),Size(40f,19f))
                }
                Kind.CAPACITOR, Kind.ELECTROLYTIC -> {
                    drawLine(Color.LightGray,Offset(0f,-62f),Offset(0f,-37f),5f)
                    drawLine(Color.LightGray,Offset(0f,36f),Offset(0f,62f),5f)
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF0043A2),Color(0xFF178DFF),Color(0xFF073773))),
                        Offset(-28f,-37f),Size(56f,74f),androidx.compose.ui.geometry.CornerRadius(9f))
                    drawOval(Brush.horizontalGradient(listOf(Color(0xFF4E6B87),Color(0xFFD7F0FF),Color(0xFF315474))),
                        Offset(-28f,-41f),Size(56f,11f))
                    drawRoundRect(Color(0xFF0856B0),Offset(-30f,27f),Size(60f,10f),
                        androidx.compose.ui.geometry.CornerRadius(3f))
                    drawLine(Color.White.copy(alpha=.25f),Offset(-17f,-23f),Offset(-17f,20f),3f)
                    drawLine(Color.White,Offset(-10f,-29f),Offset(10f,-29f),3f)
                    if (p.kind == Kind.ELECTROLYTIC) drawLine(Color.White,Offset(0f,-39f),Offset(0f,-19f),3f)
                }
                Kind.INDUCTOR -> {
                    drawLine(Color.LightGray,Offset(-67f,0f),Offset(-48f,0f),5f)
                    drawLine(Color.LightGray,Offset(48f,0f),Offset(67f,0f),5f)
                    for (i in -4..4) drawCircle(Color(0xFFCE793A),10f,Offset(i*11f,0f),style=Stroke(5f))
                }
                Kind.GROUND -> {
                    drawLine(Color.LightGray,Offset(0f,-48f),Offset(0f,-9f),5f)
                    drawLine(Color.LightGray,Offset(-31f,-8f),Offset(31f,-8f),5f)
                    drawLine(Color.LightGray,Offset(-21f,3f),Offset(21f,3f),5f)
                    drawLine(Color.LightGray,Offset(-10f,14f),Offset(10f,14f),5f)
                }
                Kind.JUNCTION -> {
                    drawCircle(Blue.copy(alpha=.18f),20f)
                    drawCircle(Color(0xFFE5F3FF),8f)
                }
                Kind.AMMETER, Kind.VOLTMETER -> {
                    drawLine(Color.LightGray,Offset(-67f,0f),Offset(67f,0f),5f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF30445A),Color(0xFF081321))),31f)
                    drawCircle(Color(0xFFB2C5D9),31f,style=Stroke(4f))
                    val path = Path().apply {
                        if (p.kind == Kind.AMMETER) { moveTo(-11f,13f); lineTo(0f,-14f); lineTo(11f,13f); moveTo(-7f,4f); lineTo(7f,4f) }
                        else { moveTo(-12f,-11f); lineTo(0f,13f); lineTo(12f,-11f) }
                    }
                    drawPath(path,TextIce,style=Stroke(4f))
                }
            }
            for (pin in 0 until p.terminalCount) {
                val pos = localTerminalOffset(p,pin)
                val connected = pin in connectedPins
                val color = if(connected) Mint else Blue
                drawCircle(color.copy(alpha=if(selected) .23f else .14f),if(selected) 20f else 16f,pos)
                drawCircle(color,if(selected) 12f else 10f,pos)
                drawCircle(Color.White,if(selected) 5f else 4f,pos)
                val name=terminalName(p,pin)
                if(name.isNotEmpty()) {
                    val paint=android.graphics.Paint(3).apply {
                        this.color=android.graphics.Color.rgb(221,234,255)
                        textSize=18f
                        textAlign=android.graphics.Paint.Align.CENTER
                    }
                    val horizontal=pos.x!=0f
                    val labelX=if(horizontal) pos.x else pos.x+25f
                    val labelY=if(horizontal) pos.y-17f else pos.y+6f
                    drawContext.canvas.nativeCanvas.drawText(name,labelX,labelY,paint)
                }
            }
        }
    }
}
