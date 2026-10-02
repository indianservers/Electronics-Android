package com.indianservers.circuitssimulator.firmware

data class PinUsage(val pin:String,val api:String,val line:Int,val raw:String)

/** Static extraction of GPIO/peripheral calls for pin highlighting and diagnostics. */
object SourceSymbols {
    fun extract(program:FirmwareProgram,board:FirmwareBoard? = null):List<PinUsage> {
        val out=mutableListOf<PinUsage>()
        val aliases=mutableMapOf<String,Any?>()
        fun pinText(expr:Expression):Any?=when(expr) {
            is Expression.Literal -> expr.value
            is Expression.Name -> aliases[expr.name] ?: expr.name
            else -> null
        }
        fun check(expr:Expression) {
            if(expr is Expression.Call) {
                if(expr.name in setOf("pinMode","digitalWrite","digitalRead","analogRead","analogWrite",
                        "tone","noTone","pulseIn","Servo.attach","Servo.write")) {
                    val raw=expr.args.firstOrNull()?.let(::pinText)
                    if(raw!=null) {
                        val analog=expr.name=="analogRead"
                        val resolved=try {
                            board?.resolve(if(raw.toString().toDoubleOrNull()!=null) raw.toString().toDouble() else raw,analog)
                                ?: raw.toString()
                        } catch(_:Exception) { raw.toString() }
                        out+=PinUsage(resolved,expr.name,expr.line,raw.toString())
                    }
                    if(expr.name=="pinMode") {
                        val mode=expr.args.getOrNull(1)
                        val pin=expr.args.firstOrNull()?.let(::pinText)
                        if(mode is Expression.Name && mode.name=="INPUT_PULLUP" && pin!=null)
                            out+=PinUsage(try { board?.resolve(pin) ?: pin.toString() } catch(_:Exception) { pin.toString() },
                                "INPUT_PULLUP",expr.line,pin.toString())
                    }
                }
                expr.args.forEach(::check)
            } else if(expr is Expression.Binary) { check(expr.left);check(expr.right) }
            else if(expr is Expression.Unary) check(expr.value)
        }
        fun walk(stmt:Statement) {
            when(stmt) {
                is Statement.Block -> stmt.body.forEach(::walk)
                is Statement.Assign -> {
                    aliases[stmt.name]=when(val value=stmt.value) {
                        is Expression.Literal -> value.value
                        is Expression.Name -> aliases[value.name] ?: value.name
                        else -> null
                    }
                    check(stmt.value)
                }
                is Statement.Evaluate -> check(stmt.call)
                is Statement.Branch -> { check(stmt.condition);walk(stmt.yes);stmt.no?.let(::walk) }
                is Statement.While -> { check(stmt.condition);walk(stmt.body) }
                is Statement.For -> { stmt.init?.let(::walk);check(stmt.condition);stmt.step?.let(::walk);walk(stmt.body) }
            }
        }
        program.setup.forEach(::walk);program.loop.forEach(::walk)
        return out
    }
}
