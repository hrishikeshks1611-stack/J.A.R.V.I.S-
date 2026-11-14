import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("native bridge exposes confirmation-based Clock and Calendar actions", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String saveFile(String requestedName, String mimeType, String base64Data)"));
  assert.ok(source.includes("MediaStore.Downloads.RELATIVE_PATH"));
  assert.ok(source.includes("Environment.DIRECTORY_DOWNLOADS + \"/JARVIS\""));
  assert.ok(source.includes("MediaStore.Downloads.IS_PENDING"));
  assert.ok(source.includes("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
  assert.ok(source.includes("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  assert.ok(source.includes("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  assert.ok(source.includes("public String openCastSettings()"));
  assert.ok(source.includes("Settings.ACTION_CAST_SETTINGS"));
  assert.ok(source.includes("public String dialNumber(String requestedNumber)"));
  assert.ok(source.includes("new Intent(Intent.ACTION_DIAL"));
  assert.ok(source.includes("public String openSmsComposer(String requestedNumber, String requestedBody)"));
  assert.ok(source.includes("new Intent(Intent.ACTION_SENDTO"));
  assert.ok(source.includes('intent.putExtra("sms_body", body)'));
  assert.ok(source.includes("public String setAlarm(int hour, int minute, String label)"));
  assert.ok(source.includes("AlarmClock.ACTION_SET_ALARM"));
  assert.ok(source.includes("AlarmClock.EXTRA_SKIP_UI, false"));
  assert.ok(source.includes("public String createCalendarEvent(String title, long beginMillis, long endMillis, String description)"));
  assert.ok(source.includes("Intent.ACTION_INSERT"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_BEGIN_TIME"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_END_TIME"));
  assert.ok(manifest.includes('com.android.alarm.permission.SET_ALARM'));
});

test("J.A.R.V.I.S. 16.0 has its own standalone Android package identity", async () => {
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  assert.match(gradle, /applicationId\s+'com\.hrishikesh\.jarvis16'/);
  assert.match(gradle, /versionCode\s+1600/);
  assert.match(gradle, /versionName\s+'16\.0\.0'/);
});

test("APK workflow builds only the phone release candidate without publishing it as an update", async () => {
  const workflow = await readFile(new URL("../.github/workflows/android-apk.yml", import.meta.url), "utf8");
  assert.match(workflow, /contents:\s*read/);
  assert.match(workflow, /assemblePhoneRelease/);
  assert.match(workflow, /JARVIS-16\.0-phone-release-candidate/);
  assert.match(workflow, /app-phone-release\.apk/);
  assert.doesNotMatch(workflow, /assembleTv|app-tv-|gh release create|action-gh-release/);
});


test("live Android bridge opens the exact selected YouTube Music track", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String playYoutubeMusic(String videoId)"));
  assert.ok(source.includes('id.matches("[A-Za-z0-9_-]{11}")'));
  assert.ok(source.includes('Uri.parse("https://music.youtube.com/watch?v=" + Uri.encode(id))'));
  assert.ok(source.includes('playTrack.setPackage("com.google.android.apps.youtube.music")'));
  assert.ok(source.includes("Intent.ACTION_VIEW, trackUri"));
  assert.ok(!source.includes("vnd.youtube.music://"));
  assert.ok(manifest.includes('com.google.android.apps.youtube.music'));
});


test("Android camera bridge supports camera launch, automatic photo, selfie and last-photo viewing", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const camera = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/CameraCaptureActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.ok(source.includes("public void openCamera()"));
  assert.ok(source.includes("public void takePhoto()"));
  assert.ok(source.includes("public void capturePhoto(boolean selfie)"));
  assert.ok(source.includes("public void showLastPhoto()"));
  assert.ok(camera.includes("CameraSelector.DEFAULT_FRONT_CAMERA"));
  assert.ok(camera.includes("CameraSelector.DEFAULT_BACK_CAMERA"));
  assert.ok(camera.includes("imageCapture.takePicture"));
  assert.ok(camera.includes('putString("last_photo_uri"'));
  assert.ok(manifest.includes('android.permission.CAMERA'));
  assert.ok(manifest.includes('android:name=".CameraCaptureActivity"'));
  assert.ok(html.includes("window.AndroidJarvis.capturePhoto(true)"));
  assert.ok(html.includes("window.AndroidJarvis.capturePhoto(false)"));
  assert.match(html, /cameraAction==='show'/);
});

test("the final app contains no in-app updater or install-package permission", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.doesNotMatch(source, /PackageInstaller|installJarvisUpdate|downloadAndInstallJarvisUpdate|getInstalledVersionCode/);
  assert.doesNotMatch(manifest, /REQUEST_INSTALL_PACKAGES|UpdateInstallReceiver/);
  assert.doesNotMatch(html, /updateButton|checkJarvisUpdates|Check for updates|jarvis-latest/);
  assert.doesNotMatch(source, /JARVIS-Android-Updater/);
});


test("wake-word service survives Activity backgrounding and restores only after explicit opt-in", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const activity = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/WakeWordActivity.java", import.meta.url), "utf8");
  const onStop = source.match(/@Override protected void onStop\(\)\s*\{([\s\S]*?)\n    \}/);
  assert.ok(onStop, "MainActivity should define onStop");
  assert.doesNotMatch(onStop[1], /stopService|putBoolean\("enabled", false\)/,
    "backgrounding the Activity must not stop the explicitly enabled foreground service");
  assert.match(source, /restoreWakeWordServiceIfEnabled\(\)/);
  assert.match(source, /getBoolean\("enabled", false\)/);
  assert.match(source, /checkSelfPermission\(Manifest\.permission\.RECORD_AUDIO\)/);
  assert.match(source, /startForegroundService\(service\)/);
  assert.match(activity, /putBoolean\("enabled", true\)/);
  assert.match(activity, /stopService\(new Intent\(this, WakeWordService\.class\)\)/,
    "the user-facing microphone switch remains an explicit OFF control");
});


test("native phone contacts and call history require explicit Android runtime permissions", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.match(source, /public String getPhoneContacts\(\)/);
  assert.match(source, /Manifest\.permission\.READ_CONTACTS/);
  assert.match(source, /ContactsContract\.CommonDataKinds\.Phone\.CONTENT_URI/);
  assert.match(source, /ContactsContract\.CommonDataKinds\.Nickname\.CONTENT_ITEM_TYPE/);
  assert.match(source, /public String getRecentCallLogs\(int requestedLimit\)/);
  assert.match(source, /Manifest\.permission\.READ_CALL_LOG/);
  assert.match(source, /CallLog\.Calls\.CONTENT_URI/);
  assert.match(manifest, /android\.permission\.READ_CONTACTS/);
  assert.match(manifest, /android\.permission\.READ_CALL_LOG/);
});


test("contact import supports native phone contacts, nicknames and local duplicate reconciliation", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(html, /getPhoneContacts/);
  assert.match(html, /function mergeJarvisContacts/);
  assert.match(html, /NICKNAME/);
  assert.match(html, /nicknames/);
  assert.match(html, /function resolveJarvisContact/);
});

test("recent call history is displayed locally and never synced as call-log content", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(html, /getRecentCallLogs\(20\)/);
  assert.match(html, /callLogCommand/);
  assert.match(html, /Call history displayed locally/);
  assert.match(html, /will not send the call log to the JARVIS server/);
});

test("the phone APK bundles its own JARVIS interface and does not require GitHub Pages to load the UI", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(source, /getAssets\(\)\.open\("index\.html"\)/);
  assert.match(source, /loadDataWithBaseURL/);
  assert.doesNotMatch(source, /webView\.loadUrl\("https:\/\/hrishikeshks1611-stack\.github\.io/);
  assert.match(gradle, /prepareJarvisWebAssets/);
  assert.match(html, /!window\.AndroidJarvis&&'serviceWorker'/);
});

test("the exact blue ARC reactor artwork is the Android launcher icon", async () => {
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  const icon = await readFile(new URL("../app/src/main/res/drawable-nodpi/ic_jarvis_launcher.webp", import.meta.url));
  assert.match(manifest, /android:icon="@drawable\/ic_jarvis_launcher"/);
  assert.match(manifest, /android:roundIcon="@drawable\/ic_jarvis_launcher"/);
  assert.equal(icon.subarray(0, 4).toString("ascii"), "RIFF");
  assert.equal(icon.subarray(8, 12).toString("ascii"), "WEBP");
});

test("app discovery is limited to launchable non-financial apps and supports arbitrary installed app names", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.match(source, /public String getInstalledApps\(\)/);
  assert.match(source, /getInstalledApplications/);
  assert.match(source, /isFinancialApp\(info\.packageName, label\)/);
  assert.match(source, /normalizeAppLabel\(label\)\.equals\(query\)/);
  assert.match(manifest, /android\.permission\.QUERY_ALL_PACKAGES/);
});

test("notification access is user-enabled, filters financial apps, and remains local to the device", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/JarvisMediaNotificationListener.java", import.meta.url), "utf8");
  const activity = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(source, /onNotificationPosted/);
  assert.match(source, /MainActivity\.isFinancialApp/);
  assert.match(activity, /ACTION_NOTIFICATION_LISTENER_SETTINGS/);
  assert.match(activity, /getRecentNotifications\(\)/);
  assert.match(html, /notificationCommand/);
  assert.match(html, /Notifications displayed locally/);
});


test("financial trackers and transactions are not exposed as JARVIS features", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  assert.doesNotMatch(html, /budget tracker|expense tracker|income tracker|financial tracker/i);
  assert.match(source, /isFinancialApp/);
  assert.match(source, /money-related actions are outside my J\.A\.R\.V\.I\.S\. permissions/);
});

test("the legacy in-app update receiver is absent from the final Android source", async () => {
  await assert.rejects(readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/UpdateInstallReceiver.java", import.meta.url)));
});
