package grill24.fishtastic.mcp;

import com.sun.net.httpserver.HttpHandler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Wraps every MCP bridge HTTP context with a shared-secret check - the bridge binds loopback-only, but
 * the token still guards against other local processes/browser JS reaching the port.
 */
public final class McpTokenAuth {
    private static final String HEADER = "X-Fishtastic-Mcp-Token";

    private McpTokenAuth() {}

    public static HttpHandler wrap(HttpHandler delegate) {
        return exchange -> {
            String expected = McpBridgeState.getToken();
            String provided = exchange.getRequestHeaders().getFirst(HEADER);
            if (expected == null || provided == null || !constantTimeEquals(expected, provided)) {
                McpBridgeServer.sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            delegate.handle(exchange);
        };
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
