package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class WakeWordActivity extends Activity {
    private static final int REQUEST_AUDIO = 7201;
    private Switch microphoneSwitch;
    private TextView status;
    private SharedPreferences prefs;
    private boolean updating;
    private boolean receiverRegistered;
    private final BroadcastReceiver serviceUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (WakeWordService.ACTION_STATUS.equals(action)) {
                String message = intent.getStringExtra(WakeWordService.EXTRA_STATUS);
                if (message != null && status != null) status.setText(message);
            } else if (WakeWordService.ACTION_ERROR.equals(action)) {
                String message = intent.getStringExtra(WakeWordService.EXTRA_ERROR);
                if (status != null) status.setText("Wake-word setup failed: " + (message == null ? "Unknown error" : message));
                updating = true;
                if (microphoneSwitch != null) microphoneSwitch.setChecked(false);
                updating = false;
            } else if (WakeWordService.ACTION_READY.equals(action)) {
                if (status != null) status.setText("READY · Offline wake-word detection is listening for Hey JARVIS. Audio stays on this phone.");
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("jarvis_wake_word", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(android.graphics.Color.rgb(5, 11, 24));

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S. · PRIVATE WAKE WORD");
        title.setTextColor(android.graphics.Color.rgb(98, 217, 255));
        title.setTextSize(20);
        root.addView(title);

        TextView detail = new TextView(this);
        detail.setText("When enabled, JARVIS listens locally for “Hey JARVIS”, “Hello JARVIS”, “Wake up JARVIS”, and “JARVIS, are you there?”. Audio is processed on this phone and is never uploaded. The offline English (India) model is downloaded once during setup; no microphone audio is sent.");
        detail.setTextColor(android.graphics.Color.WHITE);
        detail.setTextSize(15);
        detail.setPadding(0, 22, 0, 22);
        root.addView(detail);

        microphoneSwitch = new Switch(this);
        microphoneSwitch.setText("Background microphone");
        microphoneSwitch.setTextColor(android.graphics.Color.WHITE);
        microphoneSwitch.setTextSize(18);
        root.addView(microphoneSwitch);

        status = new TextView(this);
        status.setTextColor(android.graphics.Color.LTGRAY);
        status.setTextSize(14);
        status.setPadding(0, 20, 0, 0);
        root.addView(status);
        setContentView(root);

        updating = true;
        boolean enabled = prefs.getBoolean("enabled", false);
        microphoneSwitch.setChecked(enabled);
        status.setText(enabled
                ? (WakeWordService.isActive()
                    ? "Wake-word service is active. Audio is processed locally on this phone."
                    : "Wake-word service is starting. Keep this setting enabled while setup completes.")
                : "Microphone is OFF.");
        updating = false;

        microphoneSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            if (checked) enableWakeWord();
            else disableWakeWord();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WakeWordService.ACTION_STATUS);
        filter.addAction(WakeWordService.ACTION_READY);
        filter.addAction(WakeWordService.ACTION_ERROR);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(serviceUpdates, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(serviceUpdates, filter);
        receiverRegistered = true;
        String saved = prefs.getString("status", null);
        if (saved != null && prefs.getBoolean("enabled", false)) status.setText(saved);
        else if (!prefs.getBoolean("enabled", false)) status.setText("Microphone is OFF.");
    }

    @Override protected void onPause() {
        if (receiverRegistered) {
            try { unregisterReceiver(serviceUpdates); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        super.onPause();
    }

    private void enableWakeWord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        prefs.edit().putBoolean("enabled", true).apply();
        Intent service = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        status.setText("STARTING · Preparing the offline speech model. Download progress will appear here.");
    }

    private void disableWakeWord() {
        prefs.edit().putBoolean("enabled", false).apply();
        stopService(new Intent(this, WakeWordService.class));
        prefs.edit().putString("status", "Microphone is OFF. JARVIS is not listening.").apply();
        status.setText("Microphone is OFF. JARVIS is not listening.");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            boolean audioGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (audioGranted) enableWakeWord();
            else {
                updating = true;
                microphoneSwitch.setChecked(false);
                updating = false;
                status.setText("Microphone permission was denied. Wake-word listening remains OFF.");
                Toast.makeText(this, "Microphone permission is required only for wake-word listening.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
