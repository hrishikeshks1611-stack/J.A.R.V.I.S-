package com.hrishikesh.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(confirmation); }
                catch (Exception error) {
                    Toast.makeText(context, "Android could not open the update confirmation screen. Open JARVIS and check for updates again.", Toast.LENGTH_LONG).show();
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "J.A.R.V.I.S. update installed successfully.", Toast.LENGTH_LONG).show();
        } else {
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context, "Android rejected the update. Your existing JARVIS app remains installed. " + (detail == null ? "" : detail), Toast.LENGTH_LONG).show();
        }
    }
}
