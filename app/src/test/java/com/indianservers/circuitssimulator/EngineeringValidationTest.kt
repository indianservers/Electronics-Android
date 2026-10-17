package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.simulation.DcSolver
import com.indianservers.circuitssimulator.validation.AdvancedProjects
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Inventory is derived from production registries. A source URL is not verification evidence. */
class EngineeringValidationTest {
    private val root = File("../testing/engineering-validation")
    private fun csv(name: String, header: List<String>, rows: List<List<Any?>>) {
        root.mkdirs()
        File(root, name).writeText((listOf(header) + rows).joinToString("\n") { row ->
            row.joinToString(",") { "\"${(it ?: "UNVERIFIED").toString().replace("\"", "\"\"")}\"" }
        } + "\n")
    }

    @Test fun exportCoverageAndSourceRegistersWithoutClaimingExecution() {
        val boards = BoardRegistry.boards.values.sortedBy { it.kind.name }
        assertEquals(Kind.entries.filter { it.isBoard }.toSet(), boards.map { it.kind }.toSet())
        csv("board-capabilities.csv", listOf("board_id", "manufacturer", "product", "revision", "mcu",
            "clock_MHz", "architecture", "logic_V", "adc_bits_model", "adc_reference_V_model",
            "flash_KB", "ram_KB", "ram_bytes", "timers", "uart_TX_RX", "i2c_SDA_SCL", "spi_MOSI_MISO_SCK_CS",
            "pwm_pins", "dac_pins", "interrupt_pins", "pin_current_mA_profile", "aggregate_current_mA_profile",
            "boot_levels", "multiplexing_notes", "firmware", "bootloader", "watchdog", "power_consumption", "verification"),
            boards.map { b -> listOf(b.kind, b.manufacturer, b.product, b.revision.ifBlank { "UNVERIFIED" }, b.mcu,
                b.hardware.clockMhz, b.architecture, b.logicVoltage, b.adcBits, b.adcReferenceVoltage,
                b.hardware.flashKb, b.hardware.sramKb, b.hardware.sramBytes, b.hardware.timers,
                b.hardware.uartPins, b.hardware.i2cPins, b.hardware.spiPins,
                b.pins.filter { PinCapability.PWM in it.capabilities }.map { it.name },
                b.pins.filter { PinCapability.DAC in it.capabilities }.map { it.name },
                b.pins.filter { PinCapability.INTERRUPT in it.capabilities }.map { it.name },
                b.hardware.recommendedPinCurrentMa, b.hardware.gpioTotalCurrentMa, b.hardware.bootLevels,
                b.hardware.notes + b.warnings, "Bounded Arduino/MicroPython subset; not native CPU execution",
                "UNSUPPORTED", "UNVERIFIED", "UNVERIFIED", "Repository metadata; manufacturer audit pending") })
        csv("board-pins.csv", listOf("board_id", "index", "pin", "gpio", "adc_channel", "capabilities", "functions",
            "min_V_profile", "max_V_profile", "max_current_mA_profile", "input_only", "connectable", "warnings"),
            boards.flatMap { b -> b.pins.mapIndexed { i, p -> listOf(b.kind, i, p.name, p.gpioNumber, p.adcChannel,
                p.capabilities, p.functions, p.minVoltage, p.maxVoltage, p.maxCurrentMa, p.inputOnly, p.connectable, p.warnings) } })
        csv("datasheet-register.csv", listOf("implementation_id", "manufacturer", "part", "revision", "url", "pin_order", "model_accuracy", "limitations", "audit_status"),
            ComponentRegistry.definitions.values.sortedBy { it.kind.name }.map { d ->
                listOf(d.kind, d.datasheet.manufacturer, d.datasheet.partNumber, d.datasheet.revision,
                    d.datasheet.referenceUrl, d.datasheet.physicalPinOrder, d.modelAccuracy, d.limitations,
                    "UNVERIFIED: catalog metadata only; see source-audit.txt for independently reviewed subset") })
        csv("board-sources.csv", listOf("board_id", "publisher", "document", "revision", "url", "status"),
            boards.flatMap { b -> listOf(listOf(b.kind, b.manufacturer, "Primary board reference", b.revision, b.sourceUrl, "UNVERIFIED")) +
                b.references.map { r -> listOf(b.kind, r.publisher, r.title, r.revision, r.sourceUrl, "UNVERIFIED") } })
        val projects = listOf("Smart Agriculture Monitoring and Automated Irrigation", "Industrial IoT Factory Monitoring",
            "Smart Home Environmental Automation", "Advanced Weather Monitoring Station", "Greenhouse Climate Control System",
            "Smart Hospital Room Monitoring Simulator", "Air Quality and Pollution Monitoring Network",
            "Intelligent Water Treatment Monitoring", "Multi-Zone Fire and Gas Detection System", "Solar Energy and Battery Monitoring",
            "Smart Building Management System", "Advanced Robotics Sensor Fusion Platform", "Vehicle Diagnostic and Safety Monitoring",
            "Cold Storage and Refrigeration Monitoring", "Smart City Environmental Sensor Hub", "Automated Chemical Process Monitoring",
            "Intelligent Warehouse Monitoring", "Smart Aquaculture Monitoring", "Industrial Predictive Maintenance Platform",
            "Multi-Sensor Disaster Detection and Alert System")
        val records = boards.flatMap { b -> projects.mapIndexed { i, title ->
            val id="${b.kind.name.lowercase()}-${(i+1).toString().padStart(2,'0')}"
            val dir=File(root,"projects/$id")
            val result=File(dir,"result.txt").takeIf { it.exists() }?.readLines()?.firstOrNull() ?: "NOT_EXECUTED"
            val purpose=AdvancedProjects.scenarios[i].purpose
            val missingDomain=purpose.contains("unsupported")
            val status=if(result=="PASSED_MODELED_SCOPE" && missingDomain)"BLOCKED" else result
            listOf(b.kind, b.revision.ifBlank { "UNVERIFIED" }, i + 1, title, status,
                if(result=="PASSED_MODELED_SCOPE")10 else 0, if(result=="NOT_EXECUTED")"NOT_EXECUTED" else "JVM_FIRMWARE_AND_SOLVER",
                "Thermal/occupancy subsystem only. Native libraries, physical bus fault timing, domain-specific unmodeled sensing and calibration remain unsupported/unverified.",
                if(File(dir,"circuit.json").exists())"projects/$id/circuit.json" else "NOT_CREATED",
                if(File(dir,"firmware.ino").exists())"projects/$id/firmware.ino" else "NOT_CREATED", "SEE_ANDROID_RESULTS", "NOT_EXECUTED",
                result,if(missingDomain)"BLOCKED_UNMODELED_DOMAIN_FUNCTIONS" else "MODELED_SUBSYSTEM_ONLY",purpose)
        } }
        assertEquals(boards.size * 20, records.size)
        csv("project-records.csv", listOf("board_id", "revision", "project_number", "project", "status", "validated_sensor_placements",
            "execution", "blocker", "saved_circuit", "firmware", "android_runtime", "physical_hardware",
            "modeled_execution_status","full_project_status","full_project_limitation"), records)
        csv("board-results.csv", listOf("board", "projects", "passed", "failed", "blocked", "not_executed", "board_specific_bugs_fixed","modeled_subsystem_passed"),
            boards.map { b ->
                val rows=records.filter { it[0]==b.kind }
                listOf(b.product,20,rows.count { it[4]=="PASSED_MODELED_SCOPE" },rows.count { it[4]=="FAILED" },rows.count { it[4]=="BLOCKED" },
                    rows.count { it[4]=="NOT_EXECUTED" || it[4]=="RUNNING" },0,rows.count { it[12]=="PASSED_MODELED_SCOPE" })
            })
    }

    @Test fun simultaneousIndependentTmp36DevicesRespondAndReleaseOnSupplyFault() {
        // This is solver stress coverage, not one of the requested board projects.
        for (count in listOf(10, 15, 20)) {
            val sensors = (0 until count).map { i -> PlacedComponent(id="s$i",kind=Kind.TMP36_SENSOR,
                reference="S$i",x=0f,y=0f,parameters=mapOf("temperatureC" to (-40.0 + i * 8))) }
            val source = PlacedComponent(id="supply",kind=Kind.SOURCE,reference="V1",x=0f,y=0f,parameters=mapOf("voltage" to 3.3))
            val ground = PlacedComponent(id="ground",kind=Kind.GROUND,reference="G1",x=0f,y=0f)
            val loads = sensors.indices.map { i -> PlacedComponent(id="r$i",kind=Kind.RESISTOR,
                reference="R$i",x=0f,y=0f,parameters=mapOf("resistance" to 100000.0)) }
            fun wire(a:String, ai:Int,b:String,bi:Int)=Wire(start=TerminalRef(a,ai),end=TerminalRef(b,bi))
            val wires = listOf(wire(source.id,1,ground.id,0)) + sensors.flatMapIndexed { i,s ->
                listOf(wire(source.id,0,s.id,0),wire(ground.id,0,s.id,2),
                    wire(s.id,1,loads[i].id,0),wire(ground.id,0,loads[i].id,1)) }
            val circuit = Circuit("TMP36 $count-device solver stress",sensors+loads+source+ground,wires)
            val solved = DcSolver().solve(circuit)
            assertNull(solved.error)
            sensors.forEachIndexed { i,s ->
                // Rev H transfer equation, with the explicitly modeled 1-ohm source and 100k load.
                val expected = (.5 + .01 * (-40.0 + i * 8)) * 100000.0 / 100001.0
                assertEquals("$count devices / $i",expected,solved.nodeVoltages.getValue(TerminalRef(s.id,1)),1e-6)
            }
            val fault = DcSolver().solve(circuit.copy(components=circuit.components.map {
                if(it.id==source.id) it.copy(parameters=mapOf("voltage" to 2.6)) else it
            }))
            assertNull(fault.error)
            sensors.forEach { assertEquals(0.0,fault.nodeVoltages.getValue(TerminalRef(it.id,1)),1e-9) }
        }
    }
}
