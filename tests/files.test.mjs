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
  sheets: [{ name: "Budget", columns: ["Category", "Amount"], rows: [["Stationery", "1200"], ["Travel", "800"], ["Total", { formula: "SUM(B2:B3)" }]] }],
  slides: [{ title: "Overview", bullets: ["Purpose", "Results", "Next steps"], notes: "Presenter notes." }],
  references: []
};

test("office file endpoint module loads", () => {
  assert.equal(typeof createFileHandler, "function");
});

test("artifact schema rejects unsupported formats and empty content", () => {
  assert.throws(() => validateArtifact(sample, "exe"), /Choose PDF/);
  assert.throws(() => validateArtifact({ title: "Empty" }, "pdf"), /no usable content/);
  assert.throws(() => validateArtifact({ title: "Unsafe", sheets: [{ name: "S", columns: ["A"], rows: [[{ formula: "WEBSERVICE(A1)" }]] }] }, "xlsx"), /unsafe formula/);
});

test("XLSX formulas are limited to local references, arithmetic and safe functions", () => {
  const safe = validateArtifact({
    title: "Safe formulas",
    sheets: [{ name: "Budget", columns: ["Value"], rows: [
      [{ formula: "=SUM(B2:B3)+ROUND(2.5,0)" }],
      [{ formula: "A1*2-1" }]
    ] }]
  }, "xlsx");
  assert.deepEqual(safe.sheets[0].rows[0][0], { formula: "SUM(B2:B3)+ROUND(2.5,0)" });
  assert.deepEqual(safe.sheets[0].rows[1][0], { formula: "A1*2-1" });

  for (const formula of [
    "HYPERLINK(A1)", "IMAGE(A1)", "WEBSERVICE(A1)", "INDIRECT(A1)",
    "SUM([Other.xlsx]Sheet1!A1:A2)", "MyNamedRange", "SUM(A1;B1)",
    "SUM(A1)+HYPERLINK(B1)", "A1048577", "XFE1", "SUM(A1:B1048577)",
    "1+2+3+".repeat(20)
  ]) {
    assert.throws(() => validateArtifact({
      title: "Unsafe formula",
      sheets: [{ name: "S", columns: ["Value"], rows: [[{ formula }]] }]
    }, "xlsx"), /unsafe formula/, formula);
  }
});

for (const format of ["pdf", "docx", "xlsx", "pptx", "csv", "txt", "md"]) {
  test(format.toUpperCase() + " export returns a non-empty file with a safe filename", async () => {
    const file = await createArtifactFile(sample, format);
    assert.ok(file.buffer.length > 10);
    assert.match(file.filename, /^[A-Za-z0-9-]+\./);
    assert.ok(file.mimeType);
    if (format === "pdf") assert.equal(file.buffer.subarray(0, 5).toString(), "%PDF-");
    if (format === "xlsx" || format === "docx" || format === "pptx") {
      assert.equal(file.buffer.subarray(0, 2).toString(), "PK");
    }
    if (format === "csv") assert.match(file.buffer.toString("utf8"), /Category|Item/);
  });
}

test("file creation authenticates read-only and returns a real workbook without writing shared memory", async () => {
  const savedFetch = globalThis.fetch;
  const keys = ["UPSTASH_REDIS_REST_URL", "UPSTASH_REDIS_REST_TOKEN", "GEMINI_API_KEY", "JARVIS_FILE_SAVE_HISTORY"];
  const prior = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  const redisCommands = [];
  process.env.UPSTASH_REDIS_REST_URL = "https://redis.test";
  process.env.UPSTASH_REDIS_REST_TOKEN = "test-token";
  process.env.GEMINI_API_KEY = "test-gemini-key";
  process.env.JARVIS_FILE_SAVE_HISTORY = "false";

  globalThis.fetch = async (url, options = {}) => {
    if (String(url).includes("redis.test")) {
      const command = JSON.parse(options.body)[0];
      redisCommands.push(command);
      if (command[0] !== "GET") throw new Error("Staging file creation must not write to Redis.");
      return new Response(JSON.stringify([{ result: JSON.stringify({ deviceId: "test-device", role: "owner" }) }]), { status: 200 });
    }
    if (String(url).includes("generativelanguage.googleapis.com")) {
      const spec = {
        title: "Integration Test Budget",
        sections: [],
        tables: [],
        sheets: [{ name: "Budget", columns: ["Category", "Amount"], rows: [["Travel", 500], ["Food", 300], ["Total", { formula: "SUM(B2:B3)" }]] }],
        slides: [],
        references: []
      };
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(spec) }] } }] }), { status: 200 });
    }
    throw new Error("Unexpected network request: " + url);
  };

  try {
    const headers = {};
    let statusCode = 200;
    let payload;
    const res = {
      setHeader(key, value) { headers[key] = value; return this; },
      status(code) { statusCode = code; return this; },
      json(value) { payload = value; return this; },
      end() { return this; }
    };
    await createFileHandler({
      method: "POST",
      headers: { origin: "https://hrishikeshks1611-stack.github.io", authorization: "Bearer test-session-token" },
      body: { prompt: "Create a budget workbook", format: "xlsx" }
    }, res);

    assert.equal(statusCode, 200);
    assert.equal(payload.filename, "Integration-Test-Budget.xlsx");
    assert.equal(Buffer.from(payload.data, "base64").subarray(0, 2).toString(), "PK");
    assert.equal(redisCommands.length, 1);
    assert.equal(redisCommands[0][0], "GET");
    assert.equal(headers["Access-Control-Allow-Origin"], "https://hrishikeshks1611-stack.github.io");
  } finally {
    globalThis.fetch = savedFetch;
    for (const key of keys) {
      if (prior[key] === undefined) delete process.env[key];
      else process.env[key] = prior[key];
    }
  }
});



test("CSV export neutralises spreadsheet formula injection while preserving negative numbers", async () => {
  const input = {
    title: "Safe CSV",
    sections: [],
    tables: [],
    sheets: [{
      name: "Safe",
      columns: ["Value"],
      rows: [
        ["=HYPERLINK(A1)"],
        [" +SUM(A1:A2)"],
        ["-1+2"],
        ["@SUM(A1)"],
        ["-42"],
        ["ordinary text"]
      ]
    }],
    slides: [],
    references: []
  };
  const file = await createArtifactFile(input, "csv");
  const csv = file.buffer.toString("utf8");
  assert.ok(csv.includes("'=HYPERLINK"));
  assert.ok(csv.includes("' +SUM"));
  assert.ok(csv.includes("'-1+2"));
  assert.ok(csv.includes("'@SUM"));
  assert.ok(!csv.includes("'-42"));
  assert.ok(csv.includes("-42"));
  assert.ok(csv.includes("ordinary text"));
});
