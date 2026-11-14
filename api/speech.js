// J.A.R.V.I.S. low-latency streaming speech endpoint.
// Keeps the exact Gemini 3.1 Flash TTS model, Algieba voice and en-GB locale.
import { getSession } from "../lib/jarvis-store.js";

export default async function handler(req, res) {
  const origin = req.headers.origin || "";
  const allowedOrigin =
    origin === "https://hrishikeshks1611-stack.github.io" ||
    /^https:\/\/[^/]+\.(?:vercel\.app|onrender\.com)$/.test(origin);

  if (origin && !allowedOrigin) {
    return res.status(403).json({ error: "This website is not authorised to use JARVIS speech." });
  }
  if (allowedOrigin) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  }
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Use POST for speech requests." });
  }

  try {
    // Chat requests renew the session TTL; speech only validates it. Avoid
    // two extra Redis writes on every spoken answer.
    const session = await getSession(req, { touch: false });
    if (!session) return res.status(401).json({ error: "This device is not paired with JARVIS. Open Devices & Sync to pair it." });
  } catch (error) {
    console.error("JARVIS speech session check failed:", error);
    return res.status(503).json({ error: "JARVIS shared memory is temporarily unavailable." });
  }

  const apiKey = process.env.JARVIS || process.env.GEMINI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "The Gemini API key is not configured on the JARVIS server." });

  const spokenText = String(req.body?.text || "").trim().slice(0, 4000);
  if (!spokenText) return res.status(400).json({ error: "There is no text to speak." });

  try {
    const prompt = [
      "Speak the supplied transcript exactly as written. Do not paraphrase, omit, add, or announce anything.",
      "VOICE: Use Algieba, a male voice in Google’s published Gemini TTS voice catalogue. Preserve a distinctly masculine adult voice; never switch to a female voice or another speaker identity.",
      "ACCENT: Use clearly recognisable contemporary British English pronunciation, preferably educated Southern British / understated modern RP. This must sound British, not American.",
      "PRONUNCIATION: Use non-rhotic British pronunciation: do not pronounce an R at the end of a word or before a consonant. Use British vowel quality and British word stress. Avoid American R-colouring, American vowel shifts, and American intonation.",
      "DELIVERY: A polished, discreet, exceptionally articulate British personal butler: calm, measured, warm but reserved, quietly confident, precise diction, natural connected speech, controlled pacing, and understated dry wit where the words allow it.",
      "MANNER: Sound naturally courteous and intelligent, never servile, over-formal, breathless, or theatrical. Use subtle sentence-level intonation and gentle emphasis on meaningful words. Keep pauses brief and purposeful.",
      "AVOID: American-sounding pronunciation, exaggerated aristocratic parody, stage acting, announcer or audiobook performance, robotic rhythm, excessive cheerfulness, and dramatic emotional swings.",
      "Do not let the style instructions alter the words being spoken.",
      "Spoken transcript:",
      spokenText
    ].join("\n");

    const upstream = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-tts-preview:streamGenerateContent?alt=sse",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            responseModalities: ["AUDIO"],
            speechConfig: {
              voiceConfig: { prebuiltVoiceConfig: { voiceName: "Algieba" } },
              languageCode: "en-GB"
            }
          }
        })
      }
    );

    if (!upstream.ok) {
      const raw = await upstream.text();
      let data = {};
      try { data = JSON.parse(raw); } catch { }
      const detail = data?.error?.message || raw.slice(0, 500) || "Google returned no error detail.";
      console.error("Gemini streaming TTS error:", upstream.status, detail);
      if (upstream.status === 429) {
        const retryAfter = upstream.headers.get("retry-after");
        if (retryAfter && /^\d+$/.test(retryAfter)) res.setHeader("Retry-After", retryAfter);
      }
      return res.status(upstream.status === 429 ? 429 : 502).json({
        error: "The speech service returned HTTP " + upstream.status + ": " + detail
      });
    }

    if (!upstream.body) return res.status(502).json({ error: "Gemini did not provide a speech stream." });

    res.setHeader("Content-Type", "application/octet-stream");
    res.setHeader("X-Jarvis-Audio-Format", "pcm_s16le;rate=24000;channels=1");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Accel-Buffering", "no");
    const reader = upstream.body.getReader();
    const decoder = new TextDecoder();
    let pending = "";
    let sentAudio = false;
    let ended = false;

    const emitEvent = (eventText) => {
      const dataLines = eventText.split(/\r?\n/).filter(line => line.startsWith("data:"));
      if (!dataLines.length) return;
      const payload = dataLines.map(line => line.slice(5).trim()).join("\n");
      if (!payload || payload === "[DONE]") return;
      let event;
      try { event = JSON.parse(payload); } catch { return; }
      const parts = event?.candidates?.[0]?.content?.parts || [];
      for (const part of parts) {
        const encoded = part?.inlineData?.data || part?.inline_data?.data;
        if (!encoded) continue;
        const pcm = Buffer.from(encoded, "base64");
        if (!pcm.length) continue;
        if (!sentAudio) {
          sentAudio = true;
          res.flushHeaders?.();
        }
        res.write(pcm);
      }
      if (event?.error) console.error("Gemini TTS stream event error:", event.error);
    };

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        pending += decoder.decode(value, { stream: true });
        const events = pending.split(/\r?\n\r?\n/);
        pending = events.pop() || "";
        for (const eventText of events) emitEvent(eventText);
      }
      pending += decoder.decode();
      if (pending.trim()) emitEvent(pending);
      ended = true;
    } finally {
      if (!ended) await reader.cancel().catch(() => {});
    }

    if (!sentAudio) {
      if (!res.headersSent) return res.status(502).json({ error: "Gemini returned no audio. Please try again." });
      return res.end();
    }
    return res.end();
  } catch (error) {
    console.error("JARVIS streaming speech failure:", error);
    if (!res.headersSent) return res.status(500).json({ error: "JARVIS could not generate speech: " + String(error?.message || error).slice(0, 250) });
    return res.end();
  }
}
