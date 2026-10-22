#!/usr/bin/env node
/**
 * MCP server para Spheroid Universe (app ExposicionCCD).
 *
 * Trabaja en dos modos:
 *  - "local": lee/escribe la copia del proyecto en xr-su/exposicion-ccd (siempre disponible).
 *  - "nube":  habla con la API de Spheroid (file/content/get → save → publish).
 *             Se activa cuando están las variables SPHEROID_API_BASE y SPHEROID_TOKEN.
 *             Mientras falte configuración, las herramientas de nube devuelven una
 *             lista exacta de lo que falta en vez de fallar a ciegas.
 *
 * Endpoints y formas de la API verificados en vivo (incluida la subida
 * binaria: api/file/upload con campos "files" + "metadata"): ver README.md.
 */
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { readFile, writeFile, mkdir, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const APP_ID_DEFECTO = "a8c6da8a-abd9-4cfe-84a3-819d20000603";

const cfg = {
  apiBase: (process.env.SPHEROID_API_BASE || "").replace(/\/+$/, ""),
  token: process.env.SPHEROID_TOKEN || "",
  authHeader: process.env.SPHEROID_AUTH_HEADER || "Authorization",
  // "" = valor crudo sin esquema; "Bearer" por defecto.
  authScheme: process.env.SPHEROID_AUTH_SCHEME ?? "Bearer",
  appId: process.env.SPHEROID_APP_ID || APP_ID_DEFECTO,
  endpoints: {
    get: process.env.SPHEROID_ENDPOINT_GET || "api/file/content/get",
    save: process.env.SPHEROID_ENDPOINT_SAVE || "api/file/content/save",
    create: process.env.SPHEROID_ENDPOINT_CREATE || "api/file/create",
    del: process.env.SPHEROID_ENDPOINT_DELETE || "api/file/delete",
    upload: process.env.SPHEROID_ENDPOINT_UPLOAD || "api/file/upload",
    directory: process.env.SPHEROID_ENDPOINT_DIRECTORY || "api/directory/list",
    details: process.env.SPHEROID_ENDPOINT_DETAILS || "api/app/details",
    startup: process.env.SPHEROID_ENDPOINT_STARTUP || "api/publish/startup",
    publish: process.env.SPHEROID_ENDPOINT_PUBLISH || "api/publish/publish",
  },
  localProject:
    process.env.SPHEROID_LOCAL_PROJECT ||
    path.resolve(__dirname, "..", "..", "exposicion-ccd"),
};

// IDs: los del entorno + los creados en sesiones previas (file-ids.json gana).
const RUTA_IDS_LOCAL = path.resolve(__dirname, "..", "file-ids.json");

function leerIdsEnv() {
  try {
    return JSON.parse(process.env.SPHEROID_FILE_IDS || "{}");
  } catch {
    return {};
  }
}
let idsLocal = {};
try {
  idsLocal = JSON.parse(await readFile(RUTA_IDS_LOCAL, "utf8"));
} catch {
  idsLocal = {};
}
const fileIds = { ...leerIdsEnv(), ...idsLocal };

// Vocabulario oficial del lenguaje (295 términos recuperados del binario del
// runtime oficial) para las herramientas search_language / get_term.
const RUTA_REGISTRO = path.resolve(__dirname, "..", "registry.json");
let registroLenguaje = { terms: [] };
try {
  registroLenguaje = JSON.parse(await readFile(RUTA_REGISTRO, "utf8"));
} catch (e) {
  console.error(
    `Aviso: no pude leer ${RUTA_REGISTRO} (${e.message ?? e}); search_language/get_term quedarán vacías.`,
  );
}

/** Persiste un id nuevo (p. ej. archivo recién creado) para próximas sesiones. */
async function guardarIdLocal(pathArchivo, id) {
  fileIds[pathArchivo] = id;
  idsLocal[pathArchivo] = id;
  try {
    await writeFile(RUTA_IDS_LOCAL, JSON.stringify(idsLocal, null, 2) + "\n", "utf8");
  } catch (e) {
    console.error(`No pude guardar ${RUTA_IDS_LOCAL}: ${e.message ?? e}`);
  }
}

/** Pendientes para poder trabajar contra la nube. */
function pendientesNube() {
  const faltan = [];
  if (!cfg.apiBase) faltan.push("SPHEROID_API_BASE (URL base de la API de Spheroid)");
  if (!cfg.token) faltan.push("SPHEROID_TOKEN (token de acceso de Spheroid)");
  // Los ids ya no bloquean: save_file crea el archivo si no existe y persiste
  // el id en file-ids.json; read_file cae a la copia local avisando.
  return faltan;
}

function faltaId(pathArchivo) {
  return !fileIds[pathArchivo];
}

function cabeceras(conTipoJson = true) {
  const h = {};
  if (conTipoJson) h["Content-Type"] = "application/json";
  // La API de Spheroid exige el Origin de Demiurge (401 sin él en varios
  // endpoints); verificado que fetch/undici lo reenvía sin problemas.
  h["Origin"] = "https://demiurge.spheroid.io";
  if (cfg.token) {
    h[cfg.authHeader] = cfg.authScheme ? `${cfg.authScheme} ${cfg.token}` : cfg.token;
  }
  return h;
}

/** Llamada de nube. Devuelve texto legible (éxito o diagnóstico). */
async function llamarNube(endpoint, cuerpo) {
  const url = `${cfg.apiBase}/${endpoint}`;
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: cabeceras(),
      body: JSON.stringify(cuerpo),
    });
    const texto = await res.text();
    let dato = null;
    let detalle = texto;
    try {
      dato = JSON.parse(texto);
      detalle = JSON.stringify(dato, null, 2);
    } catch {
      /* se devuelve tal cual */
    }
    return {
      ok: res.ok,
      dato,
      texto: `POST ${url} → HTTP ${res.status}\n\n${detalle}`,
    };
  } catch (e) {
    return { ok: false, dato: null, texto: `POST ${url} → error de red: ${e.message ?? e}` };
  }
}

/** GET de nube (p. ej. api/app/details). Igual que llamarNube pero con query. */
async function llamarNubeGet(endpoint, params) {
  const url = `${cfg.apiBase}/${endpoint}?${new URLSearchParams(params)}`;
  try {
    const res = await fetch(url, { method: "GET", headers: cabeceras(false) });
    const t = await res.text();
    let dato = null;
    let detalle = t;
    try {
      dato = JSON.parse(t);
      detalle = JSON.stringify(dato, null, 2);
    } catch {
      /* se devuelve tal cual */
    }
    return { ok: res.ok, dato, texto: `GET ${url} → HTTP ${res.status}\n\n${detalle}` };
  } catch (e) {
    return { ok: false, dato: null, texto: `GET ${url} → error de red: ${e.message ?? e}` };
  }
}

/**
 * Subida binaria multipart (api/file/upload).
 * Forma real de Demiurge (extraída del bundle): campo "files" con el archivo
 * y "metadata" con JSON {appId, directoryId}. Sin Content-Type manual
 * (fetch pone el boundary del multipart).
 */
async function subirNube(fileBytes, fileName, directoryId = null) {
  const fd = new FormData();
  fd.append("files", new Blob([fileBytes]), fileName);
  fd.append("metadata", JSON.stringify({ appId: cfg.appId, directoryId }));
  try {
    const res = await fetch(`${cfg.apiBase}/${cfg.endpoints.upload}`, {
      method: "POST",
      headers: cabeceras(false),
      body: fd,
    });
    const t = await res.text();
    let dato = null;
    let detalle = t;
    try {
      dato = JSON.parse(t);
      detalle = JSON.stringify(dato, null, 2);
    } catch {
      /* se devuelve tal cual */
    }
    return { ok: res.ok, dato, texto: `POST ${cfg.endpoints.upload} → HTTP ${res.status}\n\n${detalle}` };
  } catch (e) {
    return { ok: false, dato: null, texto: `POST ${cfg.endpoints.upload} → error de red: ${e.message ?? e}` };
  }
}

/** Ruta local segura dentro del proyecto (sin escaparse con ..). */
function rutaLocalSegura(pathArchivo) {
  const absoluto = path.resolve(cfg.localProject, pathArchivo);
  const rel = path.relative(cfg.localProject, absoluto);
  if (rel.startsWith("..") || path.isAbsolute(rel)) {
    throw new Error(`La ruta "${pathArchivo}" sale de ${cfg.localProject}`);
  }
  return absoluto;
}

function texto(t, esError = false) {
  return { content: [{ type: "text", text: t }], isError: esError };
}

/**
 * Lee la fecha de vencimiento (exp) del JWT decodificando su payload.
 * Solo lectura local: el token nunca se imprime completo.
 */
function expiracionToken(token) {
  try {
    const parte = token.split(".")[1];
    const json = JSON.parse(Buffer.from(parte, "base64url").toString("utf8"));
    if (typeof json.exp !== "number") return null;
    const fecha = new Date(json.exp * 1000);
    const dias = Math.ceil((fecha.getTime() - Date.now()) / 86400000);
    return { fecha, dias };
  } catch {
    return null;
  }
}

const server = new McpServer({ name: "spheroid", version: "0.1.0" });

server.registerTool(
  "status",
  {
    title: "Estado de la conexión con Spheroid",
    description:
      "Diagnóstico: qué configuración falta para leer/guardar/publicar en la nube de Spheroid y qué hay disponible en local.",
    inputSchema: {},
  },
  async () => {
    const faltan = pendientesNube();
    const nubeListo = faltan.length === 0;
    let localOk = false;
    try {
      await stat(cfg.localProject);
      localOk = true;
    } catch {
      localOk = false;
    }
    let tokenLinea = `  Token:     ${cfg.token ? "sí" : "no"}`;
    if (cfg.token) {
      const exp = expiracionToken(cfg.token);
      if (exp) {
        const iso = exp.fecha.toISOString().slice(0, 10);
        if (exp.dias <= 0) {
          tokenLinea += ` · VENCIDO el ${iso} — re-extraer de Demiurge (ver README)`;
        } else if (exp.dias <= 7) {
          tokenLinea += ` · vence el ${iso} (en ${exp.dias} día${exp.dias === 1 ? "" : "s"}) — pronto habrá que re-extraerlo`;
        } else {
          tokenLinea += ` · vence el ${iso} (en ${exp.dias} días)`;
        }
      }
    }
    const lineas = [
      "=== Spheroid MCP ===",
      `Proyecto local (${cfg.localProject}): ${localOk ? "OK" : "NO ENCONTRADO"}`,
      `Nube: ${nubeListo ? "CONFIGURADA" : "INCOMPLETA"}`,
      `  API base:  ${cfg.apiBase ? "sí" : "no"}`,
      tokenLinea,
      `  App ID:    ${cfg.appId}`,
      `  IDs de archivo: ${Object.keys(fileIds).length} mapeado(s) · locales: ${Object.keys(idsLocal).length} en file-ids.json`,
      `  Endpoints: get · save · create · delete · upload · directory · details · startup · publish (defaults verificados)`,
    ];
    if (faltan.length) {
      lineas.push("", "Para activar la nube falta:");
      for (const f of faltan) lineas.push(`  - ${f}`);
      lineas.push(
        "",
        "Nota: los IDs y la URL base se recuperan del panel de Spheroid al reanudar (ver README).",
      );
    }
    return texto(lineas.join("\n"));
  },
);

server.registerTool(
  "list_files",
  {
    title: "Listar archivos del proyecto",
    description:
      "Lista los archivos del proyecto ExposicionCCD. origen='nube' (por defecto) usa api/directory/list (fuente de verdad); origen='local' barre la copia del proyecto.",
    inputSchema: {
      origen: z
        .enum(["nube", "local"])
        .default("nube")
        .describe("nube = api/directory/list; local = copia del proyecto"),
    },
  },
  async ({ origen }) => {
    if (origen !== "local") {
      const faltan = pendientesNube();
      if (faltan.length > 0) {
        return texto("Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"), true);
      }
      const r = await llamarNube(cfg.endpoints.directory, { appId: cfg.appId });
      if (!r.ok || !Array.isArray(r.dato?.items)) return texto(r.texto, true);
      const items = r.dato.items;
      const lineas = items.map((i) => `${i.name}  (${i.size} B, fileType ${i.fileType}, ${i.id})`);
      return texto(`[nube] ${items.length} archivo(s)\n\n${lineas.join("\n")}`);
    }
    try {
      const { readdir } = await import("node:fs/promises");
      const salida = [];
      async function barre(dir, prefijo = "") {
        const entradas = await readdir(dir, { withFileTypes: true });
        for (const e of entradas) {
          const rel = prefijo ? `${prefijo}/${e.name}` : e.name;
          if (e.isDirectory()) await barre(path.join(dir, e.name), rel);
          else salida.push(rel);
        }
      }
      await barre(cfg.localProject);
      return texto(`${cfg.localProject}\n\n${salida.join("\n")}`);
    } catch (e) {
      return texto(`No pude listar ${cfg.localProject}: ${e.message ?? e}`, true);
    }
  },
);

server.registerTool(
  "read_file",
  {
    title: "Leer un archivo del proyecto",
    description:
      "Lee un archivo .spheroid/app.json/assets. Si la nube está configurada y el archivo tiene ID, lee de la nube (fuente de verdad); si no, lee la copia local marcándola como posiblemente desfasada.",
    inputSchema: {
      path: z
        .string()
        .describe('Ruta relativa del archivo, p. ej. "server/Stations.spheroid"'),
    },
  },
  async ({ path: pathArchivo }) => {
    const faltan = pendientesNube();
    if (faltan.length === 0 && !faltaId(pathArchivo)) {
      const r = await llamarNube(cfg.endpoints.get, {
        appId: cfg.appId,
        fileId: fileIds[pathArchivo],
      });
      return texto(`[nube] ${pathArchivo}\n${r.texto}`, !r.ok);
    }
    try {
      const abs = rutaLocalSegura(pathArchivo);
      const contenido = await readFile(abs, "utf8");
      const aviso =
        faltan.length > 0 || faltaId(pathArchivo)
          ? "\n\n⚠️ [local] Copia local — puede estar desfasada de la nube."
          : "";
      return texto(`[local] ${pathArchivo}\n\n${contenido}${aviso}`);
    } catch (e) {
      return texto(`No pude leer "${pathArchivo}": ${e.message ?? e}`, true);
    }
  },
);

server.registerTool(
  "save_file",
  {
    title: "Guardar un archivo",
    description:
      "Guarda el contenido completo de un archivo. target='cloud' (por defecto) usa la API de Spheroid y exige configuración completa; target='local' escribe en la copia del proyecto. Recuerda: publicar en la nube requiere aprobación explícita del usuario.",
    inputSchema: {
      path: z.string().describe('Ruta relativa, p. ej. "server/Stations.spheroid"'),
      content: z.string().describe("Contenido completo nuevo del archivo"),
      target: z
        .enum(["cloud", "local"])
        .default("cloud")
        .describe("cloud = API de Spheroid; local = copia del proyecto"),
    },
  },
  async ({ path: pathArchivo, content, target }) => {
    if (target === "local") {
      try {
        const abs = rutaLocalSegura(pathArchivo);
        await mkdir(path.dirname(abs), { recursive: true });
        await writeFile(abs, content, "utf8");
        return texto(`[local] Guardado ${pathArchivo} (${content.length} bytes).`);
      } catch (e) {
        return texto(`No pude guardar localmente: ${e.message ?? e}`, true);
      }
    }
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto(
        "No puedo guardar en la nube todavía. Falta configuración:\n" +
          faltan.map((f) => `  - ${f}`).join("\n") +
          "\n\nMientras tanto: target='local' guarda en la copia del proyecto.",
        true,
      );
      }
    let fileId = fileIds[pathArchivo];
    if (!fileId) {
      // Archivo nuevo: se crea en la nube (árbol plano, nombre solo) y el id
      // se persiste en file-ids.json para las próximas sesiones.
      const fileName = path.basename(pathArchivo);
      const rc = await llamarNube(cfg.endpoints.create, { appId: cfg.appId, fileName });
      const nuevoId = rc.dato?.file?.id;
      if (!rc.ok || rc.dato?.success === false || !nuevoId) {
        return texto(`No pude crear "${fileName}" en la nube (${pathArchivo}):\n${rc.texto}`, true);
      }
      await guardarIdLocal(pathArchivo, nuevoId);
      fileId = nuevoId;
    }
    const r = await llamarNube(cfg.endpoints.save, {
      appId: cfg.appId,
      fileId,
      content,
    });
    return texto(
      `${r.texto}\n\n(recordar: el cambio NO está publicado hasta hacer publish, con aprobación del usuario)`,
      !r.ok,
    );
  },
);

const LAYER_DEFECTO = "5917fe68-57fb-497d-ac35-1ce4412a6aca";

server.registerTool(
  "publish",
  {
    title: "Publicar la app (build)",
    description:
      "Publica (build) la app ExposicionCCD en Spheroid en 2 fases (publish/startup → publish/publish). HERRAMIENTA DESTRUCTIVA/EN PRODUCCIÓN: solo ejecutar tras aprobación explícita del usuario.",
    inputSchema: {
      layerIds: z
        .array(z.string())
        .default([LAYER_DEFECTO])
        .describe("Capas a publicar (por defecto la capa ExposicionCCD)"),
      isDebug: z.boolean().default(true).describe("Build de depuración"),
      client: z.boolean().default(true).describe("Publicar cliente"),
      server: z.boolean().default(true).describe("Publicar servidor"),
    },
  },
  async ({ layerIds, isDebug, client, server: publicarServidor }) => {
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto(
        "Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"),
        true,
      );
    }
    const s1 = await llamarNube(cfg.endpoints.startup, { appId: cfg.appId });
    if (!s1.ok) return texto(`FASE 1 (publish/startup) falló:\n${s1.texto}`, true);
    const s2 = await llamarNube(cfg.endpoints.publish, {
      appId: cfg.appId,
      publishClient: client,
      publishServer: publicarServidor,
      isDebug,
      layerIds,
    });
    return texto(
      `FASE 1 — publish/startup:\n${s1.texto}\n\nFASE 2 — publish/publish:\n${s2.texto}`,
      !s2.ok,
    );
  },
);

server.registerTool(
  "list_builds",
  {
    title: "Listar builds de la app",
    description:
      "Devuelve los builds publicados leyendo GET api/app/details → publications (el historial de builds no tiene endpoint propio).",
    inputSchema: {},
  },
  async () => {
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto("Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"), true);
    }
    const r = await llamarNubeGet(cfg.endpoints.details, { appId: cfg.appId });
    if (!r.ok) return texto(r.texto, true);
    const pubs = r.dato?.app?.publications ?? r.dato?.publications;
    if (!Array.isArray(pubs)) return texto(r.texto, true);
    const lineas = pubs.map((p, i) => {
      const build = p?.appVersion?.build ?? "?";
      const extra = p?.appVersion ? JSON.stringify(p.appVersion) : "";
      return `#${i + 1} build ${build}  ${extra}`;
    });
    return texto(
      `Publicaciones: ${pubs.length} (la última es la vigente)\n\n${lineas.join("\n")}`,
      false,
    );
  },
);

server.registerTool(
  "upload_file",
  {
    title: "Subir un archivo binario",
    description:
      "Sube un archivo binario (p. ej. assets/sombra_sol.glb) a la nube con api/file/upload (multipart: files + metadata). El archivo debe estar en la copia local del proyecto. Subir a la nube requiere aprobación del usuario.",
    inputSchema: {
      path: z.string().describe('Ruta local relativa, p. ej. "assets/sombra_sol.glb"'),
      fileName: z
        .string()
        .optional()
        .describe("Nombre en la nube (árbol plano); por defecto el nombre del archivo local"),
    },
  },
  async ({ path: pathArchivo, fileName }) => {
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto("Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"), true);
    }
    let bytes;
    try {
      bytes = await readFile(rutaLocalSegura(pathArchivo));
    } catch (e) {
      return texto(`No pude leer "${pathArchivo}": ${e.message ?? e}`, true);
    }
    const nombre = fileName || path.basename(pathArchivo);
    const r = await subirNube(bytes, nombre, null);
    const f = r.dato?.uploadedFiles?.[0];
    if (r.ok && f) {
      await guardarIdLocal(pathArchivo, f.id);
      return texto(
        `${r.texto}\n\nSubido: ${f.name} → id ${f.id} (${f.size} B, fileType ${f.fileType}).\n` +
          `id guardado en file-ids.json (read_file/save_file lo verán).\n` +
          `(recordar: NO está publicado hasta hacer publish, con aprobación)`,
        false,
      );
    }
    return texto(r.texto, true);
  },
);

server.registerTool(
  "delete_file",
  {
    title: "Borrar un archivo de la nube",
    description:
      "Borra un archivo de la nube (api/file/delete). HERRAMIENTA DESTRUCTIVA: solo con aprobación explícita del usuario.",
    inputSchema: {
      path: z
        .string()
        .optional()
        .describe('Ruta local del archivo (p. ej. "assets/x.glb"); usa su id de file-ids.json'),
      fileId: z.string().optional().describe("id directo (si no se pasa path)"),
    },
  },
  async ({ path: pathArchivo, fileId }) => {
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto("Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"), true);
    }
    const id = fileId || (pathArchivo ? fileIds[pathArchivo] : null);
    if (!id) {
      return texto(
        "Falta fileId (o un path con id conocido en file-ids.json / SPHEROID_FILE_IDS).",
        true,
      );
    }
    const r = await llamarNube(cfg.endpoints.del, { appId: cfg.appId, fileId: id });
    const okInterno = r.dato?.success === true;
    return texto(r.texto, !r.ok || !okInterno);
  },
);

// ---------------------------------------------------------------------------
// Vocabulario del lenguaje (registry.json: 295 términos recuperados del
// binario del runtime oficial; antes vivían en un wrapper externo).

server.registerTool(
  "search_language",
  {
    title: "Buscar en el vocabulario oficial Spheroid Script",
    description:
      "Busca tipos, objetos globales, funciones, propiedades, eventos, componentes UI, animaciones y queries en el vocabulario de Spheroid Script (295 términos) recuperado del binario del runtime oficial (PixelVoxel).",
    inputSchema: {
      query: z
        .string()
        .describe("Subcadena del nombre (insensible a mayúsculas). Vacío = todos."),
      category: z
        .enum([
          "primitive",
          "geometry",
          "ui",
          "ar_query",
          "global",
          "function",
          "method",
          "property",
          "event",
          "style",
        ])
        .optional()
        .describe("Filtrar por categoría."),
    },
  },
  async ({ query, category }) => {
    const q = query.toLowerCase();
    let terms = registroLenguaje.terms || [];
    if (category) terms = terms.filter((t) => t.category === category);
    if (q) terms = terms.filter((t) => t.name.toLowerCase().includes(q));
    const txt =
      `Encontrados ${terms.length} términos:\n` +
      terms.map((t) => `${t.name}  (id ${t.id}, ${t.category})`).join("\n");
    return texto(txt);
  },
);

server.registerTool(
  "get_term",
  {
    title: "Detalle de un término del lenguaje",
    description:
      "Devuelve id, categoría y familia semántica de un término exacto de Spheroid Script.",
    inputSchema: { name: z.string().describe("Nombre exacto, p. ej. 'Timer', 'println'.") },
  },
  async ({ name }) => {
    const t = (registroLenguaje.terms || []).find((o) => o.name === name);
    if (!t) {
      const cerca = (registroLenguaje.terms || [])
        .filter((o) => o.name.toLowerCase().includes(name.toLowerCase()))
        .slice(0, 8)
        .map((o) => o.name);
      return texto(`No existe '${name}'. ¿Quisiste decir?: ${cerca.join(", ") || "(nada)"}`);
    }
    return texto(JSON.stringify({ id: t.id, name: t.name, category: t.category }, null, 1));
  },
);

server.registerTool(
  "api_call",
  {
    title: "Llamada genérica a la API de Spheroid",
    description:
      "Ejecuta una petición HTTP contra la API de plataforma de Spheroid (con el Origin de Demiurge). Para endpoints no cubiertos por las herramientas del servidor, p. ej. api/app/list, api/directory/list, api/app/details. Body en JSON como objeto. El endpoint debe ser RELATIVO (p. ej. 'api/app/list'): se rechazan URLs absolutas para no enviar el token fuera de la base.",
    inputSchema: {
      method: z.enum(["GET", "POST"]).describe("Método HTTP."),
      endpoint: z.string().describe("Ruta relativa, p. ej. 'api/app/list'."),
      body: z
        .record(z.any())
        .optional()
        .describe("Cuerpo JSON (para POST; también parámetros en GET)."),
    },
  },
  async ({ method, endpoint, body }) => {
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto("Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"), true);
    }
    // Ruta relativa obligatoria: una URL absoluta reemplazaría el host del
    // `new URL` y el token Bearer saldría hacia otro sitio.
    const relativa = endpoint.trim().replace(/^\/+/, "");
    if (!relativa || /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(relativa)) {
      return texto(
        `Endpoint inválido: usa una ruta relativa como "api/app/list" (se rechazan URLs absolutas para no enviar el token fuera de ${cfg.apiBase}).`,
        true,
      );
    }
    const url = new URL(relativa, cfg.apiBase + "/");
    const init = { method, headers: cabeceras(method === "POST") };
    if (method === "GET" && body) {
      for (const [k, v] of Object.entries(body)) url.searchParams.set(k, String(v));
    }
    if (method === "POST") init.body = JSON.stringify(body || {});
    try {
      const r = await fetch(url, init);
      const t = await r.text();
      return texto(`HTTP ${r.status}\n${t.slice(0, 8000)}`, r.status >= 400);
    } catch (e) {
      return texto(`Error de red: ${e.message ?? e}`, true);
    }
  },
);

const transport = new StdioServerTransport();
await server.connect(transport);
// stdout es del protocolo MCP; los avisos van a stderr.
console.error(`mcp-spheroid listo (local: ${cfg.localProject})`);
