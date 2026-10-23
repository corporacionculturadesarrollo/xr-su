# Mapa y documentación de Open AR Cloud (OARC)

**Estado:** abierto · **Inicio:** 2026-10-22 · **Última:** 2026-10-22

## 19:30 — Investigación de MCP/skills de OSCP y veredicto

**Qué se hizo** — Búsqueda exhaustiva: 0 repos «mcp» en los 42 de
github.com/OpenArCloud; búsqueda web OSCP/GeoPose/MCP sin resultados (ruido
dominado por la certificación OSCP de seguridad — colisión de siglas). Ecosistema
MCP geoespacial alrededor existe pero es GIS/mapas genéricos (registry
`geo-mcp-servers` ~89 servidores, `gis-mcp`, Mapbox MCP).

**Decisiones**
- Conclusión: **no existe ni un MCP ni una skill de OSCP en el mundo** → las
  fases siguientes (skill + MCP) serían las primeras. Documentado como
  oportunidad, no como tarea inmediata.

## 20:40 — Mapa maestro «todo open ar cloud» + separación de temas

**Qué se hizo** — Dos documentos nuevos (commit `66fc792`):
`docs/openarcloud/mapa-open-ar-cloud.md` (v1 completa: 42/42 repos en 8 familias,
arquitectura OSCP de 3 tiers, estándares GeoPose/SCD/SpatialDDS, 4 sandboxes
vivos en orbit-lab.org, licencias) y `docs/afines/index.md` (semilla: VPS
comerciales, MCPs geoespaciales GIS, pendientes OGC/Niantic).

**Decisiones**
- **Tres temas diferenciados con fronteras explícitas** (openarcloud /
  Spheroid AR Cloud OS / afines) — petición explícita del usuario: cada doc
  empieza indicando a qué cajón va lo que no es suyo.
- Spheroid **no** se duplica en `docs/`: ya vive en `skills/spheroid-xr/` +
  `mcp-spheroid/README.md`; solo se referencia.
- VPS comerciales (Immersal/Augmented City/XRMasters) en `afines/`, **no** en el
  mapa OARC, aunque OSCP-Docs los liste como compatibles: son de otro cajón.
- Hallazgo de la spec GeoPose en **OGC** (geoposeswg), no en OARC: la
  implementación de referencia es de OARC, el estándar vive en OGC.

**Pendientes**
- [ ] Páginas OARC sin leer: `/testbeds/`, `/projects/`, `/resources/`,
      `/initiatives/openvps/` — falta de tiempo, no bloqueante para v1.
- [ ] Fase 2: skill `oscp-geopose` (patrón de skills actuales).
- [ ] Fase 3: `mcp-oscp` (validar GeoPose con json-schemas de `gpp-access`;
      sandbox SSD/SCD; POIs) — sería el primero del mundo.
- [ ] Leer `gpp-access` (schemas) y `OSCP-Docs/2025_Ubicomp_tutorial`.

**Verificación**
```bash
grep -l "" xr-su/docs/openarcloud/*.md xr-su/docs/afines/*.md
git -C xr-su log --oneline -2   # 66fc792 docs(oscp) presente
```
