import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/openai-live-token.js";

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { this.ended = true; return this; }
  };
}

test("OpenAI Live Voice endpoint handles the approved app preflight", async () => {
  const res = response();
  await handler({ method: "OPTIONS", headers: { origin: "https://hrishikeshks1611-stack.github.io" } }, res);
  assert.equal(res.statusCode, 204);
  assert.equal(res.headers["Access-Control-Allow-Origin"], "https://hrishikeshks1611-stack.github.io");
  assert.equal(res.headers["Cache-Control"], "no-store");
});

test("OpenAI Live Voice endpoint rejects untrusted origins", async () => {
  const res = response();
  await handler({ method: "POST", headers: { origin: "https://untrusted.example" } }, res);
  assert.equal(res.statusCode, 403);
});

test("OpenAI Live Voice endpoint rejects unpaired devices before issuing credentials", async () => {
  const res = response();
  await handler({ method: "POST", headers: {} }, res);
  assert.equal(res.statusCode, 401);
  assert.match(res.body.error, /not paired/i);
});
