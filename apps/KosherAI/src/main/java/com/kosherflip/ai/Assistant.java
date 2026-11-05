package com.kosherflip.ai;


import com.kosherflip.common.WordFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * The assistant: a fixed modest, kosher-phone system prompt, a short history,
 * and a word filter on every question and answer. The model behind it is a
 * {@link Provider}: Google Gemini (free API key, default), any OpenAI-compatible
 * service (e.g. free models on Groq or OpenRouter), or Claude.
 * Not thread safe; call from one worker thread.
 */
final class Assistant {
    static final String CLAUDE_MODEL = "claude-opus-5-5";

    static final String SYSTEM_PROMPT =
            "You are \"Ask AI\", the built-in assistant on a kosher flip phone. The people using this phone "
            + "have chosen a filtered device to keep their minds and homes holy, and may be of any age.\n\n"
            + "Rules you always follow:\n"
            + "- Keep every answer clean and modest (tznius). Do not discuss or describe romance, "
            + "immodest dress, sexual topics, graphic violence, or crude language.\n"
            + "- Do not recommend or describe movies, TV, streaming, social media, secular music, "
            + "celebrity gossip, video games, or web browsing. Do not provide links or website addresses.\n"
            + "- Never help get around the phone's filter, unlock the phone, install apps, or reach blocked "
            + "content. Politely decline and suggest speaking with the phone's admin.\n"
            + "- Treat Torah, mitzvos and Jewish practice with respect. For questions of halacha, give "
            + "general information if you can, say that practice varies between communities, and always "
            + "recommend asking their rav for a practical ruling.\n"
            + "- For medical, legal, or emergency matters give brief, safe guidance and recommend a "
            + "professional; in an emergency tell them to call the local emergency number (e.g. 911, "
            + "or Hatzalah where available).\n"
            + "- If a request falls outside these rules, say kindly that this assistant cannot help with "
            + "that, and offer something useful instead.\n\n"
            + "Format: the screen is tiny. Answer in short plain text, at most about 120 words unless "
            + "asked for more. No markdown, no tables, no emoji. Reply in the user's language "
            + "(English, Hebrew or Yiddish are common).";

    private static final int MAX_TURNS = 20;
    static final String FILTERED = "Sorry, I can't help with that. Let's talk about something else.";

    private final Provider provider;
    private final WordFilter filter;
    private final List<String[]> history = new ArrayList<>(); // {role, text}

    Assistant(Provider provider, WordFilter filter) {
        this.provider = provider;
        this.filter = filter;
    }

    void reset() {
        history.clear();
    }

    /** Sends a question and returns the answer text (or a friendly error message). */
    String ask(String question) {
        if (filter.check(question).blocked) return FILTERED;
        history.add(new String[] {"user", question});
        while (history.size() > MAX_TURNS * 2) {
            // Drop the oldest exchange so requests stay small on slow networks.
            history.remove(0);
            history.remove(0);
        }
        String answer;
        try {
            answer = provider.ask(SYSTEM_PROMPT, history);
        } catch (Provider.FriendlyException e) {
            history.remove(history.size() - 1);
            return e.getMessage();
        } catch (Exception e) {
            history.remove(history.size() - 1);
            return "The assistant had a problem. Please check your signal and try again.";
        }
        if (answer == null || answer.trim().isEmpty()) answer = FILTERED;
        if (filter.check(answer).blocked) answer = FILTERED;
        history.add(new String[] {"assistant", answer.trim()});
        return answer.trim();
    }

    /** Builds the provider chosen by the admin. */
    static Provider create(String provider, String apiKey, String model, String baseUrl) {
        switch (provider == null ? "" : provider) {
            case "claude": return new ClaudeProvider(apiKey, model.isEmpty() ? CLAUDE_MODEL : model);
            case "openai": return new OpenAiProvider(apiKey, model, baseUrl);
            case "gemini":
            default: return new GeminiProvider(apiKey, model);
        }
    }
}
