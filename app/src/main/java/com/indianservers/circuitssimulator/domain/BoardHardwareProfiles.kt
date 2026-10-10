package com.indianservers.circuitssimulator.domain

data class BoardHardwareProfile(
    val profileId:String,val clockMhz:Int?=null,val flashKb:Int?=null,val sramKb:Int?=null,
    val timers:String="Not characterized",val uartPins:List<Pair<String,String>> = emptyList(),
    val i2cPins:Pair<String,String>?=null,val spiPins:List<String> = emptyList(),
    val minimumLogicSupply:Double,val maximumLogicSupply:Double,
    val recommendedPinCurrentMa:Double?=null,val gpioTotalCurrentMa:Double?=null,
    val regulatedInputs:Map<String,ClosedFloatingPointRange<Double>> = emptyMap(),
    val bootLevels:Map<String,Boolean> = emptyMap(),val notes:List<String> = emptyList(),
    val uartNames:List<String> = emptyList(),val uartControllerIds:List<Int> = emptyList(),
    val usbSerial:Boolean=false
)

/** Hardware facts are distinct from the bounded interpreter and educational damage policy. */
object BoardHardwareProfiles {
    private val uno=BoardHardwareProfile("arduino-uno-rev3-atmega328p",16,32,2,
        "Timer0/2: 8-bit; Timer1: 16-bit",listOf("D1" to "D0"),"A4" to "A5",
        listOf("D11","D12","D13","D10"),4.5,5.5,20.0,200.0,mapOf("VIN" to (7.0..12.0)))
    private val nano=uno.copy(profileId="arduino-nano-classic-a000005",notes=listOf("A6/A7 are ADC-only."))
    private val mega=uno.copy(profileId="arduino-mega2560-rev3-a000067",flashKb=256,sramKb=8,
        timers="Timer0/2: 8-bit; Timer1/3/4/5: 16-bit",
        uartPins=listOf("D1" to "D0","D18" to "D19","D16" to "D17","D14" to "D15"),
        i2cPins="D20" to "D21",spiPins=listOf("D51","D50","D52","D53"))
    private val node=BoardHardwareProfile("nodemcu-devkit-v1.0-esp12e",80,4096,null,
        "ESP8266 software PWM; system timer",listOf("TX" to "RX","D4" to ""),"D2" to "D1",
        listOf("D7","D6","D5","D8"),2.5,3.6,12.0,null,mapOf("VIN" to (4.75..5.25)),
        mapOf("D3" to true,"D4" to true,"D8" to false),
        listOf("A0: 220k/100k board divider, 0–3.2 V; bare ESP8266 ADC is 0–1 V.",
            "Clock can be configured to 160 MHz in hardware; interpreter is virtual-time based.",
            "Serial1 is TX-only on GPIO2. GPIO16 supports software PWM but not GPIO interrupts."))
    private val esp=BoardHardwareProfile("esp32-devkitc-v4-wroom32e",240,null,520,
        "4 general-purpose timers; 16 LEDC channels",listOf("TX" to "RX","IO17" to "IO16"),
        "IO21" to "IO22",listOf("IO23","IO19","IO18","IO5"),3.0,3.6,
        regulatedInputs=mapOf("5V" to (4.75..5.25)),bootLevels=mapOf("IO0" to true,"IO12" to false),
        notes=listOf("GPIO34–39 are input-only without internal pull-up/down.",
            "DevKitC V4 has a power LED, no GPIO2 user LED. DAC25/26 and touch are chip capabilities.",
            "WROOM-32E flash is 4/8/16 MB depending on module suffix; capacity is unspecified without it.",
            "UART1 default pins conflict with flash; supported UART2 is GPIO17 TX / GPIO16 RX."),
        uartNames=listOf("Serial","Serial2"),uartControllerIds=listOf(0,2))
    private val pico=BoardHardwareProfile("raspberry-pi-pico-rp2040",133,2048,264,
        "4 alarm timer; 8 PWM slices / 16 channels; 2 PIO blocks",listOf("GP0" to "GP1","GP4" to "GP5"),
        "GP4" to "GP5",listOf("GP3","GP0","GP2","GP1"),1.8,3.63,12.0,50.0,
        mapOf("VSYS" to (1.8..5.5),"VBUS" to (4.75..5.25)),
        notes=listOf("12 mA is maximum configurable pad drive strength; 50 mA aggregate GPIO current limit.",
            "PIO and timer register programming are reference metadata, not instruction emulation."),
        uartNames=listOf("Serial1","Serial2"),usbSerial=true)
    private val c3=BoardHardwareProfile("esp32-c3-devkitm-1-mini1",clockMhz=160,flashKb=4096,sramKb=400,
        timers="Two general-purpose timers; six LEDC PWM channels",
        uartPins=listOf("TX" to "RX"),i2cPins="IO8" to "IO9",spiPins=listOf("IO7","IO2","IO6","IO10"),
        minimumLogicSupply=3.0,maximumLogicSupply=3.6,
        regulatedInputs=mapOf("5V" to (4.75..5.25)),bootLevels=mapOf("IO9" to true,"IO8" to true),
        notes=listOf("GPIO8 drives an addressable RGB LED; its serial LED protocol is not simulated.",
            "UART1 remapping is not implemented."))
    private val nano33=BoardHardwareProfile("arduino-nano33-iot-abx00027",clockMhz=48,flashKb=256,
        timers="SAMD21 timer/counter resources: hardware reference only",uartPins=listOf("D1" to "D0"),
        i2cPins="A4" to "A5",spiPins=listOf("D11","D12","D13","D10"),
        minimumLogicSupply=3.0,maximumLogicSupply=3.6,uartNames=listOf("Serial1"),usbSerial=true,
        notes=listOf("Serial is USB CDC; Serial1 is the D1/D0 physical UART.",
            "Internal NINA, IMU and crypto devices are not simulated. VIN regulator limits are not characterized in this profile."))
    fun forKind(kind:Kind):BoardHardwareProfile=when(kind) {
        Kind.ARDUINO_UNO -> uno
        Kind.ARDUINO_NANO -> nano
        Kind.ARDUINO_MEGA -> mega
        Kind.NODEMCU_ESP8266 -> node
        Kind.WEMOS_D1_MINI -> node.copy(profileId="lolin-d1-mini-v4",regulatedInputs=mapOf("5V" to (4.75..5.25)))
        Kind.ESP32_DEVKIT -> esp
        Kind.ESP32_C3_DEVKIT -> c3
        Kind.ARDUINO_NANO_33_IOT -> nano33
        Kind.RASPBERRY_PICO -> pico
        Kind.RASPBERRY_PICO_W -> pico.copy(profileId="raspberry-pi-pico-w-rp2040-cyw43439",
            notes=pico.notes+"WL_LED is CYW43439 WL_GPIO0; GP25 is not a header or user LED.")
        Kind.RASPBERRY_PICO_2 -> pico.copy(profileId="raspberry-pi-pico2-rp2350",clockMhz=150,flashKb=4096,sramKb=520,
            timers="RP2350: 16 PWM channels exposed by Pico 2; 12 PIO state machines",
            notes=pico.notes+"RP2350 instruction sets, security, PIO and internal peripherals are not emulated.")
        else -> error("No hardware profile for $kind")
    }
    fun enrich(board:BoardDefinition):BoardDefinition {
        val profile=board.hardware
        val pins=board.pins.map { pin ->
            val n=pin.gpioNumber
            val functions=buildSet {
                profile.uartPins.forEachIndexed { i,pair ->
                    val controller=profile.uartControllerIds.getOrNull(i) ?: i
                    if(pin.name==pair.first) add("UART$controller TX")
                    if(pin.name==pair.second) add("UART$controller RX")
                }
                profile.i2cPins?.let { if(pin.name==it.first) add("I2C SDA");if(pin.name==it.second) add("I2C SCL") }
                profile.spiPins.forEachIndexed { i,name -> if(pin.name==name) add("SPI ${listOf("MOSI","MISO","SCK","CS")[i]}") }
                if(PinCapability.ANALOG_INPUT in pin.capabilities) add("ADC ${pin.adcChannel}")
                if(board.family in setOf(BoardFamily.RP2040,BoardFamily.RP2350) && n!=null) {
                    add("PWM slice ${(n/2)%8} ${if(n%2==0) "A" else "B"}")
                    add("I2C${(n/2)%2} ${if(n%2==0) "SDA" else "SCL"}")
                    add("SPI${(n/8)%2} ${listOf("MISO","CS","SCK","MOSI")[n%4]}")
                    add("PIO0/PIO1")
                }
            }
            val interrupt=when(board.family) {
                BoardFamily.ARDUINO_AVR -> n in if(board.kind==Kind.ARDUINO_MEGA) setOf(2,3,18,19,20,21) else setOf(2,3)
                BoardFamily.ESP8266 -> n!=null && n!=16
                else -> PinCapability.DIGITAL_INPUT in pin.capabilities
            }
            pin.copy(functions=functions,capabilities=pin.capabilities+
                (if(functions.any { it.startsWith("UART") }) setOf(PinCapability.UART) else emptySet())+
                (if(interrupt) setOf(PinCapability.INTERRUPT) else emptySet())+
                (if(board.family==BoardFamily.ESP32 && n in setOf(25,26)) setOf(PinCapability.DAC) else emptySet())+
                (if(board.family==BoardFamily.ESP32 && n in setOf(0,2,4,12,13,14,15,27,32,33)) setOf(PinCapability.TOUCH) else emptySet()),
                physicalNumber=if(board.kind in setOf(Kind.ARDUINO_UNO,Kind.ARDUINO_MEGA,Kind.NODEMCU_ESP8266,Kind.ESP32_DEVKIT) ||
                    pin.type==PinType.ONBOARD) null else pin.physicalNumber,
                physicalLabel=if(board.family==BoardFamily.ARDUINO_AVR && pin.name.startsWith("D"))
                    pin.name.drop(1)+(if(pin.name=="D0") "/RX" else if(pin.name=="D1") "/TX" else
                        if(PinCapability.PWM in pin.capabilities) "~" else "") else pin.name,
                maxCurrentMa=if(PinCapability.DIGITAL_OUTPUT in pin.capabilities) profile.recommendedPinCurrentMa else null,
                pullUpSupported=!(board.family==BoardFamily.ESP32 && pin.inputOnly) && !(board.family==BoardFamily.ESP8266 && n==16),
                pullDownSupported=(board.family in setOf(BoardFamily.RP2040,BoardFamily.RP2350,BoardFamily.ESP32,BoardFamily.ESP32_C3) && !pin.inputOnly) ||
                    (board.family==BoardFamily.ESP8266 && n==16),
                pwmResource=if(board.family in setOf(BoardFamily.RP2040,BoardFamily.RP2350) && n!=null) "PWM${(n/2)%8}" else null)
        }
        val verified=board.kind in setOf(Kind.ARDUINO_UNO,Kind.ARDUINO_MEGA,Kind.ARDUINO_NANO,
            Kind.NODEMCU_ESP8266,Kind.ESP32_DEVKIT,Kind.RASPBERRY_PICO,Kind.RASPBERRY_PICO_W)
        val extraReferences=when(board.kind) {
            Kind.ARDUINO_NANO -> listOf(BoardReference("Nano Classic datasheet","Arduino",ReferenceType.BOARD_DATASHEET,
                sourceUrl="https://docs.arduino.cc/resources/datasheets/A000005-datasheet.pdf"))
            Kind.ARDUINO_MEGA -> listOf(BoardReference("Mega 2560 Rev3 datasheet","Arduino",ReferenceType.BOARD_DATASHEET,
                sourceUrl="https://docs.arduino.cc/resources/datasheets/A000067-datasheet.pdf"),
                BoardReference("ATmega2560 product and datasheets","Microchip",ReferenceType.MCU_DATASHEET,
                    sourceUrl="https://www.microchip.com/en-us/product/ATmega2560"))
            Kind.NODEMCU_ESP8266 -> listOf(BoardReference("ESP8266EX datasheet","Espressif",ReferenceType.MCU_DATASHEET,
                sourceUrl="https://documentation.espressif.com/0a-esp8266ex_datasheet_en.html"),
                BoardReference("NodeMCU V1.0 schematic","NodeMCU",ReferenceType.SCHEMATIC,
                    sourceUrl="https://github.com/nodemcu/nodemcu-devkit-v1.0/blob/master/NODEMCU_DEVKIT_V1.0.PDF"),
                BoardReference("ESP8266 Arduino core IO and PWM reference","ESP8266 Arduino core",ReferenceType.USER_GUIDE,
                    sourceUrl="https://arduino-esp8266.readthedocs.io/en/latest/reference.html"))
            Kind.RASPBERRY_PICO,Kind.RASPBERRY_PICO_W -> listOf(BoardReference("RP2040 datasheet","Raspberry Pi Ltd",ReferenceType.MCU_DATASHEET,
                sourceUrl="https://datasheets.raspberrypi.org/rp2040/rp2040-datasheet.pdf"))
            else -> emptyList()
        }
        return board.copy(pins=pins,adcReferenceVoltage=if(board.kind==Kind.NODEMCU_ESP8266) 3.2 else board.adcReferenceVoltage,
            references=(board.references+extraReferences).map { it.copy(applicableRevision=board.revision,
                    lastVerified=if(verified) "2026-10-04" else null) },
            ledBuiltin=if(board.kind in setOf(Kind.ESP32_DEVKIT,Kind.ESP32_C3_DEVKIT)) null else board.ledBuiltin)
    }
}
