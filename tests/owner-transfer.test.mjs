import test from "node:test";
import { createHash } from "node:crypto";
import assert from "node:assert/strict";
import handler from "../api/pair.js";

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

test("owner transfer is authorised and performed by one atomic Redis script", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  const token = "owner-secret-token";
  const targetHash = "target-session-hash";
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const redisCommands = JSON.parse(options.body);
    const command = redisCommands[0];
    calls.push(command);
    let result;
    if (command[0] === "GET" && command[1].includes(":session:")) {
      result = JSON.stringify({ deviceId: "owner-device", deviceName: "Phone", role: "owner" });
    } else if (command[0] === "GET" && command[1] === "jarvis:v1:owner:token-hash") {
      result = createHash("sha256").update(token).digest("hex");
    } else if (command[0] === "EXPIRE") {
      result = 1;
    } else if (command[0] === "GET" && command[1] === "jarvis:v1:device:laptop-device") {
      result = JSON.stringify({ deviceId: "laptop-device", deviceName: "Laptop", role: "device", sessionHash: targetHash });
    } else if (["LPUSH", "LTRIM", "EXPIRE"].includes(command[0])) {
      result = 1;
    } else if (command[0] === "EVAL") {
      assert.equal(command[2], "5");
      assert.equal(command[3], "jarvis:v1:owner:token-hash");
      assert.ok(command[1].includes("target.role = 'owner'"));
      assert.ok(command[1].includes("current.role = 'device'"));
      assert.equal(command[9], targetHash);
      result = JSON.stringify({ ok: true, ownerDeviceId: "laptop-device", previousOwnerDeviceId: "owner-device" });
    } else {
      throw new Error("Unexpected Redis command: " + command.join(" "));
    }
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
    headers: { authorization: "Bearer " + token },
    body: { action: "transfer-owner", deviceId: "laptop-device" }
  }, res);

  assert.equal(res.statusCode, 200);
  assert.equal(res.body.transferred, true);
  assert.equal(res.body.ownerDeviceId, "laptop-device");
  assert.ok(calls.some(command => command[0] === "EVAL"));
});


test("verify resolves owner from the authoritative owner token even when session role is stale", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  const token = "stale-role-owner-token";
  const ownerHash = createHash("sha256").update(token).digest("hex");
  globalThis.fetch = async (_url, options) => {
    const commands = JSON.parse(options.body);
    const command = commands[0];
    let result;
    if (command[0] === "GET" && command[1] === "jarvis:v1:session:" + ownerHash) {
      result = JSON.stringify({ deviceId: "physical-owner-phone", deviceName: "Android phone", role: "device" });
    } else if (command[0] === "GET" && command[1] === "jarvis:v1:owner:token-hash") {
      result = ownerHash;
    } else if (command[0] === "EXPIRE") {
      result = 1;
    } else {
      throw new Error("Unexpected Redis command: " + command.join(" "));
    }
    return new Response(JSON.stringify(commands.map(() => ({ result }))), { status: 200 });
  };
  const res = {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
  await handler({ method: "POST", headers: { authorization: "Bearer " + token }, body: { action: "verify" } }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.deviceId, "physical-owner-phone");
  assert.equal(res.body.role, "owner");
});
