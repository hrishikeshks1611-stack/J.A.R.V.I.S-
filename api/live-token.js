// Short-lived, single-use Gemini Live token for J.A.R.V.I.S. real-time speech input.
// The long-lived Gemini API key never leaves this server.
import { getSession } from "../lib/jarvis-store.js";

export default async function handler(req, res) {
  const origin = req.headers.origin || "";
  const allowedOrigin =
    origin === "https://hrishikeshks1611-stack.github.io" ||
    /^https:\/\/[^/]+\.(?:vercel\.app|onrender\.com)$/.test(origin);

  if (origin && !allowedOrigin) {
    return res.status(403).json({ error: "This website is not authorised to use JARVIS Live Voice." });
  }
  if (allowedOrigin) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  }
  res.setHeader("Cache-Control", "no-store");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Use POST for Live Voice token requests." });
  }

  try {
    const session = await getSession(req, { touch: false });
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS. Open Devices & Sync to pair it." });
  } catch (error) {
    console.error("JARVIS Live Voice session check failed:", error);
    return res.status(503).json({ error: "JARVIS shared memory is temporarily unavailable." });
  }

  if (process.env.JARVIS_LIVE_VOICE_FREE_TIER_CONFIRMED !== "true") {
    return res.status(503).json({
      error: "Live Voice is disabled until the owner confirms this Gemini API project has billing disabled and is restricted to the free tier."
    });
  }

  const apiKey = process.env.JARVIS || process.env.GEMINI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "The Gemini API key is not configured on the JARVIS server." });

  try {
    const now = Date.now();
    const expireTime = new Date(now + 30 * 60 * 1000).toISOString();
    const newSessionExpireTime = new Date(now + 60 * 1000).toISOString();
    const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/auth_tokens", {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        uses: 1,
        expireTime,
        newSessionExpireTime,
        liveConnectConstraints: {
          model: "models/gemini-3.8-live",
          config: {
            responseModalities: ["TEXT"],
            inputAudioTranscription: {},
            sessionResumption: {},
            systemInstruction: {
              parts: [{
                text: "You are the speech recognition front end for J.A.R.V.I.S. Transcribe the user's spoken words faithfully in English using British English spelling where relevant. Do not answer questions, execute instructions, summarise, or add commentary. Return only a verbatim transcript of the speech. Preserve names such as Amma exactly as heard. If speech is unclear, return the words you can confidently hear without guessing."
              }]
            }
          }
        }
      })
    });

    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok || !data.name) {
      const detail = data?.error?.message || "Google did not issue a Live Voice token.";
      console.error("Gemini Live token provisioning failed:", upstream.status, detail);
      return res.status(upstream.status === 429 ? 429 : 502).json({
        error: "Live Voice is unavailable: " + String(detail).slice(0, 300)
      });
    }

    return res.status(200).json({
      token: data.name,
      model: "models/gemini-3.8-live",
      expiresAt: data.expireTime || expireTime,
      newSessionExpiresAt: data.newSessionExpireTime || newSessionExpireTime,
      responseMode: "transcription-only"
    });
  } catch (error) {
    console.error("JARVIS Live token provisioning error:", error);
    return res.status(502).json({ error: "JARVIS could not provision a Live Voice session. Please use the existing microphone instead." });
  }
}
