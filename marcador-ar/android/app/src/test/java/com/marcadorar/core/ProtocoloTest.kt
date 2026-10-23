package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloTest {

    // ---- JSON ----

    @Test fun `json parsea objeto anidado con tipos correctos`() {
        val j = parsearJson("""{"a":1,"b":"x","c":true,"d":[1,2],"e":{"f":null}}""")
        val o = j.asObj
        assertEquals(1, o.int("a"))
        assertEquals("x", o.str("b"))
        assertEquals(true, o.bool("c"))
        assertEquals(2, o.campo("d").asArr?.items?.size)
        assertTrue(o.campo("e").asObj.campo("f") is Json.Null)
    }

    @Test fun `json parser rechaza texto invalido`() {
        assertNull(parsearJson(""))
        assertNull(parsearJson("{"))
        assertNull(parsearJson("{\"a\":}"))
        assertNull(parsearJson("no-json"))
        assertNull(parsearJson("{\"a\":1} extranjero"))
    }

    @Test fun `json decodifica los escapes estandar incluido formfeed`() {
        // \f debe decodificarse a U+000C (no a la letra 'f' literal)
        assertEquals("\u000C", parsearJson("\"\\f\"").asStr)
        assertEquals("a\b", parsearJson("\"a\\b\"").asStr)
        assertEquals("A", parsearJson("\"\\u0041\"").asStr)
    }

    @Test fun `json escapa caracteres de control y comillas`() {
        val txt = Json.str("a\"b\\c\nd").escribir()
        assertEquals("\"a\\\"b\\\\c\\nd\"", txt)
        // re-parseo idéntico
        assertEquals("a\"b\\c\nd", parsearJson(txt).asStr)
    }

    @Test fun `json escribe enteros sin decimales`() {
        assertEquals("24", Json.num(24).escribir())
        assertEquals("{\"a\":1}", Json.obj("a" to Json.num(1)).escribir())
    }

    // ---- comandos: nucleo -> wire (formato exacto que espera el server Node) ----

    @Test fun `score+ home serializa exacto`() {
        val w = Cmd.ScoreMas(Team.HOME, delta = 3).aWire()
        assertEquals("score+", w.str("op"))
        assertEquals("home", w.str("team"))
        assertEquals(3, w.int("delta"))
    }

    @Test fun `clock usa op2 no op (fuente estadojs)`() {
        val w = Cmd.Clock(ClockOp.START).aWire()
        assertEquals("clock", w.str("op"))
        assertEquals("start", w.str("op2"))
    }

    @Test fun `shot reset14 serializa op2`() {
        val w = Cmd.Shot(ShotOp.RESET14).aWire()
        assertEquals("shot", w.str("op"))
        assertEquals("reset14", w.str("op2"))
    }

    @Test fun `period next con value incluye value, sin value lo omite`() {
        val next = Cmd.Period(PeriodOp.NEXT).aWire()
        assertEquals("next", next.str("op2"))
        assertNull(next.campo("value"))
        val set = Cmd.Period(PeriodOp.SET, value = 3).aWire()
        assertEquals("set", set.str("op2"))
        assertEquals(3, set.int("value"))
    }

    @Test fun `reset viaja como state reset`() {
        val w = Cmd.ResetEstado.aWire()
        assertEquals("state", w.str("op"))
        assertEquals("reset", w.str("op2"))
    }

    @Test fun `mensaje cmd empaceta tipo y cmd`() {
        val m = Cmd.FoulMas(Team.VISITOR).aMensajeWire().asObj
        assertEquals("cmd", m.str("tipo"))
        assertEquals("foul+", m.campo("cmd").asObj.str("op"))
        assertEquals("visitor", m.campo("cmd").asObj.str("team"))
    }

    // ---- comandos: wire -> nucleo ----

    @Test fun `cmdDesdeWire reconstruye todos los ops`() {
        assertEquals(Cmd.ScoreMas(Team.HOME, 2),
            cmdDesdeWire(parsearJson("""{"op":"score+","team":"home","delta":2}""").asObj))
        assertEquals(Cmd.ScoreMenos(Team.VISITOR),
            cmdDesdeWire(parsearJson("""{"op":"score-","team":"visitor"}""").asObj))
        assertEquals(Cmd.FoulMas(Team.HOME),
            cmdDesdeWire(parsearJson("""{"op":"foul+","team":"home"}""").asObj))
        assertEquals(Cmd.Clock(ClockOp.PAUSE),
            cmdDesdeWire(parsearJson("""{"op":"clock","op2":"pause"}""").asObj))
        assertEquals(Cmd.Shot(ShotOp.RESET24),
            cmdDesdeWire(parsearJson("""{"op":"shot","op2":"reset24"}""").asObj))
        assertEquals(Cmd.Period(PeriodOp.NEXT),
            cmdDesdeWire(parsearJson("""{"op":"period","op2":"next"}""").asObj))
        assertEquals(Cmd.Period(PeriodOp.SET, 2),
            cmdDesdeWire(parsearJson("""{"op":"period","op2":"set","value":2}""").asObj))
        assertEquals(Cmd.Buzzer,
            cmdDesdeWire(parsearJson("""{"op":"buzzer"}""").asObj))
        assertEquals(Cmd.ResetEstado,
            cmdDesdeWire(parsearJson("""{"op":"state","op2":"reset"}""").asObj))
    }

    @Test fun `cmdDesdeWire devuelve null en op desconocido`() {
        assertNull(cmdDesdeWire(parsearJson("""{"op":"nada"}""").asObj))
        assertNull(cmdDesdeWire(null))
    }

    // ---- estado: round-trip ----

    @Test fun `estado round-trip preserva todos los campos`() {
        val e = Estado(
            clockSec = 372, shotClockSec = 14, period = 3, running = true,
            possession = Team.VISITOR, home = Equipo(21, 4), visitor = Equipo(18, 2),
        )
        assertEquals(e, estadoDesdeWire(e.aWire()))
    }

    @Test fun `estadoDesdeWire parsea el formato del server`() {
        val wire = """{"clockSec":600,"shotClockSec":24,"period":1,"running":false,
            "possession":"home","home":{"score":0,"fouls":0},"visitor":{"score":0,"fouls":0}}"""
        val e = estadoDesdeWire(parsearJson(wire).asObj)!!
        assertEquals(600, e.clockSec)
        assertEquals(Team.HOME, e.possession)
        assertEquals(Equipo(0, 0), e.home)
    }

    @Test fun `estadoDesdeWire devuelve null si falta campo obligatorio`() {
        assertNull(estadoDesdeWire(parsearJson("""{"period":1}""").asObj))
        assertNull(estadoDesdeWire(null))
    }

    // ---- config ----

    @Test fun `config round-trip y merge tolerante`() {
        val c = Config(periodMinutes = 8, periods = 2, foulLimit = 3,
            rule24_14 = false, homeName = "TIGRES", visitorName = "AGUILAS")
        assertEquals(c, configDesdeWire(Config(), c.aWire()))
        // merge: solo homeName en el wire, el resto se conserva
        val previa = Config(periodMinutes = 12, homeName = "VIEJO")
        val merged = configDesdeWire(previa, parsearJson("""{"homeName":"NUEVO"}""").asObj)
        assertEquals("NUEVO", merged.homeName)
        assertEquals(12, merged.periodMinutes)
    }

    // ---- entrantes tipados ----

    @Test fun `welcome tipa rol config y estado`() {
        val txt = """{"tipo":"welcome","rol":"admin",
            "config":{"periodMinutes":10,"periods":4,"foulLimit":5,"rule24_14":true,
                      "homeName":"A","visitorName":"B"},
            "state":{"clockSec":500,"shotClockSec":20,"period":2,"running":true,
                     "possession":"visitor","home":{"score":9,"fouls":1},
                     "visitor":{"score":7,"fouls":0}}}"""
        val e = entradaDesdeWire(txt) as Entrada.Welcome
        assertEquals("admin", e.rol)
        assertEquals(10, e.config.periodMinutes)
        assertEquals(500, e.estado.clockSec)
        assertEquals(Team.VISITOR, e.estado.possession)
        assertTrue(e.estado.running)
    }

    @Test fun `state config role error se tipan`() {
        val st = entradaDesdeWire(
            """{"tipo":"state","state":{"clockSec":1,"shotClockSec":2,"period":1,
                "running":false,"possession":"home","home":{"score":0,"fouls":0},
                "visitor":{"score":0,"fouls":0}}}"""
        ) as Entrada.EstadoMsg
        assertEquals(1, st.estado.clockSec)

        val cfg = entradaDesdeWire(
            """{"tipo":"config","config":{"periodMinutes":7}}""") as Entrada.ConfigMsg
        assertEquals(7, cfg.config.periodMinutes)

        val role = entradaDesdeWire("""{"tipo":"role","granted":false,"motivo":"timeout"}""")
        assertEquals(Entrada.Role(false, "timeout"), role)

        val err = entradaDesdeWire("""{"tipo":"error","code":"rol","message":"x"}""")
        assertEquals(Entrada.Error("rol", "x"), err)
    }

    @Test fun `buzzer shot-expired presence se tipan`() {
        assertEquals(Entrada.Buzzer, entradaDesdeWire("""{"tipo":"buzzer"}"""))
        assertEquals(Entrada.ShotExpired, entradaDesdeWire("""{"tipo":"shot-expired"}"""))
        assertEquals(Entrada.Presencia(true, 3),
            entradaDesdeWire("""{"tipo":"presence","adminOnline":true,"espectadores":3}"""))
    }

    @Test fun `json invalido o tipo desconocido no traba`() {
        assertNull(entradaDesdeWire("no json"))
        assertEquals(Entrada.Desconocido("otro"),
            entradaDesdeWire("""{"tipo":"otro"}"""))
    }

    // ---- mensajes de sesion ----

    @Test fun `join y setup llevan los campos esperados`() {
        val j = mensajeJoin("principal", "dev-1").asObj
        assertEquals("join", j.str("tipo"))
        assertEquals("principal", j.str("instancia"))
        assertEquals("dev-1", j.str("deviceId"))

        val s = mensajeSetup(Config(homeName = "X")).asObj
        assertEquals("cmd", s.str("tipo"))
        val cmd = s.campo("cmd").asObj
        assertEquals("setup", cmd.str("op"))
        assertEquals("X", cmd.campo("config").asObj.str("homeName"))
    }

    // ---- colocación AR ----

    @Test fun `colocacion wire ida y vuelta preserva campos`() {
        val col = Colocacion(ancho = 8f, alto = 5f, x = 1.5f, y = 3f, z = -2f, rot = 45f, inc = 12f)
        val vuelta = colocacionDesdeWire(Colocacion(), col.aWire())
        assertEquals(col, vuelta)
    }

    @Test fun `colocacion merge parcial conserva lo ausente`() {
        val previa = Colocacion(ancho = 8f, y = 3f, rot = 45f, inc = 10f)
        val merged = colocacionDesdeWire(previa, Json.obj("y" to Json.num(5)))
        assertEquals(8f, merged.ancho, 0f)
        assertEquals(5f, merged.y, 0f)
        assertEquals(45f, merged.rot, 0f)
        assertEquals(10f, merged.inc, 0f)   // la inclinación ausente se conserva
        assertNull(merged.geo)
    }

    @Test fun `colocacion geo se preserva y se borra con null explicito`() {
        val previa = Colocacion(geo = GeoColocacion(19.4, -99.1, 2240.0, 90f))
        // conserva si geo ausente
        assertEquals(previa.geo, colocacionDesdeWire(previa, Json.obj("y" to Json.num(4))).geo)
        // nueva geo desde objeto
        val nueva = colocacionDesdeWire(previa,
            Json.obj("geo" to Json.obj("lat" to Json.num(20.0), "lon" to Json.num(-99.2),
                "alt" to Json.num(2250.0), "rot" to Json.num(10))))
        assertEquals(20.0, nueva.geo!!.lat, 0.0)
        assertEquals(10f, nueva.geo!!.rot, 0f)
        // borra con null explícito
        assertNull(colocacionDesdeWire(previa, Json.obj("geo" to Json.Null)).geo)
        // geo incompleto no corrompe: se ignora entero
        assertEquals(previa.geo, colocacionDesdeWire(previa,
            Json.obj("geo" to Json.obj("lat" to Json.num(1.0)))).geo)
    }

    @Test fun `mensaje place omite geo o la borra`() {
        val col = Colocacion(ancho = 7f, y = 4f, geo = null)
        val cmd = mensajePlace(col).asObj!!.campo("cmd").asObj
        assertEquals("place", cmd.str("op"))
        assertNull(cmd.campo("geo"))          // sin geo → el campo ni aparece
        val cmd2 = mensajePlace(col, borrarGeo = true).asObj!!.campo("cmd").asObj
        assertEquals(Json.Null, cmd2.campo("geo"))
    }

    @Test fun `mensaje geo reporta la pose WGS84 del ancla`() {
        val g = GeoColocacion(19.4326, -99.1332, 2240.5, 87.3f)
        val m = mensajeGeo(g).asObj
        assertEquals("geo", m.str("tipo"))
        val geo = m.campo("geo").asObj
        assertEquals(19.4326, geo.numD("lat")!!, 1e-9)
        assertEquals(-99.1332, geo.numD("lon")!!, 1e-9)
        assertEquals(2240.5, geo.numD("alt")!!, 1e-9)
        assertEquals(87.3f, geo.numF("rot")!!, 0f)
    }

    @Test fun `welcome y mensaje colocacion se tipan`() {
        val w = entradaDesdeWire(
            """{"tipo":"welcome","rol":"espectador","config":{},"state":null,
                "colocacion":{"ancho":6,"x":0,"y":2.5,"z":0,"rot":0,"geo":null}}""")
                as Entrada.Welcome
        assertEquals(6f, w.colocacion.ancho, 0f)
        assertEquals(2.5f, w.colocacion.y, 0f)

        val c = entradaDesdeWire(
            """{"tipo":"colocacion","colocacion":{"ancho":9}}""") as Entrada.ColocacionMsg
        assertEquals(9f, c.colocacion.ancho, 0f)
        assertEquals(11f, c.colocacion.y, 0f)   // default conservado (altura real de estadio)
    }
}
