package com.hrishikesh.jarvis;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.List;

/** Controls media sessions exposed by apps after the owner grants notification-listener access. */
public final class MediaControl {
    private MediaControl() { }

    public static boolean hasAccess(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName component = new ComponentName(context, JarvisNotificationListener.class);
        for (String item : enabled.split(":")) {
            ComponentName parsed = ComponentName.unflattenFromString(item);
            if (component.equals(parsed)) return true;
        }
        return false;
    }

    public static void openAccessSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean execute(Context context, CommandParser.Action action) {
        if (!hasAccess(context)) return false;
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return false;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, JarvisNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return false;
            MediaController selected = selectSession(sessions, action);
            if (selected == null) return false;
            MediaController.TransportControls controls = selected.getTransportControls();
            switch (action) {
                case PAUSE_MEDIA: controls.pause(); return true;
                case RESUME_MEDIA: controls.play(); return true;
                case NEXT_TRACK: controls.skipToNext(); return true;
                case PREVIOUS_TRACK: controls.skipToPrevious(); return true;
                default: return false;
            }
        } catch (SecurityException | IllegalStateException e) {
            return false;
        }
    }

    private static MediaController selectSession(List<MediaController> sessions, CommandParser.Action action) {
        if (action == CommandParser.Action.PAUSE_MEDIA) {
            for (MediaController c : sessions) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return c;
            }
        } else if (action == CommandParser.Action.RESUME_MEDIA) {
            for (MediaController c : sessions) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && (s.getState() == PlaybackState.STATE_PAUSED
                        || s.getState() == PlaybackState.STATE_STOPPED)) return c;
            }
        }
        return sessions.get(0);
    }
}
