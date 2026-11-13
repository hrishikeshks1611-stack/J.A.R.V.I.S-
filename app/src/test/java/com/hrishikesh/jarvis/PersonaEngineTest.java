package com.hrishikesh.jarvis;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class PersonaEngineTest {
    @Test public void identityIsProfessionalAndAddressesSir() {
        String answer = PersonaEngine.respond("Who are you?", Collections.emptyList());
        assertTrue(answer.contains("J.A.R.V.I.S."));
        assertTrue(answer.contains("Sir"));
    }
    @Test public void answersPhilosophicalPrompt() {
        String answer = PersonaEngine.respond("What is the meaning of life?", Collections.emptyList());
        assertTrue(answer.contains("purpose"));
    }
    @Test public void doesNotPretendUnsupportedActionsWork() {
        assertTrue(PersonaEngine.respond("Create a document", Collections.emptyList()).contains("not implemented"));
    }
    @Test public void usesDryWit() {
        assertTrue(PersonaEngine.respond("Thank you", Collections.emptyList()).contains("endeavour"));
    }
}