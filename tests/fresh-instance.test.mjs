import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const moduleUrl = new URL("../lib/jarvis-store.js", import.meta.url).href;

function loadStore(env) {
  return spawnSync(process.execPath, ["--input-type=module", "-e", `await import(${JSON.stringify(moduleUrl)})`], {
    cwd: root,
    encoding: "utf8",
    env: { ...process.env, UPSTASH_REDIS_REST_URL: "https://redis.example.test", UPSTASH_REDIS_REST_TOKEN: "test-token", ...env }
  });
}

test("fresh instance refuses to start without an explicitly isolated memory prefix", () => {
  const result = loadStore({ JARVIS_FRESH_INSTANCE: "true", JARVIS_PREFIX: "" });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /dedicated JARVIS_PREFIX/);
});

test("fresh instance starts with a dedicated namespace and does not use the legacy prefix", () => {
  const result = loadStore({ JARVIS_FRESH_INSTANCE: "true", JARVIS_PREFIX: "jarvis:fresh:16:2026:" });
  assert.equal(result.status, 0, result.stderr);
});

test("ordinary existing deployments retain their legacy default namespace", () => {
  const result = loadStore({ JARVIS_FRESH_INSTANCE: "false", JARVIS_PREFIX: "" });
  assert.equal(result.status, 0, result.stderr);
});
