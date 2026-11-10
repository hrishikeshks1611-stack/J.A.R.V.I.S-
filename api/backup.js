import {
  getSession, readHistory, readMemories, replaceBackup
} from "../lib/jarvis-store.js";

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

function validTimestamp(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : Date.now();
}

function validateBackup(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { error: "The backup document is not valid JSON data." };
  }
  if (value.format !== "jarvis-backup" || value.version !== 1) {
    return { error: "This backup format or version is not supported." };
  }
  if (!Array.isArray(value.conversation) || value.conversation.length > 40) {
    return { error: "A backup may contain at most 40 conversation messages." };
  }
  if (!Array.isArray(value.memories) || value.memories.length > 100) {
    return { error: "A backup may contain at most 100 saved memories." };
  }

  const conversation = [];
  for (const item of value.conversation) {
    if (!item || !["user", "model"].includes(item.role) ||
        typeof item.text !== "string" || !item.text.trim() || item.text.length > 12000) {
      return { error: "A conversation entry is malformed or exceeds the 12,000-character limit." };
    }
    conversation.push({
      role: item.role,
      text: item.text,
      createdAt: validTimestamp(item.createdAt)
    });
  }

  const memories = [];
  const ids = new Set();
  for (const item of value.memories) {
    if (!item || typeof item.id !== "string" ||
        !/^[A-Za-z0-9_-]{1,80}$/.test(item.id) ||
        ids.has(item.id) ||
        typeof item.text !== "string" || !item.text.trim() || item.text.length > 1000) {
      return { error: "A saved memory is malformed, duplicated, or exceeds the 1,000-character limit." };
    }
    ids.add(item.id);
    const memory = {
      id: item.id,
      text: item.text,
      createdAt: validTimestamp(item.createdAt)
    };
    if (Number.isFinite(Number(item.updatedAt)) && Number(item.updatedAt) > 0) {
      memory.updatedAt = Number(item.updatedAt);
    }
    memories.push(memory);
  }

  const normalized = {
    format: "jarvis-backup",
    version: 1,
    conversation,
    memories
  };
  if (JSON.stringify(normalized).length > 750000) {
    return { error: "This backup is too large. Export a fresh backup with fewer or shorter entries." };
  }
  return { backup: normalized };
}

export default async function handler(req, res) {
  if (!cors(req, res)) return res.status(403).json({ error: "This website is not authorised to access JARVIS backups." });
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Use POST for JARVIS backup actions." });
  }

  let session;
  try {
    session = await getSession(req);
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS." });
  } catch (error) {
    console.error("JARVIS backup authentication failed:", error);
    return res.status(503).json({ error: "JARVIS shared memory is temporarily unavailable." });
  }

  try {
    const body = req.body || {};
    if (body.action === "export") {
      const [conversation, memories] = await Promise.all([readHistory(), readMemories()]);
      return res.status(200).json({
        backup: {
          format: "jarvis-backup",
          version: 1,
          exportedAt: new Date().toISOString(),
          conversation,
          memories
        }
      });
    }
    if (body.action === "restore") {
      if (session.role !== "owner") {
        return res.status(403).json({ error: "Only the JARVIS owner device can restore a full backup." });
      }
      if (body.confirm !== true) {
        return res.status(400).json({ error: "Confirm before replacing shared conversation and saved memories." });
      }
      const checked = validateBackup(body.backup);
      if (checked.error) return res.status(400).json({ error: checked.error });
      const counts = await replaceBackup(checked.backup.conversation, checked.backup.memories);
      return res.status(200).json({
        restored: true,
        counts,
        message: "The full JARVIS backup has been restored across your paired devices."
      });
    }
    return res.status(400).json({ error: "Choose export or restore." });
  } catch (error) {
    console.error("JARVIS backup action failed:", error);
    return res.status(503).json({ error: "JARVIS could not complete the backup action. Please try again shortly." });
  }
}
