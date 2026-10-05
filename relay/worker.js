const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store"
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: jsonHeaders });
}

function randomToken(bytes = 32) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return [...data].map(x => x.toString(16).padStart(2, "0")).join("");
}

function pairingCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const data = new Uint32Array(8);
  crypto.getRandomValues(data);
  return [...data].map(x => alphabet[x % alphabet.length]).join("");
}

async function hash(value) {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, "0")).join("");
}

function validCommand(command) {
  return ["play", "pause", "rewind", "forward"].includes(command);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "https://cinepulse.local",
          "access-control-allow-methods": "GET,POST,OPTIONS",
          "access-control-allow-headers": "content-type"
        }
      });
    }

    if (url.pathname === "/health") return json({ ok: true, service: "CinePulse Relay" });

    if (url.pathname === "/v1/session" && request.method === "POST") {
      const code = pairingCode();
      const phoneToken = randomToken();
      const id = env.CINEPULSE_SESSIONS.idFromName(code);
      const stub = env.CINEPULSE_SESSIONS.get(id);
      const result = await stub.fetch("https://relay/session", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code, phoneTokenHash: await hash(phoneToken) })
      });
      if (!result.ok) return json({ error: "session creation failed" }, 500);
      return json({ ok: true, code, token: phoneToken });
    }

    if (url.pathname === "/v1/register" && request.method === "POST") {
      const body = await request.json().catch(() => null);
      if (!body || typeof body.code !== "string" || body.role !== "controller") return json({ error: "invalid registration" }, 400);
      const id = env.CINEPULSE_SESSIONS.idFromName(body.code.toUpperCase());
      const stub = env.CINEPULSE_SESSIONS.get(id);
      const controllerToken = randomToken();
      const result = await stub.fetch("https://relay/register", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code: body.code.toUpperCase(), controllerTokenHash: await hash(controllerToken) })
      });
      if (!result.ok) return new Response(result.body, { status: result.status, headers: jsonHeaders });
      return json({ ok: true, token: controllerToken });
    }

    if (url.pathname === "/v1/command" && request.method === "POST") {
      const body = await request.json().catch(() => null);
      if (!body || typeof body.code !== "string" || typeof body.token !== "string" || !validCommand(body.command)) return json({ error: "invalid command" }, 400);
      const id = env.CINEPULSE_SESSIONS.idFromName(body.code.toUpperCase());
      const stub = env.CINEPULSE_SESSIONS.get(id);
      return stub.fetch("https://relay/command", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ tokenHash: await hash(body.token), command: body.command })
      });
    }

    if (url.pathname === "/v1/poll" && request.method === "POST") {
      const body = await request.json().catch(() => null);
      if (!body || typeof body.code !== "string" || typeof body.token !== "string") return json({ error: "missing credentials" }, 400);
      const code = body.code.toUpperCase();
      const id = env.CINEPULSE_SESSIONS.idFromName(code);
      const stub = env.CINEPULSE_SESSIONS.get(id);
      return stub.fetch("https://relay/poll", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code, token: body.token })
      });
    }

    // Backward compatibility with older controller builds.
    if (url.pathname === "/v1/poll" && request.method === "GET") {
      const code = (url.searchParams.get("code") || "").toUpperCase();
      const token = url.searchParams.get("token") || "";
      if (!code || !token) return json({ error: "missing credentials" }, 400);
      const id = env.CINEPULSE_SESSIONS.idFromName(code);
      const stub = env.CINEPULSE_SESSIONS.get(id);
      return stub.fetch("https://relay/poll", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code, token })
      });
    }

    return json({ error: "not found" }, 404);
  }
};

export class CinePulseSession {
  constructor(state) {
    this.state = state;
  }

  async load() {
    return await this.state.storage.get("session") || null;
  }

  async fetch(request) {
    const session = await this.load();
    if (request.method === "POST" && new URL(request.url).pathname === "/session") {
      const body = await request.json();
      await this.state.storage.put("session", {
        code: body.code,
        phoneTokenHash: body.phoneTokenHash,
        controllerTokenHash: null,
        commands: [],
        createdAt: Date.now()
      });
      return json({ ok: true });
    }

    if (!session) return json({ error: "session not found or expired" }, 404);
    if (Date.now() - session.createdAt > 24 * 60 * 60 * 1000) {
      await this.state.storage.delete("session");
      return json({ error: "session expired" }, 410);
    }

    const path = new URL(request.url).pathname;

    if (request.method === "POST" && path === "/register") {
      if (session.controllerTokenHash) return json({ error: "controller already registered" }, 409);
      const body = await request.json();
      await this.state.storage.put("session", { ...session, controllerTokenHash: body.controllerTokenHash });
      return json({ ok: true });
    }

    if (request.method === "POST" && path === "/command") {
      const body = await request.json();
      if (body.tokenHash !== session.phoneTokenHash) return json({ error: "unauthorized" }, 401);
      const commands = [...session.commands, body.command].slice(-20);
      await this.state.storage.put("session", { ...session, commands });
      return json({ ok: true });
    }

    if ((request.method === "GET" || request.method === "POST") && path === "/poll") {
      let tokenHash = request.headers.get("x-token-hash") || "";
      if (request.method === "POST") {
        const body = await request.json().catch(() => null);
        if (!body || typeof body.code !== "string" || typeof body.token !== "string") return json({ error: "missing credentials" }, 400);
        if (body.code.toUpperCase() !== session.code) return json({ error: "unauthorized" }, 401);
        tokenHash = await hash(body.token);
      }
      if (tokenHash !== session.controllerTokenHash) return json({ error: "unauthorized" }, 401);
      const commands = session.commands || [];
      await this.state.storage.put("session", { ...session, commands: [] });
      return json({ ok: true, commands });
    }

    return json({ error: "not found" }, 404);
  }
}
