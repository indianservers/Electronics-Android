package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import org.junit.Assert.*
import org.junit.Test

class PartDatabaseTest {
    @Test fun manufacturerRecordsHaveIdentityPinsAndFiniteParameters() {
        val realParts=listOf(Kind.DIODE,Kind.RECTIFIER,Kind.NPN_BJT,Kind.PNP_BJT,Kind.NMOS)
        realParts.forEach { kind ->
            val definition=ComponentRegistry.definitions.getValue(kind)
            val source=definition.datasheet
            assertFalse(source.manufacturer.isNullOrBlank())
            assertFalse(source.partNumber.isNullOrBlank())
            assertFalse(source.referenceUrl.isNullOrBlank())
            assertFalse(source.packageName.isNullOrBlank())
            val placed=PlacedComponent(kind=kind,reference="X1",x=0f,y=0f)
            assertEquals(placed.terminalCount,source.physicalPinOrder.size)
            assertTrue(definition.parameters.all { it.default.isFinite() && it.min.isFinite() && it.max.isFinite() })
            assertEquals(1,placed.modelVersion)
            assertNotNull(placed.databaseId)
        }
    }
}
