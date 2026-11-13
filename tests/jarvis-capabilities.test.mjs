import test from "node:test";
import assert from "node:assert/strict";
import {
  ACTION_STATES,
  JARVIS_CAPABILITIES,
  createJarvisAction,
  getJarvisCapability,
  isJarvisCapabilityAvailable,
  listJarvisCapabilities,
  transitionJarvisAction
} from "../lib/jarvis-capabilities.js";

test("capability catalogue has unique stable identifiers", () => {
  const ids = JARVIS_CAPABILITIES.map(item => item.id);
  assert.equal(new Set(ids).size, ids.length);
  assert.ok(ids.includes("offline_wake_word"));
  assert.ok(ids.includes("youtube_music_exact_track"));
  assert.ok(ids.includes("holographic_command_station"));
});

test("prototype and planned features are not advertised as available", () => {
  assert.equal(isJarvisCapabilityAvailable("offline_wake_word", "android"), false);
  assert.equal(isJarvisCapabilityAvailable("durable_reminders_and_schedules", "android"), false);
  assert.equal(isJarvisCapabilityAvailable("conversation", "android"), true);
  assert.equal(isJarvisCapabilityAvailable("conversation", "desktop"), false);
});

test("capability queries filter by area, status and surface", () => {
  assert.equal(getJarvisCapability("conversation").area, "intelligence");
  assert.equal(getJarvisCapability("not-a-capability"), null);
  assert.ok(listJarvisCapabilities({ area: "voice" }).length >= 2);
  assert.ok(listJarvisCapabilities({ status: "planned" }).every(item => item.status === "planned"));
  assert.ok(listJarvisCapabilities({ surface: "tv" }).every(item => item.surfaces.includes("tv")));
});

test("action lifecycle requires approval and evidence before verification", () => {
  let action = createJarvisAction({ id: "a1", tool: "request_alarm", requiresApproval: true, createdAt: 100 });
  assert.equal(action.state, "awaiting_approval");
  action = transitionJarvisAction(action, "handed_off", {}, 110);
  action = transitionJarvisAction(action, "executed", {}, 120);
  assert.throws(() => transitionJarvisAction(action, "verified", {}, 130), /verification evidence/);
  action = transitionJarvisAction(action, "verified", { evidence: "Clock activity confirmed by the native app." }, 140);
  assert.equal(action.state, "verified");
  assert.equal(action.updatedAt, 140);
  assert.throws(() => transitionJarvisAction(action, "failed", { error: "late failure" }), /cannot be changed/);
});

test("action lifecycle rejects unsafe skips and requires failure reasons", () => {
  const action = createJarvisAction({ tool: "open_track" });
  assert.equal(action.state, "proposed");
  assert.throws(() => transitionJarvisAction(action, "verified", { evidence: "it probably worked" }), /Invalid JARVIS action transition/);
  assert.throws(() => transitionJarvisAction(action, "failed"), /failure reason/);
  assert.throws(() => transitionJarvisAction(action, "nonsense"), /Invalid JARVIS action transition/);
  assert.deepEqual(ACTION_STATES, ["proposed", "awaiting_approval", "handed_off", "executed", "verified", "failed", "cancelled"]);
});
