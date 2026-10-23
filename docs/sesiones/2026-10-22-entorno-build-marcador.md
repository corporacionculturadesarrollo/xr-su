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
- [ ] **Veredicto del monitor**: APK generado → siguiente paso instalar en el
      Vivo; sin APK → relanzar build con heaps acotados.
- [ ] Instalar en Vivo V2436 cuando esté disponible: `usbipd attach --wsl` →
      `adb install -r .../app-debug.apk` → E2E **modo tap** (sin
      `AR_GEOSPATIAL_KEY`), guiado por `marcador-ar/ESTADO-SESION.md`.
- [ ] Considerar `.wslconfig` (`memory=6GB`) si se repiten muertes por memoria.

**Verificación**
```bash
wsl -d Ubuntu -e bash -c "free -h; ls /mnt/c/Users/kutzm/Documents/GitHub/xr-su/marcador-ar/android/app/build/outputs/apk/debug/ 2>/dev/null"
# relanzar build si hace falta:
wsl -d Ubuntu -e bash /mnt/c/Users/kutzm/AppData/Local/Temp/opencode/marcador-setup.sh
```
