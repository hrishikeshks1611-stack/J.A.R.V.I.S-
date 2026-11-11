import { consumeDownload } from "../lib/jarvis-downloads.js";

export default function handler(req, res, token) {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    res.writeHead(405);
    res.end("Method not allowed");
    return;
  }
  const file = consumeDownload(token);
  if (!file) {
    res.writeHead(404, { "Cache-Control": "no-store", "Content-Type": "text/plain; charset=utf-8" });
    res.end("This JARVIS download link has expired or has already been used. Create the file again, Sir.");
    return;
  }
  const dispositionName = file.filename.replace(/[\r\n"\\]/g, "_");
  res.writeHead(200, {
    "Content-Type": file.mimeType,
    "Content-Length": file.buffer.length,
    "Content-Disposition": "attachment; filename=\"" + dispositionName + "\"",
    "Cache-Control": "no-store, private, max-age=0",
    "Pragma": "no-cache",
    "X-Content-Type-Options": "nosniff"
  });
  res.end(file.buffer);
}
