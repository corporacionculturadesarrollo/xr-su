package com.marcadorar.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.marcadorar.app.net.FabricaOkHttp
import com.marcadorar.app.net.TemporizadorAndroid
import com.marcadorar.core.ClienteMarcador
import com.marcadorar.core.Colocacion
import com.marcadorar.core.GeoColocacion
import com.marcadorar.core.Supervisor
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.NodeScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberViewNodeManager
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Vista AR del espectador — el tablero LED flotando en 3D anclado a la cancha.
 *
 * Reutiliza EXACTAMENTE `preview/marcador.html` (la única fuente de verdad del
 * tablero) dentro de un WebView en modo `?vista=espectador`. El contenido llega
 * a la escena 3D como dos `ImageNode` (frente y respaldo) que comparten un
 * `Bitmap` refrescado cada [INTERVALO_REFRESCO_MS] ms; el WebView vive
 * OFFSCREEN en capa de software y su `draw()` se vuelca a ese bitmap.
 * `ViewNode` se descartó porque solo capturaba el fondo negro del WebView, no
 * su contenido vivo; `RGB_565` se descartó porque reventaba Filament (SIGABRT).
 *
 * Colocación admin-centrada: la vista mantiene su PROPIO cliente WebSocket
 * ([ClienteMarcador]+[Supervisor]) con el mismo deviceId que la página admin
 * del teléfono → el servidor le devuelve rol admin (reconexión en gracia por
 * deviceId), así puede mandar `place`/`geo` y recibe los broadcasts de
 * `colocacion`, aplicando tamaño/posición/giro a las caras 3D.
 *
 * Fijado geográfico (ARCore Geospatial): al pulsar "Fijar" (en el panel nativo
 * o en la página admin) se captura la pose WGS84 del ancla y se reporta al
 * servidor; al reiniciar, si la colocación trae `geo`, el ancla se recrea
 * directamente en esas coordenadas sin tocar la pantalla.
 *
 * Flujo manual (fallback): se apunta al suelo, aparece un retículo, y al tocar
 * se ancla el tablero.
 */
class VistaAr : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pantalla siempre encendida: si la activity se queda quieta mirando la
        // escena AR, Android la bloquea por timeout y no se puede probar nada
        // (giro, colocación, etc.).
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        // Geospatial necesita ubicación fina (pose WGS84 + heading). Solo se
        // pide si hay API key: sin ella el modo geospatial quedaría DISABLED y
        // el permiso no serviría de nada. Si se deniega, la app sigue
        // funcionando con colocación temporal vía tap.
        val geoActivo = BuildConfig.AR_GEOSPATIAL_KEY.isNotEmpty()
        if (geoActivo &&
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
        // La instancia, el servidor y el deviceId admin vienen por intent
        // (defaults = servidor embebido local).
        val server = intent.getStringExtra("server") ?: "ws://127.0.0.1:8899"
        val instancia = intent.getStringExtra("instancia") ?: "principal"
        val deviceIdAdmin = intent.getStringExtra("deviceId") ?: ""
        setContent {
            TableroAr(
                server = server,
                instancia = instancia,
                deviceIdAdmin = deviceIdAdmin,
                geoActivo = geoActivo,
            )
        }
    }
}

/** Refresco de la textura del tablero (el reloj tick cada 1 s; 2.5 fps basta). */
private const val INTERVALO_REFRESCO_MS = 400L

/** Tamaño del bitmap del tablero HTML (la fuente de verdad dibuja a 640×286 dp). */
private const val TABLERO_DP_ANCHO = 640f
private const val TABLERO_DP_ALTO = 286f

/**
 * Profundidad del paralelepípedo (box de 4 caras) como fracción del alto. Un
 * marcador real es un panel colgante poco profundo: con alto=6 m da ~1.8 m de
 * fondo, suficiente para leerse como volumen desde las esquinas de la cancha
 * sin que las caras laterales estorben.
 */
private const val PROF_LATERAL_RATIO = 0.3f

/**
 * LOD (Level of Detail) para legibilidad a distancia. El material del tablero
 * ya es unlit (no se oscurece con la luz de escena), así que a >30 m el
 * "oscurecimiento" del score es PÉRDIDA DE DENSIDAD DE PÍXELES de la pantalla:
 * los trazos finos del LED se difuminan con el fondo negro. Hasta ~30 m el
 * tablero se muestra a tamaño real (legible); pasado ese umbral crece levemente
 * para que los dígitos ocupen más píxeles y el marcador siga legible a 100-150 m
 * (regla de proporción de lectura de estadios: 2.54 cm de dígito por 7.6 m).
 */
private const val LOD_DISTANCIA_M = 30f    // umbral: por debajo, tamaño real
private const val LOD_FACTOR_MAX = 2.0f    // factor tope de escala (evita que "crezca" demasiado)

@Composable
fun TableroAr(
    server: String,
    instancia: String,
    deviceIdAdmin: String,
    geoActivo: Boolean,
) {
    val engine = rememberEngine()
    val windowManager = rememberViewNodeManager()
    val actividad = LocalContext.current as? Activity

    var reticleHit by remember { mutableStateOf<HitResult?>(null) }
    var ancla by remember { mutableStateOf<Anchor?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var colocacion by remember { mutableStateOf(Colocacion()) }
    var sesion by remember { mutableStateOf<Session?>(null) }
    var avisoGeo by remember { mutableStateOf<String?>(null) }
    var rol by remember { mutableStateOf("espectador") }
    var lodFactor by remember { mutableStateOf(1f) }
    var camPos by remember { mutableStateOf<FloatArray?>(null) }

    // ---- cliente WS nativo (mismo deviceId que la página admin del teléfono) ----
    val cliente = remember(server, instancia) {
        ClienteMarcador(
            instancia,
            deviceIdAdmin.ifEmpty { "ar-" + UUID.randomUUID().toString().take(8) }
        ).also { c ->
            c.onColocacion = { colocacion = it }
            c.onRol = { r, _ ->
                rol = r
                // claim con el MISMO deviceId solo puede dar "reconectado"
                // (o conceder si el rol está libre): seguro en el propio teléfono.
                if (r != "admin") c.claim()
            }
        }
    }
    val supervisor = remember(server, instancia) {
        Supervisor(FabricaOkHttp(server), cliente, TemporizadorAndroid())
    }
    DisposableEffect(supervisor) {
        supervisor.iniciar()
        onDispose { supervisor.detener() }
    }

    // ---- fijado geográfico: captura la pose WGS84 del ancla y la reporta ----
    val capturarGeo: () -> Unit = {
        val earth = sesion?.earth
        val a = ancla
        when {
            !geoActivo -> avisoGeo =
                "Sin API key de Geospatial: fijado geográfico desactivado (colocación temporal)"
            a == null -> avisoGeo = "Toca el suelo para colocar el tablero antes de fijar"
            earth == null || earth.trackingState != TrackingState.TRACKING ->
                avisoGeo = "Esperando la ubicación GPS (Geospatial)… prueba al aire libre"
            else -> {
                val gp = earth.getGeospatialPose(a.pose)
                if (gp == null) {
                    avisoGeo = "Sin fix GPS todavía… (¿permiso de ubicación?)"
                } else {
                    // Orientación en marco EUS (east-up-south, Y=arriba): el yaw
                    // 2·atan2(y,w) sobre el eje up es el heading desde el norte.
                    // getHeading() está deprecado y getGeospatialPose ya no lo
                    // rellena; se usa getEastUpSouthQuaternion (x,y,z,w).
                    val qe = gp.eastUpSouthQuaternion
                    val yaw = Math.toDegrees(2.0 * kotlin.math.atan2(qe[1].toDouble(), qe[3].toDouble()))
                        .toFloat()
                    cliente.enviarGeo(
                        GeoColocacion(gp.latitude, gp.longitude, gp.altitude, yaw)
                    )
                    avisoGeo = "📍 fijada: %.5f, %.5f".format(gp.latitude, gp.longitude)
                    android.util.Log.i(
                        "TABLERO_AR", "geo capturada: lat=${gp.latitude} lon=${gp.longitude} " +
                            "alt=${gp.altitude} yawEUS=$yaw"
                    )
                }
            }
        }
    }
    cliente.onFijar = capturarGeo

    // ---- restauración geográfica: si la colocación trae geo, recrea el ancla
    //      directamente en esas coordenadas WGS84 (sin tocar la pantalla) ----
    LaunchedEffect(colocacion.geo, sesion, geoActivo) {
        val g = colocacion.geo ?: return@LaunchedEffect
        if (ancla != null) return@LaunchedEffect
        if (!geoActivo) {
            // Sin key no hay Earth: no giramos sin fin, avisamos y dejamos el tap.
            avisoGeo = "Guardado con geo, pero sin API key solo se coloca tocando el suelo"
            return@LaunchedEffect
        }
        avisoGeo = "Restaurando posición fijada… (esperando GPS)"
        while (isActive && ancla == null) {
            val earth = sesion?.earth
            if (earth != null && earth.trackingState == TrackingState.TRACKING) {
                // Rotación EUS (east-up-south): yaw sobre el eje up (Y), el mismo
                // marco de getEastUpSouthQuaternion al capturar → ciclo exacto.
                val phi = g.rot * Math.PI / 180.0
                val q = floatArrayOf(0f, (sin(phi / 2)).toFloat(), 0f, (cos(phi / 2)).toFloat())
                ancla = earth.createAnchor(g.lat, g.lon, g.alt, q)
                avisoGeo = null
                android.util.Log.i("TABLERO_AR", "ancla restaurada desde geo: $g")
                break
            }
            delay(700)
        }
    }

    // ---- LOD: escala el tablero según distancia cámara-ancla (ver LOD_DISTANCIA_M).
    //      La posición de la cámara se captura en onFrame (hilo GL) y aquí solo
    //      se calcula el factor cada 300 ms para pasárselo a TableroArCara.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(300)
            val cam = camPos
            val anc = ancla?.pose?.translation
            if (cam != null && anc != null) {
                val dx = cam[0] - anc[0]; val dy = cam[1] - anc[1]; val dz = cam[2] - anc[2]
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                val f = if (d <= LOD_DISTANCIA_M) 1f
                        else (1f + (d - LOD_DISTANCIA_M) / 120f).coerceAtMost(LOD_FACTOR_MAX)
                if (f != lodFactor) lodFactor = f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
    ) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            viewNodeWindowManager = windowManager,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            planeRenderer = true,
            geospatialMode = if (geoActivo) {
                Config.GeospatialMode.ENABLED
            } else {
                // Sin API key el modo ENABLED revienta la sesión (falta la
                // librería de ubicación / autorización): mantenemos DISABLED
                // para una cámara limpia y colocación temporal vía tap.
                Config.GeospatialMode.DISABLED
            },
            onSessionCreated = { sesion = it },
            // Posición de la cámara (mundo ARCore) para el LOD de legibilidad.
            onSessionUpdated = { _, frame -> camPos = frame.camera.pose.translation },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { _, _ ->
                    // Coloca el tablero exactamente donde el usuario ve el retículo.
                    if (ancla == null) {
                        reticleHit?.createAnchor()?.let { ancla = it }
                    }
                }
            )
        ) {
            // Retículo de colocación (muestra un disco sobre la superficie detectada).
            PlacementReticle(
                xPx = viewSize.width / 2f,
                yPx = viewSize.height / 2f,
                onHitResultChanged = { reticleHit = it }
            )

            ancla?.let { a ->
                AnchorNode(anchor = a) {
                    TableroArCara(server = server, instancia = instancia, colocacion = colocacion, lodFactor = lodFactor)
                }
            }
        }

        // Volver al panel admin (alternar vista admin ↔ espectador).
        Button(
            onClick = { actividad?.finish() },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 12.dp)
        ) { Text("◀ Admin", fontSize = 13.sp) }

        if (ancla == null) {
            Text(
                text = if (reticleHit != null) {
                    "Toca para colocar el tablero"
                } else {
                    "Apunta al suelo para encontrar una superficie"
                },
                color = ComposeColor.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 128.dp, start = 12.dp, end = 12.dp)
            )
        }

        avisoGeo?.let {
            Text(
                text = it,
                color = ComposeColor(0xFFF0B400),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp, start = 24.dp, end = 24.dp)
            )
        }

        // Panel rápido nativo: mismos controles que el panel HTML admin
        // (fuente de verdad del protocolo), para ajustar sin salir de la vista AR.
        PanelColocacion(
            col = colocacion,
            esAdmin = rol == "admin",
            onCambio = { nueva -> cliente.enviarColocacion(nueva) },
            onFijar = capturarGeo,
            onLiberar = {
                cliente.enviarColocacion(colocacion.copy(geo = null), borrarGeo = true)
                avisoGeo = "Fijado geográfico liberado"
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/** Panel de ajuste rápido de la colocación (botones +/− con valores numéricos). */
@Composable
private fun PanelColocacion(
    col: Colocacion,
    esAdmin: Boolean,
    onCambio: (Colocacion) -> Unit,
    onFijar: () -> Unit,
    onLiberar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ComposeColor(0xDD101418))
            .padding(10.dp)
            .width(250.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "TABLERO AR — ajuste rápido",
            color = ComposeColor(0xFFF0B400), fontSize = 11.sp, fontWeight = FontWeight.Black
        )
        if (!esAdmin) {
            Text(
                "⚠ solo el admin posiciona (reclama el rol en el tablero)",
                color = ComposeColor(0xFFFFB4A8), fontSize = 10.sp
            )
        }
        FilaControl("Tamaño", col.ancho, " m", 0.5f) {
            onCambio(col.copy(ancho = it.coerceAtLeast(1f)))
        }
        FilaControl("Alto panel", col.alto, " m", 0.5f) {
            onCambio(col.copy(alto = it.coerceAtLeast(0.5f)))
        }
        FilaControl("Altura", col.y, " m", 0.25f) {
            onCambio(col.copy(y = it.coerceAtLeast(0f)))
        }
        FilaControl("Izq/Der", col.x, " m", 0.25f) { onCambio(col.copy(x = it)) }
        FilaControl("Atrás/Adel", col.z, " m", 0.25f) { onCambio(col.copy(z = it)) }
        FilaControl("Giro", col.rot, "°", 5f) {
            onCambio(col.copy(rot = ((it % 360f) + 360f) % 360f))
        }
        FilaControl("Incl", col.inc, "°", 5f) {
            onCambio(col.copy(inc = it.coerceIn(-80f, 80f)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onFijar,
                enabled = col.geo == null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(if (col.geo == null) "📍 Fijar en mapa" else "Fijada ✔", fontSize = 11.sp) }
            Button(
                onClick = onLiberar,
                enabled = col.geo != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("Liberar", fontSize = 11.sp) }
        }
        col.geo?.let { g ->
            Text(
                "lat %.5f · lon %.5f · %d m".format(g.lat, g.lon, g.alt.toInt()),
                color = ComposeColor(0xFF12D45E), fontSize = 9.sp
            )
        }
    }
}

/** Una fila del panel: [nombre] [−] valor [+] con paso fijo. */
@Composable
private fun FilaControl(
    nombre: String,
    valor: Float,
    unidad: String,
    paso: Float,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(nombre, color = ComposeColor(0xFFCFD3D8), fontSize = 10.sp, modifier = Modifier.width(62.dp))
        Button(
            onClick = { onChange(valor - paso) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(26.dp)
        ) { Text("−", fontSize = 13.sp) }
        Text(
            "%.2f".format(valor) + unidad,
            color = ComposeColor.White, fontSize = 11.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(58.dp)
        )
        Button(
            onClick = { onChange(valor + paso) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(26.dp)
        ) { Text("+", fontSize = 13.sp) }
    }
}

/**
 * La cara del tablero LED: un WebView offscreen vuelto a bitmap y pintado en
 * dos `ImageNode` (frente y respaldo), colocados según la [Colocacion] del
 * admin (posición relativa al ancla, giro y escala en metros).
 */
@Composable
fun NodeScope.TableroArCara(server: String, instancia: String, colocacion: Colocacion, lodFactor: Float) {
    val contexto = LocalContext.current
    val densidad = contexto.resources.displayMetrics.density
    val anchoPx = (TABLERO_DP_ANCHO * densidad).toInt()
    val altoPx = (TABLERO_DP_ALTO * densidad).toInt()

    // WebView offscreen en capa de software: su draw() se puede volcar a bitmap.
    val webView = remember {
        WebView(contexto).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true          // localStorage guarda el deviceId
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW  // ws:// desde file://
            webViewClient = WebViewClient()
            // Consola del tablero AR a logcat (TAG TABLERO_AR) para poder
            // ver su estado de conexión sin salir de la vista.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.i("TABLERO_AR", msg.message() + " (" + msg.lineNumber() + ")")
                    return true
                }
            }
            setBackgroundColor(android.graphics.Color.BLACK)
            // Capa de software: garantiza que draw() capture el contenido web.
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            // Un WebView sin attach a window necesita medida y layout manuales
            // para poder componer y ser dibujable.
            layoutParams = ViewGroup.LayoutParams(anchoPx, altoPx)
            measure(
                View.MeasureSpec.makeMeasureSpec(anchoPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(altoPx, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, anchoPx, altoPx)
            loadUrl(
                "file:///android_asset/preview/marcador.html" +
                    "?server=$server&instancia=$instancia&vista=espectador"
            )
            // El renderer de un WebView offscreen puede suspenderse con el tiempo
            // (la textura "se congelaba"): reanudar timers y renderer explícitamente.
            resumeTimers()
            onResume()
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    val bitmap = remember {
        Bitmap.createBitmap(anchoPx, altoPx, Bitmap.Config.ARGB_8888).apply {
            // Opacidad desde el primer frame: sin este erase el bitmap recién
            // creado está lleno de píxeles transparentes (alpha=0) y la textura
            // se sube translúcida.
            eraseColor(android.graphics.Color.BLACK)
        }
    }

    // Casing del chasis: planos lisos oscuros (#101418) para las 6 caras del
    // paralelepípedo. El chasis opaco da presencia física y "bloquea el ruido
    // del fondo real" (como en un scoreboard broadcast).
    val bitmapLateral = remember {
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(0x10, 0x14, 0x18))
        }
    }

    // Marcador = CHASIS sólido + DECAL LED. La RAÍZ es un Node PLANO (sin
    // renderizable) que solo transporta la pose del conjunto (posición relativa
    // a la ancla, inclinación eje X + giro eje Y, y escala LOD) — así no pinta
    // nada dentro de la caja. Las 8 caras son HIJAS con rotación LOCAL: SceneView
    // compone la orientación con cuaterniones (correcto cuando giro+inclinación
    // se combinan).
    //  • Chasis (6 caras oscuras, ancho×alto×prof): el cuerpo físico opaco.
    //  • Decal LED (frente/respaldo): el marcador al aspecto NATIVO del contenido
    //    (2.24:1, sin distorsión), separado ~0.02 del chasis para evitar
    //    Z-fighting (el "parpadeo" de texturas coplanares).
    val boxRoot = remember { Node(engine) }
    val chasisFrente = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmapLateral) }
    val chasisAtras = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmapLateral) }
    val chasisDer = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmapLateral) }
    val chasisIzq = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmapLateral) }
    val chasisArriba = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmapLateral) }
    val chasisAbajo = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmapLateral) }
    val decalFrente = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmap) }
    val decalAtras = remember { ImageNode(materialLoader = materialLoader, bitmap = bitmap) }
    val nodos = listOf(
        chasisFrente, chasisAtras, chasisDer, chasisIzq, chasisArriba, chasisAbajo,
        decalFrente, decalAtras
    )

    DisposableEffect(boxRoot) {
        parentNode.addChildNode(boxRoot)
        nodos.forEach { boxRoot.addChildNode(it) }
        onDispose {
            nodos.forEach { it.parent = null }
            boxRoot.parent = null
        }
    }

    // Pose de la RAÍZ del box: posición + inclinación + giro + escala LOD. Se
    // re-aplica al cambiar la colocación o el LOD.
    LaunchedEffect(colocacion, lodFactor) {
        boxRoot.position = Position(colocacion.x, colocacion.y, colocacion.z)
        boxRoot.rotation = Rotation(x = colocacion.inc, y = colocacion.rot)
        boxRoot.scale = Float3(lodFactor, lodFactor, lodFactor)
        // El overlay HTML del espectador refleja la colocación real.
        webView.evaluateJavascript(
            "window.actualizarAncla && actualizarAncla(${colocacion.x},${colocacion.y},${colocacion.z})",
            null
        )
    }

    // Geometría local de cada cara (tamaño + offset/rotación locales respecto a
    // la raíZ). Se recalcula al cambiar la colocación (ancho/alto → prof).
    LaunchedEffect(colocacion) {
        val w = colocacion.ancho
        val h = colocacion.alto
        val prof = h * PROF_LATERAL_RATIO
        // Chasis: 6 caras oscuras que cierran la caja (opaca, sin ver a través).
        chasisFrente.updateGeometry(size = Float3(w, h, 0f))
        chasisFrente.position = Position(0f, 0f, prof / 2f)
        chasisAtras.updateGeometry(size = Float3(w, h, 0f))
        chasisAtras.position = Position(0f, 0f, -prof / 2f)
        chasisAtras.rotation = Rotation(y = 180f)
        chasisDer.updateGeometry(size = Float3(prof, h, 0f))
        chasisDer.position = Position(w / 2f, 0f, 0f)
        chasisDer.rotation = Rotation(y = 90f)
        chasisIzq.updateGeometry(size = Float3(prof, h, 0f))
        chasisIzq.position = Position(-w / 2f, 0f, 0f)
        chasisIzq.rotation = Rotation(y = -90f)
        // Tapa (normal +y) y base (normal −y): cierran el box por arriba/abajo.
        chasisArriba.updateGeometry(size = Float3(w, prof, 0f))
        chasisArriba.position = Position(0f, h / 2f, 0f)
        chasisArriba.rotation = Rotation(x = -90f)
        chasisAbajo.updateGeometry(size = Float3(w, prof, 0f))
        chasisAbajo.position = Position(0f, -h / 2f, 0f)
        chasisAbajo.rotation = Rotation(x = 90f)
        // Decal LED al aspecto NATIVO del contenido (ancho×ancho·286/640, sin
        // distorsión), centrado vertical (bezel del chasis arriba/abajo) y
        // separado ~0.02 del chasis para no pelear en profundidad (Z-fighting).
        val altoLed = w * TABLERO_DP_ALTO / TABLERO_DP_ANCHO
        decalFrente.updateGeometry(size = Float3(w, altoLed, 0f))
        decalFrente.position = Position(0f, 0f, prof / 2f + 0.02f)
        decalAtras.updateGeometry(size = Float3(w, altoLed, 0f))
        decalAtras.position = Position(0f, 0f, -(prof / 2f + 0.02f))
        decalAtras.rotation = Rotation(y = 180f)
        android.util.Log.i(
            "TABLERO_AR",
            "box ${w}x${h} prof=$prof led=${w}x${altoLed} → pos=(${colocacion.x},${colocacion.y},${colocacion.z}) lod=$lodFactor"
        )
    }

    // Refresco de la textura: vuelca el contenido vivo del WebView al bitmap y
    // lo sube a los decals LED (frente y respaldo). El chasis es estático.
    // Corre en el hilo principal (draw del WebView).
    LaunchedEffect(Unit) {
        var latido = 0
        while (isActive) {
            delay(INTERVALO_REFRESCO_MS)
            bitmap.eraseColor(android.graphics.Color.BLACK)
            webView.draw(Canvas(bitmap))
            decalFrente.bitmap = bitmap
            decalAtras.bitmap = bitmap
            // Heartbeat: si el tablero "se congela", este log dirá si el loop
            // sigue vivo (el problema estaría en el WebView, no aquí).
            if (++latido % 13 == 0) {
                android.util.Log.i("TABLERO_AR", "refresco vivo n=$latido")
            }
        }
    }
}
