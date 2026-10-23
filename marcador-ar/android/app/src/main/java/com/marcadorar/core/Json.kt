package com.marcadorar.core

// JSON mínimo sin dependencias — suficiente para el protocolo v1 (docs/protocolo.md).
// Objetos, arreglos, cadenas, números, booleanos y null. Escritura determinista
// (orden de inserción) para que los tests puedan comparar el texto exacto.

sealed class Json {
    data class Obj(val campos: LinkedHashMap<String, Json> = LinkedHashMap()) : Json()
    data class Arr(val items: List<Json> = emptyList()) : Json()
    data class Str(val v: String) : Json()
    data class Num(val v: Double) : Json()
    data class Bool(val v: Boolean) : Json()
    data object Null : Json()

    companion object {
        fun obj(vararg p: Pair<String, Json>): Obj =
            Obj(LinkedHashMap<String, Json>().apply { p.forEach { (k, v) -> put(k, v) } })
        fun str(v: String) = Str(v)
        fun num(v: Int) = Num(v.toDouble())
        fun num(v: Long) = Num(v.toDouble())
        fun num(v: Double) = Num(v)
        fun bool(v: Boolean) = Bool(v)
    }
}

// ---- lectura tipada (tolerante: devuelve null si falta o el tipo no casa) ----

val Json?.asObj: Json.Obj? get() = this as? Json.Obj
val Json?.asArr: Json.Arr? get() = this as? Json.Arr
val Json?.asStr: String? get() = (this as? Json.Str)?.v
val Json?.asBool: Boolean? get() = (this as? Json.Bool)?.v
val Json?.asInt: Int? get() = (this as? Json.Num)?.v?.toInt()

fun Json.Obj?.campo(n: String): Json? = this?.campos?.get(n)
fun Json.Obj?.str(n: String): String? = campo(n).asStr
fun Json.Obj?.int(n: String): Int? = campo(n).asInt
fun Json.Obj?.bool(n: String): Boolean? = campo(n).asBool

/** Float/Double tolerante: solo acepta números (null si falta o no es número). */
fun Json.Obj?.numD(n: String): Double? = (campo(n) as? Json.Num)?.v
fun Json.Obj?.numF(n: String): Float? = numD(n)?.toFloat()

// ---- escritura ----

fun Json.escribir(): String = when (this) {
    is Json.Null -> "null"
    is Json.Bool -> if (v) "true" else "false"
    is Json.Num -> if (v == Math.floor(v) && !v.isInfinite())
        Math.floor(v).toLong().toString() else v.toString()
    is Json.Str -> escapar(v)
    is Json.Arr -> items.joinToString(",", "[", "]") { it.escribir() }
    is Json.Obj -> campos.entries.joinToString(",", "{", "}") { (k, vv) ->
        "${escapar(k)}:${vv.escribir()}"
    }
}

private fun escapar(s: String): String {
    val sb = StringBuilder(s.length + 2).append('"')
    for (c in s) when (c) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
    }
    return sb.append('"').toString()
}

// ---- parser recursivo-descendente ----

/** Parsea JSON; devuelve null si el texto está vacío o es inválido. */
fun parsearJson(texto: String): Json? {
    val p = _Parser(texto)
    p.espacios()
    val v = p.valor() ?: return null
    p.espacios()
    return if (p.fin()) v else null
}

private class _Parser(private val s: String) {
    private var i = 0
    fun fin() = i >= s.length
    fun espacios() { while (i < s.length && s[i].isWhitespace()) i++ }

    fun valor(): Json? {
        if (fin()) return null
        return when (s[i]) {
            '{' -> objeto()
            '[' -> arreglo()
            '"' -> Json.Str(cadena())
            't' -> literal("true", Json.Bool(true))
            'f' -> literal("false", Json.Bool(false))
            'n' -> literal("null", Json.Null)
            else -> numero()
        }
    }

    private fun literal(txt: String, v: Json): Json? {
        if (s.regionMatches(i, txt, 0, txt.length)) { i += txt.length; return v }
        return null
    }

    private fun objeto(): Json? {
        i++ // {
        val o = Json.Obj()
        espacios()
        if (!fin() && s[i] == '}') { i++; return o }
        while (true) {
            espacios()
            if (fin() || s[i] != '"') return null
            val k = cadena()
            espacios()
            if (fin() || s[i] != ':') return null
            i++
            espacios()
            val v = valor() ?: return null
            o.campos[k] = v
            espacios()
            if (fin()) return null
            when (s[i]) {
                ',' -> i++
                '}' -> { i++; return o }
                else -> return null
            }
        }
    }

    private fun arreglo(): Json? {
        i++ // [
        val items = ArrayList<Json>()
        espacios()
        if (!fin() && s[i] == ']') { i++; return Json.Arr(items) }
        while (true) {
            espacios()
            val v = valor() ?: return null
            items.add(v)
            espacios()
            if (fin()) return null
            when (s[i]) {
                ',' -> i++
                ']' -> { i++; return Json.Arr(items) }
                else -> return null
            }
        }
    }

    private fun cadena(): String {
        i++ // "
        val sb = StringBuilder()
        while (!fin()) {
            val c = s[i++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' && !fin() -> {
                    when (val e = s[i++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 4 <= s.length) {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                        }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun numero(): Json? {
        val inicio = i
        if (!fin() && (s[i] == '-' || s[i] == '+')) i++
        while (!fin() && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' ||
                    s[i] == '-' || s[i] == '+')) i++
        if (i == inicio) return null
        return s.substring(inicio, i).toDoubleOrNull()?.let { Json.Num(it) }
    }
}
