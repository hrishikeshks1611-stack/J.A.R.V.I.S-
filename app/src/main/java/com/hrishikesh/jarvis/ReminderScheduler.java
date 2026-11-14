package com.hrishikesh.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ReminderScheduler {
    private static final String PREFS = "jarvis_reminders";
    private static final String KEY_ENTRIES = "entries";

    private ReminderScheduler() { }

    public static synchronized void schedule(Context context, long triggerAtMillis, String title) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Reminder title is empty");
        if (triggerAtMillis <= System.currentTimeMillis()) throw new IllegalArgumentException("Reminder time must be in the future");
        int id = notificationId(triggerAtMillis, title);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) throw new IllegalStateException("Alarm service unavailable");
        PendingIntent pending = pendingIntent(context, id, title);
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
        save(context, id, triggerAtMillis, title);
    }

    static synchronized void remove(Context context, int id) {
        JSONArray current = read(context);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item != null && item.optInt("id", Integer.MIN_VALUE) != id) updated.put(item);
        }
        prefs(context).edit().putString(KEY_ENTRIES, updated.toString()).apply();
    }

    static synchronized void rescheduleAll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        JSONArray entries = read(context);
        long now = System.currentTimeMillis();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", Integer.MIN_VALUE);
            long at = item.optLong("at", 0L);
            String title = item.optString("title", "");
            if (id == Integer.MIN_VALUE || title.trim().isEmpty()) continue;
            if (at <= now) {
                remove(context, id);
                continue;
            }
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, id, title));
            } catch (RuntimeException ignored) {
                // Keep the saved reminder so a later time-change or reboot can retry it.
            }
        }
    }

    private static void save(Context context, int id, long at, String title) {
        JSONArray current = read(context);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item != null && item.optInt("id", Integer.MIN_VALUE) != id) updated.put(item);
        }
        JSONObject entry = new JSONObject();
        try {
            entry.put("id", id);
            entry.put("at", at);
            entry.put("title", title.trim());
            updated.put(entry);
        } catch (org.json.JSONException error) {
            throw new IllegalStateException("Could not save reminder", error);
        }
        prefs(context).edit().putString(KEY_ENTRIES, updated.toString()).commit();
    }

    private static JSONArray read(Context context) {
        String raw = prefs(context).getString(KEY_ENTRIES, "[]");
        try { return new JSONArray(raw); }
        catch (org.json.JSONException ignored) { return new JSONArray(); }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int notificationId(long at, String title) {
        int id = (int) (31L * at + title.hashCode());
        return id == Integer.MIN_VALUE ? 0 : Math.abs(id);
    }

    private static PendingIntent pendingIntent(Context context, int id, String title) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_TITLE, title);
        intent.putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, id);
        return PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}