package com.marcadorar.core

// Códec del protocolo v1 — puente entre los tipos del núcleo (Cmd/Estado/Config)
// y los mensajes JSON que viajan por WebSocket (docs/protocolo.md + ref/server.js).
//
// Fuente de verdad de los nombres de campo: el servidor en ejecución (estado.js),
// que usa `op2` para clock/shot/period y `op:"state",op2:"reset"` para el reset.

// ---- comandos: núcleo -> wire ----

/** Serializa un Cmd al objeto `cmd` que viaja dentro de {tipo:"cmd", cmd:{...}}. */
fun Cmd.aWire(): Json.Obj = when (this) {
    is Cmd.ScoreMas -> Json.obj(
        "op" to Json.str("score+"), "team" to Json.str(team.wire()),
        "delta" to Json.num(delta),
    )
    is Cmd.ScoreMenos -> Json.obj("op" to Json.str("score-"), "team" to Json.str(team.wire()))
    is Cmd.FoulMas -> Json.obj("op" to Json.str("foul+"), "team" to Json.str(team.wire()))
    is Cmd.FoulMenos -> Json.obj("op" to Json.str("foul-"), "team" to Json.str(team.wire()))
    is Cmd.Clock -> Json.obj("op" to Json.str("clock"), "op2" to Json.str(op2.wire()))
    is Cmd.Shot -> Json.obj("op" to Json.str("shot"), "op2" to Json.str(op2.wire()))
    is Cmd.Period -> {
        val base = LinkedHashMap<String, Json>()
        base["op"] = Json.str("period"); base["op2"] = Json.str(op2.wire())
        if (value != null) base["value"] = Json.num(value)
        Json.Obj(base)
    }
    Cmd.Buzzer -> Json.obj("op" to Json.str("buzzer"))
    // El reset viaja como op:"state", op2:"reset" (así lo parsea estado.js).
    Cmd.ResetEstado -> Json.obj("op" to Json.str("state"), "op2" to Json.str("reset"))
}

/** Empaqueta un Cmd en el mensaje completo {tipo:"cmd", cmd:{...}}. */
fun Cmd.aMensajeWire(): Json =
    Json.Obj(LinkedHashMap<String, Json>().apply {
        put("tipo", Json.str("cmd")); put("cmd", aWire())
    })

// ---- comandos: wire -> núcleo (null si op desconocido) ----

fun cmdDesdeWire(o: Json.Obj?): Cmd? {
    val op = o?.str("op") ?: return null
    val team = { o.str("team").equipo() }
    return when (op) {
        "score+" -> Cmd.ScoreMas(team(), o.int("delta") ?: 1)
        "score-" -> Cmd.ScoreMenos(team())
        "foul+" -> Cmd.FoulMas(team())
        "foul-" -> Cmd.FoulMenos(team())
        "clock" -> o.str("op2")?.reloj()?.let { Cmd.Clock(it) }
        "shot" -> o.str("op2")?.shot()?.let { Cmd.Shot(it) }
        "period" -> o.str("op2")?.period()?.let { Cmd.Period(it, o.int("value")) }
        "buzzer" -> Cmd.Buzzer
        "state" -> if (o.str("op2") == "reset" || o.str("op2") == "fullReset") Cmd.ResetEstado else null
        else -> null
    }
}

// ---- estado: wire -> núcleo ----

/** Parsea un GameState completo (mensaje {tipo:"state"} o el `state` del welcome). */
fun estadoDesdeWire(o: Json.Obj?): Estado? {
    if (o == null) return null
    val clock = o.int("clockSec") ?: return null
    val shot = o.int("shotClockSec") ?: return null
    val period = o.int("period") ?: return null
    val running = o.bool("running") ?: return null
    val pos = o.str("possession").equipo()
    val home = o.campo("home").asObj
    val vis = o.campo("visitor").asObj
    return Estado(
        clockSec = clock, shotClockSec = shot, period = period, running = running,
        possession = pos,
        home = Equipo(home.int("score") ?: 0, home.int("fouls") ?: 0),
        visitor = Equipo(vis.int("score") ?: 0, vis.int("fouls") ?: 0),
    )
}

/** Serializa un Estado al GameState del wire. */
fun Estado.aWire(): Json.Obj {
    fun equipo(t: Team) = if (t == Team.VISITOR) visitor else home
    fun eqObj(e: Equipo) = Json.obj("score" to Json.num(e.score), "fouls" to Json.num(e.fouls))
    return Json.obj(
        "clockSec" to Json.num(clockSec),
        "shotClockSec" to Json.num(shotClockSec),
        "period" to Json.num(period),
        "running" to Json.bool(running),
        "possession" to Json.str(possession.wire()),
        "home" to eqObj(equipo(Team.HOME)),
        "visitor" to eqObj(equipo(Team.VISITOR)),
    )
}

// ---- config ----

fun Config.aWire(): Json.Obj = Json.obj(
    "periodMinutes" to Json.num(periodMinutes),
    "periods" to Json.num(periods),
    "foulLimit" to Json.num(foulLimit),
    "rule24_14" to Json.bool(rule24_14),
    "homeName" to Json.str(homeName),
    "visitorName" to Json.str(visitorName),
)

/** Merge tolerante: toma del wire solo los campos presentes, conserva el resto. */
fun configDesdeWire(previa: Config, o: Json.Obj?): Config {
    if (o == null) return previa
    return Config(
        periodMinutes = o.int("periodMinutes") ?: previa.periodMinutes,
        periods = o.int("periods") ?: previa.periods,
        foulLimit = o.int("foulLimit") ?: previa.foulLimit,
        rule24_14 = o.bool("rule24_14") ?: previa.rule24_14,
        homeName = o.str("homeName") ?: previa.homeName,
        visitorName = o.str("visitorName") ?: previa.visitorName,
    )
}

// ---- colocación del tablero (AR) ----

/** Serializa una Colocacion al objeto `colocacion` del wire. */
fun Colocacion.aWire(): Json.Obj {
    val o = LinkedHashMap<String, Json>()
    o["ancho"] = Json.num(ancho.toDouble()); o["alto"] = Json.num(alto.toDouble())
    o["x"] = Json.num(x.toDouble()); o["y"] = Json.num(y.toDouble()); o["z"] = Json.num(z.toDouble())
    o["rot"] = Json.num(rot.toDouble()); o["inc"] = Json.num(inc.toDouble())
    o["geo"] = geo?.let { g ->
        Json.obj(
            "lat" to Json.num(g.lat), "lon" to Json.num(g.lon),
            "alt" to Json.num(g.alt), "rot" to Json.num(g.rot.toDouble()),
        )
    } ?: Json.Null
    return Json.Obj(o)
}

/** Merge tolerante: toma del wire solo los campos presentes, conserva el resto.
 *  `geo`: ausente → conserva la anterior · null explícito → la limpia · objeto → nueva. */
fun colocacionDesdeWire(previa: Colocacion, o: Json.Obj?): Colocacion {
    if (o == null) return previa
    val geo = when (val gc = o.campo("geo")) {
        null -> previa.geo
        is Json.Null -> null
        is Json.Obj -> GeoColocacion(
            lat = gc.numD("lat") ?: return previa,
            lon = gc.numD("lon") ?: return previa,
            alt = gc.numD("alt") ?: return previa,
            rot = gc.numF("rot") ?: 0f,
        )
        else -> previa.geo
    }
    return Colocacion(
        ancho = o.numF("ancho") ?: previa.ancho,
        alto = o.numF("alto") ?: previa.alto,
        x = o.numF("x") ?: previa.x,
        y = o.numF("y") ?: previa.y,
        z = o.numF("z") ?: previa.z,
        rot = o.numF("rot") ?: previa.rot,
        inc = o.numF("inc") ?: previa.inc,
        geo = geo,
    )
}

// ---- mensajes de sesión (admin <-> servidor) ----

fun mensajeJoin(instancia: String, deviceId: String): Json = Json.obj(
    "tipo" to Json.str("join"),
    "instancia" to Json.str(instancia),
    "deviceId" to Json.str(deviceId),
)

fun mensajeClaim(): Json = Json.obj("tipo" to Json.str("claim"))
fun mensajeResign(): Json = Json.obj("tipo" to Json.str("resign"))
fun mensajeHeartbeat(): Json = Json.obj("tipo" to Json.str("heartbeat"))

/** Mensaje {tipo:"cmd", cmd:{op:"setup", config:{...}}} — la única vía de cambio de config. */
fun mensajeSetup(config: Config): Json =
    Json.Obj(LinkedHashMap<String, Json>().apply {
        put("tipo", Json.str("cmd"))
        put("cmd", Json.Obj(LinkedHashMap<String, Json>().apply {
            put("op", Json.str("setup")); put("config", config.aWire())
        }))
    })

/** Mensaje {tipo:"cmd", cmd:{op:"place", ...}} — la única vía de colocación AR. */
fun mensajePlace(col: Colocacion, borrarGeo: Boolean = false): Json =
    Json.Obj(LinkedHashMap<String, Json>().apply {
        put("tipo", Json.str("cmd"))
        put("cmd", Json.Obj(LinkedHashMap<String, Json>().apply {
            put("op", Json.str("place"))
            putAll(col.aWire().campos)
            // geo:null explícito borra el fijado; si no se pide y la colocación
            // no trae geo, se omite el campo para no clobberar una geo existente
            // con un simple ajuste de botón.
            if (borrarGeo) put("geo", Json.Null) else if (col.geo == null) remove("geo")
        }))
    })

/**
 * Mensaje {tipo:"geo", geo:{lat,lon,alt,rot}} — reporte de la pose WGS84 del
 * ancla AR. Lo manda el dispositivo que TIENE la sesión AR (único que puede
 * conocerla): no es un comando, así que no exige rol admin.
 */
fun mensajeGeo(g: GeoColocacion): Json = Json.obj(
    "tipo" to Json.str("geo"),
    "geo" to Json.obj(
        "lat" to Json.num(g.lat), "lon" to Json.num(g.lon),
        "alt" to Json.num(g.alt), "rot" to Json.num(g.rot.toDouble()),
    ),
)

// ---- mensajes entrantes tipados ----

sealed interface Entrada {
    data class Welcome(val rol: String, val config: Config, val estado: Estado, val colocacion: Colocacion) : Entrada
    data class EstadoMsg(val estado: Estado) : Entrada
    data class ConfigMsg(val config: Config) : Entrada
    data class ColocacionMsg(val colocacion: Colocacion) : Entrada
    data class Role(val granted: Boolean, val motivo: String?) : Entrada
    data class Error(val code: String, val message: String) : Entrada
    data object Buzzer : Entrada
    data object ShotExpired : Entrada
    data object Fijar : Entrada
    data class Presencia(val adminOnline: Boolean, val espectadores: Int) : Entrada
    data class Desconocido(val tipo: String) : Entrada
}

/** Tipifica un mensaje JSON entrante según su campo `tipo`. */
fun entradaDesdeWire(texto: String): Entrada? {
    val o = parsearJson(texto)?.asObj ?: return null
    return when (val tipo = o.str("tipo")) {
        "welcome" -> Entrada.Welcome(
            rol = o.str("rol") ?: o.str("role") ?: "espectador",
            config = configDesdeWire(Config(), o.campo("config").asObj),
            estado = estadoDesdeWire(o.campo("state").asObj)
                ?: estadoInicial(configDesdeWire(Config(), o.campo("config").asObj)),
            colocacion = colocacionDesdeWire(Colocacion(), o.campo("colocacion").asObj),
        )
        "state" -> o.campo("state").asObj?.let { st ->
            estadoDesdeWire(st)?.let { Entrada.EstadoMsg(it) }
        }
        "config" -> Entrada.ConfigMsg(configDesdeWire(Config(), o.campo("config").asObj))
        "colocacion" -> Entrada.ColocacionMsg(colocacionDesdeWire(Colocacion(), o.campo("colocacion").asObj))
        "role" -> Entrada.Role(o.bool("granted") ?: false, o.str("motivo"))
        "error" -> Entrada.Error(o.str("code") ?: "?", o.str("message") ?: "")
        "buzzer" -> Entrada.Buzzer
        "shot-expired" -> Entrada.ShotExpired
        "fijar" -> Entrada.Fijar
        "presence" -> Entrada.Presencia(
            adminOnline = o.bool("adminOnline") ?: false,
            espectadores = o.int("espectadores") ?: 0,
        )
        else -> tipo?.let { Entrada.Desconocido(it) }
    }
}

// ---- mapeo de enums a los literales del wire ----

private fun Team.wire() = if (this == Team.VISITOR) "visitor" else "home"
private fun String?.equipo() = if (this == "visitor") Team.VISITOR else Team.HOME

private fun ClockOp.wire() = when (this) {
    ClockOp.START -> "start"; ClockOp.PAUSE -> "pause"; ClockOp.RESET -> "reset"
}
private fun String.reloj() = when (this) {
    "start" -> ClockOp.START; "pause" -> ClockOp.PAUSE; "reset" -> ClockOp.RESET
    else -> null
}

private fun ShotOp.wire() = when (this) {
    ShotOp.RESET24 -> "reset24"; ShotOp.RESET14 -> "reset14"
    ShotOp.INC -> "inc"; ShotOp.DEC -> "dec"
}
private fun String.shot() = when (this) {
    "reset24" -> ShotOp.RESET24; "reset14" -> ShotOp.RESET14
    "inc" -> ShotOp.INC; "dec" -> ShotOp.DEC
    else -> null
}

private fun PeriodOp.wire() = when (this) {
    PeriodOp.NEXT -> "next"; PeriodOp.PREV -> "prev"; PeriodOp.SET -> "set"
}
private fun String.period() = when (this) {
    "next" -> PeriodOp.NEXT; "prev" -> PeriodOp.PREV; "set" -> PeriodOp.SET
    else -> null
}
