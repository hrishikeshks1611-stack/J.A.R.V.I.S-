package com.hrishikesh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
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
        detail.setText("When enabled, JARVIS listens locally for “JARVIS”, “Hello JARVIS” or “Wake up JARVIS”. Audio is processed on this phone and is never uploaded. The offline English model is downloaded once during setup; no microphone audio is sent.");
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
        boolean enabled = prefs.getBoolean("enabled", false) && WakeWordService.isActive();
        prefs.edit().putBoolean("enabled", enabled).apply();
        microphoneSwitch.setChecked(enabled);
        status.setText(enabled ? "Wake-word service is running or preparing. Use the notification or this switch to stop it." : "Microphone is OFF.");
        updating = false;

        microphoneSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            if (checked) enableWakeWord();
            else disableWakeWord();
        });
    }

    private void enableWakeWord() {
        boolean audioGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean notificationsGranted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (!audioGranted || !notificationsGranted) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}, REQUEST_AUDIO);
            else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        prefs.edit().putBoolean("enabled", true).apply();
        Intent service = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        status.setText("Starting private offline recognition. The first setup downloads the speech model once.");
    }

    private void disableWakeWord() {
        prefs.edit().putBoolean("enabled", false).apply();
        stopService(new Intent(this, WakeWordService.class));
        status.setText("Microphone is OFF. JARVIS is not listening.");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            boolean audioGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean notificationsGranted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (audioGranted && notificationsGranted) enableWakeWord();
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
