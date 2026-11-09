// J.A.R.V.I.S. server-side AI endpoint for Vercel.
// The Gemini key stays in Vercel Environment Variables; it is never sent to the browser.
export default async function handler(req, res) {
  const origin = req.headers.origin || "";
  const allowedOrigin =
    origin === "https://hrishikeshks1611-stack.github.io" ||
    /^https:\/\/[^/]+\.vercel\.app$/.test(origin);

  if (origin && !allowedOrigin) {
    return res.status(403).json({ error: "This website is not authorised to use the JARVIS AI service." });
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
    return res.status(405).json({ error: "Use POST for assistant requests." });
  }

  const apiKey = process.env.JARVIS || process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({
      error: "No Gemini API key is configured. Add GEMINI_API_KEY (or JARVIS) in this Vercel project's Environment Variables, then redeploy."
    });
  }

  try {
    const body = req.body || {};
    const message = String(body.message || "").trim().slice(0, 12000);
    const mode = String(body.mode || "witty").toLowerCase();
    if (!message) return res.status(400).json({ error: "Please enter a request." });

    const personality = {
      professional: "Be precise, calm, capable, concise, and professional.",
      witty: "Be composed, charming, subtly witty, and occasionally use dry British humour. Never let jokes obstruct clarity.",
      philosophical: "Offer thoughtful, reflective perspectives when appropriate, without becoming vague or preachy."
    }[mode] || "Be composed, helpful, and clear.";

    const system = [
      "You are J.A.R.V.I.S., a highly capable personal AI assistant.",
      "Address the user as Sir naturally, but do not begin every sentence with it.",
      "Use polished British English. Be warm, intelligent, candid, and practical.",
      personality,
      "Answer open-ended questions with real reasoning. Explain calculations and educational topics step by step when useful.",
      "If uncertain, say so. Never fabricate facts, actions, sources, or device access.",
      "You cannot control the user's phone, laptop, or TV unless a real tool explicitly provides that capability.",
      "Keep answers appropriately concise, but give enough detail to be genuinely useful."
    ].join(" ");

    const prior = Array.isArray(body.history) ? body.history.slice(-12) : [];
    const contents = prior
      .filter(item => item && (item.role === "user" || item.role === "model") && typeof item.text === "string")
      .map(item => ({ role: item.role, parts: [{ text: item.text.slice(0, 6000) }] }));
    if (!contents.length || contents[contents.length - 1].role !== "user" ||
        contents[contents.length - 1].parts[0].text !== message) {
      contents.push({ role: "user", parts: [{ text: message }] });
    }

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: system }] },
          contents,
          generationConfig: { temperature: 0.7, maxOutputTokens: 1800 }
        })
      }
    );

    const raw = await response.text();
    let data;
    try { data = JSON.parse(raw); } catch { data = {}; }

    if (!response.ok) {
      const detail = data?.error?.message || raw.slice(0, 500) || "No error detail returned by Google.";
      console.error("Gemini API error:", response.status, detail);
      return res.status(response.status === 429 ? 429 : 502).json({
        error: `Gemini rejected the request (HTTP ${response.status}): ${detail}`
      });
    }

    const reply = (data.candidates || [])
      .flatMap(candidate => candidate.content?.parts || [])
      .map(part => part.text || "")
      .join("")
      .trim();

    if (!reply) {
      const reason = data.promptFeedback?.blockReason || data.candidates?.[0]?.finishReason || "No text was returned.";
      return res.status(502).json({ error: "Gemini returned no answer: " + reason });
    }
    return res.status(200).json({ reply });
  } catch (error) {
    console.error("JARVIS API failure:", error);
    return res.status(500).json({ error: "JARVIS server error: " + String(error?.message || error).slice(0, 300) });
  }
}
