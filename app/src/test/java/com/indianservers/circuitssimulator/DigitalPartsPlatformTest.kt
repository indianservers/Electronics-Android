package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.CanvasDigitalSimulator
import com.indianservers.circuitssimulator.simulation.digital.LogicState
import org.junit.Assert.*
import org.junit.Test

class DigitalPartsPlatformTest {
    private fun p(k:Kind,r:String)=PlacedComponent(kind=k,reference=r,x=0f,y=0f)
    private fun w(a:PlacedComponent,i:Int,b:PlacedComponent,j:Int)=
        Wire(start=TerminalRef(a.id,i),end=TerminalRef(b.id,j))

    @Test fun newDigitalBlocksProduceExpectedStates() {
        val cases=listOf(
            Triple(Kind.BUFFER_GATE,listOf(1),listOf(LogicState.HIGH)),
            Triple(Kind.TRI_STATE_BUFFER,listOf(1,0),listOf(LogicState.HIGH_Z)),
            Triple(Kind.MULTIPLEXER_2,listOf(0,1,1),listOf(LogicState.HIGH)),
            Triple(Kind.DEMULTIPLEXER_2,listOf(1,1),listOf(LogicState.LOW,LogicState.HIGH)),
            Triple(Kind.ENCODER_4,listOf(0,0,0,1),listOf(LogicState.HIGH,LogicState.HIGH)),
            Triple(Kind.DECODER_2,listOf(1,0),listOf(LogicState.LOW,LogicState.HIGH,
                LogicState.LOW,LogicState.LOW)),
            Triple(Kind.SR_LATCH,listOf(1,0),listOf(LogicState.HIGH)),
            Triple(Kind.D_LATCH,listOf(1,1),listOf(LogicState.HIGH)),
            Triple(Kind.JK_FLIP_FLOP,listOf(1,0),listOf(LogicState.HIGH))
        )
        for((kind,levels,expected) in cases) {
            val device=p(kind,"U1")
            val high=p(Kind.LOGIC_INPUT,"IN1").copy(parameters=mapOf("state" to 1.0))
            val low=p(Kind.LOGIC_INPUT,"IN2").copy(parameters=mapOf("state" to 0.0))
            val ground=p(Kind.GROUND,"G1")
            val clock=if(kind==Kind.JK_FLIP_FLOP) p(Kind.CLOCK,"CLK1") else null
            val wires=levels.mapIndexed { index,level -> w(if(level==1) high else low,0,device,index) }+
                if(clock!=null) listOf(w(clock,0,device,2)) else emptyList()
            val circuit=Circuit(kind.title,listOfNotNull(device,high,low,ground,clock),wires)
            val result=CanvasDigitalSimulator().simulate(circuit,.003,.0002)
            assertNull("$kind: ${result.error}",result.error)
            val states=result.frames.last().digitalStates
            DigitalParts.outputs(kind).forEachIndexed { index,pin ->
                assertEquals("$kind pin $pin",expected[index],states[TerminalRef(device.id,pin)])
            }
        }
    }

    @Test fun logicOutputSensesInputWithoutDrivingIt() {
        val input=p(Kind.LOGIC_INPUT,"IN1").copy(parameters=mapOf("state" to 1.0))
        val output=p(Kind.LOGIC_OUTPUT,"OUT1")
        val ground=p(Kind.GROUND,"G1")
        val circuit=Circuit("Logic IO",listOf(input,output,ground),listOf(w(input,0,output,0)))
        val result=CanvasDigitalSimulator().simulate(circuit,.002,.0002)
        assertNull(result.error)
        assertEquals(LogicState.HIGH,result.frames.last().digitalStates[TerminalRef(output.id,0)])
    }
}
