import test from "node:test";
import assert from "node:assert/strict";
import { calculateExpression, executeJarvisTool, JARVIS_TOOL_DECLARATIONS } from "../lib/jarvis-tools.js";

test("calculator handles precedence, parentheses, and unary signs", () => {
  assert.equal(calculateExpression("2 + 3 * (4 - 1)"), 11);
  assert.equal(calculateExpression("-12 / 3 + 2"), -2);
  assert.equal(calculateExpression("0.1 + 0.2"), 0.3);
});

test("calculator rejects code, identifiers, and unsupported syntax", () => {
  for (const expression of ["process.exit()", "1;2", "Math.random()", "2 ** 8", "1/0", ""]) {
    assert.throws(() => calculateExpression(expression));
  }
});

test("calculator enforces input and result bounds", () => {
  assert.throws(() => calculateExpression("1".repeat(121)));
  assert.throws(() => calculateExpression("9999999999999999 * 9999999999999999"));
});

test("tool registry exposes only the approved seven tools", () => {
  const names = JARVIS_TOOL_DECLARATIONS[0].function_declarations.map(item => item.name);
  assert.deepEqual(names, ["calculate", "get_device_status", "request_open_app", "request_alarm", "request_calendar_event", "search_saved_memories", "search_conversation"]);
});

test("device status reports only fields explicitly supplied by the app", async () => {
  const result = await executeJarvisTool("get_device_status", {
    fields: ["localTime", "networkOnline", "latitude", "password"]
  }, { deviceContext: { localTime: "Tuesday 4 PM", networkOnline: true, latitude: 10, password: "secret" } });
  assert.deepEqual(result.device, { localTime: "Tuesday 4 PM", networkOnline: true });
  assert.equal(result.available, true);
});

test("saved-memory search is read-only and returns relevant bounded matches", async () => {
  const memories = [
    { text: "User prefers JARVIS to call him Sir.", createdAt: 1 },
    { text: "User likes dark blue interface design.", createdAt: 2 },
    { text: "Unrelated guitar practice.", createdAt: 3 }
  ];
  const result = await executeJarvisTool("search_saved_memories", { query: "JARVIS Sir" }, { memories });
  assert.equal(result.count, 1);
  assert.match(result.matches[0].text, /call him Sir/);
});

test("conversation search returns relevant messages without mutation", async () => {
  const history = [{ role: "user", text: "Build the offline JARVIS wake word", createdAt: 10 }];
  const result = await executeJarvisTool("search_conversation", { query: "offline wake word" }, { history });
  assert.equal(result.count, 1);
  assert.equal(result.matches[0].role, "user");
  assert.equal(history.length, 1);
});

test("unknown tools and malformed arguments fail closed", async () => {
  await assert.rejects(() => executeJarvisTool("run_shell", { command: "id" }));
  await assert.rejects(() => executeJarvisTool("calculate", ["2+2"]));
});

test("alarm tool only prepares a valid action for explicit app confirmation", async () => {
  const result = await executeJarvisTool("request_alarm", { hour: 21, minute: 5, label: "Study" }, { userMessage: "Set an alarm for 9:05 PM." });
  assert.equal(result.requiresConfirmation, true);
  assert.deepEqual(result.pendingAction, { type: "alarm", hour: 21, minute: 5, label: "Study" });
  await assert.rejects(() => executeJarvisTool("request_alarm", { hour: 25, minute: 0, label: "Invalid" }, { userMessage: "Set an alarm for 9:05 PM." }));
  await assert.rejects(() => executeJarvisTool("request_alarm", { hour: 21, minute: 5, label: "Study" }, { userMessage: "How do I set an alarm?" }));
});

test("calendar tool requires future timezone-aware dates and caps event duration", async () => {
  const result = await executeJarvisTool("request_calendar_event", {
    title: "Project review", startIso: "2099-04-10T10:00:00+05:30",
    endIso: "2099-04-10T11:00:00+05:30", description: "Review milestones"
  }, { userMessage: "Add a calendar event for the project review." });
  assert.equal(result.requiresConfirmation, true);
  assert.equal(result.pendingAction.type, "calendar");
  assert.ok(result.pendingAction.endMillis > result.pendingAction.beginMillis);
  await assert.rejects(() => executeJarvisTool("request_calendar_event", {
    title: "No offset", startIso: "2099-04-10T10:00:00", endIso: "2099-04-10T11:00:00"
  }, { userMessage: "Add a calendar event." }));
  await assert.rejects(() => executeJarvisTool("request_calendar_event", {
    title: "Too long", startIso: "2099-04-10T10:00:00Z", endIso: "2099-04-11T00:00:00Z"
  }, { userMessage: "Add a calendar event." }));
  await assert.rejects(() => executeJarvisTool("request_calendar_event", {
    title: "Meeting", startIso: "2099-04-10T10:00:00Z", endIso: "2099-04-10T11:00:00Z"
  }, { userMessage: "How do I create a calendar event?" }));
});

test("app launch tool only prepares explicit non-financial app requests", async () => {
  const result = await executeJarvisTool("request_open_app", { appName: "YouTube" }, { userMessage: "Please open YouTube." });
  assert.equal(result.requiresConfirmation, true);
  assert.deepEqual(result.pendingAction, { type: "open_app", appName: "YouTube" });
  await assert.rejects(() => executeJarvisTool("request_open_app", { appName: "YouTube" }, { userMessage: "How do I open YouTube?" }));
  await assert.rejects(() => executeJarvisTool("request_open_app", { appName: "PhonePe" }, { userMessage: "Open PhonePe." }));
});
