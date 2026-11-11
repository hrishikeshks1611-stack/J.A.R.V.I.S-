import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/pair.js";
import { hashToken } from "../lib/jarvis-store.js";

const originalFetch = globalThis.fetch;
const originalUrl = process.env.UPSTASH_REDIS_REST_URL;
const originalToken = process.env.UPSTASH_REDIS_REST_TOKEN;

function makeResponse() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
}

function mockRedis(token, writes) {
  const commands = [];
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  globalThis.fetch = async (url, options = {}) => {
    const command = JSON.parse(options.body)[0];
    commands.push(command);
    let result = "OK";
    if (command[0] === "GET" && command[1] === "jarvis:v1:session:" + hashToken(token)) {
      result = JSON.stringify({ deviceId: "device-1", deviceName: "Test device", role: "owner" });
    } else if (command[0] === "EXPIRE") {
      result = 1;
    } else if (command[0] === "SET" && command[1] === "jarvis:v1:conversation:main") {
      writes.push(command);
    }
    return new Response(JSON.stringify([{ result }]), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  };
  return commands;
}

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalUrl === undefined) delete process.env.UPSTASH_REDIS_REST_URL;
  else process.env.UPSTASH_REDIS_REST_URL = originalUrl;
  if (originalToken === undefined) delete process.env.UPSTASH_REDIS_REST_TOKEN;
  else process.env.UPSTASH_REDIS_REST_TOKEN = originalToken;
});

test("conversation clear rejects a request without explicit confirmation and preserves history", async () => {
  const token = "test-paired-session-token";
  const writes = [];
  const commands = mockRedis(token, writes);
  const res = makeResponse();

  await handler({
    method: "POST",
    headers: {
      authorization: "Bearer " + token,
      origin: "https://hrishikeshks1611-stack.github.io"
    },
    body: { action: "clear" }
  }, res);

  assert.equal(res.statusCode, 400);
  assert.match(res.body.error, /confirm/i);
  assert.equal(writes.length, 0);
  assert.equal(commands.some(command => command[0] === "SET" && command[1] === "jarvis:v1:conversation:main"), false);
});

test("conversation clear writes the shared empty history only after explicit confirmation", async () => {
  const token = "test-paired-session-token";
  const writes = [];
  mockRedis(token, writes);
  const res = makeResponse();

  await handler({
    method: "POST",
    headers: {
      authorization: "Bearer " + token,
      origin: "https://hrishikeshks1611-stack.github.io"
    },
    body: { action: "clear", confirm: true }
  }, res);

  assert.equal(res.statusCode, 200);
  assert.deepEqual(res.body, { cleared: true });
  assert.equal(writes.length, 1);
  assert.equal(writes[0][2], "[]");
});
