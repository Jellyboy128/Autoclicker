package com.example.autoclicker

class ScriptError(var line: Int, message: String) : Exception(message)
class StopSignal : RuntimeException(null, null, false, false)
class BreakSignal : RuntimeException(null, null, false, false)

interface Env {
    fun click(x: Int, y: Int)
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long)
    fun sleep(ms: Long)
    fun pixel(x: Int, y: Int): String
    fun find(colors: List<String>, x1: Int, y1: Int, x2: Int, y2: Int, tol: Int): IntArray?
    fun log(msg: String)
    fun setShotRate(perSec: Int)
}

class Pt(val x: Int, val y: Int)

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
class PointDef(line: Int, val name: String, val x: Expr, val y: Expr) : Stmt(line)
class ColorDef(line: Int, val name: String, val value: Expr) : Stmt(line)

class Parser(private val toks: List<Tok>) {
    private var p = 0
    private var depth = 0
    private var header = true

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
        while (peek().type != T.EOF) {
            val s = parseStmt()
            if (!(s is PointDef || s is ColorDef || (s is Call && s.name == "fps"))) header = false
            out.add(s)
        }
        return out
    }

    private fun parseBlock(): List<Stmt> {
        expectSym("{")
        depth++
        val out = ArrayList<Stmt>()
        while (!isSym("}")) {
            if (peek().type == T.EOF) throw ScriptError(peek().line, "Не закрыта «{»")
            out.add(parseStmt())
        }
        expectSym("}")
        depth--
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
            "point" -> {
                expectSym("(")
                val nt = next()
                if (nt.type != T.IDENT && nt.type != T.STR) {
                    throw ScriptError(nt.line, "point: сначала имя точки, например point(кнопка, 540, 1200)")
                }
                expectSym(",")
                val px = parseExpr()
                expectSym(",")
                val py = parseExpr()
                expectSym(")")
                if (isSym(";")) next()
                PointDef(t.line, nt.text.lowercase(), px, py)
            }
            "color" -> {
                expectSym("(")
                val cn = next()
                if (cn.type != T.IDENT && cn.type != T.STR) {
                    throw ScriptError(cn.line, "color: сначала имя цвета, например color(красный, \"#FF0000\")")
                }
                expectSym(",")
                val cv = parseExpr()
                expectSym(")")
                if (isSym(";")) next()
                ColorDef(t.line, cn.text.lowercase(), cv)
            }
            else -> {
                expectSym("(")
                val args = parseArgs()
                if (isSym(";")) next()
                val name = t.text.lowercase()
                if (name == "fps" && (depth > 0 || !header)) {
                    throw ScriptError(t.line, "fps() должна стоять в самом начале скрипта")
                }
                Call(t.line, name, args)
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
    private var foundColor = ""
    private val points = HashMap<String, Pt>()
    private val colors = HashMap<String, String>()

    fun runOnce(prog: List<Stmt>) {
        loopDepth = 0
        for (s in prog) if (s is PointDef || s is ColorDef) execOne(s)
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
                is PointDef -> {
                    points[s.name] = Pt(toInt(eval(s.x), s.line, "point"), toInt(eval(s.y), s.line, "point"))
                }
                is ColorDef -> {
                    val cv = eval(s.value)
                    if (cv !is String || !Regex("#[0-9a-fA-F]{6}").matches(cv)) {
                        throw ScriptError(s.line, "color: нужен цвет вида \"#RRGGBB\"")
                    }
                    colors[s.name] = cv
                }
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

    private fun flat(v: Any): List<Any> = if (v is Pt) listOf(v.x, v.y) else listOf(v)

    private fun call(c: Call) {
        val a = c.args.flatMap { flat(eval(it)) }
        fun n(i: Int) = toInt(a[i], c.line, c.name)
        fun need(k: Int) {
            if (a.size != k) throw ScriptError(c.line, "${c.name}() ожидает аргументов: $k")
        }
        when (c.name) {
            "click", "tap" -> { need(2); env.click(n(0), n(1)) }
            "sleep", "wait" -> { need(1); env.sleep(n(0).toLong()) }
            "swipe" -> { need(5); env.swipe(n(0), n(1), n(2), n(3), n(4).toLong()) }
            "log" -> { need(1); env.log(a[0].toString()) }
            "fps" -> {
                need(1)
                if (n(0) < 1 || n(0) > 3) {
                    throw ScriptError(c.line, "fps: допустимо от 1 до 3 (скриншотов в секунду)")
                }
                env.setShotRate(n(0))
            }
            "stop" -> throw StopSignal()
            "break" -> throw (if (loopDepth > 0) BreakSignal() else StopSignal())
            else -> throw ScriptError(c.line, "Неизвестная команда: ${c.name}")
        }
    }

    private fun lookup(e: Var): Any = points[e.name] ?: colors[e.name] ?: when (e.name) {
        "found" -> Pt(foundX, foundY)
        "foundx" -> foundX
        "foundy" -> foundY
        "foundcolor" -> foundColor
        "true" -> true
        "false" -> false
        else -> throw ScriptError(e.line, "Неизвестное имя: ${e.name}")
    }

    private fun eval(e: Expr): Any = when (e) {
        is Num -> e.v
        is Str -> e.s
        is Var -> lookup(e)
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
        val a = f.args.flatMap { flat(eval(it)) }
        fun n(i: Int) = toInt(a[i], f.line, f.name)
        return when (f.name) {
            "pixel" -> {
                if (a.size != 2) throw ScriptError(f.line, "pixel(x, y) ожидает точку или 2 числа")
                env.pixel(n(0), n(1))
            }
            "findcolor" -> {
                var ci = 0
                while (ci < a.size && a[ci] is String) ci++
                val rest = a.size - ci
                if (ci == 0 || (rest != 4 && rest != 5)) {
                    throw ScriptError(
                        f.line,
                        "findColor(цвет1, цвет2, ..., угол1, угол2, допуск): сначала цвета в кавычках, затем область и необязательный допуск"
                    )
                }
                val colors = a.subList(0, ci).map { it as String }
                val tol = if (rest == 5) n(ci + 4) else 10
                val r = try {
                    env.find(colors, n(ci), n(ci + 1), n(ci + 2), n(ci + 3), tol)
                } catch (ex: IllegalArgumentException) {
                    throw ScriptError(f.line, "Неверный цвет в findColor, нужен вид \"#RRGGBB\"")
                }
                if (r == null) {
                    false
                } else {
                    foundX = r[0]
                    foundY = r[1]
                    foundColor = colors[r[2]]
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
