import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/speech.js";

const originalFetch = globalThis.fetch;
const keys = ["JARVIS", "GEMINI_API_KEY", "UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN"];
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
    headers: {},
    chunks: [],
    statusCode: 200,
    headersSent: false,
    ended: false,
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; this.headersSent = true; return this; },
    write(value) { this.chunks.push(Buffer.from(value)); this.headersSent = true; return true; },
    flushHeaders() { this.headersSent = true; },
    end() { this.ended = true; return this; }
  };
}

function setup(upstream) {
  process.env.JARVIS = "test-gemini-key";
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-redis-token";
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("redis.test")) {
      const command = JSON.parse(options.body)[0];
      const result = command[0] === "GET"
        ? JSON.stringify({ deviceId: "speech-test-phone", deviceName: "Test phone", role: "owner" })
        : command[0] === "EXPIRE" ? 1 : null;
      return new Response(JSON.stringify([{ result }]), { status: 200 });
    }
    if (String(url).includes("generativelanguage.googleapis.com")) return upstream(url, options);
    throw new Error("Unexpected network request: " + url);
  };
}

const request = {
  method: "POST",
  headers: {
    origin: "https://hrishikeshks1611-stack.github.io",
    authorization: "Bearer paired-speech-test-token"
  },
  body: { text: "Good evening, Sir. How may I assist?" }
};

test("Algieba speech preserves en-GB identity and streams PCM across split SSE chunks", async () => {
  let modelRequest;
  const first = Buffer.from([1, 2, 3, 4]);
  const second = Buffer.from([5, 6, 7, 8]);
  const event = (bytes) => "data: " + JSON.stringify({
    candidates: [{ content: { parts: [{ inlineData: { mimeType: "audio/pcm", data: bytes.toString("base64") } }] } }]
  }) + "\n\n";
  const payload = event(first) + event(second) + "data: [DONE]\n\n";
  const split = Math.floor(payload.length / 3);
  setup(async (_url, options) => {
    modelRequest = JSON.parse(options.body);
    const encoder = new TextEncoder();
    const chunks = [payload.slice(0, split), payload.slice(split, split * 2), payload.slice(split * 2)];
    const stream = new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
        controller.close();
      }
    });
    return new Response(stream, { status: 200, headers: { "content-type": "text/event-stream" } });
  });

  const res = response();
  await handler(request, res);

  assert.equal(res.statusCode, 200);
  assert.equal(res.ended, true);
  assert.equal(res.headers["Content-Type"], "application/octet-stream");
  assert.equal(res.headers["X-Jarvis-Audio-Format"], "pcm_s16le;rate=24000;channels=1");
  assert.deepEqual(Buffer.concat(res.chunks), Buffer.concat([first, second]));
  assert.equal(modelRequest.generationConfig.speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName, "Algieba");
  assert.equal(modelRequest.generationConfig.speechConfig.languageCode, "en-GB");
  assert.match(modelRequest.contents[0].parts[0].text, /contemporary British English/);
});

test("Gemini speech quota errors remain visible as HTTP 429", async () => {
  setup(async () => new Response(JSON.stringify({ error: { message: "quota exceeded" } }), { status: 429 }));
  const res = response();
  await handler(request, res);
  assert.equal(res.statusCode, 429);
  assert.match(res.body.error, /HTTP 429/);
});

test("empty Gemini speech streams fail instead of returning silent audio", async () => {
  setup(async () => {
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"no audio\"}]}}]}\n\n"));
        controller.close();
      }
    });
    return new Response(stream, { status: 200, headers: { "content-type": "text/event-stream" } });
  });
  const res = response();
  await handler(request, res);
  assert.equal(res.statusCode, 502);
  assert.match(res.body.error, /no audio/i);
});

test("unpaired devices cannot call the speech service", async () => {
  process.env.JARVIS = "test-gemini-key";
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-redis-token";
  globalThis.fetch = async () => { throw new Error("Redis must not be contacted without a bearer token."); };
  const res = response();
  await handler({ method: "POST", headers: {}, body: { text: "Hello" } }, res);
  assert.equal(res.statusCode, 401);
});
