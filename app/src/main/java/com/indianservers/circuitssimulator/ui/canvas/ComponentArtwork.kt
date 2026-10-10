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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.indianservers.circuitssimulator.domain.EngineeringUnits
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.PlacedComponent
import com.indianservers.circuitssimulator.domain.BoardRegistry
import com.indianservers.circuitssimulator.domain.PinCapability
import com.indianservers.circuitssimulator.domain.PinType
import com.indianservers.circuitssimulator.domain.DigitalParts
import com.indianservers.circuitssimulator.domain.ElectromechanicalParts
import com.indianservers.circuitssimulator.domain.SemiconductorSwitchParts
import com.indianservers.circuitssimulator.domain.IcParts
import com.indianservers.circuitssimulator.domain.PhaseFiveParts
import com.indianservers.circuitssimulator.domain.isBoard
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
val Supply = Color(0xFFFFB44D)
val Return = Color(0xFF55ADFF)
val TextIce = Color(0xFFDDEAFF)
val Muted = Color(0xFF8FA9CA)

private val bandPalette = listOf(Color(0xFF171717),Color(0xFF6D351D),Color(0xFFC9402A),
    Color(0xFFDF8236),Color(0xFFE4C548),Color(0xFF3A8F5B),Color(0xFF2876BB),
    Color(0xFF713D8C),Color(0xFF808080),Color(0xFFEAEAEA))
internal fun resistorBands(ohms: Double,tolerance:Double=5.0): List<Color> {
    val r=ohms.coerceAtLeast(1.0)
    val exponent=(floor(log10(r)).toInt()-1).coerceIn(-2,9)
    val significant=(r/10.0.pow(exponent)).roundToInt().coerceIn(10,99)
    return listOf(bandPalette[significant/10],bandPalette[significant%10],if(exponent>=0) bandPalette[exponent] else if(exponent == -1) Color(0xFFCDA84B) else Color.LightGray,
        when { tolerance<=1.0 -> bandPalette[1];tolerance<=2.0 -> bandPalette[2];tolerance<=5.0 -> Color(0xFFCDA84B);else -> Color.LightGray })
}

fun localTerminalOffset(p: PlacedComponent, pin: Int): Offset {
    require(pin in 0 until p.terminalCount)
    if(p.kind.isBoard) {
        val header=BoardRegistry.boards.getValue(p.kind).pins[pin]
        if(header.nx!=null && header.ny!=null) return Offset(header.nx,header.ny)*p.sizeScale
        val left=(p.terminalCount+1)/2
        val leftSide=pin<left
        val count=if(leftSide) left else p.terminalCount-left
        val index=if(leftSide) pin else p.terminalCount-1-pin
        return Offset(if(leftSide) -122f else 122f,
            (index-(count-1)/2f)*13f)*p.sizeScale
    }
    if(p.kind in DigitalParts.pinNames) {
        if(p.kind==Kind.LOGIC_INPUT) return Offset(67f,0f)*p.sizeScale
        if(p.kind==Kind.LOGIC_OUTPUT) return Offset(-67f,0f)*p.sizeScale
        val outputs=DigitalParts.outputs(p.kind)
        val isOutput=pin in outputs
        val count=if(isOutput) outputs.count() else p.terminalCount-outputs.count()
        val order=if(isOutput) pin-outputs.first else pin
        return Offset(if(isOutput) 67f else -67f,
            (order-(count-1)/2f)*30f)*p.sizeScale
    }
    if(p.kind==Kind.DIP_SWITCH_4) return Offset(if(pin%2==0) -67f else 67f,
        -45f+(pin/2)*30f)*p.sizeScale
    if(p.kind==Kind.DPDT_RELAY) return Offset(if(pin<2) -67f else 67f,
        if(pin<2) if(pin==0) -25f else 25f else -75f+(pin-2)*30f)*p.sizeScale
    if(p.kind in IcParts.pinNames) {
        val left=(p.terminalCount+1)/2
        val leftSide=pin<left
        val count=if(leftSide) left else p.terminalCount-left
        val row=if(leftSide) pin else p.terminalCount-1-pin
        val spacing=if(p.terminalCount>=16) 25f else 30f
        return Offset(if(leftSide) -96f else 96f,
            (row-(count-1)/2f)*spacing)*p.sizeScale
    }
    val horizontal = p.kind in setOf(Kind.RESISTOR,Kind.RHEOSTAT,Kind.LDR,Kind.THERMISTOR,
        Kind.PTC_THERMISTOR,Kind.FUSE,Kind.SWITCH,Kind.NC_PUSH_BUTTON,
        Kind.DIODE,Kind.RECTIFIER,Kind.ZENER,Kind.SCHOTTKY_DIODE,Kind.PHOTODIODE,
        Kind.PUSH_BUTTON,Kind.DC_MOTOR,
        Kind.INDUCTOR,Kind.AMMETER,Kind.VOLTMETER)
    val unscaled=if (p.kind == Kind.GROUND) Offset(0f, -48f)
        else if (p.kind == Kind.JUNCTION) Offset.Zero
        else if (p.kind == Kind.CLOCK) Offset(67f,0f)
        else if (p.kind==Kind.RELAY) when(pin) {
            0 -> Offset(-67f,-28f); 1 -> Offset(-67f,28f)
            2 -> Offset(67f,0f); 3 -> Offset(67f,-34f); else -> Offset(67f,34f)
        }
        else if (p.kind == Kind.NOT_GATE) if(pin==0) Offset(-67f,0f) else Offset(67f,0f)
        else if(p.terminalCount==8) Offset(if(pin<4) -67f else 67f,
            (-45f+(pin%4)*30f))
        else if(p.terminalCount==4) Offset(if(pin<2) -67f else 67f,
            if(pin%2==0) -25f else 25f)
        else if(p.terminalCount==5) Offset(if(pin==0) -67f else 67f,
            if(pin==0) 0f else -45f+(pin-1)*30f)
        else if (p.kind.category=="Digital") when(pin) {
            0 -> Offset(-67f,-22f); 1 -> Offset(-67f,22f); else -> Offset(67f,0f)
        }
        else if (p.kind in setOf(Kind.LED,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED))
            Offset(if(pin==0) -29f else 29f,62f)
        else if (p.kind==Kind.OPAMP || p.kind==Kind.IDEAL_OPAMP) when(pin) {
            0 -> Offset(-67f,-22f); 1 -> Offset(-67f,22f); else -> Offset(67f,0f)
        }
        else if(p.kind==Kind.POTENTIOMETER) when(pin) {
            0 -> Offset(0f,-62f); 1 -> Offset(67f,0f); else -> Offset(0f,62f)
        }
        else if(p.kind in PhaseFiveParts.pinNames && p.terminalCount==4) when(pin) {
            0 -> Offset(-67f,-22f); 1 -> Offset(-67f,22f)
            2 -> Offset(67f,-22f); else -> Offset(67f,22f)
        }
        else if(p.kind in PhaseFiveParts.pinNames && p.terminalCount==6) when(pin) {
            0 -> Offset(-67f,-30f); 1 -> Offset(-67f,0f); 2 -> Offset(-67f,30f)
            3 -> Offset(67f,-30f); 4 -> Offset(67f,0f); else -> Offset(67f,30f)
        }
        else if (p.terminalCount==3) when(pin) {
            0 -> Offset(0f,-62f)
            1 -> Offset(-62f,0f)
            else -> Offset(0f,62f)
        }
        else if (horizontal) Offset(if (pin == 0) -67f else 67f, 0f)
        else Offset(0f, if (pin == 0) -62f else 62f)
    return unscaled*p.sizeScale
}

fun terminalName(p: PlacedComponent, pin: Int): String = when {
    p.kind.isBoard -> BoardRegistry.boards.getValue(p.kind).pins[pin].name
    else -> terminalNameStandard(p,pin)
}

private fun terminalNameStandard(p:PlacedComponent,pin:Int):String = when(p.kind) {
    in DigitalParts.pinNames.keys -> DigitalParts.pinNames.getValue(p.kind)[pin]
    in ElectromechanicalParts.pinNames.keys -> ElectromechanicalParts.pinNames.getValue(p.kind)[pin]
    in SemiconductorSwitchParts.pinNames.keys -> SemiconductorSwitchParts.pinNames.getValue(p.kind)[pin]
    in IcParts.pinNames.keys -> IcParts.pinNames.getValue(p.kind)[pin]
    in PhaseFiveParts.pinNames.keys -> PhaseFiveParts.pinNames.getValue(p.kind)[pin]
    Kind.BATTERY,Kind.SOURCE,Kind.FUNCTION_GENERATOR,Kind.ELECTROLYTIC,Kind.LED,
    Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED,Kind.SINGLE_CELL,Kind.BATTERY_PACK,
    Kind.VARIABLE_DC_SUPPLY,Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,
    Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR,Kind.DC_CURRENT_SOURCE -> if(pin==0) "+" else "−"
    Kind.DIODE,Kind.RECTIFIER,Kind.SCHOTTKY_DIODE,Kind.PHOTODIODE -> if(pin==0) "A" else "K"
    Kind.ZENER -> if(pin==0) "A" else "K"
    Kind.VCCS,Kind.VCVS,Kind.CCCS,Kind.CCVS -> listOf("C+","C−","O+","O−")[pin]
    Kind.TRANSFORMER -> listOf("P+","P−","S+","S−")[pin]
    Kind.RELAY -> listOf("C+","C−","COM","NO","NC")[pin]
    Kind.RGB_LED -> listOf("R","G","B","K")[pin]
    Kind.SPDT_SWITCH -> listOf("COM","A","B")[pin]
    Kind.TIMER_555 -> listOf("RESET","GND","OUT")[pin]
    Kind.D_FLIP_FLOP -> listOf("D","CLK","Q")[pin]
    Kind.T_FLIP_FLOP -> listOf("T","CLK","Q")[pin]
    Kind.COUNTER_4 -> listOf("CLK","Q0","Q1","Q2","Q3")[pin]
    Kind.ADC_2 -> listOf("VIN","GND","D0","D1")[pin]
    Kind.DAC_2 -> listOf("D0","D1","OUT","GND")[pin]
    Kind.SEVEN_SEGMENT -> listOf("A","B","C","D","E","F","G","K")[pin]
    Kind.NPN_BJT,Kind.PNP_BJT -> listOf("C","B","E")[pin]
    Kind.NMOS,Kind.PMOS -> listOf("D","G","S")[pin]
    Kind.OPAMP,Kind.IDEAL_OPAMP -> listOf("+","−","OUT")[pin]
    Kind.POTENTIOMETER -> listOf("A","W","B")[pin]
    Kind.CLOCK -> "OUT"
    Kind.NOT_GATE -> if(pin==0) "IN" else "OUT"
    Kind.AND_GATE,Kind.OR_GATE,Kind.NAND_GATE,Kind.NOR_GATE,Kind.XOR_GATE,Kind.XNOR_GATE ->
        listOf("A","B","OUT")[pin]
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
    rotationDegrees:Float = p.rotation.toFloat(), connectedPins:Set<Int> = emptySet(),
    activeMask:Int = 0, visual:ComponentVisualState = ComponentVisualState(brightness=intensity),
    details:Boolean = true) {
    translate(p.x, p.y) {
        rotate(rotationDegrees, pivot = Offset.Zero) {
          scale(p.sizeScale,p.sizeScale,pivot=Offset.Zero) {
            if (selected) {
                val board=BoardRegistry.boards[p.kind]
                if(board!=null) drawRoundRect(Blue.copy(alpha=.65f),
                    Offset(-board.boardWidth/2f-3f,-board.boardHeight/2f-3f),
                    Size(board.boardWidth+6f,board.boardHeight+6f),androidx.compose.ui.geometry.CornerRadius(8f),style=Stroke(2f))
                else drawCircle(Blue.copy(alpha=.32f),70f,style=Stroke(2f),center=Offset.Zero)
            }
            if(!drawPhysicalBody(p,visual.copy(segmentMask=activeMask),details)) when (p.kind) {
                Kind.BATTERY, Kind.SOURCE,Kind.SINGLE_CELL,Kind.BATTERY_PACK,
                Kind.VARIABLE_DC_SUPPLY -> {
                    // A shaded battery pack keeps the two electrical connections visible at any zoom.
                    drawLine(Supply,Offset(0f,-62f),Offset(0f,-49f),6f)
                    drawLine(Return,Offset(0f,49f),Offset(0f,62f),6f)
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF080D14),Color(0xFF38424D),Color(0xFF0A111A))),
                        Offset(-34f,-44f),Size(68f,89f),androidx.compose.ui.geometry.CornerRadius(8f))
                    drawRoundRect(Color(0xFF8D9AA6),Offset(-34f,-44f),Size(68f,89f),
                        androidx.compose.ui.geometry.CornerRadius(8f),style=Stroke(2f))
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFAD531B),Color(0xFFFFC46B),Color(0xFF8F3E13))),
                        Offset(-34f,-45f),Size(68f,26f),androidx.compose.ui.geometry.CornerRadius(5f))
                    drawOval(Brush.horizontalGradient(listOf(Color(0xFF8C470E),Color(0xFFFFD782),Color(0xFF8C470E))),
                        Offset(-34f,-49f),Size(68f,10f))
                    drawOval(Brush.verticalGradient(listOf(Color(0xFFF8FCFF),Color(0xFF7D8996))),
                        Offset(-11f,-55f),Size(22f,9f))
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF59636D),Color(0xFFE6E9EB),Color(0xFF53606C))),
                        Offset(-35f,38f),Size(70f,11f),androidx.compose.ui.geometry.CornerRadius(3f))
                    drawRoundRect(Color(0xFFCF8A35),Offset(-23f,-6f),Size(46f,28f),
                        androidx.compose.ui.geometry.CornerRadius(4f))
                    drawRoundRect(Color(0xFF1A2530),Offset(-21f,-4f),Size(42f,24f),
                        androidx.compose.ui.geometry.CornerRadius(3f))
                    val sourceLabel=when(p.kind) {
                        Kind.SINGLE_CELL -> "CELL";Kind.BATTERY_PACK -> "PACK"
                        Kind.VARIABLE_DC_SUPPLY -> "DC";else -> EngineeringUnits.format(p.value("voltage"),"V")
                    }
                    drawContext.canvas.nativeCanvas.drawText(sourceLabel,0f,14f,android.graphics.Paint(3).apply {
                        color=android.graphics.Color.WHITE;textSize=14f;textAlign=android.graphics.Paint.Align.CENTER
                        typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
                    })
                    drawLine(Color.White.copy(alpha=.23f),Offset(-26f,-16f),Offset(-26f,31f),3f)
                }
                Kind.RESISTOR,Kind.RHEOSTAT -> {
                    drawLine(Color(0xFFC5D5E6), Offset(-67f,0f),Offset(67f,0f),6f)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE9C5), Color(0xFFAB8061), Color(0xFF5C3F30))),
                        Offset(-48f,-19f),Size(96f,38f),androidx.compose.ui.geometry.CornerRadius(13f))
                    val colors = resistorBands(if(p.kind==Kind.RHEOSTAT)
                        p.value("resistance")*p.value("position")+p.value("contactResistance")
                        else p.value("resistance"),p.value("tolerance").takeIf { it>0 } ?: 5.0)
                    listOf(-28f,-10f,10f,32f).forEachIndexed { index, x ->
                        drawRect(colors[index], Offset(x,-18f), Size(7f,36f))
                    }
                }
                Kind.FUSE -> {
                    drawLine(Color(0xFFC5D5E6),Offset(-67f,0f),Offset(67f,0f),5f)
                    drawRoundRect(Color(0xFFDDE7EE),Offset(-37f,-18f),Size(74f,36f),
                        androidx.compose.ui.geometry.CornerRadius(8f))
                    drawRoundRect(Color(0xFF243B4B),Offset(-28f,-14f),Size(56f,28f),
                        androidx.compose.ui.geometry.CornerRadius(5f))
                    if(visual.failed) {
                        drawLine(Color(0xFF6F6260),Offset(-25f,0f),Offset(-7f,-2f),3f)
                        drawLine(Color(0xFF6F6260),Offset(7f,2f),Offset(25f,0f),3f)
                    } else drawLine(Color(0xFFFFC071),Offset(-25f,0f),Offset(25f,0f),4f)
                }
                Kind.LED,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> {
                    val light = when(p.kind) {
                        Kind.GREEN_LED -> Color(0xFF43EE79)
                        Kind.BLUE_LED -> Color(0xFF4299FF)
                        else -> Color(0xFFFF454C)
                    }
                    val on=intensity>.001f && !visual.failed
                    val emission=if(on) (.55f+.45f*kotlin.math.sqrt(intensity.coerceIn(0f,1f))) else 0f
                    val pulse=ledGlowPulse(visual)
                    if(on) {
                        // A steady luminous lens with a small breathing halo: never flashes dark while powered.
                        drawCircle(Brush.radialGradient(listOf(light.copy(alpha=emission*.65f*pulse),
                            light.copy(alpha=emission*.22f*pulse),Color.Transparent),
                            center=Offset(0f,-18f),radius=68f+7f*pulse),
                            68f+7f*pulse,center=Offset(0f,-18f))
                        drawCircle(light.copy(alpha=emission*.35f*pulse),35f,Offset(0f,-18f))
                    }
                    // The two drawn leads terminate at the two actual electrical pins.
                    drawLine(Color(0xFFD5E0E9),Offset(-29f,10f),Offset(-29f,62f),6f)
                    drawLine(Color(0xFF9BABB9),Offset(29f,10f),Offset(29f,62f),6f)
                    drawLine(Color.White.copy(alpha=.55f),Offset(-31f,19f),Offset(-31f,56f),2f)
                    val lens=if(on) listOf(androidx.compose.ui.graphics.lerp(light,Color.White,.45f),
                        light,androidx.compose.ui.graphics.lerp(light,Color(0xFF342127),.28f))
                    else listOf(androidx.compose.ui.graphics.lerp(Color(0xFF283542),light,.24f),
                        androidx.compose.ui.graphics.lerp(Color(0xFF15212D),light,.15f),Color(0xFF121D28))
                    drawRoundRect(Brush.verticalGradient(lens),
                        Offset(-31f,-53f),Size(62f,70f),androidx.compose.ui.geometry.CornerRadius(29f))
                    drawRoundRect(light.copy(alpha=if(on) .85f else .25f),Offset(-34f,10f),Size(68f,10f),
                        androidx.compose.ui.geometry.CornerRadius(3f))
                    drawRect(Color(0xFFB9C3CC).copy(alpha=.5f),Offset(-13f,-8f),Size(26f,12f))
                    drawLine(Color(0xFFCFD7DC),Offset(-11f,-5f),Offset(9f,-16f),1.3f)
                    drawCircle(Color.White.copy(alpha=if(on) .95f else .12f),9f,Offset(-13f,-33f))
                    if(on) drawCircle(Color(0xFFFFF9E9).copy(alpha=emission*.9f),10f,Offset(0f,-13f))
                }
                Kind.LAMP -> {
                    val glow = Color(0xFFFFC777)
                    if (intensity > .01f) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha=intensity*.45f),Color.Transparent)),80f,center=Offset.Zero)
                    drawLine(Color(0xFFBFCADB),Offset(0f,-62f),Offset(0f,-43f),5f)
                    drawLine(Color(0xFFBFCADB),Offset(0f,39f),Offset(0f,62f),5f)
                    drawCircle(Color(0xFFB7C5CC).copy(alpha=.25f+intensity*.3f),38f,Offset(0f,-8f))
                    drawCircle(Color(0xFFFFF2CE).copy(alpha=.7f),38f,Offset(0f,-8f),style=Stroke(3f))
                    drawLine(if(intensity>.01f) glow.copy(alpha=.4f+intensity*.6f) else Color(0xFF77828A),Offset(-12f,-13f),Offset(0f,1f),3f)
                    drawLine(if(intensity>.01f) glow.copy(alpha=.4f+intensity*.6f) else Color(0xFF77828A),Offset(0f,1f),Offset(12f,-13f),3f)
                    drawRect(Brush.horizontalGradient(listOf(Color.DarkGray,Color.LightGray,Color.DarkGray)),Offset(-20f,27f),Size(40f,19f))
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
                    drawCircle(Blue.copy(alpha=.18f),20f,center=Offset.Zero)
                    drawCircle(Color(0xFFE5F3FF),8f,center=Offset.Zero)
                }
                Kind.SOLENOID -> {
                    drawRoundRect(Color(0xFF3F5266),Offset(-44f,-31f),Size(88f,62f),
                        androidx.compose.ui.geometry.CornerRadius(7f))
                    for(i in -3..3) drawCircle(Color(0xFFD58142),10f,Offset(i*11f,0f),style=Stroke(4f))
                    drawRect(Color(0xFFBECBD8),Offset(23f+intensity*12f,-10f),Size(35f,20f))
                }
                Kind.DIP_SWITCH_4 -> {
                    drawRoundRect(Color(0xFF283D52),Offset(-44f,-59f),Size(88f,118f),
                        androidx.compose.ui.geometry.CornerRadius(8f))
                    (0..3).forEach { i ->
                        val y=-45f+i*30f
                        drawRoundRect(if(p.value("switch${i+1}")>=.5) Mint else Color(0xFFB8C4D3),
                            Offset(if(p.value("switch${i+1}")>=.5) 1f else -20f,y-9f),Size(19f,18f),
                            androidx.compose.ui.geometry.CornerRadius(3f))
                    }
                }
                else -> error("Missing physical renderer for ${p.kind}")
            }
            for (pin in 0 until p.terminalCount) {
                if(p.kind.isBoard && !BoardRegistry.boards.getValue(p.kind).pins[pin].connectable) continue
                val pos = localTerminalOffset(p,pin)/p.sizeScale
                val connected = pin in connectedPins
                val color = when {
                    p.kind==Kind.BATTERY || p.kind==Kind.SOURCE -> if(pin==0) Supply else Return
                    connected -> Mint
                    else -> Blue
                }
                drawCircle(color.copy(alpha=if(selected) .23f else .14f),if(selected) 20f else 16f,pos)
                drawCircle(color,if(selected) 12f else 10f,pos)
                drawCircle(Color.White,if(selected) 5f else 4f,pos)
                val name=if(p.kind.isBoard || p.kind in IcParts.pinNames) "" else terminalName(p,pin)
                if(name.isNotEmpty() && (selected || details)) {
                    val dense=pos.x!=0f && (0 until p.terminalCount).any { other ->
                        val neighbor=localTerminalOffset(p,other)/p.sizeScale
                        other!=pin && neighbor.x==pos.x && kotlin.math.abs(neighbor.y-pos.y)<27f
                    }
                    val paint=android.graphics.Paint(3).apply {
                        this.color=android.graphics.Color.rgb(221,234,255)
                        textSize=if(dense) 10f else 18f
                        textAlign=if(dense) {
                            if(pos.x<0f) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                        } else android.graphics.Paint.Align.CENTER
                    }
                    val horizontal=pos.x!=0f
                    val labelX=if(dense) pos.x-kotlin.math.sign(pos.x)*18f else if(horizontal) pos.x else pos.x+25f
                    val labelY=if(dense) pos.y+3f else if(horizontal) pos.y-17f else pos.y+6f
                    drawContext.canvas.nativeCanvas.drawText(name,labelX,labelY,paint)
                }
            }
          }
        }
    }
}
