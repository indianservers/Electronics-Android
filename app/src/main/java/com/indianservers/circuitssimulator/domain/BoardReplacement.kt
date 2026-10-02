package com.indianservers.circuitssimulator.domain

/** Compare two board models. Never remaps pins silently. */
object BoardReplacement {
    data class Summary(
        val compatiblePins:List<String>,
        val missingPins:List<String>,
        val voltageChange:String?,
        val codeIssues:List<String>,
        val notes:List<String>
    )

    fun summarize(from:Kind,to:Kind,source:String?=null):Summary {
        val a=BoardRegistry.boards.getValue(from)
        val b=BoardRegistry.boards.getValue(to)
        val compatible=a.pins.filter { pin ->
            pin.connectable && (b.resolve(pin.name)!=null || pin.aliases.any { b.resolve(it)!=null })
        }.map { it.name }
        val missing=a.pins.filter { it.connectable && it.name !in compatible &&
            PinCapability.DIGITAL_INPUT in it.capabilities }.map { it.name }
        val voltage=if(a.logicVoltage!=b.logicVoltage)
            "${a.product} is ${a.logicVoltage} V logic; ${b.product} is ${b.logicVoltage} V. Existing 5 V wiring may over-range ${b.product} GPIO."
        else null
        val code=mutableListOf<String>()
        source.orEmpty().split(Regex("[^A-Za-z0-9_]+")).filter { it.isNotBlank() }.distinct().forEach { token ->
            if(a.resolve(token)!=null && b.resolve(token)==null)
                code+="Pin $token from ${a.product} is not valid for ${b.product}."
        }
        return Summary(compatible,missing,voltage,code,listOf(
            "No automatic remapping is applied.",
            "${a.supportLabel()} → ${b.supportLabel()}"))
    }
}
