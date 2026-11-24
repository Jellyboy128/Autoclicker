package com.example.autoclicker

class ScriptError(var line: Int, message: String) : Exception(message)
class StopSignal : RuntimeException(null, null, false, false)
class BreakSignal : RuntimeException(null, null, false, false)

interface Env {
    fun click(x: Int, y: Int)
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long)
    fun sleep(ms: Long)
    fun pixel(x: Int, y: Int): String
    fun find(color: String, x1: Int, y1: Int, x2: Int, y2: Int, tol: Int): IntArray?
    fun log(msg: String)
}

enum class T { IDENT, NUM, STR, SYM, EOF }
class Tok(val type: T, val text: String, val line: Int)

class Lexer(private val s: String) {
    private fun isQuote(c: Char) = c == '"' || c == '\u201C' || c == '\u201D'

    fun lex(): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        var line = 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\n' -> { line++; i++ }
                c.isWhitespace() -> i++
                c == '/' && i + 1 < s.length && s[i + 1] == '/' -> {
                    while (i < s.length && s[i] != '\n') i++
                }
                c.isDigit() || (c == '-' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val st = i
                    i++
                    while (i < s.length && s[i].isDigit()) i++
                    out.add(Tok(T.NUM, s.substring(st, i), line))
                }
                c.isLetter() || c == '_' -> {
                    val st = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    out.add(Tok(T.IDENT, s.substring(st, i), line))
                }
                isQuote(c) -> {
                    i++
                    val st = i
                    while (i < s.length && !isQuote(s[i]) && s[i] != '\n') i++
                    if (i >= s.length || !isQuote(s[i])) throw ScriptError(line, "Не закрыта кавычка")
                    out.add(Tok(T.STR, s.substring(st, i), line))
                    i++
                }
                else -> {
                    val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
                    if (two == "==" || two == "!=" || two == "&&" || two == "||") {
                        out.add(Tok(T.SYM, two, line))
                        i += 2
                    } else if ("(){};,!".indexOf(c) >= 0) {
                        out.add(Tok(T.SYM, c.toString(), line))
                        i++
                    } else {
                        throw ScriptError(line, "Неизвестный символ «$c»")
                    }
                }
            }
        }
        out.add(Tok(T.EOF, "", line))
        return out
    }
}

sealed class Expr(val line: Int)
class Num(line: Int, val v: Int) : Expr(line)
class Str(line: Int, val s: String) : Expr(line)
class Var(line: Int, val name: String) : Expr(line)
class Not(line: Int, val e: Expr) : Expr(line)
class Bin(line: Int, val op: String, val l: Expr, val r: Expr) : Expr(line)
class Func(line: Int, val name: String, val args: List<Expr>) : Expr(line)

sealed class Stmt(val line: Int)
class Call(line: Int, val name: String, val args: List<Expr>) : Stmt(line)
class Repeat(line: Int, val count: Expr?, val body: List<Stmt>) : Stmt(line)
class If(line: Int, val cond: Expr, val then: List<Stmt>, val els: List<Stmt>) : Stmt(line)

class Parser(private val toks: List<Tok>) {
    private var p = 0

    private fun peek(): Tok = toks[p]
    private fun next(): Tok {
        val t = toks[p]
        if (t.type != T.EOF) p++
        return t
    }
    private fun isSym(s: String) = peek().type == T.SYM && peek().text == s
    private fun isWord(w: String) = peek().type == T.IDENT && peek().text.lowercase() == w
    private fun expectSym(s: String) {
        if (!isSym(s)) throw ScriptError(peek().line, "Ожидалось «$s»")
        next()
    }

    fun parseProgram(): List<Stmt> {
        val out = ArrayList<Stmt>()
        while (peek().type != T.EOF) out.add(parseStmt())
        return out
    }

    private fun parseBlock(): List<Stmt> {
        expectSym("{")
        val out = ArrayList<Stmt>()
        while (!isSym("}")) {
            if (peek().type == T.EOF) throw ScriptError(peek().line, "Не закрыта «{»")
            out.add(parseStmt())
        }
        expectSym("}")
        return out
    }

    private fun parseArgs(): List<Expr> {
        val args = ArrayList<Expr>()
        if (!isSym(")")) {
            args.add(parseExpr())
            while (isSym(",")) {
                next()
                args.add(parseExpr())
            }
        }
        expectSym(")")
        return args
    }

    private fun parseStmt(): Stmt {
        val t = next()
        if (t.type == T.EOF) throw ScriptError(t.line, "Неожиданный конец скрипта")
        if (t.type != T.IDENT) throw ScriptError(t.line, "Неожиданно: «${t.text}»")
        return when (t.text.lowercase()) {
            "repeat" -> {
                expectSym("(")
                val count: Expr? = if (isWord("forever")) {
                    next()
                    null
                } else {
                    parseExpr()
                }
                expectSym(")")
                Repeat(t.line, count, parseBlock())
            }
            "if" -> {
                expectSym("(")
                val c = parseExpr()
                expectSym(")")
                val th = parseBlock()
                var el: List<Stmt> = emptyList()
                if (isWord("else")) {
                    next()
                    el = if (isWord("if")) listOf(parseStmt()) else parseBlock()
                }
                If(t.line, c, th, el)
            }
            else -> {
                expectSym("(")
                val args = parseArgs()
                if (isSym(";")) next()
                Call(t.line, t.text.lowercase(), args)
            }
        }
    }

    private fun parseExpr(): Expr = parseOr()

    private fun parseOr(): Expr {
        var l = parseAnd()
        while (isSym("||")) {
            val op = next()
            l = Bin(op.line, "||", l, parseAnd())
        }
        return l
    }

    private fun parseAnd(): Expr {
        var l = parseCmp()
        while (isSym("&&")) {
            val op = next()
            l = Bin(op.line, "&&", l, parseCmp())
        }
        return l
    }

    private fun parseCmp(): Expr {
        val l = parseUnary()
        if (isSym("==") || isSym("!=")) {
            val op = next()
            return Bin(op.line, op.text, l, parseUnary())
        }
        return l
    }

    private fun parseUnary(): Expr {
        if (isSym("!")) {
            val t = next()
            return Not(t.line, parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        val t = next()
        return when (t.type) {
            T.NUM -> Num(t.line, t.text.toIntOrNull() ?: throw ScriptError(t.line, "Слишком большое число"))
            T.STR -> Str(t.line, t.text)
            T.IDENT -> if (isSym("(")) {
                next()
                Func(t.line, t.text.lowercase(), parseArgs())
            } else {
                Var(t.line, t.text.lowercase())
            }
            T.SYM -> if (t.text == "(") {
                val e = parseExpr()
                expectSym(")")
                e
            } else {
                throw ScriptError(t.line, "Неожиданно: «${t.text}»")
            }
            T.EOF -> throw ScriptError(t.line, "Неожиданный конец скрипта")
        }
    }
}

class Interpreter(private val env: Env) {
    private var loopDepth = 0
    private var foundX = -1
    private var foundY = -1

    fun runOnce(prog: List<Stmt>) {
        loopDepth = 0
        exec(prog)
    }

    private fun exec(list: List<Stmt>) {
        for (s in list) execOne(s)
    }

    private fun execOne(s: Stmt) {
        if (Thread.interrupted()) throw InterruptedException()
        try {
            when (s) {
                is Call -> call(s)
                is Repeat -> {
                    val cnt = s.count
                    val n: Int? = if (cnt == null) null else toInt(eval(cnt), s.line, "repeat")
                    loopDepth++
                    try {
                        var i = 0
                        while (n == null || i < n) {
                            if (Thread.interrupted()) throw InterruptedException()
                            try {
                                exec(s.body)
                            } catch (b: BreakSignal) {
                                break
                            }
                            i++
                        }
                    } finally {
                        loopDepth--
                    }
                }
                is If -> if (toBool(eval(s.cond), s.line)) exec(s.then) else exec(s.els)
            }
        } catch (e: ScriptError) {
            if (e.line == 0) e.line = s.line
            throw e
        }
    }

    private fun call(c: Call) {
        val a = c.args.map { eval(it) }
        fun n(i: Int) = toInt(a[i], c.line, c.name)
        fun need(k: Int) {
            if (a.size != k) throw ScriptError(c.line, "${c.name}() ожидает аргументов: $k")
        }
        when (c.name) {
            "click", "tap" -> { need(2); env.click(n(0), n(1)) }
            "sleep", "wait" -> { need(1); env.sleep(n(0).toLong()) }
            "swipe" -> { need(5); env.swipe(n(0), n(1), n(2), n(3), n(4).toLong()) }
            "log" -> { need(1); env.log(a[0].toString()) }
            "stop" -> throw StopSignal()
            "break" -> throw (if (loopDepth > 0) BreakSignal() else StopSignal())
            else -> throw ScriptError(c.line, "Неизвестная команда: ${c.name}")
        }
    }

    private fun eval(e: Expr): Any = when (e) {
        is Num -> e.v
        is Str -> e.s
        is Var -> when (e.name) {
            "foundx" -> foundX
            "foundy" -> foundY
            "true" -> true
            "false" -> false
            else -> throw ScriptError(e.line, "Неизвестное имя: ${e.name}")
        }
        is Not -> !toBool(eval(e.e), e.line)
        is Bin -> when (e.op) {
            "&&" -> toBool(eval(e.l), e.line) && toBool(eval(e.r), e.line)
            "||" -> toBool(eval(e.l), e.line) || toBool(eval(e.r), e.line)
            "==" -> same(eval(e.l), eval(e.r))
            else -> !same(eval(e.l), eval(e.r))
        }
        is Func -> func(e)
    }

    private fun func(f: Func): Any {
        val a = f.args.map { eval(it) }
        fun n(i: Int) = toInt(a[i], f.line, f.name)
        return when (f.name) {
            "pixel" -> {
                if (a.size != 2) throw ScriptError(f.line, "pixel(x, y) ожидает 2 аргумента")
                env.pixel(n(0), n(1))
            }
            "findcolor" -> {
                if (a.size != 5 && a.size != 6) {
                    throw ScriptError(f.line, "findColor(цвет, x1, y1, x2, y2, допуск) ожидает 5 или 6 аргументов")
                }
                val color = a[0] as? String
                    ?: throw ScriptError(f.line, "findColor: первый аргумент — цвет в кавычках, например \"#FF0000\"")
                val tol = if (a.size == 6) n(5) else 10
                val r = try {
                    env.find(color, n(1), n(2), n(3), n(4), tol)
                } catch (ex: IllegalArgumentException) {
                    throw ScriptError(f.line, "Неверный цвет «$color», нужен вид \"#RRGGBB\"")
                }
                if (r == null) {
                    false
                } else {
                    foundX = r[0]
                    foundY = r[1]
                    true
                }
            }
            else -> throw ScriptError(f.line, "Неизвестная функция: ${f.name}")
        }
    }

    private fun same(a: Any, b: Any): Boolean =
        if (a is String && b is String) a.equals(b, ignoreCase = true) else a == b

    private fun toInt(v: Any, line: Int, name: String): Int =
        v as? Int ?: throw ScriptError(line, "$name: ожидалось число")

    private fun toBool(v: Any, line: Int): Boolean =
        v as? Boolean ?: throw ScriptError(line, "Условие должно быть вида «да/нет»")
}
