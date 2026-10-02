package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.BoardRegistry
import com.indianservers.circuitssimulator.domain.isBoard
import com.indianservers.circuitssimulator.ui.canvas.Mint
import com.indianservers.circuitssimulator.ui.canvas.Muted
import com.indianservers.circuitssimulator.ui.canvas.TextIce

@Composable
fun LogicAnalyzerSheet(state:SimulatorState,model:SimulatorViewModel) {
    val board=state.circuit.components.firstOrNull { it.kind.isBoard }
    val definition=board?.let { BoardRegistry.boards[it.kind] }
    val frames=state.transient?.frames.orEmpty().takeLast(200)
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().heightIn(max=520.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Logic analyzer",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                TextButton(onClick={model.showLogicAnalyzer(false)}) { Text("Close") }
            }
            Text("Digital traces come from firmware/solver frames. UART/I²C/SPI decode uses the event bus log.",
                color=Muted,fontSize=11.sp)
            Column(Modifier.height(160.dp).verticalScroll(rememberScrollState())) {
                definition?.pins?.take(16)?.forEach { pin ->
                    val highs=frames.count { frame ->
                        val terminal=com.indianservers.circuitssimulator.domain.TerminalRef(board!!.id,definition.index(pin.name))
                        (frame.nodeVoltages[terminal] ?: 0.0)>board.kind.let { BoardRegistry.boards.getValue(it).logicVoltage*.7 }
                    }
                    Text("${pin.name}  ${if(highs==0) "LOW" else if(highs==frames.size) "HIGH" else "toggling $highs/${frames.size}"}",
                        color=Mint,fontFamily=FontFamily.Monospace,fontSize=11.sp)
                }
            }
            Text("Protocol log",color=TextIce,fontSize=13.sp)
            Column(Modifier.height(180.dp).verticalScroll(rememberScrollState())) {
                if(state.firmware.busLog.isEmpty()) Text("No UART/I²C/SPI events yet.",color=Muted)
                state.firmware.busLog.forEach { line ->
                    Text(line,color=CodeGreen,fontFamily=FontFamily.Monospace,fontSize=10.sp)
                }
            }
            Text("Decoder uses event timestamps in simulation microseconds, not physical bit periods.",
                color=Muted,fontSize=10.sp)
        }
    }
}

private val CodeGreen=Color(0xFF7DE0A2)
