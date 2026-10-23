package com.marcadorar.core

// Máquina de estados del partido — port directo de ref/estado.js (protocolo v1).
// Reducer puro: sin red ni reloj propios; el servidor le pasa los ticks.
// Inmutable: cada comando devuelve un Estado nuevo (data class copy).

enum class Team { HOME, VISITOR }
enum class ClockOp { START, PAUSE, RESET }
enum class ShotOp { RESET24, RESET14, INC, DEC }
enum class PeriodOp { NEXT, PREV, SET }

sealed interface Cmd {
    data class ScoreMas(val team: Team, val delta: Int = 1) : Cmd
    data class ScoreMenos(val team: Team) : Cmd
    data class FoulMas(val team: Team) : Cmd
    data class FoulMenos(val team: Team) : Cmd
    data class Clock(val op2: ClockOp) : Cmd
    data class Shot(val op2: ShotOp) : Cmd
    data class Period(val op2: PeriodOp, val value: Int? = null) : Cmd
    data object Buzzer : Cmd
    data object ResetEstado : Cmd
}

sealed interface Evento {
    data object Buzzer : Evento
    data class Bonus(val team: Team) : Evento
    data object ShotExpired : Evento
}

data class Config(
    val periodMinutes: Int = 10,
    val periods: Int = 4,
    val foulLimit: Int = 5,
    val rule24_14: Boolean = true,
    val homeName: String = "LOCAL TEAM",
    val visitorName: String = "VISITOR TEAM",
)

data class Equipo(val score: Int = 0, val fouls: Int = 0)

data class Estado(
    val clockSec: Int,
    val shotClockSec: Int,
    val period: Int,
    val running: Boolean,
    val possession: Team,
    val home: Equipo,
    val visitor: Equipo,
)

data class Resultado(val estado: Estado, val eventos: List<Evento>)

fun estadoInicial(cfg: Config): Estado = Estado(
    clockSec = cfg.periodMinutes * 60,
    shotClockSec = if (cfg.rule24_14) 24 else 0,
    period = 1,
    running = false,
    possession = Team.HOME,
    home = Equipo(),
    visitor = Equipo(),
)

fun aplicar(cfg: Config, estado: Estado, cmd: Cmd): Resultado {
    val ev = mutableListOf<Evento>()
    fun equipo(t: Team) = if (t == Team.VISITOR) estado.visitor else estado.home
    fun reemplazar(t: Team, e: Equipo) =
        if (t == Team.VISITOR) estado.copy(visitor = e) else estado.copy(home = e)

    val nueva: Estado = when (cmd) {
        is Cmd.ScoreMas -> {
            val e = equipo(cmd.team)
            val s = reemplazar(cmd.team, e.copy(score = e.score + cmd.delta))
            val posesion = if (cmd.team == Team.HOME) Team.VISITOR else Team.HOME
            val shot = if (cfg.rule24_14) 24 else s.shotClockSec // canasta → posesión nueva
            s.copy(possession = posesion, shotClockSec = shot)
        }
        is Cmd.ScoreMenos -> {
            val e = equipo(cmd.team)
            reemplazar(cmd.team, e.copy(score = maxOf(0, e.score - 1)))
        }
        is Cmd.FoulMas -> {
            val e = equipo(cmd.team)
            if (e.fouls + 1 == cfg.foulLimit) ev.add(Evento.Bonus(cmd.team))
            reemplazar(cmd.team, e.copy(fouls = e.fouls + 1))
        }
        is Cmd.FoulMenos -> {
            val e = equipo(cmd.team)
            reemplazar(cmd.team, e.copy(fouls = maxOf(0, e.fouls - 1)))
        }
        is Cmd.Clock -> when (cmd.op2) {
            ClockOp.START -> estado.copy(running = true)
            ClockOp.PAUSE -> estado.copy(running = false)
            ClockOp.RESET -> {
                val shot = if (cfg.rule24_14) 24 else 0
                estado.copy(running = false, clockSec = cfg.periodMinutes * 60, shotClockSec = shot)
            }
        }
        is Cmd.Shot -> {
            if (!cfg.rule24_14) estado // regla desactivada → sin shot clock
            else when (cmd.op2) {
                ShotOp.RESET24 -> estado.copy(shotClockSec = 24)
                ShotOp.RESET14 -> estado.copy(shotClockSec = 14)
                ShotOp.INC -> estado.copy(shotClockSec = minOf(24, estado.shotClockSec + 1))
                ShotOp.DEC -> estado.copy(shotClockSec = maxOf(0, estado.shotClockSec - 1))
            }
        }
        is Cmd.Period -> {
            fun ir(p: Int) = estado.copy(
                period = p.coerceIn(1, cfg.periods),
                clockSec = cfg.periodMinutes * 60,
                shotClockSec = if (cfg.rule24_14) 24 else 0,
                home = Equipo(fouls = 0),
                visitor = Equipo(fouls = 0),
                running = false,
            )
            when (cmd.op2) {
                PeriodOp.NEXT -> if (estado.period < cfg.periods) ir(estado.period + 1)
                else { ev.add(Evento.Buzzer); estado } // ya era el último
                PeriodOp.PREV -> ir(estado.period - 1)
                PeriodOp.SET -> ir(cmd.value ?: 1)
            }
        }
        Cmd.Buzzer -> { ev.add(Evento.Buzzer); estado }
        Cmd.ResetEstado -> estadoInicial(cfg)
    }
    return Resultado(nueva, ev)
}

// Tick de reloj (1 s) — solo si running. El shot clock auto-decrementa con la regla 24/14.
fun tick(cfg: Config, estado: Estado): Resultado {
    if (!estado.running) return Resultado(estado, emptyList())
    val ev = mutableListOf<Evento>()
    var clock = estado.clockSec
    var shot = estado.shotClockSec
    var running = true
    if (clock > 0) clock -= 1
    if (cfg.rule24_14 && shot > 0) shot -= 1
    if (clock == 0) { running = false; ev.add(Evento.Buzzer) }
    // el shot-expired suena una sola vez, al pasar de >0 a 0
    if (cfg.rule24_14 && shot == 0 && estado.shotClockSec > 0) ev.add(Evento.ShotExpired)
    return Resultado(
        estado.copy(clockSec = clock, shotClockSec = shot, running = running),
        ev,
    )
}

// Aplica un setup (diálogo) — devuelve config y estado reconciliados.
fun aplicarSetup(cfgPrev: Config, estadoPrev: Estado, nuevo: Config): Pair<Config, Estado> {
    val s = estadoPrev.copy(clockSec = minOf(estadoPrev.clockSec, nuevo.periodMinutes * 60))
    val conShot = when {
        !nuevo.rule24_14 -> s.copy(shotClockSec = 0)
        s.shotClockSec == 0 -> s.copy(shotClockSec = 24)
        else -> s
    }
    return nuevo to conShot
}
