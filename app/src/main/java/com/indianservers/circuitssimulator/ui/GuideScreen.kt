package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.guide.*

@Composable
internal fun GuidePage(modifier:Modifier,model:SimulatorViewModel,query:String,onOpen:()->Unit) {
    val state by model.state.collectAsState()
    var onboarding by remember { mutableStateOf(!state.guideOnboardingSeen) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Learn by building",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
        Text("Every step uses the real circuit canvas, simulation, and multimeter. Your work is saved as you go.",color=HubSoft)
        if(onboarding) Surface(color=Color(0xFF12375B),shape=RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("How the Guide works",color=Color.White,fontWeight=FontWeight.Bold)
                Text("1. Choose a component and tap the canvas to place it.\n2. Tap two pins to draw a wire.\n3. Use Simulation to run and open the meter.\n4. Follow the step card; use Hint whenever you need help.",color=HubSoft)
                Button(onClick={ model.markGuideOnboardingSeen();onboarding=false;model.startLesson("first-led");onOpen() }) { Text("Start first circuit") }
            }
        }
        state.guideResumeId?.let { id -> LessonCatalog.byId[id]?.let { lesson ->
            Button(onClick={if(model.resumeLesson()) onOpen()},modifier=Modifier.fillMaxWidth()) {
                Text("Continue: ${lesson.title} (${state.guideResumeStep}/${lesson.steps.size})")
            }
        } }
        val visible=LessonCatalog.lessons.filter { query.isBlank() ||
            it.title.contains(query,true) || it.summary.contains(query,true) || it.category.contains(query,true) }
        if(visible.isEmpty()) Text("No matching lessons",color=HubSoft)
        visible.groupBy { it.category }.forEach { (category,lessons) ->
            Text(category,color=HubCyan,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            lessons.forEachIndexed { _,lesson ->
                val done=lesson.id in state.guideCompleted
                Surface(Modifier.fillMaxWidth().clickable {
                    if(lesson.id==state.guideResumeId) model.resumeLesson() else model.startLesson(lesson.id)
                    onOpen()
                },
                    color=HubPanel,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,HubLine)) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lesson.title,color=Color.White,fontWeight=FontWeight.SemiBold)
                            Text(lesson.summary,color=HubSoft,fontSize=12.sp)
                            Text("${if(done) "Completed  •  " else if(lesson.id==state.guideResumeId) "${state.guideResumeStep}/${lesson.steps.size} done  •  " else ""}${lesson.steps.size} steps  •  ${lesson.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                color=HubCyan,fontSize=11.sp)
                        }
                        Text(if(done) "✓" else "›",color=HubCyan,fontSize=24.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GuideHud(state:SimulatorState,model:SimulatorViewModel,onExit:()->Unit,
                      modifier:Modifier=Modifier) {
    val session=state.guide ?: return
    val lesson=LessonCatalog.byId[session.lessonId] ?: return
    var expanded by remember(session.lessonId) { mutableStateOf(true) }
    Surface(modifier.widthIn(max=390.dp).fillMaxWidth(.96f),color=Color(0xF008223B),
        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,HubCyan)) {
        Column(Modifier.heightIn(max=230.dp).verticalScroll(rememberScrollState()).padding(11.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(lesson.title,Modifier.weight(1f),color=Color.White,fontWeight=FontWeight.Bold,maxLines=1)
                Text("${session.stepIndex.coerceAtMost(lesson.steps.size)}/${lesson.steps.size}",color=HubCyan,fontSize=12.sp)
                TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide" else "Show") }
            }
            LinearProgressIndicator(progress={session.stepIndex.toFloat()/lesson.steps.size},
                modifier=Modifier.fillMaxWidth(),color=HubCyan)
            if(expanded) {
                if(session.complete) {
                    Text("Lesson complete",color=Color(0xFF70E6A5),fontWeight=FontWeight.Bold)
                    Text(lesson.completionNote,color=HubSoft,fontSize=12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick=model::saveGuideCopy) { Text("Save a copy") }
                        TextButton(onClick={model::restartLesson}) { Text("Practice again") }
                        val next=LessonCatalog.lessons.getOrNull(LessonCatalog.lessons.indexOf(lesson)+1)
                        if(next!=null) TextButton(onClick={model.startLesson(next.id)}) { Text("Next lesson") }
                        TextButton(onClick=onExit) { Text("Guide home") }
                    }
                } else lesson.steps.getOrNull(session.stepIndex)?.let { step ->
                    Text(step.instruction,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    if(!state.running && (step.criterion is LessonCriterion.ProbeVoltage ||
                        step.criterion is LessonCriterion.ProbeCurrent ||
                        step.criterion is LessonCriterion.WorkingLed))
                        Text("Simulation paused. Tap Run to continue.",color=Color(0xFFFFD99A),fontSize=11.sp)
                    Text(step.why,color=HubSoft,fontSize=11.sp)
                    if(step.focusKinds.isNotEmpty()) Text("Find: ${step.focusKinds.joinToString { it.title }}",color=HubCyan,fontSize=11.sp)
                    if(step.focusPins.isNotEmpty()) Text("Pins: ${step.focusPins.joinToString { "${it.kind.title} ${if(it.pin==0) "+/1" else "−/2"}" }}",color=HubCyan,fontSize=11.sp)
                    val shown=session.hintsFor(step)
                    if(shown>0) Text(step.hints.take(shown).joinToString("\n"),color=Color(0xFFFFD99A),fontSize=11.sp)
                    session.feedback?.let { Text(it,color=Color(0xFFFFB7A5),fontSize=11.sp) }
                    if(step.choices.isNotEmpty()) step.choices.forEach { answer ->
                        OutlinedButton(onClick={model.guideAnswer(answer)},modifier=Modifier.fillMaxWidth()) { Text(answer) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if(step.hints.isNotEmpty()) TextButton(onClick=model::guideHint) { Text("Hint ${shown}/${step.hints.size}") }
                        if(step.criterion==LessonCriterion.Continue) TextButton(onClick=model::guideContinue) { Text("Continue") }
                        TextButton(onClick=model::restartLesson) { Text("Restart") }
                        TextButton(onClick=onExit) { Text("Exit") }
                    }
                }
            }
        }
    }
}
