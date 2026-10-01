package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.EngineeringUnits
import com.indianservers.circuitssimulator.simulation.*
import com.indianservers.circuitssimulator.ui.canvas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MultimeterSheet(state:SimulatorState,model:SimulatorViewModel) {
    val reading by produceState<MeterResult?>(null,state.circuit,state.meterMode,
        state.meterPlus,state.meterCommon,state.meterCurrentWireId,
        if(state.meterMode==MeterMode.DC_VOLTAGE || state.meterMode==MeterMode.DC_CURRENT)
            state.elapsedSeconds else 0.0) {
        value=withContext(Dispatchers.Default) {
            val meter=Multimeter()
            when(state.meterMode) {
                MeterMode.DC_CURRENT -> state.meterCurrentWireId?.let { meter.current(state.circuit,it,state.elapsedSeconds) }
                MeterMode.DC_VOLTAGE -> if(state.meterPlus!=null && state.meterCommon!=null)
                    meter.voltage(state.circuit,state.meterPlus,state.meterCommon,state.elapsedSeconds) else null
                MeterMode.RESISTANCE -> if(state.meterPlus!=null && state.meterCommon!=null)
                    meter.resistance(state.circuit,state.meterPlus,state.meterCommon) else null
                MeterMode.CONTINUITY -> if(state.meterPlus!=null && state.meterCommon!=null)
                    meter.continuity(state.circuit,state.meterPlus,state.meterCommon) else null
            }
        }
    }
    Surface(color=Color(0xFF102033),shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.fillMaxWidth().height(245.dp).padding(14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(4.dp)
                .background(Muted,RoundedCornerShape(4.dp)))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Multimeter",Modifier.weight(1f),color=TextIce,fontSize=17.sp)
                Text("✕",Modifier.clickable { model.showMeter(false) }.padding(7.dp),color=Muted)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(MeterMode.DC_VOLTAGE to "DC V",MeterMode.DC_CURRENT to "DC A",
                    MeterMode.RESISTANCE to "Ω",MeterMode.CONTINUITY to "Continuity").forEach { (mode,label) ->
                    FilterChip(selected=state.meterMode==mode,onClick={model.setMeterMode(mode)},
                        label={Text(label,fontSize=11.sp)})
                }
            }
            Surface(Modifier.fillMaxWidth().height(66.dp),color=Panel,
                shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,Color(0xFF294159))) {
                Box(contentAlignment=Alignment.Center) {
                    val value=reading?.value
                    val display=when {
                        value==null -> "Attach probes"
                        state.meterMode==MeterMode.CONTINUITY -> reading?.unit ?: "—"
                        value.isInfinite() -> "Open circuit"
                        else -> EngineeringUnits.format(value,reading?.unit ?: "")
                    }
                    Text(display,color=Mint,fontSize=25.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(if(state.meterMode==MeterMode.DC_CURRENT) "Tap a wire, then A, to insert the meter in series."
                else "Tap a wire, then V+ or COM, to attach each probe.",color=Muted,fontSize=11.sp)
            fun netName(terminal:com.indianservers.circuitssimulator.domain.TerminalRef?):String?=
                terminal?.let { probe -> state.circuit.wires.firstOrNull { it.label!=null &&
                    (it.start==probe || it.end==probe) }?.label }
            if(state.meterPlus!=null || state.meterCommon!=null)
                Text("V+ ${netName(state.meterPlus) ?: "—"}   COM ${netName(state.meterCommon) ?: "—"}",
                    color=Muted,fontSize=10.sp)
            reading?.message?.let { Text(it,color=Color(0xFFFFB25E),fontSize=11.sp) }
        }
    }
}
