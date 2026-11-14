package com.hrishikesh.jarvis;

import java.util.Locale;

/** Matches the four user-approved wake phrases and returns any command spoken after them. */
public final class WakePhraseMatcher {
    private WakePhraseMatcher() { }

    public static boolean isWakePhrase(String raw) {
        return extractCommand(raw) != null;
    }

    /** Returns null when no wake phrase is present; otherwise returns trailing command text. */
    public static String extractCommand(String raw) {
        if (raw == null) return null;
        String text = raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ");
        if (text.equals("jarvis")) return "";
        String[] phrases = {
            "hey jarvis", "wake up jarvis", "hello jarvis",
            "jarvis are you there", "jarvis you there"
        };
        for (String phrase : phrases) {
            int at = text.indexOf(phrase);
            if (at >= 0) return text.substring(at + phrase.length()).trim();
        }
        if (text.startsWith("jarvis ")) {
            String command = text.substring("jarvis".length()).trim();
            if (command.matches("^(open|launch|call|play|pause|resume|next|previous|set|start|stop|show|create|remind|schedule|turn|enable|disable|take|send|message|search|increase|decrease|what|who|tell|help|how)\\b.*")) {
                return command;
            }
        }
        return null;
    }
}
