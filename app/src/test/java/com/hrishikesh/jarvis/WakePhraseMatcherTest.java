package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakePhraseMatcherTest {
    @Test public void matchesHeyJarvis() {
        assertEquals("", WakePhraseMatcher.extractCommand("Hey JARVIS"));
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
    @Test public void rejectsOrdinarySpeech() {
        assertNull(WakePhraseMatcher.extractCommand("Please open WhatsApp"));
    }
}
