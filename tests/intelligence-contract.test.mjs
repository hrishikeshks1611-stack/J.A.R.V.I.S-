import test from "node:test";
import assert from "node:assert/strict";
import { buildJarvisSystemInstruction } from "../lib/jarvis-personality.js";

test("JARVIS intelligence contract classifies goals, plans dependencies, and verifies results", () => {
  const instruction = buildJarvisSystemInstruction("jarvis");
  assert.match(instruction, /direct answer, information retrieval, or an action/i);
  assert.match(instruction, /desired end state/i);
  assert.match(instruction, /dependencies/i);
  assert.match(instruction, /Verify intermediate results/i);
  assert.match(instruction, /Do not expose private chain-of-thought/i);
});

test("JARVIS intelligence contract avoids repeated questions and distinguishes evidence", () => {
  const instruction = buildJarvisSystemInstruction("professional");
  assert.match(instruction, /do not make the user repeat context/i);
  assert.match(instruction, /verified facts, estimates, and unknowns/i);
  assert.match(instruction, /trade-offs/i);
});
