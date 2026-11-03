package com.indianservers.circuitssimulator.domain

/** Runnable, externally wired reference circuits; no hidden frequency or motor-voltage overrides. */
object DatasheetIcExamples {
    private fun p(k:Kind,r:String,x:Float,y:Float,vararg values:Pair<String,Double>)=
        PlacedComponent(kind=k,reference=r,x=x,y=y).let { it.copy(parameters=it.parameters+values) }
    private fun w(a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    fun astable():Circuit {
        val v=p(Kind.SOURCE,"V1",140f,270f,"voltage" to 5.0)
        val u=p(Kind.TIMER_555,"U1",500f,450f)
        val ra=p(Kind.RESISTOR,"R1",310f,160f,"resistance" to 10000.0)
        val rb=p(Kind.RESISTOR,"R2",770f,260f,"resistance" to 100000.0)
        val c=p(Kind.CAPACITOR,"C1",770f,600f,"capacitance" to 1e-6)
        val bypass=p(Kind.CAPACITOR,"C2",500f,750f,"capacitance" to 10e-9)
        val load=p(Kind.RESISTOR,"R3",200f,550f,"resistance" to 10000.0)
        val g=p(Kind.GROUND,"GND1",770f,820f)
        return Circuit("NE555 RC Astable",listOf(v,u,ra,rb,c,bypass,load,g),listOf(
            w(v,1,g,0),w(u,0,g,0),w(v,0,u,7),w(v,0,u,3),w(v,0,ra,0),
            w(ra,1,u,6),w(u,6,rb,0),w(rb,1,c,0),w(c,0,u,1),w(c,0,u,5),
            w(c,1,g,0),w(u,4,bypass,0),w(bypass,1,g,0),w(u,2,load,0),w(load,1,g,0)),
            SimulationSettings(analysis="Transient"))
    }

    fun monostable():Circuit {
        val base=astable()
        val u=base.components.first { it.kind==Kind.TIMER_555 }
        val v=base.components.first { it.reference=="V1" }
        val g=base.components.first { it.kind==Kind.GROUND }
        val r=base.components.first { it.reference=="R2" }
        val c=base.components.first { it.reference=="C1" }
        // Low for the first 10 ms of each 1 s period, then high for 990 ms.
        val trigger=p(Kind.FUNCTION_GENERATOR,"FG1",160f,770f,"waveform" to 1.0,
            "frequency" to 1.0,"offset" to 2.5,"amplitude" to 2.5,"duty" to .99,"phase" to 356.4)
        val removed=base.components.first { it.reference=="R1" }.id
        val wires=base.wires.filter { wire ->
            listOf(wire.start,wire.end).none { it.componentId==removed || it==TerminalRef(u.id,1) ||
                it==TerminalRef(u.id,6) || it==TerminalRef(r.id,0) }
        }+listOf(w(v,0,r,0),w(c,0,u,6),w(trigger,0,u,1),w(trigger,1,g,0))
        return base.copy(name="NE555 RC Monostable",components=base.components.filter { it.id!=removed }+trigger,wires=wires)
    }

    fun amplifier():Circuit {
        val v=p(Kind.SOURCE,"V1",140f,240f,"voltage" to 5.0)
        val signal=p(Kind.FUNCTION_GENERATOR,"FG1",140f,570f,"offset" to .2,
            "amplitude" to .05,"frequency" to 100.0)
        val u=p(Kind.LM358,"U1",470f,430f)
        val rf=p(Kind.RESISTOR,"R1",470f,170f,"resistance" to 90000.0)
        val rg=p(Kind.RESISTOR,"R2",170f,390f,"resistance" to 10000.0)
        val load=p(Kind.RESISTOR,"R3",780f,340f,"resistance" to 10000.0)
        val g=p(Kind.GROUND,"GND1",650f,760f)
        return Circuit("LM358 Sensor Amplifier x10",listOf(v,signal,u,rf,rg,load,g),listOf(
            w(v,1,g,0),w(v,0,u,7),w(u,3,g,0),w(signal,1,g,0),w(signal,0,u,2),
            w(u,0,rf,0),w(rf,1,u,1),w(u,1,rg,0),w(rg,1,g,0),w(u,0,load,0),w(load,1,g,0),
            // Unused amplifier is a grounded follower, rather than floating inputs.
            w(u,4,g,0),w(u,5,u,6)),SimulationSettings(analysis="Transient"))
    }

    fun motorDriver():Circuit {
        val logic=p(Kind.SOURCE,"V1",150f,220f,"voltage" to 5.0)
        val supply=p(Kind.SOURCE,"V2",850f,200f,"voltage" to 9.0)
        val pwm=p(Kind.FUNCTION_GENERATOR,"FG1",160f,630f,"waveform" to 1.0,
            "frequency" to 100.0,"offset" to 2.5,"amplitude" to 2.5,"duty" to .6)
        val u=p(Kind.L293D,"U1",490f,430f)
        val motor=p(Kind.DC_MOTOR,"M1",830f,540f,"resistance" to 30.0,"inductance" to .02)
        val g=p(Kind.GROUND,"GND1",650f,820f)
        return Circuit("L293D PWM Motor",listOf(logic,supply,pwm,u,motor,g),listOf(
            w(logic,1,g,0),w(supply,1,g,0),w(pwm,1,g,0),w(logic,0,u,15),w(supply,0,u,7),
            w(u,3,g,0),w(u,4,g,0),w(u,11,g,0),w(u,12,g,0),w(pwm,0,u,0),
            w(logic,0,u,1),w(u,6,g,0),w(u,2,motor,0),w(u,5,motor,1),
            w(u,8,g,0),w(u,9,g,0),w(u,14,g,0)),SimulationSettings(analysis="Transient"))
    }
}
