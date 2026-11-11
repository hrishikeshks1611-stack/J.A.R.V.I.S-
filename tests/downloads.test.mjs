import test from "node:test";
import assert from "node:assert/strict";
import downloadHandler from "../api/download-file.js";
import { createDownload } from "../lib/jarvis-downloads.js";

function responseRecorder() {
  return {
    headers: {}, statusCode: 200, body: null,
    setHeader(key, value) { this.headers[key] = value; },
    writeHead(code, headers = {}) { this.statusCode = code; Object.assign(this.headers, headers); },
    end(body) { this.body = body; }
  };
}

test("download endpoint serves the file once with private attachment headers", () => {
  const file = createDownload({ filename: "Class-Presentation.pptx", mimeType: "application/vnd.openxmlformats-officedocument.presentationml.presentation", buffer: Buffer.from("PK-test") });
  const res = responseRecorder();
  downloadHandler({ method: "GET" }, res, file.token);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.toString(), "PK-test");
  assert.match(res.headers["Content-Disposition"], /attachment; filename="Class-Presentation.pptx"/);
  assert.equal(res.headers["Cache-Control"], "no-store, private, max-age=0");
  const second = responseRecorder();
  downloadHandler({ method: "GET" }, second, file.token);
  assert.equal(second.statusCode, 404);
});

test("download endpoint rejects non-GET requests", () => {
  const res = responseRecorder();
  downloadHandler({ method: "POST" }, res, "a".repeat(64));
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.Allow, "GET");
});
