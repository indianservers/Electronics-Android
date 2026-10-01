package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ParameterAnalysisSheet(state: SimulatorState, model: SimulatorViewModel) {
    val circuit = state.circuit
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts = circuit.components.filter { ComponentRegistry.definitions.getValue(it.kind).parameters.isNotEmpty() }
    val terminals = circuit.components.filter { it.kind != Kind.GROUND && it.kind != Kind.JUNCTION }
        .flatMap { part -> (0 until part.terminalCount).map { TerminalRef(part.id, it) to "${part.reference}:${it}" } }
    var partId by remember(circuit) { mutableStateOf(parts.firstOrNull { it.kind == Kind.RESISTOR }?.id ?: parts.firstOrNull()?.id) }
    val part = parts.firstOrNull { it.id == partId }
    val parameters = part?.let { ComponentRegistry.definitions.getValue(it.kind).parameters }.orEmpty()
    var key by remember(partId) { mutableStateOf(parameters.firstOrNull()?.key) }
    val parameter = parameters.firstOrNull { it.key == key }
    var probe by remember(circuit) {
        mutableStateOf(circuit.components.filter { it.kind != Kind.GROUND && it.kind != Kind.JUNCTION }
            .maxByOrNull { it.x }?.let { TerminalRef(it.id, 0) })
    }
    var start by remember(partId, key) { mutableStateOf(parameter?.let { (it.default * .5).coerceIn(it.min, it.max).toString() }.orEmpty()) }
    var stop by remember(partId, key) { mutableStateOf(parameter?.let { (it.default * 1.5).coerceIn(it.min, it.max).toString() }.orEmpty()) }
    var sweep by remember(circuit) { mutableStateOf<ParameterSweep?>(null) }
    var tolerance by remember(circuit) { mutableStateOf<ToleranceSummary?>(null) }
    var error by remember(circuit) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var csv by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) } }
        }
    }
    Surface(color = Color(0xFF102033), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row {
                Text("Parameter & tolerance analysis", Modifier.weight(1f), color = TextIce, fontSize = 17.sp)
                Text("✕", Modifier.clickable { model.showParameterAnalysis(false) }.padding(4.dp), color = Muted)
            }
            AnalysisPicker("Component", part?.reference ?: "Select", parts.map { it.id to "${it.reference} · ${it.kind.title}" }) {
                partId = it; sweep = null; tolerance = null
            }
            AnalysisPicker("Parameter", parameter?.label ?: "Select", parameters.map { it.key to "${it.label} (${it.unit})" }) {
                key = it; sweep = null; tolerance = null
            }
            AnalysisPicker("Probe", terminals.firstOrNull { it.first == probe }?.second ?: "Select",
                terminals.map { it.first to it.second }) { probe = it; sweep = null; tolerance = null }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("Start ${parameter?.unit.orEmpty()}") }, singleLine = true)
                OutlinedTextField(stop, { stop = it }, Modifier.weight(1f), label = { Text("Stop ${parameter?.unit.orEmpty()}") }, singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val selectedId = partId; val selectedKey = key; val selectedProbe = probe
                    val a = start.toDoubleOrNull(); val b = stop.toDoubleOrNull()
                    if (selectedId == null || selectedKey == null || selectedProbe == null || a == null || b == null) {
                        error = "Select a component, parameter, probe, and numeric range."
                    } else scope.launch {
                        busy = true; error = null
                        val result = withContext(Dispatchers.Default) {
                            ParameterAnalysis().sweep(circuit, selectedId, selectedKey, selectedProbe, a, b)
                        }
                        sweep = result; error = result.error; busy = false
                    }
                }, enabled = !busy) { Text("Sweep · 41 points") }
                OutlinedButton(onClick = {
                    val selectedProbe = probe
                    if (selectedProbe == null) error = "Choose a voltage probe."
                    else scope.launch {
                        busy = true; error = null
                        val result = withContext(Dispatchers.Default) {
                            ParameterAnalysis().tolerance(circuit, selectedProbe, seed = 1L)
                        }
                        tolerance = result.first; error = result.second; busy = false
                    }
                }, enabled = !busy) { Text("Tolerance") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = Color(0xFFFFB25E), fontSize = 12.sp) }
            sweep?.takeIf { it.points.isNotEmpty() }?.let { result ->
                Text("Probe voltage · ${result.points.size} points", color = Mint, fontSize = 12.sp)
                SweepPlot(result.points, Modifier.fillMaxWidth().height(130.dp))
                Text("${"%.4g".format(result.points.first().voltage)} V → ${"%.4g".format(result.points.last().voltage)} V",
                    color = TextIce, fontSize = 12.sp)
                TextButton(onClick = { csv = result.toCsv(); export.launch("parameter-sweep.csv") }) { Text("Export sweep CSV") }
            }
            tolerance?.let { summary ->
                Text("Resistor tolerance · ${summary.samples} samples · seed ${summary.seed}", color = Mint, fontSize = 12.sp)
                Text("Nominal ${"%.4g".format(summary.nominal)} V  ·  Mean ${"%.4g".format(summary.mean)} V",
                    color = TextIce, fontSize = 12.sp)
                Text("Observed range ${"%.4g".format(summary.minimum)} to ${"%.4g".format(summary.maximum)} V",
                    color = TextIce, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun <T> AnalysisPicker(label: String, selected: String, items: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text("$label: $selected ▾", Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 6.dp),
            color = TextIce, fontSize = 12.sp)
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (item, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(item); expanded = false }) }
        }
    }
}

@Composable
private fun SweepPlot(points: List<ParameterPoint>, modifier: Modifier) {
    Canvas(modifier.background(Panel, RoundedCornerShape(8.dp))) {
        if (points.size < 2) return@Canvas
        val lo = points.minOf { it.voltage }; val hi = points.maxOf { it.voltage }
        val range = (hi - lo).coerceAtLeast(1e-12)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = 8f + (size.width - 16f) * index / (points.size - 1)
            val y = size.height - 8f - ((point.voltage - lo) / range * (size.height - 16f)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Color(0xFF294159), Offset(8f, size.height / 2f), Offset(size.width - 8f, size.height / 2f), 1f)
        drawPath(path, Mint, style = Stroke(2.5f))
    }
}
