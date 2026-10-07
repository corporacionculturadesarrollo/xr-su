---
name: App XR ExposicionCCD
description: Proyecto ExposicionCCD en Spheroid Universe — estaciones georreferenciadas de la Corporación Cultura y Desarrollo. Actívalo para retomar, editar, añadir estaciones o publicar este proyecto específico, y cuando se mencione ExposicionCCD, el appId a8c6da8a, la escultura Un clavo, el Space de Ibagué, las estaciones de Bogotá/Ibagué, el staging exposicion-ccd o el modelo unclavo_soc.glb.
slash: true
---

# ExposicionCCD — exposición XR de la Corporación

## Estado: ACTIVA (retomada 7 oct 2026)

- Pausa anterior levantada por el usuario ("volvemos a escultura… en xr").
- **Build 36** publicado el 7 oct 2026: ficha de la escultura en vivo.
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

- El árbol de la nube es **plano** (sin subcarpetas); el staging local sí usa `client/`, `server/`, `assets/`.

## API (recuperada y verificada el 7 oct 2026)

- Base `https://platform-api.spheroid.io` — auth `Authorization: Bearer <JWT>` (OIDC `auth.spheroid.io`, client `Demiurge.Frontend`, scope `Platform.Api`; el JWT vigente **vence 31 oct 2026** — re-extraerlo de `sessionStorage["oidc.user:https://auth.spheroid.io:Demiurge.Frontend"]` con sesión activa en Demiurge).
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

## Staging local

- `C:\Users\kutzm\Documents\GitHub\xr-su\exposicion-ccd\` — **sincronizado con la nube el 7 oct 2026** (incluye `assets/unclavo_soc.glb`; `estacion.glb` retirado).
- Nube = fuente de verdad: leer → editar → `save` → `publish` con visto bueno.

## Herramientas MCP

- Servidor `spheroid` **configurado en nube** (opencode.json global: API base, token, 8 IDs, endpoints): `status` → *Nube CONFIGURADA*; `read_file` y `save_file` verificados en vivo.
- `publish` solo con aprobación explícita. `list_builds` no tiene endpoint: usar `app/details → publications`.

## Otros apps de la cuenta

- 16 apps más (New XR post ×11, Hammer Sculpture, sculpture, Rubik Presentación 8va, Copy of New XR post, Assets storage app): **no se pudieron borrar** (la API no expone el borrado; verificado en Demiurge, spheroiduniverse.io y ecosystem.spheroid.io). Dejadas pendientes.

## Fuera de aquí

Sintaxis, patrones de código y flujo general → skill **Desarrollo XR con Spheroid Script**.
