package com.indianservers.circuitssimulator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.R
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.ComponentRegistry
import com.indianservers.circuitssimulator.ui.canvas.Blue
import com.indianservers.circuitssimulator.ui.canvas.Muted
import com.indianservers.circuitssimulator.ui.canvas.TextIce

internal val HubNavy=Color(0xFF03142B)
internal val HubPanel=Color(0xFF081E37)
internal val HubLine=Color(0xFF2D537D)
internal val HubCyan=Color(0xFF09C7F5)
internal val HubSoft=Color(0xFFA9BDE3)
internal val HubBlue=Color(0xFF448FFF)

internal enum class HubPage { HOME, COMPONENTS, GUIDE, PROJECTS, SAVED, DESIGNER }

@Composable
fun AppRoot(model:SimulatorViewModel) {
    var pageName by rememberSaveable { mutableStateOf(HubPage.HOME.name) }
    val page=runCatching { HubPage.valueOf(pageName) }.getOrDefault(HubPage.HOME)
    fun navigate(target:HubPage) {
        if(target==HubPage.SAVED) model.refreshProjects()
        pageName=target.name
    }
    if(page==HubPage.DESIGNER) {
        SimulatorScreen(model,onHome={ if(model.state.value.guide!=null) { model.exitGuide();navigate(HubPage.GUIDE) }
            else navigate(HubPage.HOME) })
    } else {
        BackHandler(page!=HubPage.HOME) { navigate(HubPage.HOME) }
        HubScreen(page,model,::navigate)
    }
}

@Composable
private fun HubScreen(page:HubPage,model:SimulatorViewModel,navigate:(HubPage)->Unit) {
    var searchOpen by remember(page) { mutableStateOf(false) }
    var query by remember(page) { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    MaterialTheme(colorScheme=darkColorScheme(background=HubNavy,surface=HubPanel,primary=HubBlue,
        onBackground=TextIce,onSurface=TextIce)) {
        Column(Modifier.fillMaxSize().background(HubNavy).statusBarsPadding().navigationBarsPadding()) {
            if(page==HubPage.HOME) {
                HomePage(Modifier.weight(1f),model,onNavigate=navigate,onSearch={ searchOpen=true },
                    onSettings={ settingsOpen=true })
            } else {
                HubHeader(page,onBack={ navigate(HubPage.HOME) },onSearch={ searchOpen=!searchOpen },
                    onFilter={ infoMessage="Choose a category below to filter this screen." })
                if(searchOpen) SearchBox(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=6.dp))
                when(page) {
                    HubPage.COMPONENTS -> ComponentsPage(Modifier.weight(1f),query,model,
                        onOpenDesigner={ navigate(HubPage.DESIGNER) },onInfo={infoMessage=it})
                    HubPage.GUIDE -> GuidePage(Modifier.weight(1f),model,query,onOpen={navigate(HubPage.DESIGNER)})
                    HubPage.PROJECTS -> ProjectsPage(Modifier.weight(1f),query,model,
                        onOpenDesigner={navigate(HubPage.DESIGNER)},onInfo={infoMessage=it})
                    HubPage.SAVED -> SavedPage(Modifier.weight(1f),query,model,
                        onOpenDesigner={navigate(HubPage.DESIGNER)},onInfo={infoMessage=it})
                    else -> Unit
                }
                HubBottomBar(page,navigate)
            }
        }
        if(searchOpen && page==HubPage.HOME) {
            AlertDialog(onDismissRequest={searchOpen=false},title={Text("Search")},
                text={Column {
                    SearchBox(query,{query=it})
                    listOf("Circuit Designer","Explore Components","Guide","Projects","Saved Circuits")
                        .filter { it.contains(query,true) }.forEach { title ->
                            Text(title,Modifier.fillMaxWidth().clickable {
                                searchOpen=false
                                navigate(when(title) {
                                    "Circuit Designer" -> HubPage.DESIGNER
                                    "Explore Components" -> HubPage.COMPONENTS
                                    "Guide" -> HubPage.GUIDE
                                    "Projects" -> HubPage.PROJECTS
                                    else -> HubPage.SAVED
                                })
                            }.padding(10.dp),color=TextIce)
                        }
                }},confirmButton={TextButton(onClick={searchOpen=false}) { Text("Close") }})
        }
        if(settingsOpen) AlertDialog(onDismissRequest={settingsOpen=false},
            title={Text("Circuits Simulator")},
            text={Text("Interactive electronics and embedded-systems simulator. Models are educational approximations, datasheet-based where documented, and not a substitute for hardware checks on safety-critical designs. Circuits and code stay on this device.")},
            confirmButton={TextButton(onClick={settingsOpen=false}) { Text("Done") }})
        infoMessage?.let { message -> AlertDialog(onDismissRequest={infoMessage=null},
            title={Text("Information")},text={Text(message)},
            confirmButton={TextButton(onClick={infoMessage=null}) { Text("OK") }}) }
    }
}

@Composable
private fun SearchBox(value:String,onValue:(String)->Unit,modifier:Modifier=Modifier) {
    Surface(modifier,border=BorderStroke(1.dp,HubLine),shape=RoundedCornerShape(15.dp),color=HubPanel) {
        Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("⌕",color=HubBlue,fontSize=24.sp)
            Spacer(Modifier.width(8.dp))
            BasicTextField(value,onValue,Modifier.weight(1f),singleLine=true,
                textStyle=androidx.compose.ui.text.TextStyle(color=TextIce,fontSize=14.sp),
                decorationBox={ inner -> Box {
                    if(value.isEmpty()) Text("Search",color=HubSoft,fontSize=14.sp)
                    inner()
                } })
        }
    }
}

@Composable
private fun HomePage(modifier:Modifier,model:SimulatorViewModel,
                     onNavigate:(HubPage)->Unit,onSearch:()->Unit,onSettings:()->Unit) {
    BoxWithConstraints(modifier) {
    val footerHeight=(maxHeight-245.dp-94.dp*5-6.dp*4).coerceAtLeast(74.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(245.dp)) {
            Image(painterResource(R.drawable.home_hero),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(HubNavy.copy(alpha=.96f),HubNavy.copy(alpha=.74f),Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Transparent,Color.Transparent,HubNavy.copy(alpha=.28f)))))
            Column(Modifier.fillMaxSize().padding(horizontal=19.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    BrandChip()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text("Circuits ",color=TextIce,fontSize=20.sp,fontWeight=FontWeight.Bold)
                            Text("Simulator",color=HubCyan,fontSize=20.sp,fontWeight=FontWeight.Bold)
                        }
                        Text("Design  •  Simulate  •  Learn  •  Build",color=HubSoft,fontSize=10.sp)
                    }
                    HeaderAction("⌕","Search",onSearch)
                    HeaderAction("⚙","Settings",onSettings)
                }
                Spacer(Modifier.height(23.dp))
                Text("From Ideas",color=TextIce,fontSize=28.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp)
                Row {
                    Text("to ",color=TextIce,fontSize=28.sp,fontWeight=FontWeight.Bold)
                    Text("Real Circuits",color=HubCyan,fontSize=28.sp,fontWeight=FontWeight.Bold)
                }
                Text("Experiment • Learn • Innovate\nAnytime, Anywhere",color=HubSoft,fontSize=14.sp,lineHeight=20.sp)
                Spacer(Modifier.height(11.dp))
                Text("Electronics for a Brighter Tomorrow",color=Color(0xFF72D9FF),fontSize=12.sp,
                    fontStyle=androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier=Modifier.width(152.dp))
            }
        }
        Column(Modifier.padding(horizontal=9.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            FeatureCard("Circuit Designer","Design, simulate and\ntest your circuits",HubPage.DESIGNER,
                Color(0xFF0455D5),null,"circuit",onNavigate)
            FeatureCard("Explore Components","Discover and learn\nelectronic components",HubPage.COMPONENTS,
                Color(0xFF009A77),R.drawable.components_hero,"chip",onNavigate)
            FeatureCard("Guide","Build circuits with\ninteractive lessons",HubPage.GUIDE,
                Color(0xFF087B9B),null,"circuit",onNavigate)
            FeatureCard("Projects","Try ready-made projects\nand circuit examples",HubPage.PROJECTS,
                Color(0xFFD37A1C),R.drawable.project_rover,"folder",onNavigate)
            FeatureCard("Saved Circuits","Access and manage\nyour saved designs",HubPage.SAVED,
                Color(0xFF6634DF),R.drawable.saved_hero,"bookmark",onNavigate)
        }
        Text("Start Building",Modifier.padding(start=14.dp,top=14.dp,bottom=5.dp),
            color=TextIce,fontSize=15.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=10.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            ComponentRegistry.common.forEach { definition ->
                Surface(Modifier.width(84.dp).height(92.dp).clickable {
                    model.setPlacement(definition.kind);onNavigate(HubPage.DESIGNER)
                },color=HubPanel,shape=RoundedCornerShape(10.dp),
                    border=BorderStroke(1.dp,HubLine)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        ComponentThumbnail(definition.kind,Modifier.size(60.dp))
                        Text(definition.kind.title,color=TextIce,fontSize=9.sp,maxLines=1)
                    }
                }
            }
        }
        Text("Explore all components →",Modifier.clickable { onNavigate(HubPage.COMPONENTS) }
            .padding(horizontal=14.dp,vertical=9.dp),color=HubBlue,fontSize=12.sp)
        HubFooter(Modifier.fillMaxWidth().height(footerHeight))
    }
    }
}

@Composable
private fun HubFooter(modifier:Modifier) {
    Box(modifier,contentAlignment=Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val trace=Color(0xFF1479BF).copy(alpha=.37f)
            val w=size.width;val h=size.height
            repeat(5) { i ->
                val y=h*.12f+i*16f
                val p=Path().apply { moveTo(0f,y);lineTo(w*.08f,y);lineTo(w*.15f,y+22f)
                    lineTo(w*.25f,y+22f);lineTo(w*.31f,y+8f) }
                drawPath(p,trace,style=Stroke(1.5f))
                drawCircle(HubBlue.copy(alpha=.65f),3f,Offset(w*.31f,y+8f))
                val q=Path().apply { moveTo(w,h-y);lineTo(w*.93f,h-y)
                    lineTo(w*.87f,h-y-26f);lineTo(w*.76f,h-y-26f) }
                drawPath(q,trace,style=Stroke(1.5f))
                drawCircle(HubBlue.copy(alpha=.65f),3f,Offset(w*.76f,h-y-26f))
            }
        }
        Text("S M A L L   C I R C U I T S   B I G   P O S S I B I L I T I E S",
            color=Color(0xFF588CC8),fontSize=8.sp)
    }
}

@Composable
private fun FeatureCard(title:String,subtitle:String,page:HubPage,tint:Color,photo:Int?,
                        symbol:String,onNavigate:(HubPage)->Unit) {
    Box(Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(14.dp))
        .border(1.dp,tint.copy(alpha=.8f),RoundedCornerShape(14.dp))
        .clickable { onNavigate(page) }) {
        if(photo!=null) Image(painterResource(photo),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(
            listOf(Color(0xFF084C9B),Color(0xFF062046),Color(0xFF02356F)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(tint.copy(alpha=.58f),HubNavy.copy(alpha=.83f),HubNavy.copy(alpha=.08f)))))
        Row(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(51.dp).clip(RoundedCornerShape(13.dp)).background(
                Brush.linearGradient(listOf(tint,tint.copy(alpha=.48f))))
                .border(1.dp,Color.White.copy(alpha=.35f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
                HubSymbol(symbol,Color.White,30)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1,
                    overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle,color=Color(0xFFCADAF4),fontSize=11.sp,lineHeight=15.sp)
            }
            Box(Modifier.size(33.dp).clip(RoundedCornerShape(50)).background(tint)
                .border(1.dp,Color.White.copy(alpha=.27f),RoundedCornerShape(50)),
                contentAlignment=Alignment.Center) { Text("➜",color=Color.White,fontSize=23.sp) }
        }
    }
}

@Composable
private fun HubHeader(page:HubPage,onBack:()->Unit,onSearch:()->Unit,onFilter:()->Unit) {
    val title=when(page) { HubPage.COMPONENTS -> "Explore Components";HubPage.GUIDE -> "Guide";HubPage.PROJECTS -> "Projects";else -> "Saved Circuits" }
    val subtitle=when(page) { HubPage.COMPONENTS -> "Discover  •  Learn  •  Use";HubPage.GUIDE -> "Build  •  Measure  •  Understand";HubPage.PROJECTS -> "Build  •  Learn  •  Experiment";else -> "Organize  •  Reuse  •  Continue" }
    Row(Modifier.fillMaxWidth().height(61.dp).padding(horizontal=13.dp),verticalAlignment=Alignment.CenterVertically) {
        HeaderAction("‹","Back",onBack)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title,color=TextIce,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1)
            Text(subtitle,color=HubSoft,fontSize=11.sp)
        }
        HeaderAction("⌕","Search",onSearch)
        if(page!=HubPage.GUIDE) HeaderAction("☷","Filter",onFilter)
    }
}

@Composable
private fun HeaderAction(symbol:String,label:String,onClick:()->Unit) {
    Box(Modifier.size(39.dp).clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        Text(symbol,color=Color.White,fontSize=30.sp,modifier=Modifier.semantics {
            contentDescription=label
        })
    }
}

@Composable
private fun HubBottomBar(selected:HubPage,navigate:(HubPage)->Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF06192F))
        .border(BorderStroke(1.dp,Color(0xFF18395B))),verticalAlignment=Alignment.CenterVertically) {
        listOf(Triple(HubPage.HOME,"Home","home"),Triple(HubPage.COMPONENTS,"Components","chip"),
            Triple(HubPage.GUIDE,"Guide","circuit"),Triple(HubPage.PROJECTS,"Projects","cube"),Triple(HubPage.SAVED,"Saved","bookmark"))
            .forEach { (page,label,icon) ->
                Column(Modifier.weight(1f).fillMaxHeight().clickable { navigate(page) },
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    HubSymbol(icon,if(selected==page) HubBlue else HubSoft,22)
                    Text(label,color=if(selected==page) HubBlue else HubSoft,fontSize=10.sp,
                        fontWeight=if(selected==page) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
    }
}

@Composable
private fun BrandChip() {
    Box(Modifier.size(39.dp).clip(RoundedCornerShape(8.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF037BC7),Color(0xFF021A50))))
        .border(1.dp,HubCyan,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
        HubSymbol("circuit",Color.White,28)
    }
}

@Composable
internal fun HubSymbol(kind:String,color:Color,size:Int) {
    androidx.compose.foundation.Canvas(Modifier.size(size.dp)) {
        val u=this.size.width/24f
        fun line(x1:Float,y1:Float,x2:Float,y2:Float) = drawLine(color,
            Offset(x1*u,y1*u),Offset(x2*u,y2*u),strokeWidth=1.8f*u)
        fun outline(vararg points:Pair<Float,Float>) {
            val path=Path().apply {
                points.forEachIndexed { index,point ->
                    if(index==0) moveTo(point.first*u,point.second*u)
                    else lineTo(point.first*u,point.second*u)
                }
            }
            drawPath(path,color,style=Stroke(1.8f*u))
        }
        when(kind) {
            "chip","circuit" -> {
                drawRoundRect(color,Offset(4f*u,4f*u),
                    androidx.compose.ui.geometry.Size(16f*u,16f*u),CornerRadius(2f*u),style=Stroke(1.8f*u))
                for(v in listOf(7f,12f,17f)) {
                    line(v,1f,v,4f);line(v,20f,v,23f)
                    line(1f,v,4f,v);line(20f,v,23f,v)
                }
                if(kind=="chip") drawRoundRect(color,Offset(8f*u,8f*u),
                    androidx.compose.ui.geometry.Size(8f*u,8f*u),CornerRadius(1f*u),style=Stroke(1.5f*u))
                else {
                    line(8f,9f,15f,9f);line(8f,9f,8f,16f);line(8f,16f,15f,16f)
                    drawCircle(color,1.5f*u,Offset(15f*u,9f*u))
                    drawCircle(color,1.5f*u,Offset(15f*u,16f*u))
                }
            }
            "folder" -> outline(3f to 7f,9f to 7f,11f to 9f,21f to 9f,21f to 19f,
                3f to 19f,3f to 7f)
            "bookmark" -> outline(6f to 3f,18f to 3f,18f to 21f,12f to 17f,6f to 21f,6f to 3f)
            "home" -> {
                outline(3f to 11f,12f to 3f,21f to 11f)
                outline(5f to 10f,5f to 21f,19f to 21f,19f to 10f)
                outline(10f to 21f,10f to 15f,14f to 15f,14f to 21f)
            }
            "cube" -> {
                outline(12f to 2f,21f to 7f,21f to 17f,12f to 22f,3f to 17f,3f to 7f,12f to 2f)
                line(3f,7f,12f,12f);line(21f,7f,12f,12f);line(12f,12f,12f,22f)
            }
            else -> drawCircle(color,8f*u,style=Stroke(1.8f*u))
        }
    }
}
