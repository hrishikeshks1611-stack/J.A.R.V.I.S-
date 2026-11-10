import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/chat.js";

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { this.ended = true; return this; }
  };
}

test("chat API handles the approved web app preflight", async () => {
  const res = response();
  await handler({
    method: "OPTIONS",
    headers: { origin: "https://hrishikeshks1611-stack.github.io" }
  }, res);
  assert.equal(res.statusCode, 204);
  assert.equal(res.headers["Access-Control-Allow-Origin"], "https://hrishikeshks1611-stack.github.io");
  assert.match(res.headers["Access-Control-Allow-Headers"], /Authorization/);
});

test("chat API rejects unpaired devices before calling Gemini", async () => {
  const res = response();
  await handler({ method: "POST", headers: {}, body: { message: "Hello" } }, res);
  assert.equal(res.statusCode, 401);
  assert.match(res.body.error, /not paired/i);
});

test("chat API rejects unapproved origins", async () => {
  const res = response();
  await handler({
    method: "POST",
    headers: { origin: "https://untrusted.example" },
    body: { message: "Hello" }
  }, res);
  assert.equal(res.statusCode, 403);
});
