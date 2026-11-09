// J.A.R.V.I.S. server-side AI endpoint for Vercel.
// Configure GEMINI_API_KEY in the hosting provider's environment variables.
export default async function handler(req, res) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Use POST for assistant requests." });
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({
      error: "The AI service is not configured yet. Add GEMINI_API_KEY to the server environment."
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
      "You cannot control the user's phone, laptop, or TV unless a real tool explicitly provides that capability. Do not claim to have done so.",
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
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + encodeURIComponent(apiKey),
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: system }] },
          contents,
          generationConfig: { temperature: 0.7, maxOutputTokens: 1800 }
        })
      }
    );

    const data = await response.json();
    if (!response.ok) {
      const detail = data && data.error && data.error.message ? data.error.message : "The AI provider returned an error.";
      console.error("Gemini API error:", response.status, detail);
      return res.status(response.status === 429 ? 429 : 502).json({
        error: response.status === 429
          ? "The free AI request limit has been reached for now. Please try again later."
          : "The AI service returned an error. Please check the server configuration."
      });
    }

    const reply = (data.candidates || [])
      .flatMap(candidate => candidate.content && candidate.content.parts ? candidate.content.parts : [])
      .map(part => part.text || "")
      .join("")
      .trim();

    if (!reply) return res.status(502).json({ error: "The AI service returned an empty response. Please try again." });
    return res.status(200).json({ reply });
  } catch (error) {
    console.error("JARVIS API failure:", error);
    return res.status(500).json({ error: "J.A.R.V.I.S. encountered a server error. Please try again." });
  }
}
