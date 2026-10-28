import { DurableObject } from "cloudflare:workers";
import { TOOL_BY_NAME } from "./tools";

export interface Env {
  HUB: DurableObjectNamespace<Hub>;
  DAILY_CALLS?: string;
  IDLE_PURGE_HOURS?: string;
}

export interface ToolResult {
  content: Array<Record<string, unknown>>;
  isError?: boolean;
}

interface Pending {
  resolve: (r: ToolResult) => void;
  timer: ReturnType<typeof setTimeout>;
}

const text = (t: string, isError = false): ToolResult => ({ content: [{ type: "text", text: t }], ...(isError ? { isError: true } : {}) });

const NOT_CONNECTED =
  "app_not_connected: the Star2D app on the user's phone is not connected right now. " +
  "Tell the user (in their language): open the Star2D editor now and keep it open and visible on screen; you will work in the background. " +
  "Then call wait_for_app (repeat it a few times) until it says connected.";

const day = () => new Date().toISOString().slice(0, 10);

/**
 * One Hub per user (named by the user's secret). The phone keeps a hibernating WebSocket open to it;
 * every tool call from the AI is forwarded over that socket and the phone's answer is returned.
 *
 * Cost on the Cloudflare free plan: one DO request per tool call (incoming WebSocket messages count 1/20),
 * no duration while idle (WebSocket Hibernation). The only stored data is a tiny daily counter, purged automatically.
 */
export class Hub extends DurableObject<Env> {
  private pending = new Map<string, Pending>();
  private waiters = new Set<() => void>();
  private usageDay = "";
  private usageCount = 0;
  private unflushed = 0;
  private purged = false;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    // keep-alive pings from the phone are answered without waking this object
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  private live(): WebSocket[] {
    return this.ctx.getWebSockets().filter((w) => w.readyState === WebSocket.OPEN);
  }

  // ------------------------------------------------------------------ phone side (WebSocket)

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade") !== "websocket") return new Response("expected a websocket", { status: 426 });
    await this.purgeOld();
    for (const old of this.live()) {
      try {
        old.close(1000, "replaced by a newer connection");
      } catch {
        /* already closed */
      }
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    await this.ctx.storage.deleteAlarm();
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") return;
    let m: any;
    try {
      m = JSON.parse(message);
    } catch {
      return;
    }
    if (m.t === "hello") {
      await this.ctx.storage.put("meta", { at: Date.now(), app: m.app ?? "", ver: m.ver ?? "", device: m.device ?? "" });
      ws.send(JSON.stringify({ t: "welcome", limit: this.limit(), used: await this.used() }));
      for (const w of this.waiters) w();
      this.waiters.clear();
    } else if (m.t === "res" && typeof m.id === "string") {
      const p = this.pending.get(m.id);
      if (!p) return;
      clearTimeout(p.timer);
      this.pending.delete(m.id);
      const content = Array.isArray(m.content) ? m.content : [{ type: "text", text: String(m.text ?? "") }];
      p.resolve({ content, ...(m.isError ? { isError: true } : {}) });
    }
  }

  async webSocketClose(ws: WebSocket, code: number, reason: string): Promise<void> {
    try {
      ws.close(code, reason);
    } catch {
      /* ignore */
    }
    await this.afterDisconnect();
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    await this.afterDisconnect();
  }

  private async afterDisconnect(): Promise<void> {
    if (this.live().length > 0) return; // a newer socket took over
    for (const [id, p] of this.pending) {
      clearTimeout(p.timer);
      p.resolve(text("phone_disconnected: the app closed the connection while this call was running. Ask the user to keep the editor open, then wait_for_app.", true));
      this.pending.delete(id);
    }
    await this.flushUsage();
    const hours = Math.max(1, Number(this.env.IDLE_PURGE_HOURS ?? "24") || 24);
    await this.ctx.storage.setAlarm(Date.now() + hours * 3600_000);
  }

  /** Idle for a long time: forget everything about this user. */
  async alarm(): Promise<void> {
    if (this.live().length === 0) await this.ctx.storage.deleteAll();
  }

  // ------------------------------------------------------------------ AI side (called by the Worker over RPC)

  async invoke(name: string, args: Record<string, unknown>): Promise<ToolResult> {
    const quota = await this.countCall();
    if (quota) return quota;

    if (name === "wait_for_app") return this.waitForApp(Number(args?.seconds));

    const ws = this.live()[0];
    if (!ws) {
      if (name === "status") return text(JSON.stringify({ connected: false, hint: NOT_CONNECTED, last_seen: await this.lastSeen() }));
      return text(NOT_CONNECTED, true);
    }

    const id = Math.random().toString(36).slice(2, 10);
    const timeoutMs = (TOOL_BY_NAME.get(name)?.timeout ?? 55) * 1000;
    return new Promise<ToolResult>((resolve) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        resolve(
          text(
            "timeout: the app did not answer in time. It may be in the background or the screen is off. Ask the user to open the Star2D editor and keep it on screen, then call status.",
            true,
          ),
        );
      }, timeoutMs);
      this.pending.set(id, { resolve, timer });
      try {
        ws.send(JSON.stringify({ t: "call", id, tool: name, args }));
      } catch {
        clearTimeout(timer);
        this.pending.delete(id);
        resolve(text(NOT_CONNECTED, true));
      }
    });
  }

  private async waitForApp(seconds: number): Promise<ToolResult> {
    const s = Math.min(25, Math.max(1, Number.isFinite(seconds) ? seconds : 20));
    if (this.live().length > 0) return text(JSON.stringify({ connected: true }));
    const connected = await new Promise<boolean>((resolve) => {
      const done = () => {
        clearTimeout(t);
        resolve(this.live().length > 0);
      };
      const t = setTimeout(() => {
        this.waiters.delete(done);
        resolve(false);
      }, s * 1000);
      this.waiters.add(done);
    });
    return connected
      ? text(JSON.stringify({ connected: true }))
      : text(JSON.stringify({ connected: false, hint: "Still not connected. Keep waiting (call wait_for_app again) and remind the user to open the editor and leave it on screen." }));
  }

  // ------------------------------------------------------------------ small bookkeeping

  private limit(): number {
    return Math.max(10, Number(this.env.DAILY_CALLS ?? "4000") || 4000);
  }

  private async loadUsage(): Promise<void> {
    const d = day();
    if (this.usageDay === d) return;
    this.usageDay = d;
    this.usageCount = (await this.ctx.storage.get<number>("usage:" + d)) ?? 0;
    this.unflushed = 0;
  }

  private async used(): Promise<number> {
    await this.loadUsage();
    return this.usageCount;
  }

  private async countCall(): Promise<ToolResult | null> {
    await this.loadUsage();
    if (this.usageCount >= this.limit()) {
      return text(`daily_limit: this connection used its ${this.limit()} calls for today (shared free server). Tell the user it resets at midnight UTC, or that they can deploy their own server (see server/README.md).`, true);
    }
    this.usageCount++;
    if (++this.unflushed >= 5) await this.flushUsage();
    return null;
  }

  private async flushUsage(): Promise<void> {
    if (this.unflushed === 0 || !this.usageDay) return;
    this.unflushed = 0;
    await this.ctx.storage.put("usage:" + this.usageDay, this.usageCount);
  }

  private async lastSeen(): Promise<unknown> {
    const meta = await this.ctx.storage.get<{ at: number }>("meta");
    return meta ? new Date(meta.at).toISOString() : null;
  }

  /** Old day-folders (counters of earlier days) are deleted whenever someone connects. */
  private async purgeOld(): Promise<void> {
    if (this.purged) return;
    this.purged = true;
    const keep = "usage:" + day();
    const old = await this.ctx.storage.list({ prefix: "usage:" });
    const dead = [...old.keys()].filter((k) => k !== keep);
    if (dead.length) await this.ctx.storage.delete(dead);
  }
}
