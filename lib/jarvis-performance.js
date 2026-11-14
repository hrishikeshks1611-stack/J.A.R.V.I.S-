// Bounded response policy for J.A.R.V.I.S 15.0.
// Fast turns use a smaller output budget; multi-step or attachment requests retain
// the fuller reasoning/tool budget. Every provider attempt shares one wall-clock cap.
const ACTION_INTENT = /\b(create|make|prepare|generate|export|convert|build|draft|search|find|look up|book|schedule|set|call|phone|dial|play|pause|resume|open|send|delete|remove|update|change|install|download|upload|pair|revoke|transfer|compare|analyse|analyze|research|plan|organise|organize|execute|run)\b/i;
const COMPLEX_INTENT = /\b(step[- ]by[- ]step|in detail|comprehensive|deep dive|compare|analyse|analyze|research|plan|multiple|several|all of|entire|attached|document|file)\b/i;

export function buildChatPerformancePolicy(message, { hasAttachment = false } = {}) {
  const text = String(message || "").trim();
  const quick = !hasAttachment && text.length <= 240 &&
    !ACTION_INTENT.test(text) && !COMPLEX_INTENT.test(text);
  return Object.freeze({
    tier: quick ? "quick" : "full",
    maxOutputTokens: quick ? 700 : 1400,
    maxModelSteps: quick ? 3 : 5,
    maxToolCalls: quick ? 3 : 6
  });
}

export function createModelRequestBudget({
  startedAt = Date.now(),
  totalBudgetMs = 38000,
  perCallTimeoutMs = 12000,
  minimumCallMs = 1500
} = {}) {
  const deadline = startedAt + totalBudgetMs;
  return Object.freeze({
    remainingMs(now = Date.now()) {
      return Math.max(0, deadline - now);
    },
    timeoutForNextCall(now = Date.now()) {
      const remaining = Math.max(0, deadline - now);
      return remaining < minimumCallMs ? 0 : Math.min(perCallTimeoutMs, remaining);
    }
  });
}
