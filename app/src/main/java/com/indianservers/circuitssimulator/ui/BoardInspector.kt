package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.indianservers.circuitssimulator.firmware.BoardSupport
import com.indianservers.circuitssimulator.ui.canvas.*

@Composable
internal fun BoardInspector(part:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val board=BoardRegistry.boards.getValue(part.kind)
    var selected by remember(part.id) { mutableIntStateOf(0) }
    var tab by remember(part.id) { mutableIntStateOf(0) }
    var query by remember(part.id) { mutableStateOf("") }
    var filter by remember(part.id) { mutableStateOf<PinCapability?>(null) }
    val pin=board.pins.getOrElse(selected) { board.pins.first() }
    val volts=state.result.nodeVoltages[TerminalRef(part.id,selected)]
    val reading=state.firmware.pins[pin.name]
    val visible=board.pins.mapIndexed { i,item -> i to item }.filter { (_,item) ->
        (filter==null || filter in item.capabilities) &&
            (query.isBlank() || board.findPins(query).contains(board.pins.indexOf(item)))
    }
    Surface(color=Panel,shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=540.dp).padding(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                ComponentThumbnail(part.kind,Modifier.size(width=78.dp,height=96.dp),part)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(board.product,color=TextIce,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("${board.mcu} · ${board.architecture}",color=Muted,fontSize=12.sp)
                    Text("${board.logicVoltage} V logic · ${board.supportLabel()} · ${BoardSupport.languages(part.kind).joinToString { it.name }}",
                        color=Mint,fontSize=10.sp)
                    val powered=part.value("usbPower")>=.5
                    Text(if(powered) "Powered · USB ${board.usbConnector}" else "Unpowered — runtime will not start",
                        color=if(powered) Mint else Color(0xFFFFA5A5),fontSize=10.sp)
                }
                Text("✕",Modifier.clickable { model.select(null) }.padding(8.dp),color=Muted)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Overview","Pinout","Live","Specs").forEachIndexed { i,label ->
                    FilterChip(selected=tab==i,onClick={tab=i},label={Text(label,fontSize=10.sp)})
                }
            }
            when(tab) {
                0 -> {
                    Text(board.beginnerHint(pin),color=Muted,fontSize=11.sp)
                    Text("${board.displayName(pin.name)}  ·  ${pin.type.name.replace('_',' ')}",
                        color=TextIce,fontSize=14.sp)
                    Text(pin.capabilities.joinToString(" · ") { it.name.replace('_',' ') },color=Mint,fontSize=11.sp)
                    if(pin.notes.isNotBlank()) Text(pin.notes,color=Muted,fontSize=11.sp)
                    pin.warnings.forEach { Text(it,color=Color(0xFFFFD58A),fontSize=10.sp) }
                    Text("Measured ${volts?.let { EngineeringUnits.format(it,"V") } ?: "—"}"+
                        (reading?.let { "  ·  ${it.mode} ${it.level}"+
                            (it.pwmDuty?.let { d -> "  PWM ${(d*100).toInt()}%" } ?: "") } ?: "")+
                        (pin.maxVoltage?.let { "  ·  max $it V" } ?: ""),color=Muted,fontSize=11.sp)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("USB Power",Modifier.weight(1f),color=TextIce,fontSize=12.sp)
                        FilterChip(selected=part.value("usbPower")>=.5,onClick={
                            model.setUsbPower(part.id,part.value("usbPower")<.5) },
                            label={Text(if(part.value("usbPower")>=.5) "On" else "Off")})
                        TextButton(onClick={model.showFirmware(true)}) { Text("Firmware") }
                        TextButton(onClick={model.resetFirmware()}) { Text("Reset MCU") }
                        TextButton(onClick={model.resetBoardConfiguration(part.id)}) { Text("Reset config") }
                    }
                }
                1 -> {
                    OutlinedTextField(query,{query=it;board.findPins(it).firstOrNull()?.let { i ->
                        selected=i;model.highlightFirmwarePin(board.pins[i].name) }},
                        Modifier.fillMaxWidth(),singleLine=true,label={Text("Find pin (21, SDA, A0)")},
                        textStyle=TextStyle11)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        FilterChip(filter==null,{filter=null;model.highlightBoardCapability(part.id,null)},label={Text("All",fontSize=10.sp)})
                        listOf(PinCapability.ANALOG_INPUT to "ADC",PinCapability.PWM to "PWM",
                            PinCapability.I2C to "I²C",PinCapability.UART to "UART",
                            PinCapability.SPI to "SPI",PinCapability.GROUND to "GND").forEach { (cap,label) ->
                            FilterChip(filter==cap,{filter=cap;model.highlightBoardCapability(part.id,cap)},
                                label={Text(label,fontSize=10.sp)})
                        }
                    }
                    Text("Color is a category aid. Labels are the source of truth.",color=Muted,fontSize=10.sp)
                }
                2 -> {
                    val used=state.firmware.pins.filter { it.value.mode.name!="INPUT" || it.value.pwmDuty!=null }
                    Text(if(used.isEmpty()) "No active firmware drives." else
                        "${used.size} pins active · ${board.logicVoltage} V",color=Mint,fontSize=11.sp)
                }
                else -> {
                    CapabilityRow("GPIO",board.capabilities.gpio)
                    CapabilityRow("ADC ${board.adcBits}-bit",board.capabilities.adc)
                    CapabilityRow("PWM",board.capabilities.pwm)
                    CapabilityRow("UART",board.capabilities.uart)
                    CapabilityRow("I²C",board.capabilities.i2c)
                    CapabilityRow("SPI",board.capabilities.spi)
                    CapabilityRow("Wi-Fi",board.capabilities.wifi)
                    CapabilityRow("Bluetooth",board.capabilities.bluetooth)
                    CapabilityRow("DAC",board.capabilities.dac)
                    board.references.forEach { Text("${it.title} — ${it.publisher}",color=Muted,fontSize=10.sp) }
                    Text(board.notes,color=Muted,fontSize=10.sp)
                    var compareTo by remember(part.id) { mutableStateOf(Kind.ESP32_DEVKIT) }
                    Text("Replace Board — compatibility only, no auto-migrate",color=Mint,fontSize=11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        BoardRegistry.boards.keys.filter { it!=part.kind }.take(6).forEach { kind ->
                            FilterChip(compareTo==kind,{compareTo=kind},label={Text(kind.title.take(16),fontSize=9.sp)})
                        }
                    }
                    val swap=BoardReplacement.summarize(part.kind,compareTo,state.firmware.source)
                    swap.voltageChange?.let { Text(it,color=Color(0xFFFFD58A),fontSize=10.sp) }
                    swap.codeIssues.take(4).forEach { Text(it,color=Color(0xFFFFA5A5),fontSize=10.sp) }
                    Text("Missing on target: ${swap.missingPins.take(8).joinToString().ifBlank { "none of the used GPIO names" }}",
                        color=Muted,fontSize=10.sp)
                }
            }
            HorizontalDivider(color=Color(0xFF294159),modifier=Modifier.padding(vertical=6.dp))
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(visible,key={ _,item -> item.first }) { _,(i,item) ->
                    val active=i==selected
                    Row(Modifier.fillMaxWidth().clickable {
                        selected=i;model.highlightFirmwarePin(item.name)
                    }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("${item.physicalNumber ?: i+1}",Modifier.width(26.dp),color=Muted,fontSize=10.sp)
                        Text(board.displayName(item.name),Modifier.width(108.dp),
                            color=if(active) Mint else TextIce,fontSize=11.sp,
                            fontWeight=if(active) FontWeight.Bold else FontWeight.Normal)
                        Text(item.capabilities.joinToString(" · ") { it.name.replace('_',' ').lowercase() },
                            Modifier.weight(1f),color=Muted,fontSize=10.sp,maxLines=1)
                    }
                }
            }
            Text("Remove board",Modifier.align(Alignment.End).clickable { model.remove(part.id) }
                .padding(6.dp),color=Color(0xFFFF7777),fontSize=12.sp)
        }
    }
}

private val TextStyle11=androidx.compose.ui.text.TextStyle(fontSize=11.sp)

@Composable
private fun CapabilityRow(label:String,feature:FeatureSupport) {
    Text("$label  ·  hardware ${if(feature.hardware) "yes" else "no"}  ·  simulator ${featureLabel(feature.simulator)}"+
        (if(feature.notes.isNotBlank()) "  — ${feature.notes}" else ""),
        color=TextIce,fontSize=11.sp)
}
