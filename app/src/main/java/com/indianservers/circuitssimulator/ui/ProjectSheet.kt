package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.data.CircuitReport
import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.simulation.DcResult
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ProjectSheet(state:SimulatorState,model:SimulatorViewModel) {
    val context=LocalContext.current
    val ioScope=rememberCoroutineScope()
    var exportText by remember { mutableStateOf("") }
    var reportSnapshot by remember { mutableStateOf<Pair<Circuit,DcResult>?>(null) }
    val exportPng=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val snapshot=reportSnapshot
        if(uri!=null && snapshot!=null) ioScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching {
                val output=ByteArrayOutputStream()
                CircuitReport.writePng(output,snapshot.first,snapshot.second)
                val stream=context.contentResolver.openOutputStream(uri) ?: error("Cannot write selected file")
                stream.use { it.write(output.toByteArray()) }
            } }
            model.postMessage(if(result.isSuccess) "Circuit image exported." else
                "Image export failed: ${result.exceptionOrNull()?.message}")
        }
    }
    val exportPdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot=reportSnapshot
        if(uri!=null && snapshot!=null) ioScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching {
                val output=ByteArrayOutputStream()
                CircuitReport.writePdf(output,snapshot.first,snapshot.second)
                val stream=context.contentResolver.openOutputStream(uri) ?: error("Cannot write selected file")
                stream.use { it.write(output.toByteArray()) }
            } }
            model.postMessage(if(result.isSuccess) "Measurement report exported." else
                "Report export failed: ${result.exceptionOrNull()?.message}")
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) ioScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching {
                val stream=context.contentResolver.openOutputStream(uri) ?: error("Cannot write selected file")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(exportText) }
            } }
            model.postMessage(if(result.isSuccess) "Project exported." else "Export failed: ${result.exceptionOrNull()?.message}")
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) ioScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching {
                val stream=context.contentResolver.openInputStream(uri) ?: error("Cannot read selected file")
                val content=stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val text=StringBuilder()
                    val chunk=CharArray(8192)
                    while(true) {
                        val count=reader.read(chunk)
                        if(count<0) break
                        require(text.length+count<=CircuitJson.MAX_DOCUMENT_CHARS) { "Project file is too large" }
                        text.append(chunk,0,count)
                    }
                    text.toString()
                }
                CircuitJson.decode(content)
            } }
            result.fold(onSuccess=model::importCircuit,
                onFailure={ model.postMessage("Import failed: ${it.message ?: "Invalid project"}") })
        }
    }
    var name by remember(state.projectId) { mutableStateOf(state.circuit.name) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=500.dp).padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Projects",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                Text("✕",Modifier.semantics { contentDescription="Close projects";role=Role.Button }
                    .clickable { model.showProjects(false) }.padding(8.dp),color=Muted)
            }
            OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Circuit name")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("Save",Modifier.clickable { model.saveProject(name) }.padding(7.dp),color=Blue)
                Text("Save As",Modifier.clickable { model.saveProject(name,true) }.padding(7.dp),color=Blue)
                Text("New",Modifier.clickable { model.newCircuit() }.padding(7.dp),color=Blue)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("Circuit PNG",Modifier.clickable {
                    reportSnapshot=state.circuit to state.result
                    exportPng.launch("${state.circuit.name.replace(Regex("[^A-Za-z0-9_-]"),"_")}.png")
                }.padding(7.dp),color=Blue)
                Text("Report PDF",Modifier.clickable {
                    reportSnapshot=state.circuit to state.result
                    exportPdf.launch("${state.circuit.name.replace(Regex("[^A-Za-z0-9_-]"),"_")}-report.pdf")
                }.padding(7.dp),color=Blue)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("Export JSON",Modifier.clickable {
                    exportText=CircuitJson.encode(state.circuit)
                    export.launch("${state.circuit.name.replace(Regex("[^A-Za-z0-9_-]"),"_")}.circuit.json")
                }.padding(7.dp),color=Blue)
                Text("Import JSON",Modifier.clickable { import.launch(arrayOf("application/json","text/plain")) }
                    .padding(7.dp),color=Blue)
            }
            HorizontalDivider(color=Muted.copy(alpha=.4f))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.projects.forEach { project ->
                    Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(project.name,Modifier.weight(1f).semantics {
                            contentDescription=if(project.unreadable) "Unreadable project. Can be deleted."
                                else "Open project ${project.name}"
                            if(!project.unreadable) role=Role.Button
                        }.clickable(enabled=!project.unreadable) { model.openProject(project.id) },
                            color=if(project.unreadable) Color(0xFFFFA0A0) else TextIce,fontSize=13.sp,maxLines=1)
                        if(!project.unreadable) {
                            Text("Open",Modifier.clickable { model.openProject(project.id) }.padding(5.dp),color=Blue,fontSize=11.sp)
                            Text("Copy",Modifier.clickable { model.duplicateProject(project.id) }.padding(5.dp),color=Muted,fontSize=11.sp)
                        }
                        Text("Delete",Modifier.clickable { pendingDelete=project.id }.padding(5.dp),
                            color=Color(0xFFFF8A8A),fontSize=11.sp)
                    }
                    HorizontalDivider(color=Muted.copy(alpha=.15f))
                }
                if(state.projects.isEmpty()) Text("No saved projects yet",Modifier.padding(12.dp),color=Muted,fontSize=12.sp)
            }
        }
    }
    pendingDelete?.let { id ->
        AlertDialog(onDismissRequest={pendingDelete=null},title={Text("Delete saved project?")},
            text={Text("This removes the saved copy from this device.")},
            confirmButton={TextButton(onClick={model.deleteProject(id);pendingDelete=null}) { Text("Delete") }},
            dismissButton={TextButton(onClick={pendingDelete=null}) { Text("Cancel") }})
    }
}
