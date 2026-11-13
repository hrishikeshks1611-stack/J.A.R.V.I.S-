import { createHmac } from "node:crypto";
import {
  getSession, hashToken, randomToken, redisCommand, safeEqual,
  readHistory, saveHistory, appendConversationTurn, JARVIS_PREFIX, JARVIS_SESSION_TTL
} from "../lib/jarvis-store.js";
import { recordSecurityEvent } from "../lib/jarvis-audit.js";

function cors(req, res) {
  const origin = req.headers.origin || "";
  const allowed = origin === "https://hrishikeshks1611-stack.github.io" ||
    /^https:\/\/[^/]+\.(?:vercel\.app|onrender\.com)$/.test(origin);
  if (origin && !allowed) return false;
  if (allowed) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  }
  return true;
}

function deviceName(value) {
  return String(value || "JARVIS device").replace(/[^a-zA-Z0-9 _-]/g, "").trim().slice(0, 32) || "JARVIS device";
}

async function createSession(name, role = "device") {
  const token = randomToken(32);
  const tokenHash = hashToken(token);
  const deviceId = randomToken(12);
  const session = { deviceId, deviceName: name, role, createdAt: Date.now() };
  await redisCommand("SET", JARVIS_PREFIX + "session:" + tokenHash, JSON.stringify(session), "EX", String(JARVIS_SESSION_TTL));
  await redisCommand("SET", JARVIS_PREFIX + "device:" + deviceId, JSON.stringify({ ...session, sessionHash: tokenHash }), "EX", String(JARVIS_SESSION_TTL));
  await redisCommand("SADD", JARVIS_PREFIX + "devices", deviceId);
  return { token, deviceId, deviceName: name, role };
}

export default async function handler(req, res) {
  if (!cors(req, res)) return res.status(403).json({ error: "This website is not authorised to access JARVIS pairing." });
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST for device pairing." });

  try {
    const body = req.body || {};
    const action = String(body.action || "");
    const sourceMaterial = String(req.headers["x-forwarded-for"] || req.socket?.remoteAddress || "unknown") + "|" + String(req.headers["user-agent"] || "");
    const auditKey = process.env.JARVIS_SESSION_SECRET || process.env.JARVIS_SETUP_CODE;
    const sourceId = auditKey ? createHmac("sha256", auditKey).update(sourceMaterial).digest("hex").slice(0, 12) : undefined;
    const audit = (outcome, details = {}) => recordSecurityEvent(action, outcome, { ...details, sourceId });
    if (action === "status") {
      const claimed = await redisCommand("GET", JARVIS_PREFIX + "owner:claimed");
      return res.status(200).json({ ownerClaimed: Boolean(claimed) });
    }

    if (action === "claim") {
      const supplied = String(body.setupCode || "");
      const expected = process.env.JARVIS_SETUP_CODE || "";
      if (!expected || !safeEqual(supplied, expected)) { await audit("denied", { reason: "invalid_setup_code", deviceName: deviceName(body.deviceName) }); return res.status(401).json({ error: "That owner setup code is not valid." }); }
      const lock = await redisCommand("SET", JARVIS_PREFIX + "owner:claim-lock", randomToken(12), "NX", "EX", "30");
      if (!lock) return res.status(409).json({ error: "Owner setup is already being completed. Please try again." });
      try {
        const already = await redisCommand("GET", JARVIS_PREFIX + "owner:claimed");
        if (already) { await audit("denied", { reason: "owner_already_exists", deviceName: deviceName(body.deviceName) }); return res.status(409).json({ error: "JARVIS already has an owner device. Pair this device using a pairing code." }); }
        const result = await createSession(deviceName(body.deviceName), "owner");
        const ownerSet = await redisCommand("SET", JARVIS_PREFIX + "owner:claimed", "1", "NX");
        if (!ownerSet) {
          await redisCommand("DEL", JARVIS_PREFIX + "session:" + hashToken(result.token));
          return res.status(409).json({ error: "JARVIS owner setup was claimed by another device. Please pair instead." });
        }
        await redisCommand("SET", JARVIS_PREFIX + "owner:token-hash", hashToken(result.token));
        await audit("success", { deviceId: result.deviceId, deviceName: result.deviceName, role: "owner" });
        return res.status(200).json({ ...result, message: "This device is now the JARVIS owner device." });
      } finally {
        await redisCommand("DEL", JARVIS_PREFIX + "owner:claim-lock").catch(() => {});
      }
    }

    if (action === "pair") {
      const code = String(body.pairingCode || "").replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
      if (!code) { await audit("denied", { reason: "missing_pairing_code", deviceName: deviceName(body.deviceName) }); return res.status(400).json({ error: "Enter the pairing code shown on your owner device." }); }
      const raw = await redisCommand("GETDEL", JARVIS_PREFIX + "pair:" + hashToken(code));
      if (!raw) { await audit("denied", { reason: "invalid_or_expired_pairing_code", deviceName: deviceName(body.deviceName) }); return res.status(401).json({ error: "That pairing code has expired or has already been used. Generate a new one on your owner device." }); }
      const pairing = typeof raw === "string" ? JSON.parse(raw) : raw;
      const result = await createSession(deviceName(body.deviceName), "device");
      await audit("success", { deviceId: result.deviceId, deviceName: result.deviceName, role: "device" });
      return res.status(200).json({ ...result, message: "This device is now paired with JARVIS." });
    }

    if (action === "recover-owner") {
      const recoveryCode = String(body.recoveryCode || "");
      const expectedRecoveryCode = process.env.JARVIS_OWNER_RECOVERY_CODE || "";
      if (expectedRecoveryCode.length < 32) return res.status(503).json({ error: "Owner recovery is not configured on this JARVIS server yet." });
      const attemptKey = JARVIS_PREFIX + "owner:recovery-attempts:" + hashToken(sourceMaterial);
      const attempts = Number(await redisCommand("INCR", attemptKey));
      if (attempts === 1) await redisCommand("EXPIRE", attemptKey, "3600");
      if (attempts > 5) { await audit("denied", { reason: "recovery_rate_limited" }); return res.status(429).json({ error: "Too many recovery attempts from this connection. Try again in one hour." }); }
      if (!safeEqual(recoveryCode, expectedRecoveryCode)) { await audit("denied", { reason: "invalid_owner_recovery_credential" }); return res.status(401).json({ error: "The owner recovery credential is not valid." }); }
      if (await redisCommand("GET", JARVIS_PREFIX + "owner:claimed") !== "1") return res.status(409).json({ error: "No existing JARVIS owner record was found. Recovery was not performed." });
      // Create a new session on this phone; no existing phone session is required.
      const recovered = await createSession(deviceName(body.deviceName || "Phone"), "device");
      const currentHash = hashToken(recovered.token);
      // Consume each recovery credential once, rather than permanently locking all future recovery codes.
      const recoveryUseKey = JARVIS_PREFIX + "owner:recovery:used:" + hashToken(recoveryCode);
      const script = [
        "if redis.call('GET', KEYS[1]) ~= '1' then return cjson.encode({ ok = false, reason = 'owner_missing' }) end",
        "if redis.call('EXISTS', KEYS[3]) == 1 then return cjson.encode({ ok = false, reason = 'already_used' }) end",
        "local currentRaw = redis.call('GET', KEYS[4])",
        "local currentDeviceRaw = redis.call('GET', KEYS[5])",
        "if not currentRaw or not currentDeviceRaw then return cjson.encode({ ok = false, reason = 'new_session_missing' }) end",
        "local current = cjson.decode(currentRaw)",
        "local currentDevice = cjson.decode(currentDeviceRaw)",
        "local oldHash = redis.call('GET', KEYS[2])",
        "if oldHash and oldHash ~= ARGV[1] then",
        "  local oldSessionKey = ARGV[4] .. 'session:' .. oldHash",
        "  local oldSessionRaw = redis.call('GET', oldSessionKey)",
        "  if oldSessionRaw then",
        "    local oldSession = cjson.decode(oldSessionRaw)",
        "    oldSession.role = 'device'",
        "    redis.call('SET', oldSessionKey, cjson.encode(oldSession), 'EX', ARGV[3])",
        "    if oldSession.deviceId and oldSession.deviceId ~= ARGV[2] then",
        "      local oldDeviceKey = ARGV[4] .. 'device:' .. oldSession.deviceId",
        "      local oldDeviceRaw = redis.call('GET', oldDeviceKey)",
        "      if oldDeviceRaw then local oldDevice = cjson.decode(oldDeviceRaw); oldDevice.role = 'device'; redis.call('SET', oldDeviceKey, cjson.encode(oldDevice), 'EX', ARGV[3]) end",
        "    end",
        "  end",
        "end",
        "current.role = 'owner'",
        "currentDevice.role = 'owner'",
        "redis.call('SET', KEYS[4], cjson.encode(current), 'EX', ARGV[3])",
        "redis.call('SET', KEYS[5], cjson.encode(currentDevice), 'EX', ARGV[3])",
        "redis.call('SADD', KEYS[6], ARGV[2])",
        "redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])",
        "redis.call('SET', KEYS[3], '1')",
        "return cjson.encode({ ok = true, deviceId = ARGV[2] })",
      ].join("\n");
      let raw;
      try {
        raw = await redisCommand("EVAL", script, "6", JARVIS_PREFIX + "owner:claimed", JARVIS_PREFIX + "owner:token-hash", recoveryUseKey, JARVIS_PREFIX + "session:" + currentHash, JARVIS_PREFIX + "device:" + recovered.deviceId, JARVIS_PREFIX + "devices", currentHash, recovered.deviceId, String(JARVIS_SESSION_TTL), JARVIS_PREFIX);
      } catch (error) {
        await redisCommand("DEL", JARVIS_PREFIX + "session:" + currentHash).catch(() => {});
        await redisCommand("DEL", JARVIS_PREFIX + "device:" + recovered.deviceId).catch(() => {});
        await redisCommand("SREM", JARVIS_PREFIX + "devices", recovered.deviceId).catch(() => {});
        throw error;
      }
      const result = typeof raw === "string" ? JSON.parse(raw) : raw;
      if (!result || !result.ok) {
        await redisCommand("DEL", JARVIS_PREFIX + "session:" + currentHash).catch(() => {});
        await redisCommand("DEL", JARVIS_PREFIX + "device:" + recovered.deviceId).catch(() => {});
        await redisCommand("SREM", JARVIS_PREFIX + "devices", recovered.deviceId).catch(() => {});
        const msg = result && result.reason === "already_used" ? "The one-time owner recovery has already been used." : "Phone recovery could not be completed safely. Existing paired devices and shared memory were not removed.";
        await audit("denied", { deviceName: recovered.deviceName, reason: (result && result.reason) || "recovery_failed" });
        return res.status(409).json({ error: msg });
      }
      await audit("success", { deviceId: recovered.deviceId, deviceName: recovered.deviceName, role: "owner", reason: "one_time_phone_owner_recovery" });
      return res.status(200).json({ ...recovered, recovered: true, role: "owner", message: "Owner access has been recovered on this phone. Existing paired devices and shared memory were preserved." });
    }

    const session = await getSession(req);
    if (!session) { await audit("denied", { reason: "missing_or_invalid_session" }); return res.status(401).json({ error: "This device is not paired with JARVIS. Open Devices & Sync to pair it." }); }

    if (action === "verify") {
      await audit("success", { deviceId: session.deviceId, deviceName: session.deviceName, role: session.role });
      return res.status(200).json({ deviceId: session.deviceId, deviceName: session.deviceName, role: session.role });
    }
    if (action === "append-local") {
      const userText = String(body.message || "").trim().slice(0, 12000);
      const replyText = String(body.reply || "").trim().slice(0, 12000);
      if (!userText || !replyText) return res.status(400).json({ error: "A local command and its result are required." });
      await appendConversationTurn(userText, replyText);
      return res.status(200).json({ saved: true });
    }
    if (action === "history") {
      return res.status(200).json({ messages: await readHistory() });
    }
    if (action === "clear") {
      await saveHistory([]);
      return res.status(200).json({ cleared: true });
    }
    if (action === "create-code") {
      if (session.role !== "owner") return res.status(403).json({ error: "Only the owner device can create pairing codes." });
      const code = randomToken(16).replace(/[^a-zA-Z0-9]/g, "").slice(0, 8).toUpperCase();
      await redisCommand("SET", JARVIS_PREFIX + "pair:" + hashToken(code), JSON.stringify({ createdBy: session.deviceId, createdAt: Date.now() }), "EX", "600", "NX");
      await audit("success", { deviceId: session.deviceId, deviceName: session.deviceName, role: session.role });
      return res.status(200).json({ code, expiresIn: 600 });
    }
    if (action === "security-audit") {
      if (session.role !== "owner") return res.status(403).json({ error: "Only the owner device can view security events." });
      const rows = await redisCommand("LRANGE", JARVIS_PREFIX + "security:audit", "0", "99") || [];
      const events = rows.map(row => { try { return typeof row === "string" ? JSON.parse(row) : row; } catch { return null; } }).filter(Boolean);
      return res.status(200).json({ events });
    }
    if (action === "devices") {
      if (session.role !== "owner") return res.status(403).json({ error: "Only the owner device can manage paired devices." });
      const ids = await redisCommand("SMEMBERS", JARVIS_PREFIX + "devices") || [];
      const devices = [];
      for (const id of ids) {
        const raw = await redisCommand("GET", JARVIS_PREFIX + "device:" + id);
        if (!raw) continue;
        const item = typeof raw === "string" ? JSON.parse(raw) : raw;
        devices.push({ deviceId: id, deviceName: item.deviceName, role: item.role, createdAt: item.createdAt });
      }
      return res.status(200).json({ devices });
    }
    if (action === "transfer-owner") {
      if (session.role !== "owner") return res.status(403).json({ error: "Only the current owner can transfer ownership." });
      const target = String(body.deviceId || "");
      if (!target || target === session.deviceId) return res.status(400).json({ error: "Choose another paired device as the new owner." });
      const targetRaw = await redisCommand("GET", JARVIS_PREFIX + "device:" + target);
      if (!targetRaw) return res.status(404).json({ error: "That device is not paired." });
      const targetDevice = typeof targetRaw === "string" ? JSON.parse(targetRaw) : targetRaw;
      if (!targetDevice.sessionHash) return res.status(409).json({ error: "That device session is not available. Pair it again before transferring ownership." });
      const currentHash = hashToken(session.token);
      const script = [
        "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return cjson.encode({ ok = false, reason = 'owner_changed' }) end",
        "local currentRaw = redis.call('GET', KEYS[2])",
        "local targetRaw = redis.call('GET', KEYS[3])",
        "local currentSessionRaw = redis.call('GET', KEYS[4])",
        "local targetSessionRaw = redis.call('GET', KEYS[5])",
        "if not currentRaw or not targetRaw or not currentSessionRaw or not targetSessionRaw then return cjson.encode({ ok = false, reason = 'session_missing' }) end",
        "local current = cjson.decode(currentRaw)",
        "local target = cjson.decode(targetRaw)",
        "local currentSession = cjson.decode(currentSessionRaw)",
        "local targetSession = cjson.decode(targetSessionRaw)",
        "if target.sessionHash ~= ARGV[2] or target.deviceId == current.deviceId then return cjson.encode({ ok = false, reason = 'target_changed' }) end",
        "current.role = 'device'",
        "currentSession.role = 'device'",
        "target.role = 'owner'",
        "targetSession.role = 'owner'",
        "redis.call('SET', KEYS[2], cjson.encode(current), 'EX', ARGV[3])",
        "redis.call('SET', KEYS[3], cjson.encode(target), 'EX', ARGV[3])",
        "redis.call('SET', KEYS[4], cjson.encode(currentSession), 'EX', ARGV[3])",
        "redis.call('SET', KEYS[5], cjson.encode(targetSession), 'EX', ARGV[3])",
        "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])",
        "return cjson.encode({ ok = true, ownerDeviceId = target.deviceId, previousOwnerDeviceId = current.deviceId })"
      ].join("\n");
      const raw = await redisCommand(
        "EVAL", script, "5",
        JARVIS_PREFIX + "owner:token-hash",
        JARVIS_PREFIX + "device:" + session.deviceId,
        JARVIS_PREFIX + "device:" + target,
        JARVIS_PREFIX + "session:" + currentHash,
        JARVIS_PREFIX + "session:" + targetDevice.sessionHash,
        currentHash, targetDevice.sessionHash, String(JARVIS_SESSION_TTL)
      );
      const result = typeof raw === "string" ? JSON.parse(raw) : raw;
      if (!result?.ok) {
        const reason = result?.reason;
        await audit("denied", { deviceId: session.deviceId, deviceName: session.deviceName, role: session.role, targetDeviceId: target, reason: reason || "transfer_failed" });
        return res.status(reason === "session_missing" ? 409 : 409).json({
          error: reason === "owner_changed"
            ? "Ownership changed during this request. Please refresh Devices & Sync."
            : "Ownership could not be transferred because a device session changed. Refresh the device list and try again."
        });
      }
      await audit("success", { deviceId: session.deviceId, deviceName: session.deviceName, role: "owner", targetDeviceId: target });
      return res.status(200).json({ transferred: true, ownerDeviceId: result.ownerDeviceId, message: "JARVIS ownership has been transferred. This device is now a regular paired device." });
    }
    if (action === "revoke") {
      if (session.role !== "owner") return res.status(403).json({ error: "Only the owner device can revoke access." });
      const target = String(body.deviceId || "");
      if (!target || target === session.deviceId) return res.status(400).json({ error: "You cannot revoke the owner device from itself." });
      const raw = await redisCommand("GET", JARVIS_PREFIX + "device:" + target);
      if (!raw) return res.status(404).json({ error: "That device is not paired." });
      const item = typeof raw === "string" ? JSON.parse(raw) : raw;
      await redisCommand("DEL", JARVIS_PREFIX + "session:" + item.sessionHash, JARVIS_PREFIX + "device:" + target);
      await redisCommand("SREM", JARVIS_PREFIX + "devices", target);
      await audit("success", { deviceId: session.deviceId, deviceName: session.deviceName, role: "owner", targetDeviceId: target });
      return res.status(200).json({ revoked: true });
    }
    return res.status(400).json({ error: "Unknown device action." });
  } catch (error) {
    console.error("JARVIS pairing failure:", error);
    return res.status(500).json({ error: "JARVIS pairing service error: " + String(error?.message || error).slice(0, 180) });
  }
}
