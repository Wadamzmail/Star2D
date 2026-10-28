import { INSTRUCTIONS, TOOLS, TOOL_BY_NAME } from "./tools";
import type { Env, ToolResult } from "./hub";

const SUPPORTED = ["2025-06-18", "2025-03-26", "2024-11-05"];

type Rpc = { jsonrpc?: string; id?: string | number | null; method?: string; params?: any };

const ok = (id: Rpc["id"], result: unknown) => ({ jsonrpc: "2.0", id, result });
const fail = (id: Rpc["id"], code: number, message: string) => ({ jsonrpc: "2.0", id: id ?? null, error: { code, message } });

/** Handle one JSON-RPC message. Returns null for notifications. */
async function handleOne(msg: Rpc, secret: string, env: Env, guide: () => string): Promise<unknown | null> {
  if (!msg || typeof msg.method !== "string") return fail(msg?.id, -32600, "Invalid request");
  const isNotification = msg.id === undefined || msg.id === null;
  switch (msg.method) {
    case "initialize": {
      const wanted = msg.params?.protocolVersion;
      return ok(msg.id, {
        protocolVersion: SUPPORTED.includes(wanted) ? wanted : SUPPORTED[0],
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: "star2d-remote", version: "1.0.0" },
        instructions: INSTRUCTIONS,
      });
    }
    case "ping":
      return ok(msg.id, {});
    case "tools/list":
      return ok(msg.id, { tools: TOOLS.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })) });
    case "resources/list":
      return ok(msg.id, { resources: [] });
    case "resources/templates/list":
      return ok(msg.id, { resourceTemplates: [] });
    case "prompts/list":
      return ok(msg.id, { prompts: [] });
    case "tools/call": {
      const name = msg.params?.name;
      const args = (msg.params?.arguments ?? {}) as Record<string, unknown>;
      const def = typeof name === "string" ? TOOL_BY_NAME.get(name) : undefined;
      if (!def) return fail(msg.id, -32602, `Unknown tool: ${name}`);
      if (name === "get_guide") return ok(msg.id, { content: [{ type: "text", text: guide() }] } satisfies ToolResult);
      const stub = env.HUB.get(env.HUB.idFromName(secret));
      try {
        return ok(msg.id, await stub.invoke(name, args));
      } catch (e) {
        return ok(msg.id, { content: [{ type: "text", text: "server_error: " + String(e) }], isError: true } satisfies ToolResult);
      }
    }
    default:
      return isNotification ? null : fail(msg.id, -32601, `Method not found: ${msg.method}`);
  }
}

/** Streamable-HTTP MCP endpoint: POST a JSON-RPC message (or a batch), get JSON back. */
export async function handleMcp(request: Request, secret: string, env: Env, guide: () => string): Promise<Response> {
  const headers = { "content-type": "application/json", "cache-control": "no-store" };
  let body: unknown;
  try {
    const raw = await request.text();
    if (raw.length > 1_500_000) return new Response(JSON.stringify(fail(null, -32600, "Request too large")), { status: 413, headers });
    body = JSON.parse(raw);
  } catch {
    return new Response(JSON.stringify(fail(null, -32700, "Parse error")), { status: 400, headers });
  }
  if (Array.isArray(body)) {
    const out = (await Promise.all(body.map((m) => handleOne(m, secret, env, guide)))).filter((r) => r !== null);
    return out.length ? new Response(JSON.stringify(out), { headers }) : new Response(null, { status: 202 });
  }
  const res = await handleOne(body as Rpc, secret, env, guide);
  return res === null ? new Response(null, { status: 202 }) : new Response(JSON.stringify(res), { headers });
}
