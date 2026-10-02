package com.indianservers.circuitssimulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.BoardRegistry
import com.indianservers.circuitssimulator.domain.ComponentRegistry
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.firmware.FirmwareExamples
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseFiveBBoardInstrumentedTest {
    @Test fun savedEsp32CircuitRestoresExactKindAndWires() {
        val original=FirmwareExamples.esp32OledCircuit()
        val restored=CircuitJson.decode(CircuitJson.encode(original))
        assertEquals(Kind.ESP32_DEVKIT,restored.components.first { it.kind.category=="Development Boards" }.kind)
        assertEquals(original.wires.size,restored.wires.size)
        assertEquals(original.firmware.first().source,restored.firmware.first().source)
    }

    @Test fun catalogFindsBoardsByMcuAndAlias() {
        assertEquals(11,BoardRegistry.boards.size)
        assertTrue(ComponentRegistry.search("ATmega328").any { it.kind==Kind.ARDUINO_UNO })
        assertTrue(ComponentRegistry.search("GPIO5").isNotEmpty() ||
            BoardRegistry.boards.getValue(Kind.NODEMCU_ESP8266).resolve(5)=="D1")
        assertEquals("GP25",BoardRegistry.boards.getValue(Kind.RASPBERRY_PICO).resolve("LED_BUILTIN"))
        assertEquals("WL_LED",BoardRegistry.boards.getValue(Kind.RASPBERRY_PICO_W).resolve("LED_BUILTIN"))
    }
}
