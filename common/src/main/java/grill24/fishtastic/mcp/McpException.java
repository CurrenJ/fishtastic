package grill24.fishtastic.mcp;

/**
 * Signals a rejected MCP bridge request (bad input, out-of-region target, disallowed command) -
 * distinguished from unexpected internal errors so handlers can respond with a clean 400 instead of a 500.
 */
public final class McpException extends RuntimeException {
    public McpException(String message) {
        super(message);
    }
}
