# Entorno Android y build de marcador-ar en WSL (esta máquina)

**Estado:** abierto · **Inicio:** 2026-10-22 · **Última:** 2026-10-22

Contexto: el desarrollo del 11–13 oct (ver `marcador-ar/ESTADO-SESION.md`) fue en
**otra máquina**; este repo en esta PC arranca de cero. WSL Ubuntu de esta
máquina arranca como **root**, sin usuario `kutzmanich`, `/home` inexistente.

## 19:49 — Montaje del entorno (script idempotente)

**Qué se hizo** — `marcador-setup.sh` en WSL: JDK 17 (ya instalado), SDK 36
(`platform-tools`, `platforms;android-36`, `build-tools` 35.0.0 + 36.0.0) en
`/root/Android/Sdk`, licencias aceptadas, `local.properties` reescrito de
`/home/kutzmanich/Android/Sdk` → `/root/Android/Sdk`. usbipd-win 5.3.0
(`dorssel.usbipd-win`) instalado en Windows para el futuro attach del Vivo.

**Decisiones**
- Script `.sh` en archivo y no `wsl bash -c '...'`: PS 5.1 rompe las comillas
  dobles — regla ya aprendida, reconfirmada hoy.
- El script es **idempotente** (todo por `|| true` + comprobaciones): pensado
  para relanzarse tras cortes.

**Errores míos**
- El MSI de usbipd **reinició la VM de WSL** a mitad del primer intento (mató
  la descarga del SDK). Detectado por fallo silencioso del script; solución:
  relanzar el mismo script (ya tenía las etapas previas completadas).

## 21:00 — GRADLE-FAIL: daemon «disappeared», diagnóstico y cura

**Qué se hizo** — El build (`gradlew test assembleDebug`, background) falló con
«Gradle build daemon disappeared unexpectedly» justo tras instalar
build-tools 35 por AGP. Diagnóstico descartando causas: VM **sin reiniciar**
(uptime 1:15), **sin OOM** (dmesg limpio), sin hs_err. Causa real aparente:
corte de comunicación del cliente con el daemon en drvfs (`/mnt/c`); el daemon
siguió **huérfano** y a las 21:46 un `KotlinCompileDaemon` compilaba con CPU
alta → el build continuaba sin cliente.

**Decisiones**
- **No matar al huérfano**: dejarlo terminar; monitor background vigila si
  aparece `app/debug/app-debug.apk` (máx. 12 min).
- **`android/gradle.properties` nuevo** con `-Xmx1536m` para daemons Gradle y
  Kotlin: antes cada uno pedía **3 GiB sobre una VM de 4 GiB** (50% de 8 GB del
  host, sin `.wslconfig`) — dos JVMs así es insostenible aunque solo uno esté
  vivo. Cambio commiteado junto a esta bitácora.
- Si el huérfano no produce APK: relanzar `marcador-setup.sh` (idempotente; el
  SDK ya está, serían minutos) — **con el usuario presente**, no de madrugada.

**Pendientes**
- [ ] E2E en vivo con el Vivo cuando esté disponible: `usbipd attach --wsl`
      (PowerShell **admin**, celular desbloqueado) → `adb install -r app-debug.apk`
      → guiado por `marcador-ar/ESTADO-SESION.md`, modo tap.
- [ ] Considerar `.wslconfig` (`memory=6GB`) si se repiten muertes por memoria.

**Verificación**
```bash
wsl -d Ubuntu -e bash -c "free -h; ls /mnt/c/Users/kutzm/Documents/GitHub/xr-su/marcador-ar/android/app/build/outputs/apk/debug/ 2>/dev/null"
# relanzar build si hace falta:
wsl -d Ubuntu -e bash /mnt/c/Users/kutzm/AppData/Local/Temp/opencode/marcador-setup.sh
```

## 2026-10-23 09:39 — BUILD OK: APK verificado + 182 tests verdes

**Qué se hizo** — Tras el fallo transitorio HTTP/2 de `dl.google.com` (script
endurecido: salta la descarga de cmdline-tools si ya existe + `curl --http1.1
--retry 5`), Gradle falló dos veces y en la tercera salió verde: `BUILD
SUCCESSFUL in 1m 32s` (73 tareas). Verificado con `aapt2 dump badging`
(paquete `com.marcadorar.app`, label «Marcador AR», compileSdk 36) y con
`test-results`: **182 tests (91×2 variantes), 0 failures, 0 errors**.
APK: `app/build/outputs/apk/debug/app-debug.apk` (46,6 MB).

**Errores míos**
1. `android.useAndroidX=true` no existía en ningún sitio de esta máquina: en la
   otra vivía en el **gradle de usuario** (`~/.gradle/gradle.properties` de
   `kutzmanich`), que no viajó con la copia del proyecto; mi `gradle.properties`
   nuevo solo traía los límites de memoria. El task `checkDebugAarMetadata` lo
   exigió. **Lección: al migrar un proyecto Android a otra máquina, revisar
   también la configuración de Gradle de usuario, no solo la del repo.**
2. (Corrección de ayer) el «APK de 21:57» del daemon huérfano era **fantasma**:
   transitorio y sin `test-results`. La verificación correcta es
   `aapt2 dump badging` + contar `test-results/*.xml`, no un `ls` del APK.

**Pendientes**
- [x] Build verde con heaps acotados (1,5 G) — sin OOM ni muerte de daemon.
- [ ] E2E en vivo con el Vivo V2436 (pendiente de que el usuario lo conecte).
