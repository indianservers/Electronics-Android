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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.indianservers.circuitssimulator.intelligence.*
import com.indianservers.circuitssimulator.ui.canvas.*

@Composable
fun DiagnosticsSheet(state:SimulatorState,model:SimulatorViewModel) {
    val report=state.intelligence
    var expanded by remember { mutableStateOf<String?>(null) }
    var pendingFix by remember { mutableStateOf<SmartFix?>(null) }
    val displayed=if(state.intelligenceMode==ExplanationMode.BEGINNER && expanded==null)
        report.issues.take(4) else report.issues
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=470.dp).padding(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(state.diagnosticWhy) "Why isn't this working?" else "Circuit Check",
                    Modifier.weight(1f),color=TextIce,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("✕",Modifier.semantics { contentDescription="Close diagnostics" }
                    .clickable { model.showDiagnostics(false) }.padding(8.dp),color=Muted)
            }
            Text("${report.urgent} errors · ${report.warnings} warnings · ${report.suggestions} suggestions · ${report.insights.size} insights",
                color=Muted,fontSize=12.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Detail:",color=Muted,fontSize=11.sp)
                ExplanationMode.entries.forEach { mode -> Text(
                    mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    Modifier.clickable { model.setIntelligenceMode(mode) }
                        .padding(horizontal=7.dp,vertical=5.dp),
                    color=if(state.intelligenceMode==mode) Blue else Muted,fontSize=11.sp) }
                Spacer(Modifier.weight(1f))
                Text(if(state.liveWarnings) "Live ✓" else "Live off",
                    Modifier.clickable { model.setLiveWarnings(!state.liveWarnings) }.padding(5.dp),
                    color=if(state.liveWarnings) Mint else Muted,fontSize=11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if(report.issues.isEmpty()) Text("No obvious circuit problems found",
                    color=Mint,fontSize=14.sp,fontWeight=FontWeight.Medium)
                if(state.diagnosticWhy && report.issues.isNotEmpty())
                    Text("Most likely issue: ${report.issues.first().title}",color=Color(0xFFFFC789),fontSize=12.sp)
                displayed.forEach { issue ->
                    val tint=when(issue.severity) {
                        IssueSeverity.CRITICAL,IssueSeverity.ERROR -> Color(0xFFFF7777)
                        IssueSeverity.WARNING -> Color(0xFFFFB25E)
                        IssueSeverity.SUGGESTION -> Blue
                        IssueSeverity.INSIGHT -> Mint
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical=4.dp)
                        .background(Panel,RoundedCornerShape(9.dp))
                        .semantics { contentDescription="${issue.severity.name}: ${issue.title}. ${issue.summary}" }
                        .clickable { expanded=if(expanded==issue.id) null else issue.id }
                        .padding(10.dp)) {
                        Text("${issue.severity.name} · ${issue.category.name.replace('_',' ')}",color=tint,fontSize=10.sp)
                        Text(issue.title,color=TextIce,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        Text(issue.summary,color=Muted,fontSize=12.sp)
                        if(expanded==issue.id) {
                            Spacer(Modifier.height(6.dp))
                            Text(issue.explanation,color=TextIce,fontSize=12.sp)
                            if(issue.evidence.isNotEmpty()) {
                                Text("Why am I seeing this?",color=Blue,fontSize=11.sp,fontWeight=FontWeight.Medium)
                                issue.evidence.forEach { ev -> Text("${ev.label}: ${ev.value}"+
                                    (ev.source?.let { " · ${it.name.lowercase().replace('_',' ')}" } ?: ""),
                                    color=Muted,fontSize=11.sp) }
                            }
                            issue.modelNote?.let { Text(it,color=Muted,fontSize=10.sp) }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                if(issue.componentIds.isNotEmpty() || issue.pins.isNotEmpty())
                                    TextButton(onClick={model.focusIssue(issue)}) { Text("Show on canvas") }
                                issue.fixes.forEach { fix -> TextButton(onClick={pendingFix=fix}) { Text(fix.label) } }
                            }
                        }
                    }
                }
                if(report.issues.size>displayed.size) TextButton(onClick={expanded=report.issues.firstOrNull()?.id}) {
                    Text("Show all ${report.issues.size} issues") }
                Text("Explain Circuit",Modifier.padding(top=10.dp),color=Blue,fontWeight=FontWeight.SemiBold)
                Text(report.summary,color=TextIce,fontSize=12.sp)
                if(report.insights.isNotEmpty()) Text("Circuit Insights",Modifier.padding(top=10.dp),
                    color=Blue,fontWeight=FontWeight.SemiBold)
                report.insights.forEach { insight ->
                    Column(Modifier.fillMaxWidth().padding(vertical=3.dp)
                        .background(Panel,RoundedCornerShape(9.dp)).padding(10.dp)) {
                        Text(insight.title,color=Mint,fontSize=12.sp,fontWeight=FontWeight.Medium)
                        Text(insight.explanation,color=Muted,fontSize=11.sp)
                    }
                }
                if(state.issueHistory.isNotEmpty()) {
                    Text("This session",Modifier.padding(top=10.dp),color=Blue,fontWeight=FontWeight.SemiBold)
                    state.issueHistory.takeLast(5).reversed().forEach { transition ->
                        Text("${if(transition.resolved) "✓ Resolved" else "Applied fix"}: ${transition.issue.title}",
                            color=Muted,fontSize=11.sp)
                    }
                }
                if(state.intelligenceMode==ExplanationMode.ADVANCED)
                    Text("Analysis ${report.durationMillis} ms · ${DiagnosticRuleRegistry.count} rule groups",
                        color=Muted,fontSize=10.sp)
            }
        }
    }
    pendingFix?.let { fix -> AlertDialog(onDismissRequest={pendingFix=null},
        title={ Text("Apply suggested fix?") },
        text={ Text("${fix.label}\n\n${fix.explanation}\n\nYou can undo this change.") },
        confirmButton={ TextButton(onClick={model.applySmartFix(fix);pendingFix=null}) { Text("Apply") } },
        dismissButton={ TextButton(onClick={pendingFix=null}) { Text("Cancel") } }) }
}
