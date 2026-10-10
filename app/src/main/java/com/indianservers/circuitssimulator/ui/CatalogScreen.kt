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
        val s=if(kind.isBoard) {
            val board=BoardRegistry.boards.getValue(kind)
            kotlin.math.min(size.width/(board.boardWidth+28f),size.height/(board.boardHeight+28f))
        } else if(kind in IcParts.pinNames && IcParts.pinNames.getValue(kind).size>=16)
            kotlin.math.min(size.width/205f,size.height/240f)
        else size.minDimension/175f
        val part=(component ?: PlacedComponent(kind=kind,reference="",x=0f,y=0f)).copy(x=size.width/2,y=size.height/2)
        scale(s,s,pivot=androidx.compose.ui.geometry.Offset(size.width/2,size.height/2)) {
            drawComponent(part,false,visual=ComponentVisualAdapter.preview(part),details=size.minDimension>=80.dp.toPx())
        }
    }
}

@Composable
fun CatalogScreen(recent: List<Kind>,favorites:Set<Kind>,onBack:()->Unit,
                  onChoose:(Kind)->Unit,onClearRecent:()->Unit,onToggleFavorite:(Kind)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Common") }
    var infoKind by remember { mutableStateOf<Kind?>(null) }
    val categories=listOf("Common","Favorites","Recent")+ComponentRegistry.categories
    val definitions=remember(query,category,recent,favorites) {
        if(query.isNotBlank()) ComponentRegistry.search(query).sortedBy { it.kind.title }
        else when(category) {
            "Common" -> ComponentRegistry.common
            "Favorites" -> favorites.mapNotNull(ComponentRegistry.definitions::get)
            "Recent" -> recent.mapNotNull(ComponentRegistry.definitions::get)
            else -> ComponentRegistry.search("",category)
        }
    }
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
                            Text(if(def.kind in favorites) "★" else "☆",
                                Modifier.align(Alignment.TopStart).clickable { onToggleFavorite(def.kind) }
                                    .padding(2.dp),color=Color(0xFFFFCB69),fontSize=15.sp)
                            Text("ⓘ",Modifier.align(Alignment.TopEnd)
                                .semantics { contentDescription="About ${def.kind.title}";role=Role.Button }
                                .clickable { infoKind=def.kind }
                                .padding(2.dp),color=Muted,fontSize=12.sp)
                        }
                        Text(def.kind.title,color=TextIce,fontSize=13.sp,maxLines=1)
                        Text(if(def.supportStatus==ComponentSupportStatus.SUPPORTED) "✓ Supported" else "≈ Simplified model",
                            color=Mint,fontSize=9.sp,maxLines=1)
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
                Text("Support: ${def.supportStatus.name.replace('_',' ')}")
                Text(if(kind in favorites) "★ Favorite" else "☆ Add to favorites",
                    Modifier.clickable { onToggleFavorite(kind) }.padding(vertical=5.dp),color=Blue)
                if(def.parameters.isNotEmpty()) Text("Parameters: ${def.parameters.joinToString { it.label }}")
                def.datasheet.partNumber?.let { Text("Part: $it") }
                def.datasheet.manufacturer?.let { Text("Manufacturer: $it") }
                def.datasheet.packageName?.let { Text("Package: $it") }
                def.datasheet.sourceNotes?.let { Text(it) }
                if(def.limitations.isNotEmpty()) Text("Limits: ${def.limitations.joinToString()}")
            } },confirmButton={TextButton(onClick={infoKind=null}) { Text("Close") }})
    }
}
