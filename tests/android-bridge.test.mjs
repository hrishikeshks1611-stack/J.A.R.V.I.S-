import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("native bridge exposes confirmation-based Clock and Calendar actions", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String openCastSettings()"));
  assert.ok(source.includes("Settings.ACTION_CAST_SETTINGS"));
  assert.ok(source.includes("public String setAlarm(int hour, int minute, String label)"));
  assert.ok(source.includes("AlarmClock.ACTION_SET_ALARM"));
  assert.ok(source.includes("AlarmClock.EXTRA_SKIP_UI, false"));
  assert.ok(source.includes("public String createCalendarEvent(String title, long beginMillis, long endMillis, String description)"));
  assert.ok(source.includes("Intent.ACTION_INSERT"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_BEGIN_TIME"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_END_TIME"));
  assert.ok(manifest.includes('com.android.alarm.permission.SET_ALARM'));
});

test("Android release metadata identifies full JARVIS 1.1 and advances the installed 1.0.15 app", async () => {
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  assert.match(gradle, /versionCode\s+601/);
  assert.match(gradle, /versionName\s+'6\.0\.1'/);
});

test("APK workflow uploads phone and TV CI artifacts without publishing GitHub releases", async () => {
  const workflow = await readFile(new URL("../.github/workflows/android-apk.yml", import.meta.url), "utf8");
  assert.match(workflow, /contents:\s*read/);
  assert.doesNotMatch(workflow, /gh release create/);
  assert.match(workflow, /JARVIS-Full-Recovery-Android-phone/);
  assert.match(workflow, /JARVIS-Full-Recovery-Android-TV/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
});


test("Android bridge requests play-from-search from the YouTube Music package", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH"));
  assert.ok(source.includes("SearchManager.QUERY"));
  assert.ok(source.includes('setPackage("com.google.android.apps.youtube.music")'));
  assert.ok(source.includes("SEARCH_FALLBACK:"));
  assert.ok(manifest.includes('package android:name="com.google.android.apps.youtube.music"'));
});

test("native CameraX capture supports rear photos, selfies, and latest-photo retrieval", async () => {
  const activity = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/CameraCaptureActivity.java", import.meta.url), "utf8");
  const bridge = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  assert.ok(activity.includes("CameraSelector.DEFAULT_FRONT_CAMERA"));
  assert.ok(activity.includes("CameraSelector.DEFAULT_BACK_CAMERA"));
  assert.ok(activity.includes("last_photo_uri"));
  assert.ok(bridge.includes("public void capturePhoto(boolean selfie)"));
  assert.ok(bridge.includes("public void showLastPhoto()"));
  assert.ok(manifest.includes('android.permission.CAMERA'));
  assert.ok(manifest.includes('.CameraCaptureActivity'));
  assert.ok(gradle.includes('androidx.camera:camera-view:1.4.2'));
});

test("Android 11+ launcher discovery exposes installed apps and opens unknown app names", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String getInstalledApps()"));
  assert.ok(source.includes("queryIntentActivities(launcher, 0)"));
  assert.ok(source.includes("findLaunchableApp(name)"));
  assert.ok(manifest.includes('android:name="android.intent.action.MAIN"'));
  assert.ok(manifest.includes('android:name="android.intent.category.LAUNCHER"'));
  assert.doesNotMatch(manifest, /QUERY_ALL_PACKAGES/);
});

test("CameraX capture activity is lifecycle-aware and requests camera permission", async () => {
  const activity = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/CameraCaptureActivity.java", import.meta.url), "utf8");
  assert.ok(activity.includes("extends ComponentActivity"));
  assert.ok(activity.includes("bindToLifecycle(this"));
  assert.ok(activity.includes("requestPermissions(new String[]{Manifest.permission.CAMERA}"));
});

test("Camera smoke launcher is debug-only while the production camera activity stays private", async () => {
  const debugManifest = await readFile(new URL("../app/src/debug/AndroidManifest.xml", import.meta.url), "utf8");
  const launcher = await readFile(new URL("../app/src/debug/java/com/hrishikesh/jarvis/CameraCaptureTestActivity.java", import.meta.url), "utf8");
  const productionManifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(debugManifest.includes(".CameraCaptureTestActivity"));
  assert.ok(debugManifest.includes('android:permission="android.permission.DUMP"'));
  assert.ok(launcher.includes("new Intent(this, CameraCaptureActivity.class)"));
  assert.ok(productionManifest.includes('android:name=".CameraCaptureActivity" android:exported="false"'));
});
