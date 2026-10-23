package com.marcadorar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolesTest {

    @Test fun `claim atómico — el primero gana, el segundo denegado`() {
        val r = Roles()
        assertTrue(r.claim("A").ok)
        val seg = r.claim("B")
        assertFalse(seg.ok)
        assertEquals("ocupado", seg.motivo)
        assertEquals("A", r.admin)
    }

    @Test fun `reconexión del propio admin recupera el rol`() {
        val r = Roles()
        r.claim("A")
        val rec = r.claim("A")
        assertTrue(rec.ok)
        assertEquals("reconectado", rec.motivo)
    }

    @Test fun `resign solo lo hace el admin y libera el slot`() {
        val r = Roles()
        r.claim("A")
        assertFalse(r.resign("B").ok) // B no es admin
        assertTrue(r.resign("A").ok)
        assertNull(r.admin)
        assertTrue(r.claim("B").ok) // tras resignar, B puede reclamar
    }

    @Test fun `heartbeat timeout libera el rol solo`() {
        var t = 0L
        val r = Roles(timeoutMs = 10_000, ahora = { t })
        r.claim("A")
        t += 3000
        assertTrue(r.heartbeat("A"))
        t += 10_001 // > 10 s desde el último latido (t=3000)
        assertFalse(r.adminOnline()) // rol liberado por timeout
        assertTrue(r.claim("B").ok) // B puede reclamar tras timeout
    }

    @Test fun `tras timeout el admin viejo ya no escribe`() {
        var t = 0L
        val r = Roles(timeoutMs = 10_000, ahora = { t })
        r.claim("A")
        t += 11_000
        assertFalse(r.esAdmin("A"))
    }

    @Test fun `flujo combinado — claim, gobernar, resign, sucesor`() {
        val r = Roles()
        assertTrue(r.claim("marcador-1").ok)
        assertTrue(r.esAdmin("marcador-1"))
        assertFalse(r.esAdmin("espectador-9"))
        assertTrue(r.resign("marcador-1").ok)
        assertTrue(r.claim("marcador-2").ok) // sucesor toma el rol
        assertTrue(r.esAdmin("marcador-2"))
    }
}
