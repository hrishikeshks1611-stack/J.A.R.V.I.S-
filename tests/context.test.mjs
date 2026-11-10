import test from "node:test";
import assert from "node:assert/strict";
import { selectConversationContext, selectRelevantMemories, isFollowUpMessage, parseExplicitMemoryRequest } from "../lib/jarvis-context.js";

test("conversation context keeps recent turns and retrieves relevant older exchanges", () => {
  const history = [
    { role: "user", text: "I am learning guitar and kickboxing." },
    { role: "model", text: "I can help you practise both." },
    { role: "user", text: "Explain photosynthesis." },
    { role: "model", text: "Plants convert light into chemical energy." },
    { role: "user", text: "What is the capital of Japan?" },
    { role: "model", text: "Tokyo." },
    { role: "user", text: "Tell me about guitar chord transitions." },
    { role: "model", text: "Practise slow changes between open chords." }
  ];
  const selected = selectConversationContext(history, "Can you make a guitar practice plan?", { recentLimit: 4, relevantLimit: 2 });
  assert.ok(selected.some(item => item.text.includes("learning guitar")));
  assert.ok(selected.some(item => item.text.includes("chord transitions")));
  assert.ok(selected.some(item => item.text.includes("Tokyo")));
  assert.ok(selected.length <= 8);
});

test("follow-up messages retain a wider recent conversational window", () => {
  assert.equal(isFollowUpMessage("What about the second one?"), true);
  assert.equal(isFollowUpMessage("Please explain quantum computing in detail"), false);
  assert.equal(isFollowUpMessage("What is the capital of Japan?"), false);
});

test("memory retrieval selects relevant notes rather than blindly returning newest notes", () => {
  const memories = [
    { id: "m1", text: "Prefers the Algieba British butler voice." },
    { id: "m2", text: "Writes mythological and psychological fiction." },
    { id: "m3", text: "Uses an Android television." },
    { id: "m4", text: "Prefers concise, candid feedback." }
  ];
  const selected = selectRelevantMemories(memories, "Keep the British butler voice for JARVIS.");
  assert.equal(selected.length, 1);
  assert.match(selected[0].text, /Algieba British butler voice/);
});

test("memory retrieval returns no unrelated personal notes for generic greetings", () => {
  const memories = [{ text: "Prefers the Algieba British butler voice." }];
  assert.deepEqual(selectRelevantMemories(memories, "Hello there"), []);
});

test("only explicit save wording creates a long-term memory request", () => {
  assert.equal(parseExplicitMemoryRequest("Remember that I prefer concise answers."), "I prefer concise answers.");
  assert.equal(parseExplicitMemoryRequest("Remember I prefer concise answers."), "I prefer concise answers.");
  assert.equal(parseExplicitMemoryRequest("Please keep in mind that I use JARVIS on my phone."), "I use JARVIS on my phone.");
  assert.equal(parseExplicitMemoryRequest("I prefer concise answers."), null);
  assert.equal(parseExplicitMemoryRequest("Remember to remind me tomorrow at 7."), null);
});
