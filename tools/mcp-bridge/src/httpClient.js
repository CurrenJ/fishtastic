const DEFAULT_PORT = 25599;

function baseUrl() {
  const port = process.env.FISHTASTIC_MCP_PORT || DEFAULT_PORT;
  return `http://127.0.0.1:${port}`;
}

function token() {
  const value = process.env.FISHTASTIC_MCP_TOKEN;
  if (!value) {
    throw new Error(
      "FISHTASTIC_MCP_TOKEN is not set. Run /fishtastic mcp start in-game, copy the printed token " +
        "(and port, if not 25599) into tools/mcp-bridge/.env, then reconnect this MCP server (/mcp in Claude Code)."
    );
  }
  return value;
}

export async function callBridge(path, body) {
  const response = await fetch(`${baseUrl()}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Fishtastic-Mcp-Token": token(),
    },
    body: JSON.stringify(body ?? {}),
  });

  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { raw: text };
  }

  if (!response.ok) {
    const message = data && data.error ? data.error : `HTTP ${response.status}`;
    throw new Error(message);
  }

  return data;
}
