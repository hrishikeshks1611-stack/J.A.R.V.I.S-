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
  assert.ok(source.includes("public String setAlarm(int hour, int minute, String label)"));
  assert.ok(source.includes("AlarmClock.ACTION_SET_ALARM"));
  assert.ok(source.includes("AlarmClock.EXTRA_SKIP_UI, false"));
  assert.ok(source.includes("public String createCalendarEvent(String title, long beginMillis, long endMillis, String description)"));
  assert.ok(source.includes("Intent.ACTION_INSERT"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_BEGIN_TIME"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_END_TIME"));
  assert.ok(manifest.includes('com.android.alarm.permission.SET_ALARM'));
});

test("Android update metadata advances production JARVIS from 5.0.0 to 5.0.1", async () => {
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  assert.match(gradle, /applicationId\s+'com\.hrishikesh\.jarvis'/);
  assert.match(gradle, /versionCode\s+501/);
  assert.match(gradle, /versionName\s+'5\.0\.1'/);
});

test("APK workflow cannot publish debug-signed builds as downloadable releases", async () => {
  const workflow = await readFile(new URL("../.github/workflows/android-apk.yml", import.meta.url), "utf8");
  assert.match(workflow, /contents:\s*read/);
  assert.doesNotMatch(workflow, /gh release create/);
  assert.match(workflow, /debug-verification/);
});


test("live Android bridge sends YouTube Music play-from-search intents", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String playYoutubeMusic(String query)"));
  assert.ok(source.includes("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH"));
  assert.ok(source.includes('play.setPackage("com.google.android.apps.youtube.music")'));
  assert.ok(manifest.includes('com.google.android.apps.youtube.music'));
});
