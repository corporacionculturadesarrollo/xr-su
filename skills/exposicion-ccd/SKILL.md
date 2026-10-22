---
name: App XR ExposicionCCD
description: Proyecto ExposicionCCD en Spheroid Universe — estaciones georreferenciadas de la Corporación Cultura y Desarrollo. Actívalo para retomar, editar, añadir estaciones o publicar este proyecto específico, y cuando se mencione ExposicionCCD, el appId a8c6da8a, la escultura Un clavo, el Space de Ibagué, las estaciones de Bogotá/Ibagué, el staging exposicion-ccd o el modelo unclavo_soc.glb.
slash: true
---

# ExposicionCCD — exposición XR de la Corporación

## Estado: ACTIVA (retomada 7 oct 2026; vigente build 50)

- Pausa anterior levantada por el usuario ("volvemos a escultura… en xr").
- Historial: build 36 (7 oct, ficha de la escultura en vivo) → **build 50**
  vigente (verificado 20 oct 2026 con `list_builds`): la fase de sombra solar
  ya está **subida y publicada**.
- Contenido XR **solo institucional aprobado**; `publish` solo con visto bueno del usuario.

## Identificadores

- **appId**: `a8c6da8a-abd9-4cfe-84a3-819d20000603`
- **Layer**: `5917fe68-57fb-497d-ac35-1ce4412a6aca` ("ExposicionCCD")
- IDs de archivo (completos; obtener con `POST api/directory/list {appId}`):

| Archivo | ID |
| --- | --- |
| app.json | `198d093e-7299-4043-9d84-4489106fa872` |
| Client.spheroid | `94fae9ab-8bc6-4b57-b196-58528a0c8233` |
| GetStationsAction.spheroid | `539d8322-28e4-4a53-b9c4-0f39e0ba7d20` |
| Stations.spheroid | `0862e679-1954-4367-9462-f65e8a95ceec` |
| StartupAction.spheroid | `b27b7b0b-1790-4a96-8593-ee32a641c57f` |
| map_icon.png | `16d8c9f1-15ba-4e50-8ed8-5c382413324c` |
| notification_icon.png | `8d7b2a68-3bca-471d-a94d-6e638348b5bc` |
| unclavo_soc.glb | `1dfd3b25-6d47-4aeb-85dc-94ef76d35fc0` |
| Sun.spheroid | `921c9f73-4beb-4fba-9df8-230fb5e4c78f` |
| sombra_sol.glb | `a387460f-cd05-4c10-b946-7a9fa0b3a39d` |

- Verificado 20 oct 2026 con `list_files`: la nube tiene exactamente estos **10 archivos**.
- El árbol de la nube es **plano** (sin subcarpetas); el staging local sí usa `client/`, `server/`, `assets/`.

## API (recuperada y verificada el 7 oct 2026)

- Base `https://platform-api.spheroid.io` — auth `Authorization: Bearer <JWT>` (OIDC `auth.spheroid.io`, client `Demiurge.Frontend`, scope `Platform.Api`; el JWT vigente **vence 31 oct 2026** — la tool `status` del MCP muestra solo la fecha y los días restantes; re-extraerlo de `sessionStorage["oidc.user:https://auth.spheroid.io:Demiurge.Frontend"]` con sesión activa en Demiurge). **No hay refresh token** (Demiurge no pide `offline_access`; el grant existe en el servidor pero el cliente no lo usa) ni device flow: al abrir Demiurge con sesión viva el silent renew emite un token nuevo de ~24 días → re-extraer. Detalle y sondas en `mcp-spheroid/README.md` (sección Renovación del JWT).
- Endpoints (los 26, ver `mcp-spheroid/README.md`):
  - `POST api/directory/list {appId}` → archivos con IDs.
  - `POST api/file/content/get {appId, fileId}` → `{content}`.
  - `POST api/file/content/save {appId, fileId, content}` → `{file, success}`.
  - `POST api/publish/startup {appId}` → capas; luego `POST api/publish/publish {appId, publishClient, publishServer, isDebug, layerIds, newLayerTitle}` → `{result:1}` (publicación en 2 fases).
  - `GET api/app/details?appId=` → `publications[0].appVersion.build` = **número de build**.
  - `POST api/app/delete` **NO EXISTE** (404 vacío, todas las variantes): el borrado de apps está roto también en la UI de Demiurge.

## La escultura «Un clavo… s o c»

- `unclavo_soc.glb` (5.084.748 bytes) es el `stationModel` de **todas** las estaciones (Client.spheroid).
- Space real: estación `space-ccd-iibague` → **4.449718, -75.248218** (campos `py`, `baseLat`, `baseLng`).
- El cliente trae panel de posicionamiento in-situ (PASO/AYUDA/SUBIR/BAJAR/W-E-N-S/GPS AQUI/GUARDAR/VOLVER/RESET); `savePosition` persiste lat/lng/py en la tabla `positions` (select/insert/delete en GetStationsAction).
- **Ficha al tocar** (`"${station.title} — ${station.text}"`): desde el build 36 →
  `Un clavo... s o c — Ficción Plástica` + resumen + `https://blog.culturaydesarrollo.org/un-clavo-soc-ficcion-plastica` (artículo publicado el 26 sep 2026).

## Estaciones

- `space-ccd-iibague`: Space real de la Corporación (posición refinable con el panel).
- `bogota` (4.7108, -74.0721) e `ibague` (4.4389, -75.2322): placeholders — pendientes: Space real + texto aprobado.
- **Pose calibrada (in situ)**: `Stations.spheroid` arrastra por estación `savedLat/savedLng/savedPy/calRumbo` (lo que el panel GUARDAR fijó en el lugar); `GetStationsAction` la devuelve como pose principal (lat/lng y baseLat/baseLng quedan como referencia) y el cliente aplica `ajusteRumbo = station.calRumbo` con default defensivo para servidores viejos (no aborta el alta de la estación). La sombra se ancla al pie del objeto con `sombraDe(savedLat, savedLng)`.

## Staging local

- `C:\Users\kutzm\Documents\GitHub\xr-su\exposicion-ccd\` — copia de trabajo del repo vivo (sincronizada con la nube el 7 oct; la nube siguió avanzando hasta el build 50).
- Nube = fuente de verdad: leer → editar → `save` → `publish` con visto bueno.
- ⚠ Existe una **copia snapshot** en `F:\Proyecto predeterminado\GitHub\xr-su` (estado del 7 oct, respaldada con git el 20 oct): para tocar el proyecto usa la copia de **C:**, que es a la que apunta el MCP y va más adelantada.

## Fase sombra solar (YA EN NUBE Y PUBLICADA; verificado 20 oct 2026)

- Estudio: Zhu et al. 2023 (ISPRS IJGI 12(8):324) + NOAA simplificado → detallado en el skill **Desarrollo XR con Spheroid Script** (límites de API, ejes, patrón solar).
- **Subida y publicada**: `Sun.spheroid` (id `921c9f73…`) y `assets/sombra_sol.glb` (id `a387460f…`, 6.460 B, fileType 3) están en la nube; los builds siguieron avanzando hasta el **50** vigente. Último toque en repo: `e21a1bf` «sombra al pie del objeto + validación `require` en Sun».
- Componentes: `client/Sun.spheroid` (módulo solar + config `sombraY/sombraLargo/sombraAncho/ajusteGiroSol/horaSolUtc/diaAnioSol/modoDemoSol`; listado en `app.json`), `client/Client.spheroid` (`sombraModel`, `aplicarSol`/`refrescarSombras`, `Timer.schedule(10 s)` avanza 1 h por tick en demo, **los ACT loops del panel filtran `child.model == stationModel`** para no guardar la y de la sombra), `assets/sombra_sol.glb` (elipse xz con vertex-color alpha + BLEND).
- **Pendientes**: hora real — **imposible con la API actual** (ver límites en el skill de sintaxis): placeholders + modo demo; fase 2 = GLB tintados por franja + feature requests a Spheroid (API de hora y de luz ambiental); y la calibración fina en dispositivo que convenga (`ajusteGiroSol`, `sombraY`).

## Herramientas MCP (`mcp-spheroid`, 11 tools)

- Servidor `spheroid` vivo en `C:\Users\kutzm\Documents\GitHub\xr-su\mcp-spheroid` (config en el opencode.json global): `status` → *Nube CONFIGURADA* **y vencimiento del JWT** (solo la fecha y los días, nunca el token).
- Nube: `list_files` (nube/local), `read_file` (nube si hay id; si no, local avisando), `save_file` (crea archivos nuevos y persiste el id en `mcp-spheroid/file-ids.json`), `upload_file` (binarios, multipart `files`+`metadata`), `delete_file` (destructivo) y `publish` (**2 fases**, solo con aprobación explícita), `list_builds` (vía `app/details` → `publications`; vigente = **build 50**).
- Vocabulario del lenguaje: `search_language` y `get_term` sobre `registry.json` (295 términos del runtime oficial).
- `api_call` para endpoints sueltos (p. ej. `api/app/list`) — **endpoint relativo obligatorio**: las URLs absolutas se rechazan para que el token no salga de la base.
- Detalle y variables en `mcp-spheroid/README.md`. Tras cambiar el código hay que reiniciar OpenCode (el proceso vivo no se recarga solo); `npm test` valida sin tocar la nube.

## Otros apps de la cuenta

- 16 apps más (New XR post ×11, Hammer Sculpture, sculpture, Rubik Presentación 8va, Copy of New XR post, Assets storage app): **no se pudieron borrar** (la API no expone el borrado; verificado en Demiurge, spheroiduniverse.io y ecosystem.spheroid.io). Dejadas pendientes.

## Fuera de aquí

Sintaxis, patrones de código y flujo general → skill **Desarrollo XR con Spheroid Script**.
