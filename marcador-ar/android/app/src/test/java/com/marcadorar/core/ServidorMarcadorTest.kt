package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServidorMarcadorTest {

    private lateinit var temp: TemporizadorVirtual
    private lateinit var srv: ServidorMarcador
    private val salidas = mutableListOf<Pair<String, String>>()  // clienteId -> texto

    @Before fun setUp() {
        temp = TemporizadorVirtual()
        srv = ServidorMarcador(temp, instanciasValidas = { it == "principal" })
        srv.emitir = { cid, txt -> salidas.add(cid to txt) }
    }

    /** Último mensaje de `tipo` enviado a `cid` (o a cualquiera si cid es null). */
    private fun ultimo(tipo: String, cid: String? = null): Json.Obj? =
        salidas.lastOrNull { it.first == (cid ?: it.first) && parsearJson(it.second)?.asObj?.str("tipo") == tipo }
            ?.let { parsearJson(it.second).asObj }

    private fun join(cid: String, instancia: String = "principal", dev: String = "dev-$cid") {
        srv.conectar(cid)
        srv.recibir(cid, mensajeJoin(instancia, dev).escribir())
    }

    @Test fun `join a instancia valida recibe welcome con estado inicial`() {
        join("a")
        val w = ultimo("welcome", "a")!!
        assertEquals("espectador", w.str("rol"))
        assertEquals(600, w.campo("state").asObj.int("clockSec"))
        assertEquals(10, w.campo("config").asObj.int("periodMinutes"))
    }

    @Test fun `join a instancia no autorizada recibe error`() {
        join("a", instancia = "otra")
        assertEquals("instancia", ultimo("error", "a")!!.str("code"))
    }

    @Test fun `cmd sin claim recibe error rol`() {
        join("a", dev = "admin-1")
        srv.recibir("a", Cmd.ScoreMas(Team.HOME).aMensajeWire().escribir())
        assertEquals("rol", ultimo("error", "a")!!.str("code"))
    }

    @Test fun `claim concede rol y el cmd refleja en state broadcast`() {
        join("a", dev = "admin-1")
        srv.recibir("a", mensajeClaim().escribir())
        assertEquals(true, ultimo("role", "a")!!.bool("granted"))

        srv.recibir("a", Cmd.ScoreMas(Team.HOME, delta = 3).aMensajeWire().escribir())
        val st = ultimo("state", "a")!!.campo("state").asObj
        assertEquals(3, st.campo("home").asObj.int("score"))
        assertEquals("visitor", st.str("possession"))
    }

    @Test fun `segundo claim es ocupado`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        join("b", dev = "admin-2"); srv.recibir("b", mensajeClaim().escribir())
        val role = ultimo("role", "b")!!
        assertEquals(false, role.bool("granted"))
        assertEquals("ocupado", role.str("motivo"))
    }

    @Test fun `clock start arranca el reloj y los ticks bajan el estado`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        srv.recibir("a", Cmd.Clock(ClockOp.START).aMensajeWire().escribir())
        assertTrue(ultimo("state", "a")!!.campo("state").asObj.bool("running")!!)

        temp.avanzar(2000) // 2 ticks
        val st = ultimo("state", "a")!!.campo("state").asObj
        assertEquals(598, st.int("clockSec"))
        assertEquals(22, st.int("shotClockSec")) // 24 -> 22 con la regla 24/14
    }

    @Test fun `el estado se broadcastea a todos los clientes de la instancia`() {
        join("espectador", dev = "esp-1")
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        srv.recibir("a", Cmd.ScoreMas(Team.HOME).aMensajeWire().escribir())
        // el espectador también recibe el state
        val st = ultimo("state", "espectador")!!.campo("state").asObj
        assertEquals(1, st.campo("home").asObj.int("score"))
    }

    @Test fun `shot expired suena una sola vez al llegar a 0`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        // fuerza el shot clock a 1 vía setup con regla activa y shot bajo
        srv.recibir("a", Cmd.Shot(ShotOp.DEC).aMensajeWire().escribir()) // no toca el shot hasta 0
        // resetear shot a 24 y bajar de golpe: simpler -> reset14 y avanzar
        srv.recibir("a", Cmd.Shot(ShotOp.RESET14).aMensajeWire().escribir())
        srv.recibir("a", Cmd.Clock(ClockOp.START).aMensajeWire().escribir())
        temp.avanzar(14000) // 14 ticks -> shot llega a 0
        val expirados = salidas.count {
            it.first == "a" && parsearJson(it.second)?.asObj?.str("tipo") == "shot-expired"
        }
        assertEquals("shot-expired debe sonar una sola vez", 1, expirados)
    }

    @Test fun `buzzer suena al agotarse el reloj del periodo`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        // periodo de 10 min = 600s; aceleramos con tickMs=1 y avanzamos 600s
        srv.recibir("a", Cmd.Clock(ClockOp.START).aMensajeWire().escribir())
        temp.avanzar(600000) // 600 ticks
        val buzzers = salidas.count {
            it.first == "a" && parsearJson(it.second)?.asObj?.str("tipo") == "buzzer"
        }
        assertTrue("debería sonar el buzzer al llegar a 0", buzzers >= 1)
        assertFalse("el reloj debe pararse en 0",
            ultimo("state", "a")!!.campo("state").asObj.bool("running")!!)
    }

    @Test fun `heartbeat perdido por timeout degrada al admin`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        // sin latidos, superar el timeout (10s) libera el rol en el próximo sweep
        temp.avanzar(11000)
        srv.recibir("a", Cmd.ScoreMas(Team.HOME).aMensajeWire().escribir())
        assertEquals("rol", ultimo("error", "a")!!.str("code")) // ya no es admin
    }

    @Test fun `resign entrega el rol y permite a otro reclamarlo`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        srv.recibir("a", mensajeResign().escribir())
        assertEquals("entregado", ultimo("role", "a")!!.str("motivo"))
        join("b", dev = "admin-2"); srv.recibir("b", mensajeClaim().escribir())
        assertEquals(true, ultimo("role", "b")!!.bool("granted"))
    }

    @Test fun `setup cambia config y retransmite`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        srv.recibir("a", mensajeSetup(Config(homeName = "TIGRES", periodMinutes = 8)).escribir())
        val cfg = ultimo("config", "a")!!.campo("config").asObj
        assertEquals("TIGRES", cfg.str("homeName"))
        assertEquals(8, cfg.int("periodMinutes"))
        // el estado reconcilia el reloj al nuevo tope de período
        assertEquals(480, ultimo("state", "a")!!.campo("state").asObj.int("clockSec"))
    }

    @Test fun `json invalido responde error json`() {
        join("a")
        srv.recibir("a", "no json")
        assertEquals("json", ultimo("error", "a")!!.str("code"))
    }

    // ---- colocación AR ----

    @Test fun `place solo el admin lo aplica y se broadcastea`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        join("b", dev = "esp-1")                       // espectador conectado
        srv.recibir("a", mensajePlace(Colocacion(ancho = 7f, y = 4f)).escribir())
        // ambos clientes reciben la colocación
        assertEquals(7f, ultimo("colocacion", "a")!!.campo("colocacion").asObj.numF("ancho")!!, 0f)
        assertEquals(4f, ultimo("colocacion", "b")!!.campo("colocacion").asObj.numF("y")!!, 0f)
    }

    @Test fun `place de espectador recibe error rol`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        join("b", dev = "esp-1")
        srv.recibir("b", mensajePlace(Colocacion(ancho = 3f)).escribir())
        assertEquals("rol", ultimo("error", "b")!!.str("code"))
    }

    @Test fun `place mergea parcialmente y el welcome arrastra la colocacion`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        srv.recibir("a", mensajePlace(Colocacion(ancho = 6f, y = 3f)).escribir())
        srv.recibir("a", """{"tipo":"cmd","cmd":{"op":"place","y":5}}""")   // ajuste parcial
        assertEquals(6f, ultimo("colocacion", "a")!!.campo("colocacion").asObj.numF("ancho")!!, 0f)
        assertEquals(5f, ultimo("colocacion", "a")!!.campo("colocacion").asObj.numF("y")!!, 0f)
        // un espectador tardío lo recibe en el welcome
        join("c", dev = "esp-2")
        val col = ultimo("welcome", "c")!!.campo("colocacion").asObj
        assertEquals(6f, col.numF("ancho")!!, 0f)
        assertEquals(5f, col.numF("y")!!, 0f)
    }

    @Test fun `place con geo la almacena y borrarGeo la limpia`() {
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        val fijada = Colocacion(geo = GeoColocacion(19.4326, -99.1332, 2240.0, 180f))
        srv.recibir("a", mensajePlace(fijada).escribir())
        val geo = ultimo("colocacion", "a")!!.campo("colocacion").asObj.campo("geo").asObj
        assertEquals(19.4326, geo.numD("lat")!!, 0.0001)
        assertEquals(180f, geo.numF("rot")!!, 0f)
        // un ajuste de botón NO clobbera la geo (se omite el campo)
        srv.recibir("a", """{"tipo":"cmd","cmd":{"op":"place","y":9}}""")
        assertNotNull(ultimo("colocacion", "a")!!.campo("colocacion").asObj.campo("geo").asObj)
        // borrarGeo sí la limpia (en el wire viaja como geo:null)
        srv.recibir("a", mensajePlace(Colocacion(), borrarGeo = true).escribir())
        val limpia = colocacionDesdeWire(Colocacion(),
            ultimo("colocacion", "a")!!.campo("colocacion").asObj)
        assertNull(limpia.geo)
    }

    @Test fun `fijar emite broadcast fijar y el hook avisa en cada cambio`() {
        var persistidas = 0
        var ultima: Colocacion? = null
        srv.alCambiarColocacion = { persistidas++; ultima = it }
        join("a", dev = "admin-1"); srv.recibir("a", mensajeClaim().escribir())
        join("b", dev = "esp-1")
        // geo:"fijar" → broadcast fijar a todos (sin cambiar la colocación)
        srv.recibir("a", """{"tipo":"cmd","cmd":{"op":"place","geo":"fijar"}}""")
        assertNotNull(ultimo("fijar", "a"))
        assertNotNull(ultimo("fijar", "b"))
        assertEquals(0, persistidas)               // fijar NO es un cambio
        // la vista AR devuelve la geo → sí cambia y dispara el hook
        srv.recibir("a", mensajePlace(
            Colocacion(geo = GeoColocacion(19.4, -99.1, 2240.0, 90f))).escribir())
        assertEquals(1, persistidas)
        assertEquals(19.4, ultima!!.geo!!.lat, 0.0001)
    }
}
