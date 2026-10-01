package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.indianservers.circuitssimulator.simulation.CircuitDiagnostics
import com.indianservers.circuitssimulator.simulation.DiagnosticLevel
import com.indianservers.circuitssimulator.ui.canvas.*

@Composable
fun DiagnosticsSheet(state:SimulatorState,model:SimulatorViewModel) {
    val issues=remember(state.circuit,state.result) { CircuitDiagnostics.inspect(state.circuit,state.result) }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=380.dp).padding(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Circuit diagnostics",Modifier.weight(1f),color=TextIce,fontSize=18.sp)
                Text("✕",Modifier.semantics { contentDescription="Close diagnostics";role=Role.Button }
                    .clickable { model.showDiagnostics(false) }.padding(8.dp),color=Muted)
            }
            Text("${issues.count { it.level==DiagnosticLevel.ERROR }} errors · "+
                "${issues.count { it.level==DiagnosticLevel.WARNING }} warnings · "+
                "${issues.count { it.level==DiagnosticLevel.INFO }} notes",
                color=Muted,fontSize=12.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if(issues.isEmpty()) Text("No issues detected by the available checks.",color=Mint,fontSize=13.sp)
                issues.forEach { issue ->
                    val tint=when(issue.level) {
                        DiagnosticLevel.ERROR -> Color(0xFFFF7777)
                        DiagnosticLevel.WARNING -> Color(0xFFFFB25E)
                        DiagnosticLevel.INFO -> Blue
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical=4.dp)
                        .background(Panel,RoundedCornerShape(9.dp))
                        .semantics {
                            contentDescription="${issue.level.name}: ${issue.message}"+
                                if(issue.componentId!=null) " Double tap to inspect component." else ""
                            if(issue.componentId!=null) role=Role.Button
                        }
                        .clickable(enabled=issue.componentId!=null) {
                            issue.componentId?.let(model::openDiagnosticComponent)
                        }.padding(10.dp)) {
                        Text(issue.level.name,color=tint,fontSize=10.sp)
                        Text(issue.message,color=TextIce,fontSize=12.sp)
                    }
                }
            }
        }
    }
}
