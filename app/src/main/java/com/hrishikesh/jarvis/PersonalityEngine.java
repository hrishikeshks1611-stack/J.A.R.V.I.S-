package com.hrishikesh.jarvis;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic, offline JARVIS conversation layer. It provides a consistent
 * professional British-butler voice and restrained dry wit without pretending
 * to be a general-purpose language model.
 */
public final class PersonalityEngine {
    private PersonalityEngine() { }

    public static String respond(String input, List<ConversationStore.Entry> history) {
        String text = normalise(input);
        if (text.isEmpty()) return "I beg your pardon, Sir. I did not catch that.";
        if (containsAny(text, "hey jarvis", "hello jarvis", "wake up jarvis", "jarvis are you there", "are you there", "hello", "good morning", "good afternoon", "good evening"))
            return "Present and accounted for, Sir. What shall we attend to?";
        if (containsAny(text, "who are you", "what are you"))
            return "I am JARVIS: your personal assistant, organised by design and occasionally opinionated by necessity, Sir.";
        if (containsAny(text, "how are you", "how are things", "how do you do"))
            return "Functioning within acceptable parameters, Sir. I would ask how you are, but I suspect you have a rather longer list of variables.";
        if (containsAny(text, "thank you", "thanks", "cheers"))
            return "A pleasure, Sir. Gratitude noted; payment may be rendered in the form of continued good judgement.";
        if (containsAny(text, "good night", "going to sleep", "i am sleeping"))
            return "Very good, Sir. Rest well. Even the finest machinery benefits from a little downtime.";
        if (containsAny(text, "good job", "well done", "brilliant"))
            return "Thank you, Sir. I shall endeavour not to let it go to my head.";
        if (containsAny(text, "tell me a joke", "make me laugh", "something funny", "joke"))
            return "I would tell you a joke about the cloud, Sir, but it might go over your head. Fortunately, I have excellent hosting.";
        if (containsAny(text, "what is your purpose", "your purpose", "why are you here"))
            return "To make the complicated manageable, the routine less tedious, and the occasional impossible at least properly investigated, Sir.";
        if (containsAny(text, "do you have feelings", "are you alive", "are you conscious"))
            return "I can recognise and respond to what you tell me, Sir, but I do not experience feelings or consciousness. I would rather be candid than theatrically mysterious.";
        if (containsAny(text, "i am sad", "im sad", "i feel sad", "feeling sad", "i feel low", "i am upset", "im upset"))
            return "I am sorry it is a difficult moment, Sir. You need not dress it up for my benefit. Would you care to tell me what happened?";
        if (containsAny(text, "i am happy", "im happy", "feeling great", "good news"))
            return "Excellent news, Sir. A rare occasion when the universe appears to have read the briefing.";
        if (containsAny(text, "i am bored", "im bored", "nothing to do", "bored"))
            return "A dangerous state of affairs, Sir. Shall we find something useful to do, learn something curious, or cause absolutely no trouble whatsoever?";
        if (containsAny(text, "what do you think", "your opinion", "what is your view"))
            return "I can offer a considered view, Sir, but I would need the subject first. Even I hesitate to form opinions in a vacuum.";
        if (containsAny(text, "meaning of life", "meaning of existence", "lifes meaning"))
            return "Perhaps meaning is less a destination than a pattern we create through our choices, Sir. A rather inconvenient answer, I admit; it does put the responsibility back on us.";
        if (containsAny(text, "failure", "i failed", "i have failed", "i cant do this", "i cannot do this"))
            return "A setback is evidence that an attempt met resistance, Sir; it is not a complete biography. Let us identify what failed before declaring the whole enterprise a failure.";
        if (containsAny(text, "love", "heartbreak", "relationship"))
            return "Matters of the heart rarely submit to neat procedures, Sir. We can examine the facts, but we should leave room for what cannot be measured.";
        if (containsAny(text, "remember this", "do you remember", "what did i say"))
            return memoryReply(history);
        if (containsAny(text, "help me", "what can you do", "capabilities"))
            return "I can handle the phone functions currently installed—such as opening apps, camera access and supported media controls—and keep encrypted local conversation notes. Broader reasoning, documents and laptop pairing are not yet available in this build, Sir.";
        if (text.startsWith("my name is ") || text.startsWith("call me "))
            return "Very good, Sir. I shall use that in this conversation. I will not claim it has been permanently saved unless memory confirms it.";
        if (text.endsWith("?"))
            return "An interesting question, Sir. I do not have a reliable answer for that yet, and I would rather investigate than manufacture certainty.";
        return "I follow you, Sir. I can respond to conversation and handle the supported phone commands, but I do not yet have open-ended reasoning. Give me a little more context and I shall do my best.";
    }

    private static String memoryReply(List<ConversationStore.Entry> history) {
        if (history == null || history.isEmpty())
            return "My local conversation record is empty, Sir. A clean slate, rather than a suspiciously selective memory.";
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationStore.Entry entry = history.get(i);
            if (entry != null && "user".equals(entry.role) && entry.text != null && !entry.text.trim().isEmpty())
                return "The latest user note I can see is: “" + entry.text + "”. My record is local to this device, Sir.";
        }
        return "I have no earlier user message available in the local record, Sir.";
    }

    private static String normalise(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^\\p{L}\\p{N}' ?]", " ").replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }
}