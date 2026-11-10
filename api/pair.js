import {
  getSession, hashToken, randomToken, redisCommand, safeEqual,
  readHistory, saveHistory, JARVIS_PREFIX, JARVIS_SESSION_TTL
} from "./lib/jarvis-store.js";

function cors(req, res) {
  const origin = req.headers.origin || "";
  const allowed = origin === "https://hrishikeshks1611-stack.github.io" ||
    /^https:\/\/[^/]+\.vercel\.app$/.test(origin);
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
    if (action === "status") {
      const claimed = await redisCommand("GET", JARVIS_PREFIX + "owner:claimed");
      return res.status(200).json({ ownerClaimed: Boolean(claimed) });
    }

    if (action === "claim") {
      const supplied = String(body.setupCode || "");
      const expected = process.env.JARVIS_SETUP_CODE || "";
      if (!expected || !safeEqual(supplied, expected)) return res.status(401).json({ error: "That owner setup code is not valid." });
      const lock = await redisCommand("SET", JARVIS_PREFIX + "owner:claim-lock", randomToken(12), "NX", "EX", "30");
      if (!lock) return res.status(409).json({ error: "Owner setup is already being completed. Please try again." });
      try {
        const already = await redisCommand("GET", JARVIS_PREFIX + "owner:claimed");
        if (already) return res.status(409).json({ error: "JARVIS already has an owner device. Pair this device using a pairing code." });
        const result = await createSession(deviceName(body.deviceName), "owner");
        const ownerSet = await redisCommand("SET", JARVIS_PREFIX + "owner:claimed", "1", "NX");
        if (!ownerSet) {
          await redisCommand("DEL", JARVIS_PREFIX + "session:" + hashToken(result.token));
          return res.status(409).json({ error: "JARVIS owner setup was claimed by another device. Please pair instead." });
        }
        await redisCommand("SET", JARVIS_PREFIX + "owner:token-hash", hashToken(result.token));
        return res.status(200).json({ ...result, message: "This device is now the JARVIS owner device." });
      } finally {
        await redisCommand("DEL", JARVIS_PREFIX + "owner:claim-lock").catch(() => {});
      }
    }

    if (action === "pair") {
      const code = String(body.pairingCode || "").replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
      if (!code) return res.status(400).json({ error: "Enter the pairing code shown on your owner device." });
      const raw = await redisCommand("GETDEL", JARVIS_PREFIX + "pair:" + hashToken(code));
      if (!raw) return res.status(401).json({ error: "That pairing code has expired or has already been used. Generate a new one on your owner device." });
      const pairing = typeof raw === "string" ? JSON.parse(raw) : raw;
      const result = await createSession(deviceName(body.deviceName), "device");
      return res.status(200).json({ ...result, message: "This device is now paired with JARVIS." });
    }

    const session = await getSession(req);
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS. Open Devices & Sync to pair it." });

    if (action === "verify") {
      return res.status(200).json({ deviceId: session.deviceId, deviceName: session.deviceName, role: session.role });
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
      const code = randomToken(6).replace(/[^a-zA-Z0-9]/g, "").slice(0, 8).toUpperCase();
      await redisCommand("SET", JARVIS_PREFIX + "pair:" + hashToken(code), JSON.stringify({ createdBy: session.deviceId, createdAt: Date.now() }), "EX", "600", "NX");
      return res.status(200).json({ code, expiresIn: 600 });
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
    if (action === "revoke") {
      if (session.role !== "owner") return res.status(403).json({ error: "Only the owner device can revoke access." });
      const target = String(body.deviceId || "");
      if (!target || target === session.deviceId) return res.status(400).json({ error: "You cannot revoke the owner device from itself." });
      const raw = await redisCommand("GET", JARVIS_PREFIX + "device:" + target);
      if (!raw) return res.status(404).json({ error: "That device is not paired." });
      const item = typeof raw === "string" ? JSON.parse(raw) : raw;
      await redisCommand("DEL", JARVIS_PREFIX + "session:" + item.sessionHash, JARVIS_PREFIX + "device:" + target);
      await redisCommand("SREM", JARVIS_PREFIX + "devices", target);
      return res.status(200).json({ revoked: true });
    }
    return res.status(400).json({ error: "Unknown device action." });
  } catch (error) {
    console.error("JARVIS pairing failure:", error);
    return res.status(500).json({ error: "JARVIS pairing service error: " + String(error?.message || error).slice(0, 180) });
  }
}
