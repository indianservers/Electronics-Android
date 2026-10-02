package com.indianservers.circuitssimulator.domain

import kotlin.math.exp

/** Physically distinct two-terminal parts sharing established solver primitives where appropriate. */
object PhaseOneParts {
    private fun p(key:String,label:String,unit:String,default:Double,min:Double,max:Double)=
        Parameter(key,label,unit,default,min,max)
    val definitions=listOf(
        Definition(Kind.SINGLE_CELL,"One electrochemical cell with series resistance",listOf(
            p("cellVoltage","Cell voltage","V",1.5,.1,4.5),
            p("internalResistance","Internal resistance","Ω",.15,0.0,10.0)),
            keywords=listOf("AA","AAA","cell","battery"),applications=listOf("Portable power")),
        Definition(Kind.BATTERY_PACK,"Series-connected cells; output voltage scales with cell count",listOf(
            p("cellVoltage","Voltage per cell","V",1.5,.1,4.5),
            p("cellCount","Cells in series","",4.0,2.0,24.0),
            p("cellResistance","Resistance per cell","Ω",.15,0.0,10.0)),
            keywords=listOf("battery","series cells"),applications=listOf("Portable power")),
        Definition(Kind.VARIABLE_DC_SUPPLY,"Adjustable bench voltage source with output resistance",listOf(
            p("voltage","Output voltage","V",5.0,0.0,48.0),
            p("internalResistance","Output resistance","Ω",.05,0.0,100.0)),
            keywords=listOf("bench supply","power supply"),applications=listOf("Circuit testing")),
        Definition(Kind.DC_CURRENT_SOURCE,"Ideal current injection from positive to negative terminal",listOf(
            p("current","Current","A",.01,-10.0,10.0)),
            keywords=listOf("current source","constant current"),applications=listOf("Biasing","LED drive"),
            limitations=listOf("Ideal current source has unlimited compliance voltage.")),
        Definition(Kind.AC_VOLTAGE_SOURCE,"Sinusoidal source; amplitude is peak voltage",listOf(
            p("amplitude","Peak voltage","V",12.0,0.0,100.0),p("frequency","Frequency","Hz",50.0,.01,1e6),
            p("phase","Phase","°",0.0,-360.0,360.0),p("offset","DC offset","V",0.0,-100.0,100.0),
            p("internalResistance","Source resistance","Ω",.1,0.0,1000.0)),
            keywords=listOf("AC","alternating current","mains","sine"),applications=listOf("AC analysis")),
        Definition(Kind.SINE_GENERATOR,"Adjustable sine signal for frequency response experiments",listOf(
            p("frequency","Frequency","Hz",1000.0,.01,1e6),p("amplitude","Peak amplitude","V",2.5,0.0,100.0),
            p("offset","DC offset","V",0.0,-100.0,100.0),p("phase","Phase","°",0.0,-360.0,360.0),
            p("internalResistance","Output resistance","Ω",50.0,0.0,1000.0)),
            keywords=listOf("signal","waveform","oscillator"),applications=listOf("Frequency response")),
        Definition(Kind.SQUARE_GENERATOR,"Bipolar square signal with adjustable duty cycle",listOf(
            p("frequency","Frequency","Hz",1000.0,.01,1e6),p("amplitude","Peak amplitude","V",2.5,0.0,100.0),
            p("offset","DC offset","V",2.5,-100.0,100.0),p("duty","High fraction","",.5,.01,.99),
            p("internalResistance","Output resistance","Ω",50.0,0.0,1000.0)),
            keywords=listOf("signal","clock","PWM"),applications=listOf("Digital clocking")),
        Definition(Kind.PULSE_GENERATOR,"Pulse signal with adjustable rise, fall and duty",listOf(
            p("frequency","Frequency","Hz",100.0,.01,1e6),p("amplitude","Pulse height","V",5.0,0.0,100.0),
            p("offset","Low level","V",0.0,-100.0,100.0),p("duty","High fraction","",.2,.01,.99),
            p("rise","Rise time","s",1e-6,0.0,1.0),p("fall","Fall time","s",1e-6,0.0,1.0),
            p("internalResistance","Output resistance","Ω",50.0,0.0,1000.0)),
            keywords=listOf("pulse","PWM","signal"),applications=listOf("Transient testing")),
        Definition(Kind.RHEOSTAT,"Two-terminal variable resistor using a sliding contact",listOf(
            p("resistance","Full-track resistance","Ω",10000.0,10.0,1e7),
            p("position","Wiper position","",.5,0.0,1.0),
            p("contactResistance","Contact resistance","Ω",1.0,.01,100.0)),
            keywords=listOf("variable resistor","dimmer"),applications=listOf("Current adjustment")),
        Definition(Kind.PTC_THERMISTOR,"Resistance rises with temperature using a local linear coefficient",listOf(
            p("resistance25C","Resistance at 25 °C","Ω",1000.0,1.0,1e7),
            p("temperatureC","Temperature","°C",25.0,-40.0,150.0),
            p("alpha","Temperature coefficient","1/°C",.004,.0001,.1)),
            keywords=listOf("temperature","PTC","sensor"),applications=listOf("Temperature sensing")),
        Definition(Kind.VARIABLE_CAPACITOR,"Adjustable capacitance between a minimum and maximum",listOf(
            p("minCapacitance","Minimum capacitance","F",10e-12,1e-12,.01),
            p("maxCapacitance","Maximum capacitance","F",500e-12,1e-12,.1),
            p("position","Adjustment","",.5,0.0,1.0),
            p("initialVoltage","Initial voltage","V",0.0,-100.0,100.0)),
            keywords=listOf("trimmer","tuning","capacitor"),applications=listOf("Resonant tuning")),
        Definition(Kind.SCHOTTKY_DIODE,"Low forward drop metal-semiconductor diode",listOf(
            p("forwardVoltage","Forward knee","V",.35,.1,.8),
            p("dynamicResistance","Forward resistance","Ω",2.0,.01,100.0)),
            keywords=listOf("low drop","rectifier"),applications=listOf("Low-loss rectification"),
            limitations=listOf("Piecewise forward model; reverse leakage and capacitance omitted.")),
        *listOf(Triple(Kind.RED_LED,1.8,"red"),Triple(Kind.GREEN_LED,2.2,"green"),
            Triple(Kind.BLUE_LED,3.0,"blue")).map { (kind,vf,color) ->
            Definition(kind,"$color light-emitting diode; brightness follows simulated current",listOf(
                p("forwardVoltage","Forward knee","V",vf,1.0,4.0),
                p("seriesResistance","Internal dynamic resistance","Ω",10.0,1.0,1000.0),
                p("maxCurrent","Maximum continuous current","A",.02,.001,.1)),
                keywords=listOf("LED","light","diode","$color"),applications=listOf("Indicators","Lighting"),
                limitations=listOf("Piecewise forward model; color depends on forward current."))
        }.toTypedArray(),
        Definition(Kind.PHOTODIODE,"Reverse-biased light sensor; illumination generates photocurrent",listOf(
            p("illuminanceLux","Illumination","lx",100.0,0.0,100000.0),
            p("responsivity","Photocurrent at 100 lx","A",10e-6,1e-9,.01),
            p("darkResistance","Dark shunt resistance","Ω",1e8,1e4,1e12)),
            keywords=listOf("light","optical","photo sensor"),applications=listOf("Light sensing"),
            limitations=listOf("Photocurrent scales linearly with lux; no spectral response.")),
        Definition(Kind.NC_PUSH_BUTTON,"Normally-closed momentary contact; press to interrupt current",emptyList(),
            keywords=listOf("button","normally closed","switch"),applications=listOf("Stop button"))
    )
}

/** Converts a variant to its proven primitive while preserving id, wiring and saved identity outside the solver. */
fun PlacedComponent.electricalPrimitive():PlacedComponent = when(kind) {
    Kind.SINGLE_CELL -> copy(kind=Kind.BATTERY,parameters=mapOf("voltage" to value("cellVoltage"),
        "internalResistance" to value("internalResistance")))
    Kind.BATTERY_PACK -> copy(kind=Kind.BATTERY,parameters=mapOf(
        "voltage" to value("cellVoltage")*value("cellCount").toInt().coerceAtLeast(1),
        "internalResistance" to value("cellResistance")*value("cellCount").toInt().coerceAtLeast(1)))
    Kind.VARIABLE_DC_SUPPLY -> copy(kind=Kind.SOURCE,parameters=parameters)
    Kind.AC_VOLTAGE_SOURCE,Kind.SINE_GENERATOR,Kind.SQUARE_GENERATOR,Kind.PULSE_GENERATOR ->
        copy(kind=Kind.FUNCTION_GENERATOR,parameters=parameters+mapOf("waveform" to when(kind) {
            Kind.SQUARE_GENERATOR -> 1.0;Kind.PULSE_GENERATOR -> 4.0;else -> 0.0 }))
    Kind.RHEOSTAT -> copy(kind=Kind.RESISTOR,parameters=mapOf(
        "resistance" to (value("resistance")*value("position")+value("contactResistance")).coerceAtLeast(.01),
        "rating" to 10.0))
    Kind.PTC_THERMISTOR -> copy(kind=Kind.RESISTOR,parameters=mapOf("resistance" to
        (value("resistance25C")*(1.0+value("alpha")*(value("temperatureC")-25.0))).coerceAtLeast(.01),
        "rating" to 10.0))
    Kind.VARIABLE_CAPACITOR -> copy(kind=Kind.CAPACITOR,parameters=mapOf(
        "capacitance" to (value("minCapacitance")+
            (value("maxCapacitance")-value("minCapacitance"))*value("position")).coerceAtLeast(1e-12),
        "initialVoltage" to value("initialVoltage"),"maxVoltage" to 100.0))
    Kind.NC_PUSH_BUTTON -> copy(kind=Kind.SWITCH)
    else -> this
}
