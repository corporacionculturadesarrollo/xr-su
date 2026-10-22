# mcp-spheroid

MCP server local para trabajar con la app **ExposicionCCD** de Spheroid Universe
desde OpenCode. Registrado globalmente como servidor `spheroid`
(`~/.config/opencode/opencode.json`).

## Herramientas

| Herramienta | Qué hace | Requiere nube |
| --- | --- | --- |
| `status` | Diagnóstico de configuración (siempre disponible) | no |
| `list_files` | Lista archivos: de la **nube** (`api/directory/list`, por defecto) o de la copia local | nube opcional |
| `read_file` | Lee un archivo: de la nube si hay ID, si no de la copia local (avisando que puede estar desfasada) | opcional |
| `save_file` | Guarda contenido completo (`target: cloud` \| `local`). Si el archivo no existe aún en la nube, **lo crea** (`file/create`) y persiste el id en `file-ids.json` | cloud: sí |
| `upload_file` | Sube un **binario** (assets) con `api/file/upload` (multipart `files` + `metadata`) — solo con aprobación | sí |
| `delete_file` | Borra un archivo de la nube — **destructivo, solo con aprobación** | sí |
| `search_language` | Busca en el **vocabulario oficial Spheroid Script** (295 términos de `registry.json`, recuperados del binario del runtime) | no |
| `get_term` | Detalle de un término exacto del vocabulario (+ sugerencias si no existe) | no |
| `api_call` | Llamada **genérica** a la API (con el Origin de Demiurge) para endpoints no cubiertos, p. ej. `api/app/list`. Endpoint **relativo obligatorio**: no se manda el token fuera de la base | sí |
| `publish` | Publica (build) en **2 fases** (`publish/startup` → `publish/publish`) — **solo con aprobación explícita del usuario** | sí |
| `list_builds` | Builds publicados vía `GET api/app/details` → `app.publications` | sí |

## Configuración (variables de entorno)

Configuración actual (7 oct 2026): **nube operativa** — base + token + 8 IDs en
el `environment` de la entrada `spheroid` del opencode.json. `status` debe decir
*Nube CONFIGURADA* y ahora **muestra solo el vencimiento del JWT** (decodifica
`exp` sin imprimir el token): si vence o está por vencer (< 7 días), re-extraerlo
de Demiurge (`sessionStorage["oidc.user:https://auth.spheroid.io:Demiurge.Frontend"]`).

```sh
SPHEROID_API_BASE      # https://platform-api.spheroid.io
SPHEROID_TOKEN         # JWT Bearer (scope Platform.Api) — caduca; re-extraer
SPHEROID_FILE_IDS      # JSON path → id completo de los 8 archivos (ya en config)
# Opcionales (los defaults de todos los endpoints ya están verificados):
SPHEROID_APP_ID        # por defecto a8c6da8a-abd9-4cfe-84a3-819d20000603
SPHEROID_AUTH_HEADER   # por defecto Authorization
SPHEROID_AUTH_SCHEME   # por defecto Bearer ("" = valor crudo)
SPHEROID_ENDPOINT_GET      # por defecto api/file/content/get
SPHEROID_ENDPOINT_SAVE     # por defecto api/file/content/save
SPHEROID_ENDPOINT_CREATE   # por defecto api/file/create
SPHEROID_ENDPOINT_DELETE   # por defecto api/file/delete
SPHEROID_ENDPOINT_UPLOAD   # por defecto api/file/upload
SPHEROID_ENDPOINT_DIRECTORY# por defecto api/directory/list
SPHEROID_ENDPOINT_DETAILS  # por defecto api/app/details
SPHEROID_ENDPOINT_STARTUP  # por defecto api/publish/startup
SPHEROID_ENDPOINT_PUBLISH  # por defecto api/publish/publish
SPHEROID_LOCAL_PROJECT     # por defecto ../exposicion-ccd
```

`file-ids.json` (junto a `src/`): ids de archivos **creados por estas
herramientas** (p. ej. `client/Sun.spheroid`); se fusiona con
`SPHEROID_FILE_IDS` (este archivo gana). No contiene secretos (solo UUIDs).

`registry.json` (junto a `src/`): **295 términos** del vocabulario oficial de
Spheroid Script, recuperados por ingeniería inversa del binario del runtime
(clase `spheroid.scriptengine.d3` del APK PixelVoxel). Lo consumen
`search_language` y `get_term`; si falta el archivo esas tools quedan vacías
(con aviso en stderr al arrancar). Sin secretos.

## Estado de la API (recuperado 7 oct 2026)

1. **URL base y auth**: `https://platform-api.spheroid.io` + JWT OIDC de
   `auth.spheroid.io` (capturado desde la sesión activa de Demiurge). ✓
2. **IDs completos**: los 8 de ExposicionCCD, en `SPHEROID_FILE_IDS`. ✓
3. **Endpoint de builds**: **no existe** — el historial vive en
   `GET api/app/details?appId=` → `publications[].appVersion.build`. ✓
4. **Cuerpos verificados**: get `{appId, fileId}` · save `{appId, fileId, content}` ·
   publish 2 fases `publish/startup {appId}` → `publish/publish {appId,
   publishClient, publishServer, isDebug, layerIds, newLayerTitle}`. ✓
5. **Borrado de apps**: `api/app/delete` **no existe en el backend** (404 vacío;
   también roto en la UI). `api/app/update` sirve para renombrar. (En el bundle
   de Demiurge la llamada SÍ está; el 404 lo da el servidor.)
6. **file/create** `{appId, fileName}` → `{file, success}`. El campo `content`
   se **ignora** (queda size 0): crear y escribir con `content/save`. ✓
7. **file/delete** `{appId, fileId}` → HTTP 200. **Ojo**: HTTP 200 no implica
   éxito — mirar `success` en el cuerpo (sin `appId` devuelve `success:false`). ✓
8. **file/upload** (binarios) multipart: campo **`files`** = archivo +
   **`metadata`** = JSON `{appId, directoryId}` → `{uploadedFiles:[{id, name,
   size, fileType}]}`. Los bytes se conservan exactos. `fileType`: 1 = texto,
   2 = png, 3 = glb. ✓
9. **Mapa completo de endpoints** extraído del bundle de Demiurge
   (`demiurge.spheroid.io/app.<hash>.js`, módulo de API ~@787710):
   `file/create · directory/create · file/content/get|save · directory/list ·
   app/list (GET) · file/upload · file/delete · directory/delete ·
   file/move|copy|rename · directory/move|copy|rename · publish/startup ·
   publish/publish · app/create (FormData data+avatar) · app/update ·
   app/delete (frontend sí, backend 404) · app/details (GET → app.publications)`.
   El helper de ese bundle usa axios y **borra el Content-Type en FormData**
   (igual que hace este MCP: fetch pone el boundary solo).

## Desarrollo

```sh
npm install
npm test        # humo: verifica las 11 herramientas, status/read/search_language y el rechazo de save sin nube
npm start       # arranca por stdio (lo hace OpenCode, no a mano)
```

Tras cambiar `src/index.js`, el proceso MCP vivo (dentro de OpenCode) hay que
recargarlo (toca la config en `opencode.json` o reiniciar OpenCode); mientras
tanto, `npm test` valida los cambios.
