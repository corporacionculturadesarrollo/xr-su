// Prueba de humo: conecta por stdio, verifica las 11 herramientas y llama a
// status / read_file / search_language (sin tocar la nube).
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";

const transport = new StdioClientTransport({
  command: "node",
  // fileURLToPath (no pathname): con espacios en la ruta el pathname queda
  // percent-encoded ("%20") y node no encuentra el archivo.
  args: [fileURLToPath(new URL("../src/index.js", import.meta.url))],
  env: { ...process.env },
});

const client = new Client({ name: "smoke", version: "0.0.1" });
await client.connect(transport);

const { tools } = await client.listTools();
console.log(
  "Herramientas:",
  tools.map((t) => t.name).join(", "),
);

const ESPERADAS = [
  "status",
  "list_files",
  "read_file",
  "save_file",
  "publish",
  "list_builds",
  "upload_file",
  "delete_file",
  "search_language",
  "get_term",
  "api_call",
];
const faltan = ESPERADAS.filter((n) => !tools.some((t) => t.name === n));
if (faltan.length > 0) {
  console.error("Faltan herramientas:", faltan.join(", "));
  process.exit(1);
}

const status = await client.callTool({ name: "status", arguments: {} });
console.log("\n--- status ---");
console.log(status.content[0].text);
if (!status.content[0].text.includes("=== Spheroid MCP ===")) {
  console.error("El status no muestra la cabecera esperada");
  process.exit(1);
}
// Con token en el entorno, status debe informar su vencimiento (sin imprimirlo).
if (process.env.SPHEROID_TOKEN && !/vence|VENCIDO/.test(status.content[0].text)) {
  console.error("Hay SPHEROID_TOKEN pero status no informa el vencimiento");
  process.exit(1);
}

// Vocabulario del lenguaje (funciona sin nube).
const busqueda = await client.callTool({ name: "search_language", arguments: { query: "timer" } });
console.log("\n--- search_language('timer') ---");
console.log(busqueda.content[0].text.split("\n").slice(0, 4).join("\n"));
if (busqueda.isError) {
  console.error("search_language devolvió error");
  process.exit(1);
}

// read_file local debe funcionar sin configuración de nube.
const lectura = await client.callTool({
  name: "read_file",
  arguments: { path: "server/Stations.spheroid" },
});
console.log("\n--- read_file (primeras líneas) ---");
console.log(lectura.content[0].text.split("\n").slice(0, 6).join("\n"));

// save_file contra la nube sin configurar debe rechazar con diagnóstico.
const guardado = await client.callTool({
  name: "save_file",
  arguments: { path: "server/Stations.spheroid", content: "x", target: "cloud" },
});
console.log("\n--- save_file cloud (debe rechazar) ---");
console.log("isError:", guardado.isError);
console.log(guardado.content[0].text);

await client.close();
console.log("\nSMOKE OK");
