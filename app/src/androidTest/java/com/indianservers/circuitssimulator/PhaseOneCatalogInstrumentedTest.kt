package com.indianservers.circuitssimulator

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.ui.SimulatorViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the complete new platform with Android's real JSON implementation. */
@RunWith(AndroidJUnit4::class)
class PhaseOneCatalogInstrumentedTest {
    private val newParts by lazy {
        PhaseOneParts.definitions + DigitalParts.definitions +
            ElectromechanicalParts.definitions + SemiconductorSwitchParts.definitions +
            IcParts.definitions
    }

    @Test fun everyNewPartHasPinsPropertiesAndSurvivesWiringSaveAndReload() {
        assertEquals(50, newParts.size)
        assertEquals(50, newParts.map { it.kind }.distinct().size)
        val all = newParts + BoardRegistry.boards.keys.map { ComponentRegistry.definitions.getValue(it) }
        assertEquals(61, all.size)
        all.forEachIndexed { index, definition ->
            val part = PlacedComponent(kind = definition.kind, reference = "X${index + 1}",
                x = (index % 8) * 100f, y = (index / 8) * 130f)
            val ground = PlacedComponent(kind = Kind.GROUND, reference = "G${index + 1}",
                x = part.x, y = part.y + 60f)
            assertTrue("${definition.kind} pins", part.terminalCount > 0)
            assertTrue("${definition.kind} description", definition.description.isNotBlank())
            assertTrue("${definition.kind} status", definition.supportStatus != ComponentSupportStatus.PREVIEW)
            assertTrue("${definition.kind} search", ComponentRegistry.search(definition.kind.title)
                .any { it.kind == definition.kind })
            val wire = Wire(start = TerminalRef(part.id, 0), end = TerminalRef(ground.id, 0))
            val circuit = Circuit("Phase 1 ${definition.kind.title}", listOf(part, ground), listOf(wire))
            val restored = CircuitJson.decode(CircuitJson.encode(circuit))
            assertEquals("${definition.kind} round trip", circuit, restored)
            assertEquals("${definition.kind} pin wire", wire, restored.wires.single())
            assertEquals("${definition.kind} parameter definitions", definition.parameters.map { it.key }.distinct().size,
                definition.parameters.size)
        }
    }

    @Test fun everyNewPartUsesDesignerPlacementEditAndWirePath() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val model=SimulatorViewModel(ApplicationProvider.getApplicationContext<Application>())
            (newParts + BoardRegistry.boards.keys.map { ComponentRegistry.definitions.getValue(it) })
                .forEach { definition ->
                    val ground=PlacedComponent(kind=Kind.GROUND,reference="G1",x=50f,y=100f)
                    model.loadSample(Circuit("Placement check",listOf(ground),emptyList()))
                    model.add(definition.kind,100f,100f)
                    val added=model.state.value.circuit.components.single { it.kind==definition.kind }
                    assertEquals(definition.kind,added.kind)
                    assertEquals(100f,added.x)
                    model.connect(TerminalRef(added.id,0),TerminalRef(ground.id,0))
                    assertEquals(1,model.state.value.circuit.wires.size)
                    definition.parameters.firstOrNull()?.let { parameter ->
                        model.setParameter(added.id,parameter.key,parameter.default)
                    }
                    model.move(added.id,125f,120f)
                    val moved=model.state.value.circuit.components.single { it.id==added.id }
                    assertEquals(125f,moved.x)
                    assertEquals(1,model.state.value.circuit.wires.size)
                    val restored=CircuitJson.decode(CircuitJson.encode(model.state.value.circuit))
                    assertEquals(model.state.value.circuit,restored)
                    model.remove(added.id)
                    assertTrue(model.state.value.circuit.wires.isEmpty())
                }
        }
    }
}
