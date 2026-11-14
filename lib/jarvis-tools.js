// JARVIS 6.0 allow-listed, server-side tools. No arbitrary code, shell, or URL execution.
const MAX_QUERY = 180;

function cleanText(value, max = 180) {
  return String(value ?? "").replace(/[\r\n\u0000-\u001f]/g, " ").trim().slice(0, max);
}

// Small recursive-descent arithmetic parser. Deliberately excludes identifiers,
// property access, function calls, exponentiation, and all JavaScript evaluation.
export function calculateExpression(expression) {
  const source = String(expression || "").trim();
  if (!source || source.length > 120) throw new Error("Enter a calculation of at most 120 characters.");
  const tokens = source.match(/\d+(?:\.\d+)?|[()+*/-]/g) || [];
  if (tokens.join("") !== source.replace(/\s+/g, "")) throw new Error("Only numbers, parentheses, +, -, *, and / are supported.");
  let index = 0;
  const peek = () => tokens[index];
  const take = () => tokens[index++];
  function factor() {
    if (peek() === "+") { take(); return factor(); }
    if (peek() === "-") { take(); return -factor(); }
    if (peek() === "(") {
      take();
      const value = sum();
      if (take() !== ")") throw new Error("Check the parentheses in that calculation.");
      return value;
    }
    const token = take();
    if (!token || !/^\d+(?:\.\d+)?$/.test(token)) throw new Error("A number was expected.");
    return Number(token);
  }
  function product() {
    let value = factor();
    while (peek() === "*" || peek() === "/") {
      const op = take(), right = factor();
      if (op === "/" && right === 0) throw new Error("Division by zero is not allowed.");
      value = op === "*" ? value * right : value / right;
    }
    return value;
  }
  function sum() {
    let value = product();
    while (peek() === "+" || peek() === "-") {
      const op = take(), right = product();
      value = op === "+" ? value + right : value - right;
    }
    return value;
  }
  const result = sum();
  if (index !== tokens.length) throw new Error("The calculation contains unexpected characters.");
  if (!Number.isFinite(result) || Math.abs(result) > 1e15) throw new Error("That result is outside the supported range.");
  return Number(result.toPrecision(12));
}

export const JARVIS_TOOL_DECLARATIONS = [{
  function_declarations: [
    {
      name: "calculate",
      description: "Evaluate a basic arithmetic expression. Use this for exact arithmetic instead of mental estimation.",
      parameters: { type: "OBJECT", properties: { expression: { type: "STRING", description: "Arithmetic using numbers, parentheses, +, -, *, and /." } }, required: ["expression"] }
    },
    {
      name: "get_device_status",
      description: "Read device context explicitly supplied by the paired JARVIS app, such as local time, time zone, device type, and network status. Do not infer missing values.",
      parameters: { type: "OBJECT", properties: { fields: { type: "ARRAY", items: { type: "STRING", enum: ["localTime", "timeZone", "deviceType", "deviceName", "networkOnline"] }, description: "Requested context fields." } }, required: ["fields"] }
    },
    {
      name: "request_open_app",
      description: "Prepare opening a named non-financial Android app when the user explicitly asks J.A.R.V.I.S. to open or launch it. This creates a device action that the user confirms in the app. Do not use for financial, banking, payment, wallet or UPI apps.",
      parameters: { type: "OBJECT", properties: { appName: { type: "STRING", description: "The installed non-financial app name to open." } }, required: ["appName"] }
    },
    {
      name: "request_dial_number",
      description: "Prepare opening the Android dialler with a phone number only when the user explicitly asks to call or dial that number. The user must confirm by tapping the action; this never places a call automatically.",
      parameters: { type: "OBJECT", properties: { phoneNumber: { type: "STRING", description: "A phone number explicitly supplied by the user." } }, required: ["phoneNumber"] }
    },
    {
      name: "request_sms_draft",
      description: "Prepare an SMS draft in the Android messaging app only when the user explicitly asks to text or send an SMS. This opens a prefilled composer after the user taps the confirmation button; it never sends the message.",
      parameters: { type: "OBJECT", properties: { phoneNumber: { type: "STRING", description: "Recipient phone number explicitly supplied by the user." }, message: { type: "STRING", description: "The message text to place in the SMS composer." } }, required: ["phoneNumber", "message"] }
    },
    {
      name: "request_share_text",
      description: "Prepare sharing text or a link through Android's system Sharesheet. Use when the user explicitly asks to share, send, or post supplied text through a compatible installed app. The user chooses the destination app and completes the action manually; JARVIS does not post or send silently.",
      parameters: { type: "OBJECT", properties: { text: { type: "STRING", description: "The exact content to share, up to 8,000 characters." }, title: { type: "STRING", description: "Short chooser title such as Share with WhatsApp or Share text." } }, required: ["text", "title"] }
    },
    {
      name: "request_alarm",
      description: "Prepare an alarm only when the user explicitly asks to set one and supplies an unambiguous time. This only creates a pending confirmation request; the user must approve it in the JARVIS app before the native Clock app opens.",
      parameters: { type: "OBJECT", properties: { hour: { type: "INTEGER", description: "24-hour clock hour, 0 through 23." }, minute: { type: "INTEGER", description: "Minute, 0 through 59." }, label: { type: "STRING", description: "Short alarm label." } }, required: ["hour", "minute", "label"] }
    },
    {
      name: "request_calendar_event",
      description: "Prepare a calendar event only when the user explicitly asks to create one and supplies an unambiguous future start and end time with timezone offset. This only creates a pending confirmation request; the user must approve it in the JARVIS app before the native calendar editor opens.",
      parameters: { type: "OBJECT", properties: { title: { type: "STRING" }, startIso: { type: "STRING", description: "ISO 8601 date-time with Z or an explicit timezone offset." }, endIso: { type: "STRING", description: "ISO 8601 date-time with Z or an explicit timezone offset." }, description: { type: "STRING" } }, required: ["title", "startIso", "endIso"] }
    },
    {
      name: "search_saved_memories",
      description: "Search the user's explicitly saved JARVIS memory notes. Read-only; return only relevant notes.",
      parameters: { type: "OBJECT", properties: { query: { type: "STRING", description: "A short topic or phrase to search for." } }, required: ["query"] }
    },
    {
      name: "search_conversation",
      description: "Search recent shared JARVIS conversation history for relevant earlier user or assistant messages. Read-only.",
      parameters: { type: "OBJECT", properties: { query: { type: "STRING", description: "A short topic or phrase to search for." } }, required: ["query"] }
    }
  ]
}];

function isExplicitActionIntent(message, actionPattern) {
  const text = String(message || "").trim();
  if (!text || /^(how|what|why|when|where)\b/i.test(text) ||
      /\bhow (do|can|to)\b/i.test(text) || /^(explain|tell me about)\b/i.test(text)) return false;
  return actionPattern.test(text);
}

function scoreText(text, query) {
  const words = cleanText(query, MAX_QUERY).toLowerCase().split(/\s+/).filter(word => word.length > 1);
  const haystack = String(text || "").toLowerCase();
  return words.reduce((score, word) => score + (haystack.includes(word) ? 1 : 0), 0);
}

export async function executeJarvisTool(name, args = {}, context = {}) {
  if (!args || typeof args !== "object" || Array.isArray(args)) throw new Error("Tool arguments must be an object.");
  switch (name) {
    case "calculate": {
      const result = calculateExpression(args.expression);
      return { ok: true, expression: cleanText(args.expression, 120), result };
    }
    case "get_device_status": {
      const allowed = new Set(["localTime", "timeZone", "deviceType", "deviceName", "networkOnline"]);
      const fields = Array.isArray(args.fields) ? args.fields.filter(field => allowed.has(field)).slice(0, 5) : [];
      const result = {};
      for (const field of fields) {
        const value = context.deviceContext?.[field];
        if (typeof value === "string") result[field] = cleanText(value, 120);
        else if (typeof value === "boolean" && field === "networkOnline") result[field] = value;
      }
      return { ok: true, available: Object.keys(result).length > 0, device: result, note: "Only context explicitly supplied by the paired app is reported." };
    }
    case "request_open_app": {
      if (!isExplicitActionIntent(context.userMessage, /\b(open|launch|start)\b/i)) {
        throw new Error("An app can only be opened when the user explicitly asks J.A.R.V.I.S. to open or launch it.");
      }
      const appName = cleanText(args.appName, 80);
      if (!appName) throw new Error("A non-financial app name is required.");
      if (/\b(bank|banking|upi|wallet|payment|paytm|phonepe|gpay|google pay|cred)\b/i.test(appName)) {
        throw new Error("Financial apps and money-related functions are outside J.A.R.V.I.S. permissions.");
      }
      return { ok: true, requiresConfirmation: true, pendingAction: { type: "open_app", appName } };
    }
    case "request_dial_number": {
      if (!isExplicitActionIntent(context.userMessage, /\b(call|phone|dial)\b/i)) {
        throw new Error("The dialler can only be prepared when the user explicitly asks to call or dial.");
      }
      const phoneNumber = cleanText(args.phoneNumber, 32);
      if (!/^\+?[0-9][0-9 .()\-]{5,24}$/.test(phoneNumber)) {
        throw new Error("Provide a valid phone number explicitly; JARVIS will not guess a contact or number.");
      }
      return { ok: true, requiresConfirmation: true, pendingAction: { type: "dial", phoneNumber } };
    }
    case "request_sms_draft": {
      if (!isExplicitActionIntent(context.userMessage, /\b(text|sms|message|send)\b/i)) {
        throw new Error("An SMS draft can only be prepared when the user explicitly asks to text or send a message.");
      }
      const phoneNumber = cleanText(args.phoneNumber, 32);
      const message = cleanText(args.message, 2000);
      if (!/^\+?[0-9][0-9 .()\-]{5,24}$/.test(phoneNumber) || !message) {
        throw new Error("Provide an explicit valid phone number and non-empty message.");
      }
      return { ok: true, requiresConfirmation: true, pendingAction: { type: "sms_draft", phoneNumber, message } };
    }
    case "request_share_text": {
      if (!isExplicitActionIntent(context.userMessage, /\b(share|send|post|forward)\b/i)) {
        throw new Error("Text can only be shared when the user explicitly asks to share, send, post, or forward it.");
      }
      const text = cleanText(args.text, 8000);
      const title = cleanText(args.title || "Share with", 80);
      if (!text) throw new Error("There is no text to share.");
      return { ok: true, requiresConfirmation: true, pendingAction: { type: "share_text", text, title } };
    }
    case "request_alarm": {
      if (!isExplicitActionIntent(context.userMessage, /\b(set|create|schedule|add)\b.{0,60}\b(alarm|wake.?up)\b|\bwake me up\b/i)) {
        throw new Error("An alarm can only be prepared when the user explicitly asks JARVIS to set or schedule one.");
      }
      const hour = Number(args.hour), minute = Number(args.minute);
      const label = cleanText(args.label, 60);
      if (!Number.isInteger(hour) || hour < 0 || hour > 23 || !Number.isInteger(minute) || minute < 0 || minute > 59 || !label) {
        throw new Error("A valid 24-hour alarm time and short label are required.");
      }
      return { ok: true, requiresConfirmation: true, pendingAction: { type: "alarm", hour, minute, label } };
    }
    case "request_calendar_event": {
      if (!isExplicitActionIntent(context.userMessage, /\b(create|add|schedule|book|put)\b.{0,60}\b(calendar event|event|appointment|meeting)\b/i)) {
        throw new Error("A calendar event can only be prepared when the user explicitly asks JARVIS to create or schedule one.");
      }
      const title = cleanText(args.title, 100);
      const startIso = cleanText(args.startIso, 40), endIso = cleanText(args.endIso, 40);
      const hasOffset = value => /(?:Z|[+-](?:0\d|1\d|2[0-3]):[0-5]\d)$/i.test(value);
      const beginMillis = Date.parse(startIso), endMillis = Date.parse(endIso);
      const nowMillis = Date.now();
      if (!title || !hasOffset(startIso) || !hasOffset(endIso) || !Number.isFinite(beginMillis) || !Number.isFinite(endMillis) ||
          beginMillis <= nowMillis || endMillis <= beginMillis || endMillis - beginMillis > 12 * 60 * 60 * 1000) {
        throw new Error("Provide a title and a future start/end time with timezone offsets; events must be no longer than 12 hours.");
      }
      return { ok: true, requiresConfirmation: true, pendingAction: {
        type: "calendar", title, beginMillis, endMillis, description: cleanText(args.description, 500)
      } };
    }
    case "search_saved_memories": {
      const query = cleanText(args.query, MAX_QUERY);
      if (!query) throw new Error("A memory search query is required.");
      const rows = Array.isArray(context.memories) ? context.memories : [];
      const matches = rows.map(item => ({ item, score: scoreText(item.text, query) }))
        .filter(row => row.score > 0).sort((a, b) => b.score - a.score)
        .slice(0, 5).map(({ item }) => ({ text: cleanText(item.text, 1000), createdAt: Number(item.createdAt) || null }));
      return { ok: true, matches, count: matches.length };
    }
    case "search_conversation": {
      const query = cleanText(args.query, MAX_QUERY);
      if (!query) throw new Error("A conversation search query is required.");
      const rows = Array.isArray(context.history) ? context.history : [];
      const matches = rows.map(item => ({ item, score: scoreText(item.text, query) }))
        .filter(row => row.score > 0).sort((a, b) => b.score - a.score)
        .slice(0, 6).map(({ item }) => ({ role: item.role === "model" ? "assistant" : "user", text: cleanText(item.text, 1200), createdAt: Number(item.createdAt) || null }));
      return { ok: true, matches, count: matches.length };
    }
    default:
      throw new Error("That tool is not in JARVIS's approved tool registry.");
  }
}
