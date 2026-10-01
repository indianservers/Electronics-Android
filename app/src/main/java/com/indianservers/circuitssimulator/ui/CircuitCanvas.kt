package com.indianservers.circuitssimulator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.Health
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private fun routedPoints(a: Offset,b: Offset): List<Offset> = buildList {
    add(a)
    if (a.y>570f && b.y>570f && abs(a.x-b.x)>250f) {
        add(Offset(a.x,720f)); add(Offset(b.x,720f))
    } else if(abs(a.x-b.x)>15f && abs(a.y-b.y)>15f) add(Offset(b.x,a.y))
    add(b)
}

private fun pinDirection(part:PlacedComponent,pin:Int):Offset {
    val local=localTerminalOffset(part,pin)
    if(local==Offset.Zero) return Offset.Zero
    val radial=if(abs(local.x)>abs(local.y)) Offset(if(local.x<0f) -1f else 1f,0f)
        else Offset(0f,if(local.y<0f) -1f else 1f)
    val radians=Math.toRadians(part.rotation.toDouble())
    return Offset((radial.x*kotlin.math.cos(radians)-radial.y*kotlin.math.sin(radians)).toFloat(),
        (radial.x*kotlin.math.sin(radians)+radial.y*kotlin.math.cos(radians)).toFloat())
}

/** Route from each physical pin outward, then around component bodies. */
internal fun routedWirePoints(wire:Wire,parts:Map<String,PlacedComponent>):List<Offset> {
    val first=parts[wire.start.componentId] ?: return emptyList()
    val last=parts[wire.end.componentId] ?: return emptyList()
    if(wire.start.index !in 0 until first.terminalCount || wire.end.index !in 0 until last.terminalCount)
        return emptyList()
    val a=terminalPosition(first,wire.start.index)
    val b=terminalPosition(last,wire.end.index)
    val aOut=a+pinDirection(first,wire.start.index)*29f
    val bOut=b+pinDirection(last,wire.end.index)*29f
    val top=minOf(aOut.y,bOut.y,parts.values.minOfOrNull { it.y-95f } ?: 0f)-30f
    val bottom=maxOf(aOut.y,bOut.y,parts.values.maxOfOrNull { it.y+95f } ?: 0f)+30f
    val left=minOf(aOut.x,bOut.x,parts.values.minOfOrNull { it.x-95f } ?: 0f)-30f
    val right=maxOf(aOut.x,bOut.x,parts.values.maxOfOrNull { it.x+95f } ?: 0f)+30f
    val routes=listOf(
        listOf(a,aOut,Offset(bOut.x,aOut.y),bOut,b),
        listOf(a,aOut,Offset(aOut.x,bOut.y),bOut,b),
        listOf(a,aOut,Offset(aOut.x,top),Offset(bOut.x,top),bOut,b),
        listOf(a,aOut,Offset(aOut.x,bottom),Offset(bOut.x,bottom),bOut,b),
        listOf(a,aOut,Offset(left,aOut.y),Offset(left,bOut.y),bOut,b),
        listOf(a,aOut,Offset(right,aOut.y),Offset(right,bOut.y),bOut,b)
    )
    fun blocked(p:Offset,q:Offset,part:PlacedComponent):Boolean {
        val lx=part.x-49f;val rx=part.x+49f;val ty=part.y-51f;val by=part.y+51f
        return if(abs(p.y-q.y)<.1f) p.y in ty..by && maxOf(p.x,q.x)>lx && min(p.x,q.x)<rx
            else if(abs(p.x-q.x)<.1f) p.x in lx..rx && maxOf(p.y,q.y)>ty && min(p.y,q.y)<by
            else false
    }
    return routes.minBy { route ->
        val segments=route.zipWithNext()
        val collisions=segments.sumOf { (p,q) -> parts.values.count { blocked(p,q,it) } }
        collisions*100000f+segments.sumOf { (p,q) -> (q-p).getDistance().toDouble() }.toFloat()+
            route.size*3f
    }.fold(mutableListOf<Offset>()) { result,point ->
        if(result.lastOrNull()!=point) result.add(point)
        result
    }
}

private data class Viewport(val scale: Float, val origin: Offset) {
    fun world(pixel: Offset) = (pixel - origin) / scale
    fun pixel(world: Offset) = world * scale + origin
}

@Composable
private fun animatedAngle(part:PlacedComponent):Float {
    var previous by remember(part.id) { mutableIntStateOf(part.rotation) }
    var target by remember(part.id) { mutableFloatStateOf(part.rotation.toFloat()) }
    LaunchedEffect(part.rotation) {
        val raw=part.rotation-previous
        val shortest=when(raw) { -270 -> 90; 270 -> -90; else -> raw }
        target+=shortest
        previous=part.rotation
    }
    return animateFloatAsState(target,tween(150),label="Rotate ${part.reference}").value
}

@Composable
fun CircuitCanvas(state: SimulatorState, model: SimulatorViewModel, modifier: Modifier = Modifier) {
    val haptics=LocalHapticFeedback.current
    val transition=rememberInfiniteTransition(label="Current flow")
    val phaseState=transition.animateFloat(0f,1f,infiniteRepeatable(tween(2600,easing=LinearEasing)),label="Direction")
    val animatedAngles=state.circuit.components.associate { part ->
        key(part.id) { part.id to animatedAngle(part) }
    }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var wireStart by remember { mutableStateOf<TerminalRef?>(null) }
    var wireEnd by remember { mutableStateOf<Offset?>(null) }
    var wireHover by remember { mutableStateOf<TerminalRef?>(null) }
    var pendingTerminal by remember(state.circuit) { mutableStateOf<TerminalRef?>(null) }
    var placementPreview by remember { mutableStateOf<Offset?>(null) }
    var panning by remember { mutableStateOf(false) }
    var pinching by remember { mutableStateOf(false) }
    var pinchOccurred by remember { mutableStateOf(false) }
    val viewport = remember(canvasSize, state.zoom, state.panX, state.panY) {
        val scale = min(canvasSize.width / 1000f, canvasSize.height / 900f) * state.zoom
        Viewport(scale.coerceAtLeast(.001f),Offset((canvasSize.width - 1000f * scale)/2 + state.panX,
            (canvasSize.height - 900f * scale).coerceAtLeast(0f)*.5f + state.panY))
    }
    fun hitComponent(point: Offset): PlacedComponent? {
        val world = viewport.world(point)
        return state.circuit.components.lastOrNull { hypot((it.x-world.x).toDouble(),(it.y-world.y).toDouble()) < 90.0 }
    }
    fun hitTerminal(point: Offset): TerminalRef? {
        val world = viewport.world(point)
        return state.circuit.components.flatMap { p ->
            (0 until p.terminalCount).map { pin ->
                val t=terminalPosition(p,pin)
                val distance=hypot((t.x-world.x).toDouble(),(t.y-world.y).toDouble())
                TerminalRef(p.id,pin) to distance
            }
        }.filter { (ref,distance) -> distance < if(ref.componentId==state.selectedId) 48.0 else 38.0 }
            .minByOrNull { it.second }?.first
    }
    fun hitWire(point: Offset): Wire? {
        val world=viewport.world(point)
        val parts=state.circuit.components.associateBy { it.id }
        return state.circuit.wires.firstOrNull { wire ->
            routedWirePoints(wire,parts).zipWithNext().any { (first,last) ->
                val delta=last-first
                val fraction=if(delta.getDistanceSquared()<1f) 0f else
                    (((world-first).x*delta.x+(world-first).y*delta.y)/delta.getDistanceSquared()).coerceIn(0f,1f)
                (world-(first+delta*fraction)).getDistance()<18f
            }
        }
    }
    Canvas(modifier.onSizeChanged { model.setCanvasSize(Size(it.width.toFloat(),it.height.toFloat())) }.semantics {
        contentDescription = "Circuit workspace. Tap two component pins to connect them, or drag from one pin to another."
        customActions = state.circuit.components.filter { it.kind != Kind.JUNCTION }.map { part ->
            CustomAccessibilityAction("Select ${part.reference}, ${part.kind.title}") {
                model.select(part.id); true
            }
        }
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false)
            var active = true
            while (active) {
                val event=awaitPointerEvent()
                val contacts=event.changes.filter { it.pressed }
                if (contacts.size >= 2) {
                    pinching=true
                    pinchOccurred=true
                    val a=contacts[0]; val b=contacts[1]
                    val before=(a.previousPosition-b.previousPosition).getDistance()
                    val after=(a.position-b.position).getDistance()
                    if (before>1f && after>1f) model.transformGesture((after/before).coerceIn(.85f,1.15f),
                        (a.previousPosition+b.previousPosition)/2f,(a.position+b.position)/2f,canvasSize)
                }
                active=contacts.isNotEmpty()
                if (!active) pinching=false
            }
        }
    }.pointerInput(state.circuit, state.placement, state.selectedId, viewport) {
        detectTapGestures(onDoubleTap = { model.resetView() },onLongPress={ point ->
            hitComponent(point)?.let { model.showContext(it.id);haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        },onTap = { point ->
            if (state.placement != null) {
                val w=viewport.world(point)
                model.place((w.x/10f).toInt()*10f,(w.y/10f).toInt()*10f)
            } else {
                val pin=hitTerminal(point)
                val start=pendingTerminal
                when {
                    pin!=null && start==null -> { pendingTerminal=pin;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    pin!=null && pin==start -> pendingTerminal=null
                    pin!=null && start!=null -> {
                        if(pin.componentId!=start.componentId) model.connect(start,pin)
                        pendingTerminal=null
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    start!=null && hitWire(point)!=null -> {
                        val w=viewport.world(point)
                        model.connectToJunction(start,w.x,w.y,hitWire(point)?.id)
                        pendingTerminal=null
                    }
                    else -> {
                        pendingTerminal=null
                        val hit=hitComponent(point)
                        if (hit?.kind == Kind.SWITCH) { model.toggleSwitch(hit.id);haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        if(hit!=null) model.select(hit.id) else model.selectWire(hitWire(point)?.id)
                    }
                }
            }
        })
    }.pointerInput(state.circuit, state.placement, viewport) {
        detectDragGestures(onDragStart = { point ->
            pinchOccurred=false
            if(state.placement!=null) placementPreview=point
            else {
                val terminal=hitTerminal(point)
                if (terminal != null) { pendingTerminal=null;wireStart=terminal; wireEnd=point }
                else { dragging=hitComponent(point)?.id; panning=dragging==null }
            }
            dragDelta=Offset.Zero
        },onDrag = { change, amount ->
            change.consume()
            dragDelta += amount
            if (wireStart != null) {
                wireEnd=change.position
                val hover=hitTerminal(change.position)?.takeIf { it != wireStart && it.componentId!=wireStart?.componentId }
                if(hover!=null && hover!=wireHover) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                wireHover=hover
            }
            if (placementPreview != null) placementPreview=change.position
        },onDragEnd = {
            val start=wireStart
            if (pinching || pinchOccurred) { wireStart=null; wireEnd=null; wireHover=null; dragging=null; dragDelta=Offset.Zero; panning=false; pinchOccurred=false; placementPreview=null; return@detectDragGestures }
            if(placementPreview!=null && state.placement!=null) {
                val world=viewport.world(placementPreview!!)
                model.place((world.x/10f).toInt()*10f,(world.y/10f).toInt()*10f)
            } else if (start != null) {
                val target=wireHover ?: wireEnd?.let(::hitTerminal)
                if (target != null) { model.connect(start,target);haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                else wireEnd?.let { end ->
                    val world=viewport.world(end)
                    hitWire(end)?.id?.let { model.connectToJunction(start,world.x,world.y,it);
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            } else if (dragging != null) {
                val p=state.circuit.components.firstOrNull { it.id==dragging }
                if (p != null) {
                    val dx=dragDelta.x/viewport.scale; val dy=dragDelta.y/viewport.scale
                    model.move(p.id,((p.x+dx)/10f).toInt()*10f,((p.y+dy)/10f).toInt()*10f)
                }
            } else if (panning) model.pan(dragDelta.x,dragDelta.y)
            wireStart=null; wireEnd=null; wireHover=null; dragging=null; dragDelta=Offset.Zero; panning=false; placementPreview=null
        },onDragCancel = { wireStart=null; wireEnd=null; wireHover=null; dragging=null; dragDelta=Offset.Zero; panning=false; placementPreview=null })
    }) {
        canvasSize=size
        val renderViewport=if(panning) viewport.copy(origin=viewport.origin+dragDelta) else viewport
        drawRect(Color(0xFF071522))
        val gridStep=32f*renderViewport.scale
        if (gridStep > 8f) {
            var x=renderViewport.origin.x % gridStep
            while(x<size.width) {
                var y=renderViewport.origin.y % gridStep
                while(y<size.height) { drawCircle(Color(0xFF234867).copy(alpha=.62f),1.15f,Offset(x,y)); y+=gridStep }
                x+=gridStep
            }
        }
        withTransform({ translate(renderViewport.origin.x,renderViewport.origin.y); scale(renderViewport.scale,renderViewport.scale,Offset.Zero) }) {
            val lookup=state.circuit.components.associate { original ->
                original.id to if(original.id==dragging) original.copy(
                    x=original.x+dragDelta.x/viewport.scale,
                    y=original.y+dragDelta.y/viewport.scale) else original
            }
            val connectedPins=state.circuit.wires.flatMap { listOf(it.start,it.end) }.groupBy { it.componentId }
            val routedLookup=lookup.mapValues { (_,part) ->
                part.copy(rotation=(animatedAngles[part.id] ?: part.rotation.toFloat()).roundToInt())
            }
            state.circuit.wires.sortedBy { wire ->
                val first=abs(state.result.readings[wire.start.componentId]?.current ?: 0.0)
                val second=abs(state.result.readings[wire.end.componentId]?.current ?: 0.0)
                first>1e-5 && second>1e-5
            }.forEachIndexed { wireIndex, wire ->
                val points=routedWirePoints(wire,routedLookup)
                if(points.size<2) return@forEachIndexed
                val a=points.first();val b=points.last()
                val route=Path().apply {
                    moveTo(a.x,a.y)
                    points.drop(1).forEach { lineTo(it.x,it.y) }
                }
                val startCurrent=abs(state.result.readings[wire.start.componentId]?.current ?: 0.0)
                val endCurrent=abs(state.result.readings[wire.end.componentId]?.current ?: 0.0)
                val active=state.running && state.result.error==null && startCurrent>1e-5 && endCurrent>1e-5
                val tint=if(active) Mint else Blue
                val selected=wire.id==state.selectedWireId
                if(active) drawPath(route,tint.copy(alpha=.13f),style=Stroke(17f))
                if(selected) drawPath(route,Color.White.copy(alpha=.22f),style=Stroke(17f))
                if(active && !selected) drawPath(route,Brush.horizontalGradient(
                    listOf(Blue,Blue,Mint,Mint),startX=0f,endX=1000f),style=Stroke(5f))
                else drawPath(route,if(selected) Color(0xFF9CD9FF) else tint.copy(alpha=.75f),
                    style=Stroke(if(selected) 7f else 5f))
                if(active) {
                    val startReading=state.result.readings[wire.start.componentId]?.current ?: 0.0
                    val leaving=if(wire.start.index==0) -startReading else startReading
                    val pair=points.zipWithNext().maxByOrNull { (first,last) -> (last-first).getDistance() }
                    if(pair!=null) {
                        val first=if(leaving>=0.0) pair.first else pair.second
                        val last=if(leaving>=0.0) pair.second else pair.first
                        val delta=last-first
                        val length=delta.getDistance().coerceAtLeast(1f)
                        val dir=delta/length
                        val progress=(phaseState.value+wireIndex*.19f)%1f
                        val mid=first+delta*(.2f+.6f*progress)
                        val tip=mid+dir*11f
                        val base=mid-dir*8f
                        val normal=Offset(-dir.y,dir.x)*7f
                        val arrow=Path().apply { moveTo(tip.x,tip.y);lineTo(base.x+normal.x,base.y+normal.y);
                            lineTo(base.x-normal.x,base.y-normal.y);close() }
                        drawPath(arrow,Color(0xFFB9FFE1).copy(alpha=.55f))
                    }
                }
                drawCircle(if(active && a.x<500f) Blue else tint,8f,a)
                drawCircle(if(active && b.x<500f) Blue else tint,8f,b)
                wire.label?.let { label ->
                    val segment=points.zipWithNext().maxByOrNull { (first,last) -> (last-first).getDistance() }
                    if(segment!=null) {
                        val center=(segment.first+segment.second)/2f
                        drawContext.canvas.nativeCanvas.apply {
                            val paint=android.graphics.Paint(3).apply {
                                textAlign=android.graphics.Paint.Align.CENTER;textSize=22f
                            }
                            val width=paint.measureText(label)+24f
                            paint.color=android.graphics.Color.rgb(17,54,78)
                            drawRoundRect(center.x-width/2f,center.y-33f,
                                center.x+width/2f,center.y+5f,10f,10f,paint)
                            paint.color=android.graphics.Color.rgb(160,221,255)
                            drawText(label,center.x,center.y-6f,paint)
                        }
                    }
                }
            }
            state.circuit.components.forEach { original ->
                val p=lookup.getValue(original.id)
                val reading=state.result.readings[p.id]
                val intensity=when(p.kind) {
                    Kind.LED -> ((abs(reading?.current ?: 0.0)/.02).coerceIn(0.0,1.0)).toFloat()
                    Kind.LAMP -> ((reading?.power ?: 0.0)/(p.value("rating").coerceAtLeast(.01))).coerceIn(0.0,1.0).toFloat()
                    else -> 0f
                }
                drawComponent(p,p.id==state.selectedId,if(state.running) intensity else 0f,
                    animatedAngles[p.id] ?: p.rotation.toFloat(),connectedPins[p.id].orEmpty().map { it.index }.toSet())
                if (p.kind != Kind.JUNCTION) drawContext.canvas.nativeCanvas.apply {
                    val paint=android.graphics.Paint(3).apply { color=android.graphics.Color.rgb(221,234,255);textSize=27f;textAlign=android.graphics.Paint.Align.CENTER }
                    drawText(p.reference,p.x,p.y-88f,paint)
                    val secondary=when(p.kind) {
                        Kind.BATTERY,Kind.SOURCE -> EngineeringUnits.format(p.value("voltage"),"V")
                        Kind.FUNCTION_GENERATOR -> EngineeringUnits.format(p.value("frequency"),"Hz")
                        Kind.RESISTOR -> EngineeringUnits.format(p.value("resistance"),"Ω")
                        Kind.LDR -> EngineeringUnits.format(p.value("illuminanceLux"),"lx")
                        Kind.THERMISTOR -> EngineeringUnits.format(p.value("temperatureC"),"°C")
                        Kind.POTENTIOMETER -> "${(p.value("position")*100).toInt()}%"
                        Kind.FUSE -> if(reading?.health==Health.FAILED_OPEN) "OPEN" else EngineeringUnits.format(p.value("currentRating"),"A")
                        Kind.CAPACITOR,Kind.ELECTROLYTIC -> EngineeringUnits.format(p.value("capacitance"),"F")
                        Kind.INDUCTOR -> EngineeringUnits.format(p.value("inductance"),"H")
                        else -> p.kind.title
                    }
                    paint.color=android.graphics.Color.rgb(151,174,201);paint.textSize=23f
                    drawText(secondary,p.x,p.y+96f,paint)
                    if(state.measurementsVisible && reading!=null && state.result.error==null &&
                        p.kind!=Kind.GROUND) {
                        val label="${EngineeringUnits.format(reading.voltage,"V")}  ·  "+
                            EngineeringUnits.format(reading.current,"A")
                        paint.textSize=19f
                        val width=paint.measureText(label)+24f
                        paint.color=android.graphics.Color.rgb(18,42,58)
                        drawRoundRect(p.x-width/2f,p.y+112f,p.x+width/2f,p.y+151f,11f,11f,paint)
                        paint.color=android.graphics.Color.rgb(160,245,215)
                        drawText(label,p.x,p.y+139f,paint)
                    }
                }
            }
            val placing=state.placement
            val preview=placementPreview
            if(placing!=null && preview!=null) {
                val world=viewport.world(preview)
                drawCircle(Blue.copy(alpha=.11f),96f,world)
                drawComponent(PlacedComponent(kind=placing,reference="",x=world.x,y=world.y),true)
            }
            wireStart?.let { start ->
                val p=lookup[start.componentId]
                if(p!=null && wireEnd!=null) {
                    val a=terminalPosition(p,start.index,animatedAngles[p.id] ?: p.rotation.toFloat())
                    val b=wireHover?.let { hover -> lookup[hover.componentId]?.let {
                        terminalPosition(it,hover.index,animatedAngles[it.id] ?: it.rotation.toFloat()) } }
                        ?: viewport.world(wireEnd!!)
                    val points=routedPoints(a,b)
                    val path=Path().apply { moveTo(a.x,a.y);points.drop(1).forEach { lineTo(it.x,it.y) } }
                    drawPath(path,Mint.copy(alpha=.7f),style=Stroke(4f))
                    drawCircle(Mint,14f,a)
                    if(wireHover!=null) {
                        drawCircle(Mint.copy(alpha=.16f),34f,b)
                        drawCircle(Mint,13f,b)
                        drawCircle(Color.White,5f,b)
                    }
                }
            }
            pendingTerminal?.let { ref ->
                lookup[ref.componentId]?.let { part ->
                    val position=terminalPosition(part,ref.index,animatedAngles[part.id] ?: part.rotation.toFloat())
                    drawCircle(Mint.copy(alpha=.24f),31f,position)
                    drawCircle(Mint,15f,position,style=Stroke(3f))
                }
            }
        }
    }
}
