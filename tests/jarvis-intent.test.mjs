import test from "node:test";
import assert from "node:assert/strict";
import { classifyJarvisIntent, buildIntentRoutingInstruction } from "../lib/jarvis-intent.js";

test("questions remain conversational and are never classified as music", () => {
  for (const text of [
    "What is artificial intelligence?",
    "Who is Tony Stark?",
    "Explain how neural networks work.",
    "Why does the calendar need a time?"
  ]) assert.equal(classifyJarvisIntent(text).intent, "conversation", text);
});

test("explicit phone calls route to calling", () => {
  assert.equal(classifyJarvisIntent("Call Amma").intent, "call");
});

test("calendar requests route to calendar rather than music", () => {
  for (const text of [
    "Put a date on the calendar",
    "Add an appointment to my calendar",
    "Schedule a meeting for tomorrow",
    "Remind me tomorrow"
  ]) assert.equal(classifyJarvisIntent(text).intent, "calendar", text);
});

test("message requests route to messaging", () => {
  assert.equal(classifyJarvisIntent("Send Amma a message").intent, "message");
});

test("music routing requires an explicit playback-style request", () => {
  assert.equal(classifyJarvisIntent("Play the latest Coldplay track").intent, "music");
  assert.equal(classifyJarvisIntent("What music did Tony Stark listen to?").intent, "conversation");
});

test("routing instructions explicitly protect conversation from music fallback", () => {
  assert.match(buildIntentRoutingInstruction("conversation"), /Do not turn it into a music search/i);
});
