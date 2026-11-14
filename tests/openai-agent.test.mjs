import test from "node:test";
import assert from "node:assert/strict";
import { buildOpenAITools, runOpenAIAgent } from "../lib/openai-agent.js";

test("Gemini function declarations are translated to Responses API function tools", () => {
  const tools = buildOpenAITools([{ function_declarations: [{
    name: "calculate",
    description: "Calculate.",
    parameters: { type: "OBJECT", properties: { expression: { type: "STRING" } }, required: ["expression"] }
  }] }]);
  assert.equal(tools.length, 1);
  assert.equal(tools[0].type, "function");
  assert.equal(tools[0].name, "calculate");
  assert.equal(tools[0].parameters.type, "object");
  assert.equal(tools[0].parameters.properties.expression.type, "string");
});

test("OpenAI agent executes an approved function and returns the final response", async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (_url, options) => {
    const body = JSON.parse(options.body);
    requests.push(body);
    if (requests.length === 1) {
      return new Response(JSON.stringify({
        id: "resp_tool",
        output: [{ type: "function_call", call_id: "call_1", name: "calculate", arguments: JSON.stringify({ expression: "6*7" }) }]
      }), { status: 200 });
    }
    assert.ok(body.input.some(item => item.type === "function_call_output" && item.call_id === "call_1" && item.output.includes('"result":42')));
    return new Response(JSON.stringify({
      id: "resp_final",
      output_text: "The answer is 42.",
      output: [{ type: "message", content: [{ type: "output_text", text: "The answer is 42." }] }]
    }), { status: 200 });
  };
  try {
    const result = await runOpenAIAgent({
      apiKey: "test-secret",
      model: "gpt-6-astra",
      system: "You are JARVIS.",
      message: "Calculate 6 times 7.",
      prior: [],
      toolDeclarations: [{ function_declarations: [{ name: "calculate", description: "Calculate.", parameters: { type: "OBJECT", properties: { expression: { type: "STRING" } }, required: ["expression"] } }] }],
      executeTool: async (_name, args) => ({ ok: true, result: Number(args.expression.split("*")[0]) * Number(args.expression.split("*")[1]) })
    });
    assert.equal(result.reply, "The answer is 42.");
    assert.deepEqual(result.toolTrace, [{ tool: "calculate", status: "success" }]);
    assert.equal(requests[0].model, "gpt-6-astra");
    assert.equal(requests[0].reasoning.effort, "low");
    assert.equal(JSON.stringify(requests).includes("test-secret"), false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
