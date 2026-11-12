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

test("local app actions are appended to the shared conversation", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const commands = JSON.parse(options.body);
    const results = [];
    for (const command of commands) {
      calls.push(command);
      let result;
      if (command[0] === "GET") result = JSON.stringify({ deviceId: "phone-1", deviceName: "Phone", role: "device" });
      else if (command[0] === "EXPIRE") result = 1;
      else if (command[0] === "EVAL") {
        assert.ok(command[1].includes("redis.call('SET'"));
        assert.equal(command[4], "Open YouTube");
        assert.equal(command[5], "Opening YouTube, Sir.");
        result = JSON.stringify([
          { role: "user", text: "Open YouTube", createdAt: 1 },
          { role: "model", text: "Opening YouTube, Sir.", createdAt: 1 }
        ]);
      } else throw new Error("Unexpected Redis command: " + command[0]);
      results.push({ result });
    }
    return new Response(JSON.stringify(results), { status: 200 });
  };
  const res = {
    setHeader() {},
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: { action: "append-local", message: "Open YouTube", reply: "Opening YouTube, Sir." }
  }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.saved, true);
  assert.equal(calls.at(-1)[0], "EVAL");
});
