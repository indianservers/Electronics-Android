package com.indianservers.circuitssimulator
import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.data.CircuitJson
import com.indianservers.circuitssimulator.firmware.PhaseTwoProjects
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File

class PhaseTwoInventoryTest {
    @Test fun exportProductionInventoryAndFeasibleCircuits(){
        val root=File("../testing/phase2").apply {mkdirs()}
        check(BoardRegistry.boards.keys==Kind.entries.filter {it.isBoard}.toSet())
        val boards=JSONArray()
        BoardRegistry.boards.values.forEach {b->
            boards.put(JSONObject().put("id",b.kind.name).put("manufacturer",b.manufacturer).put("product",b.product).put("revision",b.revision)
                .put("mcu",b.mcu).put("logicVoltage",b.logicVoltage).put("adcBits",b.adcBits).put("adcReferenceVoltage",b.adcReferenceVoltage)
                .put("clockMHz",b.hardware.clockMhz).put("sourceUrl",b.sourceUrl).put("datasheetStatus","REGISTRY_METADATA_NOT_INDEPENDENTLY_REAUDITED")
                .put("pins",JSONArray(b.pins.mapIndexed {i,p->JSONObject().put("index",i).put("name",p.name).put("gpio",p.gpioNumber)
                    .put("signal",p.signal).put("maxVoltage",p.maxVoltage).put("capabilities",JSONArray(p.capabilities.map {it.name}))})))
            (1..20).forEach {n->
                val dir=File(root,"projects/${b.kind.name.lowercase()}-${n.toString().padStart(2,'0')}").apply {mkdirs()}
                File(dir,"design-request.json").writeText(JSONObject().put("board",b.kind.name).put("applicationNumber",n)
                    .put("application",PhaseTwoProjects.titles[n-1]).put("hasExecutableDesign",PhaseTwoProjects.supported(n)).toString(2))
                if(PhaseTwoProjects.supported(n)){
                    val c=PhaseTwoProjects.circuit(b.kind,n)
                    val stored=File(dir,"circuit.json")
                    if(!stored.exists() || CircuitJson.decode(stored.readText())!=c)stored.writeText(CircuitJson.encode(c))
                    c.firmware.forEach {File(dir,"firmware-${it.boardId}.ino").writeText(it.source)}
                }
            }
        }
        File(root,"production-boards.json").writeText(boards.toString(2))
        File(root,"production-components.json").writeText(JSONArray(ComponentRegistry.definitions.values.map {d->
            JSONObject().put("kind",d.kind.name).put("title",d.kind.title).put("category",d.kind.category).put("terminals",PlacedComponent(kind=d.kind,reference="",x=0f,y=0f).terminalCount)
                .put("manufacturer",d.datasheet.manufacturer).put("part",d.datasheet.partNumber).put("revision",d.datasheet.revision)
                .put("sourceUrl",d.datasheet.referenceUrl).put("sourceNotes",d.datasheet.sourceNotes).put("limitations",JSONArray(d.limitations))
                .put("parameters",JSONArray(d.parameters.map {p->JSONObject().put("key",p.key).put("default",p.default)}))
        }).toString(2))
    }
}
