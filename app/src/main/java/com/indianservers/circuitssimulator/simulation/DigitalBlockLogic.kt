package com.indianservers.circuitssimulator.simulation

import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.simulation.digital.LogicState

internal fun evaluateCustomLogic(kind:Kind,states:List<LogicState>,outputPin:Int):LogicState {
    val s=states.map { if(it==LogicState.HIGH_Z) LogicState.UNKNOWN else it }
    fun known(vararg values:LogicState)=values.all { it==LogicState.HIGH || it==LogicState.LOW }
    return when(kind) {
        Kind.TRI_STATE_BUFFER -> when(s[1]) {
            LogicState.LOW -> LogicState.HIGH_Z
            LogicState.HIGH -> s[0]
            else -> LogicState.UNKNOWN
        }
        Kind.MULTIPLEXER_2 -> when(s[2]) {
            LogicState.LOW -> s[0]; LogicState.HIGH -> s[1]
            else -> if(s[0]==s[1]) s[0] else LogicState.UNKNOWN
        }
        Kind.DEMULTIPLEXER_2 -> if(known(s[0],s[1])) {
            if((s[1]==LogicState.LOW && outputPin==2) ||
                (s[1]==LogicState.HIGH && outputPin==3)) s[0] else LogicState.LOW
        } else LogicState.UNKNOWN
        Kind.ENCODER_4 -> {
            if(s.any { it==LogicState.UNKNOWN }) LogicState.UNKNOWN else {
                val highest=(3 downTo 0).firstOrNull { s[it]==LogicState.HIGH } ?: 0
                if(highest and (1 shl (outputPin-4))!=0) LogicState.HIGH else LogicState.LOW
            }
        }
        Kind.DECODER_2 -> if(!known(s[0],s[1])) LogicState.UNKNOWN else {
            val code=(if(s[0]==LogicState.HIGH) 1 else 0)+
                (if(s[1]==LogicState.HIGH) 2 else 0)
            if(code==outputPin-2) LogicState.HIGH else LogicState.LOW
        }
        else -> error("Unsupported digital block $kind")
    }
}
