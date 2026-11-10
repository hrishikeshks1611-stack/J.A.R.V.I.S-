import test from "node:test";
import assert from "node:assert/strict";
import { createArtifactFile, validateArtifact } from "../lib/jarvis-files.js";
import createFileHandler from "../api/create-file.js";

const sample = {
  title: "Quarterly Operations Summary",
  subtitle: "Illustrative test document",
  sections: [
    { heading: "Overview", paragraphs: ["This is a generated sample used to verify JARVIS file exports."], bullets: ["Clear structure", "Editable output"] }
  ],
  tables: [{ title: "Sample data", columns: ["Item", "Quantity"], rows: [["Pens", "12"], ["Books", "4"]] }],
  sheets: [{ name: "Budget", columns: ["Category", "Amount"], rows: [["Stationery", "1200"], ["Travel", "800"]] }],
  slides: [{ title: "Overview", bullets: ["Purpose", "Results", "Next steps"], notes: "Presenter notes." }],
  references: []
};

test("office file endpoint module loads", () => {
  assert.equal(typeof createFileHandler, "function");
});

test("artifact schema rejects unsupported formats and empty content", () => {
  assert.throws(() => validateArtifact(sample, "exe"), /Choose PDF/);
  assert.throws(() => validateArtifact({ title: "Empty" }, "pdf"), /no usable content/);
});

for (const format of ["pdf", "docx", "xlsx", "pptx", "csv", "txt", "md"]) {
  test(format.toUpperCase() + " export returns a non-empty file with a safe filename", async () => {
    const file = await createArtifactFile(sample, format);
    assert.ok(file.buffer.length > 100);
    assert.match(file.filename, /^[A-Za-z0-9-]+\./);
    assert.ok(file.mimeType);
    if (format === "pdf") assert.equal(file.buffer.subarray(0, 5).toString(), "%PDF-");
    if (format === "xlsx" || format === "docx" || format === "pptx") {
      assert.equal(file.buffer.subarray(0, 2).toString(), "PK");
    }
    if (format === "csv") assert.match(file.buffer.toString("utf8"), /Category|Item/);
  });
}
