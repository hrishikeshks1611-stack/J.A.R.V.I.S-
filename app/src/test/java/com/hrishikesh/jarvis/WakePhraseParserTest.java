package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakePhraseParserTest {
    @Test public void recognizesAllFourRequestedWakePhrases() {
        assertTrue(WakePhraseParser.containsWakeWord("Hey JARVIS"));
        assertTrue(WakePhraseParser.containsWakeWord("Wake up, JARVIS"));
        assertTrue(WakePhraseParser.containsWakeWord("Hello JARVIS"));
        assertTrue(WakePhraseParser.containsWakeWord("JARVIS, are you there?"));
    }

    @Test public void extractsCommandsAfterWakePhrase() {
        assertEquals("", WakePhraseParser.extractWakeCommand("Hey JARVIS"));
        assertEquals("", WakePhraseParser.extractWakeCommand("Hello, JARVIS!"));
        assertEquals("", WakePhraseParser.extractWakeCommand("Wake up, JARVIS"));
        assertEquals("", WakePhraseParser.extractWakeCommand("JARVIS, are you there?"));
        assertEquals("open the lights", WakePhraseParser.extractWakeCommand("Hey JARVIS, open the lights"));
        assertEquals("play music", WakePhraseParser.extractWakeCommand("Wake up, JARVIS! Play music."));
    }

    @Test public void rejectsEmbeddedOrUnrequestedWakeWordAndNullInput() {
        assertFalse(WakePhraseParser.containsWakeWord("jarvisian system"));
        assertFalse(WakePhraseParser.containsWakeWord("I was talking to Jarvis yesterday"));
        assertFalse(WakePhraseParser.containsWakeWord("please wake up Jarvis"));
        assertNull(WakePhraseParser.extractWakeCommand("jarvisian system"));
        assertNull(WakePhraseParser.extractWakeCommand("I was talking to Jarvis yesterday"));
        assertFalse(WakePhraseParser.containsWakeWord(null));
        assertNull(WakePhraseParser.extractWakeCommand(null));
    }

    @Test public void normalizesPunctuationAndWhitespace() {
        assertEquals("play music", WakePhraseParser.extractWakeCommand("HEY,   JARVIS!!!   Play music."));
    }
}
