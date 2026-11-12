import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/backup.js";

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
    end() { this.ended = true; return this; }
  };
}

function setupRedis(handlerForCommand) {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const redisCommands = JSON.parse(options.body);
    const command = redisCommands[0];
    calls.push(command);
    const result = await handlerForCommand(command, calls.length);
    return new Response(JSON.stringify(redisCommands.map(() => ({ result }))), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  };
  return calls;
}

function sessionCommand(command) {
  if (command[0] === "GET" && command[1].startsWith("jarvis:v1:session:")) {
    return JSON.stringify({ deviceId: "phone-1", deviceName: "Phone", role: "owner" });
  }
  if (command[0] === "EXPIRE") return 1;
  return undefined;
}

test("backup export requires a paired device and returns conversation plus memories", async () => {
  const conversation = [{ role: "user", text: "Hello", createdAt: 1 }];
  const memories = [{ id: "memory_1", text: "Prefers Algieba", createdAt: 2 }];
  const calls = setupRedis(async command => {
    const auth = sessionCommand(command);
    if (auth !== undefined) return auth;
    if (command[0] === "GET" && command[1] === "jarvis:v1:conversation:main") return JSON.stringify(conversation);
    if (command[0] === "GET" && command[1] === "jarvis:v1:memory:main") return JSON.stringify(memories);
    throw new Error("Unexpected Redis command: " + command.join(" "));
  });
  const res = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: { action: "export" }
  }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.backup.format, "jarvis-backup");
  assert.equal(res.body.backup.version, 1);
  assert.deepEqual(res.body.backup.conversation, conversation);
  assert.deepEqual(res.body.backup.memories, memories);
  assert.equal(calls.length, 4, "GET session, pipelined TTL refresh, conversation, and memories");
});

test("backup restore requires explicit confirmation and uses one atomic Redis script", async () => {
  const backup = {
    format: "jarvis-backup",
    version: 1,
    conversation: [{ role: "user", text: "Restored question", createdAt: 123 }],
    memories: [{ id: "memory_1", text: "Restored note", createdAt: 456 }]
  };
  let evalCommand;
  setupRedis(async command => {
    const auth = sessionCommand(command);
    if (auth !== undefined) return auth;
    if (command[0] === "EVAL") {
      evalCommand = command;
      return 1;
    }
    throw new Error("Unexpected Redis command: " + command.join(" "));
  });
  const res = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: { action: "restore", backup, confirm: true }
  }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.restored, true);
  assert.deepEqual(res.body.counts, { conversation: 1, memories: 1 });
  assert.equal(evalCommand[0], "EVAL");
  assert.equal(evalCommand[2], "2");
  assert.equal(evalCommand[3], "jarvis:v1:conversation:main");
  assert.equal(evalCommand[4], "jarvis:v1:memory:main");
  assert.deepEqual(JSON.parse(evalCommand[5]), backup.conversation);
  assert.deepEqual(JSON.parse(evalCommand[6]), backup.memories);
  assert.ok(evalCommand[1].includes("redis.call('SET', KEYS[1]"));
  assert.ok(evalCommand[1].includes("redis.call('SET', KEYS[2]"));
});

test("only the owner device can restore a full backup", async () => {
  setupRedis(async command => {
    if (command[0] === "GET" && command[1].startsWith("jarvis:v1:session:")) {
      return JSON.stringify({ deviceId: "laptop-1", deviceName: "Laptop", role: "device" });
    }
    if (command[0] === "EXPIRE") return 1;
    throw new Error("A non-owner must not replace shared data.");
  });
  const res = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: {
      action: "restore",
      confirm: true,
      backup: { format: "jarvis-backup", version: 1, conversation: [], memories: [] }
    }
  }, res);
  assert.equal(res.statusCode, 403);
  assert.match(res.body.error, /owner device/i);
});

test("backup restore refuses missing confirmation and malformed documents", async () => {
  let evalCalls = 0;
  setupRedis(async command => {
    const auth = sessionCommand(command);
    if (auth !== undefined) return auth;
    if (command[0] === "EVAL") { evalCalls++; return 1; }
    throw new Error("Unexpected Redis command: " + command.join(" "));
  });
  const unconfirmed = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: { action: "restore", backup: { format: "jarvis-backup", version: 1, conversation: [], memories: [] } }
  }, unconfirmed);
  assert.equal(unconfirmed.statusCode, 400);
  assert.match(unconfirmed.body.error, /confirm/i);

  const malformed = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer paired-token" },
    body: {
      action: "restore",
      confirm: true,
      backup: { format: "wrong-format", version: 1, conversation: [], memories: [] }
    }
  }, malformed);
  assert.equal(malformed.statusCode, 400);
  assert.equal(evalCalls, 0);
});

test("backup API rejects unpaired devices and unapproved origins", async () => {
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  let contactedRedis = false;
  globalThis.fetch = async () => { contactedRedis = true; throw new Error("Unexpected Redis request"); };
  const unpaired = response();
  await handler({ method: "POST", headers: {}, body: { action: "export" } }, unpaired);
  assert.equal(unpaired.statusCode, 401);

  contactedRedis = false;
  const blocked = response();
  await handler({
    method: "POST",
    headers: { origin: "https://untrusted.example" },
    body: { action: "export" }
  }, blocked);
  assert.equal(blocked.statusCode, 403);
  assert.equal(contactedRedis, false);
});
