import { redisCommand, JARVIS_PREFIX } from "./jarvis-store.js";

const AUDIT_KEY = JARVIS_PREFIX + "security:audit";
const AUDIT_TTL = 60 * 60 * 24 * 90;
const AUDIT_LIMIT = 500;

// Store only allow-listed metadata. Never pass tokens, setup codes, pairing
// codes, request bodies, or raw credentials to this function.
export async function recordSecurityEvent(action, outcome, details = {}) {
  const event = {
    timestamp: Date.now(),
    action: String(action || "unknown").slice(0, 40),
    outcome: String(outcome || "unknown").slice(0, 24)
  };
  for (const key of ["deviceId", "deviceName", "role", "reason", "targetDeviceId", "sourceId"]) {
    if (details[key] == null) continue;
    event[key] = String(details[key]).replace(/[^a-zA-Z0-9 _.-]/g, "").slice(0, 80);
  }
  try {
    await redisCommand("LPUSH", AUDIT_KEY, JSON.stringify(event));
    await redisCommand("LTRIM", AUDIT_KEY, "0", String(AUDIT_LIMIT - 1));
    await redisCommand("EXPIRE", AUDIT_KEY, String(AUDIT_TTL));
  } catch (error) {
    // Logging must not interrupt pairing or revoke an existing session.
    console.error("JARVIS security audit write failed:", String(error?.message || error).slice(0, 160));
  }
}
