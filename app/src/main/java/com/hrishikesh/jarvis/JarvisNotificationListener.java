package com.hrishikesh.jarvis;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Narrowly used as the user-approved access gate for Android media sessions.
 * JARVIS does not read, store, or forward notification contents.
 */
public final class JarvisNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Deliberately do not inspect notification content.
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Deliberately do not inspect notification content.
    }
}
