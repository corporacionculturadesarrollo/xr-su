# Mapa Open AR Cloud (OARC) — todo el ecosistema

**Tema:** openarcloud (Open AR Cloud Association · OSCP · SpatialDDS) — **exclusivamente este tema**.
**Estado:** abierto (v1) · **Captura:** 2026-10-22 · **Fuentes:** github.com/OpenArCloud (42/42 repos), openarcloud.org (home, /platform, /github), OSCP-Docs README.

> **Frontera de este documento** (temas diferenciados, sin mezclar):
> - **Spheroid AR Cloud OS** → `xr-su/skills/spheroid-xr/SKILL.md` + `xr-su/mcp-spheroid/README.md` — otro tema, otro cajón.
> - **Otras tecnologías afines** (VPS comerciales, MCPs geoespaciales, WebXR genérico…) → `xr-su/docs/afines/index.md`.
> - Cruces entre temas solo como indicaciones puntuales, nunca fusionados.

---

## 1. Qué es OARC

- **Open AR Cloud Association** (openarcloud.org) — iniciativa sin ánimo de lucro por **estándares abiertos, datos abiertos y open source para la AR Cloud / spatial computing**.
- Piénsenla como el **lado abierto** de lo que Spheroid Universe y Niantic venden como producto: un **conjunto de protocolos y servicios interopables** donde no hay vendor lock-in.
- Licencias de los repos: **MIT / Apache 2.0 / CC0** (uso académico y comercial).
- Contacto documentado: Alina Kadlubsky (communication@openarcloud.org), Gábor Sörös (github.com/soeroesg).

## 2. Arquitectura — OSCP (Open Spatial Computing Platform)

**Definición oficial:** conjunto de componentes interoperables para **autorar, publicar, descubrir y consumir contenido digital anclado al mundo real**.

### Arquitectura de 3 niveles

1. **Raíz — Spatial Service Discovery (SSD)**: registro descentralizado de servicios espaciales por zona geográfica; punto de entrada y orquestación.
2. **Servicios GeoZone** (en el edge):
   - **GeoPose / Localización**: posicionamiento 6DOF estandarizado, integración VPS, localización gruesa y fina.
   - **Spatial Content Discovery (SCD)**: búsqueda espacial (bounding box), filtrado por contexto, POIs, búsquedas que respetan privacidad, registro descentralizado.
   - **Contenido y experiencias**: hosting, gestión de experiencias, *spatial webpage*.
   - **Reality Modeling**: BIM (IFC/Revit), GIS (CityGML/3D Tiles), 3D (glTF 2.0, USD, FBX), IoT (SensorThings API).
   - **Cloud Rendering**: render en edge, 5G, calidad adaptativa.
3. **Clientes**: librerías open source para XR headsets, móvil (iOS/Android), navegadores y robótica.

### Estándares núcleo

- **GeoPose Protocol**: 6DOF, **WGS84**, orientación por **cuaternión**, timestamps. (Grupo de estandarización en **OGC**: ogc.org/projects/groups/geoposeswg — la especificación viva está en OGC; OARC mantiene la implementación de referencia.)
- **SCD Protocol**: consultas por bounding box, filtrado por tipo, privacidad, registro descentralizado.
- **SpatialDDS** (spatialdds.org, familia de repos actualizada a diario): capa de **interop/comunicación** entre clientes y servicios (publicación de poses vía RabbitMQ, etc.) — protocolo nuevo (2026), en evolución.

### Filosofía

Edge-first (servicios locales, privacidad, latencia baja), open source total, interoperabilidad por diseño con versionado y compatibilidad hacia atrás.

## 3. Inventario completo — 42 repositorios

### 3.1 Core Services (5)

| Repo | Descripción | Lenguaje |
|---|---|---|
| `oscp-spatial-service-discovery` | SSD — registro raíz de servicios espaciales | TS · MIT |
| `oscp-spatial-content-discovery` | SCD — descubrimiento de contenido anclado | TS · MIT |
| `oscp-geopose-protocol` | Implementación de referencia del GeoPose Protocol | Python |
| `oscp-open-geopose-service` | Servicio GeoPose para OSCP | — |
| `oscp-poi-service` | Servicio de Points of Interest | JS |

### 3.2 Client Libraries & Access (6)

| Repo | Descripción | Lenguaje |
|---|---|---|
| `gpp-access` | Acceso a GeoPose Protocol — **validadores cliente + json-schemas** (clave para validar payloads) | TS · MIT |
| `ssd-access` | Cliente de Spatial Service Discovery | TS · MIT |
| `scd-access` | Cliente de Spatial Content Discovery | TS · MIT |
| `oscp-unity-client` | Cliente Unity (origen Augmented City, 2021) | C# |
| `browser_userclient` | Cliente navegador para discovery (Svelte) | Svelte · MIT |
| `sparcl` | **spARcl** — cliente WebXR común de AR Cloud (viewer de referencia) | JS · MIT |

### 3.3 Tools & Utilities (5 + deploy)

| Repo | Descripción | Lenguaje |
|---|---|---|
| `oscp-admin` | GUI de administración SSD/SCD (Auth0/Google) | Svelte · MIT |
| `openvps` | **Open Visual Positioning System** (origen Nokia) — posicionamiento visual sin API key de Google | Jupyter · MIT |
| `openvps-deploy` | Despliegue de openvps | Python · MIT |
| `Hierarchical-Localization` | Fork de **hloc** — localización visual (base de openvps) | Python · Apache-2.0 |
| `cesium-viewer` | Visor Cesium de contenido espacial | JS |
| `h3net` | Indexación geoespacial hexagonal (H3 en C#) | C# · Apache-2.0 |

### 3.4 Reference Implementations (4)

| Repo | Descripción |
|---|---|
| `SSD-FastAPI-Tile38` | SSD con FastAPI + Tile38 (Python · MIT) |
| `SCD-FastAPI-Tile38` | SCD con FastAPI + Tile38 (Python · MIT) |
| `oscp-geopose-protocol` | (ya listado en Core — doble rol: protocolo + ref impl) |
| `SolARFramework` | Framework C++ de estimación de pose de cámara (Apache-2.0) |

### 3.5 SpatialDDS family (5) — actualizada OCT 2026

| Repo | Descripción |
|---|---|
| `SpatialDDS-spec` | Especificación del protocolo (Python, «A Protocol for Real-World Spatial Computing») |
| `SpatialDDS-demo` | Demos de referencia (JS · MIT) |
| `spatialdds-ros` | Integración ROS (Python · MIT) |
| `spatialdds-web` | Variante web |
| `spatialdds-scenescape` | Escenarios (Python · MIT) |

### 3.6 Specs & Documentation (5)

| Repo | Descripción | Licencia |
|---|---|---|
| `OSCP-Docs` | Docs oficiales OSCP (tutorial Ubicomp 2025, sandboxes, guías) | CC0 |
| `GeoPose` | Especificación GeoPose (HTML) | Apache-2.0 |
| `GeoPoseWrappers` | Wrappers de entrada/salida GeoPose para otros sistemas | — |
| `OpenGlossaryForRealWorldSpatialComputing` | Glosario abierto del sector (nace del «State of the AR Cloud Report») | — |

### 3.7 Infra y web (3)

`simplecloudstorage` (cliente S3 para subir GLBs al SCD), `openarcloud.github.io` (web), `OarcBackend` (Sanity.io + Firebase, 2019).

### 3.8 Legado IndoorGML / origen (9) — 2018-2019, congelados

`InViewer`, `InViewer-Desktop`, `InEditor`, `InFactory`, `TM2IN` (mesh→indoor), `GeoposeHandlers`, `PROJ` (fork de proyecciones cartográficas), `OSMapGen`, `public-deprecated` (web antigua), `workshop_client` (archivado).

*(Etapas tempranas: interés por IndoorGML/espacios interiores, después el foco pasó a OSCP/GeoPose/SpatialDDS.)*

## 4. Servicios sandbox VIVOS (gratuitos, de referencia)

| Servicio | URL | GUI |
|---|---|---|
| Spatial Service Discovery | `https://ssd.orbit-lab.org/` | Swagger |
| Spatial Content Discovery | `https://scd.orbit-lab.org/` | Swagger |
| Discovery Admin | `https://oscp-admin.orbit-lab.org/` | Auth0 + Google |
| Cliente WebXR | `https://sparcl.orbit-lab.org/` | Auth0 + Google |

- **Contenido en SCD**: solo **GLB**, subidos a hosting público (el propio `simplecloudstorage` o GitHub).
- **No es un servicio comercial**: OSCP es material de referencia de una comunidad descentralizada.
- Tutorial completo: `OSCP-Docs/2025_Ubicomp_tutorial/`.

## 5. Hallazgo — MCP y skills: **cero en el mundo**

- Búsqueda en los 42 repos con `mcp`: **0 resultados**. Búsqueda web OSCP/GeoPose/MCP: solo ruido con la certificación OSCP de seguridad (¡colisión de siglas!).
- Ecosistema MCP geoespacial alrededor pero **genérico GIS/maps**: registry `geo-mcp-servers` (~79 servidores), `gis-mcp`, Mapbox MCP — **nadie habla OSCP/GeoPose/SCD**.
- **Oportunidad**: las fases de este proyecto (mapa → skill → MCP) serían el **primer MCP y la primera skill de OSCP del mundo**.

## 6. Pendientes de este tema

- [ ] Completar páginas web de OARC no leídas aún: `/testbeds/`, `/projects/`, `/resources/`, `/initiatives/openvps/`.
- [ ] Fase 2: skill `oscp-geopose` (patrón de skills actuales).
- [ ] Fase 3: `mcp-oscp` (validar GeoPose con json-schemas de `gpp-access`; consultar sandbox SSD/SCD; POIs).
- [ ] Leer `gpp-access` (schemas) y `OSCP-Docs/2025_Ubicomp_tutorial` en profundidad.

## 7. Fuentes

- `github.com/OpenArCloud` (42 repos, páginas 1-2, captura 2026-10-22)
- `openarcloud.org/` · `openarcloud.org/platform/` · `openarcloud.org/github/`
- `github.com/OpenArCloud/OSCP-Docs` (README + sandbox links)
- `github.com/OpenArCloud/sparcl` (README: env, RabbitMQ topics, papers UbiComp'25/MetaCom'24/ISMAR'21)
- `openarcloud.org` → GeoPose en OGC: `ogc.org/projects/groups/geoposeswg`
