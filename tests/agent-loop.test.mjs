import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/chat.js";
import { hashToken } from "../lib/jarvis-store.js";

const originalFetch = globalThis.fetch;
const originalKey = process.env.JARVIS;

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalKey === undefined) delete process.env.JARVIS;
  else process.env.JARVIS = originalKey;
});

function response() {
  return {
    headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    end() { return this; }
  };
}

test("chat agent executes an allow-listed tool and returns a confirmation-gated native action", async () => {
  process.env.JARVIS = "test-gemini-key";
  const token = "paired-test-token";
  const redisCalls = [];
  const modelCalls = [];
  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("generativelanguage.googleapis.com")) {
      const request = JSON.parse(options.body);
      modelCalls.push(request);
      if (modelCalls.length === 1) {
        return new Response(JSON.stringify({
          candidates: [{ content: { role: "model", parts: [{
            functionCall: { name: "request_alarm", args: { hour: 20, minute: 15, label: "Study" } }
          }] } }]
        }), { status: 200 });
      }
      assert.ok(request.contents.some(item => item.role === "function" &&
        item.parts.some(part => part.functionResponse?.name === "request_alarm" &&
          part.functionResponse.response?.requiresConfirmation === true)));
      return new Response(JSON.stringify({
        candidates: [{ content: { role: "model", parts: [{ text: "Sir, I have prepared the alarm. Please review it in Clock to confirm." }] } }]
      }), { status: 200 });
    }

    const command = JSON.parse(options.body)[0];
    redisCalls.push(command);
    let result = null;
    if (command[0] === "GET" && command[1] === "jarvis:v1:session:" + hashToken(token)) {
      result = JSON.stringify({ deviceId: "phone-test", deviceName: "Test phone", role: "owner" });
    } else if (command[0] === "GET") {
      result = null;
    } else if (command[0] === "EXPIRE") {
      result = 1;
    } else if (command[0] === "EVAL") {
      result = JSON.stringify([
        { role: "user", text: command[3], createdAt: 1 },
        { role: "model", text: command[4], createdAt: 1 }
      ]);
    } else {
      throw new Error("Unexpected Redis command: " + command[0]);
    }
    return new Response(JSON.stringify([{ result }]), { status: 200 });
  };

  const res = response();
  await handler({
    method: "POST",
    headers: { authorization: "Bearer " + token, origin: "https://hrishikeshks1611-stack.github.io" },
    body: { message: "Set an alarm for 8:15 PM called Study.", deviceContext: { localTime: "today" } }
  }, res);

  assert.equal(res.statusCode, 200);
  assert.equal(res.body.toolCalls[0].tool, "request_alarm");
  assert.equal(res.body.toolCalls[0].status, "confirmation_required");
  assert.deepEqual(res.body.pendingActions[0], { type: "alarm", hour: 20, minute: 15, label: "Study" });
  assert.match(res.body.reply, /review it in Clock/i);
  assert.equal(modelCalls.length, 2);
  assert.ok(redisCalls.some(command => command[0] === "EVAL"));
  assert.equal(redisCalls.some(command => command[0] === "SET"), false);
});
