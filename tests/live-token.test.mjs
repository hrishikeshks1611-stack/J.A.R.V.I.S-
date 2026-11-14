import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/live-token.js";

const originalFetch = globalThis.fetch;
const keys = ["JARVIS", "GEMINI_API_KEY", "JARVIS_LIVE_VOICE_FREE_TIER_CONFIRMED", "UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN"];
const prior = Object.fromEntries(keys.map(key => [key, process.env[key]]));

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  for (const key of keys) {
    if (prior[key] === undefined) delete process.env[key];
    else process.env[key] = prior[key];
  }
});

function response() {
  return {
    headers: {}, statusCode: 200, headersSent: false,
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; this.headersSent = true; return this; },
    end() { this.ended = true; return this; }
  };
}

function setup(upstream) {
  process.env.JARVIS = "test-gemini-key";
  process.env.JARVIS_LIVE_VOICE_FREE_TIER_CONFIRMED = "true";
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-redis-token";
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("redis.test")) {
      const command = JSON.parse(options.body)[0];
      const result = command[0] === "GET"
        ? JSON.stringify({ deviceId: "live-test-phone", deviceName: "Test phone", role: "owner" })
        : command[0] === "EXPIRE" ? 1 : null;
      return new Response(JSON.stringify([{ result }]), { status: 200 });
    }
    if (String(url).includes("/v1beta/auth_tokens")) return upstream(url, options);
    throw new Error("Unexpected network request: " + url);
  };
}

const request = {
  method: "POST",
  headers: {
    origin: "https://hrishikeshks1611-stack.github.io",
    authorization: "Bearer paired-live-test-token"
  },
  body: {}
};

test("Live Voice provisions a single-use, short-lived transcription-only token without exposing the API key", async () => {
  let sent;
  setup(async (_url, options) => {
    sent = { headers: options.headers, body: JSON.parse(options.body) };
    return new Response(JSON.stringify({
      name: "ephemeral-live-token",
      expireTime: "2026-11-14T16:00:00.000Z",
      newSessionExpireTime: "2026-11-14T15:31:00.000Z"
    }), { status: 200 });
  });
  const res = response();
  await handler(request, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.token, "ephemeral-live-token");
  assert.equal(res.body.responseMode, "transcription-only");
  assert.equal(res.headers["Cache-Control"], "no-store");
  assert.equal(sent.headers["x-goog-api-key"], "test-gemini-key");
  assert.equal(sent.body.uses, 1);
  assert.equal(sent.body.liveConnectConstraints.model, "models/gemini-3.8-live");
  assert.deepEqual(sent.body.liveConnectConstraints.config.responseModalities, ["TEXT"]);
  assert.deepEqual(sent.body.liveConnectConstraints.config.inputAudioTranscription, {});
  assert.ok(sent.body.liveConnectConstraints.config.sessionResumption);
  assert.match(sent.body.liveConnectConstraints.config.systemInstruction.parts[0].text, /Transcribe the user's spoken words faithfully/);
  assert.equal(JSON.stringify(res.body).includes("test-gemini-key"), false);
});

test("Live Voice stays disabled until the free-tier-only project is explicitly confirmed", async () => {
  process.env.JARVIS = "test-gemini-key";
  delete process.env.JARVIS_LIVE_VOICE_FREE_TIER_CONFIRMED;
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-redis-token";
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("redis.test")) {
      const command = JSON.parse(options.body)[0];
      const result = command[0] === "GET"
        ? JSON.stringify({ deviceId: "live-test-phone", deviceName: "Test phone", role: "owner" })
        : command[0] === "EXPIRE" ? 1 : null;
      return new Response(JSON.stringify([{ result }]), { status: 200 });
    }
    throw new Error("Google must not be contacted while Live Voice is disabled.");
  };
  const res = response();
  await handler(request, res);
  assert.equal(res.statusCode, 503);
  assert.match(res.body.error, /billing disabled/i);
});

test("unpaired devices cannot provision Live Voice tokens", async () => {
  process.env.JARVIS = "test-gemini-key";
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-redis-token";
  globalThis.fetch = async () => { throw new Error("Redis must not be contacted without a bearer token."); };
  const res = response();
  await handler({ method: "POST", headers: {}, body: {} }, res);
  assert.equal(res.statusCode, 401);
});

test("Live Voice quota errors are returned without silently upgrading to paid usage", async () => {
  setup(async () => new Response(JSON.stringify({ error: { message: "free tier quota exhausted" } }), { status: 429 }));
  const res = response();
  await handler(request, res);
  assert.equal(res.statusCode, 429);
  assert.match(res.body.error, /free tier quota exhausted/);
});
