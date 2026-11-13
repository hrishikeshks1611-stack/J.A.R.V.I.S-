import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
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

test("device sync identifies this device and exposes the authenticated owner transfer flow", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes('id="deviceIdentity"'));
  assert.ok(html.includes("pairApi({action:'verify'})"));
  assert.ok(html.includes("CURRENT OWNER"));
  assert.ok(html.includes("THIS PHONE"));
  assert.ok(html.includes("FULL DEVICE ID '+device.deviceId"));
  assert.ok(html.includes("FULL ID '+device.deviceId"));
  assert.ok(html.includes("Re-open Devices & Sync on the target device"));
  assert.ok(html.includes("pairApi({action:'transfer-owner',deviceId:device.deviceId})"));
  assert.ok(html.includes("Shared conversations and saved memories will be preserved"));
});

test("service worker cache version is bumped and script parses", async () => {
  const source = await readFile(new URL("../sw.js", import.meta.url), "utf8");
  assert.ok(source.includes('const CACHE="jarvis-shell-v30"'));
  assert.ok(source.includes('fetch(request, {cache:"no-store"})'));
  assert.doesNotThrow(() => new Function(source));
});


test("hosted interface provides a foreground fallback for all four JARVIS wake phrases", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("const BrowserSpeechRecognition=window.SpeechRecognition||window.webkitSpeechRecognition"));
  assert.ok(html.includes("hey\\s+jarvis|hello\\s+jarvis|wake\\s+up\\s+jarvis|jarvis\\s*,?\\s*are\\s+you\\s+there"));
  assert.ok(html.includes("recognition.continuous=true"));
  assert.ok(html.includes("function captureBrowserWakeCommand()"));
  assert.ok(html.includes("document.addEventListener('visibilitychange'"));
});


test("Build 39 does not request unavailable browser microphone permission for wake phrases", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("Build 39 does not include the Android microphone permission and native wake-word service"));
  assert.ok(html.includes("continuous wake phrases need a compatible app update"));
});

test("four voice camera phrases use native camera, capture, selfie and latest-photo actions", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("window.AndroidJarvis.saveFile(downloadFile.filename,downloadFile.mimeType||'application/octet-stream',downloadFile.data)"));
  assert.ok(html.includes("link.textContent='Saved to Downloads'"));
  assert.ok(html.includes("const cameraCommandText="));
  assert.ok(html.includes("cameraAction==='open'"));
  assert.ok(html.includes("cameraAction==='photo'"));
  assert.ok(html.includes("cameraAction==='selfie'"));
  assert.ok(html.includes("cameraAction==='show'"));
  assert.ok(html.includes("window.AndroidJarvis.capturePhoto(true)"));
  assert.ok(html.includes("window.AndroidJarvis.capturePhoto(false)"));
  assert.ok(html.includes("window.AndroidJarvis.showLastPhoto()"));
  assert.ok(html.includes('id="cameraInput"'));
});

test("YouTube Music voice commands use the native playback bridge or search fallback", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("const openYoutubeMusicOnly="));
  assert.ok(html.includes("AndroidJarvis.playYoutubeMusic(songQuery)"));
  assert.ok(html.includes("window.AndroidJarvis.openApp('youtube music')"));
  assert.ok(html.includes("const hasNativeMusicBridge=window.AndroidJarvis&&typeof window.AndroidJarvis.playYoutubeMusic==='function'"));
  assert.ok(html.includes("else window.location.href='https://music.youtube.com/'"));
  assert.ok(html.includes("music.youtube.com/search?q="));
  assert.ok(html.includes("JARVIS_API+'/music-search'"));
  assert.ok(html.includes("music.youtube.com/watch?v='+encodeURIComponent(track.videoId)"));
  assert.ok(html.includes("Finding your music, Sir…"));
  assert.ok(html.includes("const acknowledgement='Certainly, Sir.'"));
  assert.ok(html.includes("await acknowledgementFinished"));
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
  assert.ok(html.includes("backupApi({action:'restore',backup:cloudBackup,confirm:true})"));
  assert.ok(html.includes("Restore this backup? It will replace the shared conversation and saved memories"));
  assert.ok(html.includes("await restoreSharedHistory();await renderMemories();"));
  assert.ok(html.includes("sessionRole!=='owner'"));
});

test("device migration backups validate and restore bounded custom routines", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("format:'jarvis-device-migration'"));
  assert.ok(html.includes("device:{customRoutines:readCustomRoutines()}"));
  assert.ok(html.includes("backup:cloudBackup,confirm:true"));
  assert.ok(html.includes("localStorage.setItem(CUSTOM_ROUTINE_KEY,JSON.stringify(localRoutines))"));
  const start = html.indexOf("function normalizeMigrationRoutines(");
  const end = html.indexOf("\n$('exportBackup')", start);
  assert.ok(start >= 0 && end > start);
  const normalize = new Function(html.slice(start, end) + "; return normalizeMigrationRoutines;")();
  assert.deepEqual(normalize([{id:"r1",name:"Focus",mode:"focus"}]), [{id:"r1",name:"Focus",mode:"focus"}]);
  assert.deepEqual(normalize([]), []);
  assert.throws(() => normalize(Array.from({length:9},(_,i)=>({name:"Routine "+i,mode:"focus"}))), /invalid number/i);
  assert.throws(() => normalize([{name:"",mode:"focus"}]), /invalid custom routine/i);
  assert.throws(() => normalize([{name:"Sleep",mode:"unsafe"}]), /invalid custom routine/i);
});

test("animated 3D reactor remains the default and respects reduced-motion preferences", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("@keyframes reactorBreath"));
  assert.ok(html.includes("@keyframes corePulse"));
  assert.ok(html.includes(".ring{"));
  assert.ok(html.includes("@media (prefers-reduced-motion: reduce)"));
  assert.ok(html.includes(".reactor-core{animation:none!important}"));
});

test("Cast control is honestly labelled as an Android settings shortcut, not in-app mirroring", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes('id="castButton" type="button" aria-label="Open Android Cast settings"'));
  assert.ok(html.includes("JARVIS does not mirror the screen itself"));
  assert.ok(html.includes("JARVIS does not stream the screen itself"));
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
  assert.ok(html.includes('id="mcBackend"'));
  assert.ok(html.includes('id="mcDevices"'));
  assert.ok(html.includes("pairApi({action:'verify'})"));
  assert.ok(html.includes("pairApi({action:'devices'})"));
  assert.ok(html.includes("missionBackendStatus='Reachable · session verified'"));
  assert.ok(html.includes("const CUSTOM_ROUTINE_KEY='jarvisCustomRoutinesV1'"));
  assert.ok(html.includes('id="saveCustomRoutine"'));
  assert.ok(html.includes("up to eight named routines"));
  assert.ok(html.includes("function applyLocalRoutine"));
});

test("owner-only Security Audit screen exposes activity filters", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes('id="openSecurityAudit"'));
  assert.ok(html.includes('id="securityPanel"'));
  assert.ok(html.includes("pairApi({action:'security-audit'})"));
  assert.ok(html.includes("FIRST SEEN IN RECENT LOG"));
  assert.ok(html.includes("auditFailedOnly"));
});


test("permissioned tool calls are shown and alarm/calendar actions require a separate user click", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("function add(text,who,attachment,downloadFile,toolCalls,pendingActions)"));
  assert.ok(html.includes("TOOL REPORT"));
  assert.ok(html.includes("Review alarm in Clock"));
  assert.ok(html.includes("Calendar editor opened · save there to confirm"));
  assert.ok(html.includes("data.toolCalls,data.pendingActions"));
  assert.ok(html.includes("deviceType,deviceName:deviceType,networkOnline:navigator.onLine!==false"));
});


test("shared conversation clearing requires explicit confirmation across paired devices", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("Clear the shared JARVIS conversation on all paired devices? This cannot be undone."));
  assert.ok(html.includes("pairApi({action:'clear',confirm:true})"));
});

test("Office Engine describes inline file delivery without claiming an expiring secure link", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(html.includes("The file is delivered directly to this device and is not retained in durable server history"));
  assert.ok(!html.includes("The secure link works once and expires in 15 minutes"));
});


test("live app answers exact greetings locally and routes YouTube Music commands", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.indexOf("function instantReply");
  const end = html.indexOf("$('form').onsubmit", start);
  assert.ok(start >= 0 && end > start);
  const instantReply = vm.runInNewContext(html.slice(start, end) + "\ninstantReply", { Date, Intl });
  assert.match(instantReply("Good morning"), /Good morning, Sir/);
  assert.equal(instantReply("Good morning, explain this to me"), null);
  assert.ok(html.includes("AndroidJarvis.playYoutubeMusic(songQuery)"));
  assert.ok(html.includes("music.youtube.com/search?q="));
});

test("Algieba voice starts after a short pre-buffer while the audio stream is still arriving", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const start = html.indexOf("async function playPcmStream");
  const end = html.indexOf("let speechQuotaCooldownUntil", start);
  assert.ok(start >= 0 && end > start);
  const player = html.slice(start, end);
  assert.ok(player.includes("const START_BUFFER_BYTES=12000"));
  assert.ok(player.includes("const deliverChunk=(chunk)=>"));
  assert.ok(player.includes("if(initialBytes<START_BUFFER_BYTES)return"));
  assert.ok(player.includes("initialChunks.length=0"));
  assert.match(player, /parts\.push\(chunk\.slice\(\)\);deliverChunk\(chunk\)/);
  assert.ok(player.includes("Playback begins after a short pre-buffer"));
  assert.ok(!player.includes("Buffer the complete PCM response before playback"));
});
