package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.R
import com.indianservers.circuitssimulator.data.SavedProject
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.SampleCircuits
import com.indianservers.circuitssimulator.ui.canvas.TextIce
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ComponentsPage(modifier:Modifier,query:String,model:SimulatorViewModel,
                            onOpenDesigner:()->Unit,onInfo:(String)->Unit) {
    var category by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<com.indianservers.circuitssimulator.domain.Definition?>(null) }
    val state by model.state.collectAsState()
    val registry=com.indianservers.circuitssimulator.domain.ComponentRegistry
    val items=remember(query,category,state.favorites,state.recent) {
        if(query.isNotBlank()) registry.search(query)
        else when(category) {
            "Common" -> registry.common
            "Favorites" -> state.favorites.mapNotNull(registry.definitions::get)
            "Recent" -> state.recent.mapNotNull(registry.definitions::get)
            else -> registry.search("",category)
        }
    }
    Column(modifier.padding(horizontal=12.dp)) {
        PromoHero(R.drawable.components_hero,"★  Component Library","Real Parts","Real Circuits",
            "Explore, place, wire and simulate\ncomponents and development boards.","Explore All",
            onClick={category="All"})
        Spacer(Modifier.height(10.dp))
        CategoryChips(listOf("All","Common","Favorites","Recent")+registry.categories,
            category,{category=it})
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),Modifier.weight(1f),
            horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            if(items.isEmpty()) item { Text("No components match your search.",color=HubSoft,fontSize=12.sp) }
            items(items.size,key={ index -> items[index].id }) { index ->
                val item=items[index]
                Surface(Modifier.height(158.dp).clickable { selected=item },
                    shape=RoundedCornerShape(10.dp),color=HubPanel,
                    border=BorderStroke(1.dp,HubLine.copy(alpha=.85f))) {
                    Column(Modifier.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(64.dp),contentAlignment=Alignment.Center) {
                            ComponentThumbnail(item.kind,Modifier.size(64.dp))
                            Text(if(item.kind in state.favorites) "★" else "☆",
                                Modifier.align(Alignment.TopEnd).clickable { model.toggleFavorite(item.kind) },
                                color=Color(0xFFFFC76A),fontSize=14.sp)
                        }
                        Text(item.kind.title,color=TextIce,fontSize=10.sp,
                            fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(item.kind.category,color=HubBlue,fontSize=8.sp,maxLines=1)
                        Text((if(item.supportStatus==com.indianservers.circuitssimulator.domain.ComponentSupportStatus.SUPPORTED)
                            "✓ Supported" else "≈ Simplified")+" · "+item.description,
                            color=HubSoft,fontSize=8.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    selected?.let { item ->
        AlertDialog(onDismissRequest={selected=null},title={Text(item.kind.title)},
            text={Column(horizontalAlignment=Alignment.CenterHorizontally) {
                ComponentThumbnail(item.kind,Modifier.size(120.dp))
                Text(item.kind.category+" · "+item.supportStatus.name.replace('_',' ').lowercase(),color=HubSoft)
                Text(item.description,color=HubSoft,fontSize=12.sp)
                Text(if(item.kind in state.favorites) "★ Favorite" else "☆ Add to favorites",
                    Modifier.clickable { model.toggleFavorite(item.kind) }.padding(6.dp),color=HubBlue)
                item.datasheet.sourceNotes?.let { Text(it,color=HubSoft,fontSize=11.sp) }
                if(item.limitations.isNotEmpty())
                    Text(item.limitations.joinToString(" "),color=HubSoft,fontSize=11.sp)
            }},
            confirmButton={TextButton(onClick={
                selected=null
                if(item.supportStatus!=com.indianservers.circuitssimulator.domain.ComponentSupportStatus.PREVIEW) {
                    model.setPlacement(item.kind);onOpenDesigner()
                } else onInfo("Simulation model coming soon")
            }) { Text(if(item.supportStatus==com.indianservers.circuitssimulator.domain.ComponentSupportStatus.PREVIEW)
                "OK" else "Use in Designer") }},
            dismissButton={TextButton(onClick={selected=null}) { Text("Close") }})
    }
}

@Composable
internal fun PromoHero(image:Int,eyebrow:String,title:String,highlight:String,subtitle:String,
                       button:String?=null,onClick:()->Unit={}) {
    Box(Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(13.dp))
        .border(1.dp,HubLine,RoundedCornerShape(13.dp))) {
        Image(painterResource(image),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(HubNavy.copy(alpha=.98f),HubNavy.copy(alpha=.82f),Color.Transparent))))
        Column(Modifier.fillMaxHeight().padding(start=13.dp,top=10.dp,bottom=9.dp)) {
            Text(eyebrow,color=HubBlue,fontSize=10.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Text(title,color=TextIce,fontSize=21.sp,fontWeight=FontWeight.Bold,lineHeight=23.sp)
            Text(highlight,color=HubBlue,fontSize=21.sp,fontWeight=FontWeight.Bold,lineHeight=23.sp)
            Text(subtitle,color=HubSoft,fontSize=10.sp,lineHeight=13.sp)
            if(button!=null) {
                Spacer(Modifier.weight(1f))
                Surface(Modifier.clickable(onClick=onClick),color=Color(0xFF065CD5),
                    shape=RoundedCornerShape(50),border=BorderStroke(1.dp,Color(0xFF69C5FF))) {
                    Text("$button  →",Modifier.padding(horizontal=14.dp,vertical=5.dp),
                        color=Color.White,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun CategoryChips(options:List<String>,selected:String,onSelect:(String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val active=selected==option
            Surface(Modifier.clickable { onSelect(option) },shape=RoundedCornerShape(50),
                color=if(active) Color(0xFF0D65E3) else HubPanel,
                border=BorderStroke(1.dp,if(active) Color(0xFF6EB5FF) else HubLine)) {
                Text(option,Modifier.padding(horizontal=14.dp,vertical=7.dp),
                    color=if(active) Color.White else HubSoft,fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun EmptyHubMessage(message:String) {
    Box(Modifier.fillMaxWidth().height(92.dp),contentAlignment=Alignment.Center) {
        Text(message,color=HubSoft,fontSize=12.sp)
    }
}

private data class ProjectItem(val title:String,val level:String,val summary:String,
                               val count:String,val time:String,val image:Int,val category:String,
                               val launch:(()->com.indianservers.circuitssimulator.domain.Circuit)?=null)

private val projectItems=listOf(
    ProjectItem("Blinking LED","Beginner","Arduino subset code drives D13 through a resistor and LED.","5 Components","Firmware",
        R.drawable.project_led,"Beginner",SampleCircuits::blinkingLed),
    ProjectItem("Push Button LED","Beginner","digitalRead of a pulled-up button drives the LED.","6 Components","Firmware",
        R.drawable.project_led,"Beginner",SampleCircuits::buttonLed),
    ProjectItem("Traffic Light","Intermediate","Timed red, amber and green from executable firmware.","9 Components","Firmware",
        R.drawable.project_traffic,"Arduino",SampleCircuits::trafficLight),
    ProjectItem("Servo Control","Intermediate","Potentiometer ADC maps to servo pulse width.","7 Components","Firmware",
        R.drawable.project_rover,"Arduino",SampleCircuits::servoControl),
    ProjectItem("Temperature Monitor","Beginner","Environment temperature → LM35 → ADC → Serial.","6 Components","Firmware",
        R.drawable.project_alarm,"Sensor",SampleCircuits::temperatureMonitor),
    ProjectItem("I2C Scanner","Intermediate","Wire scanner finds the connected I²C devices.","9 Components","Firmware",
        R.drawable.project_irrigation,"Arduino",SampleCircuits::i2cLab),
    ProjectItem("ESP32 OLED","Intermediate","Editable code draws and blinks OLED pixels through GPIO21/22 I²C.","6 Components","Firmware",
        R.drawable.project_irrigation,"IoT",SampleCircuits::esp32Oled),
    ProjectItem("Pico ADC","Beginner","GP26 potentiometer ADC drives GP15 PWM LED and live serial values.","6 Components","Firmware",
        R.drawable.project_led,"IoT",SampleCircuits::picoAdcPwm),
    ProjectItem("NodeMCU LDR","Beginner","LDR ADC controls D4 LED; change environment light to see it respond.","8 Components","Firmware",
        R.drawable.project_streetlight,"IoT",SampleCircuits::nodeMcuLdr),
    ProjectItem("Automatic Street Light","Beginner","Real LDR divider and code switch the LED in darkness.",
        "8 Components","Firmware",R.drawable.project_streetlight,"Sensor",com.indianservers.circuitssimulator.firmware.FirmwareExamples::streetLightCircuit),
    ProjectItem("Smart Irrigation","Intermediate","Adjust the moisture potentiometer; code opens or closes the servo valve.",
        "7 Components","Firmware",R.drawable.project_irrigation,"Automation",com.indianservers.circuitssimulator.firmware.FirmwareExamples::irrigationCircuit)
)

@Composable
internal fun ProjectsPage(modifier:Modifier,query:String,model:SimulatorViewModel,
                          onOpenDesigner:()->Unit,onInfo:(String)->Unit) {
    var category by remember { mutableStateOf("All") }
    val entries=projectItems.filter { item ->
        (category=="All" || item.level==category || item.category==category) &&
            (query.isBlank() || item.title.contains(query,true) || item.summary.contains(query,true))
    }
    fun openProject(item:ProjectItem,run:Boolean=false) {
        val launch=item.launch
        if(launch==null) onInfo("${item.title} is a project preview. Open Guide for interactive lessons available now.")
        else { model.openRunnableProject(launch(),run);onOpenDesigner() }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
        PromoHero(R.drawable.projects_hero,"★  Featured Projects","Ready-Made","Projects",
            "Open the code, run a project and\nwatch its live circuit and serial output.",
            "Run Blinking LED",onClick={openProject(projectItems.first(),true)})
        Spacer(Modifier.height(10.dp))
        CategoryChips(listOf("All","Beginner","Intermediate","Advanced","Arduino","Sensor","Automation"),
            category,{category=it})
        Spacer(Modifier.height(8.dp))
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                row.forEach { item -> ProjectCard(item,Modifier.weight(1f),onClick={openProject(item)},onRun={openProject(item,true)}) }
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(7.dp))
        }
        if(entries.isEmpty()) EmptyHubMessage("No projects match this filter.")
        Surface(shape=RoundedCornerShape(13.dp),color=HubPanel,
            border=BorderStroke(1.dp,HubLine),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(9.dp)) {
                Text("🏆  Popular This Week",color=HubBlue,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(projectItems[3],projectItems[5],projectItems[1]).forEach { item ->
                        Column(Modifier.weight(1f).clickable { openProject(item) }) {
                            Image(painterResource(item.image),null,Modifier.fillMaxWidth().height(39.dp)
                                .clip(RoundedCornerShape(7.dp)),contentScale=ContentScale.Crop)
                            Text(item.title,color=TextIce,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ProjectCard(item:ProjectItem,modifier:Modifier,onClick:()->Unit,onRun:()->Unit) {
    Surface(modifier.height(188.dp).clickable(onClick=onClick),shape=RoundedCornerShape(11.dp),
        color=HubPanel,border=BorderStroke(1.dp,HubLine)) {
        Column {
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                Image(painterResource(item.image),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent,HubPanel.copy(alpha=.92f)))))
                Surface(Modifier.align(Alignment.TopEnd).padding(5.dp),shape=RoundedCornerShape(50),
                    color=HubNavy.copy(alpha=.92f),border=BorderStroke(1.dp,
                        if(item.level=="Beginner") Color(0xFF17D8AC) else Color(0xFFFFAF37))) {
                    Text(item.level,Modifier.padding(horizontal=7.dp,vertical=2.dp),fontSize=8.sp,
                        color=if(item.level=="Beginner") Color(0xFF17D8AC) else Color(0xFFFFAF37))
                }
            }
            Column(Modifier.padding(horizontal=8.dp)) {
                Text(item.title,color=TextIce,fontSize=11.sp,fontWeight=FontWeight.SemiBold,maxLines=1,
                    overflow=TextOverflow.Ellipsis)
                Text(item.summary,color=HubSoft,fontSize=9.sp,lineHeight=11.sp,maxLines=2,
                    overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("◇ ${item.count}    ◷ ${item.time}",color=Color(0xFF9DBFEF),fontSize=8.sp,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=onClick) { Text("Code",fontSize=11.sp) }
                    TextButton(onClick=onRun,enabled=item.launch!=null) { Text("Run project",fontSize=11.sp) }
                }
            }
        }
    }
}

@Composable
internal fun SavedPage(modifier:Modifier,query:String,model:SimulatorViewModel,
                       onOpenDesigner:()->Unit,onInfo:(String)->Unit) {
    val state by model.state.collectAsState()
    var filter by remember { mutableStateOf("All") }
    val favorites=remember { mutableStateListOf<String>() }
    val projects=state.projects.filter { project ->
        (query.isBlank() || project.name.contains(query,true)) &&
            when(filter) {
                "Favorites" -> project.id in favorites
                "Cloud" -> false
                else -> true
            }
    }.let { list -> if(filter=="Recent") list.sortedByDescending { it.modifiedMillis } else list }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
        PromoHero(R.drawable.saved_hero,"Your Circuits","Build Today","Use Tomorrow",
            "${state.projects.size} saved circuits  •  Stored locally\nAccess your circuits on this device.")
        Spacer(Modifier.height(10.dp))
        CategoryChips(listOf("All","Recent","Favorites","Cloud","Offline"),filter,{filter=it})
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Folders",Modifier.weight(1f),color=TextIce,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text("See All  ›",Modifier.clickable { filter="All" },color=HubBlue,fontSize=10.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(Triple("All Circuits","folder","All"),Triple("Recent","cube","Recent"),
                Triple("Favorites","bookmark","Favorites"),Triple("Offline","chip","Offline"))
                .forEach { (label,icon,target) ->
                    Surface(Modifier.weight(1f).height(77.dp).clickable { filter=target },
                        shape=RoundedCornerShape(9.dp),color=HubPanel,border=BorderStroke(1.dp,HubLine)) {
                        Column(Modifier.padding(7.dp)) {
                            HubSymbol(icon,HubBlue,21)
                            Text(label,color=TextIce,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(if(target=="Favorites") "${favorites.size} circuits" else
                                "${state.projects.size} circuits",color=HubSoft,fontSize=8.sp)
                        }
                    }
                }
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Saved Circuits",Modifier.weight(1f),color=TextIce,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text("Sort: Recent ⌄",color=HubBlue,fontSize=10.sp)
        }
        Spacer(Modifier.height(7.dp))
        if(projects.isEmpty()) {
            Surface(shape=RoundedCornerShape(12.dp),color=HubPanel,
                border=BorderStroke(1.dp,HubLine),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    HubSymbol("bookmark",HubBlue,34)
                    Spacer(Modifier.height(6.dp))
                    Text(if(filter=="Cloud") "Cloud sync is not available yet" else
                        if(filter=="Favorites") "No favorites yet" else "No saved circuits yet",
                        color=TextIce,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    Text("Save a design in Circuit Designer to see it here.",color=HubSoft,fontSize=10.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Open Circuit Designer  →",Modifier.clickable(onClick=onOpenDesigner).padding(7.dp),
                        color=HubBlue,fontSize=11.sp)
                }
            }
        } else projects.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                row.forEach { project ->
                    SavedCircuitCard(project,project.id in favorites,Modifier.weight(1f),
                        onFavorite={ if(project.id in favorites) favorites.remove(project.id) else favorites.add(project.id) },
                        onOpen={
                            if(project.unreadable) onInfo("This saved circuit cannot be read. Open Circuit Designer to manage projects.")
                            else { model.openProject(project.id);onOpenDesigner() }
                        })
                }
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SavedCircuitCard(project:SavedProject,favorite:Boolean,modifier:Modifier,
                             onFavorite:()->Unit,onOpen:()->Unit) {
    Surface(modifier.height(161.dp).clickable(onClick=onOpen),shape=RoundedCornerShape(10.dp),
        color=HubPanel,border=BorderStroke(1.dp,HubLine)) {
        Column {
            Box(Modifier.fillMaxWidth().height(82.dp)) {
                CircuitPreview(Modifier.fillMaxSize())
                Text(if(favorite) "★" else "☆",Modifier.align(Alignment.TopEnd)
                    .clickable(onClick=onFavorite).padding(horizontal=8.dp,vertical=2.dp),
                    color=if(favorite) Color(0xFFFFC34A) else HubSoft,fontSize=19.sp)
            }
            Text(project.name,Modifier.padding(horizontal=7.dp),color=TextIce,fontSize=11.sp,
                fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("Edited ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.modifiedMillis))}",
                Modifier.padding(horizontal=7.dp),color=HubSoft,fontSize=8.sp,maxLines=1)
            Text("Local  •  Circuit",Modifier.padding(horizontal=7.dp),color=HubBlue,fontSize=8.sp)
        }
    }
}

@Composable
private fun CircuitPreview(modifier:Modifier) {
    androidx.compose.foundation.Canvas(modifier.background(Color(0xFF041B33))) {
        val grid=Color(0xFF183552)
        val step=size.width/13f
        var x=0f
        while(x<size.width) { drawLine(grid,androidx.compose.ui.geometry.Offset(x,0f),
            androidx.compose.ui.geometry.Offset(x,size.height),.5f);x+=step }
        var y=0f
        while(y<size.height) { drawLine(grid,androidx.compose.ui.geometry.Offset(0f,y),
            androidx.compose.ui.geometry.Offset(size.width,y),.5f);y+=step }
        val white=Color(0xFFB7D8FF)
        val cy=size.height*.5f
        val left=size.width*.16f
        val right=size.width*.84f
        drawLine(white,androidx.compose.ui.geometry.Offset(left,cy),
            androidx.compose.ui.geometry.Offset(size.width*.38f,cy),2f)
        val zig=Path().apply {
            moveTo(size.width*.38f,cy)
            for(i in 0..5) lineTo(size.width*(.40f+i*.035f),cy+if(i%2==0) -8f else 8f)
            lineTo(size.width*.61f,cy)
        }
        drawPath(zig,white,style=Stroke(2f))
        drawLine(white,androidx.compose.ui.geometry.Offset(size.width*.61f,cy),
            androidx.compose.ui.geometry.Offset(right,cy),2f)
        drawCircle(Color(0xFFFF575C),7f,androidx.compose.ui.geometry.Offset(right,cy))
        drawCircle(white,3f,androidx.compose.ui.geometry.Offset(left,cy))
        drawLine(white,androidx.compose.ui.geometry.Offset(left,cy),
            androidx.compose.ui.geometry.Offset(left,size.height*.8f),2f)
        drawLine(white,androidx.compose.ui.geometry.Offset(left,size.height*.8f),
            androidx.compose.ui.geometry.Offset(right,size.height*.8f),2f)
        drawLine(white,androidx.compose.ui.geometry.Offset(right,size.height*.8f),
            androidx.compose.ui.geometry.Offset(right,cy),2f)
    }
}
