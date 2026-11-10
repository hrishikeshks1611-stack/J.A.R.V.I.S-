import test from "node:test";
import assert from "node:assert/strict";
import { hashToken, randomToken, safeEqual, saveHistory, readHistory, getSession, appendConversationTurn, readMemories, addMemory, updateMemory, deleteMemory, clearMemories, replaceBackup } from "../lib/jarvis-store.js";

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

test("getSession can validate a session without extending live Redis TTLs", async () => {
  const token = randomToken(32);
  const session = { deviceId: "read-only-device", role: "owner" };
  const calls = mockRedis(async command => {
    assert.equal(command[0], "GET");
    return JSON.stringify(session);
  });
  const result = await getSession({ headers: { authorization: "Bearer " + token } }, { touch: false });
  assert.equal(result.deviceId, session.deviceId);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], "GET");
});

test("getSession rejects missing bearer credentials without contacting Redis", async () => {
  const calls = mockRedis(async () => { throw new Error("Redis must not be called"); });
  const result = await getSession({ headers: {} });
  assert.equal(result, null);
  assert.equal(calls.length, 0);
});

test("appendConversationTurn uses one atomic Redis script and returns the updated history", async () => {
  let commandSeen;
  const expected = [
    { role: "user", text: "Hello from phone", createdAt: 1234 },
    { role: "model", text: "Good evening, Sir.", createdAt: 1234 }
  ];
  mockRedis(async command => {
    commandSeen = command;
    return JSON.stringify(expected);
  });
  const result = await appendConversationTurn("Hello from phone", "Good evening, Sir.", 1234);
  assert.deepEqual(result, expected);
  assert.equal(commandSeen[0], "EVAL");
  assert.equal(commandSeen[2], "1");
  assert.equal(commandSeen[3], "jarvis:v1:conversation:main");
  assert.ok(commandSeen[1].includes("redis.call('GET'"));
  assert.ok(commandSeen[1].includes("redis.call('SET'"));
  assert.match(commandSeen[1], /while #history > 40/);
  assert.equal(commandSeen[4], "Hello from phone");
  assert.equal(commandSeen[5], "Good evening, Sir.");
  assert.equal(commandSeen[6], "1234");
  assert.equal(commandSeen[7], String(60 * 60 * 24 * 365));
});

test("saved memories can be added, listed, deleted, and cleared", async () => {
  let calls = [];
  mockRedis(async command => {
    calls.push(command);
    if (command[0] === "GET") return JSON.stringify([{ id: "memory-1", text: "Prefers concise answers", createdAt: 1 }]);
    if (command[0] === "EVAL" && command[1].includes("table.insert(items")) {
      return JSON.stringify([{ id: "new-memory", text: command[5], createdAt: Number(command[6]) }]);
    }
    if (command[0] === "EVAL") return JSON.stringify({ deleted: true, items: [] });
    if (command[0] === "DEL") return 1;
    throw new Error("Unexpected Redis command: " + command[0]);
  });
  const listed = await readMemories();
  assert.equal(listed.length, 1);
  assert.equal(listed[0].text, "Prefers concise answers");
  const added = await addMemory("Likes the Algieba voice");
  assert.equal(added[0].text, "Likes the Algieba voice");
  const removed = await deleteMemory("memory-1");
  assert.equal(removed.deleted, true);
  assert.deepEqual(removed.items, []);
  assert.equal(await clearMemories(), true);
  assert.deepEqual(calls.map(command => command[0]), ["GET", "EVAL", "EVAL", "DEL"]);
});

test("saved memory text is trimmed and bounded before storage", async () => {
  let commandSeen;
  mockRedis(async command => {
    commandSeen = command;
    return JSON.stringify([{ id: "memory-2", text: command[5], createdAt: 1 }]);
  });
  await addMemory("  " + "a".repeat(1200) + "  ");
  assert.equal(commandSeen[5].length, 1000);
  assert.equal(commandSeen[5][0], "a");
});

test("saved memories can be edited without changing their identity", async () => {
  let commandSeen;
  mockRedis(async command => {
    commandSeen = command;
    return JSON.stringify({ updated: true, items: [{ id: "memory-7", text: "Updated preference", createdAt: 1, updatedAt: 2 }] });
  });
  const result = await updateMemory("memory-7", "Updated preference");
  assert.equal(result.updated, true);
  assert.equal(result.items[0].id, "memory-7");
  assert.equal(result.items[0].text, "Updated preference");
  assert.equal(commandSeen[0], "EVAL");
  assert.equal(commandSeen[4], "memory-7");
  assert.equal(commandSeen[5], "Updated preference");
  assert.ok(commandSeen[1].includes("item.text = ARGV[2]"));
});

test("full backup restore replaces conversation and memories atomically", async () => {
  let commandSeen;
  mockRedis(async command => {
    commandSeen = command;
    return 1;
  });
  const conversation = [{ role: "user", text: "Restored question", createdAt: 123 }];
  const memories = [{ id: "memory_1", text: "Restored note", createdAt: 456 }];
  const result = await replaceBackup(conversation, memories);
  assert.deepEqual(result, { conversation: 1, memories: 1 });
  assert.equal(commandSeen[0], "EVAL");
  assert.equal(commandSeen[2], "2");
  assert.equal(commandSeen[3], "jarvis:v1:conversation:main");
  assert.equal(commandSeen[4], "jarvis:v1:memory:main");
  assert.deepEqual(JSON.parse(commandSeen[5]), conversation);
  assert.deepEqual(JSON.parse(commandSeen[6]), memories);
  assert.equal(commandSeen[7], String(60 * 60 * 24 * 365));
  assert.ok(commandSeen[1].includes("redis.call('SET', KEYS[1]"));
  assert.ok(commandSeen[1].includes("redis.call('SET', KEYS[2]"));
});
