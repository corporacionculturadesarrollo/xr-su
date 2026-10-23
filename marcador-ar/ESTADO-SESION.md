# Estado de sesión — 2026-10-13 (pausa del usuario)

> **Actualización 2026-10-22** (otra máquina): entorno reconstruido en WSL
> (root, SDK en `/root/Android/Sdk`) y build re-lanzado tras fallo del daemon;
> APK pendiente de confirmar. Detalle y comandos nuevos en
> `docs/sesiones/2026-10-22-entorno-build-marcador.md`. Los comandos de abajo
> usan rutas de la máquina antigua (`$HOME/jdk17`, `$HOME/Android/Sdk`) —
> ajustar a `/root/...` aquí.

## Dónde estábamos
Sistema de marcador AR completo y compilando verde. App Kotlin única
(`10AF6F0AQF0045A`, Vivo V2436, Android 16): admin posiciona/ajusta el tablero;
espectador lo ve fijo en 3D vía AR; con `geo` fijada sobrevive reinicios.

### Logrado y verificado en esta sesión
- **92 tests JVM verdes** (`./gradlew test`) + APK debug compilado e instalado.
- **Incremento colocación completo en servidor/core**: `Colocacion`/`GeoColocacion`
  (`core/Colocacion.kt`), `cmd place` (admin-only, merge parcial), `geo:"fijar"` →
  broadcast `fijar`, `tipo:"geo"` (cualquier cliente con sesión AR reporta su
  pose), `welcome` arrastra `colocacion`, hook `alCambiarColocacion` +
  `PersistenciaColocacion` (JSON en filesDir → sobrevive reinicio de la app).
- **VistaAr.kt reescrito**: cliente WS nativo (Supervisor+ClienteMarcador+
  FabricaOkHttp) con el MISMO deviceId que la página admin (pasa por intent desde
  MainActivity) → rol admin por reconexión en gracia; recibe broadcasts de
  colocación y aplica posición/giro/escala a las dos caras 3D; panel nativo
  rápido en Compose (Tamaño/Altura/Izq-Der/Atrás-Adel/Giro con steppers + valor
  numérico, Fijar/Liberar); botón "◀ Admin" para alternar vistas.
- **Geospatial implementado**: `geospatialMode=ENABLED`, permiso
  `ACCESS_FINE_LOCATION` runtime, fijar = captura pose WGS84 del ancla
  (`earth.getGeospatialPose(anchor.pose)` → lat/lon/alt + yaw EUS de
  `getEastUpSouthQuaternion()` — `getHeading()` está deprecado y ya no se llena),
  restauración = `earth.createAnchor(lat, lon, alt, cuaternión yaw EUS)` cuando
  Earth está TRACKING, sin tap. Sin API key/permiso → degrada a tap manual con
  aviso en pantalla.
- **marcador.html**: panel CONTROL AR admin (botones ± + inputs numéricos +
  Fijar/Liberar) y overlay "ancla fija" del espectador que refleja x/y/z reales
  (`actualizarAncla`); el puente JS `MarcadorAr`/`enviarGeo` se retiró (el lado
  nativo habla WS directo).

### Pendiente (siguiente sesión)
1. **Verificación en vivo end-to-end** (usuario en pausa; el teléfono estaba en uso):
   - Reclamar rol admin → "Ver en AR" → tocar suelo (o restaurar geo) →
     panel nativo/HTML mueve el tablero en tiempo real.
   - "Fijar en el mapa" → verificar lat/lon en el panel → forzar-stop →
     relanzar → tablero reaparece en las coordenadas (requiere
     `AR_GEOSPATIAL_KEY` para fix GPS; sin ella solo funciona el tap).
   - Verificar el fix de las dos caras (z=0 + ±1 cm) en vivo.
2. **Legibilidad a >8 m**: el marcador se ve oscuro a distancia — material
   unlit/emissive o boost de brillo en el bitmap (ColorMatrix).
3. Restaurar altura final del tablero según modelo de posicionamiento.
4. Refserver Node (`ref/server.js`) aún NO implementa place/geo/fijar — añadir
   si se usa para espectadores externos.

## Comandos clave
- Build: `cd marcador-ar/android && export JAVA_HOME=$HOME/jdk17 && ./gradlew test assembleDebug --console=plain`
- adb: `export PATH="$HOME/Android/Sdk/platform-tools:$PATH"`
- Instalar: `adb -s 10AF6F0AQF0045A install -r marcador-ar/android/app/build/outputs/apk/debug/app-debug.apk`
- Lanzar admin: `adb shell am start -n com.marcadorar.app/.MainActivity` (VistaAr NO es exported; solo por botón)
- Logcat AR: `adb logcat -s TABLERO_AR:*` (console WebView, refresh, geo)
- adb intermitente: replug + `adb kill-server`; pantalla apagada → `input keyevent KEYCODE_WAKEUP` + `wm dismiss-keyguard`.
