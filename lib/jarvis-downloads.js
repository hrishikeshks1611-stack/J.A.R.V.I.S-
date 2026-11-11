import { randomBytes } from "node:crypto";

const DOWNLOAD_TTL_MS = 15 * 60 * 1000;
const MAX_DOWNLOADS = 20;
const MAX_FILE_BYTES = 30 * 1024 * 1024;
const downloads = new Map();

function prune(now = Date.now()) {
  for (const [token, item] of downloads) {
    if (item.expiresAt <= now) downloads.delete(token);
  }
  while (downloads.size >= MAX_DOWNLOADS) downloads.delete(downloads.keys().next().value);
}

export function createDownload(file) {
  if (!file?.buffer || !Buffer.isBuffer(file.buffer) || file.buffer.length === 0) {
    throw new Error("The generated file is empty.");
  }
  if (file.buffer.length > MAX_FILE_BYTES) throw new Error("The generated file exceeds the 30 MB download limit.");
  prune();
  const token = randomBytes(32).toString("hex");
  const filename = String(file.filename || "JARVIS-file").replace(/[^A-Za-z0-9._ -]/g, "_").slice(0, 120) || "JARVIS-file";
  const mimeType = String(file.mimeType || "application/octet-stream").slice(0, 120);
  downloads.set(token, { buffer: file.buffer, filename, mimeType, expiresAt: Date.now() + DOWNLOAD_TTL_MS });
  return { token, filename, mimeType, size: file.buffer.length, expiresInSeconds: DOWNLOAD_TTL_MS / 1000 };
}

export function consumeDownload(token) {
  if (!/^[a-f0-9]{64}$/.test(String(token || ""))) return null;
  const item = downloads.get(token);
  if (!item) return null;
  downloads.delete(token);
  if (item.expiresAt <= Date.now()) return null;
  return item;
}
