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
 * El detalle fino de la API (URL base, auth, IDs completos de archivo) no quedó
 * registrado al pausar el proyecto: ver README.md §"Qué falta".
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
    get: process.env.SPHEROID_ENDPOINT_GET || "file/content/get",
    save: process.env.SPHEROID_ENDPOINT_SAVE || "save",
    publish: process.env.SPHEROID_ENDPOINT_PUBLISH || "publish",
    builds: process.env.SPHEROID_ENDPOINT_BUILDS || "",
  },
  localProject:
    process.env.SPHEROID_LOCAL_PROJECT ||
    path.resolve(__dirname, "..", "..", "exposicion-ccd"),
};

let fileIds = {};
try {
  fileIds = JSON.parse(process.env.SPHEROID_FILE_IDS || "{}");
} catch {
  fileIds = {};
}

/** Pendientes para poder trabajar contra la nube. */
function pendientesNube() {
  const faltan = [];
  if (!cfg.apiBase) faltan.push("SPHEROID_API_BASE (URL base de la API de Spheroid)");
  if (!cfg.token) faltan.push("SPHEROID_TOKEN (token de acceso de Spheroid)");
  if (Object.keys(fileIds).length === 0)
    faltan.push("SPHEROID_FILE_IDS (mapa JSON path → id completo de cada archivo)");
  return faltan;
}

function faltaId(pathArchivo) {
  return !fileIds[pathArchivo];
}

function cabeceras() {
  const h = { "Content-Type": "application/json" };
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
    let detalle = texto;
    try {
      detalle = JSON.stringify(JSON.parse(texto), null, 2);
    } catch {
      /* se devuelve tal cual */
    }
    return {
      ok: res.ok,
      texto: `POST ${url} → HTTP ${res.status}\n\n${detalle}`,
    };
  } catch (e) {
    return { ok: false, texto: `POST ${url} → error de red: ${e.message ?? e}` };
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
    const lineas = [
      "=== Spheroid MCP ===",
      `Proyecto local (${cfg.localProject}): ${localOk ? "OK" : "NO ENCONTRADO"}`,
      `Nube: ${nubeListo ? "CONFIGURADA" : "INCOMPLETA"}`,
      `  API base:  ${cfg.apiBase ? "sí" : "no"}`,
      `  Token:     ${cfg.token ? "sí" : "no"}`,
      `  App ID:    ${cfg.appId}`,
      `  IDs de archivo: ${Object.keys(fileIds).length} mapeado(s)`,
      `  Endpoints: get=${cfg.endpoints.get} · save=${cfg.endpoints.save} · publish=${cfg.endpoints.publish} · builds=${cfg.endpoints.builds || "(sin configurar)"}`,
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
    description: "Lista los archivos de la copia local del proyecto ExposicionCCD.",
    inputSchema: {},
  },
  async () => {
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
        id: fileIds[pathArchivo],
        path: pathArchivo,
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
    if (faltaId(pathArchivo)) {
      return texto(
        `Sin ID de nube para "${pathArchivo}". Añádelo a SPHEROID_FILE_IDS (path → id completo).`,
        true,
      );
    }
    const r = await llamarNube(cfg.endpoints.save, {
      appId: cfg.appId,
      id: fileIds[pathArchivo],
      path: pathArchivo,
      content,
    });
    return texto(
      `${r.texto}\n\n(recordar: el cambio NO está publicado hasta hacer publish, con aprobación del usuario)`,
      !r.ok,
    );
  },
);

server.registerTool(
  "publish",
  {
    title: "Publicar la app (build)",
    description:
      "Publica (build) la app ExposicionCCD en Spheroid. HERRAMIENTA DESTRUCTIVA/EN PRODUCCIÓN: solo ejecutar tras aprobación explícita del usuario. Requiere configuración de nube completa.",
    inputSchema: {},
  },
  async () => {
    const faltan = pendientesNube();
    if (faltan.length > 0) {
      return texto(
        "Nube no configurada:\n" + faltan.map((f) => `  - ${f}`).join("\n"),
        true,
      );
    }
    const r = await llamarNube(cfg.endpoints.publish, { appId: cfg.appId });
    return texto(r.texto, !r.ok);
  },
);

server.registerTool(
  "list_builds",
  {
    title: "Listar builds de la app",
    description:
      "Devuelve los builds publicados (1–35 según el histórico). Necesita SPHEROID_ENDPOINT_BUILDS configurado (endpoint no documentado).",
    inputSchema: {},
  },
  async () => {
    const faltan = pendientesNube();
    if (faltan.length > 0 || !cfg.endpoints.builds) {
      return texto(
        [
          ...(faltan.length ? faltan.map((f) => `  - ${f}`) : []),
          ...(cfg.endpoints.builds ? [] : ["SPHEROID_ENDPOINT_BUILDS (sin configurar)"]),
        ].join("\n"),
        true,
      );
    }
    const r = await llamarNube(cfg.endpoints.builds, { appId: cfg.appId });
    return texto(r.texto, !r.ok);
  },
);

const transport = new StdioServerTransport();
await server.connect(transport);
// stdout es del protocolo MCP; los avisos van a stderr.
console.error(`mcp-spheroid listo (local: ${cfg.localProject})`);
