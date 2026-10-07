# mcp-spheroid

MCP server local para trabajar con la app **ExposicionCCD** de Spheroid Universe
desde OpenCode. Registrado globalmente como servidor `spheroid`
(`~/.config/opencode/opencode.json`).

## Herramientas

| Herramienta | Qué hace | Requiere nube |
| --- | --- | --- |
| `status` | Diagnóstico de configuración (siempre disponible) | no |
| `list_files` | Lista los archivos del proyecto local | no |
| `read_file` | Lee un archivo: de la nube si hay ID, si no de la copia local (avisando que puede estar desfasada) | opcional |
| `save_file` | Guarda contenido completo (`target: cloud` \| `local`) | cloud: sí |
| `publish` | Publica (build) la app — **solo con aprobación explícita del usuario** | sí |
| `list_builds` | Lista los builds publicados | sí + `SPHEROID_ENDPOINT_BUILDS` |

## Configuración (variables de entorno)

Configuración actual (7 oct 2026): **nube operativa** — base + token + 8 IDs en
el `environment` de la entrada `spheroid` del opencode.json. `status` debe decir
*Nube CONFIGURADA*; si el JWT vence (31 oct 2026), re-extraerlo de Demiurge
(`sessionStorage["oidc.user:https://auth.spheroid.io:Demiurge.Frontend"]`).

```sh
SPHEROID_API_BASE      # https://platform-api.spheroid.io
SPHEROID_TOKEN         # JWT Bearer (scope Platform.Api) — caduca; re-extraer
SPHEROID_FILE_IDS      # JSON path → id completo de los 8 archivos (ya en config)
SPHEROID_ENDPOINT_BUILDS   # SIN endpoint: usar GET api/app/details?appId= → publications
# Opcionales:
SPHEROID_APP_ID        # por defecto a8c6da8a-abd9-4cfe-84a3-819d20000603
SPHEROID_AUTH_HEADER   # por defecto Authorization
SPHEROID_AUTH_SCHEME   # por defecto Bearer ("" = valor crudo)
SPHEROID_ENDPOINT_GET      # por defecto api/file/content/get
SPHEROID_ENDPOINT_SAVE     # por defecto api/file/content/save
SPHEROID_ENDPOINT_PUBLISH  # por defecto api/publish/publish
SPHEROID_LOCAL_PROJECT     # por defecto ../exposicion-ccd
```

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
   también roto en la UI). `api/app/update` sirve para renombrar.

## Desarrollo

```sh
npm install
npm test        # prueba de humo: conecta, lista herramientas, llama a status/read/save
npm start       # arranca por stdio (lo hace OpenCode, no a mano)
```
