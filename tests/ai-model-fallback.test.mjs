import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("Gemini model fallback continues when a configured model is unavailable", async () => {
  const chat = await readFile(new URL("../api/chat.js", import.meta.url), "utf8");
  assert.ok(chat.includes('const models = ["gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash"]'));
  assert.ok(chat.includes("[404, 429, 500, 503, 504].includes(response.status)"));
});
