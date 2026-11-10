import {
  getSession, readMemories, addMemory, updateMemory, deleteMemory, clearMemories
} from "../lib/jarvis-store.js";

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

export default async function handler(req, res) {
  if (!cors(req, res)) return res.status(403).json({ error: "This website is not authorised to access JARVIS memory." });
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Use POST for memory actions." });
  }

  let session;
  try {
    session = await getSession(req);
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS." });
  } catch (error) {
    console.error("JARVIS memory authentication failed:", error);
    return res.status(503).json({ error: "JARVIS shared memory is temporarily unavailable." });
  }

  try {
    const body = req.body || {};
    switch (String(body.action || "").toLowerCase()) {
      case "list":
        return res.status(200).json({ memories: await readMemories() });
      case "add": {
        const value = String(body.text || "").trim();
        if (!value) return res.status(400).json({ error: "Enter the exact note you want JARVIS to remember." });
        if (value.length > 1000) return res.status(413).json({ error: "A saved memory must be 1,000 characters or fewer." });
        const memories = await addMemory(value);
        return res.status(200).json({ memories, message: "Saved to your shared JARVIS memory." });
      }
      case "update": {
        const value = String(body.text || "").trim();
        if (!value) return res.status(400).json({ error: "Enter the revised memory text." });
        if (value.length > 1000) return res.status(413).json({ error: "A saved memory must be 1,000 characters or fewer." });
        const result = await updateMemory(body.id, value);
        if (!result.updated) return res.status(404).json({ error: "That saved memory no longer exists." });
        return res.status(200).json({ memories: result.items, message: "Saved memory updated." });
      }
      case "delete": {
        const result = await deleteMemory(body.id);
        if (!result.deleted) return res.status(404).json({ error: "That saved memory no longer exists." });
        return res.status(200).json({ memories: result.items, message: "Saved memory deleted." });
      }
      case "clear":
        if (body.confirm !== true) return res.status(400).json({ error: "Confirm before clearing all saved memories." });
        await clearMemories();
        return res.status(200).json({ memories: [], message: "All saved memories have been cleared." });
      default:
        return res.status(400).json({ error: "Choose a valid memory action: list, add, update, delete or clear." });
    }
  } catch (error) {
    console.error("JARVIS memory action failed:", error);
    return res.status(503).json({ error: "JARVIS could not update shared memory. Please try again shortly." });
  }
}
