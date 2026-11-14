import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

const DEFAULT_PREFIX = "jarvis:v1:";
const PREFIX = process.env.JARVIS_PREFIX || DEFAULT_PREFIX;
// A fresh deployment must never silently fall back to the legacy data namespace.
// Use a new Redis database or a unique prefix and do not import an old backup.
if (process.env.JARVIS_FRESH_INSTANCE === "true" && (!process.env.JARVIS_PREFIX || PREFIX === DEFAULT_PREFIX)) {
  throw new Error("Fresh J.A.R.V.I.S. deployment requires a dedicated JARVIS_PREFIX; refusing to access the legacy memory namespace.");
}
const SESSION_TTL = 60 * 60 * 24 * 90;
const HISTORY_TTL = 60 * 60 * 24 * 365;

export function hashToken(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

export function randomToken(bytes = 32) {
  return randomBytes(bytes).toString("base64url");
}

export function safeEqual(a, b) {
  const left = Buffer.from(String(a || ""));
  const right = Buffer.from(String(b || ""));
  return left.length === right.length && timingSafeEqual(left, right);
}

export async function redisCommand(...args) {
  const results = await redisPipeline([args]);
  return results[0];
}

// Run related Redis commands in one HTTP round trip. This matters on the
// voice path, where avoidable sequential network calls are directly audible.
export async function redisPipeline(commands) {
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) throw new Error("JARVIS shared memory is not configured.");
  if (!Array.isArray(commands) || !commands.length) return [];
  const response = await fetch(url.replace(/\/$/, "") + "/pipeline", {
    method: "POST",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(commands)
  });
  const raw = await response.text();
  let data;
  try { data = JSON.parse(raw); } catch { throw new Error("Shared memory returned an invalid response."); }
  if (!response.ok) throw new Error("Shared memory request failed (HTTP " + response.status + ").");
  if (!Array.isArray(data) || data.length !== commands.length) {
    throw new Error("Shared memory returned an incomplete pipeline response.");
  }
  for (const item of data) {
    if (item?.error) throw new Error(String(item.error));
  }
  return data.map(item => item?.result);
}

export async function getSession(req, options = {}) {
  const header = req.headers?.authorization || req.headers?.Authorization || "";
  const token = /^Bearer\s+(.+)$/i.exec(header)?.[1]?.trim();
  if (!token) return null;
  const raw = await redisCommand("GET", PREFIX + "session:" + hashToken(token));
  if (!raw) return null;
  try {
    const session = typeof raw === "string" ? JSON.parse(raw) : raw;
    // The owner token hash is the single source of truth. Session/device role
    // fields can become stale after an interrupted transfer or older client.
    // Resolve the role from the authenticated token on every request instead
    // of trusting a cached role field.
    const ownerHash = await redisCommand("GET", PREFIX + "owner:token-hash");
    const role = ownerHash && safeEqual(ownerHash, hashToken(token)) ? "owner" : "device";
    if (options.touch !== false) {
      const commands = [["EXPIRE", PREFIX + "session:" + hashToken(token), String(SESSION_TTL)]];
      if (session.deviceId) commands.push(["EXPIRE", PREFIX + "device:" + session.deviceId, String(SESSION_TTL)]);
      await redisPipeline(commands);
    }
    return { ...session, role, token };
  } catch {
    return null;
  }
}

export async function saveHistory(messages) {
  const bounded = Array.isArray(messages) ? messages.slice(-40).map(item => ({
    role: item.role === "model" ? "model" : "user",
    text: String(item.text || "").slice(0, 12000),
    createdAt: Number(item.createdAt) || Date.now()
  })) : [];
  await redisCommand("SET", PREFIX + "conversation:main", JSON.stringify(bounded), "EX", String(HISTORY_TTL));
  return bounded;
}

// Append a completed turn atomically. A normal GET followed by SET can lose a
// different device's turn when two requests finish at nearly the same time.
export async function appendConversationTurn(userText, assistantText, createdAt = Date.now()) {
  const script = [
    "local raw = redis.call('GET', KEYS[1])",
    "local history = {}",
    "if raw then",
    "  local ok, decoded = pcall(cjson.decode, raw)",
    "  if ok and type(decoded) == 'table' then history = decoded end",
    "end",
    "local timestamp = tonumber(ARGV[3]) or 0",
    "table.insert(history, { role = 'user', text = string.sub(ARGV[1], 1, 12000), createdAt = timestamp })",
    "table.insert(history, { role = 'model', text = string.sub(ARGV[2], 1, 12000), createdAt = timestamp })",
    "while #history > 40 do table.remove(history, 1) end",
    "local encoded = cjson.encode(history)",
    "redis.call('SET', KEYS[1], encoded, 'EX', ARGV[4])",
    "return encoded"
  ].join("\n");
  const raw = await redisCommand(
    "EVAL", script, "1", PREFIX + "conversation:main",
    String(userText || "").slice(0, 12000),
    String(assistantText || "").slice(0, 12000),
    String(Number(createdAt) || Date.now()),
    String(HISTORY_TTL)
  );
  try {
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    return Array.isArray(parsed) ? parsed.slice(-40) : [];
  } catch {
    throw new Error("Shared memory returned an invalid conversation update.");
  }
}

const MEMORY_KEY = PREFIX + "memory:main";
const MEMORY_LIMIT = 100;
const MEMORY_TEXT_LIMIT = 1000;

export async function readMemories() {
  const raw = await redisCommand("GET", MEMORY_KEY);
  if (!raw) return [];
  try {
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    return Array.isArray(parsed) ? parsed.slice(-MEMORY_LIMIT) : [];
  } catch {
    return [];
  }
}

// Memories are added only through an explicit user action in the app.
export async function addMemory(text) {
  const value = String(text || "").trim().slice(0, MEMORY_TEXT_LIMIT);
  if (!value) throw new Error("Memory text is required.");
  const id = randomToken(9);
  const createdAt = Date.now();
  const script = [
    "local raw = redis.call('GET', KEYS[1])",
    "local items = {}",
    "if raw then local ok, decoded = pcall(cjson.decode, raw); if ok and type(decoded) == 'table' then items = decoded end end",
    "table.insert(items, { id = ARGV[1], text = ARGV[2], createdAt = tonumber(ARGV[3]) })",
    "while #items > 100 do table.remove(items, 1) end",
    "local encoded = cjson.encode(items)",
    "redis.call('SET', KEYS[1], encoded, 'EX', ARGV[4])",
    "return encoded"
  ].join("\n");
  const raw = await redisCommand("EVAL", script, "1", MEMORY_KEY, id, value, String(createdAt), String(HISTORY_TTL));
  try {
    const items = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!Array.isArray(items)) throw new Error("Invalid memory response.");
    return items.slice(-MEMORY_LIMIT);
  } catch {
    throw new Error("Shared memory returned an invalid saved-memory update.");
  }
}

export async function updateMemory(id, text) {
  const target = String(id || "").trim();
  const value = String(text || "").trim().slice(0, MEMORY_TEXT_LIMIT);
  if (!target || !value) throw new Error("Memory ID and text are required.");
  const script = [
    "local raw = redis.call('GET', KEYS[1])",
    "local items = {}",
    "if raw then local ok, decoded = pcall(cjson.decode, raw); if ok and type(decoded) == 'table' then items = decoded end end",
    "local updated = false",
    "for _, item in ipairs(items) do",
    "  if item.id == ARGV[1] then item.text = ARGV[2]; item.updatedAt = tonumber(ARGV[3]); updated = true end",
    "end",
    "if updated then redis.call('SET', KEYS[1], cjson.encode(items), 'EX', ARGV[4]) end",
    "return cjson.encode({ updated = updated, items = items })"
  ].join("\n");
  const raw = await redisCommand("EVAL", script, "1", MEMORY_KEY, target, value, String(Date.now()), String(HISTORY_TTL));
  try {
    const result = typeof raw === "string" ? JSON.parse(raw) : raw;
    return { updated: result.updated === true, items: Array.isArray(result.items) ? result.items.slice(-MEMORY_LIMIT) : [] };
  } catch {
    throw new Error("Shared memory returned an invalid memory edit result.");
  }
}

export async function deleteMemory(id) {
  const target = String(id || "").trim();
  if (!target) throw new Error("Memory ID is required.");
  const script = [
    "local raw = redis.call('GET', KEYS[1])",
    "local items = {}",
    "if raw then local ok, decoded = pcall(cjson.decode, raw); if ok and type(decoded) == 'table' then items = decoded end end",
    "local kept = {}",
    "local removed = false",
    "for _, item in ipairs(items) do",
    "  if item.id == ARGV[1] then removed = true else table.insert(kept, item) end",
    "end",
    "if #kept == 0 then redis.call('DEL', KEYS[1]) else redis.call('SET', KEYS[1], cjson.encode(kept), 'EX', ARGV[2]) end",
    "return cjson.encode({ deleted = removed, items = kept })"
  ].join("\n");
  const raw = await redisCommand("EVAL", script, "1", MEMORY_KEY, target, String(HISTORY_TTL));
  try {
    const result = typeof raw === "string" ? JSON.parse(raw) : raw;
    return { deleted: result.deleted === true, items: Array.isArray(result.items) ? result.items.slice(-MEMORY_LIMIT) : [] };
  } catch {
    throw new Error("Shared memory returned an invalid memory deletion result.");
  }
}

export async function clearMemories() {
  await redisCommand("DEL", MEMORY_KEY);
  return true;
}

// Replace conversation and saved memories in one Redis transaction. The API
// validates and bounds the imported document before calling this function.
export async function replaceBackup(conversation, memories) {
  const script = [
    "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])",
    "redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])",
    "return 1"
  ].join("\n");
  const history = JSON.stringify(conversation);
  const saved = JSON.stringify(memories);
  const result = await redisCommand(
    "EVAL", script, "2", PREFIX + "conversation:main", MEMORY_KEY,
    history, saved, String(HISTORY_TTL)
  );
  if (Number(result) !== 1) throw new Error("Shared memory did not confirm the backup restore.");
  return { conversation: conversation.length, memories: memories.length };
}

export async function readHistory() {
  const raw = await redisCommand("GET", PREFIX + "conversation:main");
  if (!raw) return [];
  try {
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    return Array.isArray(parsed) ? parsed.slice(-40) : [];
  } catch {
    return [];
  }
}

export const JARVIS_PREFIX = PREFIX;
export const JARVIS_SESSION_TTL = SESSION_TTL;
export const JARVIS_HISTORY_TTL = HISTORY_TTL;
