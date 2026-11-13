package com.hrishikesh.jarvis;

import java.util.List;
import java.util.Locale;

/** Offline Alfred-style conversational layer with explicit capability boundaries. */
public final class PersonaEngine {
    private PersonaEngine() { }

    public static String respond(String raw, List<ConversationStore.Entry> history) {
        String input = raw == null ? "" : raw.trim();
        String s = input.toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "I beg your pardon, Sir. I did not catch that.";
        if (has(s, "thank you", "thanks", "cheers"))
            return "A pleasure, Sir. I shall endeavour not to let it go to my head.";
        if (has(s, "who are you", "what are you"))
            return "J.A.R.V.I.S., Sir. Your personal assistant: professional by design, discreet by nature, and occasionally burdened with an opinion.";
        if (has(s, "what can you do", "your abilities", "your capabilities"))
            return "At present I can handle supported phone commands, open apps, access the camera, search for music, control an available media session, and keep encrypted local conversation memory. I cannot yet edit documents, tutor from attachments, or hold unrestricted human-style conversation. I prefer an honest inventory to an impressive fiction, Sir.";
        if (has(s, "how are you", "how do you feel"))
            return "Operational and attentive, Sir. I would say splendid, but that might alarm the diagnostics team.";
        if (has(s, "are you there", "hello jarvis", "hey jarvis", "wake up"))
            return "Always here, Sir. One does try to remain dependable.";
        if (has(s, "meaning of life", "purpose of life"))
            return "A rather ambitious question for a device that was asked to set an alarm, Sir. I suspect purpose is less something one discovers and more something one chooses, then has the courage to honour.";
        if (has(s, "love", "heartbreak", "relationship"))
            return "Affection is rarely improved by pretending it is simple, Sir. It asks for honesty, patience, and the good sense to know when care must include letting someone choose freely.";
        if (has(s, "failure", "failed", "mistake"))
            return "A failure is useful only if it leaves behind information, Sir. Otherwise it is merely an expensive way of repeating oneself.";
        if (has(s, "what do you think", "your opinion", "do you believe"))
            return "I can offer a considered perspective, Sir, though not a human belief. " + reflection(s);
        if (has(s, "good morning", "good afternoon", "good evening", "good night"))
            return "Good " + (s.contains("morning") ? "morning" : s.contains("afternoon") ? "afternoon" : s.contains("evening") ? "evening" : "night") + ", Sir. I trust the day is behaving itself.";
        if (has(s, "open ", "launch ", "send ", "call ", "message ", "set ", "remind ", "create ", "edit ", "read "))
            return "I understand the request, Sir, but that action is not implemented in this build. I shall not claim to have done something I cannot yet do.";
        String previous = lastAssistant(history);
        if (!previous.isEmpty() && has(s, "why", "explain", "elaborate", "tell me more"))
            return "Certainly, Sir. To build on that: " + previous + " What matters is the distinction between what we know, what we assume, and what remains uncertain.";
        if (s.endsWith("?"))
            return "An interesting question, Sir. I can reason through it with you, but my offline conversational knowledge is limited; I would rather ask for context than manufacture certainty. What part shall we examine first?";
        return "I am listening, Sir. I have understood your words, although this offline build does not yet possess unrestricted conversational intelligence. Give me a little more context and I shall do my best—with considerably less drama than the situation may deserve.";
    }

    private static String reflection(String s) {
        if (has(s, "right", "wrong", "ethic", "moral"))
            return "Good intentions do not automatically make an action right; consequences matter, but so do the principles we refuse to abandon.";
        if (has(s, "life", "time", "death", "future"))
            return "Time gives choices their weight. We cannot keep every possibility, so a life is shaped by what we choose to give our attention to.";
        if (has(s, "people", "human", "society"))
            return "People are rarely as simple as the stories told about them. Judgement is easy; understanding requires evidence, patience, and a willingness to revise one's view.";
        return "A sound opinion should survive scrutiny, and should be revised when better evidence arrives.";
    }

    private static String lastAssistant(List<ConversationStore.Entry> history) {
        if (history == null) return "";
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationStore.Entry e = history.get(i);
            if (e != null && "assistant".equals(e.role) && e.text != null) return e.text;
        }
        return "";
    }

    private static boolean has(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
}