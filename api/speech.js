// J.A.R.V.I.S. cloud speech endpoint. The Gemini key remains server-side.
export default async function handler(req, res) {
  const origin = req.headers.origin || "";
  const allowedOrigin =
    origin === "https://hrishikeshks1611-stack.github.io" ||
    /^https:\/\/[^/]+\.vercel\.app$/.test(origin);

  if (origin && !allowedOrigin) {
    return res.status(403).json({ error: "This website is not authorised to use JARVIS speech." });
  }
  if (allowedOrigin) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  }
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST, OPTIONS");
    return res.status(405).json({ error: "Use POST for speech requests." });
  }

  const apiKey = process.env.JARVIS || process.env.GEMINI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "The Gemini API key is not configured on the JARVIS server." });

  const spokenText = String(req.body?.text || "").trim().slice(0, 4000);
  if (!spokenText) return res.status(400).json({ error: "There is no text to speak." });

  try {
    const prompt = [
      "Synthesize the spoken transcript below verbatim.",
      "Use a mature, smooth, articulate male voice with a natural English accent from the United Kingdom.",
      "Delivery: composed, warm, quietly confident, measured, polished and subtly dry; like a refined personal butler.",
      "Avoid an American accent, exaggerated theatrical acting, robotic cadence, or excessive enthusiasm.",
      "Spoken transcript:",
      spokenText
    ].join("\n");

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-tts-preview:generateContent",
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

    const raw = await response.text();
    let data = {};
    try { data = JSON.parse(raw); } catch { }
    if (!response.ok) {
      const detail = data?.error?.message || raw.slice(0, 500) || "Google returned no error detail.";
      console.error("Gemini TTS error:", response.status, detail);
      return res.status(response.status === 429 ? 429 : 502).json({
        error: "The speech service returned HTTP " + response.status + ": " + detail
      });
    }

    const encoded = data?.candidates?.[0]?.content?.parts?.find(part => part.inlineData?.data)?.inlineData?.data;
    if (!encoded) {
      return res.status(502).json({ error: "Gemini returned no audio. Please try the speech test again." });
    }

    const pcm = Buffer.from(encoded, "base64");
    // Gemini returns mono, 24 kHz, signed 16-bit little-endian PCM. Wrap it in a WAV header for WebView playback.
    const wav = Buffer.alloc(44 + pcm.length);
    wav.write("RIFF", 0);
    wav.writeUInt32LE(36 + pcm.length, 4);
    wav.write("WAVE", 8);
    wav.write("fmt ", 12);
    wav.writeUInt32LE(16, 16);
    wav.writeUInt16LE(1, 20);
    wav.writeUInt16LE(1, 22);
    wav.writeUInt32LE(24000, 24);
    wav.writeUInt32LE(48000, 28);
    wav.writeUInt16LE(2, 32);
    wav.writeUInt16LE(16, 34);
    wav.write("data", 36);
    wav.writeUInt32LE(pcm.length, 40);
    pcm.copy(wav, 44);

    res.setHeader("Content-Type", "audio/wav");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Content-Length", String(wav.length));
    return res.status(200).send(wav);
  } catch (error) {
    console.error("JARVIS speech failure:", error);
    return res.status(500).json({ error: "JARVIS could not generate speech: " + String(error?.message || error).slice(0, 250) });
  }
}
