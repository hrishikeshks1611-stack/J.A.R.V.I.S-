import { getSession } from "../lib/jarvis-store.js";
import { searchYouTubeMusicSongs } from "../lib/ytmusic-search.js";

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

export default async function handler(req, res) {
  if (!cors(req, res)) return res.status(403).json({ error: "This website is not authorised to use JARVIS music search." });
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Use POST for YouTube Music search." });
  }

  const query = String(req.body?.query || "").trim();
  if (!query || query.length > 160) return res.status(400).json({ error: "Enter a song title of up to 160 characters." });

  try {
    const session = await getSession(req);
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS. Open Devices & Sync to pair it." });
    const items = await searchYouTubeMusicSongs(query);
    res.setHeader("Cache-Control", "no-store");
    if (!items.length) return res.status(404).json({ error: "I could not identify a matching song in YouTube Music.", items: [] });
    return res.status(200).json({ items, selected: items[0], source: "YouTube Music catalogue" });
  } catch (error) {
    console.error("JARVIS YouTube Music search failed:", error);
    return res.status(502).json({ error: "YouTube Music track lookup is temporarily unavailable. " + String(error?.message || error).slice(0, 180) });
  }
}
