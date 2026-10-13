package com.indianservers.circuitssimulator.domain

/** Revision-specific facts. CPU clocks and memory capacities are metadata, not emulated resources. */
object AdditionalBoardHardware {
    private val avr32u4=BoardHardwareProfile("arduino-atmega32u4",16,32,null,
        "Timer0: 8-bit; Timer1/3: 16-bit; Timer4: high-speed 10-bit",
        listOf("D1" to "D0"),"D2" to "D3",listOf("MOSI","MISO","SCK","D10"),
        4.5,5.5,20.0,200.0,mapOf("VIN" to (7.0..12.0)),
        uartNames=listOf("Serial1"),usbSerial=true,sramBytes=2560,
        notes=listOf("Serial is USB CDC; Serial1 is the D1/D0 UART. SPI is not on D11–D13.",
            "A6–A11 share D4/D6/D8/D9/D10/D12. SS uses external D10 in this simulator; chip SS/D17 is tied to the RX LED on Leonardo."))
    fun forKind(kind:Kind):BoardHardwareProfile=when(kind) {
        Kind.ARDUINO_LEONARDO -> avr32u4.copy(profileId="arduino-leonardo-a000057")
        Kind.ARDUINO_MICRO -> avr32u4.copy(profileId="arduino-micro-a000053",
            spiPins=listOf("D16","D14","D15","D17"),regulatedInputs=mapOf("VIN" to (7.0..12.0)),
            notes=avr32u4.notes+"Micro also exposes SPI on D14/D15/D16/D17; ICSP shares the same nets.")
        Kind.ARDUINO_DUE -> BoardHardwareProfile("arduino-due-a000062",84,512,96,
            "Nine 32-bit timer/counter channels; PWM controller",
            listOf("D1" to "D0","D18" to "D19","D16" to "D17","D14" to "D15"),
            "D20" to "D21",listOf("MOSI","MISO","SCK","D10"),3.0,3.6,3.0,130.0,
            mapOf("VIN" to (7.0..12.0),"5V" to (4.75..5.25)),
            uartNames=listOf("Serial","Serial1","Serial2","Serial3"),
            notes=listOf("3.3 V GPIO; never use 5 V GPIO signals. ADC hardware is 12-bit; Arduino default analogRead is 10-bit.",
                "3 mA is a conservative simulation current policy; physical pin limits vary by SAM3X pad (3/15 mA source, 6/9 mA sink).",
                "Native USB, DAC, CAN, Wire1 and JTAG are hardware references only. Serial models the programming-port UART."))
        Kind.ARDUINO_UNO_R4_MINIMA -> BoardHardwareProfile("arduino-uno-r4-minima-abx00080",48,256,32,
            "RA4M1 GPT/AGT timers (register programming not emulated)",listOf("D1" to "D0"),
            "A4" to "A5",listOf("D11","D12","D13","D10"),4.5,5.5,8.0,
            regulatedInputs=mapOf("VIN" to (6.0..24.0)),uartNames=listOf("Serial1"),usbSerial=true,
            notes=listOf("5 V GPIO. 14-bit ADC hardware; Arduino default analogRead is 10-bit.",
                "A0 12-bit DAC, A1–A3 op-amp, CAN, RTC and USB HID are not simulated."))
        Kind.RASPBERRY_PICO_2_W -> BoardHardwareProfiles.forKind(Kind.RASPBERRY_PICO_2).copy(
            profileId="raspberry-pi-pico2w-rp2350-cyw43439",
            notes=listOf("RP2350: 150 MHz, 4 MiB flash, 520 KiB SRAM. CYW43439 wireless hardware is metadata only.",
                "WL_LED is CYW43439 WL_GPIO0; GP25 belongs to the wireless interface, not the user LED.",
                "PIO, CPU instructions and security registers are not emulated."))
        Kind.ESP32_S3_DEVKIT -> BoardHardwareProfile("esp32-s3-devkitc1-v1.1-n8r8",240,8192,512,
            "Four general-purpose timers; eight LEDC channels",listOf("TX" to "RX","IO17" to "IO18"),
            "IO8" to "IO9",listOf("IO11","IO13","IO12","IO10"),3.0,3.6,
            regulatedInputs=mapOf("5V" to (4.75..5.25)),bootLevels=mapOf("IO0" to true),
            uartNames=listOf("Serial","Serial1"),
            notes=listOf("v1.1 N8R8 module: 8 MiB flash / 8 MiB octal PSRAM; GPIO35–37 reserved for PSRAM.",
                "RGB LED is GPIO38 on v1.1 (GPIO48 on v1.0); addressable LED protocol is not simulated.",
                "GPIO46 supports input AND output on S3. Do not copy S2's input-only restriction.",
                "USB OTG/JTAG, touch, Wi-Fi and BLE are hardware metadata only."))
        Kind.ESP32_C6_DEVKIT -> BoardHardwareProfile("esp32-c6-devkitc1-v1.2",160,8192,512,
            "Two general-purpose timers; six LEDC channels",listOf("TX" to "RX"),
            "IO23" to "IO22",listOf("IO19","IO20","IO21","IO18"),3.0,3.6,
            regulatedInputs=mapOf("5V" to (4.75..5.25)),bootLevels=mapOf("IO9" to true),
            notes=listOf("512 KiB HP SRAM plus 16 KiB LP SRAM. 8 MiB WROOM-1 flash.",
                "GPIO8 RGB LED, Wi-Fi 6, BLE, Zigbee/Thread, USB JTAG and LP peripherals are not simulated.",
                "Wire defaults follow Arduino core: GPIO23 SDA / GPIO22 SCL; LP I2C GPIO6/7 is hardware metadata."))
        Kind.ESP32_S2_SAOLA -> BoardHardwareProfile("esp32-s2-saola1-v1.2-wrover",240,4096,320,
            "Four general-purpose timers; eight LEDC channels",listOf("IO43" to "IO44"),
            "IO8" to "IO9",listOf("IO35","IO37","IO36","IO34"),3.0,3.6,
            regulatedInputs=mapOf("5V0" to (4.75..5.25)),bootLevels=mapOf("IO0" to true),
            notes=listOf("WROVER chip revision 0: 4 MiB flash, 2 MiB PSRAM, 320 KiB SRAM plus 16 KiB RTC SRAM.",
                "GPIO26 is PSRAM CS and reserved; GPIO46 is input-only. RGB LED is GPIO18.",
                "S2 has Wi-Fi but no Bluetooth. WROVER module is a legacy EOL part.",
                "ADC uses 13-bit default for S2 revision 0, following Arduino-ESP32 ADC-112 workaround; analog behavior is simplified."))
        else -> error("No hardware profile for $kind")
    }
}
