// End-to-end test: starts the Worker locally (wrangler dev), connects a fake phone over WebSocket and drives it through MCP.
import { spawn } from "node:child_process";
import WebSocket from "ws";

const PORT = 8799;
const BASE = `http://127.0.0.1:${PORT}`;
const SECRET = "test_secret_" + "x".repeat(30);
let failed = 0;
const check = (name, cond, extra = "") => {
  console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : "  " + extra));
  if (!cond) failed++;
};

const dev = spawn("npx", ["wrangler", "dev", "--local", "--port", String(PORT), "--var", "DAILY_CALLS:40"], { stdio: ["ignore", "pipe", "pipe"], cwd: new URL("..", import.meta.url).pathname });
let log = "";
dev.stdout.on("data", (d) => (log += d));
dev.stderr.on("data", (d) => (log += d));

async function waitUp() {
  for (let i = 0; i < 120; i++) {
    try {
      const r = await fetch(BASE + "/health");
      if (r.ok) return;
    } catch {}
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error("worker did not start:\n" + log);
}

const rpc = async (method, params, id = 1) => {
  const r = await fetch(`${BASE}/v1/mcp/${SECRET}`, {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json, text/event-stream" },
    body: JSON.stringify({ jsonrpc: "2.0", id, method, params }),
  });
  return { status: r.status, json: r.status === 202 ? null : await r.json() };
};
const call = async (name, args = {}) => (await rpc("tools/call", { name, arguments: args })).json.result;

function phone() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${PORT}/v1/ws/${SECRET}`);
    ws.on("open", () => {
      ws.send(JSON.stringify({ t: "hello", app: "Star2D", ver: "test", device: "sim" }));
      resolve(ws);
    });
    ws.on("message", (raw) => {
      const m = JSON.parse(raw.toString());
      if (m.t === "call") {
        if (m.tool === "slow") return; // never answers
        const content =
          m.tool === "screenshot"
            ? [{ type: "image", data: "AAAA", mimeType: "image/jpeg" }]
            : [{ type: "text", text: JSON.stringify({ echo: m.tool, args: m.args }) }];
        ws.send(JSON.stringify({ t: "res", id: m.id, content }));
      }
    });
    ws.on("error", reject);
  });
}

try {
  await waitUp();

  let r = await fetch(BASE + "/AI.md");
  const md = await r.text();
  check("AI.md served with server url", r.ok && md.includes(BASE) && !md.includes("{{SERVER}}"));

  r = await rpc("initialize", { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "t", version: "1" } });
  check("initialize", r.json.result.protocolVersion === "2025-06-18" && r.json.result.instructions.includes("Star2D"));

  r = await fetch(`${BASE}/v1/mcp/${SECRET}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }) });
  check("notification -> 202", r.status === 202);

  r = await rpc("tools/list", {});
  const names = r.json.result.tools.map((t) => t.name);
  check("tools/list has the tools", names.length >= 28 && names.includes("elements") && names.includes("run_test"), names.length);
  const elementsTool = r.json.result.tools.find((t) => t.name === "elements");
  check("elements schema keeps ops + scene", elementsTool.inputSchema.properties.ops && elementsTool.inputSchema.properties.scene && elementsTool.inputSchema.properties.ops.items.properties.op.enum.includes("add"));

  r = await call("get_guide");
  check("get_guide (local)", r.content[0].text.includes("Star2D Remote"));

  r = await call("status");
  check("status when no phone", JSON.parse(r.content[0].text).connected === false);
  r = await call("elements", { ops: [] });
  check("tool without phone -> app_not_connected", r.isError && r.content[0].text.startsWith("app_not_connected"));

  const t0 = Date.now();
  r = await call("wait_for_app", { seconds: 1 });
  check("wait_for_app times out", JSON.parse(r.content[0].text).connected === false && Date.now() - t0 >= 900);

  // phone connects while Claude waits
  const waiting = call("wait_for_app", { seconds: 10 });
  await new Promise((res) => setTimeout(res, 600));
  const ws = await phone();
  r = await waiting;
  check("wait_for_app wakes up on connect", JSON.parse(r.content[0].text).connected === true);

  r = await call("elements", { ops: [{ op: "add", type: "BOX", name: "player" }] });
  const echo = JSON.parse(r.content[0].text);
  check("call is relayed to the phone and back", echo.echo === "elements" && echo.args.ops[0].name === "player");

  r = await call("screenshot", { target: "game" });
  check("image content passes through", r.content[0].type === "image" && r.content[0].data === "AAAA");

  // plain HTTP api
  const api = await (await fetch(`${BASE}/v1/api/${SECRET}/status`, { method: "POST", body: "{}" })).json();
  check("HTTP api", JSON.parse(api.content[0].text).echo === "status");
  r = await fetch(`${BASE}/v1/api/${SECRET}/nope`, { method: "POST", body: "{}" });
  check("HTTP api unknown tool 404", r.status === 404);

  r = await fetch(`${BASE}/v1/mcp/short`, { method: "POST", body: "{}" });
  check("bad secret rejected", r.status === 404);

  // a second connection replaces the first
  const ws2 = await phone();
  await new Promise((res) => setTimeout(res, 300));
  r = await call("project_info");
  check("newest phone socket serves calls", JSON.parse(r.content[0].text).echo === "project_info");
  ws.close();
  ws2.close();
  await new Promise((res) => setTimeout(res, 500));
  r = await call("project_info");
  check("after disconnect -> app_not_connected", r.isError && r.content[0].text.startsWith("app_not_connected"));
  // the per-secret daily budget (DAILY_CALLS=40 for this test)
  let limited = false;
  for (let i = 0; i < 60 && !limited; i++) {
    r = await call("project_info");
    limited = r.isError && r.content[0].text.startsWith("daily_limit");
  }
  check("daily limit stops a runaway client", limited);
  // versions: /version lists them, /v1 works, the old unversioned paths still work, an unknown version says the server needs an update
  {
    const v = await (await fetch(`${BASE}/version`)).json();
    check("version endpoint lists v1", Array.isArray(v.versions) && v.versions.includes(1));
    const g1 = await (await fetch(`${BASE}/v1/AI.md`)).text();
    check("v1 guide points at /v1 paths", g1.includes(`${BASE}/v1/mcp/<secret>`), g1.slice(0, 200));
    const g0 = await (await fetch(`${BASE}/AI.md`)).text();
    check("legacy guide keeps unversioned paths", g0.includes(`${BASE}/mcp/<secret>`));
    const old = await fetch(`${BASE}/mcp/${SECRET}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", id: 9, method: "ping" }) });
    check("legacy unversioned mcp path still answers", old.status === 200, String(old.status));
    const v9 = await fetch(`${BASE}/v9/mcp/${SECRET}`, { method: "POST", body: "{}" });
    const j9 = await v9.json();
    check("unknown version reports unsupported_version", v9.status === 404 && j9.error === "unsupported_version" && j9.versions.includes(1));
  }
} catch (e) {
  console.log("ERROR", e);
  failed++;
} finally {
  dev.kill("SIGTERM");
}
console.log(failed ? `\n${failed} FAILED` : "\nALL OK");
process.exit(failed ? 1 : 0);
