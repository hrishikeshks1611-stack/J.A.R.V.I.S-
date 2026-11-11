import { readFile } from "node:fs/promises";

// Test the API host actually configured in the frontend. Do not silently smoke-test
// an unrelated Vercel deployment when the PWA is configured to use Render.
const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
const configuredApi = html.match(/const JARVIS_API='([^']+)'/)?.[1];
const rawBase = process.env.JARVIS_PRODUCTION_API || configuredApi;
if (!rawBase) throw new Error("No production API base is configured in index.html or JARVIS_PRODUCTION_API.");

const baseUrl = new URL(rawBase);
if (baseUrl.protocol !== "https:") throw new Error("The production API smoke requires HTTPS.");
const base = baseUrl.toString().replace(/\/$/, "");

async function request(url, options = {}, timeoutMs = 30000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function retry(label, action, attempts = 3) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await action();
    } catch (error) {
      lastError = error;
      if (attempt < attempts) await new Promise(resolve => setTimeout(resolve, attempt * 4000));
    }
  }
  throw new Error(label + " failed after " + attempts + " bounded attempts: " + String(lastError?.message || lastError));
}

async function post(path, payload = {}) {
  return request(base + "/" + path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Origin": "https://hrishikeshks1611-stack.github.io"
    },
    body: JSON.stringify(payload)
  });
}

const apiHost = baseUrl.hostname;
if (apiHost.endsWith(".onrender.com")) {
  const healthUrl = new URL("/healthz", baseUrl.origin);
  const healthResponse = await retry("Render health check", async () => {
    const response = await request(healthUrl, {}, 45000);
    if (!response.ok) throw new Error("HTTP " + response.status);
    return response;
  });
  const health = await healthResponse.json();
  if (health.ok !== true || !health.features?.sharedConversationPersistenceConfigured) {
    throw new Error("Render health check did not confirm shared-memory persistence configuration.");
  }
  if (!health.features?.fileCreation) {
    throw new Error("Render health check did not detect a configured Gemini API key.");
  }
  console.log("Configured Render API health check passed; shared-memory and Gemini key presence are configured. Credential validity is not proven by this check.");
}

const pairing = await retry("Production pairing status", async () => {
  const response = await post("pair", { action: "status" });
  if (!response.ok) throw new Error("HTTP " + response.status);
  return response;
});
const status = await pairing.json();
if (typeof status.ownerClaimed !== "boolean") {
  throw new Error("Production pairing status did not return the expected ownerClaimed boolean.");
}

for (const endpoint of ["chat", "speech", "memory", "backup"]) {
  const response = await post(endpoint, {});
  if (response.status !== 401) {
    throw new Error("Production " + endpoint + " auth gate expected HTTP 401 without a session; received " + response.status + ".");
  }
}

console.log("Read-only production smoke passed against the frontend-configured API: pairing status is healthy and chat/speech/memory/backup reject unauthenticated requests. No user session, conversation, memory, or backup data was accessed.");
