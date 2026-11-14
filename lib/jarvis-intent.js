// Deterministic request classification shared by every J.A.R.V.I.S. input path.
// This module never executes actions; it only prevents unrelated handlers from
// claiming a request. The original user text is always preserved.
const PREFIX = /^(?:(?:hey|hello)\s+)?(?:jarvis[, ]+)?/i;

export function classifyJarvisIntent(input) {
  const original = String(input ?? "");
  const text = original.trim().replace(PREFIX, "").replace(/[.!?]+$/g, "").trim();
  const q = text.toLowerCase();
  if (!q) return { intent: "empty", confidence: 1 };

  if (/^(?:please\s+)?(?:call|phone|ring|dial)\b/.test(q)) {
    return { intent: "call", confidence: 0.99 };
  }
  if (/^(?:please\s+)?(?:send|text|message|email|whatsapp)\b/.test(q)) {
    return { intent: "message", confidence: 0.97 };
  }
  if (/^(?:please\s+)?remind\s+me\b/.test(q) ||
      /\b(?:calendar|appointment|meeting|event)\b/.test(q) &&
      /^(?:please\s+)?(?:add|put|place|schedule|create|book|move|change|set)\b/.test(q)) {
    return { intent: "calendar", confidence: 0.98 };
  }
  if (/^(?:please\s+)?(?:play|listen\s+to|put\s+on|queue)\b/.test(q) &&
      !/^(?:play|put\s+on)\s+(?:chess|football|soccer|a\s+game|a\s+video\s+game)\b/.test(q)) {
    return { intent: "music", confidence: 0.96 };
  }
  if (/\b(?:youtube\s+music|spotify)\b/.test(q) &&
      /\b(?:play|listen|music|song|track|album|artist)\b/.test(q)) {
    return { intent: "music", confidence: 0.98 };
  }
  if (/^(?:who|what|when|where|why|how|which|whose|whom|is|are|am|was|were|do|does|did|can|could|would|should|will|has|have|had)\b/.test(q) ||
      /^(?:explain|define|describe|compare|summari[sz]e|分析|tell\s+me\s+about|what\s+is|who\s+is)\b/.test(q)) {
    return { intent: "conversation", confidence: 0.98 };
  }
  return { intent: "conversation", confidence: 0.72 };
}

export function buildIntentRoutingInstruction(intent) {
  const rules = {
    call: "The user's primary intent is placing a phone call. Do not search for or discuss music. Use a real calling capability only if supplied; otherwise explain the limitation.",
    message: "The user's primary intent is sending a message. Do not search for or discuss music. Never claim a message was sent without a confirmed messaging tool result.",
    calendar: "The user's primary intent is a calendar or reminder task. Do not search for or discuss music. Gather missing date, time, duration, or title details; do not invent them. Only prepare an action through an approved calendar tool.",
    music: "The user explicitly requested music. Handle only the requested music task. Do not infer a different action.",
    conversation: "The user's primary intent is ordinary conversation or a question. Answer the actual request directly. Do not turn it into a music search, music recommendation, or playback action unless the user explicitly asks for music."
  };
  return "DETERMINISTIC REQUEST ROUTING: " + (rules[intent] || rules.conversation) +
    " This routing label is metadata, not user text. Preserve the user's wording and meaning.";
}
