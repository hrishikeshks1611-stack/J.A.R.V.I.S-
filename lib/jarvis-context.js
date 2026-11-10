const STOP_WORDS = new Set([
  "the","and","for","with","that","this","what","when","where","which","who","whom","whose",
  "how","why","can","could","would","should","will","shall","have","has","had","was","were",
  "are","is","am","be","been","being","you","your","yours","me","my","mine","we","our","ours",
  "they","them","their","it","its","to","of","in","on","at","by","from","as","an","a","or",
  "but","if","then","than","so","do","does","did","not","no","yes","please","sir","jarvis",
  "about","there","here","now","just","also","too","very","more","most","some","any","all",
  "i","im","ive","id","ill","he","she","his","her","hers","weve","theyve","them"
]);

function tokens(value) {
  return String(value || "").normalize("NFKC").toLowerCase()
    .match(/[\p{L}\p{N}]{2,}/gu)?.filter(word => !STOP_WORDS.has(word)) || [];
}

function similarity(queryTokens, candidate) {
  if (!queryTokens.length) return 0;
  const candidateTokens = new Set(tokens(candidate));
  if (!candidateTokens.size) return 0;
  let overlap = 0;
  for (const token of queryTokens) if (candidateTokens.has(token)) overlap++;
  const coverage = overlap / Math.sqrt(queryTokens.length * candidateTokens.size);
  const phrase = String(candidate || "").toLowerCase().includes(queryTokens.join(" ")) ? 0.35 : 0;
  return Math.min(1, coverage + phrase);
}

export function isFollowUpMessage(message) {
  const value = String(message || "").trim().toLowerCase();
  const wordCount = value.match(/\S+/g)?.length || 0;
  return wordCount <= 5 || /^(and|but|also|what about|how about|why|then|continue|do that|make it|change it|expand on|explain that|the second|option two)\b/.test(value);
}

export function selectConversationContext(history, message, options = {}) {
  const items = Array.isArray(history) ? history.filter(item =>
    item && (item.role === "user" || item.role === "model") && typeof item.text === "string"
  ) : [];
  const recentLimit = Math.max(4, Math.min(12, Number(options.recentLimit) || (isFollowUpMessage(message) ? 10 : 8)));
  const relevantLimit = Math.max(0, Math.min(4, Number(options.relevantLimit) || 3));
  const recentStart = Math.max(0, items.length - recentLimit);
  const queryTokens = [...new Set(tokens(message))];
  const selected = new Set();
  for (let i = recentStart; i < items.length; i++) selected.add(i);

  if (queryTokens.length && relevantLimit) {
    const ranked = [];
    for (let i = 0; i < recentStart; i++) {
      if (items[i].role !== "user") continue;
      const replyIndex = items[i + 1]?.role === "model" ? i + 1 : -1;
      const score = similarity(queryTokens, items[i].text);
      if (score >= 0.16) ranked.push({ index: i, replyIndex, score, recency: i / Math.max(1, items.length) });
    }
    ranked.sort((a,b) => (b.score + b.recency * 0.015) - (a.score + a.recency * 0.015));
    for (const item of ranked.slice(0, relevantLimit)) {
      selected.add(item.index);
      if (item.replyIndex >= 0) selected.add(item.replyIndex);
    }
  }

  return [...selected].sort((a,b) => a-b).map(index => ({
    role: items[index].role,
    text: String(items[index].text).slice(0, 2500)
  })).slice(-14);
}

export function selectRelevantMemories(memories, message, options = {}) {
  const items = Array.isArray(memories) ? memories.filter(item => item && typeof item.text === "string") : [];
  const limit = Math.max(0, Math.min(8, Number(options.limit) || 6));
  const maxChars = Math.max(300, Math.min(3000, Number(options.maxChars) || 1800));
  const queryTokens = [...new Set(tokens(message))];
  if (!queryTokens.length || !limit) return [];
  const ranked = items.map((item, index) => ({
    item,
    index,
    score: similarity(queryTokens, item.text) + index / Math.max(1, items.length) * 0.01
  })).filter(entry => entry.score >= 0.16)
    .sort((a,b) => b.score - a.score || b.index - a.index);
  const chosen = [];
  let used = 0;
  for (const entry of ranked) {
    const text = entry.item.text.trim().slice(0, 1000);
    if (!text || chosen.length >= limit || used + text.length > maxChars) continue;
    chosen.push({ id: entry.item.id || null, text });
    used += text.length;
  }
  return chosen;
}

// Long-term memory is written only when the user gives an unambiguous save command.
export function parseExplicitMemoryRequest(message) {
  const value = String(message || "").trim();
  const patterns = [
    /^(?:please\s+)?remember\s+that\s+(.{3,1000})$/i,
    /^(?:please\s+)?remember\s+(I\s+.{3,1000}|my\s+.{3,1000}|we\s+.{3,1000}|our\s+.{3,1000})$/i,
    /^(?:please\s+)?keep\s+in\s+mind\s+that\s+(.{3,1000})$/i,
    /^(?:please\s+)?save\s+(?:this|that)\s+(?:to\s+my\s+memory|as\s+a\s+memory)?\s*[:,-]?\s*(.{3,1000})$/i
  ];
  for (const pattern of patterns) {
    const match = value.match(pattern);
    if (match) {
      const note = match[1].trim();
      if (/\b(remind me|alarm|tomorrow|next week|at \d{1,2}(:\d{2})?)\b/i.test(note)) return null;
      return note.slice(0, 1000);
    }
  }
  return null;
}
