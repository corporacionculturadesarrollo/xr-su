package com.marcadorar.app

import android.content.Context
import com.marcadorar.core.Colocacion
import com.marcadorar.core.colocacionDesdeWire
import com.marcadorar.core.parsearJson
import com.marcadorar.core.asObj
import com.marcadorar.core.aWire
import com.marcadorar.core.escribir
import java.io.File

/**
 * Persistencia de la colocación AR en disco — la única fuente de verdad de la
 * posición fijada del tablero. Se guarda como JSON en el filesDir de la app
 * (no en SharedPreferences: el JSON es legible y testeable).
 *
 * Guarda la colocación COMPLETA (incluida la geo si está fijada) cada vez que
 * el admin la cambia, y se carga al arrancar para que el servidor la arrastre
 * en el welcome de cada espectador. Así el tablero permanece en su sitio
 * (coordenadas geográficas) aunque la app se reinicie.
 */
object PersistenciaColocacion {

    private const val ARCHIVO = "colocacion-ar.json"

    /** Carga la colocación guardada (o la por defecto si no hay o está corrupta). */
    fun cargar(ctx: Context): Colocacion {
        return try {
            val f = File(ctx.filesDir, ARCHIVO)
            if (!f.exists()) return Colocacion()
            val o = parsearJson(f.readText())?.asObj ?: return Colocacion()
            colocacionDesdeWire(Colocacion(), o)
        } catch (e: Exception) {
            Colocacion()   // corrupta → defaults; nunca tumbar el arranque
        }
    }

    /** Guarda la colocación actual (sobrescribe). */
    fun guardar(ctx: Context, col: Colocacion) {
        try {
            File(ctx.filesDir, ARCHIVO).writeText(col.aWire().escribir())
        } catch (_: Exception) {
            // el disco puede fallar; la colocación sigue viva en memoria del servidor
        }
    }
}
