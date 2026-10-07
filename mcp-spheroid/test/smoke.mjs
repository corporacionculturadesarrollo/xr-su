// Prueba de humo: conecta por stdio, lista herramientas y llama a status.
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const transport = new StdioClientTransport({
  command: "node",
  args: [new URL("../src/index.js", import.meta.url).pathname.replace(/^\/(\w):/, "$1:")],
  env: { ...process.env },
});

const client = new Client({ name: "smoke", version: "0.0.1" });
await client.connect(transport);

const { tools } = await client.listTools();
console.log(
  "Herramientas:",
  tools.map((t) => t.name).join(", "),
);

const status = await client.callTool({ name: "status", arguments: {} });
console.log("\n--- status ---");
console.log(status.content[0].text);

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
