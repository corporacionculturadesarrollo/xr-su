package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartidoTest {

    @Test fun `estado inicial refleja la config`() {
        val cfg = Config(periodMinutes = 10)
        val s = estadoInicial(cfg)
        assertEquals(600, s.clockSec)
        assertEquals(24, s.shotClockSec)
        assertEquals(1, s.period)
        assertFalse(s.running)
        assertEquals(Equipo(0, 0), s.home)
    }

    @Test fun `score+ suma, cambia posesión y resetea shot a 24`() {
        val cfg = Config()
        val s0 = estadoInicial(cfg).copy(shotClockSec = 12)
        val r = aplicar(cfg, s0, Cmd.ScoreMas(Team.HOME, delta = 2))
        assertEquals(2, r.estado.home.score)
        assertEquals(Team.VISITOR, r.estado.possession)
        assertEquals(24, r.estado.shotClockSec)
    }

    @Test fun `score- nunca baja de 0`() {
        val cfg = Config()
        val r = aplicar(cfg, estadoInicial(cfg), Cmd.ScoreMenos(Team.HOME))
        assertEquals(0, r.estado.home.score)
    }

    @Test fun `foul+ dispara bonus exacto al límite`() {
        val cfg = Config(foulLimit = 5)
        var s = estadoInicial(cfg)
        var ev: List<Evento> = emptyList()
        repeat(5) { val r = aplicar(cfg, s, Cmd.FoulMas(Team.HOME)); s = r.estado; ev = r.eventos }
        assertTrue("debería emitir bonus al 5º", ev.contains(Evento.Bonus(Team.HOME)))
        // 6ª falta no repite bonus
        val r6 = aplicar(cfg, s, Cmd.FoulMas(Team.HOME))
        assertFalse(r6.eventos.contains(Evento.Bonus(Team.HOME)))
    }

    @Test fun `shot reset24 inc dec respeta limites`() {
        val cfg = Config()
        var s = estadoInicial(cfg)
        s = aplicar(cfg, s, Cmd.Shot(ShotOp.RESET14)).estado
        assertEquals(14, s.shotClockSec)
        s = aplicar(cfg, s, Cmd.Shot(ShotOp.INC)).estado
        assertEquals(15, s.shotClockSec)
        s = aplicar(cfg, s.copy(shotClockSec = 24), Cmd.Shot(ShotOp.INC)).estado
        assertEquals(24, s.shotClockSec) // tope 24
        s = aplicar(cfg, s, Cmd.Shot(ShotOp.DEC)).estado
        assertEquals(23, s.shotClockSec)
    }

    @Test fun `shot clock inactivo con rule24_14=false`() {
        val cfg = Config(rule24_14 = false)
        val s = estadoInicial(cfg)
        assertEquals(0, s.shotClockSec)
        assertEquals(0, aplicar(cfg, s, Cmd.Shot(ShotOp.RESET24)).estado.shotClockSec)
    }

    @Test fun `tick decrementa solo si running y buzea al llegar a cero`() {
        val cfg = Config()
        val s0 = estadoInicial(cfg)
        assertEquals(600, tick(cfg, s0).estado.clockSec) // pausado no avanza
        val s1 = s0.copy(running = true, clockSec = 1)
        val r = tick(cfg, s1)
        assertEquals(0, r.estado.clockSec)
        assertFalse(r.estado.running)
        assertTrue(r.eventos.contains(Evento.Buzzer))
    }

    @Test fun `tick emite shot-expired una sola vez al llegar a 0`() {
        val cfg = Config()
        val s0 = estadoInicial(cfg).copy(running = true, clockSec = 30, shotClockSec = 1)
        val r1 = tick(cfg, s0)
        assertEquals(0, r1.estado.shotClockSec)
        assertTrue(r1.eventos.contains(Evento.ShotExpired))
        val r2 = tick(cfg, r1.estado) // ya en 0: no repite
        assertFalse(r2.eventos.contains(Evento.ShotExpired))
    }

    @Test fun `period next limpia faltas y relojes y en el ultimo suena buzzer`() {
        val cfg = Config(periods = 4)
        val s0 = estadoInicial(cfg).copy(
            home = Equipo(fouls = 3), visitor = Equipo(fouls = 2), running = true,
        )
        val r = aplicar(cfg, s0, Cmd.Period(PeriodOp.NEXT))
        assertEquals(2, r.estado.period)
        assertEquals(0, r.estado.home.fouls)
        assertEquals(600, r.estado.clockSec)
        assertFalse(r.estado.running)
        // ir al 4º e intentar pasar del último
        val en4 = r.estado.copy(period = 4)
        val r4 = aplicar(cfg, en4, Cmd.Period(PeriodOp.NEXT))
        assertEquals(4, r4.estado.period) // no pasa del último
        assertTrue(r4.eventos.contains(Evento.Buzzer))
    }

    @Test fun `setup reconcilia config sin romper el estado`() {
        val cfg = Config()
        val s = estadoInicial(cfg).copy(clockSec = 720) // más que los nuevos 8 min
        val nueva = Config(periodMinutes = 8, homeName = "TIGRES")
        val (c, estado) = aplicarSetup(cfg, s, nueva)
        assertEquals(8, c.periodMinutes)
        assertEquals("TIGRES", c.homeName)
        assertTrue(estado.clockSec <= 8 * 60) // reloj acotado al nuevo tope
    }

    @Test fun `reset de estado vuelve al inicio conservando config`() {
        val cfg = Config(homeName = "TIGRES")
        val s = estadoInicial(cfg).copy(home = Equipo(55, 3), period = 3)
        val r = aplicar(cfg, s, Cmd.ResetEstado)
        assertEquals(0, r.estado.home.score)
        assertEquals(1, r.estado.period)
        assertEquals("TIGRES", cfg.homeName) // config intacta
    }
}
