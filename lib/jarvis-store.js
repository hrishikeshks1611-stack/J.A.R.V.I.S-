import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

const PREFIX = "jarvis:v1:";
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
  const response = await fetch(url.replace(/\/$/, "") + "/pipeline", {
    method: "POST",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json"
    },
    body: JSON.stringify([args])
  });
  const raw = await response.text();
  let data;
  try { data = JSON.parse(raw); } catch { throw new Error("Shared memory returned an invalid response."); }
  if (!response.ok) throw new Error("Shared memory request failed (HTTP " + response.status + ").");
  const item = Array.isArray(data) ? data[0] : data;
  if (item?.error) throw new Error(String(item.error));
  return item?.result;
}

export async function getSession(req) {
  const header = req.headers?.authorization || req.headers?.Authorization || "";
  const token = /^Bearer\s+(.+)$/i.exec(header)?.[1]?.trim();
  if (!token) return null;
  const raw = await redisCommand("GET", PREFIX + "session:" + hashToken(token));
  if (!raw) return null;
  try {
    const session = typeof raw === "string" ? JSON.parse(raw) : raw;
    await redisCommand("EXPIRE", PREFIX + "session:" + hashToken(token), String(SESSION_TTL));
    if (session.deviceId) await redisCommand("EXPIRE", PREFIX + "device:" + session.deviceId, String(SESSION_TTL));
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
