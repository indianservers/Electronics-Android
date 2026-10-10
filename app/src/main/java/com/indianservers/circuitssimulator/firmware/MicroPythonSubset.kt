package com.indianservers.circuitssimulator.firmware

/** Indentation-aware, bounded interpreter frontend. No host Python or Android access. */
object MicroPythonSubset {
    val supportedApis=setOf("Pin","ADC","PWM","I2C","UART","SPI","print","sleep","sleep_ms","sleep_us",
        "time.sleep","time.sleep_ms","time.sleep_us","ticks_ms","ticks_us","time.ticks_ms","time.ticks_us","bytes","bytearray","len")
    private val methods=mapOf("Pin" to setOf("value","on","off","toggle"),
        "ADC" to setOf("read","read_u16"),"PWM" to setOf("freq","duty_u16","deinit"),
        "I2C" to setOf("scan","writeto","readfrom"),"UART" to setOf("write","read","any"),
        "SPI" to setOf("write","read"))
    private data class Row(val text:String,val indent:Int,val line:Int)
    fun compile(source:String,board:FirmwareBoard?=null):FirmwareProgram {
        require(source.length<=20000) { "Firmware source exceeds 20,000 characters." }
        val rows=source.lines().mapIndexedNotNull { i,s ->
            require('\t' !in s) { "Line ${i+1}: use spaces for indentation." }
            val text=s.trim()
            require(s.length-s.trimStart().length<=128) { "Line ${i+1}: excessive indentation." }
            if(text.isEmpty() || text.startsWith("#")) null else Row(text,s.length-s.trimStart().length,i+1)
        }
        var pos=0
        val objects=mutableMapOf<String,String>()
        val functions=mutableMapOf<String,List<Statement>>()
        fun expression(text:String,line:Int):Expression=ExprParser(text,line).parse()
        lateinit var suite:(Int)->List<Statement>
        suite={ indent ->
            val body=mutableListOf<Statement>()
            while(pos<rows.size && rows[pos].indent>=indent) {
                val row=rows[pos]
                require(row.indent==indent) { "Line ${row.line}: unexpected indentation." }
                pos++
                val text=row.text
                when {
                    text.startsWith("from ") || text.startsWith("import ") -> {
                        val module=if(text.startsWith("from ")) text.substringAfter("from ").substringBefore(' ')
                            else text.substringAfter("import ")
                        require(module in setOf("machine","time")) { "Line ${row.line}: unsupported module $module." }
                        if(text.startsWith("from ")) {
                            val names=text.substringAfter(" import ", "")
                            require(names.isNotEmpty() && names.split(',').all { it.trim() in supportedApis }) {
                                "Line ${row.line}: unsupported import." }
                        }
                    }
                    text=="pass" -> Unit
                    text.startsWith("def ") -> {
                        val name=text.removePrefix("def ").substringBefore('(')
                        require(text=="def $name():") { "Line ${row.line}: only zero-argument functions are supported." }
                        require(pos<rows.size && rows[pos].indent>indent) { "Line ${row.line}: expected function body." }
                        functions[name]=suite(rows[pos].indent)
                    }
                    text.startsWith("while ") || text.startsWith("if ") -> {
                        val isWhile=text.startsWith("while ")
                        require(text.endsWith(":")) { "Line ${row.line}: expected ':'." }
                        val condition=expression(text.substringAfter(' ').dropLast(1),row.line)
                        require(pos<rows.size && rows[pos].indent>indent) { "Line ${row.line}: expected indented body." }
                        val block=Statement.Block(suite(rows[pos].indent),row.line)
                        var other:Statement?=null
                        if(!isWhile && pos<rows.size && rows[pos].indent==indent && rows[pos].text=="else:") {
                            pos++
                            require(pos<rows.size && rows[pos].indent>indent) { "Expected indented else body." }
                            other=Statement.Block(suite(rows[pos].indent),row.line)
                        }
                        body+=if(isWhile) Statement.While(condition,block,row.line)
                            else Statement.Branch(condition,block,other,row.line)
                    }
                    else -> {
                        val assignment=Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*(\\+=|-=|=(?!=))\\s*(.+)$").matchEntire(text)
                        if(assignment!=null) {
                            val (name,op,value)=assignment.destructured
                            val expr=expression(value,row.line)
                            if(expr is Expression.Call && expr.name in methods) objects[name]=expr.name
                            body+=Statement.Assign(name,if(op=="=") expr else Expression.Binary(Expression.Name(name),op.take(1),expr),row.line)
                        } else body+=Statement.Evaluate(expression(text,row.line) as? Expression.Call
                            ?: error("Line ${row.line}: expected assignment or API call."),row.line)
                    }
                }
            }
            body
        }
        val body=suite(0)
        var loop=listOf<Statement>(Statement.Evaluate(Expression.Call("sleep_ms",listOf(Expression.Literal(1000.0)),1),1))
        val setup=body.toMutableList()
        val main=body.indexOfFirst { it is Statement.While && it.condition==Expression.Name("True") }
        if(main>=0) {
            require(main==body.lastIndex) { "Statements after while True are unreachable." }
            loop=((body[main] as Statement.While).body as Statement.Block).body
            setup.removeAt(main)
        }
        fun validateExpr(expr:Expression) {
            when(expr) {
                is Expression.Call -> {
                    val name=expr.name
                    require(name in setOf("__bytes","__index") || name.startsWith("__kw_") || name in supportedApis || name in functions ||
                        name.substringAfter('.', "") in (methods[objects[name.substringBefore('.')]] ?: emptySet())) {
                        "Line ${expr.line}: unsupported API $name." }
                    if(name in setOf("Pin","ADC","PWM") && board!=null) {
                        val arg=expr.args.firstOrNull()
                        val value=(arg as? Expression.Literal)?.value
                        if(value!=null) {
                            val channel=(value as? Number)?.toInt()
                            val mapped=if(name=="ADC" && board.definition.family in setOf(
                                com.indianservers.circuitssimulator.domain.BoardFamily.RP2040,
                                com.indianservers.circuitssimulator.domain.BoardFamily.RP2350) && channel in 0..2) 26+channel!! else value
                            val pin=board.resolve(mapped,analog=name=="ADC")
                            val caps=board.definition.pin(pin)!!.capabilities
                            if(name=="Pin" && (expr.args.getOrNull(1)==Expression.Name("Pin.OUT") ||
                                expr.args.getOrNull(1)==Expression.Literal(1.0)))
                                require(com.indianservers.circuitssimulator.domain.PinCapability.DIGITAL_OUTPUT in caps) { "$pin is not an output pin." }
                            require(name!="ADC" || com.indianservers.circuitssimulator.domain.PinCapability.ANALOG_INPUT in caps) { "$pin has no ADC." }
                            require(name!="PWM" || com.indianservers.circuitssimulator.domain.PinCapability.PWM in caps) { "$pin has no PWM." }
                        }
                    }
                    expr.args.forEach(::validateExpr)
                }
                is Expression.Binary -> { validateExpr(expr.left);validateExpr(expr.right) }
                is Expression.Unary -> validateExpr(expr.value)
                else -> Unit
            }
        }
        fun validate(stmt:Statement) {
            when(stmt) {
                is Statement.Assign -> validateExpr(stmt.value)
                is Statement.Evaluate -> validateExpr(stmt.call)
                is Statement.Block -> stmt.body.forEach(::validate)
                is Statement.Branch -> { validateExpr(stmt.condition);validate(stmt.yes);stmt.no?.let(::validate) }
                is Statement.While -> { validateExpr(stmt.condition);validate(stmt.body) }
                is Statement.For -> error("Unsupported Python for loop.")
            }
        }
        body.forEach(::validate);functions.values.flatten().forEach(::validate)
        return FirmwareProgram(setup,loop,source,emptySet(),functions,true)
    }
    private class ExprParser(text:String,private val line:Int) {
        private val tokenPattern=Regex("""(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|#[^\n]*|0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?|[A-Za-z_][A-Za-z0-9_.]*|==|!=|<=|>=|[-+*/%(),\[\]=<>])""")
        private val tokens=tokenPattern.findAll(text).filterNot { it.value.startsWith("#") }.map { it.value }.toList()
        init {
            require(tokenPattern.replace(text,"").isBlank()) { "Line $line: unsupported syntax." }
            var depth=0
            tokens.forEach { token ->
                if(token in setOf("(","[")) depth++
                if(token in setOf(")","]")) depth--
                require(depth in 0..64) { "Line $line: invalid or excessive expression nesting." }
            }
        }
        private var i=0
        private val at get()=tokens.getOrNull(i) ?: "<EOF>"
        private fun take()=tokens[i++]
        private fun accept(s:String)=if(at==s) { i++;true } else false
        private fun expect(s:String) { require(accept(s)) { "Line $line: expected '$s', found '$at'." } }
        fun parse():Expression=expr().also { require(i==tokens.size) { "Line $line: unexpected '$at'." } }
        private fun expr(min:Int=0):Expression {
            var left=when {
                accept("not") -> Expression.Unary("!",expr(7))
                accept("-") -> Expression.Unary("-",expr(7))
                accept("(") -> expr().also { expect(")") }
                accept("[") -> Expression.Call("__bytes",args("]"),line)
                at.startsWith("'") || at.startsWith("\"") -> Expression.Literal(take().drop(1).dropLast(1).replace("\\n","\n").replace("\\r","\r"))
                at.startsWith("0x",true) -> Expression.Literal(take().drop(2).toInt(16).toDouble())
                at.firstOrNull()?.isDigit()==true -> Expression.Literal(take().toDouble())
                at.firstOrNull()?.let { it.isLetter() || it=='_' }==true -> {
                    val name=take()
                    if(accept("(")) Expression.Call(name,args(")"),line) else Expression.Name(name)
                }
                else -> error("Line $line: expected expression, found '$at'.")
            }
            if(accept("[")) left=Expression.Call("__index",listOf(left,expr().also { expect("]") }),line)
            val precedence=mapOf("or" to 1,"and" to 2,"==" to 3,"!=" to 3,"<" to 4,">" to 4,"<=" to 4,">=" to 4,
                "+" to 5,"-" to 5,"*" to 6,"/" to 6,"%" to 6)
            while((precedence[at] ?: -1)>=min) {
                val op=take()
                left=Expression.Binary(left,when(op) { "and" -> "&&";"or" -> "||";else -> op },expr(precedence.getValue(op)+1))
            }
            return left
        }
        private fun args(close:String):List<Expression> {
            val result=mutableListOf<Expression>()
            if(accept(close)) return result
            do {
                if(tokens.getOrNull(i+1)=="=") {
                    val key=take();expect("=")
                    result+=Expression.Call("__kw_$key",listOf(expr()),line)
                } else result+=expr()
                if(at==close) break
            } while(accept(","))
            expect(close);return result
        }
    }
}
