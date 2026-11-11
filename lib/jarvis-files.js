import PDFDocument from "pdfkit";
import { Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType, BorderStyle } from "docx";
import ExcelJS from "exceljs";
import pptxgen from "pptxgenjs";

const FORMATS = {
  pdf: { mimeType: "application/pdf", extension: "pdf" },
  docx: { mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", extension: "docx" },
  xlsx: { mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", extension: "xlsx" },
  pptx: { mimeType: "application/vnd.openxmlformats-officedocument.presentationml.presentation", extension: "pptx" },
  csv: { mimeType: "text/csv; charset=utf-8", extension: "csv" },
  txt: { mimeType: "text/plain; charset=utf-8", extension: "txt" },
  md: { mimeType: "text/markdown; charset=utf-8", extension: "md" }
};

const cleanText = (value, max = 12000) => String(value ?? "").replace(/\u0000/g, "").slice(0, max);
const safeName = value => cleanText(value, 90).normalize("NFKD").replace(/[^\w -]/g, "").trim().replace(/\s+/g, "-").slice(0, 60) || "JARVIS-Document";
const SAFE_XLSX_FUNCTIONS = new Set([
  "SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA", "PRODUCT",
  "ROUND", "ROUNDUP", "ROUNDDOWN", "ABS", "INT", "MOD", "POWER"
]);

function isLocalCellReference(token) {
  const match = /^\$?([A-Za-z]{1,3})\$?([1-9]\d{0,6})$/.exec(token || "");
  if (!match) return false;
  let column = 0;
  for (const character of match[1].toUpperCase()) {
    column = column * 26 + character.charCodeAt(0) - 64;
  }
  return column <= 16384 && Number(match[2]) <= 1048576;
}

// Parse only local A1 references, arithmetic, ranges, and a short list of
// deterministic worksheet functions. This rejects external workbook links,
// named ranges, text literals, dynamic/network functions, and formula payloads.
function validateXlsxFormula(value) {
  const raw = String(value ?? "").trim();
  if (!raw || raw.length > 120) throw new Error("The workbook contains an unsupported or unsafe formula.");
  const formula = raw.replace(/^=/, "").trim();
  const compact = formula.replace(/\s+/g, "");
  const tokens = compact.match(/\$?[A-Za-z]{1,3}\$?[1-9]\d{0,6}|\d+(?:\.\d+)?|[A-Za-z_][A-Za-z0-9_]*|[()+\-*/,:]/g) || [];
  if (!compact || tokens.join("") !== compact) {
    throw new Error("The workbook contains an unsupported or unsafe formula.");
  }

  let index = 0;
  const peek = () => tokens[index];
  const take = () => tokens[index++];

  function primary() {
    const token = take();
    if (token === "(") {
      expression();
      if (take() !== ")") throw new Error("The workbook contains an unsupported or unsafe formula.");
      return;
    }
    if (isLocalCellReference(token)) {
      if (peek() === ":") {
        take();
        if (!isLocalCellReference(take())) throw new Error("The workbook contains an unsupported or unsafe formula.");
      }
      return;
    }
    if (/^\d+(?:\.\d+)?$/.test(token || "")) return;
    if (SAFE_XLSX_FUNCTIONS.has(String(token || "").toUpperCase()) && peek() === "(") {
      take();
      if (peek() !== ")") {
        expression();
        while (peek() === ",") {
          take();
          expression();
        }
      }
      if (take() !== ")") throw new Error("The workbook contains an unsupported or unsafe formula.");
      return;
    }
    throw new Error("The workbook contains an unsupported or unsafe formula.");
  }

  function unary() {
    if (peek() === "+" || peek() === "-") take();
    primary();
  }
  function product() {
    unary();
    while (peek() === "*" || peek() === "/") {
      take();
      unary();
    }
  }
  function expression() {
    product();
    while (peek() === "+" || peek() === "-") {
      take();
      product();
    }
  }

  expression();
  if (index !== tokens.length) throw new Error("The workbook contains an unsupported or unsafe formula.");
  return formula;
}

function cleanCell(value) {
  if (value && typeof value === "object" && !Array.isArray(value) && typeof value.formula === "string") {
    return { formula: validateXlsxFormula(value.formula) };
  }
  return cleanText(value, 2000);
}

export function validateArtifact(input, format) {
  if (!FORMATS[format]) throw new Error("Choose PDF, DOCX, XLSX, PPTX, CSV, TXT or Markdown.");
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("The document content was not valid.");
  const title = cleanText(input.title || "JARVIS Document", 180).trim() || "JARVIS Document";
  const subtitle = cleanText(input.subtitle || "", 300);
  const sections = Array.isArray(input.sections) ? input.sections.slice(0, 40).map(section => ({
    heading: cleanText(section?.heading || "", 180),
    paragraphs: Array.isArray(section?.paragraphs) ? section.paragraphs.slice(0, 20).map(x => cleanText(x, 6000)) : [],
    bullets: Array.isArray(section?.bullets) ? section.bullets.slice(0, 30).map(x => cleanText(x, 1000)) : []
  })) : [];
  const tables = Array.isArray(input.tables) ? input.tables.slice(0, 12).map(table => ({
    title: cleanText(table?.title || "", 180),
    columns: Array.isArray(table?.columns) ? table.columns.slice(0, 20).map(x => cleanText(x, 100)) : [],
    rows: Array.isArray(table?.rows) ? table.rows.slice(0, 250).map(row => Array.isArray(row) ? row.slice(0, 20).map(x => cleanText(x, 1000)) : []) : []
  })) : [];
  const sheets = Array.isArray(input.sheets) ? input.sheets.slice(0, 8).map(sheet => ({
    name: cleanText(sheet?.name || "Sheet", 31).replace(/[\\/*?:[\]]/g, "").trim() || "Sheet",
    columns: Array.isArray(sheet?.columns) ? sheet.columns.slice(0, 30).map(x => cleanText(x, 100)) : [],
    rows: Array.isArray(sheet?.rows) ? sheet.rows.slice(0, 1000).map(row => Array.isArray(row) ? row.slice(0, 30).map(cleanCell) : []) : []
  })) : [];
  const slides = Array.isArray(input.slides) ? input.slides.slice(0, 60).map(slide => ({
    title: cleanText(slide?.title || "", 180),
    bullets: Array.isArray(slide?.bullets) ? slide.bullets.slice(0, 10).map(x => cleanText(x, 500)) : [],
    notes: cleanText(slide?.notes || "", 1500)
  })) : [];
  const references = Array.isArray(input.references) ? input.references.slice(0, 30).map(x => cleanText(x, 1000)) : [];
  const total = JSON.stringify({ title, subtitle, sections, tables, sheets, slides, references }).length;
  if (total > 500000) throw new Error("This document is too large to export in one file.");
  if (!sections.length && !tables.length && !sheets.length && !slides.length) throw new Error("The document contains no usable content.");
  return { title, subtitle, sections, tables, sheets, slides, references };
}

function csvEscape(value) {
  const raw = value && typeof value === "object" && typeof value.formula === "string" ? "=" + value.formula : String(value ?? "");
  // CSV has no trusted formula metadata. Prefix formula-like values so spreadsheet
  // applications display untrusted content as text instead of evaluating it.
  const negativeNumber = /^\s*-\d+(?:\.\d+)?$/.test(raw);
  const formulaLike = /^[\s\u0000-\u001f]*[=+@]/.test(raw) ||
    (/^[\s\u0000-\u001f]*-/.test(raw) && !negativeNumber);
  const text = formulaLike ? "'" + raw : raw;
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}
function asCsv(artifact) {
  const sheet = artifact.sheets[0];
  const table = artifact.tables[0];
  const columns = sheet?.columns?.length ? sheet.columns : table?.columns || [];
  const rows = sheet?.rows?.length ? sheet.rows : table?.rows || [];
  if (!columns.length) throw new Error("A CSV export needs a table with column headings.");
  return "\uFEFF" + [columns, ...rows].map(row => row.map(csvEscape).join(",")).join("\r\n");
}
function asText(artifact, markdown = false) {
  const lines = [markdown ? "# " + artifact.title : artifact.title, artifact.subtitle, ""].filter(x => x !== undefined);
  for (const section of artifact.sections) {
    if (section.heading) lines.push(markdown ? "## " + section.heading : section.heading, "");
    for (const paragraph of section.paragraphs) lines.push(paragraph, "");
    for (const bullet of section.bullets) lines.push(markdown ? "- " + bullet : "• " + bullet);
    lines.push("");
  }
  for (const table of artifact.tables) {
    if (table.title) lines.push(markdown ? "## " + table.title : table.title);
    if (table.columns.length) {
      if (markdown) {
        lines.push("| " + table.columns.join(" | ") + " |", "| " + table.columns.map(() => "---").join(" | ") + " |");
        for (const row of table.rows) lines.push("| " + table.columns.map((_, i) => row[i] || "").join(" | ") + " |");
      } else {
        lines.push(table.columns.join("\t"), ...table.rows.map(row => row.join("\t")));
      }
    }
    lines.push("");
  }
  if (artifact.references.length) lines.push(markdown ? "## References" : "References", ...artifact.references.map((x, i) => (i + 1) + ". " + x));
  return lines.join("\n").trim() + "\n";
}
function renderPdf(artifact) {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: "A4", margin: 54, info: { Title: artifact.title, Creator: "J.A.R.V.I.S." } });
    const chunks = [];
    doc.on("data", chunk => chunks.push(chunk));
    doc.on("error", reject);
    doc.on("end", () => resolve(Buffer.concat(chunks)));
    doc.font("Helvetica-Bold").fontSize(22).fillColor("#102c45").text(artifact.title, { align: "left" });
    if (artifact.subtitle) doc.moveDown(0.4).font("Helvetica").fontSize(11).fillColor("#526477").text(artifact.subtitle);
    doc.moveDown(1);
    for (const section of artifact.sections) {
      if (section.heading) doc.moveDown(0.6).font("Helvetica-Bold").fontSize(15).fillColor("#123b5a").text(section.heading);
      for (const paragraph of section.paragraphs) doc.moveDown(0.35).font("Helvetica").fontSize(10.5).fillColor("#222222").text(paragraph, { align: "justify", lineGap: 2 });
      for (const bullet of section.bullets) doc.font("Helvetica").fontSize(10.5).fillColor("#222222").text("•  " + bullet, { indent: 14, lineGap: 2 });
    }
    for (const table of artifact.tables) {
      if (table.title) doc.moveDown(0.6).font("Helvetica-Bold").fontSize(14).fillColor("#123b5a").text(table.title);
      if (table.columns.length) {
        const widths = table.columns.map(() => (doc.page.width - 108) / table.columns.length);
        const drawRow = (row, header = false) => {
          const cellHeight = Math.max(24, ...row.map(x => doc.heightOfString(String(x || ""), { width: Math.max(40, widths[0] - 8), fontSize: 8 }) + 8));
          if (doc.y + cellHeight > doc.page.height - 54) doc.addPage();
          const y = doc.y, x0 = 54;
          row.forEach((cell, i) => {
            const x = x0 + widths.slice(0, i).reduce((a, b) => a + b, 0);
            doc.rect(x, y, widths[i], cellHeight).fillAndStroke(header ? "#e8f2f8" : "#ffffff", "#c4d2dc");
            doc.font(header ? "Helvetica-Bold" : "Helvetica").fontSize(8).fillColor("#1b2c3a").text(String(cell || ""), x + 4, y + 5, { width: widths[i] - 8 });
          });
          doc.y = y + cellHeight;
        };
        drawRow(table.columns, true);
        table.rows.forEach(row => drawRow(table.columns.map((_, i) => row[i] || "")));
        doc.moveDown(0.5);
      }
    }
    if (artifact.references.length) {
      doc.addPage().font("Helvetica-Bold").fontSize(15).fillColor("#123b5a").text("References");
      artifact.references.forEach((ref, i) => doc.moveDown(0.3).font("Helvetica").fontSize(9).fillColor("#222222").text((i + 1) + ". " + ref));
    }
    doc.end();
  });
}
async function renderDocx(artifact) {
  const children = [new Paragraph({ text: artifact.title, heading: HeadingLevel.TITLE })];
  if (artifact.subtitle) children.push(new Paragraph({ text: artifact.subtitle, style: "Subtitle" }));
  for (const section of artifact.sections) {
    if (section.heading) children.push(new Paragraph({ text: section.heading, heading: HeadingLevel.HEADING_1 }));
    for (const paragraph of section.paragraphs) children.push(new Paragraph({ children: [new TextRun(paragraph)] }));
    for (const bullet of section.bullets) children.push(new Paragraph({ text: bullet, bullet: { indent: 360 } }));
  }
  for (const table of artifact.tables) {
    if (table.title) children.push(new Paragraph({ text: table.title, heading: HeadingLevel.HEADING_2 }));
    if (table.columns.length) {
      const rows = [table.columns, ...table.rows].map((row, ri) => new TableRow({
        children: table.columns.map((_, i) => new TableCell({
          width: { size: 100 / table.columns.length, type: WidthType.PERCENTAGE },
          shading: ri === 0 ? { fill: "E8F2F8" } : undefined,
          children: [new Paragraph({ children: [new TextRun({ text: String(row[i] || ""), bold: ri === 0 })] })]
        }))
      }));
      children.push(new Table({ rows, width: { size: 100, type: WidthType.PERCENTAGE }, borders: { insideH: { style: BorderStyle.SINGLE, size: 3, color: "C4D2DC" }, insideV: { style: BorderStyle.SINGLE, size: 3, color: "C4D2DC" }, top: { style: BorderStyle.SINGLE, size: 3, color: "C4D2DC" }, bottom: { style: BorderStyle.SINGLE, size: 3, color: "C4D2DC" }, left: { style: BorderStyle.SINGLE, size: 3, color: "C4D2DC" }, right: { style: BorderStyle.SINGLE, size: 3, color: "C4D2DC" } } }));
    }
  }
  if (artifact.references.length) {
    children.push(new Paragraph({ text: "References", heading: HeadingLevel.HEADING_1 }));
    artifact.references.forEach((ref, i) => children.push(new Paragraph({ text: (i + 1) + ". " + ref })));
  }
  return Packer.toBuffer(new Document({ sections: [{ properties: {}, children }] }));
}
async function renderXlsx(artifact) {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = "J.A.R.V.I.S.";
  workbook.created = new Date();
  const sheets = artifact.sheets.length ? artifact.sheets : artifact.tables.map((table, i) => ({ name: table.title || "Sheet " + (i + 1), columns: table.columns, rows: table.rows }));
  if (!sheets.length) throw new Error("An Excel workbook needs at least one table or sheet.");
  for (const source of sheets) {
    const sheet = workbook.addWorksheet(source.name.slice(0, 31));
    sheet.addRow(source.columns);
    sheet.getRow(1).font = { bold: true, color: { argb: "FFFFFFFF" } };
    sheet.getRow(1).fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FF123B5A" } };
    sheet.views = [{ state: "frozen", ySplit: 1 }];
    for (const row of source.rows) sheet.addRow(row);
    sheet.columns.forEach((column, i) => {
      const header = String(source.columns[i] || "");
      const max = Math.max(header.length, ...source.rows.slice(0, 100).map(row => String(row[i] || "").length));
      column.width = Math.min(42, Math.max(12, max + 2));
    });
    sheet.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: Math.max(1, source.columns.length) } };
  }
  return Buffer.from(await workbook.xlsx.writeBuffer());
}
async function renderPptx(artifact) {
  const pptx = new pptxgen();
  pptx.layout = "LAYOUT_WIDE";
  pptx.author = "J.A.R.V.I.S.";
  pptx.subject = artifact.subtitle || artifact.title;
  pptx.title = artifact.title;
  pptx.theme = { headFontFace: "Aptos Display", bodyFontFace: "Aptos", lang: "en-US" };
  const slides = artifact.slides.length ? artifact.slides : [
    { title: artifact.title, bullets: [artifact.subtitle, ...artifact.sections.flatMap(section => [section.heading, ...section.bullets, ...section.paragraphs.slice(0, 1)]).filter(Boolean).slice(0, 6)] }
  ];
  for (const item of slides) {
    const slide = pptx.addSlide();
    slide.background = { color: "071426" };
    slide.addText(item.title || artifact.title, { x: 0.6, y: 0.45, w: 12, h: 0.7, fontFace: "Aptos Display", fontSize: 27, bold: true, color: "78DFFF", margin: 0.04 });
    if (item.bullets.length) slide.addText(item.bullets.map(text => ({ text, options: { bullet: { indent: 18 }, hanging: 4, breakLine: true } })), { x: 0.85, y: 1.55, w: 11.4, h: 5.1, fontFace: "Aptos", fontSize: 19, color: "EAF7FF", paraSpaceAfterPt: 15, valign: "mid", margin: 0.05, breakLine: false });
    if (item.notes) slide.addNotes(item.notes);
    slide.addText("J.A.R.V.I.S.", { x: 11.15, y: 7.05, w: 1.5, h: 0.2, fontSize: 8, color: "6B8DA8", align: "right" });
  }
  return Buffer.from(await pptx.write({ outputType: "nodebuffer" }));
}

export async function createArtifactFile(input, format) {
  const artifact = validateArtifact(input, format);
  let buffer;
  if (format === "pdf") buffer = await renderPdf(artifact);
  else if (format === "docx") buffer = await renderDocx(artifact);
  else if (format === "xlsx") buffer = await renderXlsx(artifact);
  else if (format === "pptx") buffer = await renderPptx(artifact);
  else if (format === "csv") buffer = Buffer.from(asCsv(artifact), "utf8");
  else if (format === "md") buffer = Buffer.from(asText(artifact, true), "utf8");
  else buffer = Buffer.from(asText(artifact, false), "utf8");
  if (buffer.length > 3 * 1024 * 1024) throw new Error("The generated file exceeds the 3 MB attachment limit for reliable delivery across devices.");
  return {
    filename: safeName(artifact.title) + "." + FORMATS[format].extension,
    mimeType: FORMATS[format].mimeType,
    buffer,
    title: artifact.title
  };
}
