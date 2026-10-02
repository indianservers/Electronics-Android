package com.indianservers.circuitssimulator.guide

import com.indianservers.circuitssimulator.domain.*

/** Real editable circuits. Stable IDs let a saved lesson resume without matching screen coordinates. */
object LessonTemplates {
    private fun p(id:String,kind:Kind,ref:String,x:Float,y:Float,
                  parameters:Map<String,Double> = emptyMap(),closed:Boolean=true)=
        PlacedComponent(id=id,kind=kind,reference=ref,x=x,y=y,parameters=parameters,closed=closed)
    private fun w(id:String,a:PlacedComponent,ai:Int,b:PlacedComponent,bi:Int)=
        Wire(id=id,start=TerminalRef(a.id,ai),end=TerminalRef(b.id,bi))

    fun groundOnly(title:String):Circuit {
        val ground=p("guide-ground",Kind.GROUND,"GND1",500f,760f)
        return Circuit(title,listOf(ground),emptyList())
    }

    fun led(title:String="LED learning circuit",fault:String?=null):Circuit {
        val battery=p("guide-battery",Kind.BATTERY,"B1",170f,530f,
            mapOf("voltage" to if(fault=="short") 5.0 else 9.0,
                "internalResistance" to if(fault=="short") .5 else 0.0))
        val resistor=p("guide-resistor",Kind.RESISTOR,"R1",560f,320f,
            mapOf("resistance" to 330.0))
        val led=p("guide-led",Kind.LED,"D1",800f,530f)
        val ground=p("guide-ground",Kind.GROUND,"GND1",500f,760f)
        val reversed=fault=="reversed_led"
        val wires=buildList {
            add(w("guide-battery-resistor",battery,0,resistor,0))
            if(fault!="open") add(w("guide-resistor-led",resistor,1,led,if(reversed) 1 else 0))
            add(w("guide-led-return",led,if(reversed) 0 else 1,battery,1))
            add(w("guide-ground-wire",battery,1,ground,0))
            if(fault=="short") add(w("guide-short-wire",battery,0,battery,1))
        }
        return Circuit(title,listOf(battery,resistor,led,ground),wires)
    }

    fun ohm():Circuit {
        val source=p("guide-source",Kind.SOURCE,"V1",170f,530f,mapOf("voltage" to 3.0))
        val resistor=p("guide-resistor",Kind.RESISTOR,"R1",650f,400f,mapOf("resistance" to 500.0))
        val ground=p("guide-ground",Kind.GROUND,"GND1",470f,750f)
        return Circuit("Explore Ohm's Law",listOf(source,resistor,ground),listOf(
            w("guide-source-load",source,0,resistor,0),w("guide-load-return",resistor,1,source,1),
            w("guide-ground-wire",source,1,ground,0)))
    }

    fun resistorStart(title:String):Circuit {
        val source=p("guide-source",Kind.SOURCE,"V1",170f,530f,mapOf("voltage" to 10.0))
        val ground=p("guide-ground",Kind.GROUND,"GND1",500f,760f)
        return Circuit(title,listOf(source,ground),listOf(w("guide-ground-wire",source,1,ground,0)))
    }
}
