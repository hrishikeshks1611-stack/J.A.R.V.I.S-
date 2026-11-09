export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "Use POST for assistant requests." });
  const body = req.body || {};
  const message = String(body.message || "").trim();
  const mode = String(body.mode || "witty");
  if (!message) return res.status(400).json({ error: "Please enter a request." });
  const q = message.toLowerCase();
  const prefix = mode === "philosophical" ? "A thought, Sir: " : mode === "witty" ? "Certainly, Sir. " : "";
  let reply;
  if (/\b(hello|hi|hey|good morning|good afternoon|good evening)\b/.test(q)) reply = "Good day, Sir. All systems are standing by. What can I do for you?";
  else if (/\b(time|what time)\b/.test(q)) reply = "The current time on this device is " + new Date().toLocaleTimeString("en-IN", {hour:"2-digit",minute:"2-digit",timeZone:"Asia/Kolkata"}) + ", Sir.";
  else if (/\b(date|today'?s date|what day)\b/.test(q)) reply = "Today is " + new Date().toLocaleDateString("en-IN", {weekday:"long",day:"numeric",month:"long",year:"numeric",timeZone:"Asia/Kolkata"}) + ", Sir.";
  else if (/\b(who are you|your name|introduce yourself)\b/.test(q)) reply = "I am J.A.R.V.I.S., your personal assistant interface, Sir. At present I can handle a small set of built-in routines without an external AI service.";
  else if (/\b(search|google|look up)\b/.test(q)) { const term=message.replace(/^(please\s+)?(search( for)?|google|look up)\s*/i,"").trim(); reply = term ? "I can open a web search for: " + term + ". Use this link: https://www.google.com/search?q=" + encodeURIComponent(term) : "What would you like me to search for, Sir?"; }
  else if (/\b(open|visit|go to)\b/.test(q)) { const sites=[["youtube","https://youtube.com"],["google","https://google.com"],["gmail","https://mail.google.com"],["wikipedia","https://wikipedia.org"],["github","https://github.com"],["chatgpt","https://chatgpt.com"]]; const hit=sites.find(([n])=>q.includes(n)); reply=hit ? "You can open " + hit[0] + " here: " + hit[1] : "I can open common sites such as Google, YouTube, Gmail, Wikipedia, GitHub, or ChatGPT. Which one, Sir?"; }
  else if (/\b(thank you|thanks)\b/.test(q)) reply = "Always a pleasure, Sir.";
  else if (/\b(help|what can you do|commands)\b/.test(q)) reply = "My current no-key routines can tell the time and date, greet you, suggest common websites, and prepare web-search links. I can also listen and speak where your browser supports it. Open-ended reasoning and tutoring require an AI model; this build does not pretend otherwise.";
  else reply = prefix + "my no-key offline routine cannot reason through that request yet, Sir. It can handle greetings, the time, the date, basic help, and common website or web-search requests. Genuine open-ended conversation needs an AI model or a local model running on your device.";
  return res.status(200).json({ reply });
}