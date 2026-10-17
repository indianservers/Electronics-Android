package com.indianservers.circuitssimulator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.Health
import com.indianservers.circuitssimulator.simulation.sensorResistanceOhms
import com.indianservers.circuitssimulator.simulation.threeTerminalCurrents
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlin.math.abs

@Composable
internal fun ComponentPropertiesPanel(part:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val definition=ComponentRegistry.definitions.getValue(part.kind)
    var tab by remember(part.id) { mutableIntStateOf(0) }
    var more by remember(part.id) { mutableStateOf(false) }
    var detailedBoard by remember(part.id) { mutableStateOf(false) }
    var connecting by remember(part.id) { mutableStateOf<TerminalRef?>(null) }
    var pinQuery by remember(part.id) { mutableStateOf("") }
    val height=(LocalConfiguration.current.screenHeightDp*.55f).coerceIn(280f,460f).dp
    val reading=state.result.takeIf { it.error==null }?.readings?.get(part.id)
    if(detailedBoard && part.kind.isBoard) {
        Column {
            TextButton(onClick={detailedBoard=false}) { Text("Back to properties") }
            BoardInspector(part,state,model)
        }
        return
    }
    Surface(color=Panel,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),
        border=BorderStroke(1.dp,Muted.copy(alpha=.3f))) {
        Column(Modifier.fillMaxWidth().height(height).padding(horizontal=14.dp,vertical=8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                ComponentThumbnail(part.kind,Modifier.size(48.dp),part)
                Column(Modifier.weight(1f).padding(start=10.dp)) {
                    Text("${part.reference} · ${part.kind.title}",color=TextIce,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                    Text(if(part.kind.isBoard) "${BoardRegistry.boards.getValue(part.kind).logicVoltage} V logic · supported firmware subset"
                        else if(definition.modelAccuracy==ModelAccuracy.IDEAL) "Ideal simulation model" else "${definition.modelAccuracy.name.replace('_',' ').lowercase()} model",
                        color=Muted,fontSize=11.sp)
                }
                TextButton(onClick={model.select(null)}) { Text("Close") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={model.rotate(part.id)}) { Text("Rotate") }
                TextButton(onClick={model.resize(part.id,part.sizeScale-.15f)}) { Text("Smaller") }
                Text("${(part.sizeScale*100).toInt()}%",color=Muted,fontSize=11.sp)
                TextButton(onClick={model.resize(part.id,part.sizeScale+.15f)}) { Text("Larger") }
                TextButton(onClick={model.remove(part.id)}) { Text("Remove",color=MaterialTheme.colorScheme.error) }
            }
            TabRow(selectedTabIndex=tab,containerColor=Panel,contentColor=Mint) {
                listOf("Properties","Pins","Specifications").forEachIndexed { index,label ->
                    Tab(selected=tab==index,onClick={tab=index},text={Text(label,fontSize=12.sp)})
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=10.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                when(tab) {
                    0 -> {
                        if(state.result.error!=null) Text("Connect the required pins to get live readings.",color=Muted,fontSize=12.sp)
                        if(reading!=null) {
                            Text("${EngineeringUnits.format(reading.voltage,"V")}   ·   ${EngineeringUnits.format(abs(reading.current),"A")}   ·   ${EngineeringUnits.format(reading.power,"W")}",color=Mint,fontSize=12.sp)
                            if(reading.health!=Health.NORMAL) Text("${reading.health}: check the component rating",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                            reading.estimatedTemperatureC?.let { Text("Estimated temperature: ${EngineeringUnits.format(it,"°C")}",color=Muted,fontSize=11.sp) }
                        }
                        if(part.kind in setOf(Kind.SWITCH,Kind.PUSH_BUTTON,Kind.NC_PUSH_BUTTON,Kind.SPDT_SWITCH)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(if(part.kind==Kind.SPDT_SWITCH) "Common connected to ${if(part.closed) "A" else "B"}" else "Contact ${if(part.closed) "closed" else "open"}",Modifier.weight(1f),color=TextIce)
                                Switch(checked=part.closed,onCheckedChange={model.toggleSwitch(part.id)})
                            }
                        }
                        if(part.kind.isBoard) {
                            PropertyEditor(part,definition.parameters.first { it.key=="usbPower" },model)
                            Text("USB power supplies the board rails. Turn it off when testing an external supply.",color=Muted,fontSize=11.sp)
                            Row {
                                TextButton(onClick={model.showFirmware(true,part.id)}) { Text("Open code") }
                                TextButton(onClick={detailedBoard=true}) { Text("Board guide") }
                            }
                            Text("Use Pins to connect a header or set a manual GPIO level.",color=Muted,fontSize=12.sp)
                        } else {
                            val basic=ComponentProperties.primary(definition)
                            basic.forEach { spec ->
                                val shared=ComponentProperties.sharedStimulus(part.kind,spec.key)
                                val shown=if(shared && state.firmware.running) part.copy(parameters=part.parameters+
                                    (spec.key to if(spec.key=="temperatureC") state.circuit.environment.temperatureC else state.circuit.environment.illuminanceLux)) else part
                                PropertyEditor(shown,spec,model)
                                if(shared) Text("During firmware runs, this uses the shared Environment stimulus. Applying a value here updates it too.",color=Muted,fontSize=10.sp)
                            }
                            val advanced=definition.parameters.filter { it !in basic }
                            if(advanced.isNotEmpty()) {
                                TextButton(onClick={more=!more}) { Text(if(more) "Hide model settings" else "Model settings (${advanced.size})") }
                                if(more) {
                                    Text("These tune the simulation model; they are not guaranteed device ratings.",color=Muted,fontSize=11.sp)
                                    advanced.forEach { PropertyEditor(part,it,model) }
                                }
                            }
                            if(definition.parameters.isEmpty()) Text("No adjustable numeric properties. Use Pins to wire this component.",color=Muted,fontSize=12.sp)
                            if(part.kind in setOf(Kind.LDR,Kind.THERMISTOR)) Text("Sensed resistance: ${EngineeringUnits.format(sensorResistanceOhms(part),"Ω")}",color=Mint,fontSize=12.sp)
                            if(part.kind in setOf(Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS,Kind.PMOS) && state.result.error==null) {
                                val currents=threeTerminalCurrents(part,DoubleArray(3) { state.result.nodeVoltages[TerminalRef(part.id,it)] ?: 0.0 })
                                currents.forEachIndexed { i,current -> Text("${ComponentPins.name(part,i)} current: ${EngineeringUnits.format(current,"A")}",color=Mint,fontSize=11.sp) }
                            }
                            if(part.kind.category=="Embedded" || part.kind in MotionSensorParts.profiles || part.kind in ChemistrySensorParts.profiles || part.kind in GasSensorParts.pinNames || part.kind in PowerSensorParts.pinNames || part.kind in LightSensorParts.pinNames) PeripheralInspector(part,state,model)
                            if(part.kind==Kind.FUNCTION_GENERATOR) SignalChart(state.transient?.traces?.get(part.id),state.elapsedSeconds,Modifier.fillMaxWidth().height(100.dp))
                        }
                        if(definition.parameters.isNotEmpty()) TextButton(onClick={model.resetComponentProperties(part.id)}) { Text("Reset values") }
                    }
                    1 -> {
                        Text("Choose the exact terminal. Wires stay attached to that pin when you move, resize or rotate the part.",color=Muted,fontSize=11.sp)
                        if(part.terminalCount>8) OutlinedTextField(pinQuery,{pinQuery=it},Modifier.fillMaxWidth(),
                            label={Text("Find pin, GPIO or alias")},singleLine=true)
                        val visible=(0 until part.terminalCount).filter { index ->
                            pinQuery.isBlank() || ComponentPins.name(part,index).contains(pinQuery,true) ||
                                part.kind.isBoard && index in BoardRegistry.boards.getValue(part.kind).findPins(pinQuery)
                        }
                        if(visible.isEmpty()) Text("No matching pins",color=Muted)
                        visible.forEach { index ->
                            PinPropertyRow(part,index,state,model,onConnect={connecting=TerminalRef(part.id,index)})
                            HorizontalDivider(color=Muted.copy(alpha=.15f))
                        }
                    }
                    else -> {
                        Text(definition.description,color=TextIce,fontSize=12.sp)
                        val source=definition.datasheet
                        Text("Manufacturer: ${source.manufacturer ?: "Generic / ideal model"}",color=Muted,fontSize=12.sp)
                        source.partNumber?.let { Text("Part: $it",color=TextIce,fontSize=12.sp) }
                        source.packageName?.let { Text("Package: $it",color=Muted,fontSize=12.sp) }
                        source.revision?.let { Text("Document revision: $it",color=Muted,fontSize=11.sp) }
                        source.sourceNotes?.let { Text(it,color=Muted,fontSize=11.sp) }
                        if(source.physicalPinOrder.isNotEmpty() && !part.kind.isBoard) Text("${if(source.packageName?.contains("simulated",ignoreCase=true)==true) "Simulator carrier header order" else "Package order"}: ${source.physicalPinOrder.joinToString(" · ")}. Electrical terminals are identified by their names in Pins.",color=Muted,fontSize=11.sp)
                        definition.limitations.forEach { Text(it,color=Muted,fontSize=11.sp) }
                        if(part.kind.isBoard) {
                            val board=BoardRegistry.boards.getValue(part.kind)
                            Text("${board.mcu} · ${board.revision}\nADC: ${board.adcBits}-bit default, ${board.adcReferenceVoltage} V nominal reference",color=TextIce,fontSize=12.sp)
                            board.warnings.forEach { Text(it,color=Muted,fontSize=11.sp) }
                            TextButton(onClick={detailedBoard=true}) { Text("Detailed board guide and sources") }
                        }
                        val context=LocalContext.current
                        source.referenceUrl?.let { url -> TextButton(onClick={
                            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
                        }) { Text("Open manufacturer reference") } }
                    }
                }
            }
        }
    }
    connecting?.let { start -> ConnectionPicker(start,state,model,onDismiss={connecting=null}) }
}

@Composable
private fun PropertyEditor(part:PlacedComponent,spec:Parameter,model:SimulatorViewModel) {
    val choices=if(spec.key=="address" && part.kind in MotionSensorParts.profiles)
        MotionSensorParts.profiles.getValue(part.kind).addresses.map { "0x${it.toString(16).uppercase()}" to it.toDouble() }
        else ComponentProperties.choices(spec)
    if(choices.isNotEmpty()) {
        Text(ComponentProperties.label(spec),color=TextIce,fontSize=12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            choices.forEach { (label,value) -> FilterChip(selected=part.value(spec.key)==value,
                onClick={model.setParameter(part.id,spec.key,value)},label={Text(label)},modifier=Modifier.testTag("property-${spec.key}-$label")) }
        }
        return
    }
    var draft by remember(part.id,spec.key) { mutableStateOf(ComponentProperties.input(spec,part.value(spec.key))) }
    var dirty by remember(part.id,spec.key) { mutableStateOf(false) }
    LaunchedEffect(part.value(spec.key)) { if(!dirty) draft=ComponentProperties.input(spec,part.value(spec.key)) }
    val validation=ComponentProperties.parse(part,spec,draft)
    val focus=LocalFocusManager.current
    fun commit() {
        validation.value?.let { if(model.setParameter(part.id,spec.key,it)) {
            dirty=false;draft=ComponentProperties.input(spec,it);focus.clearFocus()
        } }
    }
    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft,{draft=it;dirty=true},Modifier.weight(1f).testTag("property-${spec.key}"),
            label={Text(ComponentProperties.label(spec),fontSize=12.sp)},singleLine=true,
            isError=dirty && validation.error!=null,
            supportingText={Text(if(dirty && validation.error!=null) validation.error else
                "${ComponentProperties.input(spec,spec.min)} to ${ComponentProperties.input(spec,spec.max)}",fontSize=10.sp)},
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
            keyboardActions=KeyboardActions(onDone={commit()}))
        TextButton(onClick={commit()},enabled=dirty && validation.error==null,
            modifier=Modifier.padding(top=8.dp).testTag("apply-${spec.key}")) { Text("Apply") }
    }
    if(ComponentProperties.isPercent(spec)) {
        Slider(value=part.value(spec.key).toFloat(),valueRange=spec.min.toFloat()..spec.max.toFloat(),
            onValueChange={dirty=false;model.previewParameter(part.id,spec.key,it.toDouble())},
            onValueChangeFinished=model::finishParameterPreview,modifier=Modifier.testTag("slider-${spec.key}"))
    }
}

@Composable
private fun PinPropertyRow(part:PlacedComponent,index:Int,state:SimulatorState,model:SimulatorViewModel,onConnect:()->Unit) {
    val ref=TerminalRef(part.id,index)
    val board=if(part.kind.isBoard) BoardRegistry.boards.getValue(part.kind) else null
    val pin=board?.pins?.get(index)
    val number=ComponentPins.packageNumber(part,index)
    val label=ComponentPins.name(part,index).ifEmpty { "Junction" }
    val peers=remember(state.circuit,ref) {
        val sharedPads=ComponentPins.net(state.circuit.copy(wires=emptyList()),ref)
        ComponentPins.net(state.circuit,ref).filter { it !in sharedPads }
    }
    val byId=remember(state.circuit.components) { state.circuit.components.associateBy { it.id } }
    val connected=peers.mapNotNull { other -> byId[other.componentId]?.let {
        "${it.reference} · ${ComponentPins.name(it,other.index).ifEmpty { "Junction" }}"
    } }.distinct()
    val voltage=state.result.takeIf { it.error==null }?.nodeVoltages?.get(ref)
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(label+(number?.let { " · ${pin?.connector?.let { c -> "$c " }.orEmpty()}pin $it" } ?: ""),
                Modifier.weight(1f),color=TextIce,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            Text(voltage?.let { EngineeringUnits.format(it,"V") } ?: "—",color=Mint,fontSize=11.sp)
            TextButton(onClick=onConnect,enabled=ComponentPins.canConnect(part,index),
                modifier=Modifier.testTag("connect-pin-$index")) { Text("Connect") }
        }
        Text(ComponentPins.role(part,index),color=Muted,fontSize=11.sp)
        Text(if(connected.isEmpty()) "Unconnected" else "Connected: ${connected.take(4).joinToString()}"+
            if(connected.size>4) " +${connected.size-4} more" else "",color=if(connected.isEmpty()) Muted else Mint,fontSize=11.sp)
        pin?.maxVoltage?.let { Text("Input range: ${pin.minVoltage ?: 0.0}–$it V",color=Muted,fontSize=10.sp) }
        if(pin?.aliases?.isNotEmpty()==true) Text("Aliases: ${pin.aliases.joinToString()}",color=Muted,fontSize=10.sp)
        if(pin?.notes?.isNotBlank()==true) Text(pin.notes,color=Muted,fontSize=10.sp)
        pin?.warnings?.forEach { Text(it,color=MaterialTheme.colorScheme.error,fontSize=10.sp) }
        state.result.digitalStates[ref]?.let { Text("Logic: ${it.name}",color=Mint,fontSize=11.sp) }
        val modeSpec=ComponentRegistry.definitions.getValue(part.kind).parameters.firstOrNull { it.key=="gpio_$label" }
        if(modeSpec!=null) PropertyEditor(part,modeSpec,model)
        if(state.circuit.wires.any { it.start==ref || it.end==ref }) TextButton(onClick={model.disconnectPin(ref)}) { Text("Disconnect this pin") }
    }
}

@Composable
private fun ConnectionPicker(start:TerminalRef,state:SimulatorState,model:SimulatorViewModel,onDismiss:()->Unit) {
    var query by remember(start) { mutableStateOf("") }
    val from=state.circuit.components.firstOrNull { it.id==start.componentId } ?: return
    val candidates=state.circuit.components.flatMap { part ->
        (0 until part.terminalCount).filter { ComponentPins.canConnect(part,it) }.map { part to it }
    }.filter { (part,index) ->
        val target=TerminalRef(part.id,index)
        target!=start && ComponentPins.connectionError(state.circuit,start,target)==null &&
            (query.isBlank() || "${part.reference} ${part.kind.title} ${ComponentPins.name(part,index)}".contains(query,true) ||
                part.kind.isBoard && index in BoardRegistry.boards.getValue(part.kind).findPins(query))
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Connect ${from.reference} · ${ComponentPins.name(from,start.index)}")},
        text={Column {
            Text("Choose a destination pin. Nothing is connected automatically.",fontSize=12.sp)
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("connection-search"),
                label={Text("Find component or pin")},singleLine=true)
            LazyColumn(Modifier.heightIn(max=300.dp)) {
                if(candidates.isEmpty()) item { Text("No matching connectable pins",Modifier.padding(12.dp)) }
                items(candidates,key={ (part,index) -> "${part.id}:$index" }) { (part,index) ->
                    TextButton(onClick={model.connect(start,TerminalRef(part.id,index));onDismiss()},
                        modifier=Modifier.fillMaxWidth().testTag("target-${part.reference}-$index")) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${part.reference} · ${ComponentPins.name(part,index).ifEmpty { "Junction" }}",fontWeight=FontWeight.SemiBold)
                            Text(part.kind.title+" · "+ComponentPins.role(part,index),fontSize=10.sp)
                        }
                    }
                }
            }
        }},confirmButton={TextButton(onClick={model.beginConnection(start);onDismiss()}) { Text("Pick on canvas") }},
        dismissButton={TextButton(onClick=onDismiss) { Text("Cancel") }})
}
