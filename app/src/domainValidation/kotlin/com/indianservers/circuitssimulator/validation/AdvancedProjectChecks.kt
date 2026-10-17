package com.indianservers.circuitssimulator.validation
import com.indianservers.circuitssimulator.validation.CountedChecks.check

import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import kotlin.math.floor
import kotlin.math.abs

/** The firmware runtime, electrical solver, real serial routing and production serializer are exercised together. */
object AdvancedProjectChecks {
    data class Evidence(val scenario:String,val checks:Int,val simulatedMicros:Long,
        val expected:Map<String,Double> = emptyMap(),val observed:Map<String,Double> = emptyMap())
    private fun changed(c:Circuit,id:String,key:String,value:Double)=c.copy(components=c.components.map {
        if(it.id==id)it.copy(parameters=it.parameters+(key to value)) else it
    })
    private fun allTemps(c:Circuit,value:(Int)->Double):Circuit {
        var result=c;repeat(8) { result=changed(result,"T$it","temperatureC",value(it)) };return result
    }
    private fun start(c:Circuit):FirmwareSession {
        val b=FirmwareBoard(c,"B1")
        val session=FirmwareSession(ArduinoSubset.compile(c.firmware.single().source,b),b)
        repeat(3) { advance(session,10_000_000) }
        advance(session,20_000)
        return session
    }
    private fun advance(s:FirmwareSession,time:Long) {
        val result=s.advance(time)
        check(result.error==null) { "Firmware at ${s.timeMicros}: ${result.error}" }
        check(result.frames.all { it.result.error==null && it.result.nodeVoltages.values.all(Double::isFinite) })
    }
    fun run(p:AdvancedProjects.Project):List<Evidence> {
        val encoded=CircuitJson.encode(p.circuit)
        val reloaded=CircuitJson.decode(encoded)
        check(p.circuit==reloaded) { "Production circuit serialization changed project" }
        check(reloaded.components.count { it.kind==Kind.I2C_TEMP_SENSOR || it.kind==Kind.PIR_SENSOR }==10)
        val evidence=mutableListOf<Evidence>()
        fun verify(name:String,c:Circuit,faults:Int=0,temperatures:List<Double>?=null,
            motion:List<Int> = listOf(0,0),reuse:FirmwareSession?=null):FirmwareSession {
            val s=if(reuse==null) start(c) else reuse.also { it.board.updateCircuit(c);advance(it,1_020_000) }
            val v=s.variables()
            fun value(key:String)=v.getValue(key).toDouble()
            check(value("cycles")>=1) { "$name: no firmware loop" }
            check(value("faults")==faults.toDouble()) { "$name: faults ${value("faults")} expected $faults" }
            var checks=2
            temperatures?.forEachIndexed { i,t ->
                val expected=floor(t*2+.5)/2 // TI SNIS153D signed nine-bit, 0.5 C LSB.
                check(value("t$i")==expected) { "$name / sensor $i: ${value("t$i")} != $expected" };checks++
            }
            if(faults==8) repeat(8) { i ->
                check(value("ack$i")!=0.0 || value("count$i")!=2.0) { "$name: T$i failed to report its bus error" };checks++
            }
            motion.forEachIndexed { i,m -> check(value("motion$i")==m.toDouble()) { "$name / PIR$i" };checks++ }
            val excursions=temperatures?.count { floor(it*2+.5)/2 !in p.scenario.low..p.scenario.high } ?: 0
            val alarm=if(faults>0 || excursions>=p.scenario.quorum || motion.any { it==1 })1 else 0
            check(value("alarm")==alarm.toDouble()) { "$name: decision output" };checks++
            val led=s.board.latest!!.readings.getValue("ALARM")
            check(if(alarm==1)abs(led.current)>1e-4 else abs(led.current)<1e-6) { "$name: LED current ${led.current}" };checks++
            check(s.fabric.log.any { it.kind==BusKind.I2C }) { "$name: no I2C transactions" };checks++
            check(s.fabric.displayText()["TELEMETRY"].orEmpty().contains(faults.toString())) { "$name: UART telemetry did not arrive" };checks++
            listOf("VLOGIC","V5").forEach { supply ->
                check(abs(s.board.latest!!.readings.getValue(supply).current)<.1) { "$name: external supply budget exceeded" };checks++
            }
            val expected=mutableMapOf("faults" to faults.toDouble(),"alarm" to alarm.toDouble())
            motion.forEachIndexed {i,m->expected["motion$i"]=m.toDouble()}
            temperatures?.forEachIndexed {i,t->expected["t$i"]=floor(t*2+.5)/2}
            evidence+=Evidence(name,checks,s.timeMicros,expected,expected.keys.associateWith {value(it)})
            return s
        }
        val base=reloaded
        val live=verify("normal",base,temperatures=p.normalTemperatures)
        verify("minimum",allTemps(base){-55.0},temperatures=List(8){-55.0})
        verify("maximum",allTemps(base){125.0},temperatures=List(8){125.0})
        verify("out-of-range",allTemps(base){126.0},faults=8)
        val sensorIds=(0..7).map { "T$it" }.toSet()
        val disconnected=base.copy(wires=base.wires.filterNot { it.end.componentId in sensorIds && it.end.index==2 })
        verify("disconnected",disconnected,faults=8)
        verify("wrong-supply",changed(base,"VLOGIC","voltage",2.0),faults=8)
        var collision=base;repeat(8) { collision=changed(collision,"T$it","address",72.0) }
        verify("communication-collision",collision,faults=8)
        val unavailable=changed(base,"VLOGIC","voltage",0.0)
        val delayed=verify("delayed-availability",unavailable,faults=8)
        verify("delayed-recovery",base,temperatures=p.normalTemperatures,reuse=delayed)
        verify("invalid-reading",allTemps(base){Double.NaN},faults=8)
        var simultaneous=allTemps(base){p.scenario.high+it+.5}
        repeat(2) { simultaneous=changed(simultaneous,"P$it","motion",1.0) }
        verify("simultaneous",simultaneous,temperatures=List(8){p.scenario.high+it+.5},motion=listOf(1,1))
        // Each physical device independently affects firmware variables/diagnostics.
        repeat(8) { i ->
            val isolated=changed(base,"T$i","temperatureC",p.scenario.high+5)
            verify("individual-T$i",isolated,temperatures=p.normalTemperatures.mapIndexed { j,t -> if(i==j)p.scenario.high+5 else t })
        }
        repeat(2) { i ->
            verify("individual-P$i",changed(base,"P$i","motion",1.0),temperatures=p.normalTemperatures,
                motion=List(2) { if(it==i)1 else 0 })
        }
        verify("pir-supply-loss",changed(base,"V5","voltage",2.0),temperatures=p.normalTemperatures)
        var warming=base
        repeat(2) { i ->
            warming=changed(warming,"P$i","motion",1.0)
            warming=changed(warming,"P$i","warmupMs",60000.0)
        }
        val warm=verify("pir-warmup-delay",warming,temperatures=p.normalTemperatures)
        repeat(30) { advance(warm,1_000_000) }
        verify("pir-warmup-complete",warming,temperatures=p.normalTemperatures,motion=listOf(1,1),reuse=warm)
        live.reset();repeat(3) { advance(live,10_000_000) };advance(live,20_000)
        check(live.variables().getValue("faults").toDouble()==0.0)
        evidence+=Evidence("stop-reset-restart",1,live.timeMicros)
        verify("save-reload-rerun",CircuitJson.decode(CircuitJson.encode(base)),temperatures=p.normalTemperatures)
        return evidence
    }
}
