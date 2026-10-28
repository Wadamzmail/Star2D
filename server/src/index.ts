import { handleMcp } from "./mcp";
import { Hub, type Env } from "./hub";
import { CORE_RULES, TOOL_BY_NAME } from "./tools";
import AI_MD from "../AI.md";

export { Hub };

const SECRET = /^[A-Za-z0-9_-]{24,128}$/;

const CORS: Record<string, string> = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
  "access-control-allow-headers": "content-type, mcp-session-id, mcp-protocol-version, authorization, accept",
  "access-control-expose-headers": "mcp-session-id",
};

function withCors(r: Response): Response {
  if (r.status === 101) return r; // websocket upgrade: leave untouched
  const out = new Response(r.body, r);
  for (const [k, v] of Object.entries(CORS)) out.headers.set(k, v);
  return out;
}

/**
 * API versions. Each version has its own handler, so a new app can use /v2/ while old apps keep using /v1/.
 * To release v2: write `handleV2`, add it to HANDLERS and to SUPPORTED. Never change what an old version does.
 * The unversioned paths (/mcp, /api, /ws, /AI.md) are the original v1 and stay as aliases of it.
 */
const SUPPORTED = [1];
const LATEST = 1;

interface Ctx {
  request: Request;
  env: Env;
  url: URL;
  base: string; // "" for the legacy unversioned paths, "/v1" otherwise
}

type Handler = (parts: string[], c: Ctx) => Promise<Response>;

async function handleV1(parts: string[], c: Ctx): Promise<Response> {
  const { request, env, url } = c;
  const guide = () => (AI_MD as string).replaceAll("{{SERVER}}", url.origin + c.base).replace("{{CORE}}", CORE_RULES);

  if (parts.length === 0)
    return withCors(new Response("Star2D Remote server is running (API v" + SUPPORTED.join(", v") + ").\nAI guide: " + url.origin + (c.base || "/v1") + "/AI.md\n", { headers: { "content-type": "text/plain; charset=utf-8" } }));

  if (parts[0] === "AI.md" || parts[0] === "ai.md")
    return withCors(new Response(guide(), { headers: { "content-type": "text/markdown; charset=utf-8", "cache-control": "public, max-age=300" } }));

  if (parts[0] === "health") return withCors(new Response("ok"));

  // MCP endpoint used by Claude (and any MCP client): the secret is part of the URL
  if (parts[0] === "mcp" && parts.length === 2) {
    if (!SECRET.test(parts[1])) return withCors(new Response("bad secret", { status: 404 }));
    if (request.method === "POST") return withCors(await handleMcp(request, parts[1], env, guide));
    if (request.method === "DELETE") return withCors(new Response(null, { status: 204 }));
    return withCors(new Response("Use POST", { status: 405, headers: { allow: "POST, DELETE, OPTIONS" } }));
  }

  // plain HTTP for AIs without MCP: POST /api/<secret>/<tool> with the arguments as JSON
  if (parts[0] === "api" && parts.length === 3) {
    if (!SECRET.test(parts[1])) return withCors(new Response("bad secret", { status: 404 }));
    if (request.method !== "POST") return withCors(new Response("Use POST", { status: 405 }));
    const tool = TOOL_BY_NAME.get(parts[2]);
    if (!tool) return withCors(new Response(JSON.stringify({ isError: true, content: [{ type: "text", text: "unknown tool" }] }), { status: 404, headers: { "content-type": "application/json" } }));
    if (tool.name === "get_guide") return withCors(Response.json({ content: [{ type: "text", text: guide() }] }));
    let args: Record<string, unknown> = {};
    try {
      const raw = await request.text();
      if (raw.length > 1_500_000) return withCors(new Response("too large", { status: 413 }));
      if (raw.trim()) args = JSON.parse(raw);
    } catch {
      return withCors(new Response("bad json", { status: 400 }));
    }
    const stub = env.HUB.get(env.HUB.idFromName(parts[1]));
    return withCors(Response.json(await stub.invoke(tool.name, args)));
  }

  // the phone's WebSocket
  if (parts[0] === "ws" && parts.length === 2) {
    if (!SECRET.test(parts[1])) return new Response("bad secret", { status: 404 });
    const stub = env.HUB.get(env.HUB.idFromName(parts[1]));
    return stub.fetch(request);
  }

  return withCors(new Response("not found", { status: 404 }));
}

const HANDLERS: Record<number, Handler> = { 1: handleV1 };

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);

    if (request.method === "OPTIONS") return withCors(new Response(null, { status: 204 }));

    // what this server supports: the app checks it before connecting
    if (parts.length === 1 && parts[0] === "version")
      return withCors(Response.json({ service: "star2d-remote", versions: SUPPORTED, latest: LATEST }, { headers: { "cache-control": "no-store" } }));

    const m = parts.length > 0 ? /^v(\d{1,4})$/.exec(parts[0]) : null;
    if (m) {
      const v = parseInt(m[1], 10);
      const h = HANDLERS[v];
      if (!h || !SUPPORTED.includes(v))
        return withCors(
          Response.json(
            { error: "unsupported_version", requested: v, versions: SUPPORTED, message: "This server does not support API v" + v + ". The server needs an update." },
            { status: 404 },
          ),
        );
      return h(parts.slice(1), { request, env, url, base: "/v" + v });
    }

    // legacy unversioned paths = v1
    return handleV1(parts, { request, env, url, base: "" });
  },
};
