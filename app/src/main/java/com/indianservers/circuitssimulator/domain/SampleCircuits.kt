package com.indianservers.circuitssimulator.domain

object SampleCircuits {
    private fun wire(a: PlacedComponent, ai: Int, b: PlacedComponent, bi: Int) =
        Wire(start = TerminalRef(a.id, ai), end = TerminalRef(b.id, bi))

    fun lightDivider(): Circuit {
        val source=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=170f,y=530f,
            parameters=mapOf("voltage" to 5.0))
        val fixed=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=520f,y=320f,
            parameters=mapOf("resistance" to 10000.0,"rating" to .25))
        val ldr=PlacedComponent(kind=Kind.LDR,reference="LDR1",x=800f,y=530f,rotation=90)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=470f,y=770f)
        return Circuit("Light Sensor Divider",listOf(source,fixed,ldr,ground),listOf(
            wire(source,0,fixed,0),wire(fixed,1,ldr,0),wire(ldr,1,source,1),
            wire(source,1,ground,0)))
    }

    fun potentiometerDemo(): Circuit {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=170f,y=530f,
            parameters=mapOf("voltage" to 5.0,"acAmplitude" to 1.0))
        val pot=PlacedComponent(kind=Kind.POTENTIOMETER,reference="RV1",x=590f,y=500f)
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=820f,y=550f,rotation=90,
            parameters=mapOf("resistance" to 10000.0,"rating" to .25))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=500f,y=770f)
        return Circuit("Adjustable Divider",listOf(source,pot,load,ground),listOf(
            wire(source,0,pot,0),wire(pot,1,load,0),wire(pot,2,source,1),
            wire(load,1,source,1),wire(source,1,ground,0)))
    }

    fun rcLowPass(): Circuit {
        val source=PlacedComponent(kind=Kind.FUNCTION_GENERATOR,reference="FG1",x=170f,y=530f,
            parameters=mapOf("frequency" to 1000.0,"amplitude" to 1.0,"offset" to 0.0))
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=530f,y=320f,
            parameters=mapOf("resistance" to 1000.0))
        val capacitor=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=800f,y=530f,
            parameters=mapOf("capacitance" to 100e-9))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=490f,y=770f)
        return Circuit("RC Low-pass",listOf(source,resistor,capacitor,ground),listOf(
            wire(source,0,resistor,0),wire(resistor,1,capacitor,0),wire(capacitor,1,source,1),
            wire(source,1,ground,0)))
    }

    fun rcHighPass():Circuit {
        val low=rcLowPass()
        val source=low.components.first { it.kind==Kind.FUNCTION_GENERATOR }
        val series=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=530f,y=320f,
            parameters=mapOf("capacitance" to 100e-9))
        val shunt=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=800f,y=530f,
            parameters=mapOf("resistance" to 1000.0))
        val ground=low.components.first { it.kind==Kind.GROUND }
        return Circuit("RC High-pass",listOf(source,series,shunt,ground),listOf(
            wire(source,0,series,0),wire(series,1,shunt,0),wire(shunt,1,source,1),wire(source,1,ground,0)))
    }

    fun rlLowPass():Circuit {
        val low=rcLowPass()
        val source=low.components.first { it.kind==Kind.FUNCTION_GENERATOR }
        val series=PlacedComponent(kind=Kind.INDUCTOR,reference="L1",x=530f,y=320f,
            parameters=mapOf("inductance" to .1))
        val shunt=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=800f,y=530f,
            parameters=mapOf("resistance" to 1000.0))
        val ground=low.components.first { it.kind==Kind.GROUND }
        return Circuit("RL Low-pass",listOf(source,series,shunt,ground),listOf(
            wire(source,0,series,0),wire(series,1,shunt,0),wire(shunt,1,source,1),wire(source,1,ground,0)))
    }

    fun fuseFault():Circuit {
        val source=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=170f,y=530f,
            parameters=mapOf("voltage" to 5.0,"internalResistance" to .5))
        val fuse=PlacedComponent(kind=Kind.FUSE,reference="F1",x=500f,y=320f,
            parameters=mapOf("resistance" to .1,"currentRating" to .5,"i2t" to .05))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=800f,y=530f,rotation=90,
            parameters=mapOf("resistance" to 1.0,"rating" to 10.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=500f,y=770f)
        return Circuit("Fuse Fault",listOf(source,fuse,load,ground),listOf(
            wire(source,0,fuse,0),wire(fuse,1,load,0),wire(load,1,source,1),wire(source,1,ground,0)))
    }

    fun nonInvertingOpAmp(ideal:Boolean=false):Circuit {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=170f,y=560f,
            parameters=mapOf("voltage" to 1.0))
        val op=PlacedComponent(kind=if(ideal) Kind.IDEAL_OPAMP else Kind.OPAMP,reference="U1",x=590f,y=460f)
        val lower=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=440f,y=700f,
            parameters=mapOf("resistance" to 10000.0,"rating" to .25))
        val upper=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=790f,y=690f,
            parameters=mapOf("resistance" to 90000.0,"rating" to .25))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="RL1",x=850f,y=470f,rotation=90,
            parameters=mapOf("resistance" to 10000.0,"rating" to .25))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=500f,y=820f)
        return Circuit("Non-inverting Op-amp",listOf(source,op,lower,upper,load,ground),listOf(
            wire(source,0,op,0),wire(source,1,ground,0),
            wire(op,1,lower,0),wire(lower,1,ground,0),
            wire(op,1,upper,0),wire(upper,1,op,2),
            wire(op,2,load,0),wire(load,1,ground,0)))
    }

    fun led(): Circuit {
        val battery = PlacedComponent(kind = Kind.BATTERY, reference = "B1", x = 170f, y = 530f)
        val switch = PlacedComponent(kind = Kind.SWITCH, reference = "SW1", x = 360f, y = 320f)
        val resistor = PlacedComponent(kind = Kind.RESISTOR, reference = "R1", x = 590f, y = 320f)
        val led = PlacedComponent(kind = Kind.LED, reference = "D1", x = 800f, y = 530f)
        val ground = PlacedComponent(kind = Kind.GROUND, reference = "GND1", x = 490f, y = 770f)
        return Circuit("LED Demo", listOf(battery, switch, resistor, led, ground), listOf(
            wire(battery, 0, switch, 0), wire(switch, 1, resistor, 0),
            wire(resistor, 1, led, 0), wire(led, 1, battery, 1), wire(battery, 1, ground, 0)
        ))
    }

    fun lamp(): Circuit {
        val battery = PlacedComponent(kind = Kind.BATTERY, reference = "B1", x = 170f, y = 530f)
        val switch = PlacedComponent(kind = Kind.SWITCH, reference = "SW1", x = 360f, y = 320f)
        val lamp = PlacedComponent(kind = Kind.LAMP, reference = "LAMP1", x = 800f, y = 530f)
        val ground = PlacedComponent(kind = Kind.GROUND, reference = "GND1", x = 490f, y = 770f)
        return Circuit("Lamp Demo", listOf(battery, switch, lamp, ground), listOf(
            wire(battery, 0, switch, 0), wire(switch, 1, lamp, 0),
            wire(lamp, 1, battery, 1), wire(battery, 1, ground, 0)
        ))
    }

    fun divider(): Circuit {
        val source = PlacedComponent(kind = Kind.SOURCE, reference = "V1", x = 170f, y = 530f,
            parameters = mapOf("voltage" to 10.0))
        val r1 = PlacedComponent(kind = Kind.RESISTOR, reference = "R1", x = 500f, y = 310f,
            parameters = mapOf("resistance" to 1000.0, "rating" to .25, "tolerance" to 5.0))
        val r2 = PlacedComponent(kind = Kind.RESISTOR, reference = "R2", x = 800f, y = 530f,
            rotation = 90, parameters = mapOf("resistance" to 1000.0, "rating" to .25, "tolerance" to 5.0))
        val ground = PlacedComponent(kind = Kind.GROUND, reference = "GND1", x = 500f, y = 770f)
        return Circuit("Voltage Divider", listOf(source, r1, r2, ground), listOf(
            wire(source, 0, r1, 0), wire(r1, 1, r2, 0), wire(r2, 1, source, 1), wire(source, 1, ground, 0)
        ))
    }

    fun rc(): Circuit {
        val battery=PlacedComponent(kind=Kind.BATTERY,reference="B1",x=170f,y=530f)
        val switch=PlacedComponent(kind=Kind.SWITCH,reference="SW1",x=360f,y=320f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=590f,y=320f,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25,"tolerance" to 5.0))
        val capacitor=PlacedComponent(kind=Kind.CAPACITOR,reference="C1",x=800f,y=530f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=490f,y=770f)
        return Circuit("RC Charging",listOf(battery,switch,resistor,capacitor,ground),listOf(
            wire(battery,0,switch,0),wire(switch,1,resistor,0),wire(resistor,1,capacitor,0),
            wire(capacitor,1,battery,1),wire(battery,1,ground,0)
        ))
    }

    fun rl(): Circuit {
        val source=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=170f,y=530f)
        val switch=PlacedComponent(kind=Kind.SWITCH,reference="SW1",x=360f,y=320f)
        val resistor=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=590f,y=320f,
            parameters=mapOf("resistance" to 100.0,"rating" to 1.0,"tolerance" to 5.0))
        val inductor=PlacedComponent(kind=Kind.INDUCTOR,reference="L1",x=800f,y=530f,rotation=90,
            parameters=mapOf("inductance" to 1.0,"initialCurrent" to 0.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=490f,y=770f)
        return Circuit("RL Rise",listOf(source,switch,resistor,inductor,ground),listOf(
            wire(source,0,switch,0),wire(switch,1,resistor,0),wire(resistor,1,inductor,0),
            wire(inductor,1,source,1),wire(source,1,ground,0)
        ))
    }

    fun generator(): Circuit {
        val generator=PlacedComponent(kind=Kind.FUNCTION_GENERATOR,reference="FG1",x=170f,y=530f,
            parameters=mapOf("frequency" to 10.0,"amplitude" to 2.5,"offset" to 0.0,
                "duty" to .5,"phase" to 0.0,"waveform" to 0.0,"rise" to .001,"fall" to .001))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=800f,y=530f,rotation=90,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25,"tolerance" to 5.0))
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=490f,y=770f)
        return Circuit("Function Generator Demo",listOf(generator,load,ground),listOf(
            wire(generator,0,load,0),wire(load,1,generator,1),wire(generator,1,ground,0)))
    }

    fun npnSwitch(controlVoltage:Double=5.0):Circuit {
        val supply=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=170f,y=530f,
            parameters=mapOf("voltage" to 9.0))
        val control=PlacedComponent(kind=Kind.SOURCE,reference="V2",x=390f,y=680f,
            parameters=mapOf("voltage" to controlVoltage))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=560f,y=320f,
            parameters=mapOf("resistance" to 1000.0,"rating" to .25))
        val base=PlacedComponent(kind=Kind.RESISTOR,reference="R2",x=570f,y=550f,
            parameters=mapOf("resistance" to 10000.0,"rating" to .25))
        val transistor=PlacedComponent(kind=Kind.NPN_BJT,reference="Q1",x=800f,y=530f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=600f,y=800f)
        return Circuit("NPN Switch",listOf(supply,control,load,base,transistor,ground),listOf(
            wire(supply,0,load,0),wire(load,1,transistor,0),wire(transistor,2,supply,1),
            wire(control,0,base,0),wire(base,1,transistor,1),wire(control,1,supply,1),
            wire(supply,1,ground,0)))
    }

    fun nmosSwitch(gateVoltage:Double=5.0):Circuit {
        val supply=PlacedComponent(kind=Kind.SOURCE,reference="V1",x=170f,y=530f,
            parameters=mapOf("voltage" to 9.0))
        val gate=PlacedComponent(kind=Kind.SOURCE,reference="V2",x=390f,y=680f,
            parameters=mapOf("voltage" to gateVoltage))
        val load=PlacedComponent(kind=Kind.RESISTOR,reference="R1",x=560f,y=320f,
            parameters=mapOf("resistance" to 100.0,"rating" to 1.0))
        val mos=PlacedComponent(kind=Kind.NMOS,reference="M1",x=800f,y=530f)
        val ground=PlacedComponent(kind=Kind.GROUND,reference="GND1",x=600f,y=800f)
        return Circuit("NMOS Switch",listOf(supply,gate,load,mos,ground),listOf(
            wire(supply,0,load,0),wire(load,1,mos,0),wire(mos,2,supply,1),
            wire(gate,0,mos,1),wire(gate,1,supply,1),wire(supply,1,ground,0)))
    }
}
