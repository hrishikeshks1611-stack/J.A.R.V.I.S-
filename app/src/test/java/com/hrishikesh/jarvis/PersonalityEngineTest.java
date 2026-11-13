package com.hrishikesh.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersonalityEngineTest {
    @Test public void usesProfessionalButlerGreeting() {
        assertTrue(PersonalityEngine.respond("Hey JARVIS", null).contains("Sir"));
    }
    @Test public void answersIdentityWithoutClaimingSentience() {
        assertTrue(PersonalityEngine.respond("Who are you?", null).contains("personal assistant"));
        assertTrue(PersonalityEngine.respond("Are you conscious?", null).contains("do not experience"));
    }
    @Test public void usesDryWitForJokesAndThanks() {
        assertTrue(PersonalityEngine.respond("Tell me a joke", null).contains("cloud"));
        assertTrue(PersonalityEngine.respond("Thank you", null).contains("pleasure"));
    }
    @Test public void respondsThoughtfullyToEmotionalAndPhilosophicalPrompts() {
        assertTrue(PersonalityEngine.respond("I feel sad", null).contains("what happened"));
        assertTrue(PersonalityEngine.respond("What is the meaning of life?", null).contains("choices"));
    }
    @Test public void disclosesCurrentCapabilityLimits() {
        assertTrue(PersonalityEngine.respond("What can you do?", null).contains("not yet available"));
    }
    @Test public void doesNotInventAnswersToUnknownQuestions() {
        assertTrue(PersonalityEngine.respond("What is the capital of Atlantis?", null).contains("rather investigate"));
    }
}