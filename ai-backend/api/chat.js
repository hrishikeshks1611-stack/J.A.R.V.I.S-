const WINDOW_MS = 60_000;
const MAX_REQUESTS = 12;
const buckets = new Map();

function clientKey(req) {
  const forwarded = req.headers["x-forwarded-for"];
  return (Array.isArray(forwarded) ? forwarded[0] : String(forwarded || "unknown"))
    .split(",")[0].trim().slice(0, 80);
}

function rateLimited(key) {
  const now = Date.now();
  const current = buckets.get(key);
  if (!current || now - current.start >= WINDOW_MS) {
    buckets.set(key, { start: now, count: 1 });
    return false;
  }
  current.count += 1;
  return current.count > MAX_REQUESTS;
}

module.exports = async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Use POST." });
  }

  const key = clientKey(req);
  if (rateLimited(key)) return res.status(429).json({ error: "Too many requests. Please wait a minute." });

  let body = req.body;
  if (typeof body === "string") {
    try { body = JSON.parse(body); } catch { body = null; }
  }
  if (!body || !Array.isArray(body.messages) || body.messages.length === 0 || body.messages.length > 14) {
    return res.status(400).json({ error: "A conversation with up to 14 messages is required." });
  }

  const messages = [];
  for (const item of body.messages) {
    if (!item || !["user", "assistant"].includes(item.role) || typeof item.content !== "string") {
      return res.status(400).json({ error: "Invalid conversation message." });
    }
    const content = item.content.trim();
    if (!content || content.length > 6000) return res.status(400).json({ error: "Message is empty or too long." });
    messages.push({ role: item.role, content });
  }
  if (!messages.some(m => m.role === "user")) return res.status(400).json({ error: "A user message is required." });

  const token = process.env.AI_GATEWAY_API_KEY || process.env.VERCEL_OIDC_TOKEN;
  if (!token) return res.status(503).json({ error: "AI Gateway is not configured on the server yet." });

  const system = {
    role: "system",
    content: "You are J.A.R.V.I.S., a capable, thoughtful personal AI assistant. Address the owner politely as Sir when natural, with a composed, intelligent, concise British-butler manner and restrained dry wit. You are a genuine conversational reasoning assistant: answer open-ended questions, explain, analyse, plan, write, brainstorm, and use the conversation context. Never claim to have performed an Android action. Phone actions are handled separately by the Android app; if asked to do one, explain that you can only confirm it when the app reports success. Do not invent facts, capabilities, device state, or completed actions. If uncertain, say so and reason carefully. Never mention Alfred. Respond in the user's language when clear."
  };

  try {
    const upstream = await fetch("https://ai-gateway.vercel.sh/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: process.env.AI_GATEWAY_MODEL || "openai/gpt-6-astra",
        messages: [system, ...messages],
        temperature: 0.7,
        max_tokens: 900
      }),
      signal: AbortSignal.timeout(40_000)
    });
    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      const detail = data && data.error && (data.error.message || data.error) || "AI provider request failed.";
      console.error("AI Gateway error", upstream.status, detail);
      return res.status(502).json({ error: "The AI provider rejected the request (" + upstream.status + "). Check Gateway access and billing." });
    }
    const reply = data && data.choices && data.choices[0] && data.choices[0].message
      && data.choices[0].message.content;
    if (typeof reply !== "string" || !reply.trim()) {
      console.error("AI Gateway returned no text", JSON.stringify(data).slice(0, 1000));
      return res.status(502).json({ error: "The AI provider returned no answer." });
    }
    return res.status(200).json({ reply: reply.trim() });
  } catch (error) {
    console.error("AI request failed", error && error.message);
    return res.status(504).json({ error: "The reasoning service timed out or could not be reached." });
  }
};
