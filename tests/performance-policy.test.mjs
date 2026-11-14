import test from "node:test";
import assert from "node:assert/strict";
import { buildChatPerformancePolicy, createModelRequestBudget } from "../lib/jarvis-performance.js";

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
