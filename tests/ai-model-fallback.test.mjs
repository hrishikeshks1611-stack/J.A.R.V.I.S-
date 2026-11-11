import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("Gemini model fallback continues when a configured model is unavailable", async () => {
  const chat = await readFile(new URL("../api/chat.js", import.meta.url), "utf8");
  assert.ok(chat.includes('const models = ["gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-flash-latest"]'));
  assert.ok(chat.includes("response.status === 503 || response.status === 429 || response.status === 404"));
});
