import test from "node:test";
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
    const command = JSON.parse(options.body)[0];
    calls.push(command);
    let result;
    if (command[0] === "GET" && command[1].includes(":session:")) {
      result = JSON.stringify({ deviceId: "owner-device", deviceName: "Phone", role: "owner" });
    } else if (command[0] === "EXPIRE") {
      result = 1;
    } else if (command[0] === "GET" && command[1] === "jarvis:v1:device:laptop-device") {
      result = JSON.stringify({ deviceId: "laptop-device", deviceName: "Laptop", role: "device", sessionHash: targetHash });
    } else if (command[0] === "EVAL") {
      assert.equal(command[2], "5");
      assert.ok(command[1].includes("owner:token-hash") === false);
      assert.ok(command[1].includes("target.role = 'owner'"));
      assert.ok(command[1].includes("current.role = 'device'"));
      assert.equal(command[7], targetHash);
      result = JSON.stringify({ ok: true, ownerDeviceId: "laptop-device", previousOwnerDeviceId: "owner-device" });
    } else {
      throw new Error("Unexpected Redis command: " + command.join(" "));
    }
    return new Response(JSON.stringify([{ result }]), { status: 200 });
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
  assert.equal(calls.at(-1)[0], "EVAL");
});
