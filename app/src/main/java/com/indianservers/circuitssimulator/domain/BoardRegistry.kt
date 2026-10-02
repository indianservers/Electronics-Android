package com.indianservers.circuitssimulator.domain

enum class PinCapability {
    DIGITAL_INPUT, DIGITAL_OUTPUT, PWM, ANALOG_INPUT, UART, SPI, I2C,
    POWER_INPUT, POWER_OUTPUT, GROUND, RESET, RESERVED
}

/** Header order keeps existing electrical indices stable. Visual (nx,ny) follows official headers. */
object BoardRegistry {
    val nodeMcuGpio=mapOf("D0" to 16,"D1" to 5,"D2" to 4,"D3" to 0,"D4" to 2,
        "D5" to 14,"D6" to 12,"D7" to 13,"D8" to 15)
    val nodeMcuFromGpio=nodeMcuGpio.entries.associate { it.value to it.key }

    private val buses=BoardCapabilityMatrix(
        FeatureSupport(true,FeatureSimLevel.FULL),FeatureSupport(true,FeatureSimLevel.FULL),
        FeatureSupport(true,FeatureSimLevel.FULL),FeatureSupport(true,FeatureSimLevel.BASIC,"Event UART"),
        FeatureSupport(true,FeatureSimLevel.BASIC,"Event I²C"),FeatureSupport(true,FeatureSimLevel.BASIC,"Event SPI"),
        FeatureSupport(false,FeatureSimLevel.UNSUPPORTED),FeatureSupport(false,FeatureSimLevel.UNSUPPORTED),
        FeatureSupport(false,FeatureSimLevel.UNSUPPORTED))
    private fun wireless(wifi:Boolean,ble:Boolean)=buses.copy(
        wifi=FeatureSupport(wifi,if(wifi) FeatureSimLevel.METADATA else FeatureSimLevel.UNSUPPORTED,
            if(wifi) "Wi-Fi hardware present — network simulation not implemented" else ""),
        bluetooth=FeatureSupport(ble,if(ble) FeatureSimLevel.METADATA else FeatureSimLevel.UNSUPPORTED,
            if(ble) "Bluetooth hardware present — not simulated" else ""))

    private fun layout(pins:List<BoardPin>,places:List<Pair<String,Pair<Float,Float>>>):List<BoardPin> {
        val remaining=places.toMutableList()
        return pins.map { pin ->
            val slot=remaining.indexOfFirst { it.first==pin.name }
            if(slot<0) pin else {
                val xy=remaining.removeAt(slot).second
                pin.copy(nx=xy.first,ny=xy.second)
            }
        }
    }
    private fun stack(names:List<String>,x:Float,y0:Float,pitch:Float=14f)=
        names.mapIndexed { i,n -> n to (x to y0+i*pitch) }

    private fun make(name:String,logic:Double,number:Int?=null,extra:Set<PinCapability> = emptySet(),
                     notes:String="",gpio:Int?=null,aliases:Set<String> = emptySet(),
                     type:PinType?=null,signal:String=name,adc:Int?=null,inputOnly:Boolean=false,
                     connectable:Boolean=true,warnings:List<String> = emptyList()):BoardPin {
        val inferred=when {
            name=="GND" || name.startsWith("GND") || name=="AGND" -> PinType.GROUND
            name in setOf("5V","3V3","3V3_OUT") -> PinType.POWER_OUTPUT
            name in setOf("VIN","VSYS","VBUS","VU") -> PinType.POWER_INPUT
            name in setOf("RST","RESET","RUN") -> PinType.RESET
            name in setOf("EN","3V3_EN") -> PinType.ENABLE
            name in setOf("AREF","ADC_VREF","IOREF") -> PinType.REFERENCE
            name=="NC" -> PinType.RESERVED
            else -> PinType.GPIO
        }
        val caps=when(inferred) {
            PinType.GROUND -> setOf(PinCapability.GROUND)
            PinType.POWER_OUTPUT -> setOf(PinCapability.POWER_INPUT,PinCapability.POWER_OUTPUT)
            PinType.POWER_INPUT -> setOf(PinCapability.POWER_INPUT)
            PinType.RESET,PinType.ENABLE -> setOf(PinCapability.RESET)
            PinType.REFERENCE -> setOf(PinCapability.RESERVED)
            PinType.RESERVED -> setOf(PinCapability.RESERVED)
            else -> setOf(PinCapability.DIGITAL_INPUT,PinCapability.DIGITAL_OUTPUT)
        }+extra
        val limit=if(PinCapability.DIGITAL_INPUT in caps || PinCapability.ANALOG_INPUT in caps) logic else null
        return BoardPin(name,caps,notes,if(limit!=null) 0.0 else null,limit,number,gpio,aliases,
            type ?: inferred,if(inferred==PinType.GROUND) "GND" else signal,adc,inputOnly,connectable,
            warnings=warnings)
    }
    private fun arduinoPin(name:String,board:String,number:Int?=null):BoardPin {
        val gpio=name.startsWith("D") && name.drop(1).toIntOrNull()!=null
        val analog=name.startsWith("A") && name.drop(1).toIntOrNull()!=null
        val d=name.drop(1).toIntOrNull() ?: -1
        val pwm=if(board=="mega") d in setOf(2,3,4,5,6,7,8,9,10,11,12,13,44,45,46)
            else d in setOf(3,5,6,9,10,11)
        val uart=if(board=="mega") d in setOf(0,1,14,15,16,17,18,19) else d in setOf(0,1)
        val spi=if(board=="mega") d in setOf(50,51,52,53) else d in setOf(10,11,12,13)
        val i2c=if(board=="mega") d in setOf(20,21) else name in setOf("A4","A5","SDA","SCL")
        val gpioNum=when {
            gpio -> d
            analog && board=="mega" -> 54+d
            analog -> 14+d
            name=="SDA" -> if(board=="mega") 20 else 18
            name=="SCL" -> if(board=="mega") 21 else 19
            else -> null
        }
        val extra=buildSet {
            if(analog) add(PinCapability.ANALOG_INPUT)
            if(pwm && gpio) add(PinCapability.PWM)
            if(uart && gpio) add(PinCapability.UART)
            if(spi && gpio) add(PinCapability.SPI)
            if(i2c) add(PinCapability.I2C)
        }
        val aliases=buildSet {
            if(gpio) { add("$d");add("GPIO$d") }
            if(name=="D0") { add("RX");add("0") }
            if(name=="D1") { add("TX");add("1") }
            if(name=="D13") add("LED_BUILTIN")
            if(board=="mega") {
                if(name=="D14") add("TX3")
                if(name=="D15") add("RX3")
                if(name=="D16") add("TX2")
                if(name=="D17") add("RX2")
                if(name=="D18") add("TX1")
                if(name=="D19") add("RX1")
                if(name=="D20" || name=="SDA") add("SDA")
                if(name=="D21" || name=="SCL") add("SCL")
                if(name=="D50") add("MISO")
                if(name=="D51") add("MOSI")
                if(name=="D52") add("SCK")
                if(name=="D53") add("SS")
            } else {
                if(name=="D10") add("SS")
                if(name=="D11") add("MOSI")
                if(name=="D12") add("MISO")
                if(name=="D13") add("SCK")
                if(name=="A4" || name=="SDA") { add("SDA");add("18") }
                if(name=="A5" || name=="SCL") { add("SCL");add("19") }
            }
            if(analog) add("A$d")
        }
        val signal=when(name) {
            "SDA" -> if(board=="mega") "D20" else "A4"
            "SCL" -> if(board=="mega") "D21" else "A5"
            else -> name
        }
        val pin=make(name,5.0,number,extra,"",gpioNum,aliases,signal=signal,adc=if(analog) d else null)
        return if(board=="nano" && name in setOf("A6","A7")) pin.copy(
            capabilities=setOf(PinCapability.ANALOG_INPUT),type=PinType.ANALOG_INPUT,
            inputOnly=true,aliases=aliases+name) else pin
    }
    private fun picoPin(name:String,number:Int):BoardPin {
        val n=name.removePrefix("GP").toIntOrNull()
        val extra=buildSet {
            if(n!=null) {
                add(PinCapability.PWM)
                if(n in 26..28) add(PinCapability.ANALOG_INPUT)
                if(n in setOf(0,1,4,5,8,9,12,13,16,17,20,21)) add(PinCapability.UART)
                if(n in 0..23) { add(PinCapability.SPI);add(PinCapability.I2C) }
            }
        }
        return make(name,3.3,number,extra,
            if(n!=null && n in 26..28) "ADC${n-26} on RP2040. Runtime mapping uses this GP as analogRead." else
                if(n!=null) "RP2040 GPIO$n. PWM/UART/I²C/SPI are MCU-capable; runtime uses default pairs." else "",
            n,buildSet {
                if(n!=null) { add("$n");add("GPIO$n") }
                if(n!=null && n in 26..28) add("A${n-26}")
            },
            adc=if(n!=null && n in 26..28) n-26 else null)
    }
    private fun espPin(name:String,number:Int):BoardPin {
        val n=when(name) { "TX"->1;"RX"->3;"VP"->36;"VN"->39;else->name.removePrefix("IO").removePrefix("D").toIntOrNull() }
        val reserved=name in setOf("D0","D1","D2","D3","CMD","CLK")
        val inputOnly=n in setOf(34,35,36,39)
        val extra=buildSet {
            if(n!=null && !reserved) {
                if(n in setOf(0,2,4,12,13,14,15,25,26,27,32,33,34,35,36,39)) add(PinCapability.ANALOG_INPUT)
                if(!inputOnly) add(PinCapability.PWM)
                if(n in setOf(21,22)) add(PinCapability.I2C)
                if(n in setOf(18,19,23,5)) add(PinCapability.SPI)
                if(name in setOf("TX","RX") || n in setOf(1,3)) add(PinCapability.UART)
            }
        }
        val warnings=buildList {
            if(n in setOf(0,2,12,15)) add("Boot/strapping pin. External pull during reset can change startup mode.")
            if(n in setOf(25,26)) add("Hardware DAC exists on this GPIO; the simulator does not implement analogWrite as true DAC.")
        }
        val pin=make(name,3.3,number,extra,
            if(reserved) "Connected to on-board SPI flash; do not use as GPIO." else
                if(inputOnly) "Input only; no digital output or PWM." else "",
            if(reserved) null else n,buildSet {
                if(n!=null && !reserved) { add("GPIO$n");add("$n");add("IO$n") }
                if(!reserved && n==36) add("A0"); if(!reserved && n==39) add("A3"); if(!reserved && n==32) add("A4")
                if(!reserved && n==33) add("A5"); if(!reserved && n==34) add("A6"); if(!reserved && n==35) add("A7")
            },
            adc=if(n in setOf(0,2,4,12,13,14,15,25,26,27,32,33,34,35,36,39)) n else null,
            inputOnly=inputOnly,warnings=warnings)
        return when {
            reserved -> pin.copy(capabilities=setOf(PinCapability.RESERVED),type=PinType.RESERVED,
                minVoltage=null,maxVoltage=null,connectable=true)
            inputOnly -> pin.copy(capabilities=pin.capabilities-PinCapability.DIGITAL_OUTPUT-PinCapability.PWM,
                type=PinType.GPIO,inputOnly=true)
            else -> pin
        }
    }
    private fun numbered(names:List<String>,factory:(String,Int)->BoardPin)=names.mapIndexed { i,n -> factory(n,i+1) }

    private val picoNames=listOf("GP0","GP1","GND","GP2","GP3","GP4","GP5","GND","GP6","GP7",
        "GP8","GP9","GND","GP10","GP11","GP12","GP13","GND","GP14","GP15",
        "GP16","GP17","GND","GP18","GP19","GP20","GP21","GND","GP22","RUN",
        "GP26","GP27","AGND","GP28","ADC_VREF","3V3_OUT","3V3_EN","GND","VSYS","VBUS")
    private val unoNames=listOf("D0","D1","D2","D3","D4","D5","D6","D7","D8","D9","D10","D11","D12","D13",
        "GND","AREF","SDA","SCL","RESET","3V3","5V","GND","VIN","A0","A1","A2","A3","A4","A5","IOREF")
    private val nanoNames=listOf("D1","D0","RESET","GND","D2","D3","D4","D5","D6","D7","D8","D9","D10","D11","D12","D13","3V3","AREF","A0","A1","A2","A3","A4","A5","A6","A7","5V","RESET","GND","VIN")
    private val megaNames=(0..53).map { "D$it" } + (0..15).map { "A$it" }+
        listOf("3V3","5V","GND","VIN","RESET","AREF","SDA","SCL")
    private val nodemcuNames=listOf("A0","GND","VU","S3","S2","S1","SC","S0","SK","GND","3V3","EN","RST","GND","VIN",
        "D0","D1","D2","D3","D4","3V3","GND","D5","D6","D7","D8","RX","TX","GND","3V3")
    private val esp32Names=listOf("3V3","EN","VP","VN","IO34","IO35","IO32","IO33","IO25","IO26","IO27","IO14","IO12","GND","IO13","D2","D3","CMD","5V",
        "GND","IO23","IO22","TX","RX","IO21","GND","IO19","IO18","IO5","IO17","IO16","IO4","IO0","IO2","IO15","D1","D0","CLK")
    private val c3Names=listOf("GND","3V3","3V3","IO2","IO3","GND","RST","GND","IO0","IO1","IO10","GND","5V","5V","GND",
        "GND","TX","RX","GND","IO9","IO8","GND","IO7","IO6","IO5","IO4","GND","IO18","IO19","GND")
    private val d1MiniNames=listOf("RST","A0","D0","D5","D6","D7","D8","3V3","TX","RX","D1","D2","D3","D4","GND","5V")
    private val nano33Names=listOf("D1","D0","RESET","GND","D2","D3","D4","D5","D6","D7","D8","D9","D10","D11","D12","D13",
        "3V3","AREF","A0","A1","A2","A3","A4","A5","A6","A7","VUSB","RESET","GND","VIN")

    private val unoPlaces=stack(listOf("IOREF","RESET","3V3","5V","GND","VIN","A0","A1","A2","A3","A4","A5"),-120f,-84f,14f)+
        stack(listOf("SCL","SDA","AREF","GND","D13","D12","D11","D10","D9","D8","D7","D6","D5","D4","D3","D2","D1","D0"),120f,-119f,14f)
    private val nanoPlaces=stack(nanoNames.take(15),-78f,-98f,14f)+stack(nanoNames.drop(15).reversed(),78f,-98f,14f)
    private val picoPlaces=stack(picoNames.take(20),-78f,-133f,14f)+
        stack(listOf("VBUS","VSYS","GND","3V3_EN","3V3_OUT","ADC_VREF","GP28","AGND","GP27","GP26","RUN","GP22","GND","GP21","GP20","GP19","GP18","GND","GP17","GP16"),78f,-133f,14f)+
        listOf("GP25" to (0f to 8f))
    private val nodemcuPlaces=stack(nodemcuNames.take(15),-92f,-98f,14f)+stack(nodemcuNames.drop(15),92f,-98f,14f)
    private val esp32Places=stack(esp32Names.take(19),-110f,-126f,14f)+stack(esp32Names.drop(19),110f,-126f,14f)
    private val c3Places=stack(c3Names.take(15),-92f,-98f,14f)+stack(c3Names.drop(15),92f,-98f,14f)
    private val d1Places=stack(d1MiniNames.take(8),-72f,-52f,15f)+stack(d1MiniNames.drop(8),72f,-52f,15f)
    private val nano33Places=stack(nano33Names.take(15),-78f,-98f,14f)+stack(nano33Names.drop(15).reversed(),78f,-98f,14f)

    private fun megaPlaces():List<Pair<String,Pair<Float,Float>>> {
        val left=stack(listOf("RESET","3V3","5V","GND","VIN")+(0..15).map { "A$it" },-150f,-150f,14f)
        val mid=stack((0..21).map { "D$it" }+listOf("AREF","SDA","SCL"),20f,-168f,13.5f)
        val far=stack((22..53).map { "D$it" },150f,-216f,13.5f)
        return left+mid+far
    }

    private val avrRefs=listOf(
        BoardReference("Arduino UNO R3 datasheet / connector pinouts","Arduino",ReferenceType.BOARD_DATASHEET,null,
            "https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf"),
        BoardReference("ATmega328P datasheet","Microchip",ReferenceType.MCU_DATASHEET,null,
            "https://ww1.microchip.com/downloads/en/DeviceDoc/ATmega48A-PA-88A-PA-168A-PA-328-P-DS-DS40002061A.pdf"))

    private fun nodePin(name:String,i:Int,map:Map<String,Int>,logic:Double=3.3,led:String="D4"):BoardPin {
        val extra=buildSet {
            if(name=="A0") add(PinCapability.ANALOG_INPUT)
            if(name in map && name!="D0") add(PinCapability.PWM)
            if(name in setOf("D1","D2")) add(PinCapability.I2C)
            if(name in setOf("D5","D6","D7","D8")) add(PinCapability.SPI)
            if(name in setOf("RX","TX")) add(PinCapability.UART)
        }
        val gpio=when(name) { "RX"->3;"TX"->1;else->map[name] }
        val warnings=buildList {
            if(name in setOf("D3","D4")) add("Must read HIGH at reset or the module may enter flash download mode.")
            if(name=="D8") add("Must read LOW at reset or startup can fail.")
        }
        val pin=make(name,logic,i,extra,map[name]?.let { "ESP8266 GPIO$it. Arduino sketch number $it is this header." } ?: "",
            gpio,buildSet { gpio?.let { add("GPIO$it");add("$it") }; if(name==led) add("LED_BUILTIN") },
            adc=if(name=="A0") 0 else null,warnings=warnings)
        return when {
            name=="A0" -> pin.copy(capabilities=setOf(PinCapability.ANALOG_INPUT),type=PinType.ANALOG_INPUT,
                minVoltage=0.0,maxVoltage=3.2,notes="Board divider. Official D1 Mini docs: analog input max 3.2 V. NodeMCU revisions vary.")
            name in setOf("S0","S1","S2","S3","SC","SK") ->
                pin.copy(capabilities=setOf(PinCapability.RESERVED),type=PinType.RESERVED,minVoltage=null,maxVoltage=null,
                    notes="SDIO / flash-related header. Not a user GPIO in this model.")
            else -> pin
        }
    }

    private fun c3Pin(name:String,i:Int):BoardPin {
        val n=when(name) { "TX"->21;"RX"->20;"RST"->null;else->name.removePrefix("IO").toIntOrNull() }
        val extra=buildSet {
            if(n in setOf(0,1,2,3,4,5)) add(PinCapability.ANALOG_INPUT)
            if(n!=null && name!="RST") { add(PinCapability.PWM);add(PinCapability.I2C);add(PinCapability.SPI) }
            if(name in setOf("TX","RX") || n in setOf(20,21)) add(PinCapability.UART)
        }
        val warnings=buildList {
            if(n in setOf(2,8,9)) add("ESP32-C3 strapping pin. Level at reset can change boot mode.")
            if(n in setOf(18,19)) add("USB D−/D+ on this DevKit. Prefer other GPIO for general wiring.")
        }
        return make(name,3.3,i,extra,if(n==8) "Onboard RGB LED is driven from GPIO8." else "",
            n,buildSet { n?.let { add("GPIO$it");add("$it");add("IO$it") } },
            type=if(name=="RST") PinType.RESET else null,
            adc=if(n!=null && n in 0..5) n else null,warnings=warnings)
    }

    private fun nano33Pin(name:String,i:Int):BoardPin {
        val gpio=name.startsWith("D") && name.drop(1).toIntOrNull()!=null
        val analog=name.startsWith("A") && name.drop(1).toIntOrNull()!=null
        val d=name.drop(1).toIntOrNull() ?: -1
        val extra=buildSet {
            if(analog) add(PinCapability.ANALOG_INPUT)
            if(gpio && d in setOf(3,5,6,9,10,11,12,13)) add(PinCapability.PWM)
            if(name in setOf("D0","D1")) add(PinCapability.UART)
            if(name in setOf("D11","D12","D13")) add(PinCapability.SPI)
            if(name in setOf("A4","A5","D18","D19")) add(PinCapability.I2C)
        }
        return make(name,3.3,i,extra,"3.3 V SAMD21 I/O. Do not drive from 5 V logic.",
            if(gpio) d else if(analog) 14+d else null,
            buildSet { if(gpio) { add("$d");add("GPIO$d") }; if(analog) add("A$d"); if(name=="D13") add("LED_BUILTIN") },
            adc=if(analog) d else null)
    }

    val boards:Map<Kind,BoardDefinition> = listOf(
        BoardDefinition(Kind.ARDUINO_UNO,"Arduino","UNO R3","ATmega328P",5.0,
            layout(numbered(unoNames) { n,i -> arduinoPin(n,"uno",i) },unoPlaces),setOf("5V"),
            "https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf",
            "Arduino UNO Rev3 (A000066). 5 V AVR logic. VIN/regulator path is educational, not a SPX1117 transistor model. ICSP is not a separate connector; SPI is D11–D13/D10.",
            listOf("Arduino","ATmega328P","I2C","SPI","PWM","Uno","AVR"),
            BoardFamily.ARDUINO_AVR,"Rev3","A000066","AVR 8-bit",10,5.0,"D13",
            BoardSupportLevel.FULL,
            listOf(PowerDomain("USB",5.0,"input","USB-B virtual power"),
                PowerDomain("VIN",9.0,"input","7–12 V typical; regulator not transistor-level"),
                PowerDomain("5V",5.0,"output","Logic rail"),PowerDomain("3V3",3.3,"output","Onboard 3.3 V")),
            avrRefs+BoardReference("UNO R3 full pinout PDF","Arduino",ReferenceType.PINOUT,null,
                "https://docs.arduino.cc/resources/pinouts/A000066-full-pinout.pdf"),
            listOf("SDA/SCL headers are the same nets as A4/A5."),
            buses,usbConnector="USB-B",boardWidth=248f,boardHeight=280f,pcbColor=0xFF08677E),
        BoardDefinition(Kind.ARDUINO_NANO,"Arduino","Nano","ATmega328",5.0,
            layout(numbered(nanoNames) { n,i -> arduinoPin(n,"nano",i) },nanoPlaces),setOf("5V"),
            "https://docs.arduino.cc/resources/pinouts/A000005-full-pinout.pdf",
            "Arduino Nano (A000005), breadboard DIP. A6 and A7 are analog-input only. VIN regulation is outside the model.",
            listOf("Arduino","ATmega328","I2C","SPI","PWM","Nano"),
            BoardFamily.ARDUINO_AVR,"A000005",null,"AVR 8-bit",10,5.0,"D13",
            BoardSupportLevel.FULL,
            listOf(PowerDomain("USB",5.0,"input","Mini-USB / USB-C clones exist; this model uses USB Power"),
                PowerDomain("VIN",9.0,"input","External raw input"),PowerDomain("5V",5.0,"output","Logic rail")),
            listOf(BoardReference("Arduino Nano pinout","Arduino",ReferenceType.PINOUT,null,
                "https://docs.arduino.cc/resources/pinouts/A000005-full-pinout.pdf")),
            emptyList(),buses,usbConnector="Mini-USB",boardWidth=168f,boardHeight=230f),
        BoardDefinition(Kind.ARDUINO_MEGA,"Arduino","Mega 2560","ATmega2560",5.0,
            layout(numbered(megaNames) { n,i -> arduinoPin(n,"mega",i) },megaPlaces()),setOf("5V"),
            "https://docs.arduino.cc/resources/pinouts/A000067-full-pinout.pdf",
            "Arduino Mega 2560 (A000067). Serial1/2/3 pins are labeled; the firmware subset currently drives one Serial pair (D1/D0). SPI is D50–D53.",
            listOf("Arduino","ATmega2560","I2C","SPI","PWM","Mega","UART"),
            BoardFamily.ARDUINO_AVR,"A000067",null,"AVR 8-bit",10,5.0,"D13",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("USB",5.0,"input","USB-B"),PowerDomain("5V",5.0,"output","Logic rail"),
                PowerDomain("VIN",9.0,"input","External")),
            listOf(BoardReference("Mega 2560 pinout","Arduino",ReferenceType.PINOUT,null,
                "https://docs.arduino.cc/resources/pinouts/A000067-full-pinout.pdf")),
            listOf("Runtime Serial uses D1/D0. D14–D19 are hardware UART pins (Basic / metadata)."),
            buses,usbConnector="USB-B",boardWidth=340f,boardHeight=460f),
        BoardDefinition(Kind.RASPBERRY_PICO,"Raspberry Pi","Pico","RP2040",3.3,
            layout(numbered(picoNames,::picoPin)+listOf(make("GP25",3.3,41,setOf(PinCapability.DIGITAL_INPUT,PinCapability.DIGITAL_OUTPUT,PinCapability.PWM),
                "Onboard LED. Not a header pin.",25,setOf("25","GPIO25","LED_BUILTIN"),PinType.ONBOARD,connectable=false)),picoPlaces),
            setOf("3V3_OUT"),"https://datasheets.raspberrypi.com/pico/pico-datasheet.pdf",
            "Raspberry Pi Pico. GP26–28 are ADC0–2. GP25 is the onboard LED (not on the 40-pin header). VSYS/VBUS regulation is simplified.",
            listOf("Pico","RP2040","MicroPython","I2C","SPI","PWM"),
            BoardFamily.RP2040,"",null,"ARM Cortex-M0+",12,3.3,"GP25",
            BoardSupportLevel.FULL,
            listOf(PowerDomain("VBUS",5.0,"input","USB 5 V"),PowerDomain("VSYS",5.0,"input","Board supply"),
                PowerDomain("3V3_OUT",3.3,"output","Regulated GPIO rail")),
            listOf(BoardReference("Pico datasheet","Raspberry Pi Ltd",ReferenceType.BOARD_DATASHEET,null,
                "https://datasheets.raspberrypi.com/pico/pico-datasheet.pdf"),
                BoardReference("Pico pinout","Raspberry Pi Ltd",ReferenceType.PINOUT,null,
                    "https://datasheets.raspberrypi.com/pico/Pico-R3-A4-Pinout.pdf")),
            emptyList(),buses,usbConnector="Micro-USB",boardWidth=172f,boardHeight=300f,pcbColor=0xFF177749),
        BoardDefinition(Kind.RASPBERRY_PICO_W,"Raspberry Pi","Pico W","RP2040",3.3,
            layout(numbered(picoNames,::picoPin)+listOf(make("WL_LED",3.3,41,setOf(PinCapability.DIGITAL_INPUT,PinCapability.DIGITAL_OUTPUT),
                "Pico W onboard LED is on the wireless chip (WL_GPIO0), not GP25.",null,setOf("LED_BUILTIN"),PinType.ONBOARD,connectable=false)),
                picoPlaces),
            setOf("3V3_OUT"),"https://datasheets.raspberrypi.com/picow/pico-w-datasheet.pdf",
            "Pico W. Wired GPIO matches Pico. Onboard LED is WL_GPIO0. Wireless circuitry is not simulated.",
            listOf("Pico","WiFi","RP2040","MicroPython","I2C","SPI","PWM"),
            BoardFamily.RP2040,"",null,"ARM Cortex-M0+",12,3.3,"WL_LED",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("VBUS",5.0,"input","USB 5 V"),PowerDomain("3V3_OUT",3.3,"output","GPIO rail")),
            listOf(BoardReference("Pico W datasheet","Raspberry Pi Ltd",ReferenceType.BOARD_DATASHEET,null,
                "https://datasheets.raspberrypi.com/picow/pico-w-datasheet.pdf")),
            listOf("Wi-Fi hardware present — network simulation not implemented."),
            wireless(true,false),usbConnector="Micro-USB",boardWidth=172f,boardHeight=300f,pcbColor=0xFF1B6B4A),
        BoardDefinition(Kind.NODEMCU_ESP8266,"NodeMCU","DEVKIT V1.0","ESP8266",3.3,
            layout(numbered(nodemcuNames) { n,i -> nodePin(n,i,nodeMcuGpio) },nodemcuPlaces),setOf("3V3"),
            "https://github.com/nodemcu/nodemcu-devkit-v1.0/blob/master/README.md",
            "NodeMCU DEVKIT V1.0. D-labels and GPIO numbers are both valid. D0 is GPIO16 and has no PWM. A0 range varies by revision.",
            listOf("NodeMCU","ESP8266","WiFi","D0","I2C","ESP"),
            BoardFamily.ESP8266,"DEVKIT V1.0","ESP-12E","Xtensa L106",10,3.3,"D4",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("USB",5.0,"input","Micro-USB"),PowerDomain("VIN",5.0,"input","VU / VIN"),
                PowerDomain("3V3",3.3,"output","Logic rail")),
            listOf(BoardReference("NodeMCU DEVKIT V1.0","NodeMCU",ReferenceType.USER_GUIDE,null,
                "https://github.com/nodemcu/nodemcu-devkit-v1.0/blob/master/README.md")),
            listOf("D3/D4/D8 are boot-sensitive."),
            wireless(true,false).copy(adc=FeatureSupport(true,FeatureSimLevel.FULL,"10-bit; board divider varies"),
                pwm=FeatureSupport(true,FeatureSimLevel.BASIC,"All GPIO except D0")),
            usbConnector="Micro-USB",boardWidth=196f,boardHeight=230f,pcbColor=0xFF1D395B),
        BoardDefinition(Kind.ESP32_DEVKIT,"Espressif","ESP32-DevKitC V4","ESP32-WROOM-32E",3.3,
            layout(numbered(esp32Names) { n,i -> espPin(n,i) },esp32Places),setOf("3V3"),
            "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html",
            "ESP32-DevKitC V4 with ESP32-WROOM-32E (38-pin). GPIO34–39 input only. D0–D3/CMD/CLK are flash. DAC on GPIO25/26 is hardware-only.",
            listOf("ESP32","WiFi","Bluetooth","I2C","SPI","PWM","DevKitC","WROOM"),
            BoardFamily.ESP32,"V4","ESP32-WROOM-32E","Xtensa LX6 dual-core",12,3.3,"IO2",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("USB",5.0,"input","Micro-USB / USB-C clones"),
                PowerDomain("5V",5.0,"input","Header 5 V"),PowerDomain("3V3",3.3,"output","Logic rail")),
            listOf(BoardReference("ESP32-DevKitC V4 user guide","Espressif",ReferenceType.USER_GUIDE,null,
                "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html"),
                BoardReference("ESP32 datasheet","Espressif",ReferenceType.MCU_DATASHEET,null,
                    "https://www.espressif.com/sites/default/files/documentation/esp32_datasheet_en.pdf")),
            listOf("I²C/SPI tags show default runtime pins. Other GPIO can do those functions in hardware."),
            wireless(true,true).copy(dac=FeatureSupport(true,FeatureSimLevel.UNSUPPORTED,"GPIO25/26 DAC not simulated")),
            usbConnector="Micro-USB",boardWidth=228f,boardHeight=290f,pcbColor=0xFF1D395B),
        BoardDefinition(Kind.WEMOS_D1_MINI,"Wemos / LOLIN","D1 Mini","ESP8266",3.3,
            layout(numbered(d1MiniNames) { n,i -> nodePin(n,i,nodeMcuGpio,led="D4") },d1Places),setOf("3V3"),
            "https://www.wemos.cc/en/latest/d1/d1_mini.html",
            "LOLIN D1 Mini (ESP8266). Same GPIO aliases as NodeMCU D0–D8. Official analog input max 3.2 V. Wi-Fi not simulated.",
            listOf("Wemos","D1 Mini","LOLIN","ESP8266","WiFi","ESP"),
            BoardFamily.ESP8266,"V4-class",null,"Xtensa L106",10,3.2,"D4",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("USB",5.0,"input","USB-C / Micro-USB depending on revision"),
                PowerDomain("5V",5.0,"input","Header"),PowerDomain("3V3",3.3,"output","Logic")),
            listOf(BoardReference("D1 Mini documentation","Wemos / LOLIN",ReferenceType.USER_GUIDE,null,
                "https://www.wemos.cc/en/latest/d1/d1_mini.html")),
            listOf("D3/D4/D8 are boot-sensitive. analogRead is 10-bit on the board divider."),
            wireless(true,false).copy(adc=FeatureSupport(true,FeatureSimLevel.FULL,"10-bit, 3.2 V max per LOLIN docs")),
            usbConnector="USB-C",boardWidth=156f,boardHeight=140f,pcbColor=0xFF1D395B),
        BoardDefinition(Kind.ESP32_C3_DEVKIT,"Espressif","ESP32-C3-DevKitM-1","ESP32-C3",3.3,
            layout(numbered(c3Names) { n,i -> c3Pin(n,i) },c3Places),setOf("3V3"),
            "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32c3/esp32-c3-devkitm-1/user_guide.html",
            "ESP32-C3-DevKitM-1 (ESP32-C3-MINI-1). Official J1/J3 headers. GPIO8 drives the RGB LED. Strapping pins IO2/IO8/IO9. Wi-Fi/BLE not simulated.",
            listOf("ESP32","C3","WiFi","RISC-V","I2C","SPI","PWM"),
            BoardFamily.ESP32_C3,"DevKitM-1","ESP32-C3-MINI-1","RISC-V",12,3.3,"IO8",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("USB",5.0,"input","Micro-USB"),PowerDomain("5V",5.0,"input","Header"),
                PowerDomain("3V3",3.3,"output","Logic")),
            listOf(BoardReference("ESP32-C3-DevKitM-1 user guide","Espressif",ReferenceType.USER_GUIDE,null,
                "https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32c3/esp32-c3-devkitm-1/user_guide.html")),
            listOf("IO18/IO19 are USB D−/D+ on this kit."),
            wireless(true,true),usbConnector="Micro-USB",boardWidth=196f,boardHeight=230f,pcbColor=0xFF243E2C),
        BoardDefinition(Kind.ARDUINO_NANO_33_IOT,"Arduino","Nano 33 IoT","SAMD21G18A",3.3,
            layout(numbered(nano33Names) { n,i -> nano33Pin(n,i) },nano33Places),setOf("3V3"),
            "https://docs.arduino.cc/hardware/nano-33-iot",
            "Arduino Nano 33 IoT. 3.3 V SAMD21. Not a 5 V Nano. NINA-W102 Wi-Fi/BLE is hardware-only. analogRead uses 12-bit ADC in this model.",
            listOf("Arduino","SAMD21","Nano 33","WiFi","IoT","3.3V"),
            BoardFamily.ARDUINO_SAMD,"ABXxxx","NINA-W102","ARM Cortex-M0+",12,3.3,"D13",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("USB",5.0,"input","VUSB / USB Power"),PowerDomain("VIN",5.0,"input","External"),
                PowerDomain("3V3",3.3,"output","Logic — all GPIO are 3.3 V")),
            listOf(BoardReference("Nano 33 IoT documentation","Arduino",ReferenceType.USER_GUIDE,null,
                "https://docs.arduino.cc/hardware/nano-33-iot")),
            listOf("Do not treat this board as a classic 5 V Nano."),
            wireless(true,true),usbConnector="Micro-USB",boardWidth=168f,boardHeight=230f,pcbColor=0xFF0B3D4A),
        BoardDefinition(Kind.RASPBERRY_PICO_2,"Raspberry Pi","Pico 2","RP2350",3.3,
            layout(numbered(picoNames) { n,i -> picoPin(n,i).copy(notes="RP2350 GPIO. Pinout matches Pico 40-pin header.") }+
                listOf(make("GP25",3.3,41,setOf(PinCapability.DIGITAL_INPUT,PinCapability.DIGITAL_OUTPUT,PinCapability.PWM),
                    "Onboard LED.",25,setOf("25","GPIO25","LED_BUILTIN"),PinType.ONBOARD,connectable=false)),picoPlaces),
            setOf("3V3_OUT"),"https://datasheets.raspberrypi.com/pico/pico-2-datasheet.pdf",
            "Raspberry Pi Pico 2 (RP2350). 40-pin header matches Pico. Firmware is the same supported subset as Pico, not a native RP2350 SDK.",
            listOf("Pico 2","RP2350","Pico","MicroPython","I2C","SPI","PWM"),
            BoardFamily.RP2350,"",null,"ARM / Hazard3",12,3.3,"GP25",
            BoardSupportLevel.FUNCTIONAL,
            listOf(PowerDomain("VBUS",5.0,"input","USB"),PowerDomain("3V3_OUT",3.3,"output","GPIO rail")),
            listOf(BoardReference("Pico 2 datasheet","Raspberry Pi Ltd",ReferenceType.BOARD_DATASHEET,null,
                "https://datasheets.raspberrypi.com/pico/pico-2-datasheet.pdf")),
            listOf("RP2350-specific peripherals beyond the Pico-compatible subset are not simulated."),
            buses,usbConnector="Micro-USB",boardWidth=172f,boardHeight=300f,pcbColor=0xFF0F5C3A)
    ).associateBy { it.kind }
}
