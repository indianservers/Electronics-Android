package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp
import com.indianservers.circuitssimulator.simulation.threeTerminalCurrents
import com.indianservers.circuitssimulator.simulation.sensorResistanceOhms

@Composable
fun ComponentDetails(p: PlacedComponent,state: SimulatorState,model: SimulatorViewModel) {
    if(p.kind==Kind.POTENTIOMETER) {
        PotentiometerDetails(p,state,model)
        return
    }
    if(p.kind==Kind.OPAMP || p.kind==Kind.IDEAL_OPAMP) {
        OpAmpDetails(p,state,model)
        return
    }
    if(p.terminalCount==3) {
        ThreeTerminalDetails(p,state,model)
        return
    }
    if(p.kind==Kind.FUNCTION_GENERATOR) {
        GeneratorDetails(p,state,model)
        return
    }
    if(p.kind==Kind.CAPACITOR || p.kind==Kind.ELECTROLYTIC) {
        CapacitorDetails(p,state,model)
        return
    }
    val definition=ComponentRegistry.definitions.getValue(p.kind)
    val reading=state.result.readings[p.id]
    var advanced by remember(p.id) { mutableStateOf(false) }
    var thermalPlot by remember(p.id) { mutableStateOf(false) }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=if(p.kind==Kind.LDR || p.kind==Kind.THERMISTOR) 360.dp else 260.dp)
            .verticalScroll(rememberScrollState()).padding(16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp).background(Muted,RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Component Details",Modifier.weight(1f),color=TextIce,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("✕",Modifier.clickable { model.select(null) }.padding(8.dp),color=Muted)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
                    ComponentThumbnail(p.kind,Modifier.size(82.dp),p)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.reference,color=TextIce,fontSize=18.sp,fontWeight=FontWeight.Medium)
                    Text(p.kind.title,color=TextIce,fontSize=14.sp)
                    Text(definition.description,color=Muted,fontSize=11.sp,maxLines=2)
                }
                Text("Remove",Modifier.clickable { model.remove(p.id) }.padding(8.dp),color=Color(0xFFFF666E),fontSize=13.sp)
            }
            Spacer(Modifier.height(8.dp))
            definition.parameters.take(1).forEach { param ->
                var text by remember(p.id,param.key,p.parameters[param.key]) { mutableStateOf(EngineeringUnits.format(p.value(param.key),param.unit)) }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(param.label,Modifier.weight(1f),color=TextIce,fontSize=13.sp)
                    OutlinedTextField(value=text,onValueChange={ text=it },modifier=Modifier.width(145.dp).height(54.dp),
                        singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(fontSize=14.sp,color=TextIce))
                    Text("✓",Modifier.clickable { EngineeringUnits.parse(text)?.let { model.setParameter(p.id,param.key,it) } }
                        .padding(8.dp),color=Mint,fontSize=18.sp)
                }
                if (p.kind in setOf(Kind.RESISTOR,Kind.BATTERY,Kind.SOURCE,Kind.LAMP,Kind.LDR,Kind.THERMISTOR)) {
                    val logarithmic = p.kind == Kind.RESISTOR || p.kind == Kind.LAMP
                    val logLux = p.kind==Kind.LDR
                    fun fraction(value: Double): Float = if (logarithmic)
                        ((ln(value.coerceAtLeast(param.min)) - ln(param.min)) / (ln(param.max) - ln(param.min))).toFloat()
                    else if(logLux) (ln(value.coerceAtLeast(0.0)+1.0)/ln(param.max+1.0)).toFloat()
                    else ((value-param.min)/(param.max-param.min)).toFloat()
                    fun physical(value: Float): Double = if (logarithmic)
                        exp(ln(param.min) + value * (ln(param.max)-ln(param.min)))
                    else if(logLux) exp(value*ln(param.max+1.0))-1.0
                    else param.min + value * (param.max-param.min)
                    var slider by remember(p.id,param.key,p.parameters[param.key]) { mutableFloatStateOf(fraction(p.value(param.key))) }
                    Slider(slider,onValueChange={ slider=it },onValueChangeFinished={ model.setParameter(p.id,param.key,physical(slider)) },
                        modifier=Modifier.fillMaxWidth())
                }
            }
            if(p.kind==Kind.LDR || p.kind==Kind.THERMISTOR)
                Text("Electrical resistance  ${EngineeringUnits.format(sensorResistanceOhms(p),"Ω")}",
                    color=Mint,fontSize=12.sp)
            if (p.kind==Kind.SWITCH) Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Contact",Modifier.weight(1f),color=TextIce)
                Switch(checked=p.closed,onCheckedChange={ model.toggleSwitch(p.id) })
                Text(if(p.closed) "Closed" else "Open",color=Muted,fontSize=12.sp)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("V  ${reading?.let { EngineeringUnits.format(it.voltage,"V") } ?: "—"}",color=Mint,fontSize=12.sp)
                Text("I  ${reading?.let { EngineeringUnits.format(abs(it.current),"A") } ?: "—"}",color=Mint,fontSize=12.sp)
                Text("P  ${reading?.let { EngineeringUnits.format(it.power,"W") } ?: "—"}",color=Mint,fontSize=12.sp)
            }
            if(reading?.health != null && reading.health != com.indianservers.circuitssimulator.simulation.Health.NORMAL)
                Text(if(reading.health==com.indianservers.circuitssimulator.simulation.Health.FAILED_OPEN)
                    "${p.reference} opened after accumulated overload." else
                    "${reading.health}: exceeds configured rating",color=Color(0xFFFFB25E),fontSize=12.sp)
            reading?.estimatedTemperatureC?.let { temperature ->
                Text("Estimated junction: ${"%.1f".format(java.util.Locale.US,temperature)} °C",
                    color=Muted,fontSize=11.sp)
            }
            state.transient?.traces?.get(p.id)?.takeIf { it.quantity=="Temperature" }?.let { trace ->
                Text(if(thermalPlot) "Hide temperature trace" else "Temperature vs time  ▾",
                    Modifier.clickable { thermalPlot=!thermalPlot }.padding(vertical=4.dp),color=Blue,fontSize=11.sp)
                if(thermalPlot) SignalChart(trace,state.elapsedSeconds,Modifier.fillMaxWidth().height(110.dp))
            }
            if(definition.parameters.size>1) {
                Text(if(advanced) "Hide advanced" else "More parameters  ▾",Modifier.clickable { advanced=!advanced }.padding(vertical=6.dp),color=Muted,fontSize=12.sp)
                if(advanced) definition.parameters.drop(1).forEach { param ->
                    var value by remember(p.id,param.key,p.parameters[param.key]) { mutableStateOf(EngineeringUnits.format(p.value(param.key),param.unit)) }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(param.label,Modifier.weight(1f),color=TextIce,fontSize=12.sp)
                        OutlinedTextField(value,{ value=it },Modifier.width(135.dp).height(52.dp),singleLine=true)
                        Text("✓",Modifier.clickable { EngineeringUnits.parse(value)?.let { model.setParameter(p.id,param.key,it) } }.padding(8.dp),color=Mint)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                Text("Rotate  ↻",Modifier.clickable { model.rotate(p.id) }.padding(10.dp),color=Blue,fontSize=13.sp)
            }
        }
    }
}

@Composable
private fun PotentiometerDetails(p:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val wiper=state.result.nodeVoltages[TerminalRef(p.id,1)]
    var position by remember(p.id,p.parameters["position"]) { mutableFloatStateOf(p.value("position").toFloat()) }
    var resistanceText by remember(p.id,p.parameters["resistance"]) {
        mutableStateOf(EngineeringUnits.format(p.value("resistance"),"Ω"))
    }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=300.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("${p.reference} · Potentiometer",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                Text("✕",Modifier.clickable { model.select(null) }.padding(8.dp),color=Muted)
            }
            Text("Top · wiper · bottom",color=Muted,fontSize=12.sp)
            Text("Wiper voltage  ${wiper?.let { EngineeringUnits.format(it,"V") } ?: "—"}",
                color=Mint,fontSize=14.sp)
            Text("Wiper position  ${(position*100).toInt()}%",color=TextIce,fontSize=13.sp)
            Slider(position,onValueChange={ position=it;model.previewPotentiometerPosition(p.id,it.toDouble()) },
                onValueChangeFinished=model::finishParameterPreview)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("End-to-end resistance",Modifier.weight(1f),color=TextIce,fontSize=12.sp)
                OutlinedTextField(resistanceText,{resistanceText=it},Modifier.width(125.dp).height(52.dp),singleLine=true)
                Text("✓",Modifier.clickable { EngineeringUnits.parse(resistanceText)?.let {
                    model.setParameter(p.id,"resistance",it)
                } }.padding(8.dp),color=Mint)
            }
            Text("Model: ideal resistive wiper",color=Muted,fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                Text("Rotate  ↻",Modifier.clickable { model.rotate(p.id) }.padding(8.dp),color=Blue)
                Text("Remove",Modifier.clickable { model.remove(p.id) }.padding(8.dp),color=Color(0xFFFF666E))
            }
        }
    }
}

@Composable
private fun OpAmpDetails(p:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val definition=ComponentRegistry.definitions.getValue(p.kind)
    val output=state.result.nodeVoltages[TerminalRef(p.id,2)]
    val plus=state.result.nodeVoltages[TerminalRef(p.id,0)]
    val minus=state.result.nodeVoltages[TerminalRef(p.id,1)]
    var advanced by remember(p.id) { mutableStateOf(false) }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=300.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("${p.reference} · ${p.kind.title}",Modifier.weight(1f),color=TextIce,fontSize=16.sp)
                Text("✕",Modifier.clickable { model.select(null) }.padding(7.dp),color=Muted)
            }
            Text("Pins: + input · − input · output",color=Muted,fontSize=11.sp)
            Text("V+ ${plus?.let { EngineeringUnits.format(it,"V") } ?: "—"}   "+
                "V− ${minus?.let { EngineeringUnits.format(it,"V") } ?: "—"}   "+
                "Vout ${output?.let { EngineeringUnits.format(it,"V") } ?: "—"}",color=Mint,fontSize=12.sp)
            definition.parameters.filter { it.key=="lowerRail" || it.key=="upperRail" }.forEach { spec ->
                var value by remember(p.id,spec.key,p.parameters[spec.key]) {
                    mutableStateOf(EngineeringUnits.format(p.value(spec.key),spec.unit))
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(spec.label,Modifier.weight(1f),color=TextIce,fontSize=11.sp)
                    OutlinedTextField(value,{value=it},Modifier.width(120.dp).height(48.dp),singleLine=true)
                    Text("✓",Modifier.clickable { EngineeringUnits.parse(value)?.let { model.setParameter(p.id,spec.key,it) } }
                        .padding(5.dp),color=Mint)
                }
            }
            Text(if(advanced) "Hide Advanced" else "Advanced  ▾",Modifier.clickable { advanced=!advanced }
                .padding(vertical=6.dp),color=Blue,fontSize=12.sp)
            if(advanced) definition.parameters.filterNot { it.key=="lowerRail" || it.key=="upperRail" }.forEach { spec ->
                var value by remember(p.id,spec.key,p.parameters[spec.key]) {
                    mutableStateOf(EngineeringUnits.format(p.value(spec.key),spec.unit))
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(spec.label,Modifier.weight(1f),color=TextIce,fontSize=11.sp)
                    OutlinedTextField(value,{value=it},Modifier.width(120.dp).height(48.dp),singleLine=true)
                    Text("✓",Modifier.clickable { EngineeringUnits.parse(value)?.let { model.setParameter(p.id,spec.key,it) } }
                        .padding(5.dp),color=Mint)
                }
            }
            Text("Model: ${definition.modelAccuracy.name.replace('_',' ')}",color=Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun ThreeTerminalDetails(p:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val definition=ComponentRegistry.definitions.getValue(p.kind)
    val reading=state.result.readings[p.id]
    val voltages=DoubleArray(3) { state.result.nodeVoltages[TerminalRef(p.id,it)] ?: 0.0 }
    val currents=threeTerminalCurrents(p,voltages)
    val bjt=p.kind==Kind.NPN_BJT || p.kind==Kind.PNP_BJT
    var more by remember(p.id) { mutableStateOf(false) }
    var partInfo by remember(p.id) { mutableStateOf(false) }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .background(Muted,RoundedCornerShape(4.dp)))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Component Details",Modifier.weight(1f),color=TextIce,fontSize=16.sp)
                Text("Part Info",Modifier.clickable { partInfo=true }.padding(7.dp),color=Blue,fontSize=11.sp)
                Text("✕",Modifier.clickable { model.select(null) }.padding(7.dp),color=Muted)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Surface(color=Panel,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
                    ComponentThumbnail(p.kind,Modifier.size(76.dp),p)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${p.reference}  ${p.kind.title}",color=TextIce,fontSize=15.sp)
                    Text(if(bjt) "Electrical pins: C  B  E" else "Electrical pins: D  G  S",color=Muted,fontSize=11.sp)
                }
                Text("Remove",Modifier.clickable { model.remove(p.id) }.padding(6.dp),
                    color=Color(0xFFFF666E),fontSize=11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(bjt) "VBE ${EngineeringUnits.format(voltages[1]-voltages[2],"V")}" else
                    "VGS ${EngineeringUnits.format(voltages[1]-voltages[2],"V")}",color=Mint,fontSize=11.sp)
                Text(if(bjt) "VCE ${EngineeringUnits.format(voltages[0]-voltages[2],"V")}" else
                    "VDS ${EngineeringUnits.format(voltages[0]-voltages[2],"V")}",color=Mint,fontSize=11.sp)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(bjt) "IB ${EngineeringUnits.format(currents[1],"A")}" else
                    "IG ${EngineeringUnits.format(currents[1],"A")}",color=Mint,fontSize=11.sp)
                Text(if(bjt) "IC ${EngineeringUnits.format(currents[0],"A")}" else
                    "ID ${EngineeringUnits.format(currents[0],"A")}",color=Mint,fontSize=11.sp)
                Text("P ${EngineeringUnits.format(reading?.power ?: 0.0,"W")}",color=Mint,fontSize=11.sp)
            }
            reading?.estimatedTemperatureC?.let {
                Text("Estimated junction ${"%.1f".format(java.util.Locale.US,it)} °C",color=Muted,fontSize=11.sp)
            }
            Text(if(more) "Hide model parameters" else "More model parameters  ▾",
                Modifier.clickable { more=!more }.padding(vertical=6.dp),color=Blue,fontSize=11.sp)
            if(more) definition.parameters.forEach { spec ->
                var input by remember(p.id,spec.key,p.parameters[spec.key]) {
                    mutableStateOf(EngineeringUnits.format(p.value(spec.key),spec.unit))
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(spec.label,Modifier.weight(1f),color=TextIce,fontSize=11.sp)
                    OutlinedTextField(input,{input=it},Modifier.width(120.dp).height(48.dp),singleLine=true)
                    Text("✓",Modifier.clickable { EngineeringUnits.parse(input)?.let { model.setParameter(p.id,spec.key,it) } }
                        .padding(5.dp),color=Mint)
                }
            }
        }
    }
    if(partInfo) AlertDialog(onDismissRequest={partInfo=false},title={Text(definition.datasheet.partNumber ?: p.kind.title)},
        text={Text("${definition.datasheet.manufacturer ?: "Generic model"}\n${definition.datasheet.packageName ?: ""}\n"+
            "Physical pins: ${definition.datasheet.physicalPinOrder.joinToString("  ")}\n"+
            "Model accuracy: ${definition.modelAccuracy.name.replace('_',' ')}\n"+
            "${definition.datasheet.sourceNotes ?: "Educational model"}\n"+
            "Source: ${definition.datasheet.referenceUrl ?: "None"}")},
        confirmButton={TextButton(onClick={partInfo=false}) { Text("Close") }})
}

@Composable
private fun GeneratorDetails(p:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val names=listOf("Sine","Square","Triangle","Saw","Pulse")
    val waveform=p.value("waveform").toInt().coerceIn(0,4)
    val reading=state.result.readings[p.id]
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState())
            .padding(horizontal=14.dp,vertical=9.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .background(Muted,RoundedCornerShape(4.dp)))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Function Generator  ${p.reference}",Modifier.weight(1f),color=TextIce,fontSize=16.sp)
                Text("Remove",Modifier.clickable { model.remove(p.id) }.padding(7.dp),
                    color=Color(0xFFFF666E),fontSize=12.sp)
                Text("✕",Modifier.clickable { model.select(null) }.padding(7.dp),color=Muted)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                names.forEachIndexed { index,name ->
                    FilterChip(selected=waveform==index,onClick={model.setParameter(p.id,"waveform",index.toDouble())},
                        label={Text(name,fontSize=10.sp)})
                }
            }
            SignalChart(state.transient?.traces?.get(p.id),state.elapsedSeconds,
                Modifier.fillMaxWidth().height(95.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("V ${reading?.let { EngineeringUnits.format(it.voltage,"V") } ?: "—"}",color=Mint,fontSize=11.sp)
                Text("I ${reading?.let { EngineeringUnits.format(abs(it.current),"A") } ?: "—"}",color=Mint,fontSize=11.sp)
            }
            listOf("frequency","amplitude","offset","duty","phase","rise","fall")
                .filter { it !in listOf("rise","fall") || waveform==4 }
                .forEach { key ->
                    val spec=ComponentRegistry.definitions.getValue(p.kind).parameters.first { it.key==key }
                    var input by remember(p.id,key,p.parameters[key]) {
                        mutableStateOf(if(key=="duty")
                            "%.0f%%".format(java.util.Locale.US,p.value(key)*100.0)
                            else EngineeringUnits.format(p.value(key),spec.unit))
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(if(key=="duty") "Duty cycle" else spec.label,
                            Modifier.weight(1f),color=TextIce,fontSize=12.sp)
                        OutlinedTextField(input,{input=it},Modifier.width(140.dp).height(50.dp),singleLine=true)
                        Text("✓",Modifier.clickable {
                            val parsed=if(key=="duty") input.trim().removeSuffix("%")
                                .trim().toDoubleOrNull()?.div(100.0) else EngineeringUnits.parse(input)
                            parsed?.let { model.setParameter(p.id,key,it) }
                        }
                            .padding(7.dp),color=Mint)
                    }
                }
        }
    }
}

@Composable
private fun CapacitorDetails(p: PlacedComponent,state: SimulatorState,model: SimulatorViewModel) {
    val reading=state.result.readings[p.id]
    val choices=listOf("pF" to 1e-12,"nF" to 1e-9,"µF" to 1e-6,"mF" to 1e-3,"F" to 1.0)
    var chosen by remember(p.id) { mutableStateOf(choices[2]) }
    var numeric by remember(p.id,p.value("capacitance"),chosen) {
        mutableStateOf("%.3g".format(java.util.Locale.US,p.value("capacitance")/chosen.second))
    }
    var unitsOpen by remember { mutableStateOf(false) }
    val spec=ComponentRegistry.definitions.getValue(p.kind).parameters.first()
    fun commit() {
        val containsPrefix=numeric.any { it.isLetter() || it=='µ' }
        val parsed=if(containsPrefix) EngineeringUnits.parse(numeric) else numeric.toDoubleOrNull()?.times(chosen.second)
        parsed?.let { model.setParameter(p.id,"capacitance",it) }
    }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().height(250.dp).padding(horizontal=12.dp,vertical=9.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp).background(Muted,RoundedCornerShape(4.dp)))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Component Details",Modifier.weight(1f),color=TextIce,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                if(reading?.health!=null && reading.health!=com.indianservers.circuitssimulator.simulation.Health.NORMAL)
                    Text("Rating warning",color=Color(0xFFFFB25E),fontSize=10.sp)
                Text("Remove",Modifier.clickable { model.remove(p.id) }.padding(7.dp),color=Color(0xFFFF666E),fontSize=12.sp)
                Text("✕",Modifier.clickable { model.select(null) }.padding(7.dp),color=Muted)
            }
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(.47f)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(color=Panel,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
                            ComponentThumbnail(p.kind,Modifier.size(62.dp),p)
                        }
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(p.reference,color=TextIce,fontSize=15.sp)
                            Text(p.kind.title,color=TextIce,fontSize=12.sp)
                            Text("Stores charge",color=Muted,fontSize=10.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Capacitance",color=TextIce,fontSize=12.sp)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(numeric,{ numeric=it },Modifier.weight(1f).height(50.dp),singleLine=true,
                            textStyle=androidx.compose.ui.text.TextStyle(color=TextIce,fontSize=14.sp))
                        Box {
                            Text(chosen.first+" ▾",Modifier.clickable { unitsOpen=true }.padding(6.dp),color=TextIce,fontSize=11.sp)
                            DropdownMenu(unitsOpen,onDismissRequest={ unitsOpen=false }) {
                                choices.forEach { unit -> DropdownMenuItem(text={Text(unit.first)},onClick={chosen=unit;unitsOpen=false}) }
                            }
                        }
                        Text("✓",Modifier.clickable { commit() }.padding(4.dp),color=Mint)
                    }
                    val min=ln(spec.min);val max=ln(spec.max)
                    var slider by remember(p.id,p.value("capacitance")) {
                        mutableFloatStateOf(((ln(p.value("capacitance").coerceAtLeast(spec.min))-min)/(max-min)).toFloat())
                    }
                    Slider(slider,onValueChange={slider=it},onValueChangeFinished={model.setParameter(p.id,"capacitance",exp(min+slider*(max-min)))})
                }
                Column(Modifier.weight(.53f)) {
                    SignalChart(state.transient?.traces?.get(p.id),state.elapsedSeconds,Modifier.fillMaxWidth().height(125.dp))
                    Spacer(Modifier.height(5.dp))
                    Text("V ${reading?.let { EngineeringUnits.format(it.voltage,"V") } ?: "—"}   I ${reading?.let { EngineeringUnits.format(abs(it.current),"A") } ?: "—"}",
                        color=Mint,fontSize=10.sp,maxLines=1)
                    Text("P ${reading?.let { EngineeringUnits.format(it.power,"W") } ?: "—"}   t ${"%.0f".format(java.util.Locale.US,state.elapsedSeconds*1000)} ms",
                        color=Mint,fontSize=10.sp,maxLines=1)
                }
            }
        }
    }
}
