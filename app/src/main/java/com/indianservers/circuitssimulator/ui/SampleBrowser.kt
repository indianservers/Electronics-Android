package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.indianservers.circuitssimulator.domain.Circuit
import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.ui.canvas.*

private data class SampleEntry(val title:String,val category:String,val description:String,
                               val create:()->Circuit)

private val sampleEntries=listOf(
    SampleEntry("LED Demo","Beginner","A battery, switch, resistor, and LED",SampleCircuits::led),
    SampleEntry("Lamp Demo","Beginner","A switched resistive lamp",SampleCircuits::lamp),
    SampleEntry("Voltage Divider","Beginner","Two resistors divide a DC supply",SampleCircuits::divider),
    SampleEntry("RC Charging","Transient","Capacitor charging after a switch",SampleCircuits::rc),
    SampleEntry("RL Rise","Transient","Inductor current building over time",SampleCircuits::rl),
    SampleEntry("Function Generator","Transient","Time-varying source and waveform",SampleCircuits::generator),
    SampleEntry("RC Low-pass","Filters","Resistor-capacitor frequency response",SampleCircuits::rcLowPass),
    SampleEntry("RC High-pass","Filters","Capacitor-resistor frequency response",SampleCircuits::rcHighPass),
    SampleEntry("RL Low-pass","Filters","Inductor-resistor frequency response",SampleCircuits::rlLowPass),
    SampleEntry("Light Sensor","Sensors","Photoresistor voltage divider",SampleCircuits::lightDivider),
    SampleEntry("Adjustable Divider","Sensors","Potentiometer with live wiper",SampleCircuits::potentiometerDemo),
    SampleEntry("Fuse Fault","Faults","A fuse opens after overload",SampleCircuits::fuseFault),
    SampleEntry("Op-amp ×10","Analog","Non-inverting amplifier",{SampleCircuits.nonInvertingOpAmp()}),
    SampleEntry("NPN Switch","Analog","Bipolar transistor control",{SampleCircuits.npnSwitch()}),
    SampleEntry("NMOS Switch","Analog","MOSFET load switching",{SampleCircuits.nmosSwitch()})
)

@Composable
fun SampleBrowser(model:SimulatorViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    val categories=listOf("All","Beginner","Transient","Filters","Sensors","Analog","Faults")
    val matches=sampleEntries.filter { entry ->
        (category=="All" || entry.category==category) &&
            (query.isBlank() || entry.title.contains(query,true) ||
                entry.description.contains(query,true) || entry.category.contains(query,true))
    }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=500.dp).padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Sample circuits",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                Text("✕",Modifier.semantics { contentDescription="Close sample circuits";role=Role.Button }
                    .clickable { model.showSamples(false) }.padding(8.dp),color=Muted)
            }
            OutlinedTextField(query,{ query=it },Modifier.fillMaxWidth(),
                label={Text("Search samples")},singleLine=true)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                categories.forEach { choice ->
                    FilterChip(selected=choice==category,onClick={category=choice},label={Text(choice)})
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                matches.forEach { entry ->
                    Column(Modifier.fillMaxWidth().semantics {
                        contentDescription="Open ${entry.title}. ${entry.description}";role=Role.Button
                    }.clickable { model.loadSample(entry.create()) }
                        .padding(horizontal=8.dp,vertical=9.dp)) {
                        Text(entry.title,color=TextIce,fontSize=14.sp)
                        Text("${entry.category} · ${entry.description}",color=Muted,fontSize=11.sp)
                    }
                }
                if(matches.isEmpty()) Text("No samples match your search",Modifier.padding(12.dp),
                    color=Muted,fontSize=12.sp)
            }
        }
    }
}
