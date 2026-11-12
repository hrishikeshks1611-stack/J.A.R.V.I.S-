package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WakeWordServiceSmokeTest {
    @Test
    public void startsPrivateDictationServiceThroughTheAppUid() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        instrumentation.getUiAutomation().grantRuntimePermission(
                context.getPackageName(), Manifest.permission.RECORD_AUDIO);

        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(launch);

        Intent service = new Intent(context, WakeWordService.class)
                .setAction(WakeWordService.ACTION_DICTATION);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }

        // Keep the app activity in the foreground while Android establishes
        // the microphone foreground service. The workflow checks the service
        // log for the final offline-model/recognizer-ready signal.
        Thread.sleep(3000);
        instrumentation.runOnMainSync(activity::finish);
    }
}
