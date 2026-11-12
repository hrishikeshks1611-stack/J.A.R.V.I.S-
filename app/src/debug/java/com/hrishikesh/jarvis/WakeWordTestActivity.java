package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Debug-only entry point used by Android emulator smoke tests.
 * The test launcher starts/stops the private service from inside the app UID;
 * never export WakeWordService itself just to make shell-based tests work.
 * The production APK does not include this Activity or its manifest entry.
 */
public class WakeWordTestActivity extends Activity {
    public static final String ACTION_STOP_TEST = "com.hrishikesh.jarvis.TEST_STOP_WAKE";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean audioGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        Log.i("WakeWordTest", "Test launcher started; RECORD_AUDIO granted=" + audioGranted + ", action=" + getIntent().getAction());
        if (ACTION_STOP_TEST.equals(getIntent().getAction())) {
            stopService(new Intent(this, WakeWordService.class));
            Log.i("WakeWordTest", "Requested wake-word service stop");
        } else if (audioGranted) {
            Intent service = new Intent(this, WakeWordService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            Log.i("WakeWordTest", "Requested foreground wake-word service start");
        } else {
            Log.e("WakeWordTest", "Microphone permission was not granted; service was not started");
        }
        finish();
    }
}
