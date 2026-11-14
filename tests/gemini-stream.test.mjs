import test from "node:test";
import assert from "node:assert/strict";
import { streamGeminiText } from "../lib/gemini-stream.js";

test("Gemini streaming emits text deltas as SSE events arrive", async () => {
  const originalFetch = global.fetch;
  const encoder = new TextEncoder();
  const chunks = [
    'data: {"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}\n\n',
    'data: {"candidates":[{"content":{"parts":[{"text":" there, Sir."}]}}]}\n\n'
  ];
  global.fetch = async (url, options) => {
    assert.match(String(url), /streamGenerateContent\\?alt=sse/);
    assert.equal(options.method, "POST");
    let index = 0;
    return {
      ok: true, status: 200,
      body: new ReadableStream({
        pull(controller) {
          if (index < chunks.length) controller.enqueue(encoder.encode(chunks[index++]));
          else controller.close();
        }
      })
    };
  };
  try {
    const deltas = [];
    const reply = await streamGeminiText({
      apiKey: "test-key", model: "test-model", system: "Be concise.",
      contents: [{ role: "user", parts: [{ text: "Say hello." }] }],
      onDelta: value => deltas.push(value)
    });
    assert.equal(reply, "Hello there, Sir.");
    assert.deepEqual(deltas, ["Hello", " there, Sir."]);
  } finally {
    global.fetch = originalFetch;
  }
});

test("Gemini streaming surfaces upstream failures without inventing an answer", async () => {
  const originalFetch = global.fetch;
  global.fetch = async () => ({ ok: false, status: 429, text: async () => "quota exceeded" });
  try {
    await assert.rejects(() => streamGeminiText({
      apiKey: "test-key", model: "test-model", system: "Be concise.", contents: []
    }), /quota exceeded/);
  } finally {
    global.fetch = originalFetch;
  }
});
