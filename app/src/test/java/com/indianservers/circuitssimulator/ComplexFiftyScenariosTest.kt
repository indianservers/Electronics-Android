package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.firmware.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.roundToInt

@RunWith(Parameterized::class)
class ComplexFiftyScenariosTest(private val name:String, private val family:Int, private val variant:Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun scenarios():List<Array<Any>> = buildList {
            listOf("Distributed sensor interlock", "UART sensor relay", "Independent I2C networks",
                "Multi-stage logic network", "Mixed I2C SPI monitoring").forEachIndexed { family, label ->
                repeat(10) { add(arrayOf("$label ${it+1}", family, it)) }
            }
        }
    }

    private class Bench(val name:String,val reverseControllers:Boolean=false) {
        val parts=mutableListOf<PlacedComponent>()
        val wires=mutableListOf<Wire>()
        val programs=mutableListOf<FirmwareAttachment>()
        val ground=part(Kind.GROUND,"GND")
        fun part(kind:Kind, ref:String, params:Map<String,Double> = emptyMap()):PlacedComponent =
            PlacedComponent(kind=kind,reference=ref,x=0f,y=0f,parameters=
                ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }+params)
                .also { parts+=it }
        fun pin(p:PlacedComponent,i:Int)=TerminalRef(p.id,i)
        fun pin(p:PlacedComponent,label:String)=TerminalRef(p.id,BoardRegistry.boards.getValue(p.kind).index(label))
        fun wire(a:TerminalRef,b:TerminalRef) { wires+=Wire(start=a,end=b) }
        fun board(ref:String,kind:Kind=Kind.ARDUINO_UNO)=part(kind,ref,mapOf("usbPower" to 1.0)).also {
            wire(pin(it,"GND"),pin(ground,0)) }
        fun code(board:PlacedComponent,source:String) { programs+=FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,source,true) }
        fun pot(board:PlacedComponent,input:String,ref:String,position:Double):PlacedComponent =
            // Model position is measured from the upper segment; application input is VOUT/VS.
            part(Kind.POTENTIOMETER,ref,mapOf("position" to 1.0-position)).also {
                wire(pin(board,if(BoardRegistry.boards.getValue(board.kind).logicVoltage==5.0) "5V" else "3V3"),pin(it,0))
                wire(pin(it,2),pin(ground,0));wire(pin(it,1),pin(board,input)) }
        fun sensor(board:PlacedComponent,input:String,ref:String,kind:Kind,temp:Double):PlacedComponent {
            val p=IotSensorParts.profiles.getValue(kind)
            val sensor=part(kind,ref,mapOf("temperatureC" to temp))
            val supply=if(kind==Kind.AD22103_SENSOR) "3V3" else "5V"
            wire(pin(board,supply),pin(sensor,p.supplyPin));wire(pin(sensor,p.groundPin),pin(ground,0))
            wire(pin(sensor,p.outputPin),pin(board,input));return sensor
        }
        fun led(board:PlacedComponent,output:String):PlacedComponent {
            val resistor=part(Kind.RESISTOR,"RLED",mapOf("resistance" to 470.0))
            val led=part(Kind.LED,"LED")
            wire(pin(board,output),pin(resistor,0));wire(pin(resistor,1),pin(led,0));wire(pin(led,1),pin(ground,0))
            return led
        }
        fun pullups(board:PlacedComponent) {
            repeat(2) { val r=part(Kind.RESISTOR,"PU${parts.size}",mapOf("resistance" to 4700.0))
                wire(pin(board,"3V3"),pin(r,0));wire(pin(r,1),pin(board,if(it==0) "IO21" else "IO22")) }
        }
        fun i2c(board:PlacedComponent,kind:Kind,ref:String,params:Map<String,Double> = emptyMap()) =
            part(kind,ref,params).also {
                wire(pin(board,"3V3"),pin(it,0));wire(pin(ground,0),pin(it,1))
                wire(pin(board,"IO21"),pin(it,2));wire(pin(board,"IO22"),pin(it,3)) }
        fun circuit(env:EnvironmentState=EnvironmentState())=Circuit(name,
            if(reverseControllers) parts.filterNot { it.kind.isBoard }+parts.filter { it.kind.isBoard }.reversed() else parts.toList(),
            wires.toList(),environment=env,firmware=programs.toList())
    }

    private fun advance(runtime:RuntimeScheduler,us:Long=30_000):CombinedAdvance {
        val before=runtime.sessions.values.maxOf { it.timeMicros }
        val result=runtime.advance(us)
        assertNull("$name: ${result.error}",result.error)
        assertEquals("$name scheduler clock",before+us,result.timeMicros)
        assertTrue("$name no frames",result.frames.isNotEmpty())
        result.frames.forEach {
            assertNull("$name solver error",it.result.error)
            assertTrue("$name nonfinite nodes",it.result.nodeVoltages.values.all { voltage -> voltage.isFinite() })
        }
        runtime.sessions.values.forEach { assertEquals("$name controller clock",result.timeMicros,it.timeMicros) }
        return result
    }
    private fun update(runtime:RuntimeScheduler,circuit:Circuit) { runtime.sessions.values.forEach { it.board.updateCircuit(circuit) } }
    private fun variable(runtime:RuntimeScheduler,board:PlacedComponent,key:String)=
        runtime.sessions.getValue(board.id).variables().getValue(key).toDouble()
    private fun led(result:CombinedAdvance,part:PlacedComponent,on:Boolean) {
        val current=result.frames.last().result.readings.getValue(part.id).current
        if(on) assertTrue("$name LED should be on: $current; variables=${result.variables}",current>.001)
        else assertTrue("$name LED should be off: $current",current<1e-5)
    }
    private fun change(circuit:Circuit,values:Map<String,Map<String,Double>>)=circuit.copy(components=circuit.components.map {
        val edits=values[it.id].orEmpty().mapValues { (key,value) ->
            if(it.kind==Kind.POTENTIOMETER && key=="position") 1.0-value else value }
        it.copy(parameters=it.parameters+edits) })
    private fun adc(kind:Kind,temp:Double):Int {
        val p=IotSensorParts.profiles.getValue(kind)
        val stimulus=if(kind==Kind.HIH4030_SENSOR) 50.0*(1.0546-.00216*temp) else temp
        val supply=if(kind==Kind.AD22103_SENSOR)3.3 else 5.0
        return (((p.offset+p.slope*stimulus)*(p.referenceSupply?.let { supply/it } ?: 1.0))/5.0*1023).roundToInt()
    }

    @Test fun complexScenarioHasCorrectOutputsAcrossEditsAndReset() {
        when(family) { 0 -> fusion();1 -> uart();2 -> independentI2c();3 -> logic();4 -> mixed() }
    }

    private fun fusion() {
        val b=Bench(name,variant>=5);val a=b.board("SENSE");val z=b.board("CONTROL")
        val kinds=IotSensorParts.profiles.keys.toList()
        val first=kinds[variant%kinds.size];val second=kinds[(variant+3)%kinds.size]
        val s1=b.sensor(a,"A0","T1",first,25.0);val s2=b.sensor(a,"A1","T2",second,25.0)
        val p1=b.pot(z,"A0","MOISTURE",.2);val p2=b.pot(z,"A1","SETPOINT",.8)
        b.wire(b.pin(a,"D2"),b.pin(z,"D2"));val light=b.led(z,"D13")
        val comparison=if(first==Kind.HIH4030_SENSOR) "<" else ">"
        val comparison2=if(second==Kind.HIH4030_SENSOR) "<" else ">"
        b.code(a,"""void setup(){pinMode(2,OUTPUT);int cycles=0;}
            void loop(){int t1=analogRead(A0);int t2=analogRead(A1);int sum=0;
            for(int i=0;i<4;i=i+1){sum=sum+i;} cycles=cycles+1;
            if(t1 $comparison ${adc(first,35.0)} && t2 $comparison2 ${adc(second,35.0)} && sum==6){digitalWrite(2,HIGH);}
            else{digitalWrite(2,LOW);}delay(5);}""")
        b.code(z,"""void setup(){pinMode(2,INPUT);pinMode(13,OUTPUT);int alarms=0;}
            void loop(){int moisture=analogRead(A0);int target=analogRead(A1);int remote=digitalRead(2);
            if(remote==HIGH){if(moisture<target && !(target<100)){digitalWrite(13,HIGH);alarms=alarms+1;}
            else{digitalWrite(13,LOW);}}else{digitalWrite(13,LOW);}delay(5);}""")
        val base=b.circuit();val runtime=RuntimeScheduler().compile(base)
        led(advance(runtime),light,false)
        assertEquals(adc(first,25.0).toDouble(),variable(runtime,a,"t1"),2.0)
        assertEquals(adc(second,25.0).toDouble(),variable(runtime,a,"t2"),2.0)
        val hot=change(base,mapOf(s1.id to mapOf("temperatureC" to 50.0),s2.id to mapOf("temperatureC" to 50.0)))
        update(runtime,hot);led(advance(runtime),light,true)
        assertEquals(1.0,variable(runtime,z,"remote"),0.0)
        assertEquals(6.0,variable(runtime,a,"sum"),0.0)
        val wet=change(hot,mapOf(p1.id to mapOf("position" to .9),p2.id to mapOf("position" to .1)))
        update(runtime,wet);led(advance(runtime),light,false)
        runtime.resetBoard(a.id);val now=runtime.sessions.getValue(z.id).timeMicros
        assertEquals(now,runtime.sessions.getValue(a.id).timeMicros)
        led(advance(runtime),light,false)
        assertTrue(variable(runtime,z,"alarms")>0)
        runtime.reset();led(advance(runtime),light,false)
    }

    private fun uart() {
        val b=Bench(name,variant>=5);val a=b.board("SENSE")
        val relay=b.board("RELAY",if(variant%3==0)Kind.ARDUINO_NANO else Kind.ARDUINO_UNO)
        val z=if(variant%2==0)b.board("ACTUATOR",Kind.ARDUINO_NANO)else relay
        val s1=b.sensor(a,"A0","TEMP",Kind.TMP36_SENSOR,20.0)
        val s2=b.pot(a,"A1","LIMIT",.8)
        b.wire(b.pin(a,"D1"),b.pin(relay,"D0"))
        if(z!=relay)b.wire(b.pin(relay,"D1"),b.pin(z,"D0"))
        val light=b.led(z,"D13");val baud=listOf(9600,19200,38400,57600,115200)[variant/2]
        val on=32+variant;val off=160+variant
        b.code(a,"""void setup(){Serial.begin($baud);int sent=0;}
            void loop(){int t=analogRead(A0);int limit=analogRead(A1);
            if(t>200 && limit>500){Serial.write($on);}else{Serial.write($off);}sent=sent+1;delay(5);}""")
        val receiver="""void setup(){Serial.begin($baud);pinMode(13,OUTPUT);int packets=0;int last=-1;}
            void loop(){while(Serial.available()>0){int ch=Serial.read();last=ch;
            if(ch==$on){digitalWrite(13,HIGH);}else{if(ch==$off){digitalWrite(13,LOW);}}
            packets=packets+1;}delay(1);}"""
        if(z!=relay)b.code(relay,"""void setup(){Serial.begin($baud);int forwarded=0;}
            void loop(){while(Serial.available()>0){int ch=Serial.read();
            if(ch==$on || ch==$off){Serial.write(ch);forwarded=forwarded+1;}}delay(1);}""")
        b.code(z,receiver)
        val base=b.circuit();val runtime=RuntimeScheduler().compile(base)
        led(advance(runtime,40_000),light,false)
        assertEquals(off.toDouble(),variable(runtime,z,"last"),0.0)
        val hot=change(base,mapOf(s1.id to mapOf("temperatureC" to 80.0)))
        update(runtime,hot);led(advance(runtime,40_000),light,true)
        assertEquals(on.toDouble(),variable(runtime,z,"last"),0.0)
        val inhibit=change(hot,mapOf(s2.id to mapOf("position" to .1)))
        update(runtime,inhibit);led(advance(runtime,40_000),light,false)
        assertEquals(off.toDouble(),variable(runtime,z,"last"),0.0)
        assertTrue(variable(runtime,z,"packets")>=10)
        if(z!=relay)assertTrue(variable(runtime,relay,"forwarded")>=10)
        runtime.resetBoard(a.id);led(advance(runtime,30_000),light,false)
        runtime.reset();led(advance(runtime,40_000),light,false)
        assertTrue(runtime.fabric.log.any { it.kind==BusKind.UART && it.ack==true })
    }

    private fun independentI2c() {
        val b=Bench(name,variant>=5);val boards=listOf(b.board("A",Kind.ESP32_DEVKIT),b.board("B",Kind.ESP32_DEVKIT))
        val memories=mutableListOf<PlacedComponent>();val address=72+variant%2;val memAddress=80+variant%4
        boards.forEachIndexed { index, board ->
            b.pullups(board);b.i2c(board,Kind.I2C_TEMP_SENSOR,"TEMP$index",mapOf("address" to address.toDouble()))
            memories+=b.i2c(board,Kind.I2C_EEPROM,"MEM$index",mapOf("address" to memAddress.toDouble()))
            b.code(board,"""void setup(){Wire.begin();Wire.beginTransmission($memAddress);Wire.write(${variant*8});Wire.write(${40+variant+index*80});int write=Wire.endTransmission();delay(6);}
                void loop(){Wire.beginTransmission($address);Wire.write(0);int ack=Wire.endTransmission(false);
                Wire.requestFrom($address,2);int hi=Wire.read();int lo=Wire.read();int raw=hi*256+lo;
                if(raw>=32768){raw=raw-65536;}double temperature=raw/256.0;
                Wire.beginTransmission($memAddress);Wire.write(${variant*8});Wire.endTransmission(false);
                Wire.requestFrom($memAddress,1);int saved=Wire.read();delay(5);}""")
        }
        val base=b.circuit(EnvironmentState(temperatureC=10.5+variant));val runtime=RuntimeScheduler().compile(base)
        fun check(temp:Double) {
            advance(runtime,25_000)
            boards.forEachIndexed { index,board ->
                assertEquals(0.0,variable(runtime,board,"write"),0.0)
                assertEquals(0.0,variable(runtime,board,"ack"),0.0)
                assertEquals(temp,variable(runtime,board,"temperature"),0.0)
                assertEquals((40+variant+index*80).toDouble(),variable(runtime,board,"saved"),0.0)
                assertEquals(40+variant+index*80,runtime.fabric.i2cDevices.first { it.ownerId==memories[index].id }.memory[variant*8])
            }
            assertFalse(runtime.fabric.log.any { it.summary.contains("Arbitration lost") || it.summary.contains("Address conflict") })
        }
        check(base.environment.temperatureC)
        val cold=base.copy(environment=base.environment.copy(temperatureC=-10.5-variant))
        update(runtime,cold);check(cold.environment.temperatureC)
        runtime.resetBoard(boards[0].id);check(cold.environment.temperatureC)
        runtime.reset();check(cold.environment.temperatureC)
    }

    private fun logic() {
        val b=Bench(name,variant>=5);val a=b.board("A",Kind.ESP32_DEVKIT);val z=b.board("B",Kind.ESP32_DEVKIT)
        val pots=listOf(b.pot(a,"IO34","A",.1),b.pot(a,"IO35","B",.1),b.pot(z,"IO34","C",.1),b.pot(z,"IO35","D",.1))
        val xor=b.part(Kind.XOR_GATE,"XOR");val and=b.part(Kind.AND_GATE,"AND");val not=b.part(Kind.NOT_GATE,"NOT");val or=b.part(Kind.OR_GATE,"OR")
        val mux=b.part(Kind.MULTIPLEXER_2,"MUX");val latch=b.part(Kind.D_FLIP_FLOP,"FF");val light=b.led(z,"IO25")
        b.wire(b.pin(a,"IO16"),b.pin(xor,0));b.wire(b.pin(a,"IO17"),b.pin(xor,1))
        b.wire(b.pin(xor,2),b.pin(and,0));b.wire(b.pin(z,"IO16"),b.pin(and,1))
        b.wire(b.pin(z,"IO17"),b.pin(not,0));b.wire(b.pin(and,2),b.pin(or,0));b.wire(b.pin(not,1),b.pin(or,1))
        b.wire(b.pin(or,2),b.pin(mux,0));b.wire(b.pin(xor,2),b.pin(mux,1));b.wire(b.pin(z,"IO16"),b.pin(mux,2))
        b.wire(b.pin(mux,3),b.pin(z,"IO32"))
        b.wire(b.pin(mux,3),b.pin(latch,0));b.wire(b.pin(a,"IO27"),b.pin(latch,1));b.wire(b.pin(latch,2),b.pin(z,"IO33"))
        b.code(a,"""void setup(){pinMode(16,OUTPUT);pinMode(17,OUTPUT);pinMode(27,OUTPUT);}
            void loop(){int a=analogRead(34);int b=analogRead(35);
            if(a>2048){digitalWrite(16,HIGH);}else{digitalWrite(16,LOW);}
            if(b>2048){digitalWrite(17,HIGH);}else{digitalWrite(17,LOW);}
            digitalWrite(27,LOW);delayMicroseconds(200);digitalWrite(27,HIGH);delay(2);}""")
        b.code(z,"""void setup(){pinMode(16,OUTPUT);pinMode(17,OUTPUT);pinMode(32,INPUT);pinMode(33,INPUT);pinMode(25,OUTPUT);}
            void loop(){int c=analogRead(34);int d=analogRead(35);
            if(c>2048){digitalWrite(16,HIGH);}else{digitalWrite(16,LOW);}
            if(d>2048){digitalWrite(17,HIGH);}else{digitalWrite(17,LOW);}
            delayMicroseconds(200);int result=digitalRead(32);int latched=digitalRead(33);digitalWrite(25,result);delay(2);}""")
        val base=b.circuit();val runtime=RuntimeScheduler().compile(base)
        fun check(bits:Int) {
            val values=pots.mapIndexed { i,p -> p.id to mapOf("position" to if(bits and (1 shl i)!=0).9 else .1) }.toMap()
            update(runtime,change(base,values));val result=advance(runtime,15_000)
            val av=bits and 1!=0;val bv=bits and 2!=0;val cv=bits and 4!=0;val dv=bits and 8!=0
            val expected=if(cv)av.xor(bv) else (av.xor(bv) && cv) || !dv
            assertEquals("$name bits=$bits",if(expected)1.0 else 0.0,variable(runtime,z,"result"),0.0)
            val voltage=result.frames.last().result.nodeVoltages.getValue(b.pin(mux,3))
            assertTrue("$name gate voltage=$voltage",if(expected)voltage>2.5 else voltage<.2)
            assertEquals("$name stored bit=$bits",if(expected)1.0 else 0.0,variable(runtime,z,"latched"),0.0)
            val stored=result.frames.last().result.nodeVoltages.getValue(b.pin(latch,2))
            assertTrue("$name flip-flop voltage=$stored",if(expected)stored>2.5 else stored<.2)
            led(result,light,expected)
        }
        check(variant);check(variant xor 15);check((variant+5)%16)
        runtime.resetBoard(a.id);check(variant xor 7)
        runtime.reset();check(variant)
    }

    private fun mixed() {
        val b=Bench(name,variant>=5);val a=b.board("BUS",Kind.ESP32_DEVKIT);val z=b.board("INTERLOCK",Kind.ESP32_DEVKIT)
        b.pullups(a)
        val kinds=MotionSensorParts.profiles.keys.toList();val motionKind=kinds[variant%4]
        val profile=MotionSensorParts.profiles.getValue(motionKind)
        val stimulus=if(profile.gyro)"gyroXDps" else "accelXG"
        val motion=b.i2c(a,motionKind,"MOTION",mapOf(stimulus to if(profile.gyro)87.5 else .5))
        b.i2c(a,Kind.I2C_TEMP_SENSOR,"TEMP")
        val memory=b.i2c(a,Kind.I2C_EEPROM,"MEM")
        val flash=b.part(Kind.SPI_MEMORY,"FLASH")
        b.wire(b.pin(a,"3V3"),b.pin(flash,0));b.wire(b.pin(b.ground,0),b.pin(flash,1))
        listOf("IO23","IO19","IO18","IO5").forEachIndexed { i,pin -> b.wire(b.pin(a,pin),b.pin(flash,i+2)) }
        val p1=b.pot(z,"IO34","MOISTURE",.2);b.pot(z,"IO35","SETPOINT",.8)
        b.wire(b.pin(z,"IO17"),b.pin(a,"IO17"));val light=b.led(a,"IO16")
        b.code(z,"""void setup(){pinMode(17,OUTPUT);}
            void loop(){int moisture=analogRead(34);int target=analogRead(35);
            if(moisture<target && target>2048){digitalWrite(17,HIGH);}else{digitalWrite(17,LOW);}delay(3);}""")
        val setup=MotionSensorExamples.source(motionKind).substringAfter("Wire.begin();").substringBefore("}\nvoid loop")
        val register=when(motionKind){Kind.L3GD20_GYRO,Kind.LIS3DH_ACCEL -> 0xA8;Kind.LSM6DSOX_IMU -> 0x22;else -> 0x32}
        b.code(a,"""void setup(){pinMode(5,OUTPUT);digitalWrite(5,HIGH);pinMode(16,OUTPUT);pinMode(17,INPUT);
            SPI.begin();digitalWrite(5,LOW);SPI.transfer(159);int manufacturer=SPI.transfer(0);int type=SPI.transfer(0);int capacity=SPI.transfer(0);digitalWrite(5,HIGH);
            Wire.begin();$setup
            Wire.beginTransmission(80);Wire.write(${variant*8});Wire.write(${70+variant});int stored=Wire.endTransmission();delay(6);}
            void loop(){Wire.beginTransmission(${profile.addresses.first()});Wire.write($register);int motionAck=Wire.endTransmission(false);
            Wire.requestFrom(${profile.addresses.first()},2);int low=Wire.read();int high=Wire.read();int raw=high*256+low;if(raw>=32768){raw=raw-65536;}
            Wire.beginTransmission(72);Wire.write(0);Wire.endTransmission(false);Wire.requestFrom(72,2);int hi=Wire.read();int lo=Wire.read();int t=hi*256+lo;if(t>=32768){t=t-65536;}
            Wire.beginTransmission(80);Wire.write(${variant*8});Wire.endTransmission(false);Wire.requestFrom(80,1);int saved=Wire.read();
            int interlock=digitalRead(17);if(raw<0 && t>7680 && interlock==HIGH && saved==${70+variant}){digitalWrite(16,HIGH);}else{digitalWrite(16,LOW);}delay(4);}""")
        val base=b.circuit(EnvironmentState(temperatureC=40.0));val runtime=RuntimeScheduler().compile(base)
        fun check(on:Boolean,sign:Int) {
            val result=advance(runtime,30_000);led(result,light,on)
            assertEquals(239.0,variable(runtime,a,"manufacturer"),0.0)
            assertEquals(64.0,variable(runtime,a,"type"),0.0);assertEquals(22.0,variable(runtime,a,"capacity"),0.0)
            assertEquals(0.0,variable(runtime,a,"motionAck"),0.0);assertEquals(0.0,variable(runtime,a,"stored"),0.0)
            assertTrue(variable(runtime,a,"raw")*sign>0)
            assertEquals(40.0*256,variable(runtime,a,"t"),0.0)
            assertEquals((70+variant).toDouble(),variable(runtime,a,"saved"),0.0)
            assertEquals(70+variant,runtime.fabric.i2cDevices.first { it.ownerId==memory.id }.memory[variant*8])
        }
        check(false,1)
        val negative=change(base,mapOf(motion.id to mapOf(stimulus to if(profile.gyro)-87.5 else -.5)))
        update(runtime,negative);check(true,-1)
        val wet=change(negative,mapOf(p1.id to mapOf("position" to .9)))
        update(runtime,wet);check(false,-1)
        runtime.resetBoard(a.id);check(false,-1)
        runtime.reset();check(false,-1)
    }
}
