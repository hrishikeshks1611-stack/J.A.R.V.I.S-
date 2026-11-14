package com.hrishikesh.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.NotificationManager;
import android.os.Build;
import android.provider.AlarmClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AlarmReminderRegressionTest {
    private Context context;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test public void alarmIntentCarriesExactTimeLabelAndRequiresClockConfirmation() {
        Intent intent = AlarmIntentFactory.create(7, 5, "Morning medicine");
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.getAction());
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1));
        assertEquals(5, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1));
        assertEquals("Morning medicine", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE));
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true));
    }

    @Test public void alarmIntentRejectsInvalidHourAndMinuteAndNormalizesLabel() {
        assertThrows(IllegalArgumentException.class, () -> AlarmIntentFactory.create(-1, 0, "Bad"));
        assertThrows(IllegalArgumentException.class, () -> AlarmIntentFactory.create(24, 0, "Bad"));
        assertThrows(IllegalArgumentException.class, () -> AlarmIntentFactory.create(10, -1, "Bad"));
        assertThrows(IllegalArgumentException.class, () -> AlarmIntentFactory.create(10, 60, "Bad"));
        assertEquals("JARVIS reminder", AlarmIntentFactory.create(10, 0, "  ").getStringExtra(AlarmClock.EXTRA_MESSAGE));
        assertEquals("Call home", AlarmIntentFactory.create(10, 0, "  Call home  ").getStringExtra(AlarmClock.EXTRA_MESSAGE));
    }

    @Test public void reminderIsPersistedAndHasARealAlarmManagerPendingIntent() throws Exception {
        clearStoredReminders();
        long trigger = System.currentTimeMillis() + 15 * 60 * 1000L;
        String title = "JARVIS regression " + System.currentTimeMillis();
        ReminderScheduler.schedule(context, trigger, title);

        JSONArray entries = storedEntries();
        assertEquals(1, entries.length());
        JSONObject entry = entries.getJSONObject(0);
        assertEquals(title, entry.getString("title"));
        assertEquals(trigger, entry.getLong("at"));
        int id = entry.getInt("id");
        PendingIntent pending = PendingIntent.getBroadcast(context, id,
                new Intent(context, ReminderReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertNotNull("AlarmManager PendingIntent was not registered", pending);
    }

    @Test public void reminderRejectsEmptyTitleAndPastTrigger() {
        assertThrows(IllegalArgumentException.class,
                () -> ReminderScheduler.schedule(context, System.currentTimeMillis() + 60_000L, " "));
        assertThrows(IllegalArgumentException.class,
                () -> ReminderScheduler.schedule(context, System.currentTimeMillis() - 1L, "Past"));
    }

    @Test public void bootRecoveryRecreatesAlarmFromPersistedReminder() throws Exception {
        JSONArray entries = storedEntries();
        assertTrue("Run persistence test before reboot-recovery test", entries.length() > 0);
        JSONObject entry = entries.getJSONObject(0);
        int id = entry.getInt("id");
        Intent alarmIntent = new Intent(context, ReminderReceiver.class);
        PendingIntent before = PendingIntent.getBroadcast(context, id, alarmIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        assertNotNull("Boot receiver did not restore the reminder after reboot", before);
        assertTrue("Reminder should still be in the future",
                entry.getLong("at") > System.currentTimeMillis());
    }

    private void clearStoredReminders() {
        JSONArray old = storedEntries();
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) continue;
                int id = item.optInt("id", Integer.MIN_VALUE);
                if (id == Integer.MIN_VALUE) continue;
                PendingIntent pending = PendingIntent.getBroadcast(context, id,
                        new Intent(context, ReminderReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    manager.cancel(pending);
                    pending.cancel();
                }
            }
        }
        context.getSharedPreferences("jarvis_reminders", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private JSONArray storedEntries() {
        SharedPreferences prefs = context.getSharedPreferences("jarvis_reminders", Context.MODE_PRIVATE);
        try { return new JSONArray(prefs.getString("entries", "[]")); }
        catch (Exception error) { throw new AssertionError("Saved reminder data is not valid JSON", error); }
    }
}