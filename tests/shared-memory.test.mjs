import test from "node:test";
import assert from "node:assert/strict";
import { hashToken, randomToken, safeEqual, saveHistory, readHistory, getSession } from "../lib/jarvis-store.js";

const originalFetch = globalThis.fetch;
const originalUrl = process.env.UPSTASH_REDIS_REST_URL;
const originalToken = process.env.UPSTASH_REDIS_REST_TOKEN;

function mockRedis(handler) {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const commands = JSON.parse(options.body);
    calls.push(commands[0]);
    const result = await handler(commands[0], calls.length);
    return new Response(JSON.stringify([{ result }]), { status: 200, headers: { "content-type": "application/json" } });
  };
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  return calls;
}

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalUrl === undefined) delete process.env.UPSTASH_REDIS_REST_URL;
  else process.env.UPSTASH_REDIS_REST_URL = originalUrl;
  if (originalToken === undefined) delete process.env.UPSTASH_REDIS_REST_TOKEN;
  else process.env.UPSTASH_REDIS_REST_TOKEN = originalToken;
});

test("token helpers produce opaque tokens and compare safely", () => {
  const token = randomToken(32);
  assert.equal(token.length, 43);
  assert.equal(hashToken(token), hashToken(token));
  assert.notEqual(hashToken(token), token);
  assert.equal(safeEqual("same", "same"), true);
  assert.equal(safeEqual("same", "different"), false);
  assert.equal(safeEqual("short", "longer"), false);
});

test("saveHistory enforces the 40-message and field-size bounds", async () => {
  let saved;
  mockRedis(async command => {
    assert.equal(command[0], "SET");
    saved = JSON.parse(command[2]);
    return "OK";
  });
  const input = Array.from({ length: 45 }, (_, i) => ({
    role: i % 2 ? "model" : "user",
    text: "x".repeat(13000),
    createdAt: i + 1
  }));
  const result = await saveHistory(input);
  assert.equal(result.length, 40);
  assert.equal(saved.length, 40);
  assert.equal(result[0].createdAt, 6);
  assert.equal(result[0].text.length, 12000);
  assert.ok(result.every(item => item.role === "user" || item.role === "model"));
});

test("readHistory returns the bounded stored conversation", async () => {
  const stored = Array.from({ length: 42 }, (_, i) => ({ role: "user", text: String(i), createdAt: i }));
  mockRedis(async command => {
    assert.equal(command[0], "GET");
    return JSON.stringify(stored);
  });
  const history = await readHistory();
  assert.equal(history.length, 40);
  assert.equal(history[0].text, "2");
  assert.equal(history[39].text, "41");
});

test("getSession validates the bearer token and refreshes session TTLs", async () => {
  const token = randomToken(32);
  const session = { deviceId: "device-123", deviceName: "Test phone", role: "owner" };
  const calls = mockRedis(async (command, index) => {
    if (index === 1) {
      assert.equal(command[0], "GET");
      assert.equal(command[1], "jarvis:v1:session:" + hashToken(token));
      return JSON.stringify(session);
    }
    assert.equal(command[0], "EXPIRE");
    return 1;
  });
  const result = await getSession({ headers: { authorization: "Bearer " + token } });
  assert.equal(result.deviceId, session.deviceId);
  assert.equal(result.role, "owner");
  assert.equal(result.token, token);
  assert.equal(calls.length, 3);
});

test("getSession rejects missing bearer credentials without contacting Redis", async () => {
  const calls = mockRedis(async () => { throw new Error("Redis must not be called"); });
  const result = await getSession({ headers: {} });
  assert.equal(result, null);
  assert.equal(calls.length, 0);
});
