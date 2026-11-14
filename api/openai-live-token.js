// OpenAI Realtime session credentials. The long-lived API key never leaves this server.
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
    return res.status(405).json({ error: "Use POST for Live Voice credentials." });
  }

  try {
    const session = await getSession(req, { touch: false });
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS." });
  } catch (error) {
    console.error("JARVIS Realtime session validation failed:", error);
    return res.status(503).json({ error: "JARVIS shared memory is temporarily unavailable." });
  }

  const apiKey = process.env.OPENAI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "OpenAI is not configured on the JARVIS server. Add OPENAI_API_KEY first." });

  try {
    const model = String(process.env.OPENAI_REALTIME_MODEL || "gpt-realtime-1.5").trim();
    const upstream = await fetch("https://api.openai.com/v1/realtime/client_secrets", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + apiKey,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        expires_after: { anchor: "created_at", seconds: 120 },
        session: {
          type: "realtime",
          model,
          instructions: [
            "You are J.A.R.V.I.S., the user's capable personal AI assistant.",
            "Speak in concise, natural, articulate British English with a composed, discreet, masculine butler-like delivery.",
            "Be useful and direct. Do not claim access to phone apps, files, devices, or accounts unless an available integration actually provides it.",
            "Never perform banking, UPI, wallet, payment, purchase, or other financial transactions.",
            "Ask for explicit confirmation before consequential actions. If a request needs a phone integration not available in this voice session, explain that and direct the user to text mode.",
            "Do not invent facts or pretend a task has been completed."
          ].join("\n"),
          output_modalities: ["audio"],
          audio: {
            input: {
              noise_reduction: { type: "near_field" },
              transcription: { model: "gpt-4o-mini-transcribe", language: "en" },
              turn_detection: { type: "server_vad", threshold: 0.5, prefix_padding_ms: 300, silence_duration_ms: 500 }
            },
            output: { voice: "cedar", speed: 1.0 }
          },
          max_output_tokens: 1200
        }
      })
    });
    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok || !data.value) {
      const detail = data?.error?.message || "OpenAI did not issue a Realtime client secret.";
      console.error("JARVIS Realtime credential provisioning failed:", upstream.status, detail);
      return res.status(upstream.status === 429 ? 429 : 502).json({
        error: "Live Voice could not start: " + String(detail).slice(0, 240)
      });
    }
    return res.status(200).json({
      token: data.value,
      expiresAt: data.expires_at,
      model: data.session?.model || model,
      transport: "webrtc"
    });
  } catch (error) {
    console.error("JARVIS Realtime credential error:", error);
    return res.status(502).json({ error: "JARVIS could not provision a Live Voice session." });
  }
}
