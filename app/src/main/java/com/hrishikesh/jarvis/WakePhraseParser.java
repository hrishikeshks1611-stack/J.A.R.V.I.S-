package com.hrishikesh.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java wake phrase parsing, independent of Android audio hardware. */
public final class WakePhraseParser {
    private static final Pattern WAKE_PREFIX = Pattern.compile(
            "^(?:(?:hey|hello) jarvis|wake up jarvis|jarvis(?: are you there)?)(?:\\\\b|$)");

    private WakePhraseParser() { }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\\\s+", " ")
                .trim();
    }

    public static boolean containsWakeWord(String input) {
        return WAKE_PREFIX.matcher(normalize(input)).find();
    }

    /** null = no supported wake phrase; empty = phrase alone; otherwise trailing command. */
    public static String extractWakeCommand(String input) {
        String text = normalize(input);
        Matcher matcher = WAKE_PREFIX.matcher(text);
        if (!matcher.find()) return null;
        return text.substring(matcher.end()).trim();
    }
}
