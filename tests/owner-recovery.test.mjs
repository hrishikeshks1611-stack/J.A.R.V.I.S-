import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/pair.js";

const originalFetch = globalThis.fetch;
const originalEnv = {
  url: process.env.UPSTASH_REDIS_REST_URL,
  token: process.env.UPSTASH_REDIS_REST_TOKEN,
  recovery: process.env.JARVIS_OWNER_RECOVERY_CODE
};

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

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
}

test("phone owner recovery works without an existing paired session and preserves device records", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS_OWNER_RECOVERY_CODE = "a".repeat(64);
  const commands = [];
  globalThis.fetch = async (_url, options) => {
    const command = JSON.parse(options.body)[0];
    commands.push(command);
    let result = 1;
    if (command[0] === "GET" && command[1] === "jarvis:v1:owner:claimed") result = "1";
    else if (command[0] === "INCR") result = 1;
    else if (command[0] === "EVAL") {
      assert.equal(command[2], "6");
      assert.equal(command[5], "jarvis:v1:owner:recovery:used");
      assert.ok(command[1].includes("oldSession.role = 'device'"));
      assert.ok(command[1].includes("current.role = 'owner'"));
      assert.ok(command[1].includes("currentDevice.role = 'owner'"));
      assert.ok(!command[1].includes("redis.call('DEL'"));
      result = JSON.stringify({ ok: true, deviceId: "phone-1" });
    }
    return new Response(JSON.stringify([{ result }]), { status: 200 });
  };
  const res = response();
  await handler({
    method: "POST",
    headers: { "user-agent": "JARVIS Android phone" },
    body: { action: "recover-owner", recoveryCode: "a".repeat(64), deviceName: "Android phone" }
  }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.recovered, true);
  assert.equal(res.body.role, "owner");
  assert.equal(res.body.deviceName, "Android phone");
  assert.ok(commands.some(command => command[0] === "EVAL"));
  assert.ok(commands.some(command => command[0] === "SADD" && command[1] === "jarvis:v1:devices"));
});

test("phone owner recovery rejects a wrong credential before creating a session", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.JARVIS_OWNER_RECOVERY_CODE = "a".repeat(64);
  const commands = [];
  globalThis.fetch = async (_url, options) => {
    const command = JSON.parse(options.body)[0];
    commands.push(command);
    return new Response(JSON.stringify([{ result: 1 }]), { status: 200 });
  };
  const res = response();
  await handler({
    method: "POST",
    headers: { "user-agent": "JARVIS Android phone" },
    body: { action: "recover-owner", recoveryCode: "wrong", deviceName: "Android phone" }
  }, res);
  assert.equal(res.statusCode, 401);
  assert.ok(!commands.some(command => command[0] === "EVAL"));
  assert.ok(!commands.some(command => command[0] === "SADD"));
});
