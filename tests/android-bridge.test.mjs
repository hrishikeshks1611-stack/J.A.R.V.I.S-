import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("native bridge exposes confirmation-based Clock and Calendar actions", async () => {
  const source = await readFile(new URL("../app/src/main/java/com/hrishikesh/jarvis/MainActivity.java", import.meta.url), "utf8");
  const manifest = await readFile(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
  assert.ok(source.includes("public String setAlarm(int hour, int minute, String label)"));
  assert.ok(source.includes("AlarmClock.ACTION_SET_ALARM"));
  assert.ok(source.includes("AlarmClock.EXTRA_SKIP_UI, false"));
  assert.ok(source.includes("public String createCalendarEvent(String title, long beginMillis, long endMillis, String description)"));
  assert.ok(source.includes("Intent.ACTION_INSERT"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_BEGIN_TIME"));
  assert.ok(source.includes("CalendarContract.EXTRA_EVENT_END_TIME"));
  assert.ok(manifest.includes('com.android.alarm.permission.SET_ALARM'));
});
