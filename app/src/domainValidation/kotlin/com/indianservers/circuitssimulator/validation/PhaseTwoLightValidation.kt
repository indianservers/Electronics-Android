package com.indianservers.circuitssimulator.validation

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Expected quantities derive from literal datasheet equations, never from model readbacks. */
object PhaseTwoLightValidation {
    fun run(original:Circuit,number:Int,onProgress:(JSONArray)->Unit = {}):JSONArray {
        val records=JSONArray()
        val climate=number in setOf(3,8)
        fun changed(c:Circuit,id:String,key:String,value:Double)=c.copy(components=c.components.map {if(it.id==id)it.copy(parameters=it.parameters+(key to value))else it})
        fun cut(c:Circuit,id:String,pin:Int)=c.copy(wires=c.wires.filter {it.start!=TerminalRef(id,pin) && it.end!=TerminalRef(id,pin)})
        fun expected(c:Circuit):Map<String,Double> = buildMap {
            repeat(7){i->put("t$i",floor(c.components.first {it.id=="T$i"}.value("temperatureC")*2+.5)/2)}
            repeat(2){i->put("lux$i",floor((c.components.first {it.id=="L$i"}.value("illuminanceLux")*1.2).coerceIn(0.0,65535.0)+.5)/1.2)}
            // DATA_FORMAT=0x08 selects full-resolution at +/-2 g: signed 10 bits,
            // not the 13-bit +/-16 g range (ADXL345 Rev.G Table 1, page 3).
            if(!climate)put("ax",floor(c.components.first {it.id=="ACC"}.value("accelXG")/.0039+.5).coerceIn(-512.0,511.0)*.0039)
            else {
                val gas=c.components.first {it.id=="GAS"}
                put("co2",floor(gas.value("co2Ppm")+.5))
                put("airTemperature",-45.0+175.0*floor((gas.value("temperatureC")+45.0)*65535.0/175.0+.5)/65535.0)
                put("humidity",100.0*floor(gas.value("humidityPercent")*65535.0/100.0+.5)/65535.0)
            }
        }
        fun policy(v:Map<String,Double>,faults:Int):Int {
            if(faults>0)return 1
            val t=(0..6).map {v.getValue("t$it")};val x=abs(v["ax"] ?: 0.0);val l0=v.getValue("lux0");val l1=v.getValue("lux1")
            return when(number){
                1->if(t.any {it<0 || it>60} || x>.5 || l0>20000 || l1>20000)1 else 0
                3->if(t.any {it<15 || it>35} || v.getValue("humidity")<35 || v.getValue("humidity")>80 || v.getValue("co2")>2500 || l0>50000 || l1>50000)1 else 0
                8->if(t.any {it<20 || it>30} || v.getValue("humidity")<30 || v.getValue("humidity")>65 || v.getValue("co2")>1000 || l0<100 || l1<100)1 else 0
                16->if(l0<50 && l1<50 && t.none {it>70} && x<=1)1 else 0
                18->if(t.any {it<2 || it>8} || x>.5 || l0>50 || l1>50)1 else 0
                else->error("Unknown policy")
            }
        }
        fun scenario(name:String,c:Circuit=original,faults:Int=0,valid:Set<String> = expected(original).keys,
            existing:FirmwareSession?=null,runUntilCycle:Int=1,overrides:Map<String,Double> = emptyMap(),uartDelivered:Boolean=true):FirmwareSession {
            val row=JSONObject().put("scenario",name).put("status","RUNNING");val assertions=JSONArray();records.put(row)
            fun assertion(label:String,actual:Double,want:Double,tolerance:Double=0.0){
                val pass=actual.isFinite() && abs(actual-want)<=tolerance
                assertions.put(JSONObject().put("quantity",label).put("expected",want).put("observed",if(actual.isFinite())actual else actual.toString()).put("tolerance",tolerance).put("passed",pass))
                check(pass){"$name $label observed=$actual expected=$want tolerance=$tolerance"}
            }
            try {
                val board=existing?.board ?: FirmwareBoard(c,"B1")
                val session=existing ?: FirmwareSession(ArduinoSubset.compile(c.firmware.single().source,board),board)
                var busMaximum=Double.NEGATIVE_INFINITY;var busMinimum=Double.POSITIVE_INFINITY;var sinkMaximum=0.0
                fun observe(frames:List<FirmwareFrame>){
                    val route=board.definition.hardware.i2cPins!!
                    frames.forEach {f->
                        val ground=f.result.nodeVoltages[TerminalRef("B1",board.definition.pins.indexOfFirst {it.type==PinType.GROUND})] ?: 0.0
                        listOf(route.first,route.second).forEach {pin->
                            val voltage=(f.result.nodeVoltages[TerminalRef("B1",board.definition.index(pin))] ?: 0.0)-ground
                            busMaximum=max(busMaximum,voltage);busMinimum=min(busMinimum,voltage)
                            if(voltage in 0.0..0.7)sinkMaximum=max(sinkMaximum,voltage/100.0)
                        }
                    }
                }
                if(existing!=null)board.updateCircuit(c)
                if(climate && session.variables()["cycles"]==null && !name.startsWith("conversion-not-ready")){
                    val boot=session.advance(4_800_000)
                    observe(boot.frames)
                    assertion("startupInterpreterError",if(boot.error==null)0.0 else 1.0,0.0)
                    assertion("startupSolverErrors",boot.frames.count {it.result.error!=null}.toDouble(),0.0)
                }
                var steps=0
                while((session.variables()["cycles"]?.toDouble() ?: 0.0)<runUntilCycle){
                    val step=session.advance(10000)
                    observe(step.frames)
                    assertion("interpreterError",if(step.error==null)0.0 else 1.0,0.0)
                    assertion("solverErrors",step.frames.count {it.result.error!=null}.toDouble(),0.0)
                    assertion("boundedSamplingSteps",if(++steps<2000)1.0 else 0.0,1.0)
                }
                session.advance(5000).let {assertion("postCycleError",if(it.error==null)0.0 else 1.0,0.0)}
                val v=session.variables();val expect=expected(c)+overrides
                row.put("i2cMaximumVoltageV",busMaximum).put("i2cMinimumVoltageV",busMinimum).put("modeledOpenDrainPeakSinkA",sinkMaximum)
                assertion("i2cMaximumWithin3.6V",if(busMaximum<=3.600001)1.0 else 0.0,1.0)
                assertion("i2cMinimumAboveMinus0.3V",if(busMinimum>=-.300001)1.0 else 0.0,1.0)
                assertion("i2cModeledLowSinkWithin3mA",if(sinkMaximum<=.003000001)1.0 else 0.0,1.0)
                valid.forEach {key->assertion(key,v.getValue(key).toDouble(),expect.getValue(key),if(key=="ax").00000001 else .0000001)}
                assertion("faults",v.getValue("faults").toDouble(),faults.toDouble())
                val output=policy(expect,faults);assertion("policyOutput",v.getValue("alarm").toDouble(),output.toDouble())
                val current=abs(board.latest!!.readings.getValue("ALARM").current)
                assertion("solvedLedState",if(current>1e-4)1.0 else 0.0,output.toDouble())
                val uart=session.fabric.displayText()["TELEMETRY"].orEmpty()
                row.put("physicalUart",uart).put("interpreterConsole",JSONArray(session.consoleLines()))
                    .put("protocolTail",JSONArray(session.fabric.log.toList().takeLast(24).map {it.toString()}))
                assertion("physicalUartContainsFaultCount",if(uart.contains(":$faults"))1.0 else 0.0,if(uartDelivered)1.0 else 0.0)
                if(name=="external-rail-load-overload"){
                    val supply=c.components.first {it.id=="VDOMAIN"}.value("voltage")
                    assertion("externalLoadPowerW",board.latest!!.readings.getValue("OVERLOAD").power,supply*supply,.00001)
                    assertion("overloadDiagnostic",if(board.latest!!.readings.getValue("OVERLOAD").health==com.indianservers.circuitssimulator.simulation.Health.OVERLOAD)1.0 else 0.0,1.0)
                }
                val nacks=session.fabric.log.count {it.ack==false}
                if(faults>0)assertion("faultReachedNackOrEmptyRead",if(nacks>0)1.0 else 0.0,1.0)
                val rail=board.latest!!.nodeVoltages[TerminalRef("L0",0)]!!-board.latest!!.nodeVoltages[TerminalRef("L0",1)]!!
                row.put("sensorRailV",rail).put("nackEvents",nacks).put("simulatedMicros",session.timeMicros)
                    .put("assertions",assertions).put("protocolTail",JSONArray(session.fabric.log.toList().takeLast(12).map {it.toString()})).put("status","PASSED_MODELED_SCOPE")
                onProgress(records);return session
            } catch(e:Throwable){row.put("status","FAILED_VERIFICATION").put("assertions",assertions).put("error",e.stackTraceToString());onProgress(records);throw e}
        }
        val keys=expected(original).keys
        scenario("normal")
        repeat(7){i->scenario("independent-temperature-$i",changed(original,"T$i","temperatureC",if(number==18)7.5 else 45.0))}
        repeat(2){i->scenario("independent-illuminance-$i",changed(original,"L$i","illuminanceLux",1234.0+i*100))}
        if(!climate)scenario("independent-acceleration",changed(original,"ACC","accelXG",.195))
        else {
            scenario("independent-co2",changed(original,"GAS","co2Ppm",3000.0))
            scenario("independent-humidity",changed(original,"GAS","humidityPercent",90.0))
            scenario("independent-air-temperature",changed(original,"GAS","temperatureC",15.0))
            scenario("invalid-co2-data",changed(original,"GAS","co2Ppm",Double.NaN),1,keys-setOf("co2","humidity","airTemperature"))
        }
        var minimum=original;var maximum=original
        repeat(7){i->minimum=changed(minimum,"T$i","temperatureC",-55.0);maximum=changed(maximum,"T$i","temperatureC",125.0)}
        repeat(2){i->minimum=changed(minimum,"L$i","illuminanceLux",0.0);maximum=changed(maximum,"L$i","illuminanceLux",100000.0)}
        if(!climate){minimum=changed(minimum,"ACC","accelXG",-2.0);maximum=changed(maximum,"ACC","accelXG",2.0)}
        else {
            minimum=changed(changed(changed(minimum,"GAS","co2Ppm",0.0),"GAS","temperatureC",-10.0),"GAS","humidityPercent",0.0)
            maximum=changed(changed(changed(maximum,"GAS","co2Ppm",40000.0),"GAS","temperatureC",60.0),"GAS","humidityPercent",95.0)
        }
        scenario("minimum-readings",minimum);scenario("maximum-readings-and-light-saturation",maximum)
        scenario("invalid-light-input",changed(original,"L0","illuminanceLux",Double.NaN),1,keys-"lux0")
        scenario("negative-light-input",changed(original,"L0","illuminanceLux",-1.0),1,keys-"lux0")
        scenario("invalid-temperature-input",changed(original,"T0","temperatureC",126.0),1,keys-"t0")
        scenario("sensor-power-interruption",changed(original,"VDOMAIN","voltage",0.0),10,emptySet())
        scenario("reverse-sensor-supply",changed(original,"VDOMAIN","voltage",-3.3),10,emptySet())
        scenario("missing-ground",cut(original,"L0",1),1,keys-"lux0")
        scenario("sensor-sda-disconnection",cut(original,"L0",2),1,keys-"lux0")
        scenario("address-collision",changed(original,"L1","address",35.0),2,keys-setOf("lux0","lux1"))
        scenario("invalid-address-strap",changed(original,"L0","address",36.0),1,keys-"lux0")
        val wrong=original.copy(firmware=original.firmware.map {it.copy(source=it.source.replace("Wire.write(16);","Wire.write(19);"))})
        scenario("unsupported-resolution-command",wrong,2,keys-setOf("lux0","lux1"))
        val early=original.copy(firmware=original.firmware.map {it.copy(source=it.source.replace("delay(180);","delay(1);").replace("delay(5000);","delay(1);"))})
        scenario("conversion-not-ready-data-register-still-zero",early,if(climate)1 else 0,
            if(climate)keys-setOf("co2","humidity","airTemperature")else keys,overrides=mapOf("lux0" to 0.0,"lux1" to 0.0))
        scenario("uart-framing-mismatch",changed(original,"TELEMETRY","baud",57600.0),uartDelivered=false)
        val load=PlacedComponent(id="OVERLOAD",kind=Kind.RESISTOR,reference="R_OVERLOAD",x=120f,y=1400f,
            parameters=mapOf("resistance" to 1.0,"rating" to .25))
        scenario("external-rail-load-overload",original.copy(components=original.components+load,wires=original.wires+listOf(
            Wire(start=TerminalRef("VDOMAIN",0),end=TerminalRef(load.id,0)),Wire(start=TerminalRef(load.id,1),end=TerminalRef("G1",0)))))
        var simultaneous=changed(original,"T0","temperatureC",80.0);simultaneous=changed(simultaneous,"L1","illuminanceLux",4000.0)
        simultaneous=if(climate)changed(simultaneous,"GAS","humidityPercent",90.0)else changed(simultaneous,"ACC","accelXG",2.0)
        scenario("multiple-simultaneous-inputs",simultaneous)
        scenario("mixed-electrical-faults",cut(changed(original,"L1","address",36.0),"T0",2),2,keys-setOf("lux1","t0"))
        scenario("save-reload-rerun",CircuitJson.decode(CircuitJson.encode(original)))
        val live=scenario("before-live-power-fault")
        val off=changed(original,"VDOMAIN","voltage",0.0)
        scenario("failure-during-operation",off,10,emptySet(),live,2)
        // On repower BH1750 is power-down; restart firmware initializes it again.
        live.board.updateCircuit(original);live.reset(live.timeMicros)
        scenario("power-restoration-and-firmware-restart",original,existing=live)
        live.reset(live.timeMicros);scenario("mcu-reset",original,existing=live)
        live.stop();scenario("fresh-firmware-session",original)
        return records
    }
}
