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
  assert.ok(source.includes("AlarmIntentFactory.create(hour, minute, label)"));
  const alarmFactory = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/AlarmIntentFactory.java", import.meta.url), "utf8");
  assert.ok(alarmFactory.includes("AlarmClock.ACTION_SET_ALARM"));
  assert.ok(alarmFactory.includes("AlarmClock.EXTRA_SKIP_UI, false"));
  assert.ok(alarmFactory.includes("AlarmClock.EXTRA_HOUR"));
  assert.ok(alarmFactory.includes("AlarmClock.EXTRA_MINUTES"));
  assert.ok(source.includes("public String createCalendarEvent(String title, long beginMillis, long endMillis, String description)"));
  assert.ok(source.includes("Intent.ACTION_INSERT"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_BEGIN_TIME"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_END_TIME"));
  assert.ok(manifest.includes('com.android.alarm.permission.SET_ALARM'));
});

test("JARVIS 15.0 installs separately from the protected 14.0 package", async () => {
  const gradle = await readFile(new URL("../app/build.gradle", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.match(gradle, /applicationId\s+'com\.hrishikesh\.jarvis\.fifteen\.release'/);
  assert.ok(gradle.includes('versionCode 1501'));
  assert.ok(gradle.includes("versionName '15.0.1'"));
  assert.doesNotMatch(gradle, /applicationId\s+'com\.hrishikesh\.jarvis\.fourteen\.release'/);
  assert.match(manifest, /android:label="J\.A\.R\.V\.I\.S 15\.0"/);
  const migration = await readFile(new URL("../docs/JARVIS-15-DEVICE-SYNC-AND-CUTOVER.md", import.meta.url), "utf8");
  assert.ok(migration.includes("Transfer owner here"));
  assert.ok(migration.includes("revoke the old 14.0 device session"));
  assert.ok(migration.includes("does not uninstall the app"));
  assert.ok(migration.includes("shared conversation history or saved memories"));
});

test("APK workflow builds 14.0.1 and the update workflow verifies in-place data preservation", async () => {
  const workflow = await readFile(new URL("../.github/workflows/android-14.yml", import.meta.url), "utf8");
  assert.match(workflow, /contents:\s*read/);
  assert.doesNotMatch(workflow, /gh release create|action-gh-release|Publish.*APK/);
  assert.match(workflow, /Build J.A.R.V.I.S 14.0.1 Android phone and TV packages/);
  assert.ok(workflow.includes("com.hrishikesh.jarvis.fourteen.release"));
  assert.ok(workflow.includes("com.hrishikesh.jarvis.fourteen.release.tv"));
  const updateWorkflow = await readFile(new URL("../.github/workflows/android-14-1-update.yml", import.meta.url), "utf8");
  assert.match(updateWorkflow, /name: JARVIS-14\.0-Android-phone-QA/);
  assert.match(updateWorkflow, /run-id: 43556405657/);
  assert.match(updateWorkflow, /adb install -r update-test-inputs\/jarvis-14\.0\.1-candidate\.apk/);
  assert.match(updateWorkflow, /update-compatibility-sentinel\.txt/);
  assert.match(updateWorkflow, /pm list packages/);
  assert.match(updateWorkflow, /jarvis-14\.0-preferences-preserved/);
});



test("native contact calling resolves contacts and places a direct call after Android permission", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes('public String callContact(String requested)'));
  assert.ok(source.includes('public String callNumber(String requestedNumber, String displayName)'));
  assert.ok(source.includes('ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI'));
  assert.ok(source.includes('AMBIGUOUS:'));
  assert.ok(source.includes('REQUEST_CONTACTS'));
  assert.ok(source.includes('REQUEST_CALL_PHONE'));
  assert.ok(source.includes('Manifest.permission.CALL_PHONE'));
  assert.ok(source.includes('Intent.ACTION_CALL'));
  assert.ok(source.includes('CALL_STARTING:'));
  assert.ok(source.includes('placePendingCall()'));
  assert.ok(!source.includes('Intent.ACTION_DIAL'));
  assert.ok(manifest.includes('android.permission.READ_CONTACTS'));
  assert.ok(manifest.includes('android.permission.CALL_PHONE'));
});


test("contact commands reach the native Android caller before the VCF dialler fallback", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const nativeCall = html.indexOf("window.AndroidJarvis.callContact(target)");
  const vcfFallback = html.indexOf("resolveJarvisContact(target)");
  assert.ok(nativeCall >= 0 && vcfFallback > nativeCall);
  assert.ok(html.includes("if(!nativeResult.startsWith('NOT_FOUND:'))"));
  assert.ok(html.includes('CALL_STARTING:'));
  assert.ok(html.includes('window.AndroidJarvis.callNumber(result.contact.number,result.contact.name)'));
  const bundledHtml = await readFile(new URL("../app/src/main/assets/index.html", import.meta.url), "utf8");
  assert.ok(bundledHtml.includes('window.AndroidJarvis.callNumber(result.contact.number,result.contact.name)'));
  assert.ok(bundledHtml.includes("nativeResult.startsWith('CALL_STARTING:')"));
  assert.ok(!html.includes("const callCommand=message.match"));
});

test("microphone readiness is reported only after native capture and errors reach the UI", async () => {
  const activity = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const service = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/WakeWordService.java", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(activity, /WakeWordService\.ACTION_READY/);
  assert.match(activity, /WakeWordService\.ACTION_ERROR/);
  assert.match(service, /broadcast\(ACTION_READY, null\)/);
  assert.match(service, /broadcast\(ACTION_ERROR, detail\)/);
  assert.match(html, /onMicrophoneReady/);
  assert.match(html, /onMicrophoneError/);
});

test("general YouTube video commands search in YouTube without touching YouTube Music playback", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const capabilities = await readFile(new URL("../lib/jarvis-capabilities.js", import.meta.url), "utf8");
  assert.ok(source.includes("public String searchYouTubeVideos(String query)"));
  assert.ok(source.includes('https://www.youtube.com/results?search_query='));
  assert.ok(source.includes('intent.setPackage("com.google.android.youtube")'));
  assert.ok(html.includes("function extractYouTubeVideoQuery(message)"));
  assert.ok(html.includes("window.AndroidJarvis.searchYouTubeVideos(youtubeVideoQuery)"));
  assert.ok(html.includes("automatic video selection is not enabled"));
  const bundledHtml = await readFile(new URL("../app/src/main/assets/index.html", import.meta.url), "utf8");
  const routeIndex = html.indexOf("function extractYouTubeVideoQuery(message)");
  const musicIndex = html.indexOf("const musicCommand=message.match(");
  const bundledRouteIndex = bundledHtml.indexOf("function extractYouTubeVideoQuery(message)");
  const bundledMusicIndex = bundledHtml.indexOf("const musicCommand=message.match(");
  assert.ok(routeIndex >= 0 && routeIndex < musicIndex, "YouTube routing must run before generic music routing");
  assert.ok(bundledRouteIndex >= 0 && bundledRouteIndex < bundledMusicIndex, "APK asset must include YouTube routing before music routing");
  assert.ok(html.includes("if(/\\byoutube\\s+music\\b/i.test(clean))return null;"), "explicit YouTube Music requests must bypass video routing");
  assert.ok(html.includes("window.AndroidJarvis.playYoutubeMusic(videoId)"), "existing YouTube Music playback path must remain intact");
  assert.ok(capabilities.includes('id: "youtube_video_search", area: "media", status: "implemented"'));
  assert.ok(capabilities.includes('youtube_video_search: "Search general YouTube videos'));
});

test("live Android bridge opens the exact selected YouTube Music track", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String playYoutubeMusic(String videoId)"));
  assert.ok(source.includes("public void searchYouTubeMusic(String query, String requestId)"));
  assert.ok(source.includes("public String getYouTubeMusicPlaybackStatus()"));
  assert.ok(source.includes("PlaybackState.STATE_PLAYING"));
  assert.ok(source.includes("getAssets().open(\"index.html\")"));
  assert.ok(source.includes("loadDataWithBaseURL("));
  assert.ok(!source.includes('webView.loadUrl("https://hrishikeshks1611-stack.github.io/J.A.R.V.I.S-/v7/")'));
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

test("native installer remains guarded while the web-only interface exposes no APK updater", async () => {
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
  assert.doesNotMatch(html, /checkJarvisUpdates/);
  assert.doesNotMatch(html, /id="updateButton"/);
});


test("explicit wake-word listening survives Activity stop and is restored on foreground launch", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const settings = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/WakeWordActivity.java", import.meta.url), "utf8");
  assert.match(source, /wakePrefs\.getBoolean\("enabled", false\)/);
  assert.match(source, /if \(!wakeWordEnabled\) stopService/);
  assert.doesNotMatch(source, /putBoolean\("enabled", false\)\.apply\(\);\s*stopService\(new Intent\(this, WakeWordService\.class\)\);/);
  assert.match(settings, /boolean enabled = prefs\.getBoolean\("enabled", false\);/);
});
