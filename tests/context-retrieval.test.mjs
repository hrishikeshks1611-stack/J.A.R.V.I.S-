import test from "node:test";
import assert from "node:assert/strict";
import { selectRelevantContext } from "../lib/jarvis-context.js";

test("context retrieval selects topic-relevant shared history and preserves chronology", () => {
  const history = [
    { role: "user", text: "Help me choose a dark gold book cover." },
    { role: "model", text: "We can use a symbolic feather and flute." },
    { role: "user", text: "What is the weather tomorrow?" },
    { role: "model", text: "I need a location for a forecast." },
    { role: "user", text: "The book title should remain prominent." }
  ];
  const result = selectRelevantContext({ history, query: "Continue our book cover design" });
  assert.deepEqual(result.history.map(item => item.text), [
    "Help me choose a dark gold book cover.",
    "We can use a symbolic feather and flute.",
    "The book title should remain prominent."
  ]);
});

test("context retrieval returns only relevant saved memories, not every recent note", () => {
  const memories = [
    { id: "a", text: "Prefers dark grey and gold symbolic book covers." },
    { id: "b", text: "Uses an HP Windows 10 laptop with 4 GB RAM." },
    { id: "c", text: "Likes concise, honest feedback." }
  ];
  const result = selectRelevantContext({ memories, query: "Suggest a gold book cover concept" });
  assert.deepEqual(result.memories.map(item => item.id), ["a"]);
});

test("pronoun-style follow-ups retain a small recent history window", () => {
  const history = Array.from({ length: 10 }, (_, index) => ({
    role: index % 2 ? "model" : "user",
    text: "Turn " + index
  }));
  const result = selectRelevantContext({ history, query: "And what about that?" });
  assert.deepEqual(result.history.map(item => item.text), ["Turn 6", "Turn 7", "Turn 8", "Turn 9"]);
});

test("context selection is bounded and ignores malformed records", () => {
  const history = [
    null,
    { role: "system", text: "not an allowed role" },
    ...Array.from({ length: 30 }, (_, index) => ({ role: "user", text: "Jarvis holographic interface reactor " + index }))
  ];
  const memories = Array.from({ length: 40 }, (_, index) => ({ id: String(index), text: "JARVIS reactor interface memory " + index }));
  const result = selectRelevantContext({
    history,
    memories,
    query: "JARVIS holographic reactor",
    historyLimit: 5,
    memoryLimit: 3
  });
  assert.equal(result.history.length, 5);
  assert.equal(result.memories.length, 3);
  assert.ok(result.history.every(item => item.role === "user"));
});

test("empty or unrelated memory context does not fabricate a match", () => {
  const result = selectRelevantContext({
    history: [],
    memories: [{ text: "A note about cooking pasta." }],
    query: "JARVIS device pairing"
  });
  assert.deepEqual(result.history, []);
  assert.deepEqual(result.memories, []);
});
