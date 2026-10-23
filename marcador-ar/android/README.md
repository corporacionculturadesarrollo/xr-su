# Marcador AR — app Android

Núcleo del protocolo portado a Kotlin, con el toolchain Android funcionando de punta a punta.

## Entorno instalado (Linux Mint, sin sudo, todo en el home)

| Componente | Ruta |
|---|---|
| Android Studio (Quail 4 / 2026.1.4.7) | `~/android-studio` → lanzar con `~/android-studio/bin/studio.sh` (o el ícono del menú) |
| JDK 17 (Temurin) | `~/jdk17` |
| Android SDK | `~/Android/Sdk` (platform-tools/adb, platforms;android-36, build-tools;35.0.0, cmdline-tools) |
| Gradle 8.14.5 | incluido vía wrapper (`./gradlew`) — no requiere instalación aparte |

**Stack del proyecto** (alineado al de SceneView 4.25.0): AGP 8.13.2 · Kotlin 2.4.10 ·
compileSdk/targetSdk 36 · minSdk 26 · Compose BOM 2026.06.01 · `io.github.sceneview:arsceneview:4.25.0` ·
OkHttp 4.12.0 · Java-WebSocket 1.6.0.

Variables ya agregadas a `~/.bashrc`: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME`, y `PATH` con `platform-tools` + `cmdline-tools`.

## Cómo abrir y compilar

**En Android Studio:** `File ▸ Open…` y elegir esta carpeta (`marcador-ar/android`). Detecta el SDK en `~/Android/Sdk` (su ubicación por defecto) y el wrapper de Gradle. Para probar en el teléfono: habilitá *Depuración USB* en el móvil y conectalo por cable — el botón ▶ lo instala.

**Desde la terminal** (misma carpeta):
```bash
./gradlew test            # pruebas del núcleo (92, verdes)
./gradlew assembleDebug   # genera app/build/outputs/apk/debug/app-debug.apk

# arranca el servidor embebido de Kotlin para probar el tablero en el navegador:
#   PUERTO=8897 ./gradlew servidorKotlin
#   y abrir preview/marcador.html?server=ws://127.0.0.1:8897&instancia=principal
```

> Nota RAM: con 8 GB, cerrá otras apps pesadas al compilar. El **emulador** es muy lento con esa RAM y además **ARCore necesita un teléfono físico**, así que el camino de prueba real es el dispositivo por USB.

## Qué contiene (estado actual)

**`core/` — Kotlin puro (sin Android), todo testeado:**
- `Partido.kt` — reducer puro del partido (port de `ref/estado.js`): scores, faltas+bonus, shot 24/14, relojes, períodos, setup.
- `Roles.kt` — singleton admin (port de `ref/roles.js`): claim atómico, heartbeat con timeout, renuncia, reconexión.
- `Protocolo.kt` / `Json.kt` — códec del protocolo v1 (wire ⇄ `Cmd`/`Estado`/`Config`) con parser JSON propio sin dependencias.
- `ClienteMarcador.kt` / `Supervisor.kt` — cliente de sesión (join, latido solo-admin, reconexión) sobre la interfaz `Transporte`.
- `ServidorMarcador.kt` — port transport-agnóstico de `server.js` (compone `Partido`+`Roles`), base del servidor embebido. Incluye la **colocación AR**: `cmd place` (admin-only, merge parcial de `ancho/x/y/z/rot`; `geo` ausente=conserva, `null`=borra, objeto=nueva), `geo:"fijar"` → broadcast `fijar`, `tipo:"geo"` (reporte de pose WGS84 del dispositivo con sesión AR, sin exigir rol), `welcome` arrastra la colocación, hook `alCambiarColocacion`.
- `Colocacion.kt` — modelo `Colocacion(ancho, x, y, z, rot, geo)` + `GeoColocacion(lat, lon, alt, rot)` (rot = yaw en marco EUS de ARCore).
- `TemporizadorProgramado.kt` — `Temporizador` sobre un hilo programado (lo usa el servidor para serializar red+reloj).

**`app/` — capa Android:**
- `net/FabricaOkHttp.kt` — transporte cliente (OkHttp), callbacks al hilo principal.
- `net/ServidorWebSocket.kt` — **servidor embebido** sobre Java-WebSocket: el teléfono del admin aloja la instancia.
- `net/TemporizadorAndroid.kt` — `Temporizador` sobre `Handler` (lado cliente en el hilo principal).
- `MainActivity.kt` — arranca `ServidorWebSocket` (con la colocación persistida por `PersistenciaColocacion`), carga `preview/marcador.html` en un **WebView** apuntándole por loopback y pasa el botón flotante **"Ver en AR"** → al pulsarlo lee el deviceId admin del `localStorage` de la página y lo envía por intent a `VistaAr`.
- `PersistenciaColocacion.kt` — guarda/carga la colocación JSON en `filesDir` (tolerante a archivos corruptos): la posición AR sobrevive reinicios de la app.
- `VistaAr.kt` — **vista AR** (Compose + SceneView 4.x): `ARSceneView` con `geospatialMode=ENABLED`, retículo (`PlacementReticle`) y ancla por tap. El tablero son **dos `ImageNode`** (frente + respaldo 180°) que comparten un bitmap refrescado cada 400 ms desde un WebView offscreen (`LAYER_TYPE_SOFTWARE`) que carga el **mismo** `preview/marcador.html` con `?vista=espectador` (ViewNode y RGB_565 se descartaron: fondo negro y crash SIGABRT). La colocación del admin (posición/giro/escala en metros) se aplica a los nodos vía broadcast; incluye **cliente WS nativo** con el mismo deviceId admin (reconexión en gracia por deviceId → rol admin) que recibe colocaciones y manda `place`/`geo` desde un **panel rápido nativo** (steppers ± + valores, Fijar/Liberar) y el botón **"◀ Admin"** para alternar vistas. **Fijado geográfico**: `earth.getGeospatialPose(anchor.pose)` → lat/lon/alt + yaw EUS (`getEastUpSouthQuaternion()`; `getHeading()` está deprecado) al pulsar "Fijar"; al arrancar con `geo` guardada, `earth.createAnchor(lat, lon, alt, cuaternión)` recrea el ancla solo cuando Earth está TRACKING (sin tocar la pantalla; fallback = tap manual). Sin API key o permiso de ubicación degrada con aviso en pantalla.

**Assets sin deriva:** la tarea `sincronizarTablero` copia `preview/marcador.html` + `fonts/*.ttf` a los assets en cada build — la fuente de verdad sigue siendo `preview/marcador.html`; nunca se duplica a mano.

**92 pruebas** (JUnit): reducer, roles, códec, cliente, supervisor, servidor embebido (incluida la colocación/geo), e interop de socket real. Además se verificó en navegador la integración **HTML del tablero ↔ servidor Kotlin** por socket: claim→`rol:admin`, comandos→eco, reloj del servidor ticking, broadcast a espectador, y rechazo de comandos del espectador (solo lectura).

## API key de Geospatial (para el fijado geográfico)

El fijado con coordenadas WGS84 (sobrevive reinicios sin volver a tocar la pantalla) usa la
**ARCore Geospatial API**, que exige una API key de Google Cloud (proyecto con la API "AR
Core Geospatial API" habilitada). La app la recibe por `manifestPlaceholder` `arGeospatialKey`
(en `app/build.gradle.kt`, lee `AR_GEOSPATIAL_KEY` de las variables de entorno o de
`local.properties` — nunca se commitea) y la expone al runtime vía el meta-data
`com.google.ar.core.geospatial_api_key` del `AndroidManifest.xml`. Sin la key (o sin permiso
`ACCESS_FINE_LOCATION` concedido) la vista AR funciona igual pero el fijado geográfico se
sustituye por la colocación temporal por tap y aparece un aviso en pantalla.

## Fix importante: `SO_REUSEADDR` del servidor embebido

Síntoma: tras force-stop o reinstalación, la app arrancaba en "modo local" (sin servidor):
`BindException: Address already in use` en el puerto 8899. Causa: Java-WebSocket llama a
`setReuseAddr(false)` en su constructor, y tras un reinicio los sockets del servidor anterior
quedan en `TIME_WAIT` (~60 s) — el nuevo bind fallaba y la instancia seguía sin servidor.

Fix (en `net/ServidorWebSocket.kt`): `setReuseAddr(true)` en el `init{}`, **antes** de
`start()`, más un loop de reintentos de bind en `MainActivity` (6 intentos, `esperarListo(2)`,
espera 1 s entre intentos) para el caso de LISTEN aún vivo. Verificado en vivo: bind
instantáneo (~37 ms) con sockets TIME_WAIT presentes.

## Estado de la vista AR (sobre el dispositivo real)

Verificado en un Vivo V2436 (Android 16, ARCore): colocación por tap, dos caras con el tablero
(WebView → bitmap → `ImageNode`, z=0 + separación ±1 cm para evitar z-fighting), cámara
ARCore a ~50 fps, cliente WS nativo con rol admin (mismo deviceId que la página) y panel
rápido nativo. **Pendiente de verificación en vivo**: el flujo end-to-end de colocación
(admin mueve → AR responde en tiempo real) y el fijado geográfico (Fijar → reiniciar →
ancla restaurada en coordenadas; requiere `AR_GEOSPATIAL_KEY`). Logs de diagnóstico en
logcat: tag `SRV` (servidor), `TABLERO` (WebView admin) y `TABLERO_AR` (vista AR — consola
del WebView, refresco del bitmap, geo).

**Siguientes pasos:** verificar en vivo end-to-end; legibilidad a >8 m (el tablero se ve
oscuro a distancia — boost de brillo/emissive); foto/video con overlay del marcador;
y multi-instancia/sede con el rol super admin.
