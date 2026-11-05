package com.kosherflip.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import java.util.ArrayList;
import java.util.List;

/** Talks to Claude with a modest, kosher-phone system prompt. Not thread safe; call from one worker thread. */
final class Assistant {
    static final String MODEL = "claude-opus-5-5";

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

    private final AnthropicClient client;
    private boolean useFallbacks = true;
    private final List<String[]> history = new ArrayList<>(); // {role, text}

    Assistant(String apiKey) {
        client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    void reset() {
        history.clear();
    }

    /** Sends a question and returns the answer text (or a friendly error message). */
    String ask(String question) {
        history.add(new String[] {"user", question});
        while (history.size() > MAX_TURNS * 2) {
            // Drop the oldest exchange so requests stay small on slow networks.
            history.remove(0);
            history.remove(0);
        }
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        if (useFallbacks) {
            // Server-side refusal fallback: if a safety classifier declines, the API
            // retries on a suitable model instead of returning nothing.
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (String[] m : history) {
            if ("user".equals(m[0])) b.addUserMessage(m[1]);
            else b.addAssistantMessage(m[1]);
        }
        String answer;
        try {
            Message resp = client.messages().create(b.build());
            if (resp.stopReason().isPresent() && StopReason.REFUSAL.equals(resp.stopReason().get())) {
                answer = "Sorry, I can't help with that. Let's talk about something else.";
            } else {
                StringBuilder sb = new StringBuilder();
                resp.content().forEach(block -> block.text().ifPresent(t -> sb.append(t.text())));
                answer = sb.toString().trim();
                if (answer.isEmpty()) answer = "(no answer)";
            }
        } catch (BadRequestException e) {
            history.remove(history.size() - 1);
            if (useFallbacks && String.valueOf(e.getMessage()).contains("fallback")) {
                useFallbacks = false; // account/region without the beta: retry plainly
                return ask(question);
            }
            return "The assistant could not answer that (" + e.statusCode() + ").";
        } catch (UnauthorizedException | PermissionDeniedException e) {
            history.remove(history.size() - 1);
            return "The AI key is not valid. Please ask the phone's admin to check it in KosherGuard.";
        } catch (RateLimitException e) {
            history.remove(history.size() - 1);
            return "The assistant is busy right now. Please try again in a minute.";
        } catch (AnthropicServiceException e) {
            history.remove(history.size() - 1);
            return "The assistant had a problem (" + e.statusCode() + "). Please try again.";
        } catch (AnthropicIoException e) {
            history.remove(history.size() - 1);
            return "No internet connection. Please check your signal and try again.";
        }
        history.add(new String[] {"assistant", answer});
        return answer;
    }
}
