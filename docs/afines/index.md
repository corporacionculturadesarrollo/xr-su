# Tecnologías afines (fuera de openarcloud y de Spheroid AR Cloud OS)

**Tema:** afines — tecnologías del entorno del AR Cloud que **no son** openarcloud ni Spheroid AR Cloud OS.
**Estado:** semilla · **Inicio:** 2026-10-22.

> **Frontera de este tema** (temas diferenciados, sin mezclar):
> - **openarcloud** (OARC, OSCP, SpatialDDS, sandboxes) → `xr-su/docs/openarcloud/mapa-open-ar-cloud.md`
> - **Spheroid AR Cloud OS** → `xr-su/skills/spheroid-xr/SKILL.md` + `xr-su/mcp-spheroid/README.md`
> - Este cajón solo contiene lo demás; si un dato es de OARC o de Spheroid, va a su cajón.

## Semilla inicial (capturada 2026-10-22)

### 1. VPS comerciales (posicionamiento visual de pago)
Referenciados por OSCP-Docs como soluciones compatibles con OSCP — alternativas de pago a Google ARCore Geospatial:
- **Augmented City** (augmented.city) — AC Scanner/Viewer; tuvo el cliente Unity que hoy es `oscp-unity-client`.
- **Immersal** (immersal.com) — escaneo de ciudades, demos city-scale.
- **XRMasters** (xr-masters.com) — apps de ubicación de objetos AR.
- **OpenVPS** (de Nokia) → **no va aquí**: pertenece al cajón openarcloud (`OpenArCloud/openvps`).

### 2. Ecosistema MCP geoespacial (existente, genérico)
- Registry **`geo-mcp-servers`** (Sparkgeo): ~79-89 MCP servers de GIS/mapping/earth observation catalogados.
- `gis-mcp` (mahdin75/gis-mcp) — MCP para asistentes AI + GIS.
- **Mapbox MCP server** (`mapbox/mcp-server`).
- **Ninguno habla OSCP/GeoPose/SCD** (gap confirmado → oportunidad en el cajón openarcloud).

### 3. Pendientes de investigar
- [ ] WebXR y su ecosistema más allá de spARcl (navegadores, specs W3C).
- [ ] OGC GeoPose Standardization Working Group — estado actual del estándar (la spec vive en OGC, no en OARC).
- [ ] Niantic (Lightship VPS / 8th Wall) y metaversos georreferenciados comerciales.
- [ ] Comparativa honesta Spheroid AR Cloud OS vs OSCP (como documento **aparte**, en esta carpeta, sin fusionar fuentes).
