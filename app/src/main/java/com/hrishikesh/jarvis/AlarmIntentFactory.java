package com.hrishikesh.jarvis;

import android.content.Intent;
import android.provider.AlarmClock;

public final class AlarmIntentFactory {
    private AlarmIntentFactory() { }

    public static Intent create(int hour, int minute, String label) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Alarm time is outside the valid clock range");
        }
        String message = label == null || label.trim().isEmpty()
                ? "JARVIS reminder" : label.trim();
        return new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, message)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
    }
}