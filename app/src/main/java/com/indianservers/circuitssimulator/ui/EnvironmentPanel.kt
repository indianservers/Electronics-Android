package com.indianservers.circuitssimulator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.canvas.Mint
import com.indianservers.circuitssimulator.ui.canvas.Muted
import com.indianservers.circuitssimulator.ui.canvas.TextIce

@Composable
fun EnvironmentPanel(state:SimulatorState,model:SimulatorViewModel,modifier:Modifier=Modifier) {
    val kinds=state.circuit.components.map { it.kind }.toSet()
    val showTemp=kinds.any { it in setOf(Kind.LM35_SENSOR,Kind.THERMISTOR,Kind.PTC_THERMISTOR,Kind.I2C_TEMP_SENSOR) }
    val showLight=kinds.any { it in setOf(Kind.LDR,Kind.PHOTOTRANSISTOR,Kind.PHOTODIODE) }
    val showDistance=kinds.contains(Kind.ULTRASONIC)
    val showMotion=kinds.contains(Kind.PIR_SENSOR)
    if(!showTemp && !showLight && !showDistance && !showMotion) return
    val env=state.circuit.environment
    Surface(modifier,color=Color(0xFF102033).copy(alpha=.94f),shape=RoundedCornerShape(12.dp),
        border=BorderStroke(1.dp,Color(0xFF294159))) {
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Environment",color=TextIce,fontSize=12.sp)
            if(showTemp) {
                Text("Temperature ${"%.1f".format(env.temperatureC)} °C",color=Mint,fontSize=11.sp)
                Slider((env.temperatureC.toFloat()+40f)/190f,{
                    model.setEnvironment(env.copy(temperatureC=it*190.0-40.0))
                })
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    FilterChip(false,{model.setEnvironment(EnvironmentPresets.apply(env,EnvironmentPreset.COLD))},label={Text("Cold")})
                    FilterChip(false,{model.setEnvironment(EnvironmentPresets.apply(env,EnvironmentPreset.ROOM))},label={Text("Room")})
                    FilterChip(false,{model.setEnvironment(EnvironmentPresets.apply(env,EnvironmentPreset.HOT))},label={Text("Hot")})
                }
            }
            if(showLight) {
                Text("Light ${"%.0f".format(env.illuminanceLux)} lx",color=Mint,fontSize=11.sp)
                Slider((env.illuminanceLux.toFloat()/20000f).coerceIn(0f,1f),{
                    model.setEnvironment(env.copy(illuminanceLux=it*20000.0))
                })
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    FilterChip(false,{model.setEnvironment(EnvironmentPresets.apply(env,EnvironmentPreset.DARK))},label={Text("Dark")})
                    FilterChip(false,{model.setEnvironment(EnvironmentPresets.apply(env,EnvironmentPreset.INDOOR))},label={Text("Indoor")})
                    FilterChip(false,{model.setEnvironment(EnvironmentPresets.apply(env,EnvironmentPreset.SUNLIGHT))},label={Text("Sun")})
                }
            }
            if(showDistance) {
                Text("Distance ${"%.0f".format(env.distanceCm)} cm",color=Mint,fontSize=11.sp)
                Slider(((env.distanceCm.toFloat()-1f)/399f).coerceIn(0f,1f),{
                    model.setEnvironment(env.copy(distanceCm=1.0+it*399.0))
                })
            }
            if(showMotion) {
                FilterChip(env.motion,{model.setEnvironment(env.copy(motion=!env.motion))},
                    label={Text(if(env.motion) "Motion detected" else "Still")})
            }
        }
    }
}
