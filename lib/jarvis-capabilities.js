// Central, machine-readable capability catalogue for the unified J.A.R.V.I.S. brain.
// Status describes the current codebase evidence, not a promise that every device supports it.
export const ACTION_STATES = Object.freeze([
  "proposed",
  "awaiting_approval",
  "handed_off",
  "executed",
  "verified",
  "failed",
  "cancelled"
]);

const TERMINAL_STATES = new Set(["verified", "failed", "cancelled"]);
const TRANSITIONS = Object.freeze({
  proposed: new Set(["awaiting_approval", "handed_off", "failed", "cancelled"]),
  awaiting_approval: new Set(["handed_off", "failed", "cancelled"]),
  handed_off: new Set(["executed", "failed", "cancelled"]),
  executed: new Set(["verified", "failed"]),
  verified: new Set(),
  failed: new Set(),
  cancelled: new Set()
});

export const JARVIS_CAPABILITIES = Object.freeze([
  { id: "conversation", area: "intelligence", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "none" },
  { id: "bounded_tool_reasoning", area: "intelligence", status: "implemented", surfaces: ["backend"], offline: false, approval: "per-tool" },
  { id: "personality_modes", area: "presence", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: true, approval: "none" },
  { id: "shared_conversation", area: "memory", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "none" },
  { id: "user_saved_memory", area: "memory", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "user-controlled" },
  { id: "pairing_and_device_revocation", area: "security", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "owner-only" },
  { id: "backup_and_restore", area: "security", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "owner-confirmation" },
  { id: "office_file_generation", area: "productivity", status: "implemented", surfaces: ["android", "pwa"], offline: false, approval: "user-request" },
  { id: "education_engine", area: "education", status: "partial", surfaces: ["backend"], offline: false, approval: "user-request" },
  { id: "study_plan_builder", area: "education", status: "implemented", surfaces: ["backend"], offline: true, approval: "user-request" },
  { id: "camera_capture", area: "device", status: "implemented", surfaces: ["android"], offline: true, approval: "os-permission" },
  { id: "android_app_launch", area: "device", status: "partial", surfaces: ["android"], offline: true, approval: "user-request" },
  { id: "youtube_music_exact_track", area: "media", status: "partial", surfaces: ["android"], offline: false, approval: "user-request" },
  { id: "media_session_playback_verification", area: "media", status: "partial", surfaces: ["android"], offline: true, approval: "os-permission" },
  { id: "clock_and_calendar_handoff", area: "productivity", status: "partial", surfaces: ["android"], offline: true, approval: "native-app-confirmation" },
  { id: "offline_wake_word", area: "voice", status: "prototype", surfaces: ["android"], offline: true, approval: "explicit-microphone-toggle" },
  { id: "online_british_voice", area: "voice", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "none" },
  { id: "local_offline_voice_fallback", area: "voice", status: "partial", surfaces: ["android"], offline: true, approval: "none" },
  { id: "durable_reminders_and_schedules", area: "autonomy", status: "planned", surfaces: ["android", "tv", "pwa"], offline: false, approval: "per-reminder" },
  { id: "calendar_and_weather_briefings", area: "autonomy", status: "planned", surfaces: ["android", "tv", "pwa"], offline: false, approval: "connector-scope" },
  { id: "smart_home_control", area: "device", status: "planned", surfaces: ["android", "tv", "pwa"], offline: false, approval: "per-device" },
  { id: "offline_conversational_model", area: "intelligence", status: "planned", surfaces: ["android"], offline: true, approval: "none" },
  { id: "true_screen_mirroring", area: "media", status: "planned", surfaces: ["android", "tv"], offline: true, approval: "os-projection-consent" },
  { id: "holographic_command_station", area: "presence", status: "planned", surfaces: ["desktop"], offline: true, approval: "local-device-consent" }
]);

const CAPABILITY_BY_ID = new Map(JARVIS_CAPABILITIES.map(item => [item.id, item]));

export function getJarvisCapability(id) {
  return CAPABILITY_BY_ID.get(String(id || "")) || null;
}

export function listJarvisCapabilities(filters = {}) {
  return JARVIS_CAPABILITIES.filter(item =>
    (!filters.area || item.area === filters.area) &&
    (!filters.status || item.status === filters.status) &&
    (!filters.surface || item.surfaces.includes(filters.surface))
  );
}

export function isJarvisCapabilityAvailable(id, surface) {
  const capability = getJarvisCapability(id);
  return Boolean(capability && capability.status === "implemented" &&
    (!surface || capability.surfaces.includes(surface)));
}

export function createJarvisAction({ id, tool, input, requiresApproval = false, createdAt = Date.now() } = {}) {
  if (!tool || typeof tool !== "string" || tool.length > 100) {
    throw new Error("A valid tool name is required for a JARVIS action.");
  }
  return Object.freeze({
    id: String(id || ""),
    tool,
    input: input && typeof input === "object" && !Array.isArray(input) ? input : {},
    state: requiresApproval ? "awaiting_approval" : "proposed",
    createdAt: Number.isFinite(Number(createdAt)) ? Number(createdAt) : Date.now(),
    updatedAt: Number.isFinite(Number(createdAt)) ? Number(createdAt) : Date.now(),
    evidence: null,
    error: null
  });
}

export function transitionJarvisAction(action, nextState, details = {}, now = Date.now()) {
  if (!action || !ACTION_STATES.includes(action.state)) {
    throw new Error("A valid current JARVIS action is required.");
  }
  if (TERMINAL_STATES.has(action.state)) {
    throw new Error("A completed JARVIS action cannot be changed.");
  }
  if (!ACTION_STATES.includes(nextState) || !TRANSITIONS[action.state].has(nextState)) {
    throw new Error(`Invalid JARVIS action transition: ${action.state} -> ${nextState}.`);
  }
  const evidence = typeof details.evidence === "string" ? details.evidence.trim().slice(0, 1000) : "";
  if (nextState === "verified" && !evidence) {
    throw new Error("A JARVIS action requires verification evidence before it can be marked verified.");
  }
  const error = typeof details.error === "string" ? details.error.trim().slice(0, 500) : null;
  if (nextState === "failed" && !error) {
    throw new Error("A failed JARVIS action must include a concise failure reason.");
  }
  return Object.freeze({
    ...action,
    state: nextState,
    updatedAt: Number.isFinite(Number(now)) ? Number(now) : Date.now(),
    evidence: evidence || action.evidence,
    error: error || action.error
  });
}
