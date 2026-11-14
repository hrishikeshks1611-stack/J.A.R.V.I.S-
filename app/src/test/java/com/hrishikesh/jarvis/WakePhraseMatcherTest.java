package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakePhraseMatcherTest {
    @Test public void matchesHeyJarvis() {
        assertEquals("", WakePhraseMatcher.extractCommand("Hey JARVIS"));
    }
    @Test public void matchesStandaloneJarvisWakeWord() {
        assertEquals("", WakePhraseMatcher.extractCommand("JARVIS"));
        assertEquals("open whatsapp", WakePhraseMatcher.extractCommand("JARVIS open WhatsApp"));
    }
    @Test public void matchesWakeUpJarvisAndExtractsCommand() {
        assertEquals("open whatsapp", WakePhraseMatcher.extractCommand("Wake up, JARVIS, open WhatsApp"));
    }
    @Test public void matchesHelloJarvis() {
        assertEquals("", WakePhraseMatcher.extractCommand("Hello JARVIS"));
    }
    @Test public void matchesAreYouThereVariant() {
        assertEquals("", WakePhraseMatcher.extractCommand("JARVIS, are you there?"));
    }
    @Test public void extractsCommandAfterHelloPhrase() {
        assertEquals("open whatsapp", WakePhraseMatcher.extractCommand("Hello JARVIS, open WhatsApp"));
    }
    @Test public void extractsCommandAfterAreYouTherePhrase() {
        assertEquals("play back in black", WakePhraseMatcher.extractCommand("JARVIS, are you there? Play Back in Black"));
    }
    @Test public void rejectsOrdinarySpeech() {
        assertNull(WakePhraseMatcher.extractCommand("Please open WhatsApp"));
        assertNull(WakePhraseMatcher.extractCommand("JARVIS is a helpful assistant"));
    }
}
