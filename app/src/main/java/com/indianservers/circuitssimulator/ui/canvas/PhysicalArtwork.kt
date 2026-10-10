package com.indianservers.circuitssimulator.ui.canvas

import android.graphics.Paint
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.indianservers.circuitssimulator.domain.*
import kotlin.math.*

private val metal=Color(0xFFB9C3CC)
private val darkMetal=Color(0xFF56636E)
private val plastic=Color(0xFF202830)
private val gold=Color(0xFFD5AF5C)
private val copper=Color(0xFFBD743C)
private val motorBlade=Path().apply {
    moveTo(5f,-3f);cubicTo(19f,-12f,44f,-13f,48f,-3f)
    cubicTo(48f,10f,23f,12f,5f,4f);close()
}
private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER }

private fun DrawScope.body(color:Color,x:Float,y:Float,w:Float,h:Float,r:Float=6f) {
    drawRoundRect(color,Offset(x,y),Size(w,h),CornerRadius(r))
    drawRoundRect(Color.White.copy(alpha=.17f),Offset(x,y),Size(w,h),CornerRadius(r),style=Stroke(1.5f))
}
private fun DrawScope.print(text:String,x:Float=0f,y:Float=4f,font:Float=10f,color:Color=TextIce) {
    ink.color=color.toArgb();ink.textSize=font
    drawContext.canvas.nativeCanvas.drawText(text,x,y,ink)
}
private fun DrawScope.screw(x:Float,y:Float,r:Float=4f) {
    drawCircle(metal,r,Offset(x,y));drawLine(darkMetal,Offset(x-r*.5f,y),Offset(x+r*.5f,y),1.3f)
}
private fun DrawScope.leads(p:PlacedComponent,w:Float=40f,h:Float=38f) {
    repeat(p.terminalCount) { pin ->
        val end=localTerminalOffset(p,pin)/p.sizeScale
        val edge=Offset(end.x.coerceIn(-w,w),end.y.coerceIn(-h,h))
        drawLine(darkMetal,edge,end,6f);drawLine(metal,edge,end,3f)
    }
}
private fun DrawScope.chip(p:PlacedComponent,details:Boolean) {
    val tall=p.kind in IcParts.pinNames && p.terminalCount>=16
    val h=if(tall) 104f else min(52f,24f+p.terminalCount*3f)
    val w=if(p.kind in IcParts.pinNames) 76f else 42f
    leads(p,w,h)
    body(plastic,-w,-h,w*2,h*2)
    drawLine(Color(0xFF3A454F),Offset(-w+5,-h+6),Offset(w-5,-h+6),4f)
    drawArc(darkMetal,0f,180f,false,Offset(-9f,-h-7f),Size(18f,14f),style=Stroke(3f))
    drawCircle(metal,2.8f,Offset(-w+9f,-h+12f))
    if(details) {
        print(p.kind.title.take(18),y=4f,font=if(w>50) 12f else 9f)
        if(p.kind in IcParts.pinNames) IcParts.pinNames.getValue(p.kind).forEachIndexed { i,name ->
            val at=localTerminalOffset(p,i)/p.sizeScale
            print(name,if(at.x<0) -w+18f else w-18f,at.y+2f,7f)
        }
    }
}

/** All drawings use the same local coordinate space as localTerminalOffset. */
fun DrawScope.drawPhysicalBody(p:PlacedComponent,v:ComponentVisualState,details:Boolean):Boolean {
    when(p.kind) {
        Kind.DC_MOTOR -> {
            leads(p,44f,24f)
            body(darkMetal,-44f,-34f,88f,68f,12f)
            drawRoundRect(Brush.verticalGradient(listOf(metal,Color(0xFFE0E5E8),darkMetal)),
                Offset(-40f,-30f),Size(80f,60f),CornerRadius(10f))
            body(plastic,-44f,-28f,12f,56f,3f)
            repeat(3) { drawRoundRect(plastic,Offset(22f,-22f+it*18),Size(12f,7f),CornerRadius(3f)) }
            drawCircle(darkMetal,25f,center=Offset.Zero);drawCircle(metal,21f,center=Offset.Zero);drawCircle(gold,8f,center=Offset.Zero)
            // The propeller and shaft always rotate about the exact same local origin.
            if(abs(v.motor.rpm)>3000f && !v.reducedMotion)
                drawCircle(Color(0xFF485D68).copy(alpha=.15f),49f,style=Stroke(10f),center=Offset.Zero)
            rotate(v.rotorDegrees,Offset.Zero) {
                repeat(3) { blade -> rotate(blade*120f,Offset.Zero) {
                    drawPath(motorBlade,Color(0xFF374C58));drawPath(motorBlade,Color(0xFF7A8C96),style=Stroke(1.5f))
                } }
                drawLine(Color.White,Offset(-4f,0f),Offset(4f,0f),2f)
            }
            drawCircle(gold,7f,center=Offset.Zero);screw(0f,0f,4f)
            if(details) print(if(v.motor.isPowered) "DC · ${abs(v.motor.rpm).roundToInt()} rpm" else "DC",y=51f,font=9f)
        }
        Kind.SERVO_MOTOR -> {
            leads(p,36f,40f)
            body(Color(0xFF173C6B),-36f,-40f,72f,80f)
            body(Color(0xFF25538B),-48f,20f,96f,12f,3f)
            screw(-41f,26f);screw(41f,26f)
            body(Color(0xFF25538B),-29f,-34f,58f,20f)
            val hub=Offset(0f,-17f)
            drawCircle(darkMetal,14f,hub)
            rotate(v.servoDegrees-90f,hub) {
                body(Color(0xFFE5E8E6),-5f,-49f,10f,64f,5f)
                listOf(-41f,-31f,0f,9f).forEach { drawCircle(darkMetal,2f,Offset(0f,it)) }
            }
            screw(hub.x,hub.y,5f)
            if(details) { print("MICRO SERVO",y=15f,font=8f);print("${v.servoDegrees.roundToInt()}°",y=38f,font=9f) }
        }
        Kind.RGB_LED -> {
            leads(p,24f,25f)
            val color=Color(v.red,v.green,v.blue)
            val level=max(v.red,max(v.green,v.blue))
            if(level>.001f && !v.failed) drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.45f*ledGlowPulse(v)),Color.Transparent),center=Offset(0f,-14f),radius=58f+7f*ledGlowPulse(v)),58f+7f*ledGlowPulse(v),center=Offset(0f,-14f))
            body(Color(0xFF9AABB4).copy(alpha=.65f),-26f,-40f,52f,65f,24f)
            drawRect(metal,Offset(-12f,-9f),Size(24f,15f))
            drawCircle(if(level>.001f && !v.failed) color else Color(0xFF33404B),18f,Offset(0f,-14f))
            if(level>.001f && !v.failed) drawCircle(Color.White.copy(alpha=.75f),6f,Offset(0f,-14f))
            drawOval(Color.White.copy(alpha=.3f),Offset(-18f,-33f),Size(10f,20f))
            body(metal,-29f,19f,58f,7f,2f)
        }
        Kind.SEVEN_SEGMENT -> {
            leads(p,36f,45f);body(Color(0xFFDDD8D0),-38f,-46f,76f,92f,3f)
            body(Color(0xFF241A20),-32f,-40f,64f,80f,2f)
            val segments=listOf(Offset(-17f,-30f) to Offset(17f,-30f),Offset(21f,-26f) to Offset(21f,-4f),
                Offset(21f,4f) to Offset(21f,26f),Offset(-17f,30f) to Offset(17f,30f),
                Offset(-21f,4f) to Offset(-21f,26f),Offset(-21f,-26f) to Offset(-21f,-4f),
                Offset(-17f,0f) to Offset(17f,0f))
            segments.forEachIndexed { i,(a,b) ->
                drawLine(if(v.segmentMask and (1 shl i)!=0) Color(0xFFFF493B) else Color(0xFF4C2529),a,b,5.5f)
            }
        }
        Kind.I2C_LCD,Kind.OLED_SSD1306,Kind.SERIAL_TERMINAL -> {
            leads(p,53f,35f)
            body(if(p.kind==Kind.I2C_LCD) Color(0xFF146644) else Color(0xFF1F3A58),-55f,-39f,110f,78f)
            listOf(-48f,48f).forEach { x -> listOf(-32f,32f).forEach { y ->
                drawCircle(gold,3.5f,Offset(x,y));drawCircle(Navy,2f,Offset(x,y)) } }
            body(plastic,-45f,-27f,90f,48f,2f)
            val lcd=p.kind==Kind.I2C_LCD
            body(if(lcd) Color(0xFF78965C) else Color(0xFF050B10),-40f,-22f,80f,38f,1f)
            if(p.kind==Kind.OLED_SSD1306) v.pixels.take(1024).forEachIndexed { index,byte ->
                repeat(8) { bit -> if(byte and (1 shl bit)!=0)
                    drawRect(Color(0xFFB4E9FF),Offset(-40f+(index%128)*80f/128,-22f+(index/128*8+bit)*38f/64),
                        Size(80f/128,38f/64)) }
            }
            if(details && v.display.isNotEmpty()) v.display.take(64).lines().flatMap { it.chunked(16) }.take(2).forEachIndexed { i,line ->
                print(line,0f,-7f+i*15f,8f,if(lcd) Color(0xFF243222) else Color(0xFFB4E9FF)) }
            body(plastic,-29f,27f,58f,8f,1f)
            if(details) print(if(lcd) "16 × 2" else if(p.kind==Kind.OLED_SSD1306) "SSD1306" else "UART",y=37f,font=7f)
        }
        Kind.RELAY,Kind.DPDT_RELAY -> {
            leads(p,43f,43f)
            val h=if(p.kind==Kind.DPDT_RELAY) 64f else 42f
            body(Color(0xFF576E79).copy(alpha=.85f),-44f,-h,88f,h*2)
            body(Color(0xFF304854),-37f,-h+6f,30f,h*2-12f,3f)
            repeat(7) { drawLine(copper,Offset(-34f,-h+13f+it*(h*2-25f)/6),Offset(-11f,-h+13f+it*(h*2-25f)/6),3f) }
            val throws=if(p.kind==Kind.DPDT_RELAY) listOf(-29f,29f) else listOf(0f)
            throws.forEach { y ->
                drawCircle(gold,4f,Offset(8f,y+14f));drawCircle(gold,4f,Offset(32f,y-12f));drawCircle(gold,4f,Offset(32f,y+12f))
                drawLine(metal,Offset(8f,y+14f),Offset(32f,if(v.relayClosed) y-12f else y+12f),4f)
            }
            if(v.relayClosed) drawCircle(Mint,3f,Offset(-22f,-h+7f))
        }
        Kind.PUSH_BUTTON,Kind.NC_PUSH_BUTTON,Kind.SWITCH,Kind.SPDT_SWITCH,Kind.REED_SWITCH -> {
            leads(p,32f,24f)
            if(p.kind==Kind.REED_SWITCH) {
                body(Color(0xFFACC9C9).copy(alpha=.5f),-43f,-12f,86f,24f)
                drawLine(metal,Offset(-43f,0f),Offset(2f,0f),3f)
                drawLine(metal,Offset(43f,0f),Offset(-2f,if(p.value("field")>=.5) 0f else -5f),3f)
            } else {
                body(darkMetal,-32f,-24f,64f,48f,4f)
                if(p.kind==Kind.PUSH_BUTTON || p.kind==Kind.NC_PUSH_BUTTON) {
                    val pressed=if(p.kind==Kind.NC_PUSH_BUTTON) !p.closed else p.closed
                    drawCircle(plastic,21f,center=Offset.Zero)
                    drawCircle(if(pressed) Color(0xFF8B3032) else Color(0xFFBF4645),if(pressed) 15f else 18f,Offset(0f,if(pressed) 3f else -3f))
                } else {
                    drawCircle(plastic,15f,center=Offset.Zero)
                    val end=Offset(0f,if(p.closed) -31f else 31f)
                    drawLine(darkMetal,Offset.Zero,end,11f);drawLine(metal,Offset.Zero,end,7f)
                    drawCircle(Color(0xFFE0E4E7),6f,end)
                }
                screw(-25f,-17f);screw(25f,17f)
            }
        }
        Kind.POTENTIOMETER -> {
            leads(p,34f,34f);drawCircle(darkMetal,36f,center=Offset.Zero);drawCircle(metal,33f,center=Offset.Zero)
            drawCircle(plastic,26f,center=Offset.Zero)
            repeat(18) { rotate(it*20f,Offset.Zero) { drawLine(darkMetal,Offset(0f,-21f),Offset(0f,-26f),2f) } }
            rotate(-135f+p.value("position").toFloat()*270f,Offset.Zero) {
                drawLine(Color(0xFFE4E7DF),Offset(0f,-8f),Offset(0f,-23f),3f)
            }
        }
        Kind.RHEOSTAT -> {
            leads(p,43f,22f)
            body(Color(0xFFC7C1AF),-43f,-23f,86f,46f,3f)
            repeat(14) { n -> drawLine(copper,Offset(-36f+n*5.5f,-19f),Offset(-36f+n*5.5f,19f),2.5f) }
            drawLine(metal,Offset(-40f,-28f),Offset(40f,-28f),4f)
            val x=-34f+68f*p.value("position").toFloat()
            body(darkMetal,x-6f,-34f,12f,23f,2f)
            drawLine(metal,Offset(x,-12f),Offset(x,1f),3f)
        }
        Kind.GROUND -> {
            drawLine(metal,Offset(0f,-48f),Offset(0f,-13f),5f)
            body(copper,-21f,-13f,42f,37f,6f)
            drawCircle(metal,13f,Offset(0f,6f));drawCircle(darkMetal,9f,Offset(0f,6f))
            screw(0f,6f,6f)
        }
        Kind.CAPACITOR,Kind.ELECTROLYTIC,Kind.VARIABLE_CAPACITOR -> {
            leads(p,24f,32f)
            when(p.kind) {
                Kind.CAPACITOR -> {
                    if(p.value("capacitance")<=1e-6) {
                        drawCircle(Color(0xFFB97D35),30f,center=Offset.Zero);drawCircle(Color(0xFFD59A51),27f,center=Offset.Zero)
                    } else {
                        body(Color(0xFF346B58),-28f,-30f,56f,60f,5f)
                        body(Color(0xFF537D64),-25f,-28f,50f,8f,2f)
                    }
                    if(details) print(EngineeringUnits.format(p.value("capacitance"),"F"),font=10f,
                        color=if(p.value("capacitance")<=1e-6) plastic else TextIce)
                }
                Kind.ELECTROLYTIC -> {
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF13385C),Color(0xFF326798),Color(0xFF152E48))),
                        Offset(-26f,-37f),Size(52f,74f),CornerRadius(6f))
                    drawOval(metal,Offset(-26f,-41f),Size(52f,12f))
                    drawLine(darkMetal,Offset(-10f,-38f),Offset(10f,-32f),1f)
                    drawLine(darkMetal,Offset(-10f,-32f),Offset(10f,-38f),1f)
                    drawRect(Color(0xFFC1C8D0),Offset(15f,-26f),Size(8f,53f))
                    repeat(4) { drawLine(plastic,Offset(17f,-17f+it*12f),Offset(21f,-17f+it*12f),1.5f) }
                }
                else -> {
                    body(Color(0xFFD5BD85),-30f,-30f,60f,60f)
                    repeat(7) { drawArc(metal,180f,180f,true,Offset(-26f+it*3f,-24f),Size(44f,48f)) }
                    screw(0f,0f,8f)
                }
            }
        }
        Kind.LDR,Kind.THERMISTOR,Kind.PTC_THERMISTOR -> {
            leads(p,25f,22f)
            drawCircle(if(p.kind==Kind.LDR) Color(0xFFE0BE8A) else if(p.kind==Kind.THERMISTOR) Color(0xFF313940) else Color(0xFF477FA0),29f,center=Offset.Zero)
            if(p.kind==Kind.LDR) {
                val track=Path().apply { moveTo(-17f,-18f);lineTo(17f,-18f);lineTo(17f,-10f)
                    lineTo(-17f,-10f);lineTo(-17f,-2f);lineTo(17f,-2f);lineTo(17f,6f)
                    lineTo(-17f,6f);lineTo(-17f,14f);lineTo(17f,14f) }
                drawPath(track,Color(0xFF976748),style=Stroke(3f))
            } else if(details) print(if(p.kind==Kind.THERMISTOR) "NTC" else "PTC",font=10f)
        }
        Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS,Kind.DARLINGTON,Kind.SCR,Kind.TRIAC,
        Kind.REGULATOR_7805,Kind.REGULATOR_LM317,Kind.LM35_SENSOR,Kind.HALL_SENSOR,Kind.PHOTOTRANSISTOR -> {
            leads(p,27f,26f)
            val power=p.kind in setOf(Kind.PMOS,Kind.DARLINGTON,Kind.SCR,Kind.TRIAC,Kind.REGULATOR_7805,Kind.REGULATOR_LM317)
            if(power) { body(metal,-25f,-45f,50f,22f,2f);drawCircle(Navy,5f,Offset(0f,-35f)) }
            body(plastic,-28f,-26f,56f,52f,if(power) 3f else 19f)
            drawLine(darkMetal,Offset(-21f,18f),Offset(21f,18f),3f)
            if(details) print(p.kind.title.take(13),y=3f,font=8f)
        }
        Kind.DIODE,Kind.RECTIFIER,Kind.ZENER,Kind.SCHOTTKY_DIODE,Kind.PHOTODIODE -> {
            leads(p,36f,12f)
            val glass=p.kind==Kind.ZENER || p.kind==Kind.DIODE
            body(if(glass) Color(0xFFB97438) else plastic,-36f,-14f,72f,28f)
            drawRect(if(glass) plastic else metal,Offset(24f,-14f),Size(7f,28f))
            if(p.kind==Kind.PHOTODIODE) drawCircle(Color(0xFF556D7F),8f,Offset(-6f,0f))
            if(details) print(if(p.kind==Kind.RECTIFIER) "1N4007" else if(p.kind==Kind.SCHOTTKY_DIODE) "SCHOTTKY" else if(glass) "" else "PHOTO",y=3f,font=7f)
        }
        Kind.TRANSFORMER -> {
            leads(p,43f,38f);body(darkMetal,-43f,-40f,86f,80f,3f)
            repeat(5) { body(metal,-39f+it*2f,-36f+it*2f,78f-it*4,72f-it*4,1f) }
            listOf(-23f,23f).forEach { x -> body(plastic,x-14f,-28f,28f,56f,2f)
                repeat(9) { drawLine(copper,Offset(x-13f,-23f+it*6f),Offset(x+13f,-23f+it*6f),3f) } }
        }
        Kind.BUZZER,Kind.SPEAKER -> {
            leads(p,32f,32f)
            val radius=if(p.kind==Kind.SPEAKER) 43f else 34f
            drawCircle(plastic,radius,center=Offset.Zero);drawCircle(darkMetal,radius,style=Stroke(3f),center=Offset.Zero)
            if(p.kind==Kind.SPEAKER) {
                val shift=if(v.reducedMotion) 0f else sin(v.phase*2f*PI.toFloat())*v.brightness*1.5f
                drawCircle(Color(0xFF535454),33f+shift,center=Offset.Zero);drawCircle(plastic,27f+shift,style=Stroke(5f),center=Offset.Zero)
                drawCircle(Color(0xFF343535),12f+shift,center=Offset.Zero)
                repeat(4) { rotate(it*90f,Offset.Zero) { screw(0f,-37f,2.5f) } }
            } else {
                drawCircle(Color(0xFF323D44),29f,center=Offset.Zero);drawCircle(Color(0xFF080D10),7f,center=Offset.Zero)
                if(details) print("+",x=17f,y=16f,font=13f)
                if(v.brightness>.01f) drawCircle(Mint.copy(alpha=.3f),36f+if(v.reducedMotion) 0f else v.phase*4f,style=Stroke(1.5f),center=Offset.Zero)
            }
        }
        Kind.ULTRASONIC -> {
            leads(p,48f,24f);body(Color(0xFF186390),-51f,-28f,102f,56f,3f)
            listOf(-27f,27f).forEach { x -> drawCircle(metal,23f,Offset(x,-2f));drawCircle(plastic,17f,Offset(x,-2f))
                repeat(6) { n -> drawLine(darkMetal,Offset(x-13f,-14f+n*5f),Offset(x+13f,-14f+n*5f),1.3f) }
                drawCircle(metal,17f,Offset(x,-2f),style=Stroke(2f)) }
            if(details) print("HC-SR04",y=24f,font=7f)
        }
        Kind.PIR_SENSOR -> {
            leads(p,30f,28f);body(Color(0xFF217646),-34f,-31f,68f,62f)
            drawCircle(Color(0xFFDCE2DC),27f,center=Offset.Zero)
            for(r in listOf(9f,18f,26f)) drawCircle(Color(0xFFBBC5BE),r,style=Stroke(1f),center=Offset.Zero)
            repeat(12) { rotate(it*30f,Offset.Zero) { drawLine(Color(0xFFBBC5BE),Offset(0f,-6f),Offset(0f,-26f),1f) } }
            if(v.brightness>.1f) drawCircle(Mint,3f,Offset(27f,24f))
        }
        Kind.I2C_TEMP_SENSOR -> {
            leads(p,34f,26f);body(Color(0xFF18617A),-38f,-30f,76f,60f)
            body(plastic,-12f,-13f,24f,26f,2f)
            repeat(4) { n -> listOf(-1f,1f).forEach { side -> drawLine(metal,Offset(side*12f,-9f+n*6f),Offset(side*18f,-9f+n*6f),2f) } }
            if(details) print("TEMP",y=24f,font=8f)
        }
        Kind.LOGIC_INPUT,Kind.LOGIC_OUTPUT -> {
            leads(p,30f,24f);body(Color(0xFF304D3C),-34f,-28f,68f,56f)
            if(p.kind==Kind.LOGIC_INPUT) {
                body(metal,-19f,-15f,38f,30f,3f);body(plastic,if(p.value("state")>=.5) 1f else -16f,-12f,15f,24f,2f)
            } else { drawCircle(darkMetal,17f,center=Offset.Zero);drawCircle(if(v.brightness>.01f) Mint else Color(0xFF3B594A),13f,center=Offset.Zero) }
        }
        Kind.SOURCE,Kind.VARIABLE_DC_SUPPLY,Kind.DC_CURRENT_SOURCE,
        Kind.VCCS,Kind.VCVS,Kind.CCCS,Kind.CCVS,Kind.FUNCTION_GENERATOR,Kind.AC_VOLTAGE_SOURCE,
        Kind.SINE_GENERATOR,Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR,Kind.AMMETER,Kind.VOLTMETER -> {
            leads(p,43f,40f);body(Color(0xFF64717B),-45f,-42f,90f,84f)
            body(Color(0xFFCFD5D8),-40f,-37f,80f,74f,3f)
            body(plastic,-33f,-29f,66f,31f,2f)
            val generator=p.kind.title.contains("generator",true) || p.kind==Kind.AC_VOLTAGE_SOURCE
            if(generator) {
                val wave=Path()
                repeat(41) { i ->
                    val x=-28f+i*1.4f
                    val phase=i/40f*2f*PI.toFloat()
                    val square=p.kind==Kind.SQUARE_GENERATOR || p.kind==Kind.PULSE_GENERATOR || p.value("waveform")==1.0
                    val y=-15f+(if(square) if(i<20) -6f else 6f else sin(phase)*6f)
                    if(i==0) wave.moveTo(x,y) else wave.lineTo(x,y)
                }
                drawPath(wave,Mint,style=Stroke(1.8f))
                if(details) print(v.display,y=-2f,font=6f,color=Mint)
            } else if(details) print(v.display.ifEmpty { p.kind.prefix },y=-9f,font=10f,color=Mint)
            drawCircle(plastic,12f,Offset(-19f,20f));drawLine(metal,Offset(-19f,20f),Offset(-13f,13f),2f)
            drawCircle(Color(0xFFAD3635),6f,Offset(9f,22f));drawCircle(plastic,6f,Offset(26f,22f))
        }
        Kind.SINGLE_CELL,Kind.BATTERY_PACK -> {
            leads(p,28f,40f)
            if(p.kind==Kind.SINGLE_CELL) {
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF82531F),Color(0xFFDDAB56),Color(0xFF80521F))),
                    Offset(-21f,-43f),Size(42f,86f),CornerRadius(10f))
                body(plastic,-21f,-8f,42f,41f,2f);body(metal,-9f,-49f,18f,8f,2f)
                if(details) print("1.5V",y=12f,font=10f)
            } else {
                body(plastic,-38f,-42f,76f,84f,5f)
                repeat(3) { n -> body(Color(0xFF4E8B46),-32f+n*23,-35f,19f,70f,8f)
                    drawLine(metal,Offset(-29f+n*23,-29f),Offset(-18f+n*23,-29f),2f) }
            }
        }
        else -> {
            if(p.kind.isBoard) board(p,details,v)
            else if(p.kind.category=="Digital" || p.kind in IcParts.pinNames || p.kind in setOf(Kind.OPAMP,Kind.IDEAL_OPAMP,Kind.I2C_EEPROM,Kind.SPI_MEMORY)) chip(p,details)
            else return false // Dedicated existing physical passives, LEDs, bulb, coil, ground and junction.
        }
    }
    return true
}

private fun DrawScope.board(p:PlacedComponent,details:Boolean,v:ComponentVisualState) {
    val b=BoardRegistry.boards.getValue(p.kind)
    val w=b.boardWidth/2f;val h=b.boardHeight/2f
    body(Color(b.pcbColor),-w,-h,w*2,h*2,7f)
    // Surface features vary by board family; pin pads always use registry coordinates.
    val wide=p.kind in setOf(Kind.ARDUINO_UNO,Kind.ARDUINO_MEGA)
    val pico=b.family==BoardFamily.RP2040 || b.family==BoardFamily.RP2350
    val esp=b.family in setOf(BoardFamily.ESP8266,BoardFamily.ESP32,BoardFamily.ESP32_C3)
    val usbX=if(wide) -w+27f else 0f
    body(metal,usbX-19f,-h-2f,38f,23f,2f)
    body(plastic,usbX-14f,-h+1f,28f,5f,1f)
    if(wide) {
        body(plastic,w-37f,-h+8f,27f,30f,3f);drawCircle(Navy,7f,Offset(w-24f,-h+12f))
        if(p.kind==Kind.ARDUINO_UNO) {
            body(plastic,-14f,-40f,28f,91f,2f)
            repeat(14) { n -> listOf(-1f,1f).forEach { side -> drawLine(metal,Offset(side*14f,-35f+n*6),Offset(side*19f,-35f+n*6),2f) } }
        } else { body(plastic,-26f,-19f,52f,52f,2f)
            repeat(12) { n -> listOf(-1f,1f).forEach { side -> drawLine(metal,Offset(side*26f,-15f+n*4),Offset(side*31f,-15f+n*4),1.5f) } } }
        body(metal,-w+18f,15f,19f,36f,3f)
        listOf(-1,1).forEach { x -> drawCircle(metal,8f,Offset(x*(w-20f),h-20f));drawCircle(Navy,4.5f,Offset(x*(w-20f),h-20f)) }
    } else if(esp) {
        val moduleW=(w*.65f).coerceAtMost(37f)
        body(metal,-moduleW,-h+32f,moduleW*2,h*.8f,2f)
        body(plastic,-moduleW,-h+20f,moduleW*2,20f,1f)
        val antenna=Path().apply { moveTo(-moduleW+5,-h+36);lineTo(-moduleW+5,-h+25)
            lineTo(-moduleW+15,-h+25);lineTo(-moduleW+15,-h+33);lineTo(-moduleW+25,-h+33)
            lineTo(-moduleW+25,-h+25);lineTo(moduleW-5,-h+25) }
        drawPath(antenna,gold,style=Stroke(2f))
        body(plastic,-18f,h-48f,36f,22f,2f)
        screw(-w+22f,h-16f,6f);screw(w-22f,h-16f,6f)
    } else {
        body(plastic,-22f,-16f,44f,44f,2f)
        if(b.capabilities.wifi.hardware && !pico) body(metal,-21f,-h+49f,42f,32f,2f)
        if(pico && p.kind==Kind.RASPBERRY_PICO_W) body(metal,-25f,34f,50f,36f,2f)
        else body(metal,-13f,36f,26f,10f,2f)
        body(plastic,-w+18f,-h+29f,20f,14f,2f)
    }
    if(details) print(b.product.take(20),y=h-8f,font=9f)
    drawCircle(v.brightness.let { if(it>.01f) Mint else Color(0xFF426849) },3f,Offset(0f,-h+34f))
    b.pins.forEachIndexed { i,pin ->
        if(!pin.connectable) return@forEachIndexed
        val at=localTerminalOffset(p,i)/p.sizeScale
        body(plastic,at.x-6f,at.y-6f,12f,12f,1f)
        drawCircle(gold,3.8f,at);drawCircle(Navy,2f,at)
        if(details) print(pin.physicalLabel,at.x+if(at.x<0) 15f else -15f,at.y+2.5f,6.5f)
    }
}
