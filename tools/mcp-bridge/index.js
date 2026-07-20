#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { loadEnvFile } from "./src/loadEnv.js";
import { registerTools } from "./src/tools.js";

loadEnvFile();

const server = new McpServer({
  name: "fishtastic-mcp-bridge",
  version: "1.0.0",
});

registerTools(server);

const transport = new StdioServerTransport();
await server.connect(transport);
