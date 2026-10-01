package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.ComponentRegistry
import com.indianservers.circuitssimulator.domain.Kind
import com.indianservers.circuitssimulator.domain.ModelAccuracy
import org.junit.Assert.*
import org.junit.Test

class RegistryValidationTest {
    @Test fun allShippedDefinitionsHaveValidRangesAndPinMaps() {
        assertEquals(emptyList<String>(),ComponentRegistry.validate())
        assertEquals(Kind.entries.size,ComponentRegistry.definitions.size)
        assertEquals(ModelAccuracy.IDEAL,ComponentRegistry.definitions.getValue(Kind.IDEAL_OPAMP).modelAccuracy)
        assertEquals(ModelAccuracy.SIMPLIFIED,ComponentRegistry.definitions.getValue(Kind.OPAMP).modelAccuracy)
    }
}
