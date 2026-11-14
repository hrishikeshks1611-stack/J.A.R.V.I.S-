import test from "node:test";
import assert from "node:assert/strict";
import { buildChatPerformancePolicy, createModelRequestBudget } from "../lib/jarvis-performance.js";
import { readConversationContext } from "../lib/jarvis-store.js";

test("short conversational turns receive a fast response budget", () => {
  const policy = buildChatPerformancePolicy("Why is the sky blue?");
  assert.equal(policy.tier, "quick");
  assert.equal(policy.maxOutputTokens, 700);
  assert.equal(policy.maxModelSteps, 3);
  assert.equal(policy.maxToolCalls, 3);
});

test("multi-step actions and attachments retain the full reasoning budget", () => {
  const action = buildChatPerformancePolicy("Research and compare three laptops, then prepare a purchase plan.");
  const attachment = buildChatPerformancePolicy("What is in this?", { hasAttachment: true });
  for (const policy of [action, attachment]) {
    assert.equal(policy.tier, "full");
    assert.equal(policy.maxOutputTokens, 1400);
    assert.equal(policy.maxModelSteps, 5);
    assert.equal(policy.maxToolCalls, 6);
  }
});

test("all provider attempts share a hard wall-clock budget", () => {
  const budget = createModelRequestBudget({ startedAt: 1000, totalBudgetMs: 30000, perCallTimeoutMs: 12000 });
  assert.equal(budget.remainingMs(1000), 30000);
  assert.equal(budget.timeoutForNextCall(1000), 12000);
  assert.equal(budget.timeoutForNextCall(22000), 8000);
  assert.equal(budget.timeoutForNextCall(29500), 0);
  assert.equal(budget.remainingMs(32000), 0);
});

test("shared history and saved memories load in one Redis pipeline round trip", async () => {
  const originalFetch = global.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  let calls = 0;
  global.fetch = async (_url, options) => {
    calls++;
    const commands = JSON.parse(options.body);
    assert.deepEqual(commands, [
      ["GET", "jarvis:v1:conversation:main"],
      ["GET", "jarvis:v1:memory:main"]
    ]);
    return {
      ok: true,
      status: 200,
      text: async () => JSON.stringify([
        { result: JSON.stringify([{ role: "user", text: "hello" }]) },
        { result: JSON.stringify([{ id: "m1", text: "prefers concise replies" }]) }
      ])
    };
  };
  try {
    const result = await readConversationContext();
    assert.equal(calls, 1);
    assert.equal(result.history[0].text, "hello");
    assert.equal(result.memories[0].text, "prefers concise replies");
  } finally {
    global.fetch = originalFetch;
    for (const key of keys) {
      if (original[key] === undefined) delete process.env[key];
      else process.env[key] = original[key];
    }
  }
});
