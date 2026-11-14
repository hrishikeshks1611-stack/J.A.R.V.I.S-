// Incremental Gemini text streaming for low-latency, text-only conversational turns.
export async function streamGeminiText({
  apiKey, model, system, contents, maxOutputTokens = 700,
  onDelta = () => {}, signal
}) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    signal,
    body: JSON.stringify({
      system_instruction: { parts: [{ text: system }] },
      contents,
      generationConfig: { temperature: 0.4, maxOutputTokens }
    })
  });
  if (!response.ok) {
    const detail = (await response.text()).slice(0, 500);
    const error = new Error(detail || `Gemini streaming returned HTTP ${response.status}.`);
    error.status = response.status;
    throw error;
  }
  if (!response.body?.getReader) throw new Error("The upstream response does not support streaming.");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "", reply = "";
  const consume = raw => {
    const line = raw.trim();
    if (!line.startsWith("data:")) return;
    const payload = line.slice(5).trim();
    if (!payload || payload === "[DONE]") return;
    let event;
    try { event = JSON.parse(payload); } catch { return; }
    const parts = event.candidates?.[0]?.content?.parts || [];
    for (const part of parts) {
      if (typeof part.text !== "string" || !part.text) continue;
      reply += part.text;
      onDelta(part.text);
    }
    if (event.error?.message) throw new Error(event.error.message);
  };
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() || "";
      for (const line of lines) consume(line);
    }
    buffer += decoder.decode();
    if (buffer.trim()) consume(buffer);
  } catch (error) {
    try { await reader.cancel(); } catch {}
    throw error;
  }
  if (!reply.trim()) throw new Error("Gemini streaming completed without a text response.");
  return reply.trim();
}
