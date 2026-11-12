import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("Gemini calls have bounded timeouts and supported fallback models", async () => {
  const source = await readFile(new URL("../api/chat.js", import.meta.url), "utf8");
  assert.ok(source.includes("new AbortController()"));
  assert.ok(source.includes("setTimeout(() => controller.abort(), 6000)"));
  assert.ok(source.includes('"gemini-3.8-flash"'));
  assert.ok(source.includes('"gemini-3.5-flash-lite"'));
  assert.ok(source.includes('"gemini-3.5-flash"'));
  assert.ok(source.includes("[404, 429, 500, 503, 504].includes(response.status)"));
  assert.ok(source.includes("clearTimeout(timeout)"));
});

test("shared-memory calls cannot hold a chat request open indefinitely", async () => {
  const source = await readFile(new URL("../lib/jarvis-store.js", import.meta.url), "utf8");
  assert.ok(source.includes("new AbortController()"));
  assert.ok(source.includes("setTimeout(() => controller.abort(), 3500)"));
  assert.ok(source.includes("await redisPipeline(touches)"));
});
