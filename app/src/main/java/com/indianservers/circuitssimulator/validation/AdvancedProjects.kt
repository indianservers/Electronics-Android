package com.indianservers.circuitssimulator.validation

import com.indianservers.circuitssimulator.domain.*

/** Real, importable ten-device circuits, shared with explicit Phase 2 occupancy alternatives. */
object AdvancedProjects {
    data class Scenario(val title:String,val low:Double,val high:Double,val quorum:Int,val purpose:String)
    val scenarios=listOf(
        Scenario("Smart Agriculture Monitoring and Automated Irrigation",5.0,35.0,2,"Eight root-zone thermal probes and two intrusion sensors command a protected irrigation warning output"),
        Scenario("Industrial IoT Factory Monitoring",10.0,60.0,1,"Machine thermal zones and access detection interlock"),
        Scenario("Smart Home Environmental Automation",16.0,28.0,3,"Room temperature quorum and occupancy alert"),
        Scenario("Advanced Weather Monitoring Station",-20.0,45.0,4,"Distributed ambient temperature extremes and enclosure intrusion"),
        Scenario("Greenhouse Climate Control System",18.0,32.0,2,"Growing-zone climate warning and entry monitoring"),
        Scenario("Smart Hospital Room Monitoring Simulator",20.0,26.0,1,"Room thermal excursion and restricted-area entry alert; no patient physiology"),
        Scenario("Air Quality and Pollution Monitoring Network",0.0,40.0,3,"Thermal operating-envelope and enclosure security subsystem; gas chemistry unsupported"),
        Scenario("Intelligent Water Treatment Monitoring",5.0,35.0,2,"Tank-zone thermal and access safety subsystem; water chemistry unsupported"),
        Scenario("Multi-Zone Fire and Gas Detection System",0.0,55.0,1,"Eight-zone heat alarm and intrusion; gas detection unsupported"),
        Scenario("Solar Energy and Battery Monitoring",0.0,45.0,1,"Battery enclosure thermal protection and access alarm; charge-state estimation unsupported"),
        Scenario("Smart Building Management System",15.0,30.0,4,"Zone thermal quorum and access alarm"),
        Scenario("Advanced Robotics Sensor Fusion Platform",-10.0,65.0,2,"Distributed motor thermal fusion and workspace motion; kinematics unsupported"),
        Scenario("Vehicle Diagnostic and Safety Monitoring",-20.0,85.0,1,"Vehicle thermal-zone and cabin-motion warning; CAN diagnostics unsupported"),
        Scenario("Cold Storage and Refrigeration Monitoring",-25.0,-10.0,1,"Cold-room thermal excursion and doorway motion alarm"),
        Scenario("Smart City Environmental Sensor Hub",-15.0,45.0,3,"Distributed ambient thermal and cabinet security telemetry"),
        Scenario("Automated Chemical Process Monitoring",15.0,55.0,1,"Vessel thermal-envelope and access protection; reaction chemistry unsupported"),
        Scenario("Intelligent Warehouse Monitoring",5.0,35.0,4,"Storage-zone thermal quorum and aisle motion"),
        Scenario("Smart Aquaculture Monitoring",18.0,30.0,2,"Tank thermal-envelope and perimeter monitoring; dissolved oxygen unsupported"),
        Scenario("Industrial Predictive Maintenance Platform",10.0,70.0,2,"Multi-bearing thermal excursion and service access; vibration prognostics unsupported"),
        Scenario("Multi-Sensor Disaster Detection and Alert System",-30.0,60.0,1,"Distributed thermal extremes and motion alert; seismic/flood models unsupported")
    )
    data class Project(val circuit:Circuit,val board:BoardDefinition,val scenario:Scenario,val number:Int,
        val motionPins:List<String>,val outputPin:String,val port:String) {
        val id get()="${board.kind.name.lowercase()}-${number.toString().padStart(2,'0')}"
        val normalTemperatures get()=List(8) { (scenario.low+scenario.high)/2+(it-3.5)*.5 }
    }
    fun create(kind:Kind,number:Int):Project {
        val d=BoardRegistry.boards.getValue(kind)
        val scenario=scenarios[number-1]
        val bus=requireNotNull(d.hardware.i2cPins)
        val tx=d.hardware.uartPins.first()
        val port=d.hardware.uartNames.firstOrNull() ?: "Serial"
        val reserved=(listOf(bus.first,bus.second,tx.first,tx.second)+d.hardware.bootLevels.keys).mapNotNull { d.pin(it)?.signal }.toSet()
        val pins=d.pins.filter { it.connectable && PinCapability.DIGITAL_OUTPUT in it.capabilities &&
            PinCapability.DIGITAL_INPUT in it.capabilities && it.signal !in reserved }.distinctBy { it.signal }.take(3)
        require(pins.size==3) { "${d.product}: insufficient unclaimed GPIO" }
        val motion=pins.take(2).map { it.name };val output=pins[2].name
        val components=mutableListOf<PlacedComponent>();val wires=mutableListOf<Wire>()
        fun p(kind:Kind,ref:String,params:Map<String,Double> = emptyMap()):PlacedComponent {
            val defaults=ComponentRegistry.definitions.getValue(kind).parameters.associate { it.key to it.default }
            return PlacedComponent(id=ref,kind=kind,reference=ref,x=100f+(components.size%6)*180f,
                y=100f+(components.size/6)*180f,parameters=defaults+params).also { components+=it }
        }
        fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int) {
            require(ai>=0 && bi>=0)
            wires+=Wire(id="W${wires.size}",start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))
        }
        val board=p(kind,"B1",mapOf("usbPower" to 1.0))
        val ground=p(Kind.GROUND,"G1")
        val rail=p(Kind.SOURCE,"VLOGIC",mapOf("voltage" to d.logicVoltage))
        val five=p(Kind.SOURCE,"V5",mapOf("voltage" to 5.0))
        w(board,d.pins.indexOfFirst { it.type==PinType.GROUND },ground,0)
        w(rail,1,ground,0);w(five,1,ground,0)
        for(i in 0..7) {
            val t=p(Kind.I2C_TEMP_SENSOR,"T$i",mapOf("address" to (72+i).toDouble(),"useEnvironment" to 0.0,
                "pullupsEnabled" to 0.0,"temperatureC" to ((scenario.low+scenario.high)/2+(i-3.5)*.5)))
            w(rail,0,t,0);w(ground,0,t,1);w(board,d.index(bus.first),t,2);w(board,d.index(bus.second),t,3)
            val cap=p(Kind.CAPACITOR,"C$i",mapOf("capacitance" to 100e-9))
            w(rail,0,cap,0);w(ground,0,cap,1)
        }
        listOf(bus.first,bus.second).forEachIndexed { i,pin ->
            val r=p(Kind.RESISTOR,"RPU$i",mapOf("resistance" to 4700.0))
            w(board,d.index(d.supplyPins.first()),r,0);w(r,1,board,d.index(pin))
        }
        for(i in 0..1) {
            val sensor=p(Kind.PIR_SENSOR,"P$i",mapOf("useEnvironment" to 0.0,"motion" to 0.0))
            val pull=p(Kind.RESISTOR,"RPD$i",mapOf("resistance" to 100000.0))
            val transistor=p(Kind.NPN_BJT,"Q$i")
            val baseR=p(Kind.RESISTOR,"RB$i",mapOf("resistance" to 10000.0))
            val collectorR=p(Kind.RESISTOR,"RC$i",mapOf("resistance" to 10000.0))
            w(five,0,sensor,0);w(ground,0,sensor,2)
            w(sensor,1,baseR,0);w(baseR,1,transistor,1);w(transistor,2,ground,0)
            w(board,d.index(d.supplyPins.first()),collectorR,0);w(collectorR,1,transistor,0);w(transistor,0,board,d.index(motion[i]))
            w(sensor,1,pull,0);w(pull,1,ground,0)
        }
        val led=p(Kind.LED,"ALARM")
        val limit=p(Kind.RESISTOR,"RLIMIT",mapOf("resistance" to 1000.0))
        w(board,d.index(output),limit,0);w(limit,1,led,0);w(led,1,ground,0)
        val terminal=p(Kind.SERIAL_TERMINAL,"TELEMETRY",mapOf("baud" to 115200.0))
        w(board,d.index(tx.first),terminal,1);w(ground,0,terminal,2)
        val source=buildString {
            append("// ${scenario.title}\n// ${scenario.purpose}\n")
            append("void setup(){Wire.begin();$port.begin(115200);pinMode($output,OUTPUT);")
            motion.forEach { append("pinMode($it,INPUT);") }
            append("int cycles=0;int faults=0;int excursions=0;int alarm=0;int motion0=0;int motion1=0;")
            repeat(8) { append("double t$it=0;") }
            append("delay(30000);}\nvoid loop(){faults=0;excursions=0;")
            repeat(8) { i -> append("Wire.beginTransmission(${72+i});Wire.write(0);int ack$i=Wire.endTransmission(false);")
                append("int count$i=Wire.requestFrom(${72+i},2);int hi$i=Wire.read();int lo$i=Wire.read();")
                append("if(ack$i==0 && count$i==2){int raw$i=hi$i*256+lo$i;if(raw$i>=32768){raw$i=raw$i-65536;}t$i=raw$i/256.0;")
                append("if(t$i<${scenario.low} || t$i>${scenario.high}){excursions=excursions+1;}}else{faults=faults+1;}") }
            append("motion0=1-digitalRead(${motion[0]});motion1=1-digitalRead(${motion[1]});")
            append("alarm=0;if(faults>0 || excursions>=${scenario.quorum} || motion0==1 || motion1==1){alarm=1;}")
            append("if(alarm==1){digitalWrite($output,HIGH);}else{digitalWrite($output,LOW);}")
            append("$port.println(faults);cycles=cycles+1;delay(1000);}\n")
        }
        return Project(Circuit(scenario.title,components,wires,
            firmware=listOf(FirmwareAttachment(board.id,FirmwareLanguage.ARDUINO_SUBSET,source,true))),d,scenario,number,motion,output,port)
    }
}
