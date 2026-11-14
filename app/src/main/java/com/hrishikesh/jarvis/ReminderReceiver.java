package com.hrishikesh.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    public static final String EXTRA_TITLE = "reminder_title";
    public static final String EXTRA_NOTIFICATION_ID = "reminder_notification_id";
    private static final String CHANNEL_ID = "jarvis_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String title = intent == null ? null : intent.getStringExtra(EXTRA_TITLE);
        int notificationId = intent == null ? 0 : intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        if (title == null || title.trim().isEmpty()) return;
        ReminderScheduler.remove(context, notificationId);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "J.A.R.V.I.S. reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Scheduled reminders created by J.A.R.V.I.S.");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_jarvis_launcher)
                .setContentTitle("J.A.R.V.I.S. reminder")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(content)
                .build();
        try { manager.notify(notificationId, notification); }
        catch (SecurityException ignored) { }
    }
}
