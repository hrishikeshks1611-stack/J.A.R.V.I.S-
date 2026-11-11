package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

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
        if (ACTION_STOP_TEST.equals(getIntent().getAction())) {
            stopService(new Intent(this, WakeWordService.class));
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Intent service = new Intent(this, WakeWordService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        }
        finish();
    }
}
