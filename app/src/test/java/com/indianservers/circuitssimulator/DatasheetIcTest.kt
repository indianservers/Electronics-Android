package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DatasheetIcTest {
    private fun p(k:Kind,r:String,vararg values:Pair<String,Double>)=
        PlacedComponent(kind=k,reference=r,x=0f,y=0f).let { it.copy(parameters=it.parameters+values) }
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))
    private fun voltage(f:TransientFrame,u:PlacedComponent,pin:Int)=f.nodeVoltages.getValue(TerminalRef(u.id,pin))
    private fun run(c:Circuit,duration:Double,step:Double):TransientResult=
        TransientSolver().simulate(c,duration,step).also { assertNull(it.error,it.error);assertTrue(it.frames.isNotEmpty()) }

    @Test fun physicalPackagesMatchManufacturerPinTables() {
        val tables=mapOf(
            Kind.TIMER_555 to listOf("GND","TRIG","OUT","RESET","CONT","THRES","DISCH","VCC"),
            Kind.LM358 to listOf("OUT1","IN1−","IN1+","V−","IN2+","IN2−","OUT2","V+"),
            Kind.L293D to listOf("EN12","IN1","OUT1","GND","GND","OUT2","IN2","VM","EN34","IN3","OUT3","GND","GND","OUT4","IN4","VLOGIC"))
        tables.forEach { (kind,names) ->
            val u=p(kind,"U1");assertEquals(names.size,u.terminalCount)
            names.indices.forEach { assertEquals(names[it],ComponentPins.name(u,it));assertEquals(it+1,ComponentPins.packageNumber(u,it)) }
        }
        assertTrue(ComponentRegistry.validate().toString(),ComponentRegistry.validate().isEmpty())
    }

    @Test fun timerComparatorLatchControlAndResetPriority() {
        val v=doubleArrayOf(0.0,1.0,0.0,5.0,10.0/3,2.0,0.0,5.0)
        assertTrue(timerLatch(v,false))
        v[1]=2.0;assertTrue(timerLatch(v,true));assertFalse(timerLatch(v,false))
        v[5]=4.0;assertFalse(timerLatch(v,true))
        v[1]=1.0;assertTrue(timerLatch(v,false)) // simultaneous trigger/threshold: trigger wins
        v[3]=0.0;assertFalse(timerLatch(v,true))
        v[3]=5.0;v[4]=1.5;assertFalse(timerLatch(v,true)) // external CONT changes both thresholds
        v[7]=3.3;assertFalse(timerLatch(v,true))
    }

    @Test fun astablePeriodAndDutyFollowExternalRcAndScaleWithCapacitance() {
        fun measure(c:Circuit):Pair<Double,Double> {
            val u=c.components.first { it.kind==Kind.TIMER_555 }
            val frames=run(c,.8,.0002).frames
            val rises=frames.zipWithNext().filter { voltage(it.first,u,2)<2 && voltage(it.second,u,2)>=2 }.map { it.second.timeSeconds }
            val falls=frames.zipWithNext().filter { voltage(it.first,u,2)>=2 && voltage(it.second,u,2)<2 }.map { it.second.timeSeconds }
            assertTrue(rises.size>=3)
            val start=rises[1];val stop=rises[2]
            val fall=falls.first { it>start }
            return stop-start to fall-start
        }
        val c=DatasheetIcExamples.astable()
        val (period,high)=measure(c)
        // TI astable equations: tH=ln(2)*(RA+RB)*C, tL=ln(2)*RB*C.
        assertEquals(ln(2.0)*210000*1e-6,period,.002)
        assertEquals(ln(2.0)*110000*1e-6,high,.002)
        val larger=c.copy(components=c.components.map { if(it.reference=="C1") it.copy(parameters=it.parameters+("capacitance" to 2e-6)) else it })
        assertEquals(2*period,measure(larger).first,.003)
    }

    @Test fun monostablePulseIsOnePointOneRcAndDoesNotRetriggerWithHighInput() {
        val c=DatasheetIcExamples.monostable();val u=c.components.first { it.kind==Kind.TIMER_555 }
        val frames=run(c,.3,.0001).frames
        val high=frames.filter { voltage(it,u,2)>2 }
        assertTrue(high.isNotEmpty())
        assertEquals(ln(3.0)*100000*1e-6,high.last().timeSeconds-high.first().timeSeconds,.002)
        assertTrue(frames.filter { it.timeSeconds>.13 }.all { voltage(it,u,2)<.1 })
    }

    @Test fun resetDischargesExternalTimingCapacitor() {
        val c=DatasheetIcExamples.astable();val u=c.components.first { it.kind==Kind.TIMER_555 }
        val g=c.components.first { it.kind==Kind.GROUND }
        val reset=c.copy(wires=c.wires.filter { it.start!=TerminalRef(u.id,3) && it.end!=TerminalRef(u.id,3) }+w(u,3,g,0))
        val frames=run(reset,.02,.0001).frames
        assertTrue(frames.all { voltage(it,u,2)<.01 })
        assertTrue(voltage(frames.last(),u,6)<.02)
    }

    @Test fun timerOutputAndDischargeFitElectricalTablePoints() {
        val u=p(Kind.TIMER_555,"U1")
        val v=doubleArrayOf(0.0,5.0,.1,5.0,10.0/3,0.0,.15,5.0)
        val low=datasheetIcCurrents(u,v,false)
        assertEquals(.005,low[2],1e-9)
        assertEquals(.008,low[6],1e-9)
        v[2]=3.3
        val high=datasheetIcCurrents(u,v,true)
        assertEquals(-.100,high[2],1e-9)
        assertEquals(0.0,high.sum(),1e-12)
        v[7]=15.0;v[3]=15.0;v[4]=10.0;v[2]=2.0
        assertEquals(.100,datasheetIcCurrents(u,v,false)[2],1e-9)
    }

    private fun follower(input:Double=2.0,load:Double=10000.0):Circuit {
        val u=p(Kind.LM358,"U1","inputOffset" to 0.0,"inputBias" to 0.0)
        val v=p(Kind.SOURCE,"V1","voltage" to 5.0);val signal=p(Kind.SOURCE,"V2","voltage" to input)
        val r=p(Kind.RESISTOR,"R1","resistance" to load,"rating" to 10.0);val g=p(Kind.GROUND,"GND1")
        return Circuit("Follower",listOf(u,v,signal,r,g),listOf(w(v,1,g,0),w(signal,1,g,0),w(v,0,u,7),w(u,3,g,0),
            w(signal,0,u,2),w(u,0,u,1),w(u,0,r,0),w(r,1,g,0),w(u,4,g,0),w(u,5,u,6)))
    }

    @Test fun lm358FeedbackGainOffsetAndBothChannels() {
        val c=DatasheetIcExamples.amplifier();val u=c.components.first { it.kind==Kind.LM358 }
        val dc=DcSolver().solve(c);assertNull(dc.error,dc.error)
        assertEquals(2.03,dc.nodeVoltages.getValue(TerminalRef(u.id,0)),.015)
        assertEquals(.003,dc.nodeVoltages.getValue(TerminalRef(u.id,6)),.004)
        val zero=c.copy(components=c.components.map { if(it.id==u.id) it.copy(parameters=it.parameters+("inputOffset" to 0.0)+("inputBias" to 0.0)) else it })
        assertEquals(2.0,DcSolver().solve(zero).nodeVoltages.getValue(TerminalRef(u.id,0)),.002)
    }

    @Test fun lm358SlewLimitAndSettlingArePhysicalRatherThanInstantaneous() {
        val c=follower();val u=c.components.first { it.kind==Kind.LM358 }
        val frames=run(c,30e-6,.1e-6).frames
        assertTrue(voltage(frames.first(),u,0)<.04)
        frames.zipWithNext().forEach { (a,b) ->
            assertTrue(abs(voltage(b,u,0)-voltage(a,u,0))<300000*(b.timeSeconds-a.timeSeconds)+.0001)
        }
        assertEquals(2.0,voltage(frames.last(),u,0),.003)
    }

    @Test fun lm358ClosedLoopBandwidthRollsOffWithFrequency() {
        fun gain(frequency:Double):Double {
            val base=follower()
            val c=base.copy(components=base.components.map { if(it.reference=="V2")
                p(Kind.FUNCTION_GENERATOR,"V2","offset" to 2.0,"amplitude" to .01,"frequency" to frequency).copy(id=it.id) else it })
            val u=c.components.first { it.kind==Kind.LM358 }
            val frames=run(c,12/frequency,1/(frequency*80)).frames.filter { it.timeSeconds>8/frequency }
            val values=frames.map { voltage(it,u,0) }
            return (values.max()-values.min())/.02
        }
        assertTrue(gain(20000.0)>.95)
        val high=gain(700000.0)
        assertTrue("Gain at nominal GBW: $high",high in .60.. .78)
    }

    @Test fun lm358CommonModeSupplyAndOutputCurrentLimits() {
        val invalid=follower(4.5)
        val u=invalid.components.first { it.kind==Kind.LM358 }
        val dc=DcSolver().solve(invalid);assertNull(dc.error,dc.error)
        assertEquals(Health.WARNING,dc.readings.getValue(u.id).health)
        assertTrue(dc.nodeVoltages.getValue(TerminalRef(u.id,0))<.01)
        val short=follower(2.0,10.0);val shortResult=DcSolver().solve(short);assertNull(shortResult.error,shortResult.error)
        val resistor=short.components.first { it.kind==Kind.RESISTOR }
        assertEquals(.030,shortResult.readings.getValue(resistor.id).current,.0002)
    }

    @Test fun lm358OutputSwingAndBiasFollowLoadAndSourceResistance() {
        val base=DatasheetIcExamples.amplifier()
        val u=base.components.first { it.kind==Kind.LM358 }
        val clipped=base.copy(components=base.components.map { if(it.kind==Kind.FUNCTION_GENERATOR)
            it.copy(parameters=it.parameters+("offset" to .4)+("amplitude" to 0.0)) else it })
        val dc=DcSolver().solve(clipped);assertNull(dc.error,dc.error)
        assertTrue(dc.nodeVoltages.getValue(TerminalRef(u.id,0)) in 3.45..3.5)
        val follower=follower()
        val chip=follower.components.first { it.kind==Kind.LM358 }
        val signal=follower.components.first { it.reference=="V2" }
        val sourceR=p(Kind.RESISTOR,"RS","resistance" to 100000.0)
        val biased=follower.copy(components=follower.components.map { if(it.id==chip.id)
            it.copy(parameters=it.parameters+("inputBias" to 20e-9)) else it }+sourceR,
            wires=follower.wires.filter { it.end!=TerminalRef(chip.id,2) }+
                listOf(w(signal,0,sourceR,0),w(sourceR,1,chip,2)))
        val result=DcSolver().solve(biased);assertNull(result.error,result.error)
        assertEquals(2.002,result.nodeVoltages.getValue(TerminalRef(chip.id,0)),.0001)
    }

    private fun bridge(a:Double=5.0,b:Double=0.0,en:Double=5.0,load:Double=20.0):Circuit {
        val u=p(Kind.L293D,"U1");val v=p(Kind.SOURCE,"V1","voltage" to 9.0)
        val logic=p(Kind.SOURCE,"V2","voltage" to 5.0);val g=p(Kind.GROUND,"GND1")
        val r=p(Kind.RESISTOR,"R1","resistance" to load,"rating" to 100.0)
        val controls=listOf(p(Kind.SOURCE,"VA","voltage" to a),p(Kind.SOURCE,"VB","voltage" to b),p(Kind.SOURCE,"VE","voltage" to en))
        return Circuit("Bridge",listOf(u,v,logic,g,r)+controls,listOf(w(v,1,g,0),w(logic,1,g,0),w(u,3,g,0),w(v,0,u,7),w(logic,0,u,15),
            w(u,2,r,0),w(u,5,r,1),w(u,8,g,0),w(u,9,g,0),w(u,14,g,0))+
            controls.mapIndexed { i,control -> w(control,0,u,listOf(1,6,0)[i]) }+controls.map { w(it,1,g,0) })
    }

    @Test fun l293dForwardReverseBrakeCoastAndUndefinedLogic() {
        fun current(a:Double,b:Double,en:Double):Pair<Double,Health> {
            val c=bridge(a,b,en);val dc=DcSolver().solve(c);assertNull(dc.error,dc.error)
            return dc.readings.getValue(c.components.first { it.reference=="R1" }.id).current to dc.readings.getValue(c.components.first { it.kind==Kind.L293D }.id).health
        }
        val forward=current(5.0,0.0,5.0).first
        assertEquals((9-.8)/(20+3),forward,.001)
        assertEquals(-forward,current(0.0,5.0,5.0).first,.001)
        assertEquals(0.0,current(0.0,0.0,5.0).first,.00001)
        assertEquals(0.0,current(5.0,5.0,5.0).first,.00001)
        assertEquals(0.0,current(5.0,0.0,0.0).first,.00001)
        assertEquals(Health.WARNING,current(5.0,0.0,1.9).second)
    }

    @Test fun l293dSaturationAndFlybackMatchDatasheetTestPointsAndConserveCurrent() {
        val u=p(Kind.L293D,"U1")
        val v=DoubleArray(16);v[7]=9.0;v[15]=5.0;v[0]=5.0;v[1]=5.0
        v[2]=7.6;v[5]=1.2
        val amps=datasheetIcCurrents(u,v)
        assertEquals(-.6,amps[2],1e-6);assertEquals(.6,amps[5],1e-6)
        assertEquals(0.0,amps.sum(),1e-12)
        v[0]=0.0;v[2]=10.3;v[5]= -1.3
        val clamp=datasheetIcCurrents(u,v)
        assertEquals(.6,clamp[2],1e-6);assertEquals(-.6,clamp[5],1e-6)
        assertEquals(0.0,clamp.sum(),1e-12)
    }

    @Test fun l293dSupplyPowerAccountsForLoadAndWarnsOnOverload() {
        val c=bridge(load=5.0);val dc=DcSolver().solve(c);assertNull(dc.error,dc.error)
        val u=c.components.first { it.kind==Kind.L293D }
        assertEquals(Health.OVERLOAD,dc.readings.getValue(u.id).health)
        val supplied=-c.components.filter { it.kind==Kind.SOURCE }.sumOf { dc.readings.getValue(it.id).power * sign(dc.readings.getValue(it.id).current) }
        val dissipated=dc.readings.getValue(u.id).power+dc.readings.getValue(c.components.first { it.kind==Kind.RESISTOR }.id).power
        assertEquals(supplied,dissipated,1e-5)
    }

    @Test fun motorSamplePwmRecirculatesInductorCurrentAndDrivesMechanicalLoad() {
        val c=DatasheetIcExamples.motorDriver();val u=c.components.first { it.kind==Kind.L293D }
        val m=c.components.first { it.kind==Kind.DC_MOTOR }
        val result=run(c,.04,.00005)
        assertTrue(result.checkpoint!!.motorSpeeds.getValue(m.id)>0)
        assertTrue(result.frames.any { voltage(it,u,0)<1 && it.readings.getValue(m.id).current>.001 })
        assertTrue(result.frames.all { voltage(it,u,2)> -1.5 && voltage(it,u,2)<10.5 })
    }

    @Test fun checkpointsPreserveTimerLatchAndBothAmplifierPoleStates() {
        listOf(DatasheetIcExamples.astable() to .05,DatasheetIcExamples.amplifier() to .0001).forEach { (c,duration) ->
            val step=duration/100
            val whole=run(c,duration*2,step)
            val first=run(c,duration,step)
            val next=TransientSolver().simulate(c,duration,step,checkpoint=first.checkpoint)
            assertNull(next.error,next.error)
            whole.frames.last().nodeVoltages.forEach { (pin,value) -> assertEquals(value,next.frames.last().nodeVoltages.getValue(pin),1e-6) }
        }
    }

    @Test fun samplesRoundTripAndLegacyTimerCannotBeSilentlyRewired() {
        listOf(DatasheetIcExamples.astable(),DatasheetIcExamples.monostable(),DatasheetIcExamples.amplifier(),DatasheetIcExamples.motorDriver()).forEach { c ->
            val saved=CircuitJson.encode(c);val loaded=CircuitJson.decode(saved)
            assertEquals(c.components.size,loaded.components.size);assertEquals(c.wires,loaded.wires)
        }
        val legacy=CircuitJson.encode(DatasheetIcExamples.astable()).replace("\"schemaVersion\":7","\"schemaVersion\":6")
        try { CircuitJson.decode(legacy);fail("Legacy 555 must not reinterpret old terminals") }
        catch(e:IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("legacy three-pin 555")) }
    }
}
