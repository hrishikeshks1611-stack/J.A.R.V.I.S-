import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

const PREFIX = process.env.JARVIS_PREFIX || "jarvis:v1:";
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
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) throw new Error("JARVIS shared memory is not configured.");
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 3500);
  let response;
  let raw;
  try {
    response = await fetch(url.replace(/\\/$/, "") + "/pipeline", {
      method: "POST",
      headers: {
        Authorization: "Bearer " + token,
        "Content-Type": "application/json"
      },
      signal: controller.signal,
      body: JSON.stringify([args])
    });
    raw = await response.text();
  } finally {
    clearTimeout(timeout);
  }
  let data;
  try { data = JSON.parse(raw); } catch { throw new Error("Shared memory returned an invalid response."); }
  if (!response.ok) throw new Error("Shared memory request failed (HTTP " + response.status + ").");
  const item = Array.isArray(data) ? data[0] : data;
  if (item?.error) throw new Error(String(item.error));
  return item?.result;
}

export async function getSession(req, options = {}) {
  const header = req.headers?.authorization || req.headers?.Authorization || "";
  const token = /^Bearer\s+(.+)$/i.exec(header)?.[1]?.trim();
  if (!token) return null;
  const raw = await redisCommand("GET", PREFIX + "session:" + hashToken(token));
  if (!raw) return null;
  try {
    const session = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (options.touch !== false) {
      const touches = [redisCommand("EXPIRE", PREFIX + "session:" + hashToken(token), String(SESSION_TTL))];
      if (session.deviceId) touches.push(redisCommand("EXPIRE", PREFIX + "device:" + session.deviceId, String(SESSION_TTL)));
      await Promise.all(touches);
    }
    return { ...session, token };
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
