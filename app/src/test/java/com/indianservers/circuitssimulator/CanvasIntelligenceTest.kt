package com.indianservers.circuitssimulator

import com.indianservers.circuitssimulator.domain.*
import com.indianservers.circuitssimulator.intelligence.*
import com.indianservers.circuitssimulator.simulation.DcResult
import org.junit.Assert.*
import org.junit.Test

class CanvasIntelligenceTest {
    private fun p(kind:Kind,ref:String="P")=PlacedComponent(kind=kind,reference=ref,x=0f,y=0f)
    private fun pin(p:PlacedComponent,i:Int)=TerminalRef(p.id,i)
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=Wire(start=pin(a,ai),end=pin(b,bi))
    private fun checks(c:Circuit,result:DcResult?=null)=CanvasIntelligence.inspect(IntelligenceInput(c,result),CircuitFacts(c,result))
    private fun has(issues:List<IntelligenceIssue>,id:String)=assertTrue("Missing $id: ${issues.map { it.ruleId }}",issues.any { it.ruleId==id })
    private fun absent(issues:List<IntelligenceIssue>,id:String)=assertFalse("Unexpected $id",issues.any { it.ruleId==id })

    @Test fun twentyDistinctChecksAreRegisteredInLiveAnalysis() {
        assertEquals(20,CanvasIntelligence.features.size)
        assertEquals(20,CanvasIntelligence.features.distinct().size)
        assertTrue(DiagnosticRuleRegistry.rules.contains(CanvasIntelligence))
        val part=p(Kind.RESISTOR)
        has(CircuitIntelligence.analyze(IntelligenceInput(Circuit("Test",listOf(part),emptyList()))).issues,"ISOLATED_COMPONENT")
    }
    @Test fun corruptEndpointsAndDuplicateWiresHaveTargetedRemovalActions() {
        val r=p(Kind.RESISTOR);val g=p(Kind.GROUND)
        val wire=w(r,0,g,0)
        val broken=Wire(start=TerminalRef("deleted",0),end=pin(r,0))
        val bad=w(r,99,g,0)
        val c=Circuit("Invalid",listOf(r,g),listOf(wire,wire.copy(id="duplicate"),broken,bad))
        val issues=checks(c)
        listOf("MISSING_ENDPOINT","INVALID_TERMINAL","DUPLICATE_WIRE").forEach { has(issues,it) }
        issues.filter { it.ruleId in setOf("MISSING_ENDPOINT","INVALID_TERMINAL","DUPLICATE_WIRE") }.forEach {
            assertEquals(it.wireIds.single(),(it.fixes.single() as SmartFix.RemoveWire).wireId)
        }
        has(CircuitIntelligence.analyze(IntelligenceInput(c)).issues,"MISSING_ENDPOINT")
    }
    @Test fun redundantPathsPreserveConnectivityAndLabeledWiresAreNotRemoved() {
        val a=p(Kind.JUNCTION,"A");val b=p(Kind.JUNCTION,"B");val c=p(Kind.JUNCTION,"C")
        val circuit=Circuit("Triangle",listOf(a,b,c),listOf(w(a,0,b,0),w(b,0,c,0),w(c,0,a,0)))
        val issue=checks(circuit).first { it.ruleId=="REDUNDANT_WIRE" }
        val fix=issue.fixes.single() as SmartFix.RemoveWire
        assertTrue(pin(c,0) in ComponentPins.net(circuit.copy(wires=circuit.wires.filter { it.id!=fix.wireId }),pin(a,0)))
        absent(checks(circuit.copy(wires=circuit.wires.map { it.copy(label="RAIL") })),"REDUNDANT_WIRE")
        val duplicate=circuit.wires.first().copy(id="dup",label="RAIL")
        assertTrue(checks(circuit.copy(wires=circuit.wires+duplicate)).first { it.ruleId=="DUPLICATE_WIRE" }.fixes.isEmpty())
    }
    @Test fun passiveBypassStubDuplicateReferencesAndInvalidSavedValuesAreDetected() {
        val r=p(Kind.RESISTOR,"R1");val invalid=p(Kind.RESISTOR,"R1").copy(parameters=mapOf("resistance" to Double.NaN))
        val j=p(Kind.JUNCTION)
        val issues=checks(Circuit("Test",listOf(r,invalid,j),listOf(w(r,0,r,1),w(r,0,j,0))))
        listOf("BYPASSED_PASSIVE","DANGLING_JUNCTION","DUPLICATE_REFERENCE","INVALID_PROPERTY","ISOLATED_COMPONENT").forEach { has(issues,it) }
        absent(checks(Circuit("Good",listOf(r,j),listOf(w(r,0,j,0)))),"BYPASSED_PASSIVE")
    }
    @Test fun floatingControlsClearWhenDriveIsConnected() {
        val mos=p(Kind.NMOS);val bjt=p(Kind.NPN_BJT);val r=p(Kind.RESISTOR)
        val c=Circuit("Control",listOf(mos,bjt,r),listOf(w(mos,0,r,0),w(bjt,0,r,1)))
        has(checks(c),"FLOATING_GATE");has(checks(c),"FLOATING_BASE")
        val fixed=c.copy(wires=c.wires+listOf(w(mos,1,r,1),w(bjt,1,r,0)))
        absent(checks(fixed),"FLOATING_GATE");absent(checks(fixed),"FLOATING_BASE")
    }
    @Test fun partiallyWiredDevicesRequireSupplyAndReturnButFreshDevicesDoNot() {
        val sensor=p(Kind.I2C_TEMP_SENSOR);val r=p(Kind.RESISTOR)
        val c=Circuit("Supply",listOf(sensor,r),listOf(w(sensor,2,r,0)))
        has(checks(c),"MISSING_SUPPLY_PIN");has(checks(c),"MISSING_RETURN_PIN")
        absent(checks(c.copy(wires=emptyList())),"MISSING_SUPPLY_PIN")
        val fixed=c.copy(wires=c.wires+listOf(w(sensor,0,r,1),w(sensor,1,r,0)))
        absent(checks(fixed),"MISSING_SUPPLY_PIN");absent(checks(fixed),"MISSING_RETURN_PIN")
    }
    @Test fun receiversSharingADriverAreNotMistakenForRxToRxErrors() {
        val a=p(Kind.SERIAL_TERMINAL,"A");val b=p(Kind.SERIAL_TERMINAL,"B");val driver=p(Kind.SERIAL_TERMINAL,"C")
        val c=Circuit("UART",listOf(a,b,driver),listOf(w(a,1,b,1)))
        has(checks(c),"UART_RX_RX")
        absent(checks(c.copy(wires=c.wires+w(driver,0,a,1))),"UART_RX_RX")
    }
    @Test fun sensorSupplyChecksUseEachPhysicalPinOrderAndSuppressFailedSolveValues() {
        IotSensorParts.profiles.values.forEach { profile ->
            val s=p(profile.kind);val supply=p(Kind.SOURCE);val g=p(Kind.GROUND)
            val c=Circuit("Sensor",listOf(s,supply,g),listOf(w(s,profile.supplyPin,supply,0),w(s,profile.groundPin,g,0)))
            fun solved(v:Double,error:String?=null)=DcResult(nodeVoltages=mapOf(pin(s,profile.supplyPin) to v,pin(s,profile.groundPin) to 0.0),error=error)
            has(checks(c,solved(-3.0)),"IOT_REVERSE_SUPPLY")
            has(checks(c,solved(profile.supplyMax+1)),"IOT_SUPPLY_RANGE")
            absent(checks(c,solved(profile.supplyMin)),"IOT_SUPPLY_RANGE")
            absent(checks(c,solved(profile.supplyMax)),"IOT_SUPPLY_RANGE")
            absent(checks(c,solved(99.0,"No solution")),"IOT_SUPPLY_RANGE")
        }
    }
    @Test fun sensorAdcAndOutputCollisionChecksUseElectricalNetsIncludingLabels() {
        val a=p(Kind.TMP36_SENSOR,"A");val b=p(Kind.HIH4030_SENSOR,"B");val board=p(Kind.ARDUINO_UNO)
        val def=BoardRegistry.boards.getValue(board.kind)
        val digital=def.pins.indexOfFirst { it.type==PinType.GPIO && PinCapability.ANALOG_INPUT !in it.capabilities }
        val analog=def.pins.indexOfFirst { PinCapability.ANALOG_INPUT in it.capabilities }
        val c=Circuit("ADC",listOf(a,b,board),listOf(w(a,1,board,digital),w(b,1,board,analog)))
        has(checks(c),"IOT_NON_ADC");absent(checks(c),"ANALOG_OUTPUT_COLLISION")
        has(checks(c.copy(wires=c.wires.map { it.copy(label="SENSE") })),"ANALOG_OUTPUT_COLLISION")
        absent(checks(c.copy(wires=listOf(w(a,1,board,analog)))),"IOT_NON_ADC")
    }
    @Test fun meterAndScopeChecksFollowNetAliasesAndIgnoreDistinctNets() {
        val a=p(Kind.RESISTOR,"A");val b=p(Kind.RESISTOR,"B")
        val c=Circuit("Probes",listOf(a,b),listOf(w(a,0,b,0)))
        fun inspect(common:TerminalRef)=CanvasIntelligence.inspect(IntelligenceInput(c,meterPlus=pin(a,0),meterCommon=common,
            scopeProbes=listOf(pin(a,0),common)),CircuitFacts(c,null))
        has(inspect(pin(b,0)),"METER_SAME_NET");has(inspect(pin(b,0)),"SCOPE_SAME_NET")
        absent(inspect(pin(b,1)),"METER_SAME_NET");absent(inspect(pin(b,1)),"SCOPE_SAME_NET")
    }
}
