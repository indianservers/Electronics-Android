package com.indianservers.circuitssimulator.validation
import com.indianservers.circuitssimulator.validation.CountedChecks.check

import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import kotlin.math.abs

object ChemistryProjectValidation {
    fun changed(c:Circuit,id:String,key:String,value:Double)=c.copy(components=c.components.map {
        if(it.id==id)it.copy(parameters=it.parameters+(key to value)) else it })
    fun run(kind:Kind,number:Int):List<String> {
        val original=ChemistryProjects.circuit(kind,number)
        val c=CircuitJson.decode(CircuitJson.encode(original));check(c==original)
        check(c.components.count { it.kind in ChemistrySensorParts.profiles }==10)
        val probes=ChemistryProjects.probes(number);val rows=mutableListOf<String>()
        fun verify(name:String,circuit:Circuit,expected:List<Double>?,faults:Int,alarm:Int):FirmwareSession {
            val b=FirmwareBoard(circuit,"B1");val s=FirmwareSession(ArduinoSubset.compile(circuit.firmware.single().source,b),b)
            var steps=0
            while((s.variables()["cycles"]?.toDouble() ?: 0.0)<1) {
                val step=s.advance(10_000);check(step.error==null){"$name: ${step.error}"};check(step.frames.all {it.result.error==null})
                check(++steps<200){"$name conversion/loop timeout"}
            }
            check(s.advance(5000).error==null)
            val variables=s.variables()
            fun v(k:String)=variables.getValue(k).toDouble()
            check(v("cycles")>=1){"$name: no loop"}
            check(v("faults")==faults.toDouble()){"$name: faults ${v("faults")} expected $faults"}
            check(v("alarm")==alarm.toDouble()){"$name: alarm ${v("alarm")}"}
            expected?.forEachIndexed { i,value -> check(abs(v("reading$i")-value)<.00001){"$name S$i ${v("reading$i")} expected $value"} }
            val current=abs(b.latest!!.readings.getValue("ALARM").current)
            check(if(alarm==0)current<1e-6 else current>1e-4){"$name LED current $current"}
            check(s.fabric.displayText()["TELEMETRY"].orEmpty().contains(faults.toString())){"$name UART"}
            rows+="$name,PASSED,${s.timeMicros}"
            return s
        }
        val normal=probes.map { it.normal }
        verify("normal",c,normal,0,0)
        probes.forEachIndexed { i,p ->
            val high=p.high+if(p.kind==Kind.EZO_PH).1 else 1.0
            verify("independent-S$i",changed(c,"S$i",p.profile.key,high),normal.mapIndexed { j,v -> if(i==j)high else v },0,1)
        }
        var minimum=c;var maximum=c;var invalid=c;var collision=c
        probes.forEachIndexed { i,p ->
            minimum=changed(minimum,"S$i",p.profile.key,p.profile.minimum)
            maximum=changed(maximum,"S$i",p.profile.key,p.profile.maximum)
            invalid=changed(invalid,"S$i",p.profile.key,Double.NaN)
            collision=changed(collision,"S$i","address",32.0)
        }
        verify("minimum",minimum,probes.map { it.profile.minimum },0,1)
        verify("maximum",maximum,probes.map { it.profile.maximum },0,1)
        verify("nonfinite-injection",invalid,null,10,1)
        verify("address-collision",collision,null,10,1)
        verify("undervoltage",changed(c,"VCHEM","voltage",3.0),null,10,1)
        verify("overvoltage",changed(c,"VCHEM","voltage",6.0),null,10,1)
        verify("disconnected",c.copy(wires=c.wires.filterNot { it.end==TerminalRef("S0",2) }),null,1,1)
        val restored=verify("save-reload",CircuitJson.decode(CircuitJson.encode(c)),normal,0,0)
        restored.reset(restored.timeMicros)
        var resetSteps=0
        while((restored.variables()["cycles"]?.toDouble() ?: 0.0)<1){check(restored.advance(10000).error==null);check(++resetSteps<200)}
        check(restored.advance(5000).error==null)
        check(restored.variables().getValue("faults").toDouble()==0.0);rows+="reset,PASSED,${restored.timeMicros}"
        return rows
    }
}
