package com.indianservers.circuitssimulator.validation
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.*
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.*

object PhaseTwoParkingValidation {
    fun run(base:Circuit,onProgress:(JSONArray)->Unit = {}):JSONArray {
        val rows=JSONArray()
        fun change(c:Circuit,id:String,key:String,value:Double)=c.copy(components=c.components.map {if(it.id==id)it.copy(parameters=it.parameters+(key to value))else it})
        fun cut(c:Circuit,id:String,pin:Int)=c.copy(wires=c.wires.filter {it.start!=TerminalRef(id,pin) && it.end!=TerminalRef(id,pin)})
        fun verify(name:String,c:Circuit=base,faults:Int=0,validTemps:Set<Int> = (0..7).toSet(),validEcho:Set<Int> = setOf(0,1),
            reuse:FirmwareSession?=null,uart:Boolean=true):FirmwareSession {
            val row=JSONObject().put("scenario",name).put("status","RUNNING");rows.put(row);val a=JSONArray()
            fun checkValue(key:String,actual:Double,want:Double,tolerance:Double=0.0){
                val ok=actual.isFinite() && abs(actual-want)<=tolerance
                a.put(JSONObject().put("quantity",key).put("observed",if(actual.isFinite())actual else actual.toString()).put("expected",want).put("tolerance",tolerance).put("passed",ok))
                check(ok){"$name $key observed=$actual expected=$want tolerance=$tolerance"}
            }
            try {
                val b=reuse?.board ?: FirmwareBoard(c,"B1");val s=reuse ?: FirmwareSession(ArduinoSubset.compile(c.firmware.single().source,b),b)
                if(reuse!=null)b.updateCircuit(c)
                val target=(s.variables()["cycles"]?.toDouble() ?: 0.0)+1
                var steps=0
                while((s.variables()["cycles"]?.toDouble() ?: 0.0)<target){
                    val r=s.advance(10000);checkValue("interpreterError",if(r.error==null)0.0 else 1.0,0.0)
                    checkValue("solverErrors",r.frames.count {it.result.error!=null}.toDouble(),0.0)
                    checkValue("boundedSamplingSteps",if(++steps<1000)1.0 else 0.0,1.0)
                }
                checkValue("postCycleError",if(s.advance(5000).error==null)0.0 else 1.0,0.0)
                val v=s.variables();fun n(k:String)=v.getValue(k).toDouble()
                row.put("observedVariables",JSONObject(v)).put("physicalUart",s.fabric.displayText()["TELEMETRY"].orEmpty())
                    .put("lastNodeVoltages",JSONObject(b.latest!!.nodeVoltages.entries.associate {it.key.toString() to it.value}))
                    .put("protocolTail",JSONArray(s.fabric.log.toList().takeLast(24).map {it.toString()}))
                validTemps.forEach {i->checkValue("t$i",n("t$i"),floor(c.components.first {it.id=="T$i"}.value("temperatureC")*2+.5)/2)}
                // 58 us/cm educational echo; pulseIn polls at 20 us. Two edge
                // sampling intervals plus <=1 us truncation bound numerical error.
                validEcho.forEach {i->checkValue("distance$i",n("distance$i"),floor(c.components.first {it.id=="U$i"}.value("distanceCm")*58)/58,41.0/58+1e-6)}
                val occupied=validEcho.count {c.components.first {p->p.id=="U$it"}.value("distanceCm")<100}
                val hot=validTemps.any {c.components.first {p->p.id=="T$it"}.value("temperatureC")>70}
                val alarm=if(faults>0 || occupied==2 || hot)1 else 0
                checkValue("faults",n("faults"),faults.toDouble());checkValue("occupied",n("occupied"),occupied.toDouble());checkValue("alarm",n("alarm"),alarm.toDouble())
                checkValue("solvedStatusLed",if(abs(b.latest!!.readings.getValue("ALARM").current)>1e-4)1.0 else 0.0,alarm.toDouble())
                if(name=="external-rail-load-overload"){
                    val voltage=c.components.first {it.id=="VDOMAIN"}.value("voltage")
                    checkValue("externalLoadPowerW",b.latest!!.readings.getValue("OVERLOAD").power,voltage*voltage,.00001)
                    checkValue("overloadDiagnostic",if(b.latest!!.readings.getValue("OVERLOAD").health==com.indianservers.circuitssimulator.simulation.Health.OVERLOAD)1.0 else 0.0,1.0)
                }
                row.put("physicalUart",s.fabric.displayText()["TELEMETRY"].orEmpty()).put("protocolTail",JSONArray(s.fabric.log.toList().takeLast(20).map {it.toString()}))
                checkValue("physicalUart",if(s.fabric.displayText()["TELEMETRY"].orEmpty().contains("$occupied:$faults"))1.0 else 0.0,if(uart)1.0 else 0.0)
                if(faults>0 && validEcho.size<2)checkValue("timeoutReachedPulseIn",(0..1).count {n("duration$it")==0.0}.toDouble(),(2-validEcho.size).toDouble())
                row.put("status","PASSED_MODELED_SCOPE").put("assertions",a).put("simulatedMicros",s.timeMicros);onProgress(rows);return s
            }catch(e:Throwable){row.put("status","FAILED_VERIFICATION").put("assertions",a).put("error",e.stackTraceToString());onProgress(rows);throw e}
        }
        verify("normal")
        repeat(8){i->verify("independent-temperature-$i",change(base,"T$i","temperatureC",80.0))}
        repeat(2){i->verify("independent-bay-$i",change(base,"U$i","distanceCm",50.0))}
        var minimum=base;var maximum=base
        repeat(8){i->minimum=change(minimum,"T$i","temperatureC",-55.0);maximum=change(maximum,"T$i","temperatureC",125.0)}
        repeat(2){i->minimum=change(minimum,"U$i","distanceCm",2.0);maximum=change(maximum,"U$i","distanceCm",400.0)}
        verify("minimum-inputs",minimum);verify("maximum-inputs",maximum)
        verify("invalid-local-distance",change(base,"U0","distanceCm",Double.NaN),1,validEcho=setOf(1))
        verify("outside-sonar-range",change(base,"U0","distanceCm",401.0),1,validEcho=setOf(1))
        verify("missing-sonar-ground",cut(base,"U0",1),1,validEcho=setOf(1))
        verify("trigger-disconnection",cut(base,"U0",2),1,validEcho=setOf(1))
        verify("echo-disconnection",cut(base,"U0",3),1,validEcho=setOf(1))
        verify("sonar-power-interruption",change(base,"V5","voltage",0.0),2,validEcho=emptySet())
        verify("sonar-reverse-power",change(base,"V5","voltage",-5.0),2,validEcho=emptySet())
        verify("thermal-power-interruption",change(base,"VDOMAIN","voltage",0.0),8,validTemps=emptySet())
        verify("address-collision",change(base,"T1","address",72.0),2,validTemps=(2..7).toSet())
        verify("invalid-temperature",change(base,"T0","temperatureC",126.0),1,validTemps=(1..7).toSet())
        val wrong=base.copy(firmware=base.firmware.map {it.copy(source=it.source.replace(",HIGH);delayMicroseconds(10);",",LOW);delayMicroseconds(10);"))})
        verify("incorrect-trigger-polarity",wrong,2,validEcho=emptySet())
        verify("uart-baud-mismatch",change(base,"TELEMETRY","baud",57600.0),uart=false)
        val load=PlacedComponent(id="OVERLOAD",kind=Kind.RESISTOR,reference="R_OVERLOAD",x=200f,y=1500f,parameters=mapOf("resistance" to 1.0,"rating" to .25))
        verify("external-rail-load-overload",base.copy(components=base.components+load,wires=base.wires+listOf(
            Wire(start=TerminalRef("VDOMAIN",0),end=TerminalRef(load.id,0)),Wire(start=TerminalRef(load.id,1),end=TerminalRef("G1",0)))))
        verify("simultaneous-bay-and-thermal-changes",change(change(change(base,"U0","distanceCm",40.0),"U1","distanceCm",60.0),"T3","temperatureC",90.0))
        verify("mixed-electrical-faults",cut(change(base,"VDOMAIN","voltage",0.0),"U0",2),9,validTemps=emptySet(),validEcho=setOf(1))
        verify("save-reload-rerun",CircuitJson.decode(CircuitJson.encode(base)))
        val live=verify("before-runtime-failure");verify("sensor-failure-during-operation",change(base,"U0","distanceCm",Double.NaN),1,validEcho=setOf(1),reuse=live)
        verify("restored-sensor-recovery",base,reuse=live);live.reset(live.timeMicros);verify("mcu-reset",base,reuse=live)
        live.stop();verify("firmware-restart")
        return rows
    }
}
