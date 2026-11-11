const base = (process.env.JARVIS_PRODUCTION_API || "https://jarvis-sigma-one-92.vercel.app/api").replace(/\\/+$/, "");

async function post(path, payload = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);
  try {
    return await fetch(base + "/" + path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal
    });
  } finally {
    clearTimeout(timer);
  }
}

const pairing = await post("pair", { action: "status" });
if (!pairing.ok) throw new Error("Production pairing status expected HTTP 200; received " + pairing.status + ".");
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

console.log("Read-only production smoke passed: pairing status is healthy and chat/speech/memory/backup reject unauthenticated requests. No user session, conversation, memory, or backup data was accessed.");
