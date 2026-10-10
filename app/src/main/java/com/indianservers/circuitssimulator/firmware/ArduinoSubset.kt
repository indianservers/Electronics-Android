package com.indianservers.circuitssimulator.firmware

import com.indianservers.circuitssimulator.domain.PinCapability

data class FirmwareProgram(val setup:List<Statement>,val loop:List<Statement>,
                           val source:String,val usedPins:Set<String>,
                           val functions:Map<String,List<Statement>> = emptyMap(),val microPython:Boolean=false)
sealed interface Expression {
    data class Literal(val value:Any?):Expression
    data class Name(val name:String):Expression
    data class Unary(val op:String,val value:Expression):Expression
    data class Binary(val left:Expression,val op:String,val right:Expression):Expression
    data class Call(val name:String,val args:List<Expression>,val line:Int):Expression
}
sealed interface Statement {
    val line:Int
    data class Block(val body:List<Statement>,override val line:Int):Statement
    data class Assign(val name:String,val value:Expression,override val line:Int):Statement
    data class Evaluate(val call:Expression.Call,override val line:Int):Statement
    data class Branch(val condition:Expression,val yes:Statement,val no:Statement?,override val line:Int):Statement
    data class While(val condition:Expression,val body:Statement,override val line:Int):Statement
    data class For(val init:Statement?,val condition:Expression,val step:Statement?,val body:Statement,override val line:Int):Statement
}
private data class Token(val text:String,val line:Int,val kind:Int=0)
private class ParseFailure(val at:Int,message:String):IllegalArgumentException("Line $at: $message")

/** A deliberately bounded Arduino-like grammar. No C++ preprocessor, pointers, libraries, or arbitrary calls. */
object ArduinoSubset {
    val supportedApis=setOf("pinMode","digitalWrite","digitalRead","analogRead","analogWrite",
        "delay","delayMicroseconds","millis","micros","map","constrain","hex","tone","noTone","pulseIn",
        "attachInterrupt","detachInterrupt","digitalPinToInterrupt",
        "Serial.begin","Serial.print","Serial.println","Serial.write","Serial.available","Serial.read",
        "Wire.begin","Wire.beginTransmission","Wire.write","Wire.endTransmission","Wire.requestFrom",
        "Wire.read","Wire.available","SPI.begin","SPI.transfer","SPI.setDataMode","SPI.setClockDivider","SPI.setBitOrder",
        "Servo.attach","Servo.write","Servo.read")
    private val arity=mapOf("pinMode" to 2,"digitalWrite" to 2,"digitalRead" to 1,
        "analogRead" to 1,"analogWrite" to 2,"delay" to 1,"delayMicroseconds" to 1,"millis" to 0,"micros" to 0,
        "attachInterrupt" to 3,"detachInterrupt" to 1,"digitalPinToInterrupt" to 1,
        "map" to 5,"constrain" to 3,"hex" to 1,"tone" to 2,"noTone" to 1,"pulseIn" to 2,
        "Serial.begin" to 1,"Serial.print" to 1,"Serial.println" to 1,"Serial.write" to 1,
        "Serial.available" to 0,"Serial.read" to 0,"Wire.begin" to 0,"Wire.beginTransmission" to 1,
        "Wire.write" to 1,"Wire.endTransmission" to 0,"Wire.requestFrom" to 2,"Wire.read" to 0,
        "Wire.available" to 0,"SPI.begin" to 0,"SPI.transfer" to 1,"SPI.setDataMode" to 1,"SPI.setClockDivider" to 1,"SPI.setBitOrder" to 1,
        "Servo.attach" to 1,"Servo.write" to 1,"Servo.read" to 0)
    val unsupportedLibraries=setOf("WiFi.h","WiFiClient.h","BluetoothSerial.h","ESP8266WiFi.h","HTTPClient.h")
    fun canonicalApi(name:String):String {
        if(name.substringBefore('.').matches(Regex("Serial[0-3]"))) return "Serial."+name.substringAfter('.')
        if(name in supportedApis) return name
        val method=name.substringAfterLast('.')
        return when(method) {
            "attach" -> "Servo.attach";"write" -> if(name.startsWith("Serial")) "Serial.write" else "Servo.write"
            "read" -> if(name.startsWith("Serial")) "Serial.read" else if(name.startsWith("Wire")) "Wire.read"
                else "Servo.read"
            else -> name
        }
    }

    fun compile(source:String,board:FirmwareBoard?=null):FirmwareProgram {
        require(source.length<=20000) { "Firmware source exceeds 20,000 characters." }
        val lexer=Lexer(source)
        val tokens=lexer.tokens()
        var depth=0
        tokens.forEach { token ->
            if(token.kind!=2 && token.text in setOf("{","(","[")) depth++
            if(token.kind!=2 && token.text in setOf("}",")","]")) depth--
            require(depth in 0..64) { "Line ${token.line}: invalid or excessive nesting." }
        }
        val parser=Parser(tokens)
        val program=parser.program(source)
        if(board!=null) validate(program,board)
        return program
    }

    internal fun validate(program:FirmwareProgram,board:FirmwareBoard) {
        fun check(expr:Expression) {
            when(expr) {
                is Expression.Call -> {
                    val api=canonicalApi(expr.name)
                    if(api !in supportedApis && expr.name !in program.functions) throw ParseFailure(expr.line,
                        "Unsupported API ${expr.name}; this runtime is a documented subset, not arbitrary C++.")
                    val expected=arity[api]
                    if(expr.name in program.functions && expr.args.isNotEmpty()) throw ParseFailure(expr.line,"Void helpers accept no arguments in this runtime.")
                    if(expr.name.startsWith("Serial") && expr.name.contains('.')) {
                        val port=expr.name.substringBefore('.')
                        if(board.serialPorts().none { it.name==port }) throw ParseFailure(expr.line,"${board.definition.product} has no $port hardware UART.")
                    }
                    if(expected!=null && expr.args.size!=expected &&
                        !(api=="Wire.begin" && expr.args.size in 0..2) &&
                        !(api=="Wire.endTransmission" && expr.args.size in 0..1) &&
                        !(api=="Serial.begin" && expr.args.size in 1..2) &&
                        !(api=="pulseIn" && expr.args.size in 2..3) &&
                        !(api=="tone" && expr.args.size in 2..3) &&
                        !(api=="Serial.println" && expr.args.size in 0..1))
                        throw ParseFailure(expr.line,"${expr.name} has the wrong number of arguments.")
                    if(api in setOf("pinMode","digitalWrite","digitalRead","analogRead","analogWrite","tone","noTone","pulseIn","Servo.attach")) {
                        val constant=when(val first=expr.args.first()) {
                            is Expression.Literal -> first.value
                            is Expression.Name -> first.name.takeIf { it.matches(Regex("[A-Za-z]+[0-9]+")) }
                            else -> null
                        }
                        if(constant!=null) {
                            val name=try { board.resolve(constant,analog=api=="analogRead") } catch(e:Exception) {
                                throw ParseFailure(expr.line,e.message ?: "Invalid pin") }
                            val capability=when(api) {
                                "analogRead" -> PinCapability.ANALOG_INPUT
                                "analogWrite","tone","Servo.attach" -> PinCapability.PWM
                                "digitalWrite" -> PinCapability.DIGITAL_OUTPUT
                                else -> null
                            }
                            val pin=board.definition.pin(name)!!
                            val modeName=if(api=="pinMode") when(val second=expr.args.getOrNull(1)) {
                                is Expression.Name -> second.name
                                is Expression.Literal -> second.value.toString()
                                else -> null
                            } else null
                            val needed=when {
                                capability!=null -> capability
                                api=="pinMode" && modeName in setOf("OUTPUT","1","1.0") -> PinCapability.DIGITAL_OUTPUT
                                else -> null
                            }
                            if(needed!=null && needed !in pin.capabilities) {
                                throw ParseFailure(expr.line,when {
                                    pin.inputOnly && needed==PinCapability.DIGITAL_OUTPUT ->
                                        "${name} on this ${board.definition.product} is input-only and cannot be configured as an output."
                                    needed==PinCapability.PWM ->
                                        "${name} on this ${board.definition.product} is not PWM-capable."
                                    else -> "${board.definition.product} $name does not support ${expr.name}."
                                })
                            }
                        }
                    }
                    expr.args.forEach(::check)
                }
                is Expression.Binary -> { check(expr.left);check(expr.right) }
                is Expression.Unary -> check(expr.value)
                else -> Unit
            }
        }
        fun walk(stmt:Statement) {
            when(stmt) {
                is Statement.Block -> stmt.body.forEach(::walk)
                is Statement.Assign -> check(stmt.value)
                is Statement.Evaluate -> check(stmt.call)
                is Statement.Branch -> { check(stmt.condition);walk(stmt.yes);stmt.no?.let(::walk) }
                is Statement.While -> { check(stmt.condition);walk(stmt.body) }
                is Statement.For -> { stmt.init?.let(::walk);check(stmt.condition);stmt.step?.let(::walk);walk(stmt.body) }
            }
        }
        program.setup.forEach(::walk);program.loop.forEach(::walk);program.functions.values.flatten().forEach(::walk)
    }

    private class Lexer(private val source:String) {
        fun tokens():List<Token> {
            val out=mutableListOf<Token>();var i=0;var line=1
            while(i<source.length) {
                val c=source[i]
                if(c=='\n') { line++;i++;continue }
                if(c.isWhitespace()) { i++;continue }
                if(c=='#') {
                    val start=i
                    while(i<source.length && source[i]!='\n') i++
                    val lineText=source.substring(start,i)
                    val include=Regex("#include\\s*[<\"]([^>\"]+)[>\"]").find(lineText)?.groupValues?.get(1)
                    if(include!=null && include !in setOf("Arduino.h","Wire.h","SPI.h","Servo.h"))
                        throw ParseFailure(line,"Library $include is not supported by the bounded runtime.")
                    unsupportedLibraries.firstOrNull { lineText.contains(it) }?.let { library ->
                        throw ParseFailure(line,"Library \"$library\" is not supported by this simulator runtime yet.")
                    }
                    continue
                }
                if(c=='/' && source.getOrNull(i+1)=='/') {
                    while(i<source.length && source[i]!='\n') i++
                    continue
                }
                if(c=='/' && source.getOrNull(i+1)=='*') {
                    val start=line;i+=2
                    while(i<source.length && !(source[i]=='*' && source.getOrNull(i+1)=='/')) {
                        if(source[i]=='\n') line++
                        i++
                    }
                    if(i>=source.length) throw ParseFailure(start,"Unclosed comment")
                    i+=2;continue
                }
                if(c=='"') {
                    val start=line;val value=StringBuilder();i++
                    while(i<source.length && source[i]!='"') {
                        if(source[i]=='\n') throw ParseFailure(start,"Unclosed string")
                        if(source[i]=='\\') {
                            i++
                            if(i>=source.length) throw ParseFailure(start,"Unclosed string")
                            value.append(when(source[i]) { 'n' -> '\n';'t' -> '\t';else -> source[i] })
                        } else value.append(source[i])
                        i++
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
                if(pair in setOf("==","!=","<=",">=","&&","||","++","--","+=","-=")) { out+=Token(pair,line);i+=2;continue }
                if(c in "{}();,+-*/%!<>=") { out+=Token(c.toString(),line);i++;continue }
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
        private fun identifier():Token {
            if(at.kind!=1) throw ParseFailure(at.line,"Expected identifier, found '${at.text}'")
            return take()
        }
        fun program(source:String):FirmwareProgram {
            var setup:List<Statement>?=null;var loop:List<Statement>?=null
            val globals=mutableListOf<Statement>()
            val functions=mutableMapOf<String,List<Statement>>()
            while(at.text!="<EOF>") {
                if(at.text in setOf("const","static","volatile","int","long","float","double","bool","boolean","String")) {
                    globals+=statementPrefix()
                    continue
                }
                if(at.text=="Servo") {
                    take();val name=identifier();expect(";")
                    globals+=Statement.Assign(name.text,Expression.Literal("servo"),name.line)
                    continue
                }
                expect("void")
                val name=identifier()
                expect("(");expect(")")
                val body=block().body
                if(name.text=="setup") {
                    if(setup!=null) throw ParseFailure(name.line,"Duplicate setup()")
                    setup=globals+body
                } else if(name.text=="loop") {
                    if(loop!=null) throw ParseFailure(name.line,"Duplicate loop()")
                    loop=body
                } else { if(functions.put(name.text,body)!=null) throw ParseFailure(name.line,"Duplicate function ${name.text}") }
            }
            if(setup==null || loop==null) throw ParseFailure(at.line,"Both setup() and loop() are required.")
            val pins=Regex("\\b(?:D|A|GP|IO)\\d+\\b").findAll(source).map { it.value }.toSet()
            return FirmwareProgram(setup,loop,source,pins,functions)
        }
        private fun block():Statement.Block {
            val line=at.line;expect("{")
            val body=mutableListOf<Statement>()
            while(at.text!="}" && at.text!="<EOF>") body+=statement()
            expect("}")
            return Statement.Block(body,line)
        }
        private fun statement():Statement {
            val line=at.line
            if(at.text=="{") return block()
            if(accept("if")) {
                expect("(");val condition=expression();expect(")")
                val yes=statement();val no=if(accept("else")) statement() else null
                return Statement.Branch(condition,yes,no,line)
            }
            if(accept("while")) {
                expect("(");val condition=expression();expect(")")
                return Statement.While(condition,statement(),line)
            }
            if(accept("for")) {
                expect("(")
                val init=if(at.text==";") null else statementPrefix()
                if(init==null) expect(";")
                val condition=if(at.text==";") Expression.Literal(1.0) else expression()
                expect(";")
                val step=if(at.text==")") null else statementPrefix(requireSemicolon=false)
                expect(")")
                return Statement.For(init,condition,step,statement(),line)
            }
            return statementPrefix()
        }
        private fun statementPrefix(requireSemicolon:Boolean=true):Statement {
            val line=at.line
            while(accept("const") || accept("static") || accept("volatile")) { }
            val declared=at.text in setOf("int","long","float","double","bool","boolean","String")
            if(declared) take()
            val name=identifier().text
            if(at.text in setOf("++","--","+=","-=")) {
                val op=take().text
                val rhs=if(op in setOf("++","--")) Expression.Literal(1.0) else expression()
                if(requireSemicolon) expect(";")
                return Statement.Assign(name,Expression.Binary(Expression.Name(name),if(op.startsWith("+")) "+" else "-",rhs),line)
            }
            if(accept("=")) {
                val value=expression()
                if(requireSemicolon) expect(";")
                return Statement.Assign(name,value,line)
            }
            if(declared) {
                if(requireSemicolon) expect(";")
                return Statement.Assign(name,Expression.Literal(0.0),line)
            }
            if(at.text!="(") throw ParseFailure(line,"Expected assignment or supported API call")
            val call=call(name,line)
            if(requireSemicolon) expect(";")
            return Statement.Evaluate(call,line)
        }
        private fun call(name:String,line:Int):Expression.Call {
            expect("(");val args=mutableListOf<Expression>()
            if(at.text!=")") { args+=expression();while(accept(",")) args+=expression() }
            expect(")")
            return Expression.Call(name,args,line)
        }
        private fun expression(minPrecedence:Int=0):Expression {
            var left=when {
                accept("!") -> Expression.Unary("!",expression(7))
                accept("-") -> Expression.Unary("-",expression(7))
                accept("(") -> expression().also { expect(")") }
                at.kind==3 -> Expression.Literal(take().text.toDoubleOrNull()
                    ?: throw ParseFailure(at.line,"Invalid number"))
                at.kind==2 -> Expression.Literal(take().text)
                at.kind==1 -> {
                    val name=take()
                    if(at.text=="(") call(name.text,name.line) else Expression.Name(name.text)
                }
                else -> throw ParseFailure(at.line,"Expected expression")
            }
            val precedence=mapOf("||" to 1,"&&" to 2,"==" to 3,"!=" to 3,
                "<" to 4,">" to 4,"<=" to 4,">=" to 4,
                "+" to 5,"-" to 5,"*" to 6,"/" to 6,"%" to 6)
            while((precedence[at.text] ?: -1)>=minPrecedence) {
                val op=take().text;val right=expression(precedence.getValue(op)+1)
                left=Expression.Binary(left,op,right)
            }
            return left
        }
    }
}
