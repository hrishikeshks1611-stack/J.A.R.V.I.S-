package com.hrishikesh.jarvis;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses Android's explicit Notification Access setting for media controls and a
 * small, on-device-only recent-notification list. Financial app notifications
 * are excluded and notification contents are never sent to the JARVIS server.
 */
public class JarvisMediaNotificationListener extends NotificationListenerService {
    private static final int MAX_RECENT = 50;
    private static final List<JSONObject> RECENT = new ArrayList<>();

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null || sbn.isOngoing()) return;
        String packageName = sbn.getPackageName() == null ? "" : sbn.getPackageName();
        String appName;
        try { appName = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0))); }
        catch (PackageManager.NameNotFoundException error) { appName = packageName; }
        if (MainActivity.isFinancialApp(packageName, appName)) return;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if ((title == null || title.length() == 0) && (text == null || text.length() == 0)) return;
        try {
            JSONObject item = new JSONObject();
            item.put("app", appName);
            item.put("title", title == null ? "" : title.toString());
            item.put("text", text == null ? "" : text.toString());
            item.put("postedAt", sbn.getPostTime());
            synchronized (RECENT) {
                RECENT.add(0, item);
                while (RECENT.size() > MAX_RECENT) RECENT.remove(RECENT.size() - 1);
            }
        } catch (Exception ignored) { }
    }

    public static JSONArray getRecentNotifications() {
        synchronized (RECENT) { return new JSONArray(RECENT); }
    }
}
