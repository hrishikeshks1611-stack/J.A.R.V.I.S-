import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("JARVIS web app inline script parses", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.lastIndexOf("<script>");
  const end = html.lastIndexOf("</script>");
  const scripts = start >= 0 && end > start ? [html.slice(start + 8, end)] : [];
  assert.ok(scripts.length > 0, "expected an inline application script");
  assert.doesNotThrow(() => new Function(scripts[0]));
});

test("saved-memory UI is explicit and has deletion controls", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes('id="memoryButton"'));
  assert.ok(html.includes("Only notes you explicitly save here"));
  assert.ok(html.includes("action:'delete'"));
  assert.ok(html.includes("action:'clear',confirm:true"));
  assert.ok(html.includes('id="exportMemories"'));
  assert.ok(html.includes("action:'update'"));
});
