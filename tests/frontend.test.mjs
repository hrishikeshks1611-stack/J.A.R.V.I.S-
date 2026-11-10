import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("JARVIS web app inline script parses", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.lastIndexOf("<script>");
  const end = html.lastIndexOf("</script>");
  const scripts = start >= 0 && end > start ? [html.slice(start + 8, end)] : [];
  assert.ok(scripts.length > 0, "expected an inline application script");
  assert.doesNotThrow(() => new Function(scripts[0]));
});

test("saved-memory UI is explicit and has deletion controls", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes('id="memoryButton"'));
  assert.ok(html.includes("Only notes you explicitly save here"));
  assert.ok(html.includes("Do not store passwords, API keys"));
  assert.ok(html.includes("action:'delete'"));
  assert.ok(html.includes("action:'clear',confirm:true"));
  assert.ok(html.includes('id="exportMemories"'));
  assert.ok(html.includes("editingMemoryId?'update':'add'"));
  assert.ok(html.includes("offlineReply(message,$('mode').value)"));
  assert.ok(html.includes("status('Offline mode')"));
});

test("service worker cache version is bumped and script parses", async () => {
  const source = await readFile(new URL("../sw.js", import.meta.url), "utf8");
  assert.ok(source.includes('const CACHE="jarvis-shell-v18"'));
  assert.doesNotThrow(() => new Function(source));
});

test("native app launch results are sent to shared conversation history", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("pairApi({action:'append-local',message,reply})"));
});

test("offline fallback replies are appended to shared conversation history", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("if(sessionToken)pairApi({action:'append-local',message,reply:fallback}).catch(()=>{})"));
  assert.ok(html.includes("history=history.slice(-20)"));
});

test("expired pairing is handled separately from offline AI fallback", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("if(err.status===401)"));
  assert.ok(html.includes("localStorage.removeItem('jarvisSessionToken')"));
});

test("alarm and reminder commands require clear times and call the native confirmation UI", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.indexOf("function handleCalendarOrAlarmCommand");
  const end = html.indexOf("const scheduledReply=", start);
  assert.ok(start >= 0 && end > start);
  const source = html.slice(start, end);
  const parse = new Function("window", source + "; return handleCalendarOrAlarmCommand;")({
    AndroidJarvis: {
      setAlarm: (...args) => ({ type: "alarm", args }),
      createCalendarEvent: (...args) => ({ type: "event", args })
    }
  });
  assert.deepEqual(parse("Remind me to take medicine at 7:30 pm"), {
    type: "alarm", args: [19, 30, "Reminder: take medicine"]
  });
  assert.deepEqual(parse("Set alarm for 19:45 called Workout"), {
    type: "alarm", args: [19, 45, "Workout"]
  });
  assert.equal(parse("Set alarm for 7:30"), null, "ambiguous 12-hour time should not set an alarm");
  assert.match(parse("Set alarm for 25:70"), /invalid/i);
});

test("calendar event commands validate a future ISO date and open the native event editor", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.indexOf("function handleCalendarOrAlarmCommand");
  const end = html.indexOf("const scheduledReply=", start);
  const parse = new Function("window", html.slice(start, end) + "; return handleCalendarOrAlarmCommand;")({
    AndroidJarvis: {
      setAlarm: (...args) => ({ type: "alarm", args }),
      createCalendarEvent: (...args) => ({ type: "event", args })
    }
  });
  const result = parse("Create calendar event Dentist appointment on 2030-06-15 at 09:30");
  assert.equal(result.type, "event");
  assert.equal(result.args[0], "Dentist appointment");
  assert.equal(result.args[2] - result.args[1], 60 * 60 * 1000);
  assert.equal(result.args[3], "Created through J.A.R.V.I.S.");
  assert.match(parse("Create event Invalid date on 2030-02-31 at 09:30"), /invalid/i);
});


test("older Android bridges receive clear guidance instead of throwing for new native actions", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.indexOf("function handleCalendarOrAlarmCommand");
  const end = html.indexOf("const scheduledReply=", start);
  const parse = new Function("window", html.slice(start, end) + "; return handleCalendarOrAlarmCommand;")({
    AndroidJarvis: {}
  });
  assert.match(parse("Set alarm for 7 pm"), /does not include native Clock controls yet/i);
  assert.match(parse("Create calendar event Meeting on 2030-06-15 at 09:30"), /does not include native Calendar controls yet/i);
});

test("offline fallback recognises natural-language commands", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.indexOf("function offlineReply");
  const end = html.indexOf("// Cache exact Algieba recordings", start);
  assert.ok(start >= 0 && end > start);
  const reply = new Function("deviceLocation", html.slice(start, end) + "; return offlineReply;")(null);
  assert.match(reply("hello", "witty"), /Good day, Sir/);
  assert.match(reply("what time is it", "professional"), /time on this device/i);
  assert.match(reply("what is today's date", "professional"), /Today is/);
  assert.match(reply("where am I", "professional"), /do not have your location/i);
  assert.ok(reply("search for cats", "professional").includes("https://www.google.com/search?q=cats"));
});

test("full backup UI exports and restores shared conversation plus memories with confirmation", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("id=\"exportBackup\""));
  assert.ok(html.includes("id=\"restoreBackup\""));
  assert.ok(html.includes("JARVIS_AUX_API+'/backup'"));
  assert.ok(html.includes("backupApi({action:'export'})"));
  assert.ok(html.includes("backupApi({action:'restore',backup,confirm:true})"));
  assert.ok(html.includes("Restore this backup? It will replace the shared conversation and saved memories"));
  assert.ok(html.includes("await restoreSharedHistory();await renderMemories();"));
  assert.ok(html.includes("sessionRole!=='owner'"));
});

test("Mission Control exposes local diagnostics, a bounded briefing and explicit local-only routines", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  for (const id of ["missionButton", "missionPanel", "mcTime", "mcDevice", "mcNetwork", "mcBattery", "mcPair", "mcVoice", "runBriefing", "focusRoutine", "nightRoutine", "normalRoutine"]) {
    assert.ok(html.includes('id="' + id + '"'), "expected Mission Control control " + id);
  }
  assert.ok(html.includes("Calendar, weather and external schedule data are not connected"));
  assert.ok(html.includes("These routines change JARVIS interface and voice settings on this device only"));
  assert.ok(html.includes("function detectJarvisDevice"));
  assert.ok(html.includes("navigator.getBattery"));
});
