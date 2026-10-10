package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.BoardRegistry
import com.indianservers.circuitssimulator.domain.isBoard
import com.indianservers.circuitssimulator.ui.canvas.Mint
import java.util.Locale

private val CodeBlue=Color(0xFF83C7FF)
private val CodeGreen=Color(0xFF7DE0A2)
private val CodeAmber=Color(0xFFFFD58A)
private val TextIce=Color(0xFFE9F2FF)
private val Muted=Color(0xFF9CAFC5)

private object FirmwareColors:VisualTransformation {
    private val pattern=Regex("//[^\\n]*|\\\"(?:\\\\.|[^\\\"])*\\\"|\\b(?:void|if|else|int|long|float|double|bool|String|HIGH|LOW|INPUT|OUTPUT|INPUT_PULLUP)\\b|\\b(?:pinMode|digitalWrite|digitalRead|analogRead|analogWrite|delay|millis|Serial\\.begin|Serial\\.print|Serial\\.println)\\b")
    override fun filter(text:AnnotatedString):TransformedText {
        val colored=buildAnnotatedString {
            append(text.text)
            pattern.findAll(text.text).forEach { match ->
                addStyle(SpanStyle(color=when {
                    match.value.startsWith("//") -> Color(0xFF83A6A6)
                    match.value.startsWith('"') -> CodeGreen
                    match.value.first().isUpperCase() || match.value in setOf("void","if","else","int","long","float","double","bool") -> CodeAmber
                    else -> CodeBlue
                }),match.range.first,match.range.last+1)
            }
        }
        return TransformedText(colored,OffsetMapping.Identity)
    }
}

@Composable
fun FirmwareSheet(state:SimulatorState,model:SimulatorViewModel) {
    val firmware=state.firmware
    val board=state.circuit.components.firstOrNull { it.id==firmware.boardId && it.kind.isBoard }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(firmware.boardId,firmware.running) { if(firmware.running) tab=1 }
    var search by remember { mutableStateOf("") }
    val undo=remember(firmware.boardId) { mutableStateListOf<String>() }
    val redo=remember(firmware.boardId) { mutableStateListOf<String>() }
    fun update(next:String) {
        if(next==firmware.source) return
        undo.add(firmware.source);if(undo.size>80) undo.removeAt(0)
        redo.clear();model.setFirmwareSource(next)
    }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Firmware Lab",Modifier.weight(1f),color=TextIce,fontSize=18.sp)
                Text(if(firmware.language==com.indianservers.circuitssimulator.domain.FirmwareLanguage.MICROPYTHON_SUBSET)
                    "MicroPython subset" else "Arduino subset",color=CodeAmber,fontSize=11.sp)
                TextButton(onClick={model.showFirmware(false)}) { Text("Close") }
            }
            Text("${state.circuit.name} · ${board?.kind?.title ?: "No board"} · executable supported subset · virtual time ${"%.3f".format(Locale.US,firmware.timeMicros/1e6)} s",
                color=Muted,fontSize=11.sp)
            if(board!=null) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    state.circuit.components.filter { it.kind.isBoard }.forEach { placed ->
                        FilterChip(selected=placed.id==firmware.boardId,onClick={model.showFirmware(true,placed.id)},
                            label={Text("${placed.reference} · ${placed.kind.title}",fontSize=11.sp)})
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    com.indianservers.circuitssimulator.firmware.BoardSupport.languages(board.kind).forEach { language ->
                        FilterChip(selected=firmware.language==language,onClick={model.setFirmwareLanguage(language)},
                            label={Text(if(language==com.indianservers.circuitssimulator.domain.FirmwareLanguage.MICROPYTHON_SUBSET)
                                "MicroPython" else "Arduino")})
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf("Code","Output","Pins","Bus").forEachIndexed { index,label ->
                    FilterChip(selected=tab==index,onClick={tab=index},label={Text(label)})
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick={model.compileFirmware()}) { Text("Compile") }
                        TextButton(onClick={if(firmware.running) model.pauseFirmware() else model.runFirmware()}) {
                            Text(if(firmware.running) "Pause" else "Run") }
                        TextButton(onClick={model.stepFirmware()}) { Text("Step 1 ms") }
                        TextButton(onClick={model.resetFirmware()}) { Text("Reset") }
                        TextButton(onClick={model.stopFirmware()}) { Text("Stop") }
                    }

            when(tab) {
                0 -> {
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(enabled=undo.isNotEmpty(),onClick={
                            if(undo.isNotEmpty()) { redo.add(firmware.source);model.setFirmwareSource(undo.removeAt(undo.lastIndex)) }
                        }) { Text("Undo") }
                        TextButton(enabled=redo.isNotEmpty(),onClick={
                            if(redo.isNotEmpty()) { undo.add(firmware.source);model.setFirmwareSource(redo.removeAt(redo.lastIndex)) }
                        }) { Text("Redo") }
                        OutlinedTextField(search,{search=it},Modifier.weight(1f),singleLine=true,
                            label={Text("Find")},textStyle=TextStyle(fontSize=11.sp))
                    }
                    if(search.isNotBlank()) Text("${Regex(Regex.escape(search),RegexOption.IGNORE_CASE).findAll(firmware.source).count()} matches",
                        color=CodeBlue,fontSize=11.sp)
                    Row(Modifier.fillMaxWidth().height(275.dp)) {
                        val lines=firmware.source.count { it=='\n' }+1
                        Text((1..lines).joinToString("\n"),Modifier.width(32.dp).verticalScroll(rememberScrollState())
                            .padding(top=15.dp),color=Muted,fontSize=11.sp,fontFamily=FontFamily.Monospace,lineHeight=17.sp)
                        OutlinedTextField(firmware.source,{ changed ->
                            val next=if(changed.length==firmware.source.length+1 && changed.endsWith("\n")) {
                                val previous=firmware.source.substringAfterLast('\n')
                                changed+previous.takeWhile { it==' ' }+if(previous.trimEnd().endsWith("{")) "    " else ""
                            } else changed
                            update(next)
                        },Modifier.fillMaxSize(),textStyle=TextStyle(fontFamily=FontFamily.Monospace,
                            color=TextIce,fontSize=11.sp,lineHeight=17.sp),visualTransformation=FirmwareColors)
                    }
                    firmware.error?.let { error -> Text("Line ${error.line}: ${error.message}",
                        color=Color(0xFFFFA5A5),fontSize=11.sp) }
                    Text("Current line: ${firmware.currentLine} · ${if(firmware.compiled) "Compiled" else "Not compiled"}",
                        color=Muted,fontSize=11.sp)
                    if(board!=null) {
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            com.indianservers.circuitssimulator.firmware.FirmwareExamples.examplesFor(board.kind).forEach { example ->
                                FilterChip(false,{model.loadFirmwareExample(example.source,example.language)},
                                    label={Text(example.title,fontSize=10.sp)})
                            }
                        }
                    }
                }
                1 -> {
                    var serialIn by remember { mutableStateOf("") }
                    var serialPort by remember(board?.id) { mutableStateOf("Serial") }
                    var baud by remember(board?.id) { mutableIntStateOf(9600) }
                    var ending by remember { mutableStateOf("LF") }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Live output · Serial Monitor",Modifier.weight(1f),color=TextIce)
                        TextButton(onClick={model.setSerialPaused(!firmware.serialPaused)}) {
                            Text(if(firmware.serialPaused) "Resume" else "Pause") }
                        TextButton(onClick=model::clearFirmwareConsole) { Text("Clear") }
                    }
                    Text("Board TX output · monitor sends to selected RX. Baud must match; byte timing is simplified.",
                        color=Muted,fontSize=11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        board?.let { BoardRegistry.boards.getValue(it.kind).hardware.uartPins.indices.forEach { i ->
                            val port=BoardRegistry.boards.getValue(it.kind).hardware.uartNames.getOrNull(i) ?: if(i==0) "Serial" else "Serial$i"
                            FilterChip(serialPort==port,{serialPort=port},label={Text(port,fontSize=10.sp)})
                        } }
                        if(board?.let { BoardRegistry.boards.getValue(it.kind).hardware.usbSerial }==true)
                            FilterChip(serialPort=="Serial",{serialPort="Serial"},label={Text("Serial · USB",fontSize=10.sp)})
                        listOf(9600,115200).forEach { speed -> FilterChip(baud==speed,{baud=speed},label={Text("$speed baud",fontSize=10.sp)}) }
                        listOf("None","LF","CRLF").forEach { e -> FilterChip(ending==e,{ending=e},label={Text(e,fontSize=10.sp)}) }
                    }
                    Row(Modifier.imePadding(),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(serialIn,{serialIn=it},Modifier.weight(1f),singleLine=true,
                            label={Text("TX to MCU RX")},textStyle=TextStyle(fontSize=11.sp))
                        TextButton(onClick={model.enqueueSerial(serialIn+when(ending) { "LF" -> "\n";"CRLF" -> "\r\n";else -> "" },serialPort,baud);serialIn=""}) { Text("Send") }
                    }
                    val serialScroll=rememberLazyListState()
                    LaunchedEffect(firmware.console.size,firmware.serialPaused) {
                        if(!firmware.serialPaused && firmware.console.isNotEmpty())
                            serialScroll.animateScrollToItem(firmware.console.lastIndex)
                    }
                    LazyColumn(Modifier.fillMaxWidth().height(300.dp),state=serialScroll) {
                        if(firmware.console.isEmpty()) item { Text("No serial output yet.",color=Muted) }
                        items(firmware.console.size) { index ->
                            val line=firmware.console[index]
                            Text(line,color=CodeGreen,fontSize=12.sp,fontFamily=FontFamily.Monospace)
                        }
                    }
                    firmware.error?.let { Text("Line ${it.line}: ${it.message}",color=Color(0xFFFFA5A5)) }
                }
                2 -> {
                    Text("Live board pins",color=TextIce)
                    Column(Modifier.fillMaxWidth().height(330.dp).verticalScroll(rememberScrollState())) {
                        val definition=board?.let { BoardRegistry.boards[it.kind] }
                        definition?.pins?.forEach { pin ->
                            val reading=firmware.pins[pin.name]
                            val voltage=board?.let { part ->
                                state.result.nodeVoltages[com.indianservers.circuitssimulator.domain.TerminalRef(
                                    part.id,definition.index(pin.name))] }
                            if(reading!=null || voltage!=null) Text(
                                "${pin.name}  ${reading?.mode ?: "—"}  ${reading?.level ?: "—"}  "+
                                    "${voltage?.let { "%.2f V".format(Locale.US,it) } ?: "—"}"+
                                    (reading?.pwmDuty?.let { "  PWM ${"%.0f".format(Locale.US,it*100)}% @ ${reading.pwmFrequencyHz?.toInt()} Hz" } ?: "")+
                                    (if(reading?.overCurrent==true) "  overcurrent" else ""),
                                Modifier.clickable { model.highlightFirmwarePin(pin.name) },
                                color=if(firmware.highlightedPin==pin.name) Mint else if(reading?.overCurrent==true) Color(0xFFFFA5A5) else HubSoft,
                                fontFamily=FontFamily.Monospace,fontSize=10.sp,maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                        }
                    }
                    if(firmware.variables.isNotEmpty()) Text("Variables: ${firmware.variables}",color=CodeBlue,fontSize=11.sp)
                    firmware.displays.forEach { (id,text) ->
                        val ref=state.circuit.components.firstOrNull { it.id==id }?.reference ?: id.take(8)
                        Text("$ref  \"$text\"",color=CodeGreen,fontSize=11.sp)
                    }
                }
                else -> {
                    Text("Bus events are produced by Wire/SPI/Serial runtime calls.",color=Muted,fontSize=11.sp)
                    Column(Modifier.fillMaxWidth().height(330.dp).verticalScroll(rememberScrollState())) {
                        if(firmware.busLog.isEmpty()) Text("No bus activity yet.",color=Muted)
                        firmware.busLog.forEach { line ->
                            Text(line,color=CodeGreen,fontFamily=FontFamily.Monospace,fontSize=10.sp)
                        }
                    }
                }
            }
            Text("Supported subset only. GPIO/ADC/PWM use the circuit solver. UART/I²C/SPI are event-based educational buses.",
                color=Muted,fontSize=10.sp)
        }
    }
}
