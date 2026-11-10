import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("JARVIS web app inline script parses", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.lastIndexOf("<script>");\n  const end = html.lastIndexOf("</script>");\n  const scripts = start >= 0 && end > start ? [null, html.slice(start + 8, end)] : [];
  assert.ok(scripts.length > 0, "expected an inline application script");
  assert.doesNotThrow(() => new Function(scripts[scripts.length - 1][1]));
});

test("saved-memory UI is explicit and has deletion controls", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(html, /id="memoryButton"/);
  assert.match(html, /Only notes you explicitly save here/);
  assert.match(html, /action:'delete'/);
  assert.match(html, /action:'clear',confirm:true/);
});
