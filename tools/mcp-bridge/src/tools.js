import { z } from "zod";
import { callBridge } from "./httpClient.js";

function textResult(value) {
  return {
    content: [{ type: "text", text: typeof value === "string" ? value : JSON.stringify(value, null, 2) }],
  };
}

function errorResult(error) {
  return { content: [{ type: "text", text: `Error: ${error.message}` }], isError: true };
}

async function run(fn) {
  try {
    return textResult(await fn());
  } catch (error) {
    return errorResult(error);
  }
}

export function registerTools(server) {
  server.registerTool(
    "place_block",
    {
      title: "Place block",
      description:
        "Places a single block at (x, y, z) in the running singleplayer world, restricted to the configured " +
        'MCP sandbox region (see /fishtastic mcp region set). block_state uses vanilla /setblock grammar, ' +
        'e.g. "minecraft:oak_fence[waterlogged=false]".',
      inputSchema: {
        x: z.number().int(),
        y: z.number().int(),
        z: z.number().int(),
        block_state: z.string(),
        dimension: z.string().optional().describe("Defaults to minecraft:overworld"),
      },
    },
    async (args) => run(() => callBridge("/place_block", args))
  );

  server.registerTool(
    "fill_blocks",
    {
      title: "Fill blocks",
      description:
        "Fills the box between from/to with a single block state (max 50,000 blocks), restricted to the " +
        "configured MCP sandbox region. mode 'replace' (default) overwrites everything; 'keep' only fills air.",
      inputSchema: {
        from: z.object({ x: z.number().int(), y: z.number().int(), z: z.number().int() }),
        to: z.object({ x: z.number().int(), y: z.number().int(), z: z.number().int() }),
        block_state: z.string(),
        mode: z.enum(["replace", "keep"]).optional(),
        dimension: z.string().optional().describe("Defaults to minecraft:overworld"),
      },
    },
    async (args) => run(() => callBridge("/fill_blocks", args))
  );

  server.registerTool(
    "get_block",
    {
      title: "Get block",
      description:
        "Reads the block state at (x, y, z). Not restricted to the sandbox region - reads are harmless anywhere.",
      inputSchema: {
        x: z.number().int(),
        y: z.number().int(),
        z: z.number().int(),
        dimension: z.string().optional().describe("Defaults to minecraft:overworld"),
      },
    },
    async (args) => run(() => callBridge("/get_block", args))
  );

  server.registerTool(
    "get_context",
    {
      title: "Get context",
      description:
        "Returns the singleplayer owner's position/facing/gamemode, the currently configured MCP sandbox " +
        "region (or null if unset), and world time.",
      inputSchema: {},
    },
    async () => run(() => callBridge("/get_context", {}))
  );

  server.registerTool(
    "screenshot",
    {
      title: "Take screenshot",
      description:
        "Takes a client-side screenshot of the running game and returns the absolute file path of the saved " +
        "PNG - read that path directly (e.g. with the Read tool) to visually check the build. Waits (up to " +
        "2 minutes) for any open screen (pause menu, inventory, chat, etc.) to close first, so it's safe to " +
        "call right after asking the player to tab back in - no need to ask them to confirm a menu is closed.",
      inputSchema: {},
    },
    async () => run(() => callBridge("/screenshot", {}))
  );

  server.registerTool(
    "set_camera",
    {
      title: "Set camera",
      description:
        "Moves the player's viewpoint so the next screenshot frames what you want - the screenshot tool " +
        "always shoots from wherever the player currently is, so call this first to choose an angle " +
        "instead of asking the human to walk there. Give a position (x, y, z) plus either an explicit " +
        "yaw/pitch or a look_at point to aim at automatically (look_at is easier - pass the centre of " +
        "your build). Omitting x/y/z keeps the current position and only re-aims. Restricted to within " +
        "128 blocks of the sandbox region; it is a framing tool, not a general teleport. Enables flight " +
        "when the player may fly, so a mid-air camera doesn't fall before the screenshot lands. " +
        "Pass restore:true (alone) to put the player back where they were standing before the first " +
        "set_camera call - do this when you're done taking shots.",
      inputSchema: {
        x: z.number().optional(),
        y: z.number().optional(),
        z: z.number().optional(),
        yaw: z.number().optional().describe("Degrees; 0=south, -90=east, 180=north, 90=west. Ignored if look_at is given."),
        pitch: z.number().optional().describe("Degrees; negative looks up, positive looks down. Ignored if look_at is given."),
        look_at: z
          .object({ x: z.number(), y: z.number(), z: z.number() })
          .optional()
          .describe("Aim the camera at this point, computing yaw/pitch from the eye position."),
        restore: z.boolean().optional().describe("Return the player to their pre-set_camera pose. Use alone."),
      },
    },
    async (args) => run(() => callBridge("/set_camera", args))
  );

  server.registerTool(
    "orbit_screenshot",
    {
      title: "Orbit screenshot",
      description:
        "THE DEFAULT WAY TO LOOK AT A BUILD - prefer this over screenshot. Closes any open GUI, stops " +
        "the game pausing itself while the window is unfocused, then flies a turntable orbit around " +
        "`center` taking `shots` evenly-spaced screenshots (default 8, i.e. every 45deg) and stitches " +
        "them into ONE contact-sheet PNG. Returns that sheet's absolute path plus the compass angle of " +
        "each tile in row-major order, 4 per row - Read the path to see every side of the build at once. " +
        "Also flashes the sheet on the player's own screen for 3 seconds so they see what you see. " +
        "Restores the player's original position when done. Set `radius` to roughly 1.5x the build's " +
        "largest dimension and `height` a little above it - err tight, since a wide radius leaves the " +
        "subject too small in a 400px tile to judge. Defaults (radius 12, height center.y+5) suit a " +
        "small structure. The orbit cannot see obstructions: if a tile comes back full of leaves or " +
        "terrain, that ring position was inside something - re-run with a different radius/height. " +
        "Raise `settle_ms` if tiles look like they were shot mid-move.",
      inputSchema: {
        center: z
          .object({ x: z.number(), y: z.number(), z: z.number() })
          .describe("The point to orbit - the centre of the build, not its corner."),
        radius: z.number().optional().describe("Horizontal distance from center. Default 12."),
        height: z.number().optional().describe("Camera Y for every shot. Default center.y + 5."),
        shots: z.number().int().min(1).max(16).optional().describe("Evenly spaced around the circle. Default 8."),
        settle_ms: z
          .number()
          .int()
          .optional()
          .describe("Pause after each move before capturing, letting the client apply it. Default 400."),
      },
    },
    async (args) => run(() => callBridge("/orbit_screenshot", args))
  );

  server.registerTool(
    "run_command",
    {
      title: "Run whitelisted command",
      description:
        "Runs exactly one whitelisted command shape: " +
        "'fishtastic cosmetic capture <from> <to> <anchor> <name> [scale]' - scans the box and writes a " +
        "CosmeticStructure JSON to <server-run-dir>/fishtastic_cosmetic_structures/<name>.json. " +
        "Everything else (setblock, fill, execute, data, etc.) is rejected - use place_block/fill_blocks instead.",
      inputSchema: {
        command: z.string(),
      },
    },
    async (args) => run(() => callBridge("/run_command", args))
  );
}
