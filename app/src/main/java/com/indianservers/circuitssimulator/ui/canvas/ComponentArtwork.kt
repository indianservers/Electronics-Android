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
private fun resistorBands(ohms: Double): List<Color> {
    val r=ohms.coerceAtLeast(1.0)
    val exponent=(floor(log10(r)).toInt()-1).coerceIn(0,9)
    val significant=(r/10.0.pow(exponent)).roundToInt().coerceIn(10,99)
    return listOf(bandPalette[significant/10],bandPalette[significant%10],bandPalette[exponent],Color(0xFFCDA84B))
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
    activeMask:Int = 0) {
    translate(p.x, p.y) {
        rotate(rotationDegrees, pivot = Offset.Zero) {
          scale(p.sizeScale,p.sizeScale,pivot=Offset.Zero) {
            if (selected) drawCircle(Blue.copy(alpha = .14f), 92f)
            when (p.kind) {
                in BoardRegistry.boards.keys -> {
                    val board=BoardRegistry.boards.getValue(p.kind)
                    val halfW=board.boardWidth/2f
                    val halfH=board.boardHeight/2f
                    val pcb=Color(board.pcbColor)
                    drawRoundRect(pcb,Offset(-halfW,-halfH),Size(board.boardWidth,board.boardHeight),
                        androidx.compose.ui.geometry.CornerRadius(10f))
                    drawRoundRect(Color(0xFF9BDFFF),Offset(-halfW,-halfH),Size(board.boardWidth,board.boardHeight),
                        androidx.compose.ui.geometry.CornerRadius(10f),style=Stroke(2f))
                    drawRoundRect(Color(0xFFD6DBE1),Offset(-28f,-halfH-2f),Size(56f,18f),
                        androidx.compose.ui.geometry.CornerRadius(3f))
                    if(board.capabilities.wifi.hardware)
                        drawRoundRect(Color(0xFF0E1A22),Offset(halfW-36f,-halfH+18f),Size(22f,48f),
                            androidx.compose.ui.geometry.CornerRadius(3f))
                    drawRoundRect(Color(0xFF1B2630),Offset(-30f,-22f),Size(60f,40f),
                        androidx.compose.ui.geometry.CornerRadius(4f))
                    drawContext.canvas.nativeCanvas.drawText(board.mcu.take(14),0f,0f,
                        android.graphics.Paint(3).apply { color=android.graphics.Color.WHITE
                            textSize=9f;textAlign=android.graphics.Paint.Align.CENTER })
                    drawContext.canvas.nativeCanvas.drawText(board.product.take(18),0f,-halfH+36f,
                        android.graphics.Paint(3).apply { color=android.graphics.Color.WHITE
                            textSize=12f;textAlign=android.graphics.Paint.Align.CENTER
                            typeface=android.graphics.Typeface.DEFAULT_BOLD })
                    board.pins.forEachIndexed { index,pin ->
                        if(!pin.connectable) {
                            drawCircle(Color(0xFFFFC857),6f,Offset(0f,28f))
                            return@forEachIndexed
                        }
                        val pos=localTerminalOffset(p,index)/p.sizeScale
                        val color=when {
                            pin.type==PinType.GROUND -> Color(0xFF55ADFF)
                            pin.type==PinType.POWER_INPUT || pin.type==PinType.POWER_OUTPUT -> Color(0xFFFFB44D)
                            PinCapability.ANALOG_INPUT in pin.capabilities -> Color(0xFFC48BFF)
                            PinCapability.UART in pin.capabilities || PinCapability.I2C in pin.capabilities ||
                                PinCapability.SPI in pin.capabilities -> Color(0xFF31E69A)
                            else -> Color(0xFFE3C174)
                        }
                        drawCircle(color,5f,pos)
                        val paint=android.graphics.Paint(3).apply {
                            this.color=android.graphics.Color.WHITE;textSize=8.5f
                            textAlign=if(pos.x<0f) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                        }
                        drawContext.canvas.nativeCanvas.drawText(pin.name,
                            if(pos.x<0f) pos.x+10f else pos.x-10f,pos.y+3f,paint)
                    }
                }
                in IcParts.pinNames.keys -> {
                    val tall=p.terminalCount>=16
                    val halfHeight=if(tall) 104f else 62f
                    drawRoundRect(Color(0xFF17232E),Offset(-76f,-halfHeight),Size(152f,2*halfHeight),
                        androidx.compose.ui.geometry.CornerRadius(9f))
                    drawRoundRect(Color(0xFF6FB7CE),Offset(-76f,-halfHeight),Size(152f,2*halfHeight),
                        androidx.compose.ui.geometry.CornerRadius(9f),style=Stroke(2f))
                    drawCircle(Color(0xFFADBEC9),4f,Offset(-62f,-halfHeight+13f))
                    drawContext.canvas.nativeCanvas.drawText(p.kind.title.take(12),0f,4f,
                        android.graphics.Paint(3).apply { color=android.graphics.Color.WHITE
                            textSize=15f;textAlign=android.graphics.Paint.Align.CENTER
                            typeface=android.graphics.Typeface.DEFAULT_BOLD })
                    IcParts.pinNames.getValue(p.kind).forEachIndexed { index,name ->
                        val pos=localTerminalOffset(p,index)/p.sizeScale
                        val edge=Offset(if(pos.x<0f) -76f else 76f,pos.y)
                        drawLine(Color.LightGray,pos,edge,4f)
                        drawContext.canvas.nativeCanvas.drawText(name,
                            if(pos.x<0f) -68f else 68f,pos.y+3f,
                            android.graphics.Paint(3).apply { color=android.graphics.Color.WHITE
                                textSize=9.5f;textAlign=if(pos.x<0f)
                                    android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT })
                    }
                }
                Kind.CLOCK,Kind.NOT_GATE,Kind.BUFFER_GATE,Kind.AND_GATE,Kind.OR_GATE,Kind.NAND_GATE,
                Kind.NOR_GATE,Kind.XOR_GATE,Kind.XNOR_GATE -> {
                    drawRoundRect(Panel,Offset(-43f,-39f),Size(86f,78f),
                        androidx.compose.ui.geometry.CornerRadius(12f))
                    drawRoundRect(Color(0xFF9BDFFF),Offset(-43f,-39f),Size(86f,78f),
                        androidx.compose.ui.geometry.CornerRadius(12f),style=Stroke(3f))
                    val inputYs=if(p.kind==Kind.CLOCK) emptyList() else if(p.kind==Kind.NOT_GATE) listOf(0f)
                        else listOf(-22f,22f)
                    inputYs.forEach { y -> drawLine(Color.LightGray,Offset(-67f,y),Offset(-43f,y),4f) }
                    drawLine(Mint,Offset(43f,0f),Offset(67f,0f),4f)
                    val label=when(p.kind) {
                        Kind.CLOCK -> "CLK"; Kind.NOT_GATE -> "NOT";Kind.BUFFER_GATE -> "BUF"
                        Kind.AND_GATE -> "AND"
                        Kind.OR_GATE -> "OR"; Kind.NAND_GATE -> "NAND"; Kind.NOR_GATE -> "NOR"
                        Kind.XOR_GATE -> "XOR"; else -> "XNOR"
                    }
                    drawContext.canvas.nativeCanvas.drawText(label,0f,8f,android.graphics.Paint(3).apply {
                        color=android.graphics.Color.WHITE;textSize=if(label.length>3) 20f else 24f
                        textAlign=android.graphics.Paint.Align.CENTER
                        typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                            android.graphics.Typeface.BOLD)
                    })
                }
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
                Kind.NPN_BJT,Kind.PNP_BJT,Kind.DARLINGTON -> {
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
                Kind.FUNCTION_GENERATOR,Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,
                Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR -> {
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
                        Kind.VARIABLE_DC_SUPPLY -> "DC";else -> "9V"
                    }
                    drawContext.canvas.nativeCanvas.drawText(sourceLabel,0f,14f,android.graphics.Paint(3).apply {
                        color=android.graphics.Color.WHITE;textSize=21f;textAlign=android.graphics.Paint.Align.CENTER
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
                        else p.value("resistance"))
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
                Kind.LDR,Kind.THERMISTOR,Kind.PTC_THERMISTOR -> {
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
                Kind.SWITCH,Kind.NC_PUSH_BUTTON -> {
                    drawLine(Color(0xFFAABBCF),Offset(-67f,0f),Offset(-43f,0f),5f)
                    drawLine(Color(0xFFAABBCF),Offset(43f,0f),Offset(67f,0f),5f)
                    drawCircle(Color(0xFFB8CEE8), 12f, Offset(-42f,0f))
                    drawCircle(Color(0xFFB8CEE8), 12f, Offset(42f,0f))
                    drawLine(Color(0xFFE1ECFB),Offset(-42f,0f),if (p.closed) Offset(42f,0f) else Offset(22f,-36f),7f)
                }
                Kind.LED,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED -> {
                    val light = when(p.kind) {
                        Kind.GREEN_LED -> Color(0xFF43EE79)
                        Kind.BLUE_LED -> Color(0xFF4299FF)
                        else -> Color(0xFFFF454C)
                    }
                    if (intensity > .01f) {
                        drawCircle(Brush.radialGradient(listOf(light.copy(alpha = intensity * .7f),
                            light.copy(alpha = intensity * .24f),Color.Transparent)),88f)
                        drawCircle(light.copy(alpha=intensity*.55f),36f,Offset(0f,-12f))
                    }
                    // The two drawn leads terminate at the two actual electrical pins.
                    drawLine(Color(0xFFD5E0E9),Offset(-29f,10f),Offset(-29f,62f),6f)
                    drawLine(Color(0xFF9BABB9),Offset(29f,10f),Offset(29f,62f),6f)
                    drawLine(Color.White.copy(alpha=.55f),Offset(-31f,19f),Offset(-31f,56f),2f)
                    val lens=if(intensity>.01f) listOf(Color(0xFFFFB3A0),light,Color(0xFF8F172B))
                        else listOf(Color(0xFFB94B51),Color(0xFF7B2734),Color(0xFF401721))
                    drawRoundRect(Brush.verticalGradient(lens),
                        Offset(-31f,-53f),Size(62f,70f),androidx.compose.ui.geometry.CornerRadius(29f))
                    drawRoundRect(Color(0xFFB93C46),Offset(-34f,10f),Size(68f,10f),
                        androidx.compose.ui.geometry.CornerRadius(3f))
                    drawCircle(Color.White.copy(alpha=if(intensity>.01f) .8f else .38f),9f,Offset(-13f,-33f))
                }
                Kind.DIODE,Kind.RECTIFIER,Kind.ZENER,Kind.SCHOTTKY_DIODE,Kind.PHOTODIODE -> {
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
                Kind.CAPACITOR, Kind.ELECTROLYTIC,Kind.VARIABLE_CAPACITOR -> {
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
                Kind.SERVO_MOTOR -> {
                    drawRoundRect(Color(0xFF243955),Offset(-42f,-43f),Size(84f,86f),
                        androidx.compose.ui.geometry.CornerRadius(9f))
                    drawRoundRect(Color(0xFF82B8DC),Offset(-42f,-43f),Size(84f,86f),
                        androidx.compose.ui.geometry.CornerRadius(9f),style=Stroke(3f))
                    drawCircle(Color(0xFFBECBD8),22f,Offset(0f,-4f))
                    rotate(-75f+150f*intensity,pivot=Offset(0f,-4f)) {
                        drawLine(Color(0xFFF5F5EA),Offset(0f,-4f),Offset(0f,-54f),9f)
                        drawCircle(Color.White,5f,Offset(0f,-47f))
                    }
                    drawContext.canvas.nativeCanvas.drawText("SERVO",0f,34f,
                        android.graphics.Paint(3).apply { color=android.graphics.Color.WHITE
                            textSize=13f;textAlign=android.graphics.Paint.Align.CENTER })
                }
                Kind.BUZZER,Kind.SPEAKER -> {
                    drawCircle(Color(0xFF233748),47f)
                    drawCircle(if(intensity>.1f) Mint else Color(0xFF8AA9BE),47f,style=Stroke(3f))
                    drawCircle(Color(0xFF425669),30f)
                    drawCircle(Color(0xFF141F2B),20f)
                    if(intensity>.1f) {
                        drawCircle(Mint.copy(alpha=intensity*.5f),54f,style=Stroke(2f))
                        drawCircle(Mint.copy(alpha=intensity*.25f),62f,style=Stroke(2f))
                    }
                    drawContext.canvas.nativeCanvas.drawText(if(p.kind==Kind.BUZZER) "BZ" else "SPK",0f,6f,
                        android.graphics.Paint(3).apply { color=android.graphics.Color.WHITE
                            textSize=16f;textAlign=android.graphics.Paint.Align.CENTER })
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
                else -> {
                    drawRoundRect(Panel,Offset(-44f,-49f),Size(88f,98f),
                        androidx.compose.ui.geometry.CornerRadius(10f))
                    drawRoundRect(Color(0xFF9BDFFF),Offset(-44f,-49f),Size(88f,98f),
                        androidx.compose.ui.geometry.CornerRadius(10f),style=Stroke(3f))
                    for(pin in 0 until p.terminalCount) {
                        val pos=localTerminalOffset(p,pin)/p.sizeScale
                        val edge=Offset(pos.x.coerceIn(-44f,44f),pos.y.coerceIn(-49f,49f))
                        drawLine(Color.LightGray,pos,edge,4f)
                    }
                    val label=when(p.kind) {
                        Kind.VCCS->"VCCS";Kind.VCVS->"VCVS";Kind.CCCS->"CCCS"
                        Kind.CCVS->"CCVS";Kind.TRANSFORMER->"XFMR"
                        Kind.PUSH_BUTTON->"PUSH";Kind.SPDT_SWITCH->"SPDT";Kind.RELAY->"RELAY"
                        Kind.RGB_LED->"RGB";Kind.DC_MOTOR->"MOTOR";Kind.TIMER_555->"555"
                        Kind.D_FLIP_FLOP->"D FF";Kind.T_FLIP_FLOP->"T FF";Kind.COUNTER_4->"COUNT"
                        Kind.ADC_2->"ADC";Kind.DAC_2->"DAC";Kind.SEVEN_SEGMENT->"7 SEG"
                        Kind.BUZZER->"BUZZ";Kind.SPEAKER->"SPK";Kind.SERVO_MOTOR->"SERVO"
                        Kind.SOLENOID->"COIL";Kind.DIP_SWITCH_4->"DIP 4";Kind.DPDT_RELAY->"DPDT"
                        Kind.REED_SWITCH->"REED";Kind.LM35_SENSOR->"LM35"
                        Kind.HALL_SENSOR->"HALL";Kind.PHOTOTRANSISTOR->"PHOTO"
                        Kind.SCR->"SCR";Kind.TRIAC->"TRIAC"
                        Kind.TRI_STATE_BUFFER->"3STATE";Kind.SR_LATCH->"SR";Kind.D_LATCH->"D LAT"
                        Kind.JK_FLIP_FLOP->"JK FF";Kind.MULTIPLEXER_2->"MUX"
                        Kind.DEMULTIPLEXER_2->"DEMUX";Kind.ENCODER_4->"ENC"
                        Kind.DECODER_2->"DEC";Kind.LOGIC_INPUT->"INPUT";Kind.LOGIC_OUTPUT->"OUTPUT"
                        Kind.SERIAL_TERMINAL->"UART";Kind.I2C_TEMP_SENSOR->"I2C T"
                        Kind.I2C_LCD->"LCD";Kind.OLED_SSD1306->"OLED";Kind.I2C_EEPROM->"EEPROM"
                        Kind.ULTRASONIC->"HCSR04";Kind.PIR_SENSOR->"PIR";Kind.SPI_MEMORY->"SPI"
                        else->p.kind.prefix
                    }
                    if(p.kind==Kind.SEVEN_SEGMENT) {
                        val segments=listOf(
                            Offset(-19f,-31f) to Offset(19f,-31f),
                            Offset(22f,-29f) to Offset(22f,-3f),
                            Offset(22f,2f) to Offset(22f,29f),
                            Offset(-19f,31f) to Offset(19f,31f),
                            Offset(-22f,2f) to Offset(-22f,29f),
                            Offset(-22f,-29f) to Offset(-22f,-3f),
                            Offset(-19f,0f) to Offset(19f,0f))
                        segments.forEachIndexed { index,(a,b) ->
                            drawLine(if(activeMask and (1 shl index)!=0) Color(0xFFFF664F)
                                else Color(0xFF593D42),a,b,7f)
                        }
                    } else if(p.kind==Kind.RGB_LED) {
                        listOf(Color.Red,Color.Green,Color.Blue).forEachIndexed { index,color ->
                            drawCircle(if(activeMask and (1 shl index)!=0) color else color.copy(alpha=.18f),
                                10f,Offset(-22f+22f*index,0f))
                        }
                    } else drawContext.canvas.nativeCanvas.drawText(label,0f,7f,android.graphics.Paint(3).apply {
                        color=android.graphics.Color.WHITE;textSize=18f
                        textAlign=android.graphics.Paint.Align.CENTER
                    })
                }
            }
            for (pin in 0 until p.terminalCount) {
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
}
