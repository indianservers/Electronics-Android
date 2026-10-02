package com.indianservers.circuitssimulator.intelligence

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.embedded.PinMode

internal object FirmwareDiagnostics:DiagnosticRule {
    override val id="FIRMWARE"
    override val priority=18
    override fun inspect(input:IntelligenceInput,facts:CircuitFacts):List<IntelligenceIssue> {
        val issues=mutableListOf<IntelligenceIssue>()
        val firmware=input.firmware ?: return issues
        val boards=input.circuit.components.filter { it.kind.isBoard }
        boards.forEach { board ->
            val definition=BoardRegistry.boards.getValue(board.kind)
            val source=firmware.sources[board.id] ?: return@forEach
            val usages=firmware.usages[board.id].orEmpty()
            usages.forEach { usage ->
                val index=definition.index(usage.pin)
                if(index<0) return@forEach
                val pin=facts.pin(board,index)
                if(usage.api in setOf("digitalWrite","analogWrite","tone","Servo.attach") &&
                    facts.onNet(pin).none { it.componentId!=board.id })
                    issues+=IntelligenceIssue("CODE_PIN_OPEN:${board.id}:${usage.pin}","CODE_PIN_OPEN",
                        IssueSeverity.WARNING,IssueCategory.GPIO,
                        "Code writes ${usage.pin} but it is not connected",
                        "${board.reference} ${usage.pin} is used by ${usage.api} on line ${usage.line}.",
                        "The firmware call is valid, but that header pin has no wire. Connect the load to ${usage.pin}.",
                        setOf(board.id),setOf(pin),
                        evidence=listOf(DiagnosticEvidence("Source",usage.raw),
                            DiagnosticEvidence("Line",usage.line.toString())))
                if(usage.api=="digitalRead" && facts.onNet(pin).none { it.componentId!=board.id }) {
                    val pullup=usages.any { it.pin==usage.pin && it.api=="INPUT_PULLUP" } ||
                        firmware.pins[board.id]?.get(usage.pin)?.mode==PinMode.INPUT_PULLUP
                    if(!pullup)
                        issues+=IntelligenceIssue("CODE_INPUT_OPEN:${board.id}:${usage.pin}","CODE_INPUT_OPEN",
                            IssueSeverity.WARNING,IssueCategory.GPIO,
                            "digitalRead(${usage.pin}) has no connected input",
                            "Line ${usage.line} samples ${usage.pin}, which is floating in this circuit.",
                            "Wire a button, sensor, or other driver to ${usage.pin}, or enable INPUT_PULLUP with a path to ground.",
                            setOf(board.id),setOf(pin))
                }
                if(usage.api=="analogRead") {
                    val sensors=facts.onNet(pin).mapNotNull { facts.parts[it.componentId] }
                        .filter { it.kind in setOf(Kind.POTENTIOMETER,Kind.LM35_SENSOR,Kind.LDR,Kind.I2C_TEMP_SENSOR) }
                    if(sensors.isEmpty() && facts.onNet(pin).none { it.componentId!=board.id })
                        issues+=IntelligenceIssue("ADC_UNCONNECTED:${board.id}:${usage.pin}","ADC_UNCONNECTED",
                            IssueSeverity.WARNING,IssueCategory.ANALOG,
                            "analogRead(${usage.pin}) is not connected to a sensor",
                            "Line ${usage.line} will read near zero or noise because ${usage.pin} has no analog source.",
                            "Connect the sensor output to ${usage.pin}.",setOf(board.id),setOf(pin))
                    val voltage=facts.voltage(pin)
                    if(voltage!=null && voltage>definition.logicVoltage+0.05)
                        issues+=IntelligenceIssue("ADC_OVER_RANGE:${board.id}:${usage.pin}","ADC_OVER_RANGE",
                            IssueSeverity.WARNING,IssueCategory.ANALOG,
                            "${usage.pin} is ${"%.2f".format(voltage)} V, above the ${definition.logicVoltage} V ADC range",
                            "The runtime clamps the conversion. ${definition.product} ADC is not 5 V-tolerant if this is a 3.3 V board.",
                            "Attenuate the signal or use a matching voltage domain.",setOf(board.id),setOf(pin))
                }
            }
            val written=usages.filter { it.api in setOf("digitalWrite","analogWrite") }.map { it.pin }.toSet()
            input.circuit.components.filter { it.kind in setOf(Kind.LED,Kind.RED_LED,Kind.GREEN_LED,Kind.BLUE_LED) }
                .forEach { led ->
                    val anode=facts.onNet(facts.pin(led,0))
                    val boardPins=definition.pins.mapIndexedNotNull { i,pin ->
                        if(anode.any { facts.same(it,facts.pin(board,i)) }) pin.name else null }
                    if(boardPins.isNotEmpty() && written.isNotEmpty() && boardPins.none { it in written })
                        issues+=IntelligenceIssue("CODE_LED_MISMATCH:${board.id}:${led.id}","CODE_LED_MISMATCH",
                            IssueSeverity.WARNING,IssueCategory.GPIO,
                            "Code writes ${written.joinToString()}, but the LED is on ${boardPins.joinToString()}",
                            "${led.reference} is electrically on ${board.reference} ${boardPins.joinToString()}.",
                            "Change the firmware pin or rewire the LED so they match.",
                            setOf(board.id,led.id))
                }
            firmware.pins[board.id]?.forEach { (name,reading) ->
                if(reading.overCurrent)
                    issues+=IntelligenceIssue("GPIO_OVERCURRENT:${board.id}:$name","GPIO_OVERCURRENT",
                        IssueSeverity.ERROR,IssueCategory.GPIO,
                        "$name is sourcing or sinking too much current",
                        "${board.reference} $name current exceeds the educational GPIO limit.",
                        "Add a series resistor or a driver. GPIO is modeled with finite output resistance, not an ideal source.",
                        setOf(board.id),setOf(facts.pin(board,definition.index(name))))
                if(reading.mode==PinMode.OUTPUT && reading.damaged)
                    issues+=IntelligenceIssue("GPIO_DAMAGED:${board.id}:$name","GPIO_DAMAGED",
                        IssueSeverity.CRITICAL,IssueCategory.GPIO,
                        "$name driver opened after overload",
                        "The educational I²t model opened this pin.",
                        "Reset the firmware after fixing the short.",setOf(board.id))
            }
            usages.forEach { usage ->
                val pin=definition.pin(usage.pin) ?: return@forEach
                if(usage.api in setOf("digitalWrite","pinMode") && pin.inputOnly)
                    issues+=IntelligenceIssue("INPUT_ONLY_GPIO:${board.id}:${usage.pin}","INPUT_ONLY_GPIO",
                        IssueSeverity.ERROR,IssueCategory.GPIO,
                        "${usage.pin} on this ${definition.product} is input-only and cannot be configured as an output.",
                        "${definition.displayName(usage.pin)} is documented as input-only on ${definition.revision.ifBlank { definition.product }}.",
                        "Choose a GPIO with digital-output capability.",
                        setOf(board.id),setOf(facts.pin(board,definition.index(usage.pin))))
                if(usage.api in setOf("analogWrite","tone","Servo.attach") && PinCapability.PWM !in pin.capabilities)
                    issues+=IntelligenceIssue("UNSUPPORTED_PWM:${board.id}:${usage.pin}","UNSUPPORTED_PWM",
                        IssueSeverity.ERROR,IssueCategory.GPIO,
                        "${usage.pin} on this ${definition.product} is not PWM-capable.",
                        "Line ${usage.line} requests PWM on a pin the board model does not mark PWM.",
                        "Use a PWM-capable header for this board.",
                        setOf(board.id),setOf(facts.pin(board,definition.index(usage.pin))))
            }
            definition.pins.forEachIndexed { index,pin ->
                if(pin.warnings.any { it.contains("boot",ignoreCase=true) || it.contains("strapping",ignoreCase=true) } &&
                    facts.onNet(facts.pin(board,index)).any { it.componentId!=board.id })
                    issues+=IntelligenceIssue("BOOT_PIN:${board.id}:${pin.name}","BOOT_PIN",
                        IssueSeverity.WARNING,IssueCategory.BOARD_COMPATIBILITY,
                        "${pin.name} is boot-sensitive on ${definition.product}",
                        pin.warnings.first(),
                        "Avoid a strong external pull that holds this pin in the wrong state at reset. Bootloader behavior is not simulated.",
                        setOf(board.id),setOf(facts.pin(board,index)))
            }
            if(source.contains("Serial.begin") && definition.pins.any { PinCapability.UART in it.capabilities }) {
                val tx=definition.pins.indexOfFirst { it.name in setOf("TX","D1") }
                if(tx>=0 && facts.onNet(facts.pin(board,tx)).none { part ->
                    facts.parts[part.componentId]?.kind in setOf(Kind.SERIAL_TERMINAL) ||
                        (facts.parts[part.componentId]?.kind?.isBoard==true && part.componentId!=board.id) })
                    issues+=IntelligenceIssue("SERIAL_TX_OPEN:${board.id}","SERIAL_TX_OPEN",
                        IssueSeverity.SUGGESTION,IssueCategory.DIGITAL,
                        "Serial is initialized but TX is not connected",
                        "Serial.print still appears in the Serial Monitor. A UART peer or Serial Terminal is optional.",
                        "Connect TX to another board RX or a Serial Terminal for bus traffic.",
                        setOf(board.id))
            }
        }
        if(firmware.missingI2cPullups)
            issues+=IntelligenceIssue("I2C_PULLUP:circuit","I2C_PULLUP",IssueSeverity.WARNING,IssueCategory.DIGITAL,
                "I²C bus has no explicit pull-up resistors",
                "SDA/SCL should normally be pulled to the logic rail through 4.7 kΩ resistors.",
                "The protocol layer can still complete event-based transactions, and this is disclosed as an educational simplification.",
                evidence=listOf(DiagnosticEvidence("Bus pull-ups","auto-provided logically")))
        firmware.duplicateAddresses.forEach { address ->
            issues+=IntelligenceIssue("I2C_ADDRESS:$address","I2C_ADDRESS",IssueSeverity.ERROR,IssueCategory.DIGITAL,
                "Two devices on this I²C bus use address 0x${address.toString(16)}",
                "I²C targets must have unique 7-bit addresses.",
                "Change one device address parameter.",
                evidence=listOf(DiagnosticEvidence("Address","0x${address.toString(16)}")))
        }
        return issues
    }
}

data class FirmwareIntel(
    val sources:Map<String,String> = emptyMap(),
    val usages:Map<String,List<com.indianservers.circuitssimulator.firmware.PinUsage>> = emptyMap(),
    val pins:Map<String,Map<String,com.indianservers.circuitssimulator.simulation.embedded.GpioReading>> = emptyMap(),
    val missingI2cPullups:Boolean=false,
    val duplicateAddresses:List<Int> = emptyList()
)
