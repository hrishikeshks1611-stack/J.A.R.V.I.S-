// JARVIS 6.0 allow-listed, server-side tools. No arbitrary code, shell, or URL execution.
const MAX_QUERY = 180;

function cleanText(value, max = 180) {
  return String(value ?? "").replace(/[\\r\\n\\u0000-\\u001f]/g, " ").trim().slice(0, max);
}

// Small recursive-descent arithmetic parser. Deliberately excludes identifiers,
// property access, function calls, exponentiation, and all JavaScript evaluation.
export function calculateExpression(expression) {
  const source = String(expression || "").trim();
  if (!source || source.length > 120) throw new Error("Enter a calculation of at most 120 characters.");
  const tokens = source.match(/\\d+(?:\\.\\d+)?|[()+*/-]/g) || [];
  if (tokens.join("") !== source.replace(/\\s+/g, "")) throw new Error("Only numbers, parentheses, +, -, *, and / are supported.");
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
    if (!token || !/^\\d+(?:\\.\\d+)?$/.test(token)) throw new Error("A number was expected.");
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

function scoreText(text, query) {
  const words = cleanText(query, MAX_QUERY).toLowerCase().split(/\\s+/).filter(word => word.length > 1);
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
