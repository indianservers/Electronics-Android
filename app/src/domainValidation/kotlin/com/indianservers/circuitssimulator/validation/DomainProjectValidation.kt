package com.indianservers.circuitssimulator.validation
import com.indianservers.circuitssimulator.validation.CountedChecks.check
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import kotlin.math.abs
object DomainProjectValidation {
    private fun change(c:Circuit,id:String,key:String,v:Double)=ChemistryProjectValidation.changed(c,id,key,v)
    fun circuit(kind:Kind,number:Int)=if(number==10)PowerMonitoringProjects.circuit(kind) else DomainMonitoringProjects.circuit(kind,number)
    fun run(kind:Kind,number:Int):List<String> {
        val original=circuit(kind,number);val c=CircuitJson.decode(CircuitJson.encode(original));check(c==original)
        val rows=mutableListOf<String>();val gas=number==7 || number==9
        fun settle(s:FirmwareSession,name:String) {
            val target=when(number){
                10 -> if(name=="normal-solved-shunts")2 else 1
                12 -> when(name){"acceleration-excursion","negative-full-scale","positive-full-scale"->6;"rotation-fusion"->3;else->1}
                19 -> if(name in listOf("normal","sinusoidal-vibration"))10 else 1
                else -> 1
            }
            var steps=0
            while((s.variables()["cycles"]?.toDouble() ?: 0.0)<target) {
                val step=s.advance(10_000)
                check(step.error==null){"$name ${step.error}"};check(step.frames.all {it.result.error==null}){"$name solver"}
                if(number!=10) {
                    val d=s.board.definition;val bus=d.hardware.i2cPins!!
                    step.frames.forEach {frame->listOf(bus.first,bus.second).forEach {pin->
                        val voltage=frame.result.nodeVoltages[TerminalRef("B1",d.index(pin))] ?: 0.0
                        check(voltage<=3.6001){"$name sensor I2C exceeds 3.6 V: $voltage"}
                        if(voltage in 0.0..0.7)check(voltage/100.0<=.003001){"$name I2C LOW sink exceeds 3 mA: ${voltage/100}"}
                    }}
                }
                check(++steps<1000){"$name did not finish $target cycles"}
            }
            check(s.advance(5000).error==null)
        }
        fun verify(name:String,circuit:Circuit,faults:Int,alarm:Int,extra:(FirmwareSession)->Unit = {}):FirmwareSession {
            val b=FirmwareBoard(circuit,"B1");val s=FirmwareSession(ArduinoSubset.compile(circuit.firmware.single().source,b),b)
            settle(s,name)

            val v=s.variables();fun n(key:String)=v.getValue(key).toDouble()
            check(n("cycles")>=1){"$name no loop"};check(n("faults")==faults.toDouble()){"$name faults ${n("faults")} expected $faults; $v"}
            check(n("alarm")==alarm.toDouble()){"$name alarm ${n("alarm")} expected $alarm"}
            val current=abs(b.latest!!.readings.getValue("ALARM").current)
            check(if(alarm==0)current<1e-6 else current>1e-4){"$name LED $current"}
            check(s.fabric.displayText()["TELEMETRY"].orEmpty().contains(faults.toString())){"$name UART"}
            extra(s);rows+="$name,PASSED,${s.timeMicros}";return s
        }
        if(number==10) {
            verify("normal-solved-shunts",c,0,0) {s->repeat(10){i->
                val v=s.variables();fun n(k:String)=v.getValue(k).toDouble()
                val volts=if(i<5)18.0 else 12.0;val amps=volts/100.1
                check(abs(n("amps$i")-amps)<.00011){"channel $i current ${n("amps$i")} != $amps"}
                check(abs(n("volts$i")-amps*100)<.0041)
                check(abs(n("watts$i")-amps*amps*100)<.005)
                check(n("charge$i")<100 && n("soc$i")<50)
                check(s.board.latest!!.readings.getValue("LOAD$i").power<c.components.first {it.id=="LOAD$i"}.value("rating")){"channel $i load resistor rating exceeded"}
            }}
            repeat(10){i->verify("independent-load-$i",change(c,"LOAD$i","resistance",39.0),0,1)}
            val missingCalibration=c.copy(firmware=c.firmware.map {it.copy(source=it.source.replace("Wire.write(16);Wire.write(0);","Wire.write(0);Wire.write(0);"))})
            verify("missing-calibration",missingCalibration,10,1)
            verify("under-voltage",change(c,"INPUT0","voltage",8.0),0,1)
            verify("over-voltage",change(c,"INPUT0","voltage",24.0),0,1)
            verify("sense-outside-common-mode",change(c,"INPUT0","voltage",28.0),1,1)
            verify("logic-power-loss",change(c,"VPOWER","voltage",1.0),10,1)
            verify("disconnected-shunt-monitor",c.copy(wires=c.wires.filterNot {it.end==TerminalRef("S0",2)}),1,1)
            var collision=c;repeat(10){i->collision=change(collision,"S$i","address",64.0)}
            verify("address-collision",collision,10,1)
        } else {
            verify("normal",c,0,0){s->repeat(8){i->check(s.variables().getValue("t$i").toDouble()==25+i*.5)}
                if(gas){check(s.variables().getValue("co2").toDouble()==800.0);check(abs(s.variables().getValue("co").toDouble())<2){"CO ADC ${s.variables().getValue("co")}; ${s.board.latest!!.nodeVoltages.filterKeys {it.componentId in listOf("ANALOG","RDIV0","RDIV1")}}"}}
                if(number==20)check(abs(s.variables().getValue("depth").toDouble()-2.0/9.80665)<.01)
            }
            repeat(8){i->verify("independent-thermal-$i",change(c,"T$i","temperatureC",95.0),0,1)}
            if(gas) {
                verify("co2-excursion",change(c,"GAS","co2Ppm",5000.0),0,1)
                verify("co-excursion",change(c,"ANALOG","coPpm",100.0),0,1){s->check(abs(s.variables().getValue("co").toDouble()-100)<2)}
                verify("co2-invalid",change(c,"GAS","co2Ppm",Double.NaN),1,1)
                verify("analog-supply-loss",change(c,"V5","voltage",0.0),0,1)
            } else {
                verify("acceleration-excursion",change(c,"ACC","accelXG",1.0),0,1)
                if(number==12)verify("rotation-fusion",change(c,"IMU","gyroXDps",100.0),0,1){s->check(abs(s.variables().getValue("roll").toDouble())>10)}
                if(number==19)verify("sinusoidal-vibration",change(c,"ACC","vibrationAmplitudeG",1.0),0,1){s->check(s.variables().getValue("rms").toDouble()>.2)}
                if(number==19)verify("second-vibration-channel",change(c,"IMU","accelXG",1.0),0,1){s->check(s.variables().getValue("rms").toDouble()>.2)}
                if(number==20)verify("hydrostatic-flood",change(c,"ANALOG","pressureKpa",8.0),0,1){s->check(abs(s.variables().getValue("depth").toDouble()-8.0/9.80665)<.01)}
            }
            verify("invalid-motion-input",change(c,"ACC","accelXG",Double.NaN),1,1)
            if(!gas){
                verify("negative-full-scale",change(c,"ACC","accelXG",-16.0),0,1)
                verify("positive-full-scale",change(c,"ACC","accelXG",16.0),0,1)
            }
            verify("sensor-supply-loss",change(c,"VDOMAIN","voltage",1.0),10,1)
            verify("disconnected-acceleration",c.copy(wires=c.wires.filterNot {it.end==TerminalRef("ACC",2)}),1,1)
        }
        val saved=verify("save-reload",CircuitJson.decode(CircuitJson.encode(c)),0,0)
        saved.reset(saved.timeMicros);settle(saved,"reset")
        check(saved.variables().getValue("faults").toDouble()==0.0);rows+="reset,PASSED,${saved.timeMicros}"
        return rows
    }
}
