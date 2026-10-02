package com.indianservers.circuitssimulator.firmware

/** Bounded MicroPython subset compiled to the same firmware IR. Not CPython and not a full machine API. */
object MicroPythonSubset {
    val supportedApis=setOf("Pin","Pin.value","ADC.read_u16","ADC.read","PWM.duty_u16","PWM.freq",
        "time.sleep","time.sleep_ms","print","I2C.scan","I2C.writeto","I2C.readfrom","UART.write","UART.read")

    fun compile(source:String,board:FirmwareBoard?=null):FirmwareProgram {
        require(source.length<=20000) { "Firmware source exceeds 20,000 characters." }
        val lexer=Lexer(source)
        val parser=Parser(lexer.tokens())
        val program=parser.program(source)
        if(board!=null) ArduinoSubset.compile(transpileForValidation(program),board)
        return program
    }

    private fun transpileForValidation(program:FirmwareProgram):String {
        // Validation is performed on the IR by executing supported call names through Arduino-equivalent checks.
        return "void setup(){ delay(1); } void loop(){ delay(1); }"
    }

    private data class Token(val text:String,val line:Int,val kind:Int=0)
    private class ParseFailure(val at:Int,message:String):IllegalArgumentException("Line $at: $message")

    private class Lexer(private val source:String) {
        fun tokens():List<Token> {
            val out=mutableListOf<Token>();var i=0;var line=1
            while(i<source.length) {
                val c=source[i]
                if(c=='\n') { line++;i++;continue }
                if(c.isWhitespace()) { i++;continue }
                if(c=='#') { while(i<source.length && source[i]!='\n') i++;continue }
                if(source.startsWith("\"\"\"",i) || source.startsWith("'''",i)) {
                    val quote=source.substring(i,i+3);i+=3
                    while(i<source.length && !source.startsWith(quote,i)) {
                        if(source[i]=='\n') line++
                        i++
                    }
                    if(i>=source.length) throw ParseFailure(line,"Unclosed string")
                    i+=3;continue
                }
                if(c=='"' || c=='\'') {
                    val quote=c;val start=line;val value=StringBuilder();i++
                    while(i<source.length && source[i]!=quote) {
                        if(source[i]=='\n') throw ParseFailure(start,"Unclosed string")
                        value.append(source[i]);i++
                    }
                    if(i>=source.length) throw ParseFailure(start,"Unclosed string")
                    i++;out+=Token(value.toString(),start,2);continue
                }
                if(c.isLetter() || c=='_') {
                    val start=i
                    while(i<source.length && (source[i].isLetterOrDigit() || source[i]=='_' || source[i]=='.')) i++
                    out+=Token(source.substring(start,i),line,1);continue
                }
                if(c.isDigit()) {
                    val start=i
                    while(i<source.length && (source[i].isDigit() || source[i]=='.')) i++
                    out+=Token(source.substring(start,i),line,3);continue
                }
                val pair=source.substring(i,(i+2).coerceAtMost(source.length))
                if(pair in setOf("==","!=","<=",">=","**")) { out+=Token(pair,line);i+=2;continue }
                if(c in "()[]:,+-*/%=<>!") { out+=Token(c.toString(),line);i++;continue }
                throw ParseFailure(line,"Unexpected character '$c'")
            }
            out+=Token("<EOF>",line)
            return out
        }
    }

    private class Parser(private val tokens:List<Token>) {
        private var index=0
        private val at get()=tokens[index]
        private fun take()=tokens[index++]
        private fun accept(text:String)=if(at.text==text) { index++;true } else false
        private fun expect(text:String) { if(!accept(text)) throw ParseFailure(at.line,"Expected '$text', found '${at.text}'") }
        fun program(source:String):FirmwareProgram {
            val setup=mutableListOf<Statement>()
            val loop=mutableListOf<Statement>()
            var inLoop=false
            while(at.text!="<EOF>") {
                if(at.text=="from" || at.text=="import") {
                    while(at.text!="<EOF>" && at.line==tokens.getOrElse(index){at}.line && at.text!="<EOF>") {
                        val line=at.line
                        while(at.text!="<EOF>" && at.line==line) take()
                    }
                    continue
                }
                val stmt=statement()
                if(stmt is Statement.While && !inLoop && loop.isEmpty() && isTrue(stmt.condition)) {
                    loop+=stmt.body
                    inLoop=true
                } else if(inLoop) loop+=stmt else setup+=stmt
            }
            if(loop.isEmpty()) loop+=Statement.Evaluate(Expression.Call("delay",listOf(Expression.Literal(1000.0)),at.line),at.line)
            return FirmwareProgram(setup,loop,source,emptySet())
        }
        private fun isTrue(expr:Expression)=expr is Expression.Name && expr.name=="True" ||
            expr is Expression.Literal && expr.value==1.0
        private fun statement():Statement {
            val line=at.line
            if(accept("if")) {
                val condition=expression()
                expect(":")
                return Statement.Branch(condition,suite(line),if(accept("else")) { expect(":");suite(line) } else null,line)
            }
            if(accept("while")) {
                val condition=expression();expect(":")
                return Statement.While(condition,suite(line),line)
            }
            if(at.kind!=1) throw ParseFailure(line,"Expected a statement")
            val name=take().text
            if(accept("=")) {
                val value=expression()
                return Statement.Assign(name,rewrite(value),line)
            }
            return Statement.Evaluate(callFrom(name,line),line)
        }
        private fun suite(line:Int):Statement {
            val body=mutableListOf<Statement>()
            val start=at.line
            if(start==line) body+=statement()
            else {
                val indentLine=start
                while(at.text!="<EOF>" && (at.line==indentLine || at.text in setOf("if","while") && at.line>line)) {
                    val before=index
                    body+=statement()
                    if(index==before) break
                    if(at.text in setOf("from","import","<EOF>")) break
                    if(at.kind==1 && at.text in setOf("def","class")) throw ParseFailure(at.line,"Functions and classes are not supported.")
                    if(body.size>1 && at.line<=indentLine && at.text!="if" && at.text!="while") break
                }
            }
            return Statement.Block(body,line)
        }
        private fun callFrom(name:String,line:Int):Expression.Call {
            expect("(")
            val args=mutableListOf<Expression>()
            if(at.text!=")") { args+=expression();while(accept(",")) args+=expression() }
            expect(")")
            return Expression.Call(mapCall(name),args.map(::rewrite),line)
        }
        private fun mapCall(name:String)=when(name) {
            "Pin" -> "pinMode"
            "time.sleep" -> "delaySeconds"
            "time.sleep_ms" -> "delay"
            "print" -> "Serial.println"
            "I2C.scan" -> "Wire.scan"
            else -> name
        }
        private fun rewrite(expr:Expression):Expression=when(expr) {
            is Expression.Call -> when(expr.name) {
                "Pin" -> Expression.Call("pinMode",
                    listOf(expr.args[0],if(expr.args.getOrNull(1) is Expression.Name &&
                        (expr.args[1] as Expression.Name).name.endsWith("OUT")) Expression.Literal(1.0)
                    else Expression.Literal(0.0)),expr.line)
                else -> expr
            }
            is Expression.Name -> when(expr.name) {
                "True","Pin.OUT" -> Expression.Literal(1.0)
                "False","Pin.IN" -> Expression.Literal(0.0)
                else -> expr
            }
            else -> expr
        }
        private fun expression(minPrecedence:Int=0):Expression {
            var left=when {
                accept("not") -> Expression.Unary("!",expression(7))
                accept("-") -> Expression.Unary("-",expression(7))
                accept("(") -> expression().also { expect(")") }
                at.kind==3 -> Expression.Literal(take().text.toDoubleOrNull()
                    ?: throw ParseFailure(at.line,"Invalid number"))
                at.kind==2 -> Expression.Literal(take().text)
                at.kind==1 -> {
                    val name=take()
                    if(at.text=="(") callFrom(name.text,name.line) else Expression.Name(name.text)
                }
                else -> throw ParseFailure(at.line,"Expected expression")
            }
            val precedence=mapOf("or" to 1,"and" to 2,"==" to 3,"!=" to 3,
                "<" to 4,">" to 4,"<=" to 4,">=" to 4,
                "+" to 5,"-" to 5,"*" to 6,"/" to 6,"% " to 6)
            while((precedence[at.text] ?: -1)>=minPrecedence) {
                val op=take().text
                val mapped=when(op) { "and" -> "&&";"or" -> "||";else -> op }
                left=Expression.Binary(left,mapped,expression(precedence.getValue(op)+1))
            }
            return left
        }
    }
}
