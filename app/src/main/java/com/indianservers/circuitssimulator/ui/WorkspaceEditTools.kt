package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indianservers.circuitssimulator.domain.WirePoint
import com.indianservers.circuitssimulator.simulation.TruthTableAnalyzer
import com.indianservers.circuitssimulator.simulation.CircuitTruthTable
import com.indianservers.circuitssimulator.simulation.digital.LogicState

@Composable
fun WorkspaceEditTools(state:SimulatorState,model:SimulatorViewModel) {
    var blocksOpen by remember { mutableStateOf(false) }
    var blockName by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        FilterChip(state.multiSelect,model::toggleMultiSelect,label={Text("Select group (${state.selection.size})")})
        if(state.multiSelect) {
            TextButton(onClick=model::selectAllParts) { Text("All") }
            TextButton(onClick=model::clearSelection) { Text("Clear") }
        }
        val hasSelection=state.selection.isNotEmpty() || state.selectedId!=null
        TextButton(onClick={model.copySelection()},enabled=hasSelection) { Text("Copy") }
        TextButton(onClick={model.copySelection(true)},enabled=hasSelection) { Text("Cut") }
        TextButton(onClick=model::pasteSelection,enabled=state.clipboardAvailable) { Text("Paste") }
        if(state.selection.isNotEmpty()) {
            TextButton(onClick=model::rotateSelection) { Text("Rotate group") }
            TextButton(onClick={model.moveSelection(-state.gridSpacing,0f)}) { Text("←") }
            TextButton(onClick={model.moveSelection(state.gridSpacing,0f)}) { Text("→") }
            TextButton(onClick={model.moveSelection(0f,-state.gridSpacing)}) { Text("↑") }
            TextButton(onClick={model.moveSelection(0f,state.gridSpacing)}) { Text("↓") }
            TextButton(onClick=model::deleteSelection) { Text("Delete group") }
        }
        FilterChip(state.snapEnabled,{model.setGrid(!state.snapEnabled,state.gridSpacing)},label={Text("Snap")})
        TextButton(onClick={
            val spacings=listOf(10f,16f,32f,64f)
            model.setGrid(state.snapEnabled,spacings[(spacings.indexOf(state.gridSpacing)+1)%spacings.size])
        }) { Text("Grid ${state.gridSpacing.toInt()}") }
        TextButton(onClick={blocksOpen=true}) { Text("Circuit blocks") }
        TruthTableButton(state)
    }
    if(blocksOpen) {
        var blocks by remember { mutableStateOf(model.savedBlocks()) }
        AlertDialog(onDismissRequest={blocksOpen=false},title={Text("Circuit blocks")},text={
            Column {
                Text("Saved selections insert as editable parts with their internal wires.")
                OutlinedTextField(blockName,{blockName=it},label={Text("Block name")},singleLine=true)
                TextButton(onClick={model.saveSelectionBlock(blockName);blocks=model.savedBlocks()},
                    enabled=blockName.isNotBlank() && (state.selection.isNotEmpty() || state.selectedId!=null)) {
                    Text("Save selection")
                }
                Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) {
                    blocks.forEach { (id,name) -> TextButton(onClick={model.insertSavedBlock(id);blocksOpen=false}) { Text("Insert $name") } }
                }
                if(blocks.isEmpty()) Text("No saved blocks yet.")
            }
        },confirmButton={TextButton(onClick={blocksOpen=false}) { Text("Close") }})
    }
}

@Composable
private fun TruthTableButton(state:SimulatorState) {
    var open by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CircuitTruthTable?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    TextButton(onClick={
        runCatching { TruthTableAnalyzer.analyze(state.circuit) }.fold(
            onSuccess={result=it;error=null},onFailure={result=null;error=it.message})
        open=true
    }) { Text("Truth table") }
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("Combinational truth table")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            error?.let { Text(it) }
            result?.let { table ->
                Text((table.inputs+table.outputs).joinToString("  |  "))
                table.rows.forEach { row -> Text((row.inputs+row.outputs).joinToString("     ") { value ->
                    when(value) { LogicState.HIGH -> "1";LogicState.LOW -> "0";LogicState.HIGH_Z -> "Z";LogicState.UNKNOWN -> "X" }
                }) }
                Text("X = unknown/contention; Z = undriven. Delays are excluded.")
            }
        }
    },confirmButton={TextButton(onClick={open=false}) { Text("Close") }})
}

@Composable
fun WireRouteButton(id:String,state:SimulatorState,model:SimulatorViewModel) {
    var open by remember(id) { mutableStateOf(false) }
    var text by remember(id) { mutableStateOf("") }
    var error by remember(id) { mutableStateOf<String?>(null) }
    TextButton(onClick={
        text=state.circuit.wires.firstOrNull { it.id==id }?.waypoints.orEmpty().joinToString("\n") { "${it.x}, ${it.y}" }
        error=null;open=true
    }) { Text("Route") }
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("Wire waypoints")},text={Column {
        Text("Enter one x, y coordinate per line. Empty restores automatic routing.")
        OutlinedTextField(text,{text=it;error=null},label={Text("Workspace coordinates")},maxLines=8)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(onClick={
        val parsed=runCatching {
            val points=text.lineSequence().filter { it.isNotBlank() }.map { line ->
                val values=line.split(',');require(values.size==2)
                WirePoint(values[0].trim().toFloat(),values[1].trim().toFloat())
            }.toList()
            require(points.size<=64 && points.all { it.x.isFinite() && it.y.isFinite() && kotlin.math.abs(it.x)<=1e6 && kotlin.math.abs(it.y)<=1e6 })
            points
        }
        parsed.fold(onSuccess={model.setWireRoute(id,it);open=false},onFailure={error="Use up to 64 lines of finite x, y coordinates."})
    }) { Text("Apply") }},dismissButton={TextButton(onClick={open=false}) { Text("Cancel") }})
}
