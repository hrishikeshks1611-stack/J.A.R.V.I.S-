import { readFile } from "node:fs/promises";
import vm from "node:vm";

const files = ["index.html", "app/src/main/assets/index.html"];
for (const file of files) {
  const html = await readFile(file, "utf8");
  if (!/body:\s*JSON\.stringify\(\{[\s\S]*?stream:true/.test(html)) {
    throw new Error(file + " chat requests must opt in to live SSE streaming.");
  }
  if (!html.includes("event==='progress'") || !html.includes("event==='delta'")) {
    throw new Error(file + " must handle streamed progress and text-delta events.");
  }
  const blocks = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)]
    .map(match => match[1])
    .filter(source => source.trim().length > 0);
  if (blocks.length === 0) throw new Error(file + " contains no inline JavaScript.");
  blocks.forEach((source, index) => {
    new vm.Script(source, { filename: file + "#script-" + (index + 1) });
  });
  console.log("JavaScript syntax OK:", file, "(" + blocks.length + " inline script block(s))");
}
