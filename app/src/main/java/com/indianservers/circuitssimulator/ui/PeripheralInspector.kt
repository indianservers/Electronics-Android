package com.indianservers.circuitssimulator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.EmbeddedProfiles
import com.indianservers.circuitssimulator.ui.canvas.*

@Composable
internal fun PeripheralInspector(part:PlacedComponent,state:SimulatorState,model:SimulatorViewModel) {
    val profile=EmbeddedProfiles.profiles[part.kind]
    var open by remember(part.id) { mutableStateOf(false) }
    var text by remember(part.id) { mutableStateOf("") }
    var address by remember(part.id) { mutableStateOf("00") }
    var byte by remember(part.id) { mutableStateOf("FF") }
    var hexImport by remember(part.id) { mutableStateOf("") }
    var hexMode by remember(part.id) { mutableStateOf(false) }
    var paused by remember(part.id) { mutableStateOf(false) }
    var frozen by remember(part.id) { mutableStateOf("") }
    var ending by remember(part.id) { mutableStateOf("LF") }
    var timestamps by remember(part.id) { mutableStateOf(false) }
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    if(!state.circuit.settings.advancedEmbedded) TextButton(onClick={model.guidePeripheralWiring(part.id)}) { Text("Wiring guide") }
    TextButton(onClick={open=!open}) { Text(if(open) "Hide device state" else "Device state / reference") }
    if(open) Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        profile?.let {
            Text(it.family,color=TextIce)
            Text("Supply ${it.electrical.supplyRange} V · ${it.interfaces.joinToString()}",color=Muted)
            Text(it.limitations.joinToString("\n"),color=Muted)
            TextButton(onClick={it.datasheet.referenceUrl?.let { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
            }}) { Text("Manufacturer reference") }
        }
        if(part.kind in setOf(Kind.I2C_TEMP_SENSOR,Kind.I2C_EEPROM,Kind.I2C_LCD,Kind.OLED_SSD1306))
            TextButton(onClick=model::scanI2cBus) { Text("Scan connected I²C bus") }
        val readings=state.firmware.peripheralInspectors[part.id].orEmpty()
        if(readings.isEmpty()) Text("Run firmware to inspect live device state.",color=Muted)
        readings.forEach { (name,value) -> Text("$name: $value",color=Mint,fontFamily=FontFamily.Monospace) }
        if(part.kind in setOf(Kind.I2C_EEPROM,Kind.SPI_MEMORY)) {
            Row {
                OutlinedTextField(address,{address=it},label={Text("HEX address")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(byte,{byte=it},label={Text("HEX byte")},singleLine=true,modifier=Modifier.weight(1f))
                TextButton(onClick={address.toIntOrNull(16)?.let { a -> byte.toIntOrNull(16)?.let { b -> model.editPeripheralMemory(part.id,a,b) } }}) { Text("Write") }
            }
            val start=address.toIntOrNull(16)?.coerceAtLeast(0) ?: 0
            Text(model.inspectMemory(part.id,start).chunked(16).mapIndexed { i,row ->
                "${(start+i*16).toString(16).padStart(6,'0')}  "+row.joinToString(" ") { it.toString(16).padStart(2,'0') }+
                    "  "+row.map { if(it in 32..126)it.toChar() else '.' }.joinToString("")
            }.joinToString("\n"),color=TextIce,fontFamily=FontFamily.Monospace)
            TextButton(onClick={clipboard.setText(AnnotatedString(model.inspectMemory(part.id,start).joinToString(" ") { it.toString(16).padStart(2,'0') }))}) { Text("Copy 64 bytes as HEX") }
            OutlinedTextField(hexImport,{hexImport=it},label={Text("Import HEX bytes at address")},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={
                val bytes=hexImport.trim().split(Regex("\\s+")).map { it.toIntOrNull(16) }
                if(bytes.size<=256 && bytes.all { it!=null && it in 0..255 }) bytes.forEachIndexed { i,b -> model.editPeripheralMemory(part.id,start+i,b!!) }
                else model.postMessage("Enter up to 256 valid HEX bytes separated by spaces.")
            }) { Text("Import into device memory") }
        }
    }
    if(part.kind==Kind.SERIAL_TERMINAL) {
        val live=state.firmware.displays[part.id].orEmpty().takeLast(2048)
        val received=if(paused)frozen else live
        Text(if(hexMode) received.map { it.code.toString(16).padStart(2,'0') }.joinToString(" ") else received,color=Mint,fontFamily=FontFamily.Monospace)
        Row {
            FilterChip(hexMode,{hexMode=!hexMode},label={Text("HEX")})
            FilterChip(paused,{frozen=live;paused=!paused},label={Text("Pause")})
            FilterChip(timestamps,{timestamps=!timestamps},label={Text("Times")})
        }
        if(timestamps)state.firmware.busLog.filter { it.contains(part.id.take(8)) }.takeLast(12).forEach { Text(it,color=Muted,fontFamily=FontFamily.Monospace) }
        Row { listOf("None","LF","CRLF").forEach { option -> FilterChip(ending==option,{ending=option},label={Text(option)}) } }
        OutlinedTextField(text,{text=it},label={Text(if(hexMode)"Send HEX bytes" else "Send over wired TX")},modifier=Modifier.fillMaxWidth())
        Row {
            TextButton(onClick={
                val payload=if(hexMode) {
                    val values=text.trim().split(Regex("\\s+")).map { it.toIntOrNull(16) }
                    if(values.any { it==null || it !in 0..255 }) { model.postMessage("Enter valid HEX bytes separated by spaces.");null }
                    else values.map { it!!.toChar() }.joinToString("")
                } else text
                if(payload!=null){model.sendTerminal(part.id,payload+when(ending){"LF"->"\n";"CRLF"->"\r\n";else->""});text=""}
            }) { Text("Send") }
            TextButton(onClick={model.clearTerminal(part.id);frozen=""}) { Text("Clear") }
            TextButton(onClick={clipboard.setText(AnnotatedString(received))}) { Text("Copy log") }
        }
    }
}
