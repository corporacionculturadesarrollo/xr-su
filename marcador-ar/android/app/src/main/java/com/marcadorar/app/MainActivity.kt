package com.marcadorar.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.marcadorar.app.net.ServidorWebSocket

/**
 * Admin de instancia — el teléfono aloja el partido y lo alimenta.
 *
 * Arranca el servidor WebSocket embebido (los espectadores se conectan a él) y
 * carga `preview/marcador.html` en un WebView apuntando a ese servidor por
 * loopback. La página es ella misma el cliente admin: hace `join`/`claim`, manda
 * los comandos y dibuja el tablero LED. Así el admin controla sobre el MISMO
 * render que verá el espectador (garantía de "exactamente igual"), reutilizando
 * el diseño ya verificado píxel a píxel.
 */
class MainActivity : Activity() {

    private val puerto = 8899
    private val instancia = "principal"

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("SRV", "MainActivity onCreate (pid=${android.os.Process.myPid()})")

        // 1) servidor embebido: la instancia vive en este teléfono (0.0.0.0,
        //    así los espectadores se conectan a la IP del equipo; el admin usa loopback).
        //    Arranca con la colocación AR persistida (posición fijada del tablero)
        //    y la regraba cada vez que el admin la cambia — así sobrevive reinicios.
        val colocGuardada = PersistenciaColocacion.cargar(this)
        Log.i("SRV", "colocación cargada de disco: $colocGuardada")
        val srv = arrancarServidor(puerto, colocGuardada) ?: run {
            // puerto ocupado u otro fallo: aviso en pantalla en vez de colgar el hilo principal
            Log.e("SRV", "los 6 intentos de bind fallaron — servidor NO quedó escuchando")
            setContentView(mensaje("No se pudo abrir el servidor en el puerto $puerto."))
            return
        }
        Log.i("SRV", "servidor escuchando en $puerto (reutilizado=$reutilizado)")
        // Persistir la colocación cada vez que el admin la mueve/fija.
        srv.alCambiarColocacion = { col ->
            PersistenciaColocacion.guardar(this, col)
            Log.i("SRV", "colocación persistida: $col")
        }

        // 2) tablero verificado en WebView, en modo admin, contra el servidor embebido
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true          // localStorage guarda el deviceId
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW  // ws:// desde file://
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                    Log.i("TABLERO", msg.message() + " (" + msg.sourceId() + ":" + msg.lineNumber() + ")")
                    return true
                }
            }
            setBackgroundColor(Color.BLACK)
        }
        // Contenedor con el tablero + un botón flotante para abrir la vista AR del espectador.
        val contenedor = FrameLayout(this)
        contenedor.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val botonAr = Button(this).apply {
            text = "Ver en AR"
            setOnClickListener {
                // El deviceId admin (localStorage de la página) viaja por intent
                // para que la vista AR abra su cliente WS con el MISMO deviceId:
                // el servidor le devuelve rol admin (reconexión en gracia) y así
                // puede mandar place/geo desde el panel nativo y recibir broadcasts.
                web.evaluateJavascript(
                    "localStorage.getItem('marcador deviceId')"
                ) { v ->
                    val dev = v?.trim('"')
                        ?.takeIf { it.isNotBlank() && it != "null" } ?: ""
                    startActivity(
                        Intent(this@MainActivity, VistaAr::class.java).apply {
                            putExtra("server", "ws://127.0.0.1:$puerto")
                            putExtra("instancia", instancia)
                            putExtra("deviceId", dev)
                        }
                    )
                }
            }
        }
        contenedor.addView(
            botonAr,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                setMargins(24, 96, 24, 24)
            }
        )
        setContentView(contenedor)
        web.loadUrl(
            "file:///android_asset/preview/marcador.html" +
                "?server=ws://127.0.0.1:$puerto&instancia=$instancia"
        )
    }

    private fun mensaje(txt: String): TextView = TextView(this).apply {
        text = txt
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#101418"))
        setPadding(48, 96, 48, 48)
        textSize = 16f
    }

    override fun onDestroy() {
        Log.i("SRV", "MainActivity onDestroy")
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        // El servidor embebido NO se cierra aquí: es un singleton de proceso
        // (companion) y debe seguir sirviendo a los espectadores aunque la
        // activity se recree o pase a segundo plano. Se apaga solo cuando el
        // proceso muere (force-stop / kill del sistema).
        super.onDestroy()
    }

    companion object {
        /** El servidor embebido del proceso; vive mientras viva el proceso. */
        private var servidorPropio: ServidorWebSocket? = null
        /** true si el último arrancarServidor reutilizó el singleton existente. */
        private var reutilizado = false

        /**
         * Devuelve el servidor escuchando en [puerto], creándolo si hace falta.
         * Si el singleton del proceso ya escucha, lo reutiliza — así recrear la
         * activity no provoca "Address already in use".
         */
        @Synchronized
        private fun arrancarServidor(puerto: Int, coloc: com.marcadorar.core.Colocacion): ServidorWebSocket? {
            servidorPropio?.let { reutilizado = true; return it }
            reutilizado = false
            for (intento in 1..6) {
                val s = ServidorWebSocket(puerto, instanciasValidas = { true }, colocacionInicial = coloc)
                s.start()
                if (s.esperarListo(2)) {
                    servidorPropio = s
                    return s
                }
                Log.e("SRV", "bind falló (intento $intento/6)")
                try { s.cerrar() } catch (e: Exception) { Log.e("SRV", "cerrar tras fallo: $e") }
                Thread.sleep(1000)
            }
            return null
        }
    }
}
