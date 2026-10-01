package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.canvas.*

@Composable
fun ComponentThumbnail(kind: Kind, modifier: Modifier = Modifier, component: PlacedComponent? = null) {
    Canvas(modifier) {
        val s=(size.minDimension/175f)
        scale(s,s,pivot=androidx.compose.ui.geometry.Offset(size.width/2,size.height/2)) {
            drawComponent((component ?: PlacedComponent(kind=kind,reference="",x=0f,y=0f)).copy(x=size.width/2,y=size.height/2),false,
                if(kind==Kind.LED || kind==Kind.LAMP) .45f else 0f)
        }
    }
}

@Composable
fun CatalogScreen(recent: List<Kind>,onBack:()->Unit,onChoose:(Kind)->Unit,onClearRecent:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Basic") }
    var infoKind by remember { mutableStateOf<Kind?>(null) }
    val categories=listOf("Basic","Sources","Semiconductors","Sensors","Outputs","Measurement")
    val basicOrder=listOf(Kind.BATTERY,Kind.RESISTOR,Kind.CAPACITOR,Kind.INDUCTOR,Kind.FUSE,
        Kind.DIODE,Kind.LED,Kind.NPN_BJT,Kind.SWITCH,Kind.LAMP,Kind.GROUND,Kind.AMMETER,Kind.VOLTMETER)
    val catalog=if(query.isNotBlank()) ComponentRegistry.definitions.values.filter { it.kind!=Kind.JUNCTION }
        else if(category=="Basic") basicOrder.mapNotNull(ComponentRegistry.definitions::get)
        else ComponentRegistry.definitions.values.filter { it.kind.category==category }
    val definitions=catalog.filter { def ->
        query.isBlank() || def.kind.title.contains(query,true) || def.description.contains(query,true) ||
            def.kind.name.contains(query,true) || (def.datasheet.partNumber?.contains(query,true)==true) ||
            (query.equals("cap",true) && def.kind in listOf(Kind.CAPACITOR,Kind.ELECTROLYTIC)) ||
            (query.equals("res",true) && def.kind==Kind.RESISTOR)
    }.let { if(query.isNotBlank()) it.sortedBy { def -> def.kind.title } else it }
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("‹",Modifier.semantics { contentDescription="Back to circuit";role=Role.Button }
                .clickable(onClick=onBack).padding(end=20.dp),color=Muted,fontSize=32.sp)
            Column {
                Text("Add Components",color=TextIce,fontSize=24.sp)
                Text("FIND  ·  LEARN  ·  BUILD",color=Muted,fontSize=10.sp,letterSpacing=2.sp)
            }
        }
        OutlinedTextField(query,{ query=it },Modifier.fillMaxWidth().padding(horizontal=16.dp),
            placeholder={ Text("Search components…",color=Muted) },singleLine=true,
            leadingIcon={ Text("⌕",color=Muted,fontSize=26.sp) },shape=RoundedCornerShape(15.dp))
        if(query.isNotBlank()) Text("Searching all categories",Modifier.padding(start=18.dp,top=4.dp),
            color=Muted,fontSize=11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            categories.forEach { c -> FilterChip(selected=category==c,onClick={ category=c },label={ Text(c) },
                colors=FilterChipDefaults.filterChipColors(containerColor=Navy,labelColor=TextIce,
                    selectedContainerColor=Color(0xFF0B3B70),selectedLabelColor=TextIce),
                border=FilterChipDefaults.filterChipBorder(enabled=true,selected=category==c,
                    borderColor=Color(0xFF294159),selectedBorderColor=Blue,borderWidth=1.dp,
                    selectedBorderWidth=1.dp)) }
        }
        LazyVerticalGrid(GridCells.Fixed(3),Modifier.weight(1f).padding(horizontal=12.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(definitions.isEmpty()) item { Text("No components match",Modifier.padding(12.dp),color=Muted) }
            items(definitions,key={ it.kind }) { def ->
                Surface(Modifier.fillMaxWidth().height(130.dp)
                    .semantics { contentDescription="Add ${def.kind.title}. ${def.description}";role=Role.Button }
                    .clickable { onChoose(def.kind) },
                    color=Panel,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
                    Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.SpaceBetween) {
                        Box(Modifier.fillMaxWidth().height(58.dp)) {
                            ComponentThumbnail(def.kind,Modifier.fillMaxSize())
                            Text("ⓘ",Modifier.align(Alignment.TopEnd)
                                .semantics { contentDescription="About ${def.kind.title}";role=Role.Button }
                                .clickable { infoKind=def.kind }
                                .padding(2.dp),color=Muted,fontSize=12.sp)
                        }
                        Text(def.kind.title,color=TextIce,fontSize=13.sp,maxLines=1)
                        Text(def.description,color=Muted,fontSize=9.sp,maxLines=2,lineHeight=11.sp)
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(),color=Panel,shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Recently used",Modifier.weight(1f),color=TextIce,fontSize=14.sp)
                    if(recent.isNotEmpty()) Text("Clear",Modifier.semantics {
                        contentDescription="Clear recently used components";role=Role.Button
                    }.clickable(onClick=onClearRecent).padding(6.dp),
                        color=Blue,fontSize=12.sp)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    recent.forEach { kind ->
                        Surface(Modifier.size(58.dp).semantics {
                            contentDescription="Add recent ${kind.title}";role=Role.Button
                        }.clickable { onChoose(kind) },color=Navy,
                            shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
                            ComponentThumbnail(kind,Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
    infoKind?.let { kind ->
        val def=ComponentRegistry.definitions.getValue(kind)
        AlertDialog(onDismissRequest={infoKind=null},title={Text(kind.title)},
            text={ Column {
                Text(def.description)
                Text("Terminals: ${PlacedComponent(kind=kind,reference="",x=0f,y=0f).terminalCount}")
                Text("Model accuracy: ${def.modelAccuracy.name.replace('_',' ')}")
                if(def.parameters.isNotEmpty()) Text("Parameters: ${def.parameters.joinToString { it.label }}")
                def.datasheet.partNumber?.let { Text("Part: $it") }
                def.datasheet.manufacturer?.let { Text("Manufacturer: $it") }
                def.datasheet.packageName?.let { Text("Package: $it") }
                def.datasheet.sourceNotes?.let { Text(it) }
            } },confirmButton={TextButton(onClick={infoKind=null}) { Text("Close") }})
    }
}
