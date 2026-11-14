// Bounded lexical retrieval for shared J.A.R.V.I.S context.
// This is deliberately deterministic: it selects user-approved data but never
// turns retrieved content into instructions or executable tool arguments.
const STOP_WORDS = new Set([
  "about", "after", "again", "also", "and", "are", "because", "been", "before",
  "being", "between", "could", "did", "does", "doing", "from", "have", "here",
  "into", "just", "like", "more", "most", "much", "need", "only", "other",
  "our", "please", "same", "should", "some", "than", "that", "their", "them",
  "then", "there", "these", "they", "this", "those", "through", "very", "want",
  "what", "when", "where", "which", "while", "will", "with", "would", "your"
]);

function terms(value) {
  return String(value || "")
    .toLowerCase()
    .match(/[a-z0-9]{2,}/g)?.filter(word => !STOP_WORDS.has(word)) || [];
}

function score(text, queryTerms, queryText, recency = 0) {
  const value = String(text || "").toLowerCase();
  if (!value || !queryTerms.length) return 0;
  const words = new Set(terms(value));
  let matches = 0;
  for (const word of queryTerms) if (words.has(word)) matches++;
  if (!matches) return 0;
  const coverage = matches / queryTerms.length;
  const phraseBonus = queryText.length >= 5 && value.includes(queryText) ? 1.5 : 0;
  return coverage * 4 + phraseBonus + recency * 0.2;
}

function rank(items, queryTerms, queryText, limit, textOf) {
  return items.map((item, index) => ({
    item,
    index,
    score: score(textOf(item), queryTerms, queryText, (index + 1) / Math.max(1, items.length))
  })).filter(entry => entry.score > 0)
    .sort((a, b) => b.score - a.score || b.index - a.index)
    .slice(0, limit)
    .sort((a, b) => a.index - b.index)
    .map(entry => entry.item);
}

export function selectRelevantContext({
  history = [],
  memories = [],
  query = "",
  historyLimit = 8,
  memoryLimit = 8
} = {}) {
  const safeHistory = Array.isArray(history)
    ? history.filter(item => item && (item.role === "user" || item.role === "model") && typeof item.text === "string").slice(-40)
    : [];
  const safeMemories = Array.isArray(memories)
    ? memories.filter(item => item && typeof item.text === "string").slice(-100)
    : [];
  const q = String(query || "").trim().toLowerCase().slice(0, 12000);
  const queryTerms = [...new Set(terms(q))];
  const hLimit = Math.max(0, Math.min(12, Number(historyLimit) || 0));
  const mLimit = Math.max(0, Math.min(12, Number(memoryLimit) || 0));

  const relevantHistory = rank(safeHistory, queryTerms, q, Math.max(1, Math.floor(hLimit / 2)), item => item.text);
  let selectedHistory = [];
  if (relevantHistory.length && hLimit) {
    // Keep the response paired with a relevant user turn (and vice versa).
    // This preserves conversational meaning without importing unrelated turns.
    const chosen = new Set(relevantHistory);
    for (const item of relevantHistory) {
      const index = safeHistory.indexOf(item);
      if (item.role === "user" && safeHistory[index + 1]?.role === "model") chosen.add(safeHistory[index + 1]);
      if (item.role === "model" && safeHistory[index - 1]?.role === "user") chosen.add(safeHistory[index - 1]);
    }
    selectedHistory = safeHistory.filter(item => chosen.has(item)).slice(-hLimit);
  } else if (hLimit) {
    // Follow-up questions often use pronouns rather than topic words. When no
    // lexical match exists, retain a small recent window rather than inventing
    // a topic or dropping all conversational continuity.
    selectedHistory = safeHistory.slice(-Math.min(4, hLimit));
  }

  const selectedMemories = rank(safeMemories, queryTerms, q, mLimit, item => item.text);
  return { history: selectedHistory, memories: selectedMemories };
}
