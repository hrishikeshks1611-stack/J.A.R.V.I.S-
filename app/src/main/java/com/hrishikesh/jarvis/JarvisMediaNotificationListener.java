package com.hrishikesh.jarvis;

import android.service.notification.NotificationListenerService;

/**
 * Declares JARVIS as a notification listener so Android can grant access
 * to active media sessions. The app only uses this permission to control
 * the YouTube Music media session when the user explicitly asks.
 */
public class JarvisMediaNotificationListener extends NotificationListenerService {
}
