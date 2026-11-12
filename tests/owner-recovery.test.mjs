import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import handler from "../api/pair.js";

const originalFetch = globalThis.fetch;
const originalEnv = {
  url: process.env.UPSTASH_REDIS_REST_URL,
  token: process.env.UPSTASH_REDIS_REST_TOKEN,
  recovery: process.env.JARVIS_OWNER_RECOVERY_CODE
};
const hash = value => createHash("sha256").update(value).digest("hex");

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  for (const [key, value] of Object.entries({
    UPSTASH_REDIS_REST_URL: originalEnv.url,
    UPSTASH_REDIS_REST_TOKEN: originalEnv.token,
    JARVIS_OWNER_RECOVERY_CODE: originalEnv.recovery
  })) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});

test("owner recovery requires a valid paired session and atomically preserves device records", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS_OWNER_RECOVERY_CODE = "a".repeat(64);
  const token = "paired-laptop-session";
  const commands = [];
  globalThis.fetch = async (_url, options) => {
    const redisCommands = JSON.parse(options.body);
    const command = redisCommands[0];
    commands.push(command);
    let result = 1;
    if (command[0] === "GET" && command[1] === "jarvis:v1:session:" + hash(token)) {
      result = JSON.stringify({ deviceId: "laptop-1", deviceName: "Laptop", role: "device" });
    } else if (command[0] === "INCR") result = 1;
    else if (command[0] === "EVAL") {
      assert.equal(command[2], "6");
      assert.equal(command[5], "jarvis:v1:owner:recovery:used");
      assert.ok(command[1].includes("oldSession.role = 'device'"));
      assert.ok(command[1].includes("current.role = 'owner'"));
      assert.ok(command[1].includes("currentDevice.role = 'owner'"));
      assert.ok(!command[1].includes("redis.call('DEL'"));
      result = JSON.stringify({ ok: true, deviceId: "laptop-1" });
    } else if (command[0] === "LPUSH" || command[0] === "LTRIM" || command[0] === "EXPIRE") result = 1;
    else throw new Error("Unexpected Redis command: " + command.join(" "));
    return new Response(JSON.stringify(redisCommands.map(() => ({ result }))), { status: 200 });
  };
  const res = {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
  await handler({
    method: "POST",
    headers: { authorization: "Bearer " + token, "user-agent": "JARVIS test" },
    body: { action: "recover-owner", recoveryCode: "a".repeat(64) }
  }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.recovered, true);
  assert.equal(res.body.role, "owner");
  assert.ok(commands.some(command => command[0] === "EVAL"));
});

test("owner recovery rejects a wrong credential before changing ownership", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS_OWNER_RECOVERY_CODE = "a".repeat(64);
  const token = "paired-laptop-session";
  const commands = [];
  globalThis.fetch = async (_url, options) => {
    const redisCommands = JSON.parse(options.body);
    const command = redisCommands[0];
    commands.push(command);
    let result = 1;
    if (command[0] === "GET" && command[1] === "jarvis:v1:session:" + hash(token)) result = JSON.stringify({ deviceId: "laptop-1", deviceName: "Laptop", role: "device" });
    else if (command[0] === "INCR") result = 1;
    else if (command[0] === "LPUSH" || command[0] === "LTRIM" || command[0] === "EXPIRE") result = 1;
    else throw new Error("Unexpected Redis command: " + command.join(" "));
    return new Response(JSON.stringify(redisCommands.map(() => ({ result }))), { status: 200 });
  };
  const res = { setHeader() {}, status(code) { this.statusCode = code; return this; }, json(value) { this.body = value; return this; }, end() { return this; } };
  await handler({ method: "POST", headers: { authorization: "Bearer " + token }, body: { action: "recover-owner", recoveryCode: "wrong" } }, res);
  assert.equal(res.statusCode, 401);
  assert.ok(!commands.some(command => command[0] === "EVAL"));
});
