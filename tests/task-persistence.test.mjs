import test from "node:test";
import assert from "node:assert/strict";
import { executeJarvisTool } from "../lib/jarvis-tools.js";
import { readTaskState } from "../lib/jarvis-store.js";

test("task-plan tools persist state and require evidence for completion", async () => {
  const originalFetch = global.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.example.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  const values = new Map();
  global.fetch = async (_url, options) => {
    const commands = JSON.parse(options.body);
    const results = commands.map(command => {
      const [op, key, value] = command;
      if (op === "SET") { values.set(key, value); return { result: "OK" }; }
      if (op === "GET") return { result: values.get(key) || null };
      throw new Error("Unexpected Redis command: " + op);
    });
    return { ok: true, status: 200, text: async () => JSON.stringify(results) };
  };
  try {
    const context = { userMessage: "Prepare a research brief with several steps.", taskPlan: null };
    const created = await executeJarvisTool("create_task_plan", {
      goal: "Prepare a verified research brief",
      steps: [
        { id: "research", title: "Gather reliable sources" },
        { id: "draft", title: "Write the brief", dependsOn: ["research"] }
      ]
    }, context);
    const id = created.taskPlan.id;
    assert.equal(created.taskPlan.steps.length, 2);
    assert.equal((await readTaskState(id)).id, id);
    await assert.rejects(() => executeJarvisTool("update_task_step", {
      taskId: id, stepId: "draft", status: "completed",
      evidence: "The report was drafted and checked against the source notes."
    }, context), /prerequisite/i);
    const updated = await executeJarvisTool("update_task_step", {
      taskId: id, stepId: "research", status: "completed",
      evidence: "Collected and checked 8 reliable sources from official publications."
    }, context);
    assert.equal(updated.progress.completed, 1);
    assert.equal((await readTaskState(id)).steps[0].status, "completed");
  } finally {
    global.fetch = originalFetch;
    for (const key of keys) {
      if (original[key] === undefined) delete process.env[key];
      else process.env[key] = original[key];
    }
  }
});
