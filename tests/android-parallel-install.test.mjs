import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const gradle = readFileSync(new URL('../app/build.gradle', import.meta.url), 'utf8');
const manifest = readFileSync(new URL('../app/src/phonePreview/AndroidManifest.xml', import.meta.url), 'utf8');
const workflow = readFileSync(new URL('../.github/workflows/android-check.yml', import.meta.url), 'utf8');

test('preview app has a distinct package and cannot replace the installed app', () => {
  assert.ok(gradle.includes('phonePreview {'));
  assert.ok(gradle.includes("applicationIdSuffix '.preview'"));
  assert.ok(gradle.includes("versionNameSuffix '-preview'"));
  assert.ok(manifest.includes('android:label="J.A.R.V.I.S. PREVIEW"'));
});

test('Android CI builds and retains the separate preview APK', () => {
  assert.ok(workflow.includes('assemblePhonePreviewDebug'));
  assert.ok(workflow.includes('app-phonePreview-debug.apk'));
});
