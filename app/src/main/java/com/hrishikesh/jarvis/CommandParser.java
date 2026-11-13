package com.hrishikesh.jarvis;

import java.util.Locale;

/** Deterministic command parsing kept separate so command routing can be tested without Android. */
public final class CommandParser {
    public enum Action { OPEN_CAMERA, OPEN_APP, PLAY_MUSIC, GREETING, UNKNOWN }

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
        if (text.contains("open camera") || text.contains("take a photo")
                || text.contains("take photo") || text.contains("selfie")) {
            return new Command(Action.OPEN_CAMERA, "");
        }
        if (text.startsWith("open ")) {
            String app = text.substring(5).trim();
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
