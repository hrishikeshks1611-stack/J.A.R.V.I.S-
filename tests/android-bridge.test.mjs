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

test("new JARVIS 6.0.0 has a separate package identity from legacy 5.0.2", async () => {
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  assert.match(gradle, /applicationId\s+'com\.hrishikesh\.jarvis\.next'/);
  assert.match(gradle, /versionCode\s+600/);
  assert.match(gradle, /versionName\s+'6\.0\.0'/);
});

test("APK workflow verifies separate-install builds and never publishes debug APKs as releases", async () => {
  const workflow = await readFile(new URL("../.github/workflows/android-apk.yml", import.meta.url), "utf8");
  assert.match(workflow, /contents:\s*read/);
  assert.doesNotMatch(workflow, /gh release create|action-gh-release|Publish phone APK/);
  assert.match(workflow, /separate-install/);
  assert.ok(workflow.includes("com.hrishikesh.jarvis.next"));
  assert.ok(workflow.includes("com.hrishikesh.jarvis.next.tv"));
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

test("in-app updater validates package and version before Android PackageInstaller handoff", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const receiver = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/UpdateInstallReceiver.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(source, /getInstalledVersionCode/);
  assert.match(source, /expectedVersionCode <= BuildConfig\.VERSION_CODE/);
  assert.match(source, /getPackageArchiveInfo/);
  assert.match(source, /getPackageName\(\)\.equals\(candidate\.packageName\)/);
  assert.match(source, /candidateVersion <= BuildConfig\.VERSION_CODE/);
  assert.match(source, /getPackageInstaller\(\)/);
  assert.match(receiver, /STATUS_PENDING_USER_ACTION/);
  assert.match(receiver, /STATUS_SUCCESS/);
  assert.match(manifest, /REQUEST_INSTALL_PACKAGES/);
  assert.match(manifest, /UpdateInstallReceiver/);
  assert.match(html, /checkJarvisUpdates/);
  assert.match(html, /api\.github\.com\/repos\/hrishikeshks1611-stack/);
});
