import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/memory.js";

const originalFetch = globalThis.fetch;
const originalUrl = process.env.UPSTASH_REDIS_REST_URL;
const originalToken = process.env.UPSTASH_REDIS_REST_TOKEN;

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalUrl === undefined) delete process.env.UPSTASH_REDIS_REST_URL;
  else process.env.UPSTASH_REDIS_REST_URL = originalUrl;
  if (originalToken === undefined) delete process.env.UPSTASH_REDIS_REST_TOKEN;
  else process.env.UPSTASH_REDIS_REST_TOKEN = originalToken;
});

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
}

test("saved-memory API rejects unpaired devices", async () => {
  const res = response();
  await handler({ method: "POST", headers: {}, body: { action: "list" } }, res);
  assert.equal(res.statusCode, 401);
  assert.match(res.body.error, /not paired/i);
});

test("saved-memory API blocks unapproved origins before accessing private data", async () => {
  let contactedRedis = false;
  globalThis.fetch = async () => { contactedRedis = true; throw new Error("Redis must not be contacted"); };
  const res = response();
  await handler({
    method: "POST",
    headers: { origin: "https://untrusted.example" },
    body: { action: "list" }
  }, res);
  assert.equal(res.statusCode, 403);
  assert.equal(contactedRedis, false);
});

test("clearing saved memories requires an explicit confirmation flag", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  let calls = 0;
  globalThis.fetch = async (url, options) => {
    const command = JSON.parse(options.body)[0];
    calls++;
    let result;
    if (command[0] === "GET") result = JSON.stringify({ deviceId: "phone-1", deviceName: "Phone", role: "device" });
    else if (command[0] === "EXPIRE") result = 1;
    else throw new Error("Memory deletion must not happen without confirmation");
    return new Response(JSON.stringify([{ result }]), { status: 200 });
  };
  const res = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: { action: "clear", confirm: false }
  }, res);
  assert.equal(res.statusCode, 400);
  assert.match(res.body.error, /confirm/i);
  assert.equal(calls, 3);
});
