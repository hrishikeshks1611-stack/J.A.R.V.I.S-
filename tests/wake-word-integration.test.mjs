import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("existing JARVIS Android screen opens the private wake-word controls", async () => {
  const main = await read("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java");
  const html = await read("../index.html");
  const manifest = await read("../app/src/main/AndroidManifest.xml");

  assert.ok(main.includes('webView.addJavascriptInterface(new JarvisBridge(), "AndroidJarvis")'));
  assert.ok(main.includes("public void openWakeWordSettings()"));
  assert.ok(main.includes("new Intent(MainActivity.this, WakeWordActivity.class)"));
  assert.ok(html.includes('id="wakeWordButton"'));
  assert.ok(html.includes("window.AndroidJarvis.openWakeWordSettings()"));
  assert.ok(manifest.includes('android:name=".WakeWordActivity" android:exported="false"'));
  assert.ok(manifest.includes('android:name=".WakeWordService" android:exported="false"'));
});

test("wake-word events return to the existing JARVIS conversation", async () => {
  const main = await read("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java");
  const html = await read("../index.html");
  const service = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java");

  assert.ok(main.includes("WakeWordService.ACTION_WAKE"));
  assert.ok(main.includes("WakeWordService.ACTION_COMMAND"));
  assert.ok(main.includes("window.jarvisNative.onWakeWordDetected()"));
  assert.ok(main.includes("window.jarvisNative.onSpeechResult("));
  assert.ok(main.includes("JSONObject.quote(command)"));
  assert.ok(html.includes("onWakeWordDetected:"));
  assert.ok(html.includes("onSpeechResult:(transcript)=>"));
  assert.ok(html.includes("$('form').requestSubmit()"));
  assert.ok(service.includes("broadcast(ACTION_WAKE, null)"));
  assert.ok(service.includes("broadcast(ACTION_COMMAND, command)"));
});

test("microphone listening is opt-in, visible, stoppable, and does not auto-restart", async () => {
  const activity = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordActivity.java");
  const service = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java");
  const manifest = await read("../app/src/main/AndroidManifest.xml");

  assert.ok(activity.includes("Manifest.permission.RECORD_AUDIO"));
  assert.ok(activity.includes("startForegroundService(service)"));
  assert.ok(activity.includes("stopService(new Intent(this, WakeWordService.class))"));
  assert.ok(service.includes("START_NOT_STICKY"));
  assert.ok(service.includes('"Turn microphone OFF"'));
  assert.ok(service.includes("ACTION_STOP"));
  assert.ok(service.includes("active = false"));
  assert.ok(service.includes("connection.disconnect()"));
  assert.ok(manifest.includes("android.permission.FOREGROUND_SERVICE_MICROPHONE"));
  assert.ok(manifest.includes('android:foregroundServiceType="microphone"'));
});

test("offline speech model is the only network download in the wake-word service", async () => {
  const service = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java");
  const gradle = await read("../app/build.gradle");

  assert.ok(service.includes("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"));
  assert.ok(service.includes("new Model(modelDir.getAbsolutePath())"));
  assert.ok(service.includes("new SpeechService(recognizer, 16000.0f)"));
  assert.ok(service.includes("downloadModel(archive)"));
  assert.ok(!service.includes("HttpURLConnection audio"));
  assert.ok(gradle.includes("com.alphacephei:vosk-android"));
  assert.ok(gradle.includes("net.java.dev.jna:jna"));
});

test("Android manifest keeps the wake-word service and settings screen private to this app", async () => {
  const manifest = await read("../app/src/main/AndroidManifest.xml");
  assert.match(manifest, /<uses-permission android:name="android.permission.RECORD_AUDIO"/);
  assert.match(manifest, /<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/);
  assert.match(manifest, /<activity android:name="\.WakeWordActivity" android:exported="false"/);
  assert.match(manifest, /<service android:name="\.WakeWordService" android:exported="false" android:foregroundServiceType="microphone"/);
});

test("emulator wake-word test launcher exists only in debug and is shell-restricted", async () => {
  const debugManifest = await read("../app/src/debug/AndroidManifest.xml");
  assert.ok(debugManifest.includes('android:name=".WakeWordTestActivity"'));
  assert.ok(debugManifest.includes('android:exported="true"'));
  assert.ok(debugManifest.includes('android:permission="android.permission.DUMP"'));
});

test("wake phrase matching uses a complete token and expires the command prompt", async () => {
  const service = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java");
  assert.ok(service.includes('compile("\\\\bjarvis\\\\b")'));
  assert.ok(service.includes("commandTimeoutHandler.postDelayed(commandTimeout, 8000)"));
  assert.ok(service.includes("commandTimeoutHandler.removeCallbacks(commandTimeout)"));
});


test("Vosk partial JSON is parsed from the partial field, not the final text field", async () => {
  const service = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java");
  assert.ok(service.includes("transcript(String json, boolean partial)"));
  assert.match(service, /partial\s*\?\s*result\.optString\("partial"/);
  assert.match(service, /:\s*result\.optString\("text"/);
  assert.ok(service.includes("transcript(hypothesis, partial)"));
});

test("wake phrase is handled on partial recognition without duplicate final dispatch", async () => {
  const service = await read("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java");
  assert.ok(service.includes("handleRecognizedPhrase(hypothesis, true)"));
  assert.ok(service.includes("handleRecognizedPhrase(hypothesis, false)"));
  assert.ok(service.includes("if (partial && wakeHandledInUtterance) return;"));
  assert.ok(service.includes("if (!partial && wakeHandledInUtterance)"));
  assert.ok(service.includes("broadcast(ACTION_WAKE, null)"));
  assert.ok(service.includes("if (awaitingCommand)"));
  assert.ok(service.includes("command = text.substring(wakeInCommand.end()).trim()"));
  assert.ok(service.includes("if (partial) return;"));
  assert.ok(service.includes("broadcast(ACTION_COMMAND, command)"));
});

test("full recovery keeps the installed JARVIS package and advances its version", async () => {
  const gradle = await read("../app/build.gradle");
  const manifest = await read("../app/src/main/AndroidManifest.xml");
  const workflow = await read("../.github/workflows/android-apk.yml");

  assert.ok(gradle.includes("applicationId 'com.hrishikesh.jarvis.fresh'"));
  assert.ok(gradle.includes("versionCode 118"));
  assert.ok(gradle.includes("versionName '1.1.2'"));
  assert.ok(!gradle.includes("applicationIdSuffix '.safe'"));
  assert.ok(!gradle.includes("applicationIdSuffix '.tv.safe'"));
  assert.ok(manifest.includes('android:label="J.A.R.V.I.S."'));
  assert.doesNotMatch(manifest, /BIND_NOTIFICATION_LISTENER_SERVICE|NotificationListenerService/);
  assert.ok(workflow.includes("pm path com.hrishikesh.jarvis.fresh"));
  assert.ok(workflow.includes("Installing the full-recovery JARVIS APK"));
});
