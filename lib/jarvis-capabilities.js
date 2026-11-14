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
  { id: "mentalist_engine", area: "intelligence", status: "implemented", surfaces: ["backend"], offline: false, approval: "none" },
  { id: "emotional_availability", area: "presence", status: "implemented", surfaces: ["backend"], offline: false, approval: "none" },
  { id: "alfred_british_personality", area: "presence", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "none" },
  { id: "shared_conversation", area: "memory", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "none" },
  { id: "user_saved_memory", area: "memory", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "user-controlled" },
  { id: "pairing_and_device_revocation", area: "security", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "owner-only" },
  { id: "backup_and_restore", area: "security", status: "implemented", surfaces: ["android", "tv", "pwa"], offline: false, approval: "owner-confirmation" },
  { id: "office_file_generation", area: "productivity", status: "implemented", surfaces: ["android", "pwa"], offline: false, approval: "user-request" },
  { id: "contact_calling", area: "device", status: "implemented", surfaces: ["android"], offline: true, approval: "contacts-and-phone-permissions" },
  { id: "education_engine", area: "education", status: "implemented", surfaces: ["backend"], offline: false, approval: "user-request" },
  { id: "tutor_engine", area: "education", status: "implemented", surfaces: ["backend"], offline: false, approval: "user-request" },
  { id: "exam_revision_planner", area: "education", status: "implemented", surfaces: ["backend"], offline: true, approval: "user-request" },
  { id: "medical_assistant_engine", area: "health", status: "implemented", surfaces: ["backend"], offline: false, approval: "user-request" },
  { id: "emergency_triage_guidance", area: "health", status: "implemented", surfaces: ["backend"], offline: true, approval: "none" },
  { id: "study_plan_builder", area: "education", status: "implemented", surfaces: ["backend"], offline: true, approval: "user-request" },
  { id: "camera_capture", area: "device", status: "implemented", surfaces: ["android"], offline: true, approval: "os-permission" },
  { id: "microphone_dictation", area: "voice", status: "implemented", surfaces: ["android"], offline: false, approval: "microphone-permission-and-speech-service" },
  { id: "android_app_launch", area: "device", status: "partial", surfaces: ["android"], offline: true, approval: "user-request" },
  { id: "youtube_music_exact_track", area: "media", status: "partial", surfaces: ["android"], offline: false, approval: "user-request" },
  { id: "youtube_video_search", area: "media", status: "implemented", surfaces: ["android"], offline: false, approval: "user-request" },
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

const CAPABILITY_DESCRIPTIONS = Object.freeze({
  conversation: "Answer questions, explain topics, reason through problems, and help with writing and coding.",
  shared_conversation: "Continue the shared conversation across paired JARVIS devices.",
  user_saved_memory: "Search and use notes you explicitly save; memory is user-controlled.",
  office_file_generation: "Generate downloadable PDF, DOCX, XLSX, PPTX, CSV, TXT and Markdown files from requests.",
  study_plan_builder: "Build a structured study plan for a subject, goal, level and weekly schedule.",
  camera_capture: "Open the camera, take a photo or selfie, and show the latest captured photo.",
  microphone_dictation: "Use tap-to-speak voice input with the Android microphone and speech service; microphone permission is required.",
  contact_calling: "Find a saved contact and initiate a phone call. Android may request Contacts and Phone permissions.",
  online_british_voice: "Speak replies using the configured online British voice when the voice service is reachable.",
  personality_modes: "Switch between the available assistant response styles.",
  mentalist_engine: "Analyse social situations using observable evidence, alternative explanations and ethical people skills; it does not claim to read minds or reliably detect lies.",
  emotional_availability: "Respond with attentive, empathetic listening and practical support without pretending to be human or replacing trusted people or professionals.",
  pairing_and_device_revocation: "Pair devices and manage access to the shared JARVIS account.",
  backup_and_restore: "Export and restore supported JARVIS conversation and memory backups.",
  android_app_launch: "Attempt to open supported installed Android apps; availability depends on the app installed on the phone.",
  youtube_music_exact_track: "Search for a requested track and hand playback to YouTube Music when a matching result and native bridge are available.",
  youtube_video_search: "Search general YouTube videos and open the results in the YouTube app or browser; you choose which video to play.",
  clock_and_calendar_handoff: "Prepare an alarm or calendar event for your confirmation, then hand it to the native app. It does not silently edit existing alarms or events.",
  offline_wake_word: "Experimental: persistent offline wake-word detection is not yet a verified, generally available feature.",
  local_offline_voice_fallback: "Limited offline voice fallback; full conversational voice requires network access.",
  education_engine: "Teach school, college and university subjects with structured explanations, worked examples, revision notes, practice and exam preparation.",
  tutor_engine: "Teach topics step by step, build exam-style answers, quiz you, mark your responses and explain mistakes.",
  exam_revision_planner: "Build a dated revision schedule from your exam date, subject, topics and available daily study time.",
  medical_assistant_engine: "Provide cautious health education, first-aid guidance and medication-safety information without pretending to diagnose or prescribe.",
  emergency_triage_guidance: "Recognise common life-threatening warning signs and immediately direct you to emergency services; it cannot call them for you."
});

export function isJarvisCapabilitiesQuestion(message) {
  const text = String(message || "").trim().toLowerCase();
  return /\b(what|which)\b.{0,45}\b(can you do|are your capabilities|can jarvis do|features do you have)\b/.test(text) ||
    /\bwhat can (you|jarvis) do\b/.test(text) ||
    /\b(list|show|tell me)\b.{0,35}\b(your|jarvis'?s)\b.{0,25}\b(capabilities|features)\b/.test(text) ||
    /\bwhat all can (you|he|jarvis) do\b/.test(text);
}

export function buildJarvisCapabilitiesReply(deviceType = "Browser / desktop") {
  const type = String(deviceType || "").toLowerCase();
  const surface = type.includes("android tv") ? "tv" : type.includes("android") ? "android" : "pwa";
  const availableIds = new Set([
    "conversation", "shared_conversation", "user_saved_memory", "office_file_generation",
    "study_plan_builder", "tutor_engine", "exam_revision_planner", "medical_assistant_engine", "emergency_triage_guidance", "mentalist_engine", "emotional_availability", "online_british_voice", "personality_modes",
    "pairing_and_device_revocation", "backup_and_restore"
  ]);
  if (surface === "android") {
    ["camera_capture", "microphone_dictation", "contact_calling", "android_app_launch", "youtube_music_exact_track", "youtube_video_search",
      "clock_and_calendar_handoff", "offline_wake_word", "local_offline_voice_fallback"].forEach(id => availableIds.add(id));
  }
  const lines = ["Certainly, Sir. Here is what this JARVIS build can actually do on this device:", "", "AVAILABLE NOW"];
  for (const id of availableIds) {
    const item = getJarvisCapability(id);
    if (!item || !CAPABILITY_DESCRIPTIONS[id] || item.status !== "implemented" || !item.surfaces.includes(surface) && !item.surfaces.includes("backend")) continue;
    lines.push("• " + CAPABILITY_DESCRIPTIONS[id]);
  }
  lines.push("", "LIMITATIONS / NOT YET VERIFIED");
  lines.push("• Voice input and microphone capture require the device microphone permission and a working speech service; a visible listening indicator alone does not prove audio is being captured.");
  lines.push("• Persistent offline wake-word detection is experimental and must not be presented as reliable or complete.");
  lines.push("• Unrestricted device control, background autonomous actions, smart-home control and true screen mirroring are not complete. Medical guidance is informational and cannot replace a clinician or emergency services.");
  lines.push("• Calls, camera capture, music playback and native app actions require a compatible Android device, relevant permissions and successful device-side execution. I cannot verify those from this server response.");
  lines.push("", "I will distinguish what is implemented from what is experimental, permission-dependent or still planned. I will not claim an action worked unless its result confirms it.");
  return lines.join("\n");
}


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
