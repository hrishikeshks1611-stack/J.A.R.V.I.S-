package com.hrishikesh.jarvis;

import java.util.Locale;

/** Deterministic command parsing kept separate so command routing can be tested without Android. */
public final class CommandParser {
    public enum Action { OPEN_CAMERA, OPEN_APP, PLAY_MUSIC, PAUSE_MEDIA, RESUME_MEDIA, NEXT_TRACK, PREVIOUS_TRACK, GREETING, UNKNOWN }

    public static final class Command {
        public final Action action;
        public final String argument;
        private Command(Action action, String argument) {
            this.action = action;
            this.argument = argument;
        }
    }

    private CommandParser() { }

    public static Command parse(String raw) {
        if (raw == null) return new Command(Action.UNKNOWN, "");
        String text = raw.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return new Command(Action.UNKNOWN, "");
        if (text.startsWith("pause") || text.equals("stop") || (text.startsWith("stop") && text.contains("music")))
            return new Command(Action.PAUSE_MEDIA, "");
        if (text.startsWith("resume") || text.startsWith("continue"))
            return new Command(Action.RESUME_MEDIA, "");
        if (text.contains("next track") || text.contains("next song") || text.equals("next"))
            return new Command(Action.NEXT_TRACK, "");
        if (text.contains("previous track") || text.contains("previous song") || text.equals("previous") || text.equals("go back"))
            return new Command(Action.PREVIOUS_TRACK, "");
        if ((text.startsWith("open") && text.contains("camera")) || text.contains("take a photo")
                || text.contains("take photo") || text.contains("selfie")) {
            return new Command(Action.OPEN_CAMERA, "");
        }
        if (text.startsWith("open ")) {
            String app = text.substring(5).trim();
            if (app.startsWith("the ")) app = app.substring(4).trim();
            return app.isEmpty() ? new Command(Action.UNKNOWN, "")
                    : new Command(Action.OPEN_APP, app);
        }
        if (text.startsWith("play ") || text.equals("play") || text.contains("play music")) {
            String track = text.startsWith("play ") ? text.substring(5).trim() : "";
            if (track.equals("music")) track = "";
            return new Command(Action.PLAY_MUSIC, track);
        }
        if (text.contains("hello jarvis") || text.contains("are you there")
                || text.contains("wake up") || text.equals("hello") || text.equals("hey jarvis")) {
            return new Command(Action.GREETING, "");
        }
        return new Command(Action.UNKNOWN, "");
    }
}
