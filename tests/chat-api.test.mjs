import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/chat.js";
import { hashToken } from "../lib/jarvis-store.js";

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { this.ended = true; return this; }
  };
}

test("chat API handles the approved web app preflight", async () => {
  const res = response();
  await handler({
    method: "OPTIONS",
    headers: { origin: "https://hrishikeshks1611-stack.github.io" }
  }, res);
  assert.equal(res.statusCode, 204);
  assert.equal(res.headers["Access-Control-Allow-Origin"], "https://hrishikeshks1611-stack.github.io");
  assert.match(res.headers["Access-Control-Allow-Headers"], /Authorization/);
});

test("chat API rejects unpaired devices before calling Gemini", async () => {
  const res = response();
  await handler({ method: "POST", headers: {}, body: { message: "Hello" } }, res);
  assert.equal(res.statusCode, 401);
  assert.match(res.body.error, /not paired/i);
});

test("chat API rejects unapproved origins", async () => {
  const res = response();
  await handler({
    method: "POST",
    headers: { origin: "https://untrusted.example" },
    body: { message: "Hello" }
  }, res);
  assert.equal(res.statusCode, 403);
});


test("Android capability questions return the code-backed catalogue without calling Gemini", async () => {
  const originalFetch = global.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN", "JARVIS", "GEMINI_API_KEY"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  const token = "capability-test-session-token";
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  delete process.env.JARVIS;
  delete process.env.GEMINI_API_KEY;
  const writes = [];
  global.fetch = async (_url, options) => {
    const commands = JSON.parse(options.body);
    const results = commands.map(command => {
      const [op, key] = command;
      if (op === "GET" && key.includes(":session:")) return { result: JSON.stringify({ deviceId: "android-test-device" }) };
      if (op === "GET" && key.endsWith(":owner:token-hash")) return { result: null };
      if (op === "GET") return { result: null };
      if (op === "EXPIRE") return { result: 1 };
      if (op === "EVAL") { writes.push(command); return { result: JSON.stringify([{ role: "user", text: "What all can he do?" }, { role: "model", text: "catalogue answer" }]) }; }
      throw new Error("Unexpected Redis command: " + op);
    });
    return { ok: true, status: 200, text: async () => JSON.stringify(results) };
  };
  try {
    const res = response();
    await handler({
      method: "POST",
      headers: { authorization: "Bearer " + token },
      body: { message: "What all can he do?", deviceContext: { deviceType: "Android phone/tablet" } }
    }, res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.capabilityCatalogue, true);
    assert.match(res.body.reply, /initiate a phone call/i);
    assert.match(res.body.reply, /structured study plan/i);
    assert.deepEqual(res.body.toolCalls, []);
    assert.equal(writes.length, 1, "the capability response should be saved to shared conversation history");
  } finally {
    global.fetch = originalFetch;
    for (const key of keys) {
      if (original[key] === undefined) delete process.env[key];
      else process.env[key] = original[key];
    }
  }
});


test("medical red-flag emergencies bypass Gemini and return immediate emergency instructions", async () => {
  const originalFetch = global.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN", "JARVIS", "GEMINI_API_KEY"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  delete process.env.JARVIS;
  delete process.env.GEMINI_API_KEY;
  const writes = [];
  global.fetch = async (_url, options) => {
    const commands = JSON.parse(options.body);
    const results = commands.map(command => {
      const [op, key] = command;
      if (op === "GET" && key.includes(":session:")) return { result: JSON.stringify({ deviceId: "android-test-device" }) };
      if (op === "GET") return { result: null };
      if (op === "EXPIRE") return { result: 1 };
      if (op === "EVAL") { writes.push(command); return { result: JSON.stringify([]) }; }
      throw new Error("Unexpected Redis command: " + op);
    });
    return { ok: true, status: 200, text: async () => JSON.stringify(results) };
  };
  try {
    const res = response();
    await handler({ method: "POST", headers: { authorization: "Bearer emergency-test-token" }, body: { message: "My father is having chest pain right now" } }, res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.emergency, true);
    assert.equal(res.body.engine, "medical-emergency");
    assert.match(res.body.reply, /Call 112 now/);
    assert.match(res.body.reply, /108 for an ambulance in Kerala/);
    assert.deepEqual(res.body.toolCalls, []);
    assert.equal(writes.length, 1);
  } finally {
    global.fetch = originalFetch;
    for (const key of keys) {
      if (original[key] === undefined) delete process.env[key];
      else process.env[key] = original[key];
    }
  }
});


test("chat API streams quick conversational tokens and persists the completed turn", async () => {
  const originalFetch = global.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN", "JARVIS", "GEMINI_API_KEY"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS = "test-gemini-key";
  delete process.env.GEMINI_API_KEY;
  const encoder = new TextEncoder();
  let saved = false;
  global.fetch = async (url, options) => {
    if (String(url).includes("generativelanguage.googleapis.com")) {
      const chunks = [
        'data: {"candidates":[{"content":{"parts":[{"text":"The sky"}]}}]}\n\n',
        'data: {"candidates":[{"content":{"parts":[{"text":" looks blue."}]}}]}\n\n'
      ];
      let index = 0;
      return { ok: true, status: 200, body: new ReadableStream({
        pull(controller) {
          if (index < chunks.length) controller.enqueue(encoder.encode(chunks[index++]));
          else controller.close();
        }
      }) };
    }
    const commands = JSON.parse(options.body);
    const results = commands.map(command => {
      const [op, key] = command;
      if (op === "GET" && key.includes(":session:")) return { result: JSON.stringify({ deviceId: "stream-test-device" }) };
      if (op === "GET" && key.endsWith(":owner:token-hash")) return { result: null };
      if (op === "GET") return { result: null };
      if (op === "EXPIRE") return { result: 1 };
      if (op === "EVAL") { saved = true; return { result: JSON.stringify([{ role: "user", text: "Why is the sky blue?" }, { role: "model", text: "The sky looks blue." }]) }; }
      throw new Error("Unexpected Redis command: " + op);
    });
    return { ok: true, status: 200, text: async () => JSON.stringify(results) };
  };
  try {
    const res = response();
    res.chunks = [];
    res.write = function (chunk) { this.chunks.push(chunk); return true; };
    await handler({
      method: "POST",
      headers: { authorization: "Bearer stream-test-session" },
      body: { message: "Why is the sky blue?", stream: true }
    }, res);
    const wire = res.chunks.join("");
    assert.equal(res.headers["Content-Type"], "text/event-stream; charset=utf-8");
    assert.match(wire, /event: delta/);
    assert.match(wire, /The sky/);
    assert.match(wire, /looks blue/);
    assert.match(wire, /event: done/);
    assert.equal(saved, true);
    assert.equal(res.ended, true);
  } finally {
    global.fetch = originalFetch;
    for (const key of keys) {
      if (original[key] === undefined) delete process.env[key];
      else process.env[key] = original[key];
    }
  }
});


test("chat API streams progress and final result for complex tool-enabled turns", async () => {
  const originalFetch = global.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN", "JARVIS", "GEMINI_API_KEY"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS = "test-gemini-key";
  delete process.env.GEMINI_API_KEY;
  let saved = false;
  global.fetch = async (url, options) => {
    if (String(url).includes("generativelanguage.googleapis.com")) {
      return { ok: true, status: 200, text: async () => JSON.stringify({
        candidates: [{ content: { parts: [{ text: "Here is the comparison, Sir." }] } }]
      }) };
    }
    const commands = JSON.parse(options.body);
    const results = commands.map(command => {
      const [op, key] = command;
      if (op === "GET" && key.includes(":session:")) return { result: JSON.stringify({ deviceId: "progress-test-device" }) };
      if (op === "GET" && key.endsWith(":owner:token-hash")) return { result: null };
      if (op === "GET") return { result: null };
      if (op === "EXPIRE") return { result: 1 };
      if (op === "EVAL") { saved = true; return { result: JSON.stringify([]) }; }
      throw new Error("Unexpected Redis command: " + op);
    });
    return { ok: true, status: 200, text: async () => JSON.stringify(results) };
  };
  try {
    const res = response();
    res.chunks = [];
    res.write = function (chunk) { this.chunks.push(chunk); return true; };
    await handler({
      method: "POST",
      headers: { authorization: "Bearer progress-test-session" },
      body: { message: "Research and compare three options for a study plan.", stream: true }
    }, res);
    const wire = res.chunks.join("");
    assert.equal(res.headers["Content-Type"], "text/event-stream; charset=utf-8");
    const frames = wire.trim().split(/\r?\n\r?\n/);
    assert.ok(frames.length >= 2, "progress and final events must be separate SSE frames");
    for (const frame of frames) {
      const [eventLine, dataLine] = frame.split(/\r?\n/);
      assert.match(eventLine, /^event: [a-z]+$/);
      assert.match(dataLine, /^data: /);
      assert.doesNotThrow(() => JSON.parse(dataLine.slice(6)), "each SSE data line must contain valid JSON");
    }
    assert.match(wire, /event: progress/);
    assert.match(wire, /Breaking your request into steps/);
    assert.match(wire, /event: done/);
    assert.match(wire, /Here is the comparison/);
    assert.equal(saved, true);
    assert.equal(res.ended, true);
  } finally {
    global.fetch = originalFetch;
    for (const key of keys) {
      if (original[key] === undefined) delete process.env[key];
      else process.env[key] = original[key];
    }
  }
});
